package cl.reabill.monitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PermisosStore(context: Context) {
    private val prefs = context.getSharedPreferences("permisos", Context.MODE_PRIVATE)

    fun ts(): Long = prefs.getLong("ts", 0L)
    fun valor(): JSONObject? = try {
        prefs.getString("v", null)?.let { JSONObject(it) }
    } catch (e: Exception) { aviso("leer permisos", e); null }
    fun put(v: JSONObject, ts: Long) { prefs.edit().putString("v", v.toString()).putLong("ts", ts).apply() }

    private fun lista(a: JSONArray?): Set<String> = if (a == null) emptySet() else (0 until a.length()).map { a.optString(it) }.toSet()

    fun hechos(): Set<Proc> = lista(valor()?.optJSONArray("hechos")).mapNotNull { Proc.de(it) }.toSet()
    private fun autorizadosPor(correo: String): Set<Proc> =
        lista(valor()?.optJSONObject("autoriza")?.optJSONArray(correo.trim().lowercase())).mapNotNull { Proc.de(it) }.toSet()

    fun permitidos(correo: String): Set<Proc> = autorizadosPor(correo).intersect(hechos())
    fun estaAutorizado(correo: String, p: Proc): Boolean = p in autorizadosPor(correo)

    private fun guardar(hechos: Set<Proc>, autoriza: Map<String, Set<Proc>>, ahora: Long) {
        val a = JSONObject()
        autoriza.toSortedMap().forEach { (c, s) -> if (s.isNotEmpty()) a.put(c, JSONArray(s.map { it.clave }.sorted())) }
        put(JSONObject().put("hechos", JSONArray(hechos.map { it.clave }.sorted())).put("autoriza", a), maxOf(ahora, ts() + 1))
    }

    private fun todasAutorizaciones(): Map<String, Set<Proc>> {
        val o = valor()?.optJSONObject("autoriza") ?: return emptyMap()
        return o.keys().asSequence().associateWith { lista(o.optJSONArray(it)).mapNotNull { c -> Proc.de(c) }.toSet() }
    }

    fun marcarHecho(p: Proc, ahora: Long = System.currentTimeMillis()): Boolean {
        val h = hechos()
        if (p in h) return false
        guardar(h + p, todasAutorizaciones(), ahora); return true
    }

    fun autorizar(correo: String, p: Proc, si: Boolean, ahora: Long = System.currentTimeMillis()) {
        val c = correo.trim().lowercase()
        val t = todasAutorizaciones().toMutableMap()
        t[c] = (t[c] ?: emptySet()).let { if (si) it + p else it - p }
        guardar(hechos(), t, ahora)
    }
}
