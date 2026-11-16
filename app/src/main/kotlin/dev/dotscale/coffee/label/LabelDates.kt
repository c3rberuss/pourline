package dev.dotscale.coffee.label

import dev.dotscale.coffee.fold
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Fechas de tueste de una etiqueta (reglas del §3.3 del spec y D41). Nunca inventa una: los formatos sin año o
 * sin separadores solo valen junto a un rótulo de fecha, y las líneas de años sueltos, premios y fundaciones
 * se descartan enteras.
 */
object LabelDates {
  private val months = mapOf(
    "ene" to 1, "enero" to 1, "jan" to 1, "january" to 1, "feb" to 2, "febrero" to 2, "february" to 2,
    "mar" to 3, "marzo" to 3, "march" to 3, "abr" to 4, "abril" to 4, "apr" to 4, "april" to 4,
    "may" to 5, "mayo" to 5, "jun" to 6, "junio" to 6, "june" to 6, "jul" to 7, "julio" to 7, "july" to 7,
    "ago" to 8, "agosto" to 8, "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "set" to 9,
    "septiembre" to 9, "setiembre" to 9, "september" to 9, "oct" to 10, "octubre" to 10, "october" to 10,
    "nov" to 11, "noviembre" to 11, "november" to 11, "dic" to 12, "diciembre" to 12, "dec" to 12, "december" to 12,
  )
  private const val MONTH = "([a-z]{3,10})"
  private val dayMonthYear = Regex("(?<![\\d])(\\d{1,2})(?:st|nd|rd|th)?[\\s.\\-/]*(?:de\\s+)?$MONTH\\.?[\\s,.\\-/]*(?:de\\s+)?(\\d{4}|\\d{2})(?!\\d)")
  private val monthDayYear = Regex("(?<![a-z])$MONTH\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})(?!\\d)")
  private val numeric = Regex("(?<![\\d./-])(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4}|\\d{2})(?![\\d])")
  private val iso = Regex("(?<![\\d])(\\d{4})-(\\d{1,2})-(\\d{1,2})(?![\\d])")
  private val dayMonth = Regex("(?<![\\d.,/-])(\\d{1,2})\\.(\\d{1,2})(?![\\d.,/-])")
  private val compact = Regex("(?<![\\d])(\\d{2})(\\d{2})(\\d{2})(?![\\d])")
  private val ordinal = Regex("\\d\\s*[°º]")

  /** Todas las fechas plausibles de las [lines]. */
  fun findAll(lines: List<String>, today: LocalDate): List<LocalDate> {
    val found = mutableListOf<LocalDate>()
    lines.forEachIndexed { index, raw ->
      val line = fold(raw)
      if (isExcluded(line)) return@forEachIndexed
      found += withYear(line)
      if (hasDateLabel(line) || (index > 0 && isLabelOnly(fold(lines[index - 1])))) {
        found += withoutYear(line, today)
      }
    }
    return found
  }

  /** La más reciente que no está en el futuro; `null` si no hay (solo futuras o ninguna). */
  fun pick(dates: List<LocalDate>, today: LocalDate): LocalDate? = dates.filter { !it.isAfter(today) }.maxOrNull()

  private fun isExcluded(line: String): Boolean =
    LabelKeywords.dateExclusions.any { line.contains(it) } || ordinal.containsMatchIn(line)

  private fun hasDateLabel(line: String) = LabelKeywords.dateLabels.any { line.contains(it) }

  /** Una línea de solo rótulo ("Fecha de tueste"): la fecha va en la línea siguiente. */
  private fun isLabelOnly(line: String) =
    hasDateLabel(line) && line.none { it.isDigit() } && line.trim().trimEnd(':', '|', '-').split(Regex("\\s+")).size <= 4

  private fun withYear(line: String): List<LocalDate> = buildList {
    iso.findAll(line).forEach { m ->
      date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let(::add)
    }
    numeric.findAll(line).forEach { m ->
      val a = m.groupValues[1].toInt()
      val b = m.groupValues[2].toInt()
      val year = year(m.groupValues[3])
      (date(year, b, a) ?: date(year, a, b))?.let(::add) // dd/mm primero; mm/dd de reserva
    }
    dayMonthYear.findAll(line).forEach { m ->
      val month = months[m.groupValues[2]] ?: return@forEach
      date(year(m.groupValues[3]), month, m.groupValues[1].toInt())?.let(::add)
    }
    monthDayYear.findAll(line).forEach { m ->
      val month = months[m.groupValues[1]] ?: return@forEach
      date(year(m.groupValues[3]), month, m.groupValues[2].toInt())?.let(::add)
    }
  }

  private fun withoutYear(line: String, today: LocalDate): List<LocalDate> = buildList {
    dayMonth.findAll(line).forEach { m ->
      val day = m.groupValues[1].toInt()
      val month = m.groupValues[2].toInt()
      val current = date(today.year, month, day) ?: return@forEach
      add(if (current.isAfter(today)) current.minusYears(1) else current)
    }
    compact.findAll(line).forEach { m ->
      date(2000 + m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let(::add)
    }
  }

  private fun year(text: String) = if (text.length == 2) 2000 + text.toInt() else text.toInt()

  private fun date(year: Int, month: Int, day: Int): LocalDate? = try {
    if (year < 2000) null else LocalDate.of(year, month, day)
  } catch (_: DateTimeException) {
    null
  }
}
