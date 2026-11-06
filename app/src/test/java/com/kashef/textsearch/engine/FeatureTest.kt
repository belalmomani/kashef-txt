package com.kashef.textsearch.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** DOCX/ODT reading and flexible (letter-folding) matching. */
class FeatureTest {
    private val phrase = "المشروع يحتاج إلى قاعدة بيانات"

    private fun q(s: String, m: SearchMode = SearchMode.Phrase, fold: Boolean = false) = SearchQuery.create(s, m, fold)!!

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, content) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun searchOffice(bytes: ByteArray, ext: String, query: SearchQuery): List<Int> {
        val opened = FileText.open(ByteArrayInputStream(bytes), ext)
        assertTrue(opened is TextOpen.Ready)
        val out = FileSearcher.search((opened as TextOpen.Ready).stream, query, 50) {} as FileOutcome.Done
        return out.matches.map { it.line }
    }

    private val docx = zip(
        "word/document.xml" to (
            "<w:document><w:body>" +
                "<w:p><w:r><w:t>المشروع </w:t></w:r><w:r><w:t xml:space=\"preserve\">يحتاج إلى</w:t></w:r><w:r><w:t> قاعدة بيانات</w:t></w:r></w:p>" +
                "<w:p><w:pPr><w:tabs><w:tab w:val=\"left\" w:pos=\"720\"/></w:tabs></w:pPr><w:r><w:t>A &amp; B</w:t></w:r><w:r><w:tab/></w:r><w:r><w:t>x &lt; y &#1575;</w:t></w:r></w:p>" +
                "<w:p><w:del><w:r><w:delText>deleted words</w:delText></w:r></w:del></w:p>" +
                "</w:body></w:document>"
            ),
    )

    @Test fun docxRunsAreJoinedIntoOneParagraph() {
        assertEquals(listOf(1), searchOffice(docx, "docx", q(phrase)))
    }

    @Test fun docxEntitiesAndTabsAreDecoded() {
        assertEquals(listOf(2), searchOffice(docx, "docx", q("a & b")))
        assertEquals(listOf(2), searchOffice(docx, "docx", q("x < y")))
        assertTrue(FileText.docxText("<w:p><w:r><w:t>a</w:t></w:r><w:r><w:tab/></w:r><w:r><w:t>b</w:t></w:r></w:p>").startsWith("a\tb"))
    }

    @Test fun docxTrackedDeletionsAreNotSearched() {
        assertEquals(emptyList<Int>(), searchOffice(docx, "docx", q("deleted words")))
    }

    private val odt = zip(
        "content.xml" to (
            "<office:document-content><office:body><office:text>" +
                "<text:p text:style-name=\"P1\">المشروع <text:span text:style-name=\"T1\">يحتاج</text:span><text:s text:c=\"1\"/>إلى قاعدة بيانات</text:p>" +
                "<text:p>second<text:tab/>line<text:line-break/>third &amp; last</text:p>" +
                "<text:table><table:table-cell>ignored outside paragraph</table:table-cell></text:table>" +
                "</office:text></office:body></office:document-content>"
            ),
    )

    @Test fun odtParagraphsSpansAndSpacesAreHandled() {
        assertEquals(listOf(1), searchOffice(odt, "odt", q(phrase)))
        assertEquals(listOf(3), searchOffice(odt, "odt", q("third & last")))
        assertEquals(emptyList<Int>(), searchOffice(odt, "odt", q("ignored outside")))
    }

    @Test fun notAZipIsReportedNotCrashed() {
        val r = FileText.open(ByteArrayInputStream("this is not a zip".toByteArray()), "docx")
        assertTrue(r is TextOpen.Failed)
    }

    @Test fun zipWithoutTheDocumentPartIsRejected() {
        val r = FileText.open(ByteArrayInputStream(zip("other.xml" to "<a/>")), "docx")
        assertTrue(r is TextOpen.Failed)
    }

    @Test fun hugeDecompressedContentIsRefused() {
        val big = zip("word/document.xml" to "<w:p><w:r><w:t>" + "x".repeat(5000) + "</w:t></w:r></w:p>")
        val r = FileText.open(ByteArrayInputStream(big), "docx", maxXmlBytes = 1000)
        assertTrue(r is TextOpen.Failed)
    }

    @Test fun plainFilesPassThroughUnchanged() {
        val src = ByteArrayInputStream("plain".toByteArray())
        val r = FileText.open(src, "txt")
        assertTrue(r is TextOpen.Ready && (r as TextOpen.Ready).stream === src)
    }

    @Test fun unescapeHandlesNamedAndNumericEntities() {
        assertEquals("a & b < c > d \" ' ا", FileText.unescape("a &amp; b &lt; c &gt; d &quot; &apos; &#1575;"))
        assertEquals("&#zzz;", FileText.unescape("&#zzz;"))
    }

    // ---- flexible matching ----

    @Test fun foldingMatchesTaMarbutaAndAlefVariantsOnlyWhenOn() {
        val text = "المدرسة الجديدة\nأحمد في البيت"
        fun lines(query: SearchQuery) =
            (FileSearcher.search(ByteArrayInputStream(text.toByteArray()), query, 50) {} as FileOutcome.Done).matches.map { it.line }
        assertEquals(listOf(1), lines(q("مدرسه", fold = true)))
        assertEquals(emptyList<Int>(), lines(q("مدرسه", fold = false)))
        assertEquals(listOf(2), lines(q("احمد", fold = true)))
        assertEquals(emptyList<Int>(), lines(q("احمد", fold = false)))
    }

    @Test fun foldingKeepsHighlightPositionsOnTheOriginalText() {
        val hit = LineMatcher.find("المدرسة", q("مدرسه", fold = true))!!
        assertEquals(2..6, hit.ranges[0])
    }

    @Test fun indexIsBuiltFoldedSoFlexibleQueriesAreNeverExcluded() {
        val bloom = TrigramBloom.forSize(200)
        FileSearcher.collect(ByteArrayInputStream("مدرسة جميلة".toByteArray()), bloom) {}
        assertTrue(IndexRequirement.from(q("مدرسه", fold = true)).mayContain(bloom))
        assertTrue(IndexRequirement.from(q("مدرسة", fold = false)).mayContain(bloom))
        assertFalse(IndexRequirement.from(q("quantum entanglement", fold = true)).mayContain(bloom))
    }

    @Test fun searchWithSinkFeedsFoldedTextEvenForStrictQueries() {
        val bloom = TrigramBloom.forSize(200)
        FileSearcher.search(ByteArrayInputStream("أحمد وصل".toByteArray()), q("xyz"), 50, bloom) {}
        assertTrue(IndexRequirement.from(q("احمد", fold = true)).mayContain(bloom))
    }

    @Test fun recordRoundTripKeepsTheFlexibleFlag() {
        val r = CaseRecord(
            id = "1", query = "q", mode = "AllWords", scopeIndex = 1, scopeTitle = "t", folders = listOf("a"),
            startedAt = 5, durationMs = 6, scanned = 1, matchedFiles = 1, matchCount = 1, problems = 0,
            unsupportedDocs = 0, otherSkipped = 0, limitReached = false, status = "finished", indexSkipped = 0, fold = true,
        )
        assertEquals(r, CaseCodec.decode(CaseCodec.encode(r)))
        assertTrue(CaseReport.toText(r).contains("مطابقة مرنة"))
    }
}
