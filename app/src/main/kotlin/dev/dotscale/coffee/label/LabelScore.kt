package dev.dotscale.coffee.label

import dev.dotscale.coffee.fold
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Lo que se espera de una etiqueta real: su nombre y la fecha de tueste (`null` si no la trae). */
data class LabelExpectation(val file: String, val name: String, val roastDate: LocalDate?)

/** Una fecha escrita a mano: se informa lo leído frente a lo esperado, pero no cuenta para el objetivo. */
data class HandwrittenDate(val file: String, val expected: LocalDate, val read: LocalDate?)

/** Una fila del informe: una foto. */
data class LabelScoreRow(
  val file: String,
  val expectedName: String,
  val readName: String?,
  val nameHit: Boolean,
  val readDate: LocalDate?,
  val readRoaster: String?,
)

/** Resultado de medir el parser contra los esperados (objetivos del §5 del spec). */
data class LabelScore(
  val rows: List<LabelScoreRow>,
  val nameHits: Int,
  val nameTotal: Int,
  val noDateTotal: Int,
  /** Etiquetas sin fecha en las que el parser leyó una (`archivo: fecha`): nunca debe haber ninguna. */
  val inventedDates: List<String>,
  val handwritten: List<HandwrittenDate>,
) {
  fun meetsTarget(minNameHits: Int = 12): Boolean = nameHits >= minNameHits && inventedDates.isEmpty()

  /** El `report.txt`. [details] añade por archivo un texto libre (por ejemplo, la orientación EXIF). */
  fun toReport(minNameHits: Int = 12, details: Map<String, String> = emptyMap()): String = buildString {
    appendLine("Etiquetas reales: informe del parser")
    appendLine()
    rows.forEach { r ->
      val extra = details[r.file]?.let { " | $it" }.orEmpty()
      appendLine(
        "${r.file} | esperado: ${r.expectedName} | leído: ${r.readName ?: "-"} | ${if (r.nameHit) "ACIERTO" else "FALLO"}" +
          " | fecha: ${r.readDate ?: "-"} | tostador (informativo): ${r.readRoaster ?: "-"}$extra",
      )
    }
    appendLine()
    appendLine("Nombres bien extraídos: $nameHits de $nameTotal (objetivo: $minNameHits)")
    appendLine("Fechas inventadas: ${inventedDates.size} de $noDateTotal etiquetas sin fecha (objetivo: 0)")
    inventedDates.forEach { appendLine("  $it") }
    appendLine("Fechas escritas a mano (no cuentan):")
    handwritten.forEach { appendLine("  ${it.file}: leída ${it.read ?: "-"}, esperada ${it.expected}") }
    appendLine("Objetivos: ${if (meetsTarget(minNameHits)) "CUMPLIDOS" else "NO CUMPLIDOS"}")
  }
}

/** Lee `archivo<TAB>nombre<TAB>aaaa-mm-dd` (fecha vacía = sin fecha). Una línea mal formada da error con su número. */
fun parseExpected(tsv: String): List<LabelExpectation> =
  tsv.lines().withIndex().filter { it.value.isNotBlank() }.map { (i, line) ->
    val cols = line.split('\t')
    require(cols.size in 2..3 && cols[0].isNotBlank() && cols[1].isNotBlank()) {
      "línea ${i + 1}: se esperaba archivo<TAB>nombre<TAB>aaaa-mm-dd"
    }
    val date = cols.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }?.let {
      try {
        LocalDate.parse(it)
      } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("línea ${i + 1}: fecha no válida «$it»")
      }
    }
    LabelExpectation(cols[0].trim(), cols[1].trim(), date)
  }

/** Iguales sin mayúsculas, tildes ni espacios. */
fun sameName(expected: String, actual: String?): Boolean {
  if (actual == null) return false
  fun key(s: String) = fold(s).filter { it.isLetterOrDigit() }
  return key(expected).isNotEmpty() && key(expected) == key(actual)
}

/** Mide [parsed] (por archivo) contra [expected]. Un archivo sin lectura cuenta como vacío. */
fun score(expected: List<LabelExpectation>, parsed: Map<String, LabelFields>): LabelScore {
  val rows = expected.map { e ->
    val p = parsed[e.file] ?: LabelFields()
    LabelScoreRow(e.file, e.name, p.name, sameName(e.name, p.name), p.roastDate, p.roaster)
  }
  val invented = expected.filter { it.roastDate == null }
    .mapNotNull { e -> parsed[e.file]?.roastDate?.let { "${e.file}: $it" } }
  val handwritten = expected.filter { it.roastDate != null }
    .map { HandwrittenDate(it.file, it.roastDate!!, parsed[it.file]?.roastDate) }
  return LabelScore(
    rows = rows,
    nameHits = rows.count { it.nameHit },
    nameTotal = rows.size,
    noDateTotal = expected.count { it.roastDate == null },
    inventedDates = invented,
    handwritten = handwritten,
  )
}
