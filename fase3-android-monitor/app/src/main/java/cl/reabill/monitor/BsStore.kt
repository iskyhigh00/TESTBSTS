package cl.reabill.monitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class BsStore(context: Context) {
    private val prefs = context.getSharedPreferences("bs", Context.MODE_PRIVATE)
    val dir = java.io.File(context.filesDir, "bill-sets").apply { mkdirs() }

    private fun archivo(n: String) = java.io.File(dir, n)
    fun ts(n: String): Long = prefs.getLong("ts:$n", 0L)
    private fun quitado(n: String) = prefs.getString("q:$n", null) != null
    private fun nombreOk(n: String) = n.isNotBlank() && n.length <= 120 && n.all { it.isLetterOrDigit() || it in "._-" } && !n.startsWith(".")

    fun nombres(): List<String> = (dir.listFiles() ?: emptyArray()).filter { it.isFile }.map { it.name }.sorted()

    fun valorBs(n: String): JSONObject? {
        val f = archivo(n)
        if (f.isFile && ts(n) > 0) {
            val parsed = try { FirmwareFile.parse(n, f.readBytes()).first } catch (e: Exception) { null } ?: return null
            return JSONObject().put("kind", parsed.kind).put("label", parsed.label)
                .put("b64", java.util.Base64.getEncoder().encodeToString(parsed.data))
        }
        return if (quitado(n)) JSONObject().put("quitado", true) else null
    }

    fun nombresBs(): Set<String> = nombres().filter { ts(it) > 0 }.toSet() + prefs.all.keys.filter { it.startsWith("q:") }.map { it.removePrefix("q:") }

    fun agregar(n: String, datos: ByteArray, ahora: Long = System.currentTimeMillis()) {
        archivo(n).writeBytes(datos)
        prefs.edit().putLong("ts:$n", maxOf(ahora, ts(n) + 1)).remove("q:$n").apply()
    }

    fun asegurarHoras(ahora: Long = System.currentTimeMillis()) {
        nombres().filter { ts(it) == 0L && !quitado(it) }.forEach { prefs.edit().putLong("ts:$it", ahora).apply() }
    }

    fun quitar(n: String, ahora: Long = System.currentTimeMillis()) {
        archivo(n).delete()
        prefs.edit().putLong("ts:$n", maxOf(ahora, ts(n) + 1)).putString("q:$n", "1").apply()
    }

    fun aplicarBs(n: String, v: JSONObject, ts: Long): Boolean {
        if (!nombreOk(n) || ts <= ts(n)) return false
        if (v.optBoolean("quitado")) {
            archivo(n).delete(); prefs.edit().putLong("ts:$n", ts).putString("q:$n", "1").apply(); return true
        }
        val bytes = try { java.util.Base64.getDecoder().decode(v.optString("b64")) } catch (e: Exception) { aviso("bill-set de la nube", e); return false }

        if (FirmwareFile.parse(n, bytes).first == null) return false
        archivo(n).writeBytes(bytes)
        prefs.edit().putLong("ts:$n", ts).remove("q:$n").apply()
        return true
    }

    fun okTs(n: String): Long = prefs.getLong("okts:$n", 0L)
    fun okValor(n: String): JSONObject? = try { prefs.getString("ok:$n", null)?.let { JSONObject(it) } } catch (e: Exception) { aviso("leer bill-set probado", e); null }
    fun okNombres(): Set<String> = prefs.all.keys.filter { it.startsWith("ok:") }.map { it.removePrefix("ok:") }.toSet()
    fun modelosOk(n: String): Set<String> = okValor(n)?.optJSONArray("modelos")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()

    fun probado(n: String, modelo: String): Boolean = modelo.isNotBlank() && modelo != "—" && modelosOk(n).any { it.equals(modelo, true) }

    fun aplicarOk(n: String, v: JSONObject, ts: Long): Boolean {
        if (ts <= okTs(n)) return false
        prefs.edit().putString("ok:$n", v.toString()).putLong("okts:$n", ts).apply(); return true
    }

    fun marcarProbado(n: String, modelo: String, por: String, ahora: Long = System.currentTimeMillis()): Boolean {
        if (modelo.isBlank() || modelo == "—" || probado(n, modelo)) return false
        val ms = modelosOk(n) + modelo
        val v = JSONObject().put("modelos", JSONArray(ms.sorted())).put("por", por).put("fecha", ahora)
        prefs.edit().putString("ok:$n", v.toString()).putLong("okts:$n", maxOf(ahora, okTs(n) + 1)).apply()
        return true
    }
}
