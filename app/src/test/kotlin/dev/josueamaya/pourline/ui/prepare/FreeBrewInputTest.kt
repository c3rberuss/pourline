package dev.josueamaya.pourline.ui.prepare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los rangos de la dosis y el ratio de Preparar (D27). */
class FreeBrewInputTest {

  @Test
  fun theDoseGoesFrom5To60WithOneDecimal() {
    assertEquals(5.0, parseFreeDose("5")!!, 1e-9)
    assertEquals(60.0, parseFreeDose("60.0")!!, 1e-9)
    assertEquals(15.5, parseFreeDose("15,5")!!, 1e-9)
    assertNull(parseFreeDose("4.9"))
    assertNull(parseFreeDose("60.1"))
    assertNull(parseFreeDose("15.55"))
  }

  @Test
  fun theRatioGoesFrom10To20WithOneDecimal() {
    assertEquals(10.0, parseFreeRatio("10")!!, 1e-9)
    assertEquals(20.0, parseFreeRatio("20,0")!!, 1e-9)
    assertEquals(16.7, parseFreeRatio("16.7")!!, 1e-9)
    assertNull(parseFreeRatio("9.9"))
    assertNull(parseFreeRatio("20.1"))
    assertNull(parseFreeRatio("16.25"))
  }

  @Test
  fun textThatIsNotANumberIsRejected() {
    listOf("", " ", "abc", "1e", "NaN", "Infinity", "-").forEach {
      assertNull("«$it» no es un número", parseFreeDose(it))
      assertNull("«$it» no es un número", parseFreeRatio(it))
    }
  }

  @Test
  fun surroundingSpacesAreIgnored() {
    assertEquals(18.0, parseFreeDose(" 18 ")!!, 1e-9)
  }
}
