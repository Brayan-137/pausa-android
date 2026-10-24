package co.edu.unisabana.pausa.log

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventoPausaDao {

    @Insert
    suspend fun insertar(evento: EventoPausa): Long

    @Query("SELECT * FROM eventos ORDER BY fecha DESC")
    fun observarTodos(): Flow<List<EventoPausa>>

    @Query("SELECT * FROM eventos WHERE esDemo = 0 ORDER BY fecha DESC")
    fun observarReales(): Flow<List<EventoPausa>>
}
