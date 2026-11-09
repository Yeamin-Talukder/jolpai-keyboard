package com.yeamin.jolpai.suggest

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Measures what the next-word model adds, on everyday chat-style sentences written for this
 * test (not from the model's training text). Same dictionary and an empty history both ways:
 *
 * - next word: the right word among the 3 offered after a space;
 * - completion: after 1 and 2 letters, the right word among the 2 suggestions the strip shows;
 * - autocorrect: phone slips put into words of 4+ letters; right fixes vs. wrong changes.
 *
 * Prints EVAL lines; the asserts only guard against the model making things worse.
 */
class ModelEvalTest {

    private val dict = NextWordModelTest.run { EnglishDictionary.load() }
    private val old = Suggester({ dict }, { null }, { null }, UserHistory())
    private val new = Suggester({ dict }, { null }, { null }, UserHistory(), model = { NextWordModelTest.model })

    private fun words(s: String) = s.lowercase().split(' ').filter { it.isNotEmpty() }

    private fun nextWordHits(s: Suggester, useModel: Boolean): Pair<Int, Int> {
        var hit = 0
        var total = 0
        SENTENCES.forEach { sentence ->
            val w = words(sentence)
            for (i in w.indices) {
                val offered = s.nextWords(Language.ENGLISH, w.getOrNull(i - 1), 3, SuggestOptions(context = w.take(i), useModel = useModel))
                total++
                if (offered.any { it.equals(w[i], ignoreCase = true) }) hit++
            }
        }
        return hit to total
    }

    private fun completionHits(s: Suggester, useModel: Boolean, letters: Int): Pair<Int, Int> {
        var hit = 0
        var total = 0
        SENTENCES.forEach { sentence ->
            val w = words(sentence)
            for (i in w.indices) {
                if (w[i].length <= letters) continue
                val r = s.english(w[i].take(letters), SuggestOptions(autoCorrect = false, context = w.take(i), useModel = useModel, limit = 2))
                total++
                if (r.candidates.any { it.equals(w[i], ignoreCase = true) }) hit++
            }
        }
        return hit to total
    }

    private data class Ac(var right: Int = 0, var wrong: Int = 0, var total: Int = 0)

    private fun autocorrect(s: Suggester, useModel: Boolean): Ac {
        val r = Random(11)
        val result = Ac()
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        fun neighbour(c: Char): Char {
            val row = rows.first { c in it }
            val i = row.indexOf(c)
            val options = listOfNotNull(row.getOrNull(i - 1), row.getOrNull(i + 1))
            return options[r.nextInt(options.size)]
        }
        SENTENCES.forEach { sentence ->
            val w = words(sentence)
            for (i in w.indices) {
                val word = w[i]
                if (word.length < 4 || !word.all { it in 'a'..'z' }) continue
                val at = r.nextInt(1, word.length)
                val typo = when (r.nextInt(3)) {
                    0 -> word.substring(0, at) + neighbour(word[at]) + word.substring(at + 1)
                    1 -> word.removeRange(at, at + 1)
                    else -> if (at < word.length - 1) word.substring(0, at) + word[at + 1] + word[at] + word.substring(at + 2) else word.dropLast(1)
                }
                if (dict.contains(typo)) continue
                result.total++
                val fix = s.english(typo, SuggestOptions(context = w.take(i), useModel = useModel)).autoCorrect
                when {
                    fix == null -> {}
                    fix.equals(word, ignoreCase = true) -> result.right++
                    else -> result.wrong++
                }
            }
        }
        return result
    }

    private fun pct(p: Pair<Int, Int>) = "%.1f%% (%d/%d)".format(100.0 * p.first / p.second, p.first, p.second)

    @Test fun `model improves next words, completions and autocorrect`() {
        val nwOld = nextWordHits(old, false)
        val nwNew = nextWordHits(new, true)
        println("EVAL next word top-3: without model ${pct(nwOld)}, with model ${pct(nwNew)}")
        for (letters in 1..2) {
            val cOld = completionHits(old, false, letters)
            val cNew = completionHits(new, true, letters)
            println("EVAL after $letters letter(s), top-2: without model ${pct(cOld)}, with model ${pct(cNew)}")
            assertTrue(cNew.first >= cOld.first)
        }
        val aOld = autocorrect(old, false)
        val aNew = autocorrect(new, true)
        println("EVAL autocorrect (${aOld.total} slips): without model right ${aOld.right}, wrong ${aOld.wrong}; with model right ${aNew.right}, wrong ${aNew.wrong}")
        assertTrue(nwNew.first > nwOld.first)
        assertTrue(aNew.right - aNew.wrong >= aOld.right - aOld.wrong)
    }

    @Test fun `sweep the model weight`() {
        if (System.getenv("LM_SWEEP") == null) return
        for (weight in listOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f)) {
            val s = Suggester({ dict }, { null }, { null }, UserHistory(), model = { NextWordModelTest.model }, modelWeight = weight)
            val c1 = completionHits(s, true, 1)
            val c2 = completionHits(s, true, 2)
            val a = autocorrect(s, true)
            println("SWEEP weight $weight: after 1 ${pct(c1)}, after 2 ${pct(c2)}, autocorrect right ${a.right} wrong ${a.wrong}")
        }
    }

    companion object {
        /** Everyday messages, written for this test. */
        val SENTENCES = listOf(
            "hey are you coming to the party tonight",
            "i will be there in ten minutes",
            "can you send me the address please",
            "thank you so much for your help",
            "i am running a little late",
            "what time does the movie start",
            "let me know when you get home",
            "did you see the game last night",
            "i think we should leave early tomorrow",
            "my phone is about to die",
            "where did you put the keys",
            "happy birthday i hope you have a great day",
            "we are going to the beach this weekend",
            "please call me back when you are free",
            "i forgot my wallet at home",
            "how was your day at work",
            "the meeting has been moved to friday",
            "do you want to grab lunch later",
            "i really need to get some sleep",
            "can you pick up some milk on the way",
            "sorry i missed your call",
            "the weather is really nice today",
            "i just finished my exam",
            "we should go out for dinner sometime",
            "are you still at the office",
            "i will send you the photos tonight",
            "the train is delayed again",
            "she said she would be here soon",
            "what are you doing this evening",
            "i can not find my charger anywhere",
            "this is the best pizza i have ever had",
            "my mom wants to talk to you",
            "we need to buy a new car",
            "have a safe trip and text me when you land",
            "i am so proud of you",
            "the kids are already asleep",
            "let us meet at the coffee shop",
            "i have a lot of work to do",
            "do not forget to bring your jacket",
            "the doctor said it is nothing serious",
            "good morning have a nice day",
            "i will be home for dinner",
            "can we talk about this later",
            "he is not feeling well today",
            "the store closes at nine",
            "i love this song so much",
            "we won the match last night",
            "please let me know if you need anything",
            "i am on my way",
            "it was really nice to meet you",
            "my flight leaves at six in the morning",
            "could you help me with my homework",
            "the battery is almost dead",
            "i think it is going to rain",
            "see you at the station",
            "what do you want for your birthday",
            "i have been waiting for an hour",
            "the new restaurant is really good",
            "can you believe it is already december",
            "i need to finish this report by monday",
        )
    }
}
