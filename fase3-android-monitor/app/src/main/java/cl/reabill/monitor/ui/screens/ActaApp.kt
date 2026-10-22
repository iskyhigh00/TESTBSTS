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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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

private val Gutter = 18.dp

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ActaApp(vm: MonitorViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val accounting by vm.accounting.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val updateVersion by vm.updateVersion.collectAsStateWithLifecycle()
    val updateProgress by vm.updateProgress.collectAsStateWithLifecycle()

    val phase = derivePhase(connected, busy, status, log)
    val clipboard = LocalClipboardManager.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }
    val weeklyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importWeekly(uri)
    }
    val weekly by vm.weekly.collectAsStateWithLifecycle()
    val mdaMap by vm.mdaMap.collectAsStateWithLifecycle()
    var showMda by remember { mutableStateOf(false) }
    var mdaTexto by remember { mutableStateOf("") }
    val ocr by vm.ocr.collectAsStateWithLifecycle()
    val testPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.recognizeMda(uri, test = true)
    }
    var fotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = fotoUri
        if (ok && u != null) vm.recognizeMda(u) else vm.clearOcr()
    }

    LaunchedEffect(ocr.id) {
        if (ocr.id > 0 && !ocr.running && !ocr.test) {
            if (ocr.candidates.isNotEmpty()) mdaTexto = ocr.candidates.first()
            showMda = true
        }
    }

    val view = LocalView.current
    val oscuro = Acta.dark
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !oscuro
            isAppearanceLightNavigationBars = !oscuro
        }
    }

    val kind by vm.kind.collectAsStateWithLifecycle()
    var showRestart by remember { mutableStateOf(false) }
    var showLabel by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val printUi by vm.print.collectAsStateWithLifecycle()
    val labelPrefs by vm.labelPrefs.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.labelPrint() else vm.labelNote("Sin permiso de Bluetooth: no se puede imprimir.")
    }
    val printNow: () -> Unit = {
        val sinPermiso = Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (labelPrefs.lastAddress.isEmpty()) {
            showLabel = true
        } else if (sinPermiso) {
            askBt.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            vm.labelPrint()
        }
    }
    var showHistory by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showCal by remember { mutableStateOf(false) }
    var showFlash by remember { mutableStateOf(false) }
    var calMarker by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connected) { if (!connected) showCal = false }

    if (ocr.test && ocr.id > 0 && !ocr.running) {
        AlertDialog(
            onDismissRequest = { vm.clearOcr() },
            title = { Text("Prueba de lectura") },
            text = {
                Column {
                    Text(
                        if (ocr.candidates.isNotEmpty()) "Número leído: " + ocr.candidates.joinToString("  ·  ") else ocr.message,
                        style = ActaType.Body
                    )
                    if (ocr.raw.isNotEmpty()) Text("Texto visto: ${ocr.raw}", style = ActaType.Label, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { ActaButton("Cerrar", onClick = { vm.clearOcr() }, filled = true, height = 44.dp, modifier = Modifier.width(110.dp)) }
        )
    }

    if (showMda) {
        AlertDialog(
            onDismissRequest = { showMda = false; vm.clearOcr() },
            title = { Text("N° de máquina") },
            text = {
                Column {
                    if (ocr.message.isNotEmpty()) Text(ocr.message, style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(bottom = 8.dp))
                    if (ocr.candidates.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ocr.candidates.forEach { c -> cl.reabill.monitor.ui.components.ActaChip(c, mdaTexto == c, { mdaTexto = c }) }
                        }
                    }
                    OutlinedTextField(
                        value = mdaTexto, onValueChange = { mdaTexto = it.filter { c -> c.isDigit() }.take(12) },
                        singleLine = true, label = { Text("MDA") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { ActaButton("Guardar", onClick = { vm.setMda(mdaTexto); showMda = false; vm.clearOcr() }, filled = true, height = 44.dp, modifier = Modifier.width(110.dp)) },
            dismissButton = { ActaButton("Cancelar", onClick = { showMda = false; vm.clearOcr() }, height = 44.dp, modifier = Modifier.width(110.dp)) }
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Borrar contabilidad") },
            text = {
                Text("Se guarda el estado actual y los contadores quedan en 0.")
            },
            confirmButton = {
                TextButton(onClick = { showReset = false; vm.resetAccounting() }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancelar") } }
        )
    }

    if (showRestart) {
        AlertDialog(
            onDismissRequest = { showRestart = false },
            title = { Text("Reiniciar equipo (experimental)") },
            text = {
                Text("Experimental, sin probar. ¿Reiniciar el equipo?")
            },
            confirmButton = {
                TextButton(onClick = { showRestart = false; vm.restartAdvance() }) { Text("Reiniciar") }
            },
            dismissButton = { TextButton(onClick = { showRestart = false }) { Text("Cancelar") } }
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Historial · ${status.serial} (${history.size})") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    history.forEach { h ->
                        val d = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(h.timestamp))
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(d, style = ActaType.Log.copy(color = Acta.Ink2))
                            Text(
                                "aceptados ${fmtInt(h.accepted)} · rechazados ${fmtInt(h.rejected)} · " +
                                    "insertados ${fmtInt(h.inserted)} · apilados ${fmtInt(h.stacked)} · " +
                                    "depósito ${fmtInt(h.deposited)} · tasa ${fmtRate(h.rate)} %",
                                style = ActaType.Log
                            )
                        }
                        Hairline()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Cerrar") } }
        )
    }

    Box(Modifier.fillMaxSize().background(Acta.Paper).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                TopRow(phase, onSettings = { showSettings = true })
                ActivityRule(
                    busy = phase.isBusy, fault = phase == Phase.NoResponse,
                    modifier = Modifier.padding(horizontal = Gutter)
                )

                updateVersion?.let { v ->
                    UpdateStrip(v, updateProgress, onInstall = { vm.installUpdate() })
                }

                Hero(phase, accounting)
                IdentityBlock(phase, status.billSet, status.serial, status.model, status.manufacturer, status.firmwareApp)

                AccountingSection(
                    acc = accounting, history = history, phase = phase, connected = connected,
                    onRead = { vm.readAccounting() }, onReset = { showReset = true }
                )

                if (connected) MachineSection(
                    mda = mdaMap[status.serial], weekly = weekly,
                    rate = cl.reabill.monitor.LabelPick.reading(accounting, history)?.first?.rate,
                    leyendo = ocr.running,
                    onEdit = { mdaTexto = mdaMap[status.serial] ?: ""; vm.clearOcr(); showMda = true },
                    onPhoto = {
                        val dir = java.io.File(ctx.cacheDir, "fotos").apply { mkdirs() }
                        val f = java.io.File(dir, "mda.jpg").apply { delete(); createNewFile() }
                        val u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                        fotoUri = u
                        takePhoto.launch(u)
                    }
                )

                if (history.isNotEmpty()) HistorySection(history, status.serial, onShowAll = { showHistory = true })

                Column(Modifier.padding(horizontal = Gutter).padding(top = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActaButton(
                            if (printUi.printing) "Imprimiendo…" else "Imprimir etiqueta",
                            onClick = printNow, filled = true,
                            enabled = accounting != null && !printUi.printing, modifier = Modifier.weight(1f)
                        )
                        ActaButton(
                            "Cargar bill-set", onClick = { showFlash = true },
                            enabled = connected && !busy, modifier = Modifier.weight(1f)
                        )
                    }
                    if (printUi.message.isNotEmpty()) {
                        Text(printUi.message, style = ActaType.Label, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            BottomBar(
                connected = connected, enabled = !busy,
                onConnect = { vm.connect() },
                onCalibrate = {
                    calMarker = log.firstOrNull()
                    showCal = true
                    vm.startCalibration()
                }
            )
        }

        if (showLabel) LabelSheet(vm, onClose = { showLabel = false })
        SettingsSheet(
            visible = showSettings, vm = vm, log = log, connected = connected, kind = kind,
            onClose = { showSettings = false },
            onLabelConfig = { showLabel = true },
            onRestart = { showSettings = false; showRestart = true },
            onCopy = { clipboard.setText(AnnotatedString(vm.sessionLog().ifEmpty { log.take(30).joinToString("\n") })) },
            onCopyAll = { vm.fullLogAsync { t -> clipboard.setText(AnnotatedString(t.ifEmpty { log.joinToString("\n") })) } },
            onExport = { exportLauncher.launch("billmon-respaldo-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".json") },
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onImportWeekly = { weeklyLauncher.launch(arrayOf("*/*")) },
            onTestOcr = { testPicker.launch(arrayOf("image/*")) }
        )
        FlashSheet(
            visible = showFlash, vm = vm, serial = status.serial, billSet = status.billSet,
            onClose = { showFlash = false }
        )
        CalibrationSheet(
            visible = showCal, serial = status.serial, billSet = status.billSet,
            log = log, marker = calMarker, onClose = { showCal = false }
        )
    }
}

@Composable
private fun TopRow(phase: Phase, onSettings: () -> Unit) {
    val kind = when (phase) {
        Phase.Disconnected -> MarkKind.Off
        Phase.Ready -> MarkKind.Ok
        Phase.NoResponse -> MarkKind.Error
        else -> MarkKind.Run
    }
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = 6.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("BILLMON", style = ActaType.Section.copy(fontSize = 12.sp))
        Spacer(Modifier.width(8.dp))
        Text("v$APP_VERSION", style = ActaType.Tiny.copy(fontSize = 11.sp))
        Spacer(Modifier.weight(1f))
        StatusMark(kind)
        Spacer(Modifier.width(6.dp))
        Text(
            phase.label(),
            style = ActaType.Section.copy(
                fontSize = 12.sp, color = if (phase == Phase.NoResponse) Acta.Error else Acta.Ink
            )
        )
        Spacer(Modifier.width(2.dp))
        GearButton(onSettings)
    }
}

@Composable
private fun UpdateStrip(version: String, progress: Int, onInstall: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp).border(1.dp, Acta.Ink).padding(12.dp)) {
        Text("Actualización disponible", style = ActaType.Section)
        Text("Versión v$version (tienes v$APP_VERSION).", style = ActaType.Label)
        Spacer(Modifier.height(8.dp))
        if (progress == -2) {
            ActaButton("Actualizar a v$version", onClick = onInstall, filled = true, modifier = Modifier.fillMaxWidth())
        } else {
            val frac = if (progress >= 0) progress / 100f else null
            Box(Modifier.fillMaxWidth().height(4.dp).background(Acta.Hairline)) {
                if (frac != null) Box(Modifier.fillMaxWidth(frac).height(4.dp).background(Acta.Accent))
            }
            Spacer(Modifier.height(4.dp))
            Text(if (progress >= 0) "Descargando… $progress %" else "Descargando…", style = ActaType.Label)
        }
    }
}

@Composable
private fun Hero(phase: Phase, acc: AccountingData?) {

    val shown = remember { Animatable(Acta.RATE_MIN.toFloat()) }
    LaunchedEffect(acc?.timestamp) {
        val a = acc
        if (a == null) shown.snapTo(Acta.RATE_MIN.toFloat())
        else shown.animateTo(a.rate.toFloat(), tween(800, easing = FastOutSlowInEasing))
    }
    val fault = phase == Phase.NoResponse
    val number = if (acc == null) "—" else "${fmtRate(shown.value.toDouble())} %"
    val sentence = when {
        acc != null && acc.inserted == 0 -> "No hay billetes insertados en la contabilidad."
        acc != null -> "${fmtInt(acc.accepted)} de ${fmtInt(acc.inserted)} billetes insertados fueron aceptados."
        phase == Phase.Disconnected -> "Sin billetero conectado."
        phase == Phase.ReadingAccounting -> "Leyendo la contabilidad del equipo…"
        phase.isBusy -> "Identificando el equipo…"
        fault -> "El equipo no respondió."
        else -> "Pulsa Leer para obtener la contabilidad."
    }
    Column(Modifier.padding(horizontal = Gutter).padding(top = 10.dp, bottom = 14.dp)) {
        Text("Tasa de aceptación", style = ActaType.Label)
        Text(
            number, maxLines = 1, softWrap = false,
            style = ActaType.Hero.copy(
                color = when {
                    acc != null -> Acta.Ink
                    fault -> Acta.Error
                    else -> Acta.Hairline
                }
            )
        )
        Text(
            sentence, style = ActaType.Body.copy(color = if (fault) Acta.Error else Acta.Ink),
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.9f)
        )
        RateScale(acc?.rate, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun IdentityBlock(
    phase: Phase, billSet: String, serial: String, model: String, manufacturer: String, firmware: String
) {
    Hairline(Modifier.padding(horizontal = Gutter), Acta.Ink)
    Row(Modifier.padding(horizontal = Gutter).height(IntrinsicSize.Min)) {
        Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 10.dp)) {
            Text("Bill-set", style = ActaType.Label.copy(fontSize = 11.sp))
            if (billSet.isPlaceholder() && phase.isBusy) PendingText("——", ActaType.BillSet)
            else ResolveText(billSet, ActaType.BillSet)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Acta.Hairline))
        Column(Modifier.weight(1f).padding(start = 14.dp, top = 8.dp, bottom = 10.dp)) {
            Text("Serie", style = ActaType.Label.copy(fontSize = 11.sp))
            Box(Modifier.padding(top = 5.dp)) { ResolveText(serial, ActaType.Serial) }
        }
    }
    Hairline(Modifier.padding(horizontal = Gutter))
    Row(Modifier.padding(horizontal = Gutter).height(IntrinsicSize.Min)) {
        MiniField("Modelo", model, Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().background(Acta.Hairline))
        MiniField("Fabricante", manufacturer, Modifier.weight(1f).padding(start = 10.dp))
        Box(Modifier.width(1.dp).fillMaxHeight().background(Acta.Hairline))
        MiniField("Firmware", firmware, Modifier.weight(1f).padding(start = 10.dp))
    }
    Hairline(Modifier.padding(horizontal = Gutter))
}

@Composable
private fun MiniField(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 7.dp)) {
        Text(label, style = ActaType.Label.copy(fontSize = 10.5.sp))
        ResolveText(value, ActaType.Value)
    }
}

@Composable
private fun PendingText(text: String, style: TextStyle) {
    val a by rememberInfiniteTransition(label = "pendiente").animateFloat(
        1f, 0.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a"
    )
    Text(text, style = style.copy(color = Acta.Hairline), modifier = Modifier.alpha(a))
}

@Composable
private fun AccountingSection(
    acc: AccountingData?, history: List<AccountingData>, phase: Phase, connected: Boolean,
    onRead: () -> Unit, onReset: () -> Unit
) {
    val prev = acc?.let { previousSnapshot(it, history) }
    SectionHeader("Contabilidad") {
        ActaButton(
            if (phase == Phase.ReadingAccounting) "Leyendo…" else "Leer",
            onClick = onRead, enabled = connected && !phase.isBusy,
            height = 36.dp, modifier = Modifier.padding(vertical = 5.dp).width(96.dp)
        )
    }
    Column(Modifier.padding(horizontal = Gutter)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            HeadCell("Concepto", Modifier.weight(1.5f), TextAlign.Start)
            HeadCell("Actual", Modifier.weight(1f))
            HeadCell("Anterior", Modifier.weight(1f))
        }
        if (acc == null) {
            Hairline()
            Text(
                if (connected) "Sin lectura." else "Sin billetero.",
                style = ActaType.Label, modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            LedgerRow("Aceptados", acc.accepted, prev?.accepted)
            LedgerRow("Rechazados", acc.rejected, prev?.rejected)
            RateRow(acc.rate, prev?.rate)
            PlainRow("Insertados", fmtInt(acc.inserted))
            PlainRow("Apilados", fmtInt(acc.stacked))
            PlainRow("Alcanzan depósito", fmtInt(acc.deposited))
            if (prev != null) {
                val d = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(prev.timestamp))
                Text("Anterior: lectura del $d", style = ActaType.Label, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(10.dp))
            ActaButton(
                "Borrar contabilidad", onClick = onReset, danger = true, height = 44.dp,
                enabled = connected && !phase.isBusy, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit = {}) {
    Column(Modifier.padding(horizontal = Gutter).padding(top = 14.dp)) {
        Hairline(color = Acta.Ink)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = ActaType.Section, modifier = Modifier.padding(vertical = 7.dp))
            Spacer(Modifier.weight(1f))
            action()
        }
    }
}

@Composable
private fun HeadCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.End) {
    Text(text, style = ActaType.Label.copy(fontSize = 10.5.sp), textAlign = align, modifier = modifier)
}

@Composable
private fun LedgerRow(label: String, value: Int, previous: Int?) {
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ActaType.Body, modifier = Modifier.weight(1.5f))
        CountText(value, previous, ActaType.Cell, Modifier.weight(1f), TextAlign.End)
        Text(
            previous?.let { fmtInt(it) } ?: "—",
            style = ActaType.Cell.copy(color = Acta.Ink2), textAlign = TextAlign.End, modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RateRow(current: Double, previous: Double?) {
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Tasa de aceptación", style = ActaType.Body, modifier = Modifier.weight(1.5f))
        Text("${fmtRate(current)} %", style = ActaType.Cell, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(
            previous?.let { "${fmtRate(it)} %" } ?: "—",
            style = ActaType.Cell.copy(color = Acta.Ink2), textAlign = TextAlign.End, modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlainRow(label: String, value: String) {
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ActaType.Body, modifier = Modifier.weight(1.5f))
        Text(value, style = ActaType.Cell, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MachineSection(
    mda: String?, weekly: WeeklyData?, rate: Double?, leyendo: Boolean, onEdit: () -> Unit, onPhoto: () -> Unit
) {
    SectionHeader("Máquina") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActaButton(
                if (leyendo) "Leyendo…" else "Foto", onClick = onPhoto, enabled = !leyendo,
                height = 36.dp, modifier = Modifier.padding(vertical = 5.dp).width(90.dp)
            )
            ActaButton(
                if (mda == null) "Escribir" else "Editar", onClick = onEdit,
                height = 36.dp, modifier = Modifier.padding(vertical = 5.dp).width(90.dp)
            )
        }
    }
    Column(Modifier.padding(horizontal = Gutter)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("N° de máquina (MDA)", style = ActaType.Body, modifier = Modifier.weight(1f))
            Text(mda ?: "—", style = ActaType.Cell)
        }
        if (mda != null && weekly != null) {
            val sistema = weekly.pct(mda)
            Hairline()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sistema (semana ${weekly.fecha.ifEmpty { "sin fecha" }})", style = ActaType.Body, modifier = Modifier.weight(1f))
                Text(if (sistema == null) "—" else "${fmtRate(sistema * 100.0)} %", style = ActaType.Cell)
            }
            if (rate != null) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Billetero (contadores)", style = ActaType.Body, modifier = Modifier.weight(1f))
                    Text("${fmtRate(rate)} %", style = ActaType.Cell)
                }
            }
        }
    }
}

@Composable
private fun HistorySection(history: List<AccountingData>, serial: String, onShowAll: () -> Unit) {
    SectionHeader("Historial · $serial")
    Column(Modifier.padding(horizontal = Gutter)) {
        history.take(3).forEach { h ->
            Hairline()
            val d = java.text.SimpleDateFormat("dd-MM HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(h.timestamp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(d, style = ActaType.Log.copy(color = Acta.Ink2), modifier = Modifier.width(92.dp))
                Text(
                    "acep ${h.accepted}  rech ${h.rejected}  tasa ${fmtRate(h.rate)} %",
                    style = ActaType.Log
                )
            }
        }
        Hairline()
        if (history.size > 3) {
            ActaButton(
                "Ver historial completo (${history.size})", onClick = onShowAll,
                height = 40.dp, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun SettingsSheet(
    visible: Boolean, vm: MonitorViewModel, log: List<String>, connected: Boolean, kind: String,
    onClose: () -> Unit, onLabelConfig: () -> Unit, onRestart: () -> Unit,
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

                PrefGroup("Impresora de etiquetas") {
                    Text(
                        if (prefs.lastName.isNotEmpty()) "Guardada: ${prefs.lastName}" else "Sin impresora guardada",
                        style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ActaButton("Configurar impresora y etiqueta", onClick = onLabelConfig, height = 46.dp, modifier = Modifier.fillMaxWidth())
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
                            Text(if (prog >= 0) "Descargando… $prog %" else "Descargando…", style = ActaType.Label, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                    ActaButton("Buscar actualización", onClick = { vm.checkForUpdate() }, height = 46.dp, modifier = Modifier.fillMaxWidth())
                }

                PrefGroup("Respaldo") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActaButton("Exportar", onClick = onExport, height = 46.dp, modifier = Modifier.weight(1f))
                        ActaButton("Importar", onClick = onImport, height = 46.dp, modifier = Modifier.weight(1f))
                    }
                }

                PrefGroup("Registro") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActaButton("Copiar", onClick = onCopy, height = 42.dp, modifier = Modifier.weight(1f))
                        ActaButton("Copiar todo", onClick = onCopyAll, height = 42.dp, modifier = Modifier.weight(1f))
                        ActaButton("Limpiar", onClick = { vm.clearLog() }, danger = true, height = 42.dp, modifier = Modifier.weight(1f))
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

                if (connected && kind == FirmwareFile.KIND_ADVANCE) {
                    PrefGroup("Avanzado") {
                        ActaButton(
                            "Reiniciar equipo (experimental)", onClick = onRestart,
                            enabled = !busy, height = 46.dp, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Hairline(color = Acta.Ink)
            ActaButton("Volver", onClick = onClose, modifier = Modifier.fillMaxWidth().padding(14.dp))
        }
    }
}

@Composable
private fun PrefGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Hairline(color = Acta.Ink)
        Text(title, style = ActaType.Section, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        content()
    }
}

@Composable
private fun BottomBar(connected: Boolean, enabled: Boolean, onConnect: () -> Unit, onCalibrate: () -> Unit) {
    Column {
        Hairline(color = Acta.Ink)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!connected) {

                ActaButton("Conectar", onClick = onConnect, filled = true, modifier = Modifier.weight(1f))
            } else {
                HoldButton(
                    "Calibrar", "mantener presionado", onArmed = onCalibrate,
                    enabled = enabled, modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private enum class CalAck { Waiting, Accepted, NoAck }

@Composable
private fun CalibrationSheet(
    visible: Boolean, serial: String, billSet: String,
    log: List<String>, marker: String?, onClose: () -> Unit
) {
    BackHandler(enabled = visible, onBack = onClose)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)) + slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 14 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 14 }
    ) {

        val fresh = log.takeWhile { it !== marker }
        val ack = when {
            fresh.any { it.contains("Modo calibración iniciado") } -> CalAck.Accepted
            fresh.any { it.contains("Calibración: sin confirmación") } -> CalAck.NoAck
            else -> CalAck.Waiting
        }
        Column(Modifier.fillMaxSize().background(Acta.Paper).padding(Gutter)) {
            Text("Equipo $serial", style = ActaType.Label)
            Text("Calibración", style = ActaType.Title, modifier = Modifier.padding(top = 6.dp))
            Text(
                "Bill-set $billSet",
                style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )
            Hairline(color = Acta.Ink)
            CalStep("Comando enviado", "LISTO", Acta.Accent, pulse = false)
            when (ack) {
                CalAck.Waiting -> CalStep("Confirmación del equipo", "ESPERANDO", Acta.Ink, pulse = true)
                CalAck.Accepted -> CalStep("Confirmación del equipo", "ACEPTADO", Acta.Accent, pulse = false)
                CalAck.NoAck -> CalStep("Confirmación del equipo", "SIN CONFIRMACIÓN", Acta.Error, pulse = false)
            }
            CalStep("Pasar el cupón de calibración", "LO INDICA EL EQUIPO", Acta.Ink2, pulse = false)
            Spacer(Modifier.weight(1f))
            ActaButton("Volver", onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CalStep(text: String, state: String, stateColor: Color, pulse: Boolean) {
    val a = if (pulse) {
        val v by rememberInfiniteTransition(label = "espera").animateFloat(
            1f, 0.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a"
        )
        v
    } else 1f
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = ActaType.Body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
            Text(state, style = ActaType.Log.copy(color = stateColor), modifier = Modifier.alpha(a))
        }
        Hairline()
    }
}
