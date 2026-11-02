package co.edu.unisabana.pausa.overlay

/**
 * Fase de la respiración guiada en un segundo dado: INHALA 4 s, SOSTÉN 4 s, EXHALA 6 s, en ciclo
 * de 14 s. Sin dependencias de Android para poder probarla con JUnit.
 */
data class FaseRespiracion(
    val tipo: Tipo,
    /** Duración total de la fase, en segundos. */
    val duracion: Int,
    /** Segundo dentro de la fase: 0 es el primero. */
    val segundoEnFase: Int
) {
    enum class Tipo { INHALA, SOSTEN, EXHALA }

    /** Segundos que faltan para terminar la fase, contando el actual (4, 3, 2, 1). */
    val restantes: Int
        get() = duracion - segundoEnFase

    companion object {
        const val SEGUNDOS_INHALA = 4
        const val SEGUNDOS_SOSTEN = 4
        const val SEGUNDOS_EXHALA = 6
        const val SEGUNDOS_CICLO = SEGUNDOS_INHALA + SEGUNDOS_SOSTEN + SEGUNDOS_EXHALA

        /** [segundo] cuenta desde 0 al empezar la respiración; un valor negativo se toma como 0. */
        fun en(segundo: Int): FaseRespiracion {
            val s = segundo.coerceAtLeast(0) % SEGUNDOS_CICLO
            return when {
                s < SEGUNDOS_INHALA -> FaseRespiracion(Tipo.INHALA, SEGUNDOS_INHALA, s)
                s < SEGUNDOS_INHALA + SEGUNDOS_SOSTEN ->
                    FaseRespiracion(Tipo.SOSTEN, SEGUNDOS_SOSTEN, s - SEGUNDOS_INHALA)
                else -> FaseRespiracion(Tipo.EXHALA, SEGUNDOS_EXHALA, s - SEGUNDOS_INHALA - SEGUNDOS_SOSTEN)
            }
        }
    }
}
