package cl.reabill.monitor

import android.content.Context
import org.json.JSONObject

class MaquinasStore(context: Context) {
    private val prefs = context.getSharedPreferences("maquinas", Context.MODE_PRIVATE)

    class Maq(val mda: String, val modelo: String, val juego: String, val fabricante: String, val posicion: String)

    private var cacheTs = -1L
    private var cache: Map<String, Maq> = emptyMap()

    fun ts(): Long = prefs.getLong("ts", 0L)

    fun valor(): JSONObject? = try {
        prefs.getString("v", null)?.let { JSONObject(it) }
    } catch (e: Exception) { aviso("leer máquinas", e); null }

    fun put(v: JSONObject, ts: Long) { prefs.edit().putString("v", v.toString()).putLong("ts", ts).apply(); cacheTs = -1L }

    @Synchronized private fun mapa(): Map<String, Maq> {
        if (cacheTs == ts() && cacheTs >= 0) return cache
        val arr = valor()?.optJSONArray("lista")
        val m = HashMap<String, Maq>()
        if (arr != null) for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("m").trim(); if (id.isEmpty()) continue
            m[id] = Maq(id, o.optString("mo").trim(), o.optString("j").trim(), o.optString("f").trim(), o.optString("p").trim())
        }
        cache = m; cacheTs = ts()
        return m
    }

    fun de(mda: String?): Maq? = if (mda.isNullOrBlank()) null else mapa()[mda.trim()]

    fun cantidad(): Int = mapa().size

    fun resumen(mda: String?): String? {
        val m = de(mda) ?: return null
        val partes = listOf(m.juego, m.modelo).filter { it.isNotEmpty() }
        if (partes.isEmpty()) return null
        return partes.joinToString(" · ") + if (m.fabricante.isNotEmpty()) " (${m.fabricante})" else ""
    }
}
