@file:OptIn(ExperimentalTextApi::class)

package cl.reabill.monitor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cl.reabill.monitor.R

private fun space(weight: FontWeight) = Font(
    R.font.space_grotesk, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val SpaceGrotesk = FontFamily(
    space(FontWeight.Normal), space(FontWeight.Medium), space(FontWeight.SemiBold), space(FontWeight.Bold)
)

private fun t(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp
)

fun premiumTypography() = Typography(
    displayLarge = t(57, FontWeight.Bold, 64, -1.5), displayMedium = t(45, FontWeight.Bold, 52, -1.0), displaySmall = t(36, FontWeight.Bold, 44, -0.5),
    headlineLarge = t(32, FontWeight.Bold, 40, -0.5), headlineMedium = t(28, FontWeight.Bold, 36), headlineSmall = t(24, FontWeight.SemiBold, 32),
    titleLarge = t(22, FontWeight.SemiBold, 28), titleMedium = t(16, FontWeight.SemiBold, 24, 0.15), titleSmall = t(14, FontWeight.SemiBold, 20, 0.1),
    bodyLarge = t(16, FontWeight.Normal, 24, 0.3), bodyMedium = t(14, FontWeight.Normal, 20, 0.2), bodySmall = t(12, FontWeight.Normal, 16, 0.3),
    labelLarge = t(14, FontWeight.SemiBold, 20, 0.1), labelMedium = t(12, FontWeight.Medium, 16, 0.5), labelSmall = t(11, FontWeight.Medium, 16, 0.5)
)
