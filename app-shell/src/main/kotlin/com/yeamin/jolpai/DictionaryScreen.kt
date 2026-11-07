package com.yeamin.jolpai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.data.PersonalWordEntity
import com.yeamin.jolpai.ui.R as UiR

/** The personal dictionary: words that are always suggested and never autocorrected. */
@Composable
fun DictionaryScreen(
    words: List<PersonalWordEntity>,
    onAdd: (String) -> Unit,
    onRemove: (PersonalWordEntity) -> Unit,
    onBack: () -> Unit,
) {
    var newWord by rememberSaveable { mutableStateOf("") }
    fun add() {
        if (newWord.isNotBlank()) {
            onAdd(newWord)
            newWord = ""
        }
    }
    SettingsScaffold("Personal dictionary", onBack, scrollable = false) {
        SettingsNote("Names, slang and other words you use, in English or Bangla. They're always suggested and never autocorrected.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newWord,
                onValueChange = { newWord = it.replace("\n", "") },
                placeholder = { Text("Add a word") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(onClick = { add() }, enabled = newWord.isNotBlank()) { MsIcon(UiR.drawable.ms_add, "Add word") }
        }
        if (words.isEmpty()) {
            SettingsNote("No words yet. You can also add one from the keyboard: tap the word you typed in the suggestion strip, then “Add to dictionary”.")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(words, key = { it.language + ":" + it.key }) { word ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextBadge(if (word.language == "bn") "অ" else "En")
                        Text(word.word, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { onRemove(word) }) { MsIcon(UiR.drawable.ms_delete, "Remove ${word.word}") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
