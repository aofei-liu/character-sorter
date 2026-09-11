package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.client.Character
import io.github.aofeiliu.charsorter.client.CharacterList

/** Which modal the edit screen is showing, if any. */
private sealed interface EditDialog {
    data object AddCharacter : EditDialog
    data object RenameList : EditDialog
    data object DeleteList : EditDialog
    data class EditCharacter(val character: Character) : EditDialog
    data class DeleteCharacter(val character: Character) : EditDialog
}

@Composable
fun EditListScreen(
    list: CharacterList,
    characters: List<Character>?,
    busy: Boolean,
    onAddCharacter: (String, String) -> Unit,
    onUpdateCharacter: (Int, String, String) -> Unit,
    onDeleteCharacter: (Int) -> Unit,
    onRenameList: (String) -> Unit,
    onDeleteList: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    var dialog by remember { mutableStateOf<EditDialog?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(list.title, style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Lists") }
        }
        Row(modifier = Modifier.padding(top = 4.dp)) {
            TextButton(
                onClick = { dialog = EditDialog.RenameList },
                enabled = !busy
            ) {
                Text("Rename")
            }
            TextButton(
                onClick = { dialog = EditDialog.DeleteList },
                enabled = !busy
            ) {
                Text("Delete list", color = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            onClick = { dialog = EditDialog.AddCharacter },
            enabled = !busy,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Add character")
        }

        when {
            busy -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            characters == null -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Couldn't load the characters.")
                Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
            characters.isEmpty() -> Text(
                "No characters yet.",
                modifier = Modifier.padding(top = 24.dp)
            )
            else -> LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(characters, key = { it.id }) { character ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(character.name, style = MaterialTheme.typography.titleMedium)
                            Text(character.fandom, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { dialog = EditDialog.EditCharacter(character) }) {
                            Text("Edit")
                        }
                        TextButton(onClick = { dialog = EditDialog.DeleteCharacter(character) }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    when (val shown = dialog) {
        null -> Unit
        is EditDialog.AddCharacter -> CharacterDialog(
            title = "Add character",
            onConfirm = { name, fandom ->
                dialog = null
                onAddCharacter(name, fandom)
            },
            onDismiss = { dialog = null }
        )
        is EditDialog.EditCharacter -> CharacterDialog(
            title = "Edit character",
            initialName = shown.character.name,
            initialFandom = shown.character.fandom,
            onConfirm = { name, fandom ->
                dialog = null
                onUpdateCharacter(shown.character.id, name, fandom)
            },
            onDismiss = { dialog = null }
        )
        is EditDialog.DeleteCharacter -> ConfirmDeleteDialog(
            title = "Delete ${shown.character.name}?",
            // Not a local edit: SortRecord.char1 and char2 both cascade.
            body = "This also deletes every comparison ${shown.character.name} " +
                "took part in, so the other characters' ratings will change too. " +
                "It cannot be undone.",
            onConfirm = {
                dialog = null
                onDeleteCharacter(shown.character.id)
            },
            onDismiss = { dialog = null }
        )
        is EditDialog.RenameList -> RenameListDialog(
            initialTitle = list.title,
            onConfirm = {
                dialog = null
                onRenameList(it)
            },
            onDismiss = { dialog = null }
        )
        is EditDialog.DeleteList -> ConfirmDeleteDialog(
            title = "Delete ${list.title}?",
            body = "This deletes the list, every character in it, and the whole " +
                "comparison history. It cannot be undone.",
            onConfirm = {
                dialog = null
                onDeleteList()
            },
            onDismiss = { dialog = null }
        )
    }
}
