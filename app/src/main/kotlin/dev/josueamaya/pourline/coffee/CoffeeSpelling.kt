package dev.josueamaya.pourline.coffee

import dev.josueamaya.pourline.history.canonicalCoffee

/**
 * Unifica la grafía de tostador, país, región y variedades con la de los valores ya registrados
 * (misma regla que `canonicalCoffee`), para no crear duplicados como "etiopía" frente a "Etiopía".
 */
fun canonicalize(draft: CoffeeDraft, existing: CoffeeSuggestions): CoffeeDraft {
  val varieties = draft.varieties.mapNotNull { canonicalCoffee(it, existing.varieties) }
    .distinctBy { it.lowercase() }
  return draft.copy(
    roaster = canonicalCoffee(draft.roaster, existing.roasters),
    originCountry = canonicalCoffee(draft.originCountry, existing.countries),
    originRegion = canonicalCoffee(draft.originRegion, existing.regions),
    varieties = varieties,
  )
}
