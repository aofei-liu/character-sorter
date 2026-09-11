package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.client.Character
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.NextComparison
import io.github.aofeiliu.charsorter.client.Verdict

/**
 * The core loop: one pair, two tappable cards plus a tie button, one tap per
 * comparison. Mirrors the card pattern PR 2 gave the HTML sort page.
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
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(list.title, style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Lists") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                pending?.progress.orEmpty(),
                style = MaterialTheme.typography.bodyMedium
            )
            if (canUndo) {
                TextButton(onClick = onUndo, enabled = !busy) { Text("Undo") }
            }
        }

        when {
            busy -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            pending == null -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Couldn't load the next comparison.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
            pending.done -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Done!",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            else -> {
                val char1 = pending.char1
                val char2 = pending.char2
                if (char1 != null && char2 != null) {
                    Column(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                        ComparisonCard(
                            character = char1,
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 6.dp),
                            onClick = { onAnswer(Verdict.CHAR1_WINS) }
                        )
                        ComparisonCard(
                            character = char2,
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp),
                            onClick = { onAnswer(Verdict.CHAR2_WINS) }
                        )
                    }
                    TextButton(
                        onClick = { onAnswer(Verdict.TIE) },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)
                    ) {
                        Text("Same")
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(character: Character, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                character.name,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                character.fandom,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}
