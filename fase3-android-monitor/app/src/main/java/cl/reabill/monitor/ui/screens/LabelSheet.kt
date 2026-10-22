package cl.reabill.monitor.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.reabill.monitor.LabelFields
import cl.reabill.monitor.LabelRenderer
import cl.reabill.monitor.MonitorViewModel
import cl.reabill.monitor.ui.components.ActaButton
import cl.reabill.monitor.ui.components.ActaChip
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.ActaType

private val LENGTHS = listOf(30, 40, 50, 60, 80)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelSheet(vm: MonitorViewModel, onClose: () -> Unit) {
    val ui by vm.print.collectAsStateWithLifecycle()
    val prefs by vm.labelPrefs.collectAsStateWithLifecycle()
    val accounting by vm.accounting.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    val perms = remember {
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) vm.labelScan() else vm.labelNote("Falta el permiso de Bluetooth para buscar la impresora.")
    }

    val weekly by vm.weekly.collectAsStateWithLifecycle()
    val mdaMap by vm.mdaMap.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val data = remember(accounting, status, weekly, mdaMap, history) { vm.labelData() }
    val preview = remember(data, prefs) {
        data?.let { LabelRenderer.render(it, prefs.lengthMm, prefs.fields, prefs.names, vm.labelStyle(prefs)).asImageBitmap() }
    }
    var showNames by remember { mutableStateOf(false) }
    if (showNames) NamesDialog(prefs.names, onSave = { vm.setLabelPrefs(prefs.copy(names = it)); showNames = false }, onClose = { showNames = false })
    val spacing = Arrangement.spacedBy(8.dp)

    AlertDialog(
        onDismissRequest = { vm.labelStopScan(); onClose() },
        title = { Text("Impresora y etiqueta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp)) {
                if (preview == null) {
                    Text("Lee la contabilidad de un equipo (o usa el demo) para ver la vista previa de la etiqueta.", style = ActaType.Body)
                } else {
                    Image(
                        preview, contentDescription = "Vista previa de la etiqueta",
                        modifier = Modifier.fillMaxWidth().border(1.dp, Acta.Hairline).background(Color.White),
                        contentScale = ContentScale.FillWidth, filterQuality = FilterQuality.None
                    )
                }

                val previa = data
                if (previa != null && previa.fromPrevious) {
                    Text(
                        "La contabilidad está en cero (recién borrada): la aceptación real es la de la última lectura antes de borrar, del " +
                            java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(previa.date)) + ".",
                        style = ActaType.Label, modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Datos en la etiqueta", style = ActaType.Label)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    LabelFields.ALL.forEach { k ->
                        val on = k in prefs.fields
                        ActaChip(LabelFields.NAMES.getValue(k), on, {
                            vm.setLabelPrefs(prefs.copy(fields = if (on) prefs.fields - k else prefs.fields + k))
                        })
                    }
                }

                ActaButton(
                    "Cambiar los textos (AR, AS, MDA…)", onClick = { showNames = true },
                    height = 40.dp, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )

                Spacer(Modifier.height(14.dp))
                Text("Tamaño del texto", style = ActaType.Label)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    listOf(60 to "Chico", 80 to "Medio", 100 to "Máximo").forEach { (v, n) ->
                        ActaChip(n, prefs.scale == v, { vm.setLabelPrefs(prefs.copy(scale = v)) })
                    }
                }
                Text(
                    "Máximo = el texto más grande que cabe en los 12 mm de la cinta. Para agrandarlo más, usa «2 líneas» o quita datos.",
                    style = ActaType.Label, modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(14.dp))
                Text("Distribución", style = ActaType.Label)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    ActaChip("4 líneas", !prefs.twoLines, { vm.setLabelPrefs(prefs.copy(twoLines = false)) })
                    ActaChip("2 líneas (más grande)", prefs.twoLines, { vm.setLabelPrefs(prefs.copy(twoLines = true)) })
                }

                Spacer(Modifier.height(14.dp))
                Text("Posición del texto", style = ActaType.Label)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    ActaChip("Izquierda", prefs.align == 0, { vm.setLabelPrefs(prefs.copy(align = 0)) })
                    ActaChip("Centro", prefs.align == 1, { vm.setLabelPrefs(prefs.copy(align = 1)) })
                    ActaChip("Derecha", prefs.align == 2, { vm.setLabelPrefs(prefs.copy(align = 2)) })
                }
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    ActaChip("Arriba", prefs.vpos == 0, { vm.setLabelPrefs(prefs.copy(vpos = 0)) })
                    ActaChip("Al medio", prefs.vpos == 1, { vm.setLabelPrefs(prefs.copy(vpos = 1)) })
                    ActaChip("Abajo", prefs.vpos == 2, { vm.setLabelPrefs(prefs.copy(vpos = 2)) })
                }
                Text("Arriba / medio / abajo se nota con tamaño Chico o Medio y «Líneas juntas».", style = ActaType.Label, modifier = Modifier.padding(top = 4.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    ActaChip("Líneas juntas", prefs.compact, { vm.setLabelPrefs(prefs.copy(compact = true)) })
                    ActaChip("Líneas separadas", !prefs.compact, { vm.setLabelPrefs(prefs.copy(compact = false)) })
                }

                Spacer(Modifier.height(14.dp))
                Text("Largo (mm)", style = ActaType.Label)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    LENGTHS.forEach { mm -> ActaChip("$mm", mm == prefs.lengthMm, { vm.setLabelPrefs(prefs.copy(lengthMm = mm)) }) }
                }

                Spacer(Modifier.height(14.dp))
                Text("Cinta", style = ActaType.Label)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    ActaChip("Continua", !prefs.dieCut, { vm.setLabelPrefs(prefs.copy(dieCut = false)) })
                    ActaChip("Con separación", prefs.dieCut, { vm.setLabelPrefs(prefs.copy(dieCut = true)) })
                }

                Spacer(Modifier.height(14.dp))
                Text("Opciones", style = ActaType.Label)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    ActaChip("Girar 180°", prefs.rotate, { vm.setLabelPrefs(prefs.copy(rotate = !prefs.rotate)) })
                    ActaChip("Modo compatible", prefs.confirmed, { vm.setLabelPrefs(prefs.copy(confirmed = !prefs.confirmed)) })
                }
                Text(
                    "Modo compatible: envía los datos esperando la confirmación de la impresora en cada trozo. Es más lento, pero sirve si la etiqueta sale cortada o con basura. Déjalo apagado si imprime bien.",
                    style = ActaType.Label, modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Copias", style = ActaType.Label, modifier = Modifier.weight(1f))
                    ActaButton("−", { vm.setLabelPrefs(prefs.copy(copies = (prefs.copies - 1).coerceAtLeast(1))) }, height = 40.dp, modifier = Modifier.width(48.dp))
                    Text("${prefs.copies}", style = ActaType.Cell, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    ActaButton("+", { vm.setLabelPrefs(prefs.copy(copies = (prefs.copies + 1).coerceAtMost(10))) }, height = 40.dp, modifier = Modifier.width(48.dp))
                }

                Spacer(Modifier.height(18.dp))
                Text("Impresora", style = ActaType.Label)
                Text(
                    ui.connectedName?.let { "Conectada: $it" }
                        ?: if (prefs.lastName.isNotEmpty()) "Guardada: ${prefs.lastName} (se conecta sola al imprimir)" else "Sin impresora elegida",
                    style = ActaType.Body, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                FlowRow(horizontalArrangement = spacing, verticalArrangement = spacing) {
                    ActaButton(
                        if (ui.scanning) "Detener búsqueda" else "Buscar impresora",
                        onClick = { if (ui.scanning) vm.labelStopScan() else askPerms.launch(perms) },
                        height = 42.dp, modifier = Modifier.width(170.dp)
                    )
                    if (prefs.lastAddress.isNotEmpty()) {
                        if (ui.connectedName == null)
                            ActaButton("Conectar", { vm.labelConnectLast() }, height = 42.dp, modifier = Modifier.width(110.dp))
                        ActaButton("Olvidar", { vm.labelForget() }, danger = true, height = 42.dp, modifier = Modifier.width(100.dp))
                    }
                }
                ui.devices.take(12).forEach { f ->
                    ActaButton(
                        (if (f.likely) "" else "") + f.name + "  ·  " + f.address.takeLast(5) + "  ·  ${f.rssi} dBm",
                        onClick = { vm.labelConnect(f) }, filled = f.likely, height = 42.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                if (ui.message.isNotEmpty()) Text(ui.message, style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = {
            ActaButton(
                if (ui.printing) "Imprimiendo…" else "Imprimir prueba", onClick = { vm.labelPrint() },
                filled = true, height = 44.dp, modifier = Modifier.width(150.dp),
                enabled = preview != null && !ui.printing && prefs.fields.isNotEmpty()
            )
        },
        dismissButton = { ActaButton("Cerrar", onClick = { vm.labelStopScan(); onClose() }, height = 44.dp, modifier = Modifier.width(100.dp)) }
    )
}

@Composable
private fun NamesDialog(actual: Map<String, String>, onSave: (Map<String, String>) -> Unit, onClose: () -> Unit) {
    val editables = listOf(LabelFields.MDA, LabelFields.REAL, LabelFields.WEEK, LabelFields.SERIAL, LabelFields.FW, LabelFields.BS, LabelFields.DATE, LabelFields.MODEL)
    val textos = remember { mutableStateMapOf<String, String>().apply { editables.forEach { put(it, LabelFields.textOf(it, actual)) } } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Textos de la etiqueta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                Text("Lo que se escribe antes de cada dato. Déjalo vacío para no poner texto.", style = ActaType.Label)
                editables.forEach { k ->
                    OutlinedTextField(
                        value = textos[k] ?: "", onValueChange = { textos[k] = it.take(12) },
                        singleLine = true, label = { Text(LabelFields.NAMES.getValue(k)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = { ActaButton("Guardar", { onSave(textos.toMap()) }, filled = true, height = 44.dp, modifier = Modifier.width(110.dp)) },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActaButton("Restablecer", { editables.forEach { textos[it] = LabelFields.DEFAULT_TEXT[it] ?: "" } }, height = 44.dp, modifier = Modifier.width(120.dp))
                ActaButton("Cancelar", onClose, height = 44.dp, modifier = Modifier.width(100.dp))
            }
        }
    )
}
