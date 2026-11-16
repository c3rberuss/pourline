package dev.dotscale.coffee.label

import dev.dotscale.coffee.CoffeeProcess
import dev.dotscale.coffee.RoastLevel
import java.io.File
import java.time.LocalDate

/** Android: ejecuta el OCR sobre la foto ya reducida. Un fallo del motor lanza excepción. */
interface LabelOcr {
  suspend fun read(photo: File): List<OcrLine>
}

/** Una línea de texto que el OCR vio, con el alto en píxeles de su caja (la más alta suele ser el nombre). */
data class OcrLine(val text: String, val heightPx: Int)

/** Lo que el parser deduce de una etiqueta. Todo es opcional: nunca se guarda sin que el usuario lo revise. */
data class LabelFields(
  val name: String? = null,
  val roaster: String? = null,
  val roastDate: LocalDate? = null,
  val process: CoffeeProcess? = null,
  /** Con `OTHER`, el texto leído ("Fermentación láctica"). */
  val processOther: String? = null,
  val varieties: List<String> = emptyList(),
  val roastLevel: RoastLevel? = null,
  val altitudeMinM: Int? = null,
  val altitudeMaxM: Int? = null,
  val originCountry: String? = null,
  val originRegion: String? = null,
  /** "Finca" o "Productor". */
  val farm: String? = null,
  val bagWeightG: Double? = null,
) {
  /** `true` si no se reconoció ningún campo. */
  val isEmpty: Boolean
    get() = this == LabelFields()
}
