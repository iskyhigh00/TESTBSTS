package cl.reabill.monitor

import android.content.Context
import android.os.Build
import org.json.JSONObject

data class NubeUi(
    val disponible: Boolean = false, val email: String = "", val ultima: Long = 0L,
    val trabajando: Boolean = false, val mensaje: String = "", val nombre: String = ""
)

class Nube(context: Context, private val mda: MdaStore, private val store: AccountingStore) {
    private val prefs = context.getSharedPreferences("nube", Context.MODE_PRIVATE)
    val etq = EtqStore(context)
    val rev = RevStore(context)
    val trab = TrabStore(context)
    val cfg = CfgStore(context)
    val usuarios = UsuariosStore(context)

    val disponible: Boolean get() = BuildConfig.SUPA_URL.isNotEmpty() && BuildConfig.SUPA_KEY.isNotEmpty()
    val email: String get() = prefs.getString("email", "") ?: ""
    val ultima: Long get() = prefs.getLong("ultima", 0L)

    val nombre: String? get() = if (email.isEmpty()) null else usuarios.nombreDe(email)

    fun guardarCuenta(email: String, clave: String) {
        prefs.edit().clear().putString("email", email.trim()).putString("clave_c", Cifra.cifrar(clave)).apply()
    }

    private fun clave(): String {
        prefs.getString("clave", null)?.let { plana ->
            prefs.edit().remove("clave").putString("clave_c", Cifra.cifrar(plana)).apply()
            return plana
        }
        return prefs.getString("clave_c", null)?.let { Cifra.descifrar(it) } ?: ""
    }

    fun claveCoincide(c: String): Boolean = c.isNotEmpty() && c == clave()

    fun quitarCuenta() { prefs.edit().clear().apply() }

    private fun enviado(k: String) = prefs.getString("env:$k", null)
    private fun olvidarEnviado(k: String) { prefs.edit().remove("env:$k").apply() }
    private fun marcarEnviado(k: String, d: String) { prefs.edit().putString("env:$k", d).apply() }

    private class Local(val ts: Long, val valor: JSONObject) {
        val digest: String get() = "$ts:${valor.toString().hashCode()}"
    }

    private fun localMda(s: String): Local? {
        val ts = mda.mdaTs(s)
        if (ts == 0L) return null
        return Local(ts, JSONObject().put("v", mda.mda(s) ?: JSONObject.NULL))
    }
    private fun localInfo(s: String): Local? = mda.info(s)?.let {
        Local(it.ts, JSONObject().put("m", it.modelo).put("f", it.firmware).put("b", it.billSet))
    }
    private fun localIfaz(s: String): Local? = mda.ifaz(s)?.let { Local(it.ts, JSONObject(it.toJson())) }
    private fun localSemanal(): Local? = mda.weekly()?.let { Local(mda.weeklyTs(), JSONObject().put("w", it.toJson())) }

    private fun localEtq(id: String): Local? = etq.valor(id)?.let { Local(etq.ts(id), it) }
    private fun localCfg(): Local? = cfg.valor()?.let { Local(cfg.ts(), it) }
    private fun localRev(m: String): Local? = rev.valor(m)?.let { Local(rev.ts(m), it) }

    private fun local(clave: String): Local? = when {
        clave.startsWith("etq:") -> localEtq(clave.removePrefix("etq:"))
        clave == "cfg" -> localCfg()
        clave.startsWith("rev:") -> localRev(clave.removePrefix("rev:"))
        clave.startsWith("trab:") -> trab.valor(clave.removePrefix("trab:"))?.let { Local(trab.ts(clave.removePrefix("trab:")), it) }
        clave.startsWith("mda:") -> localMda(clave.removePrefix("mda:"))
        clave.startsWith("info:") -> localInfo(clave.removePrefix("info:"))
        clave.startsWith("ifaz:") -> localIfaz(clave.removePrefix("ifaz:"))
        clave == "semanal" -> localSemanal()
        else -> null
    }

    private fun serieOk(s: String) = s.isNotBlank() && s != "—" && !DemoClient.isDemoSerial(s)

    private fun aplicar(f: FilaNube, lecturas: MutableMap<String, MutableList<AccountingData>>): Boolean {
        val k = f.clave
        try {
            when {
                k.startsWith("mda:") -> {
                    val s = k.removePrefix("mda:"); if (!serieOk(s)) return false
                    if (f.ts <= mda.mdaTs(s)) return false
                    val v = if (f.valor.isNull("v")) null else Mda.norm(f.valor.optString("v"))?.takeIf { Mda.valido(it) }
                    if (!f.valor.isNull("v") && v == null) return false
                    mda.setMdaConTs(s, v, f.ts)
                }
                k.startsWith("info:") -> {
                    val s = k.removePrefix("info:"); if (!serieOk(s)) return false
                    if (f.ts <= (mda.info(s)?.ts ?: 0L)) return false
                    mda.setInfoConTs(s, MdaStore.Info(f.valor.optString("m"), f.valor.optString("f"), f.valor.optString("b"), f.ts))
                }
                k.startsWith("ifaz:") -> {
                    val s = k.removePrefix("ifaz:"); if (!serieOk(s)) return false
                    val remoto = IfazRegistro.fromJson(f.valor.toString()) ?: return false
                    val actual = mda.ifaz(s)
                    val fusion = IfazRegistro.fusionar(actual, remoto) ?: return false
                    if (actual != null && actual.toJson() == fusion.toJson()) return false
                    mda.setIfaz(s, fusion)
                }
                k.startsWith("lec:") -> {
                    val s = k.removePrefix("lec:").substringBeforeLast(':'); if (!serieOk(s)) return false
                    lecturas.getOrPut(s) { ArrayList() }.add(AccountingData.fromJson(f.valor))
                    return false
                }
                k.startsWith("etq:") -> {
                    val id = k.removePrefix("etq:"); if (id.isBlank()) return false
                    if (f.ts <= etq.ts(id)) return false
                    etq.put(id, f.valor, f.ts)
                }
                k == "cfg" -> {
                    if (f.ts <= cfg.ts()) return false
                    cfg.put(f.valor, f.ts)
                }
                k.startsWith("rev:") -> {
                    val m = k.removePrefix("rev:"); if (m.isBlank()) return false
                    if (f.ts <= rev.ts(m)) return false
                    rev.put(m, f.valor, f.ts)
                }
                k == "usuarios" -> {
                    if (f.ts <= usuarios.ts()) return false
                    usuarios.put(f.valor, f.ts)
                }
                k.startsWith("trab:") -> {
                    val m = k.removePrefix("trab:"); if (m.isBlank()) return false
                    if (f.ts <= trab.ts(m)) return false
                    trab.put(m, f.valor, f.ts)
                }
                k == "semanal" -> {
                    if (f.ts <= mda.weeklyTs()) return false

                    val w = try { WeeklyImport.parse(f.valor.opt("w")?.toString() ?: return false) } catch (e: Exception) { aviso("reporte semanal de la nube", e); return false }
                    mda.setWeeklyConTs(w, f.ts)
                }
                else -> return false
            }

            if (!k.startsWith("ifaz:")) local(k)?.let { marcarEnviado(k, it.digest) }
            return true
        } catch (_: Exception) { return false }
    }

    class Resultado(val ok: Boolean, val mensaje: String, val bajados: Int, val subidos: Int, val huboCambios: Boolean)

    private var http: NubeHttp? = null
    private fun cliente(): NubeHttp = http ?: NubeHttp(BuildConfig.SUPA_URL, BuildConfig.SUPA_KEY).also { http = it }

    private fun entrar(forzar: Boolean = false) {
        val c = cliente()
        if (c.sesion && !forzar) return
        val e = email; val p = clave()
        if (e.isEmpty() || p.isEmpty()) throw NubeError("Falta la cuenta: toca «Conectar».")
        c.login(e, p)
    }

    fun probarCuenta(email: String, clave: String): String? = try {
        NubeHttp(BuildConfig.SUPA_URL, BuildConfig.SUPA_KEY).login(email.trim(), clave); null
    } catch (e: NubeError) { e.usuario } catch (e: java.io.IOException) { "Sin conexión: ${e.message ?: "revisa internet"}" }

    @Synchronized
    fun sincronizar(): Resultado {
        if (!disponible) return Resultado(false, "Esta versión no trae la conexión a la nube.", 0, 0, false)
        return try {
            try { entrar() ; bajarYSubir() } catch (e: NubeError) {
                if (e.http == 401) { entrar(forzar = true); bajarYSubir() } else throw e
            }
        } catch (e: NubeError) {
            Resultado(false, e.usuario, 0, 0, false)
        } catch (e: java.net.UnknownHostException) {
            Resultado(false, "Sin conexión: se sincroniza cuando vuelva internet.", 0, 0, false)
        } catch (e: java.io.IOException) {
            Resultado(false, "Sin conexión o sin respuesta (${e.message ?: e.javaClass.simpleName}).", 0, 0, false)
        } catch (e: Exception) {
            Resultado(false, "Error al sincronizar: ${e.message ?: e.javaClass.simpleName}", 0, 0, false)
        }
    }

    private fun quitarBorradas(c: NubeHttp): Boolean {
        var cambio = false
        val enNube = HashMap<String, Set<String>>()
        for (pref in listOf("rev:", "etq:", "trab:")) enNube[pref] = c.claves(pref)
        fun revisar(pref: String, ids: Set<String>, quitar: (String) -> Unit) {
            for (id in ids) {
                val k = pref + id
                if (k in enNube.getValue(pref)) continue
                val l = local(k) ?: continue
                if (enviado(k) == l.digest) { quitar(id); olvidarEnviado(k); cambio = true }
            }
        }
        revisar("rev:", rev.ids(), rev::quitar)
        revisar("etq:", etq.ids(), etq::quitar)
        revisar("trab:", trab.ids(), trab::quitar)
        return cambio
    }

    private fun bajarYSubir(): Resultado {
        val c = cliente()

        val (filas, cursor) = c.traer(prefs.getString("cursor", null))
        val lecturas = HashMap<String, MutableList<AccountingData>>()
        var bajados = 0
        for (f in filas) if (aplicar(f, lecturas)) bajados++
        var huboCambios = bajados > 0
        for ((s, lista) in lecturas) {
            val nuevas = store.mergeIn(s, lista)
            if (nuevas > 0) { bajados += nuevas; huboCambios = true }
        }

        if (quitarBorradas(c)) huboCambios = true

        val por = nombre ?: Build.MODEL ?: "teléfono"
        val claves = LinkedHashSet<String>()
        mda.mdaSeries().filter { serieOk(it) }.forEach { claves.add("mda:$it") }
        mda.infoSeries().filter { serieOk(it) }.forEach { claves.add("info:$it") }
        mda.ifazSeries().filter { serieOk(it) }.forEach { claves.add("ifaz:$it") }
        claves.add("semanal")
        claves.add("cfg")
        etq.ids().forEach { claves.add("etq:$it") }
        rev.ids().forEach { claves.add("rev:$it") }
        val cambiadas = ArrayList<FilaNube>(); val digests = HashMap<String, String>()
        for (k in claves) {
            val l = local(k) ?: continue
            if (enviado(k) == l.digest) continue
            cambiadas.add(FilaNube(k, l.valor, l.ts)); digests[k] = l.digest
        }
        val lecFilas = ArrayList<FilaNube>(); val lecDigest = HashMap<String, String>()
        for (s in store.serials()) {
            val h = store.history(s)
            val d = "${h.size}:${h.firstOrNull()?.timestamp ?: 0}"
            if (enviado("lec:$s") == d) continue
            h.forEach { lecFilas.add(FilaNube("lec:$s:${it.timestamp}", it.toJson(), it.timestamp)) }
            lecDigest["lec:$s"] = d
        }
        if (cambiadas.isNotEmpty()) { c.subir(cambiadas, por, sobrescribir = true); digests.forEach { (k, d) -> marcarEnviado(k, d) } }
        if (lecFilas.isNotEmpty()) { c.subir(lecFilas, por, sobrescribir = false); lecDigest.forEach { (k, d) -> marcarEnviado(k, d) } }

        if (cursor != null) prefs.edit().putString("cursor", cursor).apply()
        prefs.edit().putLong("ultima", System.currentTimeMillis()).apply()
        val subidos = cambiadas.size + lecFilas.size
        val msg = when {
            bajados == 0 && subidos == 0 -> "Todo al día."
            else -> "Bajó $bajados y subió $subidos."
        }
        return Resultado(true, msg, bajados, subidos, huboCambios)
    }
}
