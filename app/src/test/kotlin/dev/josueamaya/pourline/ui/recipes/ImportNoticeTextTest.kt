package dev.josueamaya.pourline.ui.recipes

import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.recipe.share.ImportError
import dev.josueamaya.pourline.recipe.share.ImportNotice
import dev.josueamaya.pourline.ui.components.AntStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportNoticeTextTest {
  private val every: List<ImportNotice> =
    listOf(ImportNotice.Added, ImportNotice.Deferred) + ImportError.entries.map { ImportNotice.Failed(it) }

  @Test
  fun `every notice has its own string`() {
    val strings = every.map { importNoticeText(it) }
    assertEquals("cada aviso necesita un texto distinto", strings.size, strings.toSet().size)
    assertEquals(R.string.recipe_import_added, importNoticeText(ImportNotice.Added))
    assertEquals(R.string.recipe_import_deferred, importNoticeText(ImportNotice.Deferred))
    assertEquals(R.string.recipe_import_reader_preparing, importNoticeText(ImportNotice.Failed(ImportError.READER_NOT_READY)))
    assertEquals(R.string.recipe_import_scanner_unavailable, importNoticeText(ImportNotice.Failed(ImportError.SCANNER_UNAVAILABLE)))
  }

  @Test
  fun `added is a success, deferred is info and every failure is an error`() {
    assertEquals(AntStatus.Success, importNoticeStatus(ImportNotice.Added))
    assertEquals(AntStatus.Info, importNoticeStatus(ImportNotice.Deferred))
    for (error in ImportError.entries) assertEquals(AntStatus.Error, importNoticeStatus(ImportNotice.Failed(error)))
  }
}
