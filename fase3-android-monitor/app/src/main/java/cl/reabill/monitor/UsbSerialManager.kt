package cl.reabill.monitor

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

class UsbSerialManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var connection: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private var dev: UsbDevice? = null

    val isOpen: Boolean get() = connection != null

    @Volatile var lastOut: Int = 0
        private set

    companion object {
        private const val ACTION_USB_PERMISSION = "cl.reabill.monitor.USB_PERMISSION"
        private const val MEI_VID = 0x0BED

        const val ANY_STX = -2

        const val SC83_PID = 0x0100

        const val ADVANCE_PID = 0x0500
    }

    fun productId(): Int = (if (connection != null) dev else findDevice())?.productId ?: 0

    fun openName(): String? = dev?.deviceName

    fun listAllDevices(): List<String> {
        val out = ArrayList<String>()
        for (d in usbManager.deviceList.values) {
            out.add("VID=%04X PID=%04X ifaces=%d".format(d.vendorId, d.productId, d.interfaceCount))
            for (i in 0 until d.interfaceCount) {
                val intf = d.getInterface(i)
                out.add("  if#%d cls=%02X sub=%02X proto=%02X eps=%d".format(
                    i, intf.interfaceClass, intf.interfaceSubclass,
                    intf.interfaceProtocol, intf.endpointCount))
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    val dir = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                    out.add("    ep=%02X type=%d %s".format(ep.address, ep.type, dir))
                }
            }
        }
        return out
    }

    fun findDevice(): UsbDevice? {
        val devs = usbManager.deviceList.values

        devs.firstOrNull { it.vendorId == MEI_VID && bulkInterface(it) != null }?.let { return it }

        return devs.firstOrNull { d -> bulkInterface(d)?.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC }
    }

    fun deviceFound(): Boolean = findDevice() != null

    private fun bulkInterface(d: UsbDevice): UsbInterface? {
        for (i in 0 until d.interfaceCount) {
            val intf = d.getInterface(i)
            var hasIn = false; var hasOut = false
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) hasIn = true else hasOut = true
                }
            }
            if (hasIn && hasOut) return intf
        }
        return null
    }

    fun requestPermission(onResult: (Boolean) -> Unit) {
        val device = findDevice() ?: run { onResult(false); return }
        if (usbManager.hasPermission(device)) { onResult(true); return }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == ACTION_USB_PERMISSION) {
                    context.unregisterReceiver(this)
                    onResult(usbManager.hasPermission(device))
                }
            }
        }
        try {
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val pi = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION),
                Context.RECEIVER_NOT_EXPORTED)
            usbManager.requestPermission(device, pi)
        } catch (e: Exception) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            onResult(false)
        }
    }

    fun open() {
        close()
        val device = findDevice() ?: throw IllegalStateException("No hay billetero MEI conectado.")
        val intf = bulkInterface(device) ?: throw IllegalStateException("El equipo no expone endpoints bulk.")

        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        for (e in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(e)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
            }
        }
        if (inEp == null || outEp == null) throw IllegalStateException("Faltan endpoints bulk IN/OUT.")

        val conn = usbManager.openDevice(device)
            ?: throw IllegalStateException("No se pudo abrir el dispositivo (¿permiso?).")
        if (!conn.claimInterface(intf, true)) {
            conn.close()
            throw IllegalStateException("No pude reclamar la interfaz USB.")
        }
        connection = conn; iface = intf; epIn = inEp; epOut = outEp; dev = device
    }

    fun close() {
        try { iface?.let { connection?.releaseInterface(it) } } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        connection = null; iface = null; epIn = null; epOut = null
    }

    fun transact(message: ByteArray, expectCmd: Int = -1, expectSub: Int = -1,
                 firstTimeoutMs: Int = 3000, totalMs: Int = 6000): ByteArray {
        val conn = connection ?: throw IllegalStateException("Puerto cerrado.")
        val out = epOut ?: throw IllegalStateException("Sin endpoint OUT.")
        val inp = epIn ?: throw IllegalStateException("Sin endpoint IN.")

        lastOut = -99
        if (message.isNotEmpty()) {

            lastOut = conn.bulkTransfer(out, message, message.size, if (message.size > 64) 3000 else 500)
        }

        val acc = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16384)
        val deadline = System.currentTimeMillis() + totalMs
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            val timeout = if (acc.size() == 0) minOf(remaining, firstTimeoutMs) else minOf(remaining, 500)
            val n = conn.bulkTransfer(inp, buf, buf.size, timeout)
            if (n <= 0) break
            acc.write(buf, 0, n)
            if (frameComplete(acc.toByteArray(), expectCmd, expectSub)) break
        }
        return acc.toByteArray()
    }

    fun transactLe(message: ByteArray, firstTimeoutMs: Int = 3000, totalMs: Int = 6000,
                   accept: (ByteArray) -> Boolean): ByteArray {
        val conn = connection ?: throw IllegalStateException("Puerto cerrado.")
        val out = epOut ?: throw IllegalStateException("Sin endpoint OUT.")
        val inp = epIn ?: throw IllegalStateException("Sin endpoint IN.")

        lastOut = -99
        if (message.isNotEmpty()) {
            lastOut = conn.bulkTransfer(out, message, message.size, if (message.size > 64) 3000 else 500)
        }

        val acc = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16384)
        val deadline = System.currentTimeMillis() + totalMs
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            val timeout = if (acc.size() == 0) minOf(remaining, firstTimeoutMs) else minOf(remaining, 500)
            val n = conn.bulkTransfer(inp, buf, buf.size, timeout)
            if (n <= 0) break
            acc.write(buf, 0, n)
            val data = acc.toByteArray()
            var pos = 0
            while (data.size - pos >= 6) {
                val len = (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)
                if (len < 6 || len > 8192) { pos++; continue }
                if (data.size - pos < len) break
                val frame = data.copyOfRange(pos, pos + len)
                if (accept(frame)) return frame
                pos += len
            }
        }
        return ByteArray(0)
    }

    fun resetEndpoints(level: Int): String {
        val conn = connection
        val intf = iface
        if (conn == null || intf == null) return "sin conexión"
        return try {
            when (level) {
                1 -> "setInterface=${conn.setInterface(intf)}"
                2 -> {
                    conn.releaseInterface(intf)
                    "reclamar=${conn.claimInterface(intf, true)}"
                }
                3 -> {
                    conn.releaseInterface(intf)
                    val cfg = dev?.getConfiguration(0)
                    val ok = if (cfg != null) conn.setConfiguration(cfg) else false
                    "setConfiguration=$ok reclamar=${conn.claimInterface(intf, true)}"
                }
                4 -> { close(); open(); "reabierto" }
                else -> "nivel $level desconocido"
            }
        } catch (e: Exception) {
            "error ${e.message}"
        }
    }

    private fun frameComplete(b: ByteArray, cmd: Int, sub: Int): Boolean {
        if (cmd == ANY_STX) {
            if (b.size < 3 || (b[0].toInt() and 0xFF) != 0x02) return false
            val len = ((b[1].toInt() and 0xFF) shl 8) or (b[2].toInt() and 0xFF)
            return len >= 6 && b.size >= len
        }
        var i = 0
        while (i + 7 <= b.size) {
            if ((b[i].toInt() and 0xFF) == 0x02 &&
                (b[i + 3].toInt() and 0xFF) == 0xFF &&
                (b[i + 4].toInt() and 0xFF) == 0xAA &&
                (cmd < 0 || ((b[i + 5].toInt() and 0xFF) == cmd &&
                             (b[i + 6].toInt() and 0xFF) == sub))) {
                val len = ((b[i + 1].toInt() and 0xFF) shl 8) or (b[i + 2].toInt() and 0xFF)
                if (len >= 9 && b.size >= i + len) return true
            }
            i++
        }
        return false
    }
}
