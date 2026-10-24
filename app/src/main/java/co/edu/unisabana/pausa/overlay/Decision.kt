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
    CONTINUAR
}
