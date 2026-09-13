package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.RankedCharacter
import io.github.aofeiliu.charsorter.client.Ranking
import kotlin.math.roundToInt

/**
 * One character's raw Glicko pair, from the graph endpoint.
 *
 * The ranking's own annotation is `rating - 2 * rd`, the low end of this
 * spread, so the spread cannot be recovered from the ranking alone.
 */
data class RatingSpread(val rating: Double, val doubleRd: Double)

/** Only a Glicko list has ratings to chart; insertion sort 404s on /graph. */
private const val GLICKO = "GL"

@Composable
fun RankingScreen(
    list: CharacterList,
    ranking: Ranking?,
    spreads: Map<Int, RatingSpread>?,
    busy: Boolean,
    onOpenCharacter: (RankedCharacter) -> Unit,
    onOpenChart: () -> Unit,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (list.controllerType == GLICKO) {
                    OutlinedButton(
                        onClick = onOpenChart,
                        shape = CharSorterShape.Pill,
                        border = BorderStroke(
                            1.dp, CharSorterColor.AccentDark.copy(alpha = 0.55f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CharSorterColor.Link
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text("Chart", style = CharSorterType.ButtonSecondary, maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = onBack,
                    shape = CharSorterShape.Pill,
                    border = BorderStroke(1.dp, CharSorterColor.AccentDark.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CharSorterColor.Link
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text("Lists", style = CharSorterType.ButtonSecondary, maxLines = 1)
                }
            }
        }
        ranking?.progress?.let {
            Row(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiamondAccent(size = 6.dp, fill = CharSorterColor.AccentDark)
                Text(it, style = CharSorterType.ProgressText, color = CharSorterColor.Muted)
            }
        }
        if (ranking == null) {
            if (busy) {
                CircularProgressIndicator(
                    color = CharSorterColor.AccentDark,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Couldn't load the ranking.",
                        style = CharSorterType.DialogBody,
                        color = CharSorterColor.Muted
                    )
                    TextButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry", style = CharSorterType.ButtonSecondary, color = CharSorterColor.Link)
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(ranking.characters) { char ->
                    RankingRow(char, spreads?.get(char.id), onOpenCharacter)
                    HorizontalDivider(color = CharSorterColor.AccentDark.copy(alpha = 0.22f))
                }
            }
        }
    }
}

@Composable
private fun RankingRow(
    char: RankedCharacter,
    spread: RatingSpread?,
    onOpenCharacter: (RankedCharacter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenCharacter(char) }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                "${char.rank}. ${char.name}",
                style = CharSorterType.RowName,
                color = CharSorterColor.Ink
            )
            Text(char.fandom, style = CharSorterType.FandomSmall, color = CharSorterColor.Muted)
        }
        char.annotation?.let {
            Column(horizontalAlignment = Alignment.End) {
                Text(it, style = CharSorterType.RatingText, color = CharSorterColor.Link)
                // The score above is already the low end of this spread, so
                // the rating is written out rather than implied by a +/-.
                spread?.let { known ->
                    Text(
                        "${known.rating.roundToInt()} ± ${known.doubleRd.roundToInt()}",
                        style = CharSorterType.FandomSmall,
                        color = CharSorterColor.Muted,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
