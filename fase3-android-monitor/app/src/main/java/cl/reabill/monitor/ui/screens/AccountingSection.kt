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
internal fun AccountingSection(
    acc: AccountingData?, history: List<AccountingData>, phase: Phase, connected: Boolean,
    sistema: SisCols?, onRead: () -> Unit, onReset: () -> Unit, puedeBorrar: Boolean = true
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
            HeadCell("Concepto", Modifier.weight(if (sistema != null) 1.6f else 1.5f), TextAlign.Start)
            HeadCell("Actual", Modifier.weight(1f))
            HeadCell("Anterior", Modifier.weight(1f))
            if (sistema != null) Text("Sistema", style = ActaType.Label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Acta.Sistema), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        if (acc == null) {
            Hairline()
            Text(
                if (connected) "Sin lectura." else "Sin billetero.",
                style = ActaType.Label, modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            LedgerRow("Aceptados", acc.accepted, prev?.accepted, sistema?.let { fmtInt(it.aceptados) }, sistema != null)
            LedgerRow("Rechazados", acc.rejected, prev?.rejected, sistema?.let { fmtInt(it.rechazados) }, sistema != null)
            RateRow(acc.rate, prev?.rate, sistema?.pct?.let { "${fmtRate(it * 100.0)}\u00A0%" }, sistema != null)
            PlainRow("Insertados", fmtInt(acc.inserted), sistema?.let { fmtInt(it.aceptados + it.rechazados) }, sistema != null)
            PlainRow("Apilados", fmtInt(acc.stacked), null, sistema != null)
            PlainRow(if (sistema != null) "En depósito" else "Alcanzan depósito", fmtInt(acc.deposited), null, sistema != null)
            if (prev != null) {
                val d = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(prev.timestamp))
                Text("Anterior: lectura del $d", style = ActaType.Label, modifier = Modifier.padding(top = 6.dp))
            }
            if (sistema != null) {
                Text("Sistema: semana ${sistema.fecha.ifEmpty { "sin fecha" }}", style = ActaType.Label.copy(color = Acta.Sistema), modifier = Modifier.padding(top = 4.dp))
            }
            if (puedeBorrar) {
                Spacer(Modifier.height(10.dp))
                ActaButton(
                    "Borrar contabilidad", onClick = onReset, danger = true, height = 44.dp,
                    enabled = connected && !phase.isBusy, modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String, action: @Composable () -> Unit = {}) {
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
internal fun HeadCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.End) {
    Text(text, style = ActaType.Label.copy(fontSize = 11.sp), textAlign = align, modifier = modifier)
}

internal class SisCols(val aceptados: Int, val rechazados: Int, val pct: Double?, val fecha: String)

@Composable
internal fun SisCell(texto: String?, modifier: Modifier) {
    Text(texto ?: "", style = ActaType.Cell.copy(color = Acta.Sistema), textAlign = TextAlign.End, modifier = modifier)
}

@Composable
internal fun LedgerRow(label: String, value: Int, previous: Int?, sis: String?, hayColumna: Boolean) {
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ActaType.Body, modifier = Modifier.weight(if (hayColumna) 1.6f else 1.5f))
        CountText(value, previous, ActaType.Cell, Modifier.weight(1f), TextAlign.End)
        Text(
            previous?.let { fmtInt(it) } ?: "—",
            style = ActaType.Cell.copy(color = Acta.Ink2), textAlign = TextAlign.End, modifier = Modifier.weight(1f)
        )
        if (hayColumna) SisCell(sis, Modifier.weight(1f))
    }
}

@Composable
internal fun RateRow(current: Double, previous: Double?, sis: String?, hayColumna: Boolean) {
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (hayColumna) "Tasa" else "Tasa de aceptación", style = ActaType.Body, modifier = Modifier.weight(if (hayColumna) 1.6f else 1.5f))
        Text("${fmtRate(current)}\u00A0%", style = ActaType.Cell, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(
            previous?.let { "${fmtRate(it)}\u00A0%" } ?: "—",
            style = ActaType.Cell.copy(color = Acta.Ink2), textAlign = TextAlign.End, modifier = Modifier.weight(1f)
        )
        if (hayColumna) SisCell(sis, Modifier.weight(1f))
    }
}

@Composable
internal fun PlainRow(label: String, value: String, sis: String?, hayColumna: Boolean) {
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ActaType.Body, modifier = Modifier.weight(if (hayColumna) 1.6f else 1.5f))
        Text(value, style = ActaType.Cell, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
        if (hayColumna) SisCell(sis, Modifier.weight(1f))
    }
}

@Composable
internal fun HistorySection(history: List<AccountingData>, serial: String, onShowAll: () -> Unit) {
    SectionHeader("Historial · $serial")
    Column(Modifier.padding(horizontal = Gutter)) {
        history.take(3).forEach { h ->
            Hairline()
            val d = java.text.SimpleDateFormat("dd-MM HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(h.timestamp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(d, style = ActaType.Log.copy(color = Acta.Ink2), modifier = Modifier.width(92.dp))
                Text(
                    "acep ${h.accepted}  rech ${h.rejected}  tasa ${fmtRate(h.rate)}\u00A0%",
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
