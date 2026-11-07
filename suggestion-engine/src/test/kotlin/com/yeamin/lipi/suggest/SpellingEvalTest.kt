package com.yeamin.lipi.suggest

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Measures English spelling correction against public misspelling lists. Skipped unless the
 * environment variable SPELL_EVAL_DIR points at a folder with:
 *   wiki.txt     Wikipedia "Lists of common misspellings/For machines" (raw wikitext, "typo->word")
 *   norvig1.txt, norvig2.txt   Peter Norvig's spell-testset files ("word: typo typo …")
 * The lists aren't bundled (they're only for measuring); see docs/ROADMAP.md for the results.
 *
 * Several cost settings are compared; the two lists are reported apart, so a change that only
 * helps the list it was tuned on shows up.
 */
class SpellingEvalTest {
    private val dir = System.getenv("SPELL_EVAL_DIR")?.let(::File)

    private fun wiki(): List<Pair<String, String>> {
        val d = dir ?: return emptyList()
        return File(d, "wiki.txt").takeIf { it.exists() }?.readLines().orEmpty().mapNotNull { line ->
            // Single-answer entries only.
            Regex("^\\s*([a-z']+)->([a-z']+)\\s*$").find(line)?.let { it.groupValues[1] to it.groupValues[2] }
        }.distinct()
    }

    private fun norvig(): List<Pair<String, String>> {
        val d = dir ?: return emptyList()
        return listOf("norvig1.txt", "norvig2.txt").flatMap { name ->
            File(d, name).takeIf { it.exists() }?.readLines().orEmpty().flatMap { line ->
                val parts = line.split(":")
                if (parts.size != 2) emptyList()
                else parts[1].trim().split(" ").filter { it.isNotBlank() }.map { it.lowercase() to parts[0].trim().lowercase() }
            }
        }.distinct()
    }

    /**
     * Phone typing slips made from the correct words of the lists: a neighbouring key, a missed
     * key, an extra neighbouring key, or two letters swapped. Fixed seed, so runs compare.
     */
    private fun slips(words: List<String>, dict: EnglishDictionary): List<Pair<String, String>> {
        val rnd = java.util.Random(7)
        fun near(c: Char): Char = KeyProximity.neighbourhood(c).filter { it != c }.let { if (it.isEmpty()) c else it[rnd.nextInt(it.length)] }
        return words.filter { it.length >= 4 && it.all { c -> c in 'a'..'z' } }.mapNotNull { w ->
            val i = rnd.nextInt(w.length)
            val typo = when (rnd.nextInt(100)) {
                in 0 until 50 -> w.substring(0, i) + near(w[i]) + w.substring(i + 1)
                in 50 until 70 -> w.removeRange(i, i + 1)
                in 70 until 85 -> w.substring(0, i + 1) + near(w[i]) + w.substring(i + 1)
                else -> if (i + 1 < w.length) w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2) else w
            }
            (typo to w).takeIf { typo != w && dict.lookup(typo) == null }
        }
    }

    private class Score(val n: Int, val top1: Int, val top3: Int, val acRight: Int, val acWrong: Int) {
        fun pct(x: Int) = 100.0 * x / n
        override fun toString() = "n=%d top1=%.1f top3=%.1f ac-right=%.1f ac-wrong=%.1f".format(n, pct(top1), pct(top3), pct(acRight), pct(acWrong))
    }

    private fun score(suggester: Suggester, pairs: List<Pair<String, String>>, misses: MutableList<String>? = null): Score {
        var top1 = 0; var top3 = 0; var acRight = 0; var acWrong = 0
        for ((typo, word) in pairs) {
            val s = suggester.english(typo, SuggestOptions(limit = 3))
            if (s.candidates.firstOrNull()?.lowercase() == word) top1++
            if (s.candidates.any { it.lowercase() == word }) top3++
            when (s.autoCorrect?.lowercase()) {
                null -> Unit
                word -> acRight++
                else -> { acWrong++; if (misses != null && misses.size < 60) misses += "$typo -> ${s.autoCorrect} (want $word; offered ${s.candidates})" }
            }
        }
        return Score(pairs.size, top1, top3, acRight, acWrong)
    }

    @Test fun evaluate() {
        assumeTrue(dir != null)
        val dict = EnglishDictionary.load()
        fun usable(p: List<Pair<String, String>>) = p.filter { (typo, word) -> dict.lookup(typo) == null && dict.lookup(word) != null && typo.length >= 3 }
        val wiki = usable(wiki())
        val norvig = usable(norvig())
        val slips = slips((wiki + norvig).map { it.second }.distinct(), dict)

        val original = TypingCosts(doubled = 0.6f, vowel = 1f, vowelGap = 1f, sound = 1f, name = 0f)
        val settings = listOf(
            "original" to Suggester({ dict }, { null }, { null }, UserHistory(), original, 0f),
            "default" to Suggester({ dict }, { null }, { null }, UserHistory()),
        ) + (System.getenv("SPELL_EVAL_SWEEP")?.let { sweep(dict) } ?: emptyList())
        for ((name, s) in settings) {
            println("EVAL %-28s wiki %s".format(name, score(s, wiki)))
            println("EVAL %-28s norv %s".format(name, score(s, norvig)))
            println("EVAL %-28s slip %s".format(name, score(s, slips)))
        }

        // The shipped setting in detail.
        val suggester = Suggester({ dict }, { null }, { null }, UserHistory())
        val misses = ArrayList<String>()
        score(suggester, wiki + norvig, misses)
        val all = wiki + norvig
        var checkRight = 0; var checkWrong = 0
        for ((typo, word) in all) {
            val fix = suggester.checker.check("we saw $typo there", endsAtFieldEnd = false).firstOrNull { it.original == typo }?.replacement?.lowercase()
            if (fix == word) checkRight++ else if (fix != null) checkWrong++
        }
        // Correct words must stay as they are.
        val correct = all.map { it.second }.distinct()
        val falseAuto = correct.count { w -> suggester.english(w).autoCorrect?.lowercase()?.let { it != w } == true }
        val falseFlags = correct.count { w -> suggester.checker.check("we saw $w there", endsAtFieldEnd = false).any { it.original == w } }
        println("EVAL checker right=%.1f%% wrong=%.1f%%".format(100.0 * checkRight / all.size, 100.0 * checkWrong / all.size))
        println("EVAL correct words: autocorrected=$falseAuto flagged=$falseFlags of ${correct.size}")
        misses.forEach { println("EVAL miss $it") }
    }

    private fun sweep(dict: EnglishDictionary): List<Pair<String, Suggester>> {
        fun s(name: String, c: TypingCosts, margin: Float = 0f) = name to Suggester({ dict }, { null }, { null }, UserHistory(), c, margin)
        val combo = TypingCosts(vowel = 0.6f, vowelGap = 0.7f, sound = 0.6f)
        return listOf(
            s("weight120", TypingCosts(frequencyWeight = 120f)),
            s("neighbour0.5", TypingCosts(neighbour = 0.5f)),
            s("vowel0.6 gap0.7", TypingCosts(vowel = 0.6f, vowelGap = 0.7f)),
            s("vowel0.5 gap0.6", TypingCosts(vowel = 0.5f, vowelGap = 0.6f)),
            s("combo", combo),
            s("combo doubled0.3", TypingCosts(vowel = 0.6f, vowelGap = 0.7f, sound = 0.6f, doubled = 0.3f)),
            s("combo margin10", combo, 10f),
            s("combo margin15", combo, 15f),
            s("combo margin20", combo, 20f),
        )
    }
}
