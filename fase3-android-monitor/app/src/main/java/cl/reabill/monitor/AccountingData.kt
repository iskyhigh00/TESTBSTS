package cl.reabill.monitor

import org.json.JSONObject

data class AccountingData(
    val inserted: Int = 0,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val rate: Double = 0.0,
    val stacked: Int = 0,
    val deposited: Int = 0,
    val bcInserted: Int = 0,
    val bcAccepted: Int = 0,
    val bcRejected: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ts", timestamp); put("ins", inserted); put("acc", accepted)
        put("rej", rejected); put("rate", rate); put("stk", stacked)
        put("dep", deposited); put("bcIns", bcInserted); put("bcAcc", bcAccepted)
        put("bcRej", bcRejected)
    }
    companion object {
        fun fromJson(o: JSONObject) = AccountingData(
            o.optInt("ins"), o.optInt("acc"), o.optInt("rej"), o.optDouble("rate"),
            o.optInt("stk"), o.optInt("dep"), o.optInt("bcIns"), o.optInt("bcAcc"),
            o.optInt("bcRej"), o.optLong("ts")
        )
    }
}
