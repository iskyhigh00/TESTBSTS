package cl.reabill.monitor

import org.json.JSONArray
import org.json.JSONObject

object Backup {
    const val FORMATO = "billmon-respaldo"
    const val VERSION = 1

    const val MAX_SNAPSHOTS = 50

    const val MAX_LINEAS = 2000
    private const val MAX_EQUIPOS = 1000
    private const val MAX_LARGO_LINEA = 4000

    class Contenido(
        val historial: Map<String, List<AccountingData>>, val registro: List<String>,
        val mda: Map<String, String> = emptyMap()
    )

    fun build(historial: Map<String, List<AccountingData>>, registro: List<String>,
              appVersion: String, ahoraMs: Long, mda: Map<String, String> = emptyMap()): String {
        val h = JSONObject()
        for (serie in historial.keys.sorted()) {
            val a = JSONArray()
            historial.getValue(serie).sortedBy { it.timestamp }.forEach { a.put(it.toJson()) }
            h.put(serie, a)
        }
        val r = JSONArray()
        registro.takeLast(MAX_LINEAS).forEach { r.put(it) }
        val o = JSONObject().put("formato", FORMATO).put("version", VERSION).put("app", appVersion)
            .put("exportado", ahoraMs).put("historial", h).put("registro", r)
        if (mda.isNotEmpty()) {
            val m = JSONObject()
            mda.toSortedMap().forEach { (serie, v) -> if (serie.isNotBlank() && v.isNotBlank()) m.put(serie, v) }
            o.put("mda", m)
        }
        return o.toString(2)
    }

    fun parse(texto: String): Contenido {
        val o = JSONObject(texto)
        require(o.optString("formato") == FORMATO) { "el archivo no es un respaldo de BILLMON" }
        val v = o.optInt("version", 0)
        require(v in 1..VERSION) { "versión de respaldo $v no soportada por esta versión de la app" }
        val hist = LinkedHashMap<String, List<AccountingData>>()
        val h = o.optJSONObject("historial") ?: JSONObject()
        require(h.length() <= MAX_EQUIPOS) { "el respaldo trae demasiados equipos" }
        for (serie in h.keys()) {
            if (serie.isBlank() || serie == "—") continue
            val a = h.optJSONArray(serie) ?: continue
            val lista = ArrayList<AccountingData>()
            for (i in 0 until minOf(a.length(), MAX_SNAPSHOTS * 10)) {
                val e = a.optJSONObject(i) ?: continue
                if (!e.has("ts")) continue
                lista.add(AccountingData.fromJson(e))
            }
            if (lista.isNotEmpty()) hist[serie] = lista
        }
        val reg = ArrayList<String>()
        val r = o.optJSONArray("registro") ?: JSONArray()
        for (i in maxOf(0, r.length() - MAX_LINEAS) until r.length()) {
            val l = r.optString(i, "")
            if (l.isNotEmpty()) reg.add(l.take(MAX_LARGO_LINEA))
        }
        val mda = LinkedHashMap<String, String>()
        o.optJSONObject("mda")?.let { m ->
            for (serie in m.keys()) {
                if (serie.isBlank() || serie == "—" || mda.size >= MAX_EQUIPOS) continue
                val v = Mda.norm(m.optString(serie, "")) ?: continue
                mda[serie] = v
            }
        }
        return Contenido(hist, reg, mda)
    }

    fun mergeHistory(actual: List<AccountingData>, nuevo: List<AccountingData>): List<AccountingData> =
        (actual + nuevo).distinct().sortedBy { it.timestamp }.takeLast(MAX_SNAPSHOTS)

    fun mergeMda(actual: Map<String, String>, nuevo: Map<String, String>): Map<String, String> = nuevo + actual

    fun mergeLog(actual: List<String>, nuevo: List<String>): List<String> {
        val vistos = actual.toHashSet()
        val extra = nuevo.filter { vistos.add(it) }
        return (actual + extra).sortedBy { it.take(19) }.takeLast(MAX_LINEAS)
    }
}
