package cl.reabill.monitor

object LabelProtocol {
    const val HEAD_DOTS = 96
    const val BYTES_PER_COLUMN = HEAD_DOTS / 8
    const val DOTS_PER_MM = 8
    const val MIN_MM = 10
    const val MAX_MM = 500

    private val INIT = byteArrayOf(0x10, 0xFF.toByte(), 0x40)
    private val PRINT_START = byteArrayOf(0x10, 0xFF.toByte(), 0xF1.toByte(), 0x02)
    private val RASTER = byteArrayOf(0x1D, 0x76, 0x30, 0x00)

    fun mmToDots(mm: Int) = mm.coerceIn(MIN_MM, MAX_MM) * DOTS_PER_MM

    fun buildJob(black: Array<BooleanArray>, dieCut: Boolean, density: Int? = null): ByteArray {
        require(black.size == HEAD_DOTS) { "La imagen debe tener $HEAD_DOTS puntos de alto" }
        val cols = black[0].size
        require(cols in 1..0xFFFF) { "Largo de imagen fuera de rango" }
        val out = java.io.ByteArrayOutputStream()
        out.write(INIT)
        if (density != null) out.write(byteArrayOf(0x1F, 0x70, 0x01, density.coerceIn(1, 15).toByte()))
        out.write(ByteArray(15))
        out.write(PRINT_START)
        out.write(RASTER)
        out.write(BYTES_PER_COLUMN); out.write(0)
        out.write(cols and 0xFF); out.write((cols shr 8) and 0xFF)
        out.write(packColumns(black))
        if (dieCut) {
            out.write(byteArrayOf(0x1D, 0x0C))
            out.write(byteArrayOf(0x10, 0xFF.toByte(), 0xF1.toByte(), 0x45))
            out.write(INIT); out.write(INIT)
        } else {
            out.write(byteArrayOf(0x1B, 0x4A, 0x5B))
            out.write(byteArrayOf(0x10, 0xFF.toByte(), 0xF1.toByte(), 0x45))
        }
        return out.toByteArray()
    }

    fun packColumns(black: Array<BooleanArray>): ByteArray {
        val cols = black[0].size
        val data = ByteArray(cols * BYTES_PER_COLUMN)
        var p = 0
        for (x in 0 until cols) {
            for (b in 0 until BYTES_PER_COLUMN) {
                val base = HEAD_DOTS - 8 * (b + 1)
                var v = 0
                for (k in 0 until 8) if (black[base + k][x]) v = v or (1 shl k)
                data[p++] = v.toByte()
            }
        }
        return data
    }

    fun chunks(data: ByteArray, size: Int): List<ByteArray> =
        (data.indices step size).map { data.copyOfRange(it, minOf(it + size, data.size)) }

    fun isP12Name(name: String?): Boolean {
        val n = name?.trim { it <= ' ' || it == '\u0000' } ?: return false
        return n.endsWith("_BLE", ignoreCase = true) &&
            (n.startsWith("P12", true) || n.startsWith("P15", true) || n.startsWith("L13", true))
    }
}
