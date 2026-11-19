package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MethodLevelsTest {
  private val entries = GrinderCatalogFile.parse(File("src/main/assets/grinders/catalog.json").readText()).getOrThrow().entries

  @Test
  fun `METHOD_LEVELS matches the catalog`() {
    val computed = methodLevels(entries)
    assertEquals(computed.keys, METHOD_LEVELS.keys)
    for ((method, level) in computed) assertEquals(method.name, level, METHOD_LEVELS.getValue(method), 1e-9)
  }

  @Test
  fun `methods without any predefined range have no level`() {
    assertFalse(BrewMethod.OTHER in methodLevels(entries))
    assertFalse(BrewMethod.OTHER in METHOD_LEVELS)
  }
}
