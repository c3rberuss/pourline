package dev.dotscale.coffee.label

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LabelDatesTest {
  private val today = LocalDate.of(2026, 11, 13)

  private fun dates(vararg lines: String) = LabelDates.findAll(lines.toList(), today)

  private fun pick(vararg lines: String) = LabelDates.pick(dates(*lines), today)

  @Test
  fun `numeric and text formats`() {
    val oct12 = LocalDate.of(2026, 10, 12)
    assertEquals(oct12, pick("12/10/2026"))
    assertEquals(oct12, pick("12/10/26"))
    assertEquals(oct12, pick("2026-10-12"))
    assertEquals(oct12, pick("12 oct 2026"))
    assertEquals(oct12, pick("Oct 12, 2026"))
    assertEquals(oct12, pick("12 de octubre de 2026"))
    assertEquals(oct12, pick("12-OCT-26"))
  }

  @Test
  fun `dd mm is preferred and mm dd is the fallback`() {
    assertEquals(LocalDate.of(2026, 4, 3), pick("03/04/26"))
    assertEquals(LocalDate.of(2026, 10, 25), pick("10/25/2026"))
  }

  @Test
  fun `an impossible date is discarded`() {
    assertTrue(dates("31/02/2026").isEmpty())
  }

  @Test
  fun `with several dates the most recent not in the future wins`() {
    assertEquals(LocalDate.of(2026, 10, 12), pick("Tostado 12/10/2026", "Consumir antes 12/04/2027", "01/01/2026"))
  }

  @Test
  fun `only future dates give null`() {
    assertNull(pick("Consumir antes 12/04/2027"))
  }

  @Test
  fun `compact and day month formats need a date label`() {
    assertEquals(LocalDate.of(2026, 9, 12), pick("Fecha de tueste: 120926"))
    assertEquals(LocalDate.of(2026, 10, 12), pick("Tostado: 12.10"))
  }

  @Test
  fun `a day month that would be in the future is last year`() {
    assertEquals(LocalDate.of(2025, 12, 20), pick("Tostado: 20.12"))
  }

  @Test
  fun `the label can be on the previous line`() {
    assertEquals(LocalDate.of(2026, 9, 12), pick("Fecha de tueste", "120926"))
  }

  @Test
  fun `without a label nothing is read`() {
    assertTrue(dates("120926").isEmpty())
    assertTrue(dates("Lote 120926").isEmpty())
    assertTrue(dates("12.10").isEmpty())
    assertTrue(dates("12.5 oz").isEmpty())
  }

  @Test
  fun `deceptive years are not roast dates`() {
    assertTrue(dates("EST. 2021").isEmpty())
    assertTrue(dates("EST. 2013").isEmpty())
    assertTrue(dates("2024: 7°").isEmpty())
    assertTrue(dates("Premio 2024").isEmpty())
    assertTrue(dates("Since 2013").isEmpty())
    assertTrue(dates("Premio 12/10/2024").isEmpty())
  }
}
