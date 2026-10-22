package cl.reabill.monitor

interface ValidatorClient {

    val lastDetail: String

    val supportsReset: Boolean

    val calibrationNote: String

    val timingNote: String get() = ""

    fun readIdentity(): DeviceStatus

    fun readFirmware(): Triple<String, String, String>
    fun readAccounting(): Pair<AccountingData?, String>

    fun initiateCalibration(): Pair<Boolean, String>
    fun resetAccounting(): Boolean
}
