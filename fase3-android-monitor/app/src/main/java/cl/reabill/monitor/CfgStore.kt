package cl.reabill.monitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CfgStore(context: Context) {
    private val prefs = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun ts(): Long = prefs.getLong("ts", 0L)

    fun valor(): JSONObject? = try {
        prefs.getString("v", null)?.let { JSONObject(it) }
    } catch (e: Exception) { aviso("leer configuración compartida", e); null }

    fun put(v: JSONObject, ts: Long) { prefs.edit().putString("v", v.toString()).putLong("ts", ts).apply() }

    fun actualizarSiCambio(v: JSONObject, ahora: Long = System.currentTimeMillis()): Boolean {
        val actual = valor()
        if (actual != null && canon(actual) == canon(v)) return false
        put(v, maxOf(ahora, ts() + 1))
        return true
    }

    companion object {

        fun canon(o: Any?): String = when (o) {
            is JSONObject -> o.keys().asSequence().sorted().joinToString(",", "{", "}") { "\"$it\":" + canon(o.get(it)) }
            is JSONArray -> (0 until o.length()).joinToString(",", "[", "]") { canon(o.get(it)) }
            is String -> JSONObject.quote(o)
            else -> o.toString()
        }
    }
}
