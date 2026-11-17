package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod

/** La referencia de la tarjeta de molienda (D24): el método y el ajuste de la receta o de la preparación elegida. */
data class GrindSource(val method: BrewMethod?, val setting: GrindSetting?)

/** Qué se ofrece cuando no hay equivalencia (D25). */
enum class GrindFix {
  /** El molino de destino es propio: se completan sus rangos. */
  COMPLETE_RANGES,

  /** El molino de destino es predefinido: se duplica para personalizarlo. */
  DUPLICATE,
}

/** Lo que recibe el convertidor suelto para abrirse ya relleno (F8). */
data class ConverterArgs(val sourceId: Long, val method: BrewMethod?, val value: Double)

/** Lo que muestra la tarjeta como ajuste del molino elegido (D25). */
sealed interface GrindOutcome {
  /** El ajuste: convertido ([exact] o no, con sus [reasons]) o [suggested] (receta sin molienda, punto medio del rango). */
  data class Value(val value: Double, val exact: Boolean, val reasons: Set<ConversionReason>, val suggested: Boolean) : GrindOutcome

  /** "La receta no indica molienda" y el molino elegido no tiene rango del método que sugerir. */
  data object NoSetting : GrindOutcome

  /** No hay equivalencia entre el molino de la receta y el elegido. */
  data class NoEquivalent(val fix: GrindFix) : GrindOutcome

  /** El molino de la receta está eliminado: no se puede convertir. */
  data object Orphan : GrindOutcome

  /** Sin receta ni preparación elegidas. */
  data object NoReference : GrindOutcome

  /** Sin molinos propios, no hay molino con el que moler. */
  data object NoGrinder : GrindOutcome
}

/**
 * La tarjeta de molienda de Preparar y del paso 2. [chosen] es el molino de "Moler con" (`null` sin molinos propios).
 */
data class GrindCardState(
  val source: GrindSource?,
  /** El molino vivo de la referencia, si lo tiene. */
  val sourceGrinder: Grinder?,
  val owned: List<Grinder>,
  val chosen: Grinder?,
  val outcome: GrindOutcome,
  /** Los argumentos del convertidor; `null` si la referencia no tiene molino vivo y ajuste. */
  val converter: ConverterArgs?,
) {
  /** Los predefinidos que intervienen (D9), sin repetir. */
  val attribution: List<Grinder>
    get() = listOfNotNull(sourceGrinder, chosen).filter { it.isPredefined }.distinctBy { it.id }

  /**
   * Lo que se guarda en la preparación: el molino elegido y el ajuste mostrado (convertido o sugerido) con su copia de
   * texto; el molino sin ajuste si no hay nada que mostrar (D16); `null` sin molino elegido.
   */
  fun snapshot(texts: GrindTexts): GrindSetting? {
    val grinder = chosen ?: return null
    val value = (outcome as? GrindOutcome.Value)?.value
    return GrindSetting(grinder.id, value, GrindFormat.label(grinder, value, texts))
  }
}

/** El molino preseleccionado de "Moler con" (§3.7): el elegido, el de la receta, el predeterminado o el primero. */
private fun preselected(source: GrindSource?, owned: List<Grinder>, chosenId: Long?, defaultId: Long?): Grinder? {
  fun byId(id: Long?) = id?.let { wanted -> owned.firstOrNull { it.id == wanted } }
  return byId(chosenId) ?: byId(source?.setting?.grinderId) ?: byId(defaultId) ?: owned.firstOrNull()
}

/**
 * El estado de la tarjeta de molienda (D25). [sourceGrinder] es el molino vivo de la referencia y [owned], mis
 * molinos; [chosenId] es la elección explícita del usuario, si la hay, y [defaultId] el predeterminado.
 */
fun grindCardState(
  source: GrindSource?,
  sourceGrinder: Grinder?,
  owned: List<Grinder>,
  chosenId: Long?,
  defaultId: Long?,
): GrindCardState {
  val chosen = preselected(source, owned, chosenId, defaultId)
  val setting = source?.setting?.takeUnless { it.isEmpty }
  val live = if (setting?.grinderId != null) sourceGrinder?.takeIf { it.id == setting.grinderId } else null
  val converter = if (live != null && setting?.value != null) ConverterArgs(live.id, source.method, setting.value) else null
  val outcome = when {
    chosen == null -> if (source == null) GrindOutcome.NoReference else GrindOutcome.NoGrinder
    source == null -> GrindOutcome.NoReference
    setting != null && live == null -> GrindOutcome.Orphan
    live != null && setting?.value != null -> convertOutcome(setting.value, source.method, live, chosen)
    else -> suggestion(source.method, chosen)
  }
  return GrindCardState(source, live, owned, chosen, outcome, converter)
}

private fun convertOutcome(value: Double, method: BrewMethod?, from: Grinder, to: Grinder): GrindOutcome =
  when (val result = GrindConverter.convert(value, method, from, to)) {
    is Conversion.Exact -> GrindOutcome.Value(result.value, exact = true, reasons = emptySet(), suggested = false)
    is Conversion.Approximate -> GrindOutcome.Value(result.value, exact = false, reasons = result.reasons, suggested = false)
    Conversion.NoEquivalent -> GrindOutcome.NoEquivalent(if (to.isPredefined) GrindFix.DUPLICATE else GrindFix.COMPLETE_RANGES)
  }

private fun suggestion(method: BrewMethod?, chosen: Grinder): GrindOutcome =
  GrindConverter.suggested(method, chosen)
    ?.let { GrindOutcome.Value(it, exact = true, reasons = emptySet(), suggested = true) }
    ?: GrindOutcome.NoSetting
