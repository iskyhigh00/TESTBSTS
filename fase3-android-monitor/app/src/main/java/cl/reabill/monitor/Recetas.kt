package cl.reabill.monitor

class Receta(
    val id: String, val nombre: String, val resumen: String,
    val app: String, val boot: String, val billset: String, val billsetVersion: String, val interfaz: Int,
    val modelosProbados: Set<String>
)

object Recetas {
    val IGT = Receta(
        "igt", "IGT Trimline",
        "Software de aplicación 286109400 (Boot 01.24.00), bill-set 403 e interfaz Netplex. Probado en SCN8342U.",
        SoftwareAprobado.APP_OBJETIVO, SoftwareAprobado.BOOT_OBJETIVO, SoftwareAprobado.BILLSET_OBJETIVO, SoftwareAprobado.BILLSET_VERSION,
        SoftwareAprobado.INTERFAZ_OBJETIVO, SoftwareAprobado.MODELOS_PROBADOS
    )
    val EGT = Receta(
        "egt", "EGT",
        "Software de aplicación 286108530 (Boot 01.25.00), bill-set 403 e interfaz EBDS (la de fábrica de este Software). Solo el admin por ahora.",
        "286108530", "01.25.00", "523361403", "04.03.00", 2, emptySet()
    )
    val TODAS = listOf(IGT, EGT)
}
