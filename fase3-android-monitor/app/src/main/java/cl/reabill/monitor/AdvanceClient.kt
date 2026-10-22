package cl.reabill.monitor

class AdvanceClient(private val usb: UsbSerialManager) : ValidatorClient {

    override var lastDetail: String = ""
        private set

    override var timingNote: String = ""
        private set
    private var tIni = 0L; private var nCmd = 0; private var nRetry = 0
    private fun medirInicio() { tIni = System.nanoTime(); nCmd = 0; nRetry = 0 }
    private fun medirFin() { timingNote = "${(System.nanoTime() - tIni) / 1_000_000} ms, $nCmd comandos, $nRetry reintentos" }

    var lastReadComplete: Boolean = false
        private set
    override val supportsReset: Boolean = true
    override val calibrationNote: String =
        " Al terminar, reinicia el equipo (sácalo y vuelve a instalarlo en el cashbox): así sale del modo calibración."

    private var toggle = 0

    companion object {

        fun crc16(data: ByteArray, from: Int, to: Int): Int {
            var crc = 0
            for (i in from until to) {
                crc = crc xor (data[i].toInt() and 0xFF)
                repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0x8408 else crc ushr 1 }
            }
            return crc and 0xFFFF
        }

        fun frameOk(f: ByteArray): Boolean {
            if (f.size < 6) return false
            val len = (f[0].toInt() and 0xFF) or ((f[1].toInt() and 0xFF) shl 8)
            if (len != f.size) return false
            val crc = (f[len - 2].toInt() and 0xFF) or ((f[len - 1].toInt() and 0xFF) shl 8)
            return crc16(f, 0, len - 2) == crc
        }

        fun buildFrame(cmd: Int, payload: ByteArray = ByteArray(0)): ByteArray {
            val len = 6 + payload.size
            val f = ByteArray(len)
            f[0] = (len and 0xFF).toByte()
            f[1] = ((len ushr 8) and 0xFF).toByte()
            f[2] = cmd.toByte()
            f[3] = 0
            payload.copyInto(f, 4)
            val crc = crc16(f, 0, len - 2)
            f[len - 2] = (crc and 0xFF).toByte()
            f[len - 1] = ((crc ushr 8) and 0xFF).toByte()
            return f
        }

        private fun u32(b: ByteArray, o: Int): Long =
            (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
    }

    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }

    private fun exchange(cmd: Int, payload: ByteArray, accept: (ByteArray) -> Boolean): Pair<ByteArray?, String> {
        val notas = StringBuilder()
        nCmd++
        for (attempt in 0..2) {
            if (attempt > 0) {
                nRetry++
                notas.append(" | R${attempt + 1}:").append(usb.resetEndpoints(attempt + 1))
                Thread.sleep(300)
            }
            val req = buildFrame(cmd or (toggle shl 7), payload)
            toggle = toggle xor 1
            val resp = usb.transactLe(req) { frameOk(it) && accept(it) }
            if (resp.isNotEmpty()) return resp to notas.toString()
            notas.append(" | intento$attempt: sin trama válida")
        }
        return null to notas.toString().trim()
    }

    private fun readMem(offset: Int, length: Int): Pair<ByteArray?, String> {
        val p = byteArrayOf(
            0x04, 0x00,
            (length and 0xFF).toByte(), ((length ushr 8) and 0xFF).toByte(),
            (offset and 0xFF).toByte(), ((offset ushr 8) and 0xFF).toByte(),
            0x00, 0x00
        )
        fun datos(it: ByteArray) = it.size == length + 10 && (it[2].toInt() and 0x7F) == 0x06

        val (r0, notas) = exchange(0x06, p) {
            datos(it) || (it.size == 10 && (it[2].toInt() and 0x7F) == 0x06 && (it[3].toInt() and 0x80) != 0)
        }
        var r = r0
        var polls = 0
        while (r != null && r.size == 10 && polls < 100) {
            polls++
            Thread.sleep(90)
            r = exchangeOnce(0x01, ByteArray(0), 4000) { datos(it) || it.size == 10 }
        }
        if (r == null || r.size != length + 10) return null to (notas + " | lectura $offset: sin datos").trim()
        return r.copyOfRange(8, 8 + length) to notas
    }

    private fun readText(arg: Int): String {
        val (r, _) = exchange(0x1B, byteArrayOf(arg.toByte())) {
            it.size > 10 && (it[2].toInt() and 0x7F) == 0x1B
        }
        if (r == null) return "—"
        return String(r, 8, r.size - 10, Charsets.ISO_8859_1).filter { it.code in 0x20..0x7E }.trim()
            .ifEmpty { "—" }
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

    override fun readIdentity(): DeviceStatus {
        medirInicio()
        try {
        val (b, notas) = readMem(0x0000, 0x118)
        if (b == null) return DeviceStatus(state = "Sin respuesta", raw = notas)
        val t = asciiTokens(b)
        return DeviceStatus(
            connected = true, state = "Conectado",
            model = t.getOrElse(0) { "—" }, manufacturer = t.getOrElse(1) { "—" },
            serial = t.getOrElse(2) { "—" }, prodDate = t.getOrElse(3) { "—" },
            raw = hex(b.copyOf(minOf(b.size, 48)))
        )
        } finally { medirFin() }
    }

    private fun readVersion(arg: Int): String {
        val (r, _) = exchange(0x03, byteArrayOf(arg.toByte())) {
            it.size > 10 && (it[2].toInt() and 0x7F) == 0x03
        }
        if (r == null) return ""
        return asciiTokens(r.copyOfRange(8, r.size - 2)).firstOrNull { Regex("\\d\\d\\.\\d\\d\\.\\d\\d").matches(it) } ?: ""
    }

    override fun readFirmware(): Triple<String, String, String> {
        medirInicio()
        try {
        Thread.sleep(150)
        val app = readText(4)
        Thread.sleep(150)
        val variantId = readText(5)
        Thread.sleep(100)
        val ver = if (variantId == "—") "" else readVersion(5)
        val variant = if (variantId != "—" && ver.isNotEmpty()) "$variantId v$ver" else variantId
        val fallos = buildString {
            if (app == "—") append("aplicación sin respuesta")
            if (variantId == "—") { if (isNotEmpty()) append("  ||  "); append("variante sin respuesta") }
        }
        return Triple(app, variant, fallos)
        } finally { medirFin() }
    }

    override fun readAccounting(): Pair<AccountingData?, String> {
        medirInicio()
        try {
        val (live, n1) = readMem(0x3A00, 0x1D8)
        if (live == null) return null to "contadores: $n1".trim()
        Thread.sleep(100)
        val (head, n2) = readMem(0x2200, 0x800)
        Thread.sleep(100)
        val (stk, n3) = readMem(0x3200, 0x800)

        val inserted = u32(live, 0x10); val validated = u32(live, 0x14); val accepted = u32(live, 0x18)
        val total = if (head == null) 0L else u32(head, 0x08)
        val deposited = if (head == null) 0L else u32(head, 0x0C)
        val den = intArrayOf(1000, 2000, 5000, 10000, 20000)

        fun fila(k: Int) = if (stk == null) 0L else (0 until 8).sumOf { j -> u32(stk, 32 * k + 4 * j) }
        val perDen = LongArray(5) { fila(it + 1) }
        val sinDen = fila(0)
        val value = perDen.indices.sumOf { perDen[it] * den[it] }
        val rate = if (inserted > 0) validated * 100.0 / inserted else 0.0
        val qp = if (accepted > 0) total.toDouble() / accepted else 0.0
        val conj = if (total > 0) accepted * 100.0 / total else 0.0
        val cuadra = stk != null && perDen.sum() + sinDen == accepted
        lastReadComplete = head != null && cuadra
        lastDetail = "Detalle contabilidad Advance: valor apilado=$value  tasa QP=${"%.2f".format(qp)}  " +
            "tasa conjunto=${"%.2f".format(conj)}%  validados=$validated  apilados por denominación=" +
            "${perDen.toList()}" + if (cuadra) "" else "  (¡suma por denominación no cuadra con aceptados!)"
        val data = AccountingData(
            inserted = inserted.toInt(), accepted = accepted.toInt(),
            rejected = (inserted - accepted).coerceAtLeast(0).toInt(), rate = rate,
            stacked = accepted.toInt(), deposited = deposited.toInt(),
            bcInserted = u32(live, 0x20).toInt(), bcAccepted = 0, bcRejected = 0
        )
        return data to (n1 + n2 + n3).trim()
        } finally { medirFin() }
    }

    fun awaitCalibration(timeoutMs: Long = 120_000): Int {
        val end = System.currentTimeMillis() + timeoutMs
        var perdidos = 0
        while (System.currentTimeMillis() < end) {
            Thread.sleep(96)
            val r = try { exchangeOnce(0x01, ByteArray(0), 3000) { it.size == 10 || it.size == 11 } }
                    catch (e: Exception) { return -2 }
            if (r == null) { if (++perdidos >= 5) return -2; continue }
            perdidos = 0
            if (r.size == 11 && (r[2].toInt() and 0x7F) == 0x17 && (r[3].toInt() and 0x80) == 0) return r[8].toInt() and 0xFF
        }
        return -1
    }

    override fun initiateCalibration(): Pair<Boolean, String> {

        val r = exchangeOnce(0x17, ByteArray(0), 5000) { it.size >= 10 && (it[2].toInt() and 0x7F) == 0x17 }
        return (r != null) to (if (r != null) hex(r) else "sin trama válida")
    }

    var lastResetNote: String = ""
        private set

    override fun resetAccounting(): Boolean {
        val n = StringBuilder()
        fun fin(ok: Boolean): Boolean { lastResetNote = n.toString().trim(); return ok }
        val clk = sendClock()
        n.append("fecha(2E)=").append(if (clk) "ok" else "sin respuesta")
        if (!clk) return fin(false)
        Thread.sleep(50)

        val start = exchangeOnce(0x1E, ByteArray(0), 4000) { it.size == 10 && (it[2].toInt() and 0x7F) == 0x1E }
        n.append(" | borrar(1E)=").append(if (start != null) hex(start) else "sin ack (se sondea igual)")
        var done = start != null && (start[3].toInt() and 0x80) == 0
        var sondeos = 0; var perdidos = 0; var libres = 0
        val limite = System.currentTimeMillis() + 20_000
        while (!done && System.currentTimeMillis() < limite) {
            Thread.sleep(90)
            sondeos++
            val r = exchangeOnce(0x01, ByteArray(0), 3000) { it.size == 10 }
            if (r == null) { if (++perdidos >= 5) { n.append(" | sondeos=$sondeos: el equipo dejó de responder"); return fin(false) }; continue }
            perdidos = 0
            val cmd = r[2].toInt() and 0x7F
            val ocupado = (r[3].toInt() and 0x80) != 0
            if (cmd == 0x1E && !ocupado) { done = true; n.append(" | fin=").append(hex(r)) }
            else if (cmd == 0x01 && !ocupado) {

                libres++
                if (start != null || libres >= 3) { done = true; n.append(" | libre=").append(hex(r)) }
            }
        }
        n.append(" | sondeos=$sondeos")
        if (!done) { n.append(" | el equipo no terminó (20 s)"); return fin(false) }
        Thread.sleep(50)
        val a20 = exchangeOnce(0x20, ByteArray(0), 4000) { it.size == 10 && (it[2].toInt() and 0x7F) == 0x20 }
        n.append(" | 20=").append(if (a20 != null) "ok" else "sin ack")
        Thread.sleep(50)
        sendClock()

        var live: ByteArray? = null
        for (i in 1..3) {
            Thread.sleep(300L * i)
            live = readMem(0x3A00, 0x1D8).first
            if (live != null) break
        }
        if (live == null) { n.append(" | verificación: no pude releer"); return fin(false) }
        val ins = u32(live, 0x10); val acc = u32(live, 0x18)
        n.append(" | verificación: insertados=$ins aceptados=$acc")
        return fin(ins == 0L && u32(live, 0x14) == 0L && acc == 0L)
    }

    private fun clockPayload(): ByteArray {
        val c = java.util.Calendar.getInstance()
        fun bcd(n: Int) = (((n / 10) shl 4) or (n % 10)).toByte()
        return byteArrayOf(bcd(c.get(java.util.Calendar.HOUR_OF_DAY)), bcd(c.get(java.util.Calendar.YEAR) % 100),
            bcd(c.get(java.util.Calendar.DAY_OF_MONTH)), bcd(c.get(java.util.Calendar.MONTH) + 1))
    }

    private fun sendClock(): Boolean {
        val (r, _) = exchange(0x2E, clockPayload()) { it.size == 10 && (it[2].toInt() and 0x7F) == 0x2E }
        return r != null
    }

    private fun exchangeOnce(cmd: Int, payload: ByteArray, totalMs: Int = 8000,
                             accept: (ByteArray) -> Boolean): ByteArray? {
        nCmd++
        val req = buildFrame(cmd or (toggle shl 7), payload)
        toggle = toggle xor 1
        val r = usb.transactLe(req, totalMs, totalMs) { frameOk(it) && accept(it) }
        return if (r.isEmpty()) null else r
    }

    fun newSession() { toggle = 0 }

    fun beginDownload(): String? {
        if (!sendClock()) return "El equipo no respondió a la fecha (2E)."
        Thread.sleep(50)
        val r = exchangeOnce(0x0A, ByteArray(0), 5000) { it.size == 10 && (it[2].toInt() and 0x7F) == 0x0A }
        return if (r == null) "El equipo no respondió al comando de modo descarga (0A)." else null
    }

    fun confirmDownload(): String? {
        val r = exchangeOnce(0x0A, ByteArray(0), 4000) { it.size == 10 && (it[2].toInt() and 0x7F) == 0x0A }
            ?: return "Modo descarga: el equipo no respondió."
        return if ((r[3].toInt() and 0xFF) == 0x01) null
        else "El equipo no está en modo descarga (respuesta ${hex(r)}); no se escribió nada."
    }

    fun writeRecord(rec: ByteArray, n: Int): String? {
        var r = exchangeOnce(0x0B, rec, 10000) { it.size == 10 && (it[2].toInt() and 0x7F) == 0x0B }
            ?: return "Registro $n: el equipo no respondió."
        var polls = 0
        while ((r!![3].toInt() and 0x80) != 0) {
            if (++polls > 250) return "Registro $n: el equipo sigue ocupado."
            Thread.sleep(90)
            r = exchangeOnce(0x01, ByteArray(0), 4000) { it.size == 10 }
                ?: return "Registro $n: sin respuesta mientras estaba ocupado."
        }
        val st = r!!.copyOfRange(4, 8)
        return if (st.all { it.toInt() == 0 }) null else "Registro $n: el equipo respondió error (${hex(r)})."
    }

    fun finishDownload(): String? {
        val r = exchangeOnce(0x02, ByteArray(0), 5000) { it.size == 10 && (it[2].toInt() and 0x7F) == 0x02 }
            ?: return "Cierre: el equipo no respondió."
        return if (r.copyOfRange(4, 8).all { it.toInt() == 0 }) null else "Cierre: respuesta de error (${hex(r)})."
    }
}
