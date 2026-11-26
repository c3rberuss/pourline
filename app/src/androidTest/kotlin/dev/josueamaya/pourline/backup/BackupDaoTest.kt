package dev.josueamaya.pourline.backup

import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.coffee.CoffeeRepository
import dev.josueamaya.pourline.coffee.photo.CoffeePhotoStore
import dev.josueamaya.pourline.history.BrewCurveCodec
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Room en memoria: ni el `history.db` real, ni las fotos ni los ajustes reales. */
class BackupDaoTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var settings: BackupTestSettings
  private lateinit var root: File
  private val dao get() = db.backupDao()

  @Before
  fun setUp() {
    root = File(context.cacheDir, "backup-dao-test").apply {
      deleteRecursively()
      mkdirs()
    }
    db = HistoryDatabase.inMemory(context)
    settings = BackupTestSettings(context, "settings_test_backup_dao")
  }

  @After
  fun tearDown() {
    db.close()
    settings.delete()
    root.deleteRecursively()
  }

  @Test
  fun readAllReturnsEveryRowOrderedByKey() = runBlocking {
    val seeded = seedEverything(context, db, File(root, "photos"), settings)
    val rows = dao.readAll()

    assertEquals(seeded.brewIds.sorted(), rows.brews.map { it.id })
    assertEquals(seeded.brewIds.sorted(), rows.brewCurves.map { it.brewId })
    assertEquals(seeded.coffeeIds.sorted(), rows.coffees.map { it.id })
    assertEquals(seeded.grinderIds.sorted(), rows.grinders.map { it.id })
    assertEquals(2, rows.grindCalibrations.size)
    assertTrue(rows.brewTargets.isNotEmpty())
    // Las dos recetas guardadas: una base nueva ya no trae recetas de fábrica.
    assertEquals(2, rows.recipes.size)
    assertEquals(rows.recipes.map { it.id }.sorted(), rows.recipes.map { it.id })
    assertEquals(rows.recipeStages.sortedWith(compareBy({ it.recipeId }, { it.position })), rows.recipeStages)
    assertEquals(rows.grinderUnits.sortedWith(compareBy({ it.grinderId }, { it.position })), rows.grinderUnits)
    assertTrue(rows.grinderMethodRanges.isNotEmpty() && rows.grinderAnchors.isNotEmpty())
    // Las curvas son BLOB reales: se pueden decodificar.
    rows.brewCurves.forEach { assertTrue(BrewCurveCodec.decode(it.data).first.isNotEmpty() || it.data.isNotEmpty()) }
    assertEquals(seeded.photoNames.toSet(), referencedPhotos(rows))
    // Todo lo leído pasa la validación de una copia.
    assertEquals(null, validate(rows))
  }

  @Test
  fun replaceAllLeavesExactlyTheGivenRowsWithTheirIds() = runBlocking {
    seedEverything(context, db, File(root, "photos"), settings)
    val source = dao.readAll()

    // Otra base con otros datos: tras reemplazar, solo queda lo de la copia.
    val other = HistoryDatabase.inMemory(context)
    try {
      seedSomethingElse(other)
      other.backupDao().replaceAll(source)
      assertRowsEqual(source, other.backupDao().readAll())
      assertTrue(other.backupDao().readAll().recipes.none { it.builtInKey != null })
      // Los ids se conservan: el siguiente alta no los pisa.
      val next = RecipeRepository(other, ResourcesRecipeTexts(context)).save(dev.josueamaya.pourline.recipe.aeroPressWithActions())
      assertTrue(next > source.recipes.maxOf { it.id })
    } finally {
      other.close()
    }
  }

  @Test
  fun replaceAllWithNothingEmptiesEveryTable() = runBlocking {
    seedEverything(context, db, File(root, "photos"), settings)
    dao.replaceAll(BackupRows())
    assertRowsEqual(BackupRows(), dao.readAll())
  }

  @Test
  fun replaceAllNotifiesTheObservers() = runBlocking {
    seedEverything(context, db, File(root, "photos"), settings)
    val source = dao.readAll()
    val other = HistoryDatabase.inMemory(context)
    try {
      val photos = CoffeePhotoStore(File(root, "other-photos"), File(root, "cache"))
      val recipes = RecipeRepository(other, ResourcesRecipeTexts(context))
      val coffees = CoffeeRepository(other, photos)
      val brews = BrewRepository(other)
      // Antes: nada de nada.
      assertEquals(0, withTimeout(5_000) { recipes.list().first() }.size)
      assertEquals(0, withTimeout(5_000) { coffees.pickable().first() }.size)
      assertEquals(0, withTimeout(5_000) { brews.count().first() })

      other.backupDao().replaceAll(source)

      assertEquals(source.recipes.size, withTimeout(5_000) { recipes.list().first { it.size == source.recipes.size } }.size)
      assertEquals(source.coffees.count { !it.archived }, withTimeout(5_000) { coffees.pickable().first { it.isNotEmpty() } }.size)
      assertEquals(source.brews.size, withTimeout(5_000) { brews.count().first { it == source.brews.size } })
    } finally {
      other.close()
    }
  }

  @Test
  fun aFailingInsertRollsEverythingBack() = runBlocking {
    seedEverything(context, db, File(root, "photos"), settings)
    val before = dao.readAll()
    val source = before.let { rows ->
      rows.copy(coffees = rows.coffees.map { it.copy(name = "Otro ${it.name}") })
    }
    assertTrue(source.brewTargets.isNotEmpty())
    // La última tabla falla dentro de la transacción: es el punto más tardío posible.
    db.openHelper.writableDatabase.execSQL(
      "CREATE TRIGGER backup_test_fail BEFORE INSERT ON brew_targets BEGIN SELECT RAISE(ABORT, 'test'); END",
    )

    assertThrows(Exception::class.java) { runBlocking { dao.replaceAll(source) } }

    assertRowsEqual(before, dao.readAll())
  }

  @Test
  fun theFingerprintFollowsCountsMaxIdsAndPhotoNames() = runBlocking {
    val seeded = seedEverything(context, db, File(root, "photos"), settings)
    val rows = dao.readAll()
    val photoNames = db.coffeeDao().photoNames()

    val actual = dao.fingerprintRow().fingerprint(photoNames)
    assertEquals(rows.fingerprint(), actual)
    assertEquals(listOf(rows.brews.size, rows.brewCurves.size, rows.brewTargets.size, rows.recipes.size), actual.counts.take(4))
    assertEquals(
      listOf(seeded.brewIds.max(), rows.recipes.maxOf { it.id }, seeded.coffeeIds.max(), seeded.grinderIds.max(), rows.grindCalibrations.maxOf { it.id }),
      actual.maxIds,
    )
    assertEquals(seeded.photoNames.sorted(), actual.photoNames)

    // Una tabla vacía: recuentos a cero e ids máximos a cero.
    dao.replaceAll(BackupRows())
    val empty = dao.fingerprintRow().fingerprint(db.coffeeDao().photoNames())
    assertEquals(BackupRows().fingerprint(), empty)
    assertEquals(List(11) { 0 }, empty.counts)
    assertEquals(List(5) { 0L }, empty.maxIds)
    // La huella cambia al importar otra cosa.
    dao.replaceAll(rows)
    assertEquals(rows.fingerprint(), dao.fingerprintRow().fingerprint(db.coffeeDao().photoNames()))
    assertFalse(rows.fingerprint() == empty)
    assertNotNull(seeded)
  }
}
