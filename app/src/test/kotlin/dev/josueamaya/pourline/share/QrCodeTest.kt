package dev.josueamaya.pourline.share

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Mode
import com.google.zxing.qrcode.encoder.Encoder
import org.junit.Assert.assertEquals
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
