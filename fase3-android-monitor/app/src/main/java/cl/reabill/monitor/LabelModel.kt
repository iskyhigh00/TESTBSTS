package cl.reabill.monitor

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LabelData(
    val date: Long,
    val model: String,
    val serial: String,
    val firmware: String,
    val billSet: String,
    val mda: String?,
    val realRate: Double,
    val weeklyRate: Double?,
    val fromPrevious: Boolean = false
)

object LabelFields {
    const val DATE = "fecha"; const val MODEL = "modelo"; const val SERIAL = "serie"
    const val MDA = "mda"; const val REAL = "acep"; const val WEEK = "sem"
    const val FW = "fw"; const val BS = "bs"; const val VER = "ver"
    val ALL = listOf(MDA, REAL, WEEK, DATE, MODEL, SERIAL, FW, BS, VER)
    val NAMES = mapOf(
        MDA to "N° máquina", REAL to "Aceptación real", WEEK to "Aceptación semanal", DATE to "Fecha",
        MODEL to "Modelo", SERIAL to "Serie", FW to "Firmware", BS to "Bill-set", VER to "Versión bill-set"
    )

    val DEFAULT_TEXT = mapOf(
        MDA to "MDA", REAL to "AR", WEEK to "AS", DATE to "", MODEL to "", SERIAL to "S/N",
        FW to "FW", BS to "BS", VER to ""
    )
    val DEFAULT: Set<String> = setOf(MDA, REAL, WEEK, DATE, FW, BS)

    fun parse(s: String?): Set<String> =
        if (s == null) DEFAULT else s.split(',').filter { it in ALL }.toSet()
    fun encode(f: Set<String>) = ALL.filter { it in f }.joinToString(",")

    fun parseNames(s: String?): Map<String, String> {
        val out = HashMap<String, String>()
        s?.lines()?.forEach { l ->
            val i = l.indexOf('=')
            if (i > 0) { val k = l.substring(0, i); if (k in ALL) out[k] = l.substring(i + 1).take(12) }
        }
        return out
    }
    fun encodeNames(m: Map<String, String>) =
        m.filter { (k, v) -> k in ALL && v != DEFAULT_TEXT[k] }.entries.joinToString("\n") { "${it.key}=${it.value.replace('\n', ' ')}" }
    fun textOf(k: String, names: Map<String, String>) = (names[k] ?: DEFAULT_TEXT[k] ?: "").trim()
}

class LabelLine(val text: String, val bold: Boolean)

object LabelText {
    private fun pct(v: Double) = String.format(Locale("es", "CL"), "%.1f", v) + "%"
    private fun join(pre: String, v: String) = if (pre.isEmpty()) v else "$pre $v"

    fun lines(d: LabelData, f: Set<String>, names: Map<String, String> = emptyMap(), twoLines: Boolean = false): List<LabelLine> {
        fun t(k: String) = LabelFields.textOf(k, names)
        val out = ArrayList<LabelLine>()
        if (LabelFields.MDA in f) out.add(LabelLine(join(t(LabelFields.MDA), d.mda ?: "—"), true))
        val ac = ArrayList<String>()
        if (LabelFields.REAL in f) ac.add(join(t(LabelFields.REAL), pct(d.realRate)))
        if (LabelFields.WEEK in f) ac.add(join(t(LabelFields.WEEK), d.weeklyRate?.let { pct(it) } ?: "—"))
        if (ac.isNotEmpty()) out.add(LabelLine(ac.joinToString("  "), true))
        val id = ArrayList<String>()
        if (LabelFields.DATE in f) id.add(join(t(LabelFields.DATE), SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date(d.date))))
        if (LabelFields.MODEL in f && d.model.isNotBlank() && d.model != "—") id.add(join(t(LabelFields.MODEL), d.model))
        if (LabelFields.SERIAL in f && d.serial.isNotBlank() && d.serial != "—") id.add(join(t(LabelFields.SERIAL), d.serial))
        if (LabelFields.FW in f) id.add(join(t(LabelFields.FW), d.firmware))
        val numero = d.billSet.trim().substringBefore(' ').takeLast(3)
        val version = d.billSet.trim().substringAfter(' ', "").trim()
        if (LabelFields.BS in f) id.add(join(t(LabelFields.BS), numero) + if (LabelFields.VER in f && version.isNotEmpty()) " $version" else "")
        else if (LabelFields.VER in f && version.isNotEmpty()) id.add(join(t(LabelFields.VER), version))
        if (id.isNotEmpty()) out.add(LabelLine(id.joinToString("  "), false))
        if (!twoLines || out.size < 3) return out
        val fuertes = out.filter { it.bold }.joinToString("  ") { it.text }
        val suaves = out.filter { !it.bold }.joinToString("  ·  ") { it.text }
        return listOfNotNull(
            fuertes.takeIf { it.isNotEmpty() }?.let { LabelLine(it, true) },
            suaves.takeIf { it.isNotEmpty() }?.let { LabelLine(it, false) }
        )
    }
}

object LabelPick {

    fun reading(actual: AccountingData?, historial: List<AccountingData>): Pair<AccountingData, Boolean>? {
        if (actual != null && actual.inserted > 0) return actual to false
        val previa = historial.filter { it.inserted > 0 }.maxByOrNull { it.timestamp }
        if (previa != null) return previa to true
        return actual?.let { it to false }
    }
}

object LabelFit {
    const val ALTO = 96f
    const val HUECO = 3f
    const val MARGEN = 4f

    fun maximo(alturaBase: Float, n: Int): Float =
        if (alturaBase <= 0f || n <= 0) 1f else (ALTO - MARGEN - HUECO * (n - 1)) / alturaBase

    fun factor(alturaBase: Float, n: Int, porcentaje: Int): Float = maximo(alturaBase, n) * porcentaje.coerceIn(30, 100) / 100f

    fun snap(p: Int): Int = listOf(60, 80, 100).minByOrNull { kotlin.math.abs(it - p) } ?: 100
}
