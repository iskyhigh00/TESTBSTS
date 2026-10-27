package cl.reabill.monitor

object Novedades {
    val NOTAS: Map<String, List<String>> = linkedMapOf(
        "2.3.0" to listOf(
            "«Ver máquinas»: arriba va la lista «POR REVISAR» con TODOS los que manda la Bitácora (en orden de isla), tengan o no billetero en este teléfono; cada uno muestra isla, modelo, firmware y % de aceptación. Debajo, las demás máquinas. El botón «Solo por revisar» deja únicamente esa lista.",
        ),
        "2.2.9" to listOf(
            "Pendientes al día: si la Bitácora borra una lista (o una etiqueta) de la nube, esos billeteros dejan de salir como «POR REVISAR» al sincronizar. Una revisión hecha aquí que aún no se subió no se pierde.",
        ),
        "2.2.8" to listOf(
            "«Ver máquinas»: las que están en la lista de billeteros por revisar de la Bitácora se marcan «POR REVISAR» (con su lista) y salen primero; hay un filtro «Por revisar» y el aviso de las que no tienen billetero asociado.",
            "Lo que manda la Bitácora se baja solo cada vez que se abre la app y al abrir «Ver máquinas».",
        ),
        "2.2.7" to listOf(
            "Billeteros por revisar: las listas A/B/C de REACBILL («Enviar a Billeteros») llegan por los datos compartidos. En Preferencias → Billeteros por revisar aparecen con «Revisar»: ¿quedó el mismo o lo cambiaste (nuevo/usado)?, qué se hizo (calibré, limpié, variant, pieza), firmware y quién revisó. Al guardar, la Bitácora lo agrega al historial del billetero y lo saca de sus pendientes.",
        ),
        "2.2.6" to listOf(
            "Reporte semanal automático: BILLMON ahora entiende el reporte que REACBILL publica en los datos compartidos (mismo formato del archivo exportado, versiones 1 y 2). Llega solo al sincronizar; ya no hace falta importar el archivo.",
        ),
        "2.2.5" to listOf(
            "Etiquetas de intercambio: la fecha de la etiqueta es la del día que se imprime (dd-mm-aaaa). Si hace falta otra, se cambia en el cuadro «Fecha en la etiqueta» antes de imprimir.",
        ),
        "2.2.4" to listOf(
            "Etiquetas de intercambio: los intercambios de billeteros que se confirman en REACBILL (Bitácora) llegan por los datos compartidos. En Preferencias → Etiquetas de intercambio aparecen los pendientes con «Imprimir 2 etiquetas» (una por billetero, «100403 → 100500»). Al imprimir quedan marcados como impresos para todos.",
        ),
        "2.2.3" to listOf(
            "Arreglo de publicación: la 2.2.2 no llegó a compilar. Trae el tema Premium (Preferencias → Tema) y todo lo de la 2.2.2.",
        ),
        "2.2.2" to listOf(
            "Nuevo tema Premium (púrpura, menta y coral, tarjetas de vidrio, botones con degradado y animaciones). Solo el admin lo activa: Preferencias → Tema, con la clave de la cuenta. El tema clásico sigue igual.",
        ),
        "2.2.1" to listOf(
            "Se quitó «Reiniciar equipo (experimental)»: podía dejar el billetero sin responder.",
            "La clave de la cuenta compartida ahora se guarda cifrada en el teléfono y ya no va en el respaldo de Google.",
            "¿Olvidaste la clave de Preferencias? Pon una clave nueva a la cuenta en Supabase y escríbela: la app la comprueba en la nube y la guarda.",
            "App más liviana: versión optimizada solo para teléfonos ARM (descarga y actualización más rápidas).",
            "Los errores que antes se ignoraban ahora quedan anotados; bibliotecas actualizadas.",
            "Se quitó la regla de calibración de la impresora (ya cumplió su función).",
        ),
        "2.2.0" to listOf(
            "Preferencias protegidas: con una cuenta compartida conectada, al tocar ⚙ la app pide la clave de la cuenta una vez cada vez que se abre la app. Al conectar o cambiar la cuenta también se vuelve a pedir.",
        ),
        "2.1.9" to listOf(
            "Etiqueta: el texto llega hasta los bordes de arriba y de abajo de la cinta (márgenes de 1–2 puntos en vez de 4 a 6).",
        ),
        "2.1.8" to listOf(
            "Letra de la etiqueta más grande: según la regla de calibración la cinta imprime los 12 mm completos, pero el texto usaba solo unos 10. Ahora el tamaño se calcula por la tinta real de las letras y el texto llena todo el alto.",
        ),
        "2.1.7" to listOf(
            "Nuevo en «Impresora y etiqueta»: «Imprimir regla de calibración» (40 mm), con una marca por milímetro y su número, para ver hasta dónde imprime la cinta de alto.",
        ),
        "2.1.6" to listOf(
            "En «Impresora y etiqueta», la vista previa de la etiqueta queda fija arriba: al desplazarte por las opciones siempre se ve el cambio al instante.",
        ),
        "2.1.5" to listOf(
            "Letra más grande en la etiqueta: cuando una línea queda limitada por el ancho, las demás crecen para aprovechar toda la altura de la cinta (menos espacio vacío arriba y abajo).",
        ),
        "2.1.4" to listOf(
            "Etiqueta más corta: el largo automático ahora mide solo el texto (mínimo 15 mm, antes 25 mm) y los márgenes son más chicos.",
            "Nuevo «Salida de cinta» (cinta continua): Normal 11 mm, Corta 7 mm o Mínima 4 mm de cinta que avanza al terminar. Bajo la vista previa se ve el largo real: etiqueta + salida = mm de cinta.",
        ),
        "2.1.3" to listOf(
            "Arreglo: en «Impresora y etiqueta» no se veían los botones de abajo del tamaño del texto (distribución, posición, largo, cinta, opciones y copias). La ventana cortaba el contenido en vez de dejarlo desplazar; lo mismo en «Textos de la etiqueta».",
            "La verificación antes de publicar ahora detecta este tipo de error."
        ),
        "2.1.2" to listOf(
            "Arreglo de compilación: la 2.1.0 y la 2.1.1 no llegaron a publicarse por un error mío; esta trae todo lo de ellas (etiqueta Auto, impresora que se conecta al imprimir, pestañas, menús revisados)."
        ),
        "2.1.1" to listOf(
            "Revisión de todos los menús: los botones ya no se cortan si el texto es largo (crecen a dos líneas) ni se aprietan en los diálogos.",
            "Cifras AR y AS de la pantalla principal y del buscador un poco más chicas para que «100,0 %» siempre quepa.",
            "Textos de la etiqueta: «Restablecer los textos» pasó arriba de la lista; Guardar y Cancelar quedan en su lugar.",
            "Registro: Copiar, Copiar todo y Limpiar con letra algo menor para que no se aprieten."
        ),
        "2.1.0" to listOf(
            "Etiqueta: el largo ahora es «Auto» (por defecto): mide el texto y corta la cinta justo, sin gastar de más. Se puede seguir eligiendo un largo fijo.",
            "Impresora: ya no queda conectada todo el tiempo. Se conecta sola al presionar «Imprimir etiqueta» y se suelta al terminar (también al cerrar la pantalla de impresora).",
            "La impresora guardada se conserva: no hay que volver a buscarla.",
            "Pantalla «Impresora y etiqueta» ordenada en dos pestañas: «Etiqueta» (vista previa, datos, textos, tamaño, posición, largo) e «Impresora» (buscar, conectar, olvidar). Los dispositivos sin nombre quedan plegados.",
            "Se recuperó la edición de la etiqueta que había quedado abajo de la lista de impresoras."
        ),
        "2.0.9" to listOf(
            "Impresora y etiqueta: la sección «Impresora» (buscar, conectar, olvidar) ahora está arriba de todo, no al final.",
            "Si falta el permiso de Bluetooth (por ejemplo tras borrar los datos de la app) lo dice en rojo y hay un botón para abrir los permisos."
        ),
        "2.0.8" to listOf(
            "Arreglo: la 2.0.6 y la 2.0.7 se cerraban al abrir. Los datos compartidos ahora arrancan después de que la app termina de iniciar."
        ),
        "2.0.7" to listOf(
            "Datos compartidos activados: ya trae la conexión a la nube. En Preferencias → Datos compartidos toca «Conectar cuenta» con el correo y la clave compartidos."
        ),
        "2.0.6" to listOf(
            "Datos compartidos: todos los teléfonos con la cuenta compartida ven lo mismo (N° de máquina por serie, identidad, interfaz, lecturas y reporte semanal).",
            "Preferencias → «Datos compartidos»: conecta la cuenta una vez; la app sincroniza sola al abrirse, cada 3 min y después de cada cambio, y tiene «Sincronizar ahora».",
            "Funciona sin internet: lo que cambies se sube cuando vuelva la conexión."
        ),
        "2.0.5" to listOf(
            "Cambiar interfaz: después de escribir, el equipo tarda unos 7 s en guardar la configuración. Ahora la app espera a que termine (como STS) y recién ahí relee para verificar.",
            "Si el equipo queda en una interfaz distinta a la pedida, lo dice y muestra la real."
        ),
        "2.0.4" to listOf(
            "SC Advance: al conectar, la app lee la interfaz del equipo (Stand-alone, IGT Netplex, EBDS / Serie o USB), la deja en el Registro y la guarda por serie.",
            "Recuerda la interfaz habitual: si dejas un billetero en stand-alone para probar, muestra a cuál volver.",
            "Nueva sección «Interfaz» con el botón «Cambiar»: pide confirmación mostrando serie, modelo, firmware, bill-set, MDA, interfaz actual, habitual y cambios recientes; después verifica releyendo.",
            "Prueba el cambio primero con el billetero demo (Preferencias → Modo demo)."
        ),
        "2.0.3" to listOf(
            "Preferencias → «Historial de versiones»: lista de todos los cambios de cada versión, de la más nueva a la más antigua."
        ),
        "2.0.2" to listOf(
            "«Real vs sistema» ahora está dentro de Contabilidad: columna «Sistema» (naranja) junto a Actual y Anterior, con aceptados, rechazados, tasa e insertados de la semana."
        ),
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
            "Ventana «Novedades» después de cada actualización, con lo que hay que probar."
        ),
        "1.12.13" to listOf(
            "Nuevo botón «Buscar máquina»: escribe o fotografía un MDA y ve AS, AR de la última lectura y datos del billetero."
        ),
        "1.12.12" to listOf(
            "Preferencias → Probar con una imagen: muestra la foto con el número encima."
        ),
        "1.12.11" to listOf(
            "Pantalla principal: AR y AS lado a lado, con el MDA asociado a la serie.",
            "Sin MDA asociado: botón «Agregar máquina (foto)». La confirmación muestra el número sobre la foto."
        ),
        "1.12.10" to listOf("Preferencias: Actualización y Reporte semanal quedan arriba."),
        "1.12.9" to listOf("Etiqueta en 3 líneas: la fecha va con el firmware y el bill-set, y el bill-set muestra los últimos 3 dígitos."),
        "1.12.8" to listOf("La pantalla no se apaga mientras la app está abierta."),
        "1.12.7" to listOf("El modo demo usa el reporte semanal real importado (solo usa el de ejemplo si no hay ninguno) y no lo pierde al salir."),
        "1.12.6" to listOf("Nombre de la impresora sin signos de relleno al final."),
        "1.12.5" to listOf("Importa el formato reacbill-aceptacion v1 (porcentajes 0–100, null sin dato); rechaza versiones desconocidas."),
        "1.12.4" to listOf("Probar la lectura del N° de máquina con una imagen de la galería; ignora otras etiquetas con letras y prefiere números de 6 dígitos."),
        "1.12.3" to listOf("Se quitaron los textos instructivos y la fila de diferencia; mensajes mínimos."),
        "1.12.2" to listOf(
            "Tamaño de la etiqueta relativo a lo que cabe (Chico / Medio / Máximo), opción de 2 líneas, copias guardadas y vista previa que se actualiza.",
            "Tras borrar la contabilidad, el demo y el comparador usan la lectura previa."
        ),
        "1.12.1" to listOf("El resultado de buscar actualización y el botón para instalarla aparecen en Preferencias."),
        "1.12.0" to listOf(
            "Foto del N° de máquina con lectura de texto en el teléfono.",
            "Etiqueta con N° de máquina, aceptación real (AR) y semanal (AS); textos editables, tamaño y posición.",
            "La aceptación tras borrar usa la última lectura de antes."
        ),
        "1.11.0" to listOf(
            "N° de máquina por serie y comparación con el reporte semanal del sistema (JSON de la Bitácora o CSV).",
            "Correcciones de Bluetooth y de la etiqueta."
        ),
        "1.10.3" to listOf("Versión del bill-set como dato opcional de la etiqueta."),
        "1.10.2" to listOf("La etiqueta muestra solo el número del bill-set, sin la versión que algunos equipos agregan."),
        "1.10.1" to listOf("Posición del texto de la etiqueta (izquierda, centro o derecha; líneas juntas o separadas) y explicación del modo compatible."),
        "1.10.0" to listOf(
            "Pantalla limpia con botones.",
            "Preferencias (engranaje) con impresora, registro, respaldo y actualización.",
            "Modo demo con billetero simulado."
        ),
        "1.9.0" to listOf("Botón para conectar a la impresora guardada y para olvidarla."),
        "1.8.9" to listOf("Elegir los datos de la etiqueta (fecha, modelo, serie, aceptación, firmware, bill-set)."),
        "1.8.8" to listOf("Etiqueta continua por defecto y márgenes laterales mayores."),
        "1.8.7" to listOf("El Registro anota cada evento USB (enchufado / desenchufado) con VID y PID."),
        "1.8.6" to listOf("Firmware y bill-set también con la familia de direcciones E60F/E618."),
        "1.8.5" to listOf("La búsqueda de impresora muestra todos los dispositivos BLE (nombre, dirección y señal), con escaneo rápido."),
        "1.8.4" to listOf("La búsqueda de impresora lista los dispositivos BLE con nombre (los P12 primero) y da más pistas si no encuentra."),
        "1.8.3" to listOf("Registro con Limpiar, Copiar (desde la última limpieza) y Copiar todo."),
        "1.8.2" to listOf("Lee firmware y bill-set también en equipos con direcciones E259/E262."),
        "1.8.1" to listOf("Lectura de bill-set y firmware: no reintenta si el equipo da la misma respuesta; registra el crudo completo."),
        "1.8.0" to listOf("Impresión de etiquetas en impresora P12 por Bluetooth LE (fecha, firmware, aceptación)."),
        "1.7.0" to listOf(
            "Reinicio experimental del Advance.",
            "Tasa de aceptación anterior en vez de diferencia; historial completo a pedido."
        ),
        "1.6.2" to listOf("Tiempos, comandos y reintentos en el Registro (Advance) y tiempo total de lectura."),
        "1.6.1" to listOf("Advance sin SET_INTERFACE en el primer intento, exclusión de operaciones USB, diagnóstico del borrado y no abrir dispositivos de almacenamiento."),
        "1.6.0" to listOf("Exportar e importar historial y registro en JSON (fusiona sin duplicar)."),
        "1.5.1" to listOf("Ícono compatible con íconos temáticos (capa monocroma), PNG con transparencia e íconos clásicos."),
        "1.5.0" to listOf("Carga de bill-set del SC Advance; la app detecta Cashflow o Advance por PID."),
        "1.4.0" to listOf("Soporte SC Advance: identidad, firmware, contabilidad, borrado y calibración."),
        "1.3.10" to listOf("Botones arriba del registro, el borrado exige copia previa, calibración con SET_INTERFACE y «Anterior» = última lectura distinta."),
        "1.3.9" to listOf("SET_INTERFACE antes de cada trama de descarga y SET_CONFIGURATION al abrir."),
        "1.3.8" to listOf("Escritura de bloques más robusta (envío con timeout largo, reescucha antes de reenviar, diagnóstico)."),
        "1.3.7" to listOf("Copiar solo la sesión actual; detalle de contabilidad solo si cambia."),
        "1.3.6" to listOf("Reintenta con reconexión al entrar en modo descarga."),
        "1.3.5" to listOf("Borrar contabilidad usa SET_INTERFACE antes de cada comando."),
        "1.3.4" to listOf("Registra los descriptores USB cuando el equipo no responde a la identidad."),
        "1.3.3" to listOf("Nombre de archivo coherente entre la selección y la biblioteca."),
        "1.3.2" to listOf("Corrige la validación del bill-set (palabras de 16 bits)."),
        "1.3.1" to listOf("Biblioteca de bill-sets guardados en la app."),
        "1.3.0" to listOf("Cargar bill-set (.BIN SC83) desde la app, con la misma secuencia que STS.")
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
