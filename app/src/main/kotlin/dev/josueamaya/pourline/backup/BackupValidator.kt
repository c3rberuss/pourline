package dev.josueamaya.pourline.backup

/**
 * Valida las filas de una copia antes de tocar nada (SPEC-011 §3.4): primero la estructura (ids, claves repetidas,
 * nombres de foto), que es `CORRUPT`, y después las claves foráneas, que son `BROKEN_REFERENCES`. `null` si todo está bien.
 */
internal fun validate(rows: BackupRows): BackupError? {
  if (!hasValidStructure(rows)) return BackupError.CORRUPT
  if (!hasValidReferences(rows)) return BackupError.BROKEN_REFERENCES
  return null
}

/** Los nombres de foto principales que referencian los cafés (`photo1` y `photo2`). */
internal fun referencedPhotos(rows: BackupRows): Set<String> =
  rows.coffees.flatMapTo(linkedSetOf()) { listOfNotNull(it.photo1, it.photo2) }

private fun hasValidStructure(rows: BackupRows): Boolean {
  // Un id 0 se insertaría con un id nuevo y rompería las referencias.
  val autoIds = listOf(
    rows.brews.map { it.id },
    rows.recipes.map { it.id },
    rows.coffees.map { it.id },
    rows.grinders.map { it.id },
    rows.grindCalibrations.map { it.id },
  )
  if (autoIds.any { ids -> ids.any { it <= 0 } }) return false

  val unique = listOf(
    rows.brews.map { it.id },
    rows.brewCurves.map { it.brewId },
    rows.brewTargets.map { it.brewId },
    rows.recipes.map { it.id },
    rows.recipeStages.map { it.recipeId to it.position },
    rows.coffees.map { it.id },
    rows.grinders.map { it.id },
    rows.grinderUnits.map { it.grinderId to it.position },
    rows.grinderMethodRanges.map { it.grinderId to it.method },
    rows.grinderAnchors.map { it.grinderId to it.level },
    rows.grindCalibrations.map { it.id },
    // Claves únicas: las filas con `null` no cuentan.
    rows.recipes.mapNotNull { it.builtInKey },
    rows.grinders.mapNotNull { it.catalogKey },
    rows.grindCalibrations.map { Triple(it.grinderAId, it.grinderBId, it.valueA) },
    rows.grindCalibrations.map { Triple(it.grinderAId, it.grinderBId, it.valueB) },
  )
  if (unique.any { keys -> keys.size != keys.toSet().size }) return false

  return rows.coffees.all { coffee -> listOfNotNull(coffee.photo1, coffee.photo2).all { MAIN_PHOTO_NAME.matches(it) } }
}

/** Son exactamente las claves foráneas del esquema 7. No se validan `brews.referenceId` ni los ids de los ajustes. */
private fun hasValidReferences(rows: BackupRows): Boolean {
  val coffees = rows.coffees.mapTo(hashSetOf()) { it.id }
  val grinders = rows.grinders.mapTo(hashSetOf()) { it.id }
  val brews = rows.brews.mapTo(hashSetOf()) { it.id }
  val recipes = rows.recipes.mapTo(hashSetOf()) { it.id }
  return rows.brews.all { (it.coffeeId == null || it.coffeeId in coffees) && (it.grinderId == null || it.grinderId in grinders) } &&
    rows.brewCurves.all { it.brewId in brews } &&
    rows.brewTargets.all { it.brewId in brews } &&
    rows.recipes.all { it.grinderId == null || it.grinderId in grinders } &&
    rows.recipeStages.all { it.recipeId in recipes } &&
    rows.grinderUnits.all { it.grinderId in grinders } &&
    rows.grinderMethodRanges.all { it.grinderId in grinders } &&
    rows.grinderAnchors.all { it.grinderId in grinders } &&
    rows.grindCalibrations.all { it.grinderAId in grinders && it.grinderBId in grinders }
}
