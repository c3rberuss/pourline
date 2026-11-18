package dev.josueamaya.pourline

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeIntentsTest {
  @Test
  fun `a fresh launch is handled`() {
    assertTrue(shouldHandleLaunchIntent(recreated = false, intentFlags = 0))
    assertTrue(shouldHandleLaunchIntent(recreated = false, intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  @Test
  fun `a recreated activity is not handled again`() {
    assertFalse(shouldHandleLaunchIntent(recreated = true, intentFlags = 0))
  }

  @Test
  fun `a launch from recents is not handled again`() {
    assertFalse(shouldHandleLaunchIntent(recreated = false, intentFlags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
    assertFalse(shouldHandleLaunchIntent(recreated = false, intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
  }
}
