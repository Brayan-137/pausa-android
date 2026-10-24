package co.edu.unisabana.pausa.log

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [EventoPausa::class], version = 1, exportSchema = false)
abstract class PausaDatabase : RoomDatabase() {

    abstract fun eventos(): EventoPausaDao

    companion object {
        @Volatile private var instancia: PausaDatabase? = null

        fun get(context: Context): PausaDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    PausaDatabase::class.java,
                    "pausa.db"
                ).build().also { instancia = it }
            }
    }
}
