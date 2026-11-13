package dev.dotscale.history

import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.FakeScale
import dev.dotscale.brew.Pour
import dev.dotscale.protocol.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random
import java.util.zip.Deflater

class BrewCurveCodecTest {

  private fun assertRoundTrip(samples: List<BrewSample>, pours: List<Pour>, tolerance: Double = 0.005) {
    val (decodedSamples, decodedPours) = BrewCurveCodec.decode(BrewCurveCodec.encode(samples, pours))
    assertEquals(samples.size, decodedSamples.size)
    samples.zip(decodedSamples).forEach { (a, b) ->
      assertEquals(a.tMs, b.tMs)
      assertEquals(a.stability, b.stability)
      assertEquals(a.valid, b.valid)
      assertEquals(a.waterG, b.waterG, tolerance)
      assertEquals(a.flowGps, b.flowGps, tolerance)
    }
    assertEquals(pours.size, decodedPours.size)
    pours.zip(decodedPours).forEach { (a, b) ->
      assertEquals(a.index, b.index)
      assertEquals(a.startMs, b.startMs)
      assertEquals(a.endMs, b.endMs)
      assertEquals(a.waterG, b.waterG, tolerance)
    }
  }

  @Test
  fun `round trip with valid and invalid samples and null and non null stability`() {
    val samples = listOf(
      BrewSample(0, 0.0, 0.6, null, true),
      BrewSample(100, 0.06, 4.1234, null, true),
      BrewSample(250, 0.5, 0.0, 0, true),
      BrewSample(400, -0.37, -1.5, 100, false),
      BrewSample(401, 1234.56, 12.34, 55, true),
      BrewSample(900, 300.0, 3.0, 99, false),
    )
    val pours = listOf(Pour(1, 0, 400, 12.5), Pour(2, 1_000, 5_000, 250.25))
    assertRoundTrip(samples, pours)
  }

  @Test
  fun `empty lists round trip`() {
    val (samples, pours) = BrewCurveCodec.decode(BrewCurveCodec.encode(emptyList(), emptyList()))
    assertTrue(samples.isEmpty())
    assertTrue(pours.isEmpty())
  }

  @Test
  fun `9000 noisy samples keep the error under half a hundredth without accumulating`() {
    val random = Random(7)
    var water = 0.0
    val samples = (0 until 9_000).map { i ->
      val flow = 4.0 + random.nextGaussian() * 0.3
      water += flow * 0.1
      BrewSample(i * 100L, water + random.nextGaussian() * 0.05, flow, if (i % 7 == 0) null else random.nextInt(101), i % 50 != 0)
    }
    val pours = listOf(Pour(1, 0, 899_900, water))
    assertRoundTrip(samples, pours)
    val size = BrewCurveCodec.encode(samples, pours).size
    assertTrue("curve too large: $size", size < 9_000 * 6)
  }

  @Test
  fun `an ounces session converted to grams round trips`() {
    val scale = FakeScale(unit = WeightUnit.OUNCES)
    scale.idle(500)
    scale.pour(4.0, 5_000)
    scale.finish()
    val brew = scale.finishedBrews.single()
    assertTrue(brew.samples.isNotEmpty())
    assertTrue(brew.samples.last().waterG in 15.0..25.0)
    assertRoundTrip(brew.samples, brew.summary.pours)
  }

  @Test
  fun `a truncated blob is rejected`() {
    val bytes = BrewCurveCodec.encode(List(300) { BrewSample(it * 100L, it * 0.37, 3.7, 80, true) }, emptyList())
    for (length in listOf(0, 1, 2, bytes.size / 2, bytes.size - 1)) {
      assertRejected(bytes.copyOf(length))
    }
  }

  @Test
  fun `random bytes are rejected`() {
    val random = Random(11)
    repeat(200) {
      val bytes = ByteArray(1 + random.nextInt(200)).also(random::nextBytes)
      bytes[0] = 1 // con la versión correcta, para que falle la descompresión o el cuerpo
      assertRejected(bytes)
    }
  }

  @Test
  fun `an unknown version is rejected`() {
    val bytes = BrewCurveCodec.encode(listOf(BrewSample(0, 1.0, 1.0, 1, true)), emptyList())
    bytes[0] = 2
    assertRejected(bytes)
  }

  @Test
  fun `a body with trailing bytes or an absurd count is rejected`() {
    assertRejected(compress(byteArrayOf(0, 0, 0))) // 0 muestras, 0 vertidos y un byte de más
    assertRejected(compress(byteArrayOf(0x7F))) // 127 muestras sin datos
  }

  @Test
  fun `stability null is stored apart from every valid score`() {
    val samples = (0..100).map { BrewSample(it * 10L, 0.0, 0.0, it, true) } + BrewSample(2_000, 0.0, 0.0, null, true)
    val (decoded, _) = BrewCurveCodec.decode(BrewCurveCodec.encode(samples, emptyList()))
    assertEquals((0..100).toList(), decoded.take(101).map { it.stability })
    assertNull(decoded.last().stability)
  }

  private fun compress(body: ByteArray): ByteArray {
    val deflater = Deflater()
    deflater.setInput(body)
    deflater.finish()
    val buffer = ByteArray(256)
    val n = deflater.deflate(buffer)
    deflater.end()
    return byteArrayOf(1) + buffer.copyOf(n)
  }

  private fun assertRejected(bytes: ByteArray) {
    try {
      BrewCurveCodec.decode(bytes)
      fail("expected BrewCurveFormatException for ${bytes.size} bytes")
    } catch (_: BrewCurveFormatException) {
      // esperado
    }
  }
}
