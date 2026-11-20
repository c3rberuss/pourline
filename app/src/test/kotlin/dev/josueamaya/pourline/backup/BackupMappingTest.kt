package dev.josueamaya.pourline.backup

import dev.josueamaya.pourline.coffee.CoffeeProcess
import dev.josueamaya.pourline.grinder.GrindLevel
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.BrewCurveEntity
import dev.josueamaya.pourline.history.BrewTargetEntity
import dev.josueamaya.pourline.history.ReferenceType
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.settings.SettingsSnapshot
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupMappingTest {

  private fun encode(rows: BackupRows): String = BackupJson.encodeToString(DataDto.serializer(), rows.toDto())

  private fun decode(json: String): DataDto = BackupJson.decodeFromString(DataDto.serializer(), json)

  private fun jsonOf(rows: BackupRows): JsonObject = BackupJson.parseToJsonElement(encode(rows)).jsonObject

  /** Reescribe el JSON de [rows] cambiando una fila de [table] con [change]. */
  private fun edited(rows: BackupRows, table: String, index: Int = 0, change: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): String {
    val root = jsonOf(rows)
    val list = root.getValue(table).jsonArray.toMutableList()
    val row = list[index].jsonObject.toMutableMap()
    change(row)
    list[index] = JsonObject(row)
    return JsonObject(root + (table to JsonArray(list))).toString()
  }

  @Test
  fun `every table round trips through json without differences`() {
    val original = fullRows()
    val back = decode(encode(original)).toRows()
    assertRowsEqual(original, back)
    // Las 11 tablas tienen filas en el fixture.
    listOf(
      original.brews, original.brewCurves, original.brewTargets, original.recipes, original.recipeStages, original.coffees, original.grinders,
      original.grinderUnits, original.grinderMethodRanges, original.grinderAnchors, original.grindCalibrations,
    ).forEach { assertTrue(it.size >= 2) }
    assertEquals(setOf(StageType.POUR, StageType.SWIRL, StageType.WAIT, StageType.ACTION), original.recipeStages.map { it.type }.toSet())
  }

  @Test
  fun `ids, foreign keys and null columns are kept`() {
    val original = fullRows()
    val back = decode(encode(original)).toRows()
    assertEquals(original.brews.map { it.id }, back.brews.map { it.id })
    assertEquals(listOf<Long?>(1, null, null), back.brews.map { it.coffeeId })
    assertEquals(listOf<Long?>(1, null, null), back.brews.map { it.grinderId })
    assertEquals(listOf<Long?>(1, null, 99), back.brews.map { it.referenceId })
    assertEquals(listOf(1L, 2L, 3L), back.brewCurves.map { it.brewId })
    assertEquals(listOf(1L, 1L, 1L, 1L, 2L), back.recipeStages.map { it.recipeId })
    assertEquals(listOf<Long?>(1, null), back.recipes.map { it.grinderId })
    assertNull(back.coffees[1].photo1)
    assertEquals(PHOTO_C, back.coffees[1].photo2)
    assertNull(back.brews[1].stabilityLevel)
    assertNull(back.brews[1].scaleModel)
    assertNull(back.brews[1].referenceType)
    assertEquals(1L to 2L, back.grindCalibrations[0].grinderAId to back.grindCalibrations[0].grinderBId)
    assertEquals(1_700_000_500_000L, back.coffees[0].lastUsedAtEpochMs)
  }

  @Test
  fun `byte arrays are written as base64 and read back byte for byte`() {
    val original = fullRows().copy(
      brewCurves = listOf(BrewCurveEntity(1, ByteArray(0)), BrewCurveEntity(2, ByteArray(300) { (it * 7).toByte() })),
      brewTargets = listOf(BrewTargetEntity(1, byteArrayOf(0, 1, -1, 127, -128))),
    )
    val json = jsonOf(original)
    val curves = json.getValue("brew_curves").jsonArray
    assertEquals("", curves[0].jsonObject.getValue("data").jsonPrimitive.content)
    val text = curves[1].jsonObject.getValue("data").jsonPrimitive.content
    assertEquals(java.util.Base64.getEncoder().encodeToString(ByteArray(300) { (it * 7).toByte() }), text)
    assertFalse("sin saltos de línea", text.contains('\n'))
    assertEquals("AAH/f4A=", json.getValue("brew_targets").jsonArray[0].jsonObject.getValue("data").jsonPrimitive.content)
    val back = decode(json.toString()).toRows()
    assertArrayEquals(ByteArray(0), back.brewCurves[0].data)
    assertArrayEquals(ByteArray(300) { (it * 7).toByte() }, back.brewCurves[1].data)
    assertArrayEquals(byteArrayOf(0, 1, -1, 127, -128), back.brewTargets[0].data)
  }

  @Test
  fun `enums are written by name and an unknown name falls back like the converters`() {
    val json = jsonOf(fullRows())
    assertEquals("V60", json.getValue("brews").jsonArray[0].jsonObject.getValue("method").jsonPrimitive.content)
    assertEquals("ACTION", json.getValue("recipe_stages").jsonArray[3].jsonObject.getValue("type").jsonPrimitive.content)
    assertEquals("NATURAL", json.getValue("coffees").jsonArray[0].jsonObject.getValue("process").jsonPrimitive.content)

    val rows = fullRows()
    val withUnknownMethod = decode(edited(rows, "brews") { it["method"] = JsonPrimitive("SIPHON") }).toRows()
    assertEquals(BrewMethod.OTHER, withUnknownMethod.brews[0].method)
    val withUnknownStage = decode(edited(rows, "recipe_stages") { it["type"] = JsonPrimitive("KNEAD") }).toRows()
    assertEquals(StageType.POUR, withUnknownStage.recipeStages[0].type)
    val withUnknownProcess = decode(edited(rows, "coffees") { it["process"] = JsonPrimitive("FERMENTED") }).toRows()
    assertNull(withUnknownProcess.coffees[0].process)
    val withUnknownReference = decode(edited(rows, "brews") { it["referenceType"] = JsonPrimitive("OTHER_KIND") }).toRows()
    assertNull(withUnknownReference.brews[0].referenceType)
    val withUnknownLevel = decode(edited(rows, "grinder_anchors") { it["level"] = JsonPrimitive("ULTRA") }).toRows()
    assertEquals(GrindLevel.EXTRA_FINE, withUnknownLevel.grinderAnchors[0].level)
    assertEquals(CoffeeProcess.NATURAL, rows.coffees[0].process)
  }

  @Test
  fun `dates are iso days and a malformed one is corrupt`() {
    val rows = fullRows()
    val coffee = jsonOf(rows).getValue("coffees").jsonArray[0].jsonObject
    assertEquals("2026-10-30", coffee.getValue("roastDate").jsonPrimitive.content)
    assertEquals("2026-11-02", coffee.getValue("openedDate").jsonPrimitive.content)
    val shifted = decode(edited(rows, "coffees") { it["roastDate"] = JsonPrimitive("2027-02-28") }).toRows()
    assertEquals(LocalDate.of(2027, 2, 28), shifted.coffees[0].roastDate)

    val bad = decode(edited(rows, "coffees") { it["openedDate"] = JsonPrimitive("02/11/2026") })
    val failure = assertThrows(BackupFailure::class.java) { bad.toRows() }
    assertEquals(BackupError.CORRUPT, failure.reason)
  }

  @Test
  fun `a missing required field or a wrong type is corrupt`() {
    val rows = fullRows()
    // Un campo no anulable ausente.
    assertThrows(SerializationException::class.java) { decode(edited(rows, "brews") { it.remove("totalTimeMs") }) }
    assertThrows(SerializationException::class.java) { decode(edited(rows, "recipes") { it.remove("name") }) }
    // Un tipo distinto.
    assertThrows(SerializationException::class.java) { decode(edited(rows, "recipes") { it["doseG"] = JsonPrimitive("mucho") }) }
    assertThrows(SerializationException::class.java) { decode(edited(rows, "grinders") { it["owned"] = JsonPrimitive("quizá") }) }
    // Un Base64 inválido es IllegalArgumentException o SerializationException: el lector trata ambas como corruptas.
    assertThrows(Exception::class.java) { decode(edited(rows, "brew_curves") { it["data"] = JsonPrimitive("%%%no%%%") }) }
    // Una tabla ausente y una clave desconocida.
    val root = jsonOf(rows)
    assertThrows(SerializationException::class.java) { decode(JsonObject(root - "coffees").toString()) }
    assertThrows(SerializationException::class.java) { decode(JsonObject(root + ("extra" to JsonArray(emptyList()))).toString()) }
    assertThrows(SerializationException::class.java) { decode(edited(rows, "coffees") { it["unknownColumn"] = JsonPrimitive(1) }) }
  }

  @Test
  fun `null fields are not written and absent nullable fields read as null`() {
    val rows = fullRows()
    val second = jsonOf(rows).getValue("coffees").jsonArray[1].jsonObject
    assertFalse(second.containsKey("roaster"))
    assertFalse(second.containsKey("photo1"))
    assertTrue(second.containsKey("photo2"))
    assertTrue("varieties", second.containsKey("varieties"))
    val first = jsonOf(rows).getValue("coffees").jsonArray[0].jsonObject
    assertFalse("processOther null no se escribe", first.containsKey("processOther"))
    // Una clave anulable ausente se lee como null.
    val read = decode(edited(rows, "coffees") { it.remove("photo2") }).toRows()
    assertNull(read.coffees[0].photo2)
    assertEquals(PHOTO_A, read.coffees[0].photo1)
  }

  @Test
  fun `settings keep only the present keys with their stored types`() {
    val snapshot = fullSettings()
    val text = BackupJson.encodeToString(SettingsDto.serializer(), snapshot.toDto())
    val json = BackupJson.parseToJsonElement(text).jsonObject
    assertEquals(17, json.size)
    assertEquals("15.3", json.getValue("default_dose_g").jsonPrimitive.content)
    assertEquals("true", json.getValue("onboarding_done").jsonPrimitive.content)
    assertFalse(json.containsKey("tour_seen"))
    val back = BackupJson.decodeFromString(SettingsDto.serializer(), text).toSnapshot()
    assertEquals(snapshot, back)
    assertEquals(15.3f, back.defaultDoseG)

    val partial = SettingsSnapshot(themeMode = "LIGHT", lastCoffeeId = 7)
    val partialText = BackupJson.encodeToString(SettingsDto.serializer(), partial.toDto())
    assertEquals(setOf("theme_mode", "last_coffee_id"), BackupJson.parseToJsonElement(partialText).jsonObject.keys)
    assertEquals(partial, BackupJson.decodeFromString(SettingsDto.serializer(), partialText).toSnapshot())
    assertEquals(SettingsSnapshot(), BackupJson.decodeFromString(SettingsDto.serializer(), "{}").toSnapshot())
    assertThrows(SerializationException::class.java) {
      BackupJson.decodeFromString(SettingsDto.serializer(), """{"auto_finish_seconds":"noventa"}""")
    }
  }

  @Test
  fun `counts follow the manifest rules`() {
    val rows = fullRows()
    val counts = rows.counts(presentPhotos = 3)
    assertEquals(BackupCounts(coffees = 2, grinders = 1, grindCalibrations = 2, recipes = 2, brews = 3, photos = 3), counts)
    assertEquals(ReferenceType.RECIPE, rows.brews[0].referenceType)
  }
}
