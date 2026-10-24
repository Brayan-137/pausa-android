package co.edu.unisabana.pausa.detection

/**
 * Estado compartido: "acaba de terminar una llamada de un número desconocido y estamos dentro
 * de los minutos en que conviene advertir". Responsable: Juan David.
 *
 * Lo abre el servicio al terminar una llamada desconocida (o el modo demo);
 * lo consulta el servicio antes de mostrar el pop-up.
 */
object VentanaDeRiesgo {

    @Volatile private var abiertaHasta: Long = 0L

    @Volatile var numero: String? = null
        private set

    @Volatile var finDeLlamada: Long = 0L
        private set

    @Volatile var esDemo: Boolean = false
        private set

    fun abrir(
        numero: String?,
        minutos: Int,
        esDemo: Boolean = false,
        ahora: Long = System.currentTimeMillis()
    ) {
        this.numero = numero
        this.finDeLlamada = ahora
        this.esDemo = esDemo
        abiertaHasta = ahora + minutos * 60_000L
    }

    fun estaAbierta(ahora: Long = System.currentTimeMillis()): Boolean = ahora < abiertaHasta

    /** Se cierra cuando ya se mostró el pop-up, para no repetirlo en la misma ventana. */
    fun cerrar() {
        abiertaHasta = 0L
    }
}
