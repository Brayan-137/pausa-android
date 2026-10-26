package co.edu.unisabana.pausa.content

import android.content.Context
import org.json.JSONObject

/**
 * Lee los textos y números oficiales desde app/src/main/assets/content.
 * Los JSON los mantiene Camilo; este lector lo mantiene Brayan.
 */
class ContentRepository(private val context: Context) {

    fun preguntas(): List<Pregunta> {
        val json = JSONObject(leer("content/preguntas.json"))
        val lista = json.getJSONArray("preguntas")
        return List(lista.length()) { i ->
            val p = lista.getJSONObject(i)
            Pregunta(id = p.getString("id"), texto = p.getString("texto"))
        }
    }

    fun subpreguntaQuien(): SubpreguntaQuien {
        val sub = JSONObject(leer("content/preguntas.json")).getJSONObject("subpregunta_quien")
        val opciones = sub.getJSONArray("opciones")
        return SubpreguntaQuien(
            texto = sub.getString("texto"),
            opciones = List(opciones.length()) { i ->
                val o = opciones.getJSONObject(i)
                OpcionQuien(id = o.getString("id"), texto = o.getString("texto"))
            }
        )
    }

    fun pasosEstafa(): List<String> {
        val pasos = JSONObject(leer("content/preguntas.json")).getJSONArray("pasos_estafa")
        return List(pasos.length()) { pasos.getString(it) }
    }

    fun consejo(clave: String): String =
        JSONObject(leer("content/preguntas.json")).getJSONObject("consejos").optString(clave, "")

    /** Solo devuelve entidades con teléfono Y fuente: nunca un número sin verificar. */
    fun entidadesOficiales(): List<EntidadOficial> {
        val lista = JSONObject(leer("content/numeros_oficiales.json")).getJSONArray("entidades")
        return List(lista.length()) { i ->
            val e = lista.getJSONObject(i)
            EntidadOficial(
                nombre = e.getString("nombre"),
                telefono = e.optString("telefono"),
                fuente = e.optString("fuente")
            )
        }.filter { it.telefono.isNotBlank() && it.fuente.isNotBlank() }
    }

    private fun leer(ruta: String): String =
        context.assets.open(ruta).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

data class Pregunta(val id: String, val texto: String)

data class OpcionQuien(val id: String, val texto: String)

data class SubpreguntaQuien(val texto: String, val opciones: List<OpcionQuien>)

data class EntidadOficial(val nombre: String, val telefono: String, val fuente: String)
