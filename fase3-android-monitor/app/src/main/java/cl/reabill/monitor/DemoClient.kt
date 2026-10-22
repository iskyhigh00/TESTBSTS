package cl.reabill.monitor

class DemoClient : ValidatorClient {
    companion object {
        const val SERIAL = "DEMO0000001"
        fun isDemoSerial(s: String) = s.startsWith("DEMO")
    }

    override var lastDetail = ""
        private set
    override val supportsReset = true
    override val calibrationNote = " (demo)"

    private var inserted = 4210
    private var accepted = 4087
    private var stacked = 3950
    private var deposited = 3820
    private var reads = 0
    private var interfaz = 3

    override val supportsInterface = true
    override fun readInterface(): Pair<Int?, String> { pause(250); return interfaz to "" }
    override fun writeInterface(valor: Int): Pair<Boolean, String> {
        pause(700)
        if (valor !in Interfaz.VALORES) return false to "interfaz desconocida"
        interfaz = valor
        return true to "demo"
    }

    private fun pause(ms: Long) = try { Thread.sleep(ms) } catch (_: InterruptedException) {}

    override fun readIdentity(): DeviceStatus {
        pause(400)
        return DeviceStatus(
            connected = true, state = "Conectado", model = "SC8307 (demo)", manufacturer = "MEI",
            serial = SERIAL, prodDate = "250101", raw = "demo"
        )
    }

    override fun readFirmware(): Triple<String, String, String> {
        pause(500)
        return Triple("286108400", "523361403", "")
    }

    override fun readAccounting(): Pair<AccountingData?, String> {
        pause(500)
        if (reads++ > 0) {
            val n = 12 + (reads * 7) % 20
            val ok = n - (reads % 3)
            inserted += n; accepted += ok; stacked += ok; deposited += ok - 1
        }
        val rejected = inserted - accepted
        val rate = if (inserted > 0) accepted * 100.0 / inserted else 0.0
        lastDetail = "Detalle contabilidad (demo): ins=$inserted acc=$accepted"
        return AccountingData(inserted, accepted, rejected, rate, stacked, deposited, 0, 0, 0) to ""
    }

    override fun initiateCalibration(): Pair<Boolean, String> { pause(800); return true to "demo" }

    override fun resetAccounting(): Boolean {
        pause(900)
        inserted = 0; accepted = 0; stacked = 0; deposited = 0
        reads = 0
        return true
    }
}
