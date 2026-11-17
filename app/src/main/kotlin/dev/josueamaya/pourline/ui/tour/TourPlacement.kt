package dev.josueamaya.pourline.ui.tour

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/** Por qué lado del hueco va el globo. */
enum class BalloonSide { BELOW, ABOVE, END, START, CENTER }

/**
 * Dónde va el globo: la esquina superior izquierda ([offset]), el lado del hueco en que quedó ([side]) y el punto del
 * borde del globo, en las mismas coordenadas, de donde sale la flecha ([arrow]; `null` sin hueco o si el globo lo tapa).
 */
data class BalloonPlacement(val offset: Offset, val side: BalloonSide, val arrow: Offset?)

/**
 * Coloca un globo de tamaño [balloon] respecto a [hole] sin salirse de [safe] (D16). En orden: sin hueco, centrado;
 * debajo, si cabe; encima, si cabe; a la derecha; a la izquierda. Si no cabe en ningún lado, el lado vertical con
 * más espacio, dentro de [safe] aunque tape parte del hueco.
 *
 * Debajo y encima el globo se centra en el hueco en horizontal; a los lados, en vertical. La flecha apunta al centro
 * del hueco sin acercarse a menos de [arrowInset] de una esquina del globo.
 */
fun placeBalloon(hole: Rect?, balloon: Size, safe: Rect, gap: Float, arrowInset: Float = gap): BalloonPlacement {
  if (hole == null) {
    val offset = Offset(centered(safe.center.x, balloon.width, safe.left, safe.right), centered(safe.center.y, balloon.height, safe.top, safe.bottom))
    return BalloonPlacement(offset, BalloonSide.CENTER, null)
  }

  val x = centered(hole.center.x, balloon.width, safe.left, safe.right)
  val y = centered(hole.center.y, balloon.height, safe.top, safe.bottom)
  val belowTop = hole.bottom + gap
  val aboveTop = hole.top - gap - balloon.height

  val (offset, side) = when {
    belowTop + balloon.height <= safe.bottom -> Offset(x, belowTop) to BalloonSide.BELOW
    aboveTop >= safe.top -> Offset(x, aboveTop) to BalloonSide.ABOVE
    hole.right + gap + balloon.width <= safe.right -> Offset(hole.right + gap, y) to BalloonSide.END
    hole.left - gap - balloon.width >= safe.left -> Offset(hole.left - gap - balloon.width, y) to BalloonSide.START
    else -> {
      // No cabe en ningún lado: el lado vertical con más espacio, dentro de la zona segura.
      val spaceBelow = safe.bottom - belowTop
      val spaceAbove = hole.top - gap - safe.top
      if (spaceBelow >= spaceAbove) {
        Offset(x, belowTop.coerceIn(safe.top, maxOf(safe.top, safe.bottom - balloon.height))) to BalloonSide.BELOW
      } else {
        Offset(x, aboveTop.coerceIn(safe.top, maxOf(safe.top, safe.bottom - balloon.height))) to BalloonSide.ABOVE
      }
    }
  }

  val bounds = Rect(offset, balloon)
  val arrow = if (bounds.overlaps(hole)) null else arrowPoint(hole, bounds, side, arrowInset)
  return BalloonPlacement(offset, side, arrow)
}

/** El comienzo de un intervalo de longitud [length] centrado en [center] y recortado a `[min, max]`. */
private fun centered(center: Float, length: Float, min: Float, max: Float): Float =
  (center - length / 2).coerceIn(min, maxOf(min, max - length))

/** El punto del borde del globo más cercano al centro del hueco, lejos de las esquinas. */
private fun arrowPoint(hole: Rect, balloon: Rect, side: BalloonSide, inset: Float): Offset {
  val along = { value: Float, from: Float, to: Float ->
    if (to - from <= 2 * inset) (from + to) / 2 else value.coerceIn(from + inset, to - inset)
  }
  return when (side) {
    BalloonSide.BELOW -> Offset(along(hole.center.x, balloon.left, balloon.right), balloon.top)
    BalloonSide.ABOVE -> Offset(along(hole.center.x, balloon.left, balloon.right), balloon.bottom)
    BalloonSide.END -> Offset(balloon.left, along(hole.center.y, balloon.top, balloon.bottom))
    BalloonSide.START -> Offset(balloon.right, along(hole.center.y, balloon.top, balloon.bottom))
    BalloonSide.CENTER -> balloon.center
  }
}
