package co.edu.unisabana.pausa.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val BIENVENIDA_COMPLETA = booleanPreferencesKey("bienvenida_completa")
        val PROTECCION_ACTIVA = booleanPreferencesKey("proteccion_activa")
        val CONTACTO_NOMBRE = stringPreferencesKey("contacto_nombre")
        val CONTACTO_TELEFONO = stringPreferencesKey("contacto_telefono")
        val BLOQUEO_RESPIRACION = booleanPreferencesKey("bloqueo_respiracion")
        val NOVEDADES_VISTAS = booleanPreferencesKey("novedades_vistas")
    }

    val appsSensibles: Flow<Set<String>> =
        context.dataStore.data.map { it[Claves.APPS_SENSIBLES] ?: APPS_POR_DEFECTO }

    val minutosVentana: Flow<Int> =
        context.dataStore.data.map { it[Claves.MINUTOS_VENTANA] ?: MINUTOS_POR_DEFECTO }

    val modoDemo: Flow<Boolean> =
        context.dataStore.data.map { it[Claves.MODO_DEMO] ?: false }

    /** true cuando la persona ya pasó por la bienvenida; la app abre directo en Inicio. */
    val bienvenidaCompleta: Flow<Boolean> =
        context.dataStore.data.map { it[Claves.BIENVENIDA_COMPLETA] ?: false }

    suspend fun guardarBienvenidaCompleta() {
        context.dataStore.edit { it[Claves.BIENVENIDA_COMPLETA] = true }
    }

    /** El interruptor de Inicio. Si está en false, nadie debe arrancar el servicio. */
    val proteccionActiva: Flow<Boolean> =
        context.dataStore.data.map { it[Claves.PROTECCION_ACTIVA] ?: true }

    suspend fun guardarProteccionActiva(activa: Boolean) {
        context.dataStore.edit { it[Claves.PROTECCION_ACTIVA] = activa }
    }

    /** Contacto de confianza al que el pánico le avisa por SMS. Null si no se ha elegido. */
    val contactoNombre: Flow<String?> =
        context.dataStore.data.map { it[Claves.CONTACTO_NOMBRE] }

    val contactoTelefono: Flow<String?> =
        context.dataStore.data.map { it[Claves.CONTACTO_TELEFONO] }

    suspend fun guardarContacto(nombre: String, telefono: String) {
        context.dataStore.edit {
            it[Claves.CONTACTO_NOMBRE] = nombre
            it[Claves.CONTACTO_TELEFONO] = telefono
        }
    }

    /** Si está activo, "Continuar de todos modos" lleva al bloqueo con respiración. */
    val bloqueoRespiracion: Flow<Boolean> =
        context.dataStore.data.map { it[Claves.BLOQUEO_RESPIRACION] ?: false }

    suspend fun guardarBloqueoRespiracion(activo: Boolean) {
        context.dataStore.edit { it[Claves.BLOQUEO_RESPIRACION] = activo }
    }

    /** true cuando la persona ya pasó por las pantallas de novedades. */
    val novedadesVistas: Flow<Boolean> =
        context.dataStore.data.map { it[Claves.NOVEDADES_VISTAS] ?: false }

    suspend fun guardarNovedadesVistas(vistas: Boolean) {
        context.dataStore.edit { it[Claves.NOVEDADES_VISTAS] = vistas }
    }

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
         * Bancos y billeteras de Colombia. Cada paquete se comprobó en Google Play el 31 oct 2026
         * (play.google.com/store/apps/details?id=<paquete>). Para agregar otro: abrir la app en
         * Play Store desde el navegador; el paquete es el valor de "id=" en la URL. No adivinarlos.
         */
        val APPS_POR_DEFECTO: Set<String> = setOf(
            "com.nequi.MobileApp",                  // Nequi
            "com.davivienda.daviplataapp",          // DaviPlata
            "co.com.bancolombia.personas.superapp", // Mi Bancolombia
            "com.davivienda.daviviendaapp",         // Davivienda
            "co.com.bbva.mb",                       // BBVA Colombia
            "com.bancodebogota.bancamovil",         // Banco de Bogotá
            "com.nu.production",                    // Nu Colombia
            "com.grupoavalav1.bancamovil",          // AV Villas
            "com.grupoavaloc1.bancamovil",          // Banco de Occidente
            "com.grupoavalpo.bancamovil",           // Banco Popular
            "com.bcs.retail",                       // Banco Caja Social
            "eu.netinfo.colpatria.system",          // DAVIbank (antes Scotiabank Colpatria)
            "co.com.bancofalabella.mobile.omc",     // Banco Falabella
            "co.com.lulobank.production",           // Lulo
            "com.mercadopago.wallet"                // Mercado Pago
        )
    }
}
