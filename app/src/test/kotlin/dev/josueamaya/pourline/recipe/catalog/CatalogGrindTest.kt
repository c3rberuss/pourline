package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.grinder.FakeGrindTexts
import dev.josueamaya.pourline.grinder.GrindResolution
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrinderFixtures
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.share.SharedGrind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Una receta del catálogo mínima para probar la molienda y la copia. */
internal fun catalogRecipe(
  key: String = "nemo_v60",
  name: String = "Nemo V60",
  grindText: String? = "Media-fina",
  grinderRef: CatalogGrinderRef? = CatalogGrinderRef("acme_uno", 27.0),
  author: String? = "Nemo",
  sourceUrl: String? = "https://example.com/nemo",
  roasts: Set<RoastLevel> = emptySet(),
) = CatalogRecipe(
  key = key,
  name = name,
  author = author,
  context = null,
  method = BrewMethod.V60,
  methodOther = null,
  doseG = 15.0,
  waterTempC = 93.0,
  grindText = grindText,
  grinderRef = grinderRef,
  notes = null,
  sourceUrl = sourceUrl,
  stages = listOf(RecipeStage(null, 0, 40.0, 10_000), RecipeStage(null, 45_000, 250.0, 30_000)),
  roasts = roasts,
)

/** El molino del autor en el catálogo de molinos y los del usuario, como en `SharedGrindResolutionTest`. */
internal object CatalogGrindFixtures {
  val catalogUno: Grinder = GrinderFixtures.uno.copy(id = 100, catalogKey = "acme_uno", owned = false)
  val ownedUno: Grinder = GrinderFixtures.uno.copy(id = 11, catalogKey = "acme_uno")
  val ownedDos: Grinder = GrinderFixtures.dos.copy(id = 12)
  val ownedTres: Grinder = GrinderFixtures.tres.copy(id = 13)
  val ownedSinCurva: Grinder = GrinderFixtures.sinCurva.copy(id = 14)
}

class CatalogGrindTest {
  private val texts = FakeGrindTexts
  private val catalogUno = CatalogGrindFixtures.catalogUno
  private val ownedUno = CatalogGrindFixtures.ownedUno
  private val ownedDos = CatalogGrindFixtures.ownedDos
  private val ownedTres = CatalogGrindFixtures.ownedTres
  private val ownedSinCurva = CatalogGrindFixtures.ownedSinCurva

  private fun resolve(recipe: CatalogRecipe, owned: List<Grinder>, catalog: Grinder? = catalogUno) =
    catalogGrindResolution(recipe, owned, catalog, emptyList(), texts)

  @Test
  fun `the grind text adds the brand, model and setting of the catalog grinder`() {
    val shared = catalogRecipe().toSharedGrind(catalogUno, texts)
    assertEquals(SharedGrind("acme_uno", "Acme", "Uno", 27.0, "Media-fina · Acme Uno · 27 clics"), shared)
  }

  @Test
  fun `without a grinder the grind is only its text`() {
    val recipe = catalogRecipe(grinderRef = null)
    assertEquals(SharedGrind(null, null, null, null, "Media-fina"), recipe.toSharedGrind(catalogUno, texts))
    assertEquals(GrindResolution.Text("Media-fina"), resolve(recipe, listOf(ownedUno)))
    assertEquals(GrindResolution.None, resolve(catalogRecipe(grinderRef = null, grindText = null), listOf(ownedUno)))
    assertNull(catalogRecipe(grinderRef = null, grindText = null).toSharedGrind(catalogUno, texts))
  }

  @Test
  fun `a grinder without a text keeps only the grinder part`() {
    val shared = catalogRecipe(grindText = null).toSharedGrind(catalogUno, texts)
    assertEquals("Acme Uno · 27 clics", shared?.label)
  }

  @Test
  fun `without the catalog grinder row the label is only the text`() {
    val shared = catalogRecipe().toSharedGrind(null, texts)
    assertEquals(SharedGrind("acme_uno", null, null, 27.0, "Media-fina"), shared)
    assertEquals(GrindResolution.Text("Media-fina"), resolve(catalogRecipe(), listOf(ownedDos), catalog = null))
  }

  @Test
  fun `the same grinder is linked when the user owns it`() {
    val result = resolve(catalogRecipe(), listOf(ownedDos, ownedUno))
    assertEquals(GrindResolution.Linked(ownedUno, 27.0), result)
    assertEquals(ownedUno.id, result.preferredGrinderId())
  }

  @Test
  fun `the first convertible grinder in the grinders order is preferred`() {
    val result = resolve(catalogRecipe(), listOf(ownedSinCurva, ownedDos, ownedTres)) as GrindResolution.Convertible
    assertEquals(listOf(ownedDos.id, ownedTres.id), result.options.map { it.grinder.id })
    assertEquals(ownedDos.id, result.preferredGrinderId())
    // El orden de Molinos manda, no cuál es el predeterminado del usuario.
    val other = resolve(catalogRecipe(), listOf(ownedTres, ownedDos)) as GrindResolution.Convertible
    assertEquals(ownedTres.id, other.preferredGrinderId())
  }

  @Test
  fun `without a convertible grinder there is no preferred one and the text is kept`() {
    val result = resolve(catalogRecipe(), listOf(ownedSinCurva))
    assertEquals(GrindResolution.Text("Media-fina · Acme Uno · 27 clics"), result)
    assertNull(result.preferredGrinderId())
    assertNull(resolve(catalogRecipe(grinderRef = null, grindText = null), listOf(ownedUno)).preferredGrinderId())
  }

  @Test
  fun `the source grind points to the catalog grinder row`() {
    assertEquals(
      GrindSetting(100, 27.0, "Media-fina · Acme Uno · 27 clics"),
      catalogSourceGrind(catalogRecipe(), catalogUno, texts),
    )
  }

  @Test
  fun `the source grind is only text without the row or without a grinder`() {
    assertEquals(GrindSetting(null, null, "Media-fina"), catalogSourceGrind(catalogRecipe(), null, texts))
    assertEquals(GrindSetting(null, null, "Media-fina"), catalogSourceGrind(catalogRecipe(grinderRef = null), catalogUno, texts))
    assertNull(catalogSourceGrind(catalogRecipe(grinderRef = null, grindText = null), catalogUno, texts))
    assertNull(catalogSourceGrind(catalogRecipe(grindText = null), null, texts))
  }
}
