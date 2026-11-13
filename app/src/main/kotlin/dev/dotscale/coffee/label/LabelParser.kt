package dev.dotscale.coffee.label

import dev.dotscale.coffee.CoffeeProcess
import dev.dotscale.coffee.RoastLevel
import dev.dotscale.coffee.fold
import dev.dotscale.coffee.label.LabelKeywords.Label
import java.time.LocalDate

/**
 * Deduce los datos de una etiqueta a partir de las líneas de OCR (JVM puro). Orden: (1) valores con rótulo de
 * campo; (2) palabras clave sueltas para lo que siga vacío, sin las líneas de notas ni las de varias opciones;
 * (3) proceso, nivel, altitud, peso y fechas; (4) nombre y tostador por altura de línea. Cuando algo es
 * ambiguo (varias opciones impresas, niveles combinados), el campo se deja vacío: nunca se adivina.
 */
object LabelParser {
  private const val MAX_PROCESS_LINE_WORDS = 4

  fun parse(lines: List<OcrLine>, today: LocalDate): LabelFields {
    val rows = toRows(lines)
    val scan = scan(rows)
    // Las notas de cata no cuentan para proceso, variedad ni nivel; sí pueden traer peso, altitud o país.
    val usable = rows.filter { it.index !in scan.notes }

    val (process, processOther) = processOf(usable, scan)
    val altitude = altitudeOf(rows, scan)
    val (name, roaster) = nameAndRoaster(rows, scan)
    val roastDate = LabelDates.pick(LabelDates.findAll(rows.map { it.text }, today), today)

    return LabelFields(
      name = name,
      roaster = roaster,
      roastDate = roastDate,
      process = process,
      processOther = processOther,
      varieties = varietiesOf(usable, scan),
      roastLevel = levelOf(usable, scan),
      altitudeMinM = altitude?.first,
      altitudeMaxM = altitude?.second,
      originCountry = countryOf(rows, scan),
      originRegion = scan.values[Label.REGION]?.let(::tidy),
      farm = scan.values[Label.FARM]?.let(::tidy),
      bagWeightG = weightOf(rows, scan),
    )
  }

  // ---------------------------------------------------------------- rótulos y notas

  private class Row(val index: Int, val text: String, val heightPx: Int) {
    val folded = fold(text)
  }

  private fun toRows(lines: List<OcrLine>): List<Row> =
    lines.mapIndexed { i, l -> Row(i, normalize(l.text.trim()), l.heightPx) }.filter { it.text.isNotEmpty() }

  /**
   * Arregla lo que el OCR confunde con frecuencia: la "İ" turca (y su punto combinado) y el cero dentro de una
   * palabra ("R0J0", "LAVAD0"). Un cero pegado a una letra y no a un dígito es una "o".
   */
  internal fun normalize(text: String): String {
    val clean = text.replace('\u0130', 'I').replace('\u0131', 'i').replace("\u0307", "")
    val out = StringBuilder(clean.length)
    clean.forEachIndexed { i, c ->
      val before = clean.getOrNull(i - 1)
      val after = clean.getOrNull(i + 1)
      val letterBefore = before?.isLetter() == true
      val letterAfter = after?.isLetter() == true
      val inWord = (letterBefore && after?.isDigit() != true) || (letterAfter && before?.isDigit() != true)
      if (c == '0' && inWord) {
        val neighbor = if (letterBefore) before else after!!
        out.append(if (neighbor.isLowerCase()) 'o' else 'O')
      } else {
        out.append(c)
      }
    }
    return out.toString()
  }

  private class Scan(
    val values: Map<Label, String>,
    /** Líneas que son un rótulo o el valor de uno: nunca son el nombre. */
    val labelLines: Set<Int>,
    /** Líneas de una sección de notas (no cuentan para proceso, variedad ni nivel). */
    val notes: Set<Int>,
    /** Rótulos que aparecen aunque su valor no se haya podido leer. */
    val present: Set<Label>,
    /** Líneas que son un rótulo de variedad o su valor: las demás pueden traer una variedad que sea el título. */
    val varietyLines: Set<Int>,
  )

  private class Hit(val label: Label, val value: String, val explicit: Boolean)

  private val labelRegexes: List<Pair<Regex, Label>> by lazy {
    LabelKeywords.labels.map { (key, label) -> Regex("^\\W*${Regex.escape(key)}\\b\\s*([:|\\-–—]?)\\s*(.*)$") to label }
  }
  private val notesRegex = Regex("^\\W*(?:${LabelKeywords.notesLabels.joinToString("|") { Regex.escape(it) }})\\b")
  private val spaceSeparatorLabels = setOf(Label.PROCESS, Label.VARIETY, Label.ALTITUDE, Label.WEIGHT, Label.ROAST)

  private fun hitOf(row: Row): Hit? {
    for ((regex, label) in labelRegexes) {
      val m = regex.find(row.folded) ?: continue
      val sep = m.groupValues[1]
      val rest = m.groups[2]!!.range.first
      val value = valueFrom(row, rest).trim()
      val explicit = sep.isNotEmpty()
      if (!explicit && value.isNotEmpty() && label !in spaceSeparatorLabels && label != Label.COUNTRY) return null
      return Hit(label, value, explicit)
    }
    return null
  }

  /** El texto original desde [start]: los índices solo valen si plegar no cambió el largo. */
  private fun looksLikeProse(text: String) = text.any { it == ',' || it == '.' || it == ';' } || wordCount(text) >= 3

  private fun valueFrom(row: Row, start: Int): String =
    if (row.folded.length == row.text.length) row.text.substring(start) else row.folded.substring(start)

  private fun scan(rows: List<Row>): Scan {
    val values = linkedMapOf<Label, String>()
    val labelLines = mutableSetOf<Int>()
    val notes = mutableSetOf<Int>()
    val present = mutableSetOf<Label>()
    val varietyLines = mutableSetOf<Int>()
    var inNotes = false
    rows.forEachIndexed { pos, row ->
      val hit = hitOf(row)
      // Una sección de notas sigue mientras las líneas parezcan prosa: el orden de lectura mezcla columnas, y una
      // línea corta sin puntuación ya no es una nota.
      if (notesRegex.containsMatchIn(row.folded)) {
        inNotes = true
      } else if (hit != null || !looksLikeProse(row.text)) {
        inNotes = false
      }
      if (inNotes) {
        notes += row.index
        return@forEachIndexed
      }
      if (hit == null) return@forEachIndexed
      labelLines += row.index
      present += hit.label
      if (hit.label == Label.VARIETY) varietyLines += row.index
      var value = hit.value
      if (value.isEmpty()) {
        val next = rows.getOrNull(pos + 1)
        if (next != null && hitOf(next) == null && !notesRegex.containsMatchIn(next.folded)) {
          value = next.text
          labelLines += next.index
          if (hit.label == Label.VARIETY) varietyLines += next.index
        }
      }
      if (value.isNotEmpty()) values.putIfAbsent(hit.label, value)
    }
    return Scan(values, labelLines, notes, present, varietyLines)
  }

  internal fun labeledValues(lines: List<OcrLine>): Map<LabelKeywords.Label, String> = scan(toRows(lines)).values

  // ---------------------------------------------------------------- proceso

  internal data class ProcessMatch(val process: CoffeeProcess, val text: String, val start: Int)

  private val phraseRegexes: List<Triple<Regex, CoffeeProcess, String>> by lazy {
    LabelKeywords.processPhrases.map { (phrase, process) ->
      Triple(Regex("(?<![a-z0-9])${Regex.escape(phrase)}(?![a-z0-9])"), process, phrase)
    }
  }

  /** Procesos de una línea, sin repetir texto: una frase larga consume el suyo ("anaeróbico lavado" no es lavado). */
  internal fun processesIn(line: String): List<ProcessMatch> = processScan(line).first

  private fun processScan(line: String): Pair<List<ProcessMatch>, String> {
    var folded = fold(line)
    val sameLength = folded.length == line.length
    val found = mutableListOf<ProcessMatch>()
    for ((regex, process, phrase) in phraseRegexes) {
      while (true) {
        val m = regex.find(folded) ?: break
        val text = when {
          process != CoffeeProcess.OTHER -> phrase
          sameLength -> line.substring(m.range)
          else -> LabelKeywords.otherDisplay[phrase] ?: phrase
        }
        found += ProcessMatch(process, text, m.range.first)
        folded = folded.replaceRange(m.range, " ".repeat(m.range.count()))
      }
    }
    return found.sortedBy { it.start } to folded
  }

  /** Una línea que es solo un proceso ("Red honey", "Natural process"): se une al nombre y no es candidata. */
  private fun isProcessOnly(line: String): Boolean {
    val (found, rest) = processScan(line)
    if (found.isEmpty()) return false
    val leftover = rest.replace(Regex("\\b(process|proceso)\\b"), " ").filter { it.isLetterOrDigit() }
    return leftover.isEmpty()
  }

  private fun processOf(usable: List<Row>, scan: Scan): Pair<CoffeeProcess?, String?> {
    val labeled = scan.values[Label.PROCESS]
    if (labeled != null) {
      val found = processesIn(labeled).distinctBy { it.process }
      return when {
        found.size == 1 -> found[0].process to otherText(found[0])
        found.size > 1 -> null to null
        else -> labeled.trim().takeIf { it.isNotEmpty() }?.let { CoffeeProcess.OTHER to it } ?: (null to null)
      }
    }
    if (Label.PROCESS in scan.present) return null to null
    val singles = usable.filter { it.index !in scan.labelLines && wordCount(it.text) <= MAX_PROCESS_LINE_WORDS }
      .mapNotNull { row -> processesIn(row.text).distinctBy { it.process }.singleOrNull() }
    val first = singles.firstOrNull() ?: return null to null
    return if (singles.all { it.process == first.process }) first.process to otherText(first) else null to null
  }

  private fun otherText(m: ProcessMatch) = if (m.process == CoffeeProcess.OTHER) m.text else null

  // ---------------------------------------------------------------- nivel de tueste

  /** Nivel de una línea (D38): solo con contexto de tueste o si la línea es solo esa palabra; combinados → `null`. */
  internal fun levelIn(line: String, labeled: Boolean = false): RoastLevel? {
    val tokens = Regex("[a-z]+").findAll(fold(line)).map { it.value }.toList()
    val levels = tokens.mapNotNull { LabelKeywords.levelWords[it] }.distinct()
    if (levels.size != 1) return null
    val context = labeled || tokens.size == 1 ||
      tokens.any { t -> LabelKeywords.roastContext.any { t.startsWith(it) } } && tokens.size <= 4
    return levels[0].takeIf { context }
  }

  private fun levelOf(usable: List<Row>, scan: Scan): RoastLevel? {
    scan.values[Label.ROAST]?.let { return levelIn(it, labeled = true) }
    if (Label.ROAST in scan.present) return null
    val levels = usable.mapNotNull { levelIn(it.text) }.distinct()
    return levels.singleOrNull()
  }

  // ---------------------------------------------------------------- altitud y peso

  private const val NUM = "\\d{1,2}[.,]\\d{3}|\\d{3,4}"
  private const val UNIT = "(?:m\\s*\\.?\\s*s\\s*\\.?\\s*n\\s*\\.?\\s*m\\.?|masl|mts?\\.?|metros|m)(?![a-z])"
  private val altRange = Regex("(?<![\\d.,])($NUM)\\s*(?:$UNIT)?\\s*(?:[-–—]|\\ba\\b|\\bto\\b)\\s*($NUM)\\s*(?:$UNIT)", RegexOption.IGNORE_CASE)
  private val altSingle = Regex("(?<![\\d.,])($NUM)\\s*$UNIT", RegexOption.IGNORE_CASE)
  private val altRangeBare = Regex("^\\W*($NUM)\\s*(?:[-–—]|\\ba\\b|\\bto\\b)\\s*($NUM)\\W*$", RegexOption.IGNORE_CASE)
  private val altSingleBare = Regex("^\\W*($NUM)\\W*$")

  /** Altitud de una línea (D39): con unidad, o sin ella solo si es el valor de un rótulo. Fuera de 100–5000 → `null`. */
  internal fun altitudeIn(line: String, labeled: Boolean): Pair<Int, Int>? {
    val text = line.trim()
    altRange.find(text)?.let { m -> range(m.groupValues[1], m.groupValues[2])?.let { return it } }
    altSingle.find(text)?.let { m -> single(m.groupValues[1])?.let { return it } }
    if (labeled) {
      altRangeBare.find(text)?.let { m -> range(m.groupValues[1], m.groupValues[2])?.let { return it } }
      altSingleBare.find(text)?.let { m -> single(m.groupValues[1])?.let { return it } }
    }
    return null
  }

  private fun altitudeNumber(text: String): Int? =
    text.replace(Regex("[.,](?=\\d{3}$)"), "").toIntOrNull()?.takeIf { it in 100..5000 }

  private fun single(a: String) = altitudeNumber(a)?.let { it to it }
  private fun range(a: String, b: String): Pair<Int, Int>? {
    val min = altitudeNumber(a) ?: return null
    val max = altitudeNumber(b) ?: return null
    return if (min <= max) min to max else null
  }

  private fun altitudeOf(usable: List<Row>, scan: Scan): Pair<Int, Int>? {
    scan.values[Label.ALTITUDE]?.let { v -> altitudeIn(v, labeled = true)?.let { return it } }
    return usable.firstNotNullOfOrNull { altitudeIn(it.text, labeled = false) }
  }

  private val weight = Regex("(?<![\\d.,])(\\d+(?:[.,]\\d+)?)\\s*(kg|grs?|gramos|g)(?![a-z])", RegexOption.IGNORE_CASE)

  /** Pesos en gramos de una línea (D39): `g`, `gr`, `grs`, `gramos` y `kg`, entre 50 y 5000. Onzas y libras se ignoran. */
  internal fun weightsIn(line: String): List<Double> =
    weight.findAll(line).mapNotNull { m ->
      val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
      val grams = if (m.groupValues[2].equals("kg", ignoreCase = true)) n * 1000 else n
      grams.takeIf { it in 50.0..5000.0 }
    }.distinct().toList()

  private fun weightOf(usable: List<Row>, scan: Scan): Double? {
    scan.values[Label.WEIGHT]?.let { v ->
      val found = weightsIn(v)
      if (found.size == 1) return found[0]
      if (found.size > 1) return null
    }
    val singles = usable.map { weightsIn(it.text) }.filter { it.size == 1 }.map { it[0] }.distinct()
    return singles.singleOrNull()
  }

  // ---------------------------------------------------------------- variedades y país

  private val varietySplit = Regex("\\s*(?:,|/|&|;|\\by\\b|\\band\\b)\\s*", RegexOption.IGNORE_CASE)
  private val varietyRegexes: List<Pair<Regex, String>> by lazy {
    LabelKeywords.varieties.map { (key, canon) -> Regex("(?<![a-z0-9])${Regex.escape(key)}(?![a-z0-9])") to canon }
  }

  private fun varietiesOf(usable: List<Row>, scan: Scan): List<String> {
    scan.values[Label.VARIETY]?.let { value ->
      return value.split(varietySplit).mapNotNull { part ->
        val clean = tidy(part)?.trim('.', ':', '|', '-')?.trim().orEmpty()
        if (clean.length < 3) return@mapNotNull null
        LabelKeywords.varieties.firstOrNull { it.first == fold(clean) }?.second ?: clean
      }.distinct()
    }
    if (Label.VARIETY in scan.present) return emptyList()
    val found = mutableListOf<Pair<Int, String>>()
    usable.forEachIndexed { pos, row ->
      var folded = row.folded
      for ((regex, canon) in varietyRegexes) {
        while (true) {
          val m = regex.find(folded) ?: break
          found += (pos * 1000 + m.range.first) to canon
          folded = folded.replaceRange(m.range, " ".repeat(m.range.count()))
        }
      }
    }
    return found.sortedBy { it.first }.map { it.second }.distinct()
  }

  private val countryRegexes: List<Pair<Regex, String>> by lazy {
    LabelKeywords.countries.map { (key, canon) -> Regex("(?<![a-z0-9])${Regex.escape(key)}(?![a-z0-9])") to canon }
  }

  private fun countriesIn(text: String): List<String> {
    var folded = fold(text)
    val found = mutableListOf<Pair<Int, String>>()
    for ((regex, canon) in countryRegexes) {
      while (true) {
        val m = regex.find(folded) ?: break
        found += m.range.first to canon
        folded = folded.replaceRange(m.range, " ".repeat(m.range.count()))
      }
    }
    return found.sortedBy { it.first }.map { it.second }.distinct()
  }

  private fun countryOf(usable: List<Row>, scan: Scan): String? {
    scan.values[Label.COUNTRY]?.let { v -> countriesIn(v).firstOrNull()?.let { return it } }
    return usable.flatMap { countriesIn(it.text) }.distinct().singleOrNull()
  }

  // ---------------------------------------------------------------- nombre y tostador

  private val dateLike = Regex("\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4}")
  private val weightUnit = Regex("\\d\\s*(?:g|gr|grs|kg|oz|lb|lbs|ml)\\b", RegexOption.IGNORE_CASE)

  private fun isNameCandidate(row: Row, scan: Scan): Boolean {
    if (row.index in scan.notes) return false
    // Una variedad sola en su línea que un rótulo ajeno ("Origin:") se tragó sigue pudiendo ser el título.
    val swallowedVariety = row.index in scan.labelLines && row.index !in scan.varietyLines && isVarietyOnly(row)
    if (row.index in scan.labelLines && !swallowedVariety) return false
    val text = row.text
    val letters = text.count { it.isLetter() }
    val visible = text.count { !it.isWhitespace() }
    if (letters < 3 || letters * 2 < visible) return false
    if (text.count { it.isDigit() } * 10 > visible * 3) return false
    if (LabelKeywords.nameExclusions.any { row.folded.contains(it) }) return false
    if (dateLike.containsMatchIn(text) || weightUnit.containsMatchIn(text) || weightsIn(text).isNotEmpty()) return false
    if (LabelDates.findAll(listOf(text), LocalDate.MAX).isNotEmpty()) return false
    if (altitudeIn(text, labeled = false) != null || levelIn(text) != null) return false
    return !isProcessOnly(text)
  }

  /** Una variedad del diccionario sola en su línea ("Geisha", "Icatú Amarillo"): casi siempre es el título. */
  private fun isVarietyOnly(row: Row): Boolean {
    var folded = row.folded
    var found = false
    for ((regex, _) in varietyRegexes) {
      while (true) {
        val m = regex.find(folded) ?: break
        found = true
        folded = folded.replaceRange(m.range, " ".repeat(m.range.count()))
      }
    }
    return found && folded.none { it.isLetterOrDigit() }
  }

  /** Palabra de marca, tolerando que el OCR añada letras al final ("espressoo"). */
  private fun isBrandLike(row: Row) = Regex("[a-z]+").findAll(row.folded).any { token ->
    LabelKeywords.brandWords.any { token.value == it || (it.length >= MIN_PREFIX_BRAND && token.value.startsWith(it)) }
  }

  private const val MIN_PREFIX_BRAND = 6

  /**
   * Puntuación de una línea como nombre: su altura, x4 si es una variedad sola en su línea y x0,25 si parece una
   * marca ("Specialty Coffee"). El valor de un rótulo ("Variedad: ...") nunca llega aquí: no es candidato.
   */
  private fun nameScore(row: Row): Double {
    var score = row.heightPx.toDouble()
    if (isVarietyOnly(row)) score *= VARIETY_BOOST
    if (isBrandLike(row)) score *= BRAND_PENALTY
    return score
  }

  private const val VARIETY_BOOST = 4.0
  private const val BRAND_PENALTY = 0.25

  /** Una línea contigua de casi la misma altura es la otra mitad de un título de dos líneas. */
  private const val SAME_TITLE_HEIGHT = 0.85

  /** Cuántas líneas después del título se busca el proceso suelto ("Pacamara" ... "Red honey"). */
  private const val PROCESS_WINDOW = 3

  private fun nameAndRoaster(rows: List<Row>, scan: Scan): Pair<String?, String?> {
    val candidates = rows.filter { isNameCandidate(it, scan) }
    val order = compareBy<Row> { nameScore(it) }.thenByDescending { it.index }
    val best = candidates.maxWithOrNull(order) ?: return null to null
    val position = rows.indexOf(best)

    fun similar(neighbor: Row?) =
      neighbor != null && neighbor in candidates && !isBrandLike(neighbor) &&
        neighbor.heightPx >= best.heightPx * SAME_TITLE_HEIGHT
    val before = rows.getOrNull(position - 1)?.takeIf(::similar)
    val after = rows.getOrNull(position + 1)?.takeIf(::similar)
    val titleRows = listOfNotNull(before, best, after)
    val title = titleRows.joinToString(" ") { it.text }
    val lastTitle = rows.indexOf(titleRows.last())

    val process = rows.subList(lastTitle + 1, minOf(rows.size, lastTitle + 1 + PROCESS_WINDOW)).firstOrNull {
      it.index !in scan.labelLines && it.index !in scan.notes && isProcessOnly(it.text)
    }
    val name = if (process != null) joinWithProcess(title, process.text) else tidy(title)
    val roasterRow = candidates.filter { it !in titleRows }.maxWithOrNull(order)
    return name to roasterRow?.let { tidy(it.text) }
  }

  private fun joinWithProcess(name: String, process: String): String {
    val base = if (name.any { it.isLowerCase() }) name else titleCase(name)
    return "${tidy(base).orEmpty()} ${titleCase(process)}".trim()
  }

  private fun titleCase(text: String): String =
    text.trim().split(Regex("\\s+")).joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }

  private fun wordCount(text: String) = text.trim().split(Regex("\\s+")).count { it.any { c -> c.isLetterOrDigit() } }

  /** Recorta, une espacios y quita adornos; un texto todo en mayúsculas pasa a "Título". */
  private fun tidy(text: String): String? {
    val clean = text.replace(Regex("\\s+"), " ").trim(' ', '.', ':', '|', '-', '–', '—', ',', ';')
    if (clean.isEmpty()) return null
    val letters = clean.filter { it.isLetter() }
    return if (letters.length > 3 && letters.none { it.isLowerCase() }) titleCase(clean) else clean
  }
}
