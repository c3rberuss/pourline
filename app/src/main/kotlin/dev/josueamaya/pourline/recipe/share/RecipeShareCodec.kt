package dev.josueamaya.pourline.recipe.share

/** El formato del QR de una receta (SPEC-009). El códec completo llega en F1. */
object RecipeShareCodec {
  const val VERSION = 1
  const val SCHEME = "pourline"
  const val HOST = "recipe"
  const val URI_PREFIX = "pourline://recipe"

  /**
   * Longitud máxima del URI del QR (D8). Medida en F0 con `QrLegibilityTest`, el lector de Play Services y el QR de
   * 432 px: es la capacidad en modo byte con corrección M de la versión 13. La regla del plan daría 362 (versión 14),
   * pero con 362 la lectura tras reducir al 50 % y JPEG 70 falla en 1 de 20 muestras; con 331 acierta 20 de 20.
   */
  const val MAX_URI_LENGTH = 331
}
