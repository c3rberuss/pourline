package dev.josueamaya.pourline.share

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Mode
import com.google.zxing.qrcode.encoder.Encoder
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {
  private val uri = "pourline://recipe?v=1&d=" + "AbC-_123".repeat(30)

  @Test
  fun `the matrix decodes back to the same text with zxing`() {
    val bitmap = binary(qrMatrix(uri))
    assertEquals(uri, QRCodeReader().decode(bitmap).text)
  }

  @Test
  fun `the error correction level is M`() {
    assertEquals(ErrorCorrectionLevel.M, Encoder.encode(uri, ErrorCorrectionLevel.M).ecLevel)
    // qrMatrix usa el mismo nivel: su versión coincide con la del codificador con M.
    assertEquals(Encoder.encode(uri, ErrorCorrectionLevel.M).version.versionNumber, qrVersion(uri))
  }

  @Test
  fun `an ascii uri uses byte mode without eci`() {
    val code = Encoder.encode(uri, ErrorCorrectionLevel.M)
    assertEquals(Mode.BYTE, code.mode)
    // Sin ECI: el lector devuelve el texto tal cual, sin tabla de caracteres declarada.
    assertEquals(uri, QRCodeReader().decode(binary(qrMatrix(uri))).text)
  }

  @Test
  fun `the version grows with the length`() {
    val short = qrVersion("pourline://recipe?v=1&d=" + "a".repeat(20))
    val long = qrVersion("pourline://recipe?v=1&d=" + "a".repeat(600))
    assertTrue("$short < $long", short < long)
    assertEquals(4 * short + 17, qrMatrix("pourline://recipe?v=1&d=" + "a".repeat(20)).modules)
  }

  @Test
  fun `the box side is the modules plus four quiet modules per side`() {
    for (version in listOf(1, 13, 21)) {
      val modules = 4 * version + 17
      val matrix = QrMatrix(modules, BooleanArray(modules * modules))
      assertEquals(modules + 8, matrix.cells)
    }
    assertEquals(77, QrMatrix(69, BooleanArray(69 * 69)).cells)
    assertEquals(109, QrMatrix(101, BooleanArray(101 * 101)).cells)
  }

  private val sizes = listOf(40, 150, 331, 600)

  private fun matrixOfLength(length: Int) = qrMatrix("pourline://recipe?v=1&d=" + "AbC-_123".repeat(length / 8 + 1).take(length - 24))

  @Test
  fun `the three eyes are the finder patterns of the matrix`() {
    for (length in sizes) {
      val matrix = matrixOfLength(length)
      assertEquals(3, matrix.eyes.size)
      for ((ex, ey) in matrix.eyes) {
        for (dy in -1..QR_EYE_SIZE) for (dx in -1..QR_EYE_SIZE) {
          val x = ex + dx
          val y = ey + dy
          if (x !in 0 until matrix.modules || y !in 0 until matrix.modules) continue
          val inside = dx in 0 until QR_EYE_SIZE && dy in 0 until QR_EYE_SIZE
          val expected = when {
            !inside -> false // fila y columna de separación
            dx == 0 || dy == 0 || dx == QR_EYE_SIZE - 1 || dy == QR_EYE_SIZE - 1 -> true // borde de 7 × 7
            dx in 2..4 && dy in 2..4 -> true // centro de 3 × 3
            else -> false // hueco
          }
          assertEquals("longitud $length, ojo ($ex, $ey) módulo ($dx, $dy)", expected, matrix[x, y])
        }
      }
    }
  }

  @Test
  fun `only the three 7 by 7 corners are eye modules`() {
    for (length in sizes) {
      val matrix = matrixOfLength(length)
      var count = 0
      for (y in 0 until matrix.modules) for (x in 0 until matrix.modules) {
        val inEye = matrix.eyes.any { (ex, ey) -> x in ex until ex + QR_EYE_SIZE && y in ey until ey + QR_EYE_SIZE }
        assertEquals("módulo ($x, $y) con longitud $length", inEye, matrix.isEye(x, y))
        if (matrix.isEye(x, y)) count++
      }
      assertEquals(147, count)
    }
    assertFalse(QrMatrix(21, BooleanArray(21 * 21)).isEye(10, 10))
  }

  @Test
  fun `the eye radii are 13, 8 and 5 px with 6 px modules`() {
    assertEquals(QrEyeRadii(outer = 13, hole = 8, center = 5), qrEyeRadii(6))
    for (modulePx in listOf(4, 6, 8)) {
      val radii = qrEyeRadii(modulePx)
      assertTrue("outer − hole ≤ $modulePx: $radii", radii.outer - radii.hole <= modulePx)
      assertTrue("el anillo (${7 * modulePx} px) aloja su radio exterior: $radii", radii.outer * 2 <= 7 * modulePx)
      assertTrue("el hueco (${5 * modulePx} px) aloja su radio: $radii", radii.hole * 2 <= 5 * modulePx)
      assertTrue("el centro (${3 * modulePx} px) aloja su radio: $radii", radii.center * 2 <= 3 * modulePx)
    }
  }

  @Test
  fun `a raster in the brand colors decodes with zxing`() {
    val text = "pourline://recipe?v=1&d=" + "AbC-_123".repeat(80).take(576)
    assertEquals(600, text.length)
    val matrix = qrMatrix(text)
    for (ring in listOf(SHARE_QR_EYE_RING.toArgb(), SHARE_QR_DARK.toArgb())) {
      assertEquals(text, QRCodeReader().decode(brandBitmap(matrix, ring)).text)
    }
  }

  /** Si el centro del píxel ([px], [py]) cae dentro del rectángulo redondeado de [size] píxeles con radio [radius]. */
  private fun inRoundRect(px: Int, py: Int, size: Int, radius: Int): Boolean {
    val x = px + 0.5
    val y = py + 0.5
    if (x < 0 || y < 0 || x > size || y > size) return false
    val cx = if (x < radius) radius.toDouble() else if (x > size - radius) (size - radius).toDouble() else x
    val cy = if (y < radius) radius.toDouble() else if (y > size - radius) (size - radius).toDouble() else y
    return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius.toDouble() * radius
  }

  /** El QR como lo dibuja `QrCodeBox`: módulos de 6 px, los ojos como piezas redondeadas decididas por el centro de cada píxel. */
  private fun brandBitmap(matrix: QrMatrix, ringArgb: Int): BinaryBitmap {
    val modulePx = 6
    val side = matrix.cells * modulePx
    val radii = qrEyeRadii(modulePx)
    val dark = SHARE_QR_DARK.toArgb()
    val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
    for (y in 0 until matrix.modules) for (x in 0 until matrix.modules) {
      if (!matrix[x, y] || matrix.isEye(x, y)) continue
      for (dy in 0 until modulePx) for (dx in 0 until modulePx) {
        pixels[((y + QR_QUIET_ZONE) * modulePx + dy) * side + (x + QR_QUIET_ZONE) * modulePx + dx] = dark
      }
    }
    for ((ex, ey) in matrix.eyes) {
      val left = (ex + QR_QUIET_ZONE) * modulePx
      val top = (ey + QR_QUIET_ZONE) * modulePx
      for (dy in 0 until 7 * modulePx) for (dx in 0 until 7 * modulePx) {
        val ring = inRoundRect(dx, dy, 7 * modulePx, radii.outer) &&
          !inRoundRect(dx - modulePx, dy - modulePx, 5 * modulePx, radii.hole)
        val center = inRoundRect(dx - 2 * modulePx, dy - 2 * modulePx, 3 * modulePx, radii.center)
        val color = when {
          center -> dark
          ring -> ringArgb
          else -> continue
        }
        pixels[(top + dy) * side + left + dx] = color
      }
    }
    val luminance = RGBLuminanceSource(side, side, pixels)
    return BinaryBitmap(HybridBinarizer(luminance))
  }

  private fun binary(matrix: QrMatrix): BinaryBitmap {
    val cells = matrix.modules + 2 * QR_QUIET_ZONE
    val scale = 4
    val side = cells * scale
    val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
    for (y in 0 until matrix.modules) for (x in 0 until matrix.modules) {
      if (!matrix[x, y]) continue
      for (dy in 0 until scale) for (dx in 0 until scale) {
        pixels[((y + QR_QUIET_ZONE) * scale + dy) * side + (x + QR_QUIET_ZONE) * scale + dx] = 0xFF000000.toInt()
      }
    }
    return BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
  }
}
