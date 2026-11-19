package cl.reabill.monitor.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import cl.reabill.monitor.WeeklyData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.reabill.monitor.APP_VERSION
import cl.reabill.monitor.AccountingData
import cl.reabill.monitor.FirmwareFile
import cl.reabill.monitor.MonitorViewModel
import cl.reabill.monitor.ui.Phase
import cl.reabill.monitor.ui.components.ActaButton
import cl.reabill.monitor.ui.components.ActaChip
import cl.reabill.monitor.ui.components.ActivityRule
import cl.reabill.monitor.ui.components.CountText
import cl.reabill.monitor.ui.components.GearButton
import cl.reabill.monitor.ui.components.Hairline
import cl.reabill.monitor.ui.components.HoldButton
import cl.reabill.monitor.ui.components.MarkKind
import cl.reabill.monitor.ui.components.RateScale
import cl.reabill.monitor.ui.components.ResolveText
import cl.reabill.monitor.ui.components.StatusMark
import cl.reabill.monitor.ui.derivePhase
import cl.reabill.monitor.ui.fmtDelta
import cl.reabill.monitor.ui.fmtInt
import cl.reabill.monitor.ui.fmtRate
import cl.reabill.monitor.ui.isBusy
import cl.reabill.monitor.ui.isPlaceholder
import cl.reabill.monitor.ui.label
import cl.reabill.monitor.ui.previousSnapshot
import cl.reabill.monitor.ui.splitLog
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.ActaType

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SettingsSheet(
    visible: Boolean, vm: MonitorViewModel,
    onClose: () -> Unit, onLabelConfig: () -> Unit, onChangelog: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit,
    onExportTraza: () -> Unit, onExportTrazaNube: () -> Unit, onExportResumen: () -> Unit
) {
    BackHandler(enabled = visible, onBack = onClose)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)) + slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 14 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 14 }
    ) {
        val acceso by vm.acceso.collectAsStateWithLifecycle()
        val prefs by vm.labelPrefs.collectAsStateWithLifecycle()
        val busy by vm.busy.collectAsStateWithLifecycle()
        Column(Modifier.fillMaxSize().background(Acta.Paper)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
                Text("Preferencias", style = ActaType.Title.copy(fontSize = 34.sp), modifier = Modifier.padding(top = 18.dp))
                Text("BILLMON v$APP_VERSION", style = ActaType.Tiny, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

                PrefGroup("Datos compartidos") {
                    val n by vm.nubeUi.collectAsStateWithLifecycle()
                    var dlg by remember { mutableStateOf(false) }
                    var salir by remember { mutableStateOf(false) }
                    when {
                        !n.disponible -> Text("Esta versión no trae la conexión a la nube.", style = ActaType.Body.copy(color = Acta.Ink2))
                        n.email.isEmpty() -> {
                            Text("Entra con tu correo y clave de la Bitácora.", style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(bottom = 8.dp))
                            if (n.mensaje.isNotEmpty()) Text(n.mensaje, style = ActaType.Label, modifier = Modifier.padding(bottom = 8.dp))
                            ActaButton("Conectar cuenta", onClick = { dlg = true }, height = 46.dp, modifier = Modifier.fillMaxWidth())
                        }
                        else -> {
                            Text(n.nombre.ifEmpty { n.email }, style = ActaType.Cell)
                            if (n.nombre.isNotEmpty()) Text(n.email, style = ActaType.Label)
                            val cuando = if (n.ultima > 0) "Última sincronización: " +
                                java.text.SimpleDateFormat("dd-MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(n.ultima)) else "Aún no sincroniza."
                            Text(cuando, style = ActaType.Label, modifier = Modifier.padding(top = 4.dp))
                            if (n.mensaje.isNotEmpty()) Text(n.mensaje, style = ActaType.Label, modifier = Modifier.padding(top = 2.dp))
                            ActaButton(
                                if (n.trabajando) "Sincronizando…" else "Sincronizar ahora", onClick = { vm.sincronizarAhora() },
                                enabled = !n.trabajando, height = 46.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActaButton("Cambiar usuario", onClick = { dlg = true }, enabled = !n.trabajando && !busy, height = 46.dp, modifier = Modifier.weight(1f))
                                ActaButton("Salir", onClick = { salir = true }, enabled = !n.trabajando && !busy, danger = true, height = 46.dp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (dlg) UsuarioDialog(vm, onClose = { dlg = false }, onListo = { dlg = false })
                    if (salir) AlertDialog(
                        onDismissRequest = { salir = false },
                        title = { Text("¿Salir de la cuenta?") },
                        text = { Text("Este teléfono deja de sincronizar con la nube hasta que vuelvas a entrar con tu correo y clave.") },
                        confirmButton = { ActaButton("Salir", onClick = { salir = false; vm.nubeDesconectar() }, danger = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) },
                        dismissButton = { ActaButton("Cancelar", onClick = { salir = false }, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
                    )
                }

                PrefGroup("Impresora de etiquetas") {
                    Text(
                        (if (prefs.lastName.isNotEmpty()) "Guardada: ${prefs.lastName}" else "Sin impresora guardada") + if (acceso.admin) "" else " (la define el admin)",
                        style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(bottom = if (acceso.admin) 8.dp else 0.dp)
                    )

                    if (acceso.admin) ActaButton("Configurar impresora y etiqueta", onClick = onLabelConfig, height = 46.dp, modifier = Modifier.fillMaxWidth())
                }

                PrefGroup("Actualización") {
                    val msg by vm.updateMsg.collectAsStateWithLifecycle()
                    val nueva by vm.updateVersion.collectAsStateWithLifecycle()
                    val prog by vm.updateProgress.collectAsStateWithLifecycle()
                    Text("Versión instalada: v$APP_VERSION", style = ActaType.Label, modifier = Modifier.padding(bottom = 4.dp))
                    if (msg.isNotEmpty()) Text(msg, style = ActaType.Body, modifier = Modifier.padding(bottom = 8.dp))
                    if (nueva != null) {
                        if (prog == -2) {
                            ActaButton("Actualizar a v$nueva", onClick = { vm.installUpdate() }, filled = true, height = 46.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                        } else {
                            Text(if (prog >= 0) "Descargando… $prog\u00A0%" else "Descargando…", style = ActaType.Label, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                    ActaButton("Buscar actualización", onClick = { vm.checkForUpdate() }, height = 46.dp, modifier = Modifier.fillMaxWidth())
                    ActaButton("Historial de versiones", onClick = onChangelog, height = 46.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }

                if (acceso.puede(cl.reabill.monitor.Proc.BILLSET)) PrefGroup("Archivos de firmware") {
                    val lib by vm.library.collectAsStateWithLifecycle()
                    val msgArch by vm.archivosMsg.collectAsStateWithLifecycle()
                    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.agregarFirmwares(uris) }
                    Text(
                        "Software y bill-sets del billetero (Advance y Cashflow). Los que agrega el admin quedan para todos los teléfonos. Puedes elegir varios a la vez.",
                        style = ActaType.Label, modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ActaButton("Agregar archivos", onClick = { elegir.launch(arrayOf("*/*")) }, filled = true, height = 46.dp, modifier = Modifier.fillMaxWidth())
                    if (msgArch.isNotEmpty()) Text(msgArch, style = ActaType.Label, modifier = Modifier.padding(top = 6.dp))
                    lib.forEach { f ->
                        Hairline(Modifier.padding(top = 8.dp))
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = ActaType.Body)
                                Text(f.software?.let { s -> "Software · Boot ${s.boot} · aplicación ${s.app}" + if (!s.probado) " · no probado" else "" } ?: "Bill-set #${f.label}", style = ActaType.Label)
                            }
                            ActaButton("Quitar", onClick = { vm.removeFromLibrary(f) }, danger = true, height = 36.dp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                if (acceso.admin) Seccion("Administración")

                if (acceso.admin) PrefGroup("Permisos de técnicos") {
                    val tecnicos = vm.tecnicos()
                    if (tecnicos.isEmpty()) Text("Los técnicos aparecen cuando la Bitácora los publica.", style = ActaType.Body.copy(color = Acta.Ink2))
                    tecnicos.forEachIndexed { i, u ->
                        if (i > 0) Hairline(Modifier.padding(vertical = 10.dp))
                        Text(u.nombre, style = ActaType.Section)
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)
                        ) {
                            cl.reabill.monitor.Proc.values().forEach { p ->
                                val hecho = p.porDefecto || vm.procHecho(p)
                                val sel = vm.procAutorizado(u.correo, p)
                                ActaChip(p.etiqueta, sel, { if (hecho || sel) vm.autorizar(u.correo, p, !sel) }, Modifier.alpha(if (hecho || sel) 1f else 0.4f))
                            }
                        }
                    }
                    Text("Lectura e impresión se permiten por defecto. Puedes desactivarlas para cada trabajador.", style = ActaType.Label, modifier = Modifier.padding(top = 10.dp))
                    if (tecnicos.isNotEmpty() && cl.reabill.monitor.Proc.values().any { !it.porDefecto && !vm.procHecho(it) })
                        Text("Lo atenuado aún no lo has hecho tú, por eso no se puede autorizar a otros.", style = ActaType.Label, modifier = Modifier.padding(top = 10.dp))
                }

                if (acceso.admin) PrefGroup("Billeteros por revisar") {
                    val pend by vm.revPendientes.collectAsStateWithLifecycle()
                    var confirmar by remember { mutableStateOf(false) }
                    var resultado by remember { mutableStateOf("") }
                    Text("${pend.size} en este teléfono. «Borrar la lista» la vacía en todos los teléfonos.", style = ActaType.Body.copy(color = Acta.Ink2))
                    if (resultado.isNotEmpty()) Text(resultado, style = ActaType.Label, modifier = Modifier.padding(top = 4.dp))
                    ActaButton("Borrar la lista (todos los teléfonos)", onClick = { confirmar = true }, danger = true, enabled = pend.isNotEmpty(), height = 46.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    if (confirmar) AlertDialog(
                        onDismissRequest = { confirmar = false },
                        title = { Text("¿Borrar la lista?") },
                        text = { Text("Se quitan los billeteros por revisar de todos los teléfonos con BILLMON. En la Bitácora no se borra nada.") },
                        confirmButton = { ActaButton("Borrar", onClick = { confirmar = false; vm.borrarPendientesParaTodos { err -> resultado = err ?: "Lista borrada." } }, danger = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) },
                        dismissButton = { ActaButton("Cancelar", onClick = { confirmar = false }, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
                    )
                }

                if (acceso.admin) PrefGroup("Resumen de billeteros") {
                    Text(
                        "Una planilla (CSV) con cada billetero: su serie, la máquina (MDA, modelo y juego de la Bitácora), su Software, bill-set, interfaz y aceptación. Sirve para ver qué funciona en cada máquina.",
                        style = ActaType.Label, modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ActaButton("Guardar resumen (CSV)", onClick = onExportResumen, height = 46.dp, modifier = Modifier.fillMaxWidth())
                }

                if (acceso.admin) PrefGroup("Respaldo") {
                    var importar by remember { mutableStateOf(false) }
                    Text("Exportar guarda un archivo con los datos de BILLMON. Importar los reemplaza por los del archivo.", style = ActaType.Label, modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActaButton("Exportar", onClick = onExport, height = 46.dp, modifier = Modifier.weight(1f))
                        ActaButton("Importar", onClick = { importar = true }, height = 46.dp, modifier = Modifier.weight(1f))
                    }
                    if (importar) AlertDialog(
                        onDismissRequest = { importar = false },
                        title = { Text("¿Importar un respaldo?") },
                        text = { Text("Los datos actuales de este teléfono se reemplazan por los del archivo que elijas.") },
                        confirmButton = { ActaButton("Elegir archivo", onClick = { importar = false; onImport() }, danger = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) },
                        dismissButton = { ActaButton("Cancelar", onClick = { importar = false }, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
                    )
                }

                if (acceso.admin) PrefGroup("Tramas USB (para análisis)") {
                    val grabando by vm.trazaActiva.collectAsStateWithLifecycle()
                    var borrarT by remember { mutableStateOf(false) }
                    var rev by remember { mutableStateOf(0) }
                    val resumen = remember(rev, grabando) { vm.trazaResumen() }
                    Text(
                        "Se guardan solas mientras la app está abierta (una sesión por billetero) y todos los teléfonos las suben a la nube. $resumen. " +
                            "Se borran las de más de 45 días o pasados 40 MB. «Guardar todo» crea un ZIP de este teléfono; «Bajar de la nube» el de todos, con una carpeta por serie.",
                        style = ActaType.Label, modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActaButton(if (grabando) "Pausar" else "Reanudar", onClick = { vm.activarTraza(!grabando) }, height = 46.dp, modifier = Modifier.weight(1f))
                        ActaButton("Guardar todo", onClick = onExportTraza, filled = true, height = 46.dp, modifier = Modifier.weight(1f))
                        ActaButton("Borrar", onClick = { borrarT = true }, danger = true, height = 46.dp, modifier = Modifier.weight(1f))
                    }
                    val estadoNube by vm.trazaNube.collectAsStateWithLifecycle()
                    ActaButton("Bajar de la nube (todos los teléfonos)", onClick = onExportTrazaNube, height = 46.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    if (estadoNube.isNotEmpty()) Text(estadoNube, style = ActaType.Label, modifier = Modifier.padding(top = 6.dp))
                    if (borrarT) AlertDialog(
                        onDismissRequest = { borrarT = false },
                        title = { Text("¿Borrar las tramas?") },
                        text = { Text("Se borran todas las sesiones guardadas en este teléfono. Si no las guardaste como ZIP, se pierden.") },
                        confirmButton = { ActaButton("Borrar", onClick = { borrarT = false; vm.limpiarTraza(); rev++ }, danger = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) },
                        dismissButton = { ActaButton("Cancelar", onClick = { borrarT = false }, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
            Hairline(color = Acta.Ink)
            ActaButton("Volver", onClick = onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp))
        }
    }
}

@Composable
internal fun Seccion(title: String) {
    Text(title.uppercase(), style = ActaType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), modifier = Modifier.padding(top = 30.dp))
}

@Composable
internal fun PrefGroup(title: String, content: @Composable () -> Unit) {
    if (Acta.premium) {
        cl.reabill.monitor.ui.components.GlassCard(Modifier.padding(top = 16.dp).fillMaxWidth()) {
            Text(title, style = ActaType.Section, modifier = Modifier.padding(bottom = 10.dp))
            content()
        }
        return
    }
    Column(Modifier.padding(top = 18.dp)) {
        Hairline(color = Acta.Ink)
        Text(title, style = ActaType.Section, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        content()
    }
}
