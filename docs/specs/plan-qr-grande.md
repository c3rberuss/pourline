# Plan de implementación: SPEC-009 Enmienda 1, QR grande

| Campo | Valor |
|---|---|
| Spec | [`spec-compartir-receta.md`](spec-compartir-receta.md) (SPEC-009), **Enmienda 1** (§3.1, §3.2, §3.3, §3.4, §3.6, §4, §5, §6 y §7) |
| Estado | Aprobado (2026-11-18), P1-P3 con la propuesta del plan. En implementación |
| Fecha | 2026-11-18 |
| Rama | `feat/share-recipe-large-qr`, desde `develop` (`bbc96aa`), con el spec modificado sin commitear |
| Módulos | `:app` (`:protocol` no cambia) |
| Plan anterior | [`plan-compartir-receta.md`](plan-compartir-receta.md) (D1 a D29). Este plan sigue desde D30 |
| `MAX_URI_LENGTH` | 700 según el spec (QR versión 21, corrección M). F0 lo confirma sobre la tarjeta real o lo baja (D34) |
| Línea base | La de F9 del plan anterior, que es este mismo código (`bbc96aa`): JVM 1039 (1 saltado, 0 fallos); instrumentados 681 por emulador; `emulator-5556` con 17 fallos (los 3 conocidos y 14 que necesitan el modelo de Play Services); `emulator-5560` con 6 (los 3 conocidos y 3 de UI intermitentes); `:app:lintDebug` con 2 errores y 21 avisos. F0 repite las clases afectadas antes de tocar código |
| Resultado de la verificación | Pendiente (F4) |

La enmienda cambia tres cosas, y el plan las ordena de modo que la medición vaya primero:

1. **La geometría del QR y la tarjeta (F0).** Cada módulo mide 6 px exactos, el QR baja a una franja propia bajo los
   pasos y la tarjeta crece con el contenido. F0 construye esa tarjeta, mide sobre ella la legibilidad con 20 muestras
   por longitud alrededor de 700 y fija `MAX_URI_LENGTH`. La tarjeta va en F0 porque medir "sobre la tarjeta real"
   exige que exista. Ninguna fase posterior empieza sin el valor fijado.
2. **El recorte (F1).** Las instrucciones se quitan de una en una desde el último paso, `encode` informa cuántas quitó y
   el códec distingue una receta inválida de una que no cabe. Todo es JVM puro y el formato `v=1` no cambia.
3. **Los avisos (F2 y F3).** Al compartir, "las instrucciones de algunos pasos" y el aviso de datos no válidos. Al
   importar, el aviso de recorte parcial.

F4 es la verificación, con las capturas de la tarjeta que pide el encargo.

Al recorrer el código salieron tres cosas que el spec no dice y que condicionan el diseño:

- **El QR centrado cae siempre en una x impar.** El ancho útil de la tarjeta es 960 px y la caja mide
  `6 × (25 + 4v)` px, así que `(960 − caja) / 2` es impar en todas las versiones (153 px en la 21), y con el margen de
  60 px la caja empieza en x = 213. Al reducir la imagen a la mitad, cada borde de módulo cae en medio de un píxel. El
  lienzo de la medición del 2026-11-18 ponía la caja en una x par (`1080 − 48 − caja`), así que su resultado no vale tal
  cual para la tarjeta. El plan alinea la caja a píxeles pares (D31) y F0 lo mide.
- **Las tarjetas altas se reescalan al importar.** `MlKitQrImageReader.read` reduce la imagen a `QR_IMAGE_MAX_SIDE` =
  2048 px de lado (§3.5). La tarjeta de 20 pasos con el QR de la versión 21 mide unos 2 400 px de alto, así que al
  importarla desde la galería o la hoja de compartir se escala por 0,86 y los módulos dejan de ser enteros. F0 añade
  esa lectura a la medición (D46).
- **El PNG no depende del tema.** `ShareCaptureHost` dibuja siempre con `PourlineTheme(darkTheme = false)` (D30 de
  SPEC-004). Las capturas de la tarjeta "en oscuro" deben salir idénticas byte a byte a las de claro (D45, P1).

No hay detekt ni ktlint en el proyecto. Como en los planes anteriores, el control es `:app:lintDebug`.

## 1. Punto de partida

### 1.1 Se reutiliza

| Pieza | Uso en la enmienda |
|---|---|
| `share/QrCode.kt`: `QrMatrix`, `qrMatrix`, `qrVersion`, `QR_QUIET_ZONE` | La matriz no cambia. `QrMatrix` gana `cells` (módulos más la zona de silencio). |
| `share/ShareCapture.kt`: `ShareCaptureHost`, `ShareRequest` | La captura fuera de pantalla con densidad 3, tema claro y espera a que la tarjeta deje de redibujarse. Gana el alto variable (D33). |
| `share/ShareFrame.kt` | El marco común. Gana un modo que crece con el contenido; el modo de tamaño fijo no cambia (D33). |
| `share/RecipeShareCard.kt`: `StepRow`, `compactStyle`, `weightNumber`, `recipeMethodText`, `RECIPE_STEP_ROW_HEIGHT` | Las filas de paso y los formateadores se conservan. Cambian la cabecera, la posición del QR y `QrCodeBox` (D30 a D32). |
| `share/QrImageReader.kt`: `MlKitQrImageReader.readBitmap`, `read`, `QR_IMAGE_MAX_SIDE` | Las lecturas de la medición y de los tests, incluida la ruta real de importación que reduce a 2048 px (D46). |
| `share/ShareLauncher.kt`, `ShareImages.kt`, `ShareSender.kt`, `ShareModels.kt` (`RecipeShareData`, `ShareTarget.Recipe`) | El PNG en `cacheDir/shared/` y el selector. No cambian: no suponen ningún tamaño. |
| `recipe/share/RecipeShareCodec.kt`: `uriFor`, `decode`, `inflate`, `MAX_URI_LENGTH` | El URI y la lectura no cambian. `encode` pasa a apoyarse en `encodeResult` (D38). |
| `recipe/share/RecipeSharePayload.kt`: `encodePayload`, `decodePayload`, `sharedGrindOf`, `writeGrind`; `RecipeShareBinary.kt`: `ShareFormat` (bits 5 a 7 de `flags`) | El formato `v=1` es el mismo. Cambia solo qué instrucciones se escriben (D36). |
| `ui/recipes/RecipeEditViewModel.kt` (`prepareShare`, `clearShare`, `buildSharePrompt`), `RecipeEditScreen.kt` (`AntModal` del recorte), `RecipeFormatters.kt` (`trimmedPartsText`), `RecipeImportScreen.kt` (`AntAlert` `recipe_import_trimmed`) | El flujo de compartir y los avisos. Cambian el motivo de "sin QR" y el texto del recorte parcial (D39, D40). |
| Tests: `QrTestSupport.kt` (`awaitQrModel`), `QrLegibilityTest` (`drawQr`, `sweep`), `RecipeQrLegibilityTest` (`worstCase`), `ShareCardsLayoutTest` (`capture`, `node`, `lines`, `brandInside`), `RecipeShareTest` (`show`, `save`, `pngBounds`, `sentStream`), `RecipeImportScreenTest` (`show`, `uriOf`), `RecipeShareFixtures` (`randomText`, `alternatingStages`, `worstCaseRecipe`, `withLongTexts`, `catalogGrinder`, `GOLDEN_V60_PAYLOAD`) | La base de los tests nuevos y adaptados. |

### 1.2 Falta o hay que corregir

- `QrCodeBox` reparte una caja fija de 432 px (`SHARE_QR_SIZE`) entre las celdas con `qrCellEdge`: el módulo mide
  432 / celdas px, que no es entero (5,61 px en la versión 13 y 3,96 px en la 21).
- El QR va a la derecha de los datos, en una fila de alto fijo (`Row(Modifier.height(SHARE_QR_SIZE))`), y la columna de
  datos tiene ese mismo alto con el texto del QR abajo (`Spacer(Modifier.weight(1f))`).
- El tamaño sale de `recipeCardSize` (4:5 hasta `RECIPE_CARD_COMPACT_MAX_STAGES` = 10 pasos, 9:16 después).
  `ShareCaptureHost` mide con `Constraints.fixed(request.size)` y `ShareFrame` usa `Modifier.size(size)` con
  `weight(1f)` en el cuerpo.
- `RecipeShareCodec.encode` devuelve `null` tanto si la receta no pasa `RecipeValidator` (línea 42) como si no cabe.
- `TrimLevel` quita todas las instrucciones de golpe (`keepInstructions` en `encodePayload`).
- `EncodedRecipe` no dice cuántas instrucciones se quitaron, `SharedRecipe` no sabe si faltan todas o algunas, y
  `trimmedPartsText` no tiene el texto "algunos pasos".
- `RecipeSharePrompt.withoutQr` es un `Boolean`: no dice si falta el QR por longitud o por datos no válidos.
- `MAX_URI_LENGTH = 331`.
- **Tests que cambian de expectativa.** El §5 pide que los tests existentes pasen "sin cambiar sus expectativas". Los
  únicos cambios son los que impone la enmienda (tamaño, posición del QR, recorte y el nuevo tope):

  | Test | Hoy | Con la enmienda | Fase |
  |---|---|---|---|
  | `ShareModelsTest.up to the compact limit the recipe card is 4 by 5 and 9 by 16 after it` | 4:5 hasta 10 pasos, 9:16 después, QR de 432 px | Se borra (no hay umbral). Lo sustituye `the qr box is 6 px per module with the quiet zone` | F0 |
  | `ShareModelsTest.a recipe request takes its size from the number of stages` | `SHARE_SIZE_TALL` con 11 pasos | `a recipe request fits its content from the minimum size and the other cards are fixed` | F0 |
  | `QrCodeTest.cell edges are integer and contiguous and cover the whole size` | `qrCellEdge` en `main` | Se borra: la función pasa a `QrTestSupport.kt` (D35) | F0 |
  | `ShareCardsLayoutTest.aShortRecipeCardIs1080x1350WithAQrOfAtLeast432px` | QR de 432 px o más, a la derecha | 1080 × 1350 (el mínimo) y QR de `(módulos + 8) × 6` px bajo los pasos. Se renombra `aShortRecipeCardKeepsTheMinimum1080x1350WithTheQrBelowTheSteps` | F0 |
  | `ShareCardsLayoutTest.aRecipeCardAtTheCompactLimitStaysAt4x5` | 10 pasos en 4:5 | Se sustituye por `theRecipeCardGrowsWithItsContent` | F0 |
  | `ShareCardsLayoutTest.aTwentyStepRecipeWithA60CharNameIs1080x1920AndShowsEveryStep` | 1080 × 1920 | Alto variable, URI en `MAX_URI_LENGTH`. Se renombra `theTallestRecipeCardShowsEveryStepAndTheQr` | F0 |
  | `ShareCardsLayoutTest.theQrIsBlackOnWhite` | Esquinas de la caja de 432 px y primer módulo con `qrCellEdge` | Las mismas comprobaciones con módulos de 6 px | F0 |
  | `RecipeQrLegibilityTest.theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70` | `MAX` = 331, 1080 × 1920, notas de 400 como máximo | `MAX` nuevo, alto mayor que 1350, búsqueda de notas hasta 1000, y una tercera lectura por `read(uri)` (D46) | F0 |
  | `QrLegibilityTest.maxUriLengthIsLegible` | Caja de 432 px | Módulos de 6 px | F0 |
  | `RecipeShareTest.aRecipeThatDoesNotFitSharesTheImageWithoutQr` | Nombre de 400 caracteres | Nombre de 800: con 700 de tope, 400 ya cabe | F0 |
  | `RecipeShareTest.aLongRecipeListsWhatWillBeMissingAndCancelSendsNothing` y `confirmingTheTrimDialogShares` | 5 pasos, notas de 300 e instrucciones de 100; PNG de 1080 × 1350 | Nombre de 500 e instrucciones de 200, para que sigan faltando todas las instrucciones; PNG de 1080 de ancho y 1350 o más de alto | F0 |
  | `RecipeShareTest.aTwentyStepRecipeIsSharedAt1080x1920` | 1080 × 1920 | 1080 de ancho y más de 1350 de alto. Se renombra `aTwentyStepRecipeGrowsAndItsQrDecodes` | F0 |
  | `RecipeImportScreenTest.showsTheTrimmedWarning` | Notas de 300 e instrucciones de 100 en 3 pasos | Notas de 600: con 700 de tope, la receta anterior ya cabe sin recorte | F0 |
  | `RecipeShareCodecTest.then instructions` | Todas a la vez | Solo la del último paso. Se renombra `then instructions go one at a time from the last stage that has one` | F1 |
  | `RecipeShareCodecTest`: `notes go first`, `then stage names`, `the trim bits and trimmed list only what was actually removed`, `encode is null only when the fully trimmed uri does not fit`, `steps, times, water, dose, temperature, method and grind are never trimmed` y `the classic V60 payload matches the golden bytes` | `TrimLevel` | `TrimPlan`. Las mismas expectativas y los mismos bytes congelados | F1 |
  | `RecipeShareCodecTest.the uri never exceeds the limit` | Topes de 60 a 500 | De 60 a 900 | F1 |

### 1.3 Discrepancias entre el spec y el código, o dentro del spec

| # | Spec | Código real o problema | Ajuste que propone el plan |
|---|---|---|---|
| 1 | §3.3: el QR va "centrado" en su franja. | Centrado, la caja cae en una x impar en todas las versiones, y al reducir la imagen al 50 % los bordes de los módulos quedan en medio de un píxel. | La caja se alinea a píxeles pares: como mucho 1 px a la izquierda del centro exacto (D31, P3). |
| 2 | §3.2: `encode(...)` devuelve `null` si "no cabe ni recortando". §4: el aviso distingue la receta inválida de la que no cabe. | `encode` también devuelve `null` con una receta inválida, y quien llama no puede distinguirlo. | `encodeResult` con `Encoded`, `TooLong` e `Invalid`; `encode` conserva la firma del §3.2 (D38). |
| 3 | §5: "los tests existentes pasan sin cambiar sus expectativas". | La enmienda cambia el tamaño de la tarjeta, la posición del QR, el recorte y el tope. | Solo cambian las expectativas de la tabla del §1.2, y cada cambio sale de la enmienda. |
| 4 | Encargo: capturas de la tarjeta en claro y oscuro. | El PNG se dibuja siempre con tema claro (D30 de SPEC-004). | En oscuro se comprueba que el PNG es idéntico al de claro, y se capturan en los dos temas el diálogo y la vista previa (D45, P1). |
| 5 | §3.5: la imagen leída se reduce a 2048 px. §3.3: la tarjeta crece sin tope. | La tarjeta de 20 pasos con el QR de la versión 21 mide unos 2 400 px y se reescala por 0,86 al importarla. | La medición de F0 y el test de peor caso leen también por esa ruta. Si falla, se para y se consulta (D46, P2). |
| 6 | §5: el QR de la receta de 20 pasos "con el URI en `MAX_URI_LENGTH`". | Con textos reales no se puede fijar el URI en un número exacto de caracteres. | Como en F4 del plan anterior: el URI queda entre `MAX_URI_LENGTH − 8` y `MAX_URI_LENGTH` sin recorte, en la versión de QR de `MAX_URI_LENGTH` (D43). |

## 2. Decisiones que completan el spec

Marcadas como "(concreta)" si eligen entre opciones que el spec deja abiertas, y como "(amplía)" si añaden algo que
el spec no dice.

### 2.1 QR y tarjeta

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D30 | Geometría del QR (concreta §3.3) | En `share/ShareSpec.kt`: `val SHARE_QR_MODULE = 2.dp` (6 px a `SHARE_DENSITY`) y `fun qrBoxSize(modules: Int): Dp = SHARE_QR_MODULE * (modules + 2 * QR_QUIET_ZONE)`. En `share/QrCode.kt` (sin `import android`): `QrMatrix` gana `val cells: Int get() = modules + 2 * QR_QUIET_ZONE`. `QrCodeBox(matrix: QrMatrix, modifier: Modifier = Modifier)` calcula `val modulePx = SHARE_QR_MODULE.roundToPx()` y mide `matrix.cells * modulePx` px (convertidos a dp con la densidad actual, para que la caja sea exactamente celdas × módulo). Pinta el fondo blanco y cada módulo oscuro en `((x + QR_QUIET_ZONE) * modulePx, (y + QR_QUIET_ZONE) * modulePx)` con lado `modulePx`, en `Color.Black`. Se borran `SHARE_QR_SIZE` y `qrCellEdge` de `main` (este pasa a `QrTestSupport.kt`, D35). Cajas: 246 px (versión 4), 270 (5), 342 (8), 462 (13), 510 (15), 534 (16), 630 (20) y 654 (21). | Bordes en píxeles enteros, sin antialias, a cualquier densidad. `QrCode.kt` sigue sin dependencias de Android: el `grep` del §7.2 del plan anterior también encuentra `import androidx`, así que los `Dp` van en `ShareSpec.kt`. |
| D31 | Caja en píxeles pares (amplía) | La franja coloca la caja con `private val EvenCenterHorizontally = Alignment.Horizontal { size, space, _ -> ((space - size) / 2) and 1.inv() }`. La franja empieza en x = 60 px (par), así que la caja queda en una x par, como mucho 1 px a la izquierda del centro. En vertical no hace falta nada: por encima de la caja todo mide un múltiplo de 6 px (interlineados de `AntTypography` en sp pares, huecos en dp pares, densidad 3). `ShareCardsLayoutTest.theQrBoxSitsOnEvenPixels` lo comprueba con varias recetas. F0 mide además en el lienzo la diferencia entre x = 212 y x = 213 (informativo). | Al reducir al 50 %, un módulo de 6 px que empieza en una x par queda en 3 px exactos. En una x impar, cada borde se mezcla con el módulo vecino. |
| D32 | Maqueta (concreta §3.3) | En `RecipeShareCard`, de arriba abajo: el nombre (`heading5`, 2 líneas como máximo, todo el ancho); 8 dp; `RecipeFacts` a todo el ancho (método, "dosis · agua · ratio", temperatura y molienda en 2 líneas como máximo), sin alto fijo ni texto del QR; 12 dp; una `StepRow` por paso, sin cambios; con QR, 12 dp y `QrStrip(matrix)`, una `Column` con `testTag("share_recipe_qr_strip")` y `EvenCenterHorizontally` que lleva `QrCodeBox` (`share_recipe_qr`), 4 dp y el texto del QR centrado (`share_recipe_qr_caption`, `CAPTION_MAX_LINES = 2`). Después, el pie del marco. Sin QR no hay franja. Los demás `testTag` no cambian. Alto estimado: receta corta (V60 clásica, 5 pasos) en el mínimo de 1350 px; la 4:6 del usuario, unos 1 660 px (QR de la versión 16); la de peor caso, unos 2 400 px (2 líneas de nombre y de molienda, 20 pasos y QR de la versión 21). | Es el orden del §3.3. Los huecos en dp pares mantienen D31. Las cifras son estimaciones: F0 las mide y la de peor caso va al §6 del spec (D43). |
| D33 | Alto variable (concreta §3.3) | `ShareRequest` gana `val fitsContent: Boolean get() = false`; `ShareRequest.Recipe` lo devuelve `true` y deja de sobrescribir `size`, que vale `SHARE_SIZE` y hace de mínimo. `ShareCaptureHost` mide con `Constraints(minWidth = width, maxWidth = width, minHeight = height, maxHeight = Constraints.Infinity)` si `fitsContent` y con `Constraints.fixed(width, height)` si no. La capa graba al tamaño medido. `ShareFrame` gana `fitContent: Boolean = false`. Con `true`: `Modifier.width(size.width).heightIn(min = size.height)` en lugar de `size(size)`, y dentro, en lugar de `weight(1f)`, una `Column(Modifier.heightIn(min = size.height − AccentHeight).padding(...), verticalArrangement = Arrangement.SpaceBetween)` con el cuerpo y, abajo, el hueco y el pie. El pie queda abajo en el alto mínimo. En ese modo el cuerpo no puede usar `weight` en vertical (se documenta en el KDoc). Las otras tres tarjetas usan el modo fijo, que no cambia. | Con alto máximo infinito, Compose reparte a los hijos con `weight` solo el alto mínimo, así que el cuerpo se recortaría. Descartado: medir con `maxIntrinsicHeight` y después con tamaño fijo. Funciona, pero depende de que todos los nodos de la tarjeta admitan medidas intrínsecas. |

### 2.2 Medición y `MAX_URI_LENGTH`

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D34 | Medición sobre la tarjeta real y regla del valor (concreta §3.2 y §6) | `RecipeQrLegibilityTest.cardSweep` captura con `ShareCaptureHost` la tarjeta de peor caso (D43) con un QR de un URI aleatorio de la longitud pedida. Cada muestra se lee tres veces: tal cual, tras reducir al 50 % y recomprimir en JPEG 70, y reducida a 2048 px de lado mayor (D46). **Pasada fina:** 650, 675, 700, 725 y 750 caracteres, 20 muestras cada una, con semillas distintas de las del 2026-11-18 (`10_000 + i`). **Pasada gruesa:** de 300 a 600 de 50 en 50, 10 muestras cada una. **Regla:** `MAX_URI_LENGTH` es el mayor de 700, 675 y 650 tal que esa longitud y todas las de la pasada fina por debajo dan 20 de 20 en las tres lecturas. 725 y 750 solo informan: el spec fija 700 como máximo. Después, `maxUriLengthIsLegibleOnTheCard` (20 muestras nuevas, semillas `20_000 + i`) tiene que pasar con el valor elegido; si falla, se baja un escalón. **Paradas** (P2): un fallo en la pasada gruesa (legibilidad no monótona, que bajar el tope no arregla); un valor por debajo de 650 que, tras medir 600 y 625, quede por debajo de 600; o un fallo solo en la lectura a 2048 px. | El §6 pide repetir la medición sobre la tarjeta real y bajar el valor si falla alguna muestra. Las semillas nuevas dan muestras independientes de las del lienzo. La pasada gruesa cubre la longitud de la receta del usuario (450, versión 16). |
| D35 | Herramientas de medición (amplía) | `QrLegibilityTest.sweep` lee argumentos del runner: `modulePx` (caja de `celdas × modulePx`; tiene prioridad sobre `qrSize`), `qrSize` (caja fija, como hoy), `from`, `to`, `step`, `samples` y `left` (x de la caja; por defecto `1080 − 48 − caja`). `cardSweep` se salta con `Assume` si no llega `lengths` (lista separada por comas) y lee `samples`; escribe en logcat (`QrLegibility`) una línea por longitud (`card <longitud> v<versión> asIs n/N reduced n/N import n/N qrTop=<px> height=<px>`) y guarda los PNG de las muestras que fallan en `getExternalFilesDir(null)/qr-legibility-card/`. `QrTestSupport.kt` recibe, en el mismo paquete (los `import` de los tests no cambian): `qrCellEdge` y `drawQr` (desde `main` y desde `QrLegibilityTest.kt`), y los nuevos `drawQrModules(canvas, matrix, left, top, modulePx)`, `uriOfLength(length, seed)`, `worstCaseRecipe(maxLength, grindText)` (D43), `findQrBox(bitmap, modulePx): Point` (el primer píxel `Color.BLACK` exacto recorriendo filas es la esquina del patrón de posición, porque el texto nunca es negro puro; la caja empieza 4 módulos antes) y `assertQrModules(bitmap, matrix, left, top, modulePx)` (cada bloque de `modulePx × modulePx` de las `cells × cells` celdas es todo negro o todo blanco según la matriz). La captura repetida usa una sola composición: `setContent` una vez, con la petición en un `mutableStateOf` que el test cambia muestra a muestra. | `createComposeRule` solo admite un `setContent` por test. Saltar `cardSweep` por defecto evita sumar unos minutos a cada suite completa; `maxUriLengthIsLegibleOnTheCard` queda como test de regresión. |
| D46 | Lectura por la ruta de importación (amplía) | Además de las dos lecturas del §5, la medición y `theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70` leen la tarjeta como la lee la importación: en el test de peor caso, guardando el PNG en `cacheDir` y llamando a `MlKitQrImageReader.read(Uri.fromFile(...))`; en la medición, con `Bitmap.createScaledBitmap` al tamaño de `scaledSize(w, h, QR_IMAGE_MAX_SIDE)`. | La tarjeta de peor caso pasa de 2048 px y la importación la escala por 0,86. Ninguna de las dos lecturas del §5 recorre ese camino. |

### 2.3 Códec y recorte

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D36 | Recorte de instrucciones de una en una (concreta §3.2) | En `RecipeSharePayload.kt`, `TrimLevel` se sustituye por `internal data class TrimPlan(val notes: Boolean = false, val droppedInstructions: Int = 0, val stageNames: Boolean = false)` con `companion object { val NONE = TrimPlan() }`. `encodePayload(recipe: Recipe, grinder: Grinder?, trim: TrimPlan): EncodedPayload` quita la instrucción de los últimos `droppedInstructions` pasos que la tienen (en orden de `startMs`). `EncodedPayload(bytes, trimmed, droppedInstructions: Int, keptInstructions: Int)`. `internal fun trimPlans(recipe: Recipe): Sequence<TrimPlan>` da, sin repetir: `NONE`; con notas, `TrimPlan(notes = true)`; para `k` de 1 a `n` instrucciones, `TrimPlan(notes, k)`; con nombres, `TrimPlan(notes, n, stageNames = true)`. `encodeResult` prueba la secuencia en orden y se queda con el primero que cabe. Bits de `flags`: los mismos (el 6 vale 1 si `droppedInstructions > 0`). Los nombres solo se quitan cuando ya no queda ninguna instrucción. | Es el orden del §3.2. Búsqueda lineal: son 23 compresiones como mucho, y la longitud comprimida no es estrictamente monótona, así que una búsqueda binaria podría quitar más instrucciones de las necesarias. Sin cambios de formato: `GOLDEN_V60_PAYLOAD` sigue valiendo y las apps instaladas leen los QR nuevos. |
| D37 | Recuento y recorte parcial (concreta §3.2) | En `recipe/share/SharedRecipe.kt`: `data class EncodedRecipe(val uri: String, val trimmed: Set<TrimmedPart>, val droppedInstructions: Int = 0, val keptInstructions: Int = 0)` con `val partialInstructions: Boolean get() = droppedInstructions > 0 && keptInstructions > 0`. `SharedRecipe` gana `val partialInstructions: Boolean get() = TrimmedPart.INSTRUCTIONS in trimmed && recipe.stages.any { !it.instruction.isNullOrBlank() }`, que es la regla del §3.2. `TrimmedPart.INSTRUCTIONS` sigue indicando que se quitó al menos una. | El mismo nombre a los dos lados permite un único `trimmedPartsText`. Los valores por defecto mantienen las llamadas existentes. Un QR de la app anterior (todas las instrucciones fuera) no es parcial y conserva su aviso. |
| D38 | Inválida frente a demasiado larga (concreta §4; discrepancia 2) | En `SharedRecipe.kt`: `sealed interface EncodeResult { data class Encoded(val encoded: EncodedRecipe) : EncodeResult; data object TooLong : EncodeResult; data object Invalid : EncodeResult }`. En `RecipeShareCodec`: `fun encodeResult(recipe: Recipe, grinder: Grinder?): EncodeResult`, `internal fun encodeResult(recipe, grinder, maxLength: Int): EncodeResult`, y `encode` con la firma del §3.2 como `(encodeResult(...) as? EncodeResult.Encoded)?.encoded`. Se valida primero: una receta inválida y además larga es `Invalid`. | Simétrico con `DecodeResult`, y probable en la JVM. El contrato del §3.2 no cambia. |

### 2.4 Compartir e importar

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D39 | Aviso al compartir (concreta §3.4 y §4) | `ui/recipes/RecipeSharePrompt.kt` (nuevo): `enum class NoQrReason { TOO_LONG, INVALID }`; `data class RecipeSharePrompt(val id: Long, val data: RecipeShareData, val trimmed: Set<TrimmedPart>, val partialInstructions: Boolean = false, val noQr: NoQrReason? = null)`, movida desde `RecipeEditViewModel.kt`, con `val withoutQr: Boolean get() = noQr != null` y `val needsConfirmation` como hoy; `internal fun recipeSharePrompt(id: Long, recipe: Recipe, grinder: Grinder?, grindText: String?): RecipeSharePrompt` (pura: `encodeResult` y `qrMatrix`); y `@StringRes internal fun RecipeSharePrompt.dialogTextRes(): Int` (`TOO_LONG` → `recipe_share_too_long_text`, `INVALID` → `recipe_share_invalid_text`, recorte → `recipe_share_trimmed_text`). `RecipeEditViewModel.buildSharePrompt` sigue leyendo la receta, el molino y `grindText`, y delega en `recipeSharePrompt`. `RecipeEditScreen` usa `dialogTextRes()` y `trimmedPartsText(prompt.trimmed, prompt.partialInstructions)`. | Una función pura deja probar en la JVM que una receta inválida y una que no cabe dan avisos distintos (§5), con el mismo patrón que `importNoticeText`. |
| D40 | Texto de lo recortado (concreta §3.4 y §3.6) | En `RecipeFormatters.kt`: `@StringRes internal fun trimmedPartsRes(parts: Set<TrimmedPart>, partialInstructions: Boolean): List<Int>` (pura; en el orden notas, instrucciones y nombres; con `partialInstructions`, `INSTRUCTIONS` da `recipe_trimmed_some_instructions`) y `trimmedPartsText(parts: Set<TrimmedPart>, partialInstructions: Boolean = false)`, que las une como hoy con `recipe_trimmed_list_two` y `recipe_trimmed_list_three`. `RecipeImportScreen` le pasa `ready.shared.partialInstructions`. | Una sola regla para compartir e importar, probable en la JVM. |
| D41 | Cadenas (concreta §3.4 y §4) | `recipe_trimmed_some_instructions`: "las instrucciones de algunos pasos" / "the instructions of some steps". `recipe_share_invalid_text`: el texto del §4 / "This recipe has invalid data and can't be imported with a QR code. Only the image will be shared.". En los dos archivos, junto a `recipe_trimmed_*` y `recipe_share_*`. | Los textos en español son los del spec. |

### 2.5 Pruebas, fixtures y verificación

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D42 | Receta 4:6 del usuario como fixture (concreta §5) | `test/.../recipe/share/RecipeShareFixtures.kt` gana `val femobookA2 = Grinder(id = 9, catalogKey = "femobook-a2", brand = "Femobook", model = "A2", units = listOf(GrindUnitLevel(GrindUnit.ROTATIONS), GrindUnitLevel(GrindUnit.CLICKS, perParent = 40)), scaleMin = 0.0, scaleMax = 120.0, owned = true)` y `fun fourSixUserRecipe(grinderId: Long = femobookA2.id): Recipe` con los valores literales de la base de datos del móvil del usuario: "4:6 · Equilibrado · Media", V60, 90 °C, 18,8 g, `GrindSetting(grinderId, 34.0, <copia de texto>)`, las notas y los 10 pasos con sus nombres e instrucciones (tabla del encargo). `androidTest/.../share/ShareTestRecipes.kt` (nuevo) repite esos valores para los tests instrumentados, que no ven las fixtures JVM. No se genera con `FourSix.recipe`. | Congela el caso exacto del criterio aunque cambien los textos del generador. Con el códec actual, su URI mide 450 caracteres (366 sin notas y 152 sin notas ni instrucciones). |
| D43 | Receta de peor caso de la tarjeta (concreta §5 y §6; discrepancia 6) | `worstCaseRecipe(maxLength, grindText)` en `QrTestSupport.kt`: 20 pasos alternos en segundos, nombre aleatorio de 60 caracteres (2 líneas), V60, 93 °C y notas aleatorias ajustadas por búsqueda binaria (hasta 1000 caracteres) para que el URI quede entre `maxLength − 8` y `maxLength` sin recorte. La tarjeta lleva un `grindText` que ocupa 2 líneas. Es la tarjeta más alta posible: su alto se anota en el plan y en el §6 del spec. Para el E2E, la receta se reimporta y se vuelve a codificar en el dispositivo, así que se toma un URI entre `MAX_URI_LENGTH − 24` y `MAX_URI_LENGTH − 8`, todavía en la misma versión de QR. | Las notas no salen en la tarjeta, pero llenan el QR. Con el margen del E2E, una diferencia de `zlib` entre la JVM y Android no provoca un recorte inesperado. |
| D44 | Impresión de URIs para el E2E (amplía) | `RecipeShareCodecTest` imprime con `println` el URI de la 4:6 del usuario y el de la receta de peor caso para el E2E (D43, con `grinder = null`). Quedan en `<system-out>` de `app/build/test-results/testDebugUnitTest/TEST-dev.josueamaya.pourline.recipe.share.RecipeShareCodecTest.xml`. | El E2E siembra las recetas con `pourline://` (§7.4), sin teclear 10 o 20 pasos por adb. |
| D45 | Capturas y tema (concreta lo pedido; discrepancia 4) | Por cada receta (corta, 4:6 del usuario y peor caso) y cada idioma (es, en): el PNG compartido en tema claro y en oscuro, sacado con `run-as`. En oscuro, `shasum -a 256` tiene que coincidir con el de claro. Además, en los dos temas e idiomas, el diálogo con "algunos pasos" y la vista previa con su aviso. Idioma: `adb shell cmd locale set-app-locales dev.josueamaya.pourline --locales es-ES` (y `en-US` para volver), o el idioma del sistema si el emulador no tiene ese comando. Tema: Ajustes → Apariencia. | La tarjeta se dibuja siempre en claro (SPEC-004, D30): el hash prueba que el tema del teléfono no la cambia. |

## 3. Fases

Orden: F0 → F1 → F2 → F3 → F4. F0 fija `MAX_URI_LENGTH`, y ninguna otra fase empieza antes. F2 depende de F1
(`encodeResult` y los recuentos). F3 depende de F1 (`SharedRecipe.partialInstructions`) y de F2 (`trimmedPartsText` y
la cadena nueva).

| Fase | Contenido | Tamaño | Estado |
|---|---|---|---|
| F0 | Línea base, tarjeta con el QR de 6 px y alto variable, medición sobre la tarjeta real y `MAX_URI_LENGTH` | L | Pendiente |
| F1 | Códec: instrucciones de una en una, recuentos, `EncodeResult` y la receta 4:6 del usuario | M | Pendiente |
| F2 | Compartir: motivo de "sin QR", "algunos pasos" y aviso de datos no válidos | M | Pendiente |
| F3 | Importar: aviso de recorte parcial | S | Pendiente |
| F4 | Verificación: suites, comprobaciones, E2E por adb, capturas y cierre | M | Pendiente |

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app`, nunca con `./gradlew build`
ni con builds limpios.

- **Compilar:** `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`.
- **Tests JVM:** `./gradlew :app:testDebugUnitTest --tests "<clase>"`.
- **Tests instrumentados:** los que leen QR con ML Kit van en `emulator-5560` (AVD `SG_API36_play`, el único que
  descarga el modelo); el resto, en `emulator-5556`. Con
  `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<clases>`.
  Nunca dos suites a la vez en el mismo emulador. `emulator-5554` no se toca. El teléfono del usuario (Xiaomi) no se
  toca: las pruebas en él las hace el usuario (§7.5).
- **Mediciones con argumentos:** con la app y el APK de test instalados (`./gradlew :app:installDebug :app:installDebugAndroidTest`),
  por `adb -s emulator-5560 shell am instrument -w -e class <clase>#<método> -e <argumento> <valor> dev.josueamaya.pourline.test/dev.josueamaya.pourline.PourlineTestRunner`,
  para que los PNG de `getExternalFilesDir` sigan ahí al terminar.
- **Sin ARTEMIS.** Tests JVM, tests instrumentados y adb. Los tests de interfaz localizan por `testTag` y leen los
  textos con `context.getString(...)`.
- **Fallos previos que no bloquean:** `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap`, los dos de
  `SessionPerformanceTest`, 3 de UI intermitentes en `emulator-5560`, y en `emulator-5556` los 14 que necesitan el
  modelo de Play Services.
- **Lint:** `./gradlew :app:lintDebug` en F0, F2, F3 y F4, sin errores ni avisos nuevos respecto a la línea base.
- **Commits:** uno por fase, en Conventional Commits y en inglés, sin `Co-Authored-By` ni atribución de herramientas,
  nunca en `main` ni `develop`. Antes de F0, el spec y este plan, cuando se aprueben:
  `docs(specs): add SPEC-009 amendment 1 (large QR) and its implementation plan`.

### F0. Línea base, tarjeta con el QR de 6 px y medición de `MAX_URI_LENGTH`

**Objetivo:** construir la tarjeta del §3.3 de la enmienda, medir sobre ella la legibilidad y fijar `MAX_URI_LENGTH`.
El recorte sigue siendo el antiguo hasta F1.

**Pasos:**

1. **Línea base, sin tocar código.** `adb devices -l` (`emulator-5556` y `emulator-5560`; si aparece otro dispositivo
   además de `emulator-5554` y del teléfono, se pregunta). `./gradlew :app:testDebugUnitTest` y `./gradlew :app:lintDebug`.
   En `emulator-5556`: `ShareCardsLayoutTest` y `ShareIntentTest`. En `emulator-5560`: `QrLegibilityTest`,
   `RecipeQrLegibilityTest`, `RecipeShareTest`, `RecipesImportTest`, `RecipeIntentImportTest` y `RecipeImportScreenTest`.
   Las cifras van a la cabecera.
2. **Herramientas (D35).** Mover `qrCellEdge` y `drawQr` a `QrTestSupport.kt` y añadir los argumentos de `sweep` y los
   ayudantes nuevos. Medición informativa de D31, en el lienzo, a 700 caracteres con 20 muestras, una vez con
   `-e left 212` y otra con `-e left 213` (además de `-e modulePx 6 -e from 700 -e to 700 -e samples 20`). El resultado
   va a la sección "Resultado de F0".
3. **Geometría y tarjeta (D30 a D33):** `ShareSpec.kt`, `QrCode.kt`, `RecipeShareCard.kt`, `ShareFrame.kt` y
   `ShareCapture.kt`.
4. **Tests de la tarjeta:** los de la tabla de abajo, en verde con `MAX_URI_LENGTH` todavía en 331.
5. **Medición (D34, D46).** Pasada gruesa (`-e lengths 300,350,400,450,500,550,600 -e samples 10`) y pasada fina
   (`-e lengths 650,675,700,725,750 -e samples 20`) de `RecipeQrLegibilityTest#cardSweep` en `emulator-5560`. Se lee
   `adb -s emulator-5560 logcat -d -s QrLegibility`, y los PNG que fallen se bajan con
   `adb -s emulator-5560 pull /sdcard/Android/data/dev.josueamaya.pourline/files/qr-legibility-card`.
6. **Valor.** Aplicar la regla de D34, escribir `MAX_URI_LENGTH` y su KDoc (medido sobre la tarjeta real con módulos de
   6 px, con la fecha y la tabla resumida), y ajustar los tests que dependen del tope (tabla del §1.2). Ejecutar
   `maxUriLengthIsLegibleOnTheCard` y `QrLegibilityTest.maxUriLengthIsLegible`.
7. **Paradas.** Si se cumple alguna de las de D34, se detiene el plan y se consulta al usuario con las tablas (P2).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `share/ShareSpec.kt` | Fuera `SHARE_SIZE_TALL`, `SHARE_QR_SIZE`, `RECIPE_CARD_COMPACT_MAX_STAGES` y `recipeCardSize`. Nuevos `SHARE_QR_MODULE` y `qrBoxSize` (D30). KDoc de `SHARE_SIZE`: también es el mínimo de la tarjeta de receta. |
| `share/QrCode.kt` | `QrMatrix.cells`; fuera `qrCellEdge` (D30). |
| `share/RecipeShareCard.kt` | Maqueta de D32, `QrStrip`, `EvenCenterHorizontally` (D31) y `QrCodeBox` con módulos de 6 px (D30). KDoc. |
| `share/ShareFrame.kt` | Parámetro `fitContent` (D33). |
| `share/ShareCapture.kt` | `ShareRequest.fitsContent`, `Recipe` sin `size` propio y las restricciones de `ShareCaptureHost` (D33). KDoc (ya no "1080 × 1920 en la receta larga"). |
| `recipe/share/RecipeShareCodec.kt` | `MAX_URI_LENGTH` medido y su KDoc. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../share/QrCodeTest.kt` | Fuera `` `cell edges are integer and contiguous and cover the whole size` ``. Nuevo `` `the box side is the modules plus four quiet modules per side` `` (`cells` de las versiones 1, 13 y 21). |
| `test/.../share/ShareModelsTest.kt` | Fuera `` `up to the compact limit the recipe card is 4 by 5 and 9 by 16 after it` ``. `` `a recipe request takes its size from the number of stages` `` pasa a `` `a recipe request fits its content from the minimum size and the other cards are fixed` ``. Nuevo `` `the qr box is 6 px per module with the quiet zone` `` (`qrBoxSize(69)` a 462 px y `qrBoxSize(101)` a 654 px con `SHARE_DENSITY`). |
| `androidTest/.../share/ShareCardsLayoutTest.kt` (`emulator-5556`) | `aShortRecipeCardKeepsTheMinimum1080x1350WithTheQrBelowTheSteps` (V60 de 4 pasos: 1080 × 1350, caja de `cells × 6` px, cada paso por encima de la franja y la franja por encima de la marca). `theRecipeCardGrowsWithItsContent` (4, 10 y 20 pasos con el mismo nombre: alto no decreciente y, con 20, mayor que 1350). `theTallestRecipeCardShowsEveryStepAndTheQr` (D43 con `MAX_URI_LENGTH`: nombre en 2 líneas, los 20 pasos por encima de la franja, caja de 654 px con `MAX_URI_LENGTH` = 700, marca dentro del alto; escribe el alto en logcat, etiqueta `RecipeCard`). `everyQrModuleIs6x6AndTheBoxIsModulesPlus8Times6` (criterio del §5: tamaño del nodo `share_recipe_qr` y `assertQrModules` sobre el bitmap capturado, en una receta corta y en la de peor caso). `theQrBoxSitsOnEvenPixels` (corta, 4:6 del usuario y peor caso, con y sin molienda: `positionInRoot` de la caja con x e y pares) (D31). `theHeaderUsesTheFullWidth` (el nombre largo ocupa el ancho útil de 960 px). `aRecipeCardWithoutQrHasNeitherQrNorCaption` (también sin `share_recipe_qr_strip`). `theQrIsBlackOnWhite` con 6 px. `theRecipeCardHasNoNotesNorInstructions`, `missingFactsAreNotShown`, `aDeletedGrinderShowsItsLabel`, `aLiveGrinderShowsTheEditorLabel` y los seis de las otras tarjetas, sin cambios. |
| `androidTest/.../share/QrTestSupport.kt` | Ayudantes de D35 y `worstCaseRecipe` (D43). |
| `androidTest/.../share/ShareTestRecipes.kt` (nuevo) | La 4:6 del usuario para los tests instrumentados (D42). |
| `androidTest/.../share/QrLegibilityTest.kt` (`emulator-5560`) | `sweep` con los argumentos de D35. `maxUriLengthIsLegible` con módulos de 6 px. |
| `androidTest/.../share/RecipeQrLegibilityTest.kt` (`emulator-5560`) | `theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70` con `worstCaseRecipe`, alto mayor que 1350 y la lectura por `read(uri)` (D46). `cardSweep` (D34, D35). `maxUriLengthIsLegibleOnTheCard` (20 muestras en la tarjeta real, tres lecturas). |
| `androidTest/.../ui/recipes/RecipeShareTest.kt` (`emulator-5560`) | Los cambios de la tabla del §1.2. Nuevo `theSharedPngHas6x6QrModules` (criterio del §5 "en el PNG generado": el PNG que entrega `FileProvider`, `findQrBox` y `assertQrModules` con la matriz del URI que da `RecipeShareCodec.encode` sobre la receta guardada). |
| `androidTest/.../ui/recipes/RecipeImportScreenTest.kt` (`emulator-5560`) | `showsTheTrimmedWarning` con notas de 600 (tabla del §1.2). |

**Verificación:**
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.share.*" --tests "dev.josueamaya.pourline.recipe.share.*"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.share.ShareCardsLayoutTest,dev.josueamaya.pourline.share.ShareIntentTest`.
`ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.share.QrLegibilityTest,dev.josueamaya.pourline.share.RecipeQrLegibilityTest,dev.josueamaya.pourline.ui.recipes.RecipeShareTest,dev.josueamaya.pourline.ui.recipes.RecipesImportTest,dev.josueamaya.pourline.RecipeIntentImportTest,dev.josueamaya.pourline.ui.recipes.RecipeImportScreenTest`.
`./gradlew :app:lintDebug`. `grep -rn "import android" app/src/main/kotlin/dev/josueamaya/pourline/share/QrCode.kt` no
encuentra nada. `git diff develop -- app/src/main/kotlin/dev/josueamaya/pourline/share/BrewShareCard.kt app/src/main/kotlin/dev/josueamaya/pourline/share/CoffeeShareCard.kt app/src/main/kotlin/dev/josueamaya/pourline/share/CompareShareCard.kt`
está vacío.

**Criterio de salida:** `MAX_URI_LENGTH` fijado con la regla de D34 y escrito en la cabecera, con las tablas de las dos
pasadas en "Resultado de F0"; todo lo anterior en verde; lint como la línea base.

Commit sugerido: `feat(share): draw the recipe QR with 6 px modules on a card that grows with its content`.

### F1. Códec: instrucciones de una en una, recuentos y `EncodeResult`

**Objetivo:** el recorte del §3.2 de la enmienda, el recuento para quien comparte, el recorte parcial para quien
importa y la distinción entre receta inválida y receta que no cabe. Todo JVM puro.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `recipe/share/RecipeSharePayload.kt` | `TrimPlan` en lugar de `TrimLevel`, `trimPlans` y `EncodedPayload` con los recuentos (D36). |
| `recipe/share/RecipeShareCodec.kt` | `encodeResult` (pública y la interna con `maxLength`) y `encode` sobre ella (D38). |
| `recipe/share/SharedRecipe.kt` | `EncodedRecipe` con `droppedInstructions`, `keptInstructions` y `partialInstructions`; `SharedRecipe.partialInstructions` (D37); `EncodeResult` (D38). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/share/RecipeShareFixtures.kt` | `femobookA2` y `fourSixUserRecipe()` (D42). `withLongTexts` admite el número de pasos. |
| `test/.../recipe/share/RecipeShareCodecTest.kt` | **Recorte:** `` `notes go first` `` (con `TrimPlan`); `` `then instructions go one at a time from the last stage that has one` `` (tope = longitud sin notas − 1: solo falta la instrucción del último paso que la tenía); `` `only the instructions needed are removed` `` (6 instrucciones aleatorias: primero comprueba que la longitud baja con cada una que se quita y después, para cada `k`, con el tope en esa longitud, `droppedInstructions == k`, y las que quedan son las `6 − k` primeras); `` `stage names go only after every instruction` ``; `` `the trim bits and trimmed list only what was actually removed` `` (también el bit 6 con un recorte parcial); `` `encode is null only when the fully trimmed uri does not fit` ``; `` `steps, times, water, dose, temperature, method and grind are never trimmed` ``; `` `the uri never exceeds the limit` `` (de 60 a 900). **Recuento y parcial:** `` `the sharer is told how many instructions were removed and how many were kept` ``; `` `a partially trimmed qr is partial on import and a fully trimmed one is not` `` (`SharedRecipe.partialInstructions`; el QR con todas fuera es el que generaba la app anterior). **Inválida frente a larga:** `` `an invalid recipe and a recipe that does not fit give different results` `` (`Invalid` y `TooLong`; `encode` da `null` en los dos); `` `an invalid recipe is invalid even when it is also too long` ``. **Receta del usuario:** `` `the user's 4:6 recipe is shared without trimming` `` (con `femobookA2`: `EncodeResult.Encoded`, `trimmed` vacío y `droppedInstructions == 0`; ida y vuelta con notas, los 10 nombres e instrucciones y la molienda `femobook-a2` con 34; imprime la longitud y el URI, D44). **E2E:** `` `the worst case uri for the end to end check is printed` `` (D43 y D44). **Contrato:** `` `the classic V60 payload matches the golden bytes` `` y `` `the golden v1 uri decodes to the classic V60` ``, sin cambios. |
| `test/.../recipe/share/RecipeSharePurityTest.kt` | Sin cambios; tiene que seguir pasando. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.share.*" --tests "dev.josueamaya.pourline.share.*"`.
`./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` (`RecipeEditViewModel` y los tests
instrumentados siguen usando `encode`). Sin instrumentados: F1 no cambia la interfaz.

**Criterio de salida:** todo en verde; `GOLDEN_V60_PAYLOAD` sin tocar; en el informe, la longitud del URI de la 4:6 del
usuario (450 esperados) y los dos URIs de D44.

Commit sugerido: `feat(recipes): trim step instructions one at a time to fit the QR`.

### F2. Compartir: motivo de "sin QR", "algunos pasos" y datos no válidos

**Objetivo:** el diálogo dice "las instrucciones de algunos pasos" si se quitaron solo algunas, y una receta inválida
tiene su propio aviso.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/recipes/RecipeSharePrompt.kt` (nuevo) | `NoQrReason`, `RecipeSharePrompt`, `recipeSharePrompt` y `dialogTextRes` (D39). |
| `ui/recipes/RecipeEditViewModel.kt` | Sin la clase `RecipeSharePrompt`; `buildSharePrompt` delega en `recipeSharePrompt` (D39). |
| `ui/recipes/RecipeEditScreen.kt` | El texto del `AntModal` con `dialogTextRes()` y `trimmedPartsText(prompt.trimmed, prompt.partialInstructions)`. Las anclas del recorrido guiado no cambian. |
| `ui/recipes/RecipeFormatters.kt` | `trimmedPartsRes` y el parámetro `partialInstructions` de `trimmedPartsText` (D40). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `recipe_trimmed_some_instructions` y `recipe_share_invalid_text` (D41). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../ui/recipes/RecipeSharePromptTest.kt` (nuevo) | `` `a recipe that fits shares without asking` `` (QR presente, `needsConfirmation` falso). `` `a trimmed recipe asks with the trimmed text` ``. `` `partially trimmed instructions are flagged` ``. `` `an invalid recipe and one that does not fit give different notices` `` (criterio del §5: `INVALID` → `recipe_share_invalid_text`, `TOO_LONG` → `recipe_share_too_long_text`, distintos, y los dos sin QR y con confirmación). `` `the trimmed parts keep their order and use the some steps text when partial` `` (`trimmedPartsRes`). |
| `androidTest/.../ui/recipes/RecipeShareTest.kt` (`emulator-5560`) | `aLongRecipeKeepsTheFirstInstructionsAndListsSomeSteps` (10 pasos, notas de 300 e instrucciones de 120: el test comprueba antes con el códec que el recorte es parcial; el diálogo dice "the notes and the instructions of some steps"; al compartir, el QR leído conserva las primeras instrucciones). `anInvalidSavedRecipeHasItsOwnNoticeAndSharesWithoutQr` (dosis de 70 g guardada con el repositorio, que no valida: diálogo con `recipe_share_invalid_text` y ML Kit no encuentra ningún QR en el PNG). `theUsersFourSixRecipeSharesWithoutAskingAndKeepsNotesAndInstructions` (criterio del §5: `femobookA2` en la base de datos, sin diálogo, y el QR del PNG da las notas, las 10 instrucciones, `femobook-a2` y 34). `aLongRecipeListsWhatWillBeMissingAndCancelSendsNothing` sigue diciendo "the notes and the step instructions". |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.ui.recipes.*" --tests "dev.josueamaya.pourline.recipe.share.*"`.
`ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipeShareTest`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipeEditScreenTest,dev.josueamaya.pourline.ui.tour.RecipesHistoryTourTest`.
`./gradlew :app:lintDebug` (sin `MissingTranslation` ni `ExtraTranslation` en las cadenas nuevas).

**Criterio de salida:** todo en verde, y `RecipeEditScreenTest` y el recorrido de Recetas sin cambios.

Commit sugerido: `feat(recipes): tell partial instruction trims and invalid recipes apart when sharing`.

### F3. Importar: aviso de recorte parcial

**Objetivo:** la vista previa dice "instrucciones de algunos pasos" cuando el QR trae el bit 6 y al menos una
instrucción.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/recipes/RecipeImportScreen.kt` | `trimmedPartsText(ready.shared.trimmed, ready.shared.partialInstructions)` en el aviso `recipe_import_trimmed` (D40). |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/recipes/RecipeImportScreenTest.kt` (`emulator-5560`) | `showsThePartialInstructionsWarning` (URI con recorte parcial generado con el códec: el aviso contiene `recipe_trimmed_some_instructions` y la vista previa muestra las instrucciones que quedaron). `aQrWithEveryInstructionRemovedSaysTheStepInstructions` (nombre de 500 e instrucciones de 200: el aviso contiene `recipe_trimmed_instructions`). `showsTheTrimmedWarning`, como lo dejó F0. |

**Verificación:** `./gradlew :app:compileDebugKotlin`.
`ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipeImportScreenTest,dev.josueamaya.pourline.ui.RecipeImportNavigationTest`.
`./gradlew :app:lintDebug`.

**Criterio de salida:** todo en verde.

Commit sugerido: `feat(recipes): warn about partially trimmed instructions on import`.

### F4. Verificación y cierre

**Objetivo:** el §7 completo: suites, comprobaciones, E2E por adb, capturas, prueba manual del usuario y cierre.

**Archivos:** este plan (cabecera, "Resultado de F0" y "Resultado de F4") y, como pide el propio §6 del spec, la nota
de cierre de la enmienda en el §6: el valor final de `MAX_URI_LENGTH` y el alto de la tarjeta con la receta de peor
caso. Si F0 bajó el tope, también el valor del §3.2. La fila Estado del spec solo cambia si el usuario lo pide.

**Verificación:** el §7.

Commit sugerido: `test(recipes): verify the large recipe QR end to end`.

### 3.1 Cadenas nuevas (`values` / `values-en`)

| Clave | Español | Inglés | Fase |
|---|---|---|---|
| `recipe_trimmed_some_instructions` | las instrucciones de algunos pasos | the instructions of some steps | F2 |
| `recipe_share_invalid_text` | Esta receta tiene datos no válidos y no se puede importar con QR. Se compartirá solo la imagen. | This recipe has invalid data and can't be imported with a QR code. Only the image will be shared. | F2 |

No se borra ninguna cadena. `recipe_card_qr_caption`, `recipe_share_too_long_text` y `recipe_trimmed_instructions`
siguen en uso.

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| El icono "Compartir" genera la imagen con la receta y el QR y abre el selector con `ACTION_SEND` `image/png`, en Básico y Experto, también con predefinidas | F0 (adapta tamaños), F4 | `RecipeShareTest.aSavedRecipeSharesAPngInBasic` / `…InExpert` y `aBuiltInRecipeCanBeShared`; §7.4 |
| **Enmienda 1:** la imagen mide 1080 px de ancho y al menos 1350 de alto, crece con el contenido y muestra todos los pasos y el QR de una receta de 20 pasos con el URI en `MAX_URI_LENGTH` | F0 | `ShareCardsLayoutTest.aShortRecipeCardKeepsTheMinimum1080x1350…`, `theRecipeCardGrowsWithItsContent` y `theTallestRecipeCardShowsEveryStepAndTheQr`; `RecipeShareTest.aTwentyStepRecipeGrowsAndItsQrDecodes`; §7.4 (PNG de 1080 × ~2 400) |
| **Enmienda 1:** en el PNG generado, cada módulo mide 6 × 6 px exactos y la caja `(módulos + 8) × 6` px, comprobado leyendo los píxeles | F0 | `ShareCardsLayoutTest.everyQrModuleIs6x6AndTheBoxIsModulesPlus8Times6` (bitmap capturado) y `RecipeShareTest.theSharedPngHas6x6QrModules` (PNG de `FileProvider`) |
| **Enmienda 1:** la receta 4:6 equilibrada e intensidad media de 18,8 g con molino del catálogo se comparte sin recortar nada | F1, F2, F4 | `RecipeShareCodecTest.the user's 4:6 recipe is shared without trimming`; `RecipeShareTest.theUsersFourSixRecipeSharesWithoutAskingAndKeepsNotesAndInstructions`; §7.4 (reimportada desde la imagen, también reducida y en JPEG 70); §7.5 |
| Unitario: ida y vuelta de predefinidas y de una receta con todos los campos | F1 (sin cambios) | `RecipeShareCodecTest` (ida y vuelta y contrato) |
| **Enmienda 1:** recorte en orden notas → instrucciones de una en una desde el último paso → nombres, con bits y `trimmed` correctos; solo las instrucciones necesarias; `null` solo sin cabida | F1 | `RecipeShareCodecTest` (bloque de recorte, `only the instructions needed are removed`) |
| **Enmienda 1:** una receta inválida y una que no cabe dan avisos distintos | F1, F2 | `RecipeShareCodecTest.an invalid recipe and a recipe that does not fit give different results`; `RecipeSharePromptTest.an invalid recipe and one that does not fit give different notices`; `RecipeShareTest.anInvalidSavedRecipeHasItsOwnNoticeAndSharesWithoutQr` |
| Unitario: `NotPourline`, `NewerVersion`, `Corrupt` e `Invalid` | F1 (sin cambios) | `RecipeShareCodecTest` (errores) |
| Unitario: molienda en los tres casos y sin molienda | F4 (regresión) | `SharedGrindResolutionTest` |
| Unitario: idéntica y sufijos " (2)" y " (3)" | F4 (regresión) | `RecipeImportRulesTest` |
| Unitario: `recipe.share` no importa `android.*` | F1, F4 | `RecipeSharePurityTest`; §7.2 |
| Instrumentado: el QR del PNG de peor caso (URI en `MAX_URI_LENGTH`, 20 pasos, nombre de 60) se lee con ML Kit tal cual y tras 50 % + JPEG 70 | F0 | `RecipeQrLegibilityTest.theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70` (más la lectura por la importación, D46) y `maxUriLengthIsLegibleOnTheCard` |
| Instrumentado: compartir envía por `ShareSender` un `ACTION_SEND` con un PNG legible por `FileProvider` | F0, F2 | `RecipeShareTest.thePngIsReadableThroughTheFileProviderAndItsQrDecodesToTheRecipe` |
| Instrumentado: `ACTION_VIEW` y `ACTION_SEND image/*` abren `RecipeImport`; Guardar añade la receta con "Receta añadida" | F0 (regresión con la tarjeta nueva), F4 | `RecipeIntentImportTest`; `RecipeImportNavigationTest.savingReturnsToTheListWithRecipeAdded` |
| Instrumentado: con una sesión, el intent no navega y la vista previa se abre al terminar | F4 (regresión) | `RecipeIntentImportTest.withASession…`; `RecipeImportNavigationTest.withASession…` |
| Instrumentado: una imagen sin QR y un QR ajeno muestran sus mensajes | F0 (regresión), F4 | `RecipesImportTest`; `RecipeIntentImportTest` |
| El manifiesto final no contiene `INTERNET` ni `CAMERA` | F4 | `ManifestPermissionsTest`; §7.2 |
| Textos nuevos en `values` y `values-en` | F2 | §3.1; `:app:lintDebug` sin `MissingTranslation` ni `ExtraTranslation` |
| `:app:lintDebug` y los tests existentes pasan sin cambiar sus expectativas | Todas, F4 | §7.1; los únicos cambios de expectativa son los de la tabla del §1.2 (discrepancia 3) |
| Manual con dos móviles: WhatsApp, cuatro entradas y conversión de molienda | F4 (usuario) | §7.5, con la receta 4:6 del usuario y la tarjeta de peor caso |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | Los 6 px por módulo no bastan sobre la tarjeta real (posición, texto alrededor) aunque bastaran en el lienzo. | D31 alinea la caja a píxeles pares y F0 mide sobre la tarjeta real con semillas nuevas (D34). Si falla a 700, se baja el tope; por debajo de 600, se para (P2). |
| R2 | La legibilidad no es monótona: falla una longitud intermedia que bajar el tope no arregla. | La pasada gruesa de D34 cubre de 300 a 600 (incluida la receta del usuario, 450). Un fallo ahí para el plan (P2). |
| R3 | La tarjeta de más de 2048 px se reescala por 0,86 al importarla y deja de leerse. | La tercera lectura de D46 en la medición y en el test de peor caso. Si solo falla esa, se para (P2): las salidas son acortar la tarjeta por debajo de 2048 px o subir `QR_IMAGE_MAX_SIDE`, que cambia el §3.5. |
| R4 | Las apps de mensajería reducen las imágenes altas por su cuenta. | La prueba del 50 % es más dura que una reducción típica: una tarjeta de 2 400 px limitada a 1 600 px de lado mayor queda al 67 % (módulo de 4 px). La prueba manual por WhatsApp lo confirma (§7.5). |
| R5 | Las redes que recortan a 4:5 (por ejemplo, el feed de Instagram) cortan la franja del QR de una tarjeta alta. | El alto variable es una decisión del spec (§3.3) y la prueba manual es por WhatsApp. Se anota en el §8. |
| R6 | El modo `fitContent` de `ShareFrame` cambia las otras tres tarjetas. | El modo fijo queda igual y es el valor por defecto (D33). `ShareCardsLayoutTest` conserva sus seis casos de cafés, preparaciones y comparaciones, y el §7.2 comprueba que las tres tarjetas no cambian. |
| R7 | Un nodo de la tarjeta exige alto acotado (`fillMaxHeight`, `verticalScroll`) y falla con alto infinito. | La tarjeta de receta no tiene ninguno. `ShareCardsLayoutTest` captura las variantes de la tarjeta en F0. |
| R8 | Una app ya instalada lee un QR con recorte parcial y avisa de que faltan "las instrucciones de los pasos", aunque traiga algunas. | Inofensivo: el formato `v=1` no cambia y la app antigua lee las instrucciones que vienen. Las versiones nuevas dicen "algunos pasos" (D37). |
| R9 | La compresión de la JVM y la de Android dan longitudes distintas cerca del tope. | Los tests nunca comparan bytes comprimidos (D23). Los instrumentados codifican en el dispositivo y el E2E deja margen (D43). |
| R10 | El Code Scanner lee peor un QR de la versión 21 mostrado en otra pantalla que el de la versión 13 que probó el usuario. | La prueba manual del §7.5 escanea la tarjeta de peor caso y la 4:6 desde otra pantalla. Si falla, se consulta antes de cerrar: bajar el tope es un cambio de una constante y sus tests. |
| R11 | Medir sube el tiempo de las suites. | `cardSweep` se salta sin argumentos (D35). `maxUriLengthIsLegibleOnTheCard` hace 20 capturas, unos 30 s. |
| R12 | El emulador no tiene `cmd locale set-app-locales`. | Se cambia el idioma del sistema desde Ajustes (D45). |

## 6. Preguntas para el usuario

Cada una tiene una propuesta. Si se aprueba el plan sin comentarios, se aplica la propuesta.

1. **P1. Capturas en oscuro (D45).** La tarjeta se dibuja siempre con tema claro (D30 de SPEC-004), así que el PNG en
   oscuro es el mismo que en claro. *Propuesta: hacer las capturas en los dos temas y comprobar con un hash que el
   PNG es idéntico, y capturar en oscuro el diálogo de compartir y la vista previa.* Alternativa: que la tarjeta siga
   el tema del teléfono, lo que cambia SPEC-004 y queda fuera de esta enmienda.
2. **P2. Si la medición de F0 sale peor de lo esperado (D34, D46).** *Propuesta: parar y consultar con las tablas si
   `MAX_URI_LENGTH` queda por debajo de 600, si falla una longitud intermedia o si solo falla la lectura a 2048 px.*
   Las salidas posibles se deciden entonces: módulos de 7 px (caja de hasta 763 px), un tope menor con 6 px, una
   tarjeta más baja que 2048 px o subir `QR_IMAGE_MAX_SIDE` (§3.5). Entre 600 y 700 se aplica la regla de D34 sin
   preguntar, como permite el §6.
3. **P3. QR alineado a píxeles pares (D31).** *Propuesta: aceptar que la caja quede hasta 1 px a la izquierda del centro
   exacto.* Alternativa: centrarla al píxel, en una x impar, lo que empeora la lectura tras reducir la imagen a la
   mitad (F0 lo cuantifica).

## 7. Verificación final

### 7.1 Suites

```
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Una suite cada vez, nunca dos en el mismo emulador. Se compara con la línea base de la cabecera:

- **Tests:** los totales suben por los tests nuevos y bajan por los borrados de la tabla del §1.2, y aparece un saltado
  más (`cardSweep`). No cambia ninguna otra expectativa.
- **Fallos que no bloquean:** los de la lista del §3, si siguen siendo los mismos. En `emulator-5556`, los que
  necesitan el modelo de Play Services, que ahora son más (los tests nuevos que leen QR).
- **Lint:** 2 errores y 21 avisos, como la línea base, sin `MissingTranslation`, `ExtraTranslation` ni
  `UnusedResources` nuevos.

### 7.2 Comprobaciones

- `grep -rn "import android" app/src/main/kotlin/dev/josueamaya/pourline/recipe/share app/src/main/kotlin/dev/josueamaya/pourline/share/QrCode.kt`
  no encuentra nada.
- `grep -rn "SHARE_SIZE_TALL\|SHARE_QR_SIZE\|RECIPE_CARD_COMPACT_MAX_STAGES\|recipeCardSize\|TrimLevel" app/src` no
  encuentra nada, y `grep -rn "qrCellEdge" app/src/main` tampoco.
- `git diff develop -- app/src/main/kotlin/dev/josueamaya/pourline/recipe/share/RecipeShareBinary.kt` está vacío: el
  formato `v=1` no cambia. `the classic V60 payload matches the golden bytes` pasa.
- `git diff develop -- app/src/main/kotlin/dev/josueamaya/pourline/share/BrewShareCard.kt app/src/main/kotlin/dev/josueamaya/pourline/share/CoffeeShareCard.kt app/src/main/kotlin/dev/josueamaya/pourline/share/CompareShareCard.kt app/src/main/AndroidManifest.xml`
  está vacío.
- `./gradlew :app:processDebugManifest`: el manifiesto fusionado sigue sin `android.permission.INTERNET` ni
  `android.permission.CAMERA`.

### 7.3 Rendimiento (informativo)

La enmienda no pide medidas. A título informativo, en la cabecera:

- El tiempo de generar el PNG de la tarjeta de peor caso (`RecipeShareTest`, mediana de 5), frente a los 286 a 622 ms
  de las imágenes de SPEC-004.
- El tiempo de `encodeResult` con el recorte más largo (23 compresiones) en `emulator-5556`, mediana de 5.

Si alguno pasa de 1 s, se comenta con el usuario. No bloquea el cierre.

### 7.4 E2E por adb (sin ARTEMIS)

Todo en `emulator-5560`, porque la reimportación desde la galería necesita el modelo de Play Services.

**Antes de empezar:**

1. `adb devices -l`. El teléfono del usuario no se toca.
2. `ANDROID_SERIAL=emulator-5560 ./gradlew :app:installDebug` y `adb -s emulator-5560 shell pm clear dev.josueamaya.pourline`.
3. Los dos URIs de D44, sacados del XML de resultados de `RecipeShareCodecTest`.

**Herramientas:** `uiautomator dump`, `input tap`, `input text` y `screencap`. El PNG compartido se saca con
`adb -s emulator-5560 exec-out run-as dev.josueamaya.pourline cat cache/shared/recipe-<id>.png > <archivo>.png` justo
después de cada compartir. `adb -s emulator-5560 logcat -b crash -d` debe quedar vacío en todo el recorrido.

| Paso | Recorrido | Comprobaciones |
|---|---|---|
| 1 | Onboarding → Molinos → añadir "Femobook A2" del catálogo a mis molinos | El A2 aparece en mis molinos. |
| 2 | `adb -s emulator-5560 shell "am start -a android.intent.action.VIEW -d '<URI de la 4:6>'"` → Guardar. Lo mismo con el URI de peor caso | Las dos vistas previas sin aviso de recorte; la 4:6 con las notas, las 10 instrucciones y la molienda enlazada al A2. |
| 3 | Para cada idioma (es-ES y en-US, D45) y cada tema (claro y oscuro): abrir "V60 clásica" / "Classic V60", la 4:6 y la de peor caso, y Compartir | Ningún diálogo. PNG de 1080 × 1350 (corta), de 1080 × unos 1 660 (4:6) y de 1080 × unos 2 400 (peor caso), medidos con `sips -g pixelWidth -g pixelHeight`. El PNG de cada receta tiene el mismo `shasum -a 256` en claro y en oscuro. |
| 4 | En el Mac, `sips --resampleWidth 540 -s format jpeg -s formatOptions 70` sobre el PNG de la 4:6 y el de peor caso; `adb push` del PNG y del JPEG a `/sdcard/Download/` y `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/<archivo>`; Recetas → Importar → "Desde una imagen" | Las cuatro imágenes abren la vista previa. La 4:6, sin aviso de recorte y con las notas y las 10 instrucciones. |
| 5 | En el editor de la receta de peor caso, poner en tres pasos una instrucción de 200 letras y números aleatorios (`input text`), guardar y Compartir | Diálogo "Esta receta es muy larga para el QR. Al importarla faltarán las notas y las instrucciones de algunos pasos." (o su versión en inglés). Captura en los dos temas e idiomas. |
| 6 | Confirmar; reimportar el PNG del paso 5 desde la galería | La vista previa con "La receta original tenía las notas y las instrucciones de algunos pasos que no cupieron en el QR." y las instrucciones que quedaron. Captura en los dos temas e idiomas. |
| 7 | `adb -s emulator-5560 shell dumpsys package dev.josueamaya.pourline \| grep -E "android.permission.(INTERNET\|CAMERA)"` | No imprime nada. |

El aviso de datos no válidos no se puede provocar desde la interfaz, porque el editor no guarda recetas inválidas. Lo
cubre `RecipeShareTest.anInvalidSavedRecipeHasItsOwnNoticeAndSharesWithoutQr`.

### 7.5 Prueba manual del usuario (Xiaomi y un segundo móvil)

La hace el usuario, con el APK de depuración de esta rama. El agente no instala nada en su móvil.

- Compartir su receta 4:6 por WhatsApp y abrir la imagen en el otro móvil: Pourline la importa por la galería y por
  la hoja de compartir, **sin aviso de recorte** y con las notas y las instrucciones.
- Escanear con "Escanear QR" la tarjeta de la 4:6 y la de peor caso mostradas en otra pantalla (al 100 %): la vista
  previa se abre en 5 s o menos (R10).
- La cámara del sistema o Lens con la tarjeta de peor caso: anotar si ofrece abrir el enlace.
- Lo pendiente del §7.5 del plan anterior (cuatro entradas y conversión de molienda) se puede hacer en la misma sesión.

### 7.6 Capturas

En `~/Downloads/pourline-recipe-share/large-qr/{es,en}/{claro,oscuro}/`, fuera del repo:

- **La tarjeta (el PNG compartido):** la receta corta (V60 clásica), la 4:6 del usuario y la de 20 pasos de peor caso.
  Son 12 archivos: los de oscuro deben ser idénticos a los de claro (D45).
- **La interfaz:** el diálogo con "algunos pasos" (paso 5 del §7.4) y la vista previa con su aviso (paso 6).
- **Qué revisar a ojo:** que la cabecera use todo el ancho; que el QR quede centrado bajo los pasos, nítido y en negro
  sobre blanco; que el texto del QR quede centrado bajo la caja; que el pie quede abajo en la tarjeta corta; y la
  redacción de los avisos con la lista insertada.

## 8. Seguimiento fuera de este plan

- **Al cerrar (F4), en el §6 del spec:** el valor final de `MAX_URI_LENGTH` y el alto de la tarjeta de peor caso, como
  pide el propio spec. Si F0 bajó el tope, también el §3.2.
- **Cambios que conviene llevar al spec, sin tocarlo ahora:** la caja alineada a píxeles pares (D31, P3);
  `encodeResult` junto a `encode` en el contrato del §3.2 (D38); la tercera lectura por la ruta de importación (D46);
  y los tests renombrados del §1.2.
- **Redes con recorte a 4:5 (R5):** si se quiere publicar la tarjeta en Instagram, haría falta una variante de
  tamaño fijo. Es una decisión de producto que no entra en esta enmienda.
- **Recetas 4:6 en inglés y con más vertidos:** el criterio solo cubre la receta del usuario. Con 7 vertidos (14
  pasos) y los textos en inglés el URI crece, aunque los textos repetidos comprimen bien. Si se quiere garantizar,
  sería un test aparte.
- **Antes de fusionar en `develop`:** revisar la rama con `/code-review`.
