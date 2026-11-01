package cl.reabill.monitor

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class FilaNube(val clave: String, val valor: JSONObject, val ts: Long, val por: String? = null)

data class PeticionNube(val method: String, val url: URL, val body: String?, val headers: Map<String, String>)
fun interface TransporteNube {
    fun ejecutar(peticion: PeticionNube): Pair<Int, String>
}

class NubeError(val usuario: String, val http: Int = 0) : IOException(usuario)

class NubeHttp(private val base: String, private val apiKey: String, private val transporte: TransporteNube? = null) {
    private var token: String? = null

    private fun u(path: String) = URL(base.trimEnd('/') + path)

    private fun llamar(method: String, url: URL, body: String?, extra: Map<String, String> = emptyMap(), auth: Boolean = true): Pair<Int, String> {
        val headers = mutableMapOf("apikey" to apiKey, "Accept" to "application/json")
        if (auth) headers["Authorization"] = "Bearer ${token ?: apiKey}"
        if (body != null) headers["Content-Type"] = "application/json"
        headers.putAll(extra)
        transporte?.let { return it.ejecutar(PeticionNube(method, url, body, headers)) }
        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val txt = stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            return code to txt
        } finally { c.disconnect() }
    }

    private fun detalle(txt: String): String = try {
        val o = JSONObject(txt)
        (o.optString("message").ifEmpty { o.optString("msg").ifEmpty { o.optString("error_description") } }).take(140)
    } catch (_: Exception) { txt.take(100) }

    fun login(email: String, password: String) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val (code, txt) = llamar("POST", u("/auth/v1/token?grant_type=password"), body, auth = false)
        if (code == 400 || code == 401 || code == 422) throw NubeError("Correo o clave incorrectos.", code)
        if (code !in 200..299) throw NubeError("No se pudo iniciar sesión (HTTP $code): ${detalle(txt)}", code)
        token = JSONObject(txt).optString("access_token").ifEmpty { throw NubeError("Respuesta de sesión sin token.", code) }
    }

    val sesion: Boolean get() = token != null

    private fun revisar(code: Int, txt: String) {
        when {
            code in 200..299 -> return
            code == 401 -> { token = null; throw NubeError("La sesión venció.", 401) }
            code == 404 -> throw NubeError("Falta la tabla compartida billmon_kv: hay que correr el SQL en Supabase.", 404)
            code == 403 -> throw NubeError("Sin permiso en la tabla billmon_kv (revisa la cuenta y las políticas).", 403)
            else -> throw NubeError("Error de la nube (HTTP $code): ${detalle(txt)}", code)
        }
    }

    fun traer(desde: String?, pagina: Int = 200): Pair<List<FilaNube>, String?> {
        require(pagina > 0)
        val out = ArrayList<FilaNube>()
        var maxAct = desde
        var ultimoTiempo: String? = null
        var ultimaClave: String? = null
        fun literal(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        while (true) {

            val desdeFiltro = if (desde == null) "" else "&actualizado=gte." + URLEncoder.encode(desde, "UTF-8")
            val siguiente = if (ultimoTiempo == null) "" else "&or=" + URLEncoder.encode(
                "(actualizado.gt.${literal(ultimoTiempo)},and(actualizado.eq.${literal(ultimoTiempo)},clave.gt.${literal(ultimaClave!!)}))", "UTF-8")

            val url = u("/rest/v1/billmon_kv?select=clave,valor,ts,por,actualizado$desdeFiltro$siguiente&order=actualizado.asc,clave.asc&limit=$pagina")
            val (code, txt) = llamar("GET", url, null)
            revisar(code, txt)
            val arr = JSONArray(txt)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val v = o.optJSONObject("valor") ?: continue
                out.add(FilaNube(o.getString("clave"), v, o.optLong("ts"), o.optString("por").takeIf { it.isNotBlank() }))
                val a = o.optString("actualizado")
                if (a.isNotEmpty() && (maxAct == null || a > maxAct)) maxAct = a
            }
            if (arr.length() < pagina) break
            val ultimo = arr.getJSONObject(arr.length() - 1)
            val tiempo = ultimo.getString("actualizado")
            val clave = ultimo.getString("clave")
            if (tiempo == ultimoTiempo && clave == ultimaClave) throw NubeError("La paginación de la nube no avanzó.")
            ultimoTiempo = tiempo
            ultimaClave = clave
        }
        return out to maxAct
    }

    fun controlesCompartidos(): Map<String, FilaNube> {
        val (code, txt) = llamar("GET", u("/rest/v1/billmon_kv?select=clave,valor,ts,por&clave=in.(cfg,permisos,usuarios)&limit=3"), null)
        revisar(code, txt)
        val arr = JSONArray(txt)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val v = o.optJSONObject("valor") ?: return@mapNotNull null
            val k = o.optString("clave")
            k to FilaNube(k, v, o.optLong("ts"), o.optString("por").takeIf { it.isNotBlank() })
        }.toMap()
    }

    fun claves(prefijo: String, pagina: Int = 1000): Set<String> {
        val out = HashSet<String>()
        var offset = 0
        while (true) {
            val url = u("/rest/v1/billmon_kv?select=clave&clave=like." + URLEncoder.encode("$prefijo*", "UTF-8") + "&order=clave.asc&limit=$pagina&offset=$offset")
            val (code, txt) = llamar("GET", url, null)
            revisar(code, txt)
            val arr = JSONArray(txt)
            for (i in 0 until arr.length()) out.add(arr.getJSONObject(i).getString("clave"))
            if (arr.length() < pagina) break
            offset += pagina
        }
        return out
    }

    fun subir(filas: List<FilaNube>, por: String, sobrescribir: Boolean, lote: Int = 200) {
        var i = 0
        while (i < filas.size) {
            val parte = filas.subList(i, minOf(i + lote, filas.size))
            val arr = JSONArray()
            parte.forEach { arr.put(JSONObject().put("clave", it.clave).put("valor", it.valor).put("ts", it.ts).put("por", it.por ?: por)) }
            val pref = if (sobrescribir) "resolution=merge-duplicates,return=minimal" else "resolution=ignore-duplicates,return=minimal"
            val (code, txt) = llamar("POST", u("/rest/v1/billmon_kv?on_conflict=clave"), arr.toString(), mapOf("Prefer" to pref))
            revisar(code, txt)
            i += lote
        }
    }
}
