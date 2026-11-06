package com.yeamin.lipi.core

import com.yeamin.lipi.core.editor.Deletion
import com.yeamin.lipi.core.editor.EditorCommand
import com.yeamin.lipi.core.editor.TextEditor
import com.yeamin.lipi.core.field.EnterAction
import com.yeamin.lipi.core.field.FieldInfo

/** An in-memory text field with the cursor always at the end. Composing text, if any, is the tail. */
class FakeEditor(initial: String = "") : TextEditor {
    val text = StringBuilder(initial)
    var editorActions = mutableListOf<EnterAction>()

    /** Start of the composing region, or -1. The region always runs to the end of [text]. */
    var composingStart = -1
        private set

    val composingText: String? get() = if (composingStart < 0) null else text.substring(composingStart)

    /** Mimics `TYPE_TEXT_FLAG_CAP_SENTENCES`: caps at the start and after ". " / "! " / "? " / newline. */
    var capSentences = true

    /** Commands sent to the app, with whether they extended the selection. */
    val commands = mutableListOf<Pair<EditorCommand, Boolean>>()
    var selection: Pair<Int, Int>? = null

    override fun textBeforeCursor(maxChars: Int): CharSequence = text.substring(maxOf(0, text.length - maxChars))
    override fun textAfterCursor(maxChars: Int): CharSequence = ""
    override fun hasSelection() = selection.let { it != null && it.first != it.second }
    override fun selectionStart(): Int = selection?.first ?: text.length
    override fun selectionEnd(): Int = selection?.second ?: text.length
    override fun setSelection(start: Int, end: Int) {
        selection = start to end
    }

    override fun run(command: EditorCommand, extendSelection: Boolean) {
        commands += command to extendSelection
    }

    override fun commit(text: CharSequence) {
        if (composingStart >= 0) this.text.setLength(composingStart)
        composingStart = -1
        this.text.append(text)
    }

    override fun setComposing(text: CharSequence) {
        if (composingStart < 0) composingStart = this.text.length else this.text.setLength(composingStart)
        this.text.append(text)
    }

    override fun finishComposing() {
        composingStart = -1
    }

    override fun replaceBefore(deleteCount: Int, text: CharSequence) {
        composingStart = -1
        this.text.setLength(this.text.length - deleteCount)
        this.text.append(text)
    }

    override fun deleteBackward() {
        composingStart = -1
        selection?.let { (s, e) ->
            if (s != e) {
                text.delete(s, e)
                selection = null
                return
            }
        }
        val n = Deletion.lengthToDelete(text)
        text.setLength(text.length - n)
    }

    override fun performEnter(field: FieldInfo) {
        if (field.enterAction == EnterAction.NEWLINE) text.append('\n') else editorActions += field.enterAction
    }

    override fun isCapsModeAtCursor(): Boolean {
        if (!capSentences) return false
        val t = text.toString().trimEnd(' ')
        if (t.isEmpty()) return true
        if (text.isNotEmpty() && text.last() != ' ' && text.last() != '\n') return false
        return t.last() in ".!?\n" || t.endsWith("\n")
    }
}
