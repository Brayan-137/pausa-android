package co.edu.unisabana.pausa.ui.historial

import android.content.Context
import co.edu.unisabana.pausa.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES_CO = Locale("es", "CO")
private val HORA = DateTimeFormatter.ofPattern("h:mm a", ES_CO)
private val FECHA_Y_HORA = DateTimeFormatter.ofPattern("d MMM, h:mm a", ES_CO)

/** Cómo decir el día de un evento frente a hoy. Separado de Android para probarlo con JUnit. */
enum class DiaRelativo { HOY, AYER, OTRO }

fun diaRelativo(fecha: Long, ahora: Long, zona: ZoneId): DiaRelativo {
    val dia = Instant.ofEpochMilli(fecha).atZone(zona).toLocalDate()
    val hoy = Instant.ofEpochMilli(ahora).atZone(zona).toLocalDate()
    return when (dia) {
        hoy -> DiaRelativo.HOY
        hoy.minusDays(1) -> DiaRelativo.AYER
        else -> DiaRelativo.OTRO
    }
}

/** "Hoy, 3:42 p. m.", "Ayer, 9:10 a. m." o "28 oct, 3:42 p. m.". */
fun formatoFecha(context: Context, fecha: Long): String {
    val zona = ZoneId.systemDefault()
    val momento = Instant.ofEpochMilli(fecha).atZone(zona)
    return when (diaRelativo(fecha, System.currentTimeMillis(), zona)) {
        DiaRelativo.HOY -> context.getString(R.string.fecha_hoy, HORA.format(momento))
        DiaRelativo.AYER -> context.getString(R.string.fecha_ayer, HORA.format(momento))
        DiaRelativo.OTRO -> FECHA_Y_HORA.format(momento)
    }
}
