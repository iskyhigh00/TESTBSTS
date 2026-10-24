package cl.reabill.monitor

fun aviso(donde: String, e: Throwable) {
    android.util.Log.w("BILLMON", "$donde: ${e.javaClass.simpleName}: ${e.message}")
}
