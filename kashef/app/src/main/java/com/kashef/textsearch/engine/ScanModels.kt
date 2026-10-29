package com.kashef.textsearch.engine

data class FileResult(val name: String, val path: String, val matches: List<MatchLine>, val truncated: Boolean)

data class Problem(val path: String, val reason: String)

data class ScanSnapshot(
    val scanned: Int = 0,
    val matchedFiles: Int = 0,
    val matchCount: Int = 0,
    val unsupportedDocs: Int = 0,
    val otherSkipped: Int = 0,
    val problemsTotal: Int = 0,
    val limitReached: Boolean = false,
    val current: String = "",
    val results: List<FileResult> = emptyList(),
    val problems: List<Problem> = emptyList(),
    val completed: Boolean = false,
    val elapsedMs: Long = 0,
)

class ScanAccumulator {
    companion object {
        const val MAX_TOTAL_MATCHES = 500
        const val MAX_PROBLEMS_KEPT = 100
    }

    var scanned = 0
    var matchedFiles = 0
    var matchCount = 0
    var unsupportedDocs = 0
    var otherSkipped = 0
    var problemsTotal = 0
    var limitReached = false
    var stop = false
    var current = ""
    private val results = ArrayList<FileResult>()
    private val problems = ArrayList<Problem>()

    fun addResult(r: FileResult) {
        results.add(r)
        matchedFiles++
        matchCount += r.matches.size
        if (matchCount >= MAX_TOTAL_MATCHES) {
            limitReached = true
            stop = true
        }
    }

    fun addProblem(path: String, reason: String) {
        problemsTotal++
        if (problems.size < MAX_PROBLEMS_KEPT) problems.add(Problem(path, reason))
    }

    fun snapshot(completed: Boolean, elapsedMs: Long) = ScanSnapshot(
        scanned, matchedFiles, matchCount, unsupportedDocs, otherSkipped, problemsTotal,
        limitReached, current, results.toList(), problems.toList(), completed, elapsedMs,
    )
}
