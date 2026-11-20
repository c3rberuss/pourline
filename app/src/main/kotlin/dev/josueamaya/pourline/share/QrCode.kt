package dev.josueamaya.pourline.share

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** Módulos en blanco alrededor del QR (la zona de silencio que pide el estándar). */
const val QR_QUIET_ZONE = 4

/** Lado, en módulos, de cada uno de los tres patrones de posición (los "ojos") de un QR. */
const val QR_EYE_SIZE = 7

/** Radios de referencia de los ojos redondeados, en módulos (los de la maqueta aprobada, enmienda 2). */
private const val EYE_OUTER_RADIUS_MODULES = 2.1
private const val EYE_HOLE_RADIUS_MODULES = 1.3
private const val EYE_CENTER_RADIUS_MODULES = 0.9

/**
 * Radios, en píxeles enteros, de las tres piezas redondeadas de un ojo: el borde exterior del anillo ([outer]), el hueco
 * del anillo ([hole]) y el centro de 3 × 3 módulos ([center]).
 */
data class QrEyeRadii(val outer: Int, val hole: Int, val center: Int)

/** Los radios de los ojos con módulos de [modulePx] píxeles: los de referencia redondeados al entero más cercano. */
fun qrEyeRadii(modulePx: Int): QrEyeRadii = QrEyeRadii(
  outer = Math.round(EYE_OUTER_RADIUS_MODULES * modulePx).toInt(),
  hole = Math.round(EYE_HOLE_RADIUS_MODULES * modulePx).toInt(),
  center = Math.round(EYE_CENTER_RADIUS_MODULES * modulePx).toInt(),
)

/** Matriz de módulos de un QR, sin zona de silencio. `true` es un módulo oscuro. */
class QrMatrix(val modules: Int, private val dark: BooleanArray) {
  init {
    require(dark.size == modules * modules) { "La matriz no es cuadrada" }
  }

  /** Celdas por lado con la zona de silencio: los módulos más [QR_QUIET_ZONE] a cada lado. */
  val cells: Int get() = modules + 2 * QR_QUIET_ZONE

  operator fun get(x: Int, y: Int): Boolean = dark[y * modules + x]

  /** La esquina superior izquierda de cada ojo, en módulos y sin zona de silencio: arriba a la izquierda, arriba a la derecha y abajo a la izquierda. */
  val eyes: List<Pair<Int, Int>> = listOf(0 to 0, modules - QR_EYE_SIZE to 0, 0 to modules - QR_EYE_SIZE)

  /** Si el módulo ([x], [y]) pertenece a uno de los tres ojos de 7 × 7 módulos. */
  fun isEye(x: Int, y: Int): Boolean = eyes.any { (ex, ey) -> x in ex until ex + QR_EYE_SIZE && y in ey until ey + QR_EYE_SIZE }
}

/**
 * El QR de [text] con corrección de errores M. El texto es ASCII (un URI), así que zxing elige el modo byte sin
 * ECI y cualquier lector lo entiende.
 */
fun qrMatrix(text: String): QrMatrix {
  val code = Encoder.encode(text, ErrorCorrectionLevel.M)
  val bytes = code.matrix
  val size = bytes.width
  val dark = BooleanArray(size * size) { bytes.get(it % size, it / size).toInt() == 1 }
  return QrMatrix(size, dark)
}

/** La versión del QR (1 a 40) de [text] con corrección M. */
fun qrVersion(text: String): Int = Encoder.encode(text, ErrorCorrectionLevel.M).version.versionNumber
