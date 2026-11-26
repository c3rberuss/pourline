package dev.josueamaya.pourline.recipe.catalog

import android.content.Context
import dev.josueamaya.pourline.R

/** El idioma de los textos del catálogo en este momento (SPEC-012 §3.2). */
fun interface CatalogLanguageSource {
  fun current(): CatalogLanguage
}

/**
 * Lee el recurso `catalog_language` (`es` en `values`, `en` en `values-en`) en cada llamada: si el usuario cambia el
 * idioma con la app abierta, la lista sigue el idioma nuevo. Un sistema en francés ve el español, como el resto de la app.
 */
class ResourcesCatalogLanguage(context: Context) : CatalogLanguageSource {
  private val app = context.applicationContext

  override fun current(): CatalogLanguage = CatalogLanguage.of(app.resources.getString(R.string.catalog_language))
}
