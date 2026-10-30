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
internal fun BottomBar(connected: Boolean, enabled: Boolean, onConnect: () -> Unit, onCalibrate: () -> Unit, puedeCalibrar: Boolean = true) {

    if (connected && !puedeCalibrar) return
    Column {
        Hairline(color = Acta.Ink)
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

internal enum class CalAck { Waiting, Accepted, NoAck }

@Composable
internal fun CalibrationSheet(
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
internal fun CalStep(text: String, state: String, stateColor: Color, pulse: Boolean) {
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
