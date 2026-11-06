package com.kashef.textsearch.engine

enum class SearchMode { Phrase, AllWords, AnyWord }

/**
 * [terms] are what the lines are compared with (folded when [fold] is on). [indexTerms] are always
 * folded, because the local index is always built from folded text; folding only merges characters,
 * so a folded term is present whenever the unfolded one is (the index never excludes a real match).
 */
class SearchQuery private constructor(
    val mode: SearchMode,
    val terms: List<String>,
    val fold: Boolean,
    val indexTerms: List<String>,
) {
    companion object {
        /** Returns null when the text is empty after normalization. */
        fun create(raw: String, mode: SearchMode, fold: Boolean = false): SearchQuery? {
            val norm = TextNormalizer.normalize(raw, fold).text
            if (norm.isEmpty()) return null
            val folded = if (fold) norm else TextNormalizer.normalize(raw, true).text
            fun split(t: String) =
                if (mode == SearchMode.Phrase) listOf(t) else t.split(' ').filter { it.isNotEmpty() }.distinct()
            return SearchQuery(mode, split(norm), fold, split(folded))
        }
    }
}

/** A matching line: [snippet] is original text; [ranges] are inclusive highlight ranges inside it. */
data class MatchLine(val line: Int, val snippet: String, val ranges: List<IntRange>)

/** Result of matching one line. [termMask] has bit i set when query term i occurs in the line (first 30 terms). */
data class LineHit(val snippet: String, val ranges: List<IntRange>, val termMask: Int)

object LineMatcher {
    private const val MAX_HITS = 10
    private const val CONTEXT = 70
    const val MAX_TERMS = 30

    private fun occurrences(n: Normalized, terms: List<String>, max: Int): List<IntRange> {
        val hits = ArrayList<IntRange>()
        for (t in terms) {
            var from = 0
            while (hits.size < max) {
                val i = n.text.indexOf(t, from)
                if (i < 0) break
                val s = n.map[i]
                val e = n.map[i + t.length - 1] + 1
                hits.add(s until e)
                from = i + t.length
            }
        }
        hits.sortBy { it.first }
        return hits
    }

    /** Highlight ranges over the WHOLE line (used by the context viewer). */
    fun ranges(line: String, q: SearchQuery, max: Int = 50): List<IntRange> {
        val n = TextNormalizer.normalize(line, q.fold)
        if (n.text.isEmpty()) return emptyList()
        return occurrences(n, q.terms, max)
    }

    /** Returns snippet, highlight ranges and matched-term mask, or null when the line does not match. Matching is per line. */
    fun find(line: String, q: SearchQuery, normalized: Normalized = TextNormalizer.normalize(line, q.fold)): LineHit? {
        val n = normalized
        if (n.text.isEmpty()) return null
        if (q.mode == SearchMode.AllWords && !q.terms.all { n.text.contains(it) }) return null

        val hits = occurrences(n, q.terms, MAX_HITS)
        if (hits.isEmpty()) return null

        var mask = 0
        q.terms.forEachIndexed { i, t -> if (i < MAX_TERMS && n.text.contains(t)) mask = mask or (1 shl i) }

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
        return LineHit(text, ranges, mask)
    }
}
