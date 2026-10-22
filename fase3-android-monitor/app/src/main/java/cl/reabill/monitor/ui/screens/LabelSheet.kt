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
import androidx.compose.foundation.layout.widthIn
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

private val LENGTHS = listOf(0, 30, 40, 50, 60, 80)

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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var permisosOk by remember { mutableStateOf(perms.all { androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        permisosOk = res.values.all { it }
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
    var pestana by remember { mutableStateOf(if (prefs.lastAddress.isEmpty()) 1 else 0) }
    var verOtros by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { vm.labelStopScan(); vm.labelDisconnect(); onClose() },
        title = { Text("Impresora y etiqueta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp)) {
                Text(
                    "Impresora: " + (ui.connectedName?.let { "$it (conectada)" } ?: if (prefs.lastName.isNotEmpty()) "${prefs.lastName} (guardada)" else "ninguna elegida"),
                    style = ActaType.Label, modifier = Modifier.padding(bottom = 4.dp)
                )
                if (ui.message.isNotEmpty()) Text(ui.message, style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.padding(top = 4.dp, bottom = 12.dp), horizontalArrangement = spacing) {
                    ActaChip("Etiqueta", pestana == 0, { pestana = 0 })
                    ActaChip("Impresora", pestana == 1, { pestana = 1 })
                }
                if (pestana == 1) {
                if (!permisosOk) {
                    Text("Falta el permiso de Bluetooth: sin él no se pueden buscar impresoras.", style = ActaType.Body.copy(color = Acta.Error), modifier = Modifier.padding(bottom = 8.dp))
                    ActaButton(
                        "Abrir permisos de la app",
                        onClick = {
                            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", ctx.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        danger = true, height = 42.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
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
                val nombrados = ui.devices.filter { it.name != "(sin nombre)" }
                val otros = ui.devices.filter { it.name == "(sin nombre)" }
                fun fila(f: P12Printer.Found) = f.name + "  ·  " + f.address.takeLast(5) + "  ·  ${f.rssi} dBm"
                nombrados.take(8).forEach { f ->
                    ActaButton(fila(f), onClick = { vm.labelConnect(f) }, filled = f.likely, height = 42.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                if (otros.isNotEmpty()) {
                    ActaButton(
                        if (verOtros) "Ocultar sin nombre (${otros.size})" else "Ver sin nombre (${otros.size})",
                        onClick = { verOtros = !verOtros }, height = 38.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    if (verOtros) otros.take(10).forEach { f ->
                        ActaButton(fila(f), onClick = { vm.labelConnect(f) }, height = 42.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
                } else {
                if (preview == null) {
                    Text("Sin lectura.", style = ActaType.Body)
                } else {
                    Image(
                        preview, contentDescription = "Vista previa de la etiqueta",
                        modifier = Modifier.fillMaxWidth().border(1.dp, Acta.Hairline).background(Color.White),
                        contentScale = ContentScale.FillWidth, filterQuality = FilterQuality.None
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

                Spacer(Modifier.height(14.dp))
                Text("Distribución", style = ActaType.Label)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    ActaChip("3 líneas", !prefs.twoLines, { vm.setLabelPrefs(prefs.copy(twoLines = false)) })
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
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    ActaChip("Líneas juntas", prefs.compact, { vm.setLabelPrefs(prefs.copy(compact = true)) })
                    ActaChip("Líneas separadas", !prefs.compact, { vm.setLabelPrefs(prefs.copy(compact = false)) })
                }

                Spacer(Modifier.height(14.dp))
                Text("Largo (mm)", style = ActaType.Label)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = spacing, verticalArrangement = spacing) {
                    LENGTHS.forEach { mm -> ActaChip(if (mm == 0) "Auto" else "$mm", mm == prefs.lengthMm, { vm.setLabelPrefs(prefs.copy(lengthMm = mm)) }) }
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

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Copias", style = ActaType.Label, modifier = Modifier.weight(1f))
                    ActaButton("−", { vm.setLabelPrefs(prefs.copy(copies = (prefs.copies - 1).coerceAtLeast(1))) }, height = 40.dp, modifier = Modifier.width(48.dp))
                    Text("${prefs.copies}", style = ActaType.Cell, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    ActaButton("+", { vm.setLabelPrefs(prefs.copy(copies = (prefs.copies + 1).coerceAtMost(10))) }, height = 40.dp, modifier = Modifier.width(48.dp))
                }

                }
            }
        },
        confirmButton = {
            ActaButton(
                if (ui.printing) "Imprimiendo…" else "Imprimir prueba", onClick = { vm.labelPrint() },
                filled = true, height = 44.dp, modifier = Modifier.width(150.dp),
                enabled = preview != null && !ui.printing && prefs.fields.isNotEmpty()
            )
        },
        dismissButton = { ActaButton("Cerrar", onClick = { vm.labelStopScan(); vm.labelDisconnect(); onClose() }, height = 44.dp, modifier = Modifier.widthIn(min = 100.dp)) }
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
                ActaButton(
                    "Restablecer los textos", { editables.forEach { textos[it] = LabelFields.DEFAULT_TEXT[it] ?: "" } },
                    height = 40.dp, modifier = Modifier.fillMaxWidth()
                )
                editables.forEach { k ->
                    OutlinedTextField(
                        value = textos[k] ?: "", onValueChange = { textos[k] = it.take(12) },
                        singleLine = true, label = { Text(LabelFields.NAMES.getValue(k)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = { ActaButton("Guardar", { onSave(textos.toMap()) }, filled = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) },
        dismissButton = { ActaButton("Cancelar", onClose, height = 44.dp, modifier = Modifier.widthIn(min = 100.dp)) }
    )
}
