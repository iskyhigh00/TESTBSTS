package cl.reabill.monitor

import android.content.Context
import org.json.JSONObject

class UsuariosStore(context: Context) {
    private val prefs = context.getSharedPreferences("usuarios", Context.MODE_PRIVATE)

    fun ts(): Long = prefs.getLong("ts", 0L)

    fun valor(): JSONObject? = try {
        prefs.getString("v", null)?.let { JSONObject(it) }
    } catch (e: Exception) { aviso("leer usuarios", e); null }

    fun put(v: JSONObject, ts: Long) { prefs.edit().putString("v", v.toString()).putLong("ts", ts).apply() }

    private fun fila(correo: String): JSONObject? {
        val lista = valor()?.optJSONArray("lista") ?: return null
        val c = correo.trim().lowercase()
        for (i in 0 until lista.length()) {
            val o = lista.optJSONObject(i) ?: continue
            if (o.optString("correo").trim().lowercase() == c) return o
        }
        return null
    }

    fun nombreDe(correo: String): String? = fila(correo)?.optString("nombre")?.trim()?.takeIf { it.isNotEmpty() }

    fun rolDe(correo: String): String = fila(correo)?.optString("rol").orEmpty()
}
