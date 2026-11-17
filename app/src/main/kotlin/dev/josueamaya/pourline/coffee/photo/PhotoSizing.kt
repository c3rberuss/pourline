package dev.josueamaya.pourline.coffee.photo

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Lado mayor de la foto guardada. */
const val PHOTO_MAX_SIDE = 1600

/** Lado de la miniatura cuadrada. */
const val THUMB_SIDE = 256

/** Mayor potencia de 2 con la que el lado mayor sigue siendo al menos [maxSide]: decodifica barato sin quedar corto. */
fun inSampleSize(width: Int, height: Int, maxSide: Int): Int {
  var sample = 1
  val longest = max(width, height)
  while (longest / (sample * 2) >= maxSide) sample *= 2
  return sample
}

/** Tamaño con el lado mayor reducido a [maxSide], sin ampliar. */
fun scaledSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
  val longest = max(width, height)
  if (longest <= maxSide) return width to height
  val scale = maxSide.toDouble() / longest
  return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/** El cuadrado central de una imagen: esquina superior izquierda y lado. */
data class SquareCrop(val left: Int, val top: Int, val side: Int)

fun centerSquare(width: Int, height: Int): SquareCrop {
  val side = min(width, height)
  return SquareCrop((width - side) / 2, (height - side) / 2, side)
}

/** Giro en grados (sentido horario) de un valor EXIF de orientación 1–8; las variantes espejo se tratan como su giro. */
fun exifRotationDegrees(orientation: Int): Int = when (orientation) {
  3, 4 -> 180
  6, 7 -> 90
  5, 8 -> 270
  else -> 0
}
