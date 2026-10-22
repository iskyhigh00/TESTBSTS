package cl.reabill.monitor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

@Suppress("DEPRECATION", "MissingPermission")
class P12Printer(private val ctx: Context, private val log: (String) -> Unit) {

    data class Found(val name: String, val address: String, val likely: Boolean = false, val rssi: Int = 0)

    companion object {
        val SERVICE: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        val WRITE: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
        val NOTIFY: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val MTU_WANTED = 185
        const val CHUNK_MAX = 96
        const val CHUNK_GAP_MS = 30L
        const val COPY_GAP_MS = 500L
    }

    private val adapter: BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var scanCb: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var mtu = 23
    @Volatile private var connectDone: CompletableDeferred<String?>? = null
    @Volatile private var writeDone: CompletableDeferred<Boolean>? = null

    val bluetoothOn get() = adapter?.isEnabled == true
    val available get() = adapter != null
    val isConnected get() = writeChar != null && gatt != null

    fun startScan(onFound: (Found) -> Unit): Boolean {
        val scanner = adapter?.bluetoothLeScanner ?: return false
        stopScan()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                val name = r.scanRecord?.deviceName ?: r.device.name

                val n = (name ?: "").trim { it <= ' ' || it == '\u0000' }
                onFound(Found(if (n.isEmpty()) "(sin nombre)" else n, r.device.address, LabelProtocol.isP12Name(n), r.rssi))
            }
            override fun onScanFailed(errorCode: Int) { log("Búsqueda Bluetooth falló (código $errorCode).") }
        }
        scanCb = cb
        return try {
            val st = android.bluetooth.le.ScanSettings.Builder().setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(null, st, cb); true } catch (e: Exception) { log("No se pudo buscar: ${e.message}"); false }
    }

    fun stopScan() {
        val cb = scanCb ?: return
        scanCb = null
        try { adapter?.bluetoothLeScanner?.stopScan(cb) } catch (_: Exception) {}
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) { try { g.close() } catch (_: Exception) {}; return }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                if (!g.requestMtu(MTU_WANTED)) g.discoverServices()
            } else {
                writeChar = null
                connectDone?.complete("Conexión perdida o rechazada (estado $status)")
                writeDone?.complete(false)
                try { g.close() } catch (_: Exception) {}
                if (gatt === g) gatt = null
            }
        }
        override fun onMtuChanged(g: BluetoothGatt, m: Int, status: Int) {
            if (g !== gatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = m
            g.discoverServices()
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) { connectDone?.complete("No se pudieron leer los servicios de la impresora (estado $status)"); return }
            val svc = g.getService(SERVICE)
            val w = svc?.getCharacteristic(WRITE)
            if (w == null) {
                val all = g.services.joinToString { it.uuid.toString().substring(4, 8) }
                connectDone?.complete("No se encontró el servicio de impresión FF00/FF02 (servicios: $all)")
                return
            }
            writeChar = w
            svc.getCharacteristic(NOTIFY)?.let { n ->
                g.setCharacteristicNotification(n, true)
                n.getDescriptor(CCCD)?.let { d ->
                    if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    else { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
                }
            }
            connectDone?.complete(null)
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (g !== gatt) return
            writeDone?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            log("Impresora respondió: " + (c.value ?: ByteArray(0)).joinToString(" ") { "%02X".format(it) })
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            log("Impresora respondió: " + value.joinToString(" ") { "%02X".format(it) })
        }
    }

    suspend fun connect(address: String): String? {
        val ad = adapter ?: return "Este teléfono no tiene Bluetooth."
        if (!ad.isEnabled) return "El Bluetooth está apagado."
        close()
        stopScan()
        val done = CompletableDeferred<String?>()
        connectDone = done
        mtu = 23
        val dev: BluetoothDevice = try { ad.getRemoteDevice(address) } catch (e: Exception) { return "Dirección inválida." }
        gatt = try { dev.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE) }
        catch (e: SecurityException) { return "Falta el permiso de Bluetooth: ábrelo en Preferencias → Impresora." }
        val r = withTimeoutOrNull(15_000) { done.await() }
        if (r != null || !isConnected) { val msg = r ?: "Sin respuesta de la impresora en 15 s."; close(); return msg }
        log("Conectado a la impresora (MTU $mtu).")
        return null
    }

    fun close() {
        writeChar = null
        gatt?.let { try { it.disconnect(); it.close() } catch (_: Exception) {} }
        gatt = null
    }

    suspend fun send(data: ByteArray, copies: Int, withResponse: Boolean): String? = try { sendInner(data, copies, withResponse) }
    catch (e: SecurityException) { "Falta el permiso de Bluetooth." }

    private suspend fun sendInner(data: ByteArray, copies: Int, withResponse: Boolean): String? {
        val g = gatt ?: return "No hay impresora conectada."
        val w = writeChar ?: return "No hay impresora conectada."
        val size = minOf(CHUNK_MAX, maxOf(20, mtu - 3))
        val canNoResp = w.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val noResp = !withResponse && canNoResp
        val type = if (noResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val parts = LabelProtocol.chunks(data, size)
        log("Imprimiendo: ${data.size} bytes en ${parts.size} trozos de $size, " + (if (noResp) "sin" else "con") + " confirmación, $copies copia(s).")
        repeat(copies) { copy ->
            for (part in parts) {
                var ok = false
                var tries = 0
                while (!ok && tries < 200) {
                    val wd = CompletableDeferred<Boolean>()
                    writeDone = wd
                    val started = if (Build.VERSION.SDK_INT >= 33) {
                        g.writeCharacteristic(w, part, type) == BluetoothStatusCodes_SUCCESS
                    } else {
                        w.writeType = type
                        w.value = part
                        g.writeCharacteristic(w)
                    }
                    if (!started) { tries++; delay(10); continue }
                    if (noResp) { ok = true } else {
                        val r = withTimeoutOrNull(3_000) { wd.await() }
                        if (r == true) ok = true else return "La impresora no confirmó la escritura."
                    }
                }
                if (!ok) return "El teléfono no pudo enviar los datos (cola Bluetooth ocupada)."
                if (!isConnected) return "Se perdió la conexión durante la impresión."
                delay(CHUNK_GAP_MS)
            }
            if (copy < copies - 1) delay(COPY_GAP_MS)
        }
        return null
    }

    private val BluetoothStatusCodes_SUCCESS = 0
}
