package com.yeamin.jolpai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeamin.jolpai.clipboard.PanelColors
import com.yeamin.jolpai.core.model.DateTimeKind
import com.yeamin.jolpai.core.model.EditCommand
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.Panel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Text editing tools in place of the keys: selection, clipboard actions, cursor keys,
 * word-wise selection and deletion, undo/redo, and date/time.
 * Cursor keys and word keys repeat while held.
 */
@Composable
fun EditingPanel(selecting: Boolean, colors: PanelColors, onAction: (KeyAction) -> Unit) {
    fun edit(c: EditCommand) = onAction(KeyAction.Edit(c))
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PanelRow {
            Tool(if (selecting) "◼ Selecting" else "◻ Select", colors, highlighted = selecting) { edit(EditCommand.TOGGLE_SELECT) }
            Tool("Select all", colors) { edit(EditCommand.SELECT_ALL) }
        }
        PanelRow {
            Tool("Copy", colors) { edit(EditCommand.COPY) }
            Tool("Cut", colors) { edit(EditCommand.CUT) }
            Tool("Paste", colors) { edit(EditCommand.PASTE) }
            Tool("Undo", colors) { edit(EditCommand.UNDO) }
            Tool("Redo", colors) { edit(EditCommand.REDO) }
        }
        PanelRow {
            Tool("⇤", colors, repeat = true) { edit(EditCommand.LINE_START) }
            Tool("◀", colors, repeat = true) { edit(EditCommand.LEFT) }
            Tool("▲", colors, repeat = true) { edit(EditCommand.UP) }
            Tool("▼", colors, repeat = true) { edit(EditCommand.DOWN) }
            Tool("▶", colors, repeat = true) { edit(EditCommand.RIGHT) }
            Tool("⇥", colors, repeat = true) { edit(EditCommand.LINE_END) }
        }
        PanelRow {
            Tool("◀ word", colors, repeat = true) { edit(EditCommand.SELECT_WORD_LEFT) }
            Tool("⌫ word", colors, repeat = true) { edit(EditCommand.DELETE_WORD) }
            Tool("word ▶", colors, repeat = true) { edit(EditCommand.SELECT_WORD_RIGHT) }
            Tool("📅 Date", colors) { onAction(KeyAction.InsertDateTime(DateTimeKind.DATE)) }
            Tool("🕑 Time", colors) { onAction(KeyAction.InsertDateTime(DateTimeKind.TIME)) }
        }
        Text(
            "Undo and redo work in apps that support Ctrl+Z.",
            color = colors.hint,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PanelRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/**
 * One tool button. With [repeat], holding it repeats the action (after 400 ms, every 60 ms).
 */
@Composable
private fun RowScope.Tool(
    label: String,
    colors: PanelColors,
    highlighted: Boolean = false,
    repeat: Boolean = false,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) colors.accent else colors.surface)
            .semantics { role = Role.Button }
            .pointerInput(repeat) {
                awaitEachGesture {
                    awaitFirstDown()
                    onClick()
                    val repeater = if (repeat) {
                        scope.launch {
                            delay(400)
                            while (true) {
                                onClick()
                                delay(60)
                            }
                        }
                    } else {
                        null
                    }
                    waitForUpOrCancellation()
                    repeater?.cancel()
                }
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (highlighted) colors.onAccent else colors.text, fontSize = 14.sp, maxLines = 1)
    }
}
