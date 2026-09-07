package com.healthy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/** The palette from `healthy-prototype.html`. */
object HealthyColors {
    val Ground = Color(0xFF131A22)
    val Raised = Color(0xFF1B232D)
    val Raised2 = Color(0xFF222C38)
    val Rule = Color(0xFF2C3846)
    val Paper = Color(0xFFE9E5DB)
    val Muted = Color(0xFF8B95A3)
    val Caffeine = Color(0xFFC9743A)
    val CaffeineDim = Color(0xFF7A4724)
    val Sleep = Color(0xFF6FA8A0)
    val Warn = Color(0xFFC25B4E)
}

private val Scheme = darkColorScheme(
    primary = HealthyColors.Caffeine,
    onPrimary = HealthyColors.Paper,
    secondary = HealthyColors.Sleep,
    onSecondary = HealthyColors.Ground,
    background = HealthyColors.Ground,
    onBackground = HealthyColors.Paper,
    surface = HealthyColors.Raised,
    onSurface = HealthyColors.Paper,
    surfaceVariant = HealthyColors.Raised2,
    onSurfaceVariant = HealthyColors.Muted,
    outline = HealthyColors.Rule,
    error = HealthyColors.Warn,
)

/**
 * The app is dark only, like the prototype. A light scheme is not offered
 * because the screen the user opens most often is the morning one, and a white
 * field at 04:00 is hostile.
 */
@Composable
fun HealthyTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = HealthyTypography,
        content = content,
    )
}

private val HealthyTypography = Typography()

/** Figures line up column-wise when they are tabular. */
val TabularNumeral = TextStyle(
    fontFeatureSettings = "tnum",
    textAlign = TextAlign.Start,
)

/**
 * The big caffeine figure. [lineHeight] is set explicitly because at this size
 * the default leading clips the tops of the digits, and [LineHeightStyle]
 * centres the glyphs in that box rather than letting them ride the baseline.
 */
val HeroNumeral = TextStyle(
    fontSize = 58.sp,
    lineHeight = 68.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-2).sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)
