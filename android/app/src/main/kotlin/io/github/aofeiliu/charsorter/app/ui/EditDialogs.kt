package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog

/** Add or edit a character: the two fields `CharacterForm` declares. */
@Composable
fun CharacterDialog(
    title: String,
    initialName: String = "",
    initialFandom: String = "",
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var fandom by remember { mutableStateOf(initialFandom) }

    CharSorterDialog(onDismissRequest = onDismiss) {
        Text(title.uppercase(), style = CharSorterType.DialogTitle, color = CharSorterColor.Ink)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name", style = CharSorterType.FieldLabel) },
            textStyle = CharSorterType.FieldValue,
            singleLine = true,
            shape = CharSorterShape.DialogField,
            colors = dialogFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = fandom,
            onValueChange = { fandom = it },
            label = { Text("Fandom", style = CharSorterType.FieldLabel) },
            textStyle = CharSorterType.FieldValue,
            singleLine = true,
            shape = CharSorterShape.DialogField,
            colors = dialogFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        DialogActions {
            DialogCancelButton(onDismiss)
            DialogConfirmButton(
                label = "Save",
                enabled = name.isNotBlank() && fandom.isNotBlank(),
                onClick = { onConfirm(name.trim(), fandom.trim()) }
            )
        }
    }
}

/**
 * Create a list: a title plus the controller, which cannot be meaningfully
 * changed later once comparisons exist, so it is asked for up front.
 */
@Composable
fun CreateListDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var controllerType by remember { mutableStateOf("GL") }

    CharSorterDialog(onDismissRequest = onDismiss) {
        Text("New list".uppercase(), style = CharSorterType.DialogTitle, color = CharSorterColor.Ink)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title", style = CharSorterType.FieldLabel) },
            textStyle = CharSorterType.FieldValue,
            singleLine = true,
            shape = CharSorterShape.DialogField,
            colors = dialogFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        ControllerChoice("GL", "Glicko — keeps refining, never finishes", controllerType) { controllerType = it }
        ControllerChoice("IS", "Insertion sort — finishes, then stops", controllerType) { controllerType = it }
        DialogActions {
            DialogCancelButton(onDismiss)
            DialogConfirmButton(
                label = "Create",
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title.trim(), controllerType) }
            )
        }
    }
}

@Composable
private fun ControllerChoice(value: String, label: String, selected: String, onSelect: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected == value, onClick = { onSelect(value) })
    ) {
        RadioButton(
            selected = selected == value,
            onClick = { onSelect(value) },
            colors = RadioButtonDefaults.colors(selectedColor = CharSorterColor.AccentDark)
        )
        Text(label, style = CharSorterType.FieldValue, color = CharSorterColor.Muted)
    }
}

/** Rename a list. The controller is deliberately not editable here. */
@Composable
fun RenameListDialog(initialTitle: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }

    CharSorterDialog(onDismissRequest = onDismiss) {
        Text("Rename list".uppercase(), style = CharSorterType.DialogTitle, color = CharSorterColor.Ink)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title", style = CharSorterType.FieldLabel) },
            textStyle = CharSorterType.FieldValue,
            singleLine = true,
            shape = CharSorterShape.DialogField,
            colors = dialogFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        DialogActions {
            DialogCancelButton(onDismiss)
            DialogConfirmButton(
                label = "Save",
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title.trim()) }
            )
        }
    }
}

/**
 * Confirms a delete by naming what it destroys.
 *
 * Both deletes here cascade further than the row being removed, and neither
 * is recoverable through any surface this app or the API has, so [body] is
 * expected to say so in full rather than asking "are you sure?".
 */
@Composable
fun ConfirmDeleteDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    CharSorterDialog(onDismissRequest = onDismiss, destructive = true) {
        Text(title.uppercase(), style = CharSorterType.DialogTitle, color = CharSorterColor.Ink)
        Text(body, style = CharSorterType.DialogBody, color = CharSorterColor.Muted)
        DialogActions {
            DialogCancelButton(onDismiss)
            Button(
                onClick = onConfirm,
                shape = CharSorterShape.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CharSorterColor.Destructive,
                    contentColor = CharSorterColor.OnDestructive
                ),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp)
            ) {
                Text("Delete", style = CharSorterType.ButtonPrimarySmall, maxLines = 1)
            }
        }
    }
}

/** Shared dialog chrome: gradient card, accent border, 26dp radius. */
@Composable
private fun CharSorterDialog(
    onDismissRequest: () -> Unit,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (destructive) CharSorterColor.DeleteDialogBorder.copy(alpha = 0.4f) else CharSorterColor.AccentDark.copy(alpha = 0.45f)
    val fillEnd = if (destructive) CharSorterColor.DeleteDialogFillEnd else CharSorterColor.CardFillEnd
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(listOf(CharSorterColor.CardFillStart, fillEnd)),
                    shape = CharSorterShape.Dialog
                )
                .border(1.dp, borderColor, CharSorterShape.Dialog)
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun DialogActions(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, alignment = Alignment.End)
    ) { content() }
}

@Composable
private fun DialogCancelButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = CharSorterShape.Pill,
        border = BorderStroke(1.dp, CharSorterColor.NeutralBorder.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CharSorterColor.NeutralText),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text("Cancel", style = CharSorterType.ButtonPrimarySmall, maxLines = 1)
    }
}

@Composable
private fun DialogConfirmButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CharSorterShape.Pill,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
        modifier = Modifier.background(
            brush = if (enabled) {
                Brush.linearGradient(listOf(CharSorterColor.AccentLight, CharSorterColor.AccentDark))
            } else {
                Brush.linearGradient(listOf(CharSorterColor.DisabledFillBorder, CharSorterColor.DisabledFillBorder))
            },
            shape = CharSorterShape.Pill
        )
    ) {
        Text(
            label,
            style = CharSorterType.ButtonPrimarySmall,
            color = if (enabled) CharSorterColor.OnAccent else CharSorterColor.DisabledText,
            maxLines = 1
        )
    }
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CharSorterColor.AccentDark,
    unfocusedBorderColor = CharSorterColor.AccentDark.copy(alpha = 0.45f),
    focusedContainerColor = Color.White.copy(alpha = 0.8f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.8f),
    focusedLabelColor = CharSorterColor.Muted,
    unfocusedLabelColor = CharSorterColor.Muted,
    focusedTextColor = CharSorterColor.Ink,
    unfocusedTextColor = CharSorterColor.Ink,
    cursorColor = CharSorterColor.AccentDark
)
