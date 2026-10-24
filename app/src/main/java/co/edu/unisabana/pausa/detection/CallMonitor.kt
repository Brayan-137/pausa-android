package co.edu.unisabana.pausa.detection

import android.content.Context

/**
 * Detecta cuándo TERMINA una llamada. Responsable: Juan David (24 oct).
 *
 * Pista: TelephonyManager.registerTelephonyCallback(executor, callback) con un
 * TelephonyCallback que implemente TelephonyCallback.CallStateListener. Una llamada terminó
 * cuando el estado pasa de OFFHOOK o RINGING a IDLE. Requiere READ_PHONE_STATE.
 */
class CallMonitor(
    private val context: Context,
    private val onLlamadaTerminada: (finEnMillis: Long) -> Unit
) {
    fun iniciar() {
        // TODO(Juan David, 24 oct): registrar el TelephonyCallback.
    }

    fun detener() {
        // TODO(Juan David, 24 oct): unregisterTelephonyCallback.
    }
}
