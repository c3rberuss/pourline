package dev.dotscale.grinder

import dev.dotscale.grinder.GrinderFixtures.cinco
import dev.dotscale.grinder.GrinderFixtures.dos
import dev.dotscale.grinder.GrinderFixtures.tres
import dev.dotscale.grinder.GrinderFixtures.uno
import dev.dotscale.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrindCardStateTest {
  private val owned = listOf(uno, dos, tres)

  private fun source(grinder: Grinder?, value: Double?, method: BrewMethod? = BrewMethod.V60) =
    GrindSource(method, grinder?.let { GrindSetting(it.id, value, null) })

  private fun state(
    source: GrindSource?,
    sourceGrinder: Grinder? = null,
    owned: List<Grinder> = this.owned,
    chosenId: Long? = null,
    defaultId: Long? = null,
  ) = grindCardState(source, sourceGrinder, owned, chosenId, defaultId)

  // --- Preselección del §3.7 ---

  @Test
  fun `the grinder of the recipe is preselected when it is one of mine`() {
    assertEquals(dos, state(source(dos, 28.0), dos, defaultId = 1).chosen)
  }

  @Test
  fun `then the default and then the first of mine`() {
    // El molino de la receta no es mío: manda el predeterminado.
    val foreign = tres.copy(id = 99)
    assertEquals(dos, state(source(foreign, 80.0), foreign, defaultId = 2).chosen)
    // Sin predeterminado, el primero.
    assertEquals(uno, state(source(foreign, 80.0), foreign).chosen)
    // Un predeterminado que ya no es mío cae al primero.
    assertEquals(uno, state(source(foreign, 80.0), foreign, defaultId = 77).chosen)
  }

  @Test
  fun `the explicit choice wins and one that is no longer mine is ignored`() {
    assertEquals(tres, state(source(uno, 20.0), uno, chosenId = 3).chosen)
    assertEquals(uno, state(source(uno, 20.0), uno, chosenId = 55).chosen)
  }

  // --- Resultados ---

  @Test
  fun `the same grinder shows the setting as it is`() {
    val s = state(source(uno, 20.0), uno)
    assertEquals(GrindOutcome.Value(20.0, exact = true, reasons = emptySet(), suggested = false), s.outcome)
    assertEquals(ConverterArgs(1, BrewMethod.V60, 20.0), s.converter)
  }

  @Test
  fun `another grinder converts through the range of the method`() {
    val s = state(source(uno, 20.0), uno, chosenId = 2)
    assertEquals(GrindOutcome.Value(28.0, exact = true, reasons = emptySet(), suggested = false), s.outcome)
  }

  @Test
  fun `the marks come from the reasons of the conversion`() {
    val limit = state(source(uno, 40.0), uno, chosenId = 2).outcome as GrindOutcome.Value
    assertEquals(60.0, limit.value, 0.0)
    assertEquals(setOf(ConversionReason.AT_LIMIT), limit.reasons)
    // Sin rango del método en el destino se aproxima por anclas.
    val anchors = state(source(uno, 14.0, BrewMethod.AEROPRESS), uno, chosenId = 2).outcome as GrindOutcome.Value
    assertEquals(setOf(ConversionReason.ANCHORS), anchors.reasons)
    assertEquals(false, anchors.exact)
  }

  @Test
  fun `a recipe without grind suggests the middle of the range or says nothing`() {
    val suggested = state(GrindSource(BrewMethod.V60, null), chosenId = 1).outcome
    assertEquals(GrindOutcome.Value(20.0, exact = true, reasons = emptySet(), suggested = true), suggested)
    // Dos no tiene rango de AeroPress.
    assertEquals(GrindOutcome.NoSetting, state(GrindSource(BrewMethod.AEROPRESS, null), chosenId = 2).outcome)
    assertNull(state(GrindSource(BrewMethod.V60, null), chosenId = 1).converter)
  }

  @Test
  fun `a grinder without setting on the recipe also suggests`() {
    val s = state(source(uno, null), uno, chosenId = 2)
    assertEquals(GrindOutcome.Value(28.0, exact = true, reasons = emptySet(), suggested = true), s.outcome)
    assertNull(s.converter)
  }

  @Test
  fun `no equivalence offers to complete my grinder or to duplicate a predefined one`() {
    val own = state(source(uno, 14.0, BrewMethod.AEROPRESS), uno, owned = listOf(uno, cinco), chosenId = cinco.id).outcome
    assertEquals(GrindOutcome.NoEquivalent(GrindFix.COMPLETE_RANGES), own)
    val predefined = cinco.copy(catalogKey = "acme-cinco")
    val dup = state(source(uno, 14.0, BrewMethod.AEROPRESS), uno, owned = listOf(uno, predefined), chosenId = predefined.id).outcome
    assertEquals(GrindOutcome.NoEquivalent(GrindFix.DUPLICATE), dup)
  }

  @Test
  fun `a deleted grinder on the recipe cannot be converted`() {
    val orphan = GrindSource(BrewMethod.V60, GrindSetting(null, 30.0, "Chestnut S3 · 30 clics"))
    val s = state(orphan)
    assertEquals(GrindOutcome.Orphan, s.outcome)
    assertNull(s.converter)
  }

  @Test
  fun `without a recipe only grind with remains and without grinders there is nothing to grind with`() {
    assertEquals(GrindOutcome.NoReference, state(null).outcome)
    val none = state(source(uno, 20.0), uno, owned = emptyList())
    assertEquals(GrindOutcome.NoGrinder, none.outcome)
    assertNull(none.chosen)
    // La línea de la receta y el atajo siguen disponibles a nivel de estado; la tarjeta decide qué muestra.
    assertEquals(ConverterArgs(1, BrewMethod.V60, 20.0), none.converter)
  }

  @Test
  fun `the source lists the predefined grinders that take part without repeating`() {
    val predefined = uno.copy(catalogKey = "acme-uno")
    val both = state(source(predefined, 20.0), predefined, owned = listOf(predefined, dos))
    assertEquals(listOf(predefined), both.attribution)
    val two = state(source(predefined, 20.0), predefined, owned = listOf(predefined, dos.copy(catalogKey = "acme-dos")), chosenId = 2)
    assertEquals(listOf(1L, 2L), two.attribution.map { it.id })
    assertTrue(state(source(uno, 20.0), uno).attribution.isEmpty())
  }

  // --- Lo que se guarda ---

  @Test
  fun `the snapshot is the chosen grinder with the shown value and its copy`() {
    val converted = state(source(uno, 20.0), uno, chosenId = 2).snapshot(FakeGrindTexts)
    assertEquals(GrindSetting(2, 28.0, "Dos · 28 clics"), converted)
    val suggested = state(GrindSource(BrewMethod.V60, null), chosenId = 1).snapshot(FakeGrindTexts)
    assertEquals(GrindSetting(1, 20.0, "Uno · 20 clics"), suggested)
  }

  @Test
  fun `the snapshot keeps the grinder without a setting when there is nothing to show`() {
    assertEquals(GrindSetting(2, null, "Dos"), state(GrindSource(BrewMethod.AEROPRESS, null), chosenId = 2).snapshot(FakeGrindTexts))
    assertEquals(GrindSetting(1, null, "Uno"), state(null, chosenId = 1).snapshot(FakeGrindTexts))
    assertEquals(
      GrindSetting(1, null, "Uno"),
      state(GrindSource(BrewMethod.V60, GrindSetting(null, 30.0, "X")), chosenId = 1).snapshot(FakeGrindTexts),
    )
  }

  @Test
  fun `the snapshot is null without a chosen grinder`() {
    assertNull(state(source(uno, 20.0), uno, owned = emptyList()).snapshot(FakeGrindTexts))
  }
}
