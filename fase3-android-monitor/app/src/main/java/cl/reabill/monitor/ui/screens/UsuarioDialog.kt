package cl.reabill.monitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.reabill.monitor.MonitorViewModel
import cl.reabill.monitor.ui.components.ActaButton
import cl.reabill.monitor.ui.components.ActaChip
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.ActaType

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun UsuarioDialog(vm: MonitorViewModel, onClose: () -> Unit, onListo: () -> Unit) {
    val usuarios = remember { vm.usuariosLista() }
    var otro by remember { mutableStateOf(usuarios.isEmpty()) }
    var correoSel by remember { mutableStateOf(vm.correoActual().takeIf { c -> usuarios.any { it.correo.equals(c, true) } } ?: "") }
    var correoOtro by remember { mutableStateOf(if (usuarios.isEmpty()) vm.correoActual() else "") }
    var clave by remember { mutableStateOf("") }
    var mala by remember { mutableStateOf(false) }
    var motivo by remember { mutableStateOf("") }
    var comprobando by remember { mutableStateOf(false) }
    val correo = if (otro) correoOtro.trim() else correoSel
    val entrar = {
        comprobando = true
        vm.entrarComo(correo, clave) { err ->
            comprobando = false
            if (err == null) onListo() else { mala = true; motivo = err }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("¿Quién eres?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (usuarios.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    usuarios.forEach { u -> ActaChip(u.corto, !otro && correoSel.equals(u.correo, true), { otro = false; correoSel = u.correo; mala = false }) }
                    ActaChip("Otro", otro, { otro = true; mala = false })
                }
                if (otro) OutlinedTextField(
                    value = correoOtro, onValueChange = { correoOtro = it.take(120); mala = false }, singleLine = true, label = { Text("Correo de la Bitácora") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().padding(top = if (usuarios.isNotEmpty()) 12.dp else 0.dp)
                )
                OutlinedTextField(
                    value = clave, onValueChange = { clave = it.take(120); mala = false }, singleLine = true, label = { Text("Clave") }, isError = mala,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                if (mala) Text(motivo, style = ActaType.Body.copy(color = Acta.Error), modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { ActaButton(if (comprobando) "Comprobando…" else "Entrar", onClick = entrar, filled = true, enabled = correo.isNotEmpty() && clave.isNotEmpty() && !comprobando, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) },
        dismissButton = { ActaButton("Cancelar", onClick = onClose, height = 44.dp, modifier = Modifier.widthIn(min = 110.dp)) }
    )
}
