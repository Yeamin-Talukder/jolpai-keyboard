package com.yeamin.jolpai.suggest

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * The next-word model on public everyday text the model was not trained on. Runs only when
 * LM_EVAL_DIR points at a folder with any of:
 *
 * - `tatoeba-heldout.txt`: Tatoeba English sentences whose id ends in 0 (held out of the
 *   model's training by its authors), one per line;
 * - `cv-sentence-collector.txt`: Mozilla Common Voice sentences (CC0), one per line.
 *
 * 1,000 sentences of 4–20 words are sampled from each (fixed seed). Both ways use the same
 * dictionary and an empty typing history, as on a fresh install:
 *
 * - next word: the right word among the 3 offered after a space;
 * - after 1 / 2 / 3 letters: the right word among the 2 suggestions beside the typed letters;
 * - autocorrect: a phone slip (neighbour key, missing letter, swapped letters) put into words of
 *   4+ letters: right fix / wrong word / left alone; and correct words changed by mistake;
 * - Check word choice: issues on the correct sentences (false alarms), and how often a swapped
 *   word (there → their …) is caught with the right fix.
 */
class RealTextEvalTest {

    private val dir = System.getenv("LM_EVAL_DIR")?.let(::File)
    private val dict by lazy { EnglishDictionary.load() }
    private val old by lazy { Suggester({ dict }, { null }, { null }, UserHistory()) }
    private val new by lazy { Suggester({ dict }, { null }, { null }, UserHistory(), model = { NextWordModelTest.model }) }

    private fun sample(file: File, n: Int = 1000): List<List<String>> {
        val r = Random(5)
        return file.readLines().asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.none { c -> c.isDigit() } }
            .map { s -> WORDS.findAll(s.replace('’', '\'')).map { it.value }.toList() }
            .filter { it.size in 4..20 }
            .toList()
            .shuffled(r)
            .take(n)
    }

    private class Score(var hit: Int = 0, var total: Int = 0) {
        fun add(ok: Boolean) { total++; if (ok) hit++ }
        override fun toString() = "%.1f%%".format(100.0 * hit / maxOf(1, total))
    }

    private class Ac(var right: Int = 0, var wrong: Int = 0, var total: Int = 0, var falseFix: Int = 0, var words: Int = 0)

    @Test fun `everyday text`() {
        assumeTrue(dir != null)
        listOf("tatoeba-heldout.txt", "cv-sentence-collector.txt").map { File(dir, it) }.filter { it.exists() }.forEach { file ->
            val sentences = sample(file)
            val wordCount = sentences.sumOf { it.size }
            println("EVAL ===== ${file.name}: ${sentences.size} sentences, $wordCount words")
            for ((label, s, useModel) in listOf(Triple("without model", old, false), Triple("with model", new, true))) {
                val next = Score()
                val after = Array(3) { Score() }
                val ac = Ac()
                val r = Random(9)
                val t0 = System.nanoTime()
                sentences.forEach { w ->
                    for (i in w.indices) {
                        val word = w[i]
                        val context = w.take(i)
                        val options = SuggestOptions(context = context, useModel = useModel)
                        next.add(s.nextWords(Language.ENGLISH, w.getOrNull(i - 1), 3, options).any { it.equals(word, ignoreCase = true) })
                        for (k in 1..3) {
                            if (word.length <= k) continue
                            val c = s.english(word.take(k), options.copy(autoCorrect = false, limit = 2)).candidates
                            after[k - 1].add(c.any { it.equals(word, ignoreCase = true) })
                        }
                        // A correctly typed word should never be changed (case fixes like i → I aside).
                        val own = s.english(word, options).autoCorrect
                        ac.words++
                        if (own != null && !own.equals(word, ignoreCase = true)) ac.falseFix++
                        if (word.length >= 4 && word.all { it in 'a'..'z' }) {
                            val typo = slip(word, r)
                            if (!dict.contains(typo)) {
                                ac.total++
                                val fix = s.english(typo, options).autoCorrect
                                when {
                                    fix == null -> {}
                                    fix.equals(word, ignoreCase = true) -> ac.right++
                                    else -> ac.wrong++
                                }
                            }
                        }
                    }
                }
                val ms = (System.nanoTime() - t0) / 1e6 / wordCount
                println(
                    "EVAL $label: next word top-3 $next | after 1 letter ${after[0]} | after 2 ${after[1]} | after 3 ${after[2]} | " +
                        "autocorrect on ${ac.total} slips: right ${ac.right} (%.1f%%), wrong ${ac.wrong} (%.1f%%) | correct words changed ${ac.falseFix} of ${ac.words} | %.2f ms/word"
                            .format(100.0 * ac.right / ac.total, 100.0 * ac.wrong / ac.total, ms),
                )
            }
            wordChoice(sentences)
        }
    }

    /** The Check card's word-choice rule on correct sentences and on ones with a word swapped. */
    private fun wordChoice(sentences: List<List<String>>) {
        val checker = new.checker
        val sets = CONFUSED.flatMap { set -> set.map { it to set } }.toMap()
        var checked = 0
        var falseAlarms = 0
        var swapped = 0
        var caught = 0
        var caughtWrongFix = 0
        sentences.forEach { w ->
            val i = w.indexOfFirst { it.lowercase() in sets }
            if (i < 0) return@forEach
            val text = w.joinToString(" ") + "."
            checked++
            checker.check(text, useModel = true).filter { it.reason.contains("fits this sentence") }.forEach {
                falseAlarms++
                println("EVAL false alarm: \"$text\" ${it.original} → ${it.replacement}")
            }
            val original = w[i].lowercase()
            val other = sets.getValue(original).first { it != original }
            val broken = w.toMutableList().also { it[i] = other }.joinToString(" ") + "."
            swapped++
            val issue = checker.check(broken, useModel = true).firstOrNull { it.reason.contains("fits this sentence") && it.original.equals(other, ignoreCase = true) }
            if (issue != null) {
                if (issue.replacement.equals(original, ignoreCase = true)) caught++ else caughtWrongFix++
            }
        }
        println("EVAL check word choice: $checked correct sentences with a confusable word → $falseAlarms false alarms; $swapped with one swapped → caught $caught (%.1f%%), wrong fix $caughtWrongFix".format(100.0 * caught / maxOf(1, swapped)))
    }

    private fun slip(word: String, r: Random): String {
        val at = r.nextInt(1, word.length)
        return when (r.nextInt(3)) {
            0 -> word.substring(0, at) + neighbour(word[at], r) + word.substring(at + 1)
            1 -> word.removeRange(at, at + 1)
            else -> if (at < word.length - 1) word.substring(0, at) + word[at + 1] + word[at] + word.substring(at + 2) else word.dropLast(1)
        }
    }

    private fun neighbour(c: Char, r: Random): Char {
        val row = ROWS.first { c in it }
        val i = row.indexOf(c)
        val options = listOfNotNull(row.getOrNull(i - 1), row.getOrNull(i + 1))
        return options[r.nextInt(options.size)]
    }

    companion object {
        private val WORDS = Regex("[A-Za-z]+(?:'[A-Za-z]+)?")
        private val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        private val CONFUSED = listOf(
            listOf("their", "there", "they're"), listOf("your", "you're"), listOf("its", "it's"), listOf("whose", "who's"),
            listOf("then", "than"), listOf("lose", "loose"), listOf("quiet", "quite"), listOf("affect", "effect"),
            listOf("accept", "except"), listOf("weather", "whether"), listOf("advice", "advise"), listOf("breath", "breathe"),
            listOf("peace", "piece"),
        )
    }
}
