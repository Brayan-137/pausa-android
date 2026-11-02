package co.edu.unisabana.pausa.ui.panico

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.content.ContentRepository
import co.edu.unisabana.pausa.databinding.ActivityPanicoBinding
import co.edu.unisabana.pausa.databinding.OverlayItemPasoBinding
import co.edu.unisabana.pausa.databinding.ItemPanicoPasoBinding
import co.edu.unisabana.pausa.log.EventoPausa
import co.edu.unisabana.pausa.log.RegistroEventos
import co.edu.unisabana.pausa.overlay.Decision
import co.edu.unisabana.pausa.settings.SettingsRepository
import co.edu.unisabana.pausa.ui.contacto.SelectorContacto
import co.edu.unisabana.pausa.ui.numeros.NumerosOficialesActivity
import co.edu.unisabana.pausa.ui.respetarBarrasDelSistema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Botón de pánico: ¿ya transferiste o diste datos? -> guía paso a paso -> aviso por SMS al
 * contacto de confianza (se abre la app de mensajes con el texto listo; Pausa no envía nada).
 * Una sola pantalla que cambia de grupo, como el pop-up. Con [EXTRA_IR_A_GUIA] (desde "Ya
 * transferí o di datos" del pop-up) abre directo en la guía.
 *
 * Registra un EventoPausa con decision = PANICO al entrar a la guía.
 */
class PanicoActivity : AppCompatActivity() {

    private enum class Pantalla { PREGUNTA, PASOS, GUIA, SMS }

    private lateinit var binding: ActivityPanicoBinding
    private val ajustes by lazy { SettingsRepository(applicationContext) }

    private var pantalla = Pantalla.PREGUNTA
    private var contactoNombre: String? = null
    private var contactoTelefono: String? = null
    private var registrado = false

    // Se crea aquí (antes de STARTED) porque registerForActivityResult lo exige.
    private val selectorContacto = SelectorContacto(this) { contacto ->
        contactoNombre = contacto.nombre
        contactoTelefono = contacto.telefono
        lifecycleScope.launch { ajustes.guardarContacto(contacto.nombre, contacto.telefono) }
        binding.panicoTextoSms.setText(TextoAviso.armar(contacto.nombre))
        pintarBotonSms()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPanicoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        respetarBarrasDelSistema(binding.root)

        dibujarPasosEstafa()
        dibujarGuia()

        binding.panicoBtnSi.setOnClickListener {
            registrarPanico()
            mostrar(Pantalla.GUIA)
        }
        binding.panicoBtnTodaviaNo.setOnClickListener { mostrar(Pantalla.PASOS) }
        binding.panicoBtnEntendido.setOnClickListener { finish() }
        binding.panicoBtnAvisar.setOnClickListener { mostrar(Pantalla.SMS) }
        binding.panicoBtnAbrirMensajes.setOnClickListener { abrirMensajes() }
        binding.panicoBtnEditar.setOnClickListener { editarMensaje() }

        // Atrás desde el aviso vuelve a la guía; desde cualquier otra pantalla cierra.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pantalla == Pantalla.SMS) {
                    mostrar(Pantalla.GUIA)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        val enRotacion = savedInstanceState != null
        if (enRotacion) {
            pantalla = Pantalla.valueOf(savedInstanceState?.getString(CLAVE_PANTALLA) ?: Pantalla.PREGUNTA.name)
        } else if (intent.getBooleanExtra(EXTRA_IR_A_GUIA, false)) {
            registrarPanico()
            pantalla = Pantalla.GUIA
        }
        mostrar(pantalla)

        lifecycleScope.launch {
            contactoNombre = ajustes.contactoNombre.first()
            contactoTelefono = ajustes.contactoTelefono.first()
            // Al rotar, el EditText conserva lo que la persona ya escribió.
            if (!enRotacion) binding.panicoTextoSms.setText(TextoAviso.armar(contactoNombre))
            pintarBotonSms()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(CLAVE_PANTALLA, pantalla.name)
    }

    private fun mostrar(nueva: Pantalla) {
        pantalla = nueva
        binding.panicoGrupoPregunta.visibility = visibleSi(nueva == Pantalla.PREGUNTA)
        binding.panicoGrupoPasos.visibility = visibleSi(nueva == Pantalla.PASOS)
        binding.panicoGrupoGuia.visibility = visibleSi(nueva == Pantalla.GUIA)
        binding.panicoGrupoSms.visibility = visibleSi(nueva == Pantalla.SMS)
    }

    private fun visibleSi(condicion: Boolean) = if (condicion) View.VISIBLE else View.GONE

    /** Guarda el evento PANICO una sola vez por apertura de la guía. */
    private fun registrarPanico() {
        if (registrado) return
        registrado = true
        val contexto = applicationContext
        lifecycleScope.launch {
            val demo = ajustes.modoDemo.first()
            RegistroEventos.guardar(
                contexto,
                EventoPausa(
                    fecha = System.currentTimeMillis(),
                    appPackage = "",
                    numero = null,
                    respuestas = "",
                    decision = Decision.PANICO.name,
                    esDemo = demo
                )
            )
        }
    }

    /** Los pasos de "Es una estafa" del pop-up, desde preguntas.json. */
    private fun dibujarPasosEstafa() {
        val pasos = try {
            ContentRepository(applicationContext).pasosEstafa()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo leer pasos_estafa de preguntas.json", e)
            emptyList()
        }
        val margen = (18 * resources.displayMetrics.density).toInt()
        pasos.forEachIndexed { i, paso ->
            val item = OverlayItemPasoBinding.inflate(layoutInflater, binding.panicoListaPasos, false)
            item.itemPasoNumero.text = (i + 1).toString()
            item.itemPasoTexto.text = paso.trim().removeSuffix(".")
            val medidas = item.root.layoutParams as LinearLayout.LayoutParams
            if (i > 0) medidas.topMargin = margen
            binding.panicoListaPasos.addView(item.root, medidas)
        }
    }

    private fun dibujarGuia() {
        val pasos = listOf(
            Triple(R.string.panico_paso1_titulo, R.string.panico_paso1_texto, R.color.pausa_alerta),
            Triple(R.string.panico_paso2_titulo, R.string.panico_paso2_texto, R.color.pausa_aviso),
            Triple(R.string.panico_paso3_titulo, R.string.panico_paso3_texto, R.color.pausa_popup_oscuro),
            Triple(R.string.panico_paso4_titulo, R.string.panico_paso4_texto, R.color.pausa_popup_oscuro)
        )
        pasos.forEachIndexed { i, (titulo, texto, color) ->
            val item = ItemPanicoPasoBinding.inflate(layoutInflater, binding.panicoListaGuia, false)
            item.pasoNumero.text = (i + 1).toString()
            item.pasoNumero.setTextColor(ContextCompat.getColor(this, color))
            item.pasoNumero.backgroundTintList = ContextCompat.getColorStateList(this, fondoDe(color))
            item.pasoTitulo.setText(titulo)
            item.pasoTexto.setText(texto)
            if (i == 0) {
                item.pasoBtnLlamar.visibility = View.VISIBLE
                item.pasoBtnLlamar.setOnClickListener {
                    startActivity(Intent(this, NumerosOficialesActivity::class.java))
                }
            }
            binding.panicoListaGuia.addView(item.root)
        }
    }

    @ColorRes
    private fun fondoDe(@ColorRes color: Int): Int = when (color) {
        R.color.pausa_alerta -> R.color.pausa_alerta_fondo
        R.color.pausa_aviso -> R.color.pausa_aviso_fondo
        else -> R.color.pausa_popup_suave
    }

    private fun tieneContacto() = !contactoTelefono.isNullOrBlank()

    /** Sin contacto de confianza guardado, el botón principal pasa a "Elegir contacto". */
    private fun pintarBotonSms() {
        binding.panicoBtnAbrirMensajes.setText(
            if (tieneContacto()) R.string.panico_abrir_mensajes else R.string.panico_elegir_contacto
        )
    }

    private fun abrirMensajes() {
        val telefono = contactoTelefono?.filter { it.isDigit() || it == '+' }
        if (telefono.isNullOrEmpty()) {
            selectorContacto.abrir()
            return
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$telefono"))
            .putExtra("sms_body", binding.panicoTextoSms.text.toString())
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.panico_sin_mensajes, Toast.LENGTH_LONG).show()
        }
    }

    private fun editarMensaje() {
        val campo = binding.panicoTextoSms
        campo.requestFocus()
        campo.setSelection(campo.text.length)
        getSystemService(InputMethodManager::class.java)
            .showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT)
    }

    companion object {
        private const val TAG = "PanicoActivity"
        private const val CLAVE_PANTALLA = "pantalla"

        /** true = abrir directo en la guía (viene de "Ya transferí o di datos" del pop-up). */
        const val EXTRA_IR_A_GUIA = "ir_a_guia"

        /** Intent para abrir el pánico desde un servicio o desde el pop-up (sin actividad). */
        fun intent(context: Context, irALaGuia: Boolean): Intent =
            Intent(context, PanicoActivity::class.java)
                .putExtra(EXTRA_IR_A_GUIA, irALaGuia)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
