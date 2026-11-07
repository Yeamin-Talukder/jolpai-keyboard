package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.gesture.BuiltIns
import com.yeamin.jolpai.core.gesture.DoubleTapTracker
import com.yeamin.jolpai.core.gesture.EmojiTab
import com.yeamin.jolpai.core.gesture.GestureAction
import com.yeamin.jolpai.core.gesture.GestureBinding
import com.yeamin.jolpai.core.gesture.GestureConfig
import com.yeamin.jolpai.core.gesture.GestureConflicts
import com.yeamin.jolpai.core.gesture.GestureDispatcher
import com.yeamin.jolpai.core.gesture.GestureThresholds
import com.yeamin.jolpai.core.gesture.GestureType
import com.yeamin.jolpai.core.gesture.GestureZone
import com.yeamin.jolpai.core.gesture.SwipeClassifier
import com.yeamin.jolpai.core.gesture.SwipeDirection
import com.yeamin.jolpai.core.layout.LayoutFactory
import com.yeamin.jolpai.core.model.HostCommand
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.KeyStyle
import com.yeamin.jolpai.core.model.KeyboardPage
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.Panel
import com.yeamin.jolpai.core.settings.KeyboardSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Custom gesture shortcuts: recognition rules, configuration, conflicts and running actions. */
class GestureShortcutsTest {

    private val normal = GestureThresholds.of(GestureConfig())

    // ------------------------------------------------------------------ swipe recognition

    @Test fun `each swipe direction is recognised`() {
        val d = 40f
        assertEquals(SwipeDirection.UP, SwipeClassifier.classify(2f, -d, 36f, normal.dominance))
        assertEquals(SwipeDirection.DOWN, SwipeClassifier.classify(-3f, d, 36f, normal.dominance))
        assertEquals(SwipeDirection.LEFT, SwipeClassifier.classify(-d, 4f, 36f, normal.dominance))
        assertEquals(SwipeDirection.RIGHT, SwipeClassifier.classify(d, -1f, 36f, normal.dominance))
    }

    @Test fun `short movements are not swipes`() {
        assertNull(SwipeClassifier.classify(0f, -35f, 36f, normal.dominance))
        assertNull(SwipeClassifier.classify(20f, 20f, 36f, normal.dominance))
    }

    @Test fun `sensitivity decides how diagonal a swipe may be`() {
        val strict = GestureThresholds.of(GestureConfig(sensitivity = 1)).dominance
        val loose = GestureThresholds.of(GestureConfig(sensitivity = 5)).dominance
        assertTrue(strict > normal.dominance && normal.dominance > loose)
        // 35° from vertical: accepted when loose, rejected when strict.
        val a = Math.toRadians(35.0)
        val dx = (60 * sin(a)).toFloat()
        val dy = -(60 * cos(a)).toFloat()
        assertEquals(SwipeDirection.UP, SwipeClassifier.classify(dx, dy, 36f, loose))
        assertNull(SwipeClassifier.classify(dx, dy, 36f, strict))
    }

    @Test fun `minimum distance and hold time come from the settings`() {
        val t = GestureThresholds.of(GestureConfig(minSwipeDp = 60, longPressMs = 700))
        assertEquals(60f, t.minSwipeDp, 0f)
        assertEquals(700L, t.longPressMs)
        assertNull(SwipeClassifier.classify(0f, -50f, t.minSwipeDp, t.dominance))
        assertEquals(SwipeDirection.UP, SwipeClassifier.classify(0f, -61f, t.minSwipeDp, t.dominance))
    }

    // ------------------------------------------------------------------ double-tap

    @Test fun `double-tap needs the same key within the window`() {
        val taps = DoubleTapTracker(280)
        assertFalse(taps.onTap("comma", 1000))
        assertTrue(taps.onTap("comma", 1200))
        assertFalse(taps.onTap("comma", 1300)) // a third tap starts over
        assertFalse(taps.onTap("comma", 1700)) // too slow after the previous one
        assertFalse(taps.onTap("period", 1800)) // a different key
        assertTrue(taps.onTap("period", 1900))
    }

    // ------------------------------------------------------------------ normal typing stays safe

    @Test fun `ordinary taps never count as swipes`() {
        // 20,000 taps whose finger drifts like a real tap (sd 3 dp): none reaches a swipe,
        // even with the loosest sensitivity and the shortest distance setting.
        val rnd = Random(11)
        val loose = GestureThresholds.of(GestureConfig(sensitivity = 5, minSwipeDp = GestureConfig.MIN_SWIPE_RANGE.first))
        val swipes = (1..20_000).count {
            SwipeClassifier.classify(gauss(rnd) * 3f, gauss(rnd) * 3f, loose.minSwipeDp, loose.dominance) != null
        }
        assertEquals(0, swipes)
    }

    @Test fun `letter keys have no gesture zone, so typing letters is unaffected`() {
        listOf(LayoutId.ENGLISH, LayoutId.AVRO, LayoutId.NATIONAL).forEach { id ->
            listOf(false, true).forEach { shifted ->
                val keys = LayoutFactory().build(id, KeyboardPage.LETTERS, shifted, FieldInfo.DEFAULT).rows.flatMap { it.keys }
                assertTrue("$id", keys.filter { it.style == KeyStyle.CHARACTER }.all { it.zone == null })
                val zones = keys.mapNotNull { it.zone }.toSet()
                assertEquals("$id", setOf(GestureZone.SHIFT, GestureZone.BACKSPACE, GestureZone.SPACE, GestureZone.COMMA, GestureZone.PERIOD), zones)
            }
        }
    }

    private fun gauss(r: Random): Float {
        val u = r.nextDouble().coerceAtLeast(1e-9)
        return (Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * r.nextDouble())).toFloat()
    }

    // ------------------------------------------------------------------ configuration

    @Test fun `defaults turn on only gestures that replace nothing`() {
        val c = GestureConfig()
        val b = BuiltIns()
        assertEquals(GestureAction.EMOJI, c.active(GestureZone.SPACE, GestureType.SWIPE_UP, b)?.action)
        assertEquals(GestureAction.CLIPBOARD, c.active(GestureZone.SPACE, GestureType.SWIPE_DOWN, b)?.action)
        assertNull("language swipes are off (cursor swipe stays)", c.active(GestureZone.SPACE, GestureType.SWIPE_LEFT, b))
        assertNull("comma keeps its menu", c.active(GestureZone.COMMA, GestureType.LONG_PRESS, b))
        assertNull("translation isn't available", c.active(GestureZone.TOOLBAR, GestureType.SWIPE_LEFT, b))
        assertEquals(2, c.activeCount(b))
    }

    @Test fun `configuration survives storage`() {
        val c = GestureConfig(sensitivity = 4, minSwipeDp = 50, longPressMs = 600)
            .with(GestureBinding(GestureZone.PERIOD, GestureType.SWIPE_UP, GestureAction.INSERT_TEXT, text = "www.example.com, ok?"))
            .with(GestureBinding(GestureZone.SPACE, GestureType.SWIPE_UP, GestureAction.VOICE, enabled = false))
        val back = GestureConfig.decode(c.encode())
        assertEquals(c.copy(bindings = c.bindings.sortedBy { it.key }), back.copy(bindings = back.bindings.sortedBy { it.key }))
    }

    @Test fun `missing, broken and outdated configurations are handled safely`() {
        assertEquals(GestureConfig(), GestureConfig.decode(null))
        assertEquals(GestureConfig(), GestureConfig.decode("garbage"))
        val odd = GestureConfig.decode(
            "v1\nenabled=maybe\nsensitivity=99\nminSwipeDp=-4\nlongPressMs=x\n" +
                "b=space,up,emoji,true,\n" +
                "b=space,up,voice,true,\n" + // duplicate gesture: first wins
                "b=moon,up,emoji,true,\n" + // unknown zone
                "b=space,spin,emoji,true,\n" + // unknown gesture
                "b=comma,up,teleport,true,\n" + // unknown action (from a newer version)
                "b=toolbar,up,emoji,true,\n" + // not allowed on the toolbar
                "b=period,down,text,true,%%%\n", // bad text encoding
        )
        assertTrue(odd.enabled)
        assertEquals(5, odd.sensitivity)
        assertEquals(GestureConfig.MIN_SWIPE_RANGE.first, odd.minSwipeDp)
        assertEquals(450, odd.longPressMs)
        assertEquals(listOf("space:up", "period:down"), odd.bindings.map { it.key })
        assertEquals(GestureAction.EMOJI, odd.binding(GestureZone.SPACE, GestureType.SWIPE_UP)?.action)
        assertEquals("", odd.binding(GestureZone.PERIOD, GestureType.SWIPE_DOWN)?.text)
    }

    // ------------------------------------------------------------------ conflicts

    @Test fun `built-in gestures are reserved`() {
        val b = BuiltIns()
        assertNotNull(GestureConflicts.reserved(GestureZone.SPACE, GestureType.LONG_PRESS, b))
        assertNotNull(GestureConflicts.reserved(GestureZone.SHIFT, GestureType.DOUBLE_TAP, b))
        assertNotNull(GestureConflicts.reserved(GestureZone.BACKSPACE, GestureType.LONG_PRESS, b))
        assertNotNull(GestureConflicts.reserved(GestureZone.BACKSPACE, GestureType.SWIPE_LEFT, b))
        assertNotNull(GestureConflicts.reserved(GestureZone.SPACE, GestureType.DOUBLE_TAP, b))
        assertNotNull(GestureConflicts.reserved(GestureZone.TOOLBAR, GestureType.SWIPE_UP, b))
        // Freed when the built-in behaviour is turned off.
        assertNull(GestureConflicts.reserved(GestureZone.BACKSPACE, GestureType.SWIPE_LEFT, BuiltIns(swipeGestures = false)))
        assertNull(GestureConflicts.reserved(GestureZone.SPACE, GestureType.DOUBLE_TAP, BuiltIns(doubleSpacePeriod = false)))
        assertNull(GestureConflicts.reserved(GestureZone.COMMA, GestureType.SWIPE_UP, b))
    }

    @Test fun `replacements and repeated actions are flagged`() {
        val b = BuiltIns()
        assertNotNull(GestureConflicts.replaces(GestureZone.SPACE, GestureType.SWIPE_LEFT, b))
        assertNotNull(GestureConflicts.replaces(GestureZone.COMMA, GestureType.LONG_PRESS, b))
        assertNull(GestureConflicts.replaces(GestureZone.SPACE, GestureType.SWIPE_LEFT, BuiltIns(swipeGestures = false)))
        val c = GestureConfig().with(GestureBinding(GestureZone.SHIFT, GestureType.SWIPE_UP, GestureAction.EMOJI))
        val same = GestureConflicts.sameAction(c, c.binding(GestureZone.SHIFT, GestureType.SWIPE_UP)!!)
        assertEquals(listOf("space:up"), same.map { it.key })
    }

    @Test fun `the dispatcher answers only for usable gestures`() {
        val c = GestureConfig()
            .with(GestureBinding(GestureZone.PERIOD, GestureType.SWIPE_UP, GestureAction.INSERT_TEXT, text = ""))
            .with(GestureBinding(GestureZone.SHIFT, GestureType.DOUBLE_TAP, GestureAction.EMOJI))
        val d = GestureDispatcher(c, BuiltIns())
        assertEquals(KeyAction.Shortcut(GestureAction.EMOJI, ""), d.actionFor(GestureZone.SPACE, GestureType.SWIPE_UP))
        assertNull("no text set", d.actionFor(GestureZone.PERIOD, GestureType.SWIPE_UP))
        assertNull("reserved", d.actionFor(GestureZone.SHIFT, GestureType.DOUBLE_TAP))
        assertNull("unavailable", d.actionFor(GestureZone.TOOLBAR, GestureType.SWIPE_RIGHT))
        assertTrue(d.hasVerticalSwipe(GestureZone.SPACE))
        assertFalse(d.hasSwipe(GestureZone.SHIFT))
        val off = GestureDispatcher(c.copy(enabled = false), BuiltIns())
        assertNull(off.actionFor(GestureZone.SPACE, GestureType.SWIPE_UP))
        val disabled = GestureDispatcher(c.with(c.binding(GestureZone.SPACE, GestureType.SWIPE_UP)!!.copy(enabled = false)), BuiltIns())
        assertNull(disabled.actionFor(GestureZone.SPACE, GestureType.SWIPE_UP))
    }

    // ------------------------------------------------------------------ running actions

    private fun controller(editor: FakeEditor = FakeEditor(), settings: KeyboardSettings = KeyboardSettings(autoCapitalize = false)) =
        KeyboardController(editor, clock = { 0L }).apply {
            updateSettings(settings)
            onStartInput(FieldInfo.DEFAULT, restarting = false)
        }

    @Test fun `shortcuts run the keyboard's own actions`() {
        val c = controller()
        c.onKey(KeyAction.Shortcut(GestureAction.EMOJI))
        assertEquals(Panel.EMOJI, c.state.value.panel)
        c.onKey(KeyAction.Shortcut(GestureAction.CLIPBOARD))
        assertEquals(Panel.CLIPBOARD, c.state.value.panel)
        c.onKey(KeyAction.Shortcut(GestureAction.GIF))
        assertEquals(Panel.EMOJI, c.state.value.panel)
        assertEquals(EmojiTab.GIFS, c.state.value.emojiTab)
        c.onKey(KeyAction.ShowPanel(Panel.NONE))
        c.onKey(KeyAction.ShowPanel(Panel.EMOJI))
        assertNull("a normal open starts on the usual tab", c.state.value.emojiTab)
        c.onKey(KeyAction.ShowPanel(Panel.NONE))
        c.onKey(KeyAction.Shortcut(GestureAction.SYMBOLS))
        assertEquals(KeyboardPage.SYMBOLS, c.state.value.page)
        c.onKey(KeyAction.Shortcut(GestureAction.NEXT_LANGUAGE))
        assertEquals(LayoutId.AVRO, c.state.value.layoutId)
        c.onKey(KeyAction.Shortcut(GestureAction.PREVIOUS_LANGUAGE))
        assertEquals(LayoutId.ENGLISH, c.state.value.layoutId)
    }

    @Test fun `custom text and punctuation are inserted`() {
        val editor = FakeEditor("mail me at name")
        val c = controller(editor)
        c.onKey(KeyAction.Shortcut(GestureAction.INSERT_TEXT, "@gmail.com"))
        assertEquals("mail me at name@gmail.com", editor.text.toString())
        c.onKey(KeyAction.Shortcut(GestureAction.INSERT_PERIOD))
        assertEquals("mail me at name@gmail.com.", editor.text.toString())
        c.onKey(KeyAction.SelectLayout(LayoutId.NATIONAL))
        c.onKey(KeyAction.Shortcut(GestureAction.INSERT_PERIOD))
        assertTrue(editor.text.endsWith("।"))
        val before = editor.text.toString()
        c.onKey(KeyAction.Shortcut(GestureAction.INSERT_TEXT, "")) // nothing set: nothing happens
        assertEquals(before, editor.text.toString())
    }

    @Test fun `service actions are handed over and unavailable ones do nothing`() {
        val c = controller()
        val host = mutableListOf<HostCommand>()
        c.onHostCommand = { host += it }
        c.onKey(KeyAction.Shortcut(GestureAction.SETTINGS))
        c.onKey(KeyAction.Shortcut(GestureAction.TOGGLE_NUMBER_ROW))
        c.onKey(KeyAction.Shortcut(GestureAction.TOGGLE_FLOATING))
        c.onKey(KeyAction.Shortcut(GestureAction.TRANSLATE))
        c.onKey(KeyAction.Shortcut(GestureAction.AI_WRITING))
        assertEquals(listOf(HostCommand.OPEN_SETTINGS, HostCommand.TOGGLE_NUMBER_ROW, HostCommand.TOGGLE_FLOATING), host)
        assertTrue(GestureAction.entries.filter { it.available }.all { it.toKeyActions("x", LayoutId.ENGLISH).isNotEmpty() })
    }

    @Test fun `editing shortcuts reach the editor`() {
        val editor = FakeEditor("abc")
        val c = controller(editor)
        listOf(GestureAction.SELECT_ALL, GestureAction.COPY, GestureAction.CUT, GestureAction.PASTE, GestureAction.UNDO, GestureAction.REDO)
            .forEach { c.onKey(KeyAction.Shortcut(it)) }
        assertEquals(6, editor.commands.size)
    }

    @Test fun `gestures are off in password fields and when switched off`() {
        val c = controller()
        assertNotNull(c.state.value.gestures)
        c.onStartInput(FieldInfo(isPassword = true), restarting = false)
        assertNull(c.state.value.gestures)
        c.onStartInput(FieldInfo.DEFAULT, restarting = false)
        c.updateSettings(KeyboardSettings(gestures = GestureConfig(enabled = false)))
        assertNull(c.state.value.gestures)
    }
}
