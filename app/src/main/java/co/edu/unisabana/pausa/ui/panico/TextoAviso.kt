package co.edu.unisabana.pausa.ui.panico

/** Texto del SMS que la persona le manda a su contacto de confianza. Sin Android para poder probarlo. */
object TextoAviso {

    /** [nombre] es el nombre del contacto; si es null o está en blanco, el saludo va sin nombre. */
    fun armar(nombre: String?): String {
        val saludo = nombre?.trim()?.takeIf { it.isNotEmpty() }?.let { "Hola $it." } ?: "Hola."
        return "$saludo Creo que me están estafando: colgué una llamada sospechosa y ya " +
            "transferí dinero o di datos. Llámame, por favor."
    }
}
