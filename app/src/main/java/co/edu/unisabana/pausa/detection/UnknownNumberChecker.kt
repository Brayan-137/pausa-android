package co.edu.unisabana.pausa.detection

import android.content.Context

/**
 * Dice si la última llamada fue de un número que NO está en los contactos.
 * Responsable: Juan David (25 oct).
 *
 * Pista: leer el último registro de CallLog.Calls.CONTENT_URI (orden DATE DESC) y buscar el
 * número con ContactsContract.PhoneLookup.CONTENT_FILTER_URI. Requiere READ_CALL_LOG y READ_CONTACTS.
 */
class UnknownNumberChecker(private val context: Context) {

    /** Número de la última llamada, o null si no se puede leer. */
    fun ultimoNumero(): String? {
        // TODO(Juan David, 25 oct)
        return null
    }

    /** true si el número no está guardado. Un número oculto o ilegible cuenta como desconocido. */
    fun esDesconocido(numero: String?): Boolean {
        // TODO(Juan David, 25 oct)
        return true
    }
}
