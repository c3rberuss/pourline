package dev.josueamaya.pourline.coffee

import java.text.Normalizer

private val Diacritics = Regex("\\p{Mn}+")

/** Minúsculas y sin marcas diacríticas: "Etiopía" y "ETIOPIA" pliegan igual. */
fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Diacritics, "").lowercase()

object CoffeeSearch {
  /** Busca en nombre, tostador, país y región, sin distinguir mayúsculas ni tildes. Vacío coincide con todo. */
  fun matches(item: CoffeeListItem, query: String): Boolean {
    val needle = fold(query.trim())
    if (needle.isEmpty()) return true
    return listOf(item.name, item.roaster, item.originCountry, item.originRegion)
      .any { it != null && fold(it).contains(needle) }
  }
}
