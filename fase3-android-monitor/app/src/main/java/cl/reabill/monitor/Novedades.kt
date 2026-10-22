package cl.reabill.monitor

object Novedades {
    val NOTAS: Map<String, List<String>> = linkedMapOf(
        "2.0.1" to listOf(
            "El reporte semanal acepta la versión 2 (con billetes aceptados y rechazados); las versiones desconocidas se rechazan. Vuelve a importar el reporte.",
            "Pantalla principal: nueva sección «Real vs sistema» con aceptados, rechazados y total, lado a lado.",
            "Buscar máquina: muestra también las cantidades del AS bajo las del AR."
        ),
        "2.0.0" to listOf(
            "AR y AS aparecen al mismo tiempo y ruedan con la misma animación (el AS espera a que llegue la lectura del billetero).",
            "Nueva numeración: cada número llega hasta 9 y luego se reinicia (2.0.9 → 2.1.0)."
        ),
        "1.12.18" to listOf(
            "La foto de confirmación se acerca al número: el que se ve queda enmarcado y el leído aparece ARRIBA, sin taparlo, para compararlos."
        ),
        "1.12.17" to listOf(
            "Los N° de máquina deben tener 6 dígitos y empezar con 10. Escribe 10025 y ya no se convierte en 110025: aparece «no válido».",
            "Guardar el MDA queda desactivado y el campo en rojo si el número no es válido.",
            "La lectura por foto descarta los números que no empiezan con 10."
        ),
        "1.12.16" to listOf(
            "Nuevo botón «Ver máquinas»: lista de todas las máquinas asociadas a un billetero, con su serie, AR y AS.",
            "Filtra por MDA o serie, y toca una fila para abrir sus datos completos en el buscador."
        ),
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
