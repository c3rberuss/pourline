package dev.josueamaya.pourline.share

import androidx.compose.ui.graphics.toArgb
import dev.josueamaya.pourline.coffee.CoffeeEntity
import dev.josueamaya.pourline.coffee.CoffeeProcess
import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.share.alternatingStages
import dev.josueamaya.pourline.ui.theme.AntPalette
import dev.josueamaya.pourline.ui.theme.AntSeedColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShareModelsTest {
  private fun coffee(id: Long, name: String = "Guji $id", days: Int? = 9, roaster: String? = "Café de Altura") =
    FooterCoffee(id, name, roaster, days)

  @Test
  fun `the coffee card data has no price nor stock even when the coffee has them`() {
    val entity = CoffeeEntity(
      id = 1,
      name = "Guji",
      roaster = "Café de Altura",
      process = CoffeeProcess.NATURAL,
      roastLevel = RoastLevel.LIGHT,
      roastDate = LocalDate.of(2026, 10, 12),
      bagWeightG = 250.0,
      price = 18.5,
      openedDate = LocalDate.of(2026, 10, 14),
      createdAtEpochMs = 1_000,
    )
    val data = entity.toShareData(daysSinceRoast = 9)

    val fields = CoffeeShareData::class.java.declaredFields.map { it.name.lowercase() }
    assertTrue(fields.none { "price" in it || "stock" in it || "bag" in it || "opened" in it })
    assertEquals("Guji", data.name)
    assertEquals(9, data.daysSinceRoast)
    assertEquals(CoffeeProcess.NATURAL, data.process)
    assertFalse(data.toString().contains("18.5"))
    assertFalse(data.toString().contains("250"))
  }

  @Test
  fun `a brew without a linked coffee has no footer lines`() {
    assertEquals(emptyList<ShareFooterLine>(), footerLines(null))
  }

  @Test
  fun `a single brew footer is one line without side`() {
    assertEquals(
      listOf(ShareFooterLine("Guji 1", "Café de Altura", listOf(9), side = null)),
      footerLines(coffee(1)),
    )
    assertEquals(listOf<Int>(), footerLines(coffee(1, days = null)).single().days)
  }

  @Test
  fun `a comparison with no linked coffee on either side has no footer`() {
    assertEquals(emptyList<ShareFooterLine>(), footerLines(null, null))
  }

  @Test
  fun `a comparison with an old text and a linked coffee only has the linked one with its side`() {
    assertEquals(listOf(ShareFooterLine("Guji 2", "Café de Altura", listOf(9), ShareSide.B)), footerLines(null, coffee(2)))
    assertEquals(listOf(ShareFooterLine("Guji 1", "Café de Altura", listOf(9), ShareSide.A)), footerLines(coffee(1), null))
  }

  @Test
  fun `the same coffee in both brews is one line without side with both days`() {
    val lines = footerLines(coffee(1, days = 9), coffee(1, days = 11))
    assertEquals(listOf(ShareFooterLine("Guji 1", "Café de Altura", listOf(9, 11), side = null)), lines)
  }

  @Test
  fun `the same coffee on the same day shows a single day`() {
    assertEquals(listOf(9), footerLines(coffee(1, days = 9), coffee(1, days = 9)).single().days)
  }

  @Test
  fun `two different coffees are two lines each with its side`() {
    val lines = footerLines(coffee(1, days = 9), coffee(2, days = 4, roaster = null))
    assertEquals(
      listOf(
        ShareFooterLine("Guji 1", "Café de Altura", listOf(9), ShareSide.A),
        ShareFooterLine("Guji 2", null, listOf(4), ShareSide.B),
      ),
      lines,
    )
  }

  @Test
  fun `file names do not depend on the image format`() {
    assertEquals("coffee-7.png", shareFileName(ShareTarget.Coffee(7)))
    assertEquals("brew-12.png", shareFileName(ShareTarget.Brew(12)))
    assertEquals("compare-3-4.png", shareFileName(ShareTarget.Compare(3, 4)))
    assertEquals("summary.png", shareFileName(ShareTarget.Summary))
  }

  @Test
  fun `a recipe is shared as recipe-id png`() {
    assertEquals("recipe-5.png", shareFileName(ShareTarget.Recipe(5)))
  }

  @Test
  fun `the qr box is 6 px per module with the quiet zone`() {
    fun px(modules: Int) = (qrBoxSize(modules).value * SHARE_DENSITY).toInt()
    assertEquals(6, (SHARE_QR_MODULE.value * SHARE_DENSITY).toInt())
    assertEquals(462, px(69))
    assertEquals(654, px(101))
    assertEquals(246, px(33))
  }

  @Test
  fun `a recipe request fits its content from the minimum size and the other cards are fixed`() {
    fun request(count: Int) = ShareRequest.Recipe(
      RecipeShareData(Recipe(name = "R", doseG = 15.0, stages = alternatingStages(count)), grindText = null, unit = WeightUnit.GRAMS, qr = null),
    )
    for (count in listOf(1, 10, 11, 20)) {
      assertTrue(request(count).fitsContent)
      assertEquals(SHARE_SIZE, request(count).size)
    }
    assertEquals(1080, (SHARE_SIZE.width.value * SHARE_DENSITY).toInt())
    assertEquals(1350, (SHARE_SIZE.height.value * SHARE_DENSITY).toInt())
    val coffee = ShareRequest.Coffee(CoffeeEntity(id = 1, name = "Guji", createdAtEpochMs = 1_000).toShareData(daysSinceRoast = null), photo = null)
    assertFalse(coffee.fitsContent)
    assertEquals(SHARE_SIZE, coffee.size)
  }

  @Test
  fun `the qr colors are tones of the primary palette`() {
    val tones = AntPalette.generate(AntSeedColors().primary.toArgb())
    val white = 0xFFFFFFFF.toInt()
    assertEquals(tones[7], SHARE_QR_DARK.toArgb())
    assertEquals(tones[5], SHARE_QR_EYE_RING.toArgb())
    assertEquals(9.0, Math.round(AntPalette.contrast(SHARE_QR_DARK.toArgb(), white) * 10) / 10.0, 0.0)
    assertEquals(4.1, Math.round(AntPalette.contrast(SHARE_QR_EYE_RING.toArgb(), white) * 10) / 10.0, 0.0)
  }
}
