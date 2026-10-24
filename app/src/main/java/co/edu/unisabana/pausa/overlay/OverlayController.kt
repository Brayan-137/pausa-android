package co.edu.unisabana.pausa.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.databinding.OverlayPausaBinding

/**
 * Muestra y oculta el pop-up encima de cualquier app.
 *
 * CONTRATO DE INTEGRACIÓN: el servicio llama a [show] con un [Trigger] y recibe la [Decision]
 * por [onDecision]. Responsable: Brayan.
 *
 * Estado al 24 oct: muestra una tarjeta base con botón Cerrar.
 * Pendiente: preguntas (26 oct), consejo y botones (28 oct), cuenta regresiva (29 oct).
 */
object OverlayController {

    private const val TAG = "OverlayController"
    private var overlayView: View? = null

    val estaVisible: Boolean
        get() = overlayView != null

    fun show(context: Context, trigger: Trigger, onDecision: (Decision) -> Unit = {}) {
        if (overlayView != null) {
            Log.d(TAG, "show() ignorado: ya hay un pop-up visible")
            return
        }
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) {
            Log.w(TAG, "Falta el permiso 'Mostrar sobre otras apps'; no se muestra el pop-up")
            return
        }

        val conTema = ContextThemeWrapper(app, R.style.Theme_Pausa)
        val binding = OverlayPausaBinding.inflate(LayoutInflater.from(conTema))

        binding.overlayBtnCerrar.setOnClickListener {
            hide(app)
            onDecision(Decision.CONTINUAR)
        }
        // El botón atrás NO cierra el pop-up: la persona debe decidir.
        binding.root.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        app.getSystemService(WindowManager::class.java).addView(binding.root, params)
        binding.root.requestFocus()
        overlayView = binding.root
        Log.d(TAG, "Pop-up mostrado para $trigger")
    }

    fun hide(context: Context) {
        val vista = overlayView ?: return
        context.applicationContext.getSystemService(WindowManager::class.java).removeView(vista)
        overlayView = null
    }
}
