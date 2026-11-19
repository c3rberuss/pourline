package dev.josueamaya.pourline.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AntRateTest {
  @Test
  fun `tapping another star chooses it`() {
    assertEquals(4, rateAfterTap(current = null, tapped = 4, allowClear = true))
    assertEquals(2, rateAfterTap(current = 4, tapped = 2, allowClear = true))
  }

  @Test
  fun `tapping the marked star clears it only with allowClear`() {
    assertNull(rateAfterTap(current = 4, tapped = 4, allowClear = true))
    assertEquals(4, rateAfterTap(current = 4, tapped = 4, allowClear = false))
  }
}
