package co.edu.unisabana.pausa.ui.historial

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoFechaTest {

    private val bogota = ZoneId.of("America/Bogota")

    private fun ms(dia: Int, hora: Int, minuto: Int = 0): Long =
        ZonedDateTime.of(2026, 10, dia, hora, minuto, 0, 0, bogota).toInstant().toEpochMilli()

    @Test
    fun `el mismo dia es hoy aunque sea de madrugada`() {
        assertEquals(DiaRelativo.HOY, diaRelativo(ms(31, 0, 1), ms(31, 23, 59), bogota))
    }

    @Test
    fun `antes de medianoche es ayer aunque hayan pasado minutos`() {
        assertEquals(DiaRelativo.AYER, diaRelativo(ms(30, 23, 59), ms(31, 0, 1), bogota))
    }

    @Test
    fun `dos dias antes no es ayer`() {
        assertEquals(DiaRelativo.OTRO, diaRelativo(ms(29, 12), ms(31, 12), bogota))
    }

    @Test
    fun `el dia se calcula en la zona del celular`() {
        // 23:00 en Bogotá del 30 ya es 31 en UTC.
        val utc = ZoneId.of("UTC")
        assertEquals(DiaRelativo.AYER, diaRelativo(ms(30, 23), ms(31, 12), bogota))
        assertEquals(DiaRelativo.HOY, diaRelativo(ms(30, 23), ms(31, 12), utc))
    }
}
