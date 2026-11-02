package co.edu.unisabana.pausa.overlay

import co.edu.unisabana.pausa.overlay.FaseRespiracion.Tipo
import org.junit.Assert.assertEquals
import org.junit.Test

class FaseRespiracionTest {

    @Test
    fun inhalaDuranteLosPrimeros4Segundos() {
        (0..3).forEach { assertEquals(Tipo.INHALA, FaseRespiracion.en(it).tipo) }
        assertEquals(4, FaseRespiracion.en(0).restantes)
        assertEquals(1, FaseRespiracion.en(3).restantes)
    }

    @Test
    fun sostieneLosSiguientes4Segundos() {
        (4..7).forEach { assertEquals(Tipo.SOSTEN, FaseRespiracion.en(it).tipo) }
        assertEquals(0, FaseRespiracion.en(4).segundoEnFase)
        assertEquals(4, FaseRespiracion.en(4).restantes)
    }

    @Test
    fun exhalaLosSiguientes6Segundos() {
        (8..13).forEach { assertEquals(Tipo.EXHALA, FaseRespiracion.en(it).tipo) }
        assertEquals(6, FaseRespiracion.en(8).restantes)
        assertEquals(1, FaseRespiracion.en(13).restantes)
    }

    @Test
    fun elCicloSeRepiteCada14Segundos() {
        assertEquals(FaseRespiracion.en(0), FaseRespiracion.en(14))
        assertEquals(FaseRespiracion.en(9), FaseRespiracion.en(9 + 14 * 3))
        assertEquals(Tipo.INHALA, FaseRespiracion.en(59 - 45).tipo)
    }

    @Test
    fun unSegundoNegativoSeTomaComoCero() {
        assertEquals(FaseRespiracion.en(0), FaseRespiracion.en(-5))
    }
}
