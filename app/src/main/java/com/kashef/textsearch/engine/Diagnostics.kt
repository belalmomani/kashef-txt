package com.kashef.textsearch.engine

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure helpers for the diagnostics log. The log is meant to be shared with the developer, so it must
 * never contain file names, paths, file contents or search phrases.
 */
object Diagnostics {
    private val URI = Regex("(?:content|file|https?)://\\S+")
    private val PATH = Regex("(?:/[\\w.\\-]+){2,}")

    /** Best-effort removal of URIs and file paths from free text. */
    fun redact(s: String): String = PATH.replace(URI.replace(s, "<uri>"), "<path>")

    /**
     * Exception type, the types of its causes, and the app's OWN stack frames. The exception MESSAGE is
     * deliberately left out, because messages often contain file names.
     */
    fun describe(e: Throwable): String {
        val sb = StringBuilder(e.javaClass.name)
        var cause = e.cause
        var depth = 0
        while (cause != null && depth < 2) {
            sb.append(" <- ").append(cause.javaClass.name)
            cause = cause.cause
            depth++
        }
        val frames = e.stackTrace.filter { it.className.startsWith("com.kashef") }.take(4)
        if (frames.isNotEmpty()) {
            sb.append(" @ ").append(
                frames.joinToString(" > ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" },
            )
        }
        return sb.toString()
    }

    fun formatLine(timeMs: Long, level: String, tag: String, message: String): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timeMs))
        return "$time $level $tag " + redact(message).replace('\n', ' ').take(300)
    }

    fun trimLines(lines: List<String>, max: Int): List<String> = if (lines.size <= max) lines else lines.takeLast(max)
}
