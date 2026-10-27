package cl.reabill.monitor

import android.content.Context
import org.json.JSONObject

class Etq(
    val id: String, val estado: String, val fecha: String, val nota: String,
    val aMda: String, val aLoc: String, val bMda: String, val bLoc: String, val ts: Long
) {
    private fun nombre(mda: String, loc: String) = mda.ifBlank { loc.ifBlank { "—" } }
    val a: String get() = nombre(aMda, aLoc)
    val b: String get() = nombre(bMda, bLoc)

    fun detalle(fechaEtiqueta: String): String = listOf(fechaEtiqueta.trim(), nota).filter { it.isNotBlank() }.joinToString("  ")

    fun etiquetas(fechaEtiqueta: String): List<List<LabelLine>> = listOf(a to b, b to a).map { (de, a) ->
        listOfNotNull(LabelLine("$de → $a", true), detalle(fechaEtiqueta).takeIf { it.isNotEmpty() }?.let { LabelLine(it, false) })
    }
}

class EtqStore(context: Context) {
    private val prefs = context.getSharedPreferences("etq", Context.MODE_PRIVATE)

    fun ts(id: String): Long = prefs.getLong("ts:$id", 0L)

    fun valor(id: String): JSONObject? = try {
        prefs.getString("v:$id", null)?.let { JSONObject(it) }
    } catch (e: Exception) { aviso("leer intercambio", e); null }

    fun put(id: String, v: JSONObject, ts: Long) {
        prefs.edit().putString("v:$id", v.toString()).putLong("ts:$id", ts).apply()
    }

    fun quitar(id: String) { prefs.edit().remove("v:$id").remove("ts:$id").apply() }

    fun ids(): Set<String> = prefs.all.keys.filter { it.startsWith("v:") }.map { it.removePrefix("v:") }.toSet()

    fun leer(id: String): Etq? {
        val v = valor(id) ?: return null
        if (v.optString("tipo") != "intercambio") return null
        val a = v.optJSONObject("a"); val b = v.optJSONObject("b")
        return Etq(
            id, v.optString("estado"), v.optString("fecha"), v.optString("nota"),
            a?.optString("mda").orEmpty(), a?.optString("loc").orEmpty(),
            b?.optString("mda").orEmpty(), b?.optString("loc").orEmpty(), ts(id)
        )
    }

    fun pendientes(): List<Etq> = ids().mapNotNull { leer(it) }.filter { it.estado == "pendiente" }
        .sortedByDescending { it.id.toLongOrNull() ?: it.ts }

    fun marcarImpresa(id: String, ahora: Long = System.currentTimeMillis()): Boolean {
        val v = valor(id) ?: return false
        put(id, v.put("estado", "impresa").put("impresa", ahora), maxOf(ahora, ts(id) + 1))
        return true
    }
}
