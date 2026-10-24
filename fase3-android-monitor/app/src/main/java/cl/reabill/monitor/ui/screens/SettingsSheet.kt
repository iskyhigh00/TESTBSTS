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

@Composable
internal fun SettingsSheet(
    visible: Boolean, vm: MonitorViewModel, log: List<String>, connected: Boolean, kind: String,
    onClose: () -> Unit, onLabelConfig: () -> Unit, onChangelog: () -> Unit,
    onCopy: () -> Unit, onCopyAll: () -> Unit, onExport: () -> Unit, onImport: () -> Unit,
    onImportWeekly: () -> Unit, onTestOcr: () -> Unit
) {
    BackHandler(enabled = visible, onBack = onClose)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)) + slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 14 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 14 }
    ) {
        val demo by vm.demo.collectAsStateWithLifecycle()
        val prefs by vm.labelPrefs.collectAsStateWithLifecycle()
        val busy by vm.busy.collectAsStateWithLifecycle()
        Column(Modifier.fillMaxSize().background(Acta.Paper)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
                Text("Preferencias", style = ActaType.Title.copy(fontSize = 34.sp), modifier = Modifier.padding(top = 18.dp))
                Text("BILLMON v$APP_VERSION", style = ActaType.Tiny, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

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

                PrefGroup("Datos compartidos") {
                    val n by vm.nubeUi.collectAsStateWithLifecycle()
                    var dlg by remember { mutableStateOf(false) }
                    var correo by remember { mutableStateOf("") }
                    var clave by remember { mutableStateOf("") }
                    when {
                        !n.disponible -> Text("Esta versión no trae la conexión a la nube.", style = ActaType.Body.copy(color = Acta.Ink2))
                        n.email.isEmpty() -> {
                            Text("Sin cuenta conectada.", style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(bottom = 8.dp))
                            if (n.mensaje.isNotEmpty()) Text(n.mensaje, style = ActaType.Label, modifier = Modifier.padding(bottom = 8.dp))
                            ActaButton("Conectar cuenta", onClick = { correo = ""; clave = ""; dlg = true }, height = 46.dp, modifier = Modifier.fillMaxWidth())
                        }
                        else -> {
                            Text(n.email, style = ActaType.Cell)
                            val cuando = if (n.ultima > 0) "Última sincronización: " +
                                java.text.SimpleDateFormat("dd-MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(n.ultima)) else "Aún no sincroniza."
                            Text(cuando, style = ActaType.Label, modifier = Modifier.padding(top = 4.dp))
                            if (n.mensaje.isNotEmpty()) Text(n.mensaje, style = ActaType.Label, modifier = Modifier.padding(top = 2.dp))
                            ActaButton(
                                if (n.trabajando) "Sincronizando…" else "Sincronizar ahora", onClick = { vm.sincronizarAhora() },
                                enabled = !n.trabajando, height = 46.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActaButton("Cambiar cuenta", onClick = { correo = n.email; clave = ""; dlg = true }, height = 46.dp, modifier = Modifier.weight(1f))
                                ActaButton("Desconectar", onClick = { vm.nubeDesconectar() }, danger = true, height = 46.dp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (dlg) AlertDialog(
                        onDismissRequest = { dlg = false },
                        title = { Text("Cuenta compartida") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = correo, onValueChange = { correo = it.take(120) }, singleLine = true, label = { Text("Correo") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = clave, onValueChange = { clave = it.take(120) }, singleLine = true, label = { Text("Clave") },
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                            }
                        },
                        confirmButton = {
                            ActaButton(
                                "Conectar", onClick = { dlg = false; vm.nubeConectar(correo, clave) }, filled = true,
                                enabled = correo.isNotBlank() && clave.isNotEmpty(), height = 44.dp, modifier = Modifier.width(110.dp)
                            )
                        },
                        dismissButton = { ActaButton("Cancelar", onClick = { dlg = false }, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
                    )
                }

                PrefGroup("Reporte semanal del sistema") {
                    val w by vm.weekly.collectAsStateWithLifecycle()
                    Text(
                        w?.let { "Semana ${it.fecha.ifEmpty { "sin fecha" }} · ${it.datos.size} máquinas" }
                            ?: "Sin reporte importado",
                        style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ActaButton("Importar reporte semanal", onClick = onImportWeekly, height = 46.dp, modifier = Modifier.fillMaxWidth())
                }

                PrefGroup("Impresora de etiquetas") {
                    Text(
                        if (prefs.lastName.isNotEmpty()) "Guardada: ${prefs.lastName}" else "Sin impresora guardada",
                        style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ActaButton("Configurar impresora y etiqueta", onClick = onLabelConfig, height = 46.dp, modifier = Modifier.fillMaxWidth())
                }

                PrefGroup("Lectura del N° de máquina") {
                    ActaButton("Probar con una imagen", onClick = onTestOcr, height = 46.dp, modifier = Modifier.fillMaxWidth())
                }

                PrefGroup("Modo demo") {
                    if (demo) ActaButton("Salir del demo", onClick = { vm.stopDemo() }, enabled = !busy, height = 46.dp, modifier = Modifier.fillMaxWidth())
                    else ActaButton(
                        "Ver billetero demo", onClick = { vm.startDemo(); onClose() },
                        enabled = !connected && !busy, height = 46.dp, modifier = Modifier.fillMaxWidth()
                    )
                }

                PrefGroup("Respaldo") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActaButton("Exportar", onClick = onExport, height = 46.dp, modifier = Modifier.weight(1f))
                        ActaButton("Importar", onClick = onImport, height = 46.dp, modifier = Modifier.weight(1f))
                    }
                }

                PrefGroup("Registro") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActaButton("Copiar", onClick = onCopy, height = 42.dp, textSize = 13.sp, modifier = Modifier.weight(1f))
                        ActaButton("Copiar todo", onClick = onCopyAll, height = 42.dp, textSize = 13.sp, modifier = Modifier.weight(1f))
                        ActaButton("Limpiar", onClick = { vm.clearLog() }, danger = true, height = 42.dp, textSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                    SelectionContainer {
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            log.take(30).forEach { line ->
                                Hairline()
                                val (time, msg) = splitLog(line)
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Text(time, style = ActaType.Tiny.copy(fontSize = 11.sp), modifier = Modifier.width(62.dp))
                                    Text(msg, style = ActaType.Log.copy(fontSize = 12.5.sp), modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
            Hairline(color = Acta.Ink)
            ActaButton("Volver", onClick = onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp))
        }
    }
}

@Composable
internal fun PrefGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Hairline(color = Acta.Ink)
        Text(title, style = ActaType.Section, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        content()
    }
}
