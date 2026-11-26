package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.grinder.TestGrinderCatalog
import java.util.concurrent.atomic.AtomicInteger

/**
 * Un catálogo de prueba de cuatro recetas (SPEC-012, D48): los tests de interfaz no dependen de la red ni del
 * contenido real de `catalog/recipes.json`.
 */
object TestRecipeCatalog {
  const val KEY_V60 = "nemo_v60"
  const val KEY_AEROPRESS = "test_aeropress"
  const val KEY_CLEVER = "test_clever"
  const val KEY_GRINDER = "test_grinder"

  /** Los textos en español y en inglés de las recetas, para los tests que buscan por nombre en cualquier idioma. */
  const val NAME_V60_ES = "V60 de Némo"
  const val NAME_V60_EN = "Némo's V60"

  fun json(revision: Int = 1, schemaVersion: Int = 1): String = """
    {
      "schemaVersion": $schemaVersion,
      "revision": $revision,
      "recipes": [
        {
          "key": "$KEY_V60",
          "name": {"es": "$NAME_V60_ES", "en": "$NAME_V60_EN"},
          "author": "Némo Pop",
          "context": {"es": "Receta de prueba", "en": "Test recipe"},
          "method": "V60",
          "doseG": 15.0,
          "waterTempC": 93.0,
          "grind": {"text": {"es": "Media-fina", "en": "Medium-fine"}},
          "notes": {"es": "Notas de la V60", "en": "V60 notes"},
          "sourceUrl": "https://example.com/nemo",
          "roasts": ["MEDIUM", "DARK"],
          "stages": [
            {"type": "POUR", "startS": 0, "durationS": 10, "waterG": 60.0,
             "name": {"es": "Preinfusión", "en": "Bloom"}, "instruction": {"es": "Vierte en espiral", "en": "Pour in a spiral"}},
            {"type": "WAIT", "startS": 10, "durationS": 35, "instruction": {"es": "Espera", "en": "Wait"}},
            {"type": "POUR", "startS": 45, "durationS": 20, "waterG": 150.0}
          ]
        },
        {
          "key": "$KEY_AEROPRESS",
          "name": {"es": "AeroPress de prueba", "en": "Test AeroPress"},
          "author": "Tester Uno",
          "context": {"es": "Campeonato de prueba", "en": "Test championship"},
          "method": "AEROPRESS",
          "doseG": 18.0,
          "waterTempC": 85.0,
          "sourceUrl": "https://example.com/aeropress",
          "roasts": ["LIGHT"],
          "stages": [
            {"type": "POUR", "startS": 0, "durationS": 10, "waterG": 100.0},
            {"type": "ACTION", "startS": 10, "durationS": 5, "instruction": {"es": "Remueve tres veces", "en": "Stir three times"}},
            {"type": "WAIT", "startS": 15, "durationS": 60},
            {"type": "ACTION", "startS": 75, "durationS": 20, "instruction": {"es": "Añade 60 g de agua a 96 °C", "en": "Add 60 g of water at 96 °C"}}
          ]
        },
        {
          "key": "$KEY_CLEVER",
          "name": {"es": "Clever de prueba", "en": "Test Clever"},
          "method": "OTHER",
          "methodOther": {"es": "Clever", "en": "Clever"},
          "doseG": 18.0,
          "stages": [
            {"type": "POUR", "startS": 0, "durationS": 15, "waterG": 300.0},
            {"type": "WAIT", "startS": 15, "durationS": 120}
          ]
        },
        {
          "key": "$KEY_GRINDER",
          "name": {"es": "Con molino de prueba", "en": "With a test grinder"},
          "method": "V60",
          "doseG": 15.0,
          "roasts": ["MEDIUM"],
          "grind": {
            "text": {"es": "Media", "en": "Medium"},
            "grinder": {"catalogKey": "${TestGrinderCatalog.KEY_UNO}", "value": 20}
          },
          "stages": [
            {"type": "POUR", "startS": 0, "durationS": 10, "waterG": 45.0},
            {"type": "WAIT", "startS": 10, "durationS": 30},
            {"type": "POUR", "startS": 40, "durationS": 20, "waterG": 250.0}
          ]
        }
      ]
    }
  """.trimIndent()
}

/** Una descarga guionizada: cada llamada toma el siguiente resultado de [script] y, agotado, devuelve [FetchResult.Failed]. */
class FakeCatalogFetcher(script: List<FetchResult> = emptyList()) : CatalogFetcher {
  private val queue = java.util.concurrent.ConcurrentLinkedQueue(script)
  private val count = AtomicInteger()

  @Volatile var lastEtag: String? = null
    private set

  /** Cuántas veces se pidió el archivo. */
  val calls: Int get() = count.get()

  fun enqueue(result: FetchResult) {
    queue.add(result)
  }

  override suspend fun fetch(etag: String?): FetchResult {
    count.incrementAndGet()
    lastEtag = etag
    return queue.poll() ?: FetchResult.Failed
  }
}
