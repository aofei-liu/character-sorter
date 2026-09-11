package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.client.Character
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.Ranking

/**
 * One editable row, from either source the screen can draw on.
 *
 * `GET /characters` carries no rating and the ranking carries no more than
 * name and fandom, so the row is whichever of the two is in play, with
 * [annotation] present only in ranked order.
 */
private data class EditRow(
    val id: Int,
    val name: String,
    val fandom: String,
    val annotation: String? = null
)

/** Which modal the edit screen is showing, if any. */
private sealed interface EditDialog {
    data object AddCharacter : EditDialog
    data object RenameList : EditDialog
    data object DeleteList : EditDialog
    data class EditCharacter(val character: EditRow) : EditDialog
    data class DeleteCharacter(val character: EditRow) : EditDialog
}

@Composable
fun EditListScreen(
    list: CharacterList,
    characters: List<Character>?,
    ranking: Ranking?,
    byScore: Boolean,
    busy: Boolean,
    onAddCharacter: (String, String) -> Unit,
    onUpdateCharacter: (Int, String, String) -> Unit,
    onDeleteCharacter: (Int) -> Unit,
    onRenameList: (String) -> Unit,
    onDeleteList: () -> Unit,
    onSetSort: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    var dialog by remember { mutableStateOf<EditDialog?>(null) }
    val rows = when {
        byScore -> ranking?.characters?.map {
            EditRow(it.id, it.name, it.fandom, it.annotation)
        }
        else -> characters?.map { EditRow(it.id, it.name, it.fandom) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                list.title.uppercase(),
                style = CharSorterType.ScreenTitle,
                color = CharSorterColor.Ink,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            OutlinedButton(
                onClick = onBack,
                shape = CharSorterShape.Pill,
                border = BorderStroke(1.dp, CharSorterColor.AccentDark.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CharSorterColor.Link)
            ) {
                Text("Lists", style = CharSorterType.ButtonSecondary)
            }
        }
        Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TextButton(onClick = { dialog = EditDialog.RenameList }, enabled = !busy) {
                Text("Rename", style = CharSorterType.ButtonSecondary, color = CharSorterColor.Link)
            }
            TextButton(onClick = { dialog = EditDialog.DeleteList }, enabled = !busy) {
                Text("Delete list", style = CharSorterType.ButtonSecondary, color = CharSorterColor.Destructive)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { dialog = EditDialog.AddCharacter },
                enabled = !busy,
                shape = CharSorterShape.Pill,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(CharSorterColor.AccentLight, CharSorterColor.AccentDark)),
                        shape = CharSorterShape.Pill
                    )
            ) {
                Text("Add character", style = CharSorterType.ButtonPrimarySmall, color = CharSorterColor.OnAccent)
            }
            ByScoreChip(selected = byScore, enabled = !busy, onClick = { onSetSort(!byScore) })
        }

        when {
            busy -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = CharSorterColor.AccentDark,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            rows == null -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Couldn't load the characters.", style = CharSorterType.DialogBody, color = CharSorterColor.Muted)
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry", style = CharSorterType.ButtonSecondary, color = CharSorterColor.Link)
                }
            }
            rows.isEmpty() -> Text(
                "No characters yet.",
                style = CharSorterType.FandomLarge,
                color = CharSorterColor.Muted,
                modifier = Modifier.padding(top = 24.dp)
            )
            else -> LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(rows, key = { it.id }) { character ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(character.name, style = CharSorterType.RowName, color = CharSorterColor.Ink)
                            Text(character.fandom, style = CharSorterType.FandomSmall, color = CharSorterColor.Muted)
                            character.annotation?.let {
                                Text(it, style = CharSorterType.RatingText, color = CharSorterColor.Link)
                            }
                        }
                        TextButton(onClick = { dialog = EditDialog.EditCharacter(character) }) {
                            Text("Edit", style = CharSorterType.ButtonSecondary, color = CharSorterColor.Link)
                        }
                        TextButton(onClick = { dialog = EditDialog.DeleteCharacter(character) }) {
                            Text("Delete", style = CharSorterType.ButtonSecondary, color = CharSorterColor.Destructive)
                        }
                    }
                    HorizontalDivider(color = CharSorterColor.AccentDark.copy(alpha = 0.22f))
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

@Composable
private fun ByScoreChip(selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = CharSorterShape.Pill
    val background = if (selected) CharSorterColor.AccentDark.copy(alpha = 0.18f) else Color.Transparent
    val border = if (selected) CharSorterColor.AccentDark else CharSorterColor.AccentDark.copy(alpha = 0.55f)
    val textColor = if (selected) CharSorterColor.AccentTintText else CharSorterColor.Link
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .background(background, shape)
            .border(BorderStroke(1.dp, border), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (selected) {
            DiamondAccent(size = 6.dp, fill = CharSorterColor.AccentDark)
        }
        Text("By score", style = CharSorterType.ButtonSecondary, color = textColor)
    }
}
