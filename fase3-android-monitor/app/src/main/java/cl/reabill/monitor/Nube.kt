package cl.reabill.monitor

import android.content.Context
import org.json.JSONObject

data class NubeUi(
    val disponible: Boolean = false, val email: String = "", val ultima: Long = 0L,
    val trabajando: Boolean = false, val mensaje: String = "", val nombre: String = ""
)

class Nube(
    context: Context, private val mda: MdaStore, private val store: AccountingStore,
    private val supaUrl: String = BuildConfig.SUPA_URL,
    private val supaKey: String = BuildConfig.SUPA_KEY,
    private val crearCliente: (String, String) -> NubeHttp = { u, k -> NubeHttp(u, k) }
) {
    private val prefs = context.getSharedPreferences("nube", Context.MODE_PRIVATE)
    init {

        if (!prefs.getBoolean("admin_cfg_v2", false))
            prefs.edit().remove("cursor").putBoolean("admin_cfg_v2", true).apply()
    }
    val etq = EtqStore(context)
    val rev = RevStore(context)
    val trab = TrabStore(context)
    val cfg = CfgStore(context)
    val usuarios = UsuariosStore(context)
    val permisos = PermisosStore(context)
    val bs = BsStore(context)
    val registros = RegistrosStore(context)
    private val autores = AutoresPendientesStore(context)
    private var descargasAplicadas = 0

    val disponible: Boolean get() = supaUrl.isNotEmpty() && supaKey.isNotEmpty()
    val email: String get() = prefs.getString("email", "") ?: ""
    val ultima: Long get() = prefs.getLong("ultima", 0L)

    var configuracionVerificada: Boolean = prefs.getString("verif", null).let { it != null && it.equals(prefs.getString("email", ""), true) }
        private set(v) {
            field = v
            prefs.edit().apply { if (v) putString("verif", email) else remove("verif") }.apply()
        }

    val requiereUsuario: Boolean get() = disponible && (email.isEmpty() || email.equals(Permisos.CUENTA_COMPARTIDA, true) || prefs.getString("invalida", null) != null)

    val esAdmin: Boolean get() = email.isNotEmpty() && Permisos.esAdmin(email, usuarios.rolDe(email))

    val sesionLista: Boolean get() = email.isNotEmpty() && !requiereUsuario && (esAdmin || configuracionVerificada)

    fun acceso(): Acceso = if (!sesionLista) Acceso.NINGUNO else Acceso(esAdmin, permisos.permitidos(email))

    val nombre: String? get() = if (email.isEmpty()) null else usuarios.nombreDe(email)

    fun guardarCuenta(email: String, clave: String) {
        conservarAutoresPendientes()
        val mantener = configuracionVerificada && this.email.equals(email.trim(), true)
        prefs.edit().clear().putString("email", email.trim()).putString("clave_c", Cifra.cifrar(clave)).apply()
        configuracionVerificada = mantener
        http = null
    }

    fun cambiarUsuario(email: String, clave: String) {
        conservarAutoresPendientes()
        configuracionVerificada = false
        prefs.edit().remove("clave").remove("invalida").remove("cursor").putString("email", email.trim()).putString("clave_c", Cifra.cifrar(clave)).apply()
        http = null
    }

    private fun clave(): String {
        prefs.getString("clave", null)?.let { plana ->
            prefs.edit().remove("clave").putString("clave_c", Cifra.cifrar(plana)).apply()
            return plana
        }
        return prefs.getString("clave_c", null)?.let { Cifra.descifrar(it) } ?: ""
    }

    fun claveCoincide(c: String): Boolean = c.isNotEmpty() && c == clave()

    fun quitarCuenta() { conservarAutoresPendientes(); configuracionVerificada = false; prefs.edit().clear().apply() }

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
        clave == "permisos" -> permisos.valor()?.let { Local(permisos.ts(), it) }
        clave.startsWith("bs:") -> clave.removePrefix("bs:").let { n -> bs.valorBs(n)?.let { Local(bs.ts(n), it) } }
        clave.startsWith("bsok:") -> clave.removePrefix("bsok:").let { n -> bs.okValor(n)?.let { Local(bs.okTs(n), it) } }
        clave.startsWith("reg:") -> clave.removePrefix("reg:").let { id -> registros.valor(id)?.let { Local(registros.ts(id), it) } }
        clave.startsWith("trab:") -> trab.valor(clave.removePrefix("trab:"))?.let { Local(trab.ts(clave.removePrefix("trab:")), it) }
        clave.startsWith("mda:") -> localMda(clave.removePrefix("mda:"))
        clave.startsWith("info:") -> localInfo(clave.removePrefix("info:"))
        clave.startsWith("ifaz:") -> localIfaz(clave.removePrefix("ifaz:"))
        clave == "semanal" -> localSemanal()
        else -> null
    }

    private fun serieOk(s: String) = s.isNotBlank() && s != "—" && !DemoClient.isDemoSerial(s)

    private fun clavesLocales(): Set<String> = linkedSetOf("semanal", "cfg", "permisos").apply {
        mda.mdaSeries().filter { serieOk(it) }.forEach { add("mda:$it") }
        mda.infoSeries().filter { serieOk(it) }.forEach { add("info:$it") }
        mda.ifazSeries().filter { serieOk(it) }.forEach { add("ifaz:$it") }
        etq.ids().forEach { add("etq:$it") }
        rev.ids().forEach { add("rev:$it") }
        bs.nombresBs().forEach { add("bs:$it") }
        bs.okNombres().forEach { add("bsok:$it") }
    }

    private fun conservarAutoresPendientes() {
        for (k in clavesLocales()) {
            val l = local(k) ?: continue
            if (enviado(k) != l.digest) autores.conservar(k, l.digest, email)
        }
        for (s in store.serials()) for (lectura in store.history(s)) {
            val l = Local(lectura.timestamp, lectura.toJson())
            autores.conservar("lec:$s:${lectura.timestamp}", l.digest, email)
        }
    }

    private fun aplicar(f: FilaNube, lecturas: MutableMap<String, MutableList<AccountingData>>, autoritativo: Boolean = false): Boolean {
        val k = f.clave
        try {
            val remoto = Local(f.ts, f.valor)
            if (local(k)?.digest == remoto.digest) {
                autores.recibido(k, remoto.digest, f.por)
                if (!k.startsWith("ifaz:")) marcarEnviado(k, remoto.digest)
                if (k == "permisos") permisos.confirmarVersion(f.valor, f.ts)
            }
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
                    autores.recibido(k, Local(f.ts, f.valor).digest, f.por)
                    return false
                }
                k.startsWith("etq:") -> {
                    val id = k.removePrefix("etq:"); if (id.isBlank()) return false
                    if (f.ts <= etq.ts(id)) return false
                    etq.put(id, f.valor, f.ts)
                }
                k == "cfg" -> {

                    if (esAdmin && f.ts <= cfg.ts()) return false
                    if (f.ts == cfg.ts() && f.valor.toString() == cfg.valor()?.toString()) return false
                    cfg.put(f.valor, f.ts)
                }
                k.startsWith("rev:") -> {
                    val m = k.removePrefix("rev:"); if (m.isBlank()) return false
                    if (f.ts <= rev.ts(m)) return false
                    rev.put(m, f.valor, f.ts)
                }
                k == "permisos" -> {
                    if (!autoritativo && f.ts <= permisos.ts()) return false
                    if (!permisos.aplicarRemoto(f.valor, f.ts, esAdmin)) return false
                }
                k.startsWith("bs:") -> { if (!bs.aplicarBs(k.removePrefix("bs:"), f.valor, f.ts)) return false }
                k.startsWith("bsok:") -> { if (!bs.aplicarOk(k.removePrefix("bsok:"), f.valor, f.ts)) return false }
                k == "usuarios" -> {
                    if (!autoritativo && f.ts <= usuarios.ts()) return false
                    if (f.ts == usuarios.ts() && CfgStore.canon(f.valor) == CfgStore.canon(usuarios.valor())) return false
                    usuarios.put(f.valor, f.ts)
                }
                k.startsWith("trab:") -> {
                    val m = k.removePrefix("trab:"); if (m.isBlank()) return false
                    if (f.ts <= trab.ts(m)) return false
                    trab.put(m, f.valor, f.ts)
                }
                k == "semanal" -> {

                    if (if (esAdmin) f.ts <= mda.weeklyTs() else f.ts == mda.weeklyTs()) return false

                    val w = try { WeeklyImport.parse(f.valor.opt("w")?.toString() ?: return false) } catch (e: Exception) { aviso("reporte semanal de la nube", e); return false }
                    mda.setWeeklyConTs(w, f.ts)
                }
                else -> return false
            }

            if (!k.startsWith("ifaz:")) local(k)?.let { marcarEnviado(k, it.digest) }
            autores.recibido(k, Local(f.ts, f.valor).digest, f.por)
            descargasAplicadas++
            return true
        } catch (_: Exception) { return false }
    }

    class Resultado(val ok: Boolean, val mensaje: String, val bajados: Int, val subidos: Int, val huboCambios: Boolean)

    private var http: NubeHttp? = null
    private fun cliente(): NubeHttp = http ?: crearCliente(supaUrl, supaKey).also { http = it }

    @Synchronized
    fun borrarPendientesRev(): String? {
        if (!esAdmin) return "Solo el admin puede borrar la lista."
        return try {
            try { entrar(); cliente().borrarPendientes("rev:") } catch (e: NubeError) {
                if (e.http == 401) { entrar(forzar = true); cliente().borrarPendientes("rev:") } else throw e
            }
            for (r in rev.pendientes()) { rev.quitar(r.mda); olvidarEnviado("rev:${r.mda}") }
            null
        } catch (e: NubeError) {
            if (e.http == 403) "Sin permiso en Supabase para borrar (la regla de billmon_kv no lo deja)." else e.usuario
        } catch (e: java.io.IOException) { "Sin conexión: inténtalo con internet." }
    }

    private fun entrar(forzar: Boolean = false) {
        val c = cliente()
        if (c.sesion && !forzar) return
        val e = email; val p = clave()
        if (e.isEmpty() || p.isEmpty()) throw NubeError("Falta la cuenta: toca «Conectar».")
        try { c.login(e, p); if (prefs.getString("invalida", null) != null) prefs.edit().remove("invalida").apply() }
        catch (x: NubeError) { if (x.http == 400 || x.http == 401 || x.http == 422) prefs.edit().putString("invalida", "1").apply(); throw x }
    }

    fun probarCuenta(email: String, clave: String): String? = try {
        crearCliente(supaUrl, supaKey).login(email.trim(), clave); null
    } catch (e: NubeError) { e.usuario } catch (e: java.io.IOException) { "Sin conexión: ${e.message ?: "revisa internet"}" }

    @Synchronized
    fun sincronizar(): Resultado {
        if (!disponible) return Resultado(false, "Esta versión no trae la conexión a la nube.", 0, 0, false)
        descargasAplicadas = 0
        return try {
            conservarAutoresPendientes()
            try { entrar() ; bajarYSubir() } catch (e: NubeError) {
                if (e.http == 401) { entrar(forzar = true); bajarYSubir() } else throw e
            }
        } catch (e: NubeError) {
            Resultado(false, e.usuario, descargasAplicadas, 0, descargasAplicadas > 0)
        } catch (e: java.net.UnknownHostException) {
            Resultado(false, "Sin conexión: se sincroniza cuando vuelva internet.", descargasAplicadas, 0, descargasAplicadas > 0)
        } catch (e: java.io.IOException) {
            Resultado(false, "Sin conexión o sin respuesta (${e.message ?: e.javaClass.simpleName}).", descargasAplicadas, 0, descargasAplicadas > 0)
        } catch (e: Exception) {
            Resultado(false, "Error al sincronizar: ${e.message ?: e.javaClass.simpleName}", descargasAplicadas, 0, descargasAplicadas > 0)
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

        val controles = c.controlesCompartidos()
        val configuracion = controles["cfg"]
        val lecturas = HashMap<String, MutableList<AccountingData>>()
        var bajados = 0
        val filaUsuarios = controles["usuarios"]
        if (filaUsuarios != null) { if (aplicar(filaUsuarios, lecturas, autoritativo = true)) bajados++ }
        else if (usuarios.lista().isNotEmpty()) { usuarios.put(JSONObject(), 0L); bajados++ }
        controles["permisos"]?.let { if (aplicar(it, lecturas, autoritativo = true)) bajados++ }
        if (esAdmin && controles["permisos"] == null) permisos.inicializarSiVacio()
        configuracionVerificada = configuracion?.valor?.optJSONObject("etq") != null &&
            (esAdmin || controles["permisos"] != null && usuarios.lista().any { it.correo.equals(email, true) })
        for (f in filas) {
            if (f.clave in setOf("cfg", "permisos", "usuarios")) continue
            if (aplicar(f, lecturas)) bajados++
        }
        if (configuracion != null && aplicar(configuracion, lecturas)) bajados++
        var huboCambios = bajados > 0
        for ((s, lista) in lecturas) {
            val nuevas = store.mergeIn(s, lista)
            if (nuevas > 0) { bajados += nuevas; descargasAplicadas += nuevas; huboCambios = true }
        }

        if (quitarBorradas(c)) { huboCambios = true; descargasAplicadas++ }

        val por = "autor no registrado"
        val claves = LinkedHashSet<String>()
        mda.mdaSeries().filter { serieOk(it) }.forEach { claves.add("mda:$it") }
        mda.infoSeries().filter { serieOk(it) }.forEach { claves.add("info:$it") }
        mda.ifazSeries().filter { serieOk(it) }.forEach { claves.add("ifaz:$it") }
        etq.ids().forEach { claves.add("etq:$it") }
        rev.ids().forEach { claves.add("rev:$it") }
        registros.ids().forEach { claves.add("reg:$it") }

        if (esAdmin) {
            claves.add("semanal")
            claves.add("cfg")
            bs.asegurarHoras()
            if (permisos.tieneCambiosPendientes()) claves.add("permisos")
            bs.nombresBs().forEach { claves.add("bs:$it") }
            bs.okNombres().forEach { claves.add("bsok:$it") }
        }
        val cambiadas = ArrayList<FilaNube>(); val digests = HashMap<String, String>()
        for (k in claves) {
            val l = local(k) ?: continue
            if (enviado(k) == l.digest) continue
            if (k.startsWith("bs:") && l.valor.toString().length > 4_000_000) continue

            autores.conservar(k, l.digest, email)
            cambiadas.add(FilaNube(k, l.valor, l.ts, autores.autor(k, l.digest))); digests[k] = l.digest
        }
        val lecFilas = ArrayList<FilaNube>(); val lecDigest = HashMap<String, String>()
        for (s in store.serials()) {
            val h = store.history(s)
            val d = "${h.size}:${h.firstOrNull()?.timestamp ?: 0}"
            if (enviado("lec:$s") == d) continue
            h.forEach {
                val k = "lec:$s:${it.timestamp}"
                val l = Local(it.timestamp, it.toJson())
                autores.conservar(k, l.digest, email)
                lecFilas.add(FilaNube(k, l.valor, l.ts, autores.autor(k, l.digest)))
            }
            lecDigest["lec:$s"] = d
        }
        if (cambiadas.isNotEmpty()) {
            val (archivos, otras) = cambiadas.partition { it.clave.startsWith("bs:") }
            if (otras.isNotEmpty()) c.subir(otras, por, sobrescribir = true)
            if (archivos.isNotEmpty()) c.subir(archivos, por, sobrescribir = true, lote = 1)
            digests.forEach { (k, d) -> marcarEnviado(k, d) }
            cambiadas.firstOrNull { it.clave == "permisos" }?.let { permisos.confirmarVersion(it.valor, it.ts) }
        }
        if (lecFilas.isNotEmpty()) { c.subir(lecFilas, por, sobrescribir = false); lecDigest.forEach { (k, d) -> marcarEnviado(k, d) } }

        if (cursor != null) prefs.edit().putString("cursor", cursor).apply()
        prefs.edit().putLong("ultima", System.currentTimeMillis()).apply()

        val subidos = cambiadas.count { !it.clave.startsWith("reg:") } + lecFilas.size
        val msg = when {
            bajados == 0 && subidos == 0 -> "Todo al día."
            else -> "Bajó $bajados y subió $subidos."
        }
        return Resultado(true, msg, bajados, subidos, huboCambios)
    }
}
