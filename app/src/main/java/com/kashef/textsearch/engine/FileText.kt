package com.kashef.textsearch.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

sealed interface TextOpen {
    class Ready(val stream: InputStream) : TextOpen
    class Failed(val reason: String) : TextOpen
}

/**
 * Turns a file into a plain-text stream. Plain files pass through unchanged. DOCX and ODT files are
 * converted paragraph by paragraph (one paragraph = one "line"), in logical reading order, which keeps
 * Arabic text reliable. PDF is NOT supported: Arabic text extraction from PDF is unreliable.
 */
object FileText {
    val OFFICE_EXT = setOf("docx", "odt")
    const val MAX_XML_BYTES = 64 * 1024 * 1024

    private class TooBig : Exception()

    fun open(input: InputStream, ext: String, maxXmlBytes: Int = MAX_XML_BYTES): TextOpen {
        if (ext !in OFFICE_EXT) return TextOpen.Ready(input)
        val text: String? = try {
            extract(input, ext, maxXmlBytes)
        } catch (e: TooBig) {
            return TextOpen.Failed("محتوى المستند بعد فكّه كبير جدًا")
        } catch (e: IOException) {
            return TextOpen.Failed("تعذرت قراءة المستند (تالف أو مشفّر)")
        }
        if (text == null) return TextOpen.Failed("ملف ${ext.uppercase()} غير صالح أو لا يحتوي نصًا قابلًا للقراءة")
        return TextOpen.Ready(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
    }

    private fun extract(input: InputStream, ext: String, max: Int): String? {
        fun wanted(name: String): Boolean =
            if (ext == "docx") name == "word/document.xml" || name == "word/footnotes.xml" || name == "word/endnotes.xml"
            else name == "content.xml"
        val out = StringBuilder()
        var found = false
        var budget = max
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !wanted(entry.name)) continue
                found = true
                val bytes = readCapped(zip, budget)
                budget -= bytes.size
                val xml = String(bytes, Charsets.UTF_8)
                out.append(if (ext == "docx") docxText(xml) else odtText(xml))
            }
        }
        return if (found) out.toString().trimEnd('\n') else null
    }

    private fun readCapped(zip: ZipInputStream, budget: Int): ByteArray {
        val buf = ByteArray(16 * 1024)
        val bos = ByteArrayOutputStream()
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            bos.write(buf, 0, n)
            if (bos.size() > budget) throw TooBig() // guards against zip bombs
        }
        return bos.toByteArray()
    }

    private val DOCX_TOKEN = Regex("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|<w:tab\\s*/>|<w:br\\s*/?>|<w:cr\\s*/?>|</w:p>")

    /** Word: text of each run is concatenated per paragraph; tabs and line breaks are kept. */
    fun docxText(xml: String): String {
        val sb = StringBuilder()
        val line = StringBuilder()
        for (m in DOCX_TOKEN.findAll(xml)) {
            val tok = m.value
            if (tok.startsWith("<w:t>") || tok.startsWith("<w:t ")) {
                line.append(unescape(m.groupValues[1]))
            } else if (tok.startsWith("<w:tab")) {
                line.append('\t')
            } else { // end of paragraph or a line break
                sb.append(line).append('\n')
                line.setLength(0)
            }
        }
        if (line.isNotEmpty()) sb.append(line).append('\n')
        return sb.toString()
    }

    private val ODT_TOKEN = Regex("</text:p>|</text:h>|<text:line-break\\s*/>|<text:tab\\s*/>|<text:s(?:\\s+text:c=\"(\\d+)\")?\\s*/>|<[^>]+>|([^<]+)")

    /** OpenDocument: only text inside paragraphs and headings is kept. */
    fun odtText(xml: String): String {
        val sb = StringBuilder()
        val line = StringBuilder()
        var inPara = false
        for (m in ODT_TOKEN.findAll(xml)) {
            val tok = m.value
            if (tok == "</text:p>" || tok == "</text:h>") {
                sb.append(line).append('\n')
                line.setLength(0)
                inPara = false
            } else if (tok.startsWith("<text:line-break")) {
                sb.append(line).append('\n')
                line.setLength(0)
            } else if (tok.startsWith("<text:tab/") || tok.startsWith("<text:tab ")) {
                line.append('\t')
            } else if (tok == "<text:s/>" || tok.startsWith("<text:s ")) {
                val count = m.groups[1]?.value?.toIntOrNull() ?: 1
                repeat(count.coerceIn(1, 1000)) { line.append(' ') }
            } else if (tok.startsWith("<")) {
                if (!tok.endsWith("/>") && (tok == "<text:p>" || tok.startsWith("<text:p ") || tok == "<text:h>" || tok.startsWith("<text:h "))) {
                    inPara = true
                }
            } else if (inPara) {
                line.append(unescape(tok))
            }
        }
        if (line.isNotEmpty()) sb.append(line).append('\n')
        return sb.toString()
    }

    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);")

    fun unescape(s: String): String {
        if ('&' !in s) return s
        return ENTITY.replace(s) { m ->
            val g = m.groupValues[1]
            when (g) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> try {
                    val cp = if (g[1] == 'x' || g[1] == 'X') g.substring(2).toInt(16) else g.substring(1).toInt()
                    String(Character.toChars(cp))
                } catch (e: Exception) {
                    m.value
                }
            }
        }
    }
}
