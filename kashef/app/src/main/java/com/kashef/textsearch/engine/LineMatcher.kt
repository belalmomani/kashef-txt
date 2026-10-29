package com.kashef.textsearch.engine

enum class SearchMode { Phrase, AllWords, AnyWord }

class SearchQuery private constructor(val mode: SearchMode, val terms: List<String>) {
    companion object {
        /** Returns null when the text is empty after normalization. */
        fun create(raw: String, mode: SearchMode): SearchQuery? {
            val norm = TextNormalizer.normalize(raw).text
            if (norm.isEmpty()) return null
            val terms = if (mode == SearchMode.Phrase) listOf(norm)
            else norm.split(' ').filter { it.isNotEmpty() }.distinct()
            return SearchQuery(mode, terms)
        }
    }
}

/** A matching line: [snippet] is original text; [ranges] are inclusive highlight ranges inside it. */
data class MatchLine(val line: Int, val snippet: String, val ranges: List<IntRange>)

object LineMatcher {
    private const val MAX_HITS = 10
    private const val CONTEXT = 70

    /** Returns snippet and highlight ranges, or null when the line does not match. Matching is per line. */
    fun find(line: String, q: SearchQuery): Pair<String, List<IntRange>>? {
        val n = TextNormalizer.normalize(line)
        if (n.text.isEmpty()) return null
        if (q.mode == SearchMode.AllWords && !q.terms.all { n.text.contains(it) }) return null

        val hits = ArrayList<IntRange>()
        for (t in q.terms) {
            var from = 0
            while (hits.size < MAX_HITS) {
                val i = n.text.indexOf(t, from)
                if (i < 0) break
                val s = n.map[i]
                val e = n.map[i + t.length - 1] + 1
                hits.add(s until e)
                from = i + t.length
            }
        }
        if (hits.isEmpty()) return null
        hits.sortBy { it.first }

        val first = hits[0]
        val start = maxOf(0, first.first - CONTEXT)
        val end = minOf(line.length, first.last + 1 + CONTEXT)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < line.length) "…" else ""
        val text = prefix + line.substring(start, end) + suffix
        val offset = prefix.length - start
        val ranges = hits
            .filter { it.first >= start && it.last + 1 <= end }
            .map { (it.first + offset)..(it.last + offset) }
        return text to ranges
    }
}
