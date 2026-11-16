package dev.dotscale.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class AntDatePickerTest {
  @Test
  fun `round trip does not depend on the default time zone`() {
    val original = TimeZone.getDefault()
    try {
      val date = LocalDate.of(2026, 10, 12)
      listOf("UTC", "America/Lima", "Pacific/Kiritimati", "Pacific/Pago_Pago").forEach { id ->
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        assertEquals(id, date, utcMillisToLocalDate(date.toUtcMillis()))
      }
    } finally {
      TimeZone.setDefault(original)
    }
  }

  @Test
  fun `a date maps to its UTC midnight`() {
    assertEquals(1_791_763_200_000L, LocalDate.of(2026, 10, 12).toUtcMillis())
  }
}
