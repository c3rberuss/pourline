package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class CatalogFileDto(val version: Int, val grinders: List<CatalogGrinderDto>)

@Serializable
data class CatalogUnitDto(val unit: String, val perParent: Int? = null)

@Serializable
data class CatalogScaleDto(val min: Double, val max: Double)

/** Un rango por método, en unidades base; [source] es el texto de la fuente ("2.2.0 – 3.8.1"), solo para revisarlo. */
@Serializable
data class CatalogRangeDto(val min: Double, val max: Double, val source: String? = null)

@Serializable
data class CatalogGrinderDto(
  val key: String,
  val brand: String,
  val model: String,
  val units: List<CatalogUnitDto>,
  val scale: CatalogScaleDto,
  val step: Double = 1.0,
  val direction: String = GrindDirection.HIGHER_IS_COARSER.name,
  val micronRange: CatalogScaleDto? = null,
  val ranges: Map<String, CatalogRangeDto> = emptyMap(),
  val anchors: Map<String, Double> = emptyMap(),
  val anchorsManual: Boolean = false,
  val sourceUrl: String = "",
  val sourceName: String = "",
  val missing: List<String> = emptyList(),
  val notes: String? = null,
)

/** Un error del archivo del catálogo. */
sealed interface CatalogError {
  val message: String

  data class Malformed(override val message: String) : CatalogError

  data class Invalid(val id: String, val errors: List<GrinderError>) : CatalogError {
    override val message: String get() = "$id: $errors"
  }

  data class DuplicateKey(val id: String) : CatalogError {
    override val message: String get() = "clave repetida: $id"
  }

  data class Unknown(val id: String, val what: String) : CatalogError {
    override val message: String get() = "$id: $what"
  }

  data class MissingSource(val id: String) : CatalogError {
    override val message: String get() = "$id: sin sourceUrl"
  }

  data class MissingSourceName(val id: String) : CatalogError {
    override val message: String get() = "$id: sin sourceName"
  }
}

class CatalogException(val errors: List<CatalogError>) : Exception(errors.joinToString("; ") { it.message })

/** Un molino del archivo con los datos que solo sirven para revisarlo. */
data class CatalogEntry(
  val grinder: Grinder,
  val micronRange: GrindRange?,
  val anchorsManual: Boolean,
  val missing: List<String>,
  val notes: String?,
)

data class CatalogData(val version: Int, val entries: List<CatalogEntry>) {
  val grinders: List<Grinder> get() = entries.map { it.grinder }
}

/** Lee y valida el archivo del catálogo predefinido (D33). Rechaza el archivo **entero** si una entrada es inválida. */
object GrinderCatalogFile {
  private val json = Json { ignoreUnknownKeys = false }

  fun parse(text: String): Result<CatalogData> {
    val dto = try {
      json.decodeFromString<CatalogFileDto>(text)
    } catch (e: SerializationException) {
      return Result.failure(CatalogException(listOf(CatalogError.Malformed(e.message ?: "JSON inválido"))))
    } catch (e: IllegalArgumentException) {
      return Result.failure(CatalogException(listOf(CatalogError.Malformed(e.message ?: "JSON inválido"))))
    }
    val errors = mutableListOf<CatalogError>()
    val entries = mutableListOf<CatalogEntry>()
    val seen = mutableSetOf<String>()
    for (item in dto.grinders) {
      if (!seen.add(item.key)) errors += CatalogError.DuplicateKey(item.key)
      val entry = toEntry(item, errors)
      if (entry != null) entries += entry
    }
    return if (errors.isEmpty()) Result.success(CatalogData(dto.version, entries)) else Result.failure(CatalogException(errors))
  }

  private fun toEntry(dto: CatalogGrinderDto, errors: MutableList<CatalogError>): CatalogEntry? {
    val before = errors.size
    val id = dto.key
    val units = dto.units.map { u ->
      val unit = GrindUnit.entries.firstOrNull { it.name == u.unit }
      if (unit == null) errors += CatalogError.Unknown(id, "unidad desconocida: ${u.unit}")
      GrindUnitLevel(unit ?: GrindUnit.CLICKS, u.perParent)
    }
    val direction = GrindDirection.entries.firstOrNull { it.name == dto.direction }
    if (direction == null) errors += CatalogError.Unknown(id, "dirección desconocida: ${dto.direction}")
    val ranges = dto.ranges.mapNotNull { (name, r) ->
      val method = BrewMethod.entries.firstOrNull { it.name == name }
      if (method == null) errors += CatalogError.Unknown(id, "método desconocido: $name")
      method?.let { it to GrindRange(r.min, r.max) }
    }.toMap()
    val anchors = dto.anchors.mapNotNull { (name, value) ->
      val level = GrindLevel.entries.firstOrNull { it.name == name }
      if (level == null) errors += CatalogError.Unknown(id, "ancla desconocida: $name")
      level?.let { it to value }
    }.toMap()
    if (dto.sourceUrl.isBlank()) errors += CatalogError.MissingSource(id)
    if (dto.sourceName.isBlank()) errors += CatalogError.MissingSourceName(id)
    val grinder = Grinder(
      catalogKey = dto.key,
      brand = dto.brand,
      model = dto.model,
      units = units,
      scaleMin = dto.scale.min,
      scaleMax = dto.scale.max,
      step = dto.step,
      direction = direction ?: GrindDirection.HIGHER_IS_COARSER,
      ranges = ranges,
      anchors = anchors,
      sourceUrl = dto.sourceUrl.ifBlank { null },
      sourceName = dto.sourceName.trim().ifBlank { null },
    )
    val invalid = GrinderValidator.validate(grinder)
    if (invalid.isNotEmpty()) errors += CatalogError.Invalid(id, invalid)
    if (errors.size > before) return null
    return CatalogEntry(grinder, dto.micronRange?.let { GrindRange(it.min, it.max) }, dto.anchorsManual, dto.missing, dto.notes)
  }
}
