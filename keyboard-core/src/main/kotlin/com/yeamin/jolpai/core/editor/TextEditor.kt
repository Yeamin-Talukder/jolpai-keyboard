package com.yeamin.jolpai.core.editor

import com.yeamin.jolpai.core.field.FieldInfo

/** Editing operations an app performs itself (context-menu actions and key presses). */
enum class EditorCommand {
    SELECT_ALL, CUT, COPY, PASTE, UNDO, REDO,
    LEFT, RIGHT, UP, DOWN, LINE_START, LINE_END,
}

/**
 * The operations the keyboard logic needs from the focused text field.
 *
 * [EditorSession] implements this on top of Android's `InputConnection`; tests use a fake.
 * Keeping the logic behind this interface means processors and the controller can be
 * unit-tested on the PC without a device.
 */
interface TextEditor {
    /** Up to [maxChars] characters before the cursor (or before the selection; empty if unknown). */
    fun textBeforeCursor(maxChars: Int): CharSequence

    /** Up to [maxChars] characters after the cursor (or after the selection). */
    fun textAfterCursor(maxChars: Int): CharSequence

    fun hasSelection(): Boolean

    /** Selection start and end as absolute positions, or -1 when unknown. */
    fun selectionStart(): Int
    fun selectionEnd(): Int

    fun setSelection(start: Int, end: Int)

    /** Inserts [text] at the cursor, replacing the composing text if there is any. */
    fun commit(text: CharSequence)

    /** Shows [text] as the underlined, still-editable word at the cursor (replacing the previous one). */
    fun setComposing(text: CharSequence)

    /** Keeps the composing text as normal text and ends composing. */
    fun finishComposing()

    /** Deletes [deleteCount] chars before the cursor and inserts [text], as one edit. */
    fun replaceBefore(deleteCount: Int, text: CharSequence)

    /** Deletes the selection, or one user-perceived character before the cursor. */
    fun deleteBackward()

    /** Runs the Enter key for [field]: an editor action, or a newline. */
    fun performEnter(field: FieldInfo)

    /**
     * Asks the app to run [command] (select all, copy, paste, undo, arrow keys …).
     * With [extendSelection], cursor moves extend the selection (Shift + arrow).
     * Undo/redo are sent as Ctrl+Z / Ctrl+Shift+Z, which many apps support but not all.
     */
    fun run(command: EditorCommand, extendSelection: Boolean = false)

    /** True when the app says the next letter should be capitalised (sentence start etc.). */
    fun isCapsModeAtCursor(): Boolean
}
