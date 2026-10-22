package cl.reabill.monitor

data class DeviceStatus(
    val connected: Boolean = false,
    val state: String = "Desconectado",
    val model: String = "—",
    val serial: String = "—",
    val manufacturer: String = "—",
    val prodDate: String = "—",
    val firmwareApp: String = "—",
    val billSet: String = "—",
    val raw: String = ""
)

class MeiClient(private val usb: UsbSerialManager) : ValidatorClient {

    override var lastDetail: String = ""
        private set
    override val supportsReset: Boolean = true
    override val calibrationNote: String = " Pasa el cupón de calibración."

    private fun crc16(data: ByteArray, from: Int, to: Int): Int {
        var crc = 0
        for (i in from until to) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    private fun frame(cmd: Int, sub: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val len = 9 + payload.size
        val f = ByteArray(len)
        f[0] = 0x02
        f[1] = ((len ushr 8) and 0xFF).toByte()
        f[2] = (len and 0xFF).toByte()
        f[3] = 0xFF.toByte()
        f[4] = 0xAA.toByte()
        f[5] = cmd.toByte()
        f[6] = sub.toByte()
        payload.copyInto(f, 7)
        val crc = crc16(f, 0, len - 2)
        f[len - 2] = ((crc ushr 8) and 0xFF).toByte()
        f[len - 1] = (crc and 0xFF).toByte()
        return f
    }

    private fun send(cmd: Int, sub: Int, payload: ByteArray = ByteArray(0)): Pair<ByteArray, String> {

        usb.resetEndpoints(1)
        Thread.sleep(30)
        val resp = usb.transact(frame(cmd, sub, payload), cmd, sub)
        val raw = resp.joinToString(" ") { "%02X".format(it) }
        return resync(resp, cmd, sub) to raw
    }

    private fun resync(resp: ByteArray, cmd: Int, sub: Int): ByteArray {

        var idx = -1
        var i = 0
        while (i + 7 <= resp.size) {
            if ((resp[i].toInt() and 0xFF) == 0x02 &&
                (resp[i + 3].toInt() and 0xFF) == 0xFF &&
                (resp[i + 4].toInt() and 0xFF) == 0xAA &&
                (resp[i + 5].toInt() and 0xFF) == cmd &&
                (resp[i + 6].toInt() and 0xFF) == sub) { idx = i }
            i++
        }
        if (idx >= 0) {
            val len = ((resp[idx + 1].toInt() and 0xFF) shl 8) or (resp[idx + 2].toInt() and 0xFF)
            val end = (idx + len - 2).coerceIn(idx + 7, resp.size)
            return resp.copyOfRange(idx + 7, end)
        }

        var p = -1
        var j = 0
        while (j + 4 <= resp.size) {
            if ((resp[j].toInt() and 0xFF) == 0xFF &&
                (resp[j + 1].toInt() and 0xFF) == 0xAA &&
                (resp[j + 2].toInt() and 0xFF) == cmd &&
                (resp[j + 3].toInt() and 0xFF) == sub) { p = j }
            j++
        }
        if (p >= 0) return resp.copyOfRange(p + 4, resp.size)
        return ByteArray(0)
    }

    private fun asciiTokens(b: ByteArray): List<String> {
        val toks = ArrayList<String>(); val sb = StringBuilder()
        for (x in b) {
            val c = x.toInt() and 0xFF
            if (c in 0x20..0x7E) sb.append(c.toChar())
            else { if (sb.length >= 2) toks.add(sb.toString().trim()); sb.setLength(0) }
        }
        if (sb.length >= 2) toks.add(sb.toString().trim())
        return toks
    }

    private fun utf16le(b: ByteArray, start: Int = 0): String {
        val sb = StringBuilder()
        var i = start
        while (i + 1 <= b.size - 1 || i == b.size - 1) {
            if (i == b.size - 1) {
                val lo = b[i].toInt() and 0xFF
                if (lo in 0x20..0x7E) sb.append(lo.toChar())
                break
            }
            val code = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
            if (code in 0x20..0x7E) sb.append(code.toChar())
            i += 2
        }
        return sb.toString().trim()
    }

    override fun readIdentity(): DeviceStatus {
        val (body, raw) = send(0x02, 0x01)
        if (body.isEmpty()) return DeviceStatus(state = "Sin respuesta", raw = raw)
        val t = asciiTokens(body)
        return DeviceStatus(
            connected = true,
            state = "Conectado",
            model = t.getOrElse(0) { "—" },
            manufacturer = t.getOrElse(1) { "—" },
            serial = t.getOrElse(2) { "—" },
            prodDate = t.getOrElse(3) { "—" },
            raw = raw
        )
    }

    private var familiaOk = 0

    private fun readItem(key: ByteArray, maxTries: Int = 4): Pair<String, String> {
        val notas = StringBuilder()
        var anterior = ""
        var raw0Eq = false
        repeat(maxTries) { attempt ->

            if (attempt >= 2 && anterior.isNotEmpty() && raw0Eq) return "—" to notas.toString().trim()
            if (attempt > 0) {

                val nivel = attempt + 1
                notas.append(" | R$nivel:").append(usb.resetEndpoints(nivel))
                Thread.sleep(300)
            }
            val payload = ByteArray(12)
            key.copyInto(payload, 2)
            val (body, raw) = send(0x04, 0x02, payload)
            val v = if (body.isEmpty()) "" else utf16le(body, 1)
            if (v.isNotEmpty()) return v to raw
            raw0Eq = raw.isNotEmpty() && raw == anterior && raw.contains("FF AA")
            anterior = raw
            notas.append(" | intento$attempt:").append(resumen(raw))
        }
        return "—" to notas.toString().trim()
    }

    private fun resumen(raw: String): String {
        val bytes = if (raw.isEmpty()) 0 else (raw.length + 1) / 3
        val cab = if (raw.contains("FF AA")) "con cabecera" else "sin cabecera"
        return "${bytes}B $cab ${raw.take(120)}".trim()
    }

    override fun readFirmware(): Triple<String, String, String> {
        Thread.sleep(250)

        val bases = listOf(0xEB to 0x32, 0xE2 to 0x59, 0xE6 to 0x0F)
        val familias = bases.sortedByDescending { it.first == familiaOk }
        fun item(delta: Int): Pair<String, String> {
            var ultimo = "—" to ""
            for ((n, fam) in familias.withIndex()) {
                val key = byteArrayOf(fam.first.toByte(), (fam.second + delta).toByte(), 0x00, 0x12, 0x00, 0x01, 0x00, 0x03)
                val r = readItem(key, if (n < familias.size - 1) 2 else 4)
                if (r.first != "—") { familiaOk = fam.first; return r }
                ultimo = r
                Thread.sleep(150)
            }
            return ultimo
        }
        val (set, setRaw) = item(9)
        Thread.sleep(250)
        val (app, appRaw) = item(0)

        val fallos = buildString {
            if (set == "—") append("variante: ").append(setRaw.ifEmpty { "(vacío)" })
            if (app == "—") {
                if (isNotEmpty()) append("  ||  ")
                append("firmware: ").append(appRaw.ifEmpty { "(vacío)" })
            }

            if (isNotEmpty()) {
                if (set != "—") append("  ||  crudo variante OK: ").append(setRaw.take(120))
                if (app != "—") append("  ||  crudo firmware OK: ").append(appRaw.take(120))
            }
        }
        return Triple(app, set, fallos)
    }

    override fun initiateCalibration(): Pair<Boolean, String> {

        val (body, raw) = send(0x03, 0x01)
        val ok = raw.isNotEmpty()
        return ok to raw
    }

    override fun readAccounting(): Pair<AccountingData?, String> {

        val notas = StringBuilder()
        var body = ByteArray(0)
        for (intento in 0..3) {
            val nivel = intento + 1
            if (intento > 0) {
                notas.append(" | R$nivel:").append(usb.resetEndpoints(nivel))
                Thread.sleep(300)
            }
            val r = send(0x01, 0x07)
            body = r.first
            if (body.size >= 1252) {
                if (intento > 0) notas.append(" | recuperada con R$nivel")
                break
            }
            notas.append(" | intento$intento:").append(resumen(r.second))
        }
        if (body.size < 1252) return null to notas.toString().trim()
        fun w(o: Int) = ((body[o].toInt() and 0xFF) shl 8) or (body[o + 1].toInt() and 0xFF)
        fun sumArr(start: Int) = (0 until 40).sumOf { w(start + it * 2) }
        val inserted = sumArr(24)
        val accepted = sumArr(824)
        val rejected = (inserted - accepted).coerceAtLeast(0)
        val stacked = w(1228); val deposited = w(1230)
        val bcRej = w(1236); val bcAcc = w(1250); val bcIns = bcRej + bcAcc
        val rate = if (inserted > 0) accepted * 100.0 / inserted else 0.0
        lastDetail = "Detalle contabilidad: ins=${(0 until 40).map { w(24 + it * 2) }} " +
            "acc=${(0 until 40).map { w(824 + it * 2) }} cola=${(1200..1250 step 2).map { w(it) }}"
        return AccountingData(inserted, accepted, rejected, rate, stacked, deposited,
            bcIns, bcAcc, bcRej) to notas.toString().trim()
    }

    override fun resetAccounting(): Boolean {

        val (b1, raw1) = send(0x01, 0x03)
        Thread.sleep(150)
        val (b2, raw2) = send(0x01, 0x04)
        val r1 = if (b1.isNotEmpty()) b1 else raw1.toByteArray()
        val r2 = if (b2.isNotEmpty()) b2 else raw2.toByteArray()
        return r1.isNotEmpty() || r2.isNotEmpty()
    }

    fun readBillSets(): Pair<List<String>, String> {
        val (body, raw) = send(0x01, 0x0A)
        if (body.isEmpty()) return emptyList<String>() to raw
        val names = utf16le(body).split(",")
            .map { it.trim() }.filter { it.isNotEmpty() && !it.all { c -> c == '0' || c == '_' } }
        return names to raw
    }
}
