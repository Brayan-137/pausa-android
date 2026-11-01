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
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.content.ContentRepository
import co.edu.unisabana.pausa.content.EntidadOficial
import co.edu.unisabana.pausa.databinding.OverlayItemEntidadBinding
import co.edu.unisabana.pausa.databinding.OverlayItemPasoBinding
import co.edu.unisabana.pausa.databinding.OverlayPausaBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import co.edu.unisabana.pausa.overlay.FlujoPreguntas.Paso
import co.edu.unisabana.pausa.settings.SettingsRepository
import co.edu.unisabana.pausa.ui.pintarEtiqueta
import co.edu.unisabana.pausa.ui.panico.PanicoActivity

/**
 * Muestra y oculta el pop-up encima de cualquier app.
 *
 * CONTRATO DE INTEGRACIÓN: el servicio llama a [show] con un [Trigger] y recibe un
 * [ResultadoPausa] (decisión + respuestas) por [onDecision]. Responsable: Brayan.
 *
 * Pantallas (Figma "Pop-up: ...", 1 nov), una por grupo de overlay_pausa.xml:
 *   aviso -> preguntas (+ "¿quién dijo ser?") -> consejo -> "Ver opciones" -> acciones
 *   aviso -> preguntas (todo No) -> todo normal ("Continuar")
 * La lógica de las preguntas vive en [FlujoPreguntas].
 *
 * Botones de acciones:
 *  - Verificar ahora: abre el marcador (ACTION_DIAL, la persona pulsa llamar) y devuelve VERIFICAR.
 *    Si dijeron ser una entidad y numeros_oficiales.json tiene entidades, primero elige a cuál;
 *    cada tarjeta trae "Llamar" y "Abrir página oficial" (ACTION_VIEW), ambos devuelven VERIFICAR.
 *  - Es una estafa: devuelve ESTAFA al tocarlo y muestra los pasos_estafa; "Entendido" cierra y
 *    "Ya transferí o di datos" abre PanicoActivity en la guía.
 *  - Continuar de todos modos: se habilita a los [SEGUNDOS_ESPERA] s y devuelve CONTINUAR. Si el
 *    ajuste "Bloqueo con respiración" está activo, no cierra: lleva a bloqueo (5 min; 1 min en demo)
 *    -> respiración guiada (60 s; 15 s en demo) -> verificar para desbloquear, y solo "Desbloquear
 *    app" devuelve CONTINUAR.
 * [onDecision] se llama a lo sumo una vez por pop-up.
 *
 * Se puede llamar desde cualquier hilo: el controlador pasa al hilo principal.
 */
object OverlayController {

    private const val TAG = "OverlayController"

    /** Segundos que la persona espera antes de poder tocar "Continuar de todos modos". */
    private const val SEGUNDOS_ESPERA = 10

    // Bloqueo con respiración: el modo demo (Trigger.isDemo) acorta los tiempos.
    private const val SEGUNDOS_BLOQUEO = 5 * 60
    private const val SEGUNDOS_BLOQUEO_DEMO = 60
    private const val SEGUNDOS_RESPIRACION = 60
    private const val SEGUNDOS_RESPIRACION_DEMO = 15
    private const val PUNTOS_RESPIRACION = 5
    private const val ESCALA_MIN = 0.7f
    private const val ESCALA_MAX = 1f
    private val principal = Handler(Looper.getMainLooper())

    @Volatile
    private var overlayView: View? = null

    // Cuenta regresiva del botón "Continuar de todos modos". Solo se toca desde el hilo principal.
    private var cuentaRegresiva: CountDownTimer? = null
    private var cuentaTerminada = false

    // Bloqueo con respiración: cuenta del bloqueo y de la respiración guiada. Hilo principal.
    private var cuentaBloqueo: CountDownTimer? = null
    private var cuentaRespiracion: CountDownTimer? = null

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

        // Bloqueo con respiración (tarea 6). Se lee con tope de 500 ms para no frenar el pop-up.
        val bloqueoActivo = runBlocking {
            withTimeoutOrNull(500) { SettingsRepository(app).bloqueoRespiracion.first() }
        } ?: false
        val segundosBloqueo = if (trigger.isDemo) SEGUNDOS_BLOQUEO_DEMO else SEGUNDOS_BLOQUEO
        val segundosRespiracion = if (trigger.isDemo) SEGUNDOS_RESPIRACION_DEMO else SEGUNDOS_RESPIRACION
        var enBloqueo = false
        var respiracionCompleta = false
        var bloqueoTerminado = false

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

        /** Abre la página oficial de la entidad en el navegador; mismo orden que el marcador. */
        fun verificarEnPagina(url: String) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                app.startActivity(intent)
                terminar(Decision.VERIFICAR)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo abrir la página oficial", e)
                Toast.makeText(app, R.string.overlay_sin_navegador, Toast.LENGTH_LONG).show()
            }
        }

        /** "Desbloquear app" se habilita al completar la respiración o al llegar a 0 el bloqueo. */
        fun pintarDesbloqueo(tiempoBloqueo: String? = null) {
            val listo = respiracionCompleta
            binding.overlayEstadoRespiracion.setText(
                if (listo) R.string.desbloqueo_listo else R.string.desbloqueo_pendiente
            )
            if (listo) {
                binding.overlayEstadoRespiracion.pintarEtiqueta(R.color.pausa_exito_fondo, R.color.pausa_exito)
            } else {
                binding.overlayEstadoRespiracion.pintarEtiqueta(R.color.pausa_aviso_fondo, R.color.pausa_aviso)
            }
            val puede = respiracionCompleta || bloqueoTerminado
            binding.overlayBtnDesbloquear.isEnabled = puede
            binding.overlayBtnDesbloquear.text = if (puede || tiempoBloqueo == null) {
                app.getString(R.string.desbloqueo_desbloquear)
            } else {
                app.getString(R.string.desbloqueo_desbloquear_espera, tiempoBloqueo)
            }
        }

        fun iniciarBloqueo() {
            enBloqueo = true
            cancelarCuentaRegresiva()
            mostrarGrupo(binding, binding.overlayGrupoBloqueo)
            binding.overlayBtnRespirar.text = app.getString(R.string.bloqueo_respirar, segundosRespiracion)
            binding.overlayBloqueoTiempo.text = formatoMinutos(segundosBloqueo)
            cuentaBloqueo?.cancel()
            cuentaBloqueo = object : CountDownTimer(segundosBloqueo * 1_000L, 1_000L) {
                override fun onTick(msRestantes: Long) {
                    val texto = formatoMinutos(((msRestantes + 999) / 1_000).toInt())
                    binding.overlayBloqueoTiempo.text = texto
                    pintarDesbloqueo(texto)
                }

                override fun onFinish() {
                    cuentaBloqueo = null
                    bloqueoTerminado = true
                    binding.overlayBloqueoTiempo.text = formatoMinutos(0)
                    pintarDesbloqueo()
                }
            }.start()
        }

        fun mostrarDesbloqueo() {
            mostrarGrupo(binding, binding.overlayGrupoDesbloqueo)
            pintarDesbloqueo(binding.overlayBloqueoTiempo.text.toString())
        }

        fun iniciarRespiracion() {
            mostrarGrupo(binding, binding.overlayGrupoRespiracion)
            if (respiracionCompleta || cuentaRespiracion != null) return

            val circulo = binding.overlayCirculoRespiracion
            circulo.scaleX = ESCALA_MIN
            circulo.scaleY = ESCALA_MIN
            binding.overlayBtnSeguir.isEnabled = false
            val ctx = binding.root.context
            val puntos = binding.overlayPuntosRespiracion
            if (puntos.childCount == 0) crearPuntos(puntos)

            cuentaRespiracion = object : CountDownTimer(segundosRespiracion * 1_000L, 1_000L) {
                override fun onTick(msRestantes: Long) {
                    val restantes = ((msRestantes + 999) / 1_000).toInt()
                    val transcurrido = segundosRespiracion - restantes
                    val fase = FaseRespiracion.en(transcurrido)
                    val nombre = ctx.getString(
                        when (fase.tipo) {
                            FaseRespiracion.Tipo.INHALA -> R.string.respiracion_inhala
                            FaseRespiracion.Tipo.SOSTEN -> R.string.respiracion_sostiene
                            FaseRespiracion.Tipo.EXHALA -> R.string.respiracion_exhala
                        }
                    )
                    binding.overlayFaseNombre.text = nombre
                    binding.overlayFasePastilla.text = nombre
                    binding.overlayFaseSegundos.text = ctx.getString(R.string.respiracion_segundos_fase, fase.restantes)
                    binding.overlayRespiracionRestantes.text = ctx.getString(R.string.respiracion_restantes, restantes)
                    binding.overlayBarraRespiracion.progress = transcurrido * 100 / segundosRespiracion
                    binding.overlayBtnSeguir.text = ctx.getString(R.string.respiracion_seguir_espera, restantes)
                    pintarPuntos(puntos, transcurrido, segundosRespiracion)
                    if (fase.segundoEnFase == 0) animarCirculo(circulo, fase)
                }

                override fun onFinish() {
                    cuentaRespiracion = null
                    respiracionCompleta = true
                    binding.overlayRespiracionRestantes.text = ctx.getString(R.string.respiracion_restantes, 0)
                    binding.overlayBarraRespiracion.progress = 100
                    pintarPuntos(puntos, segundosRespiracion, segundosRespiracion)
                    binding.overlayBtnSeguir.setText(R.string.respiracion_seguir)
                    binding.overlayBtnSeguir.isEnabled = true
                }
            }.start()
        }

        binding.overlayBtnRespirar.setOnClickListener { iniciarRespiracion() }
        binding.overlayBtnBloqueoVerificar.setOnClickListener { binding.overlayBtnVerificar.performClick() }
        binding.overlayBtnSeguir.setOnClickListener { mostrarDesbloqueo() }
        binding.overlayBtnDesbloqueoLlamar.setOnClickListener { binding.overlayBtnVerificar.performClick() }
        binding.overlayBtnDesbloquear.setOnClickListener { terminar(Decision.CONTINUAR) }

        binding.overlayBtnEmpezar.setOnClickListener { dibujar(binding, flujo, contenido) }
        binding.overlayBtnVolver.setOnClickListener {
            flujo.volver()
            dibujar(binding, flujo, contenido)
        }
        binding.overlayBtnVerOpciones.setOnClickListener { mostrarAcciones(binding) }

        binding.overlayBtnVerificar.setOnClickListener {
            val quien = (flujo.paso as? Paso.Resultado)?.quien
            val entidades = if (quien == "entidad") entidadesOficiales(contenido) else emptyList()
            if (entidades.isEmpty()) {
                // Familiar o caso general (o aún no hay números oficiales): marcador vacío para
                // que la persona elija el número que ella ya tiene guardado.
                verificarCon(null)
            } else {
                dibujarEntidades(binding, entidades, ::verificarCon, ::verificarEnPagina)
            }
        }
        binding.overlayBtnVolverResultado.setOnClickListener {
            when {
                !enBloqueo -> mostrarAcciones(binding)
                respiracionCompleta || bloqueoTerminado -> mostrarDesbloqueo()
                else -> mostrarGrupo(binding, binding.overlayGrupoBloqueo)
            }
        }

        binding.overlayBtnEstafa.setOnClickListener {
            decidir(Decision.ESTAFA)
            dibujarEstafa(binding, contenido)
        }
        // Se lanza ANTES de ocultar el pop-up (igual que el marcador). La decisión ya fue ESTAFA;
        // el evento PANICO lo registra PanicoActivity.
        binding.overlayBtnPanico.setOnClickListener {
            try {
                app.startActivity(PanicoActivity.intent(app, irALaGuia = true))
                ocultar(app)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo abrir el botón de pánico", e)
            }
        }
        // La decisión ESTAFA ya se avisó al tocar "Es una estafa"; aquí solo se cierra.
        binding.overlayBtnEntendido.setOnClickListener { ocultar(app) }

        binding.overlayBtnContinuar.setOnClickListener {
            if (bloqueoActivo) iniciarBloqueo() else terminar(Decision.CONTINUAR)
        }
        binding.overlayBtnContinuarNormal.setOnClickListener { terminar(Decision.CONTINUAR) }
        // El botón atrás NO cierra el pop-up: la persona debe decidir.
        binding.root.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }

        // Pantalla completa: el contenido no debe quedar debajo de la barra de estado, el
        // recorte de la cámara ni la barra de gestos.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { vista, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            WindowInsetsCompat.CONSUMED
        }

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
        cancelarBloqueo()
        val vista = overlayView ?: return
        overlayView = null
        try {
            app.getSystemService(WindowManager::class.java).removeView(vista)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "El pop-up ya no estaba en pantalla", e)
        }
    }

    /** Deja visible solo [grupo] (una pantalla del Figma). */
    private fun mostrarGrupo(b: OverlayPausaBinding, grupo: View) {
        listOf(
            b.overlayGrupoIntro, b.overlayGrupoPregunta, b.overlayGrupoConsejo,
            b.overlayGrupoResultado, b.overlayGrupoNormal, b.overlayGrupoVerificar,
            b.overlayGrupoEstafa, b.overlayGrupoBloqueo, b.overlayGrupoRespiracion,
            b.overlayGrupoDesbloqueo
        ).forEach { it.visibility = if (it === grupo) View.VISIBLE else View.GONE }
    }

    /** Muestra la pantalla que corresponde al paso actual del flujo. */
    private fun dibujar(
        b: OverlayPausaBinding,
        flujo: FlujoPreguntas,
        contenido: ContentRepository,
        intro: Boolean = false
    ) {
        if (intro) {
            mostrarGrupo(b, b.overlayGrupoIntro)
            return
        }

        val ctx = b.root.context
        when (val paso = flujo.paso) {
            is Paso.Pregunta -> {
                mostrarGrupo(b, b.overlayGrupoPregunta)
                ponerProgreso(b, paso.numero, paso.total)
                b.overlayPregunta.text = paso.texto
                b.overlayBtnOpcionA.setText(R.string.overlay_si)
                b.overlayBtnOpcionB.setText(R.string.overlay_no)
                b.overlayBtnOpcionA.setOnClickListener { flujo.responder(true); dibujar(b, flujo, contenido) }
                b.overlayBtnOpcionB.setOnClickListener { flujo.responder(false); dibujar(b, flujo, contenido) }
                b.overlayBtnVolver.visibility = if (flujo.puedeVolver) View.VISIBLE else View.INVISIBLE
            }

            is Paso.Quien -> {
                mostrarGrupo(b, b.overlayGrupoPregunta)
                // En el Figma "¿Quién dijo ser?" se ve como la última pregunta, con la barra llena.
                val total = contenido.preguntas().size
                ponerProgreso(b, total, total)
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
                val (titulo, detalle) = dividirConsejo(contenido.consejo(paso.claveConsejo))
                if (paso.todoNormal) {
                    // Nada que verificar: un solo botón, disponible de inmediato.
                    cancelarCuentaRegresiva()
                    mostrarGrupo(b, b.overlayGrupoNormal)
                    b.overlayNormalTitulo.text = titulo
                    b.overlayNormalDetalle.text = detalle
                    b.overlayNormalDetalle.visibility = if (detalle.isEmpty()) View.GONE else View.VISIBLE
                } else {
                    mostrarGrupo(b, b.overlayGrupoConsejo)
                    val chip = when (paso.quien) {
                        "familiar" -> ctx.getString(R.string.overlay_chip_familiar)
                        "entidad" -> ctx.getString(R.string.overlay_chip_entidad)
                        else -> null
                    }
                    b.overlayChipQuien.text = chip
                    b.overlayChipQuien.visibility = if (chip == null) View.GONE else View.VISIBLE
                    b.overlayConsejoTitulo.text = titulo
                    b.overlayConsejoDetalle.text = detalle
                    val conDetalle = if (detalle.isEmpty()) View.GONE else View.VISIBLE
                    b.overlayConsejoDetalle.visibility = conDetalle
                    b.overlayConsejoDivisor.visibility = conDetalle
                }
            }
        }
    }

    private fun ponerProgreso(b: OverlayPausaBinding, numero: Int, total: Int) {
        b.overlayProgreso.text = b.root.context.getString(R.string.overlay_progreso, numero, total)
        b.overlayBarraProgreso.progress = if (total > 0) numero * 100 / total else 0
    }

    /** Pantalla "¿Qué quieres hacer?" (tras "Ver opciones" o al volver de Verificar). */
    private fun mostrarAcciones(b: OverlayPausaBinding) {
        mostrarGrupo(b, b.overlayGrupoResultado)
        iniciarCuentaRegresiva(b)
    }

    /** Entidades con teléfono y fuente. Un JSON mal editado no debe tumbar el pop-up. */
    private fun entidadesOficiales(contenido: ContentRepository): List<EntidadOficial> =
        try {
            contenido.entidadesOficiales()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo leer assets/content/numeros_oficiales.json", e)
            emptyList()
        }

    /** Una tarjeta por entidad, cada una con "Llamar" y "Abrir página oficial". */
    private fun dibujarEntidades(
        b: OverlayPausaBinding,
        entidades: List<EntidadOficial>,
        alLlamar: (telefono: String) -> Unit,
        alAbrirPagina: (url: String) -> Unit
    ) {
        val ctx = b.root.context
        val inflador = LayoutInflater.from(ctx)
        val margen = (16 * ctx.resources.displayMetrics.density).toInt()

        mostrarGrupo(b, b.overlayGrupoVerificar)
        b.overlayListaEntidades.removeAllViews()

        entidades.forEachIndexed { i, entidad ->
            val item = OverlayItemEntidadBinding.inflate(inflador, b.overlayListaEntidades, false)
            item.itemEntidadNombre.text = entidad.nombre
            item.itemEntidadTelefono.text = entidad.telefono
            item.itemEntidadFuente.text = ctx.getString(R.string.overlay_fuente, entidad.fuente)
            item.itemEntidadBtnLlamar.setOnClickListener { alLlamar(entidad.telefono) }
            item.itemEntidadBtnPagina.setOnClickListener { alAbrirPagina(entidad.fuente) }
            val medidas = item.root.layoutParams as LinearLayout.LayoutParams
            if (i > 0) medidas.topMargin = margen
            b.overlayListaEntidades.addView(item.root, medidas)
        }
    }

    /** Pantalla "No transfieras": una tarjeta numerada por cada paso de pasos_estafa. */
    private fun dibujarEstafa(b: OverlayPausaBinding, contenido: ContentRepository) {
        cancelarCuentaRegresiva()
        val pasos = try {
            contenido.pasosEstafa()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo leer pasos_estafa de preguntas.json", e)
            emptyList()
        }
        mostrarGrupo(b, b.overlayGrupoEstafa)

        val ctx = b.root.context
        val inflador = LayoutInflater.from(ctx)
        val margen = (18 * ctx.resources.displayMetrics.density).toInt()
        b.overlayListaPasos.removeAllViews()
        pasos.forEachIndexed { i, paso ->
            val item = OverlayItemPasoBinding.inflate(inflador, b.overlayListaPasos, false)
            item.itemPasoNumero.text = (i + 1).toString()
            item.itemPasoTexto.text = paso.trim().removeSuffix(".")
            val medidas = item.root.layoutParams as LinearLayout.LayoutParams
            if (i > 0) medidas.topMargin = margen
            b.overlayListaPasos.addView(item.root, medidas)
        }
    }

    private fun ponerContinuar(b: OverlayPausaBinding, @StringRes texto: Int) {
        b.overlayBtnContinuar.isEnabled = true
        b.overlayBtnContinuar.setText(texto)
    }

    /**
     * Deshabilita "Continuar de todos modos" [SEGUNDOS_ESPERA] s mostrando los segundos que faltan.
     * Arranca la primera vez que se ve la pantalla de acciones y no se reinicia al volver de Verificar.
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

    /** Cancela las cuentas del bloqueo y de la respiración y detiene la animación del círculo. */
    private fun cancelarBloqueo() {
        cuentaBloqueo?.cancel()
        cuentaBloqueo = null
        cuentaRespiracion?.cancel()
        cuentaRespiracion = null
        overlayView?.findViewById<View>(R.id.overlayCirculoRespiracion)?.animate()?.cancel()
    }

    /** Crece al inhalar, se mantiene al sostener y se encoge al exhalar. */
    private fun animarCirculo(circulo: View, fase: FaseRespiracion) {
        val escala = when (fase.tipo) {
            FaseRespiracion.Tipo.INHALA -> ESCALA_MAX
            FaseRespiracion.Tipo.SOSTEN -> return
            FaseRespiracion.Tipo.EXHALA -> ESCALA_MIN
        }
        circulo.animate().cancel()
        circulo.animate()
            .scaleX(escala).scaleY(escala)
            .setDuration(fase.duracion * 1_000L)
            .setInterpolator(LinearInterpolator())
            .start()
    }

    private fun crearPuntos(contenedor: LinearLayout) {
        val ctx = contenedor.context
        val densidad = ctx.resources.displayMetrics.density
        val lado = (10 * densidad).toInt()
        val margen = (5 * densidad).toInt()
        repeat(PUNTOS_RESPIRACION) {
            val punto = View(ctx).apply { setBackgroundResource(R.drawable.bg_pausa_circulo) }
            contenedor.addView(punto, LinearLayout.LayoutParams(lado, lado).apply {
                setMargins(margen, 0, margen, 0)
            })
        }
    }

    /** El punto activo avanza con el tiempo; los anteriores quedan llenos. */
    private fun pintarPuntos(contenedor: LinearLayout, transcurrido: Int, total: Int) {
        val ctx = contenedor.context
        val activo = (transcurrido * PUNTOS_RESPIRACION / total).coerceAtMost(PUNTOS_RESPIRACION - 1)
        for (i in 0 until contenedor.childCount) {
            val color = if (i <= activo) R.color.pausa_popup_principal else R.color.pausa_popup_circulo_anillo
            contenedor.getChildAt(i).backgroundTintList = ContextCompat.getColorStateList(ctx, color)
        }
    }

    private fun formatoMinutos(segundos: Int): String =
        "%02d:%02d".format(segundos / 60, segundos % 60)

    private fun cancelarCuentaRegresiva() {
        cuentaRegresiva?.cancel()
        cuentaRegresiva = null
    }
}
