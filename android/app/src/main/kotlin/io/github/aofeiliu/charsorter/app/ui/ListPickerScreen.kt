package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.client.CharacterList

/**
 * The design gives the three per-list buttons `padding: 11px 0` — no
 * horizontal padding, so the whole of an equal-width third is available to
 * the label. Material3's 24dp default eats ~48dp of it and wraps "Ranking"
 * onto two lines.
 */
private val RowButtonPadding = PaddingValues(horizontal = 0.dp, vertical = 11.dp)

@Composable
fun ListPickerScreen(
    lists: List<CharacterList>,
    busy: Boolean,
    onSort: (CharacterList) -> Unit,
    onViewRanking: (CharacterList) -> Unit,
    onEdit: (CharacterList) -> Unit,
    onCreateList: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your lists".uppercase(), style = CharSorterType.ScreenTitleLarge, color = CharSorterColor.Ink)
            TextButton(onClick = onLogout) {
                Text("Log out", style = CharSorterType.ButtonSecondary, color = CharSorterColor.Muted)
            }
        }
        Button(
            onClick = { creating = true },
            enabled = !busy,
            shape = CharSorterShape.Pill,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(bottom = 18.dp)
                .background(
                    brush = Brush.linearGradient(listOf(CharSorterColor.AccentLight, CharSorterColor.AccentDark)),
                    shape = CharSorterShape.Pill
                )
        ) {
            Text("New list", style = CharSorterType.ButtonPrimary, color = CharSorterColor.OnAccent)
        }
        if (busy && lists.isEmpty()) {
            CircularProgressIndicator(color = CharSorterColor.AccentDark)
        } else if (lists.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DiamondAccent(size = 11.dp, border = CharSorterColor.AccentBorder)
                Text(
                    "No character lists yet.",
                    style = CharSorterType.FandomLarge,
                    color = CharSorterColor.Muted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(lists) { list ->
                    ListCard(list, onSort, onViewRanking, onEdit)
                }
            }
        }
    }

    if (creating) {
        CreateListDialog(
            onConfirm = { title, controllerType ->
                creating = false
                onCreateList(title, controllerType)
            },
            onDismiss = { creating = false }
        )
    }
}

@Composable
private fun ListCard(
    list: CharacterList,
    onSort: (CharacterList) -> Unit,
    onViewRanking: (CharacterList) -> Unit,
    onEdit: (CharacterList) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(listOf(CharSorterColor.CardFillStart, CharSorterColor.CardFillEnd)),
                shape = CharSorterShape.Card
            )
            .border(1.dp, CharSorterColor.AccentDark.copy(alpha = 0.4f), CharSorterShape.Card)
            .padding(20.dp)
    ) {
        Text(list.title.uppercase(), style = CharSorterType.ListTitle, color = CharSorterColor.Ink)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onSort(list) },
                shape = CharSorterShape.Pill,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = RowButtonPadding,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(CharSorterColor.AccentLight, CharSorterColor.AccentDark)),
                        shape = CharSorterShape.Pill
                    )
            ) {
                Text(
                    "Sort",
                    style = CharSorterType.ButtonPrimarySmall,
                    color = CharSorterColor.OnAccent,
                    maxLines = 1
                )
            }
            OutlinedButton(
                onClick = { onViewRanking(list) },
                shape = CharSorterShape.Pill,
                border = BorderStroke(1.dp, CharSorterColor.AccentDark.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CharSorterColor.Link),
                contentPadding = RowButtonPadding,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("Ranking", style = CharSorterType.ButtonSecondary, maxLines = 1)
            }
            OutlinedButton(
                onClick = { onEdit(list) },
                shape = CharSorterShape.Pill,
                border = BorderStroke(1.dp, CharSorterColor.AccentDark.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CharSorterColor.Link),
                contentPadding = RowButtonPadding,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("Edit", style = CharSorterType.ButtonSecondary, maxLines = 1)
            }
        }
    }
}
