package com.yeamin.jolpai.suggest

/**
 * Banglish: Bangla written in English letters and kept that way ("ami tomake onek valobashi",
 * "kmn acho", "kisu khaisos?"), which is how much everyday chat in Bangladesh is typed.
 *
 * About 18k spellings people really use, counted from BanglaTLit's 246k romanized Bangla
 * comments and posts (MIT, see bn/LICENSE-MIT-BanglaTLit.txt), each with how common it is and the
 * Bangla word it stands for. Built by `lm-trainer banglish` (common English words are left to the
 * English list). Completions match by sound ([BanglishFold]): "bh" finds valo as well as bhalo,
 * "asi" finds achi, and the spelling shown is the one the user types ([bestSpelling]).
 */
class BanglishWords private constructor(
    private val spellings: Array<String>,
    private val frequencies: ByteArray,
    private val banglaWords: Array<String?>,
    private val offensiveFlags: BooleanArray,
    /** Folded keys, sorted; [byFold] maps their positions to entries. */
    private val folded: SortedWords,
    private val byFold: IntArray,
    /** Spellings, sorted; [bySpelling] maps their positions to entries. */
    private val sorted: SortedWords,
    private val bySpelling: IntArray,
    /** Bangla word (normalised as the models read it) → its most common Banglish spelling. */
    private val byBangla: HashMap<String, Int>,
) {
    val size: Int get() = spellings.size

    /** A Banglish word: its usual spelling, how common it is (0–255, English list scale) and its Bangla word. */
    data class Entry(val spelling: String, val frequency: Int, val bangla: String?, val offensive: Boolean)

    private fun entry(i: Int) = Entry(spellings[i], frequencies[i].toInt() and 0xFF, banglaWords[i], offensiveFlags[i])

    private fun indexOf(word: String): Int {
        val key = word.lowercase()
        val p = sorted.lowerBound(key)
        return if (p < sorted.size && sorted.equalsAt(p, key)) bySpelling[p] else -1
    }

    /** The entry spelled exactly [word] (ignoring case), or null. */
    fun lookup(word: String): Entry? = indexOf(word).takeIf { it >= 0 }?.let(::entry)

    fun contains(word: String): Boolean = indexOf(word) >= 0

    /** The usual Banglish spelling of the Bangla word [bangla] (আমি → ami), or null if none is listed. */
    fun forBangla(bangla: String): Entry? = byBangla[BanglaNextWords.normalize(bangla)]?.let(::entry)

    /** The Bangla word the Banglish [word] stands for, as listed (ami → আমি), or null. */
    fun banglaOf(word: String): String? = lookup(word)?.bangla

    /**
     * Words that sound like they start with [prefix] (valo, bhalo and bhalobasa for "bh"), most
     * common first; spelling variants of one word come back as one entry, in its most common
     * spelling (use [bestSpelling] to show the user's own). Offensive words are left out unless
     * [allowOffensive].
     */
    fun completions(prefix: String, limit: Int, allowOffensive: Boolean = false): List<Entry> {
        val key = BanglishFold.fold(prefix)
        if (key.isEmpty()) return emptyList()
        val range = folded.prefixRange(key)
        // Entries are sorted most common first within one folded key, so the first of each key wins.
        val top = TopN<Int>(limit)
        val start = SortedWords.rangeStart(range)
        for (p in start until SortedWords.rangeEnd(range)) {
            if (p > start && sameKey(p - 1, p)) continue
            val i = byFold[p]
            if (!allowOffensive && offensiveFlags[i]) continue
            top.offer(i, (frequencies[i].toInt() and 0xFF).toFloat())
        }
        return top.result().map(::entry)
    }

    private fun sameKey(a: Int, b: Int): Boolean {
        val n = folded.lengthAt(a)
        if (folded.lengthAt(b) != n) return false
        for (k in 0 until n) if (folded.charAt(a, k) != folded.charAt(b, k)) return false
        return true
    }

    /** Every listed spelling that sounds like [word] (bhalo → valo, bhalo, vhalo), most common first. */
    fun variants(word: String): List<String> {
        val key = BanglishFold.fold(word)
        if (key.isEmpty()) return emptyList()
        val range = folded.prefixRange(key)
        val out = ArrayList<String>()
        for (p in SortedWords.rangeStart(range) until SortedWords.rangeEnd(range)) {
            if (folded.lengthAt(p) == key.length) out += spellings[byFold[p]]
        }
        return out
    }

    /**
     * How to show [entry] to this user: the spelling of it they have typed most (from
     * [typedCount], e.g. their history), else the most common one. "valo" for someone who
     * writes valo, "bhalo" for someone who writes bhalo.
     */
    fun bestSpelling(entry: Entry, typedCount: (String) -> Int): String {
        var best = entry.spelling
        var bestCount = typedCount(best)
        for (v in variants(entry.spelling)) {
            val c = typedCount(v)
            if (c > bestCount) { best = v; bestCount = c }
        }
        return best
    }

    companion object {
        const val RESOURCE = "/bn/banglish-words.tsv"

        /** Loads the bundled list (tens of milliseconds): call off the main thread. */
        fun load(): BanglishWords = parse(readResourceLines(RESOURCE))

        /** Lines of `spelling<TAB>frequency<TAB>bangla<TAB>flags` (bangla may be empty; flag "o" = offensive). */
        fun parse(lines: List<String>): BanglishWords {
            val rows = lines.asSequence().filter { !it.startsWith("#") }.mapNotNull { line ->
                val f = line.split('\t')
                val spelling = f[0].lowercase()
                if (spelling.isEmpty() || f.size < 2) return@mapNotNull null
                Row(spelling, f[1].toIntOrNull()?.coerceIn(0, 255) ?: 0, f.getOrNull(2)?.ifEmpty { null }, f.getOrNull(3)?.contains('o') == true)
            }.distinctBy { it.spelling }.toList()
            val spellings = Array(rows.size) { rows[it].spelling }
            val keys = Array(rows.size) { BanglishFold.fold(spellings[it]) }
            // By folded key, then most common first (so completions keep the usual spelling).
            val foldOrder = rows.indices.sortedWith(compareBy<Int> { keys[it] }.thenByDescending { rows[it].frequency }.thenBy { spellings[it] })
            val spellingOrder = rows.indices.sortedBy { spellings[it] }
            val byBangla = HashMap<String, Int>(rows.size)
            rows.indices.sortedByDescending { rows[it].frequency }.forEach { i ->
                rows[i].bangla?.let { byBangla.putIfAbsent(BanglaNextWords.normalize(it), i) }
            }
            return BanglishWords(
                spellings,
                ByteArray(rows.size) { rows[it].frequency.toByte() },
                Array(rows.size) { rows[it].bangla },
                BooleanArray(rows.size) { rows[it].offensive },
                SortedWords.of(foldOrder.map { keys[it] }),
                foldOrder.toIntArray(),
                SortedWords.of(spellingOrder.map { spellings[it] }),
                spellingOrder.toIntArray(),
                byBangla,
            )
        }

        private class Row(val spelling: String, val frequency: Int, val bangla: String?, val offensive: Boolean)
    }
}

/**
 * Reduces a Banglish spelling to how it sounds, so the many ways of writing one word meet:
 * valo = bhalo = bhaalo, achi = asi = achhi, kichu = kisu, jabo = zabo, kew = keo, amay = amai,
 * korrte = korte.
 *
 * - h after a consonant is dropped (bh → b, ch → c, sh → s, kh → k, th → t, dh → d, ph → p);
 * - v → b, z → j, c → s (so ch and s meet, as in Bangladeshi speech), q → k, f → p, w → o, y → i;
 * - a letter repeated is written once (aa → a, rr → r).
 *
 * Each step looks only at the letter before, so the fold of a word's start is the start of the
 * word's fold: prefix search works on folded keys. Anything but the letters a–z is dropped.
 */
object BanglishFold {
    fun fold(word: String): String {
        val out = StringBuilder(word.length)
        var previous = ' ' // the letter before, as typed (lower case)
        for (raw in word) {
            val c = raw.lowercaseChar()
            if (c !in 'a'..'z') continue
            val after = previous
            previous = c
            if (c == 'h' && after in 'a'..'z' && after !in VOWELS) continue // also chh, hh
            val m = when (c) {
                'v' -> 'b'
                'z' -> 'j'
                'c' -> 's'
                'q' -> 'k'
                'f' -> 'p'
                'w' -> 'o'
                'y' -> 'i'
                else -> c
            }
            if (out.isNotEmpty() && out[out.length - 1] == m) continue
            out.append(m)
        }
        return out.toString()
    }

    private const val VOWELS = "aeiouwy"
}
