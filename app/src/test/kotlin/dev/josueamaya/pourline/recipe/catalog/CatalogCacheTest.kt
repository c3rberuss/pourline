package dev.josueamaya.pourline.recipe.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CatalogCacheTest {
  @get:Rule val folder = TemporaryFolder()

  private fun cache() = CatalogCache(File(folder.root, "catalog"))

  @Test
  fun `nothing is read before something is written`() {
    assertNull(cache().read())
  }

  @Test
  fun `a written catalog is read back with its time and etag`() {
    val cache = cache()
    cache.write("""{"a":1}""", "\"abc\"", 1234L)
    assertEquals(CachedCatalog("""{"a":1}""", 1234L, "\"abc\""), cache.read())
    cache.write("""{"b":2}""", null, 99L)
    assertEquals(CachedCatalog("""{"b":2}""", 99L, null), cache.read())
  }

  @Test
  fun `writing replaces the files without leaving temporary ones`() {
    val cache = cache()
    cache.write("uno", "e1", 1L)
    cache.write("dos", "e2", 2L)
    val names = File(folder.root, "catalog").list().orEmpty().sorted()
    assertEquals(listOf("recipes.json", "recipes.meta.json"), names)
    assertEquals("dos", cache.read()?.text)
  }

  @Test
  fun `an unreadable cache is reported and cleared`() {
    val dir = File(folder.root, "catalog")
    File(dir, "recipes.json").mkdirs()
    val cache = CatalogCache(dir)
    var reported = false
    try {
      cache.read()
    } catch (_: CatalogCacheUnreadable) {
      reported = true
    }
    assertTrue(reported)
    cache.clear()
    assertFalse(dir.exists())
    assertNull(cache.read())
  }

  @Test
  fun `marking a check keeps the text and updates the time and etag`() {
    val cache = cache()
    cache.write("texto", "e1", 10L)
    cache.markChecked(500L, "e2")
    assertEquals(CachedCatalog("texto", 500L, "e2"), cache.read())
  }

  @Test
  fun `marking a check without a catalog does nothing`() {
    val cache = cache()
    cache.markChecked(500L, "e2")
    assertNull(cache.read())
  }

  @Test
  fun `a damaged meta file reads as no time and no etag`() {
    val cache = cache()
    cache.write("texto", "e1", 10L)
    File(folder.root, "catalog/recipes.meta.json").writeText("esto no es json")
    assertEquals(CachedCatalog("texto", null, null), cache.read())
  }
}
