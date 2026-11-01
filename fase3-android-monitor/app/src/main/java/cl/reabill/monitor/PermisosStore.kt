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
    private fun escribir(v: JSONObject, ts: Long, pendiente: Boolean) {
        prefs.edit().putString("v", v.toString()).putLong("ts", ts).putBoolean("pendiente", pendiente).apply()
    }
    @Synchronized fun put(v: JSONObject, ts: Long) = escribir(v, ts, false)
    fun tieneCambiosPendientes(): Boolean = prefs.getBoolean("pendiente", false)
    @Synchronized fun confirmarVersion(v: JSONObject, ts: Long) {
        if (ts == ts() && CfgStore.canon(v) == CfgStore.canon(valor())) prefs.edit().putBoolean("pendiente", false).apply()
    }
    @Synchronized fun inicializarSiVacio() {
        if (valor() == null) guardar(emptySet(), emptyMap(), System.currentTimeMillis())
    }
    @Synchronized fun aplicarRemoto(v: JSONObject, ts: Long, admin: Boolean): Boolean {
        if (admin && tieneCambiosPendientes() && ts <= ts()) return false
        if (ts == ts() && CfgStore.canon(v) == CfgStore.canon(valor())) return false
        escribir(v, ts, false)
        return true
    }

    private fun lista(a: JSONArray?): Set<String> = if (a == null) emptySet() else (0 until a.length()).map { a.optString(it) }.toSet()

    fun hechos(): Set<Proc> = lista(valor()?.optJSONArray("hechos")).mapNotNull { Proc.de(it) }.toSet()
    private fun autorizadosPor(correo: String): Set<Proc> =
        lista(valor()?.optJSONObject("autoriza")?.optJSONArray(correo.trim().lowercase())).mapNotNull { Proc.de(it) }.toSet()

    private fun denegados(correo: String): Set<Proc> =
        lista(valor()?.optJSONObject("deniega")?.optJSONArray(correo.trim().lowercase())).mapNotNull { Proc.de(it) }.toSet()

    fun permitidos(correo: String): Set<Proc> = Proc.values().filter { p ->
        if (p.porDefecto) p !in denegados(correo) else p in autorizadosPor(correo) && p in hechos()
    }.toSet()
    fun estaAutorizado(correo: String, p: Proc): Boolean =
        if (p.porDefecto) p !in denegados(correo) else p in autorizadosPor(correo)

    private fun guardar(hechos: Set<Proc>, autoriza: Map<String, Set<Proc>>, ahora: Long) {
        val a = JSONObject()
        autoriza.toSortedMap().forEach { (c, s) -> if (s.isNotEmpty()) a.put(c, JSONArray(s.map { it.clave }.sorted())) }

        escribir(JSONObject().put("hechos", JSONArray(hechos.map { it.clave }.sorted())).put("autoriza", a)
            .put("deniega", valor()?.optJSONObject("deniega") ?: JSONObject()), maxOf(ahora, ts() + 1), true)
    }

    private fun todasAutorizaciones(): Map<String, Set<Proc>> {
        val o = valor()?.optJSONObject("autoriza") ?: return emptyMap()
        return o.keys().asSequence().associateWith { lista(o.optJSONArray(it)).mapNotNull { c -> Proc.de(c) }.toSet() }
    }

    @Synchronized fun marcarHecho(p: Proc, ahora: Long = System.currentTimeMillis()): Boolean {
        val h = hechos()
        if (p in h) return false
        guardar(h + p, todasAutorizaciones(), ahora); return true
    }

    @Synchronized fun autorizar(correo: String, p: Proc, si: Boolean, ahora: Long = System.currentTimeMillis()) {
        val c = correo.trim().lowercase()
        if (p.porDefecto) {
            val v = valor() ?: JSONObject()
            val d = v.optJSONObject("deniega") ?: JSONObject()
            val cambios = denegados(c).let { if (si) it - p else it + p }
            if (cambios.isEmpty()) d.remove(c) else d.put(c, JSONArray(cambios.map { it.clave }.sorted()))
            v.put("deniega", d)
            escribir(v, maxOf(ahora, ts() + 1), true)
            return
        }
        val t = todasAutorizaciones().toMutableMap()
        t[c] = (t[c] ?: emptySet()).let { if (si) it + p else it - p }
        guardar(hechos(), t, ahora)
    }
}
