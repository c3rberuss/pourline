package dev.josueamaya.pourline.coffee

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Stock de la bolsa: `null` sin peso de bolsa. Puede ser negativo si se dosificó de más. */
fun stockOf(bagWeightG: Double?, dosedG: Double): Double? = bagWeightG?.let { it - dosedG }

/** Agotado: hay peso de bolsa y no queda nada. Sin peso no hay "agotado". */
fun isSoldOut(stockG: Double?): Boolean = stockG != null && stockG <= 0

/** Lo que se muestra del stock: nunca negativo. */
fun displayStock(stockG: Double): Double = maxOf(0.0, stockG)

/** Días entre el tueste y [on]; `null` sin fecha de tueste o con un tueste posterior. */
fun daysSinceRoast(roastDate: LocalDate?, on: LocalDate): Int? {
  if (roastDate == null) return null
  val days = ChronoUnit.DAYS.between(roastDate, on)
  return if (days < 0) null else days.toInt()
}

/** Días desde el tueste hasta la fecha local de inicio de una preparación. */
fun brewDaysSinceRoast(roastDate: LocalDate?, startedAtEpochMs: Long, zone: ZoneId): Int? =
  daysSinceRoast(roastDate, Instant.ofEpochMilli(startedAtEpochMs).atZone(zone).toLocalDate())

/** Borrador de una bolsa nueva del mismo café: copia todo salvo el id, las fechas y las fotos. */
fun newBagFrom(coffee: CoffeeDraft): CoffeeDraft =
  coffee.copy(id = null, roastDate = null, openedDate = null, photo1 = null, photo2 = null)
