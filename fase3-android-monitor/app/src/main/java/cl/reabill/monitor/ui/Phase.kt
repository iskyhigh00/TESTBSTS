package cl.reabill.monitor.ui

import cl.reabill.monitor.AccountingData
import cl.reabill.monitor.DeviceStatus
import java.text.NumberFormat
import java.util.Locale

enum class Phase { Disconnected, Identifying, ReadingFirmware, ReadingAccounting, Ready, NoResponse }

const val PLACEHOLDER = "—"

fun String.isPlaceholder() = isBlank() || this == PLACEHOLDER

fun derivePhase(
    connected: Boolean,
    busy: Boolean,
    status: DeviceStatus,
    log: List<String>
): Phase {
    if (!connected) return Phase.Disconnected
    if (busy) {
        return when {
            status.model.isPlaceholder() -> Phase.Identifying
            status.billSet.isPlaceholder() -> Phase.ReadingFirmware
            else -> Phase.ReadingAccounting
        }
    }
    if (status.state.equals("Sin respuesta", ignoreCase = true)) return Phase.NoResponse
    val head = log.firstOrNull { !isUpdateLine(it) }.orEmpty()
    if (head.contains("sin respuesta", ignoreCase = true) ||
        head.contains("interrumpida", ignoreCase = true)
    ) return Phase.NoResponse
    return Phase.Ready
}

private fun isUpdateLine(line: String) =
    line.contains("actualiza", ignoreCase = true) ||
        line.contains("al día", ignoreCase = true) ||
        line.contains("versión nueva", ignoreCase = true) ||
        line.contains("Descarg", ignoreCase = true)

fun Phase.label(): String = when (this) {
    Phase.Disconnected -> "Desconectado"
    Phase.Identifying -> "Identificando"
    Phase.ReadingFirmware -> "Leyendo firmware"
    Phase.ReadingAccounting -> "Leyendo contabilidad"
    Phase.Ready -> "Conectado"
    Phase.NoResponse -> "Sin respuesta"
}

val Phase.isBusy get() = this == Phase.Identifying || this == Phase.ReadingFirmware || this == Phase.ReadingAccounting

fun previousSnapshot(current: AccountingData, history: List<AccountingData>): AccountingData? =
    history.firstOrNull { it.timestamp < current.timestamp && it.copy(timestamp = 0) != current.copy(timestamp = 0) }
        ?: history.firstOrNull { it.timestamp < current.timestamp }

private val intFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "CL"))

fun fmtInt(n: Int): String = intFormat.format(n)

fun fmtRate(v: Double): String = String.format(Locale("es", "CL"), "%.1f", v)

fun fmtDelta(n: Int): String = when {
    n > 0 -> "+${fmtInt(n)}"
    n < 0 -> "−${fmtInt(-n)}"
    else -> "0"
}

fun splitLog(line: String): Pair<String, String> {
    val i = line.indexOf("  ")
    return if (i in 1..8) line.substring(0, i) to line.substring(i + 2).trim() else "" to line
}
