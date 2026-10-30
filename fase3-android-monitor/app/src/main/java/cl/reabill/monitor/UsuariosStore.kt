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

    class Usu(val correo: String, val nombre: String, val corto: String, val rol: String)

    fun lista(): List<Usu> {
        val arr = valor()?.optJSONArray("lista") ?: return emptyList()
        val base = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .filter { it.optString("correo").isNotBlank() && it.optString("nombre").isNotBlank() }
            .distinctBy { it.optString("correo").trim().lowercase() }
        val primeros = base.map { it.optString("nombre").trim().split(Regex("\\s+")).first() }
        return base.mapIndexed { i, o ->
            val partes = o.optString("nombre").trim().split(Regex("\\s+"))
            val dup = primeros.count { it.equals(primeros[i], true) } > 1
            val corto = if (dup && partes.size > 1) "${partes.first()} ${partes.last().first()}." else partes.first()
            Usu(o.optString("correo").trim(), o.optString("nombre").trim(), corto, o.optString("rol"))
        }.sortedBy { it.corto.lowercase() }
    }

    fun nombreDe(correo: String): String? = fila(correo)?.optString("nombre")?.trim()?.takeIf { it.isNotEmpty() }

    fun rolDe(correo: String): String = fila(correo)?.optString("rol").orEmpty()
}
