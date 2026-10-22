package cl.reabill.monitor

class FirmwareFile(val name: String, val data: ByteArray, val label: String, val kind: String = KIND_SC83) {

    val records: List<ByteArray> by lazy { if (kind == KIND_ADVANCE) splitRecords(data) ?: emptyList() else emptyList() }

    val blocks: Int get() = if (kind == KIND_ADVANCE) records.size else (data.size + BLOCK - 1) / BLOCK

    companion object {
        const val BLOCK = 1024
        const val BLOCKS = 64
        const val KIND_SC83 = "SC83"
        const val KIND_ADVANCE = "ADVANCE"

        fun splitRecords(d: ByteArray): List<ByteArray>? {
            val out = ArrayList<ByteArray>()
            var o = 0
            while (o < d.size) {
                if (o + 4 > d.size) return null
                val len = (d[o].toInt() and 0xFF) or ((d[o + 1].toInt() and 0xFF) shl 8)
                if (len < 4 || len > 4096 || o + len > d.size) return null
                out.add(d.copyOfRange(o, o + len)); o += len
            }
            return out
        }

        fun parse(name: String, data: ByteArray): Pair<FirmwareFile?, String> {
            val sc83 = parseSc83(name, data)
            if (sc83.first != null) return sc83
            val adv = parseAdvance(name, data)
            if (adv.first != null) return adv

            return null to (if (data.size in 20_000..400_000 && splitRecords(data) != null) adv.second else sc83.second)
        }

        private fun parseSc83(name: String, data: ByteArray): Pair<FirmwareFile?, String> {
            if (data.size <= BLOCK * (BLOCKS - 1) || data.size > BLOCK * BLOCKS)
                return null to "Tamaño ${data.size} B: no es un bill-set SC83 (se esperan ${BLOCK * (BLOCKS - 1) + 1}–${BLOCK * BLOCKS} B) ni un bill-set Advance válido."

            val head = buildString {
                for (i in 0 until minOf(data.size, 600)) {
                    val c = data[i].toInt() and 0xFF
                    if (c in 0x20..0x7E) append(c.toChar())
                }
            }
            if (!head.contains("CASHFLOW") || !head.contains("sc83", ignoreCase = true))
                return null to "El archivo no es un bill-set de Cashflow SC83."
            val num = Regex("MEI #\\s*(\\d+)").find(head)?.groupValues?.get(1) ?: "?"
            return FirmwareFile(name, data, num, KIND_SC83) to ""
        }

        private fun parseAdvance(name: String, data: ByteArray): Pair<FirmwareFile?, String> {
            val recs = splitRecords(data)
            if (recs == null || recs.isEmpty())
                return null to "El archivo no tiene la estructura de un bill-set Advance (registros encadenados)."
            val known = setOf(0x0403, 0x0303, 0x0404, 0x0001, 0x0000)
            fun type(r: ByteArray) = ((r[3].toInt() and 0xFF) shl 8) or (r[2].toInt() and 0xFF)
            if (recs.any { type(it) !in known })
                return null to "El bill-set Advance contiene registros de un tipo desconocido; no se escribe."
            if (type(recs.first()) != 0x0403) return null to "El bill-set Advance no empieza con el registro esperado."
            if (type(recs.last()) != 0x0000 || recs.last().size != 18)
                return null to "El bill-set Advance no termina con el registro final esperado."
            if (recs.none { type(it) == 0x0404 }) return null to "El bill-set Advance no trae datos."
            if (data.size > 200_000) return null to "El bill-set Advance es demasiado grande (${data.size} B)."

            for ((i, r) in recs.withIndex()) {
                val t = type(r)
                if (t != 0x0403 && t != 0x0303 && t != 0x0404) continue
                if (r.size < 8) return null to "Registro ${i + 1} del bill-set Advance demasiado corto."
                val addr = (r[4].toLong() and 0xFF) or ((r[5].toLong() and 0xFF) shl 8) or
                    ((r[6].toLong() and 0xFF) shl 16) or ((r[7].toLong() and 0xFF) shl 24)
                val fin = addr + r.size - 8
                val ok = if (t == 0x0404) addr >= 0x640D0000L && fin <= 0x64200000L
                         else addr >= 0x20000000L && fin <= 0x20020000L
                if (!ok) return null to "Registro ${i + 1} del bill-set Advance apunta a una dirección fuera de lo esperado (0x${"%08X".format(addr)}); no se escribe."
            }
            val txt = String(data, Charsets.ISO_8859_1)
            val m = Regex("(\\d{9})\\1\\u0000(\\d\\d\\.\\d\\d\\.\\d\\d)").findAll(txt).lastOrNull()
                ?: return null to "No encuentro el número de bill-set dentro del archivo Advance."
            return FirmwareFile(name, data, m.groupValues[1], KIND_ADVANCE).also { it.version = m.groupValues[2] } to ""
        }
    }

    var version: String = ""
        private set
}

object FirmwareFlasher {
    private val ACK = byteArrayOf(0x02, 0x00, 0x06, 0x00, 0x00, 0x00)

    private fun crc16(d: ByteArray, to: Int): Int {
        var crc = 0
        for (i in 0 until to) {
            crc = crc xor ((d[i].toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF }
        }
        return crc
    }

    fun frame(op: Int, params: ByteArray = ByteArray(0)): ByteArray {
        val len = 6 + params.size
        val f = ByteArray(len)
        f[0] = 0x02; f[1] = (len ushr 8).toByte(); f[2] = len.toByte(); f[3] = op.toByte()
        params.copyInto(f, 4)
        val c = crc16(f, len - 2)
        f[len - 2] = (c ushr 8).toByte(); f[len - 1] = c.toByte()
        return f
    }

    fun blockFrame(n: Int, payload: ByteArray): ByteArray {
        val p = ByteArray(4 + payload.size)
        p[0] = (n ushr 8).toByte(); p[1] = n.toByte()
        p[2] = (payload.size ushr 8).toByte(); p[3] = payload.size.toByte()
        payload.copyInto(p, 4)
        return frame(0x65, p)
    }

    val enterDownload get() = frame(0x61)
    val start get() = frame(0x64, byteArrayOf(0x00, 0x40, 0x04, 0x00))
    val finish get() = frame(0x68)
    val reboot get() = frame(0x67)

    fun isAck(r: ByteArray) = r.size >= ACK.size && r.copyOfRange(0, ACK.size).contentEquals(ACK)

    fun hex(b: ByteArray) = b.take(16).joinToString(" ") { "%02X".format(it) }.ifEmpty { "(nada)" }

    fun sendAcked(usb: UsbSerialManager, f: ByteArray, what: String): String? {
        var last = ByteArray(0)
        var diag = ""
        for (intento in 0 until 3) {
            last = if (intento == 1)
                usb.transact(ByteArray(0), UsbSerialManager.ANY_STX, firstTimeoutMs = 2000, totalMs = 2000)
            else {
                if (intento == 2) Thread.sleep(300)

                usb.resetEndpoints(1)
                Thread.sleep(10)
                usb.transact(f, UsbSerialManager.ANY_STX, firstTimeoutMs = 3000, totalMs = 3000)
            }
            diag += " [${intento}: out=${usb.lastOut} in=${hex(last)}]"
            if (isAck(last)) return null
            if (last.isNotEmpty()) return "$what: respuesta inesperada ${hex(last)}"
        }
        return "$what: el equipo no respondió$diag"
    }
}
