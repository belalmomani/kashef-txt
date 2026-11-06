package com.kashef.textsearch.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Random

/** Hard cases: very long lines, damaged encodings, big files, cancellation, index safety. */
class RobustnessTest {
    private val phrase = "المشروع يحتاج إلى قاعدة بيانات"
    private fun q(s: String, m: SearchMode = SearchMode.Phrase) = SearchQuery.create(s, m)!!
    private fun run(bytes: ByteArray, query: SearchQuery, max: Int = 50) =
        FileSearcher.search(ByteArrayInputStream(bytes), query, max) {} as FileOutcome.Done

    @Test fun veryLongLineKeepsLineNumbersAndFindsTheEnd() {
        val text = "a".repeat(250_000) + " $phrase\nsecond $phrase"
        val r = run(text.toByteArray(), q(phrase))
        assertEquals(listOf(1, 2), r.matches.map { it.line })
    }

    @Test fun truncatedUtf8AtTheEndDoesNotCrash() {
        val bytes = "hello needle\n".toByteArray() + byteArrayOf(0xD8.toByte()) // a cut multi-byte character
        assertEquals(listOf(1), run(bytes, q("needle")).matches.map { it.line })
    }

    @Test fun invalidUtf8InTheMiddleStillSearchable() {
        val bytes = "needle ".toByteArray() + byteArrayOf(0xE1.toByte(), 0xC7.toByte()) + " end\nneedle again".toByteArray()
        assertEquals(listOf(1, 2), run(bytes, q("needle")).matches.map { it.line })
    }

    @Test fun cancellationPropagatesOutOfALargeRead() {
        val bytes = "x\n".repeat(100_000).toByteArray()
        try {
            FileSearcher.search(ByteArrayInputStream(bytes), q("zzz"), 50) { throw IllegalStateException("cancelled") }
            fail("cancellation was swallowed")
        } catch (e: IllegalStateException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test fun largeFileFindsPlantedPhrasesExactly() {
        val sb = StringBuilder()
        for (i in 1..150_000) {
            sb.append(if (i == 12_345 || i == 149_999) "log entry $phrase" else "lorem ipsum dolor sit amet $i consectetur").append('\n')
        }
        val r = run(sb.toString().toByteArray(), q(phrase))
        assertEquals(listOf(12_345, 149_999), r.matches.map { it.line })
        assertTrue(r.termsMask != 0)
    }

    @Test fun mixedDirectionLineMatchesEnglishWordCaseInsensitively() {
        val r = run("ملف config.json يحتوي على DataBase settings".toByteArray(), q("database"))
        assertEquals(1, r.matches.size)
    }

    @Test fun tatweelAndInvisibleMarksAreIgnored() {
        val text = "المـشروع\u200F يحتاج"
        assertEquals(1, run(text.toByteArray(), q("المشروع يحتاج")).matches.size)
    }

    @Test fun windowsLineEndingsAreHandled() {
        val r = run("one\r\ntwo needle\r\nthree".toByteArray(), q("needle"))
        assertEquals(listOf(2), r.matches.map { it.line })
    }

    @Test fun fileWithoutTrailingNewlineKeepsItsLastLine() {
        val r = run("a\nb\nneedle".toByteArray(), q("needle"))
        assertEquals(listOf(3), r.matches.map { it.line })
    }

    @Test fun indexNeverExcludesAMatchingFileOnRandomText() {
        val rnd = Random(42)
        val alphabet = "abcdefghij klmnop  ابتثجحخدذرزسشصضطظعغفقكلمنهوي\u064E\u0640"
        val lines = (1..200).map { (1..rnd.nextInt(60) + 5).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("") }
        val bloom = TrigramBloom.forSize(lines.sumOf { it.length }.toLong())
        FileSearcher.collect(ByteArrayInputStream(lines.joinToString("\n").toByteArray()), bloom) {}
        var checked = 0
        for (line in lines) {
            val norm = TextNormalizer.normalize(line).text
            if (norm.length < 6) continue
            repeat(3) {
                val len = 3 + rnd.nextInt(minOf(10, norm.length - 2))
                val start = rnd.nextInt(norm.length - len + 1)
                val piece = norm.substring(start, start + len)
                val query = SearchQuery.create(piece, SearchMode.Phrase) ?: return@repeat
                assertTrue("index lost '$piece'", IndexRequirement.from(query).mayContain(bloom))
                checked++
            }
        }
        assertTrue(checked > 100)
    }

    @Test fun searchAndIndexAgreeOnTheSameFile() {
        val text = "alpha\nbeta needle gamma\ndelta"
        val bloom = TrigramBloom.forSize(text.length.toLong())
        val r = FileSearcher.search(ByteArrayInputStream(text.toByteArray()), q("needle"), 50, bloom) {} as FileOutcome.Done
        assertEquals(listOf(2), r.matches.map { it.line })
        assertTrue(IndexRequirement.from(q("needle")).mayContain(bloom))
    }
}
