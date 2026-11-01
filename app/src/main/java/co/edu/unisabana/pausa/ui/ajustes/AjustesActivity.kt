package co.edu.unisabana.pausa.ui.ajustes

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.databinding.ActivityAjustesBinding
import co.edu.unisabana.pausa.databinding.ItemAppBinding
import co.edu.unisabana.pausa.log.EventoPausa
import co.edu.unisabana.pausa.log.RegistroEventos
import co.edu.unisabana.pausa.overlay.OverlayController
import co.edu.unisabana.pausa.overlay.Trigger
import co.edu.unisabana.pausa.service.GuardService
import co.edu.unisabana.pausa.settings.SettingsRepository
import co.edu.unisabana.pausa.ui.pintarEtiqueta
import co.edu.unisabana.pausa.ui.respetarBarrasDelSistema
import co.edu.unisabana.pausa.util.Permisos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configuración: apps sensibles, minutos de la ventana y modo demo oculto (se activa tocando el
 * título 5 veces). Todo se guarda de inmediato en [SettingsRepository]. Responsable: Julian.
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
    private var toquesTitulo = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAjustesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        respetarBarrasDelSistema(binding.root)

        binding.btnVerTodas.setOnClickListener {
            verTodas = !verTodas
            pintarApps()
        }
        binding.ajustesTitulo.setOnClickListener { contarToqueTitulo() }
        binding.btnProbarPopup.setOnClickListener { probarPopup() }
        binding.btnSimularLlamada.setOnClickListener { simularLlamada() }

        lifecycleScope.launch {
            seleccionadas = ajustes.appsSensibles.first()
            destacadas = seleccionadas
            minutos = ajustes.minutosVentana.first()
            modoDemo = ajustes.modoDemo.first()
            pintarVentana()
            pintarDemo()

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

    private fun contarToqueTitulo() {
        toquesTitulo++
        if (toquesTitulo < TOQUES_MODO_DEMO) return
        toquesTitulo = 0
        modoDemo = !modoDemo
        pintarDemo()
        val activo = modoDemo
        lifecycleScope.launch { ajustes.guardarModoDemo(activo) }
        Toast.makeText(
            this,
            if (activo) R.string.demo_activado else R.string.demo_desactivado,
            Toast.LENGTH_SHORT
        ).show()
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
        const val TOQUES_MODO_DEMO = 5
    }
}
