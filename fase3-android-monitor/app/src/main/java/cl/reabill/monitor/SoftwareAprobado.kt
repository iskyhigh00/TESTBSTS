package cl.reabill.monitor

import java.security.MessageDigest

object SoftwareAprobado {
    class Info(val nombre: String, val boot: String, val app: String, val billset: String)

    private val APROBADOS = mapOf(
        "458dfe802cd8ee1bd6199aec4393646a07e3c236fe021f08a55c90e8f9441daa" to Info("N3048F400029354", "01.24.00", "286109400", "510294354")
    )

    const val BOOT_OBJETIVO = "01.24.00"
    const val APP_OBJETIVO = "286109400"
    const val BILLSET_OBJETIVO = "523361403"
    const val BILLSET_VERSION = "04.03.00"
    const val INTERFAZ_OBJETIVO = 1
    val MODELOS_PROBADOS = setOf("SCN8342U", "SCN8347NU", "SCN8347")

    fun sha256(d: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(d).joinToString("") { "%02x".format(it) }

    fun parse(name: String, data: ByteArray): Pair<FirmwareFile?, String> {
        val info = APROBADOS[sha256(data)]
            ?: return null to "Este Software no está en la lista aprobada (solo se escriben los que ya se probaron); no se escribe."
        if (FirmwareFile.splitRecords(data) == null) return null to "El Software no tiene la estructura esperada; no se escribe."
        return FirmwareFile(name, data, info.billset, FirmwareFile.KIND_SOFTWARE).also { it.software = info } to ""
    }
}

