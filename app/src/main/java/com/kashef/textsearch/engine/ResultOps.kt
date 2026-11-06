package com.kashef.textsearch.engine

enum class SortMode { Relevance, Newest, Name }

/** Empty sets mean "no restriction". [sinceMs] > 0 keeps only files modified at or after that time. */
data class Filters(
    val exts: Set<String> = emptySet(),
    val roots: Set<String> = emptySet(),
    val sinceMs: Long = 0L,
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
    fun apply(results: List<FileResult>, f: Filters, sort: SortMode): List<FileResult> {
        val kept = results.filter { r ->
            (f.exts.isEmpty() || r.ext in f.exts) &&
                (f.roots.isEmpty() || r.root in f.roots) &&
                (f.sinceMs <= 0L || (r.modified > 0L && r.modified >= f.sinceMs))
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
