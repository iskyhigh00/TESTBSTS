package cl.reabill.monitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AccountingStore(context: Context) {
    private val prefs = context.getSharedPreferences("contabilidad", Context.MODE_PRIVATE)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<AccountingData>>()

    fun save(serial: String, data: AccountingData) {
        if (serial.isBlank() || serial == "—") return
        val arr = JSONArray(prefs.getString(serial, "[]"))
        arr.put(data.toJson())

        val trimmed = JSONArray()
        val from = maxOf(0, arr.length() - 50)
        for (i in from until arr.length()) trimmed.put(arr.get(i))
        prefs.edit().putString(serial, trimmed.toString()).apply()
        cache.remove(serial)
    }

    fun serials(): List<String> = prefs.all.keys.filter { it.isNotBlank() && it != "—" && !DemoClient.isDemoSerial(it) }.sorted()

    fun clear(serial: String) { prefs.edit().remove(serial).apply(); cache.remove(serial) }

    fun mergeIn(serial: String, incoming: List<AccountingData>): Int {
        if (serial.isBlank() || serial == "—") return 0
        val current = history(serial)
        val merged = Backup.mergeHistory(current, incoming)
        val arr = JSONArray()
        merged.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(serial, arr.toString()).apply()
        cache.remove(serial)
        return incoming.distinct().count { it !in current }
    }

    fun history(serial: String): List<AccountingData> {
        if (serial.isBlank()) return emptyList()
        cache[serial]?.let { return it }
        val arr = JSONArray(prefs.getString(serial, "[]"))
        return (0 until arr.length()).map { AccountingData.fromJson(arr.getJSONObject(it)) }
            .sortedByDescending { it.timestamp }.also { cache[serial] = it }
    }
}
