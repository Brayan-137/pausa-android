package co.edu.unisabana.pausa.overlay

import co.edu.unisabana.pausa.content.OpcionQuien
import co.edu.unisabana.pausa.content.Pregunta
import co.edu.unisabana.pausa.content.SubpreguntaQuien
import co.edu.unisabana.pausa.overlay.FlujoPreguntas.Paso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlujoPreguntasTest {

    private fun nuevo() = FlujoPreguntas(
        preguntas = listOf(
            Pregunta("pidieron_dinero", "p1"),
            Pregunta("presion", "p2"),
            Pregunta("dijeron_ser", "p3")
        ),
        subpregunta = SubpreguntaQuien(
            texto = "¿Quién?",
            opciones = listOf(OpcionQuien("familiar", "Familiar"), OpcionQuien("entidad", "Entidad"))
        )
    )

    private fun FlujoPreguntas.resultado() = paso as Paso.Resultado

    @Test
    fun empiezaEnLaPrimeraPregunta() {
        assertEquals(Paso.Pregunta(1, 3, "p1"), nuevo().paso)
    }

    @Test
    fun todoNoDaTodoNormalSinSubpregunta() {
        val f = nuevo()
        repeat(3) { f.responder(false) }
        val r = f.resultado()
        assertTrue(r.todoNormal)
        assertEquals("todo_normal", r.claveConsejo)
        assertEquals("NO,NO,NO", r.respuestas)
    }

    @Test
    fun siEnDijeronSerPideQuien() {
        val f = nuevo()
        f.responder(false); f.responder(false); f.responder(true)
        assertTrue(f.paso is Paso.Quien)
    }

    @Test
    fun familiarYEntidadDanSuConsejo() {
        val a = nuevo()
        a.responder(false); a.responder(false); a.responder(true); a.elegirQuien("familiar")
        assertEquals("familiar", a.resultado().claveConsejo)

        val b = nuevo()
        b.responder(true); b.responder(true); b.responder(true); b.elegirQuien("entidad")
        assertEquals("entidad", b.resultado().claveConsejo)
        assertEquals("SI,SI,SI", b.resultado().respuestas)
    }

    @Test
    fun siSinDijeronSerDaConsejoGeneral() {
        val f = nuevo()
        f.responder(true); f.responder(false); f.responder(false)
        val r = f.resultado()
        assertFalse(r.todoNormal)
        assertEquals("general", r.claveConsejo)
    }

    @Test
    fun ignoraQuienInvalidoOFueraDeTurno() {
        val f = nuevo()
        f.elegirQuien("familiar") // todavía no toca
        f.responder(false); f.responder(false); f.responder(true)
        f.elegirQuien("otro") // id inexistente
        assertTrue(f.paso is Paso.Quien)
    }

    @Test
    fun volverDeshaceUnToqueEquivocado() {
        val f = nuevo()
        assertFalse(f.puedeVolver)
        f.responder(true)
        assertTrue(f.puedeVolver)
        f.volver()
        assertEquals(Paso.Pregunta(1, 3, "p1"), f.paso)
        f.responder(false); f.responder(false); f.responder(true)
        f.elegirQuien("entidad")
        f.volver() // quita "quién"
        assertTrue(f.paso is Paso.Quien)
        f.volver() // quita la respuesta 3
        assertEquals(Paso.Pregunta(3, 3, "p3"), f.paso)
    }

    @Test
    fun responderTrasElResultadoNoCambiaNada() {
        val f = nuevo()
        repeat(3) { f.responder(false) }
        f.responder(true)
        assertEquals("NO,NO,NO", f.resultado().respuestas)
    }
}
