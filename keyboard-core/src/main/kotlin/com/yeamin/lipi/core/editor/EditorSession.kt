package com.yeamin.lipi.core.editor

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.yeamin.lipi.core.field.EnterAction
import com.yeamin.lipi.core.field.FieldInfo

/**
 * [TextEditor] backed by the current Android [InputConnection].
 *
 * The connection is fetched fresh for every call through [connection], because the
 * service's connection changes whenever focus moves to another field.
 * Selection is tracked from `onUpdateSelection` callbacks instead of being queried,
 * since asking the app (an IPC round trip) is slow in some apps such as WebView.
 */
class EditorSession(private val connection: () -> InputConnection?) : TextEditor {

    private var inputType: Int = 0
    private var selStart = -1
    private var selEnd = -1

    fun onStartInput(info: EditorInfo?) {
        inputType = info?.inputType ?: 0
        selStart = info?.initialSelStart ?: -1
        selEnd = info?.initialSelEnd ?: -1
    }

    fun onSelectionChanged(newStart: Int, newEnd: Int) {
        selStart = newStart
        selEnd = newEnd
    }

    override fun textBeforeCursor(maxChars: Int): CharSequence =
        connection()?.getTextBeforeCursor(maxChars, 0) ?: ""

    override fun textAfterCursor(maxChars: Int): CharSequence =
        connection()?.getTextAfterCursor(maxChars, 0) ?: ""

    override fun hasSelection(): Boolean = selStart >= 0 && selEnd >= 0 && selStart != selEnd

    override fun selectionStart(): Int = minOf(selStart, selEnd)
    override fun selectionEnd(): Int = maxOf(selStart, selEnd)

    override fun setSelection(start: Int, end: Int) {
        val ic = connection() ?: return
        ic.setSelection(start, end)
        // Update right away: the app's confirmation arrives later, and repeated word
        // selections (backspace swipe) must build on this one.
        selStart = start
        selEnd = end
    }

    override fun commit(text: CharSequence) {
        connection()?.commitText(text, 1)
    }

    override fun setComposing(text: CharSequence) {
        connection()?.setComposingText(text, 1)
    }

    override fun finishComposing() {
        connection()?.finishComposingText()
    }

    override fun replaceBefore(deleteCount: Int, text: CharSequence) {
        val ic = connection() ?: return
        if (deleteCount <= 0) {
            ic.commitText(text, 1)
            return
        }
        ic.beginBatchEdit()
        ic.deleteSurroundingText(deleteCount, 0)
        ic.commitText(text, 1)
        ic.endBatchEdit()
    }

    override fun deleteBackward() {
        val ic = connection() ?: return
        if (hasSelection()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(BACKSPACE_CONTEXT, 0)
        val count = if (before == null) 0 else Deletion.lengthToDelete(before)
        if (count > 0) {
            ic.deleteSurroundingText(count, 0)
        } else {
            // Nothing we can see before the cursor: let the app handle a real key press
            // (e.g. removing a chip in a recipient field, or fields that hide their text).
            sendKey(ic, KeyEvent.KEYCODE_DEL)
        }
    }

    override fun performEnter(field: FieldInfo) {
        val ic = connection() ?: return
        if (field.enterAction == EnterAction.NEWLINE) {
            sendKey(ic, KeyEvent.KEYCODE_ENTER)
        } else {
            ic.performEditorAction(field.editorActionId)
        }
    }

    override fun run(command: EditorCommand, extendSelection: Boolean) {
        val ic = connection() ?: return
        val shift = if (extendSelection) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        when (command) {
            EditorCommand.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            EditorCommand.CUT -> ic.performContextMenuAction(android.R.id.cut)
            EditorCommand.COPY -> ic.performContextMenuAction(android.R.id.copy)
            EditorCommand.PASTE -> ic.performContextMenuAction(android.R.id.paste)
            EditorCommand.UNDO -> sendKey(ic, KeyEvent.KEYCODE_Z, ctrl)
            EditorCommand.REDO -> sendKey(ic, KeyEvent.KEYCODE_Z, ctrl or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
            EditorCommand.LEFT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT, shift)
            EditorCommand.RIGHT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT, shift)
            EditorCommand.UP -> sendKey(ic, KeyEvent.KEYCODE_DPAD_UP, shift)
            EditorCommand.DOWN -> sendKey(ic, KeyEvent.KEYCODE_DPAD_DOWN, shift)
            EditorCommand.LINE_START -> sendKey(ic, KeyEvent.KEYCODE_MOVE_HOME, shift)
            EditorCommand.LINE_END -> sendKey(ic, KeyEvent.KEYCODE_MOVE_END, shift)
        }
    }

    override fun isCapsModeAtCursor(): Boolean {
        val ic = connection() ?: return false
        if (inputType == 0) return false
        return ic.getCursorCapsMode(inputType) != 0
    }

    private fun sendKey(ic: InputConnection, keyCode: Int, meta: Int = 0) {
        val now = SystemClock.uptimeMillis()
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta, -1, 0, flags))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta, -1, 0, flags))
    }

    private companion object {
        /** Enough context to find the last grapheme, even a long emoji sequence. */
        const val BACKSPACE_CONTEXT = 32
    }
}
