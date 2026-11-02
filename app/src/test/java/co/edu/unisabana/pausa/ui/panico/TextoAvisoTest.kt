package co.edu.unisabana.pausa.ui.panico

import org.junit.Assert.assertEquals
import org.junit.Test

class TextoAvisoTest {

    @Test
    fun conNombre_saludaAlContacto() {
        assertEquals(
            "Hola Mamá. Creo que me están estafando: colgué una llamada sospechosa y ya " +
                "transferí dinero o di datos. Llámame, por favor.",
            TextoAviso.armar("Mamá")
        )
    }

    @Test
    fun sinNombre_saludaSinNombre() {
        val esperado = "Hola. Creo que me están estafando: colgué una llamada sospechosa y ya " +
            "transferí dinero o di datos. Llámame, por favor."
        assertEquals(esperado, TextoAviso.armar(null))
        assertEquals(esperado, TextoAviso.armar("   "))
    }

    @Test
    fun recortaEspaciosDelNombre() {
        assertEquals(true, TextoAviso.armar("  Ana  ").startsWith("Hola Ana. Creo"))
    }
}
