package co.edu.unisabana.pausa.ui.contacto

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import co.edu.unisabana.pausa.R

/** Contacto escogido en el selector del sistema. */
data class ContactoElegido(val nombre: String, val telefono: String)

/**
 * Abre el selector de contactos del sistema (ACTION_PICK sobre teléfonos) y entrega el nombre y el
 * número elegidos. Se crea en la propiedad o en onCreate de la actividad, antes de que arranque.
 * Lo usan Configuración y el aviso por SMS del pánico.
 */
class SelectorContacto(
    private val actividad: ComponentActivity,
    private val alElegir: (ContactoElegido) -> Unit
) {

    private val lanzador = actividad.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        val uri = resultado.data?.data ?: return@registerForActivityResult
        val columnas = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val contacto = actividad.contentResolver.query(uri, columnas, null, null, null)?.use { c ->
            if (c.moveToFirst()) ContactoElegido(nombre = c.getString(0).orEmpty(), telefono = c.getString(1).orEmpty())
            else null
        }
        if (contacto != null && contacto.telefono.isNotBlank()) alElegir(contacto)
    }

    fun abrir() {
        try {
            lanzador.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(actividad, R.string.contacto_sin_selector, Toast.LENGTH_LONG).show()
        }
    }
}
