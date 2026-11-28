package cl.reabill.monitor.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.reabill.monitor.MonitorViewModel
import cl.reabill.monitor.ui.components.ActaButton
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.ActaType

@Composable
internal fun DiagProgresoStrip(p: MonitorViewModel.DiagProg, onCancel: () -> Unit, onEntendido: () -> Unit, modifier: Modifier = Modifier) {
    if (!p.activo && p.aviso.isEmpty()) return
    val pct by animateFloatAsState(p.pct.coerceIn(0f, 1f), label = "diag")
    Column(modifier.fillMaxWidth().border(1.dp, if (p.aviso.isNotEmpty()) Acta.Error else Acta.Ink).padding(12.dp)) {
        if (p.activo) {
            Text(if (p.auto) "DIAGNÓSTICO AUTOMÁTICO" else "DIAGNÓSTICO", style = ActaType.Label)
            Text(p.texto, style = ActaType.Body, modifier = Modifier.padding(top = 4.dp))
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(10.dp).background(Acta.Ink2.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(pct).height(10.dp).background(Acta.Ink))
            }
            Text("${(pct * 100).toInt()} % (estimado)", style = ActaType.Label, modifier = Modifier.padding(top = 6.dp))
            Text(
                "Deja el billetero conectado y sin billetes en la boca: puede mover los motores. Si algo sale mal, cancela.",
                style = ActaType.Label, modifier = Modifier.padding(top = 4.dp)
            )
            ActaButton("Cancelar", onClick = onCancel, height = 44.dp, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        } else {
            Text(p.aviso, style = ActaType.Body.copy(color = Acta.Error))
            ActaButton("Entendido", onClick = onEntendido, height = 44.dp, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }
}
