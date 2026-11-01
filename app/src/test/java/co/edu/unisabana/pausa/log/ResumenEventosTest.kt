package co.edu.unisabana.pausa.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumenEventosTest {

    private fun evento(decision: String, respuestas: String) = EventoPausa(
        fecha = 0L,
        appPackage = "com.nequi.MobileApp",
        numero = null,
        respuestas = respuestas,
        decision = decision,
        esDemo = false
    )

    @Test
    fun `sin eventos no hay porcentaje`() {
        val resumen = ResumenEventos.de(emptyList())
        assertEquals(0, resumen.total)
        assertNull(resumen.porcentajeSeDetuvieron)
    }

    @Test
    fun `cuenta cada decision`() {
        val resumen = ResumenEventos.de(
            listOf(
                evento("VERIFICAR", "SI,NO,NO"),
                evento("ESTAFA", "SI,SI,SI"),
                evento("CONTINUAR", "NO,NO,NO")
            )
        )
        assertEquals(3, resumen.total)
        assertEquals(1, resumen.verificaron)
        assertEquals(1, resumen.estafas)
        assertEquals(1, resumen.continuaron)
    }

    @Test
    fun `continuar con todo No no baja el porcentaje de los que se detuvieron`() {
        val resumen = ResumenEventos.de(
            listOf(
                evento("VERIFICAR", "SI,NO,NO"),
                evento("CONTINUAR", "NO,NO,NO")
            )
        )
        assertEquals(1, resumen.conAlerta)
        assertEquals(100, resumen.porcentajeSeDetuvieron)
    }

    @Test
    fun `continuar con alguna alerta cuenta como no detenido`() {
        val resumen = ResumenEventos.de(
            listOf(
                evento("ESTAFA", "SI,SI,NO"),
                evento("CONTINUAR", "NO,SI,NO")
            )
        )
        assertEquals(2, resumen.conAlerta)
        assertEquals(1, resumen.seDetuvieron)
        assertEquals(50, resumen.porcentajeSeDetuvieron)
    }
}
