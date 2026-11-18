package dev.josueamaya.pourline.recipe.share

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRecipeImportTest {
  private val a = GOLDEN_V60_URI
  private val b = WORST_CASE_URI

  private fun PendingRecipeImport.noticeOrNull(): ImportNotice? = runBlocking { withTimeoutOrNull(100) { notices.first() } }

  @Test
  fun `an opened import is pending without a notice`() {
    val imports = PendingRecipeImport()
    assertEquals(ImportOutcome.Open(a), imports.submit(a, ImportSource.LINK, hasSession = false))
    assertEquals(a, imports.pending.value)
    assertNull(imports.noticeOrNull())
  }

  @Test
  fun `a deferred import is pending and notifies deferred`() {
    val imports = PendingRecipeImport()
    imports.submit(a, ImportSource.LINK, hasSession = true)
    assertEquals(a, imports.pending.value)
    assertEquals(ImportNotice.Deferred, imports.noticeOrNull())
  }

  @Test
  fun `the last import replaces the pending one`() {
    val imports = PendingRecipeImport()
    imports.submit(a, ImportSource.LINK, hasSession = true)
    imports.submit(b, ImportSource.IMAGE, hasSession = true)
    assertEquals(b, imports.pending.value)
  }

  @Test
  fun `a failure notifies its error and leaves the pending one alone`() {
    val imports = PendingRecipeImport()
    imports.submit(a, ImportSource.LINK, hasSession = true)
    imports.noticeOrNull()
    imports.submit("https://example.com", ImportSource.SCANNER, hasSession = false)
    assertEquals(ImportNotice.Failed(ImportError.NOT_POURLINE), imports.noticeOrNull())
    assertEquals(a, imports.pending.value)
    imports.submit(QrReadResult.ModelNotReady, hasSession = false)
    assertEquals(ImportNotice.Failed(ImportError.READER_NOT_READY), imports.noticeOrNull())
    assertEquals(a, imports.pending.value)
  }

  @Test
  fun `consume only clears the same uri`() {
    val imports = PendingRecipeImport()
    imports.submit(a, ImportSource.LINK, hasSession = false)
    assertFalse(imports.consume(b))
    assertEquals(a, imports.pending.value)
    assertTrue(imports.consume(a))
    assertNull(imports.pending.value)
    assertFalse(imports.consume(a))
  }

  @Test
  fun `notices are kept until collected`() {
    val imports = PendingRecipeImport()
    imports.notify(ImportNotice.Added)
    imports.submit(a, ImportSource.LINK, hasSession = true)
    imports.submit("x", ImportSource.SCANNER, hasSession = false)
    val collected = runBlocking { imports.notices.take(3).toList() }
    assertEquals(listOf(ImportNotice.Added, ImportNotice.Deferred, ImportNotice.Failed(ImportError.NOT_POURLINE)), collected)
  }
}
