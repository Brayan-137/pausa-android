package co.edu.unisabana.pausa.overlay

import co.edu.unisabana.pausa.content.OpcionQuien
import co.edu.unisabana.pausa.content.Pregunta
import co.edu.unisabana.pausa.content.SubpreguntaQuien

/**
 * Lógica de las 3 preguntas del pop-up, sin nada de Android para poder probarla con JUnit.
 * Responsable: Brayan.
 *
 * Orden: cada pregunta en el orden de preguntas.json; si se responde Sí a "dijeron_ser",
 * se agrega la subpregunta "¿Quién dijo ser?"; al final viene el [Paso.Resultado].
 *
 * Regla del consejo:
 *  - todo No                  -> "todo_normal"
 *  - algún Sí y quién=familiar -> "familiar"
 *  - algún Sí y quién=entidad  -> "entidad"
 *  - cualquier otro caso con Sí -> "general"
 */
class FlujoPreguntas(
    private val preguntas: List<Pregunta>,
    private val subpregunta: SubpreguntaQuien
) {

    sealed interface Paso {
        /** [numero] empieza en 1. */
        data class Pregunta(val numero: Int, val total: Int, val texto: String) : Paso

        data class Quien(val texto: String, val opciones: List<OpcionQuien>) : Paso

        /**
         * [claveConsejo] es una clave de "consejos" en preguntas.json.
         * [respuestas] va en el formato del registro de eventos, p. ej. "SI,NO,SI".
         */
        data class Resultado(
            val claveConsejo: String,
            val todoNormal: Boolean,
            val respuestas: String,
            val quien: String?
        ) : Paso
    }

    private val respuestas = mutableListOf<Boolean>()
    private var quien: String? = null

    val paso: Paso
        get() = when {
            respuestas.size < preguntas.size ->
                Paso.Pregunta(respuestas.size + 1, preguntas.size, preguntas[respuestas.size].texto)
            necesitaQuien() && quien == null ->
                Paso.Quien(subpregunta.texto, subpregunta.opciones)
            else -> resultado()
        }

    /** Solo tiene efecto cuando el paso actual es una [Paso.Pregunta]. */
    fun responder(si: Boolean) {
        if (respuestas.size < preguntas.size) respuestas.add(si)
    }

    /** Solo tiene efecto cuando el paso actual es [Paso.Quien]. Ignora ids que no existen. */
    fun elegirQuien(id: String) {
        if (paso is Paso.Quien && subpregunta.opciones.any { it.id == id }) quien = id
    }

    /** Hay algo que deshacer: sirve para corregir un toque equivocado. */
    val puedeVolver: Boolean
        get() = respuestas.isNotEmpty() || quien != null

    fun volver() {
        when {
            quien != null -> quien = null
            respuestas.isNotEmpty() -> respuestas.removeAt(respuestas.lastIndex)
        }
    }

    private fun necesitaQuien(): Boolean {
        val i = preguntas.indexOfFirst { it.id == ID_DIJERON_SER }
        return i >= 0 && i < respuestas.size && respuestas[i]
    }

    private fun resultado(): Paso.Resultado {
        val algunSi = respuestas.any { it }
        val clave = when {
            !algunSi -> "todo_normal"
            quien == "familiar" -> "familiar"
            quien == "entidad" -> "entidad"
            else -> "general"
        }
        return Paso.Resultado(
            claveConsejo = clave,
            todoNormal = !algunSi,
            respuestas = respuestas.joinToString(",") { if (it) "SI" else "NO" },
            quien = quien
        )
    }

    companion object {
        /** Id de la pregunta que activa la subpregunta (clave de preguntas.json, no cambiar). */
        const val ID_DIJERON_SER = "dijeron_ser"
    }
}
