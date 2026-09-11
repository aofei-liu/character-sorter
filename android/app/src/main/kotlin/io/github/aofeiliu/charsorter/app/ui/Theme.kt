package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val CharSorterColorScheme = lightColorScheme(
    primary = CharSorterColor.AccentDark,
    onPrimary = CharSorterColor.OnAccent,
    primaryContainer = CharSorterColor.AccentLight,
    onPrimaryContainer = CharSorterColor.OnAccent,
    secondary = CharSorterColor.Link,
    onSecondary = Color.White,
    background = CharSorterColor.BackgroundMid,
    onBackground = CharSorterColor.Ink,
    surface = Color.White,
    onSurface = CharSorterColor.Ink,
    surfaceVariant = CharSorterColor.CardFillEnd,
    onSurfaceVariant = CharSorterColor.Muted,
    outline = CharSorterColor.AccentDark,
    error = CharSorterColor.Destructive,
    onError = CharSorterColor.OnDestructive
)

private val CharSorterTypography = Typography(
    displayLarge = CharSorterType.DoneText,
    headlineLarge = CharSorterType.AppTitle,
    headlineMedium = CharSorterType.ScreenTitleLarge,
    headlineSmall = CharSorterType.ScreenTitle,
    titleLarge = CharSorterType.DialogTitle,
    titleMedium = CharSorterType.ListTitle,
    titleSmall = CharSorterType.RowName,
    bodyLarge = CharSorterType.DialogBody,
    bodyMedium = CharSorterType.FandomLarge,
    bodySmall = CharSorterType.FandomSmall,
    labelLarge = CharSorterType.ButtonPrimary,
    labelMedium = CharSorterType.ButtonSecondary,
    labelSmall = CharSorterType.FieldLabel
)

@Composable
fun CharSorterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CharSorterColorScheme,
        typography = CharSorterTypography,
        content = content
    )
}

/**
 * The pastel phone-frame background from the design: a soft diagonal
 * gradient (CSS `linear-gradient(165deg, ...)`) approximated here as a
 * screen-space linear gradient at the same angle.
 */
fun Modifier.charSorterBackground(): Modifier = this.then(
    Modifier.drawBehind {
        val angleRad = Math.toRadians(165.0)
        val dx = sin(angleRad).toFloat()
        val dy = -cos(angleRad).toFloat()
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        val centerX = halfW
        val centerY = halfH
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to CharSorterColor.BackgroundTop,
                    0.55f to CharSorterColor.BackgroundMid,
                    1f to CharSorterColor.BackgroundBottom
                ),
                start = Offset(centerX - dx * halfW, centerY - dy * halfH),
                end = Offset(centerX + dx * halfW, centerY + dy * halfH)
            )
        )
    }
)

/**
 * The small rotated-square bullet used throughout the design as a progress
 * marker, card-corner accent, and empty-state glyph.
 */
@Composable
fun DiamondAccent(
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
    fill: Color = CharSorterColor.AccentLight,
    border: Color? = null
) {
    val shape = RoundedCornerShape(1.dp)
    var diamondModifier = modifier
        .size(size)
        .rotate(45f)
        .background(fill, shape)
    if (border != null) {
        diamondModifier = diamondModifier.border(1.dp, border, shape)
    }
    Box(modifier = diamondModifier)
}
