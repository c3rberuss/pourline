package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.grinder.GrinderFixtures.cinco
import dev.josueamaya.pourline.grinder.GrinderFixtures.inverso
import dev.josueamaya.pourline.grinder.GrinderFixtures.tres
import dev.josueamaya.pourline.grinder.GrinderFixtures.uno
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrindRulerModelTest {
  @Test
  fun coarseIsOnTheLeftInBothDirections() {
    assertEquals(0f, GrindRulerModel.fraction(40.0, uno), 1e-6f)
    assertEquals(1f, GrindRulerModel.fraction(0.0, uno), 1e-6f)
    assertEquals(0.5f, GrindRulerModel.fraction(20.0, uno), 1e-6f)
    // Invertido: el máximo es el extremo fino (derecha).
    assertEquals(1f, GrindRulerModel.fraction(50.0, inverso), 1e-6f)
    assertEquals(0f, GrindRulerModel.fraction(0.0, inverso), 1e-6f)
  }

  @Test
  fun simpleGrinderHasMajorTicksWithLabelsAndMinorOnesBetween() {
    val ticks = GrindRulerModel.ticks(uno)
    val majors = ticks.filter { it.major }
    assertTrue("${majors.size} etiquetas", majors.size <= 8)
    assertEquals("0", majors.first().label)
    assertEquals("40", majors.last().label)
    assertTrue(ticks.any { !it.major && it.label == null })
    assertEquals(0.0, ticks.first().value, 0.0)
    assertEquals(40.0, ticks.last().value, 0.0)
  }

  @Test
  fun compoundGrinderPutsTheMajorTicksOnThePrimaryUnit() {
    val ticks = GrindRulerModel.ticks(tres)
    val majors = ticks.filter { it.major }
    // 200 clics = 5 rotaciones (40 clics cada una): marcas en 0, 40, 80, 120, 160 y 200, con el número de rotación.
    assertEquals(listOf(0.0, 40.0, 80.0, 120.0, 160.0, 200.0), majors.map { it.value })
    assertEquals(listOf("0", "1", "2", "3", "4", "5"), majors.map { it.label })
  }

  @Test
  fun neverMoreThanEightLabels() {
    val wide = uno.copy(scaleMax = 1000.0)
    assertTrue(GrindRulerModel.ticks(wide).count { it.label != null } <= 8)
    assertTrue(GrindRulerModel.ticks(cinco).count { it.label != null } <= 8)
    val longCompound = tres.copy(scaleMax = 40.0 * 30)
    assertTrue(GrindRulerModel.ticks(longCompound).count { it.label != null } <= 8)
  }
}
