package co.edu.unisabana.pausa.log

import co.edu.unisabana.pausa.overlay.Decision

/** true si la persona respondió Sí a alguna pregunta del pop-up. */
fun EventoPausa.tuvoAlerta(): Boolean = respuestas.split(",").any { it.trim() == "SI" }

/**
 * Métricas del historial para la demo y la prueba con usuarios. Sin dependencias de Android
 * para poder probarlas con JUnit.
 */
data class ResumenEventos(
    val total: Int,
    val verificaron: Int,
    val estafas: Int,
    val continuaron: Int,
    /** Pop-ups en los que la persona respondió Sí a alguna pregunta. */
    val conAlerta: Int,
    /** De los pop-ups con alerta, cuántos terminaron en Verificar o Es una estafa. */
    val seDetuvieron: Int
) {
    /** Porcentaje de 0 a 100 de pop-ups con alerta en los que la persona se detuvo; null si no hay. */
    val porcentajeSeDetuvieron: Int?
        get() = if (conAlerta == 0) null else seDetuvieron * 100 / conAlerta

    companion object {
        fun de(eventos: List<EventoPausa>): ResumenEventos {
            val conAlerta = eventos.filter { it.tuvoAlerta() }
            return ResumenEventos(
                total = eventos.size,
                verificaron = eventos.count { it.decision == Decision.VERIFICAR.name },
                estafas = eventos.count { it.decision == Decision.ESTAFA.name },
                continuaron = eventos.count { it.decision == Decision.CONTINUAR.name },
                conAlerta = conAlerta.size,
                seDetuvieron = conAlerta.count { it.decision != Decision.CONTINUAR.name }
            )
        }
    }
}
