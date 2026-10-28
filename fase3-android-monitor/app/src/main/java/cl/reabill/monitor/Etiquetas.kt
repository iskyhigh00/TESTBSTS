package cl.reabill.monitor

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PrintUi(
    val devices: List<P12Printer.Found> = emptyList(),
    val scanning: Boolean = false,
    val connectedName: String? = null,
    val printing: Boolean = false,
    val message: String = ""
)
data class LabelPrefs(
    val lengthMm: Int = 0, val dieCut: Boolean = false, val rotate: Boolean = false,
    val density: Int = 0, val copies: Int = 1, val confirmed: Boolean = false,
    val fields: Set<String> = LabelFields.DEFAULT, val align: Int = 0, val compact: Boolean = true,
    val scale: Int = 100, val vpos: Int = 1, val names: Map<String, String> = emptyMap(), val twoLines: Boolean = false,
    val lastAddress: String = "", val lastName: String = "",
    val salida: Int = LabelProtocol.FEED_DEFAULT
)

class Etiquetas(
    context: Context,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit,
    private val alCambiar: () -> Unit,
    private val datos: () -> LabelData?
) {
    private val prefs = context.getSharedPreferences("etiquetas", android.content.Context.MODE_PRIVATE)
    private val printer = P12Printer(context) { log(it) }
    private val _print = MutableStateFlow(PrintUi())
    val print: StateFlow<PrintUi> = _print

    private val migrarLargo = if (!prefs.contains("auto_v1")) prefs.edit().putInt("mm", 0).putBoolean("auto_v1", true).apply() else Unit
    private val _labelPrefs = MutableStateFlow(LabelPrefs(
        prefs.getInt("mm", 0), prefs.getBoolean("die", false), prefs.getBoolean("rot", false),
        prefs.getInt("dens", 0), prefs.getInt("copies", 1).coerceIn(1, 10), prefs.getBoolean("conf", false),
        LabelFields.parse(prefs.getString("campos", null)), prefs.getInt("align", 0), prefs.getBoolean("compact", true),
        LabelFit.snap(prefs.getInt("scale", 100)), prefs.getInt("vpos", 1), LabelFields.parseNames(prefs.getString("nombres", null)), prefs.getBoolean("dos", false),
        prefs.getString("addr", "") ?: "", LabelProtocol.cleanName(prefs.getString("pname", "")),
        prefs.getInt("sal", LabelProtocol.FEED_DEFAULT).coerceIn(8, 255)
    ))
    val labelPrefs: StateFlow<LabelPrefs> = _labelPrefs

    fun setLabelPrefs(p: LabelPrefs) {
        guardar(p)
        alCambiar()
    }

    fun compartida(): org.json.JSONObject {
        val p = _labelPrefs.value
        return org.json.JSONObject().put("mm", p.lengthMm).put("die", p.dieCut).put("rot", p.rotate).put("dens", p.density)
            .put("copies", p.copies).put("conf", p.confirmed).put("campos", LabelFields.encode(p.fields)).put("align", p.align)
            .put("compact", p.compact).put("scale", p.scale).put("vpos", p.vpos).put("dos", p.twoLines)
            .put("nombres", LabelFields.encodeNames(p.names)).put("sal", p.salida)
    }

    fun aplicarCompartida(j: org.json.JSONObject) {
        val a = _labelPrefs.value
        guardar(a.copy(
            lengthMm = j.optInt("mm", a.lengthMm), dieCut = j.optBoolean("die", a.dieCut), rotate = j.optBoolean("rot", a.rotate),
            density = j.optInt("dens", a.density), copies = j.optInt("copies", a.copies).coerceIn(1, 10), confirmed = j.optBoolean("conf", a.confirmed),
            fields = if (j.has("campos")) LabelFields.parse(j.optString("campos")) else a.fields,
            align = j.optInt("align", a.align), compact = j.optBoolean("compact", a.compact), scale = LabelFit.snap(j.optInt("scale", a.scale)),
            vpos = j.optInt("vpos", a.vpos), twoLines = j.optBoolean("dos", a.twoLines),
            names = if (j.has("nombres")) LabelFields.parseNames(j.optString("nombres")) else a.names,
            salida = j.optInt("sal", a.salida).coerceIn(8, 255)
        ))
    }

    private fun guardar(p: LabelPrefs) {
        _labelPrefs.value = p
        prefs.edit().putInt("mm", p.lengthMm).putBoolean("die", p.dieCut).putBoolean("rot", p.rotate)
            .putInt("dens", p.density).putBoolean("conf", p.confirmed).putString("campos", LabelFields.encode(p.fields)).putInt("align", p.align).putBoolean("compact", p.compact)
            .putInt("scale", p.scale).putInt("vpos", p.vpos).putInt("copies", p.copies).putBoolean("dos", p.twoLines).putString("nombres", LabelFields.encodeNames(p.names))
            .putString("addr", p.lastAddress).putString("pname", p.lastName).putInt("sal", p.salida).apply()
    }

    fun estilo(p: LabelPrefs) = LabelStyle(p.align, p.vpos, p.compact, p.scale, p.rotate, p.twoLines)

    private var scanJob: kotlinx.coroutines.Job? = null

    fun labelScan() {
        if (!printer.available || !printer.bluetoothOn) {
            _print.update { it.copy(message = "Enciende el Bluetooth del teléfono.") }; return
        }
        _print.update { it.copy(devices = emptyList(), scanning = true, message = "Buscando impresoras…") }
        if (!printer.startScan { f ->
                _print.update { u ->
                    val sin = u.devices.filter { it.address != f.address }
                    val prev = u.devices.firstOrNull { it.address == f.address }
                    val nuevo = if (prev != null && f.name == "(sin nombre)") prev.copy(rssi = f.rssi) else f
                    u.copy(devices = (sin + nuevo).sortedWith(compareByDescending<P12Printer.Found> { it.likely }
                        .thenByDescending { it.name != "(sin nombre)" }.thenByDescending { it.rssi }))
                }
            }) { _print.update { it.copy(scanning = false, message = "No se pudo iniciar la búsqueda.") }; return }
        scanJob?.cancel()
        scanJob = scope.launch {
            delay(12_000)
            printer.stopScan()
            _print.update { it.copy(scanning = false, message = if (it.devices.isEmpty()) "El teléfono no detectó ningún dispositivo Bluetooth LE. Enciende la impresora y revisa que la ubicación del teléfono esté activada." else "") }
        }
    }

    fun labelNote(m: String) { _print.update { it.copy(message = m) } }

    fun labelStopScan() { scanJob?.cancel(); printer.stopScan(); _print.update { it.copy(scanning = false) } }

    fun labelConnect(f: P12Printer.Found) {
        if (_print.value.printing) return
        printer.stopScan()
        _print.update { it.copy(scanning = false, message = "Conectando a ${f.name}…") }
        scope.launch {
            val err = printer.connect(f.address)
            if (err == null) {
                setLabelPrefs(_labelPrefs.value.copy(lastAddress = f.address, lastName = f.name))
                _print.update { it.copy(connectedName = f.name, message = "Conectada: ${f.name}") }
            } else {
                log("Impresora ${f.name}: $err")
                _print.update { it.copy(connectedName = null, message = err) }
            }
        }
    }

    fun labelConnectLast() {
        val p = _labelPrefs.value
        if (p.lastAddress.isEmpty()) return
        labelConnect(P12Printer.Found(p.lastName, p.lastAddress, true))
    }

    fun labelForget() {
        printer.close()
        setLabelPrefs(_labelPrefs.value.copy(lastAddress = "", lastName = ""))
        _print.update { it.copy(connectedName = null, message = "Impresora olvidada.") }
    }

    fun labelPrint() {
        val d = datos() ?: run { _print.update { it.copy(message = "Primero lee la contabilidad.") }; return }
        val p = _labelPrefs.value
        imprimir(
            descripcion = { "Etiqueta enviada a la impresora: " + LabelRenderer.lines(d, p.fields, p.names, p.twoLines).joinToString(" / ") },
            copias = p.copies, okMensaje = "Etiqueta enviada.",
            armar = { listOf(LabelRenderer.render(d, p.lengthMm, p.fields, p.names, estilo(p))) }
        )
    }

    fun imprimirIntercambio(e: Etq, fecha: String, alTerminar: (Boolean) -> Unit) {
        val p = _labelPrefs.value
        imprimir(
            descripcion = { "Etiquetas de intercambio ${e.a} ⇄ ${e.b} enviadas a la impresora." },
            copias = 1, okMensaje = "Se imprimieron las 2 etiquetas.", alTerminar = alTerminar,
            armar = { e.etiquetas(fecha).map { LabelRenderer.renderLines(it, p.lengthMm, estilo(p)) } }
        )
    }

    private fun imprimir(
        descripcion: () -> String, copias: Int, okMensaje: String,
        alTerminar: (Boolean) -> Unit = {}, armar: () -> List<android.graphics.Bitmap>
    ) {
        val p = _labelPrefs.value
        if (_print.value.printing) return
        scope.launch {
            _print.update { it.copy(printing = true, message = "") }
            var salio = false
            try {

                if (!printer.isConnected && p.lastAddress.isNotEmpty()) {
                    _print.update { it.copy(message = "Conectando a ${p.lastName}…") }
                    val e = printer.connect(p.lastAddress)
                    if (e != null) { log("Impresora: $e"); _print.update { it.copy(connectedName = null, message = e) }; return@launch }
                    _print.update { it.copy(connectedName = p.lastName) }
                }
                val err = withContext(Dispatchers.Default) {
                    var fallo: String? = null
                    for (bmp in armar()) {
                        val job = LabelProtocol.buildJob(LabelRenderer.toBlack(bmp), p.dieCut, p.density.takeIf { it in 1..15 }, p.salida)
                        fallo = printer.send(job, copias, p.confirmed)
                        if (fallo != null) break
                    }
                    fallo
                }
                if (err == null) log(descripcion()) else log("Impresora: $err")
                salio = err == null
                _print.update { it.copy(message = err ?: okMensaje) }
            } catch (e: Exception) {
                log("Impresora: error al imprimir (${e.javaClass.simpleName}: ${e.message})")
                _print.update { it.copy(message = "No se pudo imprimir: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                _print.update { it.copy(printing = false) }
                alTerminar(salio)

                delay(1500)
                if (!_print.value.printing) { printer.close(); _print.update { it.copy(connectedName = null) } }
            }
        }
    }

    fun labelDisconnect() {
        if (_print.value.printing) return
        printer.close()
        _print.update { it.copy(connectedName = null, scanning = false) }
    }

    fun cerrar() = printer.close()
}
