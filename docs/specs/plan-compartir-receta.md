# Plan de implementación: SPEC-009 Compartir e importar recetas

| Campo | Valor |
|---|---|
| Spec | [`spec-compartir-receta.md`](spec-compartir-receta.md) (SPEC-009) |
| Estado | Aprobado el 2026-11-17 con las propuestas P1 a P6. Commits por fase autorizados. |
| Fecha | 2026-11-17 |
| Rama | `feat/share-recipe` desde `develop` (`e64330a`, que ya incluye el recorrido guiado de SPEC-008). |
| Módulos | `:app` (`:protocol` no cambia) |
| `MAX_URI_LENGTH` | **Provisional: 331** (QR de 432 px, versión 13). F0 no cumple el mínimo de 500: el plan se detuvo y la decisión es del usuario. Ver "Resultado de F0". |

El spec no trae fases, así que este plan las define. El orden va de dentro afuera. Primero, un spike en el dispositivo
(F0) que cierra los dos riesgos del §6 y fija `MAX_URI_LENGTH`. Si F0 falla, el plan se detiene y se consulta al
usuario. Después va la lógica pura en la JVM: el códec (F1), las reglas de importación (F2) y la resolución de molienda
(F3). Luego, la imagen (F4) y el flujo de compartir (F5). Por último, la importación: la vista previa con la navegación
común (F6), las entradas internas (F7) y las externas (F8). Cada capa se prueba antes de que la siguiente la use.

Lo que más condiciona el diseño es que **todas las entradas acaban en el mismo sitio**. El escáner, la galería,
`ACTION_SEND` y `ACTION_VIEW` producen un texto. Ese texto pasa por `routeImport` y llega a un único
`PendingRecipeImport` en memoria de la app. `TabsNavigation` lo consume y abre la vista previa cuando no hay sesión. Así,
el aplazamiento del §3.7 no es un camino aparte: es la misma cola, que solo se vacía con `hasSession == false` (D14).

El código real contradice o no cubre algunos puntos del spec. Se listan en el §1.3 con el ajuste mínimo que propone
este plan. Lo más importante:

- La app **no tiene una unidad de peso del usuario**. `WeightUnit` es la unidad de la báscula conectada, y las recetas
  se muestran siempre en gramos. La propuesta es usar gramos en la imagen y en la vista previa (D11, P1).
- `ShareCaptureHost` y `ShareFrame` miden siempre `SHARE_SIZE`. La tarjeta de 9:16 obliga a pasarles el tamaño (D10).
- `MainActivity` no tiene `launchMode`. Con la hoja de compartir, Android crearía una segunda instancia en la tarea de
  la otra app (D17).
- No hay detekt en el proyecto. Como en los planes anteriores, el control es `:app:lintDebug`.

## 1. Punto de partida

### 1.1 Se reutiliza

| Pieza | Uso |
|---|---|
| `share/ShareCapture.kt` (`ShareCaptureHost`, `ShareRequest`), `share/ShareFrame.kt`, `share/ShareImages.kt`, `share/ShareSender.kt`, `share/ShareLauncher.kt` (`rememberShareLauncher`), `share/ShareSpec.kt` (`SHARE_SIZE`, `SHARE_DENSITY`) | El marco, la captura fuera de pantalla con tema claro y densidad 3, el PNG en `cacheDir/shared/`, `FileProvider` y el selector con `ACTION_SEND` `image/png` (D30 de SPEC-004). Se amplían con el tamaño (D10) y con `Recipe` en `ShareRequest`, `ShareContent` y `ShareTarget`. |
| `share/ShareModels.kt` (`shareFileName`) | Se añade `ShareTarget.Recipe(id)` → `recipe-<id>.png`. |
| `recipe/Recipe.kt` (`Recipe`, `RecipeStage`, `StageType`, `withDerivedWater`, `totalWaterG`, `ratio`) | El modelo que el códec codifica y decodifica. |
| `recipe/RecipeValidator.kt` | La validación de lo decodificado (`Invalid`). |
| `recipe/RecipeRepository.kt` (`get`, `save`, `list`) | `get` ya devuelve los nombres mostrados (traducidos en las predefinidas sin renombrar), que es lo que pide el §3.1. `save` inserta con `id = 0`. `list` da los nombres mostrados para el sufijo. |
| `recipe/RecipeDao.kt` (`save` con `withLiveGrinder`) | Si el molino desaparece entre resolver y guardar, la fila se guarda sin molino y conserva la copia de texto (D18 de SPEC-006). |
| `grinder/GrindConverter.kt` (`convert`, `valueOrNull`), `grinder/GrindCalibration.kt` (`pointsBetween`), `grinder/GrindFormat.kt` (`label`, `format`, `SEPARATOR`), `grinder/GrindSetting.kt` (`GrindChoice.Live`, `toSetting`) | La conversión, el texto de la molienda y el ajuste que se guarda, igual que el editor de recetas. |
| `grinder/GrinderRepository.kt` (`owned`, `all`, `get`, `calibrations`) | Los molinos del receptor. `owned()` ya da el orden de la pantalla Molinos (marca, modelo, id). |
| `coffee/photo/PhotoSizing.kt` (`inSampleSize`, `scaledSize`) | La reducción a 2048 px de la imagen leída. |
| `coffee/label/MlKitLabelOcr.kt` | El patrón de ML Kit con el modelo incluido y `Closeable`. |
| `ui/recipes/RecipeFormatters.kt` (`formatGrams`, `formatRatioNumber`), `ui/recipes/RecipeEditForm.kt` (`formatStart`), `ui/session/SessionFormat.kt` (`stepTypeLabel`), `ui/history/HistoryFormatters.kt` (`BrewMethod.label()`), `ui/components/AntIcons.kt` (`Pour`, `Swirl`, `Wait`, `Share`) | Los textos y los iconos de la tarjeta y de la vista previa. |
| `ui/components/AntModal.kt`, `AntAlert.kt`, `AntSelect.kt`, `AntMessage.kt` (`AntMessageHost`, `showAntMessage`) | Los diálogos de compartir, los avisos y el selector de molienda de la vista previa, y los mensajes. |
| `Navigation.kt` (`TabsNavigation`, `tabEntry`, `TourRoot`), `TabNavigation.kt` (`showsBottomBar`, `popToRoot`), `Tabs.kt` (`Tab.RECIPES`) | Las pilas por pestaña. La sesión vive en la pila de Preparar y oculta la barra, así que durante una sesión no se llega a Recetas (§3.7). |
| `session/ScaleUiState.kt` (`hasSession`), `session/ScaleSessionManager.kt` (`startScalelessSession`, `discardSession`, `endSession`) | La condición de aplazamiento y, en los tests, abrir y cerrar una sesión sin báscula. |
| `androidTest/.../ui/TestAppDependencies.kt` (`TestApp`, `skipOnboarding`, `seedGrinders`), `androidTest/.../share/ShareCardsLayoutTest.kt`, `ShareIntentTest.kt`, `androidTest/.../grinder/TestGrinderCatalog.kt` | La base de los tests instrumentados. `skipOnboarding()` ya apaga los recorridos guiados. |
| `test/.../recipe/RecipeTestFixtures.kt` (`FakeRecipeTexts`, `v60Classic`, `stage`, `recipeOf`), `test/.../grinder/GrinderFixtures.kt` (`GrinderFixtures`, `FakeGrindTexts`) | La base de los tests JVM. |

### 1.2 Falta o hay que corregir

- No hay códec de recetas ni paquete `recipe/share`.
- No hay generación ni lectura de QR. Las dependencias del §3.8 no están en `libs.versions.toml`.
- `ShareCaptureHost` mide con `Constraints.fixed` a `SHARE_SIZE`, y `ShareFrame` hace `size(SHARE_SIZE)`.
- `RecipeEditScreen` no tiene acciones en la barra superior, ni `SnackbarHost`, ni `ShareLauncher`.
- `RecipesScreen` solo tiene el icono "Crear receta". No hay icono de importar en `AntIcons`.
- `MainActivity` no tiene `launchMode`, ni filtros de `ACTION_SEND` o `ACTION_VIEW`, ni `onNewIntent`.
- `AppDependencies` no tiene dónde guardar una importación pendiente ni un lector de QR.
- `RecipeRepository` no da las recetas completas (con pasos) para comparar. `GrinderRepository` no busca por
  `catalogKey` con sus rangos y anclas (`GrinderDao.byCatalogKey` devuelve solo la `GrinderEntity`).
- No hay un host de mensajes fuera de las pantallas. Cada `Scaffold` tiene el suyo.
- No hay detekt en el proyecto (ni en `build.gradle.kts` ni en `libs.versions.toml`). El control es `:app:lintDebug`.

### 1.3 Discrepancias entre el spec y el código

| # | Spec | Código real | Ajuste que propone el plan |
|---|---|---|---|
| 1 | §3.3: "Pesos en la unidad de peso de quien comparte (`WeightUnit`, igual que `BrewShareCard`)". §3.6 y §4: la vista previa en la unidad del receptor. | No hay preferencia de unidad. `WeightUnit` es la unidad que informa la báscula (`ScaleUiState.unit`, gramos sin báscula). `BrewShareCard` usa la unidad guardada con cada preparación. El editor y la lista de recetas usan siempre gramos (`RecipeEditScreen`: `val unit = WeightUnit.GRAMS`; `formatGrams`). | Gramos en la tarjeta y en la vista previa, como el resto de pantallas de recetas. La tarjeta y la vista previa reciben `unit: WeightUnit`, así que la alternativa es un cambio de una línea (D11, P1). |
| 2 | §3.3: 360 × 450 dp o 360 × 640 dp. | `ShareCaptureHost` y `ShareFrame` solo conocen `SHARE_SIZE`. | `ShareRequest.size` (por defecto `SHARE_SIZE`) y `ShareFrame(size = …)`. Las tres tarjetas actuales no cambian (D10). |
| 3 | §3.5: "Una sola instancia de `MainActivity`". | Sin `launchMode`: un `ACTION_SEND` desde otra app crea otra instancia en la tarea de esa app. | `android:launchMode="singleTask"` y `onNewIntent` (D17). |
| 4 | §3.6, caso 1: `GrindSetting(grinderId = suyo, value = v, label = null)`. | D17 de SPEC-006: al guardar, la copia de texto se calcula siempre con `GrindFormat.label` y el molino vivo (`RecipeEditViewModel.save` usa `GrindChoice.toSetting`). | Se guarda como el editor: `GrindChoice.Live(id, v).toSetting(molino, grindTexts)`, con la copia de texto. Si el molino se borra después, la receta conserva un texto reconocible (D20). |
| 5 | §3.6, caso 3: `"<marca> <modelo> · <ajuste>"`. | El QR no lleva las unidades del molino del autor. Si su molino no está en la base del receptor, no se puede dar formato al ajuste. | Con el molino del catálogo en la base del receptor, `GrindFormat.format`. Si no, el número con `GrindTexts.number` (0 decimales si es entero, 1 si no) (D20). |
| 6 | §3.5: `Tabs.RECIPES`. | El enum es `Tab` (archivo `Tabs.kt`). | `Tab.RECIPES`. |
| 7 | §3.6: "Se vuelve a la lista de Recetas". | Una importación externa puede llegar con un editor de receta abierto, con cambios sin guardar, en la pila de Recetas. | Guardar y Cancelar quitan solo la vista previa. En el caso normal (desde la lista) se vuelve a la lista. Con un editor debajo, se vuelve al editor sin perder sus cambios (D15, P4). |
| 8 | §4: "QR de otra cosa → Este QR no es una receta de Pourline". | Desde una imagen solo se toma el QR que empieza por `pourline://recipe` (§3.5), y el filtro de `ACTION_VIEW` solo acepta `pourline://recipe`. | `NotPourline` solo llega desde el escáner o desde un `pourline://recipe` sin `d`. En las imágenes, cualquier QR ajeno da "No se encontró ninguna receta en la imagen", como dice el propio §4. El test del QR ajeno va por el escáner (F7). |
| 9 | §3.1: un URI con `v` y `d`. | El spec no dice qué pasa sin `v` o con `v` no numérico. | `Corrupt` (D4). |
| 10 | §5: "Unitario: el paquete `recipe.share` no importa `android.*`", "como en `history`". | En SPEC-002 fue un `grep` de la verificación, no un test. | Un test JVM que recorre `src/main/kotlin/.../recipe/share` (como `GrinderCatalogFileTest` lee `src/main/assets`), más el `grep` de la verificación final. |
| 11 | §5: "Detekt y los tests existentes pasan". | No hay detekt. | `:app:lintDebug` sin errores ni avisos nuevos, como en los planes anteriores. |
| 12 | §3.5: `MainActivity` declara `ACTION_SEND` `image/*`. | El selector que abre `ShareImages.chooser` (cafés, preparaciones, comparaciones y ahora recetas) ofrecería Pourline como destino de sus propias imágenes. | `EXTRA_EXCLUDE_COMPONENTS` con `MainActivity` en el selector (D13, P3). |
| 13 | §3.4: diálogos con texto y botones. | `AntModal` exige un título. | Título "Compartir receta" en los dos diálogos (D24). |

## 2. Decisiones que completan el spec

Marcadas como "(concreta)" si eligen entre opciones que el spec deja abiertas, y como "(amplía)" si añaden algo que el
spec no dice.

### 2.1 Formato y códec

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Archivos del paquete (concreta §3.2) | `recipe/share/SharedRecipe.kt` (nuevo): `EncodedRecipe`, `TrimmedPart`, `SharedRecipe`, `SharedGrind` y `DecodeResult`, con las firmas exactas del §3.2. `recipe/share/RecipeSharePayload.kt` (nuevo): los DTO internos `PayloadV1`, `GrindV1` y `StageV1`, `@Serializable` con `@SerialName` cortos (`n`, `m`, `mo`, `t`, `no`, `d`, `g`, `s`, `x`; `k`, `b`, `md`, `v`, `l`; `y`, `n`, `a`, `u`, `w`, `i`). `recipe/share/RecipeShareCodec.kt` (nuevo): `object RecipeShareCodec` con `const val VERSION = 1`, `const val SCHEME = "pourline"`, `const val HOST = "recipe"`, `const val URI_PREFIX = "pourline://recipe"`, `const val MAX_URI_LENGTH` (valor de F0), `internal const val MAX_INFLATED_BYTES = 65_536`, `fun encode(recipe: Recipe, grinder: Grinder?): EncodedRecipe?`, `internal fun encode(recipe: Recipe, grinder: Grinder?, maxLength: Int): EncodedRecipe?` y `fun decode(text: String): DecodeResult`. | La sobrecarga con `maxLength` deja probar el orden del recorte con topes pequeños, sin depender del valor real. Los DTO quedan `internal`: el contrato público es el del §3.2. |
| D2 | JSON (concreta §3.1) | kotlinx-serialization (ya en el proyecto) con `Json { explicitNulls = false; encodeDefaults = false; ignoreUnknownKeys = false; isLenient = false; allowSpecialFloatingPointValues = false }`. Los opcionales son `T? = null` y no se escriben si faltan. `m`, `y` y los elementos de `x` van como `String` y se traducen a mano: un valor desconocido es `Corrupt`. `a` y `u` son `Long` (un decimal es `Corrupt`). `d`, `t`, `w` y `v` son `Double`. | `ignoreUnknownKeys = false` da exactamente "una clave desconocida es un error de formato", también dentro de `g` y de `s[i]`. Traducir los enums a mano evita que un `BrewMethod` nuevo se cuele sin subir `v`. |
| D3 | Compresión y base64 (concreta §3.1) | `java.util.zip.Deflater(9, true)` (raw) y `Inflater(true)`, con el byte de relleno que pide `nowrap` al inflar. `java.util.Base64.getUrlEncoder().withoutPadding()` y `getUrlDecoder()`. Al inflar, más de `MAX_INFLATED_BYTES` es `Corrupt`. El JSON se lee como UTF-8 con `CodingErrorAction.REPORT`. | Todo es JVM puro y existe desde API 26 (`minSdk`). El tope evita una bomba de descompresión: un QR de 1 KB no puede producir más de unos pocos KB de receta legítima. |
| D4 | Lectura del URI y orden de los errores (concreta §3.2 y §4) | `decode` hace `trim()` y usa `java.net.URI`. Un texto que no es URI, un esquema distinto de `pourline` o un host distinto de `recipe` (sin distinguir mayúsculas) dan `NotPourline`. Sin `d`, o con `d` vacío, también. Los parámetros se leen de `rawQuery`; los desconocidos se ignoran. Sin `v`, `v` no entero o `v < 1`: `Corrupt`. `v > VERSION`: `NewerVersion`, **antes** de mirar `d`. Después, base64, inflado, UTF-8 y JSON: `Corrupt`. Un campo obligatorio ausente (`n`, `d`, `s`, y en cada paso `y`, `a` y `u`, y `w` en `"P"`) es `Corrupt`. La receta construida que no pasa `RecipeValidator` es `Invalid`. | Una versión futura puede cambiar la codificación de `d`, así que se rechaza sin leerlo. Un campo obligatorio ausente es un error de formato, no una receta inválida. |
| D5 | Campos fuera de sitio (concreta §3.1) | `mo` solo se usa con `m = "OTHER"`; con otro método se ignora (el repositorio ya lo descarta al guardar). `w` en `"S"` o `"W"` se ignora: `withDerivedWater()` lo fija. Un `n` o un `i` vacíos o en blanco se leen como `null`. `encode` ordena los pasos por `startMs` y no escribe `n` ni `i` nulos o en blanco. | Son claves conocidas con un valor que el modelo ya sabe derivar. Rechazarlas no protege nada. |
| D6 | Recorte (concreta §3.2) | Cada paso del recorte quita lo suyo **acumulado** sobre el anterior, y solo cuenta si había algo que quitar. `x` y `trimmed` listan lo que se quitó de verdad: una receta sin notas que necesita quitar instrucciones da `trimmed = {INSTRUCTIONS}`. El tope se mide sobre el URI completo, con `x` incluido. Pasos, tiempos, agua, dosis, temperatura, método y molienda nunca se tocan. `encode` devuelve `null` si ni el paso 4 cabe. | Anotar "las notas" cuando no había notas confundiría al receptor. |
| D7 | Molienda al codificar (concreta §3.1) | Sin `grind` o con `isEmpty`: sin `g`. Con `grinderId` y el molino vivo: `k` (si tiene `catalogKey`), `b`, `md` y `v` (si hay ajuste). Con `grinderId = null`: solo `l`. Con `grinderId` pero sin molino (borrado entre leer y compartir): como eliminado. Si `g` quedaría vacío (eliminado sin `label`), no se escribe. | El §3.1 no prevé un `g` vacío, y decodificarlo daría un `SharedGrind` sin nada útil. |
| D8 | `MAX_URI_LENGTH` (cierra §3.2 y §6) | Lo fija F0 con `QrLegibilityTest`: para cada longitud de 300 a 1 000 caracteres, de 25 en 25, cinco URI con un `d` aleatorio (semilla fija) se dibujan como en la tarjeta y se leen con ML Kit tal cual y tras reducir al 50 % y recomprimir en JPEG calidad 70. `Vmax` es la versión de QR más alta en la que pasan todas las muestras de todas sus longitudes. `MAX_URI_LENGTH` es la capacidad en modo byte con corrección M de `Vmax − 1` (una versión de margen). Mínimo exigido: 500. F1 confirma con el códec real que la receta de referencia de 20 pasos sin textos cabe (§6). | Legibilidad depende de la versión del QR (del número de módulos), no de la longitud exacta. La versión de margen cubre lo que el test sintético no mide: una cámara que lee otra pantalla (US-3). La estimación con el esquema del §3.1 da unos 475 caracteres para 20 pasos sin textos, con nombre de 60 y molienda. |
| D23 | Contrato del formato (amplía) | `RecipeShareCodecTest` guarda como constante un URI `v=1` de la V60 clásica (`GOLDEN_V60_URI`), generado una vez en F1 y congelado. El test comprueba que **se decodifica** a la receta esperada. No se compara la salida de `encode` byte a byte. La verificación por adb (§7.4) usa ese mismo URI. | El §3.1 es un contrato entre versiones de la app: una app futura tiene que leer los QR ya compartidos. La salida de `Deflater` puede variar entre versiones de zlib (JVM y Android), así que comparar bytes daría falsos fallos. |

### 2.2 QR, tarjeta y compartir

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D9 | Generación y dibujo del QR (concreta §3.1 y §3.3) | `share/QrCode.kt` (nuevo, sin `android.*`): `class QrMatrix(val modules: Int, private val dark: BooleanArray)` con `operator fun get(x: Int, y: Int): Boolean`, `const val QR_QUIET_ZONE = 4`, `fun qrMatrix(text: String): QrMatrix` con `com.google.zxing.qrcode.encoder.Encoder.encode(text, ErrorCorrectionLevel.M)` **sin** `CHARACTER_SET` (el URI es ASCII: modo byte sin ECI), `fun qrVersion(text: String): Int` y `fun qrCellEdge(index: Int, cells: Int, sizePx: Int): Int = (index.toLong() * sizePx / cells).toInt()`. La caja mide `SHARE_QR_SIZE = 144.dp` (432 px) e **incluye** la zona de silencio: `cells = modules + 2 * QR_QUIET_ZONE`. El módulo `(x, y)` se pinta de `qrCellEdge(x + 4)` a `qrCellEdge(x + 5)`, en píxeles enteros, con `Color.Black` sobre `Color.White` fijos (no con los colores del tema). | Sin ECI lo lee cualquier lector. Con bordes en píxeles enteros no hay antialias entre módulos. La caja de 144 dp cumple el "al menos el 40 % del ancho" sin agrandar la tarjeta. La misma geometría la usan la tarjeta (Compose) y el test de F0 (`android.graphics.Canvas`). |
| D10 | Tamaño y maqueta de la tarjeta (concreta §3.3) | En `ShareSpec.kt`: `val SHARE_SIZE_TALL = DpSize(360.dp, 640.dp)`, `val SHARE_QR_SIZE = 144.dp`, `val RECIPE_STEP_ROW_HEIGHT = 16.dp`, `const val RECIPE_CARD_COMPACT_MAX_STAGES = 10` y `fun recipeCardSize(stageCount: Int): DpSize` (4:5 hasta ese número de pasos, 9:16 a partir del siguiente). `ShareRequest` gana `val size: DpSize get() = SHARE_SIZE`, que `Recipe` sobrescribe, y `ShareCaptureHost` mide con `request.size`. `ShareFrame` gana `size: DpSize = SHARE_SIZE`. Maqueta del cuerpo (382 dp en 4:5 y 572 dp en 9:16, tras el marco de 68 dp): el nombre en `heading5`, 2 líneas como máximo (48); hueco de 8; una fila de 144 dp con, a la izquierda, el método, una línea "dosis · agua · ratio", la temperatura, la molienda (2 líneas como máximo) y el texto del QR, y a la derecha el QR; hueco de 12; una fila por paso de 16 dp (`textSM` con `lineHeight = 16.sp`, una línea, elipsis en el nombre) con el inicio (`m:ss`), el icono y el nombre del tipo, el nombre del paso, la duración y el agua acumulada (solo en `POUR`). Fijo: 212 dp. Quedan 170 dp (10 filas) en 4:5 y 360 dp (22 filas) en 9:16. Sin QR, la columna de datos ocupa todo el ancho y el umbral no cambia. | Las filas de alto fijo hacen que el tamaño dependa solo del número de pasos, sin medir antes de dibujar. `ShareCaptureHost` fija `fontScale = 1f`, así que la cuenta es determinista. El test de maqueta de F4 confirma el umbral; si 10 no caben, se baja la constante al valor medido. |
| D11 | Unidad de peso (discrepancia 1; P1) | `RecipeShareData.unit` y `RecipeImportScreen(unit = …)` existen, y los dos llamadores pasan `WeightUnit.GRAMS`. Con onzas, los pesos se formatean con `fromGrams` y 2 decimales, como `formatNumber`. El QR va siempre en gramos. | Las recetas se ven en gramos en toda la app. Usar la unidad de la báscula conectada haría que la misma receta salga en gramos u onzas según haya o no báscula. |
| D12 | Compartir desde el editor (concreta §3.4) | `RecipeEditViewModel` gana `val share: StateFlow<RecipeSharePrompt?>`, `fun prepareShare()` y `fun clearShare()`, con `data class RecipeSharePrompt(val id: Long, val data: RecipeShareData, val trimmed: Set<TrimmedPart>, val withoutQr: Boolean)` y `val needsConfirmation get() = withoutQr \|\| trimmed.isNotEmpty()`. `prepareShare` solo actúa con `isExisting`, `Ready` y sin cambios (`!form.isDirty(initial)`). En `Dispatchers.Default`: vuelve a leer la receta con `repository.get(id)` (la **guardada**), lee su molino con `grinderRepository.get`, llama a `RecipeShareCodec.encode` y, si hay URI, a `qrMatrix`. El texto de la molienda es `GrindFormat.label(molino, value, grindTexts)` con molino vivo y `grind.label` si fue eliminado. `RecipeEditScreen` gana `shareSender: ShareSender = rememberShareSender()`, un `SnackbarHostState` con `AntMessageHost`, `rememberShareLauncher` y `launcher.Host()`. El icono `AntIcons.Share` (`testTag("recipe_edit_share")`) va en `actions`, siempre visible y habilitado solo con `isExisting && ready && !dirty`. Sin confirmación, comparte directamente. Con ella, un `AntModal`. | Releer la receta garantiza que se comparte lo guardado. El icono visible pero deshabilitado en una receta nueva o con cambios es lo que pide el §3.4. El aviso de error de la imagen ya lo da `ShareLauncher` (§4). |
| D13 | El selector no ofrece Pourline (amplía; P3) | `ShareImages.chooser` añade `Intent.EXTRA_EXCLUDE_COMPONENTS` con `ComponentName(context.packageName, "dev.josueamaya.pourline.MainActivity")`. | Con el filtro de `ACTION_SEND` `image/*`, Pourline saldría en su propio selector al compartir cualquier imagen. Es un extra más en el `Intent`: `ShareIntentTest` no cambia. |

### 2.3 Importación, navegación y avisos

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D14 | Un solo camino para todas las entradas (concreta §3.5 y §3.7) | `recipe/share/ImportRouting.kt` (nuevo, puro): `enum class ImportSource { SCANNER, IMAGE, LINK }`, `enum class ImportError { NOT_POURLINE, NO_RECIPE_IN_IMAGE, NEWER_VERSION, CORRUPT, INVALID, SCANNER_UNAVAILABLE }`, `sealed interface ImportOutcome { Open(uri), Defer(uri), Fail(error) }` y `fun routeImport(text: String?, source: ImportSource, hasSession: Boolean): ImportOutcome`. Con `IMAGE`, un texto `null` o `NotPourline` dan `NO_RECIPE_IN_IMAGE`. `recipe/share/PendingRecipeImport.kt` (nuevo, puro): `class PendingRecipeImport` con `val pending: StateFlow<String?>`, `val notices: Flow<ImportNotice>` (un `Channel` con búfer), `fun submit(text: String?, source: ImportSource, hasSession: Boolean): ImportOutcome`, `fun consume(uri: String): Boolean` (`compareAndSet`) y `fun notify(notice: ImportNotice)`, con `sealed interface ImportNotice { Failed(error), Deferred, Added }`. `Open` y `Defer` ponen el URI en `pending` (el último reemplaza al anterior); `Defer` emite además `Deferred`; `Fail`, `Failed(error)`. Va en `AppDependencies.recipeImports`. | Es el `PendingRecipeImport` del §3.7, y además la cola de las entradas sin sesión. Con una sola cola, el aplazamiento sale gratis y se prueba en la JVM. |
| D15 | Navegación a la vista previa (concreta §3.6) | Nueva clave `@Serializable data class RecipeImport(val uri: String) : NavKey` en `NavigationKeys.kt`. `TabsNavigation` observa `recipeImports.pending` y `hasSession`, y cuando hay pendiente **y** no hay sesión: `tab = Tab.RECIPES`, `stacks.getValue(Tab.RECIPES).openImport(uri)` y `consume(uri)`. `fun MutableList<NavKey>.openImport(uri: String)` (nueva, en `TabNavigation.kt`) sustituye la cima si ya es un `RecipeImport` y si no, apila. `showsBottomBar` oculta la barra con `RecipeImport` en la cima, como en los editores. Guardar y Cancelar llaman a `back()` (quitan solo la vista previa). | Sustituir la cima evita apilar vistas previas si llegan dos recetas seguidas. Ocultar la barra impide dejar una vista previa a medias al cambiar de pestaña. Quitar solo la vista previa no descarta un editor con cambios que hubiera debajo (discrepancia 7). |
| D16 | Antes del primer arranque (amplía) | Si el onboarding no está hecho, `TabsNavigation` no está compuesta y el pendiente espera. Al terminar el onboarding se abre la vista previa. | El spec no lo cubre. Esperar es lo mismo que hace con una sesión. |
| D21 | Mensajes de importación (concreta §3.5, §3.6 y §4) | Un `AntMessageHost` propio en `TabsNavigation`, dentro de `TourRoot`, sobre el `NavDisplay` y alineado abajo (con `navigationBarsPadding()` cuando no hay barra). Muestra los `notices` de `PendingRecipeImport`: `Added` (Success), `Deferred` (Info) y `Failed` (Error). `ui/recipes/ImportNoticeText.kt` (nuevo): `fun importNoticeText(notice: ImportNotice): Int` y `fun importNoticeStatus(notice: ImportNotice): AntStatus`. | Las entradas externas no tienen pantalla propia, y "Receta recibida…" tiene que verse encima de la sesión. Un único host para todos los mensajes de importación (también "Receta añadida" y los errores del escáner) evita repartirlos entre pantallas. |
| D22 | Vista previa (concreta §3.6) | `ui/recipes/RecipeImportViewModel.kt` (nuevo): `RecipeImportViewModel(uri: String, recipes: RecipeRepository, grinders: GrinderRepository, texts: GrindTexts, imports: PendingRecipeImport)` con `val ui: StateFlow<RecipeImportUi>`, `fun pick(choice: GrindPick)` y `fun save()`. `RecipeImportUi`: `Loading`, `Ready(shared, identical, grind: GrindResolution, pick: GrindPick?, saving)`, `Saved` y `Failed(error)`. Al cargar: `decode(uri)` (si no es `Ok`, notifica el error y queda `Failed`), `recipes.all()` para `identical` y la resolución de molienda de D20. Al guardar: vuelve a leer los molinos y a resolver (§4: "se vuelve a resolver"); si el molino elegido ya no está entre las opciones, guarda como texto; calcula el nombre con `uniqueName` sobre `recipes.list().first()`; `recipes.save(...)`; notifica `Added`; `Saved`. Nuevos: `RecipeRepository.all(): List<Recipe>` (con `dao.observeAll().first()` y el mismo `toRecipe`), `GrinderDao.getByCatalogKey(key): GrinderWithDetails?` (`@Transaction`) y `GrinderRepository.byCatalogKey(key: String): Grinder?`. | Volver a decodificar el URI de la clave hace que la pantalla sobreviva a la recreación sin guardar nada más. Comparar con `all()` usa los nombres mostrados, igual que la lista. |
| D25 | Pantalla de la vista previa (concreta §3.6) | `ui/recipes/RecipeImportScreen.kt` (nuevo): `RecipeImportScreen(viewModel: RecipeImportViewModel, onDone: () -> Unit, unit: WeightUnit = WeightUnit.GRAMS)`. Barra superior con "Importar receta" y la flecha de atrás (= Cancelar). Arriba, los avisos: `AntAlert` Warning con `recipe_import_trimmed` (`testTag("recipe_import_trimmed")`) y `AntAlert` Info con `recipe_import_identical` (`testTag("recipe_import_identical")`). Después, el nombre, el método, los datos, la molienda, las notas y los pasos con su instrucción. La molienda: `Linked` y `Text` como texto fijo (`recipe_import_grind_text`); `Convertible` como `AntSelect` (`testTag = "recipe_import_grind"`) con "Usar con <marca modelo> · <ajuste>" por opción y "Guardar como texto". Abajo, "Guardar" (primario, `recipe_import_save`) y "Cancelar" (`recipe_import_cancel`). | Mismo contenido que la tarjeta, más lo que la tarjeta no lleva (notas e instrucciones). |

### 2.4 Molienda

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D20 | Resolución (concreta §3.6; amplía en dos puntos; P2) | `grinder/SharedGrindResolution.kt` (nuevo, puro): `data class GrindOption(val grinder: Grinder, val value: Double, val rounded: Boolean)`; `sealed interface GrindResolution { None; Linked(grinder: Grinder, value: Double?); Convertible(options: List<GrindOption>, textLabel: String); Text(label: String) }`; `sealed interface GrindPick { Use(grinderId: Long); AsText }`; `fun resolveSharedGrind(shared: SharedGrind?, owned: List<Grinder>, catalogGrinder: Grinder?, calibrations: List<GrindCalibration>, texts: GrindTexts): GrindResolution`; `fun GrindResolution.defaultPick(): GrindPick?` (el primer `Use` en `Convertible`); `fun GrindResolution.settingFor(pick: GrindPick?, owned: List<Grinder>, texts: GrindTexts): GrindSetting?`; `fun sharedGrindText(shared: SharedGrind, catalogGrinder: Grinder?, texts: GrindTexts): String?`. `owned` es `GrinderRepository.owned()` y `catalogGrinder`, `byCatalogKey(shared.catalogKey)`. **Caso 1:** entre `owned.filter { !it.retired }`, el primero con el mismo `catalogKey`; sin `k`, el primero con marca y modelo iguales (`trim`, sin distinguir mayúsculas). **Amplía:** si el emparejamiento es por marca y modelo y `v` cae fuera de `scaleMin..scaleMax` de ese molino, se pasa al caso 3. **Caso 2:** con `k`, `catalogGrinder` y `v`: `GrindConverter.convert(v, catalogGrinder, destino, calibrations.pointsBetween(catalogGrinder.id, destino.id))` para cada molino de `owned` no retirado y distinto del de origen, en ese orden; se quedan los que no dan `NoEquivalent`; `rounded` si la razón incluye `ROUNDED`. Sin opciones, caso 3. **Caso 3:** `l` tal cual; si no, `"<marca> <modelo>"` y, con `v`, `GrindFormat.SEPARATOR` y el ajuste (`GrindFormat.format` con `catalogGrinder` si existe; si no, `texts.number(v, 0 o 1)`). **Amplía:** los molinos retirados no son destino de la conversión. Sin texto posible (sin `l`, `b` ni `md`), `None`. **Ajuste guardado:** `Linked` y `Use` → `GrindChoice.Live(id, valor).toSetting(molino, texts)` (con copia de texto, discrepancia 4); `Text` y `AsText` → `GrindSetting(null, null, label)`; `None` → `null`. | El caso 1 excluye retirados (§3.6); hacer lo mismo con la conversión es coherente con el §4 ("si el molino ya no es válido, se guarda como texto"). Un molino propio del autor puede tener otra escala que el del receptor con la misma marca y modelo: con un valor fuera de escala, enlazarlo daría un ajuste imposible. En pantalla, una opción `rounded` lleva "≈ " delante, como el convertidor (`GrindConverterScreen`). |
| D26 | Identidad y nombre (concreta §3.6) | `recipe/share/RecipeImportRules.kt` (nuevo, puro): `internal fun nameKey(name: String) = name.trim().lowercase(Locale.ROOT)`; `fun isIdentical(a: Recipe, b: Recipe): Boolean` (nombre por `nameKey`, `doseG`, `method`, `waterTempC` y los pasos uno a uno: `type`, nombre e instrucción normalizados con `trim` y vacío como `null`, `startMs`, `durationMs` y `targetWaterG` solo en `POUR`); `fun uniqueName(name: String, existing: Collection<String>): String` (el nombre recortado si ninguno coincide por `nameKey`; si no, `" (2)"`, `" (3)"`… hasta que no coincida). | Exactamente la lista del §3.6. Las notas, `methodOther` y la molienda no cuentan. La normalización de los nombres de paso replica la del repositorio al guardar (`cleaned()`), así que una receta guardada y su QR comparan igual. |

### 2.5 Entradas

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D17 | `MainActivity` (concreta §3.5) | Manifiesto: `android:launchMode="singleTask"`, un filtro `SEND` + `DEFAULT` + `mimeType="image/*"` y otro `VIEW` + `DEFAULT` + `BROWSABLE` + `scheme="pourline"` `host="recipe"`. `RecipeIntents.kt` (nuevo, paquete raíz): `class RecipeIntentHandler(imports: PendingRecipeImport, reader: QrImageReader, hasSession: () -> Boolean, scope: CoroutineScope)` con `fun handle(intent: Intent): Boolean`, y `fun shouldHandleLaunchIntent(recreated: Boolean, intentFlags: Int): Boolean` (falso si se recrea o si lleva `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`). `VIEW` con esquema `pourline` → `submit(dataString, LINK, hasSession())`. `SEND` con `type` `image/*` → el `Uri` de `EXTRA_STREAM` (`IntentCompat.getParcelableExtra`) o del primer elemento de `clipData`; sin `Uri`, `submit(null, IMAGE, …)`; con `Uri`, `scope.launch { reader.read(uri) }` y después `submit` en el hilo principal. El resto de intents devuelve `false`. `MainActivity.onCreate` lo llama si `shouldHandleLaunchIntent(savedInstanceState != null, intent.flags)`, y `onNewIntent` siempre (con `setIntent`). El ámbito es `PourlineApp.applicationScope`. | `singleTask` garantiza una sola instancia y entrega los intents a `onNewIntent`. Con `savedInstanceState`, la rotación y la restauración del proceso no repiten la importación. La bandera de historial cubre abrir la app desde Recientes cuando la tarea nació de un `ACTION_SEND`. Leer la imagen en el ámbito de la app sobrevive a una rotación a mitad. |
| D18 | Lectura desde imagen (concreta §3.5) | `share/QrImageReader.kt` (nuevo): `fun interface QrImageReader { suspend fun read(uri: Uri): String? }` y `class MlKitQrImageReader(context: Context) : QrImageReader, Closeable`, con `BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())` (perezoso), `suspend fun readBitmap(bitmap: Bitmap): List<String>` (todos los `rawValue`) y `read(uri)`: límites con `inJustDecodeBounds`, `inSampleSize(…, QR_IMAGE_MAX_SIDE)`, reducción con `scaledSize` a `QR_IMAGE_MAX_SIDE = 2048`, y el primer valor que empieza por `RecipeShareCodec.URI_PREFIX`. `SecurityException`, `IOException`, `OutOfMemoryError`, `RuntimeException` o un bitmap nulo dan `null`. Sin EXIF. Va en `AppDependencies.qrImageReader`. | Un QR se lee en cualquier orientación, así que no hace falta girar. `null` es "No se encontró ninguna receta en la imagen" (§4), también sin permiso de lectura. |
| D19 | Escáner (concreta §3.5) | `ui/recipes/QrScanner.kt` (nuevo): `sealed interface ScanResult { Scanned(text: String); Cancelled; Unavailable }`, `fun interface QrScanner { suspend fun scan(): ScanResult }`, `val LocalQrScanner = staticCompositionLocalOf<QrScanner?> { null }` y `@Composable fun rememberQrScanner(): QrScanner` (`LocalQrScanner.current` o el de Google). El de Google: `GmsBarcodeScanning.getClient(context, GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE)…build()).startScan()` con un `suspendCancellableCoroutine` propio: éxito → `Scanned(rawValue.orEmpty())`; cancelado → `Cancelled`; fallo → `Unavailable`. Con `enableAutoZoom()` si la versión de F0 lo tiene. | El `await()` de `coffee/label/TaskAwait.kt` convierte la cancelación en `CancellationException`, y aquí cancelar es un resultado normal ("no pasa nada"). El `CompositionLocal` deja poner un escáner falso en los tests sin tocar `AppDependencies`. |
| D27 | Entradas internas (concreta §3.5) | `RecipesScreen` gana `importer: RecipeImportEntryViewModel? = null`. Con `importer`, un `AntIconButton(AntIcons.Import, …)` (`testTag("recipes_import")`, `loading = busy`) antes de "Crear receta", que abre un `DropdownMenu` (como `CoffeePhotoSlot`) con "Escanear QR" (`recipes_import_scan`) y "Desde una imagen" (`recipes_import_image`). `ui/recipes/RecipeImportEntryViewModel.kt` (nuevo): `RecipeImportEntryViewModel(imports: PendingRecipeImport, reader: QrImageReader, hasSession: () -> Boolean)` con `val busy: StateFlow<Boolean>`, `fun onScan(result: ScanResult)` (`Scanned` → `submit(text, SCANNER, …)`; `Cancelled` → nada; `Unavailable` → `notify(Failed(SCANNER_UNAVAILABLE))`) y `fun onImage(uri: Uri?)` (`null` → nada; si no, `reader.read` y `submit(text, IMAGE, …)`). La galería usa `rememberLauncherForActivityResult(PickVisualMedia())` con `ImageOnly`. `AntIcons.Import` (nuevo): una bandeja con una flecha hacia abajo. | Con `importer` opcional, `RecipesScreenTest` no cambia. El ViewModel vive en la entrada de navegación de `Recipes`, que le pasa `deps.recipeImports` y `deps.qrImageReader`. |

### 2.6 Pruebas, textos y verificación

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D24 | Cadenas (concreta §3.4, §3.6 y §4) | Nuevas en `values` y `values-en`. Compartir: `recipe_share_action` ("Compartir receta" / "Share recipe"), `recipe_share_dialog_title` ("Compartir receta"), `recipe_share_trimmed_text` ("Esta receta es muy larga para el QR. Al importarla faltarán %1$s." / "This recipe is too long for the QR code. When imported, it will be missing %1$s."), `recipe_share_too_long_text` (texto del §3.4 / "This recipe is too long to import with a QR code. Only the image will be shared."), `recipe_share_confirm` ("Compartir"), `recipe_share_cancel` ("Cancelar"), `recipe_trimmed_notes` ("las notas" / "the notes"), `recipe_trimmed_instructions` ("las instrucciones de los pasos" / "the step instructions"), `recipe_trimmed_stage_names` ("los nombres de los pasos" / "the step names"), `recipe_trimmed_list_two` ("%1$s y %2$s"), `recipe_trimmed_list_three` ("%1$s, %2$s y %3$s"). Tarjeta: `recipe_card_qr_caption` ("Escanéalo con Pourline para añadir esta receta" / "Scan it with Pourline to add this recipe"), `recipe_card_dose`, `recipe_card_water`, `recipe_card_ratio`, `recipe_card_temp`, `recipe_card_grind`, `recipe_card_step_duration` ("%1$d s"). Importar: `recipe_import_action` ("Importar receta"), `recipe_import_scan` ("Escanear QR"), `recipe_import_from_image` ("Desde una imagen"), `recipe_import_title` ("Importar receta"), `recipe_import_save`, `recipe_import_cancel`, `recipe_import_trimmed` ("La receta original tenía %1$s que no cupieron en el QR."), `recipe_import_identical` ("Ya tienes esta receta"), `recipe_import_notes` ("Notas"), `recipe_import_grind` ("Molienda"), `recipe_import_grind_use` ("Usar con %1$s · %2$s"), `recipe_import_grind_as_text` ("Guardar como texto"). Mensajes (texto del spec): `recipe_import_added`, `recipe_import_deferred`, `recipe_import_not_pourline`, `recipe_import_no_recipe`, `recipe_import_newer`, `recipe_import_corrupt`, `recipe_import_invalid` y `recipe_import_scanner_unavailable`. | El proyecto define cada texto por contexto aunque se repita ("Cancelar" ya existe varias veces). Los dos títulos que el spec no da se proponen aquí (discrepancia 13). |
| D28 | Intents en los tests (concreta §5) | Los tests instrumentados de `ACTION_VIEW` y `ACTION_SEND` montan `TestApp(env)` y construyen `RecipeIntentHandler` con `env.dependencies.recipeImports`, `env.dependencies.qrImageReader` y la sesión de `env`, y le pasan `Intent` reales (el `ACTION_SEND` con un PNG generado por la tarjeta y servido por `FileProvider`). El pegamento de `MainActivity` (`singleTask`, `onNewIntent`, recreación) se prueba con `ManifestPermissionsTest` y por adb (§7.4). | Lanzar la `MainActivity` real usaría `PourlineApp.dependencies`: el `history.db` y las preferencias reales, que los tests nunca tocan (D32 de SPEC-005). |
| D29 | Guardia del manifiesto (amplía) | `androidTest/.../ManifestPermissionsTest.kt` (nuevo): `requestedPermissions` del paquete no contiene `INTERNET` ni `CAMERA`; `queryIntentActivities` resuelve `VIEW pourline://recipe?...` y `SEND image/png` a `MainActivity`; y su `ActivityInfo.launchMode` es `LAUNCH_SINGLE_TASK`. | Una dependencia futura podría reintroducir `INTERNET`. Con el test, la regresión se ve en la suite y no solo en una revisión manual. |

## 3. Fases

Orden: F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → F9. F2 y F3 dependen de F1 y no dependen entre sí. F4 depende de F0
(QR) y F5 de F1 y F4. F6 depende de F1, F2 y F3. F7 y F8 dependen de F6.

| Fase | Contenido | Tamaño | Estado |
|---|---|---|---|
| F0 | Spike en el dispositivo: línea base, dependencias, manifiesto sin `INTERNET`, Code Scanner y `MAX_URI_LENGTH` | M | Hecha en parte: detenida, `MAX_URI_LENGTH < 500` y escáner sin verificar |
| F1 | Códec JVM puro: `RecipeShareCodec`, recorte y errores | M | Pendiente |
| F2 | Reglas de importación JVM: idéntica, sufijo, `routeImport` y `PendingRecipeImport` | S | Pendiente |
| F3 | Resolución de molienda JVM | S | Pendiente |
| F4 | Tarjeta de receta con QR y tamaño variable | M | Pendiente |
| F5 | Compartir desde el editor | M | Pendiente |
| F6 | Vista previa, guardado, navegación común y mensajes | L | Pendiente |
| F7 | Entradas internas: escáner y galería | M | Pendiente |
| F8 | Entradas externas: `ACTION_SEND`, `ACTION_VIEW`, `singleTask` y aplazamiento | M | Pendiente |
| F9 | Verificación: suites, lint, manifiesto, E2E por adb, prueba con dos móviles y capturas | M | Pendiente |

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app`, nunca con
`./gradlew build` ni con builds limpios.

- **Tests instrumentados:** siempre con `ANDROID_SERIAL=emulator-5556` (AVD `SG_API34`, Google Play, en `en-US`). El
  `emulator-5554` es del usuario y no se usa. El teléfono físico conectado (`22081212UG`, Android 15, por adb
  inalámbrico) solo se usa si el usuario lo autoriza (P5). Nunca dos suites a la vez en `emulator-5556`: una ejecución
  paralela desde otro worktree ya tumbó una suite en SPEC-008.
- **Sin ARTEMIS.** Los tests de interfaz localizan por `testTag` y leen los textos esperados con `context.getString(...)`.
- **Recorridos guiados:** los tests con `TestApp` llaman a `env.skipOnboarding()`, que los apaga.
- **Fallos previos que no bloquean:** `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap` (idioma),
  `GrinderCatalogSeederTest` (pasa aislado), la oscilación de `SessionPerformanceTest` y el fallo ocasional de
  `SettingsScreenTest` (pasa aislado). F0 confirma la lista.
- **Commits:** uno por fase, en Conventional Commits y en inglés, sin `Co-Authored-By` ni atribución, autorizados por el usuario. Antes, crear la rama `feat/share-recipe` (nunca en `develop`). El del spec y el plan, cuando se apruebe:
  `docs(specs): add the recipe sharing spec and implementation plan (SPEC-009)`.

### F0. Spike en el dispositivo

**Objetivo:** cerrar los riesgos del §6 antes de escribir el códec y la interfaz:

- que el Code Scanner de Google funcione con `INTERNET` eliminado;
- que ML Kit `barcode-scanning` (variante incluida) no reintroduzca `INTERNET` ni añada `CAMERA`;
- fijar `MAX_URI_LENGTH` con el test de legibilidad contra el QR de 432 px del §3.3.

En el repo solo queda lo que se reutiliza: las dependencias, `QrCode.kt`, `QrImageReader.kt`, el stub de
`RecipeShareCodec` con sus constantes y `QrLegibilityTest`. El test del escáner del paso 5 no se commitea.

**Pasos:**

1. **Línea base**, antes de tocar nada:
   - `adb devices -l`: confirmar `emulator-5556`. Si el usuario no ha respondido P5, no usar el teléfono físico.
   - `./gradlew :app:testDebugUnitTest` y `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest`:
     anotar totales y fallos (referencia de SPEC-008: 873 JVM y 558 instrumentados).
   - `./gradlew :app:lintDebug`: errores y avisos.
   - `./gradlew :app:processDebugManifest :app:processReleaseManifest`: guardar en el scratchpad la lista de
     `uses-permission` y `uses-feature` de los dos manifiestos fusionados (buscarlos con
     `find app/build/intermediates -name AndroidManifest.xml -path '*process*Manifest*'`).
   - `./gradlew :app:assembleDebug`: tamaño de `app-debug.apk` y salida de
     `zipalign -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk` (build-tools 35 o posterior) para las `.so`.
2. **Dependencias.** En `libs.versions.toml`: `zxing-core` (`com.google.zxing:core`), `mlkit-barcode-scanning`
   (`com.google.mlkit:barcode-scanning`, la variante incluida) y `play-services-code-scanner`
   (`com.google.android.gms:play-services-code-scanner`). Las últimas que conozco son 3.5.3, 17.3.0 y 16.1.0; F0
   confirma en Google Maven y Maven Central la última estable y la fija. En `app/build.gradle.kts`, las tres como
   `implementation`, con un comentario como el de `mlkit.text.recognition`.
3. **Manifiesto.** Repetir el paso 1 (manifiestos, tamaño y `zipalign`) y comparar:
   - La lista de permisos y de `uses-feature` es **idéntica** a la de la línea base. En particular, no aparecen
     `android.permission.INTERNET` ni `android.permission.CAMERA`.
   - `app/build/outputs/logs/manifest-merger-debug-report.txt` muestra que `INTERNET` llega de las bibliotecas nuevas y
     que el `tools:node="remove"` actual lo quita.
   - `apkanalyzer manifest permissions app/build/outputs/apk/debug/app-debug.apk` da la misma lista.
   - Tras `ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug`, `adb -s emulator-5556 shell dumpsys package dev.josueamaya.pourline | grep -E "android.permission.(INTERNET|CAMERA)"` no imprime nada.
   - `zipalign -P 16` da `OK` en todas las `.so`, también las de ML Kit `barcode-scanning`.
   - Se anota cuánto crece el APK.
4. **QR y lector.** `share/QrCode.kt` (D9) con `test/.../share/QrCodeTest.kt`; `share/QrImageReader.kt` (D18);
   `recipe/share/RecipeShareCodec.kt` como stub con `VERSION`, `SCHEME`, `HOST`, `URI_PREFIX` y `MAX_URI_LENGTH`.
5. **Legibilidad** (`androidTest/.../share/QrLegibilityTest.kt`, D8):
   - `sweep()` mide y escribe en logcat (`QrLegibility`) una tabla con longitud, versión y aciertos (tal cual y tras
     reducir y recomprimir). Cada QR se dibuja con `android.graphics.Canvas` y la geometría de `qrCellEdge` a 432 px,
     sobre un lienzo blanco de 1080 × 1350 px, en el sitio que tendrá en la tarjeta (x = 588 px, y ≈ 228 px).
   - La reducción es `Bitmap.createScaledBitmap(…, w / 2, h / 2, true)`, y la recompresión, `compress(JPEG, 70)` con
     decodificación de vuelta. La lectura usa `MlKitQrImageReader.readBitmap`.
   - Guarda el PNG del QR de cada versión en `getExternalFilesDir(null)/qr-legibility/` para el paso 6.
   - Se fija `MAX_URI_LENGTH` según D8 y se escribe en el stub y en la cabecera de este plan.
   - `maxUriLengthIsLegible()` comprueba, con 20 muestras, que la longitud fijada se lee en los dos casos.
6. **Code Scanner sin `INTERNET`.** Un test temporal, `androidTest/.../share/CodeScannerSpikeTest.kt` (no se commitea):
   con `createAndroidComposeRule<ComponentActivity>()`, llama a `GmsBarcodeScanning.getClient(activity, …).startScan()`
   y espera el resultado hasta 90 s. Se ejecuta en el teléfono físico si el usuario lo autoriza (P5). Si no, en
   `emulator-5556`, con la imagen del QR cargada por el usuario como póster en la escena virtual de la cámara
   (Extended controls → Camera). El QR es el de `MAX_URI_LENGTH`, sacado con
   `adb pull /sdcard/Android/data/dev.josueamaya.pourline/files/qr-legibility` y mostrado a tamaño real en la pantalla
   del Mac. Se comprueba:
   1. el texto devuelto es exactamente el URI, con la app instalada y sin `INTERNET` (paso 3);
   2. el primer uso descarga el módulo (Play Services, con red en el teléfono), y con el modo avión, una segunda lectura
      también funciona;
   3. la lectura tarda 5 s o menos. Si no, se baja una versión, se rehace el paso 5 y se vuelve a comprobar el mínimo;
   4. cancelar devuelve `Task.isCanceled` y no un fallo (lo necesita D19).
7. **Estimación de §6.** Con el esquema del §3.1 (un script en el scratchpad), la receta de referencia de 20 pasos sin
   textos, con un nombre de 60 caracteres, método, temperatura y molienda, ocupa unos 475 caracteres. Se compara con el
   `MAX_URI_LENGTH` fijado.

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../share/QrCodeTest.kt` (nuevo) | `` `the matrix decodes back to the same text with zxing` `` (un `RGBLuminanceSource` a partir de la matriz con zona de silencio y `QRCodeReader`); `` `the error correction level is M` ``; `` `an ascii uri uses byte mode without eci` ``; `` `the version grows with the length` ``; `` `cell edges are integer and contiguous and cover the whole size` ``. |
| `androidTest/.../share/QrLegibilityTest.kt` (nuevo) | `sweep` (medida, sin aserciones de umbral) y `maxUriLengthIsLegible`. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.share.QrCodeTest"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.share.QrLegibilityTest`.
`grep -n "import android" app/src/main/kotlin/dev/josueamaya/pourline/share/QrCode.kt` no encuentra nada.

**Criterio de salida.** Se cumplen todos:

1. Los manifiestos fusionados (debug y release) tienen exactamente los permisos y `uses-feature` de la línea base: sin
   `INTERNET` y sin `CAMERA`.
2. El Code Scanner devuelve el URI en el dispositivo con la app sin `INTERNET`, y funciona sin red una vez descargado
   el módulo.
3. `MAX_URI_LENGTH ≥ 500`, y se lee en 5 s o menos con el escáner desde otra pantalla.
4. `zipalign -P 16` da `OK` en todas las `.so`.

**Si F0 falla, el plan se detiene y se consulta al usuario**, con lo medido y la alternativa de cada caso:

- Si el escáner no funciona sin `INTERNET`: CameraX + ML Kit, que exige el permiso `CAMERA` (§6). La decisión es del
  usuario.
- Si ML Kit reintroduce `INTERNET` u otro permiso pese al `tools:node="remove"`: quitarlo también, o cambiar de
  variante. La decisión es del usuario.
- Si `MAX_URI_LENGTH < 500`, o si no se lee con el escáner: agrandar el QR (160 a 180 dp), usar siempre 9:16, o bajar
  la corrección a L (§6). La decisión es del usuario.
- Si las `.so` nuevas no están alineadas a 16 KB: buscar una versión alineada antes de seguir.

### Resultado de F0 (2026-11-18)

**Línea base** (`develop` en `e64330a`, `emulator-5556`): 932 tests JVM (1 saltado, 0 fallos); 608 instrumentados (5
saltados, 3 fallos conocidos: `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap` y los dos de
`SessionPerformanceTest`); `lintDebug` con 2 errores (`StringFormatInvalid` en `four_six_split_*`, previos) y 21 avisos;
APK de debug de 60 403 569 bytes.

**Dependencias:** `com.google.zxing:core` 3.5.4, `com.google.mlkit:barcode-scanning` 17.3.0 y
`com.google.android.gms:play-services-code-scanner` 16.1.0 (las últimas estables de Maven Central y Google Maven).

| Criterio de salida | Resultado |
|---|---|
| 1. Manifiestos sin `INTERNET` ni `CAMERA`, idénticos a la línea base | Cumple. `uses-permission` y `uses-feature` de debug y release idénticos a la línea base; el informe del fusionador muestra `INTERNET` rechazado en `transport-backend-cct` por el `tools:node="remove"`; `apkanalyzer` da la misma lista. |
| 2. El Code Scanner devuelve el URI sin `INTERNET` | **Sin verificar.** Hace falta una cámara que vea un QR (lo apunta una persona o una escena virtual del emulador). El teléfono físico es el móvil personal del usuario y estaba en uso; no se le lanzó nada más. |
| 3. `MAX_URI_LENGTH >= 500` | **No cumple.** Ver abajo. |
| 4. `zipalign -P 16` da `OK` en las `.so` | Cumple (también `libbarhopper_v3.so`). |

El APK crece de 60 403 569 a 81 167 759 bytes (+20,8 MB, el modelo incluido de ML Kit `barcode-scanning`).

**Legibilidad (`QrLegibilityTest.sweep`, 5 muestras por longitud, QR de 432 px, 1080 x 1350):** tal cual, todas las
muestras se leen hasta la versión 24. Tras reducir al 50 % y JPEG 70, solo se leen siempre hasta la versión 15 (375 y 400
caracteres); desde la 16 falla de forma irregular (no monótona, depende de cómo caen los píxeles al reducir):

| Longitud | 300 | 350 | 375 | 400 | 425 | 475 | 525 | 600 | 700 |
|---|---|---|---|---|---|---|---|---|---|
| Versión | 13 | 14 | 15 | 15 | 16 | 17 | 18 | 19 | 21 |
| Aciertos reducido + JPEG 70 | 5/5 | 5/5 | 5/5 | 5/5 | 2/5 | 0/5 | 3/5 | 0/5 | 0/5 |

La regla de D8 (`Vmax = 15`, una versión de margen) da 362 (capacidad de la versión 14), pero con 362 el test
`maxUriLengthIsLegible` falla en 1 de 20 muestras (19/20 con la reducción y JPEG 70). Por eso el valor provisional es
**331** (versión 13, 20/20).

Otras medidas, con el QR en el borde derecho del lienzo y las mismas muestras (aciertos reducido + JPEG 70):

- QR de 504 px (168 dp): se lee de forma irregular desde la versión 14.
- QR de 540 px (180 dp): todas las muestras hasta la versión 17 (500 caracteres), mezcla en la 18 y 19, irregular después.
- QR de 576 px (192 dp): peor que el de 540 px por el mismo efecto del redimensionado.
- Corrección L con 432 px: más capacidad por versión, pero no mejora la lectura reducida (falla desde la versión 12 o 13 a
  ratos).

**Receta de 20 pasos sin textos** (script en el scratchpad con el esquema del §3.1, nombre de 60, método, temperatura y
molienda): 430 a 490 caracteres, según los valores. La V60 clásica ocupa unos 232. Con 331, **una receta de 20 pasos no
cabe aunque se quiten todos los textos**; y con 362 tampoco.

**Alternativas del §6 (decide el usuario):** (a) agrandar el QR a 180 dp (540 px), con lo que la versión 17 se lee con
fiabilidad (hasta unos 500 caracteres) y una receta de 20 pasos sin textos cabe justo, sin margen; (b) usar siempre 9:16
y un QR aún mayor (hay que medirlo: el redimensionado hace que más grande no sea siempre mejor); (c) relajar el criterio
de lectura del spec (reducción al 50 % es más duro que lo que hace WhatsApp con una imagen de 1080 px); (d) bajar la
corrección a L no ayuda con el criterio actual.

Commit sugerido: `build(deps): add QR generation and reading libraries for recipe sharing`.

### F1. Códec JVM puro

**Objetivo:** modelo → URI → modelo, con versión, compresión, recorte por tamaño y todos los errores del §4.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `recipe/share/SharedRecipe.kt` (nuevo) | Contratos del §3.2 (D1). |
| `recipe/share/RecipeSharePayload.kt` (nuevo) | `PayloadV1`, `GrindV1` y `StageV1`, y la conversión desde y hacia `Recipe` y `SharedGrind` (D2, D5 y D7). |
| `recipe/share/RecipeShareCodec.kt` | `encode` (con la sobrecarga `maxLength`) y `decode` (D1, D3, D4 y D6). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/share/RecipeShareCodecTest.kt` (nuevo) | **Ida y vuelta:** `` `the built-in recipes round trip` `` (`BuiltInRecipes.all(FakeRecipeTexts())`: nombre, método, `methodOther`, dosis, temperatura, notas y, por paso, tipo, nombre, inicio, duración, agua e instrucción); `` `a recipe with every field round trips` `` (`OTHER` con `methodOther`, 93,5 °C, notas, 20 pasos mezclados con nombre e instrucción); `` `swirl and wait water is derived on decode` ``; `` `the decoded recipe has id 0, no built-in key, no localized names and no grind` ``; `` `stages are written ordered by start` ``. **Molienda:** `` `a catalog grinder travels as key, brand, model and value` ``; `` `a custom grinder has no key` ``; `` `a grinder without a setting has no value` ``; `` `a deleted grinder only carries the label` ``; `` `no grind, an empty grind or a deleted grinder without label omit g` ``. **Recorte** (con `maxLength` pequeño): `` `notes go first` ``; `` `then instructions` ``; `` `then stage names` ``; `` `x and trimmed list only what was actually removed` ``; `` `encode is null only when the fully trimmed uri does not fit` ``; `` `steps, times, water, dose, temperature, method and grind are never trimmed` ``; `` `the uri never exceeds the limit` ``. **Tope real:** `` `a 20 step recipe without texts fits under MAX_URI_LENGTH` `` (receta de referencia del paso 7 de F0). **Errores:** `` `surrounding spaces are accepted` ``; `` `another scheme, another host or no payload is not pourline` `` (`https://…`, `pourline://brew?…`, sin `d`, texto libre); `` `a newer version is rejected before reading the payload` `` (`v=2` con `d` basura); `` `a missing or non numeric version is corrupt` ``; `` `bad base64, bad deflate and bad json are corrupt` ``; `` `an unknown key is corrupt at the top, in g and in a stage` ``; `` `an unknown method, stage type or trimmed part is corrupt` ``; `` `a missing required key is corrupt` ``; `` `an oversized inflated payload is corrupt` ``; `` `a payload that fails the validator is invalid` `` (dosis 0, sin pasos, 21 pasos, inicios solapados, sin `POUR`). **Contrato:** `` `the golden v1 uri decodes to the classic V60` `` (D23). |
| `test/.../recipe/share/RecipeSharePurityTest.kt` (nuevo) | `` `the recipe share package does not import android` ``: recorre `src/main/kotlin/dev/josueamaya/pourline/recipe/share` y ninguna línea empieza por `import android` ni `import androidx`. |
| `test/.../recipe/share/RecipeShareFixtures.kt` (nuevo) | `twentyStepsWithoutTexts()`, `everyField()` y `withLongTexts(notes, instructions)`. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.share.*" --tests "dev.josueamaya.pourline.recipe.*"`.

**Criterio de salida:** todo en verde, también `` `a 20 step recipe without texts fits under MAX_URI_LENGTH` ``. Si ese caso falla,
se detiene el plan y se consulta al usuario (§6: agrandar el QR o la tarjeta). Antes, se puede probar a escribir los
`Double` enteros sin `.0` (R3).

Commit sugerido: `feat(recipes): add the recipe share codec`.

### F2. Reglas de importación JVM

**Objetivo:** la detección de idéntica, el sufijo del nombre, el enrutado de cada resultado y la importación pendiente,
sin Android.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `recipe/share/RecipeImportRules.kt` (nuevo) | `nameKey`, `isIdentical` y `uniqueName` (D26). |
| `recipe/share/ImportRouting.kt` (nuevo) | `ImportSource`, `ImportError`, `ImportOutcome` y `routeImport` (D14). |
| `recipe/share/PendingRecipeImport.kt` (nuevo) | `PendingRecipeImport` e `ImportNotice` (D14). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/share/RecipeImportRulesTest.kt` (nuevo) | `` `the same recipe is identical` ``; `` `a name that differs only in case or outer spaces is still identical` ``; `` `a different dose, method or temperature is not identical` ``; `` `a different stage type, name, start, duration, pour water or instruction is not identical` ``; `` `swirl and wait water does not count` ``; `` `notes, method other and grind do not count` ``; `` `a blank stage name equals no name` ``; `` `a free name is kept trimmed` ``; `` `a taken name gets (2)` ``; `` `(2) taken gives (3)` ``; `` `the clash ignores case and outer spaces` ``. |
| `test/.../recipe/share/ImportRoutingTest.kt` (nuevo) | Por cada `DecodeResult` y cada `ImportSource`: `` `ok without a session opens` ``; `` `ok with a session defers` ``; `` `a null text from an image is no recipe in image` ``; `` `not pourline from an image is no recipe in image` ``; `` `not pourline from the scanner or a link is not pourline` ``; `` `newer, corrupt and invalid map to their errors` ``. |
| `test/.../recipe/share/PendingRecipeImportTest.kt` (nuevo) | `` `an opened import is pending without a notice` ``; `` `a deferred import is pending and notifies deferred` ``; `` `the last import replaces the pending one` ``; `` `a failure notifies its error and leaves the pending one alone` ``; `` `consume only clears the same uri` ``; `` `notices are kept until collected` ``. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.share.*"`.

**Criterio de salida:** todo en verde y `RecipeSharePurityTest` sigue pasando con los archivos nuevos.

Commit sugerido: `feat(recipes): add import naming, routing and the pending import`.

### F3. Resolución de molienda JVM

**Objetivo:** los tres casos del §3.6 y el ajuste que se guarda, sin Android.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `grinder/SharedGrindResolution.kt` (nuevo) | D20. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../grinder/SharedGrindResolutionTest.kt` (nuevo; `GrinderFixtures` con `catalogKey` en copias de `uno` y `dos`, y `FakeGrindTexts`) | `` `no shared grind is none` ``. **Mismo molino:** `` `an owned grinder with the same catalog key is linked` ``; `` `without a key, the same brand and model ignoring case is linked` ``; `` `a retired owned grinder is not linked` ``; `` `a brand and model match with a value out of scale falls back to text` ``; `` `linked without a value keeps the grinder without a setting` ``. **Conversión:** `` `the catalog grinder converts to every owned grinder with an equivalent, in owned order` `` (tres molinos propios, uno sin curva que se queda fuera); `` `the first convertible is preselected` ``; `` `calibration pairs between the catalog grinder and an owned one are used` ``; `` `retired grinders are not offered` ``; `` `a rounded conversion is flagged` ``; `` `no convertible grinder falls back to text` ``. **Texto:** `` `a deleted grinder keeps its label as is` ``; `` `a custom grinder the receiver lacks is brand, model and the number` ``; `` `the setting is formatted with the catalog grinder when it exists` ``; `` `without a setting the text is brand and model` ``. **Ajuste guardado:** `` `linked and use save the grinder, the value and the text copy` ``; `` `text and as text save only the label` ``; `` `a picked grinder no longer offered saves as text` ``. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.grinder.*"`.

**Criterio de salida:** todo en verde, y `GrindConverterTest` y `ConverterRowsTest` sin cambios.

Commit sugerido: `feat(grinders): resolve the grind of an imported recipe`.

### F4. Tarjeta de receta

**Objetivo:** la imagen con la receta completa y el QR, en 4:5 o 9:16, con todos los pasos.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `share/ShareSpec.kt` | `SHARE_SIZE_TALL`, `SHARE_QR_SIZE`, `RECIPE_STEP_ROW_HEIGHT`, `RECIPE_CARD_COMPACT_MAX_STAGES` y `recipeCardSize` (D10). |
| `share/ShareModels.kt` | `data class RecipeShareData(val recipe: Recipe, val grindText: String?, val unit: WeightUnit, val qr: QrMatrix?)`; `ShareTarget.Recipe(id: Long)` y su nombre de archivo. |
| `share/ShareCapture.kt` | `ShareRequest.size` y `ShareRequest.Recipe(data: RecipeShareData)`, que sobrescribe `size` con `recipeCardSize(data.recipe.stages.size)`. `ShareCaptureHost` mide con `request.size`. `ShareCard` gana la rama `Recipe`. |
| `share/ShareFrame.kt` | Parámetro `size: DpSize = SHARE_SIZE`. |
| `share/ShareLauncher.kt` | `ShareContent.Recipe(data)` y su rama en `share`. |
| `share/RecipeShareCard.kt` (nuevo) | `RecipeShareCard(data: RecipeShareData, modifier: Modifier = Modifier)` con la maqueta de D10, sin notas ni instrucciones. `QrCodeBox(matrix: QrMatrix, modifier: Modifier)` (`Canvas` de `SHARE_QR_SIZE`, D9). `testTag`: `share_recipe_name`, `share_recipe_method`, `share_recipe_facts`, `share_recipe_dose`, `share_recipe_water`, `share_recipe_ratio`, `share_recipe_temp`, `share_recipe_grind`, `share_recipe_qr`, `share_recipe_qr_caption` y `share_recipe_step_<i>`. |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `recipe_card_*` (D24). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../share/ShareModelsTest.kt` | `` `a recipe is shared as recipe-id png` ``; `` `up to the compact limit the recipe card is 4 by 5 and 9 by 16 after it` ``. |
| `androidTest/.../share/ShareCardsLayoutTest.kt` | `aShortRecipeCardIs1080x1350WithAQrOfAtLeast432px` (V60 clásica: tamaño, `share_recipe_qr` de 432 × 432 px o más, el texto del QR, la marca dentro y cada `share_recipe_step_<i>` por encima de la marca). `aRecipeCardAtTheCompactLimitStaysAt4x5` (10 pasos). `aTwentyStepRecipeWithA60CharNameIs1080x1920AndShowsEveryStep`. `aRecipeCardWithoutQrHasNeitherQrNorCaption`. `theRecipeCardHasNoNotesNorInstructions` (ningún nodo contiene sus textos). `missingFactsAreNotShown` (sin temperatura ni molienda). `theQrIsBlackOnWhite` (píxeles de la zona de silencio blancos y el primer módulo del patrón de posición negro, en el bitmap capturado). `aDeletedGrinderShowsItsLabel` y `aLiveGrinderShowsTheEditorLabel`. Los casos existentes no cambian. |
| `androidTest/.../share/RecipeQrLegibilityTest.kt` (nuevo) | `theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70`: receta de 20 pasos, nombre de 60 caracteres y notas aleatorias (poco comprimibles) ajustadas por búsqueda binaria hasta que el URI quede entre `MAX_URI_LENGTH − 8` y `MAX_URI_LENGTH` sin recorte. Se captura con `ShareCaptureHost` (1080 × 1920) y `MlKitQrImageReader.readBitmap` devuelve el URI, tal cual y tras reducir al 50 % y recomprimir en JPEG 70. `decode` del texto leído da `Ok` con la misma receta. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.share.*"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.share.ShareCardsLayoutTest,dev.josueamaya.pourline.share.RecipeQrLegibilityTest,dev.josueamaya.pourline.share.ShareIntentTest`.

**Criterio de salida:** todo en verde. Si 10 pasos no caben en 4:5, se baja `RECIPE_CARD_COMPACT_MAX_STAGES` al valor
medido. Si 20 pasos no caben en 9:16, se reduce el nombre a una línea y se vuelve a medir; si aún no caben, se consulta.

Commit sugerido: `feat(share): add the recipe share card with a QR code`.

### F5. Compartir desde el editor

**Objetivo:** el icono "Compartir" del editor genera la imagen y abre el selector, con los dos diálogos del §3.4.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/recipes/RecipeEditViewModel.kt` | `share`, `prepareShare`, `clearShare` y `RecipeSharePrompt` (D12). |
| `ui/recipes/RecipeEditScreen.kt` | Parámetro `shareSender`, el `SnackbarHost`, el icono en `actions`, el `AntModal` con la lista de lo recortado o el aviso de "solo la imagen", y `launcher.Host()` (D12). `TourHost(Tours.recipeEdit, …)` y las anclas no cambian. |
| `ui/recipes/RecipeFormatters.kt` | `@Composable internal fun trimmedPartsText(parts: Set<TrimmedPart>): String`, en el orden notas, instrucciones y nombres, con `recipe_trimmed_list_two` y `recipe_trimmed_list_three`. La vista previa también la usa. |
| `share/ShareImages.kt` | `EXTRA_EXCLUDE_COMPONENTS` (D13). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `recipe_share_*` y `recipe_trimmed_*` (D24). |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/recipes/RecipeShareTest.kt` (nuevo; mismo montaje que `RecipeEditScreenTest.show`, con un `ShareSender` falso) | `aSavedRecipeSharesAPngInBasic` y `…InExpert`. `aBuiltInRecipeCanBeShared`. `theIconIsDisabledWithUnsavedChanges` (y se vuelve a habilitar al deshacer el cambio). `theIconIsDisabledForANewRecipe` (y en los modos desde preparación y desde el 4:6). `aLongRecipeListsWhatWillBeMissingAndCancelSendsNothing` (notas e instrucciones largas: el texto incluye "the notes" y "the step instructions", en `en-US`). `confirmingTheTrimDialogShares`. `aRecipeThatDoesNotFitSharesTheImageWithoutQr` (nombre de 400 caracteres: diálogo de "solo la imagen", y ML Kit no encuentra ningún QR en el PNG enviado). `thePngIsReadableThroughTheFileProviderAndItsQrDecodesToTheRecipe` (se abre el `EXTRA_STREAM` con `contentResolver`: 1080 × 1350 con la V60, 1080 × 1920 con 20 pasos, y el QR leído da `Ok` con la receta guardada). `theChooserExcludesPourline`. |
| `androidTest/.../ui/recipes/RecipeEditScreenTest.kt`, `androidTest/.../share/ShareIntentTest.kt` | Sin cambios. Se ejecutan para comprobar que el editor y el resto de imágenes no cambian. |

**Verificación:** `./gradlew :app:compileDebugKotlin`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipeShareTest,dev.josueamaya.pourline.ui.recipes.RecipeEditScreenTest,dev.josueamaya.pourline.share.ShareIntentTest`.

**Criterio de salida:** todo en verde y `RecipeEditScreenTest` sin cambios.

Commit sugerido: `feat(recipes): share a recipe as an image from the editor`.

### F6. Vista previa, guardado, navegación común y mensajes

**Objetivo:** cualquier texto que llegue a `PendingRecipeImport` abre la vista previa en Recetas (o espera a que
termine la sesión), y Guardar añade la receta con el mensaje "Receta añadida".

**Archivos:**

| Archivo | Cambio |
|---|---|
| `NavigationKeys.kt` | `RecipeImport(val uri: String)` (D15). |
| `TabNavigation.kt` | `showsBottomBar` con `RecipeImport`, y `openImport` (D15). |
| `Navigation.kt` | La entrada `tabEntry<RecipeImport>(tab, stack, activeTab)` con `RecipeImportScreen` y `onDone = ::back`. En `TabsNavigation`, el efecto de D15 y el host de mensajes de D21, dentro de `TourRoot`. |
| `AppDependencies.kt`, `PourlineApp.kt`, `androidTest/.../ui/TestAppDependencies.kt` | `recipeImports: PendingRecipeImport`. |
| `recipe/RecipeRepository.kt` | `suspend fun all(): List<Recipe>` (D22). |
| `grinder/GrinderDao.kt`, `grinder/GrinderRepository.kt` | `getByCatalogKey` y `byCatalogKey` (D22). |
| `ui/recipes/RecipeImportViewModel.kt` (nuevo), `ui/recipes/RecipeImportScreen.kt` (nuevo) | D22 y D25. |
| `ui/recipes/ImportNoticeText.kt` (nuevo) | D21. |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `recipe_import_title`, `_save`, `_cancel`, `_trimmed`, `_identical`, `_notes`, `_grind`, `_grind_use`, `_grind_as_text` y los ocho mensajes. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../TabNavigationTest.kt` | `` `the bottom bar is hidden with an import preview on top` ``; `` `open import pushes a preview` ``; `` `open import replaces a preview already on top` ``. |
| `test/.../ui/recipes/ImportNoticeTextTest.kt` (nuevo) | Cada `ImportNotice` tiene su cadena y su estado. |
| `androidTest/.../ui/recipes/RecipeImportScreenTest.kt` (nuevo; Room en memoria, catálogo de prueba con `TestGrinderCatalog`) | `showsTheRecipeWithNotesAndStepInstructions`. `showsTheTrimmedWarning`. `anIdenticalRecipeWarnsAndStillSavesWithASuffix` (V60 clásica predefinida e importar `GOLDEN_V60_URI`: aviso, y al guardar, "V60 classic (2)"). `sameNameDifferentContentSavesWithASuffixWithoutWarning`. `theSameGrinderIsLinked` (la receta guardada tiene el `grinderId` propio, el valor y la copia de texto). `aConvertibleGrindOffersEveryOwnedGrinderAndPreselectsTheFirst`. `choosingSaveAsTextSavesTheLabel`. `aGrinderRetiredBeforeSavingIsSavedAsText`. `noGrindSavesWithoutGrind`. `cancelSavesNothing`. `aViewModelRebuiltFromTheSameUriShowsTheSameRecipe`. `aUriThatNoLongerDecodesNotifiesTheErrorAndCloses`. |
| `androidTest/.../ui/recipes/RecipeImportNavigationTest.kt` (nuevo; `TestApp(env)`, `env.skipOnboarding()`) | `aSubmittedImportOpensThePreviewInTheRecipesTabWithoutBottomBar` (`env.dependencies.recipeImports.submit(GOLDEN_V60_URI, LINK, false)`). `savingReturnsToTheListWithRecipeAdded`. `cancelReturnsToTheListWithoutSaving`. `withASessionTheImportWaitsAndOpensWhenTheSessionEnds` (`startScalelessSession(BrewPlan(15.0, 16.0))`, `submit(…, hasSession = true)`: mensaje de aplazamiento y sin vista previa; tras `discardSession()`, la vista previa). `aSecondImportReplacesTheOpenPreview`. `anImportBeforeOnboardingOpensAfterIt`. `aFailedImportShowsItsMessage`. |
| `androidTest/.../grinder/GrinderRepositoryTest.kt` | `byCatalogKeyReturnsTheGrinderWithRangesAndAnchors` y `null` con una clave desconocida. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.TabNavigationTest" --tests "dev.josueamaya.pourline.ui.recipes.*"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipeImportScreenTest,dev.josueamaya.pourline.ui.recipes.RecipeImportNavigationTest,dev.josueamaya.pourline.grinder.GrinderRepositoryTest,dev.josueamaya.pourline.ui.TabsNavigationTest,dev.josueamaya.pourline.ui.tour.RecipesHistoryTourTest`.

**Criterio de salida:** todo en verde, y `TabsNavigationTest` y los tests del recorrido de Recetas sin cambios.

Commit sugerido: `feat(recipes): preview and save an imported recipe`.

### F7. Entradas internas

**Objetivo:** "Importar" en Recetas, con el escáner y la galería, en los dos modos.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/recipes/QrScanner.kt` (nuevo) | D19. |
| `ui/recipes/RecipeImportEntryViewModel.kt` (nuevo) | D27. |
| `ui/recipes/RecipesScreen.kt` | `importer`, el icono, el menú, el escáner (`rememberQrScanner()`) y el selector de fotos (D27). Las anclas del recorrido no cambian. |
| `ui/components/AntIcons.kt` | `Import`. |
| `Navigation.kt` | `tabEntry<Recipes>` crea `RecipeImportEntryViewModel(deps.recipeImports, deps.qrImageReader) { deps.scaleSession.ui.value.hasSession }` y lo pasa a `RecipesScreen`. |
| `AppDependencies.kt`, `PourlineApp.kt` (`MlKitQrImageReader(this)`, perezoso), `androidTest/.../ui/TestAppDependencies.kt` | `qrImageReader: QrImageReader`. |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `recipe_import_action`, `recipe_import_scan` y `recipe_import_from_image`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/recipes/RecipesImportTest.kt` (nuevo; `TestApp(env)` dentro de `CompositionLocalProvider(LocalQrScanner provides fake)`, y un `LocalActivityResultRegistryOwner` falso que devuelve el `Uri` que fija el test) | `theImportMenuOffersScanAndImageInBasicAndExpert`. `aScannedRecipeOpensThePreview`. `aForeignQrShowsNotPourline` ("https://example.com"). `cancellingTheScannerDoesNothing`. `anUnavailableScannerSuggestsTheImage`. `aGalleryImageWithARecipeOpensThePreview` (PNG de la tarjeta servido por `FileProvider`). `aGalleryImageWithoutQrShowsNoRecipeFound`. `aGalleryImageWithAForeignQrShowsNoRecipeFound`. `cancellingThePickerDoesNothing`. `aHugeImageIsReducedAndStillRead` (6 000 × 6 000 px con el QR escalado). `anUnreadableFileShowsNoRecipeFound` (bytes basura con extensión `.png`). |
| `androidTest/.../ui/recipes/RecipesScreenTest.kt` | Sin cambios (sin `importer`). |

**Verificación:** `./gradlew :app:compileDebugKotlin`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipesImportTest,dev.josueamaya.pourline.ui.recipes.RecipesScreenTest,dev.josueamaya.pourline.ui.tour.RecipesHistoryTourTest`.

**Criterio de salida:** todo en verde. El escáner real se prueba en F9, porque el emulador no tiene una cámara que vea
un QR sin preparar la escena.

Commit sugerido: `feat(recipes): import a recipe from the scanner or a gallery image`.

### F8. Entradas externas

**Objetivo:** Pourline aparece en la hoja de compartir de imágenes y abre los enlaces `pourline://recipe`, con una
sola instancia de `MainActivity` y el aplazamiento durante una sesión.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `AndroidManifest.xml` | `launchMode="singleTask"` y los dos `intent-filter` de D17. |
| `RecipeIntents.kt` (nuevo) | `RecipeIntentHandler` y `shouldHandleLaunchIntent` (D17). |
| `MainActivity.kt` | El manejador, la llamada en `onCreate` y `onNewIntent` (D17). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../RecipeIntentsTest.kt` (nuevo) | `` `a fresh launch is handled` ``; `` `a recreated activity is not handled again` ``; `` `a launch from recents is not handled again` ``. |
| `androidTest/.../RecipeIntentImportTest.kt` (nuevo; D28) | `aViewIntentOpensThePreviewAndSavingAddsTheRecipe` (con "Recipe added"). `aSendIntentWithAGeneratedPngOpensThePreviewAndSavingAddsTheRecipe`. `aSendIntentWithAnImageWithoutQrShowsNoRecipeFound`. `aSendIntentWithAForeignQrShowsNoRecipeFound`. `aSendIntentWithoutStreamShowsNoRecipeFound`. `aViewIntentWithoutPayloadShowsNotPourline`. `withASessionTheIntentDoesNotNavigateAndThePreviewOpensWhenItEnds` (la pantalla de sesión sigue y aparece "Recipe received…"). `aSecondIntentDuringTheSessionReplacesThePendingOne`. `otherIntentsAreIgnored` (`MAIN`/`LAUNCHER`: `handle` da `false` y no navega). |
| `androidTest/.../ManifestPermissionsTest.kt` (nuevo) | D29. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.RecipeIntentsTest"`.
`./gradlew :app:processDebugManifest` y `grep -E "android.permission.(INTERNET|CAMERA)"` sobre el manifiesto
fusionado no encuentra nada.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.RecipeIntentImportTest,dev.josueamaya.pourline.ManifestPermissionsTest,dev.josueamaya.pourline.ui.TabsNavigationTest`.
Una comprobación rápida por adb: `adb -s emulator-5556 shell "am start -a android.intent.action.VIEW -d 'pourline://recipe?v=1&d=…'"`
con `GOLDEN_V60_URI` abre la vista previa, y `adb -s emulator-5556 shell dumpsys activity activities | grep -c "dev.josueamaya.pourline/.MainActivity"`
da una sola instancia.

**Criterio de salida:** todo en verde y una sola instancia en la comprobación por adb.

Commit sugerido: `feat(recipes): import recipes from shared images and pourline links`.

### F9. Verificación

**Objetivo:** el §7 completo, la prueba manual con dos móviles y las capturas.

**Archivos:** solo este plan (cabecera con las cifras) y, si el usuario lo pide al cerrar, el estado del spec.

**Verificación:** el §7.

Commit sugerido: `test(recipes): verify recipe sharing end to end`.

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| El icono "Compartir" genera la imagen con la receta y el QR y abre el selector con `ACTION_SEND` `image/png`, en Básico y Experto, también con predefinidas | F4, F5 | `RecipeShareTest.aSavedRecipeSharesAPngInBasic` / `…InExpert` y `aBuiltInRecipeCanBeShared`; §7.4, paso 2 |
| La imagen mide 1080 × 1350, o 1080 × 1920 si no caben los pasos, y muestra los 20 pasos | F4 | `ShareCardsLayoutTest.aShortRecipeCardIs1080x1350…`, `…AtTheCompactLimitStaysAt4x5` y `aTwentyStepRecipe…Is1080x1920AndShowsEveryStep`; `RecipeShareTest.thePngIsReadable…` |
| Unitario: ida y vuelta de predefinidas y de una receta con todos los campos | F1 | `RecipeShareCodecTest` (ida y vuelta y contrato) |
| Unitario: recorte en orden, con `x` y `trimmed`, y `null` solo sin cabida | F1 | `RecipeShareCodecTest` (recorte y tope real) |
| Unitario: `NotPourline`, `NewerVersion`, `Corrupt` (base64, deflate, JSON, clave y método desconocidos) e `Invalid` | F1 | `RecipeShareCodecTest` (errores) |
| Unitario: molienda en los tres casos y sin molienda | F3 | `SharedGrindResolutionTest` |
| Unitario: idéntica y sufijos " (2)" y " (3)" | F2 | `RecipeImportRulesTest` |
| Unitario: `recipe.share` no importa `android.*` | F1, F2 | `RecipeSharePurityTest`; `grep` del §7.2 |
| Instrumentado: el QR del PNG de peor caso se lee con ML Kit, tal cual y tras 50 % + JPEG 70 | F0, F4 | `QrLegibilityTest.maxUriLengthIsLegible` y `RecipeQrLegibilityTest.theWorstCaseCardIsReadable…` |
| Instrumentado: compartir envía por `ShareSender` un `ACTION_SEND` con un PNG legible por `FileProvider` | F5 | `RecipeShareTest.thePngIsReadableThroughTheFileProvider…` |
| Instrumentado: `ACTION_VIEW` y `ACTION_SEND image/*` abren `RecipeImport`; Guardar añade la receta con "Receta añadida" | F6, F8 | `RecipeIntentImportTest.aViewIntent…` y `aSendIntentWithAGeneratedPng…`; `RecipeImportNavigationTest.savingReturnsToTheListWithRecipeAdded` |
| Instrumentado: con una sesión, el intent no navega y la vista previa se abre al terminar | F2, F6, F8 | `PendingRecipeImportTest`; `RecipeImportNavigationTest.withASession…`; `RecipeIntentImportTest.withASession…` |
| Instrumentado: una imagen sin QR y un QR ajeno muestran sus mensajes | F7, F8 | `RecipesImportTest.aForeignQrShowsNotPourline` y `aGalleryImageWithoutQr…`; `RecipeIntentImportTest.aSendIntentWithAnImageWithoutQr…` |
| El manifiesto final no contiene `INTERNET` ni `CAMERA` | F0, F8, F9 | Paso 3 de F0; `ManifestPermissionsTest`; §7.2 |
| Textos nuevos en `values` y `values-en` | F4 a F7, F9 | `:app:lintDebug` (`MissingTranslation` y `ExtraTranslation` a cero en las cadenas nuevas) |
| Detekt y los tests existentes pasan sin cambiar expectativas | Todas | `:app:lintDebug` (no hay detekt, discrepancia 11); suites del §7.1 frente a la línea base de F0 |
| Manual con dos móviles: WhatsApp, cuatro entradas (anotando el fabricante si la cámara no ofrece el enlace) y conversión de molienda | F9 | §7.5 |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | El Code Scanner no funciona sin `INTERNET`, o el módulo no se descarga. | F0 lo prueba en el dispositivo antes de escribir nada más. Si falla, se detiene y se consulta (alternativa: CameraX + `CAMERA`). Sin módulo y sin red, el mensaje del §4 sugiere la galería. |
| R2 | ML Kit `barcode-scanning` reintroduce `INTERNET` u otro permiso. | El `tools:node="remove"` es global. F0 compara la lista completa de permisos con la línea base, y `ManifestPermissionsTest` lo vigila después. |
| R3 | `MAX_URI_LENGTH` no da cabida a 20 pasos sin textos, o un QR denso no se lee con una cámara desde otra pantalla. | D8: una versión de margen, el mínimo de 500, la lectura con el escáner en F0 y la auto-zoom del escáner. Primera palanca antes de consultar: escribir los `Double` enteros sin `.0` (unos 10 a 20 caracteres menos en 20 pasos). Después, las alternativas del §6. |
| R4 | Los 20 pasos no caben en 9:16 o 10 no caben en 4:5. | Filas de alto fijo y el test de maqueta de F4, que fija la constante. |
| R5 | La biblioteca nativa de ML Kit no está alineada a 16 KB (`targetSdk 37`). | `zipalign -P 16` en F0, comparado con la línea base. `emulator-5554` (16 KB) es del usuario y no se usa. |
| R6 | `singleTask` cierra lo que haya encima de `MainActivity` en su tarea al volver desde el lanzador (el selector de fotos o el escáner abiertos). | Ese caso devuelve "cancelado", que no hace nada. Se comprueba por adb en el §7.4. |
| R7 | El permiso del `Uri` de `ACTION_SEND` se pierde si la lectura se retrasa. | La imagen se lee en cuanto llega, en el ámbito de la app (D17), y lo que se aplaza es el texto ya decodificado (§3.7). |
| R8 | Los bytes de `Deflater` difieren entre la JVM y Android. | Los tests comparan lo decodificado, nunca los bytes (D23). |
| R9 | El host de mensajes de importación coincide con el `Snackbar` propio de una pantalla ("Receta borrada"). | Es raro y solo dura lo que un mensaje corto. Los dos usan `AntMessageHost`. |
| R10 | La cámara del fabricante no ofrece abrir `pourline://`. | Está fuera del alcance de la app (§4). La prueba manual anota el fabricante. |
| R11 | Un payload enorme o malicioso. | Tope de inflado (D3), `RecipeValidator` y el propio tamaño del QR. |
| R12 | El APK crece por el modelo incluido de ML Kit. | Es el precio de leer sin red (§3.8). F0 lo mide y lo anota. |
| R13 | Una regresión en las imágenes existentes por el tamaño variable. | `ShareRequest.size` y `ShareFrame(size)` valen `SHARE_SIZE` por defecto. F4 ejecuta `ShareCardsLayoutTest` y `ShareIntentTest` sin cambiar sus casos. |
| R14 | Las anclas del recorrido guiado en `RecipesScreen` y `RecipeEditScreen` se rompen al tocar la barra superior. | Los iconos nuevos van en `actions` sin tocar las anclas existentes. F6 y F7 ejecutan `RecipesHistoryTourTest`. |

## 6. Preguntas para el usuario

Cada una tiene una propuesta. Si se aprueba el plan sin comentarios, se aplica la propuesta.

1. **P1. Unidad de peso de la imagen y de la vista previa (D11).** La app no tiene una unidad del usuario: `WeightUnit`
   es la de la báscula conectada, y las recetas se ven siempre en gramos. *Propuesta: gramos en la imagen y en la
   vista previa, como el editor y la lista.* Alternativa: la unidad actual de la báscula (`ScaleUiState.unit`, gramos
   sin báscula). La misma receta saldría en gramos u onzas según haya báscula o no.
2. **P2. Emparejar por marca y modelo con un ajuste fuera de escala (D20).** *Propuesta: guardar como texto, porque el
   ajuste no existe en el molino del receptor.* Alternativa: enlazar igualmente, como dice el §3.6 al pie de la letra.
3. **P3. Pourline en su propio selector de compartir (D13).** *Propuesta: excluirla con `EXTRA_EXCLUDE_COMPONENTS` en
   todas las imágenes.* Alternativa: no tocar el selector, y Pourline aparecerá como destino de sus propias imágenes.
4. **P4. Adónde vuelve Guardar (D15).** *Propuesta: quitar solo la vista previa. Desde la lista se vuelve a la lista, y
   si había un editor abierto debajo, al editor, sin perder sus cambios.* Alternativa: vaciar la pila de Recetas hasta
   la lista, aunque se pierdan los cambios de ese editor.
5. **P5. Teléfono físico.** Está conectado un `22081212UG` (Android 15, Play Store, por adb inalámbrico). *Propuesta:
   usarlo en F0 para el escáner y en F9 para la cámara del sistema, Lens y la prueba con dos móviles.* Sin él, el
   escáner de F0 se prueba en `emulator-5556` con la escena virtual preparada por el usuario, y la cámara del sistema
   queda para la prueba manual del usuario.
6. **P6. Títulos que el spec no da (D24).** *Propuesta: "Compartir receta" en los dos diálogos e "Importar receta" en
   la vista previa.*

## 7. Verificación final

### 7.1 Suites

```
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Se compara con la línea base de F0:

- **Tests:** solo suben los totales, por los tests nuevos. Ninguna expectativa existente cambia.
- **Lint:** sin errores ni avisos nuevos, y `MissingTranslation`, `ExtraTranslation` y `UnusedResources` a cero en las
  cadenas nuevas.
- **Fallos que no bloquean:** los de la lista del §3, si siguen siendo los mismos.

### 7.2 Comprobaciones

- `grep -rn "import android" app/src/main/kotlin/dev/josueamaya/pourline/recipe/share app/src/main/kotlin/dev/josueamaya/pourline/share/QrCode.kt app/src/main/kotlin/dev/josueamaya/pourline/grinder/SharedGrindResolution.kt`
  no encuentra nada.
- Los manifiestos fusionados de `:app:processDebugManifest` y `:app:processReleaseManifest` no contienen
  `android.permission.INTERNET` ni `android.permission.CAMERA`, y sus permisos son los de la línea base de F0.
- `git diff develop -- app/src/main/kotlin/dev/josueamaya/pourline/share/BrewShareCard.kt app/src/main/kotlin/dev/josueamaya/pourline/share/CoffeeShareCard.kt app/src/main/kotlin/dev/josueamaya/pourline/share/CompareShareCard.kt`
  está vacío.
- `zipalign -c -P 16` da `OK` en todas las `.so` del APK de debug.

### 7.3 Rendimiento

El spec no pide medidas de rendimiento. A título informativo, se anotan en la cabecera:

- el tiempo de generar el PNG de la receta de 20 pasos (`RecipeShareTest`, mediana de 5; SPEC-004 midió 286 a 622 ms en
  sus imágenes);
- el tiempo de `MlKitQrImageReader.read` sobre un PNG de 1080 × 1920 (mediana de 5).

Si alguno pasa de 1 s en `emulator-5556`, se comenta con el usuario. No bloquea el cierre.

### 7.4 E2E por adb (sin ARTEMIS)

**Antes de empezar:**

1. `adb devices -l`. El teléfono físico solo con P5.
2. `adb -s emulator-5556 shell wm size`, para convertir coordenadas.
3. `ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug` y `adb -s emulator-5556 shell pm clear dev.josueamaya.pourline`.

**Herramientas:** `uiautomator dump`, `input tap` y `screencap -d <display id>` (el id sale de
`adb shell dumpsys SurfaceFlinger --display-id`). `adb logcat -b crash -d` debe quedar vacío en todo el recorrido. El
emulador está en inglés: los textos esperados son los de `values-en`.

| Paso | Recorrido | Comprobaciones |
|---|---|---|
| 1 | Onboarding → Recetas → "V60 classic" | En la barra superior, el icono de compartir habilitado. Al cambiar el nombre sin guardar, deshabilitado. Captura. |
| 2 | Compartir | Se abre el selector del sistema, sin Pourline entre los destinos. `adb exec-out run-as dev.josueamaya.pourline cat cache/shared/recipe-<id>.png > v60.png` y `file v60.png` da 1080 × 1350. Captura del selector y del PNG. |
| 3 | `adb -s emulator-5556 shell "am start -a android.intent.action.VIEW -d 'pourline://recipe?v=1&d=…'"` con `GOLDEN_V60_URI`, con la app delante y después en segundo plano | La vista previa en Recetas, con el aviso "You already have this recipe". `dumpsys activity activities` muestra una sola `MainActivity`. Captura. |
| 4 | Con la vista previa abierta, girar (`settings put system accelerometer_rotation 0` y `settings put system user_rotation 1`) y volver | La vista previa sigue igual y no se abre otra. Al final se restauran los dos ajustes. |
| 5 | Guardar | La lista con "V60 classic (2)" y el mensaje "Recipe added". Captura. |
| 6 | `adb push v60.png /sdcard/Download/` → app Files → compartir la imagen → Pourline (o `am start -a android.intent.action.SEND -t image/png --eu android.intent.extra.STREAM <content://media/…> --grant-read-uri-permission`) | La vista previa. Se cuentan los toques desde la imagen abierta hasta Guardar: 3 o menos (§1, "menos de 4 toques"). Captura. |
| 7 | Preparar → "Start without scale" → "Start timer"; repetir el paso 3 | La sesión sigue y sale "Recipe received. You'll see it when you finish brewing.". Tras "Finish" y "Done" (o descartar), se abre la vista previa. Capturas. |
| 8 | Compartir hacia Pourline una imagen sin QR (una captura de pantalla cualquiera) | "No recipe was found in the image". |
| 9 | Recetas → Importar → "From an image" → `v60.png` en el selector de fotos | La vista previa. |
| 10 | Recetas → Importar → "Scan QR code" → cancelar | No pasa nada. Con el teléfono físico (P5): leer el QR del PNG en la pantalla del Mac → la vista previa. |
| 11 | Con la vista previa del paso 9 abierta, pulsar el icono de Pourline en el lanzador | Se vuelve a la misma tarea, con una sola `MainActivity`. |
| 12 | `adb -s emulator-5556 shell dumpsys package dev.josueamaya.pourline \| grep -E "android.permission.(INTERNET\|CAMERA)"` | No imprime nada. |

### 7.5 Prueba manual con dos móviles (usuario)

- Compartir "V60 clásica" por WhatsApp desde un móvil y abrir la imagen en el otro.
- Importar en el otro móvil por las cuatro entradas: escáner, galería, compartir hacia Pourline y la cámara del
  sistema o Lens. Si la cámara no ofrece abrir el enlace, anotar el fabricante y el modelo (§4).
- Contar los toques desde que se abre la imagen hasta Guardar (menos de 4).
- Conversión de molienda: en el móvil emisor, una receta con un molino del catálogo; en el receptor, sin ese molino
  y con otro propio distinto. La vista previa ofrece "Usar con <molino> · <ajuste>" y el ajuste guardado coincide con
  el del convertidor.
- Unidad: con P1 en su propuesta, la imagen y la vista previa salen en gramos aunque la báscula esté en onzas.

### 7.6 Capturas

En claro y en oscuro (Ajustes → Apariencia): el editor con el icono de compartir, los dos diálogos, la tarjeta en 4:5
y en 9:16 (el PNG), el menú Importar, la vista previa con los dos avisos y con el selector de molienda, y los mensajes
de importación.

- **Dónde:** fuera del repo, con `adb pull` a `~/Downloads/pourline-recipe-share/{en,es}`.
- **Qué revisar a ojo:** que los textos no se corten a 360 dp ni con la escala de letra a 1,3 en la vista previa
  (`adb shell settings put system font_scale 1.3`, y después `1.0`); que el QR de la tarjeta sea nítido y negro sobre
  blanco; y la redacción de "La receta original tenía … que no cupieron en el QR" con la lista insertada.

## 8. Seguimiento fuera de este plan

- **Cambios que conviene llevar al spec al cerrar.** No se tocan ahora.
  - La unidad de peso de la imagen y de la vista previa (D11, P1).
  - La copia de texto al enlazar el mismo molino (discrepancia 4, D20).
  - El formato de `<ajuste>` sin el molino del autor (discrepancia 5, D20).
  - `Tab.RECIPES` en lugar de `Tabs.RECIPES` (discrepancia 6).
  - Guardar quita solo la vista previa (D15, P4).
  - `v` ausente es `Corrupt` (D4).
  - El test de pureza es un test JVM (discrepancia 10).
  - Lint en lugar de detekt (discrepancia 11).
  - La exclusión de Pourline del selector (D13, P3).
  - El valor final de `MAX_URI_LENGTH` y el umbral de 4:5 (D8 y D10).
- **Recorrido guiado (SPEC-008):** los iconos nuevos de compartir e importar no tienen paso en el recorrido. Si se
  añaden después, SPEC-008 los presenta solos a quien ya vio el resto.
- **Al publicar en Play:** valorar `<meta-data android:name="com.google.mlkit.vision.DEPENDENCIES" android:value="barcode_ui" />`
  para que la tienda instale el módulo del escáner junto con la app. Hoy no se puede comprobar.
- **Fuera del alcance (§1 del spec):** enlace a Play, App Links, archivos `.pourline`, compartir como texto, varias
  recetas a la vez y sincronizar recetas importadas.
- **Antes de fusionar en `develop`:** revisar la rama con `/code-review`.
