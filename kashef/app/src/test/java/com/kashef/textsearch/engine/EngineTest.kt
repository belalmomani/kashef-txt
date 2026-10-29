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
        assertEquals(0..7, found!!.second[0])
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
}
