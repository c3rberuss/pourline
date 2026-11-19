package dev.josueamaya.pourline.share

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** Módulos en blanco alrededor del QR (la zona de silencio que pide el estándar). */
const val QR_QUIET_ZONE = 4

/** Matriz de módulos de un QR, sin zona de silencio. `true` es un módulo oscuro. */
class QrMatrix(val modules: Int, private val dark: BooleanArray) {
  init {
    require(dark.size == modules * modules) { "La matriz no es cuadrada" }
  }

  /** Celdas por lado con la zona de silencio: los módulos más [QR_QUIET_ZONE] a cada lado. */
  val cells: Int get() = modules + 2 * QR_QUIET_ZONE

  operator fun get(x: Int, y: Int): Boolean = dark[y * modules + x]
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
