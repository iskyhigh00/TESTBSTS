package cl.reabill.monitor.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.PREVIEW_DIA
import cl.reabill.monitor.ui.theme.PREVIEW_NOCHE
import cl.reabill.monitor.ui.theme.PremiumPreview

fun Modifier.shimmer(): Modifier = composed {
    val t by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f, targetValue = 1f, label = "x",
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart)
    )
    val base = Acta.Ink.copy(alpha = 0.07f)
    val luz = Acta.Ink.copy(alpha = 0.17f)
    drawBehind {
        val ancho = size.width.coerceAtLeast(1f)
        val x = (t * 2f - 0.5f) * ancho
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, luz, base),
                start = Offset(x - ancho * 0.4f, 0f), end = Offset(x + ancho * 0.4f, size.height * 0.3f)
            )
        )
        drawRect(base.copy(alpha = base.alpha * 0.5f))
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    Box(modifier.clip(shape).shimmer())
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier, alto: Dp = 20.dp) {
    Column(
        modifier.semantics { contentDescription = "Cargando" }.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ShimmerBox(Modifier.width(120.dp).height(alto))
        ShimmerBox(Modifier.fillMaxWidth(0.6f).height(alto * 2.4f), RoundedCornerShape(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(Modifier.fillMaxWidth().height(alto * 0.7f))
            ShimmerBox(Modifier.fillMaxWidth(0.8f).height(alto * 0.7f))
        }
    }
}

@Preview(name = "Noche", showBackground = true, uiMode = PREVIEW_NOCHE)
@Preview(name = "Día", showBackground = true, uiMode = PREVIEW_DIA)
@Composable
private fun ShimmerPreview() = PremiumPreview {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SkeletonCard()
        ShimmerBox(Modifier.size(48.dp), CircleShape)
    }
}
