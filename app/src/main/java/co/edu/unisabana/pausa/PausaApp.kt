package co.edu.unisabana.pausa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

/**
 * Punto de entrada de la app. Crea el canal de notificaciones que usa [service.GuardService].
 */
class PausaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        crearCanalDeNotificaciones()
    }

    private fun crearCanalDeNotificaciones() {
        val canal = NotificationChannel(
            CANAL_SERVICIO,
            getString(R.string.canal_servicio_nombre),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.canal_servicio_descripcion)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    companion object {
        const val CANAL_SERVICIO = "pausa_servicio"
    }
}
