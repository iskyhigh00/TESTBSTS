package cl.reabill.monitor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.ActaType
import cl.reabill.monitor.ui.theme.PREVIEW_DIA
import cl.reabill.monitor.ui.theme.PREVIEW_NOCHE
import cl.reabill.monitor.ui.theme.PremiumPreview

class NavItem(val label: String, val icon: @Composable (Color) -> Unit)

@Composable
fun AnimatedNavigationBar(
    items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().clip(shape).background(Acta.Glass).border(1.dp, Acta.GlassBorder, shape).padding(6.dp)
    ) {
        val ancho = maxWidth / items.size
        val x by animateDpAsState(ancho * selected, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "pastilla")
        Box(Modifier.offset(x = x).width(ancho).height(56.dp).clip(RoundedCornerShape(22.dp)).background(Acta.Primary.copy(alpha = 0.22f)))
        Row(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, item ->
                val elegida = i == selected
                val color by animateColorAsState(if (elegida) Acta.Ink else Acta.Ink2, label = "color")
                Column(
                    Modifier.width(ancho).heightIn(min = 56.dp).clip(RoundedCornerShape(22.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = Acta.Primary), role = Role.Tab) { onSelect(i) }
                        .semantics { contentDescription = item.label; this.selected = elegida },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    item.icon(color)
                    Text(item.label, style = ActaType.Label.copy(color = color), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Preview(name = "Noche", showBackground = true, uiMode = PREVIEW_NOCHE)
@Preview(name = "Día", showBackground = true, uiMode = PREVIEW_DIA)
@Composable
private fun NavigationBarPreview() = PremiumPreview {
    var elegida by remember { mutableIntStateOf(0) }
    val punto: @Composable (Color) -> Unit = { c -> Box(Modifier.size(20.dp).clip(CircleShape).background(c)) }
    Box(Modifier.padding(20.dp)) {
        AnimatedNavigationBar(
            listOf(NavItem("Inicio", punto), NavItem("Historial", punto), NavItem("Ajustes", punto)),
            elegida, { elegida = it }
        )
    }
}
