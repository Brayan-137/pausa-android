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

data class EntidadOficial(val nombre: String, val telefono: String, val fuente: String)
