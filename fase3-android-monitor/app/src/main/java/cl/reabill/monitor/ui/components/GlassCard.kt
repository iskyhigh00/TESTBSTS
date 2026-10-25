package cl.reabill.monitor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.ActaType
import cl.reabill.monitor.ui.theme.PREVIEW_DIA
import cl.reabill.monitor.ui.theme.PREVIEW_NOCHE
import cl.reabill.monitor.ui.theme.PremiumPreview
import cl.reabill.monitor.ui.theme.PremiumShapes

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = PremiumShapes.CardLarge,
    contentPadding: Dp = 20.dp,
    entrada: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val aparecer = remember { Animatable(if (entrada) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (entrada) aparecer.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Column(
        modifier
            .graphicsLayer {
                alpha = aparecer.value.coerceIn(0f, 1f)
                translationY = (1f - aparecer.value) * size.height / 3f
            }
            .shadow(8.dp, shape, spotColor = Acta.Primary.copy(alpha = 0.15f), ambientColor = Acta.Primary.copy(alpha = 0.1f))
            .clip(shape)
            .background(Acta.Glass)
            .background(Brush.verticalGradient(listOf(Acta.Primary.copy(alpha = 0.10f), Color.Transparent)))
            .border(1.dp, Acta.GlassBorder, shape)
            .padding(contentPadding),
        content = content
    )
}

@Preview(name = "Noche", showBackground = true, uiMode = PREVIEW_NOCHE)
@Preview(name = "Día", showBackground = true, uiMode = PREVIEW_DIA)
@Composable
private fun GlassCardPreview() = PremiumPreview {
    GlassCard(Modifier.fillMaxWidth().padding(20.dp), entrada = false) {
        Text("Tarjeta de vidrio", style = ActaType.Section)
        Text("Fondo translúcido, borde fino y sombra de color.", style = ActaType.Body)
    }
}
