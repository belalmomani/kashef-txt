package com.kashef.textsearch.engine

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

sealed interface FileOutcome {
    /** [termsMask]: bit i set when query term i occurred somewhere in the file. */
    data class Done(val matches: List<MatchLine>, val truncated: Boolean, val termsMask: Int = 0) : FileOutcome
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

    /** Opens a text reader (UTF-8 / UTF-16 / windows-1256 fallback), or null for binary files. */
    private fun openReader(input: InputStream): InputStreamReader? {
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
        val charset = detectCharset(sample, len) ?: return null
        return InputStreamReader(bis, charset)
    }

    /**
     * Searches a stream. When [sink] is given the whole file is also read to the end (even after the
     * per-file match cap is reached) so the index gets every line.
     */
    fun search(
        input: InputStream,
        q: SearchQuery,
        maxMatches: Int,
        sink: LineSink? = null,
        checkCancel: () -> Unit,
    ): FileOutcome {
        val reader = openReader(input) ?: return FileOutcome.Unreadable("يبدو ملفًا ثنائيًا وليس نصًا")
        val matches = ArrayList<MatchLine>()
        val buf = CharArray(8192)
        val sb = StringBuilder()
        var lineNo = 1
        var truncated = false
        var chunks = 0
        var mask = 0
        var capped = false

        fun emit(): Boolean { // returns true when reading can stop
            val line = sb.toString()
            sb.setLength(0)
            if (capped && sink == null) return true
            val norm = TextNormalizer.normalize(line, q.fold)
            // The index is always built from FOLDED text, whatever the query's own setting.
            if (sink != null) sink.addNormalized(if (q.fold) norm.text else TextNormalizer.normalize(line, true).text)
            if (!capped) {
                val found = LineMatcher.find(line, q, norm)
                if (found != null) {
                    matches.add(MatchLine(lineNo, found.snippet, found.ranges))
                    mask = mask or found.termMask
                    if (matches.size >= maxMatches) {
                        truncated = true
                        capped = true
                    }
                }
            }
            return capped && sink == null
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
        if (sb.isNotEmpty() && !(capped && sink == null)) emit()
        return FileOutcome.Done(matches, truncated, mask)
    }

    /** Reads a whole file only to feed [sink] (used to build or refresh the index). Null reader = binary file. */
    fun collect(input: InputStream, sink: LineSink, checkCancel: () -> Unit): FileOutcome {
        val reader = openReader(input) ?: return FileOutcome.Unreadable("يبدو ملفًا ثنائيًا وليس نصًا")
        val buf = CharArray(8192)
        val sb = StringBuilder()
        var chunks = 0
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            chunks++
            if ((chunks and 15) == 0) checkCancel()
            for (k in 0 until n) {
                val ch = buf[k]
                if (ch == '\n') {
                    sink.addNormalized(TextNormalizer.normalize(sb.toString(), true).text)
                    sb.setLength(0)
                } else if (ch != '\r') {
                    sb.append(ch)
                    if (sb.length >= MAX_LINE) {
                        sink.addNormalized(TextNormalizer.normalize(sb.toString(), true).text)
                        sb.setLength(0)
                    }
                }
            }
        }
        if (sb.isNotEmpty()) sink.addNormalized(TextNormalizer.normalize(sb.toString(), true).text)
        return FileOutcome.Done(emptyList(), false, 0)
    }

    /**
     * Reads lines [fromLine]..[toLine] (1-based, same numbering as [search]) for the context viewer.
     * Very long lines are cut to [maxChars]. Returns null for binary files.
     */
    fun readWindow(input: InputStream, fromLine: Int, toLine: Int, maxChars: Int = 2000): List<Pair<Int, String>>? {
        val reader = openReader(input) ?: return null
        val out = ArrayList<Pair<Int, String>>()
        val buf = CharArray(8192)
        val sb = StringBuilder()
        var lineNo = 1
        var cut = false
        outer@ while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            for (k in 0 until n) {
                val ch = buf[k]
                if (ch == '\n') {
                    if (lineNo in fromLine..toLine) out.add(lineNo to (sb.toString() + if (cut) "…" else ""))
                    sb.setLength(0)
                    cut = false
                    lineNo++
                    if (lineNo > toLine) break@outer
                } else if (ch != '\r') {
                    if (sb.length < maxChars) sb.append(ch) else cut = true
                }
            }
        }
        if (lineNo in fromLine..toLine && sb.isNotEmpty()) out.add(lineNo to (sb.toString() + if (cut) "…" else ""))
        return out
    }
}
