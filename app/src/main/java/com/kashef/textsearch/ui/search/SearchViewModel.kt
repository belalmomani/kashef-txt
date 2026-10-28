package com.kashef.textsearch.ui.search

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kashef.textsearch.engine.ScanAccumulator
import com.kashef.textsearch.engine.ScanSnapshot
import com.kashef.textsearch.engine.ScopeFilter
import com.kashef.textsearch.engine.SearchMode
import com.kashef.textsearch.engine.SearchQuery
import com.kashef.textsearch.engine.TreeScanner
import com.kashef.textsearch.ui.overlay.OverlayController
import com.kashef.textsearch.ui.scopes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ScanStage { Running, Finished, Cancelled, Failed }

data class ScanUi(
    val stage: ScanStage,
    val snap: ScanSnapshot,
    val query: String,
    val scopeTitle: String,
    val modeLabel: String,
    val message: String = "",
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

    private var job: Job? = null
    @Volatile private var closed = false

    init {
        viewModelScope.launch { ui.collect { refreshOverlay() } }
        // Keep only folders whose read permission is still granted by Android.
        val granted = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        prefs.getStringSet("folders", emptySet())?.filter { it in granted }?.sorted()?.let { folders.addAll(it) }
    }

    private fun refreshOverlay() = overlay.render(_ui.value, overlayEnabled && !inForeground)

    fun setForeground(v: Boolean) {
        inForeground = v
        refreshOverlay()
    }

    fun setOverlayEnabled(v: Boolean) {
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
    }

    fun start() {
        val q = SearchQuery.create(query, mode) ?: return
        val roots = folders.map { Uri.parse(it) }
        if (roots.isEmpty()) return
        val filter = ScopeFilter.forIndex(scopeIndex)
        val title = scopes[scopeIndex].title
        val shownQuery = query.trim()
        val modeLabel = mode.label()

        job?.cancel()
        closed = false
        overlay.newRun()
        val acc = ScanAccumulator()
        val t0 = System.nanoTime()
        fun ms() = (System.nanoTime() - t0) / 1_000_000
        _ui.value = ScanUi(ScanStage.Running, acc.snapshot(false, 0), shownQuery, title, modeLabel)

        job = viewModelScope.launch(Dispatchers.IO) {
            var last = 0L
            val tick = {
                val now = System.nanoTime()
                if (now - last > 200_000_000L) {
                    last = now
                    _ui.value = ScanUi(ScanStage.Running, acc.snapshot(false, ms()), shownQuery, title, modeLabel)
                }
            }
            try {
                val scanner = TreeScanner(resolver, filter, q, acc, coroutineContext[Job], tick)
                for (r in roots) {
                    scanner.scan(r)
                    if (acc.stop) break
                }
                _ui.value = ScanUi(ScanStage.Finished, acc.snapshot(true, ms()), shownQuery, title, modeLabel)
            } catch (e: CancellationException) {
                if (!closed) _ui.value = ScanUi(ScanStage.Cancelled, acc.snapshot(false, ms()), shownQuery, title, modeLabel)
                throw e
            } catch (e: Exception) {
                _ui.value = ScanUi(
                    ScanStage.Failed, acc.snapshot(false, ms()), shownQuery, title, modeLabel,
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    /** Stop the running search but keep partial results on screen. */
    fun stop() { job?.cancel() }

    /** Leave the results screen. */
    fun close() {
        closed = true
        job?.cancel()
        _ui.value = null
    }
}
