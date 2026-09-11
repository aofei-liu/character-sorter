package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.client.Character
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.NextComparison
import io.github.aofeiliu.charsorter.client.Verdict

/**
 * The core loop: one pair, two tappable cards plus a tie button, one tap per
 * comparison. Cards stack top/bottom for portrait phone use.
 */
@Composable
fun SortScreen(
    list: CharacterList,
    pending: NextComparison?,
    busy: Boolean,
    canUndo: Boolean,
    onAnswer: (Verdict) -> Unit,
    onUndo: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CharSorterColor.Link),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text("Lists", style = CharSorterType.ButtonSecondary, maxLines = 1)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiamondAccent(size = 6.dp, fill = CharSorterColor.AccentDark)
                Text(pending?.progress.orEmpty(), style = CharSorterType.ProgressText, color = CharSorterColor.Muted)
            }
            if (canUndo) {
                TextButton(onClick = onUndo, enabled = !busy) {
                    Text("Undo", style = CharSorterType.ButtonSecondary, color = CharSorterColor.Link)
                }
            }
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
            pending == null -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Couldn't load the next comparison.",
                    style = CharSorterType.DialogBody,
                    color = CharSorterColor.Muted
                )
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry", style = CharSorterType.ButtonSecondary, color = CharSorterColor.Link)
                }
            }
            pending.done -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DiamondAccent(size = 13.dp, border = CharSorterColor.AccentBorder)
                Text(
                    "Done!",
                    style = CharSorterType.DoneText,
                    color = CharSorterColor.Ink,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            else -> {
                val char1 = pending.char1
                val char2 = pending.char2
                if (char1 != null && char2 != null) {
                    Column(
                        modifier = Modifier.weight(1f).padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ComparisonCard(
                            character = char1,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onClick = { onAnswer(Verdict.CHAR1_WINS) }
                        )
                        ComparisonCard(
                            character = char2,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onClick = { onAnswer(Verdict.CHAR2_WINS) }
                        )
                    }
                    OutlinedButton(
                        onClick = { onAnswer(Verdict.TIE) },
                        shape = CharSorterShape.Pill,
                        border = BorderStroke(1.dp, CharSorterColor.NeutralBorder.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CharSorterColor.NeutralText),
                        contentPadding = PaddingValues(horizontal = 34.dp, vertical = 12.dp),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .heightIn(min = 44.dp)
                            .padding(top = 16.dp, bottom = 6.dp)
                    ) {
                        Text("Same", style = CharSorterType.ButtonTie, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(character: Character, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(listOf(CharSorterColor.CardFillStart, CharSorterColor.CardFillEnd)),
                shape = CharSorterShape.Card
            )
            .border(1.dp, CharSorterColor.AccentDark.copy(alpha = 0.42f), CharSorterShape.Card)
            .clickable(onClick = onClick)
            .padding(24.dp)
    ) {
        DiamondAccent(
            size = 7.dp,
            border = CharSorterColor.AccentBorder,
            modifier = Modifier.align(Alignment.TopEnd)
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                character.name,
                style = CharSorterType.CharacterName,
                color = CharSorterColor.Ink,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                character.fandom,
                style = CharSorterType.FandomLarge,
                color = CharSorterColor.Muted,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}
