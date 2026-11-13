package dev.dotscale.coffee.label

import dev.dotscale.coffee.CoffeeProcess
import dev.dotscale.coffee.RoastLevel

/**
 * Diccionarios es/en de la etiqueta. Todas las claves están plegadas con `fold` (sin mayúsculas ni tildes) y
 * las frases largas van antes que las cortas: el parser consume el texto de cada coincidencia.
 */
internal object LabelKeywords {
  /** Frase plegada → proceso. "semilavado" y "anaeróbico lavado" contienen "lavado": van antes. */
  val processPhrases: List<Pair<String, CoffeeProcess>> = listOf(
    "anaerobico lavado" to CoffeeProcess.ANAEROBIC,
    "anaerobic washed" to CoffeeProcess.ANAEROBIC,
    "anaerobic natural" to CoffeeProcess.ANAEROBIC,
    "anaerobico natural" to CoffeeProcess.ANAEROBIC,
    "anaerobico" to CoffeeProcess.ANAEROBIC,
    "anaerobic" to CoffeeProcess.ANAEROBIC,
    "semi lavado" to CoffeeProcess.WET_HULLED,
    "semi-lavado" to CoffeeProcess.WET_HULLED,
    "semilavado" to CoffeeProcess.WET_HULLED,
    "wet hulled" to CoffeeProcess.WET_HULLED,
    "yellow honey" to CoffeeProcess.HONEY,
    "red honey" to CoffeeProcess.HONEY,
    "black honey" to CoffeeProcess.HONEY,
    "white honey" to CoffeeProcess.HONEY,
    "honey" to CoffeeProcess.HONEY,
    "natural" to CoffeeProcess.NATURAL,
    "fully washed" to CoffeeProcess.WASHED,
    "lavado" to CoffeeProcess.WASHED,
    "washed" to CoffeeProcess.WASHED,
    "fermentacion lactica" to CoffeeProcess.OTHER,
    "lactic fermentation" to CoffeeProcess.OTHER,
  )

  /** Texto en español con que se muestra el proceso `OTHER` cuando no se puede recuperar el de la etiqueta. */
  val otherDisplay = mapOf("fermentacion lactica" to "Fermentación láctica", "lactic fermentation" to "Lactic fermentation")

  /** Variedad plegada → nombre canónico. Las frases largas primero ("bourbon rojo" antes que "bourbon"). */
  val varieties: List<Pair<String, String>> = listOf(
    "bourbon rojo" to "Bourbon Rojo", "red bourbon" to "Red Bourbon",
    "bourbon rosado" to "Bourbon Rosado", "pink bourbon" to "Pink Bourbon",
    "bourbon amarillo" to "Bourbon Amarillo", "yellow bourbon" to "Yellow Bourbon",
    "bourbon naranja" to "Bourbon Naranja", "orange bourbon" to "Orange Bourbon",
    "bourbon" to "Bourbon",
    "villa sarchi" to "Villa Sarchi", "mundo novo" to "Mundo Novo", "wush wush" to "Wush Wush",
    "ruiru 11" to "Ruiru 11", "ruiru" to "Ruiru 11",
    "geisha" to "Geisha", "gesha" to "Gesha", "typica" to "Typica", "tipica" to "Typica",
    "caturra" to "Caturra", "catuai" to "Catuaí", "pacamara" to "Pacamara", "pacas" to "Pacas",
    "sl28" to "SL28", "sl-28" to "SL28", "sl34" to "SL34", "sl-34" to "SL34",
    "icatu amarillo" to "Icatú Amarillo", "icatu rojo" to "Icatú Rojo", "icatu" to "Icatú",
    "heirloom" to "Heirloom", "74110" to "74110", "74112" to "74112",
    "castillo" to "Castillo", "maragogipe" to "Maragogipe", "pache" to "Pache",
    "sidra" to "Sidra", "java" to "Java", "batian" to "Batian", "laurina" to "Laurina",
    "catimor" to "Catimor", "parainema" to "Parainema", "marsellesa" to "Marsellesa",
  )

  /** Países: nombre plegado (es o en) → nombre canónico en español. "Colombia" es país, nunca variedad. */
  val countries: List<Pair<String, String>> = listOf(
    "republica dominicana" to "República Dominicana", "dominican republic" to "República Dominicana",
    "papua nueva guinea" to "Papúa Nueva Guinea", "papua new guinea" to "Papúa Nueva Guinea",
    "costa rica" to "Costa Rica", "el salvador" to "El Salvador",
    "colombia" to "Colombia", "etiopia" to "Etiopía", "ethiopia" to "Etiopía",
    "kenia" to "Kenia", "kenya" to "Kenia", "brasil" to "Brasil", "brazil" to "Brasil",
    "guatemala" to "Guatemala", "honduras" to "Honduras", "nicaragua" to "Nicaragua",
    "panama" to "Panamá", "mexico" to "México", "peru" to "Perú", "bolivia" to "Bolivia",
    "ecuador" to "Ecuador", "ruanda" to "Ruanda", "rwanda" to "Ruanda", "burundi" to "Burundi",
    "tanzania" to "Tanzania", "uganda" to "Uganda", "yemen" to "Yemen", "indonesia" to "Indonesia",
    "india" to "India", "vietnam" to "Vietnam", "jamaica" to "Jamaica", "haiti" to "Haití",
    "tailandia" to "Tailandia", "thailand" to "Tailandia", "china" to "China",
  )

  /** Palabras de nivel de tueste (plegadas). */
  val levelWords = mapOf(
    "claro" to RoastLevel.LIGHT, "light" to RoastLevel.LIGHT,
    "medio" to RoastLevel.MEDIUM, "medium" to RoastLevel.MEDIUM,
    "oscuro" to RoastLevel.DARK, "dark" to RoastLevel.DARK,
  )

  /** Una línea con una de estas palabras (como prefijo de palabra) habla del tueste. */
  val roastContext = listOf("tueste", "tostado", "tostad", "roast", "nivel")

  /** Rótulos de campo. */
  enum class Label { VARIETY, PROCESS, ALTITUDE, COUNTRY, REGION, FARM, ROAST, WEIGHT }

  /** Rótulo plegado → campo, con las frases largas primero. */
  val labels: List<Pair<String, Label>> = listOf(
    "contenido neto" to Label.WEIGHT, "net weight" to Label.WEIGHT, "net wt" to Label.WEIGHT, "peso" to Label.WEIGHT,
    "variedad" to Label.VARIETY, "varietal" to Label.VARIETY, "variety" to Label.VARIETY,
    "proceso" to Label.PROCESS, "process" to Label.PROCESS,
    "altitud" to Label.ALTITUDE, "altitude" to Label.ALTITUDE, "altura" to Label.ALTITUDE, "elevation" to Label.ALTITUDE,
    "origen" to Label.COUNTRY, "origin" to Label.COUNTRY,
    "region" to Label.REGION,
    "finca" to Label.FARM, "farm" to Label.FARM, "productor" to Label.FARM, "producer" to Label.FARM,
    "tueste" to Label.ROAST, "roast" to Label.ROAST,
  )

  /** Rótulos que abren una sección de notas de cata: nada de ella cuenta para proceso, variedad ni nivel. */
  val notesLabels = listOf("notas de cata", "tasting notes", "notas", "notes", "perfil", "profile")

  /** Rótulos de fecha de tueste (D41). */
  val dateLabels = listOf("fecha", "tueste", "tostado", "roasted", "roast date")

  /** Una línea con alguno de estos textos no aporta fecha de tueste (D41). */
  val dateExclusions = listOf("est.", "est ", "since", "desde", "premio", "award", "cup of excellence")

  /**
   * Palabras de marca o genéricas ("Specialty Coffee", "Tostadores"): una línea con alguna es casi siempre un logo o
   * un lema, no el nombre del café. Penalizan la línea al elegir el nombre; no la descartan.
   */
  val brandWords = setOf(
    "coffee", "coffees", "cafe", "specialty", "speciality", "specially", "espresso", "roaster", "roasters",
    "roasting", "tostador", "tostadores", "shop", "especialidad", "edition", "edicion",
  )

  /** Una línea con alguna de estas palabras no es el nombre del café. */
  val nameExclusions = listOf(
    "tostado", "roasted", "roast date", "peso", "net w", "net weight", "lote", "batch", "proceso", "variedad",
    "altitud", "notas", "notes", "est.", "molido", "grano", "ground", "whole bean", "msnm", "fecha de",
    "www.", ".com", "@",
  )
}
