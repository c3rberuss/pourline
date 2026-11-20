package dev.josueamaya.pourline.backup

import dev.josueamaya.pourline.coffee.CoffeeConverters
import dev.josueamaya.pourline.coffee.CoffeeEntity
import dev.josueamaya.pourline.grinder.GrindCalibrationEntity
import dev.josueamaya.pourline.grinder.GrinderAnchorEntity
import dev.josueamaya.pourline.grinder.GrinderConverters
import dev.josueamaya.pourline.grinder.GrinderEntity
import dev.josueamaya.pourline.grinder.GrinderMethodRangeEntity
import dev.josueamaya.pourline.grinder.GrinderUnitEntity
import dev.josueamaya.pourline.history.BrewCurveEntity
import dev.josueamaya.pourline.history.BrewEntity
import dev.josueamaya.pourline.history.BrewTargetEntity
import dev.josueamaya.pourline.history.HistoryConverters
import dev.josueamaya.pourline.recipe.RecipeEntity
import dev.josueamaya.pourline.recipe.RecipeStageEntity
import dev.josueamaya.pourline.settings.SettingsSnapshot
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Entre las entidades de Room y los DTO del formato. Los enums salen y entran por nombre, con las reservas de los `TypeConverters`. */

private val historyConv = HistoryConverters()
private val coffeeConv = CoffeeConverters()
private val grinderConv = GrinderConverters()

internal fun BackupRows.toDto(): DataDto = DataDto(
  brews = brews.map { it.toDto() },
  brewCurves = brewCurves.map { BrewCurveDto(it.brewId, it.data) },
  brewTargets = brewTargets.map { BrewTargetDto(it.brewId, it.data) },
  recipes = recipes.map { it.toDto() },
  recipeStages = recipeStages.map { it.toDto() },
  coffees = coffees.map { it.toDto() },
  grinders = grinders.map { it.toDto() },
  grinderUnits = grinderUnits.map { GrinderUnitDto(it.grinderId, it.position, it.unit.name, it.perParent) },
  grinderMethodRanges = grinderMethodRanges.map { GrinderMethodRangeDto(it.grinderId, it.method.name, it.min, it.max) },
  grinderAnchors = grinderAnchors.map { GrinderAnchorDto(it.grinderId, it.level.name, it.value) },
  grindCalibrations = grindCalibrations.map { GrindCalibrationDto(it.id, it.grinderAId, it.valueA, it.grinderBId, it.valueB, it.updatedAtEpochMs) },
)

/** Lanza `BackupFailure(CORRUPT)` ante una fecha mal formada. */
internal fun DataDto.toRows(): BackupRows = BackupRows(
  brews = brews.map { it.toEntity() },
  brewCurves = brewCurves.map { BrewCurveEntity(it.brewId, it.data) },
  brewTargets = brewTargets.map { BrewTargetEntity(it.brewId, it.data) },
  recipes = recipes.map { it.toEntity() },
  recipeStages = recipeStages.map { it.toEntity() },
  coffees = coffees.map { it.toEntity() },
  grinders = grinders.map { it.toEntity() },
  grinderUnits = grinderUnits.map { GrinderUnitEntity(it.grinderId, it.position, grinderConv.stringToUnit(it.unit), it.perParent) },
  grinderMethodRanges = grinderMethodRanges.map { GrinderMethodRangeEntity(it.grinderId, historyConv.stringToBrewMethod(it.method), it.min, it.max) },
  grinderAnchors = grinderAnchors.map { GrinderAnchorEntity(it.grinderId, grinderConv.stringToLevel(it.level), it.value) },
  grindCalibrations = grindCalibrations.map { GrindCalibrationEntity(it.id, it.grinderAId, it.valueA, it.grinderBId, it.valueB, it.updatedAtEpochMs) },
)

internal fun SettingsSnapshot.toDto(): SettingsDto = SettingsDto(
  themeMode = themeMode,
  experienceMode = experienceMode,
  onboardingDone = onboardingDone,
  useScale = useScale,
  lastScaleAddress = lastScaleAddress,
  lastScaleName = lastScaleName,
  autoFinishSeconds = autoFinishSeconds,
  countdownSeconds = countdownSeconds,
  lastCoffeeId = lastCoffeeId,
  defaultDoseG = defaultDoseG,
  defaultRatio = defaultRatio,
  targetRefType = targetRefType,
  targetRefId = targetRefId,
  stageVibration = stageVibration,
  stageSound = stageSound,
  defaultGrinderId = defaultGrinderId,
  grinderCatalogVersion = grinderCatalogVersion,
)

internal fun SettingsDto.toSnapshot(): SettingsSnapshot = SettingsSnapshot(
  themeMode = themeMode,
  experienceMode = experienceMode,
  onboardingDone = onboardingDone,
  useScale = useScale,
  lastScaleAddress = lastScaleAddress,
  lastScaleName = lastScaleName,
  autoFinishSeconds = autoFinishSeconds,
  countdownSeconds = countdownSeconds,
  lastCoffeeId = lastCoffeeId,
  defaultDoseG = defaultDoseG,
  defaultRatio = defaultRatio,
  targetRefType = targetRefType,
  targetRefId = targetRefId,
  stageVibration = stageVibration,
  stageSound = stageSound,
  defaultGrinderId = defaultGrinderId,
  grinderCatalogVersion = grinderCatalogVersion,
)

/** Los recuentos del §3.1; [presentPhotos] son las fotos principales distintas que van en el archivo. */
internal fun BackupRows.counts(presentPhotos: Int): BackupCounts = BackupCounts(
  coffees = coffees.size,
  grinders = grinders.count { it.owned },
  grindCalibrations = grindCalibrations.size,
  recipes = recipes.size,
  brews = brews.size,
  photos = presentPhotos,
)

private fun BrewEntity.toDto() = BrewDto(
  id = id,
  startedAtEpochMs = startedAtEpochMs,
  name = name,
  coffee = coffee,
  method = method?.name,
  methodOther = methodOther,
  waterTempC = waterTempC,
  notes = notes,
  totalTimeMs = totalTimeMs,
  totalWaterG = totalWaterG,
  avgFlowGps = avgFlowGps,
  maxFlowGps = maxFlowGps,
  stabilityScore = stabilityScore,
  stabilityLevel = stabilityLevel?.name,
  shareStable = shareStable,
  shareIrregular = shareIrregular,
  shareTurbulent = shareTurbulent,
  doseG = doseG,
  finalRatio = finalRatio,
  finishReason = finishReason.name,
  interrupted = interrupted,
  unit = unit.name,
  scaleModel = scaleModel?.name,
  simulated = simulated,
  metricVersion = metricVersion,
  preview = preview,
  referenceType = referenceType?.name,
  referenceId = referenceId,
  referenceName = referenceName,
  targetWaterG = targetWaterG,
  targetEndMs = targetEndMs,
  fitScore = fitScore,
  coffeeId = coffeeId,
  rating = rating,
  grinderId = grinderId,
  grindValue = grindValue,
  grindLabel = grindLabel,
  scaleless = scaleless,
)

private fun BrewDto.toEntity() = BrewEntity(
  id = id,
  startedAtEpochMs = startedAtEpochMs,
  name = name,
  coffee = coffee,
  method = method?.let(historyConv::stringToBrewMethod),
  methodOther = methodOther,
  waterTempC = waterTempC,
  notes = notes,
  totalTimeMs = totalTimeMs,
  totalWaterG = totalWaterG,
  avgFlowGps = avgFlowGps,
  maxFlowGps = maxFlowGps,
  stabilityScore = stabilityScore,
  stabilityLevel = stabilityLevel?.let(historyConv::stringToStabilityLevel),
  shareStable = shareStable,
  shareIrregular = shareIrregular,
  shareTurbulent = shareTurbulent,
  doseG = doseG,
  finalRatio = finalRatio,
  finishReason = historyConv.stringToFinishReason(finishReason),
  interrupted = interrupted,
  unit = historyConv.stringToWeightUnit(unit),
  scaleModel = scaleModel?.let(historyConv::stringToScaleModel),
  simulated = simulated,
  metricVersion = metricVersion,
  preview = preview,
  referenceType = historyConv.stringToReferenceType(referenceType),
  referenceId = referenceId,
  referenceName = referenceName,
  targetWaterG = targetWaterG,
  targetEndMs = targetEndMs,
  fitScore = fitScore,
  coffeeId = coffeeId,
  rating = rating,
  grinderId = grinderId,
  grindValue = grindValue,
  grindLabel = grindLabel,
  scaleless = scaleless,
)

private fun RecipeEntity.toDto() = RecipeDto(
  id = id,
  name = name,
  method = method?.name,
  methodOther = methodOther,
  waterTempC = waterTempC,
  notes = notes,
  doseG = doseG,
  builtInKey = builtInKey,
  localizedNames = localizedNames,
  updatedAtEpochMs = updatedAtEpochMs,
  grinderId = grinderId,
  grindValue = grindValue,
  grindLabel = grindLabel,
)

private fun RecipeDto.toEntity() = RecipeEntity(
  id = id,
  name = name,
  method = method?.let(historyConv::stringToBrewMethod),
  methodOther = methodOther,
  waterTempC = waterTempC,
  notes = notes,
  doseG = doseG,
  builtInKey = builtInKey,
  localizedNames = localizedNames,
  updatedAtEpochMs = updatedAtEpochMs,
  grinderId = grinderId,
  grindValue = grindValue,
  grindLabel = grindLabel,
)

private fun RecipeStageEntity.toDto() = RecipeStageDto(recipeId, position, name, startMs, targetWaterG, durationMs, type.name, instruction)

private fun RecipeStageDto.toEntity() =
  RecipeStageEntity(recipeId, position, name, startMs, targetWaterG, durationMs, historyConv.stringToStageType(type), instruction)

private fun CoffeeEntity.toDto() = CoffeeDto(
  id = id,
  name = name,
  roaster = roaster,
  originCountry = originCountry,
  originRegion = originRegion,
  farm = farm,
  varieties = varieties,
  process = process?.name,
  processOther = processOther,
  altitudeMinM = altitudeMinM,
  altitudeMaxM = altitudeMaxM,
  roastLevel = roastLevel?.name,
  roastDate = roastDate?.toString(),
  roasterNotes = roasterNotes,
  bagWeightG = bagWeightG,
  price = price,
  openedDate = openedDate?.toString(),
  photo1 = photo1,
  photo2 = photo2,
  archived = archived,
  createdAtEpochMs = createdAtEpochMs,
  lastUsedAtEpochMs = lastUsedAtEpochMs,
)

private fun CoffeeDto.toEntity() = CoffeeEntity(
  id = id,
  name = name,
  roaster = roaster,
  originCountry = originCountry,
  originRegion = originRegion,
  farm = farm,
  varieties = varieties,
  process = coffeeConv.stringToProcess(process),
  processOther = processOther,
  altitudeMinM = altitudeMinM,
  altitudeMaxM = altitudeMaxM,
  roastLevel = coffeeConv.stringToRoastLevel(roastLevel),
  roastDate = parseDay(roastDate),
  roasterNotes = roasterNotes,
  bagWeightG = bagWeightG,
  price = price,
  openedDate = parseDay(openedDate),
  photo1 = photo1,
  photo2 = photo2,
  archived = archived,
  createdAtEpochMs = createdAtEpochMs,
  lastUsedAtEpochMs = lastUsedAtEpochMs,
)

private fun GrinderEntity.toDto() = GrinderDto(
  id = id,
  catalogKey = catalogKey,
  brand = brand,
  model = model,
  direction = direction.name,
  scaleMin = scaleMin,
  scaleMax = scaleMax,
  step = step,
  owned = owned,
  retired = retired,
  sourceUrl = sourceUrl,
  sourceName = sourceName,
  duplicatedFromKey = duplicatedFromKey,
  updatedAtEpochMs = updatedAtEpochMs,
)

private fun GrinderDto.toEntity() = GrinderEntity(
  id = id,
  catalogKey = catalogKey,
  brand = brand,
  model = model,
  direction = grinderConv.stringToDirection(direction),
  scaleMin = scaleMin,
  scaleMax = scaleMax,
  step = step,
  owned = owned,
  retired = retired,
  sourceUrl = sourceUrl,
  sourceName = sourceName,
  duplicatedFromKey = duplicatedFromKey,
  updatedAtEpochMs = updatedAtEpochMs,
)

private fun parseDay(value: String?): LocalDate? =
  try {
    value?.let(LocalDate::parse)
  } catch (e: DateTimeParseException) {
    throw BackupFailure(BackupError.CORRUPT, e)
  }
