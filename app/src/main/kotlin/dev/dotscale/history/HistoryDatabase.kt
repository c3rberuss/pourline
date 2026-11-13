package dev.dotscale.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base de datos del historial. Todo cambio de esquema posterior a la versión 1 exige una `Migration`
 * con su test: no se usa `fallbackToDestructiveMigration`.
 */
@Database(entities = [BrewEntity::class, BrewCurveEntity::class], version = 1, exportSchema = true)
@TypeConverters(HistoryConverters::class)
abstract class HistoryDatabase : RoomDatabase() {
  abstract fun brewDao(): BrewDao

  companion object {
    const val FILE_NAME = "history.db"

    /** [fileName] solo cambia en los tests de rendimiento, que usan un archivo aparte del `history.db` real. */
    fun create(context: Context, fileName: String = FILE_NAME): HistoryDatabase =
      Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, fileName).build()

    /** Para los tests: no toca el `history.db` real. */
    fun inMemory(context: Context): HistoryDatabase =
      Room.inMemoryDatabaseBuilder(context.applicationContext, HistoryDatabase::class.java).build()
  }
}
