package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod

/**
 * La referencia de la tarjeta de molienda (D24): el método y el ajuste de la receta o de la preparación elegida.
 * [preferredGrinderId] es el molino que conviene preseleccionar (SPEC-012): el de una receta del catálogo que el usuario
 * tiene o el primero al que se puede convertir. Las demás referencias lo dejan en `null`.
 */
data class GrindSource(val method: BrewMethod?, val setting: GrindSetting?, val preferredGrinderId: Long? = null)

/** Qué se ofrece cuando no hay equivalencia (D25). */
enum class GrindFix {
  /** El molino de destino es propio: se completan sus rangos. */
  COMPLETE_RANGES,

  /** El molino de destino es predefinido: se duplica para personalizarlo. */
  DUPLICATE,

  /** Hay un solo par de calibración y falta la curva de algún molino: se añade otra equivalencia (enmienda 1). */
  ADD_CALIBRATION,
}

/** El arreglo de "Sin equivalencia" y el molino al que lleva (D45). */
data class FixTarget(val fix: GrindFix, val grinderId: Long)

/**
 * Qué se ofrece cuando no hay equivalencia de [from] a [to] y cuántos pares hay entre ellos (D45). Con 1 par, añadir
 * otra equivalencia en [to]. Si no, completar los rangos del molino sin curva ([to] si ninguno la tiene) si es propio, o
 * duplicarlo si es predefinido.
 */
fun fixFor(from: Grinder, to: Grinder, pairCount: Int): FixTarget {
  if (pairCount == 1) return FixTarget(GrindFix.ADD_CALIBRATION, to.id)
  val target = if (GrindCurve.of(to) == null || GrindCurve.of(from) != null) to else from
  return FixTarget(if (target.isPredefined) GrindFix.DUPLICATE else GrindFix.COMPLETE_RANGES, target.id)
}

/** Lo que recibe el convertidor suelto para abrirse ya relleno (F8). */
data class ConverterArgs(val sourceId: Long, val value: Double)

/** Lo que muestra la tarjeta como ajuste del molino elegido (D25). */
sealed interface GrindOutcome {
  /** El ajuste: convertido ([exact] o no, con sus [reasons]) o [suggested] (receta sin molienda, punto medio del rango). */
  data class Value(val value: Double, val exact: Boolean, val reasons: Set<ConversionReason>, val suggested: Boolean) : GrindOutcome

  /** "La receta no indica molienda" y el molino elegido no tiene rango del método que sugerir. */
  data object NoSetting : GrindOutcome

  /** No hay equivalencia entre el molino de la receta y el elegido; [fix] se aplica al molino [grinderId] (D45). */
  data class NoEquivalent(val fix: GrindFix, val grinderId: Long) : GrindOutcome

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

/**
 * El molino preseleccionado de "Moler con" (§3.7): el elegido, el de la receta si es propio, el preferido de la
 * referencia, el predeterminado o el primero.
 */
private fun preselected(source: GrindSource?, owned: List<Grinder>, chosenId: Long?, defaultId: Long?): Grinder? {
  fun byId(id: Long?) = id?.let { wanted -> owned.firstOrNull { it.id == wanted } }
  return byId(chosenId) ?: byId(source?.setting?.grinderId) ?: byId(source?.preferredGrinderId) ?: byId(defaultId) ?: owned.firstOrNull()
}

/**
 * El estado de la tarjeta de molienda (D25). [sourceGrinder] es el molino vivo de la referencia y [owned], mis
 * molinos; [chosenId] es la elección explícita del usuario, si la hay, y [defaultId] el predeterminado.
 * [calibrations] son los pares de calibración del molino de origen; la conversión usa los que lo unen con el elegido.
 */
fun grindCardState(
  source: GrindSource?,
  sourceGrinder: Grinder?,
  owned: List<Grinder>,
  chosenId: Long?,
  defaultId: Long?,
  calibrations: List<GrindCalibration> = emptyList(),
): GrindCardState {
  val chosen = preselected(source, owned, chosenId, defaultId)
  val setting = source?.setting?.takeUnless { it.isEmpty }
  val live = if (setting?.grinderId != null) sourceGrinder?.takeIf { it.id == setting.grinderId } else null
  val converter = if (live != null && setting?.value != null) ConverterArgs(live.id, setting.value) else null
  val outcome = when {
    chosen == null -> if (source == null) GrindOutcome.NoReference else GrindOutcome.NoGrinder
    source == null -> GrindOutcome.NoReference
    // Una molienda solo de texto (una receta del catálogo o importada como texto) no es un molino eliminado.
    setting != null && live == null && !setting.isTextOnly -> GrindOutcome.Orphan
    live != null && setting?.value != null -> convertOutcome(setting.value, live, chosen, calibrations)
    else -> suggestion(source.method, chosen)
  }
  return GrindCardState(source, live, owned, chosen, outcome, converter)
}

private fun convertOutcome(value: Double, from: Grinder, to: Grinder, calibrations: List<GrindCalibration>): GrindOutcome {
  val pairs = calibrations.pointsBetween(from.id, to.id)
  return when (val result = GrindConverter.convert(value, from, to, pairs)) {
    is Conversion.Exact -> GrindOutcome.Value(result.value, exact = true, reasons = emptySet(), suggested = false)
    is Conversion.Approximate -> GrindOutcome.Value(result.value, exact = false, reasons = result.reasons, suggested = false)
    Conversion.NoEquivalent -> fixFor(from, to, pairs.size).let { GrindOutcome.NoEquivalent(it.fix, it.grinderId) }
  }
}

private fun suggestion(method: BrewMethod?, chosen: Grinder): GrindOutcome =
  GrindConverter.suggested(method, chosen)
    ?.let { GrindOutcome.Value(it, exact = true, reasons = emptySet(), suggested = true) }
    ?: GrindOutcome.NoSetting
