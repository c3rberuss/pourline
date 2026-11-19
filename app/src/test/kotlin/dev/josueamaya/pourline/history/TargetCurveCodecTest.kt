package dev.josueamaya.pourline.history

import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.brew.TargetPoint
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.recipe.StageGaps
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.stage
import dev.josueamaya.pourline.recipe.recipeOf
import dev.josueamaya.pourline.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random
import java.util.zip.Deflater

class TargetCurveCodecTest {

  private fun assertRoundTrip(curve: TargetCurve, tolerance: Double = 0.005) {
    val decoded = TargetCurveCodec.decode(TargetCurveCodec.encode(curve))
    assertEquals(curve.points.size, decoded.points.size)
    curve.points.zip(decoded.points).forEach { (a, b) ->
      assertEquals(a.tMs, b.tMs)
      assertEquals(a.waterG, b.waterG, tolerance)
    }
    // El formato guarda solo los vertidos (D21): las esperas ya están en la meseta de los puntos.
    val pours = curve.stages.filter { it.type == StageType.POUR }
    assertEquals(pours.size, decoded.stages.size)
    pours.zip(decoded.stages).forEachIndexed { i, (a, b) ->
      assertEquals(i, b.index)
      assertEquals(StageType.POUR, b.type)
      assertEquals(a.name, b.name)
      assertEquals(a.startMs, b.startMs)
      assertEquals(a.endMs, b.endMs)
      assertEquals(a.targetWaterG, b.targetWaterG, tolerance)
    }
  }

  @Test
  fun `round trip with named stages`() {
    assertRoundTrip(TargetCurve.fromRecipe(v60Classic()))
  }

  @Test
  fun `round trip with null names`() {
    val curve = TargetCurve.fromRecipe(recipeOf(stage(0, 40.0, 10), stage(45, 150.0, 20)))
    assertTrue(curve.stages.all { it.name == null })
    assertRoundTrip(curve)
  }

  @Test
  fun `round trip with a mix of null and non null names, accents and emoji`() {
    val curve = TargetCurve.fromRecipe(recipeOf(stage(0, 40.0, 10, "Bloom ☕"), stage(45, 150.0, 20), stage(90, 250.0, 25, "3.er vertido · ñandú")))
    assertRoundTrip(curve)
  }

  @Test
  fun `water error stays at 0,005 or less for scaled curves`() {
    val curve = TargetCurve.fromRecipe(v60Classic()).scaled(16.4 / 15)
    assertRoundTrip(curve, 0.005)
  }

  @Test
  fun `a long curve taken from a brew round trips and stays small`() {
    val random = Random(5)
    var water = 0.0
    val points = (0 until 960).map { i ->
      water += 0.3 + random.nextDouble() * 0.2
      TargetPoint(i * 250L, water)
    }
    val curve = TargetCurve(points, listOf(TargetStage(0, null, 0, 240_000, water)))
    assertRoundTrip(curve)
    assertTrue(TargetCurveCodec.encode(curve).size < 960 * 4)
  }

  @Test
  fun `a curve with waits encodes to the same bytes as the one with only the pours`() {
    val pours = recipeOf(stage(0, 40.0, 10), stage(45, 150.0, 20), stage(90, 250.0, 25))
    val withWaits = pours.copy(stages = StageGaps.fillWaits(pours.stages))
    assertEquals(5, withWaits.stages.size)
    val a = TargetCurveCodec.encode(TargetCurve.fromRecipe(pours))
    val b = TargetCurveCodec.encode(TargetCurve.fromRecipe(withWaits))
    assertTrue(a.contentEquals(b))
  }

  @Test
  fun `the stored v60 of before the typed steps still decodes the same`() {
    // La V60 de antes (tres vertidos) y la de ahora (con esperas) producen la misma curva guardada.
    val old = recipeOf(stage(0, 40.0, 10, "Bloom"), stage(45, 150.0, 20, "2.º vertido"), stage(90, 250.0, 25, "3.er vertido"))
    val decoded = TargetCurveCodec.decode(TargetCurveCodec.encode(TargetCurve.fromRecipe(v60Classic())))
    assertEquals(TargetCurve.fromRecipe(old).points.map { it.tMs }, decoded.points.map { it.tMs })
    assertEquals(listOf("Bloom", "2.º vertido", "3.er vertido"), decoded.stages.map { it.name })
  }

  @Test
  fun `a truncated blob is rejected`() {
    val bytes = TargetCurveCodec.encode(TargetCurve.fromRecipe(v60Classic()))
    for (length in listOf(0, 1, 2, bytes.size / 2, bytes.size - 1)) assertRejected(bytes.copyOf(length))
  }

  @Test
  fun `random bytes are rejected`() {
    val random = Random(13)
    repeat(200) {
      val bytes = ByteArray(1 + random.nextInt(200)).also(random::nextBytes)
      bytes[0] = 1
      assertRejected(bytes)
    }
  }

  @Test
  fun `an unknown version is rejected`() {
    val bytes = TargetCurveCodec.encode(TargetCurve.fromRecipe(v60Classic()))
    bytes[0] = 2
    assertRejected(bytes)
  }

  @Test
  fun `a body with no points, no stages, trailing bytes or an absurd count is rejected`() {
    assertRejected(compress(byteArrayOf(0, 0))) // sin puntos ni etapas
    assertRejected(compress(byteArrayOf(1, 0, 0, 0))) // un punto, cero etapas
    assertRejected(compress(byteArrayOf(0x7F))) // 127 puntos sin datos
    val valid = inflate(TargetCurveCodec.encode(TargetCurve.fromRecipe(v60Classic())))
    assertRejected(compress(valid + byteArrayOf(0)))
  }

  @Test
  fun `points that go backwards in time are rejected`() {
    // 2 puntos: (t=10, w=0) y (Δt=-5 → t=5, w=0); 1 etapa (0, 0, 0, sin nombre).
    val body = byteArrayOf(2, 20, 0, 9, 0, 1, 0, 0, 0, 0)
    assertRejected(compress(body))
  }

  @Test
  fun `a name longer than the remaining bytes is rejected`() {
    // 1 punto (0, 0); 1 etapa (0, 0, 0) con un nombre de 100 bytes que no existen.
    assertRejected(compress(byteArrayOf(1, 0, 0, 1, 0, 0, 0, 100)))
  }

  @Test
  fun `the decoded index is the position`() {
    val decoded = TargetCurveCodec.decode(TargetCurveCodec.encode(TargetCurve.fromRecipe(v60Classic())))
    assertEquals(listOf(0, 1, 2), decoded.stages.map { it.index })
    assertEquals(listOf(0L, 45_000L, 90_000L), decoded.stages.map { it.startMs })
    assertNull(TargetCurveCodec.decode(TargetCurveCodec.encode(TargetCurve.fromRecipe(recipeOf(stage(0, 40.0, 10))))).stages.single().name)
  }

  private fun compress(body: ByteArray): ByteArray {
    val deflater = Deflater()
    deflater.setInput(body)
    deflater.finish()
    val buffer = ByteArray(1024)
    val n = deflater.deflate(buffer)
    deflater.end()
    return byteArrayOf(1) + buffer.copyOf(n)
  }

  private fun inflate(bytes: ByteArray): ByteArray {
    val inflater = java.util.zip.Inflater()
    inflater.setInput(bytes, 1, bytes.size - 1)
    val buffer = ByteArray(4096)
    val n = inflater.inflate(buffer)
    inflater.end()
    return buffer.copyOf(n)
  }

  private fun assertRejected(bytes: ByteArray) {
    try {
      TargetCurveCodec.decode(bytes)
      fail("expected TargetCurveFormatException for ${bytes.size} bytes")
    } catch (_: TargetCurveFormatException) {
      // esperado
    }
  }
}
