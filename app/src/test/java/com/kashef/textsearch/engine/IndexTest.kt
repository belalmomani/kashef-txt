package com.kashef.textsearch.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class IndexTest {
    private fun q(s: String, m: SearchMode = SearchMode.Phrase) = SearchQuery.create(s, m)!!

    /** Reads [text] the same way the app does, feeding the Bloom filter. */
    private fun bloomOf(text: String): TrigramBloom {
        val b = TrigramBloom.forSize(text.length.toLong())
        FileSearcher.collect(ByteArrayInputStream(text.toByteArray()), b) {}
        return b
    }

    @Test fun neverExcludesAFileThatContainsTheQuery() {
        val lines = listOf(
            "المشروع يحتاج إلى قاعدة بيانات",
            "The Database Server needs a restart, urgently!",
            "مَرحبًا   بالعالم — hello world 123",
        )
        val b = bloomOf(lines.joinToString("\n"))
        for (line in lines) {
            val norm = TextNormalizer.normalize(line).text
            for (start in 0..norm.length - 5 step 3) {
                val piece = norm.substring(start, start + 5)
                val query = SearchQuery.create(piece, SearchMode.Phrase) ?: continue
                assertTrue("lost: '$piece'", IndexRequirement.from(query).mayContain(b))
            }
        }
    }

    @Test fun excludesAFileThatClearlyLacksTheQuery() {
        val b = bloomOf("alpha beta gamma\nمرحبا بالعالم")
        assertFalse(IndexRequirement.from(q("quantum entanglement")).mayContain(b))
    }

    @Test fun shortQueriesAreNeverFiltered() {
        val empty = TrigramBloom.forSize(0)
        assertTrue(IndexRequirement.from(q("ab")).mayContain(empty))
        assertTrue(IndexRequirement.from(q("ab cd", SearchMode.AnyWord)).mayContain(empty))
    }

    @Test fun anyWordNeedsOnlyOneWord() {
        val b = bloomOf("alpha is here")
        assertTrue(IndexRequirement.from(q("alpha zzzzzz", SearchMode.AnyWord)).mayContain(b))
        assertFalse(IndexRequirement.from(q("yyyyyy zzzzzz", SearchMode.AnyWord)).mayContain(b))
    }

    @Test fun allWordsNeedsEveryWord() {
        val b = bloomOf("alpha is here")
        assertFalse(IndexRequirement.from(q("alpha zzzzzz", SearchMode.AllWords)).mayContain(b))
        assertTrue(IndexRequirement.from(q("alpha here", SearchMode.AllWords)).mayContain(b))
    }

    @Test fun bloomSurvivesStorageRoundTrip() {
        val b = bloomOf("storage round trip test")
        val back = TrigramBloom.fromBytes(b.bits, b.data.copyOf())
        assertNotNull(back)
        assertTrue(IndexRequirement.from(q("round trip")).mayContain(back!!))
        assertFalse(IndexRequirement.from(q("unrelated words")).mayContain(back))
    }

    @Test fun corruptedBloomSizeIsRejected() {
        assertEquals(null, TrigramBloom.fromBytes(4096, ByteArray(10)))
    }

    @Test fun indexKeepsReadingAfterTheMatchCapIsReached() {
        val text = (1..100).joinToString("\n") { if (it == 100) "match zebra" else "match" }
        val b = TrigramBloom.forSize(text.length.toLong())
        val out = FileSearcher.search(ByteArrayInputStream(text.toByteArray()), q("match"), 3, b) {}
        val done = out as FileOutcome.Done
        assertEquals(3, done.matches.size)
        assertTrue(done.truncated)
        // the last line was still fed to the index even though matching stopped early
        assertTrue(IndexRequirement.from(q("zebra")).mayContain(b))
    }

    @Test fun withoutAnIndexTheSearchStillStopsAtTheCap() {
        val text = (1..100).joinToString("\n") { "match $it" }
        val done = FileSearcher.search(ByteArrayInputStream(text.toByteArray()), q("match"), 3) {} as FileOutcome.Done
        assertEquals(3, done.matches.size)
        assertTrue(done.truncated)
    }

    @Test fun collectRejectsBinaryFiles() {
        val b = TrigramBloom.forSize(10)
        val out = FileSearcher.collect(ByteArrayInputStream(byteArrayOf(1, 0, 2, 0)), b) {}
        assertTrue(out is FileOutcome.Unreadable)
    }

    @Test fun oldRecordsWithoutTheIndexFieldStillLoad() {
        val r = CaseRecord(
            id = "1", query = "q", mode = "Phrase", scopeIndex = 0, scopeTitle = "t", folders = emptyList(),
            startedAt = 1, durationMs = 1, scanned = 1, matchedFiles = 0, matchCount = 0, problems = 0,
            unsupportedDocs = 0, otherSkipped = 0, limitReached = false, status = "finished", indexSkipped = 7,
        )
        val full = CaseCodec.encode(r)                       // 18 fields (current)
        val line17 = full.substringBeforeLast('\t')           // 17 fields (before flexible matching existed)
        val line16 = line17.substringBeforeLast('\t')         // 16 fields (before the index existed)
        assertEquals(7, CaseCodec.decode(full)!!.indexSkipped)
        assertEquals(7, CaseCodec.decode(line17)!!.indexSkipped)
        assertEquals(0, CaseCodec.decode(line16)!!.indexSkipped)
    }

    @Test fun reportExplainsIndexSkippingHonestly() {
        val r = CaseRecord(
            id = "1", query = "q", mode = "Phrase", scopeIndex = 0, scopeTitle = "t", folders = emptyList(),
            startedAt = 1, durationMs = 1000, scanned = 5, matchedFiles = 0, matchCount = 0, problems = 0,
            unsupportedDocs = 0, otherSkipped = 0, limitReached = false, status = "finished", indexSkipped = 40,
        )
        val text = CaseReport.toText(r)
        assertTrue(text.contains("استُبعد 40 ملفًا عبر الفهرس"))
        assertTrue(text.contains("حجم الملف وتاريخ تعديله"))
    }
}
