package dev.josueamaya.pourline.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BackupLegacyTest {
  @Test
  fun `a format 1 built in recipe becomes an own recipe with its catalog key`() {
    val rows = fullRows()
    val builtIn = rows.recipes[0]
    assertEquals("V60_CLASSIC", builtIn.builtInKey)
    assertEquals(true, builtIn.localizedNames)

    val converted = rows.withLegacyBuiltInsConverted().recipes[0]

    assertNull(converted.builtInKey)
    assertEquals("V60_CLASSIC", converted.catalogKey)
    assertEquals(false, converted.localizedNames)
    // El resto de la fila queda como estaba, sin traducir.
    assertEquals(builtIn.copy(builtInKey = null, catalogKey = "V60_CLASSIC", localizedNames = false), converted)
  }

  @Test
  fun `other recipes are untouched`() {
    val rows = fullRows()
    val converted = rows.withLegacyBuiltInsConverted()

    assertEquals(rows.recipes[1], converted.recipes[1])
    assertEquals(rows.copy(recipes = emptyList()), converted.copy(recipes = emptyList()))
    val own = rows.copy(recipes = rows.recipes.drop(1))
    assertSame(own.recipes[0], own.withLegacyBuiltInsConverted().recipes[0])
  }
}
