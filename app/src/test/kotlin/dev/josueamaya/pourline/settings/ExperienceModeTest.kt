package dev.josueamaya.pourline.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceModeTest {

  @Test
  fun `every expert feature is hidden in basic and shown in expert`() {
    for (feature in ExpertFeature.entries) {
      assertFalse("$feature should be hidden in BASIC", ExperienceMode.BASIC.shows(feature))
      assertTrue("$feature should show in EXPERT", ExperienceMode.EXPERT.shows(feature))
    }
  }

  @Test
  fun `the table covers the features of SPEC-005 and the grind ruler of SPEC-006`() {
    assertEquals(17, ExpertFeature.entries.size)
    assertEquals(
      setOf(
        "SESSION_CHART_PAGE", "SESSION_DIAL_INSIGHTS", "SESSION_STABILITY", "SESSION_DEVIATION", "TARGET_FROM_BREW", "SUMMARY_FLOW",
        "SUMMARY_STABILITY", "SUMMARY_FIT", "SUMMARY_FINISH_REASON", "BREW_DETAIL_CHART", "BREW_DETAIL_FIT",
        "BREW_COMPARE", "BREW_SAVE_AS_RECIPE", "HISTORY_STABILITY_PROGRESS", "HISTORY_ROW_METRICS",
        "RECIPE_CURVE_PREVIEW", "GRIND_RULER",
      ),
      ExpertFeature.entries.map { it.name }.toSet(),
    )
  }
}
