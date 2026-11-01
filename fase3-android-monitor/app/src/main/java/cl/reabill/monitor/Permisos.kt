package cl.reabill.monitor

enum class Proc(val clave: String, val etiqueta: String, val porDefecto: Boolean = false) {
    LEER("leer", "Leer billeteros", true),
    IMPRIMIR("imprimir", "Imprimir etiquetas", true),
    CALIBRAR("calibrar", "Calibrar"),
    BORRAR("borrar", "Borrar contabilidad"),
    INTERFAZ("interfaz", "Cambiar interfaz"),
    BILLSET("billset", "Cargar cualquier bill-set");

    companion object { fun de(clave: String): Proc? = values().firstOrNull { it.clave == clave } }
}

object Permisos {
    const val ADMIN_EMAIL = "admin@robrist.app"

    const val CUENTA_COMPARTIDA = "billmon@robrist.app"
    fun esAdmin(email: String, rol: String): Boolean = email.trim().equals(ADMIN_EMAIL, true) || rol.equals("admin", true)
}

class Acceso(val admin: Boolean, private val permitidos: Set<Proc>) {
    fun puede(p: Proc): Boolean = admin || p in permitidos
    companion object { val NINGUNO = Acceso(false, emptySet()) }
}
