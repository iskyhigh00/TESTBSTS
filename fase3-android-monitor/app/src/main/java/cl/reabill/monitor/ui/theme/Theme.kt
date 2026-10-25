package cl.reabill.monitor.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

fun premiumColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = PremiumColors.Primary, onPrimary = Color.White,
    primaryContainer = Color(0xFF2E2278), onPrimaryContainer = PremiumColors.PrimaryVariant,
    secondary = PremiumColors.Secondary, onSecondary = Color(0xFF00382A),
    tertiary = PremiumColors.Tertiary, onTertiary = Color(0xFF3F0020),
    background = PremiumColors.BackgroundDark, onBackground = PremiumColors.InkDark,
    surface = PremiumColors.SurfaceDark, onSurface = PremiumColors.InkDark,
    surfaceVariant = PremiumColors.SurfaceVariantDark, onSurfaceVariant = PremiumColors.Ink2Dark,
    surfaceContainerHigh = PremiumColors.SurfaceVariantDark, surfaceContainerHighest = PremiumColors.SurfaceVariantDark,
    surfaceTint = Color.Transparent,
    outline = PremiumColors.Ink2Dark, outlineVariant = PremiumColors.OutlineDark.copy(alpha = 0.3f),
    error = PremiumColors.ErrorDark
) else lightColorScheme(
    primary = PremiumColors.Primary, onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DEFF), onPrimaryContainer = Color(0xFF1E0F7A),
    secondary = PremiumColors.SecondaryLight, onSecondary = Color.White,
    tertiary = PremiumColors.TertiaryLight, onTertiary = Color.White,
    background = PremiumColors.BackgroundLight, onBackground = PremiumColors.InkLight,
    surface = PremiumColors.SurfaceLight, onSurface = PremiumColors.InkLight,
    surfaceVariant = PremiumColors.SurfaceVariantLight, onSurfaceVariant = PremiumColors.Ink2Light,
    surfaceContainerHigh = PremiumColors.SurfaceLight, surfaceContainerHighest = PremiumColors.SurfaceLight,
    surfaceTint = Color.Transparent,
    outline = PremiumColors.Ink2Light, outlineVariant = PremiumColors.OutlineLight,
    error = PremiumColors.ErrorLight
)

@Composable
fun PremiumBackground(content: @Composable () -> Unit) {
    if (!Acta.premium) { content(); return }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    PremiumColors.Primary.copy(alpha = 0.05f), Color.Transparent,
                    PremiumColors.Secondary.copy(alpha = 0.03f)
                )
            )
        )
    ) { content() }
}

object Tema {
    private fun prefs(c: Context) = c.getSharedPreferences("apariencia", Context.MODE_PRIVATE)

    fun cargar(c: Context) { Acta.premium = prefs(c).getBoolean("premium", false) }

    fun guardar(c: Context, premium: Boolean) {
        prefs(c).edit().putBoolean("premium", premium).apply()
        Acta.premium = premium
    }
}

@Composable
fun PremiumPreview(content: @Composable () -> Unit) {
    Acta.premium = true
    Acta.dark = isSystemInDarkTheme()
    ActaMaterial { PremiumBackground { Box(Modifier.background(Acta.Paper)) { content() } } }
}

const val PREVIEW_NOCHE = Configuration.UI_MODE_NIGHT_YES
const val PREVIEW_DIA = Configuration.UI_MODE_NIGHT_NO
