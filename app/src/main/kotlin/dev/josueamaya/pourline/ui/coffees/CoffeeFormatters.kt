package dev.josueamaya.pourline.ui.coffees

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** "1800–2000 m" (con U+2013) o "1900 m"; `null` sin altitud. Con un solo valor, se trata como igual al otro. */
fun formatAltitude(min: Int?, max: Int?): String? {
  val low = min ?: max ?: return null
  val high = max ?: low
  return if (low == high) "$low m" else "$low–$high m"
}

/** Dólares con dos decimales: "$18.50" (D36). */
fun formatPrice(price: Double): String = String.format(Locale.US, "$%.2f", price)

/** Gramos siempre, sea cual sea la unidad de la báscula: "180 g" (D36); con decimales solo si los hay. */
fun formatGrams(grams: Double): String = "${formatDecimal(grams, 1)} g"

/** Fecha de tueste o de apertura con el formato del idioma actual: "12 oct 2026". */
fun formatCoffeeDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
  DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
