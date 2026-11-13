package dev.dotscale.ui.coffees

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
