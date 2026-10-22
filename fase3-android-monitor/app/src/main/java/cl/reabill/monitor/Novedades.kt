package cl.reabill.monitor

object Novedades {
    val NOTAS: Map<String, List<String>> = linkedMapOf(
        "1.12.15" to listOf(
            "Revisión visual completa: diálogos con la paleta y tipografía de la app, en vez de los colores de fábrica.",
            "Buscar máquina: AR y AS en columnas, y datos del billetero alineados (serie, modelo, firmware, bill-set).",
            "Pantalla principal: MDA, AR y AS alineados por línea base; «editar» en vez del lápiz.",
            "Revisa que los botones Borrar, Reiniciar e Historial se vean como el resto, y que los % no se corten en dos líneas."
        ),
        "1.12.14" to listOf(
            "Pantalla principal: AR y AS lado a lado, con el MDA asociado a la serie. Toca el MDA para editarlo.",
            "Sin MDA asociado: botón «Agregar máquina (foto)». La confirmación muestra el número sobre la foto.",
            "Preferencias → Probar con una imagen: ahora muestra la foto con el número encima.",
            "Nuevo botón «Buscar máquina»: escribe o fotografía un MDA y ve AS, AR de la última lectura y datos del billetero.",
            "Esta lista aparece después de cada actualización."
        )
    )

    private fun partes(v: String) = v.split('.').map { it.toIntOrNull() ?: 0 }

    fun comparar(a: String, b: String): Int {
        val x = partes(a); val y = partes(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    fun pendientes(vista: String?, actual: String): List<Pair<String, List<String>>> =
        NOTAS.filter { (v, _) ->
            comparar(v, actual) <= 0 && (if (vista == null) v == actual else comparar(v, vista) > 0)
        }.toList().sortedWith { a, b -> comparar(b.first, a.first) }
}
