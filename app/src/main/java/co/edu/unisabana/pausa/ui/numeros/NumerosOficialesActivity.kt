package co.edu.unisabana.pausa.ui.numeros

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import co.edu.unisabana.pausa.R
import co.edu.unisabana.pausa.content.ContentRepository
import co.edu.unisabana.pausa.content.EntidadOficial
import co.edu.unisabana.pausa.databinding.ActivityNumerosOficialesBinding
import co.edu.unisabana.pausa.databinding.ItemEntidadBinding
import co.edu.unisabana.pausa.ui.abrirMarcador
import co.edu.unisabana.pausa.ui.abrirPaginaWeb
import co.edu.unisabana.pausa.ui.respetarBarrasDelSistema

/**
 * Números oficiales dentro de la app: la misma lista del pop-up (numeros_oficiales.json), con
 * "Llamar" (abre el marcador, la persona pulsa llamar) y "Abrir página oficial".
 */
class NumerosOficialesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNumerosOficialesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNumerosOficialesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        respetarBarrasDelSistema(binding.root)

        val entidades = try {
            ContentRepository(applicationContext).entidadesOficiales()
        } catch (e: Exception) {
            Log.e("NumerosOficiales", "No se pudo leer assets/content/numeros_oficiales.json", e)
            emptyList()
        }
        pintar(entidades)
    }

    private fun pintar(entidades: List<EntidadOficial>) {
        binding.textoSinNumeros.visibility = if (entidades.isEmpty()) View.VISIBLE else View.GONE
        entidades.forEach { entidad ->
            val fila = ItemEntidadBinding.inflate(layoutInflater, binding.listaEntidades, true)
            fila.entidadNombre.text = entidad.nombre
            fila.entidadTelefono.text = entidad.telefono
            fila.entidadFuente.text = getString(R.string.overlay_fuente, entidad.fuente)
            fila.entidadBtnLlamar.setOnClickListener { abrirMarcador(entidad.telefono) }
            fila.entidadBtnPagina.setOnClickListener { abrirPaginaWeb(entidad.fuente) }
        }
    }
}
