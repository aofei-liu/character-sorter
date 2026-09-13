package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.app.PasteWrite
import io.github.aofeiliu.charsorter.app.WriteResult
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.EntryStatus
import io.github.aofeiliu.charsorter.client.ParsedEntry
import io.github.aofeiliu.charsorter.client.ReviewedEntry
import io.github.aofeiliu.charsorter.client.SkipReason
import io.github.aofeiliu.charsorter.client.SkippedLine

/** One fandom's worth of the preview, in the order the fandoms first appear. */
private data class PreviewGroup(val fandom: String, val entries: List<ReviewedEntry>)

/**
 * Confirms a paste before anything is written, then reports how it went.
 *
 * Grouped by the fandom each name *resolved* to, not by input order: a
 * `[Fandom]` header that caught the wrong lines is obvious here and invisible
 * in a flat list, which is the whole reason this screen exists.
 */
@Composable
fun PastePreviewScreen(
    list: CharacterList,
    reviewed: List<ReviewedEntry>,
    skipped: List<SkippedLine>,
    writes: List<PasteWrite>?,
    busy: Boolean,
    onEditText: () -> Unit,
    onJumpToLine: (Int) -> Unit,
    onConfirm: (List<ParsedEntry>) -> Unit,
    onDone: () -> Unit
) {
    val addable = reviewed.filter { it.status == EntryStatus.NEW }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                (if (writes == null) "Add ${addable.size} to ${list.title}" else list.title)
                    .uppercase(),
                style = CharSorterType.ScreenTitle,
                color = CharSorterColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            if (writes == null) {
                OutlinedButton(
                    onClick = onEditText,
                    enabled = !busy,
                    shape = CharSorterShape.Pill,
                    border = BorderStroke(1.dp, CharSorterColor.AccentDark.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CharSorterColor.Link
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text("Edit text", style = CharSorterType.ButtonSecondary, maxLines = 1)
                }
            }
        }

        if (writes == null) {
            ConfirmBody(
                groups = groupByFandom(reviewed),
                skipped = skipped,
                addable = addable,
                busy = busy,
                onJumpToLine = onJumpToLine,
                onConfirm = onConfirm,
                modifier = Modifier.weight(1f)
            )
        } else {
            WriteBody(
                writes = writes,
                busy = busy,
                onDone = onDone,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConfirmBody(
    groups: List<PreviewGroup>,
    skipped: List<SkippedLine>,
    addable: List<ReviewedEntry>,
    busy: Boolean,
    onJumpToLine: (Int) -> Unit,
    onConfirm: (List<ParsedEntry>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Tap a row to fix it in the text",
            style = CharSorterType.FandomSmall,
            color = CharSorterColor.Muted,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(groups) { group ->
                FandomGroup(group = group, onJumpToLine = onJumpToLine)
            }
            if (skipped.isNotEmpty()) {
                items(skipped) { line ->
                    SkippedCard(line = line, onJumpToLine = onJumpToLine)
                }
            }
        }
        Button(
            onClick = { onConfirm(addable.map { it.entry }) },
            enabled = !busy && addable.isNotEmpty(),
            shape = CharSorterShape.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(top = 12.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(CharSorterColor.AccentLight, CharSorterColor.AccentDark)
                    ),
                    shape = CharSorterShape.Pill
                )
        ) {
            Text(
                if (addable.size == 1) "Add 1 new character" else "Add ${addable.size} new characters",
                style = CharSorterType.ButtonPrimarySmall,
                color = CharSorterColor.OnAccent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FandomGroup(group: PreviewGroup, onJumpToLine: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    listOf(CharSorterColor.CardFillStart, CharSorterColor.CardFillEnd)
                ),
                shape = CharSorterShape.DialogField
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                group.fandom,
                style = CharSorterType.ButtonSecondary,
                color = CharSorterColor.Link,
                maxLines = 2,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(
                "${group.entries.size}",
                style = CharSorterType.FandomSmall,
                color = CharSorterColor.Placeholder
            )
        }
        group.entries.forEach { reviewed ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onJumpToLine(reviewed.entry.line) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    reviewed.entry.name,
                    style = CharSorterType.RowName,
                    color = CharSorterColor.Ink,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Text(
                    statusLabel(reviewed),
                    style = CharSorterType.FandomSmall,
                    color = statusColor(reviewed.status),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SkippedCard(line: SkippedLine, onJumpToLine: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                CharSorterColor.DeleteDialogFillEnd,
                CharSorterShape.DialogField
            )
            .clickable { onJumpToLine(line.line) }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            "Skipped",
            style = CharSorterType.ButtonSecondary,
            color = CharSorterColor.Destructive
        )
        Text(
            line.text,
            style = CharSorterType.RowName,
            color = CharSorterColor.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            skipAdvice(line.reason),
            style = CharSorterType.FandomSmall,
            color = CharSorterColor.Muted,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun WriteBody(
    writes: List<PasteWrite>,
    busy: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val added = writes.count { it.result == WriteResult.ADDED }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DiamondAccent(size = 6.dp, fill = CharSorterColor.AccentDark)
            Text(
                if (busy) "Adding $added of ${writes.size}…" else "Added $added of ${writes.size}",
                style = CharSorterType.ProgressText,
                color = CharSorterColor.Muted
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(writes) { write ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(write.name, style = CharSorterType.RowName, color = CharSorterColor.Ink)
                        Text(
                            write.fandom,
                            style = CharSorterType.FandomSmall,
                            color = CharSorterColor.Muted
                        )
                    }
                    Text(
                        writeLabel(write.result),
                        style = CharSorterType.FandomSmall,
                        color = writeColor(write.result),
                        maxLines = 1
                    )
                }
            }
        }
        Button(
            onClick = onDone,
            enabled = !busy,
            shape = CharSorterShape.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(top = 12.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(CharSorterColor.AccentLight, CharSorterColor.AccentDark)
                    ),
                    shape = CharSorterShape.Pill
                )
        ) {
            Text(
                "Done",
                style = CharSorterType.ButtonPrimarySmall,
                color = CharSorterColor.OnAccent,
                maxLines = 1
            )
        }
    }
}

/** Fandoms in the order they first appear, so the reader's order is preserved. */
private fun groupByFandom(reviewed: List<ReviewedEntry>): List<PreviewGroup> =
    reviewed.groupBy { it.entry.fandom }.map { (fandom, entries) ->
        PreviewGroup(fandom, entries)
    }

private fun statusLabel(reviewed: ReviewedEntry) = when (reviewed.status) {
    EntryStatus.IN_LIST -> "in list"
    EntryStatus.REPEATED -> "repeated"
    EntryStatus.NEW -> if (reviewed.entry.inline) "new · inline" else "new"
}

private fun statusColor(status: EntryStatus) = when (status) {
    EntryStatus.NEW -> CharSorterColor.Link
    else -> CharSorterColor.Placeholder
}

private fun skipAdvice(reason: SkipReason) = when (reason) {
    SkipReason.NO_FANDOM ->
        "No fandom. Put it under a [Fandom] header, or write Name (Fandom)."
    SkipReason.NO_NAME -> "No name before the fandom."
    SkipReason.NAME_TOO_LONG -> "The name is over 200 characters."
    SkipReason.FANDOM_TOO_LONG -> "The fandom is over 200 characters."
}

private fun writeLabel(result: WriteResult) = when (result) {
    WriteResult.ADDED -> "added"
    WriteResult.FAILED -> "failed"
    WriteResult.NOT_SENT -> "not sent"
}

private fun writeColor(result: WriteResult) = when (result) {
    WriteResult.ADDED -> CharSorterColor.Link
    WriteResult.FAILED -> CharSorterColor.Destructive
    WriteResult.NOT_SENT -> CharSorterColor.Placeholder
}
