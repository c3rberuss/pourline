package dev.dotscale.grinder

/**
 * El ajuste de molienda guardado en una receta o una preparación. Estados: con molino y ajuste; con molino y sin
 * ajuste (el molino elegido, a completar); y molino eliminado (`grinderId = null` con [label] presente, la copia de
 * texto). Un `null` en lugar del objeto significa sin molienda.
 */
data class GrindSetting(val grinderId: Long?, val value: Double?, val label: String?) {
  /** Todas las partes vacías: equivale a no tener molienda. */
  val isEmpty: Boolean get() = grinderId == null && value == null && label.isNullOrBlank()
}

/** Lo que edita un formulario de receta o de preparación. */
sealed interface GrindChoice {
  /** Sin molino. */
  data object None : GrindChoice

  /** Un molino vivo, con su ajuste o sin él. */
  data class Live(val grinderId: Long, val value: Double?) : GrindChoice

  /** El ajuste de un molino ya eliminado: solo existe si ya lo era al cargar y se conserva hasta que se elija otro. */
  data class Orphan(val setting: GrindSetting) : GrindChoice
}

/** El ajuste que se guarda. [grinder] es el molino vivo de un [GrindChoice.Live] (`null` si ya no existe). */
fun GrindChoice.toSetting(grinder: Grinder?, texts: GrindTexts): GrindSetting? = when (this) {
  GrindChoice.None -> null
  is GrindChoice.Orphan -> setting
  is GrindChoice.Live ->
    if (grinder == null) GrindSetting(grinderId, value, null) else GrindSetting(grinder.id, value, GrindFormat.label(grinder, value, texts))
}

/** El ajuste sin regenerar la copia de texto: un [GrindChoice.Live] sale con `label = null` (la regenera quien tiene el molino). */
fun GrindChoice.toSettingUnlabeled(): GrindSetting? = when (this) {
  GrindChoice.None -> null
  is GrindChoice.Orphan -> setting
  is GrindChoice.Live -> GrindSetting(grinderId, value, null)
}

/** La elección de un formulario a partir de lo guardado. */
fun GrindSetting?.toChoice(): GrindChoice = when {
  this == null || isEmpty -> GrindChoice.None
  grinderId != null -> GrindChoice.Live(grinderId, value)
  else -> GrindChoice.Orphan(this)
}

/**
 * Elegir otro molino (D16): deja el ajuste en `null`; elegir el mismo no cambia nada, y `null` ("Sin molino") deja la
 * elección vacía. Un [GrindChoice.Orphan] desaparece al elegir.
 */
fun GrindChoice.withGrinder(grinderId: Long?): GrindChoice = when {
  grinderId == null -> GrindChoice.None
  this is GrindChoice.Live && this.grinderId == grinderId -> this
  else -> GrindChoice.Live(grinderId, null)
}

/** Fija el ajuste del molino elegido; sin molino vivo no hace nada. */
fun GrindChoice.withValue(value: Double): GrindChoice = if (this is GrindChoice.Live) copy(value = value) else this

/** El id del molino vivo de la elección, o `null`. */
val GrindChoice.liveGrinderId: Long? get() = (this as? GrindChoice.Live)?.grinderId
