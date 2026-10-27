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
internal fun TopRow(phase: Phase, onSettings: () -> Unit) {
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
internal fun UpdateStrip(version: String, progress: Int, onInstall: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp).border(1.dp, Acta.Ink, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(14.dp)) {
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
            Text(if (progress >= 0) "Descargando… $progress\u00A0%" else "Descargando…", style = ActaType.Label)
        }
    }
}

@Composable
internal fun Hero(
    phase: Phase, acc: AccountingData?, connected: Boolean, mda: String?, sistema: Double?, hayReporte: Boolean,
    leyendo: Boolean, onEdit: () -> Unit, onPhoto: () -> Unit
) {

    val avance = remember { Animatable(0f) }
    LaunchedEffect(acc?.timestamp, sistema) {
        if (acc == null) avance.snapTo(0f)
        else { avance.snapTo(0f); avance.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    }
    val fault = phase == Phase.NoResponse
    val ar = if (acc == null) "—" else "${fmtRate(acc.rate * avance.value)}\u00A0%"
    val sentence = when {
        acc != null && acc.inserted == 0 -> "No hay billetes insertados en la contabilidad."
        acc != null -> "${fmtInt(acc.accepted)} de ${fmtInt(acc.inserted)} billetes insertados fueron aceptados."
        phase == Phase.Disconnected -> "Sin billetero conectado."
        phase == Phase.ReadingAccounting -> "Leyendo la contabilidad del equipo…"
        phase.isBusy -> "Identificando el equipo…"
        fault -> "El equipo no respondió."
        else -> "Pulsa Leer para obtener la contabilidad."
    }
    val colorAs = Acta.Sistema
    Column(Modifier.padding(horizontal = Gutter).padding(top = 10.dp, bottom = 14.dp)) {
        if (connected) {
            if (mda != null) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("MDA", style = ActaType.Label.copy(fontWeight = FontWeight.Bold), modifier = Modifier.alignByBaseline())
                    Text(mda, style = ActaType.BillSet, modifier = Modifier.padding(start = 10.dp).alignByBaseline())
                    Spacer(Modifier.weight(1f))
                    Text("editar", style = ActaType.Link.copy(fontSize = 12.sp), modifier = Modifier.alignByBaseline())
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActaButton(
                        if (leyendo) "Leyendo…" else "Agregar máquina (foto)", onClick = onPhoto, enabled = !leyendo,
                        filled = true, height = 40.dp, modifier = Modifier.weight(1f)
                    )
                    ActaButton("Escribir", onClick = onEdit, height = 40.dp, modifier = Modifier.width(96.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Row {
                    Text("AR", style = ActaType.Label.copy(fontWeight = FontWeight.Bold, color = Acta.Ink), modifier = Modifier.alignByBaseline())
                    Text("  billetero", style = ActaType.Label, modifier = Modifier.alignByBaseline())
                }
                Text(
                    ar, maxLines = 1, softWrap = false,
                    style = ActaType.Title.copy(
                        fontSize = 36.sp,
                        color = when {
                            acc != null -> Acta.Ink
                            fault -> Acta.Error
                            else -> Acta.Hairline
                        }
                    )
                )
            }
            if (connected && mda != null && hayReporte) Column(Modifier.weight(1f)) {
                Row {
                    Text("AS", style = ActaType.Label.copy(fontWeight = FontWeight.Bold, color = colorAs), modifier = Modifier.alignByBaseline())
                    Text("  sistema", style = ActaType.Label, modifier = Modifier.alignByBaseline())
                }
                Text(
                    if (sistema == null || acc == null) "—" else "${fmtRate(sistema * 100.0 * avance.value)}\u00A0%", maxLines = 1, softWrap = false,
                    style = ActaType.Title.copy(fontSize = 36.sp, color = if (sistema == null || acc == null) Acta.Hairline else colorAs)
                )
            }
        }
        Text(
            sentence, style = ActaType.Body.copy(color = if (fault) Acta.Error else Acta.Ink),
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.9f)
        )
        RateScale(acc?.rate, Modifier.padding(top = 12.dp))
    }
}

@Composable
internal fun InterfazSection(u: MonitorViewModel.IfazUi, onChange: () -> Unit) {
    SectionHeader("Interfaz") {
        ActaButton(
            if (u.trabajando) "Cambiando…" else "Cambiar", onClick = onChange,
            enabled = !u.trabajando && u.valor != null, height = 36.dp,
            modifier = Modifier.padding(vertical = 5.dp).width(96.dp)
        )
    }
    Column(Modifier.padding(horizontal = Gutter)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Actual", style = ActaType.Body, modifier = Modifier.weight(1f).alignByBaseline())
            Text(cl.reabill.monitor.Interfaz.nombre(u.valor), style = ActaType.Cell, modifier = Modifier.alignByBaseline())
        }
        val hab = u.registro?.habitual
        if (hab != null && hab != u.valor) {
            Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Habitual (volver a)", style = ActaType.Body, modifier = Modifier.weight(1f).alignByBaseline())
                Text(cl.reabill.monitor.Interfaz.nombre(hab), style = ActaType.Cell.copy(color = Acta.Sistema), modifier = Modifier.alignByBaseline())
            }
        }
        if (u.mensaje.isNotEmpty()) Text(u.mensaje, style = ActaType.Label, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
internal fun Dato(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(rotulo, style = ActaType.Label.copy(fontSize = 11.sp))
        Text(valor, style = ActaType.Cell, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
internal fun Filas(filas: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier) {
        filas.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(k, style = ActaType.Label, modifier = Modifier.weight(0.8f).alignByBaseline())
                Text(v, style = ActaType.Cell, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f).alignByBaseline())
            }
        }
    }
}

@Composable
internal fun FotoConfirmacion(bmp: android.graphics.Bitmap, boxes: Map<String, android.graphics.RectF>, sel: String, onSel: (String) -> Unit) {
    val img = remember(bmp) { bmp.asImageBitmap() }
    val relleno = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD } }
    val borde = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD; style = android.graphics.Paint.Style.STROKE; color = android.graphics.Color.BLACK
        }
    }
    val corte = remember(bmp, boxes) {
        if (boxes.isEmpty()) android.graphics.RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        else {
            val u = android.graphics.RectF(boxes.values.first())
            boxes.values.forEach { u.union(it) }
            val h = u.height(); val w = u.width()
            val mx = maxOf(w * 0.35f, h * 1.0f)
            android.graphics.RectF(
                maxOf(0f, u.left - mx), maxOf(0f, u.top - h * 2.4f),
                minOf(bmp.width.toFloat(), u.right + mx), minOf(bmp.height.toFloat(), u.bottom + h * 1.2f)
            )
        }
    }
    Canvas(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .aspectRatio(corte.width() / corte.height())
            .pointerInput(boxes, corte) {
                detectTapGestures { pos ->
                    val k = size.width.toFloat() / corte.width()
                    boxes.entries.firstOrNull { (_, r) ->
                        val l = (r.left - corte.left) * k; val t = (r.top - corte.top) * k - r.height() * k * 1.4f
                        pos.x in (l - 12)..((r.right - corte.left) * k + 12) && pos.y in (t - 12)..((r.bottom - corte.top) * k + 12)
                    }?.let { onSel(it.key) }
                }
            }
    ) {
        val k = size.width / corte.width()
        drawImage(
            img, srcOffset = androidx.compose.ui.unit.IntOffset(corte.left.toInt(), corte.top.toInt()),
            srcSize = androidx.compose.ui.unit.IntSize(corte.width().toInt(), corte.height().toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
        )
        drawIntoCanvas { c ->
            boxes.forEach { (cand, r) ->
                val elegido = cand == sel
                val color = if (elegido) android.graphics.Color.rgb(229, 57, 53) else android.graphics.Color.rgb(30, 136, 229)
                val l = (r.left - corte.left) * k; val t = (r.top - corte.top) * k
                val ancho = r.width() * k; val alto = r.height() * k

                val marco = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE; strokeWidth = if (elegido) 4f else 2f; this.color = color
                }
                c.nativeCanvas.drawRoundRect(l - 4f, t - 4f, l + ancho + 4f, t + alto + 4f, 8f, 8f, marco)

                relleno.color = color; relleno.textSize = 100f
                val tam = (ancho / relleno.measureText(cand) * 100f).coerceAtMost(alto * 0.95f)
                relleno.textSize = tam; borde.textSize = tam; borde.strokeWidth = tam * 0.1f; borde.color = android.graphics.Color.WHITE
                val ancho2 = relleno.measureText(cand)
                val x = l + (ancho - ancho2) / 2f
                val y = if (t - 10f - tam * 0.2f > tam) t - 10f - tam * 0.2f else t + alto + 10f + tam
                c.nativeCanvas.drawText(cand, x, y, borde)
                c.nativeCanvas.drawText(cand, x, y, relleno)
            }
        }
    }
}

@Composable
internal fun IdentityBlock(
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
internal fun MiniField(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 7.dp)) {
        Text(label, style = ActaType.Label.copy(fontSize = 11.sp))
        ResolveText(value, ActaType.Value)
    }
}

@Composable
internal fun PendingText(text: String, style: TextStyle) {
    val a by rememberInfiniteTransition(label = "pendiente").animateFloat(
        1f, 0.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a"
    )
    Text(text, style = style.copy(color = Acta.Hairline), modifier = Modifier.alpha(a))
}

@Composable
internal fun FilaMaquina(r: cl.reabill.monitor.MonitorViewModel.MdaFila, rv: cl.reabill.monitor.Rev?, sis: Double?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.3f)) {
            Text(r.mda, style = ActaType.Serial)
            Text(r.serie, style = ActaType.Tiny)
            if (rv != null) MarcaPorRevisar(rv)
        }
        Column(Modifier.weight(1f)) {
            Text("AR", style = ActaType.Label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Acta.Ink))
            Text(r.lectura?.let { "${cl.reabill.monitor.ui.fmtRate(it.rate)}\u00A0%" } ?: "—", style = ActaType.Cell, maxLines = 1, softWrap = false)
        }
        Column(Modifier.weight(1f)) {
            Text("AS", style = ActaType.Label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Acta.Sistema))
            Text(sis?.let { "${cl.reabill.monitor.ui.fmtRate(it * 100.0)}\u00A0%" } ?: "—", style = ActaType.Cell.copy(color = Acta.Sistema), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
internal fun FilaPendienteSinBilletero(rv: cl.reabill.monitor.Rev, sis: Double?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.3f)) {
            Text(rv.mda, style = ActaType.Serial)
            Text("sin billetero en este teléfono", style = ActaType.Tiny)
            MarcaPorRevisar(rv)
        }
        Column(Modifier.weight(1f)) {
            Text("AR", style = ActaType.Label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Acta.Ink))
            Text(rv.pct?.let { "${cl.reabill.monitor.ui.fmtRate(it)}\u00A0%" } ?: "—", style = ActaType.Cell, maxLines = 1, softWrap = false)
        }
        Column(Modifier.weight(1f)) {
            Text("AS", style = ActaType.Label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Acta.Sistema))
            Text(sis?.let { "${cl.reabill.monitor.ui.fmtRate(it * 100.0)}\u00A0%" } ?: "—", style = ActaType.Cell.copy(color = Acta.Sistema), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun MarcaPorRevisar(rv: cl.reabill.monitor.Rev) {
    Text(
        "POR REVISAR" + rv.etiqueta.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
        style = ActaType.Tiny.copy(color = Acta.Accent, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 2.dp), maxLines = 1
    )
    val d = listOfNotNull(
        rv.isla.takeIf { it.isNotBlank() }?.let { "isla $it" }, rv.mod.takeIf { it.isNotBlank() }, rv.fw.takeIf { it.isNotBlank() }?.let { "fw $it" }
    ).joinToString(" · ")
    if (d.isNotEmpty()) Text(d, style = ActaType.Tiny, maxLines = 1)
}
