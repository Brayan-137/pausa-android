package co.edu.unisabana.pausa.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.content.ContentRepository
import co.edu.unisabana.pausa.databinding.OverlayPausaBinding
import co.edu.unisabana.pausa.overlay.FlujoPreguntas.Paso

/**
 * Muestra y oculta el pop-up encima de cualquier app.
 *
 * CONTRATO DE INTEGRACIÓN: el servicio llama a [show] con un [Trigger] y recibe la [Decision]
 * por [onDecision]. Responsable: Brayan.
 *
 * Estado al 26 oct: pop-up encima de otras apps con intro, 3 preguntas (+ "¿quién dijo ser?")
 * y un resultado con el consejo. La lógica de las preguntas vive en [FlujoPreguntas].
 * Pendiente: botones Verificar / Es una estafa / Continuar (28 oct), cuenta regresiva (29 oct).
 *
 * Se puede llamar desde cualquier hilo: el controlador pasa al hilo principal.
 */
object OverlayController {

    private const val TAG = "OverlayController"
    private val principal = Handler(Looper.getMainLooper())

    @Volatile
    private var overlayView: View? = null

    val estaVisible: Boolean
        get() = overlayView != null

    fun show(context: Context, trigger: Trigger, onDecision: (Decision) -> Unit = {}) {
        val app = context.applicationContext
        enHiloPrincipal { mostrar(app, trigger, onDecision) }
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        enHiloPrincipal { ocultar(app) }
    }

    private fun enHiloPrincipal(bloque: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) bloque() else principal.post(bloque)
    }

    private fun mostrar(app: Context, trigger: Trigger, onDecision: (Decision) -> Unit) {
        if (overlayView != null) {
            Log.d(TAG, "show() ignorado: ya hay un pop-up visible")
            return
        }
        if (!Settings.canDrawOverlays(app)) {
            Log.w(TAG, "Falta el permiso 'Mostrar sobre otras apps'; no se muestra el pop-up")
            return
        }

        val contenido = ContentRepository(app)
        val flujo = try {
            FlujoPreguntas(contenido.preguntas(), contenido.subpreguntaQuien())
        } catch (e: Exception) {
            // JSON mal editado: preferimos no mostrar nada a tumbar el servicio.
            Log.e(TAG, "No se pudo leer assets/content/preguntas.json", e)
            return
        }

        val conTema = ContextThemeWrapper(app, R.style.Theme_Pausa)
        val binding = OverlayPausaBinding.inflate(LayoutInflater.from(conTema))

        fun terminar(decision: Decision) {
            ocultar(app)
            onDecision(decision)
        }

        binding.overlayBtnEmpezar.setOnClickListener { dibujar(binding, flujo, contenido) }
        binding.overlayBtnVolver.setOnClickListener {
            flujo.volver()
            dibujar(binding, flujo, contenido)
        }
        binding.overlayBtnCerrar.setOnClickListener { terminar(Decision.CONTINUAR) }
        // El botón atrás NO cierra el pop-up: la persona debe decidir.
        binding.root.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Que también cubra el recorte (notch) en modo horizontal.
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            app.getSystemService(WindowManager::class.java).addView(binding.root, params)
        } catch (e: Exception) {
            // Permiso revocado justo ahora, o el sistema rechazó la ventana.
            Log.e(TAG, "No se pudo agregar el pop-up", e)
            return
        }
        binding.root.requestFocus()
        overlayView = binding.root
        dibujar(binding, flujo, contenido, intro = true)
        Log.d(TAG, "Pop-up mostrado para $trigger")
    }

    private fun ocultar(app: Context) {
        val vista = overlayView ?: return
        overlayView = null
        try {
            app.getSystemService(WindowManager::class.java).removeView(vista)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "El pop-up ya no estaba en pantalla", e)
        }
    }

    /** Muestra el grupo que corresponde al paso actual del flujo. */
    private fun dibujar(
        b: OverlayPausaBinding,
        flujo: FlujoPreguntas,
        contenido: ContentRepository,
        intro: Boolean = false
    ) {
        b.overlayGrupoIntro.visibility = if (intro) View.VISIBLE else View.GONE
        if (intro) {
            b.overlayGrupoPregunta.visibility = View.GONE
            b.overlayGrupoResultado.visibility = View.GONE
            return
        }

        val ctx = b.root.context
        when (val paso = flujo.paso) {
            is Paso.Pregunta -> {
                b.overlayTitulo.setText(R.string.overlay_titulo)
                b.overlayGrupoPregunta.visibility = View.VISIBLE
                b.overlayGrupoResultado.visibility = View.GONE
                b.overlayProgreso.visibility = View.VISIBLE
                b.overlayProgreso.text = ctx.getString(R.string.overlay_progreso, paso.numero, paso.total)
                b.overlayPregunta.text = paso.texto
                b.overlayBtnOpcionA.setText(R.string.overlay_si)
                b.overlayBtnOpcionB.setText(R.string.overlay_no)
                b.overlayBtnOpcionA.setOnClickListener { flujo.responder(true); dibujar(b, flujo, contenido) }
                b.overlayBtnOpcionB.setOnClickListener { flujo.responder(false); dibujar(b, flujo, contenido) }
                b.overlayBtnVolver.visibility = if (flujo.puedeVolver) View.VISIBLE else View.INVISIBLE
            }

            is Paso.Quien -> {
                b.overlayTitulo.setText(R.string.overlay_titulo)
                b.overlayGrupoPregunta.visibility = View.VISIBLE
                b.overlayGrupoResultado.visibility = View.GONE
                b.overlayProgreso.visibility = View.GONE
                b.overlayPregunta.text = paso.texto
                // Dos opciones fijas en el JSON (familiar / entidad); los botones toman su texto.
                val (a, c) = paso.opciones
                b.overlayBtnOpcionA.text = a.texto
                b.overlayBtnOpcionB.text = c.texto
                b.overlayBtnOpcionA.setOnClickListener { flujo.elegirQuien(a.id); dibujar(b, flujo, contenido) }
                b.overlayBtnOpcionB.setOnClickListener { flujo.elegirQuien(c.id); dibujar(b, flujo, contenido) }
                b.overlayBtnVolver.visibility = View.VISIBLE
            }

            is Paso.Resultado -> {
                b.overlayTitulo.setText(
                    if (paso.todoNormal) R.string.overlay_titulo_normal else R.string.overlay_titulo_alerta
                )
                b.overlayGrupoPregunta.visibility = View.GONE
                b.overlayGrupoResultado.visibility = View.VISIBLE
                b.overlayConsejo.text = contenido.consejo(paso.claveConsejo)
            }
        }
    }
}
