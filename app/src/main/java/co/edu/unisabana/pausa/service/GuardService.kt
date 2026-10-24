package co.edu.unisabana.pausa.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import co.edu.unisabana.pausa.PausaApp
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.detection.VentanaDeRiesgo

/**
 * Servicio en primer plano que mantiene viva la protección. Responsable: Juan David.
 *
 * Estado al 24 oct: arranca con su notificación fija y atiende el modo demo.
 * Pendiente (Juan David):
 *  - 24-25 oct: iniciar [co.edu.unisabana.pausa.detection.CallMonitor] y, al terminar una llamada,
 *    consultar [co.edu.unisabana.pausa.detection.UnknownNumberChecker] y abrir la [VentanaDeRiesgo].
 *  - 26 oct: con la ventana abierta, vigilar con [co.edu.unisabana.pausa.detection.ForegroundAppWatcher]
 *    y llamar a OverlayController.show(...) al abrirse una app sensible.
 *  - 28 oct: sobrevivir al reinicio del celular (BOOT_COMPLETED) y a la optimización de batería.
 */
class GuardService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        iniciarEnPrimerPlano()

        if (intent?.action == ACCION_SIMULAR_LLAMADA) {
            // Modo demo: como si acabara de colgar un número desconocido.
            VentanaDeRiesgo.abrir(numero = null, minutos = MINUTOS_VENTANA_DEMO, esDemo = true)
        }
        return START_STICKY
    }

    private fun iniciarEnPrimerPlano() {
        val notificacion = NotificationCompat.Builder(this, PausaApp.CANAL_SERVICIO)
            .setSmallIcon(R.drawable.ic_pausa_notificacion)
            .setContentTitle(getString(R.string.servicio_titulo))
            .setContentText(getString(R.string.servicio_texto))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: el tipo es obligatorio y debe coincidir con el Manifest.
            startForeground(ID_NOTIFICACION, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // Android 12 y 13 no conocen "specialUse": declararlo aquí haría fallar el servicio.
            startForeground(ID_NOTIFICACION, notificacion)
        }
    }

    companion object {
        private const val ID_NOTIFICACION = 1001
        private const val MINUTOS_VENTANA_DEMO = 15
        const val ACCION_SIMULAR_LLAMADA = "co.edu.unisabana.pausa.SIMULAR_LLAMADA"

        fun iniciar(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GuardService::class.java))
        }

        /** Modo demo (Brayan, 29 oct): abre la ventana sin llamada real. */
        fun simularLlamadaTerminada(context: Context) {
            val intent = Intent(context, GuardService::class.java).setAction(ACCION_SIMULAR_LLAMADA)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
