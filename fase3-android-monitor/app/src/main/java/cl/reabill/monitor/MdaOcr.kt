package cl.reabill.monitor

object MdaOcr {

    fun candidatos(lineas: List<Pair<String, Int>>): List<String> {
        val vistos = LinkedHashSet<String>()
        lineas.sortedByDescending { it.second }.forEach { (texto, _) ->

            val limpio = texto.replace(Regex("(?<=\\d) (?=\\d)"), "")
            Regex("(?<!\\d)\\d{3,7}(?!\\d)").findAll(limpio).forEach { m -> Mda.norm(m.value)?.let { vistos.add(it) } }
        }
        return vistos.take(5)
    }
}
