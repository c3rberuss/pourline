package dev.dotscale.brew

import org.junit.Assert.assertEquals
import org.junit.Test

class StabilityTest {
  private val cvZero = 0.5

  @Test
  fun `constant flow scores 100`() {
    assertEquals(100, stabilityScore(List(30) { 4.0 }, cvZero))
  }

  @Test
  fun `cv of 0_25 scores 50`() {
    // 3,0 y 5,0 alternos: media 4, σ 1.
    assertEquals(50, stabilityScore(List(30) { if (it % 2 == 0) 3.0 else 5.0 }, cvZero))
  }

  @Test
  fun `cv of 0_5 scores 0`() {
    assertEquals(0, stabilityScore(List(30) { if (it % 2 == 0) 2.0 else 6.0 }, cvZero))
  }

  @Test
  fun `cv above 0_5 scores 0`() {
    assertEquals(0, stabilityScore(List(30) { if (it % 2 == 0) 1.0 else 7.0 }, cvZero))
  }

  @Test
  fun `empty or non positive mean scores 0`() {
    assertEquals(0, stabilityScore(emptyList(), cvZero))
    assertEquals(0, stabilityScore(listOf(0.0, 0.0), cvZero))
  }

  @Test
  fun `levels follow the thresholds`() {
    val config = BrewConfig()
    assertEquals(StabilityLevel.STABLE, config.levelOf(75))
    assertEquals(StabilityLevel.IRREGULAR, config.levelOf(74))
    assertEquals(StabilityLevel.IRREGULAR, config.levelOf(50))
    assertEquals(StabilityLevel.TURBULENT, config.levelOf(49))
  }

  @Test
  fun `a window with less than 1000 ms of coverage has no score`() {
    val scale = FakeScale()
    scale.idle(500)
    // Rampa de 1000 ms + menos de 1000 ms de ventana: nunca hay puntuación.
    scale.pour(4.0, 1_900)
    assertEquals(null, scale.state.currentStability)
    assertEquals(true, scale.state.samples.all { it.stability == null })
  }
}
