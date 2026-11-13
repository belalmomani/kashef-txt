package com.kashef.textsearch.ui.search

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kashef.textsearch.BuildConfig
import com.kashef.textsearch.ads.AdManager
import com.kashef.textsearch.diag.DiagLog
import com.kashef.textsearch.engine.CaseRecord
import com.kashef.textsearch.engine.FileResult
import com.kashef.textsearch.engine.FileSearcher
import com.kashef.textsearch.engine.FileText
import com.kashef.textsearch.engine.TextOpen
import com.kashef.textsearch.engine.IndexDb
import com.kashef.textsearch.engine.IndexSession
import com.kashef.textsearch.engine.IndexedRoot
import com.kashef.textsearch.engine.MemoryStore
import com.kashef.textsearch.engine.ScanAccumulator
import com.kashef.textsearch.engine.ScanSnapshot
import com.kashef.textsearch.engine.ScopeFilter
import com.kashef.textsearch.engine.SearchMode
import com.kashef.textsearch.engine.SearchQuery
import com.kashef.textsearch.engine.TreeScanner
import com.kashef.textsearch.ui.overlay.OverlayController
import com.kashef.textsearch.service.SearchService
import com.kashef.textsearch.ui.scopes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

enum class ScanStage { Running, Finished, Cancelled, Failed }

data class ScanUi(
    val stage: ScanStage,
    val snap: ScanSnapshot,
    val query: String,
    val scopeTitle: String,
    val modeLabel: String,
    val message: String = "",
    val startedAt: Long = 0L,
    val folders: List<String> = emptyList(),
    val scopeIndex: Int = 0,
    val modeName: String = "",
    val fold: Boolean = false,
)

/** The case record for a finished/stopped/failed search (counts only, never file contents). */
fun ScanUi.toRecord() = CaseRecord(
    id = "$startedAt-${query.hashCode()}",
    query = query,
    mode = modeName,
    scopeIndex = scopeIndex,
    scopeTitle = scopeTitle,
    folders = folders,
    startedAt = startedAt,
    durationMs = snap.elapsedMs,
    scanned = snap.scanned,
    matchedFiles = snap.matchedFiles,
    matchCount = snap.matchCount,
    problems = snap.problemsTotal,
    unsupportedDocs = snap.unsupportedDocs,
    otherSkipped = snap.otherSkipped,
    limitReached = snap.limitReached,
    status = when (stage) {
        ScanStage.Finished -> "finished"
        ScanStage.Cancelled -> "cancelled"
        ScanStage.Failed -> "failed"
        ScanStage.Running -> "running"
    },
    indexSkipped = snap.skippedByIndex,
    fold = fold,
)

/** Progress/result of a manual index build or refresh. */
data class IndexUi(
    val label: String,
    val running: Boolean,
    val indexed: Int = 0,
    val scanned: Int = 0,
    val problems: Int = 0,
    val cancelled: Boolean = false,
    val message: String = "",
)

data class ViewerUi(
    val title: String,
    val path: String,
    val uri: String,
    val mime: String,
    val line: Int,
    val lines: List<Pair<Int, String>> = emptyList(),
    val query: SearchQuery? = null,
    val loading: Boolean = true,
    val error: String = "",
)

fun SearchMode.label() = when (this) {
    SearchMode.Phrase -> "عبارة كاملة"
    SearchMode.AllWords -> "كل الكلمات"
    SearchMode.AnyWord -> "أي كلمة"
}

fun folderLabel(uriString: String): String = try {
    val id = DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
    id.substringAfter(':', "").ifEmpty { id }
} catch (e: Exception) {
    uriString
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("kashef_prefs", Context.MODE_PRIVATE)
    private val resolver = app.contentResolver

    var query by mutableStateOf("")
    var scopeIndex by mutableIntStateOf(0)
    var mode by mutableStateOf(SearchMode.Phrase)
    val folders = mutableStateListOf<String>()

    val overlay = OverlayController(app)
    var overlayEnabled by mutableStateOf(prefs.getBoolean("overlay", true))
        private set
    private var inForeground = true

    private val _ui = MutableStateFlow<ScanUi?>(null)
    val ui: StateFlow<ScanUi?> = _ui

    private val store = MemoryStore(File(app.filesDir, "investigator_memory.tsv"))
    val records = mutableStateListOf<CaseRecord>() // newest first
    var memoryEnabled by mutableStateOf(prefs.getBoolean("memory", false)) // optional: off by default
        private set
    private val _report = MutableStateFlow<CaseRecord?>(null)
    val report: StateFlow<CaseRecord?> = _report

    // Flexible matching of Arabic letter variants (off by default).
    var flexible by mutableStateOf(prefs.getBoolean("flexible", false))
        private set

    fun updateFlexible(v: Boolean) {
        flexible = v
        prefs.edit().putBoolean("flexible", v).apply()
    }

    // Keeps a foreground service alive while a search or an index build is running.
    private val activeWork = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun workStarted() {
        if (activeWork.incrementAndGet() == 1) {
            try {
                val ctx: Application = getApplication()
                ContextCompat.startForegroundService(ctx, Intent(ctx, SearchService::class.java))
            } catch (e: Exception) { /* the search still runs; it just may not survive in the background */ }
        }
    }

    private fun workEnded() {
        if (activeWork.decrementAndGet() <= 0) {
            activeWork.set(0)
            // Wait a moment so a start request and a stop request can never cross each other.
            mainHandler.postDelayed({
                if (activeWork.get() == 0) {
                    try {
                        val ctx: Application = getApplication()
                        ctx.stopService(Intent(ctx, SearchService::class.java))
                    } catch (e: Exception) { /* ignore */ }
                }
            }, 1500)
        }
    }

    fun notifAsked(): Boolean = prefs.getBoolean("notif_asked", false)

    fun markNotifAsked() {
        prefs.edit().putBoolean("notif_asked", true).apply()
    }

    // Personalized-ads consent (off by default). Only meaningful when ads are configured in the build.
    var adsPersonalized by mutableStateOf(prefs.getBoolean("ads_pas", false))
        private set
    val adsConsentTime: Long get() = prefs.getLong("ads_pas_time", System.currentTimeMillis())

    // Optional local index (per folder, opt-in).
    private val indexDb = IndexDb(app)
    private val _indexRoots = MutableStateFlow<List<IndexedRoot>>(emptyList())
    val indexRoots: StateFlow<List<IndexedRoot>> = _indexRoots
    private val _index = MutableStateFlow<IndexUi?>(null)
    val index: StateFlow<IndexUi?> = _index
    private var indexJob: Job? = null

    private var lastQuery: SearchQuery? = null
    private val _viewer = MutableStateFlow<ViewerUi?>(null)
    val viewer: StateFlow<ViewerUi?> = _viewer

    private var job: Job? = null
    @Volatile private var closed = false

    init {
        viewModelScope.launch { ui.collect { refreshOverlay() } }
        records.addAll(store.load())
        refreshIndexStats()
        // Keep only folders whose read permission is still granted by Android.
        val granted = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        prefs.getStringSet("folders", emptySet())?.filter { it in granted }?.sorted()?.let { folders.addAll(it) }
    }

    private fun refreshOverlay() = overlay.render(_ui.value, overlayEnabled && !inForeground)

    fun setForeground(v: Boolean) {
        inForeground = v
        refreshOverlay()
    }

    fun updateOverlayEnabled(v: Boolean) {
        overlayEnabled = v
        prefs.edit().putBoolean("overlay", v).apply()
        refreshOverlay()
    }

    override fun onCleared() {
        overlay.hide()
    }

    private fun saveFolders() = prefs.edit().putStringSet("folders", folders.toSet()).apply()

    fun addFolder(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { /* permission may already be persisted or not persistable */ }
        val s = uri.toString()
        if (s !in folders) {
            folders.add(s)
            saveFolders()
        }
    }

    fun removeFolder(s: String) {
        try {
            resolver.releasePersistableUriPermission(Uri.parse(s), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { /* ignore */ }
        folders.remove(s)
        saveFolders()
        viewModelScope.launch(Dispatchers.IO) {
            try { indexDb.disable(s) } catch (e: Exception) { /* ignore */ }
            refreshStatsBlocking()
        }
    }

    /** Reads index statistics; call from a background thread. */
    private fun refreshStatsBlocking() {
        _indexRoots.value = try { indexDb.roots() } catch (e: Exception) { emptyList() }
    }

    fun refreshIndexStats() {
        viewModelScope.launch(Dispatchers.IO) { refreshStatsBlocking() }
    }

    /** Turns indexing on or off for ONE folder. Turning it off deletes that folder's index. */
    fun setIndexing(root: String, on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (on) indexDb.enable(root, folderLabel(root)) else indexDb.disable(root)
            } catch (e: Exception) { /* ignore */ }
            refreshStatsBlocking()
            if (on) buildIndex(root)
        }
    }

    /** Reads the folder and indexes new or changed files (unchanged files are skipped). */
    fun buildIndex(root: String) {
        indexJob?.cancel()
        val label = folderLabel(root)
        _index.value = IndexUi(label, true)
        workStarted()
        val buildJob = viewModelScope.launch(Dispatchers.IO) {
            val acc = ScanAccumulator()
            var last = 0L
            val tick = {
                val now = System.nanoTime()
                if (now - last > 300_000_000L) {
                    last = now
                    _index.value = IndexUi(label, true, acc.indexedNow, acc.scanned, acc.problemsTotal)
                }
            }
            val session = IndexSession(indexDb, root)
            try {
                val scanner = TreeScanner(resolver, ScopeFilter.forIndex(0), null, acc, coroutineContext[Job], tick)
                scanner.scan(Uri.parse(root), session)
                session.flush()
                // Forget files that no longer exist, only after a complete and error-free walk.
                if (!acc.stop && scanner.listingFailures == 0) session.prune(scanner.visitedIds())
                _index.value = IndexUi(label, false, acc.indexedNow, acc.scanned, acc.problemsTotal)
                DiagLog.log("index", "done indexed=${acc.indexedNow} read=${acc.scanned} problems=${acc.problemsTotal}")
            } catch (e: CancellationException) {
                try { session.flush() } catch (ex: Exception) { /* ignore */ }
                _index.value = IndexUi(label, false, acc.indexedNow, acc.scanned, acc.problemsTotal, cancelled = true)
                throw e
            } catch (e: Exception) {
                DiagLog.error("index", e)
                _index.value = IndexUi(label, false, acc.indexedNow, acc.scanned, acc.problemsTotal, message = e.message ?: e.javaClass.simpleName)
            } finally {
                refreshStatsBlocking()
            }
        }
        indexJob = buildJob
        buildJob.invokeOnCompletion { workEnded() }
    }

    fun updateAdsPersonalized(v: Boolean) {
        adsPersonalized = v
        val now = System.currentTimeMillis()
        prefs.edit().putBoolean("ads_pas", v).putLong("ads_pas_time", now).apply()
        AdManager.updateConsent(getApplication(), v, now)
    }

    /** Revokes Android's access to every folder and forgets them (their indexes are deleted too). */
    fun removeAllFolders() {
        folders.toList().forEach { removeFolder(it) }
    }

    /** Deletes everything the app stores locally: folders, indexes, investigator memory and options. */
    fun deleteAllLocalData() {
        _report.value = null
        _viewer.value = null
        close()
        clearRecords()
        deleteAllIndexes()
        removeAllFolders()
        DiagLog.clear()
        prefs.edit().clear().apply()
        memoryEnabled = false
        overlayEnabled = true
        adsPersonalized = false
        flexible = false
        AdManager.updateConsent(getApplication(), false, System.currentTimeMillis())
        query = ""
        mode = SearchMode.Phrase
        scopeIndex = 0
    }

    fun cancelIndex() { indexJob?.cancel() }

    fun dismissIndexStatus() { _index.value = null }

    /** Deletes every index of every folder. */
    fun deleteAllIndexes() {
        indexJob?.cancel()
        _index.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try { indexDb.clearAll() } catch (e: Exception) { /* ignore */ }
            refreshStatsBlocking()
        }
    }

    private fun persistRecords() {
        val copy = records.toList()
        viewModelScope.launch(Dispatchers.IO) {
            try { store.save(copy) } catch (e: Exception) { /* memory is best-effort */ }
        }
    }

    fun updateMemoryEnabled(v: Boolean) {
        memoryEnabled = v
        prefs.edit().putBoolean("memory", v).apply()
    }

    private fun saveRecord(r: CaseRecord) {
        if (!memoryEnabled) return
        viewModelScope.launch(Dispatchers.Main) {
            records.removeAll { it.id == r.id }
            records.add(0, r)
            while (records.size > 200) records.removeAt(records.lastIndex)
            persistRecords()
        }
    }

    fun deleteRecord(id: String) {
        records.removeAll { it.id == id }
        persistRecords()
    }

    fun clearRecords() {
        records.clear()
        viewModelScope.launch(Dispatchers.IO) {
            try { store.deleteAll() } catch (e: Exception) { /* ignore */ }
        }
    }

    /** Fills the search form from a past case (the folders chosen now are kept). */
    fun prefill(r: CaseRecord) {
        query = r.query
        mode = try { SearchMode.valueOf(r.mode) } catch (e: Exception) { SearchMode.Phrase }
        scopeIndex = r.scopeIndex.coerceIn(0, scopes.lastIndex)
        updateFlexible(r.fold)
    }

    fun showReport() {
        val cur = _ui.value ?: return
        if (cur.stage != ScanStage.Running) _report.value = cur.toRecord()
    }

    fun showReportFor(r: CaseRecord) {
        _report.value = r
    }

    fun closeReport() {
        _report.value = null
    }

    /** Counts and fixed reason categories only: no file names, paths, contents or search phrases. */
    private fun searchSummary(u: ScanUi): String {
        val s = u.snap
        val reasons = s.problems.groupingBy { it.reason }.eachCount().entries.joinToString("; ") { "${it.key}×${it.value}" }
        return "stage=${u.stage} scanned=${s.scanned} matched=${s.matchedFiles} problems=${s.problemsTotal} " +
            "skippedByIndex=${s.skippedByIndex} unsupported=${s.unsupportedDocs} ms=${s.elapsedMs}" +
            if (reasons.isNotEmpty()) " reasons=[$reasons]" else ""
    }

    /** A text report the user can share with the developer. It contains no file names, paths, contents or phrases. */
    fun diagnosticsReport(): String {
        val sb = StringBuilder()
        sb.append("تقرير تشخيص كاشف النصوص\n")
        sb.append("الإصدار: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        sb.append("أندرويد: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • الجهاز: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append(
            "الإعدادات: ذاكرة=$memoryEnabled • مرن=$flexible • فوق التطبيقات=${overlayEnabled && overlay.canDraw()} • " +
                "مجلدات=${folders.size} • فهارس=${_indexRoots.value.size} • إعلانات=${AdManager.configured()}\n",
        )
        _ui.value?.let { sb.append("آخر بحث: ").append(searchSummary(it)).append('\n') }
        sb.append("\nآخر الأحداث (بلا أسماء ملفات ولا محتوى):\n")
        DiagLog.lines(60).forEach { sb.append(it).append('\n') }
        return sb.toString()
    }

    fun start() {
        val foldOn = flexible
        val q = SearchQuery.create(query, mode, foldOn) ?: return
        lastQuery = q
        val roots = folders.map { Uri.parse(it) }
        if (roots.isEmpty()) return
        val filter = ScopeFilter.forIndex(scopeIndex)
        val title = scopes[scopeIndex].title
        val shownQuery = query.trim()
        val modeLabel = mode.label()
        val modeName = mode.name
        val scopeIdx = scopeIndex
        val folderLabels = folders.map { folderLabel(it) }
        val startedAt = System.currentTimeMillis()

        job?.cancel()
        closed = false
        overlay.newRun()
        val acc = ScanAccumulator()
        val t0 = System.nanoTime()
        fun ms() = (System.nanoTime() - t0) / 1_000_000
        fun make(stage: ScanStage, completed: Boolean, msg: String = "") = ScanUi(
            stage, acc.snapshot(completed, ms()),
            shownQuery, title, modeLabel, msg, startedAt, folderLabels, scopeIdx, modeName, foldOn,
        )
        fun finish(stage: ScanStage, completed: Boolean, msg: String = "") {
            val u = make(stage, completed, msg)
            _ui.value = u
            saveRecord(u.toRecord())
            DiagLog.log("search", searchSummary(u))
        }
        _ui.value = ScanUi(
            ScanStage.Running, acc.snapshot(false, 0), shownQuery, title, modeLabel, "",
            startedAt, folderLabels, scopeIdx, modeName, foldOn,
        )

        workStarted()
        val searchJob = viewModelScope.launch(Dispatchers.IO) {
            var last = 0L
            val tick = {
                val now = System.nanoTime()
                if (now - last > 200_000_000L) {
                    last = now
                    _ui.value = make(ScanStage.Running, false)
                }
            }
            val sessions = ArrayList<IndexSession>()
            try {
                val scanner = TreeScanner(resolver, filter, q, acc, coroutineContext[Job], tick)
                for (r in roots) {
                    val key = r.toString()
                    // The index is used (and updated) only for folders where the user turned it on.
                    val session = if (indexDb.isEnabled(key)) IndexSession(indexDb, key).also { sessions.add(it) } else null
                    scanner.scan(r, session)
                    session?.flush()
                    if (session != null && !acc.stop && scanner.listingFailures == 0) session.prune(scanner.visitedIds())
                    if (acc.stop) break
                }
                finish(ScanStage.Finished, true)
            } catch (e: CancellationException) {
                try { sessions.forEach { it.flush() } } catch (ex: Exception) { /* ignore */ }
                if (!closed) finish(ScanStage.Cancelled, false)
                throw e
            } catch (e: Exception) {
                DiagLog.error("search", e)
                finish(ScanStage.Failed, false, e.message ?: e.javaClass.simpleName)
            }
        }
        job = searchJob
        searchJob.invokeOnCompletion { workEnded() } // runs for finish, cancel and failure alike
    }

    /** Stop the running search but keep partial results on screen. */
    fun stop() { job?.cancel() }

    /** Reads the lines around a match from the file and shows them in the context viewer. */
    fun openContext(r: FileResult, line: Int) {
        _viewer.value = ViewerUi(r.name, r.path, r.uri, r.mime, line, query = lastQuery)
        viewModelScope.launch(Dispatchers.IO) {
            val window = try {
                resolver.openInputStream(Uri.parse(r.uri))?.use { input ->
                    when (val opened = FileText.open(input, r.ext)) {
                        is TextOpen.Ready -> FileSearcher.readWindow(opened.stream, maxOf(1, line - 8), line + 8)
                        is TextOpen.Failed -> null
                    }
                }
            } catch (e: Exception) {
                DiagLog.error("viewer", e)
                null
            }
            val cur = _viewer.value
            if (cur != null && cur.uri == r.uri && cur.line == line) {
                _viewer.value = if (window == null) {
                    cur.copy(loading = false, error = "تعذر قراءة الملف (ثنائي أو غير متاح الآن).")
                } else {
                    cur.copy(loading = false, lines = window)
                }
            }
        }
    }

    fun closeViewer() {
        _viewer.value = null
    }

    /** Leave the results screen. */
    fun close() {
        _viewer.value = null
        closed = true
        job?.cancel()
        _ui.value = null
    }
}
