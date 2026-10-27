package co.edu.unisabana.pausa.detection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Consulta el registro reciente y comprueba si el número está guardado.
 */
class UnknownNumberChecker(private val context: Context) {

    /**
     * Devuelve el número de la última llamada ocurrida desde [desde] (milisegundos)
     * o null si no hay una reciente o no se puede consultar.
     */
    fun ultimoNumero(desde: Long = 0L): String? {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            val projection = arrayOf(CallLog.Calls.NUMBER)

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(desde.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                    )?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Devuelve true si no se encuentra el número en los contactos.
     * Un número nulo o no consultable se considera desconocido.
     */
    fun esDesconocido(numero: String?): Boolean {
        if (numero.isNullOrBlank()) return true

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(numero)
            )

            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null
            )?.use { cursor ->
                !cursor.moveToFirst()
            } ?: true
        } catch (_: SecurityException) {
            true
        } catch (_: IllegalArgumentException) {
            true
        }
    }
}
