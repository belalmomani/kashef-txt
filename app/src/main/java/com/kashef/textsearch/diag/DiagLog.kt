package com.kashef.textsearch.diag

import android.content.Context
import com.kashef.textsearch.engine.Diagnostics
import java.io.File

/** Small local log kept in the app's private storage. Nothing is ever sent automatically. */
object DiagLog {
    private const val MAX_LINES = 200
    private const val MAX_BYTES = 64 * 1024
    private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, "diagnostics.log")
    }

    @Synchronized
    fun log(tag: String, message: String, level: String = "I") {
        append(Diagnostics.formatLine(System.currentTimeMillis(), level, tag, message))
    }

    fun error(tag: String, e: Throwable) = log(tag, Diagnostics.describe(e), "E")

    fun crash(e: Throwable) = log("crash", Diagnostics.describe(e), "F")

    @Synchronized
    private fun append(line: String) {
        val f = file ?: return
        try {
            f.appendText(line + "\n", Charsets.UTF_8)
            if (f.length() > MAX_BYTES) {
                val kept = Diagnostics.trimLines(f.readLines(Charsets.UTF_8), MAX_LINES)
                f.writeText(kept.joinToString("\n") + "\n", Charsets.UTF_8)
            }
        } catch (e: Exception) {
            // diagnostics must never break the app
        }
    }

    @Synchronized
    fun lines(max: Int = 100): List<String> = try {
        val f = file
        if (f == null || !f.exists()) emptyList() else Diagnostics.trimLines(f.readLines(Charsets.UTF_8), max)
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun clear() {
        try { file?.delete() } catch (e: Exception) { /* ignore */ }
    }
}
