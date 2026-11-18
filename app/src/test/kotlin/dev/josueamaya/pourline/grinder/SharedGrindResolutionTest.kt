package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.recipe.share.SharedGrind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedGrindResolutionTest {
  private val texts = FakeGrindTexts

  /** El molino del autor, del catálogo y sin propietario: Uno con clave. */
  private val catalogUno = GrinderFixtures.uno.copy(id = 100, catalogKey = "acme_uno", owned = false)

  /** El mismo molino del catálogo, ya en la lista del receptor. */
  private val ownedUno = GrinderFixtures.uno.copy(id = 11, catalogKey = "acme_uno")
  private val ownedDos = GrinderFixtures.dos.copy(id = 12)
  private val ownedTres = GrinderFixtures.tres.copy(id = 13)
  private val ownedSinCurva = GrinderFixtures.sinCurva.copy(id = 14)

  private fun shared(key: String? = "acme_uno", brand: String? = "Acme", model: String? = "Uno", value: Double? = 20.0, label: String? = null) =
    SharedGrind(key, brand, model, value, label)

  private fun resolve(
    shared: SharedGrind?,
    owned: List<Grinder>,
    catalog: Grinder? = catalogUno,
    calibrations: List<GrindCalibration> = emptyList(),
  ) = resolveSharedGrind(shared, owned, catalog, calibrations, texts)

  @Test
  fun `no shared grind is none`() {
    assertEquals(GrindResolution.None, resolve(null, listOf(ownedUno)))
    assertNull(GrindResolution.None.settingFor(null, emptyList(), texts))
    assertNull(GrindResolution.None.defaultPick())
  }

  // --- Mismo molino ---

  @Test
  fun `an owned grinder with the same catalog key is linked`() {
    val result = resolve(shared(), listOf(ownedDos, ownedUno))
    assertEquals(GrindResolution.Linked(ownedUno, 20.0), result)
  }

  @Test
  fun `without a key, the same brand and model ignoring case is linked`() {
    val custom = GrinderFixtures.uno.copy(id = 20, brand = "ACME ", model = " uno")
    val result = resolve(shared(key = null, value = 12.0), listOf(custom), catalog = null)
    assertEquals(GrindResolution.Linked(custom, 12.0), result)
  }

  @Test
  fun `a retired owned grinder is not linked`() {
    val result = resolve(shared(), listOf(ownedUno.copy(retired = true)))
    assertEquals(GrindResolution.Text("Acme Uno · 20 clics"), result)
  }

  @Test
  fun `a brand and model match with a value out of scale falls back to text`() {
    val custom = GrinderFixtures.uno.copy(id = 20)
    val result = resolve(shared(key = null, value = 90.0), listOf(custom), catalog = null)
    assertEquals(GrindResolution.Text("Acme Uno · 90"), result)
  }

  @Test
  fun `linked without a value keeps the grinder without a setting`() {
    val result = resolve(shared(value = null), listOf(ownedUno))
    assertEquals(GrindResolution.Linked(ownedUno, null), result)
    val setting = result.settingFor(null, listOf(ownedUno), texts)!!
    assertEquals(ownedUno.id, setting.grinderId)
    assertNull(setting.value)
  }

  // --- Conversión ---

  @Test
  fun `the catalog grinder converts to every owned grinder with an equivalent, in owned order`() {
    val result = resolve(shared(value = 20.0), listOf(ownedDos, ownedSinCurva, ownedTres)) as GrindResolution.Convertible
    assertEquals(listOf(ownedDos.id, ownedTres.id), result.options.map { it.grinder.id })
    // El nivel medio de Uno (20) es el medio de Dos (30) y de Tres (100).
    assertEquals(30.0, result.options[0].value, 1e-9)
    assertEquals(100.0, result.options[1].value, 1e-9)
    assertEquals("Acme Uno · 20 clics", result.textLabel)
  }

  @Test
  fun `the first convertible is preselected`() {
    val result = resolve(shared(), listOf(ownedTres, ownedDos))
    assertEquals(GrindPick.Use(ownedTres.id), result.defaultPick())
  }

  @Test
  fun `calibration pairs between the catalog grinder and an owned one are used`() {
    val pair = GrindCalibration(id = 1, grinderAId = catalogUno.id, valueA = 20.0, grinderBId = ownedDos.id, valueB = 33.0)
    val result = resolve(shared(value = 20.0), listOf(ownedDos), calibrations = listOf(pair)) as GrindResolution.Convertible
    assertEquals(33.0, result.options.single().value, 1e-9)
  }

  @Test
  fun `retired grinders are not offered`() {
    val result = resolve(shared(), listOf(ownedDos.copy(retired = true), ownedTres)) as GrindResolution.Convertible
    assertEquals(listOf(ownedTres.id), result.options.map { it.grinder.id })
  }

  @Test
  fun `a rounded conversion is flagged`() {
    // 22 queda entre el nivel medio (20) y el grueso (32) de Uno: en Dos cae en 33,33 y se redondea al paso de 0,5.
    val rounded = resolve(shared(value = 22.0), listOf(ownedDos)) as GrindResolution.Convertible
    assertTrue(rounded.options.single().rounded)
    val exact = resolve(shared(value = 20.0), listOf(ownedDos)) as GrindResolution.Convertible
    assertEquals(false, exact.options.single().rounded)
  }

  @Test
  fun `no convertible grinder falls back to text`() {
    assertEquals(GrindResolution.Text("Acme Uno · 20 clics"), resolve(shared(), listOf(ownedSinCurva)))
    assertEquals(GrindResolution.Text("Acme Uno · 20 clics"), resolve(shared(), emptyList()))
    // Sin ajuste no hay nada que convertir.
    assertEquals(GrindResolution.Text("Acme Uno"), resolve(shared(value = null), listOf(ownedDos)))
  }

  // --- Texto ---

  @Test
  fun `a deleted grinder keeps its label as is`() {
    val deleted = SharedGrind(null, null, null, null, "Molino viejo · 18")
    val result = resolve(deleted, listOf(ownedUno), catalog = null)
    assertEquals(GrindResolution.Text("Molino viejo · 18"), result)
    assertEquals(GrindSetting(null, null, "Molino viejo · 18"), result.settingFor(null, listOf(ownedUno), texts))
  }

  @Test
  fun `a custom grinder the receiver lacks is brand, model and the number`() {
    assertEquals(GrindResolution.Text("Casero Prueba · 18"), resolve(shared(key = null, brand = "Casero", model = "Prueba", value = 18.0), listOf(ownedUno), catalog = null))
    assertEquals(GrindResolution.Text("Casero Prueba · 18,5"), resolve(shared(key = null, brand = "Casero", model = "Prueba", value = 18.5), listOf(ownedUno), catalog = null))
  }

  @Test
  fun `the setting is formatted with the catalog grinder when it exists`() {
    val composed = GrinderFixtures.tres.copy(id = 101, catalogKey = "acme_tres", owned = false)
    // 100 clics = 2 rotaciones, 2 números y 0 clics (4 números por rotación, 10 clics por número).
    val result = resolve(shared(key = "acme_tres", brand = "Acme", model = "Tres", value = 100.0), emptyList(), catalog = composed)
    assertEquals(GrindResolution.Text("Acme Tres · 2 · 2 · 0"), result)
  }

  @Test
  fun `without a setting the text is brand and model`() {
    assertEquals("Acme Uno", sharedGrindText(shared(value = null), catalogUno, texts))
    assertNull(sharedGrindText(SharedGrind(null, null, null, 5.0, null), null, texts))
    assertEquals(GrindResolution.None, resolve(SharedGrind(null, null, null, 5.0, null), emptyList(), catalog = null))
  }

  // --- Ajuste guardado ---

  @Test
  fun `linked and use save the grinder, the value and the text copy`() {
    val linked = resolve(shared(), listOf(ownedUno))
    assertEquals(GrindSetting(ownedUno.id, 20.0, "Uno · 20 clics"), linked.settingFor(null, listOf(ownedUno), texts))
    val convertible = resolve(shared(), listOf(ownedDos)) as GrindResolution.Convertible
    val setting = convertible.settingFor(GrindPick.Use(ownedDos.id), listOf(ownedDos), texts)!!
    assertEquals(ownedDos.id, setting.grinderId)
    assertEquals(30.0, setting.value!!, 1e-9)
    assertEquals("Dos · 30 clics", setting.label)
  }

  @Test
  fun `text and as text save only the label`() {
    val text = resolve(shared(), listOf(ownedSinCurva))
    assertEquals(GrindSetting(null, null, "Acme Uno · 20 clics"), text.settingFor(null, listOf(ownedSinCurva), texts))
    val convertible = resolve(shared(), listOf(ownedDos))
    assertEquals(GrindSetting(null, null, "Acme Uno · 20 clics"), convertible.settingFor(GrindPick.AsText, listOf(ownedDos), texts))
    assertEquals(GrindSetting(null, null, "Acme Uno · 20 clics"), convertible.settingFor(null, listOf(ownedDos), texts))
  }

  @Test
  fun `a picked grinder no longer offered saves as text`() {
    val convertible = resolve(shared(), listOf(ownedDos, ownedTres))
    val label = GrindSetting(null, null, "Acme Uno · 20 clics")
    assertEquals(label, convertible.settingFor(GrindPick.Use(ownedDos.id), listOf(ownedTres), texts))
    assertEquals(label, convertible.settingFor(GrindPick.Use(ownedDos.id), listOf(ownedDos.copy(retired = true), ownedTres), texts))
    assertEquals(label, convertible.settingFor(GrindPick.Use(999), listOf(ownedDos, ownedTres), texts))
  }
}
