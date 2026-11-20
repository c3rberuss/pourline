package dev.josueamaya.pourline.backup

import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.coffee.photo.CoffeePhotoStore
import dev.josueamaya.pourline.grinder.GrinderCatalogSeeder
import dev.josueamaya.pourline.grinder.TestGrinderCatalog
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import dev.josueamaya.pourline.session.RememberedScale
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * El entorno de los tests del gestor: Room en memoria, un directorio de fotos y otro de trabajo en `cacheDir` (el mismo
 * volumen, para que renombrar sea atómico) y preferencias propias. Nunca toca `history.db`, `coffee_photos` ni el
 * `settings.xml` reales.
 */
internal class BackupHarness(prefsName: String) {
  val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  val root = File(context.cacheDir, "backup-$prefsName").apply {
    deleteRecursively()
    mkdirs()
  }
  val photosDir = File(root, "coffee_photos")
  val workDir = File(root, "backup_import")
  val db: HistoryDatabase = HistoryDatabase.inMemory(context)
  val settings: BackupTestSettings = BackupTestSettings(context, prefsName).also { it.delete() }.let { BackupTestSettings(context, prefsName) }
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val brews = BrewRepository(db)
  val recipes = RecipeRepository(db, ResourcesRecipeTexts(context))
  private val prefsName = prefsName

  /** Cuántas veces el gestor pidió descartar cachés. */
  var invalidations = 0

  /** El catálogo de molinos que sincroniza el gestor tras importar. */
  var catalogJson: String = TestGrinderCatalog.JSON

  fun manager(
    streams: BackupStreams = ContentResolverStreams(context.contentResolver),
    limits: BackupLimits = BackupLimits(),
  ) = BackupManager(
    db = db,
    photosDir = photosDir,
    workDir = workDir,
    settings = settings.all,
    seeder = GrinderCatalogSeeder(db.grinderDao(), { catalogJson }, settings.grinder, settings.grinder),
    streams = streams,
    thumbnails = ThumbnailMaker(CoffeePhotoStore(photosDir, root)::regenerateThumbnail),
    appVersion = AppVersion("1.0-test", 7),
    scope = scope,
    invalidateCaches = {
      invalidations++
      brews.clearPendingUndo()
      recipes.clearPendingUndo()
    },
    limits = limits,
  )

  fun uri(name: String): Uri = Uri.fromFile(File(root, name))

  fun file(name: String) = File(root, name)

  fun exportTo(name: String, manager: BackupManager = manager()): ExportResult = runBlocking { manager.export(uri(name), {}) }

  fun entriesOf(file: File): List<String> = ZipFile(file).use { zip -> zip.entries().toList().map { it.name } }

  fun textOf(file: File, entry: String): String = ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry(entry)).readBytes().decodeToString() }

  /** Copia [zip] en [target] sin las entradas para las que [drop] es verdadero. */
  fun withoutEntries(zip: File, target: File, drop: (String) -> Boolean) {
    ZipInputStream(zip.inputStream()).use { input ->
      ZipOutputStream(target.outputStream()).use { output ->
        while (true) {
          val entry = input.nextEntry ?: break
          if (drop(entry.name)) continue
          output.putNextEntry(ZipEntry(entry.name))
          input.copyTo(output)
          output.closeEntry()
        }
      }
    }
  }

  /** Cambia cada ajuste de la copia a un valor distinto del que siembra [seedEverything]; `tour_seen` queda como estaba. */
  fun scrambleSettings() {
    settings.theme.setMode(ThemeMode.LIGHT)
    settings.experience.completeOnboarding(ExperienceMode.BASIC)
    settings.connection.remember(RememberedScale("11:22:33:44:55:66", "Otra"))
    settings.connection.setUseScale(true)
    settings.brew.setAutoFinishSeconds(60)
    settings.brew.setCountdownSeconds(3)
    settings.brew.setLastCoffeeId(999)
    settings.brew.setDefaultDoseG(20.0)
    settings.brew.setDefaultRatio(14.0)
    settings.target.setReference(ReferenceKey.Brew(999))
    settings.target.setStageVibration(true)
    settings.target.setStageSound(false)
    settings.grinder.setDefaultGrinderId(999)
    settings.grinder.setCatalogVersion(1)
  }

  /** Vacía las 11 tablas, las fotos y deja los ajustes sin claves salvo el recorrido (que no se toca). */
  fun wipe() {
    runBlocking { db.backupDao().replaceAll(BackupRows()) }
    photosDir.deleteRecursively()
  }

  fun close() {
    scope.cancel()
    db.close()
    settings.delete()
    root.deleteRecursively()
    context.deleteSharedPreferences(prefsName)
  }
}
