package cl.reabill.monitor

interface ValidatorClient {

    val lastDetail: String

    val supportsReset: Boolean

    val calibrationNote: String

    val timingNote: String get() = ""

    val supportsInterface: Boolean get() = false

    fun readInterface(): Pair<Int?, String> = null to "no disponible en este equipo"

    fun writeInterface(valor: Int): Pair<Boolean, String> = false to "no disponible en este equipo"

    fun readIdentity(): DeviceStatus

    fun readFirmware(): Triple<String, String, String>
    fun readAccounting(): Pair<AccountingData?, String>

    fun initiateCalibration(): Pair<Boolean, String>
    fun resetAccounting(): Boolean
}
