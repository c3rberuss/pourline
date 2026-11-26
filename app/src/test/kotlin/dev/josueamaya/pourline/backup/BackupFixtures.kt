package dev.josueamaya.pourline.backup

import dev.josueamaya.pourline.brew.BrewSample
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.brew.Pour
import dev.josueamaya.pourline.brew.StabilityLevel
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.brew.TargetPoint
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.coffee.CoffeeEntity
import dev.josueamaya.pourline.coffee.CoffeeProcess
import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.grinder.GrindCalibrationEntity
import dev.josueamaya.pourline.grinder.GrindDirection
import dev.josueamaya.pourline.grinder.GrindLevel
import dev.josueamaya.pourline.grinder.GrindUnit
import dev.josueamaya.pourline.grinder.GrinderAnchorEntity
import dev.josueamaya.pourline.grinder.GrinderEntity
import dev.josueamaya.pourline.grinder.GrinderMethodRangeEntity
import dev.josueamaya.pourline.grinder.GrinderUnitEntity
import dev.josueamaya.pourline.history.BrewCurveCodec
import dev.josueamaya.pourline.history.BrewCurveEntity
import dev.josueamaya.pourline.history.BrewEntity
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.BrewTargetEntity
import dev.josueamaya.pourline.history.ReferenceType
import dev.josueamaya.pourline.history.TargetCurveCodec
import dev.josueamaya.pourline.protocol.ScaleModel
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.RecipeEntity
import dev.josueamaya.pourline.recipe.RecipeStageEntity
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.settings.SettingsSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import java.time.LocalDate

/** Las fotos del café completo y del que solo tiene `photo2`. */
internal const val PHOTO_A = "11111111-1111-4111-8111-111111111111.jpg"
internal const val PHOTO_B = "22222222-2222-4222-8222-222222222222.jpg"
internal const val PHOTO_C = "33333333-3333-4333-8333-333333333333.jpg"

private val NO_BYTES = ByteArray(0)

/** Una curva real, con sus vertidos, para que el Base64 lleve bytes de verdad. */
private fun realCurve(seed: Int): ByteArray {
  val samples = (0..40).map { i ->
    BrewSample(tMs = i * 1_000L, waterG = i * 5.0 + seed, flowGps = 4.5 + seed / 10.0, stability = if (i % 7 == 0) null else 60 + i, valid = i % 11 != 0)
  }
  return BrewCurveCodec.encode(samples, listOf(Pour(0, 0, 20_000, 100.0 + seed), Pour(1, 30_000, 40_000, 200.0 + seed)))
}

private fun realTarget(): ByteArray {
  val curve = TargetCurve(
    points = listOf(TargetPoint(0, 0.0), TargetPoint(30_000, 60.0), TargetPoint(90_000, 250.0)),
    stages = listOf(
      TargetStage(0, "Bloom", 0, 30_000, 60.0),
      TargetStage(1, null, 45_000, 90_000, 250.0),
    ),
  )
  return TargetCurveCodec.encode(curve)
}

/**
 * Al menos dos filas por tabla: una con todos los campos opcionales y otra con todos a `null` donde se puede. Filas
 * ordenadas por clave primaria, como las devuelve el DAO.
 */
internal fun fullRows(): BackupRows {
  val coffees = listOf(
    CoffeeEntity(
      id = 1, name = "Finca La Esperanza", roaster = "Tostadores", originCountry = "Colombia", originRegion = "Huila", farm = "La Esperanza",
      varieties = listOf("Caturra", "Castillo"), process = CoffeeProcess.NATURAL, processOther = null, altitudeMinM = 1600, altitudeMaxM = 1900,
      roastLevel = RoastLevel.LIGHT, roastDate = LocalDate.of(2026, 10, 30), roasterNotes = "Fruta roja\ny cacao", bagWeightG = 250.0, price = 14.5,
      openedDate = LocalDate.of(2026, 11, 2), photo1 = PHOTO_A, photo2 = PHOTO_B, archived = true, createdAtEpochMs = 1_700_000_000_000,
      lastUsedAtEpochMs = 1_700_000_500_000,
    ),
    CoffeeEntity(id = 2, name = "Solo una foto", photo2 = PHOTO_C, createdAtEpochMs = 1_700_001_000_000),
  )
  val grinders = listOf(
    GrinderEntity(
      id = 1, catalogKey = null, brand = "Mi marca", model = "Mi molino", direction = GrindDirection.HIGHER_IS_FINER, scaleMin = 0.0, scaleMax = 90.0,
      step = 0.5, owned = true, retired = false, sourceUrl = "https://example.com/m", sourceName = "Ejemplo", duplicatedFromKey = "timemore-c2",
      updatedAtEpochMs = 1_700_000_000_000,
    ),
    GrinderEntity(
      id = 2, catalogKey = "timemore-c2", brand = "TIMEMORE", model = "C2", direction = GrindDirection.HIGHER_IS_COARSER, scaleMin = 0.0,
      scaleMax = 36.0, step = 1.0, owned = false, retired = true, updatedAtEpochMs = 1_700_000_100_000,
    ),
  )
  val recipes = listOf(
    RecipeEntity(
      id = 1, name = "V60 clásica", method = BrewMethod.V60, methodOther = "con filtro", waterTempC = 94.5, notes = "Notas de la receta", doseG = 15.0,
      builtInKey = "V60_CLASSIC", localizedNames = true, updatedAtEpochMs = 1_700_000_000_000, grinderId = 1, grindValue = 20.5, grindLabel = "Mi molino 20,5",
    ),
    RecipeEntity(
      id = 2, name = "Sin nada", doseG = 18.0, updatedAtEpochMs = 1_700_000_200_000, author = "Ana", sourceUrl = "https://example.com/r",
      catalogKey = "v60_basica",
    ),
  )
  val stages = listOf(
    RecipeStageEntity(1, 0, "Bloom", 0, 45.0, 30_000, StageType.POUR, null),
    RecipeStageEntity(1, 1, null, 30_000, 45.0, 5_000, StageType.SWIRL, "Remueve"),
    RecipeStageEntity(1, 2, "Espera", 35_000, 45.0, 10_000, StageType.WAIT, null),
    RecipeStageEntity(1, 3, "Presiona", 45_000, 45.0, 15_000, StageType.ACTION, "Presiona despacio"),
    RecipeStageEntity(2, 0, null, 0, 250.0, 60_000, StageType.POUR, null),
  )
  val brews = listOf(
    BrewEntity(
      id = 1, startedAtEpochMs = 1_700_000_000_000, name = "La del domingo", coffee = "Finca La Esperanza", method = BrewMethod.V60, methodOther = "x",
      waterTempC = 93.0, notes = "Dulce", totalTimeMs = 180_000, totalWaterG = 250.5, avgFlowGps = 4.2, maxFlowGps = 7.9, stabilityScore = 82,
      stabilityLevel = StabilityLevel.STABLE, shareStable = 0.7, shareIrregular = 0.2, shareTurbulent = 0.1, doseG = 15.0, finalRatio = 16.7,
      finishReason = FinishReason.INACTIVITY, interrupted = true, unit = WeightUnit.OUNCES, scaleModel = ScaleModel.DOT, simulated = true, metricVersion = 2,
      preview = ByteArray(48) { it.toByte() }, referenceType = ReferenceType.RECIPE, referenceId = 1, referenceName = "V60 clásica", targetWaterG = 250.0,
      targetEndMs = 90_000, fitScore = 91, coffeeId = 1, rating = 5, grinderId = 1, grindValue = 20.5, grindLabel = "Mi molino 20,5", scaleless = false,
    ),
    BrewEntity(
      id = 2, startedAtEpochMs = 1_700_100_000_000, totalTimeMs = 120_000, totalWaterG = 200.0, avgFlowGps = 3.0, maxFlowGps = 5.0, stabilityScore = null,
      stabilityLevel = null, shareStable = 0.0, shareIrregular = 0.0, shareTurbulent = 0.0, doseG = null, finalRatio = null, finishReason = FinishReason.MANUAL,
      interrupted = false, unit = WeightUnit.GRAMS, scaleModel = null, simulated = false, metricVersion = 1, preview = ByteArray(48),
      referenceType = ReferenceType.CATALOG, referenceKey = "v60_basica", referenceName = "V60 recomendada",
    ),
    BrewEntity(
      id = 3, startedAtEpochMs = 1_700_200_000_000, totalTimeMs = 90_000, totalWaterG = 150.0, avgFlowGps = 0.0, maxFlowGps = 0.0, stabilityScore = null,
      stabilityLevel = null, shareStable = 0.0, shareIrregular = 0.0, shareTurbulent = 0.0, doseG = 10.0, finalRatio = 15.0, finishReason = FinishReason.MANUAL,
      interrupted = false, unit = WeightUnit.GRAMS, scaleModel = null, simulated = false, metricVersion = 1, preview = ByteArray(48) { 255.toByte() },
      referenceType = ReferenceType.BREW, referenceId = 99, scaleless = true,
    ),
  )
  return BackupRows(
    brews = brews,
    brewCurves = listOf(BrewCurveEntity(1, realCurve(1)), BrewCurveEntity(2, realCurve(2)), BrewCurveEntity(3, ByteArray(0))),
    brewTargets = listOf(BrewTargetEntity(1, realTarget()), BrewTargetEntity(2, realTarget())),
    recipes = recipes,
    recipeStages = stages,
    coffees = coffees,
    grinders = grinders,
    grinderUnits = listOf(
      GrinderUnitEntity(1, 0, GrindUnit.ROTATIONS, null),
      GrinderUnitEntity(1, 1, GrindUnit.CLICKS, 24),
      GrinderUnitEntity(2, 0, GrindUnit.CLICKS, null),
    ),
    grinderMethodRanges = listOf(
      GrinderMethodRangeEntity(1, BrewMethod.ESPRESSO, 2.0, 8.0),
      GrinderMethodRangeEntity(1, BrewMethod.V60, 40.0, 60.0),
      GrinderMethodRangeEntity(2, BrewMethod.V60, 18.0, 24.0),
    ),
    grinderAnchors = listOf(
      GrinderAnchorEntity(1, GrindLevel.FINE, 10.0),
      GrinderAnchorEntity(1, GrindLevel.COARSE, 70.0),
      GrinderAnchorEntity(2, GrindLevel.MEDIUM, 20.0),
    ),
    grindCalibrations = listOf(
      GrindCalibrationEntity(1, 1, 10.0, 2, 20.0, 1_700_000_000_000),
      GrindCalibrationEntity(2, 1, 12.5, 2, 25.0, 1_700_000_100_000),
    ),
  )
}

/** Todas las claves de ajustes presentes, con sus tipos guardados. */
internal fun fullSettings() = SettingsSnapshot(
  themeMode = "DARK",
  experienceMode = "EXPERT",
  onboardingDone = true,
  useScale = false,
  lastScaleAddress = "AA:BB:CC:DD:EE:FF",
  lastScaleName = "TIMEMORE DOT",
  autoFinishSeconds = 90,
  countdownSeconds = 5,
  lastCoffeeId = 1,
  defaultDoseG = 15.3f,
  defaultRatio = 16.5f,
  targetRefType = "RECIPE",
  targetRefId = 1,
  targetRefKey = "v60_basica",
  stageVibration = false,
  stageSound = true,
  defaultGrinderId = 1,
  grinderCatalogVersion = 2,
)

/** Las filas iguales campo a campo; los `ByteArray` por contenido, que `equals` de las entidades no hace. */
internal fun assertRowsEqual(expected: BackupRows, actual: BackupRows) {
  assertEquals(expected.brews.map { it.copy(preview = NO_BYTES) }, actual.brews.map { it.copy(preview = NO_BYTES) })
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
