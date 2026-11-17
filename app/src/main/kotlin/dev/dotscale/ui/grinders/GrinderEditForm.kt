package dev.dotscale.ui.grinders

import dev.dotscale.grinder.GrindDirection
import dev.dotscale.grinder.GrindLevel
import dev.dotscale.grinder.GrindRange
import dev.dotscale.grinder.GrindUnit
import dev.dotscale.grinder.GrindUnitLevel
import dev.dotscale.grinder.Grinder
import dev.dotscale.grinder.GrinderError
import dev.dotscale.grinder.GrinderValidator
import dev.dotscale.history.BrewMethod

/** Una unidad del formulario; [perParent] es el texto de cuántas caben en la anterior (vacío en la primaria). */
data class UnitForm(val unit: GrindUnit, val perParent: String = "")

/** Un rango del formulario; un extremo `null` está por escribir. */
data class RangeForm(val min: Double? = null, val max: Double? = null)

/** El campo del formulario al que pertenece un error. */
sealed interface GrinderField {
  data object Brand : GrinderField

  data object Model : GrinderField

  data object Units : GrinderField

  data class Unit(val index: Int) : GrinderField

  data class PerParent(val index: Int) : GrinderField

  data object Step : GrinderField

  data object Scale : GrinderField

  data class Range(val method: BrewMethod) : GrinderField

  data class Anchor(val level: GrindLevel) : GrinderField
}

/**
 * El formulario del editor de molinos (§3.5), puro y sin Android. Los rangos y las anclas se conservan aunque se
 * cambien las unidades o la escala: el validador avisa de los que queden fuera, pero no se pierden.
 */
data class GrinderEditForm(
  val brand: String = "",
  val model: String = "",
  val units: List<UnitForm> = listOf(UnitForm(GrindUnit.CLICKS)),
  val step: String = "1",
  val direction: GrindDirection = GrindDirection.HIGHER_IS_COARSER,
  val scaleMin: Double? = 0.0,
  val scaleMax: Double? = null,
  val ranges: Map<BrewMethod, RangeForm> = emptyMap(),
  val anchors: Map<GrindLevel, Double?> = emptyMap(),
) {
  val canAddUnit: Boolean get() = units.size < MAX_UNITS

  /** Añade una unidad fina: la primera que no esté usada. */
  fun addUnit(): GrinderEditForm {
    if (!canAddUnit) return this
    val next = GrindUnit.entries.firstOrNull { e -> units.none { it.unit == e } } ?: GrindUnit.CLICKS
    return copy(units = units + UnitForm(next, "10"))
  }

  /** Quita la unidad [index]; la primaria pasa a ser la siguiente (y pierde su `perParent`). Siempre queda una. */
  fun removeUnit(index: Int): GrinderEditForm {
    if (units.size <= 1 || index !in units.indices) return this
    return copy(units = units.filterIndexed { i, _ -> i != index }.mapIndexed { i, u -> if (i == 0) u.copy(perParent = "") else u })
  }

  fun setUnit(index: Int, unit: GrindUnit): GrinderEditForm =
    copy(units = units.mapIndexed { i, u -> if (i == index) u.copy(unit = unit) else u })

  fun setPerParent(index: Int, text: String): GrinderEditForm =
    copy(units = units.mapIndexed { i, u -> if (i == index) u.copy(perParent = text) else u })

  fun setRange(method: BrewMethod, range: RangeForm?): GrinderEditForm =
    copy(ranges = if (range == null) ranges - method else ranges + (method to range))

  fun setAnchor(level: GrindLevel, value: Double?, present: Boolean = true): GrinderEditForm =
    copy(anchors = if (present) anchors + (level to value) else anchors - level)

  private val stepValue: Double? get() = step.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

  private val perParents: List<Int?> get() = units.mapIndexed { i, u -> if (i == 0) null else u.perParent.trim().toIntOrNull() }

  /** `true` si todos los números están escritos: solo entonces se puede construir un molino. */
  private val complete: Boolean
    get() = scaleMin != null && scaleMax != null && stepValue != null &&
      perParents.drop(1).all { it != null } &&
      ranges.values.all { it.min != null && it.max != null } &&
      anchors.values.all { it != null }

  /**
   * El molino del formulario, o `null` si falta algún número. [base] aporta lo que el formulario no edita (clave,
   * origen, "tengo", retirado, fuente y duplicado de); con `id` y `base` nulos es un molino propio nuevo.
   */
  fun toGrinder(id: Long = 0, base: Grinder? = null): Grinder? {
    if (!complete) return null
    return Grinder(
      id = id,
      catalogKey = base?.catalogKey,
      brand = brand.trim(),
      model = model.trim(),
      units = units.mapIndexed { i, u -> GrindUnitLevel(u.unit, if (i == 0) null else perParents[i]) },
      scaleMin = scaleMin!!,
      scaleMax = scaleMax!!,
      step = stepValue!!,
      direction = direction,
      ranges = ranges.mapValues { (_, r) -> GrindRange(r.min!!, r.max!!) },
      anchors = anchors.mapValues { it.value!! },
      owned = base?.owned ?: true,
      retired = base?.retired ?: false,
      sourceUrl = base?.sourceUrl,
      sourceName = base?.sourceName,
      duplicatedFromKey = base?.duplicatedFromKey,
    )
  }

  /** El molino con lo que ya hay, para la vista previa; `null` si aún no tiene escala. Ignora rangos y anclas incompletos. */
  fun previewGrinder(): Grinder? {
    val min = scaleMin ?: return null
    val max = scaleMax ?: return null
    val step = stepValue ?: return null
    if (perParents.drop(1).any { it == null } || min >= max) return null
    return Grinder(
      brand = brand,
      model = model,
      units = units.mapIndexed { i, u -> GrindUnitLevel(u.unit, if (i == 0) null else perParents[i]) },
      scaleMin = min,
      scaleMax = max,
      step = step,
      direction = direction,
      ranges = ranges.mapNotNull { (m, r) -> if (r.min != null && r.max != null && r.min < r.max) m to GrindRange(r.min, r.max) else null }.toMap(),
      anchors = anchors.mapNotNull { (l, v) -> v?.let { l to it } }.toMap(),
    )
  }

  /**
   * Un molino con la jerarquía, el paso y la dirección del formulario, para los `GrindInput`: con [limited] usa la
   * escala del formulario (si está completa y es válida); si no, una escala muy ancha desde 0.
   */
  fun inputGrinder(limited: Boolean): Grinder {
    val min = scaleMin
    val max = scaleMax
    val hasScale = limited && min != null && max != null && min < max
    return Grinder(
      brand = brand,
      model = model,
      units = units.mapIndexed { i, u -> GrindUnitLevel(u.unit, if (i == 0) null else (perParents[i] ?: 10).coerceAtLeast(2)) },
      scaleMin = if (hasScale) min!! else 0.0,
      scaleMax = if (hasScale) max!! else WIDE_SCALE,
      step = stepValue ?: 1.0,
      direction = direction,
    )
  }

  /** Un error por campo (el primero): los de formulario (falta un valor) y, con todo escrito, los del validador. */
  fun errors(): Map<GrinderField, GrinderError> {
    val result = linkedMapOf<GrinderField, GrinderError>()
    if (brand.isBlank()) result[GrinderField.Brand] = GrinderError.BlankBrand
    if (model.isBlank()) result[GrinderField.Model] = GrinderError.BlankModel
    units.forEachIndexed { i, u ->
      if (units.indexOfFirst { it.unit == u.unit } != i) result[GrinderField.Unit(i)] = GrinderError.DuplicateUnit(i)
      if (i > 0 && perParents[i] == null) result[GrinderField.PerParent(i)] = GrinderError.MissingValue
    }
    if (stepValue == null) result[GrinderField.Step] = GrinderError.StepNotPositive
    if (scaleMin == null || scaleMax == null) result[GrinderField.Scale] = GrinderError.MissingValue
    ranges.forEach { (m, r) -> if (r.min == null || r.max == null) result[GrinderField.Range(m)] = GrinderError.MissingValue }
    anchors.forEach { (l, v) -> if (v == null) result[GrinderField.Anchor(l)] = GrinderError.MissingValue }
    // Las reglas del validador se aplican a lo que ya está escrito: los números que faltan se rellenan con valores
    // neutros y se descartan los errores que dependen de ellos (ya van marcados como "falta un valor").
    val scaleKnown = scaleMin != null && scaleMax != null
    val candidate = Grinder(
      brand = brand,
      model = model,
      units = units.mapIndexed { i, u -> GrindUnitLevel(u.unit, if (i == 0) null else perParents[i] ?: 10) },
      scaleMin = scaleMin ?: 0.0,
      scaleMax = scaleMax ?: WIDE_SCALE,
      step = stepValue ?: 1.0,
      direction = direction,
      ranges = ranges.mapNotNull { (m, r) -> if (r.min != null && r.max != null) m to GrindRange(r.min, r.max) else null }.toMap(),
      anchors = anchors.mapNotNull { (l, v) -> v?.let { l to it } }.toMap(),
    )
    for (error in GrinderValidator.validate(candidate)) {
      val dependsOnScale = error == GrinderError.ScaleOrder || error == GrinderError.NegativeCompoundScale
      if (!scaleKnown && dependsOnScale) continue
      if ((!scaleKnown || stepValue == null) && error == GrinderError.ScaleNotMultipleOfStep) continue
      result.putIfAbsent(fieldOf(error), error)
    }
    return result
  }

  val hasErrors: Boolean get() = errors().isNotEmpty()

  fun isDirty(initial: GrinderEditForm): Boolean = this != initial

  companion object {
    const val MAX_UNITS = 4
    private const val WIDE_SCALE = 1_000_000.0

    fun from(grinder: Grinder): GrinderEditForm = GrinderEditForm(
      brand = grinder.brand,
      model = grinder.model,
      units = grinder.units.mapIndexed { i, u -> UnitForm(u.unit, if (i == 0) "" else u.perParent?.toString().orEmpty()) },
      step = formatNumber(grinder.step),
      direction = grinder.direction,
      scaleMin = grinder.scaleMin,
      scaleMax = grinder.scaleMax,
      ranges = grinder.ranges.mapValues { (_, r) -> RangeForm(r.min, r.max) },
      anchors = grinder.anchors,
    )

    private fun formatNumber(value: Double): String = if (value == Math.rint(value)) value.toLong().toString() else value.toString()

    private fun fieldOf(error: GrinderError): GrinderField = when (error) {
      GrinderError.BlankBrand -> GrinderField.Brand
      GrinderError.BlankModel -> GrinderField.Model
      GrinderError.NoUnits, GrinderError.PrimaryWithPerParent, GrinderError.RangeForOther -> GrinderField.Units
      GrinderError.ScaleOrder, GrinderError.NegativeCompoundScale, GrinderError.ScaleNotMultipleOfStep, GrinderError.MissingValue -> GrinderField.Scale
      GrinderError.StepNotPositive -> GrinderField.Step
      is GrinderError.DuplicateUnit -> GrinderField.Unit(error.index)
      is GrinderError.PerParentTooSmall -> GrinderField.PerParent(error.index)
      is GrinderError.RangeOrder -> GrinderField.Range(error.method)
      is GrinderError.RangeOutsideScale -> GrinderField.Range(error.method)
      is GrinderError.AnchorOutsideScale -> GrinderField.Anchor(error.level)
      is GrinderError.AnchorsNotMonotonic -> GrinderField.Anchor(error.level)
    }
  }
}
