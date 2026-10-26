package co.edu.unisabana.pausa.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import co.edu.unisabana.pausa.PausaApp
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.detection.CallMonitor
import co.edu.unisabana.pausa.detection.ForegroundAppWatcher
import co.edu.unisabana.pausa.detection.UnknownNumberChecker
import co.edu.unisabana.pausa.detection.VentanaDeRiesgo
import co.edu.unisabana.pausa.overlay.OverlayController
import co.edu.unisabana.pausa.overlay.Trigger

class GuardService : LifecycleService() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var callMonitor: CallMonitor
    private lateinit var numberChecker: UnknownNumberChecker
    private lateinit var appWatcher: ForegroundAppWatcher

    private var detectoresIniciados = false
    private var popUpMostrado = false

    private val revisarVentana = object : Runnable {
        override fun run() {
            if (VentanaDeRiesgo.estaAbierta()) {
                handler.postDelayed(this, 1_000L)
            } else {
                appWatcher.detener()
                popUpMostrado = false
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)
        iniciarEnPrimerPlano()
        iniciarDetectores()

        if (intent?.action == ACCION_SIMULAR_LLAMADA) {
            popUpMostrado = false

            VentanaDeRiesgo.abrir(
                numero = null,
                minutos = MINUTOS_VENTANA_DEMO,
                esDemo = true
            )

            iniciarVigilancia()
        }

        return START_STICKY
    }

    private fun iniciarDetectores() {
        if (detectoresIniciados) return

        numberChecker = UnknownNumberChecker(this)

        appWatcher = ForegroundAppWatcher(this) { paquete ->
            if (
                VentanaDeRiesgo.estaAbierta() &&
                !popUpMostrado &&
                esAppSensibleTemporal(paquete)
            ) {
                mostrarAdvertencia(paquete)
            }
        }

        callMonitor = CallMonitor(this) {
            handler.postDelayed({
                if (!VentanaDeRiesgo.estaAbierta()) {
                    val numero = numberChecker.ultimoNumero()

                    if (numberChecker.esDesconocido(numero)) {
                        popUpMostrado = false

                        VentanaDeRiesgo.abrir(
                            numero = numero,
                            minutos = MINUTOS_VENTANA,
                            esDemo = false
                        )

                        iniciarVigilancia()
                    }
                }
            }, 1_000L)
        }

        callMonitor.iniciar()
        detectoresIniciados = true
    }

    private fun esAppSensibleTemporal(paquete: String): Boolean {
        // SOLO PARA PRUEBAS.
        // Añade aquí el identificador real de una app instalada.
        // Reemplazar por la configuración oficial acordada con Julian.
        return paquete in APPS_SENSIBLES_TEMPORALES
    }

    private fun iniciarVigilancia() {
        if (!VentanaDeRiesgo.estaAbierta()) return

        appWatcher.iniciar()

        handler.removeCallbacks(revisarVentana)
        handler.post(revisarVentana)
    }

    private fun mostrarAdvertencia(paquete: String) {
        if (popUpMostrado || !VentanaDeRiesgo.estaAbierta()) return

        popUpMostrado = true

        // El contrato del proyecto indica cerrar la ventana
        // cuando se dispara el aviso.
        VentanaDeRiesgo.cerrar()
        appWatcher.detener()
        handler.removeCallbacks(revisarVentana)

        val trigger = Trigger(
            appPackage = paquete,
            callNumber = VentanaDeRiesgo.numero,
            callEndedAt = VentanaDeRiesgo.finDeLlamada,
            isDemo = VentanaDeRiesgo.esDemo
        )

        OverlayController.show(this, trigger) {
            // El overlay ya recibió la advertencia.
            // No se necesita volver a cerrar la ventana.
        }

        // Si no se pudo mostrar, no dejar el servicio bloqueado
        // en el estado "pop-up mostrado".
        if (!OverlayController.estaVisible) {
            popUpMostrado = false
        }
    }

    private fun iniciarEnPrimerPlano() {
        val notificacion = NotificationCompat.Builder(
            this,
            PausaApp.CANAL_SERVICIO
        )
            .setSmallIcon(R.drawable.ic_pausa_notificacion)
            .setContentTitle(getString(R.string.servicio_titulo))
            .setContentText(getString(R.string.servicio_texto))
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ID_NOTIFICACION,
                notificacion,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ID_NOTIFICACION, notificacion)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        if (detectoresIniciados) {
            callMonitor.detener()
            appWatcher.detener()
        }

        super.onDestroy()
    }

    companion object {
        private const val ID_NOTIFICACION = 1001
        private const val MINUTOS_VENTANA = 15
        private const val MINUTOS_VENTANA_DEMO = 15

        // Temporal: completar únicamente con paquetes verificados.
        private val APPS_SENSIBLES_TEMPORALES = setOf<String>(
            "com.nequi.MobileApp"
        )

        const val ACCION_SIMULAR_LLAMADA =
            "co.edu.unisabana.pausa.SIMULAR_LLAMADA"

        fun iniciar(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GuardService::class.java)
            )
        }

        fun simularLlamadaTerminada(context: Context) {
            val intent = Intent(
                context,
                GuardService::class.java
            ).setAction(ACCION_SIMULAR_LLAMADA)

            ContextCompat.startForegroundService(context, intent)
        }
    }
}
