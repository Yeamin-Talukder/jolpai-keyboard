package com.yeamin.lipi.core.input

import com.yeamin.lipi.bangla.NationalComposer
import com.yeamin.lipi.bangla.phonetic.PhoneticEngine
import com.yeamin.lipi.core.editor.TextEditor
import com.yeamin.lipi.suggest.AvroCandidates

/**
 * The word being typed in a composing layout: the Roman keys pressed, the Bangla shown
 * ([bangla], which is `candidates[selected]`), and the other candidates.
 */
data class Composition(
    val roman: String,
    val bangla: String,
    val candidates: List<String> = listOf(bangla),
    val selected: Int = 0,
)

/**
 * Turns key presses for one layout into edits. One instance per layout, kept for the
 * lifetime of the keyboard service.
 */
interface InputProcessor {
    fun onText(text: String, editor: TextEditor)
    fun onSpace(editor: TextEditor, nowMillis: Long)
    fun onDelete(editor: TextEditor)

    /** The word being composed, or null when nothing is being composed. */
    val composition: Composition? get() = null

    /** Commits any word being composed as it is shown (before Enter, a layout switch, …). */
    fun finishComposing(editor: TextEditor) = Unit

    /** Replaces the word being composed with [text] (a tapped candidate) and adds a space. */
    fun commitCandidate(text: String, editor: TextEditor, nowMillis: Long) {
        editor.commit(text)
        onSpace(editor, nowMillis)
    }

    /** The user moved the cursor away: keep the composed text and forget the word. */
    fun onCursorMoved(editor: TextEditor) = Unit

    /** Forget any per-word state (field changed, input finished). Does not touch the text. */
    fun reset()
}

/**
 * Shared behaviour for layouts that commit text straight away:
 * double-space inserts the layout's full stop ("." or "।") followed by a space.
 */
abstract class DirectInputProcessor(
    private val fullStop: String,
    private val doubleSpaceEnabled: () -> Boolean,
) : InputProcessor {

    private var lastSpaceAt = NEVER

    override fun onSpace(editor: TextEditor, nowMillis: Long) {
        val recent = lastSpaceAt != NEVER && nowMillis - lastSpaceAt <= DOUBLE_SPACE_WINDOW_MS
        if (recent && doubleSpaceEnabled() && !editor.hasSelection()) {
            val before = editor.textBeforeCursor(2)
            if (before.length == 2 && before[1] == ' ' && endsWord(before[0])) {
                editor.replaceBefore(1, "$fullStop ")
                lastSpaceAt = NEVER // a third space is just a space
                return
            }
        }
        editor.commit(" ")
        lastSpaceAt = nowMillis
    }

    override fun onDelete(editor: TextEditor) {
        forgetSpace()
        editor.deleteBackward()
    }

    override fun onText(text: String, editor: TextEditor) {
        forgetSpace()
        insert(text, editor)
    }

    protected abstract fun insert(text: String, editor: TextEditor)

    protected fun forgetSpace() {
        lastSpaceAt = NEVER
    }

    override fun reset() = forgetSpace()

    private fun endsWord(c: Char): Boolean {
        if (Character.isLetterOrDigit(c)) return true
        val type = Character.getType(c)
        // Bangla kars and signs (ি া ং …) are marks, not letters, but they end a word.
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
        const val DOUBLE_SPACE_WINDOW_MS = 1000L
    }
}

class EnglishProcessor(doubleSpaceEnabled: () -> Boolean) : DirectInputProcessor(".", doubleSpaceEnabled) {
    override fun insert(text: String, editor: TextEditor) = editor.commit(text)
}

class NationalProcessor(
    doubleSpaceEnabled: () -> Boolean,
    private val composer: NationalComposer = NationalComposer(),
) : DirectInputProcessor("।", doubleSpaceEnabled) {

    override fun insert(text: String, editor: TextEditor) {
        // When text is selected the new text replaces it, so nothing before the cursor combines.
        val before = if (editor.hasSelection()) "" else editor.textBeforeCursor(CONTEXT)
        val edit = composer.compose(before, text)
        if (edit.deleteBefore > 0) editor.replaceBefore(edit.deleteBefore, edit.insert) else editor.commit(edit.insert)
    }

    private companion object {
        /** Longest sequence the composer looks back over (a 3-consonant cluster + reph). */
        const val CONTEXT = 8
    }
}

/**
 * Avro phonetic typing. Roman keys collect into a word buffer; the whole word is converted
 * on every key press (Avro's rules depend on the surrounding letters) and shown as composing
 * text. Space, Enter, a layout switch, or tapping a candidate commits it.
 *
 * Backspace while composing removes the last *Roman* key, so `amr` → `am` turns আম্র back into আম.
 */
class AvroProcessor(
    private val engine: () -> PhoneticEngine,
    doubleSpaceEnabled: () -> Boolean,
    /** Dictionary candidates for a Roman word, or null when they aren't available (not loaded, or off). */
    private val candidatesFor: (String) -> AvroCandidates? = { null },
) : DirectInputProcessor("।", doubleSpaceEnabled) {

    private val roman = StringBuilder()
    private var bangla = ""
    private var candidates: List<String> = emptyList()
    private var selected = 0

    override val composition: Composition?
        get() = if (roman.isEmpty()) null else Composition(roman.toString(), bangla, candidates, selected)

    override fun insert(text: String, editor: TextEditor) {
        roman.append(text)
        show(editor)
    }

    override fun onSpace(editor: TextEditor, nowMillis: Long) {
        finishComposing(editor)
        super.onSpace(editor, nowMillis)
    }

    override fun onDelete(editor: TextEditor) {
        if (roman.isEmpty()) {
            super.onDelete(editor)
            return
        }
        forgetSpace()
        roman.setLength(roman.length - 1)
        if (roman.isEmpty()) {
            bangla = ""
            editor.setComposing("")
            editor.finishComposing()
        } else {
            show(editor)
        }
    }

    override fun finishComposing(editor: TextEditor) {
        if (roman.isEmpty()) return
        editor.commit(bangla)
        clear()
    }

    override fun commitCandidate(text: String, editor: TextEditor, nowMillis: Long) {
        editor.commit(text)
        clear()
        super.onSpace(editor, nowMillis)
    }

    override fun onCursorMoved(editor: TextEditor) {
        if (roman.isEmpty()) return
        editor.finishComposing()
        clear()
    }

    override fun reset() {
        super.reset()
        clear()
    }

    /**
     * Shows the default candidate: the user's previous pick for this word, else the best
     * dictionary match, else the rule-based conversion.
     */
    private fun show(editor: TextEditor) {
        val term = roman.toString()
        val c = candidatesFor(term)
        if (c != null && c.candidates.isNotEmpty()) {
            candidates = c.candidates
            selected = c.selected.coerceIn(0, c.candidates.lastIndex)
            bangla = c.candidates[selected]
        } else {
            bangla = engine().convert(term)
            candidates = if (bangla == term) listOf(bangla) else listOf(bangla, term)
            selected = 0
        }
        editor.setComposing(bangla)
    }

    private fun clear() {
        roman.setLength(0)
        bangla = ""
        candidates = emptyList()
        selected = 0
    }
}
