package co.edu.unisabana.pausa.ui.novedades

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.databinding.ActivityNovedadesBinding
import co.edu.unisabana.pausa.settings.SettingsRepository
import co.edu.unisabana.pausa.ui.respetarBarrasDelSistema
import kotlinx.coroutines.launch

/**
 * Novedades al instalar: 3 pantallas en un solo layout que cambia de contenido. "Saltar", "Empezar"
 * y el botón atrás guardan novedadesVistas = true y cierran. MainActivity la abre antes de la
 * bienvenida la primera vez; Configuración > "Ver novedades" la abre cuando se quiera.
 */
class NovedadesActivity : AppCompatActivity() {

    private class Pantalla(
        @DrawableRes val icono: Int,
        @StringRes val titulo: Int,
        @StringRes val texto: Int
    )

    private val pantallas = listOf(
        Pantalla(R.drawable.ic_pausa_telefono_colgado, R.string.novedades_1_titulo, R.string.novedades_1_texto),
        Pantalla(R.drawable.ic_pausa_check, R.string.novedades_2_titulo, R.string.novedades_2_texto),
        Pantalla(R.drawable.ic_pausa_telefono, R.string.novedades_3_titulo, R.string.novedades_3_texto)
    )

    private lateinit var binding: ActivityNovedadesBinding
    private var actual = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovedadesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        respetarBarrasDelSistema(binding.root)

        actual = savedInstanceState?.getInt(CLAVE_ACTUAL) ?: 0
        crearPuntos()
        binding.btnSaltar.setOnClickListener { terminar() }
        binding.btnSiguiente.setOnClickListener {
            if (actual < pantallas.lastIndex) mostrar(actual + 1) else terminar()
        }
        // Atrás se toma como "Saltar": si no, Inicio volvería a abrir las novedades.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = terminar()
        })
        mostrar(actual)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(CLAVE_ACTUAL, actual)
    }

    private fun mostrar(indice: Int) {
        actual = indice
        val pantalla = pantallas[indice]
        binding.novedadesIcono.setImageResource(pantalla.icono)
        binding.novedadesTitulo.setText(pantalla.titulo)
        binding.novedadesTexto.setText(pantalla.texto)
        val ultima = indice == pantallas.lastIndex
        binding.btnSiguiente.setText(if (ultima) R.string.novedades_empezar else R.string.novedades_siguiente)
        binding.btnSaltar.visibility = if (ultima) View.INVISIBLE else View.VISIBLE
        pintarPuntos()
    }

    private fun crearPuntos() {
        val densidad = resources.displayMetrics.density
        val lado = (10 * densidad).toInt()
        val margen = (5 * densidad).toInt()
        repeat(pantallas.size) {
            val punto = View(this).apply { setBackgroundResource(R.drawable.bg_pausa_circulo) }
            binding.novedadesPuntos.addView(punto, LinearLayout.LayoutParams(lado, lado).apply {
                setMargins(margen, 0, margen, 0)
            })
        }
    }

    private fun pintarPuntos() {
        for (i in 0 until binding.novedadesPuntos.childCount) {
            val color = if (i == actual) R.color.pausa_primario else R.color.pausa_borde
            binding.novedadesPuntos.getChildAt(i).backgroundTintList =
                ContextCompat.getColorStateList(this, color)
        }
    }

    private fun terminar() {
        val contexto = applicationContext
        lifecycleScope.launch {
            SettingsRepository(contexto).guardarNovedadesVistas(true)
            finish()
        }
    }

    private companion object {
        const val CLAVE_ACTUAL = "actual"
    }
}
