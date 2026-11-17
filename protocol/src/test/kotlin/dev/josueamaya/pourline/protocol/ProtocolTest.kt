package dev.josueamaya.pourline.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los vectores hex se calcularon de forma independiente (script Python), no con este código. */
class ProtocolTest {

  private fun hex(s: String): ByteArray =
    s.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

  @Test
  fun `crc16 modbus matches the standard check value`() {
    assertEquals(0x4B37, Crc16Modbus.compute("123456789".encodeToByteArray()))
  }

  @Test
  fun `commands encode to the expected bytes`() {
    assertArrayEquals(hex("a5 5a 03 0d 00 02 00 00 7b fc"), ScaleCommands.tare().encode())
    assertArrayEquals(hex("a5 5a 03 02 00 01 01 18 67"), ScaleCommands.startTimer().encode())
    assertArrayEquals(hex("a5 5a 02 05 00 00 5a 51"), ScaleCommands.read(Cmd.BATTERY).encode())
  }

  @Test
  fun `weight frame is decoded in grams`() {
    val frames = FrameDecoder().feed(hex("a5 5a 01 01 00 09 00 00 00 b9 00 17 00 2a 00 8e 50"))
    val msg = ScaleMessageParser().parse(frames.single()) as ScaleMessage.Weight
    assertTrue(frames.single().crcValid)
    assertEquals(18.5, msg.weight, 0.0001)
    assertEquals(2.3, msg.flowRate, 0.0001)
    assertEquals(42, msg.seconds)
    assertFalse(msg.outOfRange)
  }

  @Test
  fun `negative weight and flow are signed`() {
    val frame = FrameDecoder().feed(hex("a5 5a 01 01 00 08 ff ff ff f4 ff fb 00 00 35 ec")).single()
    val msg = ScaleMessageParser().parse(frame) as ScaleMessage.Weight
    assertEquals(-1.2, msg.weight, 0.0001)
    assertEquals(-0.5, msg.flowRate, 0.0001)
  }

  @Test
  fun `weight uses ounce scale after unit change`() {
    val parser = ScaleMessageParser()
    parser.parse(Frame(Op.READ, Cmd.WEIGHT_UNIT, byteArrayOf(1)))
    val msg = parser.parse(Frame(Op.NOTIFY, Cmd.WEIGHT, hex("00 00 00 b9 00 17 00 2a"))) as ScaleMessage.Weight
    assertEquals(WeightUnit.OUNCES, msg.unit)
    assertEquals(1.85, msg.weight, 0.0001)
  }

  @Test
  fun `decoder splits concatenated frames and buffers partial ones`() {
    val battery = hex("a5 5a 02 05 00 02 00 57 95 5d")
    val model = hex("a5 5a 02 13 00 06 54 45 53 30 31 37 fa 50")
    val stream = hex("00 ff") + battery + model
    val decoder = FrameDecoder()

    val first = decoder.feed(stream.copyOfRange(0, 15)) // battery completa + 3 bytes de model
    assertEquals(1, first.size)
    assertEquals(ScaleMessage.Battery(percent = 87, status = 0), ScaleMessageParser().parse(first[0]))

    val second = decoder.feed(stream.copyOfRange(15, stream.size))
    assertEquals(
      ScaleMessage.Model(ScaleModel.DOT, "TES017"),
      ScaleMessageParser().parse(second.single()),
    )
  }

  @Test
  fun `write responses are acks`() {
    val frame = FrameDecoder().feed(hex("a5 5a 03 0d 00 01 01 0c 64")).single()
    assertEquals(ScaleMessage.Ack(Cmd.TARE, success = true), ScaleMessageParser().parse(frame))
  }

  @Test
  fun `bad crc is flagged but frame is kept`() {
    val frame = FrameDecoder().feed(hex("a5 5a 02 05 00 02 00 57 00 00")).single()
    assertFalse(frame.crcValid)
  }

  @Test
  fun `round trip through encoder and decoder`() {
    val original = ScaleCommands.setDeviceName("Mi DOT")
    assertEquals(original, FrameDecoder().feed(original.encode()).single())
  }

  @Test
  fun `advertisement identifies the model`() {
    assertEquals(ScaleModel.DOT, AdvertisementInfo.parse(hex("01 01 02"))?.model)
    assertEquals(ScaleModel.ESPRO, AdvertisementInfo.parse(hex("02 03"))?.model)
    assertEquals(null, AdvertisementInfo.parse(hex("05 01")))
  }
}
