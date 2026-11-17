package dev.josueamaya.pourline.grinder

/**
 * Los textos que necesita el formato: los nombres de las unidades con su plural por idioma. Es una interfaz para que
 * el dominio no dependa de Android (`ResourcesGrindTexts` la implementa con `plurals`).
 */
interface GrindTexts {
  /** La cantidad con su unidad y el plural del idioma: "1 clic", "30 clics", "15,5 clics". */
  fun quantity(unit: GrindUnit, value: Double): String

  /** El nombre de la unidad en plural: "Clics". */
  fun unitName(unit: GrindUnit): String

  /** Un número con [decimals] decimales y el separador del idioma. */
  fun number(value: Double, decimals: Int): String

  /** El modelo de una copia para personalizar: "Chestnut S3 (copia)". */
  fun copyName(model: String): String
}
