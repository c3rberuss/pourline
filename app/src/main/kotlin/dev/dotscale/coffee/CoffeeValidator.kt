package dev.dotscale.coffee

import java.time.LocalDate

enum class CoffeeError {
  NAME_REQUIRED,
  ALTITUDE_INVALID,
  ALTITUDE_RANGE,
  BAG_WEIGHT_INVALID,
  PRICE_INVALID,
  ROAST_DATE_FUTURE,
  OPENED_DATE_FUTURE,
}

object CoffeeValidator {
  /** Errores del borrador tal como está (sin normalizar). Vacío = se puede guardar. */
  fun validate(draft: CoffeeDraft, today: LocalDate): Set<CoffeeError> = buildSet {
    if (draft.name.isBlank()) add(CoffeeError.NAME_REQUIRED)
    val min = draft.altitudeMinM
    val max = draft.altitudeMaxM
    if ((min != null && min <= 0) || (max != null && max <= 0)) {
      add(CoffeeError.ALTITUDE_INVALID)
    } else if (min != null && max != null && min > max) {
      add(CoffeeError.ALTITUDE_RANGE)
    }
    if (draft.bagWeightG != null && draft.bagWeightG <= 0) add(CoffeeError.BAG_WEIGHT_INVALID)
    if (draft.price != null && draft.price <= 0) add(CoffeeError.PRICE_INVALID)
    if (draft.roastDate != null && draft.roastDate.isAfter(today)) add(CoffeeError.ROAST_DATE_FUTURE)
    if (draft.openedDate != null && draft.openedDate.isAfter(today)) add(CoffeeError.OPENED_DATE_FUTURE)
  }
}
