package cl.reabill.monitor

import android.content.Context

class MdaStore(context: Context) {
    private val prefs = context.getSharedPreferences("equipos", Context.MODE_PRIVATE)

    fun mda(serie: String): String? = prefs.getString("mda:$serie", null)

    fun setMda(serie: String, mda: String?) = setMdaConTs(serie, mda, System.currentTimeMillis())

    fun setMdaConTs(serie: String, mda: String?, ts: Long) {
        if (serie.isBlank() || serie == "—") return
        prefs.edit().apply {
            if (mda == null) remove("mda:$serie") else putString("mda:$serie", mda)
            putLong("mdats:$serie", ts)
        }.apply()
    }

    fun mdaTs(serie: String): Long = prefs.getLong("mdats:$serie", if (prefs.contains("mda:$serie")) 1L else 0L)

    fun mdaSeries(): Set<String> = prefs.all.keys.filter { it.startsWith("mda:") || it.startsWith("mdats:") }
        .map { it.substringAfter(':') }.toSet()

    class Info(val modelo: String, val firmware: String, val billSet: String, val ts: Long)

    fun setInfo(serie: String, modelo: String, firmware: String, billSet: String) {
        if (serie.isBlank() || serie == "—") return
        val j = org.json.JSONObject().put("m", modelo).put("f", firmware).put("b", billSet).put("t", System.currentTimeMillis())
        prefs.edit().putString("info:$serie", j.toString()).apply()
    }

    fun setInfoConTs(serie: String, i: Info) {
        if (serie.isBlank() || serie == "—") return
        val j = org.json.JSONObject().put("m", i.modelo).put("f", i.firmware).put("b", i.billSet).put("t", i.ts)
        prefs.edit().putString("info:$serie", j.toString()).apply()
    }

    fun infoSeries(): Set<String> = prefs.all.keys.filter { it.startsWith("info:") }.map { it.removePrefix("info:") }.toSet()
    fun ifazSeries(): Set<String> = prefs.all.keys.filter { it.startsWith("ifaz:") }.map { it.removePrefix("ifaz:") }.toSet()

    fun info(serie: String): Info? = try {
        prefs.getString("info:$serie", null)?.let { org.json.JSONObject(it) }
            ?.let { Info(it.optString("m"), it.optString("f"), it.optString("b"), it.optLong("t")) }
    } catch (_: Exception) { null }

    fun versionVista(): String? = prefs.getString("version_vista", null)
    fun setVersionVista(v: String) { prefs.edit().putString("version_vista", v).apply() }

    fun ifaz(serie: String): IfazRegistro? = IfazRegistro.fromJson(prefs.getString("ifaz:$serie", null))
    fun setIfaz(serie: String, r: IfazRegistro) {
        if (serie.isBlank() || serie == "—") return
        prefs.edit().putString("ifaz:$serie", r.toJson()).apply()
    }
    fun removeIfaz(serie: String) { prefs.edit().remove("ifaz:$serie").apply() }

    fun all(): Map<String, String> =
        prefs.all.filterKeys { it.startsWith("mda:") }.mapKeys { it.key.removePrefix("mda:") }
            .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun mergeIn(nuevo: Map<String, String>): Int {
        val actual = all()
        val agregar = Backup.mergeMda(actual, nuevo)
        agregar.filterKeys { it !in actual }.forEach { (s, m) -> setMda(s, m) }
        return agregar.keys.count { it !in actual }
    }

    fun weekly(): WeeklyData? = WeeklyData.fromJson(prefs.getString("semanal", null))
    fun setWeekly(w: WeeklyData) = setWeeklyConTs(w, System.currentTimeMillis())
    fun setWeeklyConTs(w: WeeklyData, ts: Long) { prefs.edit().putString("semanal", w.toJson()).putLong("semanal_ts", ts).apply() }
    fun weeklyTs(): Long = prefs.getLong("semanal_ts", if (prefs.contains("semanal")) 1L else 0L)
}
