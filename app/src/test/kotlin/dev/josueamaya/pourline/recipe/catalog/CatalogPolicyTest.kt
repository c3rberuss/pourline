package dev.josueamaya.pourline.recipe.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPolicyTest {
  private val hour = 60 * 60 * 1000L
  private val minute = 60 * 1000L

  private fun catalog(revision: Int) = parsed(catalogJson(recipeJson("a"), revision = revision))

  @Test
  fun `a download older than 24 hours or missing is stale`() {
    val now = 1_000 * hour
    assertFalse(CatalogPolicy.isStale(now - (24 * hour - minute), now))
    assertTrue(CatalogPolicy.isStale(now - 24 * hour, now))
    assertTrue(CatalogPolicy.isStale(now - 25 * hour, now))
    assertTrue(CatalogPolicy.isStale(null, now))
    assertFalse(CatalogPolicy.isStale(now, now))
  }

  @Test
  fun `a clock that went backwards is stale`() {
    val now = 1_000 * hour
    assertTrue(CatalogPolicy.isStale(now + minute, now))
  }

  @Test
  fun `the higher revision wins and a tie keeps the cache`() {
    val cached = catalog(3)
    val newerBundled = catalog(4)
    val olderBundled = catalog(2)
    val tiedBundled = catalog(3)
    assertEquals(4, CatalogPolicy.choose(cached, newerBundled)?.revision)
    assertSame(cached, CatalogPolicy.choose(cached, olderBundled))
    assertSame(cached, CatalogPolicy.choose(cached, tiedBundled))
  }

  @Test
  fun `a missing copy falls back to the other one`() {
    val only = catalog(1)
    assertSame(only, CatalogPolicy.choose(only, null))
    assertSame(only, CatalogPolicy.choose(null, only))
    assertNull(CatalogPolicy.choose(null, null))
  }
}
