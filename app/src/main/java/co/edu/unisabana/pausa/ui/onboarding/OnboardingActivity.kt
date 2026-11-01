package co.edu.unisabana.pausa.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.databinding.ActivityOnboardingBinding
import co.edu.unisabana.pausa.databinding.ItemPermisoBinding
import co.edu.unisabana.pausa.settings.SettingsRepository
import co.edu.unisabana.pausa.ui.MainActivity
import co.edu.unisabana.pausa.ui.abrirAjustes
import co.edu.unisabana.pausa.ui.respetarBarrasDelSistema
import co.edu.unisabana.pausa.util.Permisos
import kotlinx.coroutines.launch

/**
 * Bienvenida y permisos, para que la persona instale y active Pausa sin ayuda. Responsable: Julian.
 *
 * Inicio también la abre con [intentSoloPermisos] cuando falta algún permiso.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val ajustes by lazy { SettingsRepository(applicationContext) }

    private var enPermisos = false
    private var soloPermisos = false

    /** Ya tocó "Acceso a uso" o "Mostrar sobre otras apps": si no quedó activo, puede estar bloqueado. */
    private var intentoEnAjustes = false

    /** Grupos ya pedidos: Android deja de mostrar el diálogo tras negarlo varias veces. */
    private val gruposPedidos = ArrayList<String>()

    private val pedirPermisos =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            pintarPermisos()
        }

    private val volverABienvenida = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = mostrarPaso(permisos = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        respetarBarrasDelSistema(binding.root)
        onBackPressedDispatcher.addCallback(this, volverABienvenida)

        soloPermisos = intent.getBooleanExtra(EXTRA_SOLO_PERMISOS, false)
        savedInstanceState?.let {
            intentoEnAjustes = it.getBoolean(ESTADO_INTENTO)
            it.getStringArrayList(ESTADO_GRUPOS)?.let(gruposPedidos::addAll)
        }

        configurarFila(binding.filaTelefono, R.string.permiso_telefono_titulo, R.string.permiso_telefono_texto) {
            pedirEnDialogo(Permisos.LLAMADAS_Y_CONTACTOS)
        }
        configurarFila(binding.filaUso, R.string.permiso_uso_titulo, R.string.permiso_uso_texto) {
            intentoEnAjustes = true
            abrirAjustes(Permisos.intentAccesoAUso())
        }
        configurarFila(binding.filaEncima, R.string.permiso_encima_titulo, R.string.permiso_encima_texto) {
            intentoEnAjustes = true
            abrirAjustes(Permisos.intentMostrarEncima(this))
        }
        configurarFila(
            binding.filaNotificaciones,
            R.string.permiso_notificaciones_titulo,
            R.string.permiso_notificaciones_texto
        ) {
            pedirEnDialogo(Permisos.NOTIFICACIONES)
        }
        configurarFila(binding.filaBateria, R.string.permiso_bateria_titulo, R.string.permiso_bateria_texto) {
            abrirAjustes(Permisos.intentQuitarOptimizacionDeBateria(this))
        }

        binding.btnBienvenidaContinuar.setOnClickListener { mostrarPaso(permisos = true) }
        binding.btnInfoApp.setOnClickListener { abrirAjustes(Permisos.intentInfoDeLaApp(this)) }
        binding.btnPermisosContinuar.setOnClickListener { terminar() }

        mostrarPaso(permisos = soloPermisos || savedInstanceState?.getBoolean(ESTADO_EN_PERMISOS) == true)
    }

    override fun onResume() {
        super.onResume()
        pintarPermisos() // al volver de Ajustes
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ESTADO_EN_PERMISOS, enPermisos)
        outState.putBoolean(ESTADO_INTENTO, intentoEnAjustes)
        outState.putStringArrayList(ESTADO_GRUPOS, gruposPedidos)
    }

    private fun mostrarPaso(permisos: Boolean) {
        enPermisos = permisos
        binding.grupoBienvenida.visibility = if (permisos) View.GONE else View.VISIBLE
        binding.grupoPermisos.visibility = if (permisos) View.VISIBLE else View.GONE
        volverABienvenida.isEnabled = permisos && !soloPermisos
    }

    private fun configurarFila(
        fila: ItemPermisoBinding,
        @StringRes titulo: Int,
        @StringRes texto: Int,
        alTocar: () -> Unit
    ) {
        fila.permisoTitulo.setText(titulo)
        fila.permisoTexto.setText(texto)
        // Quitar un permiso ya concedido se hace desde Ajustes: tocarlo aquí no hace nada.
        fila.root.setOnClickListener { if (!fila.permisoInterruptor.isChecked) alTocar() }
    }

    /**
     * Muestra el diálogo de Android. Si ya se pidió y Android dejó de mostrarlo porque la persona
     * lo negó, el único camino que queda es la información de la app.
     */
    private fun pedirEnDialogo(permisos: Array<String>) {
        val grupo = permisos.firstOrNull() ?: return
        val bloqueado = grupo in gruposPedidos &&
            permisos.none { shouldShowRequestPermissionRationale(it) }
        if (bloqueado) {
            abrirAjustes(Permisos.intentInfoDeLaApp(this))
        } else {
            if (grupo !in gruposPedidos) gruposPedidos.add(grupo)
            pedirPermisos.launch(permisos)
        }
    }

    private fun pintarPermisos() {
        val telefono = Permisos.tieneLlamadasYContactos(this)
        val uso = Permisos.tieneAccesoAUso(this)
        val encima = Permisos.puedeMostrarEncima(this)
        val notificaciones = Permisos.tieneNotificaciones(this)
        val bateria = Permisos.ignoraOptimizacionDeBateria(this)

        binding.filaTelefono.permisoInterruptor.isChecked = telefono
        binding.filaUso.permisoInterruptor.isChecked = uso
        binding.filaEncima.permisoInterruptor.isChecked = encima
        binding.filaNotificaciones.permisoInterruptor.isChecked = notificaciones
        binding.filaBateria.permisoInterruptor.isChecked = bateria

        val estados = listOf(telefono, uso, encima, notificaciones, bateria)
        binding.textoContador.text =
            getString(R.string.permisos_contador, estados.count { it }, estados.size)

        binding.tarjetaRestringida.visibility =
            if (intentoEnAjustes && !(uso && encima)) View.VISIBLE else View.GONE

        val indispensables = telefono && uso && encima
        binding.btnPermisosContinuar.isEnabled = indispensables
        binding.textoFaltan.visibility = if (indispensables) View.GONE else View.VISIBLE
    }

    private fun terminar() {
        lifecycleScope.launch {
            ajustes.guardarBienvenidaCompleta()
            startActivity(
                Intent(this@OnboardingActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
    }

    companion object {
        private const val EXTRA_SOLO_PERMISOS = "solo_permisos"
        private const val ESTADO_EN_PERMISOS = "en_permisos"
        private const val ESTADO_INTENTO = "intento_en_ajustes"
        private const val ESTADO_GRUPOS = "grupos_pedidos"

        fun intentSoloPermisos(context: Context): Intent =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_SOLO_PERMISOS, true)
    }
}
