package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.RankedCharacter
import io.github.aofeiliu.charsorter.client.RatingHistory
import io.github.aofeiliu.charsorter.client.RatingPoint
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/** Where an unplayed character sits before any comparison moves it. */
private const val DEFAULT_RATING = 1500.0

private val DayMonth = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun TrendScreen(
    list: CharacterList,
    character: RankedCharacter,
    history: RatingHistory?,
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
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                // Names stay sentence case; only titles are uppercased.
                Text(
                    character.name,
                    style = CharSorterType.CharacterName.copy(
                        fontSize = 22.sp, lineHeight = 26.sp
                    ),
                    color = CharSorterColor.Ink
                )
                Text(
                    character.fandom,
                    style = CharSorterType.FandomSmall,
                    color = CharSorterColor.Muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
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

        when {
            busy && history == null -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = CharSorterColor.AccentDark,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            history == null -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Couldn't load this character's history.",
                    style = CharSorterType.DialogBody,
                    color = CharSorterColor.Muted
                )
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry", style = CharSorterType.ButtonSecondary, color = CharSorterColor.Link)
                }
            }
            else -> TrendBody(character, history)
        }
    }
}

@Composable
private fun TrendBody(character: RankedCharacter, history: RatingHistory) {
    val doubleRd = 2 * history.rd
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DiamondAccent(size = 6.dp, fill = CharSorterColor.AccentDark)
        Text(
            "Rank ${character.rank} · ${history.rating.roundToInt()} ± ${doubleRd.roundToInt()}",
            style = CharSorterType.ProgressText,
            color = CharSorterColor.Muted
        )
    }
    Text(
        "Ranked on ${(history.rating - doubleRd).roundToInt()}, the low end of that range.",
        style = CharSorterType.FandomSmall,
        color = CharSorterColor.Muted,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
    )

    if (history.history.isEmpty()) {
        Text(
            "No comparisons yet, so this character still sits at the starting rating.",
            style = CharSorterType.FandomLarge,
            color = CharSorterColor.Muted,
            modifier = Modifier.padding(top = 16.dp)
        )
        return
    }

    RatingChart(history.history)
    LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
        items(history.history.reversed()) { point ->
            MatchRow(point)
            HorizontalDivider(color = CharSorterColor.AccentDark.copy(alpha = 0.22f))
        }
    }
}

@Composable
private fun MatchRow(point: RatingPoint) {
    val outcome = when {
        point.value > 0 -> "Won"
        point.value < 0 -> "Lost"
        else -> "Tied"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                "$outcome against ${point.opponent.name}",
                style = CharSorterType.RowName,
                color = CharSorterColor.Ink
            )
            Text(
                dayMonthOf(point.timestamp),
                style = CharSorterType.FandomSmall,
                color = CharSorterColor.Muted
            )
        }
        Text(
            point.rating.roundToInt().toString(),
            style = CharSorterType.RatingText,
            color = CharSorterColor.Link
        )
    }
}

/**
 * Rating over matches, with the 2 * rd band around it.
 *
 * The x axis steps per match rather than per day: comparisons arrive in
 * bursts, and spacing them by wall-clock time collapses a whole session into
 * one unreadable column. The ends are dated instead.
 */
@Composable
private fun RatingChart(points: List<RatingPoint>) {
    val low = points.minOf { it.rating - 2 * it.rd }.coerceAtMost(DEFAULT_RATING)
    val high = points.maxOf { it.rating + 2 * it.rd }.coerceAtLeast(DEFAULT_RATING)
    val span = (high - low).coerceAtLeast(1.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    listOf(CharSorterColor.CardFillStart, CharSorterColor.CardFillEnd)
                ),
                shape = CharSorterShape.Card
            )
            .border(1.dp, CharSorterColor.AccentDark.copy(alpha = 0.42f), CharSorterShape.Card)
            .padding(16.dp)
    ) {
        Text(
            high.roundToInt().toString(),
            style = CharSorterType.FandomSmall,
            color = CharSorterColor.Muted
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 6.dp)) {
            fun xOf(index: Int): Float = when {
                points.size == 1 -> size.width / 2f
                else -> size.width * index / (points.size - 1).toFloat()
            }
            fun yOf(value: Double): Float =
                (size.height * (1.0 - (value - low) / span)).toFloat()

            val baseline = yOf(DEFAULT_RATING)
            drawLine(
                color = CharSorterColor.Muted.copy(alpha = 0.35f),
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1.dp.toPx()
            )

            if (points.size > 1) {
                val band = Path()
                points.forEachIndexed { index, point ->
                    val x = xOf(index)
                    val y = yOf(point.rating + 2 * point.rd)
                    if (index == 0) band.moveTo(x, y) else band.lineTo(x, y)
                }
                for (index in points.indices.reversed()) {
                    band.lineTo(xOf(index), yOf(points[index].rating - 2 * points[index].rd))
                }
                band.close()
                drawPath(band, color = CharSorterColor.AccentLight.copy(alpha = 0.45f))

                val line = Path()
                points.forEachIndexed { index, point ->
                    val x = xOf(index)
                    val y = yOf(point.rating)
                    if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }
                drawPath(
                    line,
                    color = CharSorterColor.AccentDark,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            points.forEachIndexed { index, point ->
                drawCircle(
                    color = CharSorterColor.AccentDark,
                    radius = 3.dp.toPx(),
                    center = Offset(xOf(index), yOf(point.rating))
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                low.roundToInt().toString(),
                style = CharSorterType.FandomSmall,
                color = CharSorterColor.Muted
            )
            Text(
                "${dayMonthOf(points.first().timestamp)} – ${dayMonthOf(points.last().timestamp)}",
                style = CharSorterType.FandomSmall,
                color = CharSorterColor.Muted
            )
        }
    }
}

/** The server's ISO timestamp as a short day-and-month, or as sent if unparseable. */
private fun dayMonthOf(timestamp: String): String = try {
    OffsetDateTime.parse(timestamp).format(DayMonth)
} catch (err: DateTimeParseException) {
    timestamp
}
