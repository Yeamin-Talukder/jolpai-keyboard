package com.yeamin.jolpai.core.editor

/**
 * Jolpai's own undo and redo for the field being typed in, so Undo works in every app.
 * (Sending the app Ctrl+Z, the only other way, does nothing in most plain text boxes.)
 *
 * It keeps snapshots of the text just before the cursor, taken at natural steps: a typed word
 * with its space or punctuation, a run of deletes, a paste, a picked suggestion, an inserted
 * snippet. Undo puts the text before the cursor back the way it was at the last step (only
 * the part that differs is replaced, as one edit); redo goes forward again. A new edit after
 * an undo drops the redo steps, as everywhere else.
 *
 * Safety: the history only applies while the cursor is where Jolpai's last edit left it. If the
 * text just before the cursor isn't what it was (the user tapped elsewhere, or the app changed
 * the text), the old steps are forgotten instead of being applied in the wrong place, and the
 * caller falls back to asking the app. Plain Kotlin, unit-tested with [InMemoryEditor].
 */
class UndoHistory(
    private val maxSteps: Int = 40,
    /** How much text before the cursor a snapshot keeps. */
    private val window: Int = 2000,
) {
    /** What kind of edit a key press makes, for finding step boundaries. */
    enum class Kind {
        /** A letter (part of a word). */
        TYPE,
        /** Space, Enter or punctuation: stays in the step before it (the word, paste or suggestion). */
        WORD_END,
        /** Backspace (a run of them is one step). */
        DELETE,
        /** Paste, a picked suggestion, a snippet: always its own step. */
        INSERT,
        /** Doesn't change the text (Shift, panels, layouts): no step. */
        OTHER,
    }

    private class Snap(val before: String, val selected: String)

    private val undoSteps = ArrayDeque<Snap>()
    private val redoSteps = ArrayDeque<Snap>()
    private var lastKind = Kind.OTHER
    private var lastTime = Long.MIN_VALUE

    /** The text just before the cursor right after our last edit, to notice the cursor moving away. */
    private var lastTail: String? = null

    val canUndo: Boolean get() = undoSteps.isNotEmpty()
    val canRedo: Boolean get() = redoSteps.isNotEmpty()

    /** Forgets everything (a new field). */
    fun clear() {
        undoSteps.clear()
        redoSteps.clear()
        lastKind = Kind.OTHER
        lastTime = Long.MIN_VALUE
        lastTail = null
    }

    /** Call before a key press of [kind] changes the text in [editor], at time [now] (ms). */
    fun before(kind: Kind, editor: TextEditor, now: Long) {
        if (kind == Kind.OTHER) return
        if (!stillHere(editor)) {
            // The cursor moved (or the app changed the text): earlier steps no longer fit here.
            undoSteps.clear()
            redoSteps.clear()
            lastKind = Kind.OTHER
        }
        val pause = lastTime != Long.MIN_VALUE && now - lastTime > PAUSE_MS
        val boundary = when (kind) {
            Kind.TYPE -> lastKind != Kind.TYPE || pause
            // A space or full stop joins the word, paste or suggestion before it.
            Kind.WORD_END -> lastKind == Kind.DELETE || lastKind == Kind.OTHER || pause
            Kind.DELETE -> lastKind != Kind.DELETE || pause
            Kind.INSERT -> true
            Kind.OTHER -> false
        }
        if (boundary) {
            val snap = Snap(editor.textBeforeCursor(window).toString(), editor.selectedText(window).toString())
            val top = undoSteps.lastOrNull()
            if (top == null || top.before != snap.before || top.selected != snap.selected) {
                undoSteps.addLast(snap)
                if (undoSteps.size > maxSteps) undoSteps.removeFirst()
            }
            redoSteps.clear()
        }
        lastKind = kind
        lastTime = now
    }

    /** Call after every key press: remembers where the cursor is now. */
    fun after(editor: TextEditor) {
        lastTail = editor.textBeforeCursor(TAIL).toString()
    }

    /**
     * Undoes the last step in [editor]. False when there is nothing of ours to undo here (the
     * caller can then ask the app).
     */
    fun undo(editor: TextEditor): Boolean = move(editor, from = undoSteps, to = redoSteps)

    /** Redoes the last undone step. False when there is nothing to redo here. */
    fun redo(editor: TextEditor): Boolean = move(editor, from = redoSteps, to = undoSteps)

    private fun move(editor: TextEditor, from: ArrayDeque<Snap>, to: ArrayDeque<Snap>): Boolean {
        if (editor.hasSelection() || !stillHere(editor)) {
            clear()
            return false
        }
        val current = editor.textBeforeCursor(window).toString()
        while (from.isNotEmpty()) {
            val target = from.removeLast()
            if (target.before == current && target.selected.isEmpty()) continue // that step changed nothing
            val edit = TextDiff.between(current, target.before, window)
            if (edit == null) {
                clear()
                return false
            }
            editor.replaceBefore(edit.deleteCount, edit.insert + target.selected)
            to.addLast(Snap(current, ""))
            lastKind = Kind.OTHER // the next key press starts a new step
            after(editor)
            return true
        }
        return false
    }

    private fun stillHere(editor: TextEditor): Boolean {
        val tail = lastTail ?: return true
        return editor.textBeforeCursor(TAIL).toString() == tail
    }

    private companion object {
        /** A pause this long in typing starts a new step even mid-word. */
        const val PAUSE_MS = 2500L
        /** Characters compared to check the cursor hasn't moved. */
        const val TAIL = 48
    }
}

/**
 * The single edit at the end of a text that turns one version into another, for undo: delete
 * [deleteCount] characters before the cursor, then insert [insert].
 */
data class TextEdit(val deleteCount: Int, val insert: String)

object TextDiff {
    /** Characters used to line up two windows of text that don't both start at the document's start. */
    private const val ANCHOR = 32

    /**
     * The edit at the cursor that turns [current] into [target], both being the last (up to
     * [window]) characters before the cursor; null when they can't be lined up safely.
     */
    fun between(current: String, target: String, window: Int): TextEdit? {
        val (oc, ot) = align(current, target, window) ?: return null
        var p = 0
        while (oc + p < current.length && ot + p < target.length && current[oc + p] == target[ot + p]) p++
        // Never split a surrogate pair (emoji and other characters beyond the basic plane).
        if (p > 0 && Character.isHighSurrogate(current[oc + p - 1])) p--
        return TextEdit(current.length - (oc + p), target.substring(ot + p))
    }

    /** Where [a] and [b] start describing the same place in the document. */
    private fun align(a: String, b: String, window: Int): Pair<Int, Int>? {
        val aWhole = a.length < window
        val bWhole = b.length < window
        if (aWhole && bWhole) return 0 to 0 // both reach back to the start of the text
        // A cut-off window starts later in the document: find its first characters in the other.
        if (!aWhole && a.length >= ANCHOR) {
            val i = b.indexOf(a.substring(0, ANCHOR))
            if (i >= 0) return 0 to i
        }
        if (!bWhole && b.length >= ANCHOR) {
            val j = a.indexOf(b.substring(0, ANCHOR))
            if (j >= 0) return j to 0
        }
        return null
    }
}
