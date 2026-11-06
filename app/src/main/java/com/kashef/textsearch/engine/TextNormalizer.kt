package com.kashef.textsearch.engine

/** Normalized text plus a map from each normalized index to the original index. */
class Normalized(val text: String, val map: IntArray)

/**
 * Matching rules (shown to the user in the app):
 *  - English letter case is ignored.
 *  - Arabic diacritics (tashkeel), tatweel and invisible direction marks are ignored.
 *  - Runs of spaces/tabs count as one space.
 *  - Punctuation DOES matter. Arabic letter forms (e.g. أ / ا) are NOT merged, unless the user turns on "flexible" matching (fold = true).
 */
object TextNormalizer {
    private fun isIgnorable(c: Char): Boolean =
        c in '\u064B'..'\u065F' || c == '\u0670' || c == '\u0640' || c in '\u06D6'..'\u06ED' ||
            c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u200E' || c == '\u200F' ||
            c == '\u061C' || c == '\uFEFF'

    /**
     * Optional "flexible" folding of Arabic/Persian spelling variants. It maps one character to one
     * character, so positions (and highlight ranges) stay valid.
     */
    fun foldChar(c: Char): Char = when (c) {
        'أ', 'إ', 'آ', 'ٱ' -> 'ا'
        'ى', 'ی' -> 'ي'
        'ة' -> 'ه'
        'ؤ' -> 'و'
        'ئ' -> 'ي'
        'ک' -> 'ك'
        else -> c
    }

    fun normalize(src: String, fold: Boolean = false): Normalized {
        val sb = StringBuilder(src.length)
        val map = IntArray(src.length)
        var n = 0
        var lastSpace = false
        for (i in src.indices) {
            val c = src[i]
            if (isIgnorable(c)) continue
            if (c.isWhitespace()) {
                if (!lastSpace && n > 0) {
                    sb.append(' ')
                    map[n++] = i
                    lastSpace = true
                }
                continue
            }
            lastSpace = false
            val lc = Character.toLowerCase(c)
            sb.append(if (fold) foldChar(lc) else lc)
            map[n++] = i
        }
        if (n > 0 && sb[n - 1] == ' ') {
            sb.setLength(n - 1)
            n--
        }
        return Normalized(sb.toString(), map.copyOf(n))
    }
}
