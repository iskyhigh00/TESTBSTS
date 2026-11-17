package cl.reabill.monitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CambiosStore(context: Context) {
    private val prefs = context.getSharedPreferences("cambios", Context.MODE_PRIVATE)

    class Estado(val boot: String, val app: String, val billSet: String, val interfaz: Int?, val cfg: String) {
        fun toJson(): JSONObject = JSONObject().put("boot", boot).put("app", app).put("bs", billSet)
            .put("if", interfaz ?: JSONObject.NULL).put("cfg", cfg)
        companion object {
            fun de(j: JSONObject) = Estado(j.optString("boot"), j.optString("app"), j.optString("bs"),
                if (j.isNull("if")) null else j.optInt("if"), j.optString("cfg"))
        }
    }

    class Evento(val t: Long, val por: String, val txt: String, val est: Estado)

    private fun lista(s: String): List<JSONObject> = try {
        val a = JSONArray(prefs.getString("ev:$s", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }
    } catch (e: Exception) { aviso("leer cambios del billetero", e); emptyList() }

    private fun guardar(s: String, l: List<JSONObject>) {
        prefs.edit().putString("ev:$s", JSONArray(l.sortedBy { it.optLong("t") }.takeLast(MAX)).toString()).apply()
    }

    fun series(): Set<String> = prefs.all.keys.filter { it.startsWith("ev:") }.map { it.removePrefix("ev:") }.toSet()

    fun eventos(s: String): List<Evento> = lista(s).map {
        Evento(it.optLong("t"), it.optString("por"), it.optString("txt"), Estado.de(it.optJSONObject("est") ?: JSONObject()))
    }.sortedBy { it.t }

    fun valorEvento(s: String, t: Long): JSONObject? = lista(s).firstOrNull { it.optLong("t") == t }

    fun ultimo(s: String): Estado? = eventos(s).lastOrNull()?.est

    fun mezclar(s: String, v: JSONObject, t: Long): Boolean {
        val l = lista(s)
        if (l.any { it.optLong("t") == t }) return false
        guardar(s, l + JSONObject(v.toString()).put("t", t))
        return true
    }

    fun registrar(serie: String, nuevo: Estado, por: String, ahora: Long = System.currentTimeMillis()): Evento? {
        val antes = ultimo(serie)
        val txt = if (antes == null) "Primera lectura" else describir(antes, nuevo).ifEmpty { return null }
        val previos = lista(serie)
        val t = maxOf(ahora, (previos.maxOfOrNull { it.optLong("t") } ?: 0L) + 1)
        val v = JSONObject().put("t", t).put("por", por).put("txt", txt).put("est", nuevo.toJson())
        guardar(serie, previos + v)
        return Evento(t, por, txt, nuevo)
    }

    companion object {
        const val MAX = 300

        private fun hex(h: String): ByteArray =
            try { ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() } } catch (e: Exception) { ByteArray(0) }

        fun describir(a: Estado, b: Estado): String {
            val p = ArrayList<String>()
            fun cmp(n: String, x: String, y: String) { if (x.isNotBlank() && y.isNotBlank() && x != y) p.add("$n $x → $y") }
            cmp("Boot", a.boot, b.boot); cmp("Aplicación", a.app, b.app); cmp("Bill-set", a.billSet, b.billSet)
            if (a.interfaz != null && b.interfaz != null && a.interfaz != b.interfaz)
                p.add("Interfaz ${Interfaz.nombre(a.interfaz)} → ${Interfaz.nombre(b.interfaz)}")
            val x = hex(a.cfg); val y = hex(b.cfg)
            if (x.isNotEmpty() && y.isNotEmpty()) {
                if (x.size != y.size) p.add("Configuración de ${x.size} a ${y.size} bytes")
                val dif = (0 until minOf(x.size, y.size)).filter { it !in Interfaz.RECALCULADOS && it != Interfaz.OFFSET && x[it] != y[it] }
                if (dif.isNotEmpty()) {
                    fun v(z: ByteArray) = dif.take(12).joinToString(", ") { (z[it].toInt() and 0xFF).toString() }
                    p.add("Configuración, bytes ${dif.take(12).joinToString(", ")}${if (dif.size > 12) " …" else ""}: ${v(x)} → ${v(y)}")
                }
            }
            return p.joinToString(" · ")
        }
    }
}

