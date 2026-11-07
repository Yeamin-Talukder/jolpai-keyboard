package com.yeamin.jolpai.core.editor

import com.yeamin.jolpai.core.field.FieldInfo

/**
 * A text field that lives inside the keyboard: the search box of a panel (emoji, GIF,
 * clipboard). While searching, the keys type here instead of into the app.
 * The cursor is always at the end; composing text (Avro) is the tail.
 */
class InMemoryEditor : TextEditor {
    private val buffer = StringBuilder()
    private var composingStart = -1

    val text: String get() = buffer.toString()

    fun clear() {
        buffer.setLength(0)
        composingStart = -1
    }

    override fun textBeforeCursor(maxChars: Int): CharSequence = buffer.substring(maxOf(0, buffer.length - maxChars))
    override fun textAfterCursor(maxChars: Int): CharSequence = ""
    override fun hasSelection(): Boolean = false
    override fun selectionStart(): Int = buffer.length
    override fun selectionEnd(): Int = buffer.length
    override fun setSelection(start: Int, end: Int) = Unit

    override fun commit(text: CharSequence) {
        if (composingStart >= 0) buffer.setLength(composingStart)
        composingStart = -1
        buffer.append(text)
    }

    override fun setComposing(text: CharSequence) {
        if (composingStart < 0) composingStart = buffer.length else buffer.setLength(composingStart)
        buffer.append(text)
    }

    override fun finishComposing() {
        composingStart = -1
    }

    override fun replaceBefore(deleteCount: Int, text: CharSequence) {
        composingStart = -1
        buffer.setLength(maxOf(0, buffer.length - deleteCount))
        buffer.append(text)
    }

    override fun deleteBackward() {
        composingStart = -1
        buffer.setLength(buffer.length - Deletion.lengthToDelete(buffer))
    }

    /** Enter ends the search; the controller handles that before it reaches here. */
    override fun performEnter(field: FieldInfo) = Unit

    override fun run(command: EditorCommand, extendSelection: Boolean) = Unit

    override fun isCapsModeAtCursor(): Boolean = false
}
