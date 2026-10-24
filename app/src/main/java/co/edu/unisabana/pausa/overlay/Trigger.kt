package co.edu.unisabana.pausa.overlay

/**
 * CONTRATO DE INTEGRACIÓN (no cambiar sin avisar en el grupo).
 *
 * Lo crea el servicio (Juan David) cuando, dentro de la ventana posterior a una llamada
 * desconocida, la persona abre una app sensible. Lo recibe [OverlayController.show] (Brayan).
 */
data class Trigger(
    /** Paquete de la app sensible que se abrió, p. ej. "com.ejemplo.banco". */
    val appPackage: String,
    /** Número de la llamada que terminó. Null si no se pudo leer o en modo demo. */
    val callNumber: String?,
    /** Momento en que terminó la llamada (System.currentTimeMillis()). */
    val callEndedAt: Long,
    /** true si lo disparó el modo demo y no una llamada real. */
    val isDemo: Boolean = false
)
