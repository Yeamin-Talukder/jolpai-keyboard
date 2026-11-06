package com.yeamin.lipi.suggest

/**
 * The Bangla word list (~159k words, from OpenBangla's upodesh, MIT), shared by Avro
 * dictionary search and National-layout word completion.
 *
 * The list has no frequencies, so completions prefer words the user has typed before,
 * then shorter words (which tend to be the common base forms).
 */
class BanglaDictionary private constructor(internal val words: SortedWords) {

    val size: Int get() = words.size

    /** Like [EnglishDictionary.nextCharWeights], with every word weighing 1 (the list has no frequencies). */
    fun nextCharWeights(prefix: String, out: MutableMap<Char, Float>): Float {
        val range = words.prefixRange(prefix)
        var whole = 0f
        for (i in SortedWords.rangeStart(range) until SortedWords.rangeEnd(range)) {
            if (words.lengthAt(i) == prefix.length) {
                whole += 1f
            } else {
                val c = words.charAt(i, prefix.length)
                out[c] = (out[c] ?: 0f) + 1f
            }
        }
        return whole
    }

    fun contains(word: String): Boolean {
        val i = words.lowerBound(word)
        return i < words.size && words.equalsAt(i, word)
    }

    /** Up to [limit] dictionary words that start with [prefix] and are longer than it, shortest first. */
    fun completions(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val range = words.prefixRange(prefix)
        val top = TopN<Int>(limit)
        for (i in SortedWords.rangeStart(range) until SortedWords.rangeEnd(range)) {
            val len = words.lengthAt(i)
            if (len == prefix.length) continue
            top.offer(i, -len.toFloat())
        }
        return top.result().map { words.wordAt(it) }
    }

    companion object {
        /** Loads the bundled list (a few hundred ms on a phone): call off the main thread. */
        fun load(): BanglaDictionary = BanglaDictionary(SortedWords.of(readResourceLines("/bn/bangla-words.txt")))

        fun of(words: List<String>): BanglaDictionary = BanglaDictionary(SortedWords.of(words))
    }
}

/**
 * Finds Bangla dictionary words that a Roman (Avro) word could stand for.
 *
 * A port of the algorithm in OpenBangla's `upodesh` (MIT): the Roman input is cut into the
 * longest known patterns (`kh`, `sh`, `o`, …); each pattern can be written several ways in
 * Bangla (`sh` → শ, ষ, স); the search walks the sorted word list keeping only spellings that
 * are prefixes of real words. Common endings (hasanta, ঁ, ঃ, ্য, ্ব, ্ম) may appear between
 * patterns. upodesh walks an FST; here a "node" is a prefix plus the index range of words
 * starting with it, narrowed by binary search.
 */
class AvroDictionarySearch(
    private val dictionary: BanglaDictionary,
    private val patterns: Map<String, Block>,
    private val commonSuffixes: List<String>,
) {
    /** The Bangla spellings of one Roman pattern. [optional] means the pattern may produce nothing. */
    class Block(val spellings: List<String>, val optional: Boolean)

    private val patternTrie = CharTrie(patterns.keys)

    private inner class Node(val prefix: String, val from: Int, val to: Int) {
        fun child(suffix: String): Node? {
            val next = prefix + suffix
            val range = dictionary.words.prefixRange(next, from, to)
            val s = SortedWords.rangeStart(range)
            val e = SortedWords.rangeEnd(range)
            return if (s < e) Node(next, s, e) else null
        }

        fun wordOrNull(): String? =
            if (from < to && dictionary.words.equalsAt(from, prefix)) prefix else null
    }

    private fun root(prefix: String): Node? {
        val range = dictionary.words.prefixRange(prefix)
        val s = SortedWords.rangeStart(range)
        val e = SortedWords.rangeEnd(range)
        return if (s < e) Node(prefix, s, e) else null
    }

    /** Dictionary words matching the Roman [input], in no particular order. */
    fun search(input: String): List<String> {
        val fixed = fixInput(input)
        val first = patternTrie.longestPrefix(fixed, 0, fixed.length)
        val firstBlock = patterns[fixed.substring(0, first.length)] ?: return emptyList()

        var nodes = dedupe(firstBlock.spellings.mapNotNull { root(it) })
        nodes = withCommonSuffixes(nodes)

        var remaining = fixed.substring(first.length)
        while (remaining.isNotEmpty()) {
            var match = patternTrie.longestPrefix(remaining, 0, remaining.length)
            var matched = remaining.substring(0, match.length)
            if (!match.complete) {
                // Back off to the longest shorter prefix that is a whole pattern.
                var found = false
                for (i in remaining.length - 1 downTo 0) {
                    match = patternTrie.longestPrefix(remaining, 0, i)
                    if (match.complete) {
                        matched = remaining.substring(0, match.length)
                        remaining = remaining.substring(i)
                        found = true
                        break
                    }
                }
                if (!found) break
            } else {
                remaining = remaining.substring(match.length)
            }
            val block = patterns[matched] ?: break
            val next = block.spellings.flatMap { spelling -> nodes.mapNotNull { it.child(spelling) } }
            nodes = if (block.optional) dedupe(nodes + next) else dedupe(next)
            nodes = withCommonSuffixes(nodes)
        }
        return nodes.mapNotNull { it.wordOrNull() }.distinct()
    }

    private fun withCommonSuffixes(nodes: List<Node>): List<Node> =
        dedupe(nodes + nodes.flatMap { n -> commonSuffixes.mapNotNull { n.child(it) } })

    private fun dedupe(nodes: List<Node>): List<Node> = nodes.distinctBy { it.prefix }

    companion object {
        /**
         * upodesh's input clean-up: drop non-alphanumerics (except the ` escape), lower-case,
         * and write `o` as `O` at the start of a word, where it is a full vowel.
         */
        fun fixInput(s: String): String {
            val out = StringBuilder(s.length)
            var prev = ' '
            for (c in s.trim()) {
                if ((c == 'o' || c == 'O') && !(prev in 'a'..'z' || prev in 'A'..'Z')) {
                    out.append('O')
                } else if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '`') {
                    out.append(c.lowercaseChar())
                }
                prev = c
            }
            return out.toString()
        }

        fun load(dictionary: BanglaDictionary): AvroDictionarySearch {
            val patterns = readResourceLines("/bn/avro-patterns.tsv").associate { line ->
                val (key, optional, spellings) = line.split('\t', limit = 3)
                key to Block(spellings.split('|').filter { it.isNotEmpty() }, optional == "1")
            }
            val common = readResourceLines("/bn/common-suffix-patterns.txt")
            return AvroDictionarySearch(dictionary, patterns, common)
        }
    }
}

/** A tiny character trie over the Roman pattern keys, for longest-prefix matching. */
internal class CharTrie(keys: Collection<String>) {
    private class N {
        val next = HashMap<Char, N>(4)
        var terminal = false
    }

    private val root = N()

    init {
        for (k in keys) {
            var n = root
            for (c in k) n = n.next.getOrPut(c) { N() }
            n.terminal = true
        }
    }

    /** How far [s][from, to) can be walked in the trie, and whether the walk ended on a whole key. */
    data class Match(val length: Int, val complete: Boolean)

    fun longestPrefix(s: String, from: Int, to: Int): Match {
        var n = root
        var i = from
        while (i < to) {
            n = n.next[s[i]] ?: break
            i++
        }
        return Match(i - from, n.terminal)
    }
}
