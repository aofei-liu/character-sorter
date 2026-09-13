package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.PasteParse

private const val PLACEHOLDER = "Sailor Neptune (Sailor Moon)\n" +
    "\n" +
    "[Madoka Magica]\n" +
    "Homura Akemi\n" +
    "Mami Tomoe"

/**
 * The paste editor: free text in, a parse tally out.
 *
 * [text] comes from the view model rather than local state, so returning from
 * the preview restores the paste exactly. [caretLine] is a one-shot request to
 * move the caret to a source line the preview pointed at.
 */
@Composable
fun PasteScreen(
    list: CharacterList,
    text: String,
    parse: PasteParse,
    caretLine: Int?,
    busy: Boolean,
    onTextChange: (String) -> Unit,
    onCaretHandled: () -> Unit,
    onPreview: () -> Unit,
    onCancel: () -> Unit
) {
    var field by remember { mutableStateOf(TextFieldValue(text)) }
    if (field.text != text) {
        // Clamp: the incoming text can be shorter than where the caret sat.
        field = TextFieldValue(text, TextRange(field.selection.start.coerceAtMost(text.length)))
    }
    LaunchedEffect(caretLine) {
        if (caretLine != null) {
            field = field.copy(selection = TextRange(offsetOfLine(text, caretLine)))
            onCaretHandled()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Paste many".uppercase(),
                style = CharSorterType.ScreenTitle,
                color = CharSorterColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            OutlinedButton(
                onClick = onCancel,
                shape = CharSorterShape.Pill,
                border = BorderStroke(1.dp, CharSorterColor.AccentDark.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CharSorterColor.Link),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text("Cancel", style = CharSorterType.ButtonSecondary, maxLines = 1)
            }
        }

        Text(
            "Into ${list.title}",
            style = CharSorterType.FandomSmall,
            color = CharSorterColor.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )

        HintStrip(modifier = Modifier.padding(top = 10.dp))

        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                onTextChange(it.text)
            },
            placeholder = {
                Text(PLACEHOLDER, style = CharSorterType.FieldValue, color = CharSorterColor.Placeholder)
            },
            textStyle = CharSorterType.FieldValue,
            shape = CharSorterShape.DialogField,
            colors = pasteFieldColors(),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp)
        )

        Tally(parse = parse, modifier = Modifier.padding(top = 10.dp))

        Button(
            onClick = onPreview,
            enabled = !busy && parse.entries.isNotEmpty(),
            shape = CharSorterShape.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(top = 10.dp)
                .background(
                    brush = if (parse.entries.isNotEmpty()) {
                        Brush.linearGradient(
                            listOf(CharSorterColor.AccentLight, CharSorterColor.AccentDark)
                        )
                    } else {
                        SolidColor(CharSorterColor.DisabledFillBorder)
                    },
                    shape = CharSorterShape.Pill
                )
        ) {
            Text(
                previewLabel(parse),
                style = CharSorterType.ButtonPrimarySmall,
                color = if (parse.entries.isNotEmpty()) {
                    CharSorterColor.OnAccent
                } else {
                    CharSorterColor.DisabledText
                },
                maxLines = 1
            )
        }
    }
}

private fun previewLabel(parse: PasteParse) = when (parse.entries.size) {
    0 -> "Nothing to add yet"
    1 -> "Preview 1 character"
    else -> "Preview ${parse.entries.size} characters"
}

/** The convention, kept on screen after the placeholder is typed over. */
@Composable
private fun HintStrip(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                CharSorterColor.AccentLight.copy(alpha = 0.28f),
                CharSorterShape.DialogField
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            "One per line.",
            style = CharSorterType.ButtonSecondary,
            color = CharSorterColor.AccentTintText
        )
        Text(
            "Name (Fandom) — or a [Fandom] header, then bare names",
            style = CharSorterType.FandomSmall,
            color = CharSorterColor.Muted
        )
    }
}

@Composable
private fun Tally(parse: PasteParse, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DiamondAccent(size = 6.dp, fill = CharSorterColor.AccentDark)
        Text(
            "${parse.entries.size} ready",
            style = CharSorterType.ProgressText,
            color = CharSorterColor.Muted
        )
        if (parse.skipped.isNotEmpty()) {
            Text(
                "· ${parse.skipped.size} need a fandom or are too long",
                style = CharSorterType.ProgressText,
                color = CharSorterColor.Destructive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun pasteFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CharSorterColor.AccentDark,
    unfocusedBorderColor = CharSorterColor.AccentDark.copy(alpha = 0.45f),
    focusedContainerColor = Color.White.copy(alpha = 0.82f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.82f),
    focusedTextColor = CharSorterColor.Ink,
    unfocusedTextColor = CharSorterColor.Ink,
    cursorColor = CharSorterColor.AccentDark
)

/** The character offset where 0-based [line] starts. */
private fun offsetOfLine(text: String, line: Int): Int {
    var offset = 0
    var seen = 0
    while (seen < line) {
        val next = text.indexOf('\n', offset)
        if (next < 0) {
            return text.length
        }
        offset = next + 1
        seen++
    }
    return offset.coerceAtMost(text.length)
}
