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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.RankedCharacter
import io.github.aofeiliu.charsorter.client.RatingHistory
import io.github.aofeiliu.charsorter.client.RatingPoint
import java.time.OffsetDateTime
import java.time.Year
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Where an unplayed character sits before any comparison moves it. */
private const val DEFAULT_RATING = 1500.0

/** Above this many matches the line is downsampled to stay readable. */
private const val MAX_PLOT_POINTS = 80

/** Below this many, each match is also marked with its own dot. */
private const val MAX_DOT_POINTS = 40

private val DayMonth = DateTimeFormatter.ofPattern("d MMM")
private val DayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")

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
            "Rank ${character.rank} · ${history.rating.roundToInt()} ± " +
                "${doubleRd.roundToInt()} · ${history.history.size} matches",
            style = CharSorterType.ProgressText,
            color = CharSorterColor.Muted
        )
    }
    Text(
        "Scored at ${(history.rating - doubleRd).roundToInt()}, the minimum of that interval.",
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
    val plotted = remember(points) { downsample(points, MAX_PLOT_POINTS) }
    val measurer = rememberTextMeasurer()
    val baselineStyle = remember {
        CharSorterType.FandomSmall.copy(color = CharSorterColor.Muted.copy(alpha = 0.75f))
    }
    // Scaled to the line, not the band: the first matches carry an rd near
    // 350, and letting that set the range squashes the trend into a strip.
    // The band clips at the edges instead.
    val lowest = plotted.minOf { it.rating }
    val highest = plotted.maxOf { it.rating }
    val pad = ((highest - lowest) * 0.08).coerceAtLeast(20.0)
    val low = lowest - pad
    val high = highest + pad
    val span = high - low

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
            highest.roundToInt().toString(),
            style = CharSorterType.FandomSmall,
            color = CharSorterColor.Muted
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(vertical = 6.dp)
                .clipToBounds()
        ) {
            fun xOf(index: Int): Float = when {
                plotted.size == 1 -> size.width / 2f
                else -> size.width * index / (plotted.size - 1).toFloat()
            }
            fun yOf(value: Double): Float =
                (size.height * (1.0 - (value - low) / span)).toFloat()

            if (DEFAULT_RATING in low..high) {
                val baseline = yOf(DEFAULT_RATING)
                val label = measurer.measure(
                    DEFAULT_RATING.roundToInt().toString(), baselineStyle
                )
                drawLine(
                    color = CharSorterColor.Muted.copy(alpha = 0.35f),
                    start = Offset(label.size.width + 6.dp.toPx(), baseline),
                    end = Offset(size.width, baseline),
                    strokeWidth = 1.dp.toPx()
                )
                drawText(
                    label,
                    topLeft = Offset(
                        0f,
                        (baseline - label.size.height / 2f)
                            .coerceIn(0f, size.height - label.size.height)
                    )
                )
            }

            if (plotted.size > 1) {
                val band = Path()
                plotted.forEachIndexed { index, point ->
                    val x = xOf(index)
                    val y = yOf(point.rating + 2 * point.rd)
                    if (index == 0) band.moveTo(x, y) else band.lineTo(x, y)
                }
                for (index in plotted.indices.reversed()) {
                    band.lineTo(xOf(index), yOf(plotted[index].rating - 2 * plotted[index].rd))
                }
                band.close()
                drawPath(band, color = CharSorterColor.AccentLight.copy(alpha = 0.45f))

                val line = Path()
                plotted.forEachIndexed { index, point ->
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

            if (plotted.size <= MAX_DOT_POINTS) {
                plotted.forEachIndexed { index, point ->
                    drawCircle(
                        color = CharSorterColor.AccentDark,
                        radius = 3.dp.toPx(),
                        center = Offset(xOf(index), yOf(point.rating))
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                lowest.roundToInt().toString(),
                style = CharSorterType.FandomSmall,
                color = CharSorterColor.Muted
            )
            Text(
                spanOf(points.first().timestamp, points.last().timestamp),
                style = CharSorterType.FandomSmall,
                color = CharSorterColor.Muted
            )
        }
    }
}

/**
 * Thins a long history to [limit] points for drawing.
 *
 * Largest-Triangle-Three-Buckets: it keeps whichever point in each bucket
 * forms the largest triangle with its neighbours, which preserves the peaks
 * and dips that plain every-Nth sampling drops. It guarantees only the
 * first and last, so the peak and trough the chart labels are added back.
 */
private fun downsample(points: List<RatingPoint>, limit: Int): List<RatingPoint> {
    if (points.size <= limit || limit < 3) {
        return points
    }
    val bucket = (points.size - 2).toDouble() / (limit - 2)
    val sampled = ArrayList<Int>(limit)
    sampled.add(0)
    var anchor = 0
    for (i in 0 until limit - 2) {
        val avgStart = (floor((i + 1) * bucket).toInt() + 1).coerceIn(1, points.size - 1)
        val avgEnd = (floor((i + 2) * bucket).toInt() + 1).coerceIn(avgStart + 1, points.size)
        var avgX = 0.0
        var avgY = 0.0
        for (j in avgStart until avgEnd) {
            avgX += j
            avgY += points[j].rating
        }
        avgX /= (avgEnd - avgStart)
        avgY /= (avgEnd - avgStart)

        val start = (floor(i * bucket).toInt() + 1).coerceIn(1, points.size - 1)
        val end = (floor((i + 1) * bucket).toInt() + 1).coerceIn(start + 1, points.size)
        var best = start
        var bestArea = -1.0
        for (j in start until end) {
            val area = abs(
                (anchor - avgX) * (points[j].rating - points[anchor].rating) -
                    (anchor - j.toDouble()) * (avgY - points[anchor].rating)
            )
            if (area > bestArea) {
                bestArea = area
                best = j
            }
        }
        sampled.add(best)
        anchor = best
    }
    sampled.add(points.size - 1)
    val extremes = listOf(
        points.indices.minBy { points[it].rating },
        points.indices.maxBy { points[it].rating }
    )
    return (sampled + extremes).distinct().sorted().map { points[it] }
}

private fun parsedOrNull(timestamp: String): OffsetDateTime? = try {
    OffsetDateTime.parse(timestamp)
} catch (err: DateTimeParseException) {
    null
}

/** Short date, carrying the year unless it is the current one. */
private fun dayMonthOf(timestamp: String): String {
    val parsed = parsedOrNull(timestamp) ?: return timestamp
    val format = if (parsed.year == Year.now().value) DayMonth else DayMonthYear
    return parsed.format(format)
}

/**
 * The span two timestamps cover. A history can run for years, and "10 Sep –
 * 11 Sep" across two of them reads as a single day, so both ends carry the
 * year unless both fall in the current one.
 */
private fun spanOf(first: String, last: String): String {
    val from = parsedOrNull(first)
    val to = parsedOrNull(last)
    if (from == null || to == null) {
        return "$first – $last"
    }
    val thisYear = Year.now().value
    val format =
        if (from.year == thisYear && to.year == thisYear) DayMonth else DayMonthYear
    return "${from.format(format)} – ${to.format(format)}"
}
