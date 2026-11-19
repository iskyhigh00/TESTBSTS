package cl.reabill.monitor

import java.security.MessageDigest

object SoftwareAprobado {
    class Info(val nombre: String, val boot: String, val app: String, val billset: String, val probado: Boolean = true)

    private val APROBADOS = mapOf(
        "458dfe802cd8ee1bd6199aec4393646a07e3c236fe021f08a55c90e8f9441daa" to Info("N3048F400029354", "01.24.00", "286109400", "510294354"),
        "3528bab5ef0aaadcd479629ab79263872c711d95011aec8caf7cddf6ad760243" to Info("N3028F400029354", "01.24.00", "286110400", "510294354"),
        "87862f3832c5cc52c23e011591483c191a9675166f1d5e4ed486c4f9ab2edcc6" to Info("N3028F501336401", "01.25.00", "286110501", "523365401"),
        "ab7cfa1398c3f5dd7b04dcae211b44eb227d6e509514b4a34b56ec0e6f4859d9" to Info("Paquete 403", "01.26.00", "286110512", "523361403"),
        "fab44b3b06adee43fba897a3f7dd1065aebff3fd552ab4c3193be71e75f31fed" to Info("8NY530336402", "01.25.00", "286108530", "523363402")
    )

    const val BOOT_OBJETIVO = "01.24.00"
    const val APP_OBJETIVO = "286109400"
    const val BILLSET_OBJETIVO = "523361403"
    const val BILLSET_VERSION = "04.03.00"
    const val INTERFAZ_OBJETIVO = 1

    val MODELOS_PROBADOS = setOf("SCN8342U", "SCN8347NU", "SCN8347")

    fun sha256(d: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(d).joinToString("") { "%02x".format(it) }

    private fun leer(d: ByteArray, sha: String): Info? {
        val t = String(d, Charsets.ISO_8859_1)
        val app = Regex("(286\\d{6})285590100").find(t)?.groupValues?.get(1) ?: return null
        val bs = Regex("(\\d{9})285590100").findAll(t).map { it.groupValues[1] }.firstOrNull { !it.startsWith("286") } ?: "—"
        val boot = Regex("(286098\\d{3})\\1.{0,6}?(\\d\\d\\.\\d\\d\\.\\d\\d)", RegexOption.DOT_MATCHES_ALL).find(t)?.groupValues?.get(2) ?: "—"
        return Info("no probado ${sha.take(12)}", boot, app, bs, probado = false)
    }

    fun parse(name: String, data: ByteArray): Pair<FirmwareFile?, String> {
        if (FirmwareFile.splitRecords(data) == null) return null to "El archivo no tiene la estructura de un Software de Advance; no se escribe."
        val sha = sha256(data)
        val info = APROBADOS[sha] ?: leer(data, sha)
            ?: return null to "No parece un Software de SC Advance (no trae los datos de aplicación); no se escribe."
        return FirmwareFile(name, data, info.billset, FirmwareFile.KIND_SOFTWARE).also { it.software = info } to ""
    }
}
