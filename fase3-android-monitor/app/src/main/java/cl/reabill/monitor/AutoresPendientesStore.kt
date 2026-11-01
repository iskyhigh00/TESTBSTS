package cl.reabill.monitor

import android.content.Context
import org.json.JSONObject

class AutoresPendientesStore(context: Context) {
    private val prefs = context.getSharedPreferences("autores_pendientes", Context.MODE_PRIVATE)

    fun autor(clave: String, version: String): String? = try {
        prefs.getString(clave, null)?.let { JSONObject(it) }
            ?.takeIf { it.optString("version") == version }?.optString("autor")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    fun conservar(clave: String, version: String, correo: String) {
        if (autor(clave, version) != null) return
        recibido(clave, version, correo.trim().lowercase().ifEmpty { "autor no registrado" })
    }

    fun recibido(clave: String, version: String, autor: String?) {
        val nombre = autor?.takeIf { it.isNotBlank() } ?: "autor no registrado"
        prefs.edit().putString(clave, JSONObject().put("version", version).put("autor", nombre).toString()).apply()
    }
}
