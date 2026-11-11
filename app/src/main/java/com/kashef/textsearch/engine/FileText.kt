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
 * Turns a file into a plain-text stream. Plain files pass through unchanged. DOCX, ODT, XLSX and PPTX
 * are zipped XML packages whose text is stored in logical reading order, so they are converted to text:
 * one paragraph (DOCX/ODT/PPTX) or one non-empty row (XLSX) becomes one "line".
 * PDF is NOT supported: Arabic text extraction from PDF is unreliable.
 */
object FileText {
    val OFFICE_EXT = setOf("docx", "odt", "xlsx", "pptx")
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

    private fun wanted(ext: String, name: String): Boolean = when (ext) {
        "docx" -> name == "word/document.xml" || name == "word/footnotes.xml" || name == "word/endnotes.xml"
        "odt" -> name == "content.xml"
        "xlsx" -> name == "xl/sharedStrings.xml" || (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
        "pptx" -> name.startsWith("ppt/slides/slide") && name.endsWith(".xml")
        else -> false
    }

    private fun extract(input: InputStream, ext: String, max: Int): String? {
        // Parts are read first, because in XLSX the shared strings often come AFTER the sheets that use them.
        val parts = LinkedHashMap<String, String>()
        var budget = max
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !wanted(ext, entry.name)) continue
                val bytes = readCapped(zip, budget)
                budget -= bytes.size
                parts[entry.name] = String(bytes, Charsets.UTF_8)
            }
        }
        if (parts.isEmpty()) return null
        val text = when (ext) {
            "docx" -> parts.values.joinToString("") { docxText(it) }
            "odt" -> parts.values.joinToString("") { odtText(it) }
            "xlsx" -> xlsxText(parts)
            else -> pptxText(parts)
        }
        return text.trimEnd('\n')
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

    private fun numberIn(name: String): Int =
        Regex("(\\d+)\\.xml$").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    // ---------------- DOCX ----------------

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

    // ---------------- ODT ----------------

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

    // ---------------- XLSX ----------------

    private val SHARED_ITEM = Regex("<si\\s*/>|<si(?:\\s[^>]*)?>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
    private val PHONETIC = Regex("<rPh[\\s>].*?</rPh>", RegexOption.DOT_MATCHES_ALL)
    private val T_TAG = Regex("<t(?:\\s[^>]*)?>([^<]*)</t>")
    // The self-closing form is tried first, so <row r="3"/> can never swallow the rows after it.
    private val ROW = Regex("<row(?:\\s[^>]*)?/>|<row(?:\\s[^>]*)?>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
    private val CELL = Regex("<c(?:\\s([^>]*?))?(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
    private val CELL_TYPE = Regex("\\bt=\"([^\"]*)\"")
    private val CELL_VALUE = Regex("<v(?:\\s[^>]*)?>([^<]*)</v>")

    /** Shared strings by index (an empty item still occupies its index). Rich-text runs are joined. */
    fun sharedStrings(xml: String): List<String> =
        SHARED_ITEM.findAll(xml).map { m ->
            val body = PHONETIC.replace(m.groups[1]?.value ?: "", "")
            T_TAG.findAll(body).joinToString("") { unescape(it.groupValues[1]) }
        }.toList()

    /** One line per non-empty row; the cells of a row are separated by a tab. Sheets follow each other. */
    fun xlsxText(parts: Map<String, String>): String {
        val shared = parts["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        val sheets = parts.keys.filter { it.startsWith("xl/worksheets/sheet") }.sortedBy { numberIn(it) }
        val sb = StringBuilder()
        for (name in sheets) sb.append(sheetText(parts.getValue(name), shared))
        return sb.toString()
    }

    fun sheetText(xml: String, shared: List<String>): String {
        val sb = StringBuilder()
        for (row in ROW.findAll(xml)) {
            val body = row.groups[1]?.value ?: continue // a self-closing row is empty
            val cells = ArrayList<String>()
            for (c in CELL.findAll(body)) {
                val attrs = c.groupValues[1]
                val inner = c.groupValues[2]
                val type = CELL_TYPE.find(attrs)?.groupValues?.get(1) ?: ""
                val text = when (type) {
                    "s" -> CELL_VALUE.find(inner)?.groupValues?.get(1)?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> T_TAG.findAll(inner).joinToString("") { unescape(it.groupValues[1]) }
                    "e" -> "" // formula errors are not text
                    else -> CELL_VALUE.find(inner)?.let { unescape(it.groupValues[1]) } ?: ""
                }
                if (text.isNotEmpty()) cells.add(text)
            }
            if (cells.isNotEmpty()) sb.append(cells.joinToString("\t")).append('\n')
        }
        return sb.toString()
    }

    // ---------------- PPTX ----------------

    private val PPT_TOKEN = Regex("<a:t(?:\\s[^>]*)?>([^<]*)</a:t>|<a:br\\s*/?>|</a:p>")

    /** PowerPoint: slides in numeric order, one paragraph per line. */
    fun pptxText(parts: Map<String, String>): String {
        val slides = parts.keys.filter { it.startsWith("ppt/slides/slide") }.sortedBy { numberIn(it) }
        val sb = StringBuilder()
        for (name in slides) sb.append(slideText(parts.getValue(name)))
        return sb.toString()
    }

    fun slideText(xml: String): String {
        val sb = StringBuilder()
        val line = StringBuilder()
        for (m in PPT_TOKEN.findAll(xml)) {
            val tok = m.value
            if (tok.startsWith("<a:t>") || tok.startsWith("<a:t ")) {
                line.append(unescape(m.groupValues[1]))
            } else { // end of paragraph or a line break
                sb.append(line).append('\n')
                line.setLength(0)
            }
        }
        if (line.isNotEmpty()) sb.append(line).append('\n')
        return sb.toString()
    }

    // ---------------- shared ----------------

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
