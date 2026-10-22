package cl.reabill.monitor

object MdaOcr {
    private val PERMITIDAS = Regex("(?i)\\b(mda|nro|no|n[°ºo])\\b\\.?")

    fun candidatos(lineas: List<Pair<String, Int>>): List<String> {
        val seis = LinkedHashSet<String>()
        val otros = LinkedHashSet<String>()
        lineas.sortedByDescending { it.second }.forEach { (texto, _) ->
            val sinPermitidas = PERMITIDAS.replace(texto, " ")
            if (sinPermitidas.any { it.isLetter() }) return@forEach
            val limpio = texto.replace(Regex("(?<=\\d) (?=\\d)"), "")
            Regex("(?<!\\d)\\d{3,7}(?!\\d)").findAll(limpio).forEach { m ->
                val n = Mda.norm(m.value) ?: return@forEach
                if (m.value.length == 6) seis.add(n) else otros.add(n)
            }
        }
        return (seis + otros).distinct().take(5)
    }
}
