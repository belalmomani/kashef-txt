package com.kashef.textsearch.engine

enum class SortMode { Relevance, Newest, Name }

/** Empty sets mean "no restriction". [sinceMs] > 0 keeps only files modified at or after that time. */
data class Filters(
    val exts: Set<String> = emptySet(),
    val roots: Set<String> = emptySet(),
    val sinceMs: Long = 0L,
    val text: String = "", // narrows by file name/path or by the visible snippet
)

/**
 * Ranking is explainable and uses only measured values:
 *  1. how many of the query words occur in the file (more is better),
 *  2. how many matching lines were found (more is better),
 *  3. the most recently modified file first,
 *  4. file name, for a stable order.
 * There is no invented "percentage".
 */
object ResultOps {
    private fun mentions(r: FileResult, needle: String): Boolean =
        TextNormalizer.normalize(r.name + " " + r.path).text.contains(needle) ||
            r.matches.any { TextNormalizer.normalize(it.snippet).text.contains(needle) }

    /** Plain-text export of a result list (includes snippets of the user's files; the user chooses where to send it). */
    fun toText(results: List<FileResult>, query: String): String = buildString {
        append("نتائج البحث عن «").append(query).append("» — ").append(results.size).append(" ملفًا\n")
        results.forEachIndexed { i, r ->
            append('\n').append('#').append(i + 1).append(' ').append(r.name).append('\n')
            append(r.path).append('\n')
            r.matches.forEach { m -> append("  سطر ").append(m.line).append(": ").append(m.snippet).append('\n') }
            if (r.truncated) append("  (توجد مطابقات أخرى لم تُعرض)\n")
        }
    }

    fun apply(results: List<FileResult>, f: Filters, sort: SortMode): List<FileResult> {
        val needle = TextNormalizer.normalize(f.text).text
        val kept = results.filter { r ->
            (f.exts.isEmpty() || r.ext in f.exts) &&
                (f.roots.isEmpty() || r.root in f.roots) &&
                (f.sinceMs <= 0L || (r.modified > 0L && r.modified >= f.sinceMs)) &&
                (needle.isEmpty() || mentions(r, needle))
        }
        val byName = compareBy<FileResult> { it.name.lowercase() }
        val cmp: Comparator<FileResult> = when (sort) {
            SortMode.Relevance -> compareByDescending<FileResult> { it.termsMatched }
                .thenByDescending { it.matches.size }
                .thenByDescending { it.modified }
                .then(byName)
            SortMode.Newest -> compareByDescending<FileResult> { it.modified }.then(byName)
            SortMode.Name -> byName
        }
        return kept.sortedWith(cmp)
    }
}
