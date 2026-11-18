package cl.reabill.monitor

import java.io.File
import java.io.OutputStream

class TrazaUsb(private val archivo: File, private val tope: Long = 5_000_000L) {
    @Volatile var activa: Boolean = false

    private fun hex(b: ByteArray, n: Int): String {
        val sb = StringBuilder(n * 2)
        for (i in 0 until n) { val v = b[i].toInt() and 0xFF; sb.append("0123456789abcdef"[v shr 4]).append("0123456789abcdef"[v and 15]) }
        return sb.toString()
    }

    @Synchronized private fun linea(s: String) {
        if (!activa) return
        try {
            if (archivo.length() > tope) { activa = false; archivo.appendText("# tope de tamaño alcanzado: se detuvo la grabación\n"); return }
            archivo.appendText(s + "\n")
        } catch (e: Exception) { activa = false }
    }

    fun out(b: ByteArray) { if (activa && b.isNotEmpty()) linea("${System.currentTimeMillis()} OUT ${hex(b, b.size)}") }
    fun inn(b: ByteArray, n: Int) { if (activa && n > 0) linea("${System.currentTimeMillis()} IN ${hex(b, n)}") }
    fun marca(texto: String) { if (activa) linea("# " + texto.replace('\n', ' ')) }

    @Synchronized fun tamano(): Long = try { if (archivo.exists()) archivo.length() else 0L } catch (e: Exception) { 0L }
    @Synchronized fun limpiar() { try { archivo.delete() } catch (e: Exception) { } }
    @Synchronized fun copiarA(out: OutputStream) { if (archivo.exists()) archivo.inputStream().use { it.copyTo(out) } }
}

