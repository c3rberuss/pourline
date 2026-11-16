package dev.dotscale.grinder

import dev.dotscale.history.BrewMethod
import kotlin.math.abs
import kotlin.math.roundToLong

/** Un error de validación de un molino, con el campo al que pertenece. */
sealed interface GrinderError {
  data object BlankBrand : GrinderError

  data object BlankModel : GrinderError

  data object NoUnits : GrinderError

  data object ScaleOrder : GrinderError

  data object StepNotPositive : GrinderError

  data object ScaleNotMultipleOfStep : GrinderError

  data object NegativeCompoundScale : GrinderError

  /** La unidad de la posición [index] ya aparece antes. */
  data class DuplicateUnit(val index: Int) : GrinderError

  data object PrimaryWithPerParent : GrinderError

  /** La unidad secundaria de la posición [index] no declara al menos 2 unidades suyas por cada una de la anterior. */
  data class PerParentTooSmall(val index: Int) : GrinderError

  data class RangeOrder(val method: BrewMethod) : GrinderError

  data class RangeOutsideScale(val method: BrewMethod) : GrinderError

  data object RangeForOther : GrinderError

  data class AnchorOutsideScale(val level: GrindLevel) : GrinderError

  /** El ancla [level] rompe el orden de fino a grueso (tiene que ser estrictamente monótona según la dirección). */
  data class AnchorsNotMonotonic(val level: GrindLevel) : GrinderError
}

/** Valida un molino con las reglas del §3.5 del spec y las de la D15 del plan. */
object GrinderValidator {
  fun validate(grinder: Grinder): List<GrinderError> = buildList {
    if (grinder.brand.isBlank()) add(GrinderError.BlankBrand)
    if (grinder.model.isBlank()) add(GrinderError.BlankModel)
    if (grinder.units.isEmpty()) add(GrinderError.NoUnits)
    validateScale(grinder)
    validateUnits(grinder)
    validateRanges(grinder)
    validateAnchors(grinder)
  }

  private fun MutableList<GrinderError>.validateScale(g: Grinder) {
    if (!(g.scaleMin < g.scaleMax)) add(GrinderError.ScaleOrder)
    if (!(g.step > 0)) {
      add(GrinderError.StepNotPositive)
    } else if (g.scaleMin < g.scaleMax) {
      val steps = (g.scaleMax - g.scaleMin) / g.step
      if (abs(steps - steps.roundToLong()) > 1e-6) add(GrinderError.ScaleNotMultipleOfStep)
    }
    if (g.units.size > 1 && g.scaleMin < 0) add(GrinderError.NegativeCompoundScale)
  }

  private fun MutableList<GrinderError>.validateUnits(g: Grinder) {
    val seen = mutableSetOf<GrindUnit>()
    g.units.forEachIndexed { index, level ->
      if (!seen.add(level.unit)) add(GrinderError.DuplicateUnit(index))
      if (index == 0) {
        if (level.perParent != null) add(GrinderError.PrimaryWithPerParent)
      } else if ((level.perParent ?: 0) < 2) {
        add(GrinderError.PerParentTooSmall(index))
      }
    }
  }

  private fun MutableList<GrinderError>.validateRanges(g: Grinder) {
    g.ranges.forEach { (method, range) ->
      if (method == BrewMethod.OTHER) {
        add(GrinderError.RangeForOther)
        return@forEach
      }
      if (!(range.min < range.max)) add(GrinderError.RangeOrder(method))
      if (range.min < g.scaleMin - EPS || range.max > g.scaleMax + EPS) add(GrinderError.RangeOutsideScale(method))
    }
  }

  private fun MutableList<GrinderError>.validateAnchors(g: Grinder) {
    var previous: Double? = null
    GrindLevel.entries.filter { it in g.anchors }.forEach { level ->
      val value = g.anchors.getValue(level)
      if (value < g.scaleMin - EPS || value > g.scaleMax + EPS) add(GrinderError.AnchorOutsideScale(level))
      val before = previous
      if (before != null) {
        val ordered = if (g.direction == GrindDirection.HIGHER_IS_COARSER) value > before + EPS else value < before - EPS
        if (!ordered) add(GrinderError.AnchorsNotMonotonic(level))
      }
      previous = value
    }
  }
}
