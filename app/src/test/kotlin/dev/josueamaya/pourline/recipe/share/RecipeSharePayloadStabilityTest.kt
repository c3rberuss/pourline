package dev.josueamaya.pourline.recipe.share

import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.recipe.BuiltInRecipes
import dev.josueamaya.pourline.recipe.FakeRecipeTexts
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 * SPEC-010 (D14): las recetas sin pasos Acción tienen que seguir escribiendo los mismos bytes. Por cada fixture se
 * concatenan los payloads de todos los recortes de [trimPlans] (con la longitud de cada uno delante) y se compara el
 * SHA-256 con el valor congelado antes de añadir `StageType.ACTION`. Los payloads son anteriores a `deflate`.
 */
class RecipeSharePayloadStabilityTest {
  private class Fixture(val name: String, val recipe: Recipe, val grinder: Grinder? = null)

  private val fixtures = listOf(
    Fixture("v60Classic", v60Classic()),
    Fixture("fourSixBuiltIn", BuiltInRecipes.recipe(BuiltInRecipes.FOUR_SIX, FakeRecipeTexts())),
    Fixture("everyField1000", everyField(1_000)),
    Fixture("everyField100", everyField(100)),
    Fixture("everyField1", everyField(1)),
    Fixture("worstCase", worstCaseRecipe(), catalogGrinder),
    Fixture("fourSixUser", fourSixUserRecipe(), femobookA2),
    Fixture("withLongTexts", withLongTexts(300, 120, 10)),
  )

  private val frozen = mapOf(
    "v60Classic" to
      "e7f6082c7781144866e2c6d8fa5ee38ccb7bcb644439d1944e5236f127fa10f0",
    "fourSixBuiltIn" to
      "d82eb617cda484813d2c27fb387e3e9e258cc2fa7d47dc303465952159513f87",
    "everyField1000" to
      "12946bcf934287356ca9d0641aaa44b6cb437437481076dbc6e56302110d05e9",
    "everyField100" to
      "37261603eaec42719ced8fba539ff5d3fb4a45152690918c8b71e6609ec1468d",
    "everyField1" to
      "94f7c50b5c49bf2d6ab22e4f41f50aff207061344548073ac88d1a2abae5dd3e",
    "worstCase" to
      "9c6d6bc4b8b60b658de75b2da1f31865133062b2fc74eb78416a7272d2e7730e",
    "fourSixUser" to
      "0129f72b6957f35cf594014d3102d2aed4f3029f571ee8d06776c85132b62e1c",
    "withLongTexts" to
      "e07e53b073197836cf37f8d1c1f129caa0947f7b4a7728ab68717514bb717a72",
  )

  private fun sha256(fixture: Fixture): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (plan in trimPlans(fixture.recipe)) {
      val bytes = encodePayload(fixture.recipe, fixture.grinder, plan).bytes
      digest.update(bytes.size.toString().toByteArray())
      digest.update(':'.code.toByte())
      digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  @Test
  fun `the payloads of the recipes without actions keep their bytes`() {
    val actual = fixtures.associate { it.name to sha256(it) }
    assertEquals(frozen, actual)
  }
}
