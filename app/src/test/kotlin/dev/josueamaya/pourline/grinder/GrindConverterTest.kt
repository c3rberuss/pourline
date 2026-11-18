package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.grinder.GrinderFixtures.dos
import dev.josueamaya.pourline.grinder.GrinderFixtures.inverso
import dev.josueamaya.pourline.grinder.GrinderFixtures.sinCurva
import dev.josueamaya.pourline.grinder.GrinderFixtures.soloRangos
import dev.josueamaya.pourline.grinder.GrinderFixtures.tres
import dev.josueamaya.pourline.grinder.GrinderFixtures.uno
import dev.josueamaya.pourline.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrindConverterTest {
  private fun curves(v: Double, from: Grinder, to: Grinder) = GrindConverter.convert(v, from, to, emptyList())

  private fun calibrated(v: Double, from: Grinder, to: Grinder, vararg pairs: Pair<Double, Double>) =
    GrindConverter.convert(v, from, to, pairs.map { CalibrationPoint(it.first, it.second) })

  private val approx = ConversionReason.ANCHORS

  @Test
  fun `drafts without an id are not the same grinder`() {
    // Con id 0 no hay atajo de "mismo molino": se calcula por curvas (anclas), y sale aproximado.
    val draft = uno.copy(id = 0)
    assertEquals(Conversion.Approximate(20.0, setOf(approx)), curves(20.0, draft, draft.copy()))
  }

  @Test
  fun suggestedIsTheRoundedMidpointOrNull() {
    assertEquals(20.0, GrindConverter.suggested(BrewMethod.V60, uno)!!, 0.0)
    assertEquals(28.0, GrindConverter.suggested(BrewMethod.V60, dos)!!, 0.0)
    assertEquals(null, GrindConverter.suggested(BrewMethod.CHEMEX, uno))
    assertEquals(null, GrindConverter.suggested(null, uno))
    // Punto medio 20,5 con paso 1 → redondea (≥ .5 hacia arriba).
    assertEquals(21.0, GrindConverter.suggested(BrewMethod.V60, uno.copy(ranges = mapOf(BrewMethod.V60 to GrindRange(16.0, 25.0))))!!, 0.0)
  }

  // Enmienda 1: conversión por curvas y pares, sin método.

  @Test
  fun `the same source gives the same result for every method`() {
    // La firma no tiene método: los rangos no cuentan. Uno 20 está en MEDIUM (nivel 3), que en Dos es 30.
    val expected = Conversion.Approximate(30.0, setOf(ConversionReason.ANCHORS))
    assertEquals(expected, curves(20.0, uno, dos))
    assertEquals(expected, curves(20.0, uno.copy(ranges = emptyMap()), dos.copy(ranges = emptyMap())))
    assertEquals(expected, curves(20.0, uno, dos.copy(ranges = mapOf(BrewMethod.ESPRESSO to GrindRange(1.0, 2.0)))))
  }

  @Test
  fun `curves convert between grinders with no anchors in common`() {
    // Uno 20 → nivel 3 → recta de Solo rangos (a = −7,293, b = 26,290): 71,577, redondeado a 72.
    assertEquals(Conversion.Approximate(72.0, setOf(approx, ConversionReason.ROUNDED)), curves(20.0, uno, soloRangos))
    // Y de vuelta, con las anclas de Uno: 72 → nivel 3,01 → 20,1 → 20.
    val back = curves(72.0, soloRangos, uno) as Conversion.Approximate
    assertEquals(20.0, back.value, 0.0)
    assertEquals(setOf(approx, ConversionReason.ROUNDED), back.reasons)
  }

  @Test
  fun `curves keep the coarse to fine position with an inverted grinder`() {
    // Uno 20 y Inverso 25 son los dos MEDIUM.
    assertEquals(Conversion.Approximate(25.0, setOf(approx)), curves(20.0, uno, inverso))
    assertEquals(Conversion.Approximate(20.0, setOf(approx)), curves(25.0, inverso, uno))
    // Lo más fino de Uno (8) es lo más fino de Inverso (40); lo más grueso (32), el 10.
    assertEquals(Conversion.Approximate(40.0, setOf(approx)), curves(8.0, uno, inverso))
    assertEquals(Conversion.Approximate(10.0, setOf(approx)), curves(32.0, uno, inverso))
  }

  @Test
  fun `two or more pairs interpolate, are exact on a pair and extrapolate`() {
    val pairs = arrayOf(10.0 to 14.0, 30.0 to 50.0)
    assertEquals(Conversion.Approximate(32.0, setOf(ConversionReason.CALIBRATED)), calibrated(20.0, uno, dos, *pairs))
    assertEquals(Conversion.Exact(14.0), calibrated(10.0, uno, dos, *pairs))
    assertEquals(Conversion.Exact(50.0), calibrated(30.0, uno, dos, *pairs))
    assertEquals(Conversion.Approximate(59.0, setOf(ConversionReason.CALIBRATED)), calibrated(35.0, uno, dos, *pairs))
    // Los pares no necesitan que el orden de entrada sea el de origen.
    assertEquals(calibrated(20.0, uno, dos, *pairs), calibrated(20.0, uno, dos, *pairs.reversedArray()))
    // Nunca ANCHORS, aunque los molinos tengan curva.
    for (v in listOf(0.0, 5.0, 15.0, 25.0, 40.0)) {
      val result = calibrated(v, uno, dos, *pairs)
      assertTrue("$result", (result as? Conversion.Approximate)?.reasons?.contains(ConversionReason.ANCHORS) != true)
    }
  }

  @Test
  fun `pairs work in both directions`() {
    // Los mismos pares vistos desde Dos: 14 → 10 y 50 → 30.
    assertEquals(Conversion.Approximate(20.0, setOf(ConversionReason.CALIBRATED)), calibrated(32.0, dos, uno, 14.0 to 10.0, 50.0 to 30.0))
    assertEquals(Conversion.Exact(10.0), calibrated(14.0, dos, uno, 14.0 to 10.0, 50.0 to 30.0))
  }

  @Test
  fun `calibration keeps the position with an inverted grinder`() {
    // Uno 10 = Inverso 40 y Uno 30 = Inverso 10: engrosar Uno baja el número de Inverso, que es engrosarlo.
    assertEquals(Conversion.Approximate(25.0, setOf(ConversionReason.CALIBRATED)), calibrated(20.0, uno, inverso, 10.0 to 40.0, 30.0 to 10.0))
    // En el sentido contrario, los pares llegan orientados desde Inverso (40 → 10 y 10 → 30).
    assertEquals(Conversion.Approximate(20.0, setOf(ConversionReason.CALIBRATED)), calibrated(25.0, inverso, uno, 40.0 to 10.0, 10.0 to 30.0))
  }

  @Test
  fun `one pair shifts the curve conversion through the pair`() {
    // Por curvas, Uno 20 → Dos 30; con el par Uno 20 = Dos 31, la conversión se desplaza 1.
    assertEquals(Conversion.Exact(31.0), calibrated(20.0, uno, dos, 20.0 to 31.0))
    // Uno 30 → nivel 4,667 → Dos 46,667; más 1 = 47,667 → 47,5.
    assertEquals(
      Conversion.Approximate(47.5, setOf(ConversionReason.CALIBRATED, ConversionReason.ROUNDED)),
      calibrated(30.0, uno, dos, 20.0 to 31.0),
    )
  }

  @Test
  fun `one pair without a curve has no equivalent`() {
    assertEquals(Conversion.NoEquivalent, calibrated(20.0, uno, sinCurva, 20.0 to 20.0))
    assertEquals(Conversion.NoEquivalent, calibrated(20.0, sinCurva, uno, 20.0 to 20.0))
    // Sin pares ni curva, tampoco; con dos pares, sí hay equivalencia.
    assertEquals(Conversion.NoEquivalent, curves(20.0, uno, sinCurva))
    assertTrue(calibrated(20.0, uno, sinCurva, 10.0 to 10.0, 30.0 to 30.0) is Conversion.Approximate)
  }

  @Test
  fun `rounding and the limit still apply after calibrating`() {
    // Uno 40 con los dos pares: 14 + 1,5 × 36 = 68 > 60 → 60.
    assertEquals(
      Conversion.Approximate(60.0, setOf(ConversionReason.CALIBRATED, ConversionReason.AT_LIMIT)),
      calibrated(40.0, uno, dos, 10.0 to 14.0, 30.0 to 50.0),
    )
    // Tres → Uno (paso 1): 100 → 22,5 por pares → 23 (ROUNDED; 22,5 sube).
    assertEquals(
      Conversion.Approximate(23.0, setOf(ConversionReason.CALIBRATED, ConversionReason.ROUNDED)),
      calibrated(110.0, tres, uno, 100.0 to 20.0, 120.0 to 25.0),
    )
  }

  @Test
  fun `a pair outside the edited scale is rounded or limited`() {
    // El par dice Dos 50, pero Dos se editó a 0–40: el valor del par no es válido y se limita.
    val shrunk = dos.copy(scaleMax = 40.0)
    assertEquals(
      Conversion.Approximate(40.0, setOf(ConversionReason.CALIBRATED, ConversionReason.AT_LIMIT)),
      calibrated(30.0, uno, shrunk, 10.0 to 14.0, 30.0 to 50.0),
    )
    // Y un par fuera de paso: Dos con paso 1 y el par en 14,5.
    val coarse = dos.copy(step = 1.0)
    assertEquals(
      Conversion.Approximate(15.0, setOf(ConversionReason.CALIBRATED, ConversionReason.ROUNDED)),
      calibrated(10.0, uno, coarse, 10.0 to 14.5, 30.0 to 50.0),
    )
  }

  @Test
  fun `the same grinder is exact without pairs or curves`() {
    assertEquals(Conversion.Exact(7.0), curves(7.0, sinCurva, sinCurva))
    assertEquals(Conversion.Exact(7.0), calibrated(7.0, uno, uno, 1.0 to 2.0))
  }
}
