package co.edu.unisabana.pausa.ui.ajustes

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.edu.unisabana.pausa.BuildConfig
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.databinding.ActivityAjustesBinding
import co.edu.unisabana.pausa.databinding.ItemAppBinding
import co.edu.unisabana.pausa.log.EventoPausa
import co.edu.unisabana.pausa.log.RegistroEventos
import co.edu.unisabana.pausa.overlay.OverlayController
import co.edu.unisabana.pausa.overlay.Trigger
import co.edu.unisabana.pausa.service.GuardService
import co.edu.unisabana.pausa.settings.SettingsRepository
import co.edu.unisabana.pausa.ui.contacto.SelectorContacto
import co.edu.unisabana.pausa.ui.pintarEtiqueta
import co.edu.unisabana.pausa.ui.respetarBarrasDelSistema
import co.edu.unisabana.pausa.util.Permisos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configuración: apps sensibles, minutos de la ventana, contacto de confianza, bloqueo con
 * respiración y modo demo oculto (se activa o desactiva
 * tocando 7 veces la versión, al final de la pantalla, como el "Número de compilación" de
 * Android). Todo se guarda de inmediato en [SettingsRepository]. Responsable: Julian.
 */
class AjustesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAjustesBinding
    private val ajustes by lazy { SettingsRepository(applicationContext) }

    private var instaladas: List<AppInstalada> = emptyList()

    /** Puede incluir apps no instaladas (por defecto): se conservan por si las instalan después. */
    private var seleccionadas: Set<String> = emptySet()

    /** Las marcadas al abrir la pantalla: no desaparecen de la lista mientras se desmarcan. */
    private var destacadas: Set<String> = emptySet()
    private var verTodas = false

    private var minutos = SettingsRepository.MINUTOS_POR_DEFECTO
    private var modoDemo = false
    private var toquesVersion = 0
    private var avisoToques: Toast? = null

    // Se crea aquí (antes de STARTED) porque registerForActivityResult lo exige.
    private val selectorContacto = SelectorContacto(this) { contacto ->
        lifecycleScope.launch {
            ajustes.guardarContacto(contacto.nombre, contacto.telefono)
            pintarContacto(contacto.nombre)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAjustesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        respetarBarrasDelSistema(binding.root)

        binding.btnVerTodas.setOnClickListener {
            verTodas = !verTodas
            pintarApps()
        }
        binding.ajustesVersion.text = getString(R.string.ajustes_version, BuildConfig.VERSION_NAME)
        binding.ajustesVersion.setOnClickListener { contarToqueVersion() }
        binding.btnProbarPopup.setOnClickListener { probarPopup() }
        binding.btnSimularLlamada.setOnClickListener { simularLlamada() }
        binding.btnElegirContacto.setOnClickListener { selectorContacto.abrir() }

        lifecycleScope.launch {
            seleccionadas = ajustes.appsSensibles.first()
            destacadas = seleccionadas
            minutos = ajustes.minutosVentana.first()
            modoDemo = ajustes.modoDemo.first()
            pintarVentana()
            pintarDemo()
            pintarContacto(ajustes.contactoNombre.first())
            pintarBloqueo(ajustes.bloqueoRespiracion.first())

            instaladas = withContext(Dispatchers.Default) { appsInstaladas(applicationContext) }
            pintarApps()
        }
    }

    private fun pintarApps() {
        val marcadas = instaladas.filter { it.paquete in destacadas }
        val visibles = if (verTodas) marcadas + (instaladas - marcadas.toSet()) else marcadas

        binding.listaApps.removeAllViews()
        visibles.forEach { app ->
            val fila = ItemAppBinding.inflate(layoutInflater, binding.listaApps, true)
            fila.appNombre.text = app.nombre
            fila.appInterruptor.isChecked = app.paquete in seleccionadas
            fila.root.setOnClickListener {
                val marcar = !fila.appInterruptor.isChecked
                fila.appInterruptor.isChecked = marcar
                cambiarApp(app.paquete, marcar)
            }
        }
        binding.listaApps.visibility = if (visibles.isEmpty()) View.GONE else View.VISIBLE
        binding.textoSinApps.visibility = if (visibles.isEmpty()) View.VISIBLE else View.GONE
        binding.btnVerTodas.setText(if (verTodas) R.string.ajustes_ver_menos else R.string.ajustes_ver_todas)
    }

    private fun cambiarApp(paquete: String, marcar: Boolean) {
        seleccionadas = if (marcar) seleccionadas + paquete else seleccionadas - paquete
        val aGuardar = seleccionadas
        lifecycleScope.launch { ajustes.guardarAppsSensibles(aGuardar) }
    }

    private fun pintarVentana() {
        val densidad = resources.displayMetrics.density
        binding.opcionesVentana.removeAllViews()
        OPCIONES_MINUTOS.forEachIndexed { i, opcion ->
            val elegida = opcion == minutos
            val vista = TextView(this, null, 0, R.style.PausaEtiqueta).apply {
                text = getString(R.string.ajustes_ventana_opcion, opcion)
                minHeight = (48 * densidad).toInt()
                isSelected = elegida
                if (elegida) {
                    pintarEtiqueta(R.color.pausa_exito_fondo, R.color.pausa_exito)
                } else {
                    pintarEtiqueta(R.color.pausa_aviso_fondo, R.color.pausa_aviso)
                }
                setOnClickListener {
                    minutos = opcion
                    pintarVentana()
                    lifecycleScope.launch { ajustes.guardarMinutosVentana(opcion) }
                }
            }
            val medidas = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = (8 * densidad).toInt()
            }
            binding.opcionesVentana.addView(vista, medidas)
        }
    }

    private fun contarToqueVersion() {
        toquesVersion++
        val faltan = TOQUES_MODO_DEMO - toquesVersion
        if (faltan > 0) {
            // Igual que Android: avisa solo en los últimos toques.
            if (faltan <= TOQUES_AVISO) {
                avisar(resources.getQuantityString(R.plurals.demo_faltan_toques, faltan, faltan))
            }
            return
        }
        toquesVersion = 0
        modoDemo = !modoDemo
        pintarDemo()
        val activo = modoDemo
        lifecycleScope.launch { ajustes.guardarModoDemo(activo) }
        avisar(getString(if (activo) R.string.demo_activado else R.string.demo_desactivado))
    }

    /** Reemplaza el aviso anterior para que los toques seguidos no apilen mensajes. */
    private fun avisar(texto: String) {
        avisoToques?.cancel()
        avisoToques = Toast.makeText(this, texto, Toast.LENGTH_SHORT).also { it.show() }
    }

    private fun pintarContacto(nombre: String?) {
        binding.textoContacto.text = nombre?.takeIf { it.isNotBlank() }
            ?: getString(R.string.ajustes_contacto_sin_elegir)
        binding.btnElegirContacto.setText(
            if (nombre.isNullOrBlank()) R.string.ajustes_contacto_elegir else R.string.ajustes_contacto_cambiar
        )
    }

    private fun pintarBloqueo(activo: Boolean) {
        // Sin quitar el listener, pintar el valor guardado se tomaría como un toque de la persona.
        binding.interruptorBloqueo.setOnCheckedChangeListener(null)
        binding.interruptorBloqueo.isChecked = activo
        binding.interruptorBloqueo.setOnCheckedChangeListener { _, encendido ->
            lifecycleScope.launch { ajustes.guardarBloqueoRespiracion(encendido) }
        }
    }

    private fun pintarDemo() {
        binding.grupoDemo.visibility = if (modoDemo) View.VISIBLE else View.GONE
    }

    /** Pop-up sin llamada ni app sensible. Queda en el historial marcado como demo. */
    private fun probarPopup() {
        if (!Permisos.puedeMostrarEncima(this)) {
            Toast.makeText(this, R.string.demo_faltan_permisos, Toast.LENGTH_LONG).show()
            return
        }
        val trigger = Trigger(
            appPackage = packageName,
            callNumber = null,
            callEndedAt = System.currentTimeMillis(),
            isDemo = true
        )
        val contexto = applicationContext
        OverlayController.show(this, trigger) { resultado ->
            RegistroEventos.guardar(
                contexto,
                EventoPausa(
                    fecha = System.currentTimeMillis(),
                    appPackage = trigger.appPackage,
                    numero = trigger.callNumber,
                    respuestas = resultado.respuestas,
                    decision = resultado.decision.name,
                    esDemo = true
                )
            )
        }
    }

    /** Abre la ventana de riesgo sin llamada real: al abrir una app sensible debe salir el pop-up. */
    private fun simularLlamada() {
        if (!Permisos.tieneLosIndispensables(this)) {
            Toast.makeText(this, R.string.demo_faltan_permisos, Toast.LENGTH_LONG).show()
            return
        }
        GuardService.simularLlamadaTerminada(this)
        Toast.makeText(this, R.string.demo_simulada, Toast.LENGTH_LONG).show()
    }

    private companion object {
        val OPCIONES_MINUTOS = listOf(5, 10, 15, 25)
        const val TOQUES_MODO_DEMO = 7
        const val TOQUES_AVISO = 3
    }
}
