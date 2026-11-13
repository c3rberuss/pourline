# SPEC-004: Catálogo de cafés, bitácora y compartir imagen

| Campo | Valor |
|---|---|
| Estado | Aprobado; plan aprobado, en implementación. Plan: [`plan-catalogo-cafes.md`](plan-catalogo-cafes.md) |
| Fecha | 2026-11-13 |
| Deriva de | [SPEC-002 Historial](spec-historial.md) (alcance Out: catálogo de cafés y compartir como imagen) y [SPEC-003 Curva objetivo](spec-curva-objetivo.md) (alcance Out: compartir como imagen) |
| Módulos afectados | `:app` (nuevo paquete `dev.dotscale.coffee`, nuevo `ui/coffees`, nuevo `share`, `history`, `settings`, `ui/scale`, `ui/history`, navegación) |
| Specs relacionados | [SPEC-001 Gráfica de vertido](spec-grafica-vertido.md), [SPEC-002 Historial](spec-historial.md), [SPEC-003 Curva objetivo](spec-curva-objetivo.md) |

## 1. Resumen y Contexto

- **Problema:**
  - El café de una preparación es un texto libre (`BrewEntity.coffee: String?`) con autocompletado.
    No hay ficha del café: ni tostador, ni origen, ni proceso, ni fecha de tueste.
  - No se puede seguir cómo evoluciona un mismo café de una preparación a otra ni cuánto queda en
    la bolsa.
  - Pasar los datos de la etiqueta a mano es lento.
  - No se puede compartir ni un café ni una preparación.
- **Solución:**
  - **Catálogo de cafés:** pantalla propia para registrar cafés con sus datos y hasta 2 fotos de la
    etiqueta.
  - **OCR de la etiqueta:** al añadir una foto, se lee el texto en el dispositivo (ML Kit) y se
    rellenan los campos vacíos del formulario para que el usuario los revise.
  - **Vínculo con las preparaciones:** el café se elige en Scale antes de verter y queda guardado
    con la preparación.
  - **Bitácora:** el detalle del café lista sus preparaciones con su nota de cata y una valoración
    de 1 a 5.
  - **Compartir como imagen:** la tarjeta del café y las gráficas (resumen en Scale, detalle de una
    preparación y comparación de dos).
- **Éxito:**
  - Registrar un café con foto y datos básicos cuesta **5 toques o menos** desde la pantalla de
    cafés, sin contar el teclado.
  - En las 15 etiquetas reales del usuario, el OCR extrae bien **el nombre en al menos 12 de 15** y
    **no inventa una fecha de tueste** en ninguna de las 13 que no la traen. Solo 2 traen fecha, y
    las dos están escritas a mano: su lectura se informa, pero no cuenta para el criterio. Nunca se
    guarda nada sin que el usuario lo revise.
  - Al terminar una preparación queda vinculada al café elegido, y desde ese café se ven todas sus
    preparaciones.
  - La tarjeta del café se comparte en **2 toques o menos** desde el detalle del café.
- **Alcance (In):**
  - Catálogo: alta, edición, detalle, búsqueda, archivar y desarchivar, borrar.
  - Hasta 2 fotos por café, tomadas con la cámara del sistema o elegidas en la galería.
  - OCR en el dispositivo con un parser propio.
  - Stock calculado de la bolsa, con estado "Agotado".
  - Días desde el tueste, en el café y en cada preparación.
  - Acción "Nueva bolsa".
  - Selector de café en Scale y en "Editar preparación".
  - Valoración de 1 a 5 por preparación; `notes` pasa a ser la nota de cata.
  - Filtro "Café" del historial basado en el catálogo.
  - Compartir como imagen: tarjeta del café, resumen en Scale, detalle de preparación y
    comparación.
- **Alcance (Out):**
  - Migrar los textos de café de las preparaciones existentes al catálogo (no hay datos reales que
    conservar).
  - OCR en la nube o con un modelo de visión remoto.
  - Cámara propia dentro de la app (CameraX) y OCR en vivo.
  - Varias bolsas dentro de un mismo café.
  - Compartir como texto o como archivo importable por otra instalación.
  - Sincronización o copia de seguridad del catálogo.
  - Rediseño de la navegación y de las barras superiores. Se hará en un spec posterior; el acceso a
    Cafés de §3.6 es provisional.

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como aficionado, quiero registrar un café fotografiando su etiqueta para no teclear sus
  datos uno por uno.
- **US-2:** Como aficionado, quiero revisar y corregir lo que leyó el OCR antes de guardar, para que
  el catálogo no tenga datos erróneos.
- **US-3:** Como aficionado, quiero elegir el café antes de verter para que la preparación quede
  vinculada sin pasos extra al terminar.
- **US-4:** Como aficionado, quiero ver en un café todas sus preparaciones con mi nota de cata, mi
  valoración y los días desde el tueste, para seguir cómo evoluciona.
- **US-5:** Como aficionado, quiero saber cuánto café queda en la bolsa y que la app me avise
  cuando se acabe.
- **US-6:** Como aficionado, quiero registrar una bolsa nueva del mismo café sin volver a escribir
  sus datos.
- **US-7:** Como aficionado, quiero compartir un café o una preparación como imagen en una app de
  mensajería o una red social.

### Flujos principales

1. **Alta con foto:**
   1. En Cafés, pulsar "+". Se abre `CoffeeEdit` vacío.
   2. En el hueco de la foto 1, pulsar "Cámara" (o "Galería").
   3. Tomar la foto en la cámara del sistema y confirmarla.
   4. La foto se reduce y se guarda; el OCR se ejecuta y rellena los campos vacíos, marcados como
      "Leído de la etiqueta".
   5. Revisar, corregir y pulsar "Guardar".
2. **Preparación con café:**
   1. En Scale, el selector de café muestra el último café usado (si no está archivado ni borrado).
   2. Se puede cambiar, quitar ("Sin café") o crear uno ("Nuevo café").
   3. Al terminar y guardar la preparación, queda vinculada al café y el stock se descuenta.
3. **Bitácora:** Cafés, abrir un café, ver sus preparaciones con fecha, "día N", valoración y nota;
   tocar una abre `BrewDetail`.
4. **Nueva bolsa:** en el detalle del café, "Nueva bolsa" abre `CoffeeEdit` prellenado con todos
   los datos salvo fecha de tueste, fecha de apertura y fotos.
5. **Compartir:** el icono de compartir (detalle del café, resumen en Scale, detalle de preparación
   o comparación) genera la imagen y abre el selector de Android.

## 3. Especificación Técnica

### 3.1 Modelo de datos

Base de datos `HistoryDatabase`: versión 2 a 3.

**Tabla nueva `coffees` (`CoffeeEntity`):**

| Campo | Tipo | Regla |
|---|---|---|
| `id` | `Long` | Clave autogenerada |
| `name` | `String` | Obligatorio, recortado, no vacío |
| `roaster` | `String?` | Tostador |
| `originCountry` | `String?` | País de origen |
| `originRegion` | `String?` | Región |
| `farm` | `String?` | Finca o productor |
| `varieties` | `List<String>` | Puede estar vacía; se guarda con un `TypeConverter` |
| `process` | `CoffeeProcess?` | `WASHED` (lavado), `NATURAL`, `HONEY`, `ANAEROBIC` (anaeróbico), `WET_HULLED` (semilavado), `OTHER` |
| `processOther` | `String?` | Solo con `OTHER`, como `BrewEntity.methodOther` |
| `altitudeMinM` | `Int?` | Mayor que 0. Con un solo valor, `altitudeMinM == altitudeMaxM` |
| `altitudeMaxM` | `Int?` | Mayor que 0 y mayor o igual que `altitudeMinM`. Se muestra "1800–2000 m" o "1900 m" |
| `roastLevel` | `RoastLevel?` | `LIGHT`, `MEDIUM`, `DARK` |
| `roastDate` | `LocalDate?` | Se guarda como día epoch; no puede ser futura |
| `roasterNotes` | `String?` | Notas de cata del tostador |
| `bagWeightG` | `Double?` | Mayor que 0 |
| `price` | `Double?` | Mayor que 0, en dólares (USD); se muestra como `$12.50` |
| `openedDate` | `LocalDate?` | Día epoch; no puede ser futura |
| `photo1`, `photo2` | `String?` | Nombre del archivo en `filesDir/coffee_photos/` |
| `archived` | `Boolean` | `false` por defecto |
| `createdAtEpochMs` | `Long` | |
| `lastUsedAtEpochMs` | `Long?` | Inicio de la última preparación vinculada; ordena el selector |

Índices: `name`, `archived`.

**Cambios en `brews` (`BrewEntity`):**

| Campo | Tipo | Regla |
|---|---|---|
| `coffeeId` | `Long?` | Clave foránea a `coffees.id`, `ON DELETE SET NULL`, con índice |
| `rating` | `Int?` | 1 a 5, o `null` sin valorar |
| `coffee` | `String?` | Existente. Pasa a ser la **copia del nombre** del café vinculado: se escribe al vincular y al renombrar el café. Si el café se borra, la preparación conserva el nombre |
| `notes` | `String?` | Existente. En la UI pasa a llamarse "Notas de cata" |

`MIGRATION_2_3` añade la tabla y las columnas; los valores existentes de `coffee` no se tocan y
quedan sin vínculo.

**Fotos:** JPEG con el lado mayor reducido a 1600 px como máximo, respetando la orientación EXIF.
Por cada foto se genera además una miniatura de 256 px para la lista.

### 3.2 Valores derivados (funciones puras en `dev.dotscale.coffee`)

- **Stock:** `stockG = bagWeightG - suma(doseG de las preparaciones vinculadas con doseG != null)`.
  Es `null` sin `bagWeightG`. No se guarda: se calcula en la consulta, así que editar o borrar una
  preparación lo corrige solo. Se muestra `max(0, stockG)`; con `stockG <= 0` el café está
  **agotado**.
- **Días desde el tueste:**
  - Del café: días entre `roastDate` y hoy (zona local).
  - De una preparación: días entre `roastDate` y la fecha local de `startedAtEpochMs`.
  - `null` si no hay `roastDate`.
- **Nueva bolsa:** `newBagFrom(coffee): CoffeeDraft` copia todo salvo `id`, `roastDate`,
  `openedDate`, fotos, `archived` y marcas de tiempo.

### 3.3 OCR

- **Motor:** ML Kit Text Recognition, alfabeto latino, **modelo incluido en el APK**
  (`com.google.mlkit:text-recognition`). Funciona sin conexión y sin Google Play Services.
- **Contratos:**

```kotlin
// Android: ejecuta ML Kit sobre la foto ya reducida.
interface LabelOcr {
  suspend fun read(photo: File): List<OcrLine>
}

data class OcrLine(val text: String, val heightPx: Int)

// JVM puro, sin dependencias de Android.
object LabelParser {
  fun parse(lines: List<OcrLine>, today: LocalDate): LabelFields
}

data class LabelFields(
  val name: String? = null,
  val roaster: String? = null,
  val roastDate: LocalDate? = null,
  val process: CoffeeProcess? = null,
  val processOther: String? = null,      // con OTHER, el texto leído ("Fermentación láctica")
  val varieties: List<String> = emptyList(),
  val roastLevel: RoastLevel? = null,
  val altitudeMinM: Int? = null,
  val altitudeMaxM: Int? = null,
  val originCountry: String? = null,
  val originRegion: String? = null,
  val farm: String? = null,              // "Finca" o "Productor"
  val bagWeightG: Double? = null,
)
```

- **Reglas del parser:**
  - **Nombre y tostador:** heurística por altura de línea (las más altas) que descarta líneas que
    son fechas, pesos o palabras clave.
  - **Nombre con proceso:** si la línea siguiente al título es un proceso conocido ("Lavado",
    "Semi lavado", "Honey", "Yellow honey", "Anaeróbico lavado"...), se une al nombre:
    "Pacamara" + "Red honey" da "Pacamara Red Honey". Así se distinguen cafés cuyo título es la
    variedad. El proceso se rellena igualmente en su campo.
  - **Fechas:**
    - Formatos numéricos (`dd/mm/aa`, `dd/mm/aaaa`, `aaaa-mm-dd`) y con mes en texto en español e
      inglés ("12 oct 2026", "Oct 12, 2026").
    - Solo junto a un rótulo de fecha ("Fecha de tueste", "Tostado", "Roasted"...): `dd.mm` sin
      año (para no leer "12.5 oz" como 12 de mayo) y `ddmmaa` de 6 cifras sin separadores
      ("Fecha de tueste: 120926" da 2026-09-12).
    - Nunca son fecha de tueste: "EST. 2021", años de premios ("2024: 7°") ni números sueltos.
    - Se prefiere `dd/mm`; si `dd/mm` es inválida y `mm/dd` es válida, se usa `mm/dd`. Los años
      de 2 cifras son `20aa`.
    - Si hay varias fechas, se elige la más reciente que **no esté en el futuro** (así se descarta
      el "consumir antes de").
  - **Rótulos de campo** en español e inglés ("Variedad", "Proceso"/"Process", "Altura"/"Altitud",
    "Origen"/"Origin", "Región", "Finca", "Productor"): rellenan el campo con el valor que les
    sigue. El valor con rótulo manda sobre el deducido por palabras clave.
  - **Proceso, variedad, nivel de tueste y país:** diccionarios de palabras clave en español e
    inglés, sin distinguir mayúsculas ni tildes. Las frases largas van primero: "Anaeróbico lavado"
    es `ANAEROBIC`; "Semi lavado" y "Semilavado", `WET_HULLED`; "Yellow honey" y "Red honey",
    `HONEY`; un proceso sin entrada fija ("Fermentación láctica") es `OTHER` con su texto.
  - **Opciones impresas:** si una línea trae varias opciones del mismo campo (por ejemplo,
    "Washed Natural Honey" con casillas, o "200 gr / 400 gr"), ese campo no se rellena. La misma
    cantidad en dos unidades ("12oz. / 340g.") no cuenta como opciones: se usan los gramos.
  - **Nivel de tueste:** solo `LIGHT`, `MEDIUM` o `DARK`. Los niveles combinados ("Light - Medium")
    no se rellenan, y "Medio" solo cuenta junto a un rótulo de tueste o en una línea sola.
  - **Altitud:** un número o un rango (`1800-2000`, `1800 – 2000`, `1800 a 2000`) seguido de `m`,
    `mts`, `msnm`, `m.s.n.m.`, `m s.n.m.` o `masl`, o sin unidad tras un rótulo de altitud. La coma
    es separador de miles ("1,850" es 1850). Un solo valor rellena mínimo y máximo con el mismo
    número.
  - **Peso:** número seguido de `g`, `gr` o `kg`, entre 50 y 5000 g.
  - **Tostador:** suele ser un logo, así que se extrae solo cuando aparece como texto. No cuenta
    para el criterio de aceptación.
- **Aplicación al formulario:**
  - Solo rellena campos **vacíos**; nunca pisa lo escrito.
  - Los campos rellenados quedan marcados como "Leído de la etiqueta" hasta que el usuario los
    edita.
  - Con 2 fotos, el OCR se ejecuta al añadir cada una; como solo rellena vacíos, manda la primera.
  - El botón **"Leer etiqueta"** del editor vuelve a ejecutar el OCR sobre las fotos que ya tiene
    el café (foto 1 y luego foto 2), con la misma regla: solo rellena campos vacíos. Está
    deshabilitado sin fotos y mientras el OCR está en curso.
  - La marca "Leído de la etiqueta" solo existe mientras el editor está abierto; no se guarda.

### 3.4 Fotos

- `ActivityResultContracts.TakePicture` hacia un archivo de caché expuesto con `FileProvider`, y
  `ActivityResultContracts.PickVisualMedia` (solo imágenes) para la galería.
- **No se declara el permiso `CAMERA`**, para que el intent de la cámara del sistema no lo exija.
- Cada hueco de foto muestra dos botones directos, "Cámara" y "Galería", y con foto, "Reemplazar"
  y "Quitar". Esto mantiene el alta en 5 toques: "+", "Cámara", disparador, confirmar en la cámara
  y "Guardar".
- Al reemplazar o quitar una foto, o al borrar el café, se borran sus archivos (foto y miniatura).
- Las miniaturas se decodifican fuera del hilo principal con una `LruCache`, sin añadir una
  librería de carga de imágenes.

### 3.5 Contratos de datos

```kotlin
interface CoffeeRepository {
  fun coffees(query: String, archived: Boolean): Flow<List<CoffeeListItem>>  // con stock y días desde el tueste
  fun pickable(): Flow<List<CoffeeListItem>>     // no archivados, por lastUsedAtEpochMs desc
  fun coffee(id: Long): Flow<CoffeeDetail?>      // café, stock y sus preparaciones (bitácora)
  suspend fun save(draft: CoffeeDraft): Long     // alta o edición; al renombrar actualiza brews.coffee
  suspend fun setArchived(id: Long, archived: Boolean)
  suspend fun delete(id: Long)                   // en @Transaction; luego borra los archivos de fotos
  fun suggestions(): Flow<CoffeeSuggestions>     // valores distintos de tostador, país, región y variedades
}
```

- La búsqueda filtra por `name`, `roaster`, `originCountry` y `originRegion`, sin distinguir
  mayúsculas.
- `BrewRepository` añade `setCoffee(brewId, coffeeId?)` y `rating` en `BrewDetails`. Vincular
  escribe `coffeeId`, la copia `coffee` y actualiza `coffees.lastUsedAtEpochMs`.
- `BrewFilter(coffee: String?)` pasa a `BrewFilter(coffeeId: Long?, method)`. Las opciones del
  filtro son los cafés con al menos una preparación vinculada, incluidos los archivados.
- El último café elegido en Scale se guarda en los ajustes (`BrewSettings.lastCoffeeId`).

### 3.6 Pantallas y navegación

Claves nuevas en `NavigationKeys.kt`:

```kotlin
@Serializable data object Coffees : NavKey
@Serializable data class CoffeeDetail(val id: Long) : NavKey
/** `id == null` crea; `fromCoffeeId` prellena una "Nueva bolsa". */
@Serializable data class CoffeeEdit(val id: Long? = null, val fromCoffeeId: Long? = null) : NavKey
```

- **Cafés (`CoffeesScreen`):**
  - Buscador (`AntInput`) y `AntSegmented` "Activos / Archivados".
  - Cada fila muestra miniatura, nombre, tostador, días desde el tueste y stock (o "Agotado").
  - Botón "+".
  - Estado vacío con una llamada a registrar el primer café.
- **Detalle (`CoffeeDetailScreen`):**
  - Fotos, datos, stock y días desde el tueste.
  - Con el café agotado y sin archivar, un `AntAlert` con "Agotado" y la acción "Archivar".
  - Acciones: Editar, Compartir, Nueva bolsa, Archivar o Desarchivar, Borrar.
  - **Bitácora:** sus preparaciones, de la más reciente a la más antigua, con fecha, "día N",
    valoración y nota de cata. Tocar una abre `BrewDetail`.
- **Editor (`CoffeeEditScreen`):**
  - Los 2 huecos de foto arriba, el botón "Leer etiqueta" (§3.3) y el formulario con los campos
    de §3.1.
  - Proceso y nivel de tueste con `AntSelect`. Altitud con dos campos, "desde" y "hasta".
  - Tostador, país, región y variedades con `AntAutoComplete`, sugiriendo los valores que ya hay
    en el catálogo (`suggestions`), filtrados por prefijo sin distinguir mayúsculas. Variedades
    admite varias entradas.
  - Al guardar, esos campos usan la grafía existente si coinciden sin distinguir mayúsculas, con
    la misma regla que `canonicalCoffee` (SPEC-002), para no crear duplicados.
- **Scale:** un selector de café junto al de referencia (`TargetPicker`), con la misma regla que
  este (SPEC-003): habilitado en `Armed` y `Finished`, deshabilitado en `Active`. El café se fija
  al pasar a `Active`. En `Finished` la preparación ya está guardada, así que cambiar el café aplica
  a la **siguiente**; la guardada se corrige en "Editar preparación". Opciones: cafés de `pickable()`, "Sin café" y "Nuevo café". "Nuevo café"
  abre `CoffeeEdit`; al guardar vuelve a Scale con ese café elegido.
- **Referencia y café (cambio respecto a SPEC-003):** hoy, elegir una preparación guardada como
  referencia copia su café al prellenado de la nueva (`ScaleViewModel.resolveBrew`). Con el catálogo
  deja de hacerlo: el café de la preparación sale solo del selector de café de Scale.
- **Editar preparación (`BrewEditScreen`):**
  - El campo "Café" pasa de `AntAutoComplete` a un selector del catálogo. Incluye el café
    vinculado aunque esté archivado, "Sin café" y "Nuevo café".
  - Se añade "Valoración" (1 a 5, con opción de quitarla).
  - "Notas" pasa a "Notas de cata".
- **Historial:** `HistoryRow` muestra la valoración. `BrewDetailScreen` muestra el café vinculado
  (enlace a su detalle), los días desde el tueste y la valoración.
- **Acceso:** un icono "Cafés" (`AntIconButton`) en la barra superior de Scan, junto a recetas e
  historial, y en la de Scale, junto a historial.

### 3.7 Compartir como imagen (paquete `dev.dotscale.share`)

- **Generación:** un composable de la tarjeta se dibuja fuera de pantalla con `GraphicsLayer`, se
  exporta a PNG en `cacheDir/shared/` y se expone con `FileProvider`
  (`${applicationId}.fileprovider`). Cada vez que se comparte se borran las imágenes anteriores de
  esa carpeta.
- **Envío:** `Intent.ACTION_SEND` con `image/png`, `EXTRA_STREAM`,
  `FLAG_GRANT_READ_URI_PERMISSION` y `Intent.createChooser`.
- **Imágenes:**
  - **Tarjeta del café:** foto 1 (si hay), nombre, tostador, origen, altitud, variedades, proceso,
    nivel y fecha de tueste con días desde el tueste, y notas del tostador. **No incluye** precio
    ni stock.
  - **Resumen en Scale** (`BrewSummaryCard`), **detalle de preparación** (`BrewChart` con
    objetivo) y **comparación** (`CompareChart`): la gráfica y sus métricas principales.
  - La de **detalle de preparación** añade la valoración (si hay) y la nota de cata, cortada a 3
    líneas con puntos suspensivos.
  - Las de gráficas llevan **al pie** el café vinculado: nombre, tostador y días desde el tueste.
    Sin café, no hay pie. En la comparación va una línea por cada preparación con café vinculado, o
    una sola si las dos usan el mismo café.
- **Accesos:** icono de compartir en la barra superior del detalle del café, de `BrewDetailScreen`
  y de `BrewCompareScreen`, y un botón en `BrewSummaryCard`. Con un toque se abre el selector y
  con otro se elige la app.
- **Diseño visual** (proporciones, tipografía y disposición de cada imagen): se define en el plan
  de implementación.

## 4. Casos Límite y Manejo de Errores

- El OCR no encuentra texto o no reconoce ningún campo -> `AntMessage` "No se pudo leer la
  etiqueta"; el formulario no cambia.
- El OCR falla por una excepción de ML Kit -> el mismo aviso; la foto se conserva.
- Fecha ambigua ("03/04/26") -> se lee como 3 de abril de 2026.
- Solo hay fechas futuras -> no se rellena la fecha de tueste.
- Fecha de tueste o de apertura futura escrita a mano -> error de validación; no se guarda.
- Nombre vacío, o peso, precio o altitud menores o iguales que 0 -> error de validación en el
  campo.
- Altitud "desde" mayor que "hasta" -> error de validación; no se guarda.
- Solo se rellena uno de los dos campos de altitud -> se guarda el mismo valor en ambos.
- Tostador, país, región o variedad escritos con otra grafía ("etiopía" frente a "Etiopía") -> se
  guarda con la grafía existente.
- El usuario cancela la cámara o la galería -> no pasa nada.
- La foto no se puede leer, reducir o guardar -> `AntMessage` de error; el café se puede guardar sin
  esa foto.
- Borrar un café con preparaciones -> `AntModal` de confirmación que sugiere archivar. Si se
  confirma, las preparaciones pierden el vínculo (`SET NULL`) pero conservan el nombre en
  `coffee`, y se borran los archivos de fotos.
- Café agotado (`stockG <= 0`) -> se muestra "Agotado" y se sugiere archivar; nunca se archiva
  solo. Sigue apareciendo en el selector hasta que se archive.
- El último café usado está archivado o borrado -> Scale no preselecciona ninguno.
- Cambiar el café en Scale tras terminar (`Finished`) -> no modifica la preparación guardada; aplica
  a la siguiente.
- Preparación sin dosis -> no descuenta stock.
- Café sin peso de bolsa -> no muestra stock ni "Agotado".
- Dos cafés con el mismo nombre (dos bolsas) -> se permiten; el selector y la lista los distinguen
  por la fecha de tueste.
- Preparaciones antiguas con `coffee` en texto y sin `coffeeId` -> se muestran con su texto; solo
  aparecen en el filtro del historial con "Todos".
- Falla la generación de la imagen para compartir -> `AntMessage` de error; no se abre el
  selector.
- No hay ninguna app que acepte imágenes -> el selector del sistema lo indica; la app no hace nada
  más.
- Concurrencia -> no aplica: app local de un solo usuario; Room serializa las escrituras y el
  borrado de un café ocurre en una `@Transaction`.
- **Privacidad:** fotos y datos se quedan en el dispositivo; solo salen cuando el usuario comparte
  una imagen. No se añade el permiso `CAMERA` (ver §6 sobre `INTERNET`).

## 5. Criterios de Aceptación (Definition of Done)

**Funcionales:**
- [ ] Se puede dar de alta, editar, buscar, archivar, desarchivar y borrar un café con los campos
      de §3.1.
- [ ] El alta de un café con una foto de la cámara y "Guardar" cuesta 5 toques o menos desde Cafés,
      sin contar el teclado.
- [ ] Al añadir una foto, o con "Leer etiqueta", el OCR rellena solo campos vacíos y los marca como
      "Leído de la etiqueta".
- [ ] Con las 15 etiquetas reales del usuario, el OCR extrae bien el nombre (igual sin distinguir
      mayúsculas, tildes ni espacios) en al menos 12 de 15.
- [ ] En las 13 etiquetas sin fecha de tueste, el OCR deja la fecha vacía (13 de 13). La lectura de
      las 2 fechas escritas a mano se informa, pero no cuenta. La lectura de fechas impresas se
      cubre con casos sintéticos en `LabelParserTest`.
- [ ] Una preparación hecha con un café elegido en Scale queda vinculada al terminar, aparece en la
      bitácora del café y descuenta su dosis del stock.
- [ ] Borrar una preparación, o cambiar su `doseG` en la base de datos, corrige el stock sin más
      acciones. La app no permite editar la dosis; el cambio de dosis se verifica a nivel de datos
      (`CoffeeDaoTest`).
- [ ] El café agotado muestra "Agotado" y la sugerencia de archivar.
- [ ] "Nueva bolsa" crea un café con los datos copiados salvo fechas y fotos.
- [ ] La valoración y la nota de cata se editan en "Editar preparación" y se ven en el historial,
      en el detalle de la preparación y en la bitácora.
- [ ] El filtro "Café" del historial usa el catálogo.
- [ ] La tarjeta del café se comparte en 2 toques o menos desde su detalle; el resumen en Scale, el
      detalle de preparación y la comparación también se comparten, con el café al pie si lo hay.
- [ ] La tarjeta del café no muestra precio ni stock; la imagen del detalle de preparación muestra
      la valoración y la nota de cata (3 líneas como máximo).
- [ ] Borrar un café conserva el nombre en sus preparaciones y elimina sus archivos de fotos.

**Rendimiento** (emulador con API 36 o superior; una apertura previa de calentamiento que no cuenta
y la mediana de 5):
- [ ] Con 200 cafés con foto, Cafés muestra la primera fila (`coffee_row`) en menos de 1 s.
- [ ] El OCR de una foto de 1600 px termina en menos de 3 s.
- [ ] La imagen para compartir se genera en menos de 1 s.

**Tests unitarios (JVM):**
- [ ] `LabelParserTest`: fixtures con el texto (líneas y alturas) que da ML Kit en cada etiqueta
      real, versionados en `app/src/test/resources/labels/`, más casos sintéticos de fechas,
      proceso (incluido semilavado), variedades y altitud con valor único y con rango.
- [ ] Lectura de fechas: `dd/mm`, `mm/dd` de reserva, mes en texto, años de 2 cifras y descarte de
      futuras.
- [ ] `CoffeeValidator` (incluido el rango de altitud), grafía canónica de las sugerencias, stock,
      días desde el tueste y `newBagFrom`.
- [ ] Aplicación del OCR al formulario: no pisa campos escritos y marca los rellenados.

**Tests instrumentados:**
- [ ] `CoffeeDaoTest`: búsqueda, archivados, stock, `SET NULL` al borrar con el nombre conservado,
      y `lastUsedAtEpochMs`.
- [ ] `HistoryMigrationTest`: migración de 2 a 3 conservando las preparaciones existentes.
- [ ] Tests de UI: alta desde la galería, detalle con bitácora, selector de café en Scale con la
      báscula simulada, valoración en "Editar preparación", filtro del historial y que compartir
      lanza un `ACTION_SEND` con un `image/png`.
- [ ] `CoffeePerformanceTest`: los tres objetivos de rendimiento. La foto del OCR es una etiqueta
      sintética generada por el test.

**Verificación del OCR con etiquetas reales:**
- [ ] Las 15 fotos están en una carpeta local **fuera del repo** y se suben al emulador o al
      teléfono con `adb push`.
- [ ] Un test instrumentado, excluido de la suite normal, pasa cada foto por ML Kit y guarda sus
      líneas y alturas como JSON en `app/src/test/resources/labels/`. Ese JSON se versiona y
      alimenta `LabelParserTest`, aunque contenga todo el texto de la etiqueta (son etiquetas
      comerciales). Las fotos no se versionan.
- [ ] El mismo test compara lo que extrae el parser con un archivo de valores esperados (nombre y
      fecha de tueste por etiqueta, vacía si no la trae), que también vive fuera del repo, e
      informa de los aciertos (objetivos: nombre 12 de 15; ninguna fecha inventada, 13 de 13).

**E2E por adb** en el emulador con la báscula simulada, sin ARTEMIS:
- [ ] Alta de un café con una foto real de la galería, revisión de lo leído y guardado.
- [ ] Vertido simulado con el café elegido, valoración y nota, y comprobación de bitácora y stock.
- [ ] Compartir la tarjeta del café y una preparación.

**Calidad:**
- [ ] Lint y formato del proyecto sin avisos nuevos.

## 6. Preguntas abiertas

1. **Moneda del precio.** *Resuelto en la revisión: dólares (USD) de momento, sin selector de
   moneda (§3.1).*
2. **Permiso `INTERNET` de ML Kit.** El modelo incluido no necesita red, pero las dependencias de
   ML Kit pueden añadir `INTERNET` al manifiesto fusionado para su telemetría. *Resuelto en la
   revisión: se verifica en el plan y, si aparece, se decide allí si se elimina con
   `tools:node="remove"`.*
3. **Diseño visual de las imágenes para compartir.** *Resuelto en la revisión: se define en el plan
   de implementación.*
4. **Ubicación del acceso a Cafés.** *Resuelto en la revisión: icono en la barra superior de Scan,
   junto a recetas e historial, y de Scale, junto a historial (§3.6).*

## 7. Alternativas descartadas

- **OCR con un modelo de visión en la nube, o híbrido:** extrae mejor los datos estructurados,
  pero exige API key, conexión y coste por foto, y la foto saldría del teléfono.
- **Modelo de ML Kit descargado por Play Services:** el APK no crece, pero el primer uso necesita
  conexión y Google Play Services.
- **Cámara propia con CameraX y OCR en vivo:** mejor experiencia, pero mucho más trabajo y exige el
  permiso `CAMERA`.
- **Varias bolsas dentro de un café:** más completo, pero complica el modelo; se cubre con "Nueva
  bolsa".
- **Stock guardado en una columna:** habría que mantenerlo al editar o borrar preparaciones;
  calcularlo lo mantiene correcto siempre.
- **Archivar automáticamente al agotarse:** se prefirió solo sugerirlo.
- **Impedir borrar un café con preparaciones:** se permite con confirmación, conservando el nombre.
- **Migrar los textos de café existentes al catálogo:** no hay datos reales que conservar.
- **Notas de cata propias del café, separadas de las preparaciones:** se usan las notas de cada
  preparación más una valoración.
- **Compartir en un spec aparte:** la tarjeta del café y las gráficas comparten la misma base de
  generación y envío de imágenes, así que van juntas.
