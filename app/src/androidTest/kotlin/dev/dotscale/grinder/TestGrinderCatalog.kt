package dev.dotscale.grinder

/**
 * Un catálogo de prueba con marcas ficticias (D36): sus rangos y anclas están inventados a propósito para que nadie
 * confunda un dato de prueba con uno de la fuente.
 */
object TestGrinderCatalog {
  const val VERSION = 1

  const val KEY_UNO = "acme-uno"
  const val KEY_DOS = "acme-dos"
  const val KEY_TRES = "acme-tres"
  const val KEY_INVERSO = "acme-inverso"

  /** Un archivo con la [version] y las [entries] dadas (cada una, un objeto JSON de molino). */
  fun catalog(version: Int, entries: List<String>): String = """{"version": $version, "grinders": [${entries.joinToString(",")}]}"""

  /** Clics 0–40. [key], [model] y [max] se pueden cambiar para probar actualizaciones. */
  fun uno(key: String = KEY_UNO, model: String = "Uno", max: Double = 40.0): String = entry(
    key, "Acme", model, units = """[{"unit":"CLICKS"}]""", min = 0.0, max = max, step = 1.0,
    ranges = """{"V60":{"min":16,"max":24},"AEROPRESS":{"min":8,"max":20}}""",
    anchors = """{"FINE":8,"MEDIUM":20,"COARSE":32}""",
  )

  /** Clics 0–60 con medio paso. */
  fun dos(): String = entry(
    KEY_DOS, "Acme", "Dos", units = """[{"unit":"CLICKS"}]""", min = 0.0, max = 60.0, step = 0.5,
    ranges = """{"V60":{"min":20,"max":36}}""",
    anchors = """{"FINE":10,"MEDIUM":30,"COARSE":50}""",
  )

  /** Rotaciones, números (4 por rotación) y clics (10 por número), 0–200. */
  fun tres(): String = entry(
    KEY_TRES, "Acme", "Tres",
    units = """[{"unit":"ROTATIONS"},{"unit":"NUMBERS","perParent":4},{"unit":"CLICKS","perParent":10}]""",
    min = 0.0, max = 200.0, step = 1.0,
    ranges = """{"V60":{"min":60,"max":100}}""",
    anchors = """{"FINE":40,"MEDIUM":100,"COARSE":160}""",
  )

  /** Subir el ajuste afina. */
  fun inverso(): String = entry(
    KEY_INVERSO, "Acme", "Inverso", units = """[{"unit":"CLICKS"}]""", min = 0.0, max = 50.0, step = 1.0,
    direction = "HIGHER_IS_FINER",
    ranges = """{"V60":{"min":20,"max":32}}""",
    anchors = """{"FINE":40,"MEDIUM":25,"COARSE":10}""",
  )

  fun entry(
    key: String,
    brand: String,
    model: String,
    units: String,
    min: Double,
    max: Double,
    step: Double,
    direction: String = "HIGHER_IS_COARSER",
    ranges: String = "{}",
    anchors: String = "{}",
  ): String = """{"key":"$key","brand":"$brand","model":"$model","units":$units,"scale":{"min":$min,"max":$max},""" +
    """"step":$step,"direction":"$direction","ranges":$ranges,"anchors":$anchors,"sourceUrl":"https://example.test/$key"}"""

  val JSON: String = catalog(VERSION, listOf(uno(), dos(), tres(), inverso()))
}
