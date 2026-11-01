package co.edu.unisabana.pausa.ui.historial

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.databinding.ActivityHistorialBinding
import co.edu.unisabana.pausa.databinding.ItemEventoBinding
import co.edu.unisabana.pausa.log.EventoPausa
import co.edu.unisabana.pausa.log.PausaDatabase
import co.edu.unisabana.pausa.log.ResumenEventos
import co.edu.unisabana.pausa.log.tuvoAlerta
import co.edu.unisabana.pausa.overlay.Decision
import co.edu.unisabana.pausa.ui.ajustes.nombreDeApp
import co.edu.unisabana.pausa.ui.pintarEtiqueta
import co.edu.unisabana.pausa.ui.respetarBarrasDelSistema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lista de eventos y métricas para la demo y la prueba con usuarios. Responsable: Julian. */
class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding

    private var eventos: List<EventoPausa> = emptyList()
    private var nombresDeApps: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        respetarBarrasDelSistema(binding.root)

        binding.metricaTotal.metricaNombre.setText(R.string.metrica_total)
        binding.metricaVerifico.metricaNombre.setText(R.string.metrica_verifico)
        binding.metricaEstafa.metricaNombre.setText(R.string.metrica_estafa)
        binding.metricaContinuo.metricaNombre.setText(R.string.metrica_continuo)
        binding.interruptorDemo.setOnCheckedChangeListener { _, _ -> pintar() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PausaDatabase.get(applicationContext).eventos().observarTodos().collect { nuevos ->
                    nombresDeApps = withContext(Dispatchers.Default) {
                        nuevos.map { it.appPackage }.distinct()
                            .associateWith { nombreDeApp(applicationContext, it) }
                    }
                    eventos = nuevos
                    pintar()
                }
            }
        }
    }

    private fun pintar() {
        binding.interruptorDemo.visibility =
            if (eventos.any { it.esDemo }) View.VISIBLE else View.GONE
        val visibles =
            if (binding.interruptorDemo.isChecked) eventos else eventos.filterNot { it.esDemo }

        binding.textoVacio.visibility = if (visibles.isEmpty()) View.VISIBLE else View.GONE
        binding.tarjetaMetricas.visibility = if (visibles.isEmpty()) View.GONE else View.VISIBLE
        pintarMetricas(ResumenEventos.de(visibles))

        binding.listaEventos.removeAllViews()
        visibles.forEach { pintarEvento(it) }
    }

    private fun pintarMetricas(resumen: ResumenEventos) {
        binding.metricaTotal.metricaValor.text = resumen.total.toString()
        binding.metricaVerifico.metricaValor.text = resumen.verificaron.toString()
        binding.metricaEstafa.metricaValor.text = resumen.estafas.toString()
        binding.metricaContinuo.metricaValor.text = resumen.continuaron.toString()

        val porcentaje = resumen.porcentajeSeDetuvieron
        binding.textoSeDetuvieron.visibility = if (porcentaje == null) View.GONE else View.VISIBLE
        if (porcentaje != null) {
            binding.textoSeDetuvieron.text = getString(
                R.string.metrica_se_detuvieron,
                resumen.conAlerta,
                resumen.seDetuvieron,
                porcentaje
            )
        }
    }

    private fun pintarEvento(evento: EventoPausa) {
        val fila = ItemEventoBinding.inflate(layoutInflater, binding.listaEventos, true)
        fila.eventoFecha.text = formatoFecha(this, evento.fecha)
        fila.eventoApp.text = getString(
            R.string.evento_en_app,
            nombresDeApps[evento.appPackage] ?: evento.appPackage
        )

        when (evento.decision) {
            Decision.VERIFICAR.name -> {
                fila.eventoDecision.setText(R.string.evento_verifico)
                fila.eventoEtiqueta.setText(R.string.etiqueta_verificado)
                fila.eventoEtiqueta.pintarEtiqueta(R.color.pausa_exito_fondo, R.color.pausa_exito)
            }
            Decision.ESTAFA.name -> {
                fila.eventoDecision.setText(R.string.evento_estafa)
                fila.eventoEtiqueta.setText(R.string.etiqueta_estafa)
                fila.eventoEtiqueta.pintarEtiqueta(R.color.pausa_aviso_fondo, R.color.pausa_aviso)
            }
            Decision.PANICO.name -> {
                fila.eventoDecision.setText(R.string.evento_panico)
                fila.eventoEtiqueta.setText(R.string.etiqueta_panico)
                fila.eventoEtiqueta.pintarEtiqueta(R.color.pausa_alerta_fondo, R.color.pausa_alerta)
            }
            else -> {
                fila.eventoDecision.setText(
                    if (evento.tuvoAlerta()) R.string.evento_continuo else R.string.evento_normal
                )
                fila.eventoEtiqueta.setText(R.string.etiqueta_continuo)
                fila.eventoEtiqueta.pintarEtiqueta(R.color.pausa_aviso_fondo, R.color.pausa_aviso)
            }
        }

        if (evento.esDemo) {
            fila.eventoDemo.visibility = View.VISIBLE
            fila.eventoDemo.pintarEtiqueta(R.color.pausa_primario_suave, R.color.pausa_primario_oscuro)
        }
    }
}
