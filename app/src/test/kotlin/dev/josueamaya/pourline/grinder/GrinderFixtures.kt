package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod

/** Molinos de prueba con marcas ficticias y valores inventados a propósito: no son datos de la fuente (D36). */
object GrinderFixtures {
  /** Un molino de clics, 0–40, paso 1. */
  val uno = Grinder(
    id = 1,
    brand = "Acme",
    model = "Uno",
    units = listOf(GrindUnitLevel(GrindUnit.CLICKS)),
    scaleMin = 0.0,
    scaleMax = 40.0,
    ranges = mapOf(
      BrewMethod.V60 to GrindRange(16.0, 24.0),
      BrewMethod.AEROPRESS to GrindRange(8.0, 20.0),
    ),
    anchors = mapOf(GrindLevel.FINE to 8.0, GrindLevel.MEDIUM to 20.0, GrindLevel.COARSE to 32.0),
    owned = true,
  )

  /** Un molino de clics con medios pasos, 0–60, paso 0,5. */
  val dos = Grinder(
    id = 2,
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

  /** El molino de prueba del spec: rotaciones, números (4 por rotación) y clics (10 por número). 0–200 clics. */
  val tres = Grinder(
    id = 3,
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

  /** Subir el ajuste afina. Clics 0–50. */
  val inverso = Grinder(
    id = 4,
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

  /** Sin pasos: dial de 0 a 10 con paso 0,1. */
  val cinco = Grinder(
    id = 5,
    brand = "Acme",
    model = "Cinco",
    units = listOf(GrindUnitLevel(GrindUnit.NUMBERS)),
    scaleMin = 0.0,
    scaleMax = 10.0,
    step = 0.1,
    ranges = mapOf(BrewMethod.V60 to GrindRange(3.0, 5.0)),
    owned = true,
  )

  /** Compuesto con paso fraccionario y escala que empieza en 3: números (3–9) y puntos (3 por número, paso 0,5). */
  val seis = Grinder(
    id = 6,
    brand = "Acme",
    model = "Seis",
    units = listOf(GrindUnitLevel(GrindUnit.NUMBERS), GrindUnitLevel(GrindUnit.POINTS, perParent = 3)),
    scaleMin = 3.0,
    scaleMax = 27.0,
    step = 0.5,
    owned = true,
  )

  val all = listOf(uno, dos, tres, inverso, cinco, seis)
}

/** Textos en español para los tests JVM, con el plural simple ("1 clic", "2 clics"). */
object FakeGrindTexts : GrindTexts {
  private val singular = mapOf(
    GrindUnit.CLICKS to "clic",
    GrindUnit.POINTS to "punto",
    GrindUnit.NOTCHES to "muesca",
    GrindUnit.NUMBERS to "número",
    GrindUnit.ROTATIONS to "rotación",
    GrindUnit.STEPS to "paso",
  )

  private fun plural(unit: GrindUnit) = when (unit) {
    GrindUnit.ROTATIONS -> "rotaciones"
    else -> singular.getValue(unit) + "s"
  }

  override fun quantity(unit: GrindUnit, value: Double): String =
    number(value, if (value == Math.rint(value)) 0 else decimalsFor(value)) + " " + if (value == 1.0) singular.getValue(unit) else plural(unit)

  override fun unitName(unit: GrindUnit): String = plural(unit).replaceFirstChar { it.uppercase() }

  override fun number(value: Double, decimals: Int): String = "%.${decimals}f".format(java.util.Locale.forLanguageTag("es"), value)

  override fun copyName(model: String): String = "$model (copia)"

  private fun decimalsFor(value: Double) = if (Math.abs(value * 10 - Math.rint(value * 10)) < 1e-9) 1 else 2
}
