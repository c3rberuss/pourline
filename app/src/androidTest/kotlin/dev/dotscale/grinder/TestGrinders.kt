package dev.dotscale.grinder

import dev.dotscale.history.BrewMethod
import dev.dotscale.history.HistoryDatabase

/** Molinos de prueba con marcas ficticias (D36): sus datos no son los de la fuente. */
object TestGrinders {
  val uno = Grinder(
    brand = "Acme",
    model = "Uno",
    units = listOf(GrindUnitLevel(GrindUnit.CLICKS)),
    scaleMin = 0.0,
    scaleMax = 40.0,
    ranges = mapOf(BrewMethod.V60 to GrindRange(16.0, 24.0), BrewMethod.AEROPRESS to GrindRange(8.0, 20.0)),
    anchors = mapOf(GrindLevel.FINE to 8.0, GrindLevel.MEDIUM to 20.0, GrindLevel.COARSE to 32.0),
    owned = true,
  )

  val dos = Grinder(
    brand = "Acme",
    model = "Dos",
    units = listOf(GrindUnitLevel(GrindUnit.CLICKS)),
    scaleMin = 0.0,
    scaleMax = 60.0,
    step = 0.5,
    ranges = mapOf(BrewMethod.V60 to GrindRange(20.0, 36.0)),
    anchors = mapOf(GrindLevel.FINE to 10.0, GrindLevel.MEDIUM to 30.0, GrindLevel.COARSE to 50.0),
    owned = true,
  )

  /** Rotaciones, números (4 por rotación) y clics (10 por número). */
  val tres = Grinder(
    brand = "Acme",
    model = "Tres",
    units = listOf(
      GrindUnitLevel(GrindUnit.ROTATIONS),
      GrindUnitLevel(GrindUnit.NUMBERS, perParent = 4),
      GrindUnitLevel(GrindUnit.CLICKS, perParent = 10),
    ),
    scaleMin = 0.0,
    scaleMax = 200.0,
    ranges = mapOf(BrewMethod.V60 to GrindRange(60.0, 100.0)),
    anchors = mapOf(GrindLevel.FINE to 40.0, GrindLevel.MEDIUM to 100.0, GrindLevel.COARSE to 160.0),
    owned = true,
  )

  /** Subir el ajuste afina. */
  val inverso = Grinder(
    brand = "Acme",
    model = "Inverso",
    units = listOf(GrindUnitLevel(GrindUnit.CLICKS)),
    scaleMin = 0.0,
    scaleMax = 50.0,
    direction = GrindDirection.HIGHER_IS_FINER,
    ranges = mapOf(BrewMethod.V60 to GrindRange(20.0, 32.0)),
    anchors = mapOf(GrindLevel.FINE to 40.0, GrindLevel.MEDIUM to 25.0, GrindLevel.COARSE to 10.0),
    owned = true,
  )
}

/** Inserta [grinder] y devuelve su id. */
suspend fun HistoryDatabase.addGrinder(grinder: Grinder, nowMs: Long = 1_000L): Long = grinderDao().save(grinder.copy(id = 0).toRows(nowMs))
