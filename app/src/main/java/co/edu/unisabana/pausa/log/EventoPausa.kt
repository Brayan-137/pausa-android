package co.edu.unisabana.pausa.log

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un pop-up mostrado y lo que decidió la persona. Responsable: Julian (28 oct).
 * Sirve para el historial y para las métricas de la prueba con usuarios y la demo.
 */
@Entity(tableName = "eventos")
data class EventoPausa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha: Long,
    val appPackage: String,
    val numero: String?,
    /** Respuestas en orden, p. ej. "SI,NO,SI". */
    val respuestas: String,
    /** Nombre de la Decision: VERIFICAR, ESTAFA o CONTINUAR. */
    val decision: String,
    val esDemo: Boolean
)
