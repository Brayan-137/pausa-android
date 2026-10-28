package co.edu.unisabana.pausa.ui

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.databinding.ActivityMainBinding
import co.edu.unisabana.pausa.overlay.OverlayController
import co.edu.unisabana.pausa.overlay.Trigger
import co.edu.unisabana.pausa.service.GuardService
import co.edu.unisabana.pausa.util.Permisos

/**
 * Pantalla TEMPORAL de desarrollo: muestra el estado de los permisos y permite probar
 * el servicio y el pop-up. Julian la reemplaza por la bienvenida (ui/onboarding).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val pedirPermisos =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            actualizarEstado()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPermisos.setOnClickListener {
            pedirPermisos.launch(Permisos.EN_TIEMPO_DE_EJECUCION)
        }
        binding.btnOverlay.setOnClickListener {
            startActivity(Permisos.intentMostrarEncima(this))
        }
        binding.btnUso.setOnClickListener {
            startActivity(Permisos.intentAccesoAUso())
        }
        binding.btnIniciar.setOnClickListener {
            GuardService.iniciar(this)
        }
        binding.btnSimular.setOnClickListener {
            // Abre la ventana de riesgo sin llamada real: ahora abre Nequi y debe salir el pop-up.
            GuardService.simularLlamadaTerminada(this)
        }
        binding.btnProbar.setOnClickListener {
            OverlayController.show(
                this,
                Trigger(
                    appPackage = packageName,
                    callNumber = null,
                    callEndedAt = System.currentTimeMillis(),
                    isDemo = true
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarEstado() // al volver de Ajustes
    }

    private fun actualizarEstado() {
        val ok = getString(R.string.permiso_ok)
        val falta = getString(R.string.permiso_falta)
        fun marca(concedido: Boolean) = if (concedido) ok else falta

        binding.textEstado.text = buildString {
            appendLine("${marca(Permisos.tieneTodosLosDeEjecucion(this@MainActivity))} Llamadas, contactos y notificaciones")
            appendLine("${marca(Permisos.puedeMostrarEncima(this@MainActivity))} Mostrar sobre otras apps")
            append("${marca(Permisos.tieneAccesoAUso(this@MainActivity))} Acceso a uso")
        }
    }
}
