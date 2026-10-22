package cl.reabill.monitor

import org.json.JSONArray
import org.json.JSONObject

object Interfaz {
    const val BLOQUE = 544
    const val OFFSET = 16

    val RECALCULADOS = 252..253
    const val STAND_ALONE = 0

    val VALORES = listOf(0, 1, 2, 3)
    private val NOMBRES = mapOf(0 to "Stand-alone", 1 to "IGT Netplex", 2 to "EBDS / Serie", 3 to "USB")

    fun nombre(v: Int?): String = if (v == null) "—" else NOMBRES[v] ?: "Desconocida ($v)"

    fun leer(bloque: ByteArray): Int? =
        if (bloque.size == BLOQUE) bloque[OFFSET].toInt() and 0xFF else null

    fun ponerEnBloque(bloque: ByteArray, valor: Int): ByteArray {
        require(bloque.size == BLOQUE) { "el bloque de configuración debe tener $BLOQUE bytes" }
        require(valor in VALORES) { "interfaz desconocida: $valor" }
        return bloque.copyOf().also { it[OFFSET] = valor.toByte() }
    }

    fun soloCambioEsperado(antes: ByteArray, despues: ByteArray, valor: Int): Boolean {
        if (antes.size != BLOQUE || despues.size != BLOQUE) return false
        for (i in 0 until BLOQUE) {
            if (i in RECALCULADOS) continue
            val esperado = if (i == OFFSET) valor.toByte() else antes[i]
            if (despues[i] != esperado) return false
        }
        return true
    }
}

class IfazCambio(val ts: Long, val valor: Int, val origen: String)

class IfazRegistro(val ultima: Int?, val habitual: Int?, val ts: Long, val hist: List<IfazCambio>) {

    fun toJson(): String {
        val h = JSONArray()
        hist.forEach { h.put(JSONArray().put(it.ts).put(it.valor).put(it.origen)) }
        return JSONObject().put("u", ultima ?: JSONObject.NULL).put("h", habitual ?: JSONObject.NULL)
            .put("t", ts).put("hist", h).toString()
    }

    companion object {
        const val MAX_HIST = 30

        fun fromJson(s: String?): IfazRegistro? = try {
            if (s.isNullOrBlank()) null else {
                val o = JSONObject(s)
                val arr = o.optJSONArray("hist")
                val hist = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                    val a = arr.optJSONArray(i) ?: return@mapNotNull null
                    IfazCambio(a.optLong(0), a.optInt(1), a.optString(2))
                }
                IfazRegistro(
                    if (o.isNull("u")) null else o.optInt("u"),
                    if (o.isNull("h")) null else o.optInt("h"), o.optLong("t"), hist
                )
            }
        } catch (_: Exception) { null }

        fun fusionar(a: IfazRegistro?, b: IfazRegistro?): IfazRegistro? {
            if (a == null) return b
            if (b == null) return a
            val hist = (a.hist + b.hist).distinctBy { Triple(it.ts, it.valor, it.origen) }.sortedBy { it.ts }.takeLast(MAX_HIST)
            val nueva = if (a.ts >= b.ts) a else b
            val ultima = nueva.ultima ?: a.ultima ?: b.ultima
            val habitual = if (ultima != null && ultima != Interfaz.STAND_ALONE) ultima
                else hist.lastOrNull { it.valor != Interfaz.STAND_ALONE }?.valor ?: nueva.habitual ?: a.habitual ?: b.habitual
            return IfazRegistro(ultima, habitual, maxOf(a.ts, b.ts), hist)
        }

        fun registrar(previo: IfazRegistro?, actual: Int, ahora: Long, origenCambio: String = "fuera"): IfazRegistro {
            val ultimaPrevia = previo?.ultima
            val hist = (previo?.hist ?: emptyList()).toMutableList()
            if (previo == null) hist.add(IfazCambio(ahora, actual, "primera"))
            else if (ultimaPrevia != actual) hist.add(IfazCambio(ahora, actual, origenCambio))
            val habitual = if (actual != Interfaz.STAND_ALONE) actual else previo?.habitual
            return IfazRegistro(actual, habitual, ahora, hist.takeLast(MAX_HIST))
        }
    }
}
