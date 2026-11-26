package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.grinder.GrindCalibration
import dev.josueamaya.pourline.grinder.GrindFormat
import dev.josueamaya.pourline.grinder.GrindResolution
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.resolveSharedGrind
import dev.josueamaya.pourline.recipe.share.SharedGrind

/**
 * La molienda de la receta como la de una receta compartida (SPEC-012 §3.6), para reutilizar `resolveSharedGrind`. Sin
 * `grind` es `null`. Con molino (`grinderRef`), `label` es "<texto> · <marca> <modelo> · <ajuste>" y, si el molino del
 * catálogo de molinos no está en la base ([catalogGrinder] `null`), solo el texto. Sin molino, `label` es el texto.
 */
fun CatalogRecipe.toSharedGrind(catalogGrinder: Grinder?, texts: GrindTexts): SharedGrind? {
  val ref = grinderRef
  if (ref == null) return grindText?.let { SharedGrind(catalogKey = null, brand = null, model = null, value = null, label = it) }
  val grinderPart = catalogGrinder?.let {
    "${it.brand} ${it.model}" + GrindFormat.SEPARATOR + GrindFormat.format(ref.value, it, texts)
  }
  val label = listOfNotNull(grindText, grinderPart).takeIf { it.isNotEmpty() }?.joinToString(GrindFormat.SEPARATOR)
  return SharedGrind(
    catalogKey = ref.catalogKey,
    brand = catalogGrinder?.brand,
    model = catalogGrinder?.model,
    value = ref.value,
    label = label,
  )
}

/** Qué hacer con la molienda de [recipe] con los molinos del usuario: la lógica de SPEC-009 §3.6, sin duplicarla. */
fun catalogGrindResolution(
  recipe: CatalogRecipe,
  owned: List<Grinder>,
  catalogGrinder: Grinder?,
  calibrations: List<GrindCalibration>,
  texts: GrindTexts,
): GrindResolution = resolveSharedGrind(recipe.toSharedGrind(catalogGrinder, texts), owned, catalogGrinder, calibrations, texts)

/**
 * La molienda de origen de [recipe] para usarla como referencia en Preparar (D30): con molino y la fila del catálogo de
 * molinos presente, apunta a esa fila con su ajuste; si no, es solo el texto. `null` sin molienda.
 */
fun catalogSourceGrind(recipe: CatalogRecipe, catalogGrinder: Grinder?, texts: GrindTexts): GrindSetting? {
  val shared = recipe.toSharedGrind(catalogGrinder, texts) ?: return null
  val ref = recipe.grinderRef
  if (ref != null && catalogGrinder != null) return GrindSetting(catalogGrinder.id, ref.value, shared.label)
  return shared.label?.let { GrindSetting(grinderId = null, value = null, label = it) }
}

/**
 * El molino que Preparar preselecciona al moler esta receta (D30): el mismo molino con [GrindResolution.Linked], el
 * primer molino convertible (en el orden de Molinos) con [GrindResolution.Convertible], o `null` si solo hay texto.
 */
fun GrindResolution.preferredGrinderId(): Long? = when (this) {
  is GrindResolution.Linked -> grinder.id
  is GrindResolution.Convertible -> options.firstOrNull()?.grinder?.id
  else -> null
}
