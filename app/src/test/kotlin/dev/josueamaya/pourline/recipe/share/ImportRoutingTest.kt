package dev.josueamaya.pourline.recipe.share

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportRoutingTest {
  private val ok = GOLDEN_V60_URI
  private val newer = "pourline://recipe?v=2&d=x"
  private val corrupt = "pourline://recipe?v=1&d=a"
  private val invalid = RecipeShareCodec.uriFor(ByteWriter().apply { uvar(0); str("x"); uvar(0); uvar(0) }.toByteArray())
  private val foreign = "https://example.com"

  @Test
  fun `ok without a session opens`() {
    for (source in ImportSource.entries) {
      assertEquals(ImportOutcome.Open(ok), routeImport("  $ok ", source, hasSession = false))
    }
  }

  @Test
  fun `ok with a session defers`() {
    for (source in ImportSource.entries) {
      assertEquals(ImportOutcome.Defer(ok), routeImport(ok, source, hasSession = true))
    }
  }

  @Test
  fun `a null text from an image is no recipe in image`() {
    assertEquals(ImportOutcome.Fail(ImportError.NO_RECIPE_IN_IMAGE), routeImport(null, ImportSource.IMAGE, false))
    assertEquals(ImportOutcome.Fail(ImportError.NO_RECIPE_IN_IMAGE), routeImport(null, ImportSource.IMAGE, true))
  }

  @Test
  fun `not pourline from an image is no recipe in image`() {
    assertEquals(ImportOutcome.Fail(ImportError.NO_RECIPE_IN_IMAGE), routeImport(foreign, ImportSource.IMAGE, false))
    assertEquals(ImportOutcome.Fail(ImportError.NO_RECIPE_IN_IMAGE), routeImport("pourline://recipe", ImportSource.IMAGE, false))
  }

  @Test
  fun `not pourline from the scanner or a link is not pourline`() {
    for (source in listOf(ImportSource.SCANNER, ImportSource.LINK)) {
      assertEquals(ImportOutcome.Fail(ImportError.NOT_POURLINE), routeImport(foreign, source, false))
      assertEquals(ImportOutcome.Fail(ImportError.NOT_POURLINE), routeImport("pourline://recipe", source, true))
    }
  }

  @Test
  fun `newer, corrupt and invalid map to their errors`() {
    for (source in ImportSource.entries) {
      for (session in listOf(false, true)) {
        assertEquals(ImportOutcome.Fail(ImportError.NEWER_VERSION), routeImport(newer, source, session))
        assertEquals(ImportOutcome.Fail(ImportError.CORRUPT), routeImport(corrupt, source, session))
        assertEquals(ImportOutcome.Fail(ImportError.INVALID), routeImport(invalid, source, session))
      }
    }
  }

  @Test
  fun `a reader that is not ready maps to reader not ready`() {
    for (session in listOf(false, true)) {
      assertEquals(ImportOutcome.Fail(ImportError.READER_NOT_READY), routeImageRead(QrReadResult.ModelNotReady, session))
    }
  }

  @Test
  fun `an image read maps like its text`() {
    assertEquals(ImportOutcome.Open(ok), routeImageRead(QrReadResult.Found(ok), false))
    assertEquals(ImportOutcome.Defer(ok), routeImageRead(QrReadResult.Found(ok), true))
    assertEquals(ImportOutcome.Fail(ImportError.NO_RECIPE_IN_IMAGE), routeImageRead(QrReadResult.NoRecipe, false))
  }
}
