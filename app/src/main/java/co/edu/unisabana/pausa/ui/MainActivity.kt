package co.edu.unisabana.pausa.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.databinding.ActivityMainBinding
import co.edu.unisabana.pausa.log.EventoPausa
import co.edu.unisabana.pausa.log.PausaDatabase
import co.edu.unisabana.pausa.log.tuvoAlerta
import co.edu.unisabana.pausa.overlay.Decision
import co.edu.unisabana.pausa.service.GuardService
import co.edu.unisabana.pausa.settings.SettingsRepository
import co.edu.unisabana.pausa.ui.ajustes.AjustesActivity
import co.edu.unisabana.pausa.ui.historial.HistorialActivity
import co.edu.unisabana.pausa.ui.historial.formatoFecha
import co.edu.unisabana.pausa.ui.novedades.NovedadesActivity
import co.edu.unisabana.pausa.ui.numeros.NumerosOficialesActivity
import co.edu.unisabana.pausa.ui.onboarding.OnboardingActivity
import co.edu.unisabana.pausa.ui.panico.PanicoActivity
import co.edu.unisabana.pausa.util.Permisos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Inicio: estado de la protección y última alerta. Es la pantalla de arranque; la primera vez
 * lleva a la bienvenida. Mientras la protección esté activa y estén los permisos, arranca el
 * servicio. Responsable: Julian.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ajustes by lazy { SettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        respetarBarrasDelSistema(binding.root)

        binding.btnPanico.setOnClickListener {
            startActivity(PanicoActivity.intent(this, irALaGuia = false))
        }
        binding.btnHistorial.setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }
        binding.btnNumerosOficiales.setOnClickListener {
            startActivity(Intent(this, NumerosOficialesActivity::class.java))
        }
        binding.btnConfiguracion.setOnClickListener {
            startActivity(Intent(this, AjustesActivity::class.java))
        }
        binding.tarjetaPermisos.setOnClickListener { abrirPermisos() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PausaDatabase.get(applicationContext).eventos().observarUltimo().collect(::pintarUltima)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { refrescar() } // también al volver de Ajustes o de los permisos
    }

    private suspend fun refrescar() {
        // Orden en la primera instalación: Novedades -> Bienvenida/permisos -> Inicio. Al cerrar las
        // novedades (guardan novedadesVistas) Inicio vuelve a onResume y sigue con la bienvenida.
        if (!ajustes.novedadesVistas.first()) {
            startActivity(Intent(this, NovedadesActivity::class.java))
            return
        }
        if (!ajustes.bienvenidaCompleta.first()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        binding.root.visibility = View.VISIBLE

        val indispensables = Permisos.tieneLosIndispensables(this)
        val activa = ajustes.proteccionActiva.first() && indispensables
        if (activa) GuardService.iniciar(this)

        pintarEstado(activa)
        pintarTarjetaPermisos(indispensables)
    }

    private fun pintarEstado(activa: Boolean) {
        binding.textoEstado.setText(if (activa) R.string.inicio_activa else R.string.inicio_apagada)
        binding.textoEstadoDetalle.setText(
            if (activa) R.string.inicio_activa_texto else R.string.inicio_apagada_texto
        )
        // Sin quitar el listener, pintar el estado se tomaría como un toque de la persona.
        binding.interruptorProteccion.setOnCheckedChangeListener(null)
        binding.interruptorProteccion.isChecked = activa
        binding.interruptorProteccion.setOnCheckedChangeListener { _, encendida ->
            cambiarProteccion(encendida)
        }
    }

    private fun cambiarProteccion(encendida: Boolean) {
        lifecycleScope.launch {
            ajustes.guardarProteccionActiva(encendida)
            when {
                !encendida -> stopService(Intent(this@MainActivity, GuardService::class.java))
                !Permisos.tieneLosIndispensables(this@MainActivity) -> {
                    abrirPermisos()
                    return@launch
                }
            }
            refrescar()
        }
    }

    private fun pintarTarjetaPermisos(indispensables: Boolean) {
        val recomendados =
            Permisos.tieneNotificaciones(this) && Permisos.ignoraOptimizacionDeBateria(this)
        binding.tarjetaPermisos.visibility =
            if (indispensables && recomendados) View.GONE else View.VISIBLE
        binding.tarjetaPermisosTitulo.setText(
            if (indispensables) R.string.inicio_recomendados else R.string.inicio_faltan_permisos
        )
        binding.tarjetaPermisosTexto.setText(
            if (indispensables) R.string.inicio_recomendados_texto else R.string.inicio_faltan_permisos_texto
        )
    }

    private fun pintarUltima(evento: EventoPausa?) {
        if (evento == null) {
            binding.textoUltimaFecha.visibility = View.GONE
            binding.textoUltimaDecision.setText(R.string.inicio_sin_alertas)
            return
        }
        binding.textoUltimaFecha.visibility = View.VISIBLE
        binding.textoUltimaFecha.text = formatoFecha(this, evento.fecha)
        binding.textoUltimaDecision.setText(
            when {
                evento.decision == Decision.VERIFICAR.name -> R.string.inicio_verifico
                evento.decision == Decision.ESTAFA.name -> R.string.inicio_estafa
                evento.decision == Decision.PANICO.name -> R.string.inicio_panico_evento
                evento.tuvoAlerta() -> R.string.inicio_continuo
                else -> R.string.inicio_normal
            }
        )
    }

    private fun abrirPermisos() {
        startActivity(OnboardingActivity.intentSoloPermisos(this))
    }
}
