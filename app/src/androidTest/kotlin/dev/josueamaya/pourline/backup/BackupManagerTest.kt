package dev.josueamaya.pourline.backup

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.coffee.photo.CoffeePhotoStore
import dev.josueamaya.pourline.coffee.photo.thumbNameOf
import dev.josueamaya.pourline.grinder.GrinderCatalogSeeder
import dev.josueamaya.pourline.grinder.TestGrinderCatalog
import dev.josueamaya.pourline.history.HistoryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.OffsetDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Exportar y leer una copia contra Room en memoria, fotos y ajustes de prueba: nunca los datos reales de la app. */
class BackupManagerTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var root: File
  private lateinit var photosDir: File
  private lateinit var workDir: File
  private lateinit var db: HistoryDatabase
  private lateinit var settings: BackupTestSettings
  private lateinit var scope: CoroutineScope
  private var invalidations = 0

  @Before
  fun setUp() {
    root = File(context.cacheDir, "backup-manager-test").apply {
      deleteRecursively()
      mkdirs()
    }
    photosDir = File(root, "coffee_photos")
    workDir = File(root, "backup_import")
    db = HistoryDatabase.inMemory(context)
    settings = BackupTestSettings(context, PREFS)
    settings.delete()
    settings = BackupTestSettings(context, PREFS)
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    invalidations = 0
  }

  @After
  fun tearDown() {
    scope.cancel()
    db.close()
    settings.delete()
    root.deleteRecursively()
  }

  private fun manager(
    streams: BackupStreams = ContentResolverStreams(context.contentResolver),
    limits: BackupLimits = BackupLimits(),
  ) = BackupManager(
    db = db,
    photosDir = photosDir,
    workDir = workDir,
    settings = settings.all,
    seeder = GrinderCatalogSeeder(db.grinderDao(), { TestGrinderCatalog.JSON }, settings.grinder, settings.grinder),
    streams = streams,
    thumbnails = ThumbnailMaker(CoffeePhotoStore(photosDir, root)::regenerateThumbnail),
    appVersion = AppVersion("1.0-test", 7),
    scope = scope,
    invalidateCaches = { invalidations++ },
    limits = limits,
  )

  private fun uri(name: String): Uri = Uri.fromFile(File(root, name))

  private fun exportTo(name: String, manager: BackupManager = manager()): ExportResult = runBlocking { manager.export(uri(name), {}) }

  private fun entriesOf(file: File): List<String> = ZipFile(file).use { zip -> zip.entries().toList().map { it.name } }

  private fun textOf(file: File, entry: String): String = ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry(entry)).readBytes().decodeToString() }

  /** Copia [zip] sin las entradas para las que [drop] es verdadero. */
  private fun withoutEntries(zip: File, target: File, drop: (String) -> Boolean) {
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

  @Test
  fun exportingWritesTheManifestDataSettingsAndEveryPhoto() = runBlocking {
    val seeded = seedEverything(context, db, photosDir, settings)
    val result = exportTo("backup.zip")

    val counts = (result as ExportResult.Success).counts
    // Solo "Mis molinos" (2 de 3), las 4 recetas (2 predefinidas), las 3 preparaciones y las fotos principales.
    assertEquals(BackupCounts(coffees = 3, grinders = 2, grindCalibrations = 2, recipes = 4, brews = 3, photos = 3), counts)

    val file = File(root, "backup.zip")
    val photoEntries = seeded.photoNames.flatMap { listOf(it, thumbNameOf(it)) }.sorted().map { "photos/$it" }
    assertEquals(listOf("manifest.json", "data.json", "settings.json") + photoEntries, entriesOf(file))

    val manifest = BackupJson.parseToJsonElement(textOf(file, "manifest.json")).jsonObject
    assertEquals("pourline", manifest.getValue("app").toString().trim('"'))
    assertEquals("1", manifest.getValue("formatVersion").toString())
    assertEquals(HistoryDatabase.VERSION.toString(), manifest.getValue("schemaVersion").toString())
    assertEquals("1.0-test", manifest.getValue("appVersionName").toString().trim('"'))
    assertEquals("7", manifest.getValue("appVersionCode").toString())
    OffsetDateTime.parse(manifest.getValue("createdAt").toString().trim('"'))
    assertEquals("2", manifest.getValue("counts").jsonObject.getValue("grinders").toString())

    val data = BackupJson.decodeFromString(DataDto.serializer(), textOf(file, "data.json")).toRows()
    assertRowsEqual(db.backupDao().readAll(), data)

    val written = BackupJson.parseToJsonElement(textOf(file, "settings.json")).jsonObject
    assertEquals(settings.snapshot().toDto().let { BackupJson.parseToJsonElement(BackupJson.encodeToString(SettingsDto.serializer(), it)).jsonObject }, written)
    assertEquals(17, written.size)
    assertFalse(written.containsKey("tour_seen"))
    // La copia pasa por el lector: es lo que importaría.
    val archive = BackupReader().read(file.inputStream(), File(root, "check-staging")) {}
    assertEquals(seeded.photoNames.flatMap { listOf(it, thumbNameOf(it)) }.toSet(), archive.staged)
  }

  @Test
  fun exportingAnEmptyAppGivesAValidBackupWithZeroCounts() = runBlocking {
    db.backupDao().replaceAll(BackupRows())
    val manager = manager()
    val result = manager.export(uri("empty.zip"), {})
    assertEquals(ExportResult.Success(BackupCounts(0, 0, 0, 0, 0, 0)), result)
    assertEquals(listOf("manifest.json", "data.json", "settings.json"), entriesOf(File(root, "empty.zip")))
    assertEquals("{}", textOf(File(root, "empty.zip"), "settings.json"))

    val prepared = manager.prepareImport(uri("empty.zip"), {})
    assertEquals(BackupCounts(0, 0, 0, 0, 0, 0), ((prepared as PrepareResult.Ready).import).summary.counts)
  }

  @Test
  fun aFailedExportDeletesTheHalfWrittenFile() = runBlocking {
    seedEverything(context, db, photosDir, settings)
    val streams = object : BackupStreams by ContentResolverStreams(context.contentResolver) {
      override fun openOutput(uri: Uri): OutputStream = FailingOutput(File(uri.path!!).outputStream(), failAfter = 16 * 1024)
    }
    val result = manager(streams).export(uri("broken.zip"), {})
    assertEquals(ExportResult.Failed(BackupError.IO), result)
    assertFalse(File(root, "broken.zip").exists())
  }

  @Test
  fun aProviderThatCannotBeOpenedFailsWithIo() = runBlocking {
    val streams = object : BackupStreams by ContentResolverStreams(context.contentResolver) {
      override fun openOutput(uri: Uri): OutputStream = throw java.io.FileNotFoundException("sin permiso")
    }
    assertEquals(ExportResult.Failed(BackupError.IO), manager(streams).export(uri("none.zip"), {}))
  }

  @Test
  fun anExportOverTheLimitIsTooLargeAndDeletesTheFile() = runBlocking {
    seedEverything(context, db, photosDir, settings)
    val result = manager(limits = BackupLimits(maxPhotoBytes = 1_000)).export(uri("big.zip"), {})
    assertEquals(ExportResult.Failed(BackupError.TOO_LARGE), result)
    assertFalse(File(root, "big.zip").exists())
  }

  @Test
  fun exportingReportsMonotonicProgressUpToOne() = runBlocking {
    seedEverything(context, db, photosDir, settings)
    val progress = mutableListOf<Float>()
    manager().export(uri("progress.zip"), { progress += it })
    assertTrue(progress.isNotEmpty())
    assertEquals(progress.sorted(), progress)
    assertEquals(1f, progress.last(), 0f)
    assertTrue(progress.all { it in 0f..1f })
  }

  @Test
  fun aPhotoWithoutItsFileIsLeftOutOfTheArchive() = runBlocking {
    val seeded = seedEverything(context, db, photosDir, settings)
    File(photosDir, seeded.photoNames[0]).delete()
    File(photosDir, thumbNameOf(seeded.photoNames[0])).delete()
    val result = exportTo("missing-photo.zip") as ExportResult.Success
    assertEquals(2, result.counts.photos)
    assertFalse(entriesOf(File(root, "missing-photo.zip")).any { it.contains(seeded.photoNames[0]) })
    // Al importarla, la referencia sin archivo cuenta como foto perdida.
    val prepared = (manager().prepareImport(uri("missing-photo.zip"), {}) as PrepareResult.Ready).import
    assertEquals(1, prepared.missingPhotos)
    assertEquals(2, prepared.summary.counts.photos)
  }

  @Test
  fun preparingAValidBackupChangesNothingAndSummarizesIt() = runBlocking {
    val seeded = seedEverything(context, db, photosDir, settings)
    val export = exportTo("valid.zip") as ExportResult.Success
    val rowsBefore = db.backupDao().readAll()
    val photosBefore = readFiles(photosDir)
    val settingsBefore = settings.snapshot()
    val tourBefore = settings.tour.seen.value
    val before = System.currentTimeMillis()

    val prepared = (manager().prepareImport(uri("valid.zip"), {}) as PrepareResult.Ready).import

    assertRowsEqual(rowsBefore, db.backupDao().readAll())
    assertFilesEqual(photosBefore, readFiles(photosDir))
    assertEquals(settingsBefore, settings.snapshot())
    assertEquals(tourBefore, settings.tour.seen.value)
    assertEquals(export.counts, prepared.summary.counts)
    assertEquals("1.0-test", prepared.summary.appVersionName)
    assertTrue(prepared.summary.createdAt.toInstant().toEpochMilli() in (before - 120_000)..(System.currentTimeMillis() + 1_000))
    assertEquals(0, prepared.missingPhotos)
    // Las fotos y las miniaturas están en el staging, no en `coffee_photos`.
    assertEquals(seeded.photoNames.flatMap { listOf(it, thumbNameOf(it)) }.toSet(), File(workDir, "staging").list()!!.toSet())
    assertEquals(0, invalidations)
  }

  @Test
  fun preparingAnInvalidBackupLeavesNoStaging() = runBlocking {
    seedEverything(context, db, photosDir, settings)
    val manager = manager()
    val rowsBefore = db.backupDao().readAll()

    // No es una copia.
    File(root, "junk.zip").writeBytes(ByteArray(4096) { (it * 13).toByte() })
    assertEquals(PrepareResult.Invalid(BackupError.NOT_A_BACKUP), manager.prepareImport(uri("junk.zip"), {}))
    assertFalse(File(workDir, "staging").exists())

    // Una copia truncada.
    exportTo("whole.zip", manager)
    val whole = File(root, "whole.zip").readBytes()
    File(root, "truncated.zip").writeBytes(whole.copyOf(whole.size / 2))
    val truncated = manager.prepareImport(uri("truncated.zip"), {})
    assertTrue(truncated.toString(), truncated is PrepareResult.Invalid)
    assertFalse(File(workDir, "staging").exists())

    // Una con una referencia rota: sin los cafés.
    val rows = db.backupDao().readAll().let { it.copy(coffees = emptyList()) }
    ByteArrayOutputStream().also { out ->
      BackupWriter().write(
        out,
        ManifestDto(APP_ID, FORMAT_VERSION, HistoryDatabase.VERSION, "1.0", 1, "2026-11-19T18:45:12-06:00", CountsDto(0, 0, 0, 0, 0, 0)),
        rows.toDto(),
        settings.snapshot().toDto(),
        emptyList(),
      ) {}
      File(root, "broken.zip").writeBytes(out.toByteArray())
    }
    assertEquals(PrepareResult.Invalid(BackupError.BROKEN_REFERENCES), manager.prepareImport(uri("broken.zip"), {}))
    assertFalse(File(workDir, "staging").exists())

    // Un documento que no se puede abrir.
    assertEquals(PrepareResult.Invalid(BackupError.IO), manager.prepareImport(uri("no-existe.zip"), {}))

    assertRowsEqual(rowsBefore, db.backupDao().readAll())
  }

  @Test
  fun aMissingThumbnailIsRegeneratedWithARealBitmap() = runBlocking {
    val seeded = seedEverything(context, db, photosDir, settings)
    exportTo("whole.zip")
    val name = seeded.photoNames[0]
    withoutEntries(File(root, "whole.zip"), File(root, "nothumb.zip")) { it == "photos/${thumbNameOf(name)}" }

    val prepared = (manager().prepareImport(uri("nothumb.zip"), {}) as PrepareResult.Ready).import

    val thumb = File(File(workDir, "staging"), thumbNameOf(name))
    assertTrue(thumb.isFile)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(thumb.path, bounds)
    assertEquals(256, bounds.outWidth)
    assertEquals(256, bounds.outHeight)
    assertEquals(0, prepared.missingPhotos)
    assertEquals(3, prepared.summary.counts.photos)
  }

  @Test
  fun unreferencedPhotosInTheArchiveAreDroppedFromTheStaging() = runBlocking {
    seedEverything(context, db, photosDir, settings)
    exportTo("whole.zip")
    // Se añade una foto que ningún café referencia.
    val stray = "99999999-9999-4999-8999-999999999999.jpg"
    ZipOutputStream(File(root, "stray.zip").outputStream()).use { output ->
      ZipInputStream(File(root, "whole.zip").inputStream()).use { input ->
        while (true) {
          val entry = input.nextEntry ?: break
          output.putNextEntry(ZipEntry(entry.name))
          input.copyTo(output)
          output.closeEntry()
        }
      }
      output.putNextEntry(ZipEntry("photos/$stray"))
      output.write(byteArrayOf(1, 2, 3))
      output.closeEntry()
    }
    val prepared = (manager().prepareImport(uri("stray.zip"), {}) as PrepareResult.Ready).import
    assertFalse(File(File(workDir, "staging"), stray).exists())
    assertEquals(3, prepared.summary.counts.photos)
  }

  @Test
  fun discardingRemovesTheStaging() = runBlocking {
    seedEverything(context, db, photosDir, settings)
    exportTo("valid.zip")
    val manager = manager()
    val prepared = (manager.prepareImport(uri("valid.zip"), {}) as PrepareResult.Ready).import
    assertTrue(File(workDir, "staging").isDirectory)

    manager.discard(prepared)

    val deadline = System.currentTimeMillis() + 5_000
    while (File(workDir, "staging").exists() && System.currentTimeMillis() < deadline) Thread.sleep(25)
    assertFalse(File(workDir, "staging").exists())
    // Descartar dos veces no hace nada.
    manager.discard(prepared)
    assertNotNull(manager)
  }

  @Test
  fun preparingAnotherBackupReplacesTheStagingOfThePreviousOne() = runBlocking {
    val seeded = seedEverything(context, db, photosDir, settings)
    exportTo("valid.zip")
    val manager = manager()
    val first = (manager.prepareImport(uri("valid.zip"), {}) as PrepareResult.Ready).import
    val second = (manager.prepareImport(uri("valid.zip"), {}) as PrepareResult.Ready).import
    // Descartar la primera, que ya no es la vigente, no borra el staging de la segunda.
    manager.discard(first)
    Thread.sleep(300)
    assertEquals(seeded.photoNames.size * 2, File(workDir, "staging").list()!!.size)
    assertNotNull(second)
  }

  /** Un `OutputStream` que falla al pasar de [failAfter] bytes, como un proveedor sin espacio o sin conexión. */
  private class FailingOutput(private val target: OutputStream, private val failAfter: Int) : OutputStream() {
    private var written = 0

    override fun write(b: Int) {
      check1(1)
      target.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      check1(len)
      target.write(b, off, len)
    }

    override fun close() = target.close()

    private fun check1(n: Int) {
      written += n
      if (written > failAfter) throw IOException("Sin espacio")
    }
  }

  private companion object {
    const val PREFS = "settings_test_backup_manager"
  }
}
