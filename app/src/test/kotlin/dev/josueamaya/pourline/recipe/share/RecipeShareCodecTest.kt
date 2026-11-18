package dev.josueamaya.pourline.recipe.share

import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.BuiltInRecipes
import dev.josueamaya.pourline.recipe.FakeRecipeTexts
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.aeroPressWithActions
import dev.josueamaya.pourline.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class RecipeShareCodecTest {
  private fun decoded(uri: String): SharedRecipe {
    val result = RecipeShareCodec.decode(uri)
    assertTrue("se esperaba Ok y fue $result", result is DecodeResult.Ok)
    return (result as DecodeResult.Ok).shared
  }

  private fun roundTrip(recipe: Recipe, grinder: dev.josueamaya.pourline.grinder.Grinder? = null): SharedRecipe {
    val encoded = RecipeShareCodec.encode(recipe, grinder, Int.MAX_VALUE)
    assertNotNull("no cabe: ${recipe.name}", encoded)
    return decoded(encoded!!.uri)
  }

  private fun expected(recipe: Recipe) = recipe.copy(id = 0, builtInKey = null, localizedNames = false, grind = null)

  private fun write(block: ByteWriter.() -> Unit): ByteArray = ByteWriter().apply(block).toByteArray()

  private fun uriOf(payload: ByteArray) = RecipeShareCodec.uriFor(payload)

  private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it) }

  /** Una receta válida mínima: `Café`, 15,0 g y un `POUR` de 10 s con 40,0 g. [flags] y [tail] se pueden alterar. */
  private fun minimal(flags: Long = 0, stageCount: Long = 1, stageFlags: Long = 0, tail: ByteWriter.() -> Unit = {}) = write {
    uvar(flags)
    str("Café")
    uvar(150)
    uvar(stageCount)
    if (stageCount in 1..20) {
      uvar(stageFlags)
      uvar(0)
      uvar(10)
      if (stageFlags and 3 == 0L) uvar(400)
    }
    tail()
  }

  // --- Ida y vuelta ---

  @Test
  fun `the built-in recipes round trip`() {
    for (recipe in BuiltInRecipes.all(FakeRecipeTexts())) {
      assertEquals(expected(recipe), roundTrip(recipe).recipe)
    }
  }

  @Test
  fun `a recipe with every field round trips`() {
    for (unitMs in listOf(1_000L, 100L, 1L)) {
      val recipe = everyField(unitMs)
      val shared = roundTrip(recipe)
      assertEquals("unidad $unitMs", expected(recipe), shared.recipe)
      assertEquals(BrewMethod.OTHER, shared.recipe.method)
      assertEquals("Sifón", shared.recipe.methodOther)
      assertEquals(93.5, shared.recipe.waterTempC!!, 0.0)
      assertEquals(20, shared.recipe.stages.size)
      assertTrue(shared.trimmed.isEmpty())
    }
  }

  @Test
  fun `the time unit is the largest that divides every start and duration`() {
    fun flagsOf(recipe: Recipe) = (firstUvar(encodePayload(recipe, null, TrimPlan.NONE).bytes) shr ShareFormat.TIME_UNIT_SHIFT) and 3
    assertEquals(0L, flagsOf(everyField(1_000)))
    assertEquals(1L, flagsOf(everyField(100)))
    assertEquals(2L, flagsOf(everyField(1)))
  }

  @Test
  fun `swirl and wait water is derived on decode`() {
    val recipe = everyField()
    val shared = roundTrip(recipe)
    var accumulated = 0.0
    for (stage in shared.recipe.stages) {
      if (stage.type == StageType.POUR) accumulated = stage.targetWaterG
      else assertEquals(accumulated, stage.targetWaterG, 0.0)
    }
    assertTrue(shared.recipe.stages.any { it.type == StageType.SWIRL })
  }

  @Test
  fun `water is incremental in tenths of a gram`() {
    val payload = encodePayload(v60Classic(), null, TrimPlan.NONE).bytes
    // El primer vertido añade 40,0 g (400 décimas: 90 03) y el segundo 110,0 g (1 100 décimas: CC 08).
    val text = hex(payload)
    assertTrue(text, text.contains("0C000A9003"))
    assertTrue(text, text.contains("0C0014CC08"))
  }

  @Test
  fun `the decoded recipe has id 0, no built-in key, no localized names and no grind`() {
    val shared = roundTrip(v60Classic().copy(id = 42))
    assertEquals(0L, shared.recipe.id)
    assertNull(shared.recipe.builtInKey)
    assertFalse(shared.recipe.localizedNames)
    assertNull(shared.recipe.grind)
  }

  @Test
  fun `stages are written ordered by start`() {
    val recipe = v60Classic()
    val shuffled = recipe.copy(stages = recipe.stages.reversed())
    assertEquals(expected(recipe), roundTrip(shuffled).recipe)
  }

  @Test
  fun `an invalid recipe does not encode`() {
    assertNull(RecipeShareCodec.encode(v60Classic().copy(doseG = 0.0), null))
    assertNull(RecipeShareCodec.encode(v60Classic().copy(stages = emptyList()), null))
    assertNull(RecipeShareCodec.encode(v60Classic().copy(name = " "), null))
  }

  // --- Molienda ---

  @Test
  fun `a catalog grinder travels as key, brand, model and value`() {
    val shared = roundTrip(everyField().copy(grind = catalogGrind(24.5)), catalogGrinder)
    assertEquals(SharedGrind("timemore_c3_pro", "Timemore", "Chestnut C3 Pro", 24.5, null), shared.grind)
  }

  @Test
  fun `a custom grinder has no key`() {
    val custom = catalogGrinder.copy(catalogKey = null, brand = "Casero", model = "Uno")
    val shared = roundTrip(v60Classic().copy(grind = catalogGrind(10.0)), custom)
    assertEquals(SharedGrind(null, "Casero", "Uno", 10.0, null), shared.grind)
  }

  @Test
  fun `a grinder without a setting has no value`() {
    val shared = roundTrip(v60Classic().copy(grind = GrindSetting(catalogGrinder.id, null, null)), catalogGrinder)
    assertEquals(SharedGrind("timemore_c3_pro", "Timemore", "Chestnut C3 Pro", null, null), shared.grind)
  }

  @Test
  fun `the value is rounded to hundredths`() {
    val shared = roundTrip(v60Classic().copy(grind = catalogGrind(12.3456)), catalogGrinder)
    assertEquals(12.35, shared.grind!!.value!!, 1e-9)
  }

  @Test
  fun `a deleted grinder only carries the label`() {
    val grind = GrindSetting(null, null, "Molino viejo · 18")
    assertEquals(SharedGrind(null, null, null, null, "Molino viejo · 18"), roundTrip(v60Classic().copy(grind = grind)).grind)
    // Con grinderId pero sin molino (borrado entre leer y compartir) pasa lo mismo.
    val dangling = GrindSetting(99, 18.0, "Molino viejo · 18")
    assertEquals(SharedGrind(null, null, null, null, "Molino viejo · 18"), roundTrip(v60Classic().copy(grind = dangling)).grind)
  }

  @Test
  fun `no grind, an empty grind or a deleted grinder without label omit the block`() {
    for (grind in listOf(null, GrindSetting(null, null, null), GrindSetting(null, null, " "), GrindSetting(99, 18.0, null))) {
      val recipe = v60Classic().copy(grind = grind)
      val payload = encodePayload(recipe, null, TrimPlan.NONE).bytes
      assertEquals("flags de $grind", 0L, (firstUvar(payload) and ShareFormat.F_GRIND.toLong()))
      assertNull(roundTrip(recipe).grind)
    }
  }

  // --- Recorte ---

  private val longTexts = withLongTexts(notesLength = 120, instructionLength = 60)

  /** El plan con las notas fuera y las instrucciones de los [dropped] últimos pasos fuera. */
  private fun noNotes(dropped: Int) = TrimPlan(notes = true, droppedInstructions = dropped)

  private val noInstructions = TrimPlan(notes = true, droppedInstructions = 5)
  private val noNames = TrimPlan(notes = true, droppedInstructions = 5, stageNames = true)

  private fun lengthAt(recipe: Recipe, plan: TrimPlan) = uriOf(encodePayload(recipe, null, plan).bytes).length

  @Test
  fun `notes go first`() {
    val limit = lengthAt(longTexts, TrimPlan.NONE) - 1
    val encoded = RecipeShareCodec.encode(longTexts, null, limit)!!
    assertEquals(setOf(TrimmedPart.NOTES), encoded.trimmed)
    assertEquals(0, encoded.droppedInstructions)
    assertEquals(5, encoded.keptInstructions)
    val shared = decoded(encoded.uri)
    assertNull(shared.recipe.notes)
    assertEquals(longTexts.stages.map { it.instruction }, shared.recipe.stages.map { it.instruction })
    assertEquals(longTexts.stages.map { it.name }, shared.recipe.stages.map { it.name })
  }

  @Test
  fun `then instructions go one at a time from the last stage that has one`() {
    val limit = lengthAt(longTexts, noNotes(0)) - 1
    val encoded = RecipeShareCodec.encode(longTexts, null, limit)!!
    assertEquals(setOf(TrimmedPart.NOTES, TrimmedPart.INSTRUCTIONS), encoded.trimmed)
    assertEquals(1, encoded.droppedInstructions)
    assertEquals(4, encoded.keptInstructions)
    val shared = decoded(encoded.uri)
    assertEquals(longTexts.stages.take(4).map { it.instruction }, shared.recipe.stages.take(4).map { it.instruction })
    assertNull(shared.recipe.stages.last().instruction)
    assertEquals(longTexts.stages.map { it.name }, shared.recipe.stages.map { it.name })
  }

  @Test
  fun `only the instructions needed are removed`() {
    val recipe = withLongTexts(notesLength = 0, instructionLength = 60, stageCount = 6).copy(notes = null)
    val lengths = (0..6).map { lengthAt(recipe, TrimPlan(droppedInstructions = it)) }
    for (k in 1..6) assertTrue("quitar $k no acorta: $lengths", lengths[k] < lengths[k - 1])
    for (k in 0..6) {
      val encoded = RecipeShareCodec.encode(recipe, null, lengths[k])!!
      assertEquals("tope en la longitud sin $k instrucciones", k, encoded.droppedInstructions)
      assertEquals(6 - k, encoded.keptInstructions)
      val shared = decoded(encoded.uri)
      assertEquals(recipe.stages.take(6 - k).map { it.instruction }, shared.recipe.stages.take(6 - k).map { it.instruction })
      assertTrue(shared.recipe.stages.drop(6 - k).all { it.instruction == null })
      assertEquals(k > 0, TrimmedPart.INSTRUCTIONS in encoded.trimmed)
    }
  }

  @Test
  fun `stage names go only after every instruction`() {
    val limit = lengthAt(longTexts, noInstructions) - 1
    val encoded = RecipeShareCodec.encode(longTexts, null, limit)!!
    assertEquals(setOf(TrimmedPart.NOTES, TrimmedPart.INSTRUCTIONS, TrimmedPart.STAGE_NAMES), encoded.trimmed)
    assertEquals(5, encoded.droppedInstructions)
    assertEquals(0, encoded.keptInstructions)
    assertTrue(decoded(encoded.uri).recipe.stages.all { it.name == null && it.instruction == null })
    // Con un carácter más de sitio, los nombres se conservan.
    val roomy = RecipeShareCodec.encode(longTexts, null, limit + 1)!!
    assertEquals(setOf(TrimmedPart.NOTES, TrimmedPart.INSTRUCTIONS), roomy.trimmed)
    assertEquals(longTexts.stages.map { it.name }, decoded(roomy.uri).recipe.stages.map { it.name })
  }

  @Test
  fun `the trim bits and trimmed list only what was actually removed`() {
    val noNotes = longTexts.copy(notes = null)
    val limit = lengthAt(noNotes, TrimPlan.NONE) - 1
    val encoded = RecipeShareCodec.encode(noNotes, null, limit)!!
    assertEquals(setOf(TrimmedPart.INSTRUCTIONS), encoded.trimmed)
    assertEquals(setOf(TrimmedPart.INSTRUCTIONS), decoded(encoded.uri).trimmed)
    // El bit 6 vale 1 con un recorte parcial: se quitó una instrucción y quedan cuatro.
    assertEquals(1, encoded.droppedInstructions)
    assertTrue(firstUvar(inflatedPayload(encoded.uri)) and ShareFormat.F_TRIMMED_INSTRUCTIONS.toLong() != 0L)
    // Sin nada que quitar no se anota nada.
    assertEquals(emptySet<TrimmedPart>(), RecipeShareCodec.encode(longTexts, null, Int.MAX_VALUE)!!.trimmed)
  }

  @Test
  fun `encode is null only when the fully trimmed uri does not fit`() {
    val fullyTrimmed = lengthAt(longTexts, noNames)
    assertNull(RecipeShareCodec.encode(longTexts, null, fullyTrimmed - 1))
    assertNotNull(RecipeShareCodec.encode(longTexts, null, fullyTrimmed))
  }

  @Test
  fun `steps, times, water, dose, temperature, method and grind are never trimmed`() {
    val recipe = longTexts.copy(waterTempC = 91.5, grind = catalogGrind(), method = BrewMethod.CHEMEX)
    val limit = lengthAt(recipe.copy(), noNames)
    val encoded = RecipeShareCodec.encode(recipe, catalogGrinder, limit + 40)!!
    val shared = decoded(encoded.uri)
    assertEquals(recipe.stages.map { Triple(it.startMs, it.durationMs, it.targetWaterG) }, shared.recipe.stages.map { Triple(it.startMs, it.durationMs, it.targetWaterG) })
    assertEquals(recipe.stages.map { it.type }, shared.recipe.stages.map { it.type })
    assertEquals(15.0, shared.recipe.doseG, 0.0)
    assertEquals(91.5, shared.recipe.waterTempC!!, 0.0)
    assertEquals(BrewMethod.CHEMEX, shared.recipe.method)
    assertEquals("Timemore", shared.grind!!.brand)
    assertEquals(24.0, shared.grind!!.value!!, 0.0)
  }

  @Test
  fun `the uri never exceeds the limit`() {
    for (limit in 60..900 step 7) {
      val encoded = RecipeShareCodec.encode(longTexts, catalogGrinder, limit)
      if (encoded != null) assertTrue("$limit < ${encoded.uri.length}", encoded.uri.length <= limit)
    }
  }

  @Test
  fun `a long recipe is trimmed to fit the real limit`() {
    val encoded = RecipeShareCodec.encode(withLongTexts(notesLength = 300, instructionLength = 120), null)!!
    assertTrue(encoded.uri.length <= RecipeShareCodec.MAX_URI_LENGTH)
    assertTrue(encoded.trimmed.isNotEmpty())
  }

  // --- Recuento y recorte parcial ---

  @Test
  fun `the sharer is told how many instructions were removed and how many were kept`() {
    val none = RecipeShareCodec.encode(longTexts, null, Int.MAX_VALUE)!!
    assertEquals(0 to 5, none.droppedInstructions to none.keptInstructions)
    assertFalse(none.partialInstructions)
    val some = RecipeShareCodec.encode(longTexts, null, lengthAt(longTexts, noNotes(2)))!!
    assertEquals(2 to 3, some.droppedInstructions to some.keptInstructions)
    assertTrue(some.partialInstructions)
    val all = RecipeShareCodec.encode(longTexts, null, lengthAt(longTexts, noInstructions))!!
    assertEquals(5 to 0, all.droppedInstructions to all.keptInstructions)
    assertFalse(all.partialInstructions)
  }

  @Test
  fun `a partially trimmed qr is partial on import and a fully trimmed one is not`() {
    val partial = RecipeShareCodec.encode(longTexts, null, lengthAt(longTexts, noNotes(2)))!!
    assertTrue(decoded(partial.uri).partialInstructions)
    // El QR que generaba la app anterior: todas las instrucciones fuera de golpe.
    val old = uriOf(encodePayload(longTexts, null, noInstructions).bytes)
    val shared = decoded(old)
    assertTrue(TrimmedPart.INSTRUCTIONS in shared.trimmed)
    assertFalse(shared.partialInstructions)
    assertFalse(decoded(RecipeShareCodec.encode(longTexts, null, Int.MAX_VALUE)!!.uri).partialInstructions)
  }

  // --- Inválida frente a demasiado larga ---

  @Test
  fun `an invalid recipe and a recipe that does not fit give different results`() {
    val invalid = v60Classic().copy(doseG = 0.0)
    val tooLong = lengthAt(longTexts, noNames) - 1
    assertEquals(EncodeResult.Invalid, RecipeShareCodec.encodeResult(invalid, null))
    assertEquals(EncodeResult.TooLong, RecipeShareCodec.encodeResult(longTexts, null, tooLong))
    assertNull(RecipeShareCodec.encode(invalid, null))
    assertNull(RecipeShareCodec.encode(longTexts, null, tooLong))
    assertTrue(RecipeShareCodec.encodeResult(v60Classic(), null) is EncodeResult.Encoded)
  }

  @Test
  fun `an invalid recipe is invalid even when it is also too long`() {
    val invalid = longTexts.copy(doseG = 0.0)
    assertEquals(EncodeResult.Invalid, RecipeShareCodec.encodeResult(invalid, null, 10))
  }

  // --- La receta del usuario y el E2E ---

  @Test
  fun `the four six recipe of the user is shared without trimming`() {
    val recipe = fourSixUserRecipe()
    val result = RecipeShareCodec.encodeResult(recipe, femobookA2)
    assertTrue("se esperaba Encoded y fue $result", result is EncodeResult.Encoded)
    val encoded = (result as EncodeResult.Encoded).encoded
    println("4:6 del usuario: ${encoded.uri.length} caracteres, QR versión ${qrVersionOf(encoded.uri)}")
    println("4:6 URI: ${encoded.uri}")
    assertTrue(encoded.trimmed.isEmpty())
    assertEquals(0, encoded.droppedInstructions)
    val shared = decoded(encoded.uri)
    assertEquals(expected(recipe), shared.recipe)
    assertEquals(recipe.notes, shared.recipe.notes)
    assertEquals(10, shared.recipe.stages.count { it.instruction != null })
    assertEquals(5, shared.recipe.stages.count { it.name != null })
    assertEquals(SharedGrind("femobook-a2", "Femobook", "A2", 34.0, null), shared.grind)
    assertTrue(encoded.uri.length <= RecipeShareCodec.MAX_URI_LENGTH)
  }

  @Test
  fun `the worst case uri for the end to end check is printed`() {
    val max = RecipeShareCodec.MAX_URI_LENGTH
    fun recipe(notesLength: Int) = Recipe(
      name = randomText(60, seed = 7),
      method = BrewMethod.V60,
      waterTempC = 93.0,
      notes = if (notesLength > 0) randomText(notesLength, seed = 7) else null,
      doseG = 20.0,
      stages = alternatingStages(),
    )
    fun uri(n: Int) = RecipeShareCodec.encode(recipe(n), null, max)?.takeIf { it.trimmed.isEmpty() }?.uri
    var low = 0
    var high = 1_000
    while (low < high) {
      val mid = (low + high + 1) / 2
      if (uri(mid) != null) low = mid else high = mid - 1
    }
    // Entre MAX − 24 y MAX − 8: la misma versión de QR y margen para la diferencia de zlib entre la JVM y Android (D43).
    val chosen = (maxOf(0, low - 40)..low).first { n -> uri(n)?.length?.let { it <= max - 8 } == true && uri(n)!!.length >= max - 24 }
    val worst = uri(chosen)!!
    println("E2E peor caso: ${worst.length} caracteres, QR versión ${qrVersionOf(worst)}")
    println("E2E URI: $worst")
    assertTrue(worst.length in max - 24..max - 8)
    assertTrue(decoded(worst).trimmed.isEmpty())
    assertEquals(20, decoded(worst).recipe.stages.size)
  }

  // --- Tope real ---

  @Test
  fun `the worst case recipe fits under MAX_URI_LENGTH with 60 characters to spare`() {
    val recipe = worstCaseRecipe()
    val encoded = RecipeShareCodec.encode(recipe, catalogGrinder)!!
    assertTrue(encoded.trimmed.isEmpty())
    val margin = RecipeShareCodec.MAX_URI_LENGTH - encoded.uri.length
    println("peor caso: ${encoded.uri.length} caracteres, margen $margin")
    assertTrue("margen $margin", margin >= 60)
    assertEquals(expected(recipe), decoded(encoded.uri).recipe)
    for (unitMs in listOf(100L, 1L)) {
      val other = RecipeShareCodec.encode(worstCaseRecipe(unitMs), catalogGrinder, Int.MAX_VALUE)!!
      println("peor caso con unidad $unitMs ms: ${other.uri.length} caracteres")
    }
  }

  // --- Errores ---

  @Test
  fun `surrounding spaces are accepted`() {
    assertEquals(expected(v60Classic()).stages, decoded("  \n$GOLDEN_V60_URI \t").recipe.stages)
  }

  @Test
  fun `another scheme, another host or no payload is not pourline`() {
    for (text in listOf("https://example.com/recipe?v=1&d=AAAA", "pourline://brew?v=1&d=AAAA", "pourline://recipe?v=1", "pourline://recipe?v=1&d=", "pourline://recipe", "hola mundo", "", "recipe")) {
      assertEquals(text, DecodeResult.NotPourline, RecipeShareCodec.decode(text))
    }
  }

  @Test
  fun `a newer version is rejected before reading the payload`() {
    assertEquals(DecodeResult.NewerVersion, RecipeShareCodec.decode("pourline://recipe?v=2&d=!!!!"))
    assertEquals(DecodeResult.NewerVersion, RecipeShareCodec.decode("pourline://recipe?d=basura&v=99"))
  }

  @Test
  fun `a missing or non numeric version is corrupt`() {
    for (text in listOf("pourline://recipe?d=AAAA", "pourline://recipe?v=x&d=AAAA", "pourline://recipe?v=1.5&d=AAAA", "pourline://recipe?v=0&d=AAAA", "pourline://recipe?v=&d=AAAA")) {
      assertEquals(text, DecodeResult.Corrupt, RecipeShareCodec.decode(text))
    }
  }

  @Test
  fun `bad base64 and bad deflate are corrupt`() {
    assertEquals(DecodeResult.Corrupt, RecipeShareCodec.decode("pourline://recipe?v=1&d=a"))
    assertEquals(DecodeResult.Corrupt, RecipeShareCodec.decode("pourline://recipe?v=1&d=%25%25"))
    val garbage = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(40) { 0xFF.toByte() })
    assertEquals(DecodeResult.Corrupt, RecipeShareCodec.decode("pourline://recipe?v=1&d=$garbage"))
    val truncated = GOLDEN_V60_URI.dropLast(40)
    assertEquals(DecodeResult.Corrupt, RecipeShareCodec.decode(truncated))
  }

  @Test
  fun `a truncated payload is corrupt`() {
    val full = hexBytes(GOLDEN_V60_PAYLOAD)
    for (length in 0 until full.size) {
      assertEquals("prefijo de $length bytes", DecodeResult.Corrupt, decodePayload(full.copyOf(length)))
    }
  }

  @Test
  fun `trailing bytes are corrupt`() {
    assertTrue(decodePayload(minimal()) is DecodeResult.Ok)
    assertEquals(DecodeResult.Corrupt, decodePayload(minimal { uvar(0) }))
  }

  @Test
  fun `an undefined flag bit is corrupt`() {
    assertEquals(DecodeResult.Corrupt, decodePayload(minimal(flags = 1L shl 10)))
    assertEquals(DecodeResult.Corrupt, decodePayload(minimal(flags = 1L shl 40)))
    // La unidad de tiempo 3 no existe.
    assertEquals(DecodeResult.Corrupt, decodePayload(minimal(flags = 3L shl ShareFormat.TIME_UNIT_SHIFT)))
    // Bits sin definir en sflags y en gflags.
    assertEquals(DecodeResult.Corrupt, decodePayload(minimal(stageFlags = 1L shl 4)))
    val badGrind = write {
      uvar(ShareFormat.F_GRIND.toLong())
      str("Café")
      uvar(150)
      uvar(1 shl 4)
    }
    assertEquals(DecodeResult.Corrupt, decodePayload(badGrind))
    val emptyGrind = write {
      uvar(ShareFormat.F_GRIND.toLong())
      str("Café")
      uvar(150)
      uvar(0)
    }
    assertEquals(DecodeResult.Corrupt, decodePayload(emptyGrind))
  }

  @Test
  fun `an unknown method code is corrupt`() {
    fun withMethod(code: Long) = write {
      uvar(ShareFormat.F_METHOD.toLong())
      str("Café")
      uvar(150)
      uvar(code)
      uvar(1)
      uvar(0)
      uvar(0)
      uvar(10)
      uvar(400)
    }
    assertTrue(decodePayload(withMethod(9)) is DecodeResult.Ok)
    assertEquals(DecodeResult.Corrupt, decodePayload(withMethod(10)))
    assertEquals(DecodeResult.Corrupt, decodePayload(withMethod(255)))
  }

  @Test
  fun `type code 3 is an action`() {
    // Un POUR de 40 g y un paso con sflags = 3 (ACTION, sin agua): se decodifican con el agua derivada.
    val payload = write {
      uvar(0)
      str("Café")
      uvar(150)
      uvar(2)
      uvar(0)
      uvar(0)
      uvar(10)
      uvar(400)
      uvar(3)
      uvar(0)
      uvar(5)
    }
    val shared = (decodePayload(payload) as DecodeResult.Ok).shared
    assertEquals(listOf(StageType.POUR, StageType.ACTION), shared.recipe.stages.map { it.type })
    assertEquals(listOf(40.0, 40.0), shared.recipe.stages.map { it.targetWaterG })
    // Con una sola Acción no hay vertido: la receta no es válida.
    assertEquals(DecodeResult.Invalid, decodePayload(minimal(stageFlags = 3)))
  }

  @Test
  fun `a recipe with actions round trips`() {
    val recipe = aeroPressWithActions()
    val encoded = RecipeShareCodec.encode(recipe, null, Int.MAX_VALUE)!!
    // El URI para la prueba de extremo a extremo por adb (plan §7.4).
    println("AEROPRESS_URI=${encoded.uri}")
    assertEquals(expected(recipe), decoded(encoded.uri).recipe)
    val types = decoded(encoded.uri).recipe.stages.map { it.type }
    assertEquals(
      listOf(StageType.POUR, StageType.SWIRL, StageType.ACTION, StageType.WAIT, StageType.ACTION, StageType.ACTION),
      types,
    )
    assertEquals(listOf(null, null, "Pon la tapa", null, "Invierte", null), decoded(encoded.uri).recipe.stages.map { it.instruction })
  }

  @Test
  fun `an action writes no water`() {
    val recipe = Recipe(
      name = "Café",
      doseG = 15.0,
      stages = listOf(
        RecipeStage(null, 0, 40.0, 10_000),
        RecipeStage(null, 10_000, 40.0, 5_000, StageType.ACTION, "Pon la tapa"),
      ),
    )
    val expected = write {
      uvar(0) // flags: unidad de 1 s (código 0), sin nada más
      str("Café")
      uvar(150)
      uvar(2)
      uvar(0) // POUR sin nombre ni instrucción
      uvar(0)
      uvar(10)
      uvar(400) // agua del vertido
      uvar((3 or ShareFormat.S_INSTRUCTION).toLong()) // ACTION con instrucción, sin agua
      uvar(0)
      uvar(5)
      str("Pon la tapa")
    }
    assertEquals(hex(expected), hex(encodePayload(recipe, null, TrimPlan.NONE).bytes))
  }

  @Test
  fun `an action instruction is trimmed like any other`() {
    val recipe = Recipe(
      name = "AeroPress",
      doseG = 15.0,
      stages = listOf(
        RecipeStage(null, 0, 200.0, 10_000),
        RecipeStage(null, 10_000, 200.0, 5_000, StageType.ACTION, randomText(80, seed = 5)),
        RecipeStage(null, 15_000, 200.0, 30_000, StageType.WAIT),
        RecipeStage(null, 45_000, 200.0, 5_000, StageType.ACTION, randomText(80, seed = 6)),
      ),
    )
    val limit = lengthAt(recipe, TrimPlan.NONE) - 1
    val encoded = RecipeShareCodec.encode(recipe, null, limit)!!
    assertEquals(setOf(TrimmedPart.INSTRUCTIONS), encoded.trimmed)
    assertEquals(1 to 1, encoded.droppedInstructions to encoded.keptInstructions)
    val shared = decoded(encoded.uri)
    assertEquals(StageType.ACTION, shared.recipe.stages.last().type)
    assertNull(shared.recipe.stages.last().instruction)
    assertEquals(recipe.stages[1].instruction, shared.recipe.stages[1].instruction)
  }

  @Test
  fun `method other without other method is corrupt`() {
    val withoutMethod = write {
      uvar(ShareFormat.F_METHOD_OTHER.toLong())
      str("Café")
      uvar(150)
      str("Sifón")
    }
    assertEquals(DecodeResult.Corrupt, decodePayload(withoutMethod))
    val withV60 = write {
      uvar((ShareFormat.F_METHOD or ShareFormat.F_METHOD_OTHER).toLong())
      str("Café")
      uvar(150)
      uvar(0)
      str("Sifón")
    }
    assertEquals(DecodeResult.Corrupt, decodePayload(withV60))
  }

  @Test
  fun `an oversized inflated payload is corrupt`() {
    val deflater = Deflater(9, true)
    deflater.setInput(ByteArray(RecipeShareCodec.MAX_INFLATED_BYTES + 1000))
    deflater.finish()
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(512)
    while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
    deflater.end()
    val uri = "pourline://recipe?v=1&d=" + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    assertEquals(DecodeResult.Corrupt, RecipeShareCodec.decode(uri))
  }

  @Test
  fun `a stage count above 20 is invalid before reading stages`() {
    assertEquals(DecodeResult.Invalid, decodePayload(minimal(stageCount = 21)))
    assertEquals(DecodeResult.Invalid, decodePayload(minimal(stageCount = 1_000_000_000)))
    assertEquals(DecodeResult.Invalid, decodePayload(minimal(stageCount = 0)))
  }

  @Test
  fun `a payload that fails the validator is invalid`() {
    fun payload(name: String = "Café", dose: Long = 150, stageFlags: Long = 0, duration: Long = 10, water: Long = 400) = write {
      uvar(0)
      str(name)
      uvar(dose)
      uvar(1)
      uvar(stageFlags)
      uvar(0)
      uvar(duration)
      if (stageFlags and 3 == 0L) uvar(water)
    }
    assertTrue(decodePayload(payload()) is DecodeResult.Ok)
    assertEquals("dosis 0", DecodeResult.Invalid, decodePayload(payload(dose = 0)))
    assertEquals("dosis de 70 g", DecodeResult.Invalid, decodePayload(payload(dose = 700)))
    assertEquals("nombre en blanco", DecodeResult.Invalid, decodePayload(payload(name = "  ")))
    assertEquals("sin POUR", DecodeResult.Invalid, decodePayload(payload(stageFlags = 2)))
    assertEquals("duración 0", DecodeResult.Invalid, decodePayload(payload(duration = 0)))
    assertEquals("agua 0", DecodeResult.Invalid, decodePayload(payload(water = 0)))
  }

  // --- Contrato ---

  @Test
  fun `the classic V60 payload matches the golden bytes`() {
    assertEquals(GOLDEN_V60_PAYLOAD, hex(encodePayload(v60Classic(), null, TrimPlan.NONE).bytes))
  }

  @Test
  fun `the golden v1 uri decodes to the classic V60`() {
    val shared = decoded(GOLDEN_V60_URI)
    assertEquals(expected(v60Classic()), shared.recipe)
    assertNull(shared.grind)
    assertTrue(shared.trimmed.isEmpty())
  }

  @Test
  fun `the scanner worst case uri decodes to twenty stages`() {
    assertEquals(331, WORST_CASE_URI.length)
    val shared = decoded(WORST_CASE_URI)
    assertEquals(20, shared.recipe.stages.size)
    assertEquals(60, shared.recipe.name.length)
    assertEquals(78, shared.recipe.notes!!.length)
    assertEquals(SharedGrind("timemore_c3_pro", "Timemore", "Chestnut C3 Pro", 24.0, null), shared.grind)
  }

  // --- Ayudas ---

  private fun qrVersionOf(uri: String): Int = dev.josueamaya.pourline.share.qrVersion(uri)

  /** El payload inflado de [uri]: lo que escribió `encodePayload` antes de `deflate`. */
  private fun inflatedPayload(uri: String): ByteArray {
    val compressed = Base64.getUrlDecoder().decode(uri.substringAfter("&d="))
    val inflater = java.util.zip.Inflater(true)
    inflater.setInput(compressed + byteArrayOf(0))
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    while (!inflater.finished()) {
      val read = inflater.inflate(buffer)
      if (read == 0) break
      out.write(buffer, 0, read)
    }
    inflater.end()
    return out.toByteArray()
  }

  private fun hexBytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

  /** El primer `uvar` de un payload: sus `flags`. */
  private fun firstUvar(bytes: ByteArray): Long = ByteReader(bytes).uvar()
}
