package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Recalcula `METHOD_LEVELS` desde el catálogo (§3.6 de la enmienda 1). Solo vive en los tests: el cálculo no está en la
 * app, y un cambio del catálogo hace fallar `MethodLevelsTest`.
 *
 * Para cada predefinido con `micronRange` y rango del método: las micras del punto medio del rango, suponiendo el molino
 * lineal entre su escala y su rango en micras (invertido en `HIGHER_IS_FINER`); se promedia entre molinos y se pasa a
 * nivel con `L = (µ − 100) / 200`, redondeado a 2 decimales (`HALF_UP`).
 */
fun methodLevels(entries: List<CatalogEntry>): Map<BrewMethod, Double> {
  val microns = mutableMapOf<BrewMethod, MutableList<Double>>()
  for (entry in entries) {
    val g = entry.grinder
    val range = entry.micronRange ?: continue
    for ((method, r) in g.ranges) {
      val fraction = ((r.min + r.max) / 2 - g.scaleMin) / (g.scaleMax - g.scaleMin)
      val mu = if (g.direction == GrindDirection.HIGHER_IS_FINER) range.max - fraction * (range.max - range.min) else range.min + fraction * (range.max - range.min)
      microns.getOrPut(method) { mutableListOf() } += mu
    }
  }
  return microns.mapValues { (_, list) ->
    val level = (list.average() - 100) / 200
    BigDecimal.valueOf(level).setScale(2, RoundingMode.HALF_UP).toDouble()
  }
}
