package cl.reabill.monitor.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.reabill.monitor.MonitorViewModel
import cl.reabill.monitor.ui.components.ActaButton
import cl.reabill.monitor.ui.components.Hairline
import cl.reabill.monitor.ui.components.HoldButton
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.ActaType

@Composable
fun DiagSheet(visible: Boolean, vm: MonitorViewModel, serial: String, onClose: () -> Unit) {
    val ui by vm.diag.collectAsStateWithLifecycle()
    val close = { if (!ui.leyendo) { vm.cerrarDiag(); onClose() } }
    BackHandler(enabled = visible, onBack = { close() })
    fun p(x: Double?) = if (x == null) "—" else "%.1f %%".format(java.util.Locale.US, x)
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Column(Modifier.fillMaxSize().background(Acta.Paper).padding(Gutter)) {
            Text("Equipo $serial", style = ActaType.Label)
            Text("Diagnóstico", style = ActaType.Title, modifier = Modifier.padding(top = 6.dp))
            Text(
                "Envía las mismas dos consultas que STS al ejecutar el diagnóstico. Puede mover los motores del billetero: quita cualquier billete de la boca antes. " +
                    "El resultado queda en el Registro para compararlo con el % de rechazo.",
                style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            Hairline(color = Acta.Ink)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                val r = ui.resultado
                if (r != null) {
                    @Composable fun fila(t: String, v: String) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(t, style = ActaType.Body, modifier = Modifier.weight(1f))
                            Text(v, style = ActaType.Body)
                        }
                        Hairline()
                    }
                    Text("ACEPTACIÓN", style = ActaType.Label, modifier = Modifier.padding(top = 14.dp))
                    fila("Real, de toda la vida del billetero", p(r.realVida))
                    fila("Real reciente" + (r.reciente?.let { " (${"%.1f".format(java.util.Locale.US, it.dias)} días, ${it.insertados} billetes)" } ?: ""), p(r.reciente?.pct))
                    fila("Sistema, última semana" + (r.sistemaConteo?.let { " (${it.first} aceptados, ${it.second} rechazados)" } ?: ""), p(r.sistema))
                    Text("LO QUE LEE EL DIAGNÓSTICO", style = ActaType.Label, modifier = Modifier.padding(top = 14.dp))
                    fila("Sensor del stacker / umbral", "${r.sensor} / ${r.umbral}")
                    fila("Otros valores (aún sin interpretar)", "A ${r.a} · C ${r.c} · E ${r.e} · F ${r.f}")
                    Text("Referencia de capturas (pocas muestras): con falla A 275–442, C 62, E 7–8; sanos A 492–1027, C 81–170, E 11–32.", style = ActaType.Label, modifier = Modifier.padding(top = 6.dp))
                    if (!r.valido) Text("El formato de la respuesta no es el esperado: revisa el Registro.", style = ActaType.Body.copy(color = Acta.Error), modifier = Modifier.padding(top = 8.dp))
                }
                if (ui.mensaje.isNotEmpty()) Text(ui.mensaje, style = ActaType.Body, modifier = Modifier.padding(top = 14.dp))
            }
            HoldButton("Leer diagnóstico", "mantener presionado", onArmed = { vm.leerDiagnostico() }, enabled = !ui.leyendo, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            ActaButton("Volver", onClick = { close() }, enabled = !ui.leyendo, modifier = Modifier.fillMaxWidth())
        }
    }
}

