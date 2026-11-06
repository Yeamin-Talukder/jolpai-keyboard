package com.yeamin.lipi.core

import com.yeamin.lipi.bangla.phonetic.AvroGrammar
import com.yeamin.lipi.bangla.phonetic.PhoneticEngine
import com.yeamin.lipi.core.field.FieldInfo
import com.yeamin.lipi.core.layout.NationalKeyMap
import com.yeamin.lipi.core.model.KeyAction
import com.yeamin.lipi.core.model.LayoutId
import com.yeamin.lipi.core.settings.KeyboardSettings
import com.yeamin.lipi.core.touch.TouchCalibration
import com.yeamin.lipi.core.touch.TouchResolver
import com.yeamin.lipi.core.touch.TouchTarget
import com.yeamin.lipi.suggest.AvroDictionarySearch
import com.yeamin.lipi.suggest.AvroSuggester
import com.yeamin.lipi.suggest.BanglaDictionary
import com.yeamin.lipi.suggest.EnglishDictionary
import com.yeamin.lipi.suggest.NextCharPredictor
import com.yeamin.lipi.suggest.NextChars
import com.yeamin.lipi.suggest.Suggester
import com.yeamin.lipi.suggest.TypingScript
import com.yeamin.lipi.suggest.UserHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Predictive touch targets: the resolver's safeguards, calibration, and a typing simulation
 * that measures accuracy with the feature off and on (results are printed and asserted).
 */
class PredictiveTouchTest {

    companion object {
        private val english by lazy { EnglishDictionary.load() }
        private val bangla by lazy { BanglaDictionary.load() }
        private val avro by lazy { AvroSuggester.load(PhoneticEngine(AvroGrammar.load()), AvroDictionarySearch.load(bangla)) }
        private val predictor by lazy { NextCharPredictor({ english }, { bangla }, { avro }, UserHistory()) }

        /** Key width 1, height 1.4 (phone proportions); rows staggered like the real layouts. */
        private const val H = 1.4f

        fun grid(rows: List<List<Char?>>, offsets: List<Float>): List<TouchTarget> = buildList {
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { i, c ->
                    val left = offsets[r] + i
                    add(TouchTarget(left, r * H, left + 1, (r + 1) * H, c))
                }
            }
        }

        val QWERTY = grid(
            listOf("qwertyuiop".toList(), "asdfghjkl".toList(), listOf<Char?>(null) + "zxcvbnm".toList()),
            listOf(0f, 0.5f, 0f),
        ).let { keys -> keys.filterIndexed { i, _ -> i != 19 } + keys[19] } // shift key last, still a target

        val NATIONAL = grid(
            NationalKeyMap.NORMAL.mapIndexed { r, row -> (if (r == 2) listOf<Char?>(null) else emptyList()) + row.map { it[0] } },
            listOf(0f, 0.5f, 0f),
        )
    }

    private fun keyOf(targets: List<TouchTarget>, c: Char) = targets.indexOfFirst { it.char == c }

    /** The key whose rectangle holds the point, else the nearest centre (the plain keyboard). */
    private fun direct(targets: List<TouchTarget>, x: Float, y: Float): Int {
        val inside = targets.indexOfFirst { x >= it.left && x < it.right && y >= it.top && y < it.bottom }
        if (inside >= 0) return inside
        return targets.indices.minByOrNull { val t = targets[it]; (t.centerX - x) * (t.centerX - x) + (t.centerY - y) * (t.centerY - y) }!!
    }

    private class Result(val baseline: Float, val predictive: Float) {
        override fun toString() = "plain %.1f%% → predictive %.1f%%".format(baseline * 100, predictive * 100)
    }

    /**
     * Types [words] with touches scattered around each intended key (sd 0.27 key widths
     * sideways, 0.22 key heights up/down: realistic thumb error), once on a plain keyboard
     * and once with predictive touch, and returns the share of letters typed correctly.
     */
    private fun simulate(words: List<String>, targets: List<TouchTarget>, script: TypingScript, seed: Int = 7): Result {
        fun run(predictive: Boolean): Float {
            val rnd = Random(seed)
            var ok = 0
            var total = 0
            for (word in words) {
                val typed = StringBuilder()
                for (c in word) {
                    val k = targets[keyOf(targets, c)]
                    val x = k.centerX + gaussian(rnd) * 0.27f * k.width
                    val y = k.centerY + gaussian(rnd) * 0.22f * k.height
                    val d = direct(targets, x, y)
                    val chosen = if (predictive) TouchResolver.resolve(targets, x, y, d, predictor.predict(script, typed.toString())) else d
                    val out = targets[chosen].char ?: '?'
                    typed.append(out)
                    total++
                    if (out == c) ok++
                }
            }
            return ok.toFloat() / total
        }
        return Result(run(false), run(true))
    }

    private fun gaussian(r: Random): Float {
        val u = r.nextDouble().coerceAtLeast(1e-9)
        val v = r.nextDouble()
        return (Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v)).toFloat()
    }

    /** Frequent dictionary words: the top completions for every two-letter start. */
    private val commonWords: List<String> by lazy {
        ('a'..'z').flatMap { a -> ('a'..'z').flatMap { b -> english.completions("$a$b", 3).map { it.word.lowercase() } } }
            .filter { w -> w.length >= 3 && w.all { it in 'a'..'z' } }
            .distinct()
            .sortedByDescending { english.lookup(it)?.frequency ?: 0 }
            .take(600)
    }

    // ------------------------------------------------------------------ prediction

    @Test fun `lik predicts e`() {
        val next = predictor.predict(TypingScript.ENGLISH, "lik")
        assertTrue(next.toString(), next.probability('e') > 0.5f)
    }

    @Test fun `bangla and avro predictions exist`() {
        assertTrue(!predictor.predict(TypingScript.NATIONAL, "কর").isEmpty)
        val avroNext = predictor.predict(TypingScript.AVRO, "bhal")
        assertTrue(avroNext.toString(), avroNext.probability('o') > avroNext.probability('x'))
    }

    // ------------------------------------------------------------------ safeguards

    @Test fun `a touch between e and r after lik types e, after fo types r`() {
        val e = QWERTY[keyOf(QWERTY, 'e')]
        val x = e.right + 0.02f // just inside r
        val y = e.centerY
        val d = direct(QWERTY, x, y)
        assertEquals('r', QWERTY[d].char)
        assertEquals('e', QWERTY[TouchResolver.resolve(QWERTY, x, y, d, predictor.predict(TypingScript.ENGLISH, "lik"))].char)
        assertEquals('r', QWERTY[TouchResolver.resolve(QWERTY, x, y, d, predictor.predict(TypingScript.ENGLISH, "fo"))].char)
    }

    @Test fun `a clear hit in the middle of a key always types that key`() {
        val r = QWERTY[keyOf(QWERTY, 'r')]
        val x = r.centerX - r.width * 0.15f
        val d = direct(QWERTY, x, r.centerY)
        assertEquals('r', QWERTY[TouchResolver.resolve(QWERTY, x, r.centerY, d, predictor.predict(TypingScript.ENGLISH, "lik"))].char)
    }

    @Test fun `a likely key cannot reach beyond its limit`() {
        val e = QWERTY[keyOf(QWERTY, 'e')]
        val x = e.centerX + e.width * (0.5f + TouchResolver.MAX_EXPAND + 0.05f) // in r, past e's reach
        val d = direct(QWERTY, x, e.centerY)
        assertEquals('r', QWERTY[TouchResolver.resolve(QWERTY, x, e.centerY, d, predictor.predict(TypingScript.ENGLISH, "lik"))].char)
    }

    @Test fun `function keys are never taken over`() {
        val shift = QWERTY.indexOfFirst { it.char == null }
        val t = QWERTY[shift]
        val x = t.right - 0.05f
        assertEquals(shift, TouchResolver.resolve(QWERTY, x, t.centerY, shift, predictor.predict(TypingScript.ENGLISH, "")))
    }

    @Test fun `no prediction means the plain key`() {
        val e = QWERTY[keyOf(QWERTY, 'e')]
        val d = direct(QWERTY, e.right + 0.02f, e.centerY)
        assertEquals(d, TouchResolver.resolve(QWERTY, e.right + 0.02f, e.centerY, d, null))
    }

    @Test fun `resolving is fast enough for every touch`() {
        val next = predictor.predict(TypingScript.ENGLISH, "th")
        val start = System.nanoTime()
        var sink = 0
        repeat(100_000) { i -> sink += TouchResolver.resolve(QWERTY, (i % 100) / 10f, 0.7f, i % 10, next) }
        val perTouchUs = (System.nanoTime() - start) / 100_000 / 1000.0
        println("predictive touch: %.2f µs per touch".format(perTouchUs))
        assertTrue("$perTouchUs µs", perTouchUs < 50 && sink >= 0)
    }

    // ------------------------------------------------------------------ calibration

    @Test fun `calibration learns slowly, stays limited, and round-trips`() {
        val cal = TouchCalibration()
        repeat(200) { cal.learn('e', 0.6f, 0.2f, TouchCalibration.TAP_RATE) }
        assertEquals(TouchCalibration.LIMIT, cal.offsetX('e'), 0.001f)
        assertEquals(0.2f, cal.offsetY('e'), 0.01f)
        val copy = TouchCalibration().apply { decode(cal.encode()) }
        assertEquals(cal.offsetX('e'), copy.offsetX('e'), 0.0001f)
        cal.learn('q', 3f, 0f, 1f) // nowhere near: ignored
        assertEquals(0f, cal.offsetX('q'), 0f)
        cal.clear()
        assertTrue(cal.isEmpty)
    }

    @Test fun `calibration moves a boundary toward where the user taps`() {
        val e = QWERTY[keyOf(QWERTY, 'e')]
        val x = e.right + 0.12f
        val d = direct(QWERTY, x, e.centerY)
        val flat = NextChars(TypingScript.ENGLISH, "", ('a'..'z').associateWith { 1f / 26 }) // no preference
        assertEquals('r', QWERTY[TouchResolver.resolve(QWERTY, x, e.centerY, d, flat)].char)
        val cal = TouchCalibration().apply { repeat(100) { learn('e', 0.3f, 0f, 0.2f) } } // this user taps e to the right
        val withCal = TouchResolver.resolve(QWERTY, x, e.centerY, d, flat, cal)
        assertEquals('e', QWERTY[withCal].char)
    }

    // ------------------------------------------------------------------ accuracy (before / after)

    @Test fun `common English words are typed more accurately`() {
        val r = simulate(commonWords, QWERTY, TypingScript.ENGLISH)
        println("English common words (${commonWords.size}): $r")
        assertTrue(r.toString(), r.predictive > r.baseline + 0.015f)
    }

    @Test fun `uncommon words, names and technical terms are not made worse`() {
        val words = listOf(
            "yeamin", "kotlin", "gradle", "xiaomi", "dhaka", "chattogram", "sylhet", "jetpack", "compose", "hilt",
            "kubernetes", "nginx", "postgres", "async", "mutex", "lambda", "zephyr", "quixotic", "fjord", "sphinx",
            "rahman", "hossain", "tasnim", "nusrat", "farhana", "mymensingh", "khulna", "rajshahi", "barishal", "rangpur",
        )
        val r = simulate(words, QWERTY, TypingScript.ENGLISH)
        println("English names/terms (${words.size}): $r")
        assertTrue(r.toString(), r.predictive >= r.baseline - 0.02f)
    }

    @Test fun `random letters are not made noticeably worse`() {
        val rnd = Random(3)
        val words = List(300) { String(CharArray(3 + rnd.nextInt(6)) { 'a' + rnd.nextInt(26) }) }
        val r = simulate(words, QWERTY, TypingScript.ENGLISH)
        println("Random letters: $r")
        assertTrue(r.toString(), r.predictive >= r.baseline - 0.02f)
    }

    @Test fun `avro phonetic words are typed at least as accurately`() {
        val words = listOf(
            "ami", "tumi", "bangla", "bhalo", "kemon", "achi", "amar", "shonar", "desh", "bhalobashi",
            "kotha", "bolo", "jabo", "kobe", "ekhon", "porjonto", "shokal", "bikal", "raat", "din",
            "manush", "pani", "khabar", "bari", "school", "boi", "pora", "lekha", "gaan", "shundor",
        )
        val r = simulate(words, QWERTY, TypingScript.AVRO)
        println("Avro words (${words.size}): $r")
        assertTrue(r.toString(), r.predictive >= r.baseline)
    }

    @Test fun `national layout words are typed at least as accurately`() {
        val unshifted = NationalKeyMap.NORMAL.flatten().filter { it.length == 1 }.map { it[0] }.toSet()
        val words = listOf("কর", "বল", "মন", "দেখ", "সময়", "কাজ", "নতুন", "কথা", "বাড়", "জান", "তার", "মান")
            .flatMap { bangla.completions(it, 15) }
            .filter { w -> w.length >= 2 && w.all { it in unshifted } }
            .distinct()
            .take(300)
        val r = simulate(words, NATIONAL, TypingScript.NATIONAL)
        println("জাতীয় words (${words.size}): $r")
        assertTrue(r.toString(), words.size >= 50 && r.predictive >= r.baseline)
    }

    // ------------------------------------------------------------------ controller

    @Test fun `the controller publishes predictions only where they belong`() {
        val history = UserHistory()
        val suggester = Suggester({ english }, { bangla }, { avro }, history)
        val editor = FakeEditor()
        val controller = KeyboardController(editor, clock = { 0L }, suggester = suggester).apply {
            updateSettings(KeyboardSettings(autoCapitalize = false))
            onStartInput(FieldInfo.DEFAULT, restarting = false)
        }
        "lik".forEach { controller.onKey(KeyAction.Text(it.toString())) }
        val next = controller.state.value.nextChars
        assertNotNull(next)
        assertTrue(next!!.probability('e') > 0.5f)

        controller.onStartInput(FieldInfo(isPassword = true), restarting = false)
        assertNull(controller.state.value.nextChars)

        controller.onStartInput(FieldInfo.DEFAULT, restarting = false)
        controller.updateSettings(KeyboardSettings(predictiveTouch = false))
        controller.onKey(KeyAction.Text("a"))
        assertNull(controller.state.value.nextChars)

        controller.updateSettings(KeyboardSettings())
        controller.onKey(KeyAction.SelectLayout(LayoutId.NATIONAL))
        controller.onKey(KeyAction.Text("ক"))
        assertNotNull(controller.state.value.nextChars)
    }
}
