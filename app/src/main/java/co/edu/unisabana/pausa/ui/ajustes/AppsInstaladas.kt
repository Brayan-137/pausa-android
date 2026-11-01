package co.edu.unisabana.pausa.ui.ajustes

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppInstalada(val paquete: String, val nombre: String)

/** Apps con ícono en el launcher, en orden alfabético y sin Pausa. Es lento: fuera del hilo principal. */
fun appsInstaladas(context: Context): List<AppInstalada> {
    val pm = context.packageManager
    val conLauncher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(conLauncher, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map { AppInstalada(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .sortedBy { it.nombre.lowercase() }
}

/** Nombre visible de una app; si ya no está instalada, su paquete. */
fun nombreDeApp(context: Context, paquete: String): String =
    try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(paquete, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        paquete
    }
