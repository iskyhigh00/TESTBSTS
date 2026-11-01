package cl.reabill.monitor

import android.content.Context
import org.json.JSONObject

class RegistrosStore(context: Context) {
    private val prefs = context.getSharedPreferences("registros", Context.MODE_PRIVATE)

    fun ts(id: String): Long = prefs.getLong("ts:$id", 0L)

    fun valor(id: String): JSONObject? = try {
        prefs.getString("v:$id", null)?.let { JSONObject(it) }
    } catch (e: Exception) { aviso("leer registro", e); null }

    fun ids(): Set<String> = prefs.all.keys.filter { it.startsWith("v:") }.map { it.removePrefix("v:") }.toSet()

    fun actualizar(id: String, v: JSONObject, ahora: Long = System.currentTimeMillis()): Boolean {
        val actual = valor(id)
        if (actual != null && CfgStore.canon(actual) == CfgStore.canon(v)) return false
        prefs.edit().putString("v:$id", v.toString()).putLong("ts:$id", maxOf(ahora, ts(id) + 1)).apply()
        return true
    }

    fun purgar(desdeDia: String) {
        val e = prefs.edit()
        for (id in ids()) if (id.takeLast(10) < desdeDia) e.remove("v:$id").remove("ts:$id")
        e.apply()
    }
}
