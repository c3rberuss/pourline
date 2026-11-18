package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.recipe.share.SharedGrind

/** Un molino propio al que se puede convertir el ajuste compartido, con el ajuste convertido y si salió redondeado. */
data class GrindOption(val grinder: Grinder, val value: Double, val rounded: Boolean)

/** Qué se hace con la molienda de una receta importada (§3.6). */
sealed interface GrindResolution {
  /** Sin molienda. */
  data object None : GrindResolution

  /** El mismo molino que el del autor: se enlaza con [value] (puede faltar). */
  data class Linked(val grinder: Grinder, val value: Double?) : GrindResolution

  /** Otro molino con conversión: una opción por molino propio convertible, o guardar como [textLabel]. */
  data class Convertible(val options: List<GrindOption>, val textLabel: String) : GrindResolution

  /** Solo se puede guardar como texto. */
  data class Text(val label: String) : GrindResolution
}

/** Lo que elige el usuario en una [GrindResolution.Convertible]. */
sealed interface GrindPick {
  data class Use(val grinderId: Long) : GrindPick

  data object AsText : GrindPick
}

/**
 * Resuelve la molienda de [shared] con los molinos del receptor: [owned] son los propios, [catalogGrinder] el molino del
 * catálogo con la clave de [shared] (si el receptor lo tiene sembrado) y [calibrations] sus pares de calibración.
 */
fun resolveSharedGrind(
  shared: SharedGrind?,
  owned: List<Grinder>,
  catalogGrinder: Grinder?,
  calibrations: List<GrindCalibration>,
  texts: GrindTexts,
): GrindResolution {
  if (shared == null) return GrindResolution.None
  val candidates = owned.filter { !it.retired }
  sameGrinder(shared, candidates)?.let { return GrindResolution.Linked(it, shared.value) }
  val textLabel = sharedGrindText(shared, catalogGrinder, texts) ?: return GrindResolution.None
  if (shared.catalogKey != null && catalogGrinder != null && shared.value != null) {
    val options = candidates.filter { it.id != catalogGrinder.id }.mapNotNull { destination ->
      val conversion = GrindConverter.convert(shared.value, catalogGrinder, destination, calibrations.pointsBetween(catalogGrinder.id, destination.id))
      val value = conversion.valueOrNull ?: return@mapNotNull null
      val rounded = conversion is Conversion.Approximate && ConversionReason.ROUNDED in conversion.reasons
      GrindOption(destination, value, rounded)
    }
    if (options.isNotEmpty()) return GrindResolution.Convertible(options, textLabel)
  }
  return GrindResolution.Text(textLabel)
}

/** El molino propio igual al del autor: por clave de catálogo o, sin ella, por marca y modelo con el ajuste en su escala. */
private fun sameGrinder(shared: SharedGrind, candidates: List<Grinder>): Grinder? {
  if (shared.catalogKey != null) return candidates.firstOrNull { it.catalogKey == shared.catalogKey }
  val brand = shared.brand?.trim() ?: return null
  val model = shared.model?.trim() ?: return null
  val match = candidates.firstOrNull { it.brand.trim().equals(brand, ignoreCase = true) && it.model.trim().equals(model, ignoreCase = true) }
    ?: return null
  val value = shared.value
  return match.takeIf { value == null || value in it.scaleMin..it.scaleMax }
}

/** La opción preseleccionada: el primer molino convertible, o `null` si no hay opciones. */
fun GrindResolution.defaultPick(): GrindPick? =
  (this as? GrindResolution.Convertible)?.options?.firstOrNull()?.let { GrindPick.Use(it.grinder.id) }

/**
 * El texto de la molienda compartida: el `label` tal cual de un molino eliminado o "<marca> <modelo>" y, con ajuste,
 * " · <ajuste>" (con las unidades de [catalogGrinder] si existe, o el número sin formato). `null` si no hay nada que decir.
 */
fun sharedGrindText(shared: SharedGrind, catalogGrinder: Grinder?, texts: GrindTexts): String? {
  shared.label?.takeIf { it.isNotBlank() }?.let { return it }
  val brand = shared.brand ?: return null
  val model = shared.model ?: return null
  val name = "$brand $model"
  val value = shared.value ?: return name
  val setting = if (catalogGrinder != null) {
    GrindFormat.format(value, catalogGrinder, texts)
  } else {
    texts.number(value, if (value == Math.rint(value)) 0 else 1)
  }
  return name + GrindFormat.SEPARATOR + setting
}

/**
 * El ajuste que se guarda con [pick]. [owned] son los molinos de ahora: un molino elegido que ya no está, o que se
 * retiró, se guarda como texto (§4).
 */
fun GrindResolution.settingFor(pick: GrindPick?, owned: List<Grinder>, texts: GrindTexts): GrindSetting? = when (this) {
  GrindResolution.None -> null
  is GrindResolution.Text -> asText(label)
  is GrindResolution.Linked -> GrindChoice.Live(grinder.id, value).toSetting(grinder, texts)
  is GrindResolution.Convertible -> {
    val option = (pick as? GrindPick.Use)?.let { use -> options.firstOrNull { it.grinder.id == use.grinderId } }
    val live = option?.let { chosen -> owned.firstOrNull { it.id == chosen.grinder.id && !it.retired } }
    if (option != null && live != null) GrindChoice.Live(live.id, option.value).toSetting(live, texts) else asText(textLabel)
  }
}

private fun asText(label: String) = GrindSetting(grinderId = null, value = null, label = label)
