package dev.josueamaya.pourline.backup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.brew.TargetConfig
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.brew.brewFit
import dev.josueamaya.pourline.coffee.CoffeeRepository
import dev.josueamaya.pourline.coffee.photo.CoffeePhotoStore
import dev.josueamaya.pourline.coffee.photo.thumbNameOf
import dev.josueamaya.pourline.grinder.GrinderCatalogSeeder
import dev.josueamaya.pourline.grinder.TestGrinderCatalog
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.history.ReferenceType
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.history.UsedCoffee
import dev.josueamaya.pourline.history.UsedTarget
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import dev.josueamaya.pourline.recipe.aeroPressWithActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.random.Random

/**
 * El criterio 8 de SPEC-011 §5 con un caso grande (1000 preparaciones de 4 min con curva objetivo, 100 cafés con una foto
 * de 1600 x 1200, 20 recetas) en bases aparte (`backup-perf-*.db`), con sus propios directorios y preferencias. Pensado
 * para el emulador, como referencia: nunca se ejecuta en un teléfono con datos.
 */
class BackupPerformanceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val root = File(context.cacheDir, "backup-perf")
  private val srcPhotos = File(root, "src-photos")
  private val dstPhotos = File(root, "dst-photos")
  private lateinit var src: HistoryDatabase
  private lateinit var dst: HistoryDatabase
  private lateinit var srcSettings: BackupTestSettings
  private lateinit var dstSettings: BackupTestSettings
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @Before
  fun setUp() {
    cleanUp()
    root.mkdirs()
    srcSettings = BackupTestSettings(context, "settings_test_backup_perf_src")
    dstSettings = BackupTestSettings(context, "settings_test_backup_perf_dst")
    src = HistoryDatabase.create(context, SRC_DB)
    dst = HistoryDatabase.create(context, DST_DB)
  }

  @After
  fun tearDown() {
    scope.cancel()
    src.close()
    dst.close()
    cleanUp()
  }

  private fun cleanUp() {
    context.deleteDatabase(SRC_DB)
    context.deleteDatabase(DST_DB)
    root.deleteRecursively()
    BackupTestSettings(context, "settings_test_backup_perf_src").delete()
    BackupTestSettings(context, "settings_test_backup_perf_dst").delete()
  }

  private fun manager(db: HistoryDatabase, photos: File, work: File, settings: BackupTestSettings) = BackupManager(
    db = db,
    photosDir = photos,
    workDir = work,
    settings = settings.all,
    seeder = GrinderCatalogSeeder(db.grinderDao(), { TestGrinderCatalog.JSON }, settings.grinder, settings.grinder),
    streams = ContentResolverStreams(context.contentResolver),
    thumbnails = ThumbnailMaker(CoffeePhotoStore(photos, root)::regenerateThumbnail),
    appVersion = AppVersion("1.0-perf", 1),
    scope = scope,
    invalidateCaches = {},
  )

  /** Un JPEG de 1600 x 1200 con ruido, para que no se comprima de más: el peor caso de tamaño. */
  private fun writeNoisyJpeg(file: File, width: Int, height: Int) {
    val random = Random(7)
    val pixels = IntArray(width * height) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
    val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    bitmap.recycle()
  }

  private fun seedSource() = runBlocking {
    srcPhotos.mkdirs()
    val mainSource = File(root, "main-source.jpg").also { writeNoisyJpeg(it, 1600, 1200) }
    val thumbSource = File(root, "thumb-source.jpg").also { writeNoisyJpeg(it, 256, 256) }
    val store = CoffeePhotoStore(srcPhotos, root)
    val coffees = CoffeeRepository(src, store)
    val coffeeIds = (0 until COFFEES).map { i ->
      val name = "${UUID.randomUUID()}.jpg"
      mainSource.copyTo(File(srcPhotos, name))
      thumbSource.copyTo(File(srcPhotos, thumbNameOf(name)))
      coffees.save(TestBrews.coffeeDraft(name = "Café $i", photo1 = name))
    }
    val recipes = RecipeRepository(src, ResourcesRecipeTexts(context))
    repeat(RECIPES - 2) { recipes.save(aeroPressWithActions().copy(name = "Receta $it")) }
    val brews = BrewRepository(src)
    repeat(BREWS) { i ->
      val finished = TestBrews.finished(totalMs = 240_000, seed = i.toLong())
      val curve = TargetCurve.fromBrew(finished.samples, finished.summary.pours, finished.summary.totalTimeMs)!!
      val target = UsedTarget(ReferenceType.BREW, i + 1L, "Referencia $i", curve, brewFit(finished.samples, finished.summary.totalTimeMs, curve, TargetConfig()))
      val coffeeId = coffeeIds[i % COFFEES]
      brews.save(
        finished,
        TestBrews.context(startedAtEpochMs = 1_700_000_000_000 + i * 3_600_000L, target = target, coffee = UsedCoffee(coffeeId, "Café ${i % COFFEES}")),
      )
    }
    mainSource.delete()
    thumbSource.delete()
  }

  @Test
  fun theLargeDataSetExportsAndImportsWithinTheLimits() = runBlocking {
    seedSource()
    val srcManager = manager(src, srcPhotos, File(root, "src-work"), srcSettings)
    val dstManager = manager(dst, dstPhotos, File(root, "dst-work"), dstSettings)
    val zip = File(root, "perf.zip")

    val exportStart = System.nanoTime()
    val export = srcManager.export(Uri.fromFile(zip), {})
    val exportMs = (System.nanoTime() - exportStart) / 1_000_000
    assertTrue(export.toString(), export is ExportResult.Success)

    val readStart = System.nanoTime()
    val prepared = (dstManager.prepareImport(Uri.fromFile(zip), {}) as PrepareResult.Ready).import
    val readMs = (System.nanoTime() - readStart) / 1_000_000

    val applyStart = System.nanoTime()
    val applied = dstManager.apply(prepared) {}
    val applyMs = (System.nanoTime() - applyStart) / 1_000_000
    assertTrue(applied.toString(), applied is ImportResult.Success)

    val (dataBytes, totalBytes) = ZipFile(zip).use { archive ->
      val entries = archive.entries().toList()
      archive.getEntry("data.json").size to entries.sumOf { it.size }
    }
    val runtime = Runtime.getRuntime()
    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val report = "exportar=$exportMs ms; leer=$readMs ms; aplicar=$applyMs ms; zip=${zip.length()} B; data.json=$dataBytes B; " +
      "descomprimido=$totalBytes B; memoria Java tras importar=$usedMb MB"
    Log.i(TAG, report)
    println("$TAG: $report")

    assertEquals(BREWS, dst.backupDao().readAll().brews.size)
    assertEquals(COFFEES, dst.backupDao().readAll().coffees.size)
    assertEquals(RECIPES, dst.backupDao().readAll().recipes.size)
    assertEquals(COFFEES * 2, dstPhotos.list()!!.size)
    assertTrue("data.json debe quedar por debajo de 16 MiB (D11): $dataBytes", dataBytes < 16L * 1024 * 1024)
    assertTrue("exportar: $report", exportMs < LIMIT_MS)
    assertTrue("leer: $report", readMs < LIMIT_MS)
    assertTrue("aplicar: $report", applyMs < LIMIT_MS)
  }

  private companion object {
    const val TAG = "BackupPerf"
    const val SRC_DB = "backup-perf-src.db"
    const val DST_DB = "backup-perf-dst.db"
    const val BREWS = 1000
    const val COFFEES = 100
    const val RECIPES = 20
    const val LIMIT_MS = 15_000L
  }
}
