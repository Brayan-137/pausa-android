package co.edu.unisabana.pausa.detection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * Detecta cuándo termina una llamada.
 */
class CallMonitor(
    private val context: Context,
    private val onLlamadaTerminada: (finEnMillis: Long) -> Unit
) {
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val executor: Executor = ContextCompat.getMainExecutor(context)

    private var estadoAnterior = TelephonyManager.CALL_STATE_IDLE
    private var callbackRegistrado = false

    private val callback = object : TelephonyCallback(),
        TelephonyCallback.CallStateListener {

        override fun onCallStateChanged(state: Int) {
            val llamadaTerminada =
                state == TelephonyManager.CALL_STATE_IDLE &&
                        (estadoAnterior == TelephonyManager.CALL_STATE_OFFHOOK ||
                                estadoAnterior == TelephonyManager.CALL_STATE_RINGING)

            estadoAnterior = state

            if (llamadaTerminada) {
                onLlamadaTerminada(System.currentTimeMillis())
            }
        }
    }

    fun iniciar() {
        if (callbackRegistrado) return

        val permisoConcedido = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!permisoConcedido) return

        telephonyManager.registerTelephonyCallback(executor, callback)
        callbackRegistrado = true
    }

    fun detener() {
        if (!callbackRegistrado) return

        telephonyManager.unregisterTelephonyCallback(callback)
        callbackRegistrado = false
    }
}
