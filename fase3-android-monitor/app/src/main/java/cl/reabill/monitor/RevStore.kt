package cl.reabill.monitor

import android.content.Context
import org.json.JSONObject

class RevDatos(
    val quien: String, val cambiado: Boolean, val usado: Boolean,
    val calibraste: Boolean, val limpiaste: Boolean, val variant: Boolean, val pieza: Boolean, val firmware: String
)

class Rev(
    val mda: String, val estado: String, val isla: String, val modMaq: String, val mod: String, val fw: String,
    val pct: Double?, val etiqueta: String, val fecha: String, val ts: Long
) {

    val detalle: String get() = listOfNotNull(
        isla.takeIf { it.isNotBlank() }?.let { "isla $it" },
        mod.takeIf { it.isNotBlank() },
        fw.takeIf { it.isNotBlank() }?.let { "fw $it" },
        pct?.let { String.format(java.util.Locale("es", "CL"), "%.1f", it) + "%" }
    ).joinToString(" · ")
}

class RevStore(context: Context) {
    private val prefs = context.getSharedPreferences("rev", Context.MODE_PRIVATE)

    fun ts(mda: String): Long = prefs.getLong("ts:$mda", 0L)

    fun valor(mda: String): JSONObject? = try {
        prefs.getString("v:$mda", null)?.let { JSONObject(it) }
    } catch (e: Exception) { aviso("leer revisión", e); null }

    fun put(mda: String, v: JSONObject, ts: Long) {
        prefs.edit().putString("v:$mda", v.toString()).putLong("ts:$mda", ts).apply()
    }

    fun ids(): Set<String> = prefs.all.keys.filter { it.startsWith("v:") }.map { it.removePrefix("v:") }.toSet()

    fun leer(mda: String): Rev? {
        val v = valor(mda) ?: return null

        if (v.optString("tipo").let { it.isNotEmpty() && it != "revision" }) return null
        return Rev(
            mda, v.optString("estado"), v.optString("isla"), v.optString("mod_maq"), v.optString("mod"), v.optString("fw"),
            if (v.isNull("pct") || !v.has("pct")) null else v.optDouble("pct").takeIf { !it.isNaN() },
            v.optString("etiqueta"), v.optString("fecha"), ts(mda)
        )
    }

    fun pendientes(): List<Rev> = ids().mapNotNull { leer(it) }.filter { it.estado == "pendiente" }
        .sortedWith(compareBy({ it.isla.ifBlank { "~" } }, { it.mda }))

    fun revisar(mda: String, d: RevDatos, ahora: Long = System.currentTimeMillis()): Boolean {
        val v = valor(mda) ?: return false
        val n = JSONObject(v.toString())
        n.put("estado", "revisada").put("revisada", ahora).put("quien", d.quien.trim().take(40))
        n.put("resultado", if (d.cambiado) "cambiado" else "mismo")
        if (d.cambiado) n.put("tipo_cambio", if (d.usado) "usado" else "nuevo") else n.remove("tipo_cambio")
        n.put("calibraste", d.calibraste).put("limpiaste", d.limpiaste).put("variant", d.variant).put("pieza", d.pieza)
        n.put("firmware", d.firmware.trim().take(30))
        put(mda, n, maxOf(ahora, ts(mda) + 1))
        return true
    }

    fun quien(): String = prefs.getString("quien", "") ?: ""
    fun setQuien(q: String) { prefs.edit().putString("quien", q.trim().take(40)).apply() }
}
