package dev.josueamaya.pourline.coffee.label

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.LocalDate

/** Una etiqueta real capturada con `LabelFixtureCaptureTest`: lo que ML Kit leyó, sin la foto. */
class LabelFixture(val file: String, val capturedOn: LocalDate, val lines: List<OcrLine>)

object LabelFixtures {
  /** Los JSON de `app/src/test/resources/labels/`, por nombre de archivo. */
  fun load(): List<LabelFixture> {
    val dir = LabelFixtures::class.java.classLoader.getResource("labels")?.let { File(it.toURI()) } ?: return emptyList()
    return dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.map { file ->
      val json = Json.parseToJsonElement(file.readText()).jsonObject
      LabelFixture(
        file = json.getValue("file").jsonPrimitive.content,
        capturedOn = LocalDate.parse(json.getValue("capturedOn").jsonPrimitive.content),
        lines = json.getValue("lines").jsonArray.map {
          val line = it.jsonObject
          OcrLine(line.getValue("text").jsonPrimitive.content, line.getValue("heightPx").jsonPrimitive.int)
        },
      )
    }
  }
}
