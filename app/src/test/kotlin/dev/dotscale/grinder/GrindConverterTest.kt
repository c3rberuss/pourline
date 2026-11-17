package dev.dotscale.grinder

import dev.dotscale.grinder.GrinderFixtures.dos
import dev.dotscale.grinder.GrinderFixtures.inverso
import dev.dotscale.grinder.GrinderFixtures.tres
import dev.dotscale.grinder.GrinderFixtures.uno
import dev.dotscale.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrindConverterTest {
  private fun convert(v: Double, m: BrewMethod?, from: Grinder, to: Grinder) = GrindConverter.convert(v, m, from, to)

  @Test
  fun linearInsideTheRangeKeepsTheSamePercentage() {
    // Uno V60 16–24; 20 es el 50 %. Dos V60 20–36: el 50 % es 28.
    assertEquals(Conversion.Exact(28.0), convert(20.0, BrewMethod.V60, uno, dos))
    // 17 es el 12,5 % → 22 en Dos.
    assertEquals(Conversion.Exact(22.0), convert(17.0, BrewMethod.V60, uno, dos))
  }

  @Test
  fun outsideTheRangeExtrapolatesWithoutAtLimitWhileItFitsTheScale() {
    // 8 está a −100 % → 20 − 16 = 4 en Dos.
    assertEquals(Conversion.Exact(4.0), convert(8.0, BrewMethod.V60, uno, dos))
    // 28 está a +150 % → 20 + 24 = 44 en Dos (dentro de 0–60).
    assertEquals(Conversion.Exact(44.0), convert(28.0, BrewMethod.V60, uno, dos))
  }

  @Test
  fun physicalLimitClampsAndFlagsAtLimit() {
    // 40 en Uno: (40−16)/8 = 300 % → 20 + 48 = 68 > 60 → 60.
    assertEquals(
      Conversion.Approximate(60.0, setOf(ConversionReason.AT_LIMIT)),
      convert(40.0, BrewMethod.V60, uno, dos),
    )
    // Hacia abajo: 0 en Uno está a −200 % → 20 − 32 = −12 < 0 → 0.
    assertEquals(
      Conversion.Approximate(0.0, setOf(ConversionReason.AT_LIMIT)),
      convert(0.0, BrewMethod.V60, uno, dos),
    )
  }

  @Test
  fun anchorsAreTheFallbackWhenTheRangeIsMissingOrTheMethodIsOtherOrNull() {
    // AEROPRESS no existe en Dos: se usa Uno FINE 8 / MEDIUM 20 / COARSE 32 contra Dos 10 / 30 / 50.
    val byMissing = convert(14.0, BrewMethod.AEROPRESS, uno, dos)
    // 14 está entre FINE (8) y MEDIUM (20), a la mitad → 20 en Dos.
    assertEquals(Conversion.Approximate(20.0, setOf(ConversionReason.ANCHORS)), byMissing)
    assertEquals(byMissing, convert(14.0, BrewMethod.OTHER, uno, dos))
    assertEquals(byMissing, convert(14.0, null, uno, dos))
  }

  @Test
  fun anchorsExtrapolateWithTheNearestSegment() {
    // 38 está más allá de COARSE (32): tramo MEDIUM–COARSE de Uno (20–32), 1,5 tramos → nivel 3 + ... se calcula así:
    // nivel = 3 + 2·(38−20)/12 = 6 ; en Dos: MEDIUM 30 (nivel 3) → COARSE 50 (nivel 5): pendiente 10/nivel → 30 + 3·10 = 60.
    val result = convert(38.0, null, uno, dos)
    assertEquals(Conversion.Approximate(60.0, setOf(ConversionReason.ANCHORS)), result)
  }

  @Test
  fun noEquivalentWithoutRangeAndWithFewerThanTwoCommonAnchors() {
    val withOneAnchor = dos.copy(id = 9, ranges = emptyMap(), anchors = mapOf(GrindLevel.FINE to 10.0))
    assertEquals(Conversion.NoEquivalent, convert(20.0, BrewMethod.V60, uno, withOneAnchor))
    val noAnchors = dos.copy(id = 10, ranges = emptyMap(), anchors = emptyMap())
    assertEquals(Conversion.NoEquivalent, convert(20.0, null, uno, noAnchors))
    // Anclas distintas en cada molino: solo una en común.
    val other = dos.copy(id = 11, ranges = emptyMap(), anchors = mapOf(GrindLevel.FINE to 10.0, GrindLevel.EXTRA_COARSE to 55.0))
    assertEquals(Conversion.NoEquivalent, convert(20.0, null, uno, other))
  }

  @Test
  fun roundsToTheStepAndFlagsRoundedOnlyWhenTheValueChanges() {
    // 18 en Uno → 25 % → 20 + 4 = 24 (exacto en Dos, paso 0,5).
    assertEquals(Conversion.Exact(24.0), convert(18.0, BrewMethod.V60, uno, dos))
    // Dos → Uno con paso 1: 23 → (23−20)/16 = 18,75 % → 16 + 1,5 = 17,5 → redondea a 18 (ROUNDED).
    assertEquals(Conversion.Approximate(18.0, setOf(ConversionReason.ROUNDED)), convert(23.0, BrewMethod.V60, dos, uno))
    // Tres → Dos, paso 0,5: 70 → 25 % → 24 exacto; 61 → 2,5 % → 20,4 → 20,5 (redondeado).
    assertEquals(Conversion.Exact(24.0), convert(70.0, BrewMethod.V60, tres, dos))
    assertEquals(Conversion.Approximate(20.5, setOf(ConversionReason.ROUNDED)), convert(61.0, BrewMethod.V60, tres, dos))
  }

  @Test
  fun invertedGrinderKeepsThePositionFromFineToCoarse() {
    // Inverso V60 20–32 con subir = afinar: el extremo fino es 32. 32 → posición 0 → fino de Uno (16).
    assertEquals(Conversion.Exact(16.0), convert(32.0, BrewMethod.V60, inverso, uno))
    // 20 → posición 1 (grueso) → 24.
    assertEquals(Conversion.Exact(24.0), convert(20.0, BrewMethod.V60, inverso, uno))
    // Y al revés: 16 (fino) de Uno → 32 en Inverso; 24 → 20.
    assertEquals(Conversion.Exact(32.0), convert(16.0, BrewMethod.V60, uno, inverso))
    assertEquals(Conversion.Exact(20.0), convert(24.0, BrewMethod.V60, uno, inverso))
    // 26 (mitad del rango 20–32) ↔ 20 (mitad de 16–24).
    assertEquals(Conversion.Exact(26.0), convert(20.0, BrewMethod.V60, uno, inverso))
  }

  @Test
  fun invertedGrinderUsesAnchorsTheSameWay() {
    // Inverso FINE 40 / MEDIUM 25 / COARSE 10. Uno 8 / 20 / 32. 14 está a mitad de FINE–MEDIUM → 32,5 en Inverso → 33.
    val result = convert(14.0, null, uno, inverso)
    assertTrue("$result", result is Conversion.Approximate)
    val value = (result as Conversion.Approximate).value
    assertEquals(33.0, value, 0.0)
    assertTrue(ConversionReason.ANCHORS in result.reasons)
  }

  @Test
  fun sameGrinderIsExactWithoutCalculation() {
    assertEquals(Conversion.Exact(33.3), convert(33.3, BrewMethod.V60, uno, uno))
    assertEquals(Conversion.Exact(99.0), convert(99.0, null, uno, uno.copy(model = "renombrado")))
  }

  @Test
  fun draftsWithoutIdAreNotTheSameGrinder() {
    val a = uno.copy(id = 0)
    val b = a.copy()
    assertTrue(convert(20.0, BrewMethod.V60, a, b) is Conversion.Exact)
    // Con id 0 se calcula: 20 → 50 % → 20 (igual rango): exacto igualmente; con otro rango cambia.
    val c = a.copy(ranges = mapOf(BrewMethod.V60 to GrindRange(10.0, 14.0)))
    assertEquals(Conversion.Exact(12.0), convert(20.0, BrewMethod.V60, a, c))
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
}
