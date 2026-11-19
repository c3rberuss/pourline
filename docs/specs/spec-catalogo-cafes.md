# SPEC-004: Catálogo de cafés, bitácora y compartir imagen

| Campo | Valor |
|---|---|
| Estado | Implementado (F0 a F9); queda el E2E 1b manual de la cámara (§5). Plan: [`plan-catalogo-cafes.md`](plan-catalogo-cafes.md) |
| Fecha | 2026-11-13 |
| Deriva de | [SPEC-002 Historial](spec-historial.md) (alcance Out: catálogo de cafés y compartir como imagen) y [SPEC-003 Curva objetivo](spec-curva-objetivo.md) (alcance Out: compartir como imagen) |
| Módulos afectados | `:app` (nuevo paquete `dev.josueamaya.pourline.coffee`, nuevo `ui/coffees`, nuevo `share`, `history`, `settings`, `ui/scale`, `ui/history`, navegación) |
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

### 3.2 Valores derivados (funciones puras en `dev.josueamaya.pourline.coffee`)

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

### 3.7 Compartir como imagen (paquete `dev.josueamaya.pourline.share`)

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

### 3.8 Decisiones de diseño e implementación (D1 a D41)

Se aprobaron junto con el plan ([`plan-catalogo-cafes.md`](plan-catalogo-cafes.md)) y completan los apartados anteriores. El diseño visual detallado de cada pantalla e imagen (la fase F0) vive en el plan; las referencias "F0.x" de las tablas apuntan a él.

#### 3.8.1 Diseño visual

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Tamaño de las imágenes (cierra §6.3) | Las cuatro imágenes (tarjeta del café, resumen en Scale, detalle de preparación y comparación) miden **1080 × 1350 px** (4:5; 360 × 450 dp). Se dibujan con `Density(3f, fontScale = 1f)` fija, sea cual sea el teléfono. | Decisión del usuario al revisar F0. 4:5 es el vertical máximo del feed de Instagram y se ve entero en WhatsApp. Con un solo formato hay una sola constante y una sola comprobación de tamaño. La densidad fija hace el tamaño en píxeles determinista y el escalado de fuente del sistema no rompe la maqueta. |
| D2 | Tema, marco y compactación de las imágenes | Siempre en **tema claro** (`PourlineTheme(darkTheme = false)`), fondo `bgContainer`, barra de acento de 4 dp en `primary.base` arriba, márgenes de 20 dp a los lados y 16 dp arriba y abajo, y la marca "Dot Scale" (`app_name`) abajo a la derecha, en la misma línea que la última del pie del café. Tipografía de `antTypography()`; tabla en F0.4. Para que las tres gráficas quepan en 450 dp: (a) sin sobretítulo ("RESUMEN", "PREPARACIÓN", "COMPARACIÓN"); (b) el ajuste al objetivo pasa de línea propia a un `AntTag` en la fila del título; (c) la valoración va como estrellas a la derecha del subtítulo y la nota de cata, sin rótulo, como cita de 3 líneas; (d) se quita el reparto por nivel de estabilidad; (e) la gráfica deja los 200 dp fijos y ocupa el alto que sobra (`weight(1f)`), con un **mínimo de 144 dp**; (f) la comparación lleva solo la gráfica de agua, 4 de las 8 métricas de `compareRows` y un pie de una línea por café. | La imagen se ve en otras apps sobre fondos desconocidos: el claro es el más legible y no depende del tema de quien la genera. Con los diseños de 9:16 harían falta 533, 603 y 589 dp. Se conserva lo que fija §3.7: gráfica, métricas principales, café al pie y, en el detalle, valoración y nota de 3 líneas. El sobretítulo y el rótulo de la nota no aportan datos. El reparto por nivel no cabe en una fila a 320 dp (tres etiquetas ocupan unos 330), y la estabilidad ya lleva el color de su nivel. 144 dp son 432 px de alto, legibles con los ejes, y en el resumen, que no tiene nota, la gráfica crece hasta 233 dp en vez de dejar un hueco. Marca y pie en la misma línea ahorran 20 dp. |
| D3 | Contenido y recortes | Lo que lleva cada imagen y su orden están en F0.4. Todos los textos tienen `maxLines` y elipsis; las filas sin dato se omiten. La tarjeta del café nunca incluye precio ni stock. | Con alto fijo, el contenido no puede crecer sin límite. |
| D4 | Pie de la comparación | Con dos cafés distintos, una línea por preparación vinculada: punto de color de A o B, nombre y "tostador · día N". Si las dos tienen el mismo `coffeeId`, el pie normal de un café, sin punto, con "días 9 y 11" (o "día 9" si coinciden). Con una sola vinculada, el pie normal con su punto. Sin ninguna vinculada, no hay pie. | El spec habla de "el café vinculado" en singular, pero en una comparación puede haber dos. |
| D5 | Selectores de Scale | El chip "Objetivo" sale de la cabecera de `BrewChartCard`. Arriba del cuerpo de la tarjeta van dos filas: "Objetivo [V60 clásica v]" y "Café [Etiopía Guji · día 9 v]", con `AntSelectTrigger` (nuevo). Las dos se deshabilitan en `Active`. Se conserva `testTag("target_chip")`. | Dos chips con nombre no caben en la cabecera a 360 dp sin cortar el título. Con etiqueta a la izquierda se leen sin el prefijo "Objetivo:" y TalkBack recibe la descripción completa. |
| D6 | Selector de café | Un único `CoffeePicker` (nuevo, sobre `AntDrawer`) para Scale y para "Editar preparación": buscador, filas con miniatura, tostador, fecha de tueste y stock, y los botones "Sin café" y "Nuevo café". | Distingue dos bolsas del mismo café por la fecha de tueste (§4 del spec) y admite 200 cafés con `LazyColumn`, cosa que el menú de `AntSelect` no hace. |
| D7 | Editor del café | "Guardar" va en la barra superior (siempre visible). Los dos huecos de foto son cuadrados, como el `Upload` *picture-card* de Ant (borde discontinuo). "Reemplazar" abre un menú con "Cámara" y "Galería". "Leer etiqueta" va justo debajo de los huecos, a todo el ancho. | Con "Guardar" arriba, el alta cuesta 5 toques sin desplazar el formulario: "+", "Cámara", disparador, confirmar y "Guardar". |
| D8 | Marca "Leído de la etiqueta" | Un `AntTag` (`Primary`, icono de leer etiqueta) a la derecha de la etiqueta del campo, dentro de `AntFormItem` (nuevo). Tras cada lectura con resultados, un `AntAlert` informativo con el número de campos rellenados. | La marca no tapa el valor y se ve sin enfocar el campo. El aviso explica qué revisar (US-2). |
| D9 | Fechas | Tueste y apertura con `AntDatePicker` (nuevo): un campo con el aspecto de `AntSelect` que abre el `DatePickerDialog` de Material 3 con las fechas futuras deshabilitadas (también en su modo de escritura) y un botón para borrarla. `CoffeeValidator` sigue rechazando fechas futuras. | Escribir fechas con el teclado numérico de Android es incómodo (falta "/"). El modo de escritura del diálogo cubre "escrita a mano" y la validación queda como defensa. |
| D10 | Detalle del café | Orden (decisión del usuario al revisar F0): aviso de agotado o archivado, fotos, estado (días y stock con `AntProgress`), **bitácora**, datos del café en un `AntCollapse` (nuevo) **plegado por defecto** y, al final, las acciones (dos filas). Toda la pantalla es una `LazyColumn` y cada preparación de la bitácora es una tarjeta pequeña. "Compartir" va en la barra superior. El estado del plegable se guarda con `rememberSaveable` y no se persiste entre visitas. | Lo que se consulta a diario (cuánto queda, cuántos días lleva y cómo salieron las últimas preparaciones) queda arriba; los datos de la etiqueta casi no cambian y plegados no empujan la bitácora. "Compartir" en 1 toque cumple los 2 toques del §1 del spec. Con una bitácora larga, las acciones quedan al final (R14). |
| D11 | Valoración | `AntRate` (nuevo). Estrella llena en `warning.text` y vacía en `fill`. En `HistoryRow`, en la bitácora y en las imágenes es de solo lectura (12–16 dp). En "Editar preparación" es interactiva (32 dp, área de 48 dp): tocar la estrella marcada la quita, y hay además un enlace "Quitar valoración". | El dorado de Ant (`#FADB14`) no llega a 3:1 sobre blanco. Quitar con un toque sobre la misma estrella es lo de antd (`allowClear`); el enlace lo hace descubrible y accesible. |
| D12 | Detalle de la preparación y edición | `BrewDetailScreen` gana la tarjeta "Cata" (café vinculado como fila pulsable, "día N", valoración y notas de cata) justo después de la gráfica, y "Detalles" deja de mostrar café y notas. En `BrewEditScreen` el orden pasa a ser Nombre, Café, Valoración, Notas de cata, Método, Molienda y Temperatura. | Lo que se escribe después de catar queda junto y arriba. Sin duplicar el café y las notas en dos tarjetas. |
| D13 | Lista de cafés | Orden por `COALESCE(lastUsedAtEpochMs, createdAtEpochMs) DESC, id DESC`. "Agotado" es un `AntTag` `Error` en la fila y el stock se muestra como "Quedan 180 g". | Arriba quedan los cafés en uso. El spec no fija el orden. |
| D14 | Fotos en la UI | Las imágenes llegan a los composables por `LocalCoffeeImages` (`staticCompositionLocalOf<CoffeeImages?> { null }`), provisto en `MainActivity`. Con `null` se dibuja el marcador sin foto. | Evita pasar la caché por diez pantallas, y los tests que no la proveen funcionan con el marcador. |

#### 3.8.2 Implementación

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D15 | Paquetes | `dev.josueamaya.pourline.coffee`: modelo, validación, derivados, Room y repositorio. `dev.josueamaya.pourline.coffee.label`: parser (JVM puro) y `MlKitLabelOcr`. `dev.josueamaya.pourline.coffee.photo`: fotos y miniaturas. `dev.josueamaya.pourline.ui.coffees`: pantallas. `dev.josueamaya.pourline.share`: imágenes para compartir. | Los archivos puros se comprueban con `grep "import android"` por lista, como en `history`. |
| D16 | Migración 2 → 3 | `val MIGRATION_2_3` (constante, no necesita dependencias): `CREATE TABLE coffees` y sus índices copiados de `3.json`, `ALTER TABLE brews ADD COLUMN coffeeId INTEGER REFERENCES coffees(id) ON UPDATE NO ACTION ON DELETE SET NULL`, `ALTER TABLE brews ADD COLUMN rating INTEGER` e `index_brews_coffeeId`. No se reconstruye `brews`. | Reconstruir `brews` (crear, copiar, borrar la antigua) dispararía el `CASCADE` hacia `brew_curves` y `brew_targets` con las claves foráneas activas. SQLite admite `REFERENCES` en `ADD COLUMN` con valor por defecto `NULL`, y `PRAGMA foreign_key_list` lo informa igual que una FK de tabla (R2). |
| D17 | Conversores (`CoffeeConverters`) | `List<String>` como texto unido con `\n` (vacía → `""`, columna no nula). `LocalDate` como `toEpochDay()`. `CoffeeProcess` y `RoastLevel` por nombre; un nombre desconocido se lee como `null`. | Ninguna variedad tiene saltos de línea (se recortan). Igual que `ReferenceType` en `HistoryConverters`. |
| D18 | Búsqueda | SQL solo filtra `archived`. El texto se busca en Kotlin con `CoffeeSearch.matches(item, query)`: `name`, `roaster`, `originCountry` y `originRegion`, sin distinguir mayúsculas **ni tildes** (`Normalizer` NFD sin marcas). La usan la lista y `CoffeePicker`. | `LIKE` de SQLite no pliega "Í" ni "é". Plegar tildes amplía un poco el spec ("etiopia" encuentra "Etiopía"); se puede quitar si no se quiere. |
| D19 | Stock y días | El stock se calcula en la consulta: `bagWeightG - COALESCE((SELECT SUM(doseG) FROM brews WHERE coffeeId = c.id AND doseG IS NOT NULL), 0)` (`NULL` sin peso). Los días, en Kotlin con `today: () -> LocalDate` y la zona inyectados en el repositorio. Un número de días negativo (tueste posterior a la preparación) se trata como `null`. | El stock queda bien solo al editar o borrar preparaciones (§3.2). "Hoy" depende de la zona local, que SQL no conoce. |
| D20 | `lastUsedAtEpochMs` | Se recalcula como `MAX(startedAtEpochMs)` de sus preparaciones vinculadas en la misma transacción que guarda, vincula, desvincula, borra o restaura una preparación (del café anterior y del nuevo). | Definición del spec ("inicio de la última preparación vinculada"), correcta también al vincular una preparación antigua o al borrar la última. |
| D21 | Café al guardar una preparación | `BrewContext` gana `coffee: UsedCoffee? = null` (`id`, `name`). En la transacción de `insertBrew`: si el café existe, `coffeeId` y el nombre actual; si se borró mientras tanto, `coffeeId = null` y se conserva el nombre. El prellenado desde una referencia (`resolveBrew`) **deja de copiar el café**: el café sale solo del selector de Scale. | `coffee` pasa a ser la copia del nombre del café vinculado (§3.1). Desde Scale se puede abrir Cafés y borrar el café elegido en plena sesión: sin la comprobación, la FK haría fallar el guardado. Copiar el texto de la referencia crearía preparaciones nuevas sin vínculo. Es el cambio respecto a SPEC-003 que fija el §3.6 del spec ("Referencia y café"; P5 resuelta). |
| D22 | Edición del café de una preparación | `BrewDetails` gana `rating: Int?` (fuera de 1–5 → `null` en `normalized()`). `applyDetails` deja de escribir `coffee`. El café se cambia con `BrewRepository.setCoffee(brewId, coffeeId?)` solo si la elección cambió. El formulario guarda `CoffeeChoice` (`None`, `Linked(PickedCoffee)`, `Legacy(name)`): una preparación antigua con texto y sin vínculo muestra su texto y lo conserva mientras no se elija otra cosa. "Sin café" quita el vínculo y el nombre. Son dos escrituras `NonCancellable` seguidas. | Así el nombre solo lo escriben el vínculo y el renombrado (§3.1), y las preparaciones antiguas se ven con su texto (§4). Dos escrituras locales no justifican un contrato nuevo que mezcle las dos cosas. |
| D23 | "Nuevo café" desde Scale o "Editar preparación" | `CoffeeRepository.created: SharedFlow<Long>` emite el id de cada alta. El ViewModel que pulsó "Nuevo café" marca que espera uno y, al recibirlo, lo elige: Scale con `BrewSettings.setLastCoffeeId(id)` y la edición en su formulario. Con la sesión `Active` el id se guarda para la siguiente. | En Navigation 3 la entrada que abrió el editor sigue viva en la pila con su ViewModel, así que no hace falta pasar resultados por la navegación ni cambiar la clave `CoffeeEdit` del spec. |
| D24 | Navegación al guardar en el editor | Alta desde "+": vuelve a la lista. "Nueva bolsa": sustituye `CoffeeEdit` por `CoffeeDetail(nuevo)` en la pila (atrás vuelve al café original). Edición y "Nuevo café": vuelve atrás. | Tras una bolsa nueva interesa ver esa bolsa. En los demás casos, la pantalla de origen ya muestra el resultado. |
| D25 | Fotos | `<uuid>.jpg` (lado mayor ≤ 1600 px, JPEG 85) y `<uuid>_thumb.jpg` (256 × 256 recortada al centro, JPEG 80) en `filesDir/coffee_photos/`. Las columnas guardan solo el nombre principal. Se decodifica con `BitmapFactory` (primero los límites y `inSampleSize`) y se gira con `android.media.ExifInterface(InputStream)`, sin dependencias nuevas. | Funciona igual desde minSdk 26 (`ImageDecoder` gira solo desde la API 28). La miniatura cuadrada encaja en la fila de 56 dp (168 px a 3x). |
| D26 | Vida de los archivos | El editor escribe la foto al añadirla. Las sustituidas o quitadas se borran **al guardar**: `CoffeeRepository.save` compara los nombres antiguos y los nuevos tras la transacción. Al descartar el editor (o en `onCleared` sin guardar) se borran las fotos nuevas no guardadas. Al arrancar, `CoffeePhotoStore.sweepOrphans(referenced)` borra los archivos no referenciados de más de 24 h. | Descartar no puede perder la foto que ya tenía el café. El barrido cubre la muerte del proceso a mitad de edición. |
| D27 | OCR en Android | `MlKitLabelOcr` con `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` sobre la foto ya reducida (`InputImage.fromBitmap(bitmap, 0)`). Un `OcrLine` por `Text.Line`, con `boundingBox.height()`, en el orden de lectura de ML Kit. `Task.await()` propio con `suspendCancellableCoroutine`, sin `kotlinx-coroutines-play-services`. El ViewModel ejecuta el OCR bajo un `Mutex`. | Con dos fotos, los resultados se aplican en orden y "manda la primera" (§3.3). |
| D28 | Proceso y nombre en la etiqueta | Diccionario plegado (sin mayúsculas ni tildes), con las frases largas antes que las cortas: "anaeróbico lavado", "anaerobic washed", "anaerobic natural", "anaeróbico" y "anaerobic" → `ANAEROBIC`; "semi lavado", "semilavado", "semi-lavado" y "wet hulled" → `WET_HULLED`; "yellow honey", "red honey", "black honey", "white honey" y "honey" → `HONEY`; "natural" → `NATURAL`; "lavado", "washed" y "fully washed" → `WASHED`; "fermentación láctica" y "lactic fermentation" → `OTHER`, con `processOther` = el texto tal como viene en la etiqueta. Una frase larga consume su texto: "Anaeróbico lavado" no cuenta además como lavado. "Miel" no cuenta (aparece en las notas). Fuentes, por orden: el valor del rótulo "Proceso"/"Process" (D40) y, si no hay, las líneas con un solo proceso; si esas líneas no coinciden, no se rellena. **Nombre:** la línea candidata más alta (sin fechas, pesos, rótulos de campo ni las palabras de D37); si la línea siguiente en el orden de lectura es solo una frase de proceso, se une al nombre con mayúscula inicial en cada palabra ("Pacamara" + "Red honey" → "Pacamara Red Honey", §3.3 del spec), y el proceso se rellena igualmente. **Tostador:** la segunda candidata más alta; es poco fiable (casi siempre es un logo, y en 2 de las 15 fotos no se ve) y no cuenta para el criterio. País con el nombre canónico en español; "Colombia" no cuenta como variedad. | Son los procesos que aparecen en las 15 etiquetas reales, y "semilavado" y "anaeróbico lavado" contienen "lavado". Con dos procesos distintos sin rótulo, cualquier elección sería adivinar. La unión con el proceso distingue los cafés cuyo título es la variedad. |
| D29 | `INTERNET` (cierra §6.2) | En F3 se revisa el manifiesto fusionado. Si ML Kit añade `INTERNET` (lo hace `transport-backend-cct`), se elimina con `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />`. `ACCESS_NETWORK_STATE`, si aparece, se deja. | La app no usa la red y el spec promete que fotos y datos no salen del teléfono. `ACCESS_NETWORK_STATE` no da acceso a datos, y quitarlo podría hacer fallar la planificación de trabajos de la telemetría de ML Kit (R4). |
| D30 | Captura y envío | `ShareCaptureHost` compone la tarjeta dentro de un nodo de tamaño 0 (`Modifier.layout`) que mide el hijo al tamaño fijo, con `LocalDensity` fija y `drawWithContent { layer.record { drawContent() } }` **sin** dibujar la capa. Tras dos frames, `layer.toImageBitmap()`. El PNG se escribe en `Dispatchers.IO` en `cacheDir/shared/`, tras borrar los anteriores. El envío pasa por `fun interface ShareSender { fun send(chooser: Intent) }`, que cada pantalla recibe con un valor por defecto. | Es el mecanismo del spec (§3.7) sin que la tarjeta aparezca en pantalla. El `ShareSender` permite probar el `ACTION_SEND` sin Espresso-Intents. |
| D31 | Fixtures reales del OCR | Escritura en el dispositivo con `org.json` (real en Android) y lectura en la JVM con `kotlinx-serialization-json` (`testImplementation`). JSON por etiqueta: `file`, `capturedOn` (fecha de captura) y `lines` (`text`, `heightPx`). La captura solo corre con `-e labelsCapture true` (`assumeTrue`), y se lanza con `am instrument` para que la app no se desinstale al terminar. Los esperados llegan al test JVM con `-PlabelsExpected=<ruta>`. | `org.json` está simulado en los tests JVM (`isReturnDefaultValues`). `connectedDebugAndroidTest` desinstala la app y borraría `Android/data/dev.josueamaya.pourline/`. `capturedOn` hace deterministas las fechas "no futuras". |
| D32 | Dependencias del ViewModel de Scale | Dos interfaces nuevas: `CoffeeStore` (`lastCoffeeId: StateFlow<Long?>`, `setLastCoffeeId`), implementada por `BrewSettings`, y `ScaleCoffees` (`coffee(id): Flow<PickedCoffee?>`, `null` si no existe o está archivado, y `created: Flow<Long>`), implementada por `RepositoryScaleCoffees(coffeeRepository)` sobre `pickable()`. Las dos tienen un valor por defecto sin cafés. | Igual que `ReferenceStore` y `TargetReferences` (D2 de SPEC-003). Con valores por defecto no cambian los tests actuales del ViewModel. |
| D33 | Filtro del historial | `BrewFilter(coffeeId: Long?, method)`. Opciones: `BrewDao.coffeeOptions()` → `CoffeeOption(id, name, roastDate)`, cafés con al menos una preparación vinculada (también los archivados), por la preparación más reciente. Si dos opciones tienen el mismo nombre, la etiqueta añade " · tostado 12 oct 2026" (o " · sin fecha"). `FilterSaver` guarda el id como texto. `ComparePickScreen` empieza con el `coffeeId` de A (o "Todos"). | §3.5 del spec. Dos bolsas del mismo café se distinguen por la fecha de tueste. |
| D34 | Números del formulario | Altitud: enteros. Peso de la bolsa: `,` o `.` y un decimal como máximo. Precio: `,` o `.` y dos decimales como máximo. Un solo campo de altitud relleno → los dos iguales al guardar (§4). | Mismo estilo de parseo que `parseWaterTemp`. |
| D35 | Borrar un café | Siempre con `AntModal`. Con preparaciones: "Tiene N preparaciones. Perderán el vínculo, pero conservarán el nombre. Si ya no lo compras, puedes archivarlo." Sin preparaciones: "Se borrarán también sus fotos." Sin deshacer. | Las fotos se borran del disco: no hay forma de deshacerlo. |
| D36 | Unidades y moneda | Peso de la bolsa y stock siempre en gramos ("180 g"), sea cual sea la unidad de la báscula. Precio con `String.format(Locale.US, "$%.2f")`. | El peso de las bolsas se etiqueta en gramos. USD según §6.1. |
| D37 | Líneas con varias opciones | Si una línea trae dos o más valores distintos de un mismo campo (procesos, niveles de tueste o pesos en gramos), ese campo no se rellena desde esa línea: "PROCESS: WASHED NATURAL HONEY", "200 gr / 400 gr", "Claro Medio Oscuro". La misma cantidad en dos unidades no son opciones: "12oz. / 340g." da 340 g (onzas y libras se ignoran). "Molido", "Grano", "Ground" y "Whole bean" descartan una línea como nombre. | Esas etiquetas imprimen todas las opciones y marcan una con una casilla o a mano, y el OCR no ve la marca. No se adivina. |
| D38 | Nivel de tueste | "Claro"/"light", "medio"/"medium" y "oscuro"/"dark" solo cuentan en una línea con "tueste", "tostado", "roast" o "nivel", como valor de ese rótulo o si la línea es solo esa palabra ("Tueste medio", "Medio"). Los niveles combinados ("Light - Medium", "Medium-Dark", "medio claro", "medio-oscuro") **no se rellenan**. | El enum solo tiene tres niveles, y elegir uno de los dos sería adivinar. "Medio" y "light" son palabras comunes en otros textos de la etiqueta. |
| D39 | Números de la etiqueta | **Altitud:** entero de 3 o 4 cifras, o con separador de miles (`,` o `.` seguido de exactamente 3 cifras: "1,560", "1.800"), solo o en rango, seguido de `m`, `mts`, `mts.`, `msnm`, `m.s.n.m.`, `m s.n.m.`, `masl` o `metros`, sin distinguir mayúsculas ("1,850 M.S.N.M.", "1,600 Mts.", "1,500 m s.n.m.", "Cultivado a: 1350 msnm"). Sin unidad, solo como valor de un rótulo de altitud ("Altura \| 1,200"). Fuera de 100–5000 m se descarta. **Peso:** número seguido de `g`, `gr`, `grs`, `gramos` o `kg` ("250G", "340g", "454 g"; "0,5 kg" → 500), entre 50 y 5000 g. | Las etiquetas reales escriben la altitud con coma de miles: en ese formato la coma nunca es decimal. El límite inferior del peso descarta "15 g" de una receta impresa. |
| D40 | Rótulos de campo y ampliación de `LabelFields` | Rótulos reconocidos (es/en, plegados): "Variedad", "Varietal" y "Variety" → variedades; "Proceso" y "Process" → proceso; "Altura", "Altitud", "Altitude" y "Elevation" → altitud; "Origen" y "Origin" → país; "Región" y "Region" → región; "Finca", "Farm", "Productor" y "Producer" → finca; "Tueste" y "Roast" → nivel; "Peso", "Net wt" y "Contenido neto" → peso. El valor es el resto de la línea tras el rótulo y un separador (`:`, `\|`, `-` o espacio) o, si queda vacío, la línea siguiente. Un valor con rótulo manda sobre las palabras clave sueltas. Las variedades con rótulo se separan por `,`, `/`, `&`, " y " o " and " y se aceptan aunque no estén en el diccionario; un proceso con rótulo que no está en el diccionario queda como `OTHER` con ese texto. Las líneas tras "Notas", "Notes", "Perfil" o "Tasting notes" no se usan para proceso, variedad ni nivel. `LabelFields` gana `originRegion: String? = null`, `farm: String? = null` y `processOther: String? = null`. | Las etiquetas reales llevan estos rótulos. Sin los tres campos nuevos no se podrían rellenar la región, la finca ni el texto de "Fermentación láctica". Con valores `null` por defecto, el contrato del §3.3 sigue siendo compatible, pero conviene anotarlo en el spec. |
| D41 | Fechas engañosas y formatos sin año | No son fecha de tueste un año suelto ("EST. 2021", "EST. 2013") ni nada en líneas con "est.", "since", "desde", "premio", "award", "cup of excellence" o el "°"/"º" de un puesto ("2024: 7°"). Los formatos sin año (`dd.mm`, con el año actual o el anterior si sería futura) y `ddmmaa` de 6 cifras ("Fecha de tueste: 120926" → 2026-09-12) solo valen en una línea con rótulo de fecha ("fecha", "tueste", "tostado", "roasted", "roast date") o en la siguiente. Los formatos con año valen en cualquier línea no excluida. | El objetivo es no inventar ninguna fecha en las 13 etiquetas que no la traen (§5 del spec). Sin rótulo, "12.5" (un peso en onzas) o "120926" (un lote) se leerían como fechas. |

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
- [x] Se puede dar de alta, editar, buscar, archivar, desarchivar y borrar un café con los campos
      de §3.1.
- [ ] El alta de un café con una foto de la cámara y "Guardar" cuesta 5 toques o menos desde Cafés,
      sin contar el teclado. *(Cubierto por `CoffeeEditScreenTest` con un registro de resultados falso; falta
      el E2E 1b manual con la escena virtual de la cámara del emulador.)*
- [x] Al añadir una foto, o con "Leer etiqueta", el OCR rellena solo campos vacíos y los marca como
      "Leído de la etiqueta".
- [x] Con las 15 etiquetas reales del usuario, el OCR extrae bien el nombre (igual sin distinguir
      mayúsculas, tildes ni espacios) en al menos 12 de 15.
- [x] En las 13 etiquetas sin fecha de tueste, el OCR deja la fecha vacía (13 de 13). La lectura de
      las 2 fechas escritas a mano se informa, pero no cuenta. La lectura de fechas impresas se
      cubre con casos sintéticos en `LabelParserTest`.
- [x] Una preparación hecha con un café elegido en Scale queda vinculada al terminar, aparece en la
      bitácora del café y descuenta su dosis del stock.
- [x] Borrar una preparación, o cambiar su `doseG` en la base de datos, corrige el stock sin más
      acciones. La app no permite editar la dosis; el cambio de dosis se verifica a nivel de datos
      (`CoffeeDaoTest`).
- [x] El café agotado muestra "Agotado" y la sugerencia de archivar.
- [x] "Nueva bolsa" crea un café con los datos copiados salvo fechas y fotos.
- [x] La valoración y la nota de cata se editan en "Editar preparación" y se ven en el historial,
      en el detalle de la preparación y en la bitácora.
- [x] El filtro "Café" del historial usa el catálogo.
- [x] La tarjeta del café se comparte en 2 toques o menos desde su detalle; el resumen en Scale, el
      detalle de preparación y la comparación también se comparten, con el café al pie si lo hay.
- [x] La tarjeta del café no muestra precio ni stock; la imagen del detalle de preparación muestra
      la valoración y la nota de cata (3 líneas como máximo).
- [x] Borrar un café conserva el nombre en sus preparaciones y elimina sus archivos de fotos.

**Rendimiento** (emulador con API 36 o superior; una apertura previa de calentamiento que no cuenta
y la mediana de 5):
- [x] Con 200 cafés con foto, Cafés muestra la primera fila (`coffee_row`) en menos de 1 s.
- [x] El OCR de una foto de 1600 px termina en menos de 3 s.
- [x] La imagen para compartir se genera en menos de 1 s.

**Tests unitarios (JVM):**
- [x] `LabelParserTest`: fixtures con el texto (líneas y alturas) que da ML Kit en cada etiqueta
      real, versionados en `app/src/test/resources/labels/`, más casos sintéticos de fechas,
      proceso (incluido semilavado), variedades y altitud con valor único y con rango.
- [x] Lectura de fechas: `dd/mm`, `mm/dd` de reserva, mes en texto, años de 2 cifras y descarte de
      futuras.
- [x] `CoffeeValidator` (incluido el rango de altitud), grafía canónica de las sugerencias, stock,
      días desde el tueste y `newBagFrom`.
- [x] Aplicación del OCR al formulario: no pisa campos escritos y marca los rellenados.

**Tests instrumentados:**
- [x] `CoffeeDaoTest`: búsqueda, archivados, stock, `SET NULL` al borrar con el nombre conservado,
      y `lastUsedAtEpochMs`.
- [x] `HistoryMigrationTest`: migración de 2 a 3 conservando las preparaciones existentes.
- [x] Tests de UI: alta desde la galería, detalle con bitácora, selector de café en Scale con la
      báscula simulada, valoración en "Editar preparación", filtro del historial y que compartir
      lanza un `ACTION_SEND` con un `image/png`.
- [x] `CoffeePerformanceTest`: los tres objetivos de rendimiento. La foto del OCR es una etiqueta
      sintética generada por el test.

**Verificación del OCR con etiquetas reales:**
- [x] Las 15 fotos están en una carpeta local **fuera del repo** y se suben al emulador o al
      teléfono con `adb push`.
- [x] Un test instrumentado, excluido de la suite normal, pasa cada foto por ML Kit y guarda sus
      líneas y alturas como JSON en `app/src/test/resources/labels/`. Ese JSON se versiona y
      alimenta `LabelParserTest`, aunque contenga todo el texto de la etiqueta (son etiquetas
      comerciales). Las fotos no se versionan.
- [x] El mismo test compara lo que extrae el parser con un archivo de valores esperados (nombre y
      fecha de tueste por etiqueta, vacía si no la trae), que también vive fuera del repo, e
      informa de los aciertos (objetivos: nombre 12 de 15; ninguna fecha inventada, 13 de 13).

**E2E por adb** en el emulador con la báscula simulada, sin ARTEMIS:
- [x] Alta de un café con una foto real de la galería, revisión de lo leído y guardado.
- [x] Vertido simulado con el café elegido, valoración y nota, y comprobación de bitácora y stock.
- [x] Compartir la tarjeta del café y una preparación.

**Calidad:**
- [x] Lint y formato del proyecto sin avisos nuevos: `lintDebug` con 0 errores y 21 avisos, los mismos que antes de F8
      (2 de `android.media.ExifInterface`, decisión D25, y el resto versiones disponibles y recursos sin uso).

## 6. Preguntas abiertas

1. **Moneda del precio.** *Resuelto en la revisión: dólares (USD) de momento, sin selector de
   moneda (§3.1).*
2. **Permiso `INTERNET` de ML Kit.** El modelo incluido no necesita red, pero las dependencias de
   ML Kit pueden añadir `INTERNET` al manifiesto fusionado para su telemetría. *Resuelto en la
   revisión: se verifica en el plan y, si aparece, se decide allí si se elimina con
   `tools:node="remove"`.* **Resultado (F3, D29):** el manifiesto fusionado de ML Kit añadía `INTERNET`
   y `ACCESS_NETWORK_STATE`. Se elimina `INTERNET` con `tools:node="remove"` y se mantiene
   `ACCESS_NETWORK_STATE` (solo consulta el estado de la red). El OCR funciona igual en modo avión
   (E2E 13) y el registro de fallos queda vacío.
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
