package co.edu.unisabana.pausa.detection

import android.content.Context

/**
 * Detecta qué app pasa a primer plano. Responsable: Juan David (26 oct).
 *
 * Pista: UsageStatsManager.queryEvents(desde, hasta), buscar eventos ACTIVITY_RESUMED.
 * Consultar cada ~1 s SOLO mientras la VentanaDeRiesgo esté abierta (ahorra batería).
 * Requiere "Acceso a uso" (PACKAGE_USAGE_STATS), que se concede en Ajustes.
 */
class ForegroundAppWatcher(
    private val context: Context,
    private val onAppAbierta: (paquete: String) -> Unit
) {
    fun iniciar() {
        // TODO(Juan David, 26 oct)
    }

    fun detener() {
        // TODO(Juan David, 26 oct)
    }
}
