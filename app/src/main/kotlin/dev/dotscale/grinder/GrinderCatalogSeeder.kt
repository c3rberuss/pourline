package dev.dotscale.grinder

import android.content.Context
import dev.dotscale.settings.CatalogVersionStore
import dev.dotscale.settings.DefaultGrinderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** De dónde sale el archivo del catálogo; `null` si no existe. */
fun interface CatalogSource {
  fun read(): String?
}

/** El archivo del catálogo dentro de los `assets` de la app. */
class AssetCatalogSource(context: Context, private val path: String = "grinders/catalog.json") : CatalogSource {
  private val assets = context.applicationContext.assets

  override fun read(): String? = try {
    assets.open(path).bufferedReader().use { it.readText() }
  } catch (_: java.io.IOException) {
    null
  }
}

sealed interface SyncResult {
  /** El archivo no trae una versión más nueva que la ya sincronizada. */
  data object UpToDate : SyncResult

  data class Synced(val version: Int, val added: Int, val updated: Int, val retired: Int, val deleted: Int) : SyncResult

  /** El archivo falta o es inválido, o la base falló: no se cambió nada. */
  data class Failed(val reason: String) : SyncResult
}

/**
 * Sincroniza el catálogo predefinido con la base de datos (§3.6, D34). La base cambia en una transacción y la
 * versión se guarda **después** del commit: si el proceso muere en medio, el siguiente arranque repite la
 * sincronización, que es idempotente.
 */
class GrinderCatalogSeeder(
  private val dao: GrinderDao,
  private val source: CatalogSource,
  private val versions: CatalogVersionStore,
  private val defaults: DefaultGrinderStore,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
    val text = source.read() ?: return@withContext SyncResult.Failed("catalog file not found")
    val data = GrinderCatalogFile.parse(text).getOrElse { return@withContext SyncResult.Failed(it.message ?: "invalid catalog") }
    if (data.version <= versions.catalogVersion) return@withContext SyncResult.UpToDate
    val now = clock()
    try {
      val counts = dao.syncCatalog(data.grinders.map { it.toRows(now) }, defaults.defaultGrinderId.value)
      versions.setCatalogVersion(data.version)
      SyncResult.Synced(data.version, counts.added, counts.updated, counts.retired, counts.deleted)
    } catch (e: android.database.sqlite.SQLiteException) {
      SyncResult.Failed(e.message ?: "database error")
    }
  }
}
