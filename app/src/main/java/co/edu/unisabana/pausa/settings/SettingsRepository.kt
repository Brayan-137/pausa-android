package co.edu.unisabana.pausa.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ajustes")

/**
 * Ajustes de la app guardados en el celular. Responsable: Julian.
 */
class SettingsRepository(private val context: Context) {

    private object Claves {
        val APPS_SENSIBLES = stringSetPreferencesKey("apps_sensibles")
        val MINUTOS_VENTANA = intPreferencesKey("minutos_ventana")
        val MODO_DEMO = booleanPreferencesKey("modo_demo")
    }

    val appsSensibles: Flow<Set<String>> =
        context.dataStore.data.map { it[Claves.APPS_SENSIBLES] ?: APPS_POR_DEFECTO }

    val minutosVentana: Flow<Int> =
        context.dataStore.data.map { it[Claves.MINUTOS_VENTANA] ?: MINUTOS_POR_DEFECTO }

    val modoDemo: Flow<Boolean> =
        context.dataStore.data.map { it[Claves.MODO_DEMO] ?: false }

    suspend fun guardarAppsSensibles(paquetes: Set<String>) {
        context.dataStore.edit { it[Claves.APPS_SENSIBLES] = paquetes }
    }

    suspend fun guardarMinutosVentana(minutos: Int) {
        context.dataStore.edit { it[Claves.MINUTOS_VENTANA] = minutos }
    }

    suspend fun guardarModoDemo(activo: Boolean) {
        context.dataStore.edit { it[Claves.MODO_DEMO] = activo }
    }

    companion object {
        const val MINUTOS_POR_DEFECTO = 15

        /**
         * TODO(Julian): nombres de paquete de los bancos y billeteras acordados.
         * Para sacarlos: abrir la app en Play Store desde el navegador; el paquete es el valor
         * de "id=" en la URL. No adivinarlos.
         */
        val APPS_POR_DEFECTO: Set<String> = emptySet()
    }
}
