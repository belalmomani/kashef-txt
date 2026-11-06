package com.kashef.textsearch.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MemoryTest {
    private fun rec(
        id: String = "1",
        query: String = "المشروع يحتاج إلى قاعدة بيانات",
        status: String = "finished",
        scanned: Int = 10,
        matched: Int = 2,
        unsupported: Int = 0,
        problems: Int = 0,
        limit: Boolean = false,
    ) = CaseRecord(
        id = id, query = query, mode = "Phrase", scopeIndex = 0, scopeTitle = "كل الملفات النصية المتاحة",
        folders = listOf("Documents/Notes", "Download"), startedAt = 1_700_000_000_000L, durationMs = 2500,
        scanned = scanned, matchedFiles = matched, matchCount = matched * 2, problems = problems,
        unsupportedDocs = unsupported, otherSkipped = 3, limitReached = limit, status = status,
    )

    @Test fun codecRoundTripKeepsTrickyCharacters() {
        val r = rec(query = "a\tb\nc\\d «عربي»")
        assertEquals(r, CaseCodec.decode(CaseCodec.encode(r)))
    }

    @Test fun damagedLineIsRejectedNotFatal() {
        assertNull(CaseCodec.decode("garbage"))
        assertNull(CaseCodec.decode(CaseCodec.encode(rec()).replace("\t10\t", "\tabc\t")))
    }

    @Test fun storeSavesLoadsAndSkipsDamagedLines() {
        val f = File.createTempFile("mem", ".tsv")
        try {
            val store = MemoryStore(f)
            store.save(listOf(rec(id = "a"), rec(id = "b")))
            f.appendText("\nbroken line\n")
            assertEquals(listOf("a", "b"), store.load().map { it.id })
            store.deleteAll()
            assertTrue(store.load().isEmpty())
        } finally {
            f.delete()
        }
    }

    @Test fun reportShowsTheRealNumbers() {
        val text = CaseReport.toText(rec(scanned = 41, matched = 3, problems = 2))
        assertTrue(text.contains("الملفات التي فُحصت فعلًا: 41"))
        assertTrue(text.contains("الملفات المطابقة: 3"))
        assertTrue(text.contains("الملفات التي تعذرت قراءتها: 2"))
        assertTrue(text.contains("2.5 ثانية"))
    }

    @Test fun reportNeverClaimsEverythingWasScanned() {
        val text = CaseReport.toText(rec())
        assertFalse(text.contains("تم فحص كل الملفات"))
    }

    @Test fun cancelledOrLimitedSearchIsNotReportedAsComplete() {
        assertFalse(CaseReport.toText(rec(status = "cancelled")).contains("اكتمل المرور"))
        assertFalse(CaseReport.toText(rec(limit = true)).contains("اكتمل المرور"))
        assertTrue(CaseReport.toText(rec(status = "finished")).contains("اكتمل المرور"))
    }

    @Test fun noResultReportExplainsKnownLimits() {
        val text = CaseReport.toText(rec(matched = 0, unsupported = 4, problems = 1))
        assertTrue(text.contains("4 ملفًا من نوع PDF"))
        assertTrue(text.contains("1 ملفًا تعذرت قراءته"))
        assertTrue(text.contains("لم تُوجد العبارة في أي ملف فُحص"))
    }
}
