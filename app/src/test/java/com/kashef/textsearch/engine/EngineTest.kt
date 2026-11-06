package com.kashef.textsearch.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class EngineTest {
    private fun q(s: String, m: SearchMode = SearchMode.Phrase) = SearchQuery.create(s, m)!!
    private fun run(bytes: ByteArray, query: SearchQuery, max: Int = 50) =
        FileSearcher.search(ByteArrayInputStream(bytes), query, max) {}
    private fun done(o: FileOutcome) = o as FileOutcome.Done

    @Test fun arabicPhraseFoundWithLineNumber() {
        val text = "السطر الأول\nلا شيء هنا\nالمشروع يحتاج إلى قاعدة بيانات\n"
        val r = done(run(text.toByteArray(Charsets.UTF_8), q("المشروع يحتاج إلى قاعدة بيانات")))
        assertEquals(1, r.matches.size)
        assertEquals(3, r.matches[0].line)
    }

    @Test fun diacriticsAreIgnored() {
        val text = "المَشروعُ يحتاجُ إلى قاعدةِ بيانات"
        val r = done(run(text.toByteArray(), q("المشروع يحتاج إلى قاعدة بيانات")))
        assertEquals(1, r.matches.size)
    }

    @Test fun englishCaseInsensitive() {
        val r = done(run("The Database Server\n".toByteArray(), q("database server")))
        assertEquals(1, r.matches.size)
    }

    @Test fun extraSpacesCountAsOne() {
        val r = done(run("المشروع    يحتاج\tقاعدة".toByteArray(), q("المشروع يحتاج قاعدة")))
        assertEquals(1, r.matches.size)
    }

    @Test fun punctuationMatters() {
        val r = done(run("database, server".toByteArray(), q("database server")))
        assertEquals(0, r.matches.size)
    }

    @Test fun allWordsMustShareALine() {
        val text = "alpha beta\nalpha\nbeta gamma"
        val r = done(run(text.toByteArray(), q("alpha beta", SearchMode.AllWords)))
        assertEquals(listOf(1), r.matches.map { it.line })
    }

    @Test fun anyWordMatchesEachLine() {
        val text = "alpha beta\nalpha\nbeta gamma\nnothing"
        val r = done(run(text.toByteArray(), q("alpha beta", SearchMode.AnyWord)))
        assertEquals(listOf(1, 2, 3), r.matches.map { it.line })
    }

    @Test fun binaryFileIsRejected() {
        val bytes = byteArrayOf(0x50, 0x4B, 0x00, 0x01, 0x02, 0x00)
        assertTrue(run(bytes, q("x")) is FileOutcome.Unreadable)
    }

    @Test fun utf16WithBomIsSupported() {
        val body = "مرحبا بالعالم".toByteArray(Charsets.UTF_16LE)
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + body
        val r = done(run(bytes, q("بالعالم")))
        assertEquals(1, r.matches.size)
    }

    @Test fun windows1256FallbackWorks() {
        val bytes = "المشروع الجديد".toByteArray(Charset.forName("windows-1256"))
        val r = done(run(bytes, q("المشروع")))
        assertEquals(1, r.matches.size)
    }

    @Test fun emptyFileHasNoMatches() {
        val r = done(run(ByteArray(0), q("x")))
        assertEquals(0, r.matches.size)
    }

    @Test fun highlightRangeMapsToOriginalText() {
        val line = "المَشروع"
        val found = LineMatcher.find(line, q("المشروع"))
        assertNotNull(found)
        assertEquals(0..7, found!!.ranges[0])
    }

    @Test fun perFileCapTruncates() {
        val text = (1..100).joinToString("\n") { "x" }
        val r = done(run(text.toByteArray(), q("x"), max = 10))
        assertEquals(10, r.matches.size)
        assertTrue(r.truncated)
    }

    @Test fun blankQueryIsRejected() {
        assertNull(SearchQuery.create("  \u064E  ", SearchMode.Phrase))
    }

    @Test fun termsMaskCountsDistinctWordsAcrossLines() {
        val text = "alpha here\nnothing\nbeta there\n"
        val r = done(run(text.toByteArray(), q("alpha beta gamma", SearchMode.AnyWord)))
        assertEquals(2, Integer.bitCount(r.termsMask))
    }

    @Test fun readWindowReturnsRequestedLinesWithSameNumbering() {
        val text = (1..10).joinToString("\n") { "line $it" }
        val w = FileSearcher.readWindow(ByteArrayInputStream(text.toByteArray()), 4, 6)!!
        assertEquals(listOf(4, 5, 6), w.map { it.first })
        assertEquals("line 5", w[1].second)
    }

    @Test fun readWindowOnBinaryIsNull() {
        val bytes = byteArrayOf(1, 0, 2, 0)
        assertNull(FileSearcher.readWindow(ByteArrayInputStream(bytes), 1, 3))
    }

    @Test fun rangesCoverWholeLine() {
        val line = "x ".repeat(100) + "needle"
        val r = LineMatcher.ranges(line, q("needle"))
        assertEquals(1, r.size)
        assertEquals(line.length - 6, r[0].first)
    }

    private fun fr(name: String, terms: Int, lines: Int, modified: Long, ext: String = "txt", root: String = "A") =
        FileResult(
            name = name, path = "$root/$name",
            matches = (1..lines).map { MatchLine(it, "x", emptyList()) },
            truncated = false, termsMatched = terms, termsTotal = 3,
            modified = modified, ext = ext, root = root,
        )

    @Test fun relevanceRanksMoreWordsFirstThenMoreLines() {
        val a = fr("a.txt", terms = 2, lines = 1, modified = 1)
        val b = fr("b.txt", terms = 1, lines = 10, modified = 2)
        val c = fr("c.txt", terms = 2, lines = 5, modified = 3)
        val r = ResultOps.apply(listOf(a, b, c), Filters(), SortMode.Relevance)
        assertEquals(listOf("c.txt", "a.txt", "b.txt"), r.map { it.name })
    }

    @Test fun newestSortUsesModifiedTime() {
        val r = ResultOps.apply(listOf(fr("a", 1, 1, 10), fr("b", 1, 1, 30), fr("c", 1, 1, 20)), Filters(), SortMode.Newest)
        assertEquals(listOf("b", "c", "a"), r.map { it.name })
    }

    @Test fun filtersByTypeFolderAndDate() {
        val list = listOf(
            fr("a.txt", 1, 1, 100, ext = "txt", root = "A"),
            fr("b.md", 1, 1, 200, ext = "md", root = "A"),
            fr("c.txt", 1, 1, 300, ext = "txt", root = "B"),
            fr("d.txt", 1, 1, 0, ext = "txt", root = "A"),
        )
        assertEquals(setOf("a.txt", "c.txt", "d.txt"), ResultOps.apply(list, Filters(exts = setOf("txt")), SortMode.Name).map { it.name }.toSet())
        assertEquals(setOf("c.txt"), ResultOps.apply(list, Filters(roots = setOf("B")), SortMode.Name).map { it.name }.toSet())
        // files without a known date are excluded when a date filter is active
        assertEquals(setOf("b.md", "c.txt"), ResultOps.apply(list, Filters(sinceMs = 150), SortMode.Name).map { it.name }.toSet())
    }
}
