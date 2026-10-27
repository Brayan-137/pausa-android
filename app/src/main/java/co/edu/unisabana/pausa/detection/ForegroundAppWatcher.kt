package co.edu.unisabana.pausa.detection

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * Detecta aplicaciones que pasan a primer plano.
 * Consultar solamente mientras la ventana de riesgo esté activa.
 */
class ForegroundAppWatcher(
    private val context: Context,
    private val onAppAbierta: (paquete: String) -> Unit
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE)
                as UsageStatsManager

    private val handler = Handler(Looper.getMainLooper())

    private var activo = false
    private var ultimaConsulta = 0L
    private var ultimoPaquete: String? = null

    private val tarea = object : Runnable {
        override fun run() {
            if (!activo) return

            consultarEventos()

            if (activo) {
                handler.postDelayed(this, INTERVALO_MS)
            }
        }
    }

    fun iniciar() {
        if (activo || !tieneAccesoUso()) return

        activo = true
        ultimaConsulta = System.currentTimeMillis() - INTERVALO_MS
        handler.post(tarea)
    }

    fun detener() {
        activo = false
        handler.removeCallbacks(tarea)
        ultimoPaquete = null
    }

    private fun tieneAccesoUso(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE)
                as AppOpsManager

        val resultado = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )

        return resultado == AppOpsManager.MODE_ALLOWED
    }

    private fun consultarEventos() {
        val ahora = System.currentTimeMillis()
        val desde = ultimaConsulta

        if (ahora <= desde) return

        val eventos = usageStatsManager.queryEvents(desde, ahora)
        val evento = UsageEvents.Event()
        var ultimoEventoValido: String? = null

        while (eventos.hasNextEvent()) {
            eventos.getNextEvent(evento)

            val esPrimerPlano =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    evento.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    evento.eventType ==
                            UsageEvents.Event.MOVE_TO_FOREGROUND
                }

            if (esPrimerPlano && !evento.packageName.isNullOrBlank()) {
                ultimoEventoValido = evento.packageName
            }
        }

        ultimaConsulta = ahora

        if (ultimoEventoValido != null &&
            ultimoEventoValido != ultimoPaquete
        ) {
            ultimoPaquete = ultimoEventoValido
            onAppAbierta(ultimoEventoValido)
        }
    }

    companion object {
        private const val INTERVALO_MS = 1_000L
    }
}
