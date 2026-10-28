package com.kashef.textsearch.engine

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

sealed interface FileOutcome {
    data class Done(val matches: List<MatchLine>, val truncated: Boolean) : FileOutcome
    data class Unreadable(val reason: String) : FileOutcome
}

/** Pure JVM text search over a stream, so it can be unit-tested without Android. */
object FileSearcher {
    private const val SAMPLE = 8192
    private const val MAX_LINE = 100_000

    private fun detectCharset(sample: ByteArray, len: Int): Charset? {
        if (len >= 2 && sample[0] == 0xFF.toByte() && sample[1] == 0xFE.toByte()) return Charsets.UTF_16LE
        if (len >= 2 && sample[0] == 0xFE.toByte() && sample[1] == 0xFF.toByte()) return Charsets.UTF_16BE
        for (i in 0 until len) if (sample[i] == 0.toByte()) return null // NUL byte: binary file
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        // endOfInput=false so a multi-byte character cut at the sample border is not an error
        val res = decoder.decode(ByteBuffer.wrap(sample, 0, len), CharBuffer.allocate(len + 8), false)
        if (!res.isError) return Charsets.UTF_8
        return try { Charset.forName("windows-1256") } catch (e: Exception) { Charsets.ISO_8859_1 }
    }

    fun search(input: InputStream, q: SearchQuery, maxMatches: Int, checkCancel: () -> Unit): FileOutcome {
        val bis = BufferedInputStream(input, 16 * 1024)
        bis.mark(SAMPLE + 16)
        val sample = ByteArray(SAMPLE)
        var len = 0
        while (len < SAMPLE) {
            val r = bis.read(sample, len, SAMPLE - len)
            if (r < 0) break
            len += r
        }
        bis.reset()
        val charset = detectCharset(sample, len)
            ?: return FileOutcome.Unreadable("يبدو ملفًا ثنائيًا وليس نصًا")

        val reader = InputStreamReader(bis, charset)
        val matches = ArrayList<MatchLine>()
        val buf = CharArray(8192)
        val sb = StringBuilder()
        var lineNo = 1
        var truncated = false
        var chunks = 0

        fun emit(): Boolean { // returns true when the per-file cap is reached
            val line = sb.toString()
            sb.setLength(0)
            val found = LineMatcher.find(line, q)
            if (found != null) {
                matches.add(MatchLine(lineNo, found.first, found.second))
                if (matches.size >= maxMatches) {
                    truncated = true
                    return true
                }
            }
            return false
        }

        outer@ while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            chunks++
            if ((chunks and 15) == 0) checkCancel()
            for (k in 0 until n) {
                val ch = buf[k]
                if (ch == '\n') {
                    if (emit()) break@outer
                    lineNo++
                } else if (ch != '\r') {
                    sb.append(ch)
                    if (sb.length >= MAX_LINE && emit()) break@outer
                }
            }
        }
        if (!truncated && sb.isNotEmpty()) emit()
        return FileOutcome.Done(matches, truncated)
    }
}
