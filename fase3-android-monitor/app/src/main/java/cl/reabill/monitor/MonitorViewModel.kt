package cl.reabill.monitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FlashUi(
    val file: FirmwareFile? = null,
    val error: String = "",
    val running: Boolean = false,
    val progress: Int = 0,
    val step: String = "",
    val done: Boolean = false,
    val ok: Boolean = false,
    val message: String = ""
)

class MonitorViewModel(app: Application) : AndroidViewModel(app) {

    private val usb = UsbSerialManager(app.applicationContext)
    private val store = AccountingStore(app.applicationContext)
    private var mei: ValidatorClient? = null

    private val logFile = java.io.File(app.applicationContext.filesDir, "billmon-registro.log")
    private val logWriter = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status

    private val _log = MutableStateFlow(listOf("Listo. Conecta el billetero por USB-OTG."))
    val log: StateFlow<List<String>> = _log

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _accounting = MutableStateFlow<AccountingData?>(null)
    val accounting: StateFlow<AccountingData?> = _accounting

    private val _history = MutableStateFlow<List<AccountingData>>(emptyList())
    val history: StateFlow<List<AccountingData>> = _history

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _updateVersion = MutableStateFlow<String?>(null)
    val updateVersion: StateFlow<String?> = _updateVersion

    private val _updateProgress = MutableStateFlow(-2)
    val updateProgress: StateFlow<Int> = _updateProgress

    private val _flash = MutableStateFlow(FlashUi())
    val flash: StateFlow<FlashUi> = _flash

    private val libDir = java.io.File(app.applicationContext.filesDir, "bill-sets").apply { mkdirs() }
    private val _library = MutableStateFlow<List<FirmwareFile>>(emptyList())
    val library: StateFlow<List<FirmwareFile>> = _library

    @Volatile private var flashing = false

    @Volatile private var opRunning = false
    private fun beginOp(): Boolean = synchronized(this) {
        if (opRunning || calibrating || flashing) false else { opRunning = true; true }
    }
    private fun endOp() { opRunning = false }
    private fun ocupado() = addLog("Hay otra operación en curso: espera a que termine.")

    private fun tiempos(c: ValidatorClient) = if (c.timingNote.isEmpty()) "" else "  [${c.timingNote}]"

    init {

        _log.value = listOf("Listo. Conecta el billetero por USB-OTG.") + loadSavedLog()
        appendToFile(System.currentTimeMillis(), "──── BILLMON v$APP_VERSION iniciada ────")
        viewModelScope.launch(Dispatchers.IO) { Updater.cleanupDownloads(getApplication()) }
        checkForUpdate()
        refreshLibrary()
    }

    private fun refreshLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            _library.value = (libDir.listFiles() ?: emptyArray()).sortedBy { it.name.lowercase() }
                .mapNotNull { f -> try { FirmwareFile.parse(f.name, f.readBytes()).first } catch (_: Exception) { null } }
        }
    }

    fun selectFromLibrary(f: FirmwareFile) {
        if (!flashing) _flash.value = FlashUi(file = f)
    }

    fun removeFromLibrary(f: FirmwareFile) {
        viewModelScope.launch(Dispatchers.IO) {
            java.io.File(libDir, f.name).delete()
            refreshLibrary()
            addLog("Bill-set quitado de la biblioteca: ${f.name}")
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { Updater.checkLatest() }
            when {
                res.version == null ->
                    addLog("No se pudo consultar actualización (${res.error}).")
                Updater.isNewer(res.version, APP_VERSION) -> {
                    _updateVersion.value = res.version
                    addLog("Hay una versión nueva: v${res.version} (tienes v$APP_VERSION).")
                }
                else -> {
                    _updateVersion.value = null
                    addLog("Estás al día (v$APP_VERSION).")
                }
            }
        }
    }

    fun installUpdate() {
        val version = _updateVersion.value ?: return
        viewModelScope.launch {
            _updateProgress.value = -1
            addLog("Preparando actualización...")
            try {
                val reutilizada = withContext(Dispatchers.IO) {
                    Updater.downloadAndInstall(getApplication(), version) { p ->
                        _updateProgress.value = p
                    }
                }
                addLog(
                    if (reutilizada) "Descarga ya guardada: abriendo el instalador."
                    else "Descarga lista. Confirma la instalación."
                )
            } catch (e: Exception) {
                addLog("No se pudo actualizar: ${e.message}")
            } finally {
                _updateProgress.value = -2
            }
        }
    }

    fun onUsbAttached() {
        if (flashing) return
        if (_demo.value) stopDemo()
        usb.close(); mei = null
        _connected.value = false
        _status.value = DeviceStatus(state = "Desconectado")
        _accounting.value = null
        _history.value = emptyList()
        connect()
    }

    fun refreshConnection() {
        if (flashing || _demo.value) return
        if ((_connected.value || mei != null) && !usb.deviceFound()) onUsbDetached()
    }

    private val _kind = MutableStateFlow("")

    val kind: StateFlow<String> = _kind

    private var connectRetries = 0

    fun connect() { if (_demo.value) return; connectRetries = 0; doConnect() }

    private val mdaStore = MdaStore(app.applicationContext)
    private val _mdaMap = MutableStateFlow<Map<String, String>>(mdaStore.all())

    val mdaMap: StateFlow<Map<String, String>> = _mdaMap
    private val _weekly = MutableStateFlow<WeeklyData?>(mdaStore.weekly())

    val weekly: StateFlow<WeeklyData?> = _weekly
    private var weeklyReal: WeeklyData? = null

    fun setMda(texto: String) {
        val serie = _status.value.serial
        if (serie.isBlank() || serie == "—") { addLog("Sin serie del equipo: no se puede guardar el N° de máquina."); return }
        val n = Mda.norm(texto)
        if (texto.isNotBlank() && n == null) { addLog("N° de máquina inválido: escribe solo números."); return }
        mdaStore.setMda(serie, n)
        _mdaMap.value = mdaStore.all()
        addLog(if (n == null) "N° de máquina quitado." else "N° de máquina guardado: $n (serie $serie).")
    }

    fun importWeekly(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val w = withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() } ?: throw IllegalStateException("no se pudo leer el archivo")
                    if (bytes.size > 20_000_000) throw IllegalStateException("el archivo es demasiado grande")
                    WeeklyImport.parse(String(bytes, Charsets.UTF_8))
                }
                mdaStore.setWeekly(w)
                _weekly.value = w
                addLog("Reporte semanal importado: semana ${w.fecha.ifEmpty { "sin fecha" }}, ${w.datos.size} máquinas.")
            } catch (e: Exception) {
                addLog("No se pudo importar el reporte semanal: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    data class OcrUi(val running: Boolean = false, val candidates: List<String> = emptyList(), val message: String = "", val id: Int = 0)
    private val _ocr = MutableStateFlow(OcrUi())
    val ocr: StateFlow<OcrUi> = _ocr

    fun clearOcr() { _ocr.update { OcrUi(id = it.id) } }

    fun recognizeMda(uri: android.net.Uri) {
        _ocr.update { OcrUi(running = true, message = "Leyendo la foto…", id = it.id) }
        viewModelScope.launch {
            val res = try {
                withContext(Dispatchers.IO) {
                    val img = com.google.mlkit.vision.common.InputImage.fromFilePath(getApplication<Application>(), uri)
                    val rec = com.google.mlkit.vision.text.TextRecognition.getClient(
                        com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        val t = com.google.android.gms.tasks.Tasks.await(rec.process(img))
                        val lineas = t.textBlocks.flatMap { b -> b.lines }.map { l -> l.text to (l.boundingBox?.height() ?: 0) }
                        MdaOcr.candidatos(lineas) to t.text
                    } finally { rec.close() }
                }
            } catch (e: Exception) { null to (e.message ?: e.javaClass.simpleName) }
            try { java.io.File(getApplication<Application>().cacheDir, "fotos").deleteRecursively() } catch (_: Exception) {}
            val (cands, crudo) = res
            if (cands == null) {
                addLog("No se pudo leer la foto: $crudo")
                _ocr.update { OcrUi(message = "No se pudo leer la foto. Escribe el número.", id = it.id + 1) }
            } else if (cands.isEmpty()) {
                addLog("Foto sin números reconocibles. Texto visto: ${crudo.replace('\n', ' ').take(80)}")
                _ocr.update { OcrUi(message = "No encontré un número de 3 a 7 dígitos. Intenta otra foto, más cerca y de frente, o escríbelo.", id = it.id + 1) }
            } else {
                addLog("Número leído en la foto: ${cands.joinToString(", ")}")
                _ocr.update { OcrUi(candidates = cands, id = it.id + 1) }
            }
        }
    }

    private val _demo = MutableStateFlow(false)
    val demo: StateFlow<Boolean> = _demo

    fun startDemo() {
        if (_demo.value || _connected.value || flashing || opRunning) {
            addLog("Para usar el demo, desconecta el billetero real."); return
        }
        _demo.value = true

        weeklyReal = _weekly.value
        _weekly.value = WeeklyData("21/10/2026", mapOf("100500" to 0.984))
        mdaStore.setMda(DemoClient.SERIAL, "100500")
        _mdaMap.value = mdaStore.all()
        mei = DemoClient()
        _kind.value = FirmwareFile.KIND_SC83
        _connected.value = true
        addLog("Modo demo: billetero simulado (no es un equipo real).")
        readAll()
    }

    fun stopDemo() {
        if (!_demo.value) return
        _demo.value = false
        mei = null
        _connected.value = false
        _kind.value = ""
        _status.value = DeviceStatus(state = "Desconectado")
        _accounting.value = null
        _history.value = emptyList()
        store.clear(DemoClient.SERIAL)
        mdaStore.setMda(DemoClient.SERIAL, null)
        _mdaMap.value = mdaStore.all()
        _weekly.value = weeklyReal
        addLog("Modo demo terminado.")
    }

    private fun doConnect() {
        usb.requestPermission { granted ->
            if (!granted) {
                addLog("Permiso USB denegado o sin billetero MEI.")

                val vistos = usb.listAllDevices().filter { !it.startsWith(" ") }
                addLog("USB visto por el teléfono: " + (if (vistos.isEmpty()) "ningún dispositivo" else vistos.joinToString(" | ")))
                if (!usb.deviceFound() && connectRetries < 3 && !flashing) {
                    connectRetries++
                    viewModelScope.launch { delay(1500); if (!_connected.value && !flashing) doConnect() }
                }
                return@requestPermission
            }
            try {
                usb.open()
                val advance = usb.productId() == UsbSerialManager.ADVANCE_PID
                mei = if (advance) AdvanceClient(usb) else MeiClient(usb)
                _kind.value = if (advance) FirmwareFile.KIND_ADVANCE else FirmwareFile.KIND_SC83
                _connected.value = true
                addLog("Conectado al billetero." + if (advance) " (SC Advance)" else "")
                readAll()
            } catch (e: Exception) {
                addLog("Error al conectar: ${e.message}")
            }
        }
    }

    fun disconnect() {
        usb.close()
        mei = null
        _connected.value = false
        _status.value = DeviceStatus(state = "Desconectado")
        _accounting.value = null
        _history.value = emptyList()
        addLog("Desconectado.")
    }

    fun logUsbEvent(msg: String) = addLog(msg)

    fun onUsbDetached() {
        if (flashing || _demo.value) return
        if (_connected.value || mei != null) {
            usb.close(); mei = null
            _connected.value = false
            _status.value = DeviceStatus(state = "Desconectado")
            _accounting.value = null
            _history.value = emptyList()
            addLog("Cable desconectado. Conecta el siguiente billetero y pulsa Conectar.")
        }
    }

    private fun readAll() {
        val client = mei ?: return
        opRunning = true
        viewModelScope.launch {
            _busy.value = true
            try {
                val t0 = System.nanoTime()
                addLog("Leyendo identidad...")
                val id = withContext(Dispatchers.IO) { client.readIdentity() }
                _status.value = id
                if (id.connected) {
                    addLog("Equipo: ${id.model}  serie ${id.serial}" + tiempos(client))
                    _history.value = withContext(Dispatchers.IO) { store.history(id.serial) }
                } else {

                    addLog("USB: " + usb.listAllDevices().joinToString(" | ") { it.trim() })
                    addLog("Sin respuesta a identidad. Crudo: ${id.raw}")
                    return@launch
                }

                addLog("Leyendo variante y firmware...")
                val (app, set, setRaw) = withContext(Dispatchers.IO) { client.readFirmware() }
                _status.value = _status.value.copy(firmwareApp = app, billSet = set)
                addLog("Firmware app: $app   bill-set: $set" + tiempos(client))
                if (setRaw.isNotEmpty()) addLog("Crudo de lectura: $setRaw")

                delay(300)

                if (fetchAccounting(client, saveSnapshot = true))
                    addLog("Lectura completa en ${"%.1f".format((System.nanoTime() - t0) / 1e9)} s")
            } catch (e: Exception) {
                addLog("Lectura interrumpida: ${e.message}")
            } finally {
                _busy.value = false
                endOp()
            }
        }
    }

    fun readFirmware() {
        val client = mei ?: run { addLog("Conecta el equipo primero."); return }
        if (!beginOp()) { ocupado(); return }
        viewModelScope.launch {
            try {
                addLog("Leyendo firmware...")
                val (app, set, setRaw) = withContext(Dispatchers.IO) { client.readFirmware() }
                _status.value = _status.value.copy(firmwareApp = app, billSet = set)
                addLog("Firmware app: $app   bill-set: $set" + tiempos(client))
                if (setRaw.isNotEmpty()) addLog("Crudo de lectura: $setRaw")
            } catch (e: Exception) {
                addLog("Lectura interrumpida: ${e.message}")
            } finally { endOp() }
        }
    }

    fun readAccounting(saveSnapshot: Boolean = true) {
        val client = mei ?: run { addLog("Conecta el equipo primero."); return }
        if (!beginOp()) { ocupado(); return }
        viewModelScope.launch {
            _busy.value = true
            try {
                fetchAccounting(client, saveSnapshot)
            } catch (e: Exception) {
                addLog("Lectura interrumpida: ${e.message}")
            } finally {
                _busy.value = false
                endOp()
            }
        }
    }

    private suspend fun fetchAccounting(client: ValidatorClient, saveSnapshot: Boolean): Boolean {
        addLog("Leyendo contabilidad...")
        val (data, dbg) = withContext(Dispatchers.IO) { client.readAccounting() }
        if (data == null) {
            addLog("Contabilidad: sin respuesta. $dbg")
            return false
        }
        _accounting.value = data
        val serial = _status.value.serial
        var nota = ""
        if (saveSnapshot) {
            val igual = withContext(Dispatchers.IO) { saveIfChanged(serial, data) }
            _history.value = withContext(Dispatchers.IO) { store.history(serial) }
            if (igual) nota = " (igual que la última lectura guardada)"
        }
        addLog("Contabilidad: aceptados ${data.accepted}, rechazados ${data.rejected}, " +
                "tasa ${"%.1f".format(data.rate)}%$nota" + tiempos(client))
        if (dbg.isNotEmpty()) addLog("Contabilidad recuperada tras reiniciar la comunicación:$dbg")

        if (client.lastDetail.isNotEmpty() && client.lastDetail != lastDetail) {
            lastDetail = client.lastDetail
            addLog(client.lastDetail)
        }
        return true
    }

    private fun saveIfChanged(serial: String, data: AccountingData): Boolean {
        val last = store.history(serial).firstOrNull()
        val igual = last != null && last.copy(timestamp = 0) == data.copy(timestamp = 0)
        if (!igual) store.save(serial, data)
        return igual
    }

    fun resetAccounting() {
        val client = mei ?: run { addLog("Conecta el equipo primero."); return }
        if (!client.supportsReset) {
            addLog("Borrar contabilidad no está disponible para este equipo.")
            return
        }
        if (!beginOp()) { ocupado(); return }
        viewModelScope.launch {
          try {
            addLog("Guardando estado previo y borrando contabilidad...")

            val (prev, dbg) = withContext(Dispatchers.IO) { client.readAccounting() }
            if (prev == null) {
                addLog("Contabilidad: sin respuesta al leer el estado previo; no se borró nada. $dbg")
                return@launch
            }
            if (client is AdvanceClient) {
                val sn = _status.value.serial
                if (!client.lastReadComplete) {
                    addLog("Contabilidad incompleta (falta algún bloque o la suma no cuadra); no se borró nada.")
                    return@launch
                }
                if (sn.isBlank() || sn == "—") {
                    addLog("Sin número de serie del equipo para guardar la copia; no se borró nada.")
                    return@launch
                }
                addLog(client.lastDetail)
            }
            val ya = withContext(Dispatchers.IO) { saveIfChanged(_status.value.serial, prev) }
            _history.value = withContext(Dispatchers.IO) { store.history(_status.value.serial) }
            addLog("Estado previo guardado: aceptados ${prev.accepted}, rechazados ${prev.rejected}" +
                    if (ya) " (ya estaba en el historial)" else "")
            val ok = withContext(Dispatchers.IO) { client.resetAccounting() }
            if (client is AdvanceClient && client.lastResetNote.isNotEmpty()) addLog("Detalle del borrado: ${client.lastResetNote}")
            addLog(if (ok) "Contabilidad borrada." else "Reset: sin confirmación.")
            _busy.value = true
            try { fetchAccounting(client, true) } finally { _busy.value = false }
          } catch (e: Exception) {
            addLog("Borrado interrumpido: ${e.message}")
          } finally { endOp() }
        }
    }

    @Volatile private var calibrating = false

    fun startCalibration() {
        val client = mei ?: run { addLog("Conecta el equipo primero."); return }
        if (!beginOp()) { ocupado(); return }
        viewModelScope.launch {
          try {
            addLog("Iniciando modo calibración...")
            val (ok, raw) = withContext(Dispatchers.IO) { client.initiateCalibration() }
            addLog(if (ok) "Modo calibración iniciado." + client.calibrationNote
                   else "Calibración: sin confirmación. Crudo: $raw")

            if (ok && client is AdvanceClient) {
                calibrating = true
                try {
                    val res = withContext(Dispatchers.IO) { client.awaitCalibration() }
                    addLog(when (res) {
                        -1 -> "Calibración: el equipo no terminó en 2 minutos. Reinícialo (sácalo y vuelve a instalarlo en el cashbox)."
                        -2 -> "Calibración: se perdió la comunicación con el equipo. Reinícialo para salir del modo calibración."
                        else -> "Calibración terminada (resultado $res). Reinicia el equipo (sácalo y vuelve a instalarlo en el cashbox) para salir del modo calibración."
                    })
                } finally { calibrating = false }
            }
          } catch (e: Exception) {
            calibrating = false
            addLog("Calibración interrumpida: ${e.message}")
          } finally { endOp() }
        }
    }

    fun restartAdvance() {
        val client = mei as? AdvanceClient ?: run { addLog("Conecta un SC Advance primero."); return }
        if (flashing || calibrating || opRunning || _busy.value) { ocupado(); return }
        flashing = true
        _busy.value = true
        addLog("Reiniciando el equipo (experimental): entrando a modo descarga, sin escribir nada...")
        viewModelScope.launch {
            val err = try { runRestartAdvance(client) } catch (e: Exception) { "Error: ${e.message}" }
            if (err != null) {
                addLog("Reinicio falló: $err El equipo puede haber quedado en modo descarga: sácalo y vuelve a instalarlo en el cashbox.")
                flashing = false
                _busy.value = false
                usb.close(); mei = null
                _connected.value = false
                _status.value = DeviceStatus(state = "Desconectado")
                return@launch
            }
            addLog("Cierre enviado. Reconectando para verificar...")
            flashing = false
            _busy.value = false
            _status.value = DeviceStatus(state = "Desconectado")
            _accounting.value = null
            _history.value = emptyList()
            connect()
            val end = System.currentTimeMillis() + 45000
            while (System.currentTimeMillis() < end &&
                (_status.value.billSet == "—" || _busy.value || !_connected.value)) delay(300)
            addLog(if (_connected.value && _status.value.connected) "Reinicio completo: el equipo volvió en modo normal."
                   else "El equipo no volvió en modo normal: sácalo y vuelve a instalarlo en el cashbox.")
        }
    }

    private suspend fun runRestartAdvance(client: AdvanceClient): String? {
        val io = Dispatchers.IO
        val old = usb.openName()
        val e0 = withContext(io) { client.beginDownload() }
        if (e0 != null) return e0
        usb.close(); mei = null
        _connected.value = false
        if (!awaitReenum(old, 20000)) return "El equipo no volvió en modo descarga."
        delay(800)
        if (!awaitPermission()) return "Permiso USB denegado."
        withContext(io) { usb.open(); usb.resetEndpoints(3) }
        if (usb.productId() != UsbSerialManager.ADVANCE_PID) {
            val pid = usb.productId()
            usb.close()
            return "El equipo abierto no es un SC Advance (PID ${"%04X".format(pid)}). Deja un solo validador conectado."
        }
        val adv = AdvanceClient(usb)
        adv.newSession()
        var e1: String? = null
        for (intento in 0 until 6) {
            e1 = withContext(io) { adv.confirmDownload() }
            if (e1 == null || !e1.contains("no respondió")) break
            delay(1200)
            if (!awaitPermission()) return "Permiso USB denegado."
            withContext(io) { try { usb.close(); usb.open(); usb.resetEndpoints(1); adv.newSession() } catch (_: Exception) {} }
        }
        if (e1 != null) return e1
        addLog("Modo descarga confirmado: enviando el cierre (no se escribe nada).")
        val e2 = withContext(io) { adv.finishDownload() }
        if (e2 != null) return e2
        val old2 = usb.openName()
        usb.close()
        if (!awaitReenum(old2, 45000)) return "El equipo no volvió después de reiniciar."
        return null
    }

    fun resetFlash() { if (!flashing) _flash.value = FlashUi() }

    fun pickFirmware(uri: android.net.Uri) {
        if (flashing) return
        viewModelScope.launch {
            val (file, err) = withContext(Dispatchers.IO) {
                try {
                    val cr = getApplication<Application>().contentResolver
                    val name = cr.query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && i >= 0) c.getString(i) else null
                    } ?: "archivo.bin"
                    val data = cr.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    FirmwareFile.parse(name.replace(Regex("[^A-Za-z0-9._-]"), "_"), data)
                } catch (e: Exception) { null to "No se pudo leer el archivo: ${e.message}" }
            }
            _flash.value = FlashUi(file = file, error = err)
            if (file != null) {
                withContext(Dispatchers.IO) {
                    try { java.io.File(libDir, file.name).writeBytes(file.data) }
                    catch (_: Exception) {}
                }
                refreshLibrary()
                addLog("Bill-set elegido y guardado: ${file.name} (#${file.label}, ${file.data.size} B)")
            }
        }
    }

    private fun step(text: String, progress: Int = -1) =
        _flash.update { it.copy(step = text, progress = if (progress >= 0) progress else it.progress) }

    private suspend fun awaitPermission(): Boolean =
        suspendCancellableCoroutine { c -> usb.requestPermission { if (c.isActive) c.resume(it) } }

    private suspend fun awaitReenum(old: String?, timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        val end = System.currentTimeMillis() + timeoutMs
        var gone = false
        while (System.currentTimeMillis() < end) {
            val d = usb.findDevice()?.deviceName
            if (d == null) gone = true else if (d != old || gone) return@withContext true
            delay(150)
        }
        false
    }

    private fun deviceKind(): String = when (usb.productId()) {
        UsbSerialManager.SC83_PID -> FirmwareFile.KIND_SC83
        UsbSerialManager.ADVANCE_PID -> FirmwareFile.KIND_ADVANCE
        else -> "?"
    }

    private fun kindName(k: String) = when (k) {
        FirmwareFile.KIND_ADVANCE -> "SC Advance"
        FirmwareFile.KIND_SC83 -> "Cashflow SC83"
        else -> "desconocido"
    }

    private suspend fun runFlashAdvance(f: FirmwareFile, normal: Boolean): String? {
        val io = Dispatchers.IO
        if (normal) {
            step("Pasando a modo descarga", 0)
            val old = usb.openName()
            val adv = mei as? AdvanceClient ?: return "El equipo no está conectado como SC Advance."
            val e0 = withContext(io) { adv.beginDownload() }
            if (e0 != null) return e0
            usb.close(); mei = null
            _connected.value = false
            if (!awaitReenum(old, 20000)) return "El equipo no volvió en modo descarga."
            delay(800)
        } else {
            usb.close(); mei = null
            _connected.value = false
        }
        step("Abriendo conexión", 0)
        if (!awaitPermission()) return "Permiso USB denegado."
        withContext(io) { usb.open(); usb.resetEndpoints(3) }

        if (usb.productId() != UsbSerialManager.ADVANCE_PID) {
            usb.close()
            return "El equipo abierto no es un SC Advance (PID ${"%04X".format(usb.productId())}). Deja un solo validador conectado."
        }

        val adv = AdvanceClient(usb)
        adv.newSession()
        step("Iniciando escritura", 0)
        var e1: String? = null
        for (intento in 0 until 6) {
            e1 = withContext(io) { adv.confirmDownload() }
            if (e1 == null || !e1.contains("no respondió")) break
            step("Esperando al equipo (${intento + 1}/6)", 0)
            delay(1200)
            if (!awaitPermission()) return "Permiso USB denegado."
            withContext(io) { try { usb.close(); usb.open(); usb.resetEndpoints(1); adv.newSession() } catch (_: Exception) {} }
        }
        if (e1 != null) return e1

        val total = f.records.size
        for ((i, rec) in f.records.withIndex()) {
            step("Escribiendo registro ${i + 1} de $total", (i + 1) * 95 / total)
            val e = withContext(io) { adv.writeRecord(rec, i + 1) }
            if (e != null) return e
        }
        addLog("Escritura: $total registros enviados.")
        step("Cerrando escritura", 96)
        val e2 = withContext(io) { adv.finishDownload() }
        if (e2 != null) return e2

        step("Reiniciando equipo", 98)
        val old = usb.openName()
        usb.close()
        if (!awaitReenum(old, 45000)) return "El equipo no volvió después de reiniciar."
        return null
    }

    private suspend fun runFlash(f: FirmwareFile, normal: Boolean): String? {
        if (f.kind == FirmwareFile.KIND_ADVANCE) return runFlashAdvance(f, normal)
        val io = Dispatchers.IO
        if (normal) {
            step("Pasando a modo descarga", 0)
            val old = usb.openName()
            withContext(io) {
                usb.resetEndpoints(1)
                usb.transact(FirmwareFlasher.enterDownload, UsbSerialManager.ANY_STX, 1500, 1500)
            }
            usb.close(); mei = null
            _connected.value = false
            if (!awaitReenum(old, 20000)) return "El equipo no volvió en modo descarga."
            delay(1500)
        } else {
            usb.close(); mei = null
            _connected.value = false
        }
        step("Abriendo conexión", 0)
        if (!awaitPermission()) return "Permiso USB denegado."

        withContext(io) { usb.open(); usb.resetEndpoints(3) }

        step("Iniciando escritura", 0)

        var e1: String? = null
        for (intento in 0 until 6) {
            e1 = withContext(io) { FirmwareFlasher.sendAcked(usb, FirmwareFlasher.enterDownload, "Modo descarga") }
            if (e1 == null || !e1.contains("no respondió")) break
            step("Esperando al equipo (${intento + 1}/6)", 0)
            delay(1200)
            if (!awaitPermission()) return "Permiso USB denegado."
            withContext(io) { try { usb.close(); usb.open(); usb.resetEndpoints(1) } catch (_: Exception) {} }
        }
        if (e1 != null) return e1
        e1 = withContext(io) { FirmwareFlasher.sendAcked(usb, FirmwareFlasher.start, "Inicio") }
        if (e1 != null) return e1

        val B = FirmwareFile.BLOCK
        for (i in 0 until FirmwareFile.BLOCKS) {
            step("Escribiendo bloque ${i + 1} de ${FirmwareFile.BLOCKS}", (i + 1) * 95 / FirmwareFile.BLOCKS)
            val payload = f.data.copyOfRange(i * B, minOf(f.data.size, (i + 1) * B))
            val e = withContext(io) {
                FirmwareFlasher.sendAcked(usb, FirmwareFlasher.blockFrame(i, payload), "Bloque ${i + 1}")
            }
            if (e != null) return e
        }
        addLog("Escritura: 64 bloques enviados.")
        step("Cerrando escritura", 96)
        val e2 = withContext(io) {
            FirmwareFlasher.sendAcked(usb, FirmwareFlasher.finish, "Cierre")
                ?: FirmwareFlasher.sendAcked(usb, FirmwareFlasher.reboot, "Reinicio")
        }
        if (e2 != null) return e2

        step("Reiniciando equipo", 98)
        val old = usb.openName()
        usb.close()
        if (!awaitReenum(old, 45000)) return "El equipo no volvió después de reiniciar."
        return null
    }

    private fun runFlashDemo(f: FirmwareFile) {
        flashing = true
        _busy.value = true
        _flash.value = _flash.value.copy(running = true, done = false, ok = false, message = "", progress = 0, step = "Preparando (demo)")
        addLog("Demo: escritura simulada de bill-set #${f.label}.")
        viewModelScope.launch {
            for (p in 5..95 step 5) { step("Escribiendo (demo)", p); delay(120) }
            _status.value = _status.value.copy(billSet = f.label)
            flashing = false
            _busy.value = false
            _flash.update { it.copy(running = false, done = true, progress = 100, ok = true, message = "Listo (demo). Bill-set simulado: ${f.label}") }
            addLog("Demo: bill-set simulado ahora ${f.label}.")
        }
    }

    fun startFlash() {
        val f = _flash.value.file ?: return
        if (flashing || _flash.value.running) return
        if (calibrating || opRunning) { ocupado(); return }
        if (_busy.value) { addLog("Espera a que termine la lectura en curso antes de escribir."); return }
        if (_demo.value) { runFlashDemo(f); return }

        if (!usb.deviceFound()) { addLog("Escritura: conecta el equipo primero."); return }
        val kind = deviceKind()

        val msg = when {
            f.kind == FirmwareFile.KIND_ADVANCE && kind != FirmwareFile.KIND_ADVANCE ->
                "El archivo es de un SC Advance y el equipo conectado no es un Advance (${kindName(kind)}, PID ${"%04X".format(usb.productId())}); no se escribe nada."
            f.kind == FirmwareFile.KIND_SC83 && kind == FirmwareFile.KIND_ADVANCE ->
                "El archivo es de un Cashflow SC83 y el equipo conectado es un SC Advance; no se escribe nada."
            else -> null
        }
        if (msg != null) {
            addLog(msg)
            _flash.update { it.copy(error = msg) }
            return
        }
        val normal = _status.value.connected && mei != null
        flashing = true
        _busy.value = true
        _flash.value = _flash.value.copy(running = true, done = false, ok = false, message = "", progress = 0, step = "Preparando")
        addLog("Escritura de bill-set #${f.label} iniciada (${if (normal) "modo normal" else "modo descarga"}).")
        viewModelScope.launch {
            val err = try { runFlash(f, normal) } catch (e: Exception) { "Error: ${e.message}" }
            if (err != null) {
                addLog("Escritura falló: $err")
                flashing = false
                _busy.value = false
                usb.close(); mei = null
                _connected.value = false
                _status.value = DeviceStatus(state = "Desconectado")
                _flash.update {
                    it.copy(running = false, done = true, ok = false,
                        message = "$err\nEl equipo puede haber quedado en modo descarga: vuelve a elegir el archivo y escribir, o desenchúfalo y reconecta.")
                }
                return@launch
            }
            addLog("Escritura terminada. Reconectando para verificar...")
            step("Verificando", 99)
            flashing = false
            _busy.value = false
            _status.value = DeviceStatus(state = "Desconectado")
            _accounting.value = null
            _history.value = emptyList()
            connect()

            val end = System.currentTimeMillis() + 45000
            while (System.currentTimeMillis() < end &&
                (_status.value.billSet == "—" || _busy.value || !_connected.value)) delay(300)
            val ahora = _status.value.billSet
            val coincide = ahora.contains(f.label)
            addLog("Bill-set tras escribir: $ahora (esperado #${f.label})")
            _flash.update {
                it.copy(running = false, done = true, progress = 100, ok = true,
                    message = if (ahora == "—") "Escritura terminada, pero no pude leer el bill-set. Reconecta el equipo."
                    else if (coincide) "Listo. Bill-set leído: $ahora"
                    else "Escritura terminada. Bill-set leído: $ahora (archivo #${f.label}). Compáralo con STS.")
            }
        }
    }

    private fun vaciarRegistro() {
        try { logWriter.submit {}.get(3, java.util.concurrent.TimeUnit.SECONDS) } catch (_: Exception) {}
    }

    data class PrintUi(
        val devices: List<P12Printer.Found> = emptyList(),
        val scanning: Boolean = false,
        val connectedName: String? = null,
        val printing: Boolean = false,
        val message: String = ""
    )
    data class LabelPrefs(
        val lengthMm: Int = 50, val dieCut: Boolean = false, val rotate: Boolean = false,
        val density: Int = 0, val copies: Int = 1, val confirmed: Boolean = false,
        val fields: Set<String> = LabelFields.DEFAULT, val align: Int = 0, val compact: Boolean = true,
        val scale: Int = 100, val vpos: Int = 1, val names: Map<String, String> = emptyMap(),
        val lastAddress: String = "", val lastName: String = ""
    )

    private val prefs = app.applicationContext.getSharedPreferences("etiquetas", android.content.Context.MODE_PRIVATE)
    private val printer = P12Printer(app.applicationContext) { addLog(it) }
    private val _print = MutableStateFlow(PrintUi())
    val print: StateFlow<PrintUi> = _print
    private val _labelPrefs = MutableStateFlow(LabelPrefs(
        prefs.getInt("mm", 50), prefs.getBoolean("die", false), prefs.getBoolean("rot", false),
        prefs.getInt("dens", 0), 1, prefs.getBoolean("conf", false),
        LabelFields.parse(prefs.getString("campos", null)), prefs.getInt("align", 0), prefs.getBoolean("compact", true),
        prefs.getInt("scale", 100), prefs.getInt("vpos", 1), LabelFields.parseNames(prefs.getString("nombres", null)),
        prefs.getString("addr", "") ?: "", prefs.getString("pname", "") ?: ""
    ))
    val labelPrefs: StateFlow<LabelPrefs> = _labelPrefs

    fun setLabelPrefs(p: LabelPrefs) {
        _labelPrefs.value = p
        prefs.edit().putInt("mm", p.lengthMm).putBoolean("die", p.dieCut).putBoolean("rot", p.rotate)
            .putInt("dens", p.density).putBoolean("conf", p.confirmed).putString("campos", LabelFields.encode(p.fields)).putInt("align", p.align).putBoolean("compact", p.compact)
            .putInt("scale", p.scale).putInt("vpos", p.vpos).putString("nombres", LabelFields.encodeNames(p.names))
            .putString("addr", p.lastAddress).putString("pname", p.lastName).apply()
    }

    fun labelData(): LabelData? {

        val (a, previa) = LabelPick.reading(_accounting.value, _history.value) ?: return null
        val s = _status.value
        val mda = _mdaMap.value[s.serial]
        val semanal = _weekly.value?.pct(mda)?.times(100.0)
        return LabelData(a.timestamp, s.model, s.serial, s.firmwareApp, s.billSet, mda, a.rate, semanal, previa)
    }

    fun labelStyle(p: LabelPrefs) = LabelStyle(p.align, p.vpos, p.compact, p.scale, p.rotate)

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
        scanJob = viewModelScope.launch {
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
        viewModelScope.launch {
            val err = printer.connect(f.address)
            if (err == null) {
                setLabelPrefs(_labelPrefs.value.copy(lastAddress = f.address, lastName = f.name))
                _print.update { it.copy(connectedName = f.name, message = "Conectada: ${f.name}") }
            } else {
                addLog("Impresora ${f.name}: $err")
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
        val d = labelData() ?: run { _print.update { it.copy(message = "Primero lee la contabilidad.") }; return }
        val p = _labelPrefs.value
        if (_print.value.printing) return
        viewModelScope.launch {
            _print.update { it.copy(printing = true, message = "") }
            try {

                if (!printer.isConnected && p.lastAddress.isNotEmpty()) {
                    _print.update { it.copy(message = "Conectando a ${p.lastName}…") }
                    val e = printer.connect(p.lastAddress)
                    if (e != null) { addLog("Impresora: $e"); _print.update { it.copy(connectedName = null, message = e) }; return@launch }
                    _print.update { it.copy(connectedName = p.lastName) }
                }
                val err = withContext(Dispatchers.Default) {
                    val bmp = LabelRenderer.render(d, p.lengthMm, p.fields, p.names, labelStyle(p))
                    val job = LabelProtocol.buildJob(LabelRenderer.toBlack(bmp), p.dieCut, p.density.takeIf { it in 1..15 })
                    printer.send(job, p.copies, p.confirmed)
                }
                if (err == null) addLog("Etiqueta enviada a la impresora: " + LabelRenderer.lines(d, p.fields, p.names).joinToString(" / ")) else addLog("Impresora: $err")
                _print.update { it.copy(message = err ?: "Etiqueta enviada.") }
            } catch (e: Exception) {
                addLog("Impresora: error al imprimir (${e.javaClass.simpleName}: ${e.message})")
                _print.update { it.copy(message = "No se pudo imprimir: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                _print.update { it.copy(printing = false) }
            }
        }
    }

    fun exportBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val (texto, resumen) = withContext(Dispatchers.IO) {
                    vaciarRegistro()
                    val hist = store.serials().associateWith { store.history(it) }
                    val reg = try { if (logFile.exists()) logFile.readLines() else emptyList() } catch (_: Exception) { emptyList() }
                    Backup.build(hist, reg, APP_VERSION, System.currentTimeMillis(), mdaStore.all().filterKeys { !DemoClient.isDemoSerial(it) }) to
                        "${hist.size} equipos, ${hist.values.sumOf { it.size }} lecturas, ${minOf(reg.size, Backup.MAX_LINEAS)} líneas de registro"
                }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?.use { it.write(texto.toByteArray(Charsets.UTF_8)) }
                        ?: throw IllegalStateException("no se pudo abrir el archivo de destino")
                }
                addLog("Respaldo exportado: $resumen.")
            } catch (e: Exception) {
                addLog("Exportación fallida: ${e.message}")
            }
        }
    }

    fun importBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val texto = withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() } ?: throw IllegalStateException("no se pudo leer el archivo")
                    if (bytes.size > 20_000_000) throw IllegalStateException("el archivo es demasiado grande para ser un respaldo")
                    String(bytes, Charsets.UTF_8)
                }
                val c = try { Backup.parse(texto) }
                        catch (e: org.json.JSONException) { throw IllegalStateException("el archivo no es un JSON válido") }
                val (nuevas, lineas) = withContext(Dispatchers.IO) {
                    var nuevas = 0
                    for ((serie, lista) in c.historial) nuevas += store.mergeIn(serie, lista)
                    mdaStore.mergeIn(c.mda.filterKeys { !DemoClient.isDemoSerial(it) })
                    vaciarRegistro()
                    val actual = try { if (logFile.exists()) logFile.readLines() else emptyList() } catch (_: Exception) { emptyList() }
                    val ya = actual.toHashSet()
                    val lineasNuevas = c.registro.distinct().count { it !in ya }
                    if (lineasNuevas > 0) {
                        val fusion = Backup.mergeLog(actual, c.registro)
                        logWriter.submit { logFile.writeText(fusion.joinToString("\n") + "\n") }.get(10, java.util.concurrent.TimeUnit.SECONDS)
                    }
                    nuevas to lineasNuevas
                }
                _history.value = withContext(Dispatchers.IO) { store.history(_status.value.serial) }
                _mdaMap.value = mdaStore.all()
                addLog("Respaldo importado: ${c.historial.size} equipos en el archivo, $nuevas lecturas nuevas, $lineas líneas de registro nuevas.")
            } catch (e: Exception) {
                addLog("No se pudo importar el respaldo: ${e.message}")
            }
        }
    }

    private val sessionLines = ArrayList<String>()
    private var lastDetail = ""

    fun fullLog(): String = try {
        logWriter.submit<String> {
            if (!logFile.exists()) "" else logFile.readLines().joinToString("\n") { l ->
                val tab = l.indexOf('\t')
                if (tab < 0) l else l.substring(0, tab) + "  " + l.substring(tab + 1)
            }
        }.get()
    } catch (_: Exception) { sessionLog() }

    fun fullLogAsync(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(withContext(Dispatchers.IO) { fullLog() }) }
    }

    fun clearLog() {
        synchronized(sessionLines) { sessionLines.clear() }
        try { logWriter.execute { try { logFile.writeText("") } catch (_: Exception) {} } } catch (_: Exception) {}
        _log.value = listOf("${android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())}  Registro limpiado.")
    }

    fun sessionLog(): String = synchronized(sessionLines) { sessionLines.joinToString("\n") }

    private fun addLog(msg: String) {
        val now = System.currentTimeMillis()
        val ts = android.text.format.DateFormat.format("HH:mm:ss", now)
        synchronized(sessionLines) { sessionLines.add("$ts  $msg") }
        _log.value = (listOf("$ts  $msg") + _log.value).take(MAX_UI_LINES)
        appendToFile(now, msg)
    }

    private fun appendToFile(now: Long, msg: String) {
        val stamp = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", now)
        val line = "$stamp\t${msg.replace('\n', ' ')}\n"
        try {
            logWriter.execute { try { logFile.appendText(line) } catch (_: Exception) {} }
        } catch (_: Exception) {  }
    }

    private fun loadSavedLog(): List<String> = try {
        if (!logFile.exists()) emptyList() else {
            val lines = logFile.readLines()
            if (lines.size > MAX_FILE_LINES)
                logFile.writeText(lines.takeLast(MAX_FILE_LINES).joinToString("\n") + "\n")
            lines.takeLast(MAX_UI_LINES - 1).asReversed().map { l ->
                val tab = l.indexOf('\t')
                if (tab < 19) l else {
                    val d = l.substring(0, tab)
                    "${d.substring(8, 10)}-${d.substring(5, 7)} ${d.substring(11)}  ${l.substring(tab + 1)}"
                }
            }
        }
    } catch (_: Exception) { emptyList() }

    override fun onCleared() {
        disconnect()
        printer.close()
        logWriter.shutdown()
        super.onCleared()
    }

    private companion object {
        const val MAX_UI_LINES = 60
        const val MAX_FILE_LINES = 2000
    }
}
