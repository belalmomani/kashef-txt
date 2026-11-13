package com.kashef.textsearch.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The diagnostics log is meant to be shared, so it must never leak names, paths or contents. */
class DiagTest {
    @Test fun redactRemovesUrisAndPaths() {
        val s = Diagnostics.redact(
            "failed content://com.android.externalstorage.documents/tree/primary%3ADocuments and /storage/emulated/0/Private/a.txt end",
        )
        assertFalse(s.contains("externalstorage"))
        assertFalse(s.contains("Private"))
        assertTrue(s.contains("<uri>"))
        assertTrue(s.contains("<path>"))
        assertTrue(s.contains("failed") && s.contains("end"))
    }

    @Test fun describeNeverIncludesTheExceptionMessage() {
        val e = IllegalStateException("secret /storage/emulated/0/Private/report.txt", RuntimeException("inner message"))
        val d = Diagnostics.describe(e)
        assertTrue(d.contains("java.lang.IllegalStateException"))
        assertTrue(d.contains("java.lang.RuntimeException"))
        assertTrue(d.contains("DiagTest"))   // the app's own frame is kept
        assertFalse(d.contains("secret"))
        assertFalse(d.contains("Private"))
        assertFalse(d.contains("inner message"))
    }

    @Test fun formattedLineIsOneRedactedLine() {
        val line = Diagnostics.formatLine(0L, "E", "search", "line one\nline two content://x/y /a/b/c")
        assertFalse(line.contains('\n'))
        assertFalse(line.contains("content://"))
        assertTrue(line.contains(" E search "))
    }

    @Test fun longMessagesAreCut() {
        assertTrue(Diagnostics.formatLine(0L, "I", "t", "x".repeat(5000)).length < 400)
    }

    @Test fun trimKeepsTheNewestLines() {
        assertEquals(listOf("c", "d"), Diagnostics.trimLines(listOf("a", "b", "c", "d"), 2))
        assertEquals(listOf("a"), Diagnostics.trimLines(listOf("a"), 5))
    }

    private fun fr(name: String, path: String, snippet: String) = FileResult(
        name = name, path = path, matches = listOf(MatchLine(3, snippet, emptyList())),
        truncated = false, termsMatched = 1, termsTotal = 1, modified = 1, ext = "txt", root = "A",
    )

    @Test fun narrowingMatchesNameOrVisibleSnippetIgnoringCaseAndDiacritics() {
        val list = listOf(
            fr("budget.txt", "A/budget.txt", "الميزانية السنوية"),
            fr("notes.txt", "A/notes.txt", "خطة المَشروع الجديد"),
            fr("other.txt", "A/other.txt", "شيء آخر"),
        )
        fun names(text: String) = ResultOps.apply(list, Filters(text = text), SortMode.Name).map { it.name }
        assertEquals(listOf("budget.txt"), names("BUDGET"))      // file name, case-insensitive
        assertEquals(listOf("notes.txt"), names("المشروع"))      // snippet, diacritics ignored
        assertEquals(3, names("").size)                           // empty text keeps everything
        assertEquals(emptyList<String>(), names("غير موجود"))
    }

    @Test fun exportListsRankNamePathAndLines() {
        val text = ResultOps.toText(listOf(fr("a.txt", "A/a.txt", "مقتطف")), "عبارة")
        assertTrue(text.contains("«عبارة»"))
        assertTrue(text.contains("#1 a.txt"))
        assertTrue(text.contains("A/a.txt"))
        assertTrue(text.contains("سطر 3: مقتطف"))
    }
}
