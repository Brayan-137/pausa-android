package co.edu.unisabana.pausa.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.StringRes
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.content.ContentRepository
import co.edu.unisabana.pausa.content.EntidadOficial
import co.edu.unisabana.pausa.databinding.OverlayPausaBinding
import co.edu.unisabana.pausa.overlay.FlujoPreguntas.Paso
import com.google.android.material.button.MaterialButton

/**
 * Muestra y oculta el pop-up encima de cualquier app.
 *
 * CONTRATO DE INTEGRACIÓN: el servicio llama a [show] con un [Trigger] y recibe un
 * [ResultadoPausa] (decisión + respuestas) por [onDecision]. Responsable: Brayan.
 *
 * Estado al 28 oct: pop-up encima de otras apps con intro, 3 preguntas (+ "¿quién dijo ser?"),
 * consejo por caso y tres botones. La lógica de las preguntas vive en [FlujoPreguntas].
 *
 * Botones del resultado (con todo No solo sale "Continuar"):
 *  - Verificar ahora: abre el marcador (ACTION_DIAL, la persona pulsa llamar) y devuelve VERIFICAR.
 *    Si dijeron ser una entidad y numeros_oficiales.json tiene entidades, primero elige a cuál.
 *  - Es una estafa: devuelve ESTAFA al tocarlo y muestra los pasos_estafa; "Entendido" cierra.
 *  - Continuar de todos modos: se habilita a los [SEGUNDOS_ESPERA] s y devuelve CONTINUAR.
 * [onDecision] se llama a lo sumo una vez por pop-up.
 *
 * Pendiente: modo demo y cuenta regresiva (29 oct).
 *
 * Se puede llamar desde cualquier hilo: el controlador pasa al hilo principal.
 */
object OverlayController {

    private const val TAG = "OverlayController"

    /** Segundos que la persona espera antes de poder tocar "Continuar de todos modos". */
    private const val SEGUNDOS_ESPERA = 10
    private val principal = Handler(Looper.getMainLooper())

    @Volatile
    private var overlayView: View? = null

    // Cuenta regresiva del botón "Continuar de todos modos". Solo se toca desde el hilo principal.
    private var cuentaRegresiva: CountDownTimer? = null
    private var cuentaTerminada = false

    val estaVisible: Boolean
        get() = overlayView != null

    fun show(context: Context, trigger: Trigger, onDecision: (ResultadoPausa) -> Unit = {}) {
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

    private fun mostrar(app: Context, trigger: Trigger, onDecision: (ResultadoPausa) -> Unit) {
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

        cuentaTerminada = false
        var decidido = false

        /** Avisa la decisión (con las respuestas) una sola vez, sin cerrar el pop-up. */
        fun decidir(decision: Decision) {
            if (decidido) return
            decidido = true
            // Todas las decisiones se toman desde el paso Resultado, que trae las respuestas.
            val resultado = flujo.paso as? Paso.Resultado
            onDecision(
                ResultadoPausa(
                    decision = decision,
                    respuestas = resultado?.respuestas.orEmpty(),
                    quien = resultado?.quien
                )
            )
        }

        fun terminar(decision: Decision) {
            ocultar(app)
            decidir(decision)
        }

        /**
         * Abre el marcador con el número escrito (o vacío) sin llamar por la persona.
         * Se lanza ANTES de ocultar el pop-up: mientras su ventana siga visible, Android
         * permite abrir una actividad desde el servicio en segundo plano.
         */
        fun abrirMarcador(telefono: String?): Boolean {
            val intent = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!telefono.isNullOrBlank()) intent.data = Uri.fromParts("tel", telefono, null)
            return try {
                app.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo abrir el marcador", e)
                Toast.makeText(app, R.string.overlay_sin_marcador, Toast.LENGTH_LONG).show()
                false
            }
        }

        fun verificarCon(telefono: String?) {
            if (abrirMarcador(telefono)) terminar(Decision.VERIFICAR)
        }

        binding.overlayBtnEmpezar.setOnClickListener { dibujar(binding, flujo, contenido) }
        binding.overlayBtnVolver.setOnClickListener {
            flujo.volver()
            dibujar(binding, flujo, contenido)
        }

        binding.overlayBtnVerificar.setOnClickListener {
            val quien = (flujo.paso as? Paso.Resultado)?.quien
            val entidades = if (quien == "entidad") entidadesOficiales(contenido) else emptyList()
            if (entidades.isEmpty()) {
                // Familiar o caso general (o aún no hay números oficiales): marcador vacío para
                // que la persona elija el número que ella ya tiene guardado.
                verificarCon(null)
            } else {
                dibujarEntidades(binding, entidades) { telefono -> verificarCon(telefono) }
            }
        }
        binding.overlayBtnMarcador.setOnClickListener { verificarCon(null) }
        binding.overlayBtnVolverResultado.setOnClickListener { dibujar(binding, flujo, contenido) }

        binding.overlayBtnEstafa.setOnClickListener {
            decidir(Decision.ESTAFA)
            dibujarEstafa(binding, contenido)
        }
        // La decisión ESTAFA ya se avisó al tocar "Es una estafa"; aquí solo se cierra.
        binding.overlayBtnEntendido.setOnClickListener { ocultar(app) }

        binding.overlayBtnContinuar.setOnClickListener { terminar(Decision.CONTINUAR) }
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
        cancelarCuentaRegresiva()
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
        b.overlayGrupoVerificar.visibility = View.GONE
        b.overlayGrupoEstafa.visibility = View.GONE
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

                if (paso.todoNormal) {
                    // Nada que verificar: un solo botón, disponible de inmediato.
                    cancelarCuentaRegresiva()
                    b.overlayBtnVerificar.visibility = View.GONE
                    b.overlayBtnEstafa.visibility = View.GONE
                    ponerContinuar(b, R.string.overlay_continuar)
                } else {
                    b.overlayBtnVerificar.visibility = View.VISIBLE
                    b.overlayBtnEstafa.visibility = View.VISIBLE
                    iniciarCuentaRegresiva(b)
                }
            }
        }
    }

    /** Entidades con teléfono y fuente. Un JSON mal editado no debe tumbar el pop-up. */
    private fun entidadesOficiales(contenido: ContentRepository): List<EntidadOficial> =
        try {
            contenido.entidadesOficiales()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo leer assets/content/numeros_oficiales.json", e)
            emptyList()
        }

    /** Un botón por entidad; al tocarlo se llama a [alElegir] con su teléfono. */
    private fun dibujarEntidades(
        b: OverlayPausaBinding,
        entidades: List<EntidadOficial>,
        alElegir: (telefono: String) -> Unit
    ) {
        val ctx = b.root.context
        val densidad = ctx.resources.displayMetrics.density

        b.overlayTitulo.setText(R.string.overlay_titulo_verificar)
        b.overlayGrupoResultado.visibility = View.GONE
        b.overlayGrupoVerificar.visibility = View.VISIBLE

        b.overlayListaEntidades.removeAllViews()
        entidades.forEach { entidad ->
            val boton = MaterialButton(ctx).apply {
                text = ctx.getString(R.string.overlay_llamar_a, entidad.nombre)
                textSize = 20f
                minHeight = (64 * densidad).toInt()
                minimumHeight = (64 * densidad).toInt()
                setOnClickListener { alElegir(entidad.telefono) }
            }
            val medidas = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * densidad).toInt() }
            b.overlayListaEntidades.addView(boton, medidas)
        }
    }

    /** Muestra qué hacer (pasos_estafa de preguntas.json). */
    private fun dibujarEstafa(b: OverlayPausaBinding, contenido: ContentRepository) {
        cancelarCuentaRegresiva()
        val pasos = try {
            contenido.pasosEstafa()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo leer pasos_estafa de preguntas.json", e)
            emptyList()
        }
        b.overlayTitulo.setText(R.string.overlay_titulo_estafa)
        b.overlayGrupoResultado.visibility = View.GONE
        b.overlayGrupoVerificar.visibility = View.GONE
        b.overlayGrupoEstafa.visibility = View.VISIBLE
        b.overlayPasosEstafa.text =
            pasos.mapIndexed { i, paso -> "${i + 1}. $paso" }.joinToString("\n\n")
    }

    private fun ponerContinuar(b: OverlayPausaBinding, @StringRes texto: Int) {
        b.overlayBtnContinuar.isEnabled = true
        b.overlayBtnContinuar.setText(texto)
    }

    /**
     * Deshabilita "Continuar de todos modos" [SEGUNDOS_ESPERA] s mostrando los segundos que faltan.
     * No se reinicia si la persona vuelve desde la lista de entidades.
     */
    private fun iniciarCuentaRegresiva(b: OverlayPausaBinding) {
        if (cuentaTerminada) {
            ponerContinuar(b, R.string.overlay_continuar_todos_modos)
            return
        }
        if (cuentaRegresiva != null) return

        val ctx = b.root.context
        b.overlayBtnContinuar.isEnabled = false
        b.overlayBtnContinuar.text = ctx.getString(R.string.overlay_continuar_espera, SEGUNDOS_ESPERA)
        cuentaRegresiva = object : CountDownTimer(SEGUNDOS_ESPERA * 1_000L, 1_000L) {
            override fun onTick(msRestantes: Long) {
                val segundos = ((msRestantes + 999) / 1_000).toInt()
                b.overlayBtnContinuar.text = ctx.getString(R.string.overlay_continuar_espera, segundos)
            }

            override fun onFinish() {
                cuentaRegresiva = null
                cuentaTerminada = true
                ponerContinuar(b, R.string.overlay_continuar_todos_modos)
            }
        }.start()
    }

    private fun cancelarCuentaRegresiva() {
        cuentaRegresiva?.cancel()
        cuentaRegresiva = null
    }
}
