package cl.reabill.monitor

import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TrazaUsb(
    private val dir: File,
    private val topeSesion: Long = 3_000_000L,
    private val topeTotal: Long = 40_000_000L,
    private val maxDias: Int = 45
) {
    private val marcaPausa = File(dir, ".pausada")

    @Volatile var activa: Boolean = true
        set(v) {
            field = v
            try { dir.mkdirs(); if (v) marcaPausa.delete() else marcaPausa.createNewFile() } catch (e: Exception) { }
        }

    private var actual: File? = null
    private var etiquetada = false
    private var tam = 0L
    private var excedida = false

    init { try { if (marcaPausa.exists()) activa = false } catch (e: Exception) { } }

    private fun hex(b: ByteArray, n: Int): String {
        val sb = StringBuilder(n * 2)
        for (i in 0 until n) { val v = b[i].toInt() and 0xFF; sb.append("0123456789abcdef"[v shr 4]).append("0123456789abcdef"[v and 15]) }
        return sb.toString()
    }

    private fun listar(): List<File> =
        (dir.listFiles { x -> x.isFile && x.name.endsWith(".txt") } ?: emptyArray<File>()).sortedBy { it.name }

    private fun escribir(s: String) {
        val f = actual ?: return
        if (excedida) return
        try {
            if (tam > topeSesion) { excedida = true; f.appendText("# tope de ${topeSesion / 1_000_000} MB por sesión: no se guarda más de esta sesión\n"); return }
            f.appendText(s + "\n"); tam += s.length + 1
        } catch (e: Exception) { }
    }

    private fun abrir(cabecera: String) {
        cerrar()
        if (!activa) return
        try {
            dir.mkdirs()
            val base = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            var f = File(dir, "${base}_sin-identificar.txt"); var i = 2
            while (f.exists()) { f = File(dir, "${base}x${i}_sin-identificar.txt"); i++ }
            actual = f; etiquetada = false; tam = 0L; excedida = false
            escribir("# ${System.currentTimeMillis()} sesión iniciada" + if (cabecera.isNotEmpty()) " · $cabecera" else "")
            podar()
        } catch (e: Exception) { actual = null }
    }

    private fun cerrar() {
        if (actual != null) escribir("# ${System.currentTimeMillis()} sesión cerrada")
        actual = null
    }

    @Synchronized fun abrirSesion(cabecera: String = "") = abrir(cabecera)
    @Synchronized fun cerrarSesion() = cerrar()

    @Synchronized fun out(b: ByteArray) {
        if (!activa || b.isEmpty()) return
        if (actual == null) abrir("trama sin sesión abierta")
        escribir("${System.currentTimeMillis()} OUT ${hex(b, b.size)}")
    }

    @Synchronized fun inn(b: ByteArray, n: Int) {
        if (!activa || n <= 0) return
        if (actual == null) abrir("trama sin sesión abierta")
        escribir("${System.currentTimeMillis()} IN ${hex(b, n)}")
    }

    @Synchronized fun marca(texto: String) {
        if (!activa || actual == null) return
        escribir("# ${System.currentTimeMillis()} " + texto.replace('\n', ' '))
    }

    @Synchronized fun etiquetar(modelo: String, serie: String) {
        val f = actual ?: return
        if (etiquetada) return
        fun limpio(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "").take(30).ifEmpty { "x" }
        val base = f.name.substringBefore("_")
        var nuevo = File(dir, "${base}_${limpio(modelo)}_${limpio(serie)}.txt"); var i = 2
        while (nuevo.exists()) { nuevo = File(dir, "${base}_${limpio(modelo)}_${limpio(serie)}-${i}.txt"); i++ }
        try { if (f.renameTo(nuevo)) { actual = nuevo; etiquetada = true } } catch (e: Exception) { }
    }

    private fun podar() {
        try {
            val limite = System.currentTimeMillis() - maxDias * 86_400_000L
            listar().filter { it != actual && it.lastModified() < limite }.forEach { it.delete() }
            val resto = listar()
            var total = resto.sumOf { it.length() }
            for (x in resto) { if (total <= topeTotal) break; if (x == actual) continue; total -= x.length(); x.delete() }
        } catch (e: Exception) { }
    }

    @Synchronized fun resumen(): String {
        val fs = listar()
        val por = pendientes().size
        return "${fs.size} sesiones · ${"%.1f".format(Locale.US, fs.sumOf { it.length() } / 1_000_000.0)} MB" +
            (if (por > 0) " · $por por subir a la nube" else "") + if (!activa) " · en pausa" else ""
    }

    private val subidas = File(dir, ".subidas")
    private val archivoId = File(dir, ".id")

    private fun leerSubidas(): Set<String> = try { if (subidas.exists()) subidas.readLines().toSet() else emptySet() } catch (e: Exception) { emptySet() }

    private fun marcar(f: File) { try { subidas.appendText(f.name + "\n") } catch (e: Exception) { } }

    @Synchronized fun instalacion(): String = try {
        dir.mkdirs()
        if (archivoId.exists() && archivoId.readText().isNotBlank()) archivoId.readText().trim()
        else java.util.UUID.randomUUID().toString().take(8).also { archivoId.writeText(it) }
    } catch (e: Exception) { "x" }

    @Synchronized fun pendientes(): List<File> {
        val ya = leerSubidas(); val out = ArrayList<File>()
        for (f in listar()) {
            if (f == actual || f.name in ya) continue
            if (contar(f) == 0) { marcar(f); continue }
            out.add(f)
        }
        return out
    }

    @Synchronized fun marcarSubida(f: File) = marcar(f)

    class Sesion(val archivo: String, val modelo: String, val serie: String, val tam: Long, val tramas: Int, val gzBase64: String)

    @Synchronized fun leer(f: File): Sesion? = try {
        val bytes = f.readBytes()
        val bo = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(bo).use { it.write(bytes) }
        val p = f.name.removeSuffix(".txt").split("_")
        Sesion(f.name, p.getOrNull(1) ?: "", (p.getOrNull(2) ?: "").replace(Regex("-\\d+$"), ""), bytes.size.toLong(), contar(f), java.util.Base64.getEncoder().encodeToString(bo.toByteArray()))
    } catch (e: Exception) { null }

    @Synchronized fun hayDatos(): Boolean = listar().isNotEmpty()

    @Synchronized fun limpiar() { listar().forEach { try { it.delete() } catch (e: Exception) { } }; actual = null }

    private fun contar(f: File): Int { var n = 0; try { f.forEachLine { if (it.contains(" OUT ")) n++ } } catch (e: Exception) { }; return n }

    private fun usuarioDe(f: File): String = try {
        f.useLines { it.firstOrNull() }?.let { Regex("usuario (.+?) · PID").find(it)?.groupValues?.get(1) } ?: ""
    } catch (e: Exception) { "" }

    @Synchronized fun exportarZip(out: OutputStream, quien: String = ""): Int {
        val fs = listar()
        ZipOutputStream(out).use { z ->
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val ind = StringBuilder("ORIGEN: ESTE TELÉFONO (solo lo grabado aquí)\n")
            ind.append("Generado: ${fmt.format(Date())}" + (if (quien.isNotBlank()) " por $quien" else "") + "\n")
            ind.append("Sesiones: ${fs.size}" + (if (fs.isNotEmpty()) " · de ${fs.first().name.substringBefore("_")} a ${fs.last().name.substringBefore("_")}" else "") + "\n")
            ind.append("(fecha-hora = hora de este teléfono al conectar el billetero)\n\n")
            ind.append("fecha-hora | modelo | serie | usuario | KB | tramas enviadas\n")
            for (f in fs.sortedByDescending { it.name }) {
                val p = f.name.removeSuffix(".txt").split("_")
                ind.append("${p.getOrNull(0)} | ${p.getOrNull(1) ?: ""} | ${p.getOrNull(2) ?: ""} | ${usuarioDe(f)} | ${f.length() / 1024} | ${contar(f)}\n")
            }
            z.putNextEntry(ZipEntry("INDICE.txt")); z.write(ind.toString().toByteArray(Charsets.UTF_8)); z.closeEntry()
            for (f in fs) {
                val p = f.name.removeSuffix(".txt").split("_")
                val carpeta = (p.getOrNull(2) ?: "sin-identificar").replace(Regex("-\\d+$"), "")
                z.putNextEntry(ZipEntry("$carpeta/${f.name}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        return fs.size
    }
}
