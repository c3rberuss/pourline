package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod

/** Una fila del convertidor: el ajuste equivalente en [grinder] y, si no hay equivalencia, qué se ofrece ([fix]). */
data class ConverterRow(val grinder: Grinder, val conversion: Conversion, val fix: GrindFix?)

/**
 * Una fila por cada molino de [owned] distinto de [source], en el orden de [owned] (D26). [value] está en unidades
 * base de [source].
 */
fun converterRows(source: Grinder, method: BrewMethod?, value: Double, owned: List<Grinder>): List<ConverterRow> =
  owned.filter { it.id != source.id }.map { target ->
    val conversion = GrindConverter.convert(value, method, source, target)
    val fix = if (conversion == Conversion.NoEquivalent) {
      if (target.isPredefined) GrindFix.DUPLICATE else GrindFix.COMPLETE_RANGES
    } else {
      null
    }
    ConverterRow(target, conversion, fix)
  }
