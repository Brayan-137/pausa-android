package co.edu.unisabana.pausa.util

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Consulta de permisos e intents a Ajustes. Lo usan la bienvenida e Inicio (Julian).
 */
object Permisos {

    /** Se piden con el diálogo normal de Android. */
    val LLAMADAS_Y_CONTACTOS: Array<String> = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS
    )

    /** Diálogo normal desde Android 13; en Android 12 no hay nada que pedir. */
    val NOTIFICACIONES: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    fun tiene(context: Context, permiso: String): Boolean =
        ContextCompat.checkSelfPermission(context, permiso) == PackageManager.PERMISSION_GRANTED

    fun tieneLlamadasYContactos(context: Context): Boolean =
        LLAMADAS_Y_CONTACTOS.all { tiene(context, it) }

    fun tieneNotificaciones(context: Context): Boolean =
        NOTIFICACIONES.all { tiene(context, it) }

    fun puedeMostrarEncima(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun tieneAccesoAUso(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val modo = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return modo == AppOpsManager.MODE_ALLOWED
    }

    /** true si Android no va a cerrar el servicio para ahorrar batería. */
    fun ignoraOptimizacionDeBateria(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    /** Sin estos tres no hay detección ni pop-up; notificaciones y batería son recomendados. */
    fun tieneLosIndispensables(context: Context): Boolean =
        tieneLlamadasYContactos(context) && puedeMostrarEncima(context) && tieneAccesoAUso(context)

    /** Diálogo directo de Android para quitar la optimización de batería solo a Pausa. */
    fun intentQuitarOptimizacionDeBateria(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )

    fun intentMostrarEncima(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun intentAccesoAUso(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Para "Permitir configuración restringida" (Android 15, APK instalado desde archivo). */
    fun intentInfoDeLaApp(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
