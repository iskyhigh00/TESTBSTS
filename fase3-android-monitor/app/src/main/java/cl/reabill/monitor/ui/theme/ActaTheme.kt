@file:OptIn(ExperimentalTextApi::class)

package cl.reabill.monitor.ui.theme

import androidx.compose.runtime.mutableStateOf
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

    var dark: Boolean
        get() = darkState.value
        set(v) { darkState.value = v }

    val Paper: Color get() = if (dark) Color(0xFF0D1117) else Color(0xFFFFFFFF)
    val Ink: Color get() = if (dark) Color(0xFFE8ECEF) else Color(0xFF0D1117)
    val Ink2: Color get() = if (dark) Color(0xFF9AA3AD) else Color(0xFF5F6872)
    val Hairline: Color get() = if (dark) Color(0xFF2B323B) else Color(0xFFDDE1E5)
    val Accent: Color get() = if (dark) Color(0xFF4DB6AC) else Color(0xFF00695C)
    val Error: Color get() = if (dark) Color(0xFFF28B82) else Color(0xFFB3261E)

    const val RATE_MIN = 80.0
}

private fun grotesk(weight: FontWeight) = Font(
    R.font.schibsted_grotesk, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val ActaSans = FontFamily(
    grotesk(FontWeight.Normal), grotesk(FontWeight.Medium),
    grotesk(FontWeight.SemiBold), grotesk(FontWeight.Bold)
)

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
