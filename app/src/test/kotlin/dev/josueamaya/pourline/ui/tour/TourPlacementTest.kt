package dev.josueamaya.pourline.ui.tour

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourPlacementTest {

  private val safe = Rect(0f, 100f, 1000f, 2000f)
  private val balloon = Size(600f, 400f)
  private val gap = 24f

  private fun inside(placement: BalloonPlacement, safe: Rect = this.safe, size: Size = balloon): Boolean {
    val r = Rect(placement.offset, size)
    return r.left >= safe.left && r.right <= safe.right && r.top >= safe.top && r.bottom <= safe.bottom
  }

  @Test
  fun `without a hole the balloon is centered in the safe area`() {
    val p = placeBalloon(null, balloon, safe, gap)

    assertEquals(BalloonSide.CENTER, p.side)
    assertEquals(Offset(200f, 850f), p.offset)
    assertNull(p.arrow)
  }

  @Test
  fun `below when it fits`() {
    val hole = Rect(100f, 300f, 400f, 400f)

    val p = placeBalloon(hole, balloon, safe, gap)

    assertEquals(BalloonSide.BELOW, p.side)
    assertEquals(hole.bottom + gap, p.offset.y, 0f)
  }

  @Test
  fun `above when it does not fit below`() {
    val hole = Rect(100f, 1500f, 400f, 1700f)

    val p = placeBalloon(hole, balloon, safe, gap)

    assertEquals(BalloonSide.ABOVE, p.side)
    assertEquals(hole.top - gap - balloon.height, p.offset.y, 0f)
  }

  @Test
  fun `beside when it fits neither above nor below (landscape)`() {
    val land = Rect(0f, 0f, 2000f, 500f)
    val hole = Rect(100f, 150f, 500f, 350f)

    val p = placeBalloon(hole, balloon, land, gap)

    assertEquals(BalloonSide.END, p.side)
    assertEquals(hole.right + gap, p.offset.x, 0f)
    assertTrue(inside(p, land))

    val holeRight = Rect(1500f, 150f, 1900f, 350f)
    val q = placeBalloon(holeRight, balloon, land, gap)
    assertEquals(BalloonSide.START, q.side)
    assertEquals(holeRight.left - gap - balloon.width, q.offset.x, 0f)
    assertTrue(inside(q, land))
  }

  @Test
  fun `x is centered on the hole and clamped to the safe area`() {
    val centeredHole = Rect(450f, 300f, 550f, 400f)
    assertEquals(200f, placeBalloon(centeredHole, balloon, safe, gap).offset.x, 0f)

    val leftHole = Rect(0f, 300f, 100f, 400f)
    assertEquals(0f, placeBalloon(leftHole, balloon, safe, gap).offset.x, 0f)

    val rightHole = Rect(900f, 300f, 1000f, 400f)
    assertEquals(400f, placeBalloon(rightHole, balloon, safe, gap).offset.x, 0f)
  }

  @Test
  fun `the balloon never leaves the safe area even if it covers the hole`() {
    val hole = Rect(20f, 120f, 980f, 1980f)

    val p = placeBalloon(hole, balloon, safe, gap)

    assertTrue(inside(p))
    assertNull(p.arrow)
  }

  @Test
  fun `the arrow points to the hole center within the balloon edge`() {
    val hole = Rect(100f, 300f, 140f, 400f)
    val p = placeBalloon(hole, balloon, safe, gap, arrowInset = 20f)

    assertNotNull(p.arrow)
    val arrow = p.arrow!!
    assertEquals(BalloonSide.BELOW, p.side)
    // El hueco queda cerca de la esquina: la flecha se queda a `arrowInset` del borde.
    assertEquals(p.offset.y, arrow.y, 0f)
    assertTrue(arrow.x >= p.offset.x + 20f && arrow.x <= p.offset.x + balloon.width - 20f)

    val centered = placeBalloon(Rect(450f, 300f, 550f, 400f), balloon, safe, gap, arrowInset = 20f)
    assertEquals(500f, centered.arrow!!.x, 0f)

    val above = placeBalloon(Rect(450f, 1500f, 550f, 1700f), balloon, safe, gap)
    assertEquals(above.offset.y + balloon.height, above.arrow!!.y, 0f)
  }
}
