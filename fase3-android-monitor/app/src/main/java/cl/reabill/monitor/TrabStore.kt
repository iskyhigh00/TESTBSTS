package cl.reabill.monitor

import android.content.Context
import org.json.JSONObject

class Trab(
    val mda: String, val quien: String, val fecha: String, val resultado: String, val tipo: String, val firmware: String,
    val origenMda: String, val acciones: List<String>, val total: Int
) {

    val que: String get() {
        val base = when (resultado) {
            "cambiado" -> "Cambiado" + (if (tipo == "nuevo") " (nuevo)" else if (tipo == "usado") " (usado)" else "") +
                firmware.takeIf { it.isNotBlank() }?.let { " · fw $it" }.orEmpty()
            "trasladado" -> "Se le instaló el billetero de la MDA ${origenMda.ifBlank { "?" }}"
            "mismo" -> "Quedó el mismo"
            else -> resultado.ifBlank { "Revisado" }
        }
        return base + if (acciones.isNotEmpty()) " · " + acciones.joinToString(", ") else ""
    }

    val fechaCorta: String get() = fmtFecha(fecha)

    companion object {
        fun fmtFecha(s: String): String {
            if (s.isBlank()) return ""
            return try {
                val ms = s.toLongOrNull() ?: run {
                    val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT)
                    f.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    f.parse(s.take(19).replace(' ', 'T'))!!.time
                }
                java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.ROOT).format(java.util.Date(ms))
            } catch (e: Exception) { s.take(10) }
        }
    }
}

class TrabStore(context: Context) {
    private val prefs = context.getSharedPreferences("trab", Context.MODE_PRIVATE)

    fun ts(mda: String): Long = prefs.getLong("ts:$mda", 0L)

    fun valor(mda: String): JSONObject? = try {
        prefs.getString("v:$mda", null)?.let { JSONObject(it) }
    } catch (e: Exception) { aviso("leer último trabajo", e); null }

    fun put(mda: String, v: JSONObject, ts: Long) {
        prefs.edit().putString("v:$mda", v.toString()).putLong("ts:$mda", ts).apply()
    }

    fun quitar(id: String) { prefs.edit().remove("v:$id").remove("ts:$id").apply() }

    fun ids(): Set<String> = prefs.all.keys.filter { it.startsWith("v:") }.map { it.removePrefix("v:") }.toSet()

    fun leer(mda: String): Trab? {
        val v = valor(mda) ?: return null
        val res = v.optString("resultado")
        if (res == "quitado") return null
        val acc = listOf("calibraste" to "se calibró", "limpiaste" to "se limpió", "variant" to "se actualizó el variant", "pieza" to "se cambió una pieza", "probaste" to "se probó")
            .filter { v.optBoolean(it.first) }.map { it.second }
        return Trab(
            mda, v.optString("quien"), v.optString("ts"), res, v.optString("tipo_cambio").ifBlank { v.optString("tipo") }, v.optString("firmware"),
            v.optString("origenMda"), acc, v.optInt("n", 1)
        )
    }
}
