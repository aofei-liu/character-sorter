package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object CharSorterShape {
    val Card = RoundedCornerShape(24.dp)
    val LoginField = RoundedCornerShape(16.dp)
    val DialogField = RoundedCornerShape(14.dp)
    val Dialog = RoundedCornerShape(26.dp)

    /** `border-radius: 999px` in the design — a full stadium, at any height. */
    val Pill = CircleShape
}
