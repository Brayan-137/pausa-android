package co.edu.unisabana.pausa.util

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Consulta de permisos e intents a Ajustes. Lo usan la pantalla de desarrollo y la bienvenida (Julian).
 */
object Permisos {

    /** Permisos que se piden con el diálogo normal de Android. */
    val EN_TIEMPO_DE_EJECUCION: Array<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun tiene(context: Context, permiso: String): Boolean =
        ContextCompat.checkSelfPermission(context, permiso) == PackageManager.PERMISSION_GRANTED

    fun tieneTodosLosDeEjecucion(context: Context): Boolean =
        EN_TIEMPO_DE_EJECUCION.all { tiene(context, it) }

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

    fun intentMostrarEncima(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun intentAccesoAUso(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Para "Permitir configuración restringida" (Android 15, APK instalado desde archivo). */
    fun intentInfoDeLaApp(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
