package co.edu.unisabana.pausa.overlay

/**
 * CONTRATO DE INTEGRACIÓN: lo que eligió la persona en el pop-up.
 * Se guarda en el registro de eventos (Julian).
 */
enum class Decision {
    /** Tocó "Verificar ahora": llamó al familiar o al número oficial. */
    VERIFICAR,
    /** Tocó "Es una estafa". */
    ESTAFA,
    /** Tocó "Continuar de todos modos" (o respondió No a todo). */
    CONTINUAR,
    /** Tocó "Sí, ya transferí o di datos" en el botón de pánico. Lo registra PanicoActivity, no el pop-up. */
    PANICO
}

/**
 * CONTRATO DE INTEGRACIÓN: lo que [OverlayController.show] entrega por su callback cuando la
 * persona decide. Con esto el servicio arma el EventoPausa (Julian).
 */
data class ResultadoPausa(
    val decision: Decision,
    /** Respuestas en el formato del registro de eventos, p. ej. "SI,NO,SI". */
    val respuestas: String,
    /** "familiar" o "entidad" si dijeron ser alguien (pregunta 3 = Sí); null en otro caso. */
    val quien: String?
)
