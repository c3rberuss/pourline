package dev.dotscale.ui.coffees

import dev.dotscale.coffee.CoffeeDraft
import dev.dotscale.coffee.CoffeeError
import dev.dotscale.coffee.CoffeeProcess
import dev.dotscale.coffee.CoffeeValidator
import dev.dotscale.coffee.RoastLevel
import dev.dotscale.coffee.label.LabelFields
import java.time.LocalDate
import java.util.Locale

/** Cada campo editable del formulario. Es la clave de los errores y de las marcas "Leído de la etiqueta". */
enum class CoffeeField {
  NAME, ROASTER, COUNTRY, REGION, FARM, VARIETIES, PROCESS, PROCESS_OTHER, ALTITUDE_MIN, ALTITUDE_MAX,
  ROAST_LEVEL, ROAST_DATE, ROASTER_NOTES, BAG_WEIGHT, PRICE, OPENED_DATE;

  /** El campo que lleva la marca del OCR: la altitud y el proceso cuentan como uno solo. */
  val mark: CoffeeField
    get() = when (this) {
      ALTITUDE_MAX -> ALTITUDE_MIN
      PROCESS_OTHER -> PROCESS
      else -> this
    }
}

/** Resultado de aplicar una lectura del OCR: el formulario nuevo y los campos que se rellenaron. */
data class OcrResult(val form: CoffeeForm, val filled: Set<CoffeeField>)

/**
 * El formulario del café con los textos tal como se escriben (altitudes, peso y precio), las fechas ya como
 * fechas y las variedades como lista más el texto pendiente de añadir. [ocrFilled] son los campos que rellenó
 * el OCR y que el usuario aún no ha editado (D8): no se guarda.
 */
data class CoffeeForm(
  val id: Long? = null,
  val name: String = "",
  val roaster: String = "",
  val country: String = "",
  val region: String = "",
  val farm: String = "",
  val varieties: List<String> = emptyList(),
  val varietyText: String = "",
  val process: CoffeeProcess? = null,
  val processOther: String = "",
  val altitudeMin: String = "",
  val altitudeMax: String = "",
  val roastLevel: RoastLevel? = null,
  val roastDate: LocalDate? = null,
  val roasterNotes: String = "",
  val bagWeight: String = "",
  val price: String = "",
  val openedDate: LocalDate? = null,
  val photo1: String? = null,
  val photo2: String? = null,
  val ocrFilled: Set<CoffeeField> = emptySet(),
) {
  /** Aplica [transform] como una edición del usuario sobre [field]: le quita la marca del OCR. */
  fun edit(field: CoffeeField, transform: CoffeeForm.() -> CoffeeForm): CoffeeForm {
    val edited = transform()
    return edited.copy(ocrFilled = ocrFilled - field.mark)
  }

  /** Rellena solo los campos vacíos con lo que leyó el OCR y marca los rellenados. Nunca pisa lo escrito. */
  fun applyOcr(fields: LabelFields): OcrResult {
    var next = this
    val filled = mutableSetOf<CoffeeField>()
    fun text(field: CoffeeField, current: String, read: String?, set: CoffeeForm.(String) -> CoffeeForm) {
      if (current.isBlank() && !read.isNullOrBlank()) {
        next = next.set(read)
        filled += field
      }
    }
    text(CoffeeField.NAME, name, fields.name) { copy(name = it) }
    text(CoffeeField.ROASTER, roaster, fields.roaster) { copy(roaster = it) }
    text(CoffeeField.COUNTRY, country, fields.originCountry) { copy(country = it) }
    text(CoffeeField.REGION, region, fields.originRegion) { copy(region = it) }
    text(CoffeeField.FARM, farm, fields.farm) { copy(farm = it) }
    if (varieties.isEmpty() && fields.varieties.isNotEmpty()) {
      next = next.copy(varieties = fields.varieties)
      filled += CoffeeField.VARIETIES
    }
    if (process == null && fields.process != null) {
      next = next.copy(
        process = fields.process,
        processOther = if (fields.process == CoffeeProcess.OTHER) fields.processOther.orEmpty() else "",
      )
      filled += CoffeeField.PROCESS
    }
    val min = fields.altitudeMinM ?: fields.altitudeMaxM
    val max = fields.altitudeMaxM ?: fields.altitudeMinM
    if (altitudeMin.isBlank() && altitudeMax.isBlank() && min != null && max != null) {
      next = next.copy(altitudeMin = min.toString(), altitudeMax = max.toString())
      filled += CoffeeField.ALTITUDE_MIN
    }
    if (roastLevel == null && fields.roastLevel != null) {
      next = next.copy(roastLevel = fields.roastLevel)
      filled += CoffeeField.ROAST_LEVEL
    }
    if (roastDate == null && fields.roastDate != null) {
      next = next.copy(roastDate = fields.roastDate)
      filled += CoffeeField.ROAST_DATE
    }
    if (bagWeight.isBlank() && fields.bagWeightG != null) {
      next = next.copy(bagWeight = formatDecimal(fields.bagWeightG, 1))
      filled += CoffeeField.BAG_WEIGHT
    }
    return OcrResult(next.copy(ocrFilled = ocrFilled + filled), filled)
  }

  private class Parsed(
    val draft: CoffeeDraft,
    val errors: Map<CoffeeField, CoffeeError>,
  )

  private fun parse(today: LocalDate): Parsed {
    val errors = linkedMapOf<CoffeeField, CoffeeError>()
    val min = parseInt(altitudeMin)
    val max = parseInt(altitudeMax)
    val weight = parseDecimal(bagWeight, maxDecimals = 1)
    val priceValue = parseDecimal(price, maxDecimals = 2)
    if (!min.valid) errors[CoffeeField.ALTITUDE_MIN] = CoffeeError.ALTITUDE_INVALID
    if (!max.valid) errors.putIfAbsent(CoffeeField.ALTITUDE_MIN, CoffeeError.ALTITUDE_INVALID)
    if (!weight.valid) errors[CoffeeField.BAG_WEIGHT] = CoffeeError.BAG_WEIGHT_INVALID
    if (!priceValue.valid) errors[CoffeeField.PRICE] = CoffeeError.PRICE_INVALID
    val draft = CoffeeDraft(
      id = id,
      name = name,
      roaster = roaster,
      originCountry = country,
      originRegion = region,
      farm = farm,
      varieties = varieties + varietyText,
      process = process,
      processOther = processOther,
      altitudeMinM = min.value,
      altitudeMaxM = max.value,
      roastLevel = roastLevel,
      roastDate = roastDate,
      roasterNotes = roasterNotes,
      bagWeightG = weight.value,
      price = priceValue.value,
      openedDate = openedDate,
      photo1 = photo1,
      photo2 = photo2,
    ).normalized()
    CoffeeValidator.validate(draft, today).forEach { error ->
      val field = when (error) {
        CoffeeError.NAME_REQUIRED -> CoffeeField.NAME
        CoffeeError.ALTITUDE_INVALID, CoffeeError.ALTITUDE_RANGE -> CoffeeField.ALTITUDE_MIN
        CoffeeError.BAG_WEIGHT_INVALID -> CoffeeField.BAG_WEIGHT
        CoffeeError.PRICE_INVALID -> CoffeeField.PRICE
        CoffeeError.ROAST_DATE_FUTURE -> CoffeeField.ROAST_DATE
        CoffeeError.OPENED_DATE_FUTURE -> CoffeeField.OPENED_DATE
      }
      errors.putIfAbsent(field, error)
    }
    return Parsed(draft, errors)
  }

  /** Errores por campo (parseo de D34 más `CoffeeValidator`). Vacío = se puede guardar. */
  fun errors(today: LocalDate): Map<CoffeeField, CoffeeError> = parse(today).errors

  /** El borrador a guardar. Con errores, los números mal escritos quedan como `null`: valida antes con [errors]. */
  fun toDraft(): CoffeeDraft = parse(LocalDate.MAX).draft

  /** `true` si difiere del [initial]; las marcas del OCR no cuentan. */
  fun isDirty(initial: CoffeeForm): Boolean = copy(ocrFilled = emptySet()) != initial.copy(ocrFilled = emptySet())

  companion object {
    fun from(draft: CoffeeDraft): CoffeeForm = CoffeeForm(
      id = draft.id,
      name = draft.name,
      roaster = draft.roaster.orEmpty(),
      country = draft.originCountry.orEmpty(),
      region = draft.originRegion.orEmpty(),
      farm = draft.farm.orEmpty(),
      varieties = draft.varieties,
      process = draft.process,
      processOther = draft.processOther.orEmpty(),
      altitudeMin = draft.altitudeMinM?.toString().orEmpty(),
      altitudeMax = draft.altitudeMaxM?.toString().orEmpty(),
      roastLevel = draft.roastLevel,
      roastDate = draft.roastDate,
      roasterNotes = draft.roasterNotes.orEmpty(),
      bagWeight = draft.bagWeightG?.let { formatDecimal(it, 1) }.orEmpty(),
      price = draft.price?.let { formatDecimal(it, 2, trim = false) }.orEmpty(),
      openedDate = draft.openedDate,
      photo1 = draft.photo1,
      photo2 = draft.photo2,
    )
  }
}

private class ParsedNumber<T>(val value: T?, val valid: Boolean)

/** Altitud (D34): enteros. Vacío es válido y vale `null`. */
private fun parseInt(text: String): ParsedNumber<Int> {
  val t = text.trim()
  if (t.isEmpty()) return ParsedNumber(null, true)
  val n = if (Regex("\\d{1,6}").matches(t)) t.toInt() else null
  return ParsedNumber(n, n != null)
}

/** Peso y precio (D34): `,` o `.` y hasta [maxDecimals] decimales. */
private fun parseDecimal(text: String, maxDecimals: Int): ParsedNumber<Double> {
  val t = text.trim()
  if (t.isEmpty()) return ParsedNumber(null, true)
  val ok = Regex("\\d{1,7}(?:[.,]\\d{1,$maxDecimals})?").matches(t)
  return if (ok) ParsedNumber(t.replace(',', '.').toDouble(), true) else ParsedNumber(null, false)
}

/** Número con punto decimal para el campo de texto: sin ceros sobrantes salvo que [trim] sea `false`. */
internal fun formatDecimal(value: Double, decimals: Int, trim: Boolean = true): String {
  val text = String.format(Locale.US, "%.${decimals}f", value)
  return if (trim && text.contains('.')) text.trimEnd('0').trimEnd('.') else text
}
