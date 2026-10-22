package cl.reabill.monitor

import org.json.JSONArray
import org.json.JSONObject

object Mda {
    const val PREFIJO = 100000

    fun norm(raw: String?): String? {
        val d = (raw ?: "").filter { it.isDigit() }.take(12)
        if (d.isEmpty()) return null
        val n = d.toLongOrNull() ?: return null
        return (if (n < PREFIJO) n + PREFIJO else n).toString()
    }
}

class WeeklyData(
    val fecha: String,
    val datos: Map<String, Double>,
    val semanas: List<String> = emptyList(),
    val hist: Map<String, List<Double?>> = emptyMap()
) {
    fun pct(mda: String?): Double? = Mda.norm(mda)?.let { datos[it] }

    fun toJson(): String {
        val d = JSONObject(); datos.forEach { (k, v) -> d.put(k, v) }
        val h = JSONObject()
        hist.forEach { (k, l) -> h.put(k, JSONArray().also { a -> l.forEach { x -> a.put(if (x == null) JSONObject.NULL else JSONArray().put(x).put(JSONObject.NULL)) } }) }
        return JSONObject().put("fecha", fecha).put("datos", d).put("semanas", JSONArray(semanas)).put("hist", h).toString()
    }

    companion object {
        fun fromJson(s: String?): WeeklyData? = try {
            if (s.isNullOrBlank()) null else WeeklyImport.fromObject(JSONObject(s))
        } catch (_: Exception) { null }
    }
}

object WeeklyImport {
    private const val MAX_MAQUINAS = 5000

    fun parse(texto: String): WeeklyData {
        val t = texto.trim().removePrefix("﻿").trim()
        require(t.isNotEmpty()) { "el archivo está vacío" }
        if (t.startsWith("{")) {
            val raiz = JSONObject(t)
            if (raiz.optString("formato") == FORMATO_REACBILL) return fromReacbill(raiz)
            return fromObject(raiz) ?: throw IllegalArgumentException("el JSON no trae el reporte semanal (config.termoplanoPct). Abre REACBILL en la Bitácora con las semanas cargadas y vuelve a exportar")
        }
        return fromText(t)
    }

    const val FORMATO_REACBILL = "reacbill-aceptacion"
    const val VERSION_REACBILL = 1

    internal fun fromReacbill(o: JSONObject): WeeklyData {
        val v = o.optInt("version", 0)
        require(v == VERSION_REACBILL) { "el reporte es de la versión $v y esta app solo entiende la $VERSION_REACBILL; actualiza BILLMON" }
        val unidad = o.optString("unidad", "porcentaje")
        require(unidad == "porcentaje") { "unidad no soportada: $unidad" }
        val arr = o.optJSONArray("maquinas") ?: throw IllegalArgumentException("el reporte no trae la lista de máquinas")
        val datos = LinkedHashMap<String, Double>()
        for (i in 0 until minOf(arr.length(), MAX_MAQUINAS)) {
            val m = arr.optJSONObject(i) ?: continue
            val mda = Mda.norm(m.opt("mda")?.toString()) ?: continue
            if (m.isNull("aceptacion_billetes") || !m.has("aceptacion_billetes")) continue
            val pct = m.optDouble("aceptacion_billetes", Double.NaN)
            if (pct.isNaN() || pct < 0.0 || pct > 100.0) continue
            datos[mda] = Math.round(pct * 100.0) / 10000.0
        }
        return WeeklyData(fechaLarga(o.optString("semana", "")), datos)
    }

    internal fun fechaLarga(s: String): String {
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(s.trim()) ?: return s.trim()
        return "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}"
    }

    internal fun fromObject(o: JSONObject): WeeklyData? {
        val p = o.optJSONObject("config")?.optJSONObject("termoplanoPct")
            ?: o.optJSONObject("termoplanoPct")
            ?: (if (o.has("datos")) o else null)
            ?: return null
        val src = p.optJSONObject("datos") ?: return null
        val datos = LinkedHashMap<String, Double>()
        for (k in src.keys()) {
            val m = Mda.norm(k) ?: continue
            val a = src.optJSONArray(k)
            val v = if (a != null) (if (a.isNull(0)) null else a.optDouble(0, Double.NaN)) else src.optDouble(k, Double.NaN)
            val f = frac(v) ?: continue
            datos[m] = f
            if (datos.size >= MAX_MAQUINAS) break
        }
        val sem = ArrayList<String>()
        p.optJSONArray("semanas")?.let { for (i in 0 until it.length()) sem.add(it.optString(i)) }
        val hist = LinkedHashMap<String, List<Double?>>()
        p.optJSONObject("hist")?.let { h ->
            for (k in h.keys()) {
                val m = Mda.norm(k) ?: continue
                val a = h.optJSONArray(k) ?: continue
                hist[m] = (0 until a.length()).map { i ->
                    val par = a.optJSONArray(i)
                    if (par == null || par.isNull(0)) null else frac(par.optDouble(0, Double.NaN))
                }
            }
        }
        return WeeklyData(p.optString("fecha", ""), datos, sem, hist)
    }

    private fun fromText(t: String): WeeklyData {
        var fecha = ""
        val datos = LinkedHashMap<String, Double>()
        for (raw in t.lines()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            val low = l.lowercase()
            if (low.startsWith("fecha") || low.startsWith("semana")) {
                fecha = l.substringAfter(':', l.substringAfter('=', "")).trim(); continue
            }
            val partes = when {
                ';' in l || '\t' in l -> l.split(';', '\t')
                else -> {
                    val c = l.split(',')
                    when (c.size) { 2 -> c; 3 -> listOf(c[0], c[1] + "." + c[2]); else -> l.split(Regex("\\s+")) }
                }
            }.map { it.trim() }.filter { it.isNotEmpty() }
            if (partes.size < 2) continue
            val m = Mda.norm(partes[0]) ?: continue
            val v = partes[1].removeSuffix("%").trim().replace(',', '.').toDoubleOrNull()
            val f = frac(v) ?: continue
            datos[m] = f
            if (datos.size >= MAX_MAQUINAS) break
        }
        require(datos.isNotEmpty()) { "no encontré líneas \"MDA;%\" en el archivo" }
        return WeeklyData(fecha, datos)
    }

    private fun frac(v: Double?): Double? {
        if (v == null || v.isNaN() || v < 0) return null
        val f = if (v <= 1.0) v else v / 100.0
        return if (f > 1.0) null else Math.round(f * 10000.0) / 10000.0
    }
}
