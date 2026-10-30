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

internal val Gutter: androidx.compose.ui.unit.Dp get() = if (Acta.premium) 20.dp else 18.dp

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
    var showBuscar by remember { mutableStateOf(false) }
    var buscarVieneDeLista by remember { mutableStateOf(false) }
    var showLista by remember { mutableStateOf(false) }
    var showCambios by remember { mutableStateOf(false) }
    var showIfaz by remember { mutableStateOf(false) }
    var ifazSel by remember { mutableStateOf<Int?>(null) }
    val ifaz by vm.ifaz.collectAsStateWithLifecycle()
    var filtroLista by remember { mutableStateOf("") }
    var soloRevisar by remember { mutableStateOf(false) }
    var ordenLista by remember { mutableStateOf(0) }
    val revPend by vm.revPendientes.collectAsStateWithLifecycle()
    var buscarTexto by remember { mutableStateOf("") }
    var revisando by remember { mutableStateOf<String?>(null) }
    var showEtq by remember { mutableStateOf(false) }
    val etqPend by vm.etqPendientes.collectAsStateWithLifecycle()
    var buscandoFoto by remember { mutableStateOf(false) }
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
            if (buscandoFoto) {
                buscandoFoto = false
                val c = ocr.candidates.firstOrNull()
                filtroLista = c ?: ""
                showLista = true
                if (c != null) { buscarTexto = c; showLista = false; buscarVieneDeLista = true; showBuscar = true }
            } else {
                if (ocr.candidates.isNotEmpty()) mdaTexto = ocr.candidates.first()
                showMda = true
            }
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
    var showLabel by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var pidiendoClave by remember { mutableStateOf(false) }
    var claveAjustes by remember { mutableStateOf("") }
    var claveMala by remember { mutableStateOf(false) }
    var motivoClave by remember { mutableStateOf("") }
    var comprobando by remember { mutableStateOf(false) }
    val printUi by vm.print.collectAsStateWithLifecycle()
    val labelPrefs by vm.labelPrefs.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val lanzarFoto = {
        buscandoFoto = false
        val dir = java.io.File(ctx.cacheDir, "fotos").apply { mkdirs() }
        val f = java.io.File(dir, "mda.jpg").apply { delete(); createNewFile() }
        val u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        fotoUri = u
        takePhoto.launch(u)
    }

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
        var elegido by remember(ocr.id) { mutableStateOf(ocr.candidates.firstOrNull() ?: "") }
        AlertDialog(
            onDismissRequest = { vm.clearOcr() },
            title = { Text("Prueba de lectura") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ocr.photo?.let { FotoConfirmacion(it, ocr.boxes, elegido) { c -> elegido = c } }
                    Text(
                        if (ocr.candidates.isNotEmpty()) "Número leído: " + ocr.candidates.joinToString("  ·  ") else ocr.message,
                        style = ActaType.Body
                    )
                    if (ocr.raw.isNotEmpty()) Text("Texto visto: ${ocr.raw}", style = ActaType.Label, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { ActaButton("Cerrar", onClick = { vm.clearOcr() }, filled = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
        )
    }

    val novedades by vm.novedades.collectAsStateWithLifecycle()
    if (novedades.isNotEmpty() && !showSettings) {
        AlertDialog(
            onDismissRequest = { vm.novedadesVistas() },
            title = { Text("Novedades · v$APP_VERSION") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    novedades.forEach { (v, lineas) ->
                        if (novedades.size > 1) Text("v$v", style = ActaType.Section, modifier = Modifier.padding(top = 6.dp))
                        lineas.forEach { Text("•  $it", style = ActaType.Body, modifier = Modifier.padding(top = 6.dp)) }
                    }
                }
            },
            confirmButton = { ActaButton("Entendido", onClick = { vm.novedadesVistas() }, filled = true, height = 44.dp, modifier = Modifier.widthIn(min = 130.dp)) }
        )
    }

    if (showIfaz) {
        val actual = ifaz.valor
        val reg = ifaz.registro
        val fmt = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
        AlertDialog(
            onDismissRequest = { showIfaz = false },
            title = { Text("Cambiar interfaz") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Filas(
                        listOfNotNull(
                            "Serie" to status.serial,
                            status.model.takeIf { it.isNotBlank() && it != "—" }?.let { "Modelo" to it },
                            status.firmwareApp.takeIf { it.isNotBlank() && it != "—" }?.let { "Firmware (programa)" to it },
                            status.billSet.takeIf { it.isNotBlank() && it != "—" }?.let { "Bill-set (variant)" to it },
                            mdaMap[status.serial]?.let { "MDA" to it },
                            "Interfaz actual" to "${cl.reabill.monitor.Interfaz.nombre(actual)}${actual?.let { " (valor $it)" } ?: ""}",
                            reg?.habitual?.let { "Habitual" to cl.reabill.monitor.Interfaz.nombre(it) },
                            reg?.ts?.takeIf { it > 0 }?.let { "Última lectura" to fmt.format(java.util.Date(it)) }
                        )
                    )
                    Text("Nueva interfaz", style = ActaType.Label, modifier = Modifier.padding(top = 14.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cl.reabill.monitor.Interfaz.VALORES.forEach { v ->
                            val esActual = v == actual
                            val etiqueta = cl.reabill.monitor.Interfaz.nombre(v) +
                                (if (esActual) " (actual)" else if (v == reg?.habitual && actual == cl.reabill.monitor.Interfaz.STAND_ALONE) " (volver)" else "")
                            cl.reabill.monitor.ui.components.ActaChip(etiqueta, ifazSel == v, { if (!esActual) ifazSel = v }, Modifier.alpha(if (esActual) 0.45f else 1f))
                        }
                    }
                    val hist = reg?.hist?.takeLast(4)?.reversed().orEmpty()
                    if (hist.isNotEmpty()) {
                        Text("Cambios recientes", style = ActaType.Label, modifier = Modifier.padding(top = 14.dp))
                        hist.forEach { h ->
                            val quien = when (h.origen) { "app" -> "BILLMON"; "primera" -> "primera lectura"; else -> "fuera de la app" }
                            Text("${fmt.format(java.util.Date(h.ts))}  ${cl.reabill.monitor.Interfaz.nombre(h.valor)}  ·  $quien", style = ActaType.Tiny, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    Text(
                        "Stand-alone, Netplex o EBDS pueden dejar de responder por USB al reiniciar el equipo.",
                        style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(top = 14.dp)
                    )
                }
            },
            confirmButton = {
                val sel = ifazSel
                ActaButton(
                    if (sel == null) "Elige una" else "Cambiar a ${cl.reabill.monitor.Interfaz.nombre(sel)}",
                    onClick = { if (sel != null) { showIfaz = false; vm.cambiarInterfaz(sel) } },
                    danger = true, enabled = sel != null && sel != actual && !ifaz.trabajando, height = 44.dp
                )
            },
            dismissButton = { ActaButton("Cancelar", onClick = { showIfaz = false }, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
        )
    }

    if (showCambios) {
        AlertDialog(
            onDismissRequest = { showCambios = false },
            title = { Text("Historial de versiones") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    cl.reabill.monitor.Novedades.NOTAS.entries
                        .sortedWith { a, b -> cl.reabill.monitor.Novedades.comparar(b.key, a.key) }
                        .forEachIndexed { i, (v, lineas) ->
                            if (i > 0) Hairline(Modifier.padding(top = 12.dp))
                            Row(Modifier.padding(top = if (i > 0) 10.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("v$v", style = ActaType.Section.copy(fontFamily = cl.reabill.monitor.ui.theme.ActaMono))
                                if (v == APP_VERSION) Text("  instalada", style = ActaType.Label.copy(color = Acta.Accent))
                            }
                            lineas.forEach { Text("•  $it", style = ActaType.Body, modifier = Modifier.padding(top = 4.dp)) }
                        }
                }
            },
            confirmButton = { ActaButton("Cerrar", onClick = { showCambios = false }, filled = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
        )
    }

    if (showLista) {
        val filas = remember(mdaMap, showLista) { vm.listaMaquinas() }

        LaunchedEffect(showLista) { vm.sincronizarAhora(false) }
        val porMda = revPend.associateBy { it.mda }
        val f = filtroLista.trim()
        fun coincide(mda: String, serie: String) = f.isEmpty() || mda.contains(f) || serie.contains(f, ignoreCase = true)

        val pendientes = revPend.flatMap { rv ->
            val propias = filas.filter { it.mda == rv.mda }
            if (propias.isEmpty()) listOf(rv to null) else propias.map { rv to it }
        }.filter { (rv, r) -> coincide(rv.mda, r?.serie ?: "") }

        fun sectorDe(rv: cl.reabill.monitor.Rev) = rv.isla.trim().firstOrNull()?.takeIf { it.isDigit() }?.toString() ?: ""
        fun numIsla(rv: cl.reabill.monitor.Rev) = rv.isla.trim().toLongOrNull() ?: Long.MAX_VALUE
        val pendOrdenados = when (ordenLista) {
            2 -> pendientes.sortedBy { it.first.mda }
            1 -> pendientes.sortedWith(compareBy({ sectorDe(it.first).ifEmpty { "~" } }, { numIsla(it.first) }, { it.first.mda }))
            else -> pendientes.sortedWith(compareBy({ numIsla(it.first) }, { it.first.isla }, { it.first.mda }))
        }
        val porSector = pendOrdenados.groupBy { sectorDe(it.first) }.mapValues { e -> e.value.map { it.first.mda }.toSet().size }
        val resto = if (soloRevisar) emptyList() else filas.filter { it.mda !in porMda && coincide(it.mda, it.serie) }
        AlertDialog(
            onDismissRequest = { showLista = false },
            title = { Text("Máquinas") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = filtroLista, onValueChange = { filtroLista = it.take(16) }, singleLine = true,
                            label = { Text("MDA o serie") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        ActaButton(
                            if (ocr.running) "Leyendo…" else "Foto", onClick = { showLista = false; lanzarFoto(); buscandoFoto = true },
                            enabled = !ocr.running, height = 52.dp, modifier = Modifier.padding(start = 8.dp).widthIn(min = 84.dp)
                        )
                    }
                    if (ocr.message.isNotEmpty() && !ocr.running && !ocr.test && ocr.candidates.isEmpty()) Text(ocr.message, style = ActaType.Label, modifier = Modifier.padding(top = 6.dp))
                    if (revPend.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ActaChip("Solo por revisar (${revPend.size})", soloRevisar, { soloRevisar = !soloRevisar })
                            ActaChip("Por isla", ordenLista == 0, { ordenLista = 0 })
                            ActaChip("Por sector", ordenLista == 1, { ordenLista = 1 })
                            ActaChip("Por MDA", ordenLista == 2, { ordenLista = 2 })
                        }
                    }

                    val mdaEscrito = cl.reabill.monitor.Mda.norm(f)?.takeIf { cl.reabill.monitor.Mda.valido(it) && f.length >= 6 }
                    val abrirEscrito = mdaEscrito != null && pendientes.none { it.first.mda == mdaEscrito } && resto.none { it.mda == mdaEscrito }
                    if (abrirEscrito) {
                        ActaButton("Ver MDA $mdaEscrito", onClick = { buscarTexto = mdaEscrito!!; showLista = false; buscarVieneDeLista = true; showBuscar = true }, height = 44.dp, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    } else if (filas.isEmpty() && revPend.isEmpty()) {
                        Text("Aún no hay máquinas. Conecta un billetero y anota su MDA.", style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(top = 14.dp))
                    } else if (pendientes.isEmpty() && resto.isEmpty()) {
                        Text("Sin resultados.", style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(top = 14.dp))
                    }
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                        if (pendientes.isNotEmpty()) {
                            Text("POR REVISAR (${pendientes.map { it.first.mda }.toSet().size})", style = ActaType.Label.copy(color = Acta.Accent, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                            var sectorAnterior: String? = null
                            pendOrdenados.forEach { (rv, r) ->
                                if (ordenLista == 1 && sectorDe(rv) != sectorAnterior) {
                                    sectorAnterior = sectorDe(rv)
                                    val sec = sectorDe(rv)
                                    Text(
                                        (if (sec.isEmpty()) "SIN ISLA" else "SECTOR $sec") + " · ${porSector[sec] ?: 0}",
                                        style = ActaType.Label.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                                    )
                                }
                                Hairline()
                                if (r != null) FilaMaquina(r, rv, weekly?.pct(r.mda)) { buscarTexto = r.mda; showLista = false; buscarVieneDeLista = true; showBuscar = true }
                                else FilaPendienteSinBilletero(rv, weekly?.pct(rv.mda)) { buscarTexto = rv.mda; showLista = false; buscarVieneDeLista = true; showBuscar = true }
                            }
                        }
                        if (resto.isNotEmpty()) {
                            if (pendientes.isNotEmpty()) Text("TODAS LAS DEMÁS", style = ActaType.Label.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                            resto.forEach { r ->
                                Hairline()
                                FilaMaquina(r, null, weekly?.pct(r.mda)) { buscarTexto = r.mda; showLista = false; buscarVieneDeLista = true; showBuscar = true }
                            }
                        }
                        if (pendientes.isNotEmpty() || resto.isNotEmpty()) Hairline()
                    }
                }
            },
            confirmButton = { ActaButton("Cerrar", onClick = { showLista = false }, filled = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
        )
    }

    if (showBuscar) {
        val hits = remember(buscarTexto, mdaMap, history) { if (buscarTexto.filter { it.isDigit() }.length >= 3) vm.buscarMda(buscarTexto) else emptyList() }
        val nTodo = cl.reabill.monitor.Mda.norm(buscarTexto)
        val n = nTodo?.takeIf { cl.reabill.monitor.Mda.valido(it) }
        val sistema = if (n != null && buscarTexto.filter { it.isDigit() }.length >= 3) weekly?.pct(n) else null
        val colorAs = Acta.Sistema
        val trabVersion by vm.trabVersion.collectAsStateWithLifecycle()
        val trabajo = if (n != null && buscarTexto.filter { it.isDigit() }.length >= 3) remember(n, trabVersion) { vm.ultimoTrabajo(n) } else null
        AlertDialog(
            onDismissRequest = { showBuscar = false; vm.clearOcr(); if (buscarVieneDeLista) { buscarVieneDeLista = false; showLista = true } },
            title = { Text("Máquina") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {

                    if (ocr.candidates.size > 1) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) { ocr.candidates.forEach { c -> cl.reabill.monitor.ui.components.ActaChip(c, buscarTexto == c, { buscarTexto = c }) } }
                    }
                    if (n == null && buscarTexto.filter { it.isDigit() }.length >= 3) {
                        Text("Número de máquina no válido.", style = ActaType.Body.copy(color = Acta.Error), modifier = Modifier.padding(top = 14.dp))
                    }
                    if (n != null && buscarTexto.filter { it.isDigit() }.length >= 3) {
                        Text("MDA", style = ActaType.Label.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 6.dp))
                        Text(n, style = ActaType.BillSet)
                        val pend = revPend.firstOrNull { it.mda == n }
                        if (pend != null) {
                            Text("POR REVISAR" + pend.etiqueta.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), style = ActaType.Label.copy(color = Acta.Accent, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 4.dp))
                            ActaButton("Revisar", onClick = { revisando = n }, filled = true, height = 44.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                        if (hits.isEmpty()) {
                            Text("Sin billetero leído en esta máquina.", style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(top = 10.dp))
                        }
                        val lecturaMax = hits.firstOrNull()?.lectura
                        Hairline(Modifier.padding(top = 14.dp), Acta.Ink)
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text("AR", style = ActaType.Label.copy(fontWeight = FontWeight.Bold, color = Acta.Ink), modifier = Modifier.alignByBaseline())
                                    Text("  billetero", style = ActaType.Label, modifier = Modifier.alignByBaseline())
                                }
                                Text(
                                    if (lecturaMax == null) "—" else "${fmtRate(lecturaMax.rate)}\u00A0%", maxLines = 1, softWrap = false,
                                    style = ActaType.Title.copy(fontSize = 28.sp, color = if (lecturaMax == null) Acta.Hairline else Acta.Ink)
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text("AS", style = ActaType.Label.copy(fontWeight = FontWeight.Bold, color = colorAs), modifier = Modifier.alignByBaseline())
                                    Text("  sistema", style = ActaType.Label, modifier = Modifier.alignByBaseline())
                                }
                                Text(
                                    if (sistema == null) "—" else "${fmtRate(sistema * 100.0)}\u00A0%", maxLines = 1, softWrap = false,
                                    style = ActaType.Title.copy(fontSize = 28.sp, color = if (sistema == null) Acta.Hairline else colorAs)
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                lecturaMax?.let { java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp)) } ?: "sin lectura",
                                style = ActaType.Tiny, modifier = Modifier.weight(1f)
                            )
                            Text(weekly?.let { "semana ${it.fecha.ifEmpty { "sin fecha" }}" } ?: "sin reporte", style = ActaType.Tiny, modifier = Modifier.weight(1f))
                        }
                        if (trabajo != null) {
                            Hairline(Modifier.padding(top = 14.dp))
                            Text("ÚLTIMO TRABAJO (Bitácora)", style = ActaType.Label.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 10.dp))
                            Text(
                                listOf(trabajo.fechaCorta, trabajo.quien).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "sin fecha ni nombre" },
                                style = ActaType.Cell, modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(trabajo.que, style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(top = 2.dp))
                            if (trabajo.total > 1) Text("${trabajo.total} revisiones en su historial", style = ActaType.Tiny, modifier = Modifier.padding(top = 2.dp))
                        }
                        hits.forEach { h ->
                            val l = h.lectura
                            Hairline(Modifier.padding(top = 14.dp))
                            val cs = weekly?.conteo(n)
                            if (l != null || cs != null) {
                                Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                    Spacer(Modifier.width(30.dp))
                                    Text("Aceptados", style = ActaType.Label.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
                                    Text("Rechazados", style = ActaType.Label.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
                                    Text("Total", style = ActaType.Label.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
                                }
                                if (l != null) Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                    Text("AR", style = ActaType.Label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Acta.Ink), modifier = Modifier.width(30.dp).alignByBaseline())
                                    Text(fmtInt(l.accepted), style = ActaType.Cell, modifier = Modifier.weight(1f).alignByBaseline())
                                    Text(fmtInt(l.rejected), style = ActaType.Cell, modifier = Modifier.weight(1f).alignByBaseline())
                                    Text(fmtInt(l.accepted + l.rejected), style = ActaType.Cell, modifier = Modifier.weight(1f).alignByBaseline())
                                }
                                if (cs != null) Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                    Text("AS", style = ActaType.Label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorAs), modifier = Modifier.width(30.dp).alignByBaseline())
                                    Text(fmtInt(cs.first), style = ActaType.Cell.copy(color = colorAs), modifier = Modifier.weight(1f).alignByBaseline())
                                    Text(fmtInt(cs.second), style = ActaType.Cell.copy(color = colorAs), modifier = Modifier.weight(1f).alignByBaseline())
                                    Text(fmtInt(cs.first + cs.second), style = ActaType.Cell.copy(color = colorAs), modifier = Modifier.weight(1f).alignByBaseline())
                                }
                            }
                            Filas(
                                listOfNotNull(
                                    "Serie" to h.serie,
                                    h.info?.modelo?.takeIf { it.isNotBlank() }?.let { "Modelo" to it },
                                    h.info?.firmware?.takeIf { it.isNotBlank() }?.let { "Firmware (programa)" to it },
                                    h.info?.billSet?.takeIf { it.isNotBlank() }?.let { "Bill-set (variant)" to it }
                                ),
                                Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { ActaButton("Cerrar", onClick = { showBuscar = false; vm.clearOcr(); if (buscarVieneDeLista) { buscarVieneDeLista = false; showLista = true } }, filled = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
        )
    }

    if (showMda) {
        AlertDialog(
            onDismissRequest = { showMda = false; vm.clearOcr() },
            title = { Text("N° de máquina") },
            text = {
                Column {
                    if (ocr.message.isNotEmpty()) Text(ocr.message, style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(bottom = 8.dp))
                    ocr.photo?.let { FotoConfirmacion(it, ocr.boxes, mdaTexto) { c -> mdaTexto = c } }
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
                        isError = mdaTexto.isNotBlank() && !cl.reabill.monitor.Mda.valido(cl.reabill.monitor.Mda.norm(mdaTexto)),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    ActaButton("Tomar foto", onClick = { showMda = false; vm.clearOcr(); lanzarFoto() }, height = 40.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = { ActaButton("Guardar", onClick = { vm.setMda(mdaTexto); showMda = false; vm.clearOcr() }, filled = true, enabled = mdaTexto.isBlank() || cl.reabill.monitor.Mda.valido(cl.reabill.monitor.Mda.norm(mdaTexto)), height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) },
            dismissButton = { ActaButton("Cancelar", onClick = { showMda = false; vm.clearOcr() }, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Borrar contabilidad") },
            text = {
                Text("Se guarda el estado actual y los contadores quedan en 0.")
            },
            confirmButton = { ActaButton("Borrar", onClick = { showReset = false; vm.resetAccounting() }, danger = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) },
            dismissButton = { ActaButton("Cancelar", onClick = { showReset = false }, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
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
                                    "depósito ${fmtInt(h.deposited)} · tasa ${fmtRate(h.rate)}\u00A0%",
                                style = ActaType.Log
                            )
                        }
                        Hairline()
                    }
                }
            },
            confirmButton = { ActaButton("Cerrar", onClick = { showHistory = false }, filled = true, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
        )
    }

    if (pidiendoClave) {

        val usuarios = remember(pidiendoClave) { vm.usuariosLista() }
        var otro by remember(pidiendoClave) { mutableStateOf(usuarios.isEmpty()) }
        var correoSel by remember(pidiendoClave) { mutableStateOf(vm.correoActual().takeIf { c -> usuarios.any { it.correo.equals(c, true) } } ?: "") }
        var correoOtro by remember(pidiendoClave) { mutableStateOf(if (usuarios.isEmpty()) vm.correoActual() else "") }
        val correo = if (otro) correoOtro.trim() else correoSel
        val entrar = {
            comprobando = true
            vm.entrarComo(correo, claveAjustes) { err ->
                comprobando = false
                if (err == null) { pidiendoClave = false; showSettings = true } else { claveMala = true; motivoClave = err }
            }
        }
        AlertDialog(
            onDismissRequest = { pidiendoClave = false },
            title = { Text("¿Quién eres?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (usuarios.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        usuarios.forEach { u -> ActaChip(u.corto, !otro && correoSel.equals(u.correo, true), { otro = false; correoSel = u.correo; claveMala = false }) }
                        ActaChip("Otro", otro, { otro = true; claveMala = false })
                    }
                    if (otro) OutlinedTextField(
                        value = correoOtro, onValueChange = { correoOtro = it.take(120); claveMala = false }, singleLine = true, label = { Text("Correo de la Bitácora") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().padding(top = if (usuarios.isNotEmpty()) 12.dp else 0.dp)
                    )
                    OutlinedTextField(
                        value = claveAjustes, onValueChange = { claveAjustes = it.take(120); claveMala = false }, singleLine = true, label = { Text("Clave") },
                        isError = claveMala,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    if (claveMala) Text(motivoClave, style = ActaType.Body.copy(color = Acta.Error), modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { ActaButton(if (comprobando) "Comprobando…" else "Entrar", onClick = entrar, filled = true, enabled = correo.isNotEmpty() && claveAjustes.isNotEmpty() && !comprobando, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) },
            dismissButton = { ActaButton("Cancelar", onClick = { pidiendoClave = false }, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
        )
    }

    Box(Modifier.fillMaxSize().background(Acta.Paper).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                TopRow(phase, onSettings = { if (vm.preferenciasBloqueadas()) { claveAjustes = ""; claveMala = false; pidiendoClave = true } else showSettings = true })
                ActivityRule(
                    busy = phase.isBusy, fault = phase == Phase.NoResponse,
                    modifier = Modifier.padding(horizontal = Gutter)
                )

                updateVersion?.let { v ->
                    UpdateStrip(v, updateProgress, onInstall = { vm.installUpdate() })
                }

                Hero(
                    phase, accounting, connected,
                    mda = mdaMap[status.serial],
                    sistema = mdaMap[status.serial]?.let { weekly?.pct(it) },
                    hayReporte = weekly != null, leyendo = ocr.running,
                    onEdit = { mdaTexto = mdaMap[status.serial] ?: ""; vm.clearOcr(); showMda = true },
                    onPhoto = lanzarFoto
                )
                IdentityBlock(phase, status.billSet, status.serial, status.model, status.manufacturer, status.firmwareApp)
                if (connected && ifaz.soportada) InterfazSection(
                    ifaz,
                    onChange = {
                        val v = ifaz.valor
                        ifazSel = if (v == cl.reabill.monitor.Interfaz.STAND_ALONE) ifaz.registro?.habitual else null
                        showIfaz = true
                    }
                )

                val mdaActual = mdaMap[status.serial]
                AccountingSection(
                    acc = accounting, history = history, phase = phase, connected = connected,
                    sistema = if (connected) mdaActual?.let { m -> weekly?.conteo(m)?.let { SisCols(it.first, it.second, weekly?.pct(m), weekly?.fecha.orEmpty()) } } else null,
                    onRead = { vm.readAccounting() }, onReset = { showReset = true }
                )

                if (history.isNotEmpty()) HistorySection(history, status.serial, onShowAll = { showHistory = true })

                Column(Modifier.padding(horizontal = Gutter).padding(top = 16.dp)) {

                    val pendAqui = mdaActual?.let { cl.reabill.monitor.Mda.norm(it) }?.let { m -> revPend.firstOrNull { it.mda == m } }
                    if (connected && pendAqui != null) ActaButton(
                        "Revisar MDA ${pendAqui.mda}", onClick = { revisando = pendAqui.mda }, filled = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActaButton(
                            if (printUi.printing) "Imprimiendo…" else "Imprimir etiqueta",
                            onClick = printNow, filled = pendAqui == null || !connected,
                            enabled = accounting != null && !printUi.printing, modifier = Modifier.weight(1f)
                        )
                        ActaButton(
                            "Cargar bill-set", onClick = { showFlash = true },
                            enabled = connected && !busy, modifier = Modifier.weight(1f)
                        )
                    }
                    ActaButton(
                        "Máquinas", onClick = { filtroLista = ""; soloRevisar = false; vm.clearOcr(); showLista = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                    TareasStrip(
                        porRevisar = revPend.size, etiquetas = etqPend.size,
                        onRevisar = { filtroLista = ""; soloRevisar = true; vm.clearOcr(); showLista = true },
                        onEtiquetas = { showEtq = true },
                        modifier = Modifier.padding(top = 10.dp)
                    )
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

        revisando?.let { m -> RevisarDialog(vm, m, revPend.firstOrNull { it.mda == m }, onClose = { revisando = null }) }
        if (showEtq) EtiquetasDialog(vm, onClose = { showEtq = false })
        if (showLabel) LabelSheet(vm, onClose = { showLabel = false })
        SettingsSheet(
            visible = showSettings, vm = vm, log = log, connected = connected, kind = kind,
            onClose = { showSettings = false },
            onLabelConfig = { showLabel = true },
            onChangelog = { showCambios = true },
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
