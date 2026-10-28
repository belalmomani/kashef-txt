package com.kashef.textsearch.engine

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

/** Which file extensions are searched for a given scope option. */
class ScopeFilter(val extensions: Set<String>, val countsUnsupportedDocs: Boolean) {
    companion object {
        private val TEXT = setOf(
            "txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "css", "js", "ts",
            "kt", "kts", "java", "py", "c", "cpp", "h", "cs", "go", "rs", "php", "rb", "sql", "sh",
            "bat", "yml", "yaml", "ini", "cfg", "conf", "properties", "gradle", "log", "srt",
        )
        private val CODE = setOf(
            "js", "ts", "kt", "kts", "java", "py", "c", "cpp", "h", "cs", "go", "rs", "php", "rb",
            "sql", "sh", "bat", "xml", "html", "htm", "css", "json", "yml", "yaml", "gradle", "properties",
        )
        private val NOTES = setOf("txt", "md", "markdown", "csv", "log", "srt")
        private val DOCS = setOf("txt") // PDF/DOCX readers are not implemented yet

        fun forIndex(i: Int): ScopeFilter = when (i) {
            1 -> ScopeFilter(DOCS, true)
            2 -> ScopeFilter(CODE, false)
            3 -> ScopeFilter(NOTES, false)
            else -> ScopeFilter(TEXT, true)
        }
    }
}

/** Walks a SAF tree and searches supported files. Blocking: call from a background thread. */
class TreeScanner(
    private val resolver: ContentResolver,
    private val filter: ScopeFilter,
    private val query: SearchQuery,
    private val acc: ScanAccumulator,
    private val job: Job?,
    private val tick: () -> Unit,
) {
    companion object {
        private const val MAX_DEPTH = 30
        private const val MAX_FILE_BYTES = 30L * 1024 * 1024
        private const val MAX_PER_FILE = 50
        private val UNSUPPORTED_DOC = setOf("pdf", "doc", "docx", "odt", "xls", "xlsx", "ppt", "pptx")
    }

    private class Entry(val id: String, val name: String, val isDir: Boolean, val size: Long)

    private val visited = HashSet<String>() // avoids scanning the same document twice

    fun scan(treeUri: Uri) {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val label = rootId.substringAfter(':', "").ifEmpty { rootId }
        walk(treeUri, rootId, label, 0)
    }

    private fun walk(treeUri: Uri, docId: String, path: String, depth: Int) {
        if (acc.stop || depth > MAX_DEPTH) return
        if (!visited.add(docId)) return
        val children = listChildren(treeUri, docId, path) ?: return
        for (c in children) {
            job?.ensureActive()
            if (acc.stop) return
            val childPath = "$path/${c.name}"
            if (c.isDir) walk(treeUri, c.id, childPath, depth + 1) else handleFile(treeUri, c, childPath)
        }
    }

    private fun listChildren(treeUri: Uri, docId: String, path: String): List<Entry>? {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        return try {
            val out = ArrayList<Entry>()
            resolver.query(uri, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val mime = c.getString(2) ?: ""
                    out.add(
                        Entry(
                            id = c.getString(0),
                            name = c.getString(1) ?: "؟",
                            isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = if (c.isNull(3)) -1L else c.getLong(3),
                        ),
                    )
                }
            }
            out
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            acc.addProblem(path, "لا يوجد إذن للوصول إلى المجلد"); null
        } catch (e: Exception) {
            acc.addProblem(path, "تعذر فتح المجلد"); null
        }
    }

    private fun handleFile(treeUri: Uri, e: Entry, path: String) {
        if (!visited.add(e.id)) return
        val ext = e.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        when {
            ext in filter.extensions -> scanFile(treeUri, e, path)
            filter.countsUnsupportedDocs && ext in UNSUPPORTED_DOC -> acc.unsupportedDocs++
            else -> acc.otherSkipped++
        }
    }

    private fun scanFile(treeUri: Uri, e: Entry, path: String) {
        if (e.size > MAX_FILE_BYTES) {
            acc.addProblem(path, "حجم الملف كبير جدًا (الحد 30 ميغابايت)")
            return
        }
        acc.current = e.name
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, e.id)
        try {
            val outcome = resolver.openInputStream(uri)?.use { input ->
                FileSearcher.search(input, query, MAX_PER_FILE) { job?.ensureActive() }
            }
            when (outcome) {
                null -> acc.addProblem(path, "تعذر فتح الملف")
                is FileOutcome.Unreadable -> acc.addProblem(path, outcome.reason)
                is FileOutcome.Done -> {
                    acc.scanned++
                    if (outcome.matches.isNotEmpty()) acc.addResult(FileResult(e.name, path, outcome.matches, outcome.truncated))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: SecurityException) {
            acc.addProblem(path, "لا يوجد إذن للوصول إلى الملف")
        } catch (ex: FileNotFoundException) {
            acc.addProblem(path, "الملف غير موجود أو غير متاح")
        } catch (ex: IOException) {
            acc.addProblem(path, "خطأ أثناء القراءة")
        } catch (ex: Exception) {
            acc.addProblem(path, "خطأ غير متوقع")
        }
        tick()
    }
}
