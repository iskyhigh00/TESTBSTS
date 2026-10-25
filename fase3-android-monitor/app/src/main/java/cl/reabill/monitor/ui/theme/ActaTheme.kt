@file:OptIn(ExperimentalTextApi::class)

package cl.reabill.monitor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cl.reabill.monitor.R

object Acta {
    private val darkState = mutableStateOf(false)
    private val premiumState = mutableStateOf(false)

    var dark: Boolean
        get() = darkState.value
        set(v) { darkState.value = v }

    var premium: Boolean
        get() = premiumState.value
        set(v) { premiumState.value = v }

    val Paper: Color get() = when {
        premium -> if (dark) PremiumColors.BackgroundDark else PremiumColors.BackgroundLight
        dark -> Color(0xFF0D1117); else -> Color(0xFFFFFFFF)
    }
    val Ink: Color get() = when {
        premium -> if (dark) PremiumColors.InkDark else PremiumColors.InkLight
        dark -> Color(0xFFE8ECEF); else -> Color(0xFF0D1117)
    }
    val Ink2: Color get() = when {
        premium -> if (dark) PremiumColors.Ink2Dark else PremiumColors.Ink2Light
        dark -> Color(0xFF9AA3AD); else -> Color(0xFF5F6872)
    }
    val Hairline: Color get() = when {
        premium -> if (dark) PremiumColors.OutlineDark else PremiumColors.OutlineLight
        dark -> Color(0xFF2B323B); else -> Color(0xFFDDE1E5)
    }

    val Raised: Color get() = when {
        premium -> if (dark) PremiumColors.SurfaceVariantDark else PremiumColors.SurfaceLight
        dark -> Color(0xFF161B22); else -> Color(0xFFFFFFFF)
    }

    val Sistema: Color get() = when {
        premium -> if (dark) PremiumColors.Tertiary else PremiumColors.TertiaryLight
        dark -> Color(0xFFFFB74D); else -> Color(0xFFC25E00)
    }
    val Accent: Color get() = when {
        premium -> if (dark) PremiumColors.Secondary else PremiumColors.SecondaryLight
        dark -> Color(0xFF4DB6AC); else -> Color(0xFF00695C)
    }
    val Error: Color get() = when {
        premium -> if (dark) PremiumColors.ErrorDark else PremiumColors.ErrorLight
        dark -> Color(0xFFF28B82); else -> Color(0xFFB3261E)
    }

    val Primary: Color get() = PremiumColors.Primary
    val Tertiary: Color get() = PremiumColors.Tertiary

    val Glass: Color get() = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.65f)
    val GlassBorder: Color get() = if (dark) Color.White.copy(alpha = 0.12f) else PremiumColors.Primary.copy(alpha = 0.14f)

    const val RATE_MIN = 80.0
}

private fun grotesk(weight: FontWeight) = Font(
    R.font.schibsted_grotesk, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

private val ActaSansClasico = FontFamily(
    grotesk(FontWeight.Normal), grotesk(FontWeight.Medium),
    grotesk(FontWeight.SemiBold), grotesk(FontWeight.Bold)
)

val ActaSans: FontFamily get() = if (Acta.premium) SpaceGrotesk else ActaSansClasico

val ActaMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold)
)

object ActaType {
    val Label get() = TextStyle(fontFamily = ActaSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Acta.Ink2)
    val Body get() = TextStyle(fontFamily = ActaSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = Acta.Ink)
    val Section get() = TextStyle(fontFamily = ActaSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Acta.Ink)
    val Link get() = TextStyle(fontFamily = ActaSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Acta.Accent)
    val Button get() = TextStyle(fontFamily = ActaSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val Hero get() = TextStyle(
        fontFamily = ActaSans, fontWeight = FontWeight.Bold, fontSize = 72.sp,
        letterSpacing = (-3).sp, color = Acta.Ink, fontFeatureSettings = "tnum"
    )
    val Title get() = TextStyle(
        fontFamily = ActaSans, fontWeight = FontWeight.Bold, fontSize = 44.sp,
        letterSpacing = (-1.5).sp, color = Acta.Ink
    )
    val BillSet get() = TextStyle(
        fontFamily = ActaMono, fontWeight = FontWeight.Medium, fontSize = 28.sp,
        letterSpacing = (-1).sp, color = Acta.Ink
    )
    val Serial get() = BillSet.copy(fontSize = 20.sp, letterSpacing = 0.sp)
    val Value get() = TextStyle(fontFamily = ActaMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Acta.Ink)
    val Cell get() = TextStyle(fontFamily = ActaMono, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = Acta.Ink)
    val Tiny get() = TextStyle(fontFamily = ActaMono, fontWeight = FontWeight.Normal, fontSize = 10.sp, color = Acta.Ink2)
    val Log get() = TextStyle(fontFamily = ActaMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = Acta.Ink)
}

@Composable
fun ActaMaterial(content: @Composable () -> Unit) {
    if (Acta.premium) {
        MaterialTheme(colorScheme = premiumColorScheme(Acta.dark), typography = premiumTypography(), shapes = PremiumShapes.material, content = content)
        return
    }
    val d = Acta.dark
    val colores = if (d) darkColorScheme(
        primary = Acta.Accent, onPrimary = Acta.Paper, background = Acta.Paper, onBackground = Acta.Ink,
        surface = Acta.Paper, onSurface = Acta.Ink, onSurfaceVariant = Acta.Ink2,
        surfaceContainerHigh = Acta.Raised, surfaceContainerHighest = Acta.Raised, surfaceTint = Acta.Raised,
        outline = Acta.Ink2, outlineVariant = Acta.Hairline, error = Acta.Error
    ) else lightColorScheme(
        primary = Acta.Accent, onPrimary = Acta.Paper, background = Acta.Paper, onBackground = Acta.Ink,
        surface = Acta.Paper, onSurface = Acta.Ink, onSurfaceVariant = Acta.Ink2,
        surfaceContainerHigh = Acta.Raised, surfaceContainerHighest = Acta.Raised, surfaceTint = Acta.Raised,
        outline = Acta.Ink2, outlineVariant = Acta.Hairline, error = Acta.Error
    )
    val tipos = Typography(
        headlineSmall = TextStyle(fontFamily = ActaSans, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.4).sp),
        titleLarge = TextStyle(fontFamily = ActaSans, fontWeight = FontWeight.Bold, fontSize = 22.sp),
        bodyLarge = TextStyle(fontFamily = ActaMono, fontWeight = FontWeight.Medium, fontSize = 15.sp),
        bodyMedium = TextStyle(fontFamily = ActaSans, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = ActaSans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = ActaSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    )
    MaterialTheme(
        colorScheme = colores, typography = tipos,
        shapes = Shapes(extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(20.dp)),
        content = content
    )
}
