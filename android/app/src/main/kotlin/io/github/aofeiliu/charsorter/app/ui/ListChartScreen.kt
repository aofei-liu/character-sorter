package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.Graph
import kotlin.math.roundToInt

/** Where an unrated character sits, drawn as a reference across every row. */
private const val DEFAULT_RATING = 1500.0

/**
 * Every character's rating and its uncertainty, in ranked order.
 *
 * The web version is a Plotly bar chart, which a phone cannot fit hundreds of
 * columns into. This is the same data as one row per character: a marker at
 * the rating with a whisker across rating +/- 2 * rd, on a scale shared by
 * every row. Bars are deliberately not used -- a bar's length reads as a
 * magnitude from zero, and a Glicko rating has no zero to measure from.
 */
@Composable
fun ListChartScreen(
    list: CharacterList,
    graph: Graph?,
    busy: Boolean,
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
                Text("Back", style = CharSorterType.ButtonSecondary, maxLines = 1)
            }
        }

        val rows = graph?.let { rowsOf(it) }
        when {
            busy && rows == null -> Column(
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
                Text(
                    "Couldn't load this list's ratings.",
                    style = CharSorterType.DialogBody,
                    color = CharSorterColor.Muted
                )
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry", style = CharSorterType.ButtonSecondary, color = CharSorterColor.Link)
                }
            }
            rows.isEmpty() -> Text(
                "No characters to rate yet.",
                style = CharSorterType.FandomLarge,
                color = CharSorterColor.Muted,
                modifier = Modifier.padding(top = 24.dp)
            )
            else -> {
                val low = rows.minOf { it.rating - it.doubleRd }
                val high = rows.maxOf { it.rating + it.doubleRd }
                val span = (high - low).coerceAtLeast(1.0)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiamondAccent(size = 6.dp, fill = CharSorterColor.AccentDark)
                    Text(
                        "${rows.size} characters · ${low.roundToInt()} to ${high.roundToInt()}",
                        style = CharSorterType.ProgressText,
                        color = CharSorterColor.Muted
                    )
                }
                LazyColumn {
                    itemsIndexed(rows) { index, row ->
                        ChartRow(index + 1, row, low, span)
                    }
                }
            }
        }
    }
}

/** One character's place on the shared scale. */
private data class ChartEntry(
    val name: String,
    val rating: Double,
    val doubleRd: Double
)

/**
 * The graph's parallel arrays as rows, truncated to the shortest of them so a
 * ragged response cannot pair a name with another character's rating.
 */
private fun rowsOf(graph: Graph): List<ChartEntry> {
    val count = minOf(
        graph.characters.size, graph.ratings.size, graph.doubleRds.size
    )
    return (0 until count).map { index ->
        ChartEntry(graph.characters[index], graph.ratings[index], graph.doubleRds[index])
    }
}

@Composable
private fun ChartRow(rank: Int, row: ChartEntry, low: Double, span: Double) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "$rank. ${row.name}",
                style = CharSorterType.RowName,
                color = CharSorterColor.Ink,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            Text(
                "${row.rating.roundToInt()} ± ${row.doubleRd.roundToInt()}",
                style = CharSorterType.RatingText,
                color = CharSorterColor.Link
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(18.dp).padding(top = 6.dp)) {
            fun xOf(value: Double): Float =
                (size.width * (value - low) / span).toFloat()

            val mid = size.height / 2f
            drawLine(
                color = CharSorterColor.AccentDark.copy(alpha = 0.18f),
                start = Offset(0f, mid),
                end = Offset(size.width, mid),
                strokeWidth = 1.dp.toPx()
            )
            if (DEFAULT_RATING in low..(low + span)) {
                val baseline = xOf(DEFAULT_RATING)
                drawLine(
                    color = CharSorterColor.Muted.copy(alpha = 0.3f),
                    start = Offset(baseline, 0f),
                    end = Offset(baseline, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            drawLine(
                color = CharSorterColor.AccentLight,
                start = Offset(xOf(row.rating - row.doubleRd), mid),
                end = Offset(xOf(row.rating + row.doubleRd), mid),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = CharSorterColor.AccentDark,
                radius = 4.dp.toPx(),
                center = Offset(xOf(row.rating), mid)
            )
        }
    }
}
