package dev.josueamaya.pourline.backup

import android.content.Context
import dev.josueamaya.pourline.brew.PlannedAmounts
import dev.josueamaya.pourline.coffee.CoffeeRepository
import dev.josueamaya.pourline.coffee.label.SyntheticLabel
import dev.josueamaya.pourline.coffee.photo.CoffeePhotoStore
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addCalibration
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.history.UsedCoffee
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import dev.josueamaya.pourline.recipe.aeroPressWithActions
import dev.josueamaya.pourline.session.RememberedScale
import dev.josueamaya.pourline.settings.BackupableSettings
import dev.josueamaya.pourline.settings.BrewSettings
import dev.josueamaya.pourline.settings.ConnectionSettings
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.ExperienceSettings
import dev.josueamaya.pourline.settings.GrinderSettings
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.settings.SettingsSnapshot
import dev.josueamaya.pourline.settings.TargetSettings
import dev.josueamaya.pourline.settings.ThemeMode
import dev.josueamaya.pourline.settings.ThemeSettings
import dev.josueamaya.pourline.settings.TourSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import java.io.File

/** El archivo de preferencias de los tests: nunca el `settings.xml` real. */
internal const val BACKUP_TEST_PREFS = "settings_test_backup"

/** Las siete clases de ajustes sobre las mismas preferencias de prueba. */
internal class BackupTestSettings(private val context: Context, private val prefsName: String = BACKUP_TEST_PREFS) {
  val theme = ThemeSettings(context, prefsName)
  val experience = ExperienceSettings(context, prefsName)
  val connection = ConnectionSettings(context, prefsName)
  val brew = BrewSettings(context, prefsName)
  val target = TargetSettings(context, prefsName)
  val grinder = GrinderSettings(context, prefsName)
  val tour = TourSettings(context, prefsName)

  /** Las que viajan en la copia (`TourSettings` no). */
  val all: List<BackupableSettings> = listOf(theme, experience, connection, brew, target, grinder)

  fun snapshot(): SettingsSnapshot = all.fold(SettingsSnapshot()) { snapshot, settings -> settings.exportInto(snapshot) }

  /** Borra el archivo de preferencias: las instancias creadas antes no se deben usar después. */
  fun delete() {
    context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    context.deleteSharedPreferences(prefsName)
  }
}

/** Lo que sembró [seedEverything], para que los tests comprueben sin releer. */
internal class Seeded(
  val coffeeIds: List<Long>,
  val grinderIds: List<Long>,
  val brewIds: List<Long>,
  val recipeId: Long,
  /** Los nombres principales de las fotos sembradas. */
  val photoNames: List<String>,
)

/**
 * Datos en las 11 tablas, tres fotos con su miniatura y todos los ajustes del §3.2, sembrados por los DAO y los
 * repositorios reales. La base en memoria ya trae las recetas predefinidas.
 */
internal suspend fun seedEverything(
  context: Context,
  db: HistoryDatabase,
  photosDir: File,
  settings: BackupTestSettings,
): Seeded {
  val store = CoffeePhotoStore(photosDir, File(context.cacheDir, "backup-test-camera"))
  val source = File(context.cacheDir, "backup-test-source.jpg").also { SyntheticLabel.writeJpeg(it) }
  val photos = (1..3).map { store.import { source.inputStream() } }
  source.delete()

  val coffees = CoffeeRepository(db, store)
  val coffeeA = coffees.save(TestBrews.coffeeDraft(name = "Etiopía Guji", photo1 = photos[0], photo2 = photos[1]))
  val coffeeB = coffees.save(TestBrews.coffeeDraft(name = "Solo segunda foto", roaster = null, roastDate = null, bagWeightG = null, photo2 = photos[2]))
  val coffeeC = coffees.save(TestBrews.coffeeDraft(name = "Sin fotos", photo1 = null, photo2 = null))

  val uno = db.addGrinder(TestGrinders.uno)
  val tres = db.addGrinder(TestGrinders.tres)
  val catalog = db.addGrinder(TestGrinders.dos.copy(catalogKey = "acme-dos", owned = false))
  db.addCalibration(uno, 20.0, tres, 100.0)
  db.addCalibration(uno, 24.0, tres, 120.0)

  val recipes = RecipeRepository(db, ResourcesRecipeTexts(context))
  val recipeId = recipes.save(aeroPressWithActions().copy(grind = GrindSetting(uno, 18.0, "Acme Uno 18")))
  recipes.save(aeroPressWithActions().copy(name = "Sin molino"))

  val brews = BrewRepository(db)
  val first = brews.save(
    TestBrews.finished(totalMs = 60_000, seed = 1),
    TestBrews.context(
      startedAtEpochMs = 1_700_000_000_000,
      target = TestBrews.usedTarget(id = recipeId),
      coffee = UsedCoffee(coffeeA, "Etiopía Guji"),
      details = BrewDetails(name = "Domingo", method = BrewMethod.V60, grind = GrindSetting(uno, 18.0, "Acme Uno 18"), notes = "Dulce", rating = 5),
    ),
  )
  val second = brews.save(TestBrews.scalelessFinished(), TestBrews.scalelessContext(startedAtEpochMs = 1_700_100_000_000))
  val third = brews.save(TestBrews.finished(totalMs = 45_000, seed = 3), TestBrews.context(startedAtEpochMs = 1_700_200_000_000, simulated = true))

  settings.theme.setMode(ThemeMode.DARK)
  settings.experience.completeOnboarding(ExperienceMode.EXPERT)
  settings.connection.remember(RememberedScale("AA:BB:CC:DD:EE:FF", "TIMEMORE DOT"))
  settings.connection.setUseScale(false)
  settings.brew.setAutoFinishSeconds(90)
  settings.brew.setCountdownSeconds(5)
  settings.brew.setLastCoffeeId(coffeeA)
  settings.brew.setDefaultDoseG(18.5)
  settings.brew.setDefaultRatio(15.5)
  settings.target.setReference(ReferenceKey.Recipe(recipeId))
  settings.target.setStageVibration(false)
  settings.target.setStageSound(true)
  settings.grinder.setDefaultGrinderId(uno)
  settings.grinder.setCatalogVersion(2)
  settings.tour.markSeen(listOf("prepare.settings", "prepare.start"))

  return Seeded(
    coffeeIds = listOf(coffeeA, coffeeB, coffeeC),
    grinderIds = listOf(uno, tres, catalog),
    brewIds = listOf(first, second, third),
    recipeId = recipeId,
    photoNames = photos,
  )
}

/** Las filas iguales campo a campo; los `ByteArray` por contenido, que `equals` de las entidades no hace. */
internal fun assertRowsEqual(expected: BackupRows, actual: BackupRows) {
  val none = ByteArray(0)
  assertEquals(expected.brews.map { it.copy(preview = none) }, actual.brews.map { it.copy(preview = none) })
  expected.brews.zip(actual.brews).forEach { (e, a) -> assertArrayEquals("preview ${e.id}", e.preview, a.preview) }
  assertBlobs("brew_curves", expected.brewCurves.map { it.brewId to it.data }, actual.brewCurves.map { it.brewId to it.data })
  assertBlobs("brew_targets", expected.brewTargets.map { it.brewId to it.data }, actual.brewTargets.map { it.brewId to it.data })
  assertEquals(expected.recipes, actual.recipes)
  assertEquals(expected.recipeStages, actual.recipeStages)
  assertEquals(expected.coffees, actual.coffees)
  assertEquals(expected.grinders, actual.grinders)
  assertEquals(expected.grinderUnits, actual.grinderUnits)
  assertEquals(expected.grinderMethodRanges, actual.grinderMethodRanges)
  assertEquals(expected.grinderAnchors, actual.grinderAnchors)
  assertEquals(expected.grindCalibrations, actual.grindCalibrations)
}

private fun assertBlobs(table: String, expected: List<Pair<Long, ByteArray>>, actual: List<Pair<Long, ByteArray>>) {
  assertEquals("$table ids", expected.map { it.first }, actual.map { it.first })
  expected.zip(actual).forEach { (e, a) -> assertArrayEquals("$table ${e.first}", e.second, a.second) }
}

/** Nombre y contenido de cada archivo de [dir], para comparar fotos. */
internal fun readFiles(dir: File): Map<String, ByteArray> =
  dir.listFiles().orEmpty().filter { it.isFile }.associate { it.name to it.readBytes() }

internal fun assertFilesEqual(expected: Map<String, ByteArray>, actual: Map<String, ByteArray>) {
  assertEquals(expected.keys, actual.keys)
  expected.forEach { (name, bytes) -> assertArrayEquals(name, bytes, actual.getValue(name)) }
}

/** Una preparación sin báscula con cantidades propias, para bases que ya tienen otros datos. */
internal suspend fun seedSomethingElse(db: HistoryDatabase) {
  db.addGrinder(TestGrinders.dos)
  BrewRepository(db).save(TestBrews.scalelessFinished(amounts = PlannedAmounts(doseG = 20.0, waterG = 320.0)), TestBrews.scalelessContext())
}
