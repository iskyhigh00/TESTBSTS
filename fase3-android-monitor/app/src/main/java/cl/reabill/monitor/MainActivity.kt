package cl.reabill.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.reabill.monitor.ui.screens.ActaApp
import cl.reabill.monitor.ui.theme.Acta

const val APP_VERSION = "2.1.1"

class MainActivity : ComponentActivity() {
    private val vm: MonitorViewModel by viewModels()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {

            @Suppress("DEPRECATION")
            val d = i.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
            val que = if (i.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) "enchufado" else "desenchufado"
            vm.logUsbEvent("USB $que: " + (d?.let { "VID=%04X PID=%04X".format(it.vendorId, it.productId) } ?: "?"))
            if (i.action == UsbManager.ACTION_USB_DEVICE_DETACHED) vm.onUsbDetached()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {

            val oscuro = isSystemInDarkTheme()
            Acta.dark = oscuro
            cl.reabill.monitor.ui.theme.ActaMaterial {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ActaApp(vm)
                }
            }
        }

        if (savedInstanceState == null) handleUsbIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(i: Intent?) {
        if (i?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) vm.onUsbAttached()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(usbReceiver, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        }, Context.RECEIVER_NOT_EXPORTED)

        vm.refreshConnection()
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) {}
    }
}

@Composable
fun MonitorScreen(vm: MonitorViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val accounting by vm.accounting.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val updateVersion by vm.updateVersion.collectAsStateWithLifecycle()
    val updateProgress by vm.updateProgress.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var showCalib by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Borrar contabilidad") },
            text = { Text("Se guardará el estado actual en el historial y luego se " +
                    "pondrán los contadores en 0. ¿Continuar?") },
            confirmButton = {
                Button(onClick = { showReset = false; vm.resetAccounting() }) { Text("Borrar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReset = false }) { Text("Cancelar") }
            }
        )
    }

    if (showCalib) {
        AlertDialog(
            onDismissRequest = { showCalib = false },
            title = { Text("Iniciar modo calibración") },
            text = { Text("El equipo entrará en modo calibración. Ten listo el cupón de " +
                    "calibración para pasarlo. No borra la contabilidad. ¿Continuar?") },
            confirmButton = {
                Button(onClick = { showCalib = false; vm.startCalibration() }) { Text("Iniciar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCalib = false }) { Text("Cancelar") }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Cashflow Monitor", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Validadores Cashflow SC por USB-OTG  ·  v$APP_VERSION",
            fontSize = 13.sp, color = Color(0xFF9AA0A6))
        if (busy) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth(),
                color = Color(0xFFB69CFF),
                trackColor = Color(0x33B69CFF))
        }

        updateVersion?.let { newV ->
            ElevatedCard(Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = Color(0xFF26314A))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Actualización disponible", fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF9FC1FF))
                    Spacer(Modifier.height(4.dp))
                    Text("Versión v$newV (tienes v$APP_VERSION).",
                        fontSize = 13.sp, color = Color(0xFFD7E1F5))
                    Spacer(Modifier.height(10.dp))
                    if (updateProgress == -2) {
                        Button(onClick = { vm.installUpdate() }) {
                            Text("Actualizar a v$newV")
                        }
                    } else {
                        if (updateProgress >= 0) {
                            LinearProgressIndicator(
                                progress = { updateProgress / 100f },
                                modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text("Descargando… $updateProgress%", fontSize = 12.sp,
                                color = Color(0xFFD7E1F5))
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text("Descargando…", fontSize = 12.sp, color = Color(0xFFD7E1F5))
                        }
                    }
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Estado del equipo", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(status.state, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = stateColor(status.state))
                Spacer(Modifier.height(12.dp))

                Text("Variante (Bill-set)", fontSize = 12.sp, color = Color(0xFF9AA0A6))
                val billSetLoading = busy && (status.billSet == "—" || status.billSet.isBlank())
                if (billSetLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp), strokeWidth = 2.5.dp,
                            color = Color(0xFFB69CFF))
                        Spacer(Modifier.width(10.dp))
                        Text("Cargando…", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFFB69CFF))
                    }
                } else {
                    Text(status.billSet, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = Color(0xFFB69CFF))
                }
                Spacer(Modifier.height(12.dp))

                InfoRow("Modelo", status.model)
                InfoRow("Serie", status.serial)
                InfoRow("Fabricante", status.manufacturer)
                InfoRow("Firmware app", status.firmwareApp)
            }
        }

        if (!connected) {
            Button(onClick = { vm.connect() }) { Text("Conectar") }
        }
        TextButton(onClick = { vm.checkForUpdate() }) { Text("Buscar actualización") }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Contabilidad", fontWeight = FontWeight.SemiBold)
                    Button(onClick = { vm.readAccounting() }, enabled = connected) {
                        Text("Leer")
                    }
                }
                Spacer(Modifier.height(8.dp))
                val acc = accounting
                if (busy && acc == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Cargando contabilidad…", fontSize = 12.sp,
                            color = Color(0xFF9AA0A6))
                    }
                } else if (acc == null) {
                    Text("Conecta el equipo y pulsa Leer.",
                        fontSize = 12.sp, color = Color(0xFF9AA0A6))
                } else {
                    InfoRow("Insertados", acc.inserted.toString())
                    InfoRow("Aceptados", acc.accepted.toString())
                    InfoRow("Rechazados", acc.rejected.toString())
                    InfoRow("Tasa aceptación", "%.1f%%".format(acc.rate))
                    InfoRow("Apilados", acc.stacked.toString())
                    InfoRow("Alcanzan depósito", acc.deposited.toString())
                    InfoRow("Barcodes acep/rech", "${acc.bcAccepted} / ${acc.bcRejected}")
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { showReset = true }, enabled = connected) {
                        Text("Borrar contabilidad")
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Historial (${status.serial})", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    history.take(10).forEach { h ->
                        val d = java.text.SimpleDateFormat("dd-MM HH:mm",
                            java.util.Locale.getDefault()).format(java.util.Date(h.timestamp))
                        Text("$d  ·  acep ${h.accepted}  rech ${h.rejected}  tasa ${"%.1f".format(h.rate)}%",
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Calibración", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Inicia el modo calibración. Requiere el cupón. No borra la contabilidad.",
                    fontSize = 12.sp, color = Color(0xFF9AA0A6))
                Spacer(Modifier.height(10.dp))
                Button(onClick = { showCalib = true }, enabled = connected) {
                    Text("Iniciar modo calibración")
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Registro", fontWeight = FontWeight.SemiBold)
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(log.joinToString("\n")))
                    }) { Text("Copiar") }
                }
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Column(Modifier.fillMaxWidth()) {
                        log.forEach { line ->
                            Text(line, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color(0xFF9AA0A6))
        Text(value, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun stateColor(state: String): Color {
    val s = state.lowercase()
    return when {
        s.contains("desconect") -> Color(0xFF9A9A9A)
        s.contains("idle") || s.contains("conectado") -> Color(0xFF4CC27A)
        s.contains("acept") || s.contains("apil") -> Color(0xFF4C9AFF)
        s.contains("escrow") -> Color(0xFFF2B03C)
        s.contains("rechaz") || s.contains("jam") || s.contains("error") -> Color(0xFFE25A5A)
        else -> Color(0xFF9A9A9A)
    }
}
