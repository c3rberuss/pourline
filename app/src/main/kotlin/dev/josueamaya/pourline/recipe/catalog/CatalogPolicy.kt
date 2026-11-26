package dev.josueamaya.pourline.recipe.catalog

/** Cuándo se descarga el catálogo y qué copia se usa (SPEC-012 §3.3). Funciones puras. */
object CatalogPolicy {
  /** Una descarga correcta de hace más de esto ya no es reciente. */
  const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

  /**
   * Sin descarga previa, con 24 h o más desde la última, o con un reloj que fue hacia atrás (una descarga en el futuro no
   * puede bloquear las siguientes).
   */
  fun isStale(lastDownloadMs: Long?, nowMs: Long): Boolean =
    lastDownloadMs == null || nowMs - lastDownloadMs >= MAX_AGE_MS || nowMs < lastDownloadMs

  /** La copia de `revision` mayor; con empate, la de la caché. `null` si no hay ninguna. */
  fun choose(cached: ParsedCatalog?, bundled: ParsedCatalog?): ParsedCatalog? = when {
    cached == null -> bundled
    bundled == null -> cached
    bundled.revision > cached.revision -> bundled
    else -> cached
  }
}
