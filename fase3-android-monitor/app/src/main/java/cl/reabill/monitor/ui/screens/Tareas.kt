package cl.reabill.monitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.reabill.monitor.MonitorViewModel
import cl.reabill.monitor.Rev
import cl.reabill.monitor.RevDatos
import cl.reabill.monitor.ui.components.ActaButton
import cl.reabill.monitor.ui.components.ActaChip
import cl.reabill.monitor.ui.components.Hairline
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.ActaType

@Composable
internal fun TareasStrip(porRevisar: Int, etiquetas: Int, onRevisar: () -> Unit, onEtiquetas: () -> Unit, modifier: Modifier = Modifier) {
    if (porRevisar == 0 && etiquetas == 0) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (porRevisar > 0) ActaButton("Por revisar ($porRevisar)", onClick = onRevisar, height = 44.dp, modifier = Modifier.weight(1f))
        if (etiquetas > 0) ActaButton("Etiquetas ($etiquetas)", onClick = onEtiquetas, height = 44.dp, modifier = Modifier.weight(1f))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun RevisarDialog(vm: MonitorViewModel, mda: String, rev: Rev?, onClose: () -> Unit) {
    var cambiado by remember(mda) { mutableStateOf(false) }
    var usado by remember(mda) { mutableStateOf(false) }
    var calibraste by remember(mda) { mutableStateOf(false) }
    var limpiaste by remember(mda) { mutableStateOf(false) }
    var variant by remember(mda) { mutableStateOf(false) }
    var pieza by remember(mda) { mutableStateOf(false) }
    var firmware by remember(mda) { mutableStateOf("") }

    var modelo by remember(mda) { mutableStateOf(rev?.modeloLista?.ifEmpty { null } ?: vm.modeloDetectado(mda)) }
    var quien by remember(mda) { mutableStateOf(vm.quienRevisa()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Revisar MDA $mda") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rev?.detalle?.takeIf { it.isNotEmpty() }?.let { Text(it, style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(bottom = 10.dp)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActaChip("Quedó el mismo", !cambiado, { cambiado = false })
                    ActaChip("Lo cambié", cambiado, { cambiado = true })
                }
                if (cambiado) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    ActaChip("Nuevo", !usado, { usado = false })
                    ActaChip("Usado", usado, { usado = true })
                }
                Text("Qué se hizo", style = ActaType.Label, modifier = Modifier.padding(top = 14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    ActaChip("Calibré", calibraste, { calibraste = !calibraste })
                    ActaChip("Limpié", limpiaste, { limpiaste = !limpiaste })
                    ActaChip("Actualicé el bill-set", variant, { variant = !variant })
                    ActaChip("Cambié una pieza", pieza, { pieza = !pieza })
                }
                Text("Modelo", style = ActaType.Label, modifier = Modifier.padding(top = 14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    ActaChip("Cashflow", modelo == "Cashflow", { modelo = if (modelo == "Cashflow") "" else "Cashflow" })
                    ActaChip("Advance", modelo == "Advance", { modelo = if (modelo == "Advance") "" else "Advance" })
                }
                if (cambiado || variant) OutlinedTextField(
                    value = firmware, onValueChange = { firmware = it.take(30); if (Regex("^\\s*255(\\D|$)").containsMatchIn(it)) modelo = "Cashflow" },
                    singleLine = true, label = { Text("Bill-set (255 / 403)") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = quien, onValueChange = { quien = it.take(40) }, singleLine = true, label = { Text("Quién revisó") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            ActaButton(
                "Guardar", filled = true, enabled = quien.isNotBlank(), height = 44.dp, modifier = Modifier.widthIn(min = 110.dp),
                onClick = {
                    val fw = if (cambiado || variant) firmware else ""
                    vm.revisarBilletero(mda, RevDatos(quien, cambiado, usado, calibraste, limpiaste, variant, pieza, fw, modelo)); onClose()
                }
            )
        },
        dismissButton = { ActaButton("Cancelar", onClick = onClose, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
    )
}

@Composable
internal fun EtiquetasDialog(vm: MonitorViewModel, onClose: () -> Unit) {
    val pend by vm.etqPendientes.collectAsStateWithLifecycle()
    val imp by vm.print.collectAsStateWithLifecycle()
    val hoy = remember { java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).format(java.util.Date()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Etiquetas de intercambio") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (pend.isEmpty()) Text("Todas impresas.", style = ActaType.Body.copy(color = Acta.Ink2))
                pend.forEachIndexed { i, e ->
                    var fecha by remember(e.id) { mutableStateOf(hoy) }
                    if (i > 0) Hairline(Modifier.padding(vertical = 10.dp))
                    Text("${e.a} ⇄ ${e.b}", style = ActaType.Section)
                    if (e.nota.isNotEmpty()) Text(e.nota, style = ActaType.Body.copy(color = Acta.Ink2))
                    OutlinedTextField(
                        value = fecha, onValueChange = { fecha = it.take(12) }, singleLine = true, label = { Text("Fecha") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    ActaButton(
                        if (imp.printing) "Imprimiendo…" else "Imprimir 2 etiquetas", onClick = { vm.imprimirIntercambio(e, fecha) },
                        filled = true, enabled = !imp.printing, height = 46.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                if (imp.message.isNotEmpty()) Text(imp.message, style = ActaType.Label, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { ActaButton("Cerrar", onClick = onClose, filled = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
    )
}
