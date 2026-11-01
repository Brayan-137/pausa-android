package co.edu.unisabana.pausa.log

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Guarda un evento aunque la pantalla que abrió el pop-up ya se haya cerrado: la persona decide
 * con el pop-up encima de otra app, cuando esa pantalla puede no existir.
 */
object RegistroEventos {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun guardar(context: Context, evento: EventoPausa) {
        val db = PausaDatabase.get(context.applicationContext)
        alcance.launch { db.eventos().insertar(evento) }
    }
}
