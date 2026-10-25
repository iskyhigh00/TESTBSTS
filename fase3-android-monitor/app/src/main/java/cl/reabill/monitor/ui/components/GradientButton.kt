package cl.reabill.monitor.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.ActaType
import cl.reabill.monitor.ui.theme.PREVIEW_DIA
import cl.reabill.monitor.ui.theme.PREVIEW_NOCHE
import cl.reabill.monitor.ui.theme.PremiumPreview
import cl.reabill.monitor.ui.theme.PremiumShapes

@Composable
fun GradientButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    filled: Boolean = true, enabled: Boolean = true, danger: Boolean = false,
    height: Dp = 56.dp, textSize: TextUnit = 14.sp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val escala by animateFloatAsState(if (pressed) 0.95f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "escala")
    val shape = PremiumShapes.Button
    val fondo: Modifier = when {
        danger -> Modifier.border(1.dp, Acta.Error, shape)
        filled -> Modifier.background(Brush.horizontalGradient(listOf(Acta.Primary, Acta.Tertiary)))
        else -> Modifier.background(Acta.Accent.copy(alpha = 0.30f))
    }
    val colorTexto = when { danger -> Acta.Error; filled -> Color.White; else -> Acta.Ink }
    Box(
        modifier
            .heightIn(min = height)
            .graphicsLayer { scaleX = escala; scaleY = escala }
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (filled && !danger && enabled)
                    Modifier.shadow(8.dp, shape, spotColor = Acta.Primary.copy(alpha = 0.35f), ambientColor = Acta.Primary.copy(alpha = 0.15f))
                else Modifier
            )
            .clip(shape)
            .then(fondo)
            .clickable(interactionSource = interaction, indication = ripple(color = Acta.Primary), enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, style = ActaType.Button.copy(fontSize = textSize, color = colorTexto),
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Preview(name = "Noche", showBackground = true, uiMode = PREVIEW_NOCHE)
@Preview(name = "Día", showBackground = true, uiMode = PREVIEW_DIA)
@Composable
private fun GradientButtonPreview() = PremiumPreview {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientButton("Principal", {})
        GradientButton("Secundario", {}, filled = false)
        GradientButton("Borrar", {}, danger = true)
        GradientButton("Desactivado", {}, enabled = false)
    }
}
