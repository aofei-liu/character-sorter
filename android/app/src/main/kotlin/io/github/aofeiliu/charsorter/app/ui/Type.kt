@file:OptIn(ExperimentalTextApi::class)

package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.aofeiliu.charsorter.app.R

val MarcellusFamily = FontFamily(Font(R.font.marcellus_regular, FontWeight.Normal))

/**
 * Quicksand ships upstream only as a variable font, so each weight is the
 * same file with a different `wght` axis setting rather than a separate
 * resource.
 */
val QuicksandFamily = FontFamily(
    Font(R.font.quicksand_variable, FontWeight.W400, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.quicksand_variable, FontWeight.W500, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.quicksand_variable, FontWeight.W600, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.quicksand_variable, FontWeight.W700, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

/**
 * Named text styles from the imported design ("Character Sorter 2b").
 * Marcellus carries titles and character names; screen/list/dialog titles
 * render `.uppercase()` at the call site (a presentational transform, not a
 * change to the stored text) while character names stay sentence case.
 * Quicksand carries every other UI string. A couple of near-duplicate sizes
 * in the original mockup (21px vs 22px dialog titles, 12px vs 12.5px rating
 * text) are unified here to one style each.
 */
object CharSorterType {
    val AppTitle = TextStyle(
        fontFamily = MarcellusFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 31.2.sp,
        letterSpacing = 0.14.em
    )

    val ScreenTitleLarge = TextStyle(
        fontFamily = MarcellusFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 23.sp,
        lineHeight = 27.6.sp,
        letterSpacing = 0.1.em
    )

    val ScreenTitle = TextStyle(
        fontFamily = MarcellusFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 24.7.sp,
        letterSpacing = 0.08.em
    )

    val ListTitle = TextStyle(
        fontFamily = MarcellusFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 23.4.sp,
        letterSpacing = 0.07.em
    )

    val DialogTitle = TextStyle(
        fontFamily = MarcellusFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 27.5.sp,
        letterSpacing = 0.09.em
    )

    val CharacterName = TextStyle(
        fontFamily = MarcellusFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 31.sp,
        lineHeight = 36.6.sp
    )

    val DoneText = TextStyle(
        fontFamily = MarcellusFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.02.em
    )

    val FieldLabel = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W600,
        fontSize = 12.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.04.em
    )

    val FieldValue = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W500,
        fontSize = 15.sp,
        lineHeight = 15.sp
    )

    val ButtonPrimary = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W700,
        fontSize = 15.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.03.em
    )

    val ButtonPrimarySmall = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W700,
        fontSize = 13.5.sp,
        lineHeight = 13.5.sp
    )

    val ButtonSecondary = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        lineHeight = 13.sp
    )

    val ButtonTie = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 14.sp
    )

    val SubtitleBody = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W500,
        fontSize = 13.5.sp,
        lineHeight = 20.25.sp
    )

    val ProgressText = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 16.9.sp
    )

    val FandomLarge = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 19.6.sp
    )

    val FandomSmall = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W500,
        fontSize = 12.5.sp,
        lineHeight = 17.5.sp
    )

    val RowName = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W700,
        fontSize = 15.sp,
        lineHeight = 20.25.sp
    )

    val RatingText = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W600,
        fontSize = 12.5.sp,
        lineHeight = 18.125.sp
    )

    val DialogBody = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 21.7.sp
    )

    val SpinnerLabel = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.04.em
    )
}
