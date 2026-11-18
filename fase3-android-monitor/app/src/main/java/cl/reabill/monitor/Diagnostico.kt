package cl.reabill.monitor

class Diag(val bloque: ByteArray, val final: ByteArray) {
    private fun u(i: Int) = if (bloque.size >= i + 2) (bloque[i].toInt() and 0xFF) or ((bloque[i + 1].toInt() and 0xFF) shl 8) else -1
    val valido: Boolean get() = bloque.size == 52 && (bloque[38].toInt() and 0xFF) == 2 && (bloque[39].toInt() and 0xFF) == 3
    val a: Int get() = u(40)
    val sensor: Int get() = u(42)
    val c: Int get() = u(44)
    val umbral: Int get() = u(46)
    val e: Int get() = u(48)
    val f: Int get() = u(50)
    val hex: String get() = bloque.joinToString("") { "%02x".format(it) }
    val finalHex: String get() = final.joinToString("") { "%02x".format(it) }
}

object Rendimiento {
    class Reciente(val pct: Double, val insertados: Int, val dias: Double)

    fun reciente(historial: List<AccountingData>, dias: Int = 7, minimo: Int = 20): Reciente? {
        val h = historial.sortedBy { it.timestamp }
        val ultima = h.lastOrNull() ?: return null
        val desde = ultima.timestamp - dias * 86_400_000L
        var base = ultima
        for (i in h.size - 2 downTo 0) {
            val x = h[i]
            if (x.timestamp < desde) break
            if (x.inserted > base.inserted || x.accepted > base.accepted) break
            base = x
        }
        val dIns = ultima.inserted - base.inserted
        if (base === ultima || dIns < minimo) return null
        val dAcc = ultima.accepted - base.accepted
        return Reciente(dAcc * 100.0 / dIns, dIns, (ultima.timestamp - base.timestamp) / 86_400_000.0)
    }
}

