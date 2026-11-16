package dev.dotscale.coffee.label

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LabelScoreTest {
  @Test
  fun `parseExpected reads an empty date as null and an ISO date`() {
    val rows = parseExpected("a.jpg\tPacamara\t\nb.jpg\tGuji\t2026-10-12\nc.jpg\tKenia\n")
    assertEquals(3, rows.size)
    assertNull(rows[0].roastDate)
    assertEquals(LocalDate.of(2026, 10, 12), rows[1].roastDate)
    assertNull(rows[2].roastDate)
  }

  @Test
  fun `parseExpected reports the line of a malformed row`() {
    val noName = assertThrows(IllegalArgumentException::class.java) { parseExpected("a.jpg\tx\t\nb.jpg\n") }
    assertTrue(noName.message!!, noName.message!!.startsWith("línea 2"))
    val badDate = assertThrows(IllegalArgumentException::class.java) { parseExpected("a.jpg\tx\t12/10/2026") }
    assertTrue(badDate.message!!, badDate.message!!.startsWith("línea 1"))
  }

  @Test
  fun `names are compared without case accents or spaces`() {
    assertTrue(sameName("Pacamara Red Honey", "PACAMARA RED HONEY"))
    assertTrue(sameName("Pacamara Red Honey", "pacamara redhoney"))
    assertTrue(sameName("Etiopía", "Etiopia"))
    assertFalse(sameName("Etiopía", "Kenia"))
    assertFalse(sameName("Etiopía", null))
  }

  private fun expected(total: Int = 15, handwritten: Int = 2) = List(total) { i ->
    LabelExpectation("$i.jpg", "Café $i", if (i < handwritten) LocalDate.of(2026, 10, 1 + i) else null)
  }

  private fun parsed(expected: List<LabelExpectation>, wrong: Set<Int> = emptySet(), date: Map<Int, LocalDate> = emptyMap()) =
    expected.mapIndexed { i, e ->
      e.file to LabelFields(name = if (i in wrong) "otro" else e.name.uppercase(), roastDate = date[i])
    }.toMap()

  @Test
  fun `twelve names and no invented date meet the target`() {
    val e = expected()
    val score = score(e, parsed(e, wrong = setOf(12, 13, 14)))
    assertEquals(12, score.nameHits)
    assertTrue(score.inventedDates.isEmpty())
    assertTrue(score.meetsTarget())
  }

  @Test
  fun `eleven names do not meet the target`() {
    val e = expected()
    assertFalse(score(e, parsed(e, wrong = setOf(11, 12, 13, 14))).meetsTarget())
  }

  @Test
  fun `a date read on a label without one is reported and fails`() {
    val e = expected()
    val score = score(e, parsed(e, date = mapOf(5 to LocalDate.of(2026, 9, 1))))
    assertEquals(listOf("5.jpg: 2026-09-01"), score.inventedDates)
    assertFalse(score.meetsTarget())
    assertEquals(13, score.noDateTotal)
  }

  @Test
  fun `handwritten dates read wrong are only reported`() {
    val e = expected()
    val score = score(e, parsed(e, date = mapOf(0 to LocalDate.of(2026, 1, 1))))
    assertTrue(score.inventedDates.isEmpty())
    assertTrue(score.meetsTarget())
    assertEquals(2, score.handwritten.size)
    assertEquals(LocalDate.of(2026, 1, 1), score.handwritten[0].read)
    assertTrue(score.toReport().contains("0.jpg: leída 2026-01-01, esperada 2026-10-01"))
  }
}
