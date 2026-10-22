package cl.reabill.monitor

import android.content.Context

class MdaStore(context: Context) {
    private val prefs = context.getSharedPreferences("equipos", Context.MODE_PRIVATE)

    fun mda(serie: String): String? = prefs.getString("mda:$serie", null)

    fun setMda(serie: String, mda: String?) {
        if (serie.isBlank() || serie == "—") return
        prefs.edit().apply { if (mda == null) remove("mda:$serie") else putString("mda:$serie", mda) }.apply()
    }

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
    fun setWeekly(w: WeeklyData) { prefs.edit().putString("semanal", w.toJson()).apply() }
}
