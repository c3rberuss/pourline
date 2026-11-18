package dev.josueamaya.pourline.ui.session

import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.BrewState
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.session.PrepState
import dev.josueamaya.pourline.session.ScaleUiState
import dev.josueamaya.pourline.ui.tour.Tours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionToursTest {

  private val summary = BrewSummary(
    totalTimeMs = 120_000,
    totalWaterG = 250.0,
    avgFlowGps = 3.0,
    maxFlowGps = 5.0,
    stabilityScore = null,
    stabilityLevel = null,
    levelShare = emptyMap(),
    pours = emptyList(),
    finalRatio = null,
    reason = FinishReason.MANUAL,
    interrupted = false,
  )

  private fun armed(prep: PrepState? = null, scaleless: Boolean = false, countdownS: Int? = null) =
    ScaleUiState(brew = BrewState.Initial, prep = prep, scaleless = scaleless, countdownS = countdownS)

  private fun active(scaleless: Boolean = false) =
    ScaleUiState(brew = BrewState.Initial.copy(phase = BrewPhase.Active(pouring = true)), prep = PrepState(), scaleless = scaleless)

  private fun finished() =
    ScaleUiState(brew = BrewState.Initial.copy(phase = BrewPhase.Finished(summary, rearmed = false, pourIgnored = false)))

  @Test
  fun `prep steps with a scale give the prep tour`() {
    assertEquals(Tours.sessionPrep, sessionTour(armed(prep = PrepState())))
  }

  @Test
  fun `a scaleless armed session gives the scaleless tour`() {
    assertEquals(Tours.sessionScaleless, sessionTour(armed(scaleless = true)))
  }

  @Test
  fun `finished gives the summary tour`() {
    assertEquals(Tours.summary, sessionTour(finished()))
  }

  @Test
  fun `active gives no tour`() {
    assertNull(sessionTour(active()))
    assertNull(sessionTour(active(scaleless = true)))
  }

  @Test
  fun `armed without prep and not scaleless gives no tour`() {
    assertNull(sessionTour(armed(prep = null)))
  }

  @Test
  fun `a visible countdown disables the tour`() {
    assertTrue(sessionTourEnabled(armed(prep = PrepState())))
    assertFalse(sessionTourEnabled(armed(prep = PrepState(), countdownS = 3)))
  }

  @Test
  fun `active disables the tour`() {
    assertFalse(sessionTourEnabled(active()))
    assertTrue(sessionTourEnabled(finished()))
  }
}
