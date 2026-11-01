package co.edu.unisabana.pausa.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import co.edu.unisabana.pausa.R

/**
 * Con targetSdk 35, Android 15 dibuja la app detrás de las barras del sistema: esto evita que
 * el contenido quede debajo de la barra de estado o de la de navegación.
 */
fun respetarBarrasDelSistema(raiz: View) {
    ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        vista.updatePadding(
            left = barras.left,
            top = barras.top,
            right = barras.right,
            bottom = barras.bottom
        )
        insets
    }
}

/** Abre una pantalla de Ajustes; algunas marcas no traen todas y no debe cerrarse la app. */
fun Activity.abrirAjustes(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.ajustes_no_disponibles, Toast.LENGTH_LONG).show()
    }
}

/** Colorea una etiqueta (fondo bg_pastilla) con un par de colores de la paleta. */
fun TextView.pintarEtiqueta(@ColorRes fondo: Int, @ColorRes texto: Int) {
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, fondo))
    setTextColor(ContextCompat.getColor(context, texto))
}
