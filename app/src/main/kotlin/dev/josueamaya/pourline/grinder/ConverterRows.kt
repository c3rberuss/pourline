package dev.josueamaya.pourline.grinder

/**
 * Una fila del convertidor: el ajuste equivalente en [grinder] y, si no hay equivalencia, qué se ofrece ([fix]).
 * [correctable] es `true` si el molino de origen es mío: solo entonces se puede guardar un par (§3.3.1).
 */
data class ConverterRow(val grinder: Grinder, val conversion: Conversion, val fix: FixTarget?, val correctable: Boolean)

/**
 * Una fila por cada molino de [owned] distinto de [source], en el orden de [owned] (D26). [value] está en unidades
 * base de [source]; [pairs] son los pares de calibración de [source] (cualquiera de sus pares; cada fila usa los que la
 * unen con su molino).
 */
fun converterRows(source: Grinder, value: Double, owned: List<Grinder>, pairs: List<GrindCalibration>): List<ConverterRow> =
  owned.filter { it.id != source.id }.map { target ->
    val points = pairs.pointsBetween(source.id, target.id)
    val conversion = GrindConverter.convert(value, source, target, points)
    val fix = if (conversion == Conversion.NoEquivalent) fixFor(source, target, points.size) else null
    ConverterRow(target, conversion, fix, correctable = source.owned)
  }
