package dev.dotscale.coffee.label

import dev.dotscale.coffee.CoffeeProcess
import dev.dotscale.coffee.RoastLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LabelParserTest {
  private val today = LocalDate.of(2026, 11, 13)

  private fun lines(vararg texts: String, height: Int = 40) = texts.map { OcrLine(it, height) }

  private fun parse(vararg texts: String) = LabelParser.parse(lines(*texts), today)

  private fun process(text: String) = parse(text).process

  // ---------------------------------------------------------------- proceso

  @Test
  fun `simple processes`() {
    assertEquals(CoffeeProcess.WASHED, process("Lavado"))
    assertEquals(CoffeeProcess.WASHED, process("Washed"))
    assertEquals(CoffeeProcess.NATURAL, process("Natural"))
    assertEquals(CoffeeProcess.HONEY, process("Honey"))
  }

  @Test
  fun `long phrases win over their parts`() {
    assertEquals(CoffeeProcess.ANAEROBIC, process("Anaeróbico lavado"))
    assertEquals(CoffeeProcess.ANAEROBIC, process("anaerobic natural"))
    assertEquals(CoffeeProcess.WET_HULLED, process("Semi lavado"))
    assertEquals(CoffeeProcess.WET_HULLED, process("Semilavado"))
    assertEquals(CoffeeProcess.WET_HULLED, process("wet hulled"))
    assertEquals(CoffeeProcess.HONEY, process("Yellow honey"))
    assertEquals(CoffeeProcess.HONEY, process("Red honey"))
  }

  @Test
  fun `an unknown fermentation is OTHER with its text`() {
    val fields = parse("Fermentación láctica")
    assertEquals(CoffeeProcess.OTHER, fields.process)
    assertEquals("Fermentación láctica", fields.processOther)
  }

  @Test
  fun `honey in tasting notes is not a process`() {
    assertNull(parse("Notas de miel y durazno").process)
    assertNull(parse("Notas:", "Honey, durazno").process)
  }

  @Test
  fun `conflicting loose processes are left empty`() {
    assertNull(parse("Lavado", "Natural").process)
  }

  // ---------------------------------------------------------------- nombre

  @Test
  fun `a title that is a variety is joined with its process`() {
    val pacamara = LabelParser.parse(listOf(OcrLine("Pacamara", 90), OcrLine("Red honey", 40)), today)
    assertEquals("Pacamara Red Honey", pacamara.name)
    assertEquals(CoffeeProcess.HONEY, pacamara.process)
    val geisha = LabelParser.parse(listOf(OcrLine("Geisha", 90), OcrLine("Anaeróbico lavado", 40)), today)
    assertEquals("Geisha Anaeróbico Lavado", geisha.name)
    assertEquals(CoffeeProcess.ANAEROBIC, geisha.process)
  }

  @Test
  fun `a following line that is not a process is not joined`() {
    val fields = LabelParser.parse(listOf(OcrLine("Etiopía Guji", 90), OcrLine("Café de altura", 40)), today)
    assertEquals("Etiopía Guji", fields.name)
    assertEquals("Café de altura", fields.roaster)
  }

  @Test
  fun `the name is the tallest line that is not a date weight label or option`() {
    val fields = LabelParser.parse(
      listOf(
        OcrLine("Molido / Grano", 120),
        OcrLine("Tostado: 12/10/2026", 110),
        OcrLine("250 g", 100),
        OcrLine("Proceso: Natural", 100),
        OcrLine("Huehuetenango", 80),
        OcrLine("Cafetal", 50),
      ),
      today,
    )
    assertEquals("Huehuetenango", fields.name)
    assertEquals("Cafetal", fields.roaster)
  }

  // ---------------------------------------------------------------- opciones impresas

  @Test
  fun `printed options are not filled`() {
    assertNull(parse("PROCESS: WASHED NATURAL HONEY").process)
    assertNull(parse("200 gr / 400 gr").bagWeightG)
    assertNull(parse("Claro Medio Oscuro").roastLevel)
  }

  @Test
  fun `the same amount in two units is one weight`() {
    assertEquals(340.0, parse("12oz. / 340g.").bagWeightG!!, 0.0)
  }

  // ---------------------------------------------------------------- nivel

  @Test
  fun `roast levels need context or a lone word`() {
    assertEquals(RoastLevel.MEDIUM, parse("Tueste medio").roastLevel)
    assertEquals(RoastLevel.MEDIUM, parse("Medio").roastLevel)
    assertEquals(RoastLevel.LIGHT, parse("Light roast").roastLevel)
    assertEquals(RoastLevel.DARK, parse("Tueste: Oscuro").roastLevel)
  }

  @Test
  fun `combined levels and words in notes are not filled`() {
    assertNull(parse("Light - Medium").roastLevel)
    assertNull(parse("medio-oscuro").roastLevel)
    assertNull(parse("Tueste medio claro").roastLevel)
    assertNull(parse("Cuerpo light y acidez brillante").roastLevel)
  }

  // ---------------------------------------------------------------- números

  @Test
  fun `altitude with the thousands comma`() {
    fun alt(text: String) = parse(text).let { it.altitudeMinM to it.altitudeMaxM }
    assertEquals(1560 to 1560, alt("1,560 msnm"))
    assertEquals(1850 to 1850, alt("1,850 M.S.N.M."))
    assertEquals(1600 to 1600, alt("1,600 Mts."))
    assertEquals(1500 to 1500, alt("1,500 m s.n.m."))
    assertEquals(1200 to 1200, alt("Altura | 1,200"))
    assertEquals(1350 to 1350, alt("Cultivado a: 1350 msnm"))
    assertEquals(1800 to 1800, alt("1.800 msnm"))
  }

  @Test
  fun `altitude ranges`() {
    fun alt(text: String) = parse(text).let { it.altitudeMinM to it.altitudeMaxM }
    assertEquals(1800 to 2000, alt("1800-2000 msnm"))
    assertEquals(1800 to 2000, alt("1800 – 2000 m"))
    assertEquals(1800 to 2000, alt("1800 a 2000 m"))
  }

  @Test
  fun `numbers without a unit or label are not an altitude`() {
    assertNull(parse("1,560").altitudeMinM)
    assertNull(parse("15 min").altitudeMinM)
    assertNull(parse("Altura 9000 m").altitudeMinM)
  }

  @Test
  fun `weights`() {
    assertEquals(454.0, parse("454 g").bagWeightG!!, 0.0)
    assertEquals(340.0, parse("340g").bagWeightG!!, 0.0)
    assertEquals(250.0, parse("250G").bagWeightG!!, 0.0)
    assertEquals(500.0, parse("0,5 kg").bagWeightG!!, 0.0)
    assertNull(parse("15 g de café").bagWeightG)
  }

  // ---------------------------------------------------------------- rótulos

  @Test
  fun `labeled varieties are accepted even when unknown`() {
    assertEquals(listOf("Caturra", "Chiroso"), parse("Variedad: Caturra, Chiroso").varieties)
    assertEquals(listOf("Caturra", "Castillo"), parse("Variety: Caturra & Castillo").varieties)
  }

  @Test
  fun `loose varieties come from the dictionary`() {
    assertEquals(listOf("Pacamara", "Bourbon Rojo"), parse("Pacamara y bourbon rojo").varieties)
  }

  @Test
  fun `a variety in a notes line does not count`() {
    assertTrue(parse("Notas:", "Dulce, como un geisha").varieties.isEmpty())
  }

  @Test
  fun `labeled fields`() {
    assertEquals(CoffeeProcess.NATURAL, parse("Process: Natural").process)
    assertEquals("Colombia", parse("Origin: Colombia").originCountry)
    assertEquals("Huila", parse("Región: Huila").originRegion)
    assertEquals("La Esperanza", parse("Finca: La Esperanza").farm)
    assertEquals("Juan Pérez", parse("Productor: Juan Pérez").farm)
  }

  @Test
  fun `the value can be on the next line`() {
    val fields = parse("Proceso", "Lavado", "Región", "Huila")
    assertEquals(CoffeeProcess.WASHED, fields.process)
    assertEquals("Huila", fields.originRegion)
  }

  @Test
  fun `a labeled value wins over loose keywords`() {
    assertEquals(CoffeeProcess.NATURAL, parse("Lavado", "Proceso: Natural").process)
  }

  @Test
  fun `an English country becomes Spanish and Colombia is never a variety`() {
    val fields = parse("Ethiopia", "Colombia")
    assertNull(parse("Ethiopia").let { if (it.originCountry == "Etiopía") null else it.originCountry })
    assertTrue(fields.varieties.isEmpty())
    assertEquals("Colombia", parse("Café de Colombia").originCountry)
  }

  // ---------------------------------------------------------------- fechas

  @Test
  fun `a label without a date and with misleading years leaves the date empty`() {
    val fields = LabelParser.parse(
      listOf(
        OcrLine("Café del Valle", 90),
        OcrLine("EST. 2021", 50),
        OcrLine("2024: 7°", 50),
        OcrLine("Lote 120926", 40),
        OcrLine("Lavado", 40),
      ),
      today,
    )
    assertNull(fields.roastDate)
    assertEquals("Café del Valle", fields.name)
  }

  @Test
  fun `a printed roast date is read`() {
    assertEquals(LocalDate.of(2026, 10, 12), parse("Tostado: 12/10/2026").roastDate)
  }

  @Test
  fun `an empty label gives empty fields`() {
    assertTrue(LabelParser.parse(emptyList(), today).isEmpty)
    assertTrue(parse("   ").isEmpty)
  }

  @Test
  fun `internal readers`() {
    assertEquals(setOf(CoffeeProcess.WASHED), LabelParser.processesIn("Lavado").map { it.process }.toSet())
    assertNull(LabelParser.levelIn("Medio y claro"))
    assertEquals(1800 to 1800, LabelParser.altitudeIn("1800 m", labeled = false))
    assertEquals(listOf(340.0), LabelParser.weightsIn("12oz. / 340g."))
    assertEquals("Lavado", LabelParser.labeledValues(lines("Proceso: Lavado"))[LabelKeywords.Label.PROCESS])
  }
}
