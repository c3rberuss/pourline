package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.grinder.FakeGrindTexts
import dev.josueamaya.pourline.grinder.GrindPick
import dev.josueamaya.pourline.grinder.GrindResolution
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.Grinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCopyTest {
  private val texts = FakeGrindTexts
  private val catalogUno = CatalogGrindFixtures.catalogUno
  private val ownedUno = CatalogGrindFixtures.ownedUno
  private val ownedDos = CatalogGrindFixtures.ownedDos
  private val ownedTres = CatalogGrindFixtures.ownedTres
  private val ownedSinCurva = CatalogGrindFixtures.ownedSinCurva

  private fun copy(
    recipe: CatalogRecipe = catalogRecipe(),
    owned: List<Grinder>,
    pick: GrindPick? = null,
    existing: Collection<String> = emptyList(),
  ): dev.josueamaya.pourline.recipe.Recipe {
    val resolution = catalogGrindResolution(recipe, owned, catalogUno, emptyList(), texts)
    return catalogCopy(recipe, resolution, pick, owned, existing, texts)
  }

  @Test
  fun `a copy with an owned grinder is linked to it with the same setting`() {
    val recipe = copy(owned = listOf(ownedDos, ownedUno))
    assertEquals(ownedUno.id, recipe.grind?.grinderId)
    assertEquals(27.0, recipe.grind?.value!!, 0.0)
    assertEquals(0L, recipe.id)
    assertNull(recipe.builtInKey)
  }

  @Test
  fun `a copy converted to another grinder uses the picked option`() {
    val owned = listOf(ownedDos, ownedTres)
    val resolution = catalogGrindResolution(catalogRecipe(), owned, catalogUno, emptyList(), texts) as GrindResolution.Convertible
    val option = resolution.options.single { it.grinder.id == ownedTres.id }
    val picked = copy(owned = owned, pick = GrindPick.Use(ownedTres.id))
    assertEquals(ownedTres.id, picked.grind?.grinderId)
    assertEquals(option.value, picked.grind?.value!!, 0.0)
    val asText = copy(owned = owned, pick = GrindPick.AsText)
    assertEquals(GrindSetting(null, null, "Media-fina · Acme Uno · 27 clics"), asText.grind)
  }

  @Test
  fun `a copy without a convertible grinder saves the grind as text`() {
    val recipe = copy(owned = listOf(ownedSinCurva))
    assertEquals(GrindSetting(null, null, "Media-fina · Acme Uno · 27 clics"), recipe.grind)
  }

  @Test
  fun `a copy without grind has no grind`() {
    val recipe = copy(catalogRecipe(grinderRef = null, grindText = null), owned = listOf(ownedUno))
    assertNull(recipe.grind)
  }

  @Test
  fun `a repeated name gets the next suffix`() {
    assertEquals("Nemo V60", copy(owned = emptyList()).name)
    assertEquals("Nemo V60 (2)", copy(owned = emptyList(), existing = listOf("Nemo V60")).name)
    assertEquals("Nemo V60 (3)", copy(owned = emptyList(), existing = listOf("Nemo V60", "Nemo V60 (2)")).name)
    assertEquals("Nemo V60 (2)", copy(owned = emptyList(), existing = listOf("  nemo v60 ")).name)
  }

  @Test
  fun `an existing copy is detected by catalog key`() {
    assertTrue(hasCopy("nemo_v60", setOf("nemo_v60", "otra")))
    assertFalse(hasCopy("nemo_v60", setOf("otra")))
    assertFalse(hasCopy("nemo_v60", emptySet()))
  }

  @Test
  fun `the copy keeps author, source and catalog key`() {
    val recipe = copy(owned = emptyList())
    assertEquals("Nemo", recipe.author)
    assertEquals("https://example.com/nemo", recipe.sourceUrl)
    assertEquals("nemo_v60", recipe.catalogKey)
    assertEquals(15.0, recipe.doseG, 0.0)
    assertEquals(2, recipe.stages.size)
  }
}
