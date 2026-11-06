package com.kashef.textsearch.engine

/** Receives the NORMALIZED text of each line while a file is being read. */
fun interface LineSink {
    fun addNormalized(text: String)
}

/** Hash of three consecutive characters. */
object Tri {
    fun hash(a: Char, b: Char, c: Char): Int = (a.code * 31 + b.code) * 31 + c.code

    inline fun forEach(text: String, action: (Int) -> Unit) {
        var i = 0
        while (i + 2 < text.length) {
            action(hash(text[i], text[i + 1], text[i + 2]))
            i++
        }
    }
}

/**
 * A per-file Bloom filter of the character trigrams that occur in the file's normalized lines.
 * It stores hashed bits, not readable text, and it never says "absent" for a trigram that is present
 * (no false negatives), so it can only be used to SKIP files that certainly do not match.
 */
class TrigramBloom(val bits: Int, val data: ByteArray = ByteArray((bits + 7) / 8)) : LineSink {
    companion object {
        const val MIN_BITS = 2048
        const val MAX_BITS = 1 shl 20
        private const val MIX = -7046029254386353131L // 0x9E3779B97F4A7C15

        fun bitsForSize(sizeBytes: Long): Int = (sizeBytes * 3).coerceIn(MIN_BITS.toLong(), MAX_BITS.toLong()).toInt()

        fun forSize(sizeBytes: Long) = TrigramBloom(bitsForSize(sizeBytes))

        fun fromBytes(bits: Int, bytes: ByteArray): TrigramBloom? =
            if (bits > 0 && bytes.size == (bits + 7) / 8) TrigramBloom(bits, bytes) else null
    }

    private fun slot(h: Int, i: Int): Int {
        val x = h.toLong() * MIX
        val h1 = (x ushr 32).toInt().toLong()
        val h2 = (x.toInt() or 1).toLong()
        return Math.floorMod(h1 + i * h2, bits.toLong()).toInt()
    }

    fun add(h: Int) {
        for (i in 0 until 3) {
            val b = slot(h, i)
            data[b ushr 3] = (data[b ushr 3].toInt() or (1 shl (b and 7))).toByte()
        }
    }

    fun mightContain(h: Int): Boolean {
        for (i in 0 until 3) {
            val b = slot(h, i)
            if ((data[b ushr 3].toInt() and (1 shl (b and 7))) == 0) return false
        }
        return true
    }

    override fun addNormalized(text: String) {
        Tri.forEach(text) { add(it) }
    }
}

/**
 * What a file must contain to possibly match a query. [mayContain] returns false ONLY when the file
 * certainly does not contain the query; any doubt (including words shorter than 3 characters, which
 * have no trigrams) returns true so the file is scanned normally.
 */
class IndexRequirement private constructor(private val groups: List<IntArray>, private val anyMode: Boolean) {
    companion object {
        fun from(q: SearchQuery): IndexRequirement {
            val perTerm = q.indexTerms.map { t ->
                val set = LinkedHashSet<Int>()
                Tri.forEach(t) { set.add(it) }
                set.toIntArray()
            }
            return if (q.mode == SearchMode.AnyWord) {
                IndexRequirement(perTerm, true)
            } else {
                // Phrase and AllWords: every trigram of every term must occur somewhere in the file.
                IndexRequirement(listOf(perTerm.flatMap { it.toList() }.distinct().toIntArray()), false)
            }
        }
    }

    fun mayContain(b: TrigramBloom): Boolean {
        if (anyMode) {
            if (groups.any { it.isEmpty() }) return true
            return groups.any { g -> g.all { b.mightContain(it) } }
        }
        return groups[0].all { b.mightContain(it) }
    }
}
