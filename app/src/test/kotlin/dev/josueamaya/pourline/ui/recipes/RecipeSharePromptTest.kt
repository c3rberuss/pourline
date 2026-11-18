package dev.josueamaya.pourline.ui.recipes

import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import dev.josueamaya.pourline.recipe.share.TrimmedPart
import dev.josueamaya.pourline.recipe.share.randomText
import dev.josueamaya.pourline.recipe.share.withLongTexts
import dev.josueamaya.pourline.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeSharePromptTest {
  @Test
  fun `a recipe that fits shares without asking`() {
    val prompt = recipeSharePrompt(1, v60Classic(), grinder = null, grindText = null)

    assertNotNull(prompt.data.qr)
    assertNull(prompt.noQr)
    assertTrue(prompt.trimmed.isEmpty())
    assertFalse(prompt.partialInstructions)
    assertFalse(prompt.needsConfirmation)
  }

  @Test
  fun `a trimmed recipe asks with the trimmed text`() {
    val prompt = recipeSharePrompt(1, withLongTexts(notesLength = 300, instructionLength = 120), null, "Molino · 20")

    assertTrue(prompt.trimmed.isNotEmpty())
    assertNotNull(prompt.data.qr)
    assertNull(prompt.noQr)
    assertTrue(prompt.needsConfirmation)
    assertEquals(R.string.recipe_share_trimmed_text, prompt.dialogTextRes())
    assertEquals("Molino · 20", prompt.data.grindText)
  }

  @Test
  fun `partially trimmed instructions are flagged`() {
    // Sin notas caben las primeras instrucciones pero no las diez.
    val recipe = withLongTexts(notesLength = 300, instructionLength = 60, stageCount = 10)
    val encoded = RecipeShareCodec.encode(recipe, null)!!
    assertTrue("se esperaba un recorte parcial: ${encoded.droppedInstructions} quitadas y ${encoded.keptInstructions} conservadas", encoded.partialInstructions)

    val prompt = recipeSharePrompt(1, recipe, null, null)
    assertTrue(TrimmedPart.INSTRUCTIONS in prompt.trimmed)
    assertTrue(prompt.partialInstructions)
    assertTrue(prompt.needsConfirmation)
    assertEquals(listOf(R.string.recipe_trimmed_notes, R.string.recipe_trimmed_some_instructions), trimmedPartsRes(prompt.trimmed, prompt.partialInstructions))
  }

  @Test
  fun `an invalid recipe and one that does not fit give different notices`() {
    val invalid = recipeSharePrompt(1, v60Classic().copy(doseG = 0.0), null, null)
    val tooLong = recipeSharePrompt(2, v60Classic().copy(name = randomText(800)), null, null)

    assertEquals(NoQrReason.INVALID, invalid.noQr)
    assertEquals(NoQrReason.TOO_LONG, tooLong.noQr)
    assertEquals(R.string.recipe_share_invalid_text, invalid.dialogTextRes())
    assertEquals(R.string.recipe_share_too_long_text, tooLong.dialogTextRes())
    assertNotEquals(invalid.dialogTextRes(), tooLong.dialogTextRes())
    for (prompt in listOf(invalid, tooLong)) {
      assertNull(prompt.data.qr)
      assertTrue(prompt.withoutQr)
      assertTrue(prompt.needsConfirmation)
      assertTrue(prompt.trimmed.isEmpty())
    }
  }

  @Test
  fun `the trimmed parts keep their order and use the some steps text when partial`() {
    val all = setOf(TrimmedPart.STAGE_NAMES, TrimmedPart.INSTRUCTIONS, TrimmedPart.NOTES)

    assertEquals(
      listOf(R.string.recipe_trimmed_notes, R.string.recipe_trimmed_instructions, R.string.recipe_trimmed_stage_names),
      trimmedPartsRes(all, partialInstructions = false),
    )
    assertEquals(
      listOf(R.string.recipe_trimmed_notes, R.string.recipe_trimmed_some_instructions, R.string.recipe_trimmed_stage_names),
      trimmedPartsRes(all, partialInstructions = true),
    )
    assertEquals(listOf(R.string.recipe_trimmed_notes), trimmedPartsRes(setOf(TrimmedPart.NOTES), partialInstructions = true))
    assertEquals(emptyList<Int>(), trimmedPartsRes(emptySet(), partialInstructions = false))
  }
}
