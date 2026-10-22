package cl.reabill.monitor.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.reabill.monitor.MonitorViewModel
import cl.reabill.monitor.ui.components.ActaButton
import cl.reabill.monitor.ui.components.Hairline
import cl.reabill.monitor.ui.components.HoldButton
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.ActaType

private val Gutter = 18.dp

@Composable
fun FlashSheet(
    visible: Boolean, vm: MonitorViewModel, serial: String, billSet: String, onClose: () -> Unit
) {
    val ui by vm.flash.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val close = { if (!ui.running) { vm.resetFlash(); onClose() } }
    BackHandler(enabled = visible, onBack = { close() })

    val view = LocalView.current
    DisposableEffect(ui.running) {
        view.keepScreenOn = ui.running
        onDispose { view.keepScreenOn = false }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickFirmware(uri)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)) + slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 14 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 14 }
    ) {
        Column(Modifier.fillMaxSize().background(Acta.Paper).padding(Gutter)) {
            Text("Equipo $serial", style = ActaType.Label)
            Text("Cargar bill-set", style = ActaType.Title, modifier = Modifier.padding(top = 6.dp))
            Text(
                "Bill-set actual $billSet. Para Cashflow SC83.",
                style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )
            Hairline(color = Acta.Ink)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ui.file?.name ?: "Ningún archivo elegido", style = ActaType.Body)
                        ui.file?.let {
                            Text("Bill-set #${it.label} · ${it.blocks} bloques · ${it.data.size} B", style = ActaType.Label)
                        }
                        if (ui.error.isNotEmpty())
                            Text(ui.error, style = ActaType.Body.copy(color = Acta.Error), modifier = Modifier.padding(top = 4.dp))
                    }
                    ActaButton("Agregar archivo", onClick = { picker.launch(arrayOf("*/*")) }, enabled = !ui.running, height = 40.dp, modifier = Modifier.padding(start = 8.dp))
                }
                Hairline()

                if (library.isNotEmpty() && !ui.done) {
                    Text("GUARDADOS", style = ActaType.Label, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    library.forEach { f ->
                        val sel = ui.file?.name == f.name
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !ui.running) { vm.selectFromLibrary(f) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = ActaType.Body.copy(color = if (sel) Acta.Accent else Acta.Ink))
                                Text("Bill-set #${f.label}", style = ActaType.Label)
                            }
                            ActaButton("Quitar", onClick = { vm.removeFromLibrary(f) }, danger = true, enabled = !ui.running, height = 36.dp, modifier = Modifier.padding(start = 8.dp))
                        }
                        Hairline()
                    }
                }

                if (ui.running || ui.done) {
                    Text(
                        if (ui.running) ui.step else if (ui.ok) "ESCRITURA TERMINADA" else "ESCRITURA FALLÓ",
                        style = ActaType.Log.copy(color = if (ui.done && !ui.ok) Acta.Error else if (ui.done) Acta.Accent else Acta.Ink),
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(3.dp).background(Acta.Hairline)) {
                        Box(
                            Modifier.fillMaxWidth(ui.progress.coerceIn(0, 100) / 100f).height(3.dp)
                                .background(if (ui.done && !ui.ok) Acta.Error else Acta.Accent)
                        )
                    }
                    if (ui.message.isNotEmpty())
                        Text(ui.message, style = ActaType.Body, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (ui.running)
                    Text(
                        "No desconectes el cable ni cierres la app. Tarda unos segundos.",
                        style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(top = 8.dp)
                    )
                else if (!ui.done)
                    Text(
                        "Reemplaza el bill-set del equipo con este archivo. Se escribe sin modificarlo; " +
                            "el equipo se reinicia solo al terminar.",
                        style = ActaType.Body.copy(color = Acta.Ink2), modifier = Modifier.padding(top = 14.dp)
                    )
            }
            if (!ui.done) {
                HoldButton(
                    "Escribir en el equipo", "mantener presionado", onArmed = { vm.startFlash() },
                    enabled = ui.file != null && !ui.running, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            ActaButton("Volver", onClick = { close() }, enabled = !ui.running, modifier = Modifier.fillMaxWidth())
        }
    }
}
