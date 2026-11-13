# Plan de implementación: SPEC-004 Catálogo de cafés, bitácora y compartir imagen

| Campo | Valor |
|---|---|
| Spec | [`spec-catalogo-cafes.md`](spec-catalogo-cafes.md) (SPEC-004) |
| Estado | En implementación: F0 a F8 hechas. F4 (etiquetas reales): 13 de 15 nombres bien extraídos (objetivo 12), 0 fechas inventadas en las 13 etiquetas sin fecha; las 2 fechas escritas a mano no se leen (no cuentan). Fallos: `Pacamana` (el OCR lee mal la variedad) y `Tusell` (logo del tostador más alto que el nombre). |
| Fecha | 2026-11-13 |
| Rama | `feat/coffee-catalog`, creada desde `develop` (`0a7a6b2`), con el commit del spec (`edbad3c`) |
| Módulos | `:app` (`:protocol` no cambia) |

El spec no trae un apartado de fases, así que este plan las define. Por petición del usuario, la primera
fase (F0) es el **diseño visual**: fija la disposición de todas las pantallas nuevas y modificadas y el
diseño de las cuatro imágenes para compartir (§3.7 y §6.3 del spec lo delegan aquí), y construye los
componentes `Ant*` que faltan. Antes de las fases están las decisiones que el spec deja abiertas (§2).
Varias amplían contratos del spec (D18, D21, D22, D23, D29 y D40), las de D37 a D41 concretan y amplían las
reglas del parser del §3.3 con lo visto en las 15 etiquetas reales, y D5 y D12 cambian comportamientos
existentes (D21 aplica el cambio que fija el §3.6 del spec). Conviene revisarlas antes de empezar F1. Las
preguntas de §6 están resueltas.

## 1. Punto de partida

**Se reutiliza:**
- `canonicalCoffee(input, existing)` (`history/HistoryModels.kt`) ya es genérica: recorta y reutiliza la
  grafía existente sin distinguir mayúsculas. Sirve para tostador, país, región y variedades (§3.6 del spec).
- El patrón de los repositorios: escrituras en `Dispatchers.IO + NonCancellable`, `observe(id)` que emite
  `null` cuando la fila desaparece y los ViewModels con `stateIn(WhileSubscribed(5_000))` e inicial `null`
  (D14 de SPEC-002).
- `TargetPicker` + `TargetPickerViewModel` (`ui/scale/`) como modelo del selector de café: `AntDrawer`,
  listas recogidas solo con el selector abierto y elección que pasa por el ViewModel de Scale, que la ignora
  en `Active` (D10 de SPEC-003).
- La congelación del objetivo al pasar a `Active` en `ScaleViewModel.withTarget` (`frozenFor`,
  `sessionTarget`) sirve tal cual para congelar el café.
- `BrewSettings` (`"settings"`, `StateFlow` + `SharedPreferences`) para `lastCoffeeId` (§3.5 del spec).
- Gráficas: la función `internal` `Chart(...)` de `BrewChartCard.kt`, `BrewChart`, `CompareChart`,
  `CompareSeries`, `CompareKind`, `compareRows`, `compareColors`, `brewChartColors`, `fitView`, `levelText`,
  `tagColor`, `summaryStability`, `formatNumber`, `formatDuration`, `formatBrewDateTime` y `formatBrewDate`.
- Componentes: `AntCard`, `AntTopBar(actions)`, `AntInput` (`prefix`, `suffix`, `status`, multilínea),
  `AntSelect`, `AntAutoComplete`, `AntSegmented`, `AntDrawer`, `AntModal`, `AntMessageHost` +
  `showAntMessage`, `AntAlert` (con `action`), `AntEmpty(action)`, `AntTag` (`icon`, `onClose`),
  `AntButton`, `AntIconButton`, `AntProgress`, `AntStatistic`, `AntDivider(text, orientation)`, `AntSpin`,
  `AntText`, `AntTitle` y los modificadores `internal` `antBorder(dashed = true)` y `thenIf`.
- Tema: `DotScaleTheme(darkTheme)` y `AntTheme.colors / typography / spacing / radius / controlHeight`.
- Tests: `TestBrews`, `HistoryDatabase.inMemory`, `MigrationTestHelper` (`HistoryMigrationTest`), la
  metodología de `HistoryPerformanceTest` (calentamiento y mediana de 5), los falsos de
  `ui/scale/ScaleTestFakes.kt` (JVM y `androidTest`) y `FakeScale`.
- `kotlinx-serialization-json` 1.8.1 está en la caché local de Gradle (para leer los fixtures del OCR).

**Falta o hay que corregir:**
- No hay `FileProvider` ni `res/xml/file_paths.xml`. El manifiesto no declara `CAMERA` (y así debe seguir).
- ML Kit (`com.google.mlkit:text-recognition`) **no está en la caché local**: la primera compilación de F3
  necesita red.
- No hay componentes de valoración (`Rate`), de fecha (`DatePicker`), de campo de formulario (`Form.Item`)
  ni plegables (`Collapse`). `LabeledInput` (`RecipeEditScreen`) y `Field` (`BrewEditScreen`) son privados.
- `AntAutoComplete` no tiene estado de error ni acciones de teclado (`ImeAction.Next` fijo) y no sabe
  "añadir" una sugerencia (lo necesitan las variedades). `AntSelect` exige `label: String`.
- Faltan iconos: café, cámara, galería, compartir, buscar, estrella, leer etiqueta, calendario y chevron.
- `BrewChartCard` lleva el chip del objetivo en `extra` de la cabecera: no cabe un segundo chip a 360 dp.
- `ScaleViewModel.resolveBrew` copia `coffee` de la referencia al prellenado (`coffee = entity.coffee`, línea 437):
  el §3.6 del spec ("Referencia y café") pide quitarlo (D21).
- `BrewDetailScreen.DetailsCard` muestra café y notas; `BrewEditScreen` edita el café como texto libre y
  `BrewDao.applyDetails` lo escribe con `canonicalCoffee`.
- `BrewFilter(coffee: String?)`, `BrewDao.coffees()` y `coffeesExcept(id)` trabajan con el texto del café.
  `ComparePickScreen` crea su filtro inicial con `BrewFilter(coffee = aCoffee)`.
- `HistoryConverters` no convierte `LocalDate` ni listas.
- `HistoryMigrationTest` abre la base con Room solo con `migration1To2`: al subir a la versión 3 necesita
  también `MIGRATION_2_3`.
- `formatValue` y `formatDiff` de `BrewCompareScreen.kt` son privadas y las necesita la imagen de comparación.
- `BrewChart` y `CompareChart` fijan su alto con `.height(ChartHeight)` (200 dp, `ChartDrawing.kt`) y `Chart`
  (`BrewChartCard.kt`) no recibe `modifier`: en las imágenes de 4:5 la gráfica tiene que ocupar el alto que
  quede (D2).

## 2. Decisiones que completan el spec

Se aprueban junto con el plan. Al terminar se pasan al spec como §3.8, como en los specs anteriores.

### 2.1 Diseño visual

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Tamaño de las imágenes (cierra §6.3) | Las cuatro imágenes (tarjeta del café, resumen en Scale, detalle de preparación y comparación) miden **1080 × 1350 px** (4:5; 360 × 450 dp). Se dibujan con `Density(3f, fontScale = 1f)` fija, sea cual sea el teléfono. | Decisión del usuario al revisar F0. 4:5 es el vertical máximo del feed de Instagram y se ve entero en WhatsApp. Con un solo formato hay una sola constante y una sola comprobación de tamaño. La densidad fija hace el tamaño en píxeles determinista y el escalado de fuente del sistema no rompe la maqueta. |
| D2 | Tema, marco y compactación de las imágenes | Siempre en **tema claro** (`DotScaleTheme(darkTheme = false)`), fondo `bgContainer`, barra de acento de 4 dp en `primary.base` arriba, márgenes de 20 dp a los lados y 16 dp arriba y abajo, y la marca "Dot Scale" (`app_name`) abajo a la derecha, en la misma línea que la última del pie del café. Tipografía de `antTypography()`; tabla en F0.4. Para que las tres gráficas quepan en 450 dp: (a) sin sobretítulo ("RESUMEN", "PREPARACIÓN", "COMPARACIÓN"); (b) el ajuste al objetivo pasa de línea propia a un `AntTag` en la fila del título; (c) la valoración va como estrellas a la derecha del subtítulo y la nota de cata, sin rótulo, como cita de 3 líneas; (d) se quita el reparto por nivel de estabilidad; (e) la gráfica deja los 200 dp fijos y ocupa el alto que sobra (`weight(1f)`), con un **mínimo de 144 dp**; (f) la comparación lleva solo la gráfica de agua, 4 de las 8 métricas de `compareRows` y un pie de una línea por café. | La imagen se ve en otras apps sobre fondos desconocidos: el claro es el más legible y no depende del tema de quien la genera. Con los diseños de 9:16 harían falta 533, 603 y 589 dp. Se conserva lo que fija §3.7: gráfica, métricas principales, café al pie y, en el detalle, valoración y nota de 3 líneas. El sobretítulo y el rótulo de la nota no aportan datos. El reparto por nivel no cabe en una fila a 320 dp (tres etiquetas ocupan unos 330), y la estabilidad ya lleva el color de su nivel. 144 dp son 432 px de alto, legibles con los ejes, y en el resumen, que no tiene nota, la gráfica crece hasta 233 dp en vez de dejar un hueco. Marca y pie en la misma línea ahorran 20 dp. |
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

### 2.2 Implementación

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D15 | Paquetes | `dev.dotscale.coffee`: modelo, validación, derivados, Room y repositorio. `dev.dotscale.coffee.label`: parser (JVM puro) y `MlKitLabelOcr`. `dev.dotscale.coffee.photo`: fotos y miniaturas. `dev.dotscale.ui.coffees`: pantallas. `dev.dotscale.share`: imágenes para compartir. | Los archivos puros se comprueban con `grep "import android"` por lista, como en `history`. |
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
| D31 | Fixtures reales del OCR | Escritura en el dispositivo con `org.json` (real en Android) y lectura en la JVM con `kotlinx-serialization-json` (`testImplementation`). JSON por etiqueta: `file`, `capturedOn` (fecha de captura) y `lines` (`text`, `heightPx`). La captura solo corre con `-e labelsCapture true` (`assumeTrue`), y se lanza con `am instrument` para que la app no se desinstale al terminar. Los esperados llegan al test JVM con `-PlabelsExpected=<ruta>`. | `org.json` está simulado en los tests JVM (`isReturnDefaultValues`). `connectedDebugAndroidTest` desinstala la app y borraría `Android/data/dev.dotscale/`. `capturedOn` hace deterministas las fechas "no futuras". |
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

## 3. Fases

Orden: F0 → F1 → F2 → F3 → F5 → (F6 ∥ F7) → F8 → F9. F4 depende de F3 y de las fotos del usuario (P1, resuelta), y
puede hacerse en cualquier momento antes de F9.

| Fase | Contenido | Tamaño |
|---|---|---|
| F0 | Diseño visual: pantallas, imágenes para compartir, componentes nuevos y línea base | M |
| F1 | Lógica pura: modelo del café, validación, derivados, formulario y parser de etiquetas | L |
| F2 | Base de datos v3, repositorio de cafés, vínculo con las preparaciones y `lastCoffeeId` | L |
| F3 | Fotos, miniaturas, OCR con ML Kit, `FileProvider` y permiso `INTERNET` | M |
| F4 | Etiquetas reales: fixtures, informe de aciertos y ajuste del parser | M |
| F5 | Pantallas del catálogo, selector de café y acceso provisional desde Scan y Scale | L |
| F6 | Scale: elegir el café antes de verter | M |
| F7 | Historial: valoración, notas de cata, café vinculado y filtro por catálogo | L |
| F8 | Compartir como imagen | L |
| F9 | Verificación: rendimiento, E2E por adb, lint y cierre | M |

Cada fase deja `:app` compilando y sus tests en verde. Se compila solo `:app` (nunca `./gradlew build`).
Commits: uno por fase, en Conventional Commits y en inglés, solo cuando se pidan. Se propone el mensaje al
final de cada fase. El plan, cuando se apruebe: `docs(specs): add coffee catalog implementation plan (SPEC-004)`.

### F0. Diseño visual

**Objetivo:** fijar la disposición de cada pantalla y de cada imagen antes de escribir lógica, y construir
los componentes `Ant*` que el diseño necesita. El diseño se aprueba junto con el plan; las fases F5 a F8 lo
implementan tal cual.

#### F0.1 Reglas comunes

- Ancho de referencia: 360 dp. Margen de página `spacing.base` (16 dp); separación entre tarjetas
  `spacing.sm` (12 dp); separación entre campos de formulario `spacing.base`.
- Toda pantalla nueva: `Scaffold(containerColor = bgLayout)`, `AntTopBar` con flecha atrás (`scale_back`) y
  `AntMessageHost` para los avisos.
- Listas: `AntCard(size = Small, onClick)` por fila, como `HistoryRow` y `RecipeRow`.
- Textos en `values/` y `values-en/`. Prefijos: `coffee_`, `coffees_`, `coffee_edit_`, `coffee_detail_`,
  `coffee_picker_`, `coffee_field_` (etiquetas compartidas por el editor, el detalle y la tarjeta),
  `coffee_process_`, `coffee_roast_`, `ocr_`, `rate_` y `share_`.
- Cada composable de pantalla nuevo lleva `@Preview` en claro y oscuro con datos de ejemplo privados del
  archivo, como `BrewChartCard.kt`.
- Accesibilidad: iconos con `contentDescription`; la valoración se lee como "Valoración: 4 de 5"; los
  disparadores de selección dicen "Café: Etiopía Guji, día 9 desde el tueste".

#### F0.2 Componentes nuevos y cambios (`ui/components/`)

| Archivo | Firma o cambio |
|---|---|
| `AntFormItem.kt` (nuevo) | `AntFormItem(label: String, modifier, required = false, error: String? = null, help: String? = null, errorTag: String? = null, extra: (@Composable RowScope.() -> Unit)? = null, content: @Composable () -> Unit)`. Fila de etiqueta (`textSM`, `textSecondary`, " *" en `error.text` si `required`) con `extra` a la derecha; debajo el contenido; debajo el error (`AntTextType.Danger`) o la ayuda (`textSM`, `textTertiary`). Es el `Form.Item` de antd. |
| `AntRate.kt` (nuevo) | `AntRate(value: Int?, onValueChange: ((Int?) -> Unit)?, modifier, count = 5, starSize: Dp = 24.dp, allowClear = true, testTag = "ant_rate")`. Con `onValueChange = null` es de solo lectura y se lee con `clearAndSetSemantics` ("Valoración: N de 5", o "Sin valorar"). Interactiva: cada estrella `selectable(role = Role.RadioButton)`, área mínima de 48 dp y `testTag("${testTag}_$i")`. `internal fun rateAfterTap(current: Int?, tapped: Int, allowClear: Boolean): Int?`. Colores de D11. |
| `AntSelect.kt` | `label` pasa a `String?` (la descripción usa el valor si falta). Se extrae el ancla a un `private` `SelectAnchor` y se añade `AntSelectTrigger(value: String?, onClick: () -> Unit, modifier, label: String? = null, placeholder: String? = null, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, enabled = true, description: String? = null, testTag: String? = null)`: el mismo aspecto, sin menú, con `Role.Button`. |
| `AntDatePicker.kt` (nuevo) | `AntDatePicker(value: LocalDate?, onValueChange: (LocalDate?) -> Unit, modifier, placeholder: String? = null, maxDate: LocalDate? = null, status = AntInputStatus.Default, enabled = true, testTag: String? = null)`. `AntSelectTrigger` con el icono de calendario y, con valor, un `AntIconButton(Close)` que la borra (`"${testTag}_clear"`). Abre `DatePickerDialog` de M3 con `SelectableDates` hasta `maxDate`. El texto, con `ofLocalizedDate(FormatStyle.MEDIUM)`. `internal fun LocalDate.toUtcMillis()` y `utcMillisToLocalDate(ms)`. |
| `AntCollapse.kt` (nuevo) | Un panel del `Collapse` de antd, sin estado propio: `AntCollapse(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier, extra: String? = null, testTag: String = "ant_collapse", content: @Composable ColumnScope.() -> Unit)`. Contenedor con `antBorder(colors.border, radius.lg)`, como `AntCard`. Cabecera: fondo `fillQuaternary`, alto mínimo 48 dp, relleno `base`/`sm`, `AntIcons.Right` de 14 dp en `textSecondary` que gira de 0 a 90° (`animateFloatAsState`, `motion.durationMid`, `motion.easeInOut`), título en `text` 14/22 SemiBold y, a la derecha, `extra` (`textSM`, `textSecondary`, 1 línea, elipsis). Contenido: fondo `bgContainer`, separado por una línea de 1 dp en `split`, relleno `base`, con `AnimatedVisibility(expandVertically + fadeIn / shrinkVertically + fadeOut)` y la misma duración. Semántica: la cabecera es `Role.Button` con `onClickLabel` "Desplegar"/"Plegar" y `stateDescription` "Desplegado"/"Plegado". `testTag`: `"${testTag}_header"` y `"${testTag}_content"`. |
| `AntAutoComplete.kt` | Parámetros nuevos con valores por defecto compatibles: `status = AntInputStatus.Default`, `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)`, `keyboardActions = KeyboardActions.Default` y `onSelect: ((String) -> Unit)? = null` (si está, elegir una sugerencia la entrega ahí en vez de a `onValueChange`; lo usan las variedades). |
| `AntIcons.kt` | Trazo propio de 24 × 24, como los actuales: `Coffee` (taza con vapor), `Camera`, `Image` (galería), `Share` (tres nodos unidos), `Search`, `Star` (contorno), `StarFilled` (relleno, con `fill`), `ScanText` (esquinas de visor con dos líneas de texto), `Calendar` y `Right` (chevron). |
| `AntComponentsPreview.kt` | Ejemplos de los componentes nuevos en claro y oscuro: `AntRate` vacía, con 3 y de solo lectura; `AntFormItem` con error, con ayuda y con la marca OCR; `AntSelectTrigger` habilitado y deshabilitado; `AntDatePicker` vacío y con fecha; `AntCollapse` plegado (con `extra`) y desplegado. |

#### F0.3 Pantallas

Los bocetos representan 360 dp de ancho. `[x]` es un botón o icono, `(x)` un botón de icono pequeño,
`[****-]` una valoración de 4 sobre 5 y `v` el desplegable de un selector.

**Barras superiores (acceso a Cafés, §3.6 del spec)**

```
Scan
+------------------------------------------------------+
| <scan_title>        [cafe] [recetas] [hist] [cfg] [T]|
+------------------------------------------------------+
Scale
+------------------------------------------------------+
|[<-] DOT...   [bat 80 %] [cafe] [hist] [cfg] [T]      |
+------------------------------------------------------+
```

Acceso **provisional**: el rediseño de la navegación y de las barras superiores queda fuera del alcance de
SPEC-004 y se hará en un spec posterior. Aquí solo se añade el icono, sin redistribuir nada.
`AntIconButton(AntIcons.Coffee, contentDescription = coffees_open)`, antes de recetas en Scan y antes de
historial en Scale (`ScaleTopBar` gana `onOpenCoffees`). `ScaleTopBarTest` comprueba a 360 dp que el título
largo se recorta y todas las acciones siguen visibles.

**Cafés (`CoffeesScreen`)**

```
+------------------------------------------------------+
|[<-] Cafés                                       [+]  |  "coffees_add"
+------------------------------------------------------+
| [Q Buscar por nombre, tostador u origen          (x)]|  AntInput prefix Search, suffix Close con texto
| [        Activos         |       Archivados        ] |  AntSegmented, fillMaxWidth
| +--------------------------------------------------+ |
| | +------+ Etiopía Guji Hambela                    | |  CoffeeRow, "coffee_row"
| | |      | Café de Altura                          | |
| | | foto | Tostado 12 oct 2026 · día 9             | |
| | +------+ Quedan 180 g                            | |
| +--------------------------------------------------+ |
| +--------------------------------------------------+ |
| | +------+ Kenia Kiambu AA             [Agotado]   | |
| | | foto | Origen Tostadores                       | |
| | +------+ Tostado 2 oct 2026 · día 19             | |
| +--------------------------------------------------+ |
+------------------------------------------------------+
```

- `LazyColumn(contentPadding = base, spacedBy(sm))`. El buscador y las pestañas son los dos primeros items.
- `CoffeeRow(item: CoffeeListItem, onClick, modifier, compact = false, selected = false, archivedTag = false)`:
  `Row(spacedBy(sm))` con `CoffeeThumbnail(photo1, size = 56.dp)` (radio `base`; sin foto, fondo
  `fillTertiary` con `AntIcons.Coffee` de 24 dp en `textQuaternary`) y una columna `spacedBy(xxs)`:
  - Nombre (`AntText strong`, 1 línea, elipsis) y, al final de esa línea, `AntTag("Agotado", Error)`.
  - Tostador (`Secondary`, 1 línea).
  - "Tostado 12 oct 2026 · día 9" (`Secondary`, `textSM`); sin fecha, "Sin fecha de tueste". Es lo que
    distingue dos bolsas del mismo café.
  - "Quedan 180 g" (`Secondary`, `textSM`), solo con peso de bolsa y sin agotar.
  - `compact = true` (selector): miniatura de 40 dp y sin la línea del stock en otra fila (va tras la fecha).
- Estados vacíos (`AntEmpty(icon = AntIcons.Coffee)`):
  - Activos sin cafés y sin búsqueda: "Aún no hay cafés. Registra el primero con una foto de la etiqueta." y
    `AntButton(Primary, "Registrar café", icon = Plus)` (`coffees_empty_add`).
  - Archivados vacíos: "No hay cafés archivados".
  - Búsqueda sin resultados: "Ningún café coincide con «…»".

**Detalle del café (`CoffeeDetailScreen`)**

```
+------------------------------------------------------+
|[<-] Etiopía Guji Hambela                    [share]  |  título = nombre; subtítulo = tostador
|     Café de Altura                                   |  "coffee_detail_share"
+------------------------------------------------------+
| +--------------------------------------------------+ |
| | ! Agotado                                        | |  AntAlert Warning, "coffee_detail_sold_out"
| |   Ya no queda café en la bolsa.                  | |  (agotado y sin archivar)
| |   [Archivar]                                     | |
| +--------------------------------------------------+ |
| +-----------------------+ +-----------------------+  |  2 fotos: Row, weight 1, aspecto 3:4
| |        foto 1         | |        foto 2         |  |  1 foto: ancho completo, aspecto 4:3
| +-----------------------+ +-----------------------+  |  recorte al centro, radio lg
| +--------------------------------------------------+ |
| | Días desde el tueste      Quedan                 | |  AntCard: dos AntStatistic
| | 9                         180 g de 250 g         | |
| | [=================--------------]                | |  AntProgress(showInfo = false)
| +--------------------------------------------------+ |
| Bitácora · 3 preparaciones                           |  AntTitle(5) + AntText Secondary
| +--------------------------------------------------+ |
| | 13 nov 2026, 8:30                      [****-]   | |  CoffeeLogRow, "coffee_log_row"
| | Día 9 · 15 g                                     | |  (un item de la LazyColumn por fila)
| | Jazmín y durazno; más dulce que ayer, molienda…  | |  notas: Secondary, 2 líneas
| +--------------------------------------------------+ |
| +--------------------------------------------------+ |
| | 12 nov 2026, 8:10                      [***--]   | |
| | Día 8 · 15 g                                     | |
| +--------------------------------------------------+ |
| +--------------------------------------------------+ |
| | (>) Datos del café             Natural · Claro   | |  AntCollapse plegado (por defecto),
| +--------------------------------------------------+ |  "coffee_detail_facts"; extra = proceso · nivel
| [     Editar      ] [    Nueva bolsa    ]            |  "coffee_detail_edit", "coffee_detail_new_bag"
| [     Archivar    ] [  Borrar (danger)  ]            |  "coffee_detail_archive", "coffee_detail_delete"
+------------------------------------------------------+

Datos desplegados (tras tocar la cabecera)
| +--------------------------------------------------+ |
| | (v) Datos del café             Natural · Claro   | |  cabecera: fillQuaternary
| |--------------------------------------------------| |
| | Tostador       Café de Altura                    | |  CoffeeFact: etiqueta 112 dp
| | Origen         Etiopía · Guji                    | |  (textSecondary) y valor weight 1,
| | Finca          Hambela Estate                    | |  "coffee_fact_<campo>"
| | Variedades     Heirloom, 74110                   | |
| | Proceso        Natural                           | |
| | Altitud        1800–2000 m                       | |
| | Tueste         Claro · 12 oct 2026               | |
| | Notas del t.   Jazmín, bergamota y durazno       | |
| | Bolsa          250 g · $18.50                    | |
| | Abierto        14 oct 2026                       | |
| +--------------------------------------------------+ |
```

- `LazyColumn(Modifier.testTag("coffee_detail_list"), contentPadding = base, spacedBy(sm))`. Items en este orden:
  aviso, fotos, estado, cabecera de la bitácora, una fila por preparación (`key = brewId`), el plegable de
  datos y las dos filas de acciones.
- Plegable: `AntCollapse(title = "Datos del café", expanded, onExpandedChange, extra = "Natural · Claro")`, con
  `var factsExpanded by rememberSaveable { mutableStateOf(false) }` en la pantalla. `extra` es proceso ·
  nivel con las partes que haya (sin ninguna, no hay `extra`). Plegado por defecto en cada visita.
- Archivado: `AntAlert(Info, "Archivado: no aparece en el selector de Scale")` en lugar del de agotado, y el
  botón pasa a "Desarchivar". Archivar o desarchivar muestra un `AntMessage` de éxito.
- Tarjeta de estado: sin fecha de tueste se omite su estadística; sin peso de bolsa, el stock y la barra;
  sin ninguno de los dos, la tarjeta. Agotado: "Agotado" en `error.text` y barra a 0 con `Exception`.
- Datos: se omiten las filas vacías. Proceso `OTHER` muestra `processOther` (o "Otro"). Si no queda ninguna
  fila (café con solo el nombre), no se muestra el plegable.
- Bitácora vacía: "Aún no hay preparaciones con este café. Elígelo en Scale antes de verter." (una sola línea
  `Secondary` en lugar de las filas; el plegable y las acciones siguen debajo).
- `CoffeeLogRow`: fecha y hora (`formatBrewDateTime`) con `AntRate` de 14 dp a la derecha; "Día 9 · 15 g"
  (cada parte se omite si falta); notas de cata a 2 líneas. Tocarla abre `BrewDetail`.
- Borrar abre el `AntModal` de D35 (`danger`); al confirmar, vuelve a la lista.

**Editor del café (`CoffeeEditScreen`)**

```
+------------------------------------------------------+
|[<-] Nuevo café                          [Guardar]    |  "coffee_edit_save" (Primary, Small)
+------------------------------------------------------+   títulos: Nuevo café / Editar café / Nueva bolsa
| +- - - - - - - - - -+  +----------------------+      |  CoffeePhotoSlot x2, 158 x 158 dp
| :      Foto 1       :  |                      |      |  vacío: borde discontinuo (antBorder dashed)
| : [cam  Cámara    ] :  |     foto 2 (crop)    |      |  y fondo fillQuaternary
| : [img  Galería   ] :  |                      |      |  con foto: imagen recortada, radio lg
| +- - - - - - - - - -+  +----------------------+      |
|                        [Reemplazar v] [Quitar]       |  AntButton Link Small; Quitar con danger
| [ (scan) Leer etiqueta                          ]    |  "coffee_edit_read_label", loading en curso
| +--------------------------------------------------+ |
| | i Se leyeron 4 campos de la etiqueta         (x) | |  AntAlert Info, "coffee_edit_ocr_alert"
| |   Revísalos antes de guardar.                    | |
| +--------------------------------------------------+ |
| ---- Café ------------------------------------------ |  AntDivider(text, Left)
| Nombre *                 [(scan) Leído de la etiqueta]|  AntFormItem + marca, "ocr_mark_name"
| [ Etiopía Guji Hambela                          ]    |
| Tostador                                             |
| [ Café de Altura                                ]    |  AntAutoComplete (sugerencias)
| ---- Origen ---------------------------------------- |
| País                       Región                    |
| [ Etiopía            ]     [ Guji               ]    |  dos AntAutoComplete, weight 1
| Finca o productor                                    |
| [ Hambela Estate                                ]    |
| Variedades                                           |
| [Heirloom x] [74110 x]                               |  AntTag(onClose) en FlowRow
| [ Añadir variedad                         ] (+)      |  AntAutoComplete(onSelect) + AntIconButton
| Proceso                                              |
| [ Natural                                     v ]    |  AntSelect; OTHER añade "Nombre del proceso"
| Altitud (m)                                          |
| [ 1800             ]  –  [ 2000             ]        |  desde / hasta, teclado numérico
| ---- Tueste ---------------------------------------- |
| Nivel                      Fecha de tueste           |
| [ Claro          v ]       [ 12 oct 2026   (x)(cal)] |  AntSelect | AntDatePicker
| Notas del tostador                                   |
| [ Jazmín, bergamota y durazno                   ]    |  AntInput multilínea, 3 líneas
| ---- Bolsa ----------------------------------------- |
| Peso (g)                   Precio (USD)              |
| [ 250            ]         [ $ 18.50          ]      |  AntInput; precio con prefijo "$"
| Fecha de apertura                                    |
| [ 14 oct 2026                           (x)(cal)]    |
+------------------------------------------------------+
```

- `CoffeePhotoSlot(slot: Int, photo: String?, busy: Boolean, onCamera, onGallery, onRemove, modifier)`:
  `testTag("coffee_photo_slot_$slot")` y botones `coffee_photo_camera_$slot`, `coffee_photo_gallery_$slot`,
  `coffee_photo_replace_$slot` y `coffee_photo_remove_$slot`. Mientras se reduce y guarda la foto, encima
  va `AntSpin` sobre `fillSecondary`. "Reemplazar" abre un `DropdownMenu` con "Cámara" y "Galería".
- "Leer etiqueta" está deshabilitado sin fotos, con una foto en curso o con el OCR en curso (`loading`).
- Marca OCR (D8): `OcrMark()` = `AntTag("Leído de la etiqueta", color = Primary, icon = ScanText)` con
  `testTag("ocr_mark_<campo>")`, en `AntFormItem(extra = …)`. Desaparece al editar el campo.
- Si el OCR no rellena nada o falla: `AntMessage(Warning, "No se pudo leer la etiqueta")` y el formulario no
  cambia. Si falla la foto: `AntMessage(Error, "No se pudo guardar la foto")`.
- Errores bajo cada campo con `AntFormItem(error = …)` y el estado `Error` del input. El de altitud va
  debajo de la fila "desde – hasta". "Guardar" está deshabilitado con errores o mientras guarda.
- Atrás con cambios: el `AntModal` "¿Descartar los cambios?" de `BrewEditScreen` (mismos textos).
- Etiquetas de campo con `testTag` `coffee_edit_<campo>`: `name`, `roaster`, `country`, `region`, `farm`,
  `varieties`, `process`, `process_other`, `altitude_min`, `altitude_max`, `roast_level`, `roast_date`,
  `roaster_notes`, `bag_weight`, `price` y `opened_date`.

**Selector de café (`CoffeePicker`, Scale y "Editar preparación")**

```
+------------------------------------------------------+
| Café de la preparación                          (x)  |  AntDrawer, "coffee_picker"
+------------------------------------------------------+
| [Q Buscar café                                   ]   |  solo con más de 5 cafés
| +--------------------------------------------------+ |
| | +----+ Etiopía Guji Hambela          [Elegido]   | |  CoffeeRow(compact, selected),
| | |foto| Café de Altura                            | |  "coffee_picker_row"
| | +----+ Tostado 12 oct · día 9 · 180 g            | |
| +--------------------------------------------------+ |
| | +----+ Etiopía Guji Hambela                      | |  la otra bolsa: se distingue por la fecha
| | |foto| Café de Altura                            | |
| | +----+ Tostado 2 nov · día 11 · 250 g            | |
| +--------------------------------------------------+ |
| | +----+ Kenia Kiambu AA      [Archivado]          | |  solo en "Editar preparación": el vinculado
| +--------------------------------------------------+ |  aunque esté archivado
| [     Sin café     ] [  (+) Nuevo café   ]           |  "coffee_picker_clear", "coffee_picker_new"
+------------------------------------------------------+
```

`CoffeePicker(coffees: List<CoffeeListItem>, selectedId: Long?, onSelect: (Long) -> Unit, onClear: (() -> Unit)?, onNew: () -> Unit, onClose: () -> Unit, extra: CoffeeListItem? = null)`.
Lista con `heightIn(max = 360.dp)`. "Sin café" solo si hay algo elegido (`onClear != null`), como "Quitar
objetivo". Sin cafés: el texto "Aún no hay cafés" y solo "Nuevo café". Elegir cierra el selector. El
`AntTag` "Agotado" de la fila se mantiene: un café agotado sigue apareciendo hasta que se archive (§4).

**Scale: `BrewChartCard` con los dos selectores (D5)**

```
Armed                                                 Active
+- Vertido ----------------------------------------+  +- Vertido ----------------------------------------+
| Objetivo [ (rec) V60 clásica                  v ]|  | Objetivo [ V60 clásica  (deshabilitado)       v ]|
| Café     [ (cafe) Etiopía Guji · día 9        v ]|  | Café     [ Etiopía Guji · día 9 (deshab.)     v ]|
|                                                  |  | TargetGuideBlock                                 |
| (gráfica con la curva objetivo)                  |  | (gráfica)                                        |
| Etapa 1/3 · Bloom: hasta 40 g en 10 s            |  | [Estable · 82]                     [Terminar]    |
| Vierte agua para empezar                         |  +--------------------------------------------------+
+--------------------------------------------------+
```

- `BrewSetupRow(label, trigger)`: etiqueta de 72 dp (`AntText`, `textSecondary`) y `AntSelectTrigger`
  (`weight 1`, `leading` = icono de 16 dp). Filas con `spacedBy(xs)`.
- Objetivo: el nombre o "Ninguno"; `testTag("target_chip")` y descripción "Objetivo: …" (`target_chip`).
- Café: "Nombre · día N" (sin fecha, solo el nombre) o "Sin café"; `testTag("coffee_trigger")` y
  descripción "Café: …".
- Cada fila solo aparece si hay callback (`onPickTarget`, `onPickCoffee`), así que la tarjeta sigue igual sin
  ellos (tests y previews).
- En `Finished` los selectores muestran la elección para la siguiente preparación, como el objetivo (D8 de
  SPEC-003).

**`BrewSummaryCard` en Scale**

Al pie, una fila con "Añadir detalles" (`weight 1`) y "Compartir" (`weight 1`, icono `Share`,
`testTag("brew_summary_share_image")`; el nombre `brew_summary_share` ya lo usa el texto del reparto por nivel). Si "Añadir detalles" no se muestra (`Failed` o `null`), "Compartir" ocupa
todo el ancho. Sin `onShare`, como en el detalle del historial, no aparece.

**Editar preparación (`BrewEditScreen`)**

```
+------------------------------------------------------+
|[<-] Detalles de la preparación                       |
+------------------------------------------------------+
| Nombre                                               |
| [ 13 nov 2026, 8:30                             ]    |
| Café                                                 |
| [ (cafe) Etiopía Guji Hambela · día 9          v ]   |  AntSelectTrigger → CoffeePicker,
|                                                      |  "brew_edit_coffee"
| Valoración                                           |
| (*) (*) (*) (*) ( )        Quitar valoración         |  AntRate 32 dp, "brew_edit_rating";
|                                                      |  AntButton Link Small
| Notas de cata                                        |
| [ Jazmín y durazno…                             ]    |  multilínea, 3 líneas
| Método                                               |
| [ V60                                          v ]   |
| Molienda / Temperatura (°C)                          |  sin cambios
| [ Guardar ]                                          |
+------------------------------------------------------+
```

Con texto antiguo sin vínculo (`CoffeeChoice.Legacy`), el disparador muestra ese texto y debajo la ayuda
"No está en el catálogo. Elige un café para vincular la preparación." "Notas" pasa a "Notas de cata" en los
dos idiomas (`brew_edit_notes`, `brew_detail_notes`).

**Fila del historial (`HistoryRow`)**

```
+------------------------------------------------------+
| /\_/~~  Mañana                             [****-]   |  AntRate 12 dp, "history_row_rating"
| (mini)  13 nov 2026, 8:30                            |
|         Etiopía Guji Hambela                         |
|         250 g · 3:05 · 1:16,7                        |
|         [Estable · 82] [Ajuste 92] [Simulada]        |
+------------------------------------------------------+
```

La primera línea pasa a ser una `Row` con el título (`weight 1`, elipsis) y la valoración. Sin valoración, la
fila queda como hoy.

**Detalle de la preparación (`BrewDetailScreen`)**

```
+------------------------------------------------------+
|[<-] Mañana                                  [share]  |  "brew_detail_share"
+------------------------------------------------------+
| +- Vertido ----------------------------------------+ |
| | (gráfica)                                        | |
| +--------------------------------------------------+ |
| +- Cata -------------------------------------------+ |  "brew_detail_tasting"
| | +----+ Etiopía Guji Hambela                  >   | |  fila pulsable → CoffeeDetail,
| | |foto| Café de Altura · día 9                    | |  "brew_detail_coffee"
| | +----+                                           | |
| | ------------------------------------------------ | |
| | Valoración          [****-]                      | |
| | Notas de cata                                    | |
| | Jazmín y durazno, más dulce que ayer.            | |
| +--------------------------------------------------+ |
| (Resumen, Ajuste al objetivo, Detalles sin café ni   |
|  notas y los botones, como hoy)                      |
+------------------------------------------------------+
```

Café sin vínculo con texto antiguo: fila sin miniatura ni chevron, no pulsable, con "No está en el
catálogo". La tarjeta se oculta sin café, sin valoración y sin notas. Cada parte se omite si falta.

**Comparación (`BrewCompareScreen`)**: solo gana el icono de compartir en la barra superior
(`compare_share`).

#### F0.4 Imágenes para compartir

Las cuatro miden **1080 × 1350 px (360 × 450 dp, 4:5)** a densidad 3 (D1). Las tres de gráficas se compactan
según D2.

**Marco común (`ShareFrame`)**: fondo `bgContainer`, barra de acento de 4 dp en `primary.base` a todo el
ancho, márgenes de 20 dp a los lados (ancho útil: 320 dp), 16 dp arriba y 16 dp abajo. La última fila
(`ShareBottomRow`) lleva el pie del café (`weight 1`) y la marca "Dot Scale" (`textSM`, `textTertiary`) a la
derecha, alineada con la última línea del pie. Sin pie, la fila solo tiene la marca (20 dp). Entre el cuerpo
y esa fila hay al menos 12 dp.

**Gráfica en las imágenes**: `Modifier.weight(1f)` dentro de la columna de 450 dp, así que ocupa todo el alto
que dejan los demás elementos. Los presupuestos garantizan un **mínimo de 144 dp** (432 px) en el peor caso.
Con la curva ilegible, ese mismo hueco muestra centrado el texto `brew_detail_curve_error`.

**Tipografía** (tema claro, `antTypography()`, `fontScale` 1):

| Elemento | Estilo | Color |
|---|---|---|
| Sobretítulo (tostador; solo en la tarjeta del café) | `textSM` 12/20, SemiBold, mayúsculas, `letterSpacing` 0,6 sp | `textSecondary` |
| Nombre del café | `heading3` 24/32, 2 líneas como máximo | `text` |
| Título del resumen y del detalle | `heading4` 20/28, 1 línea, elipsis | `text` |
| Ajuste al objetivo | `AntTag` con `history_row_fit` ("Ajuste 92"), como en `HistoryRow` | `AntTagColor.Default` |
| Subtítulo y leyenda de la comparación | `text` 14/22, 1 línea, elipsis | `textSecondary`; en la leyenda, "A" y "B" en SemiBold y `text` |
| Valoración | `AntRate` de solo lectura, estrellas de 14 dp | D11 |
| Etiqueta de dato o métrica | `textSM` 12/20 | `textTertiary` |
| Valor de dato | `text` 14/22, 1 línea | `text` |
| Cifra de métrica | `textLG` 16/24, SemiBold, `tnum` | `text`; estabilidad con el color de su nivel |
| Tabla de la comparación | `textSM` 12/20, `tnum`, filas de 22 dp; cabecera en `textTertiary` | `text` |
| Notas del tostador | `text` 14/22, 3 líneas como máximo, elipsis | `text` |
| Nota de cata | `text` 14/22, 3 líneas como máximo, elipsis; barra de 2 dp en `border` a la izquierda y 10 dp de sangría | `text` |
| Pie del café | Nombre `text` 14/22 SemiBold y debajo `textSM` 12/20. Con dos cafés: una línea `text` 14/22 por café (nombre SemiBold, el resto en `textSecondary`) | `text` / `textSecondary` |
| Marca | `textSM` 12/20, a la derecha | `textTertiary` |

**1. Tarjeta del café**

```
+==============================================================+  barra de acento 4 dp
|                                                              |
|  CAFÉ DE ALTURA                              +------------+  |  sobretítulo = tostador
|  Etiopía Guji                                |            |  |  nombre: heading3, 2 líneas
|  Hambela                                     |   foto 1   |  |  foto: 112 x 112 dp, recorte
|                                              |            |  |  al centro, radio lg
|                                              +------------+  |  (sin foto: el texto ocupa
|                                                              |   todo el ancho)
|  Origen     Etiopía · Guji · Hambela Estate                  |  5 filas de dato: etiqueta 72 dp
|  Altitud    1800–2000 m                                      |  + valor, 1 línea cada una
|  Variedad   Heirloom, 74110                                  |
|  Proceso    Natural                                          |
|  Tueste     Claro · 12 oct 2026 · día 9                      |
|                                                              |
|  NOTAS DEL TOSTADOR                                          |
|  Jazmín, bergamota y durazno, con final de miel.             |  3 líneas como máximo
|                                                              |
|  (espacio flexible)                                          |
|                                                   Dot Scale  |
+--------------------------------------------------------------+
```

- Datos: Origen = país · región · finca (las partes que haya); Altitud con el formato del spec; Variedad =
  lista separada por comas; Proceso; Tueste = nivel · fecha · "día N" (días hasta hoy). Cada fila se omite
  si no tiene nada; las notas, también. **Nunca** precio ni stock.
- Presupuesto vertical (dp): acento 4 + margen 16 + cabecera 112 + 16 + datos (5 × 22 + 4 × 4 = 126) + 16 +
  notas (20 + 4 + 66 = 90) + 12 + marca 20 + margen 16 = 428. Holgura: 22 dp (espacio flexible).
- La foto se decodifica a 336 px (112 dp a 3x) con `CoffeeImages.photo(name, 336)`.

**2. Resumen en Scale** (`BrewShareCard` sin nota)

```
+==============================================================+  acento 4
|  13 nov 2026, 8:30                             [Ajuste 92]   |  título: hora de inicio; etiqueta
|  Objetivo: V60 clásica                                       |  de ajuste y subtítulo solo con
|  +--------------------------------------------------------+  |  objetivo
|  |                                                        |  |
|  |                                                        |  |  BrewChart: agua, flujo y curva
|  |   (gráfica: weight 1, mínimo 144 dp; aquí 233 dp)      |  |  objetivo, con el alto sobrante
|  |                                                        |  |
|  |                                                        |  |
|  +--------------------------------------------------------+  |
|  Tiempo      Agua        Ratio       Estabilidad             |  4 métricas, weight 1 (80 dp)
|  3:05        250 g       1:16,7      82                      |
|                                                              |
|  ----------------------------------------------------------  |  pie del café, solo si hay
|  (cafe) Etiopía Guji Hambela                                 |
|         Café de Altura · día 9                   Dot Scale   |  marca en la última línea del pie
+--------------------------------------------------------------+
```

- Sin objetivo: sin subtítulo ni etiqueta de ajuste, y la gráfica crece 22 dp más.
- Sin reparto por nivel (D2): la estabilidad lleva el color de su nivel.

**3. Detalle de preparación** (`BrewShareCard` con nota)

```
+==============================================================+
|  Mañana                                        [Ajuste 92]   |  título: nombre o fecha y hora
|  13 nov 2026, 8:30 · V60                         [****-]     |  subtítulo (fecha si hay nombre ·
|  +--------------------------------------------------------+  |  método) + valoración a la derecha
|  |   (gráfica con la curva objetivo guardada;             |  |
|  |    weight 1, mínimo 144 dp; aquí 155 dp)               |  |
|  +--------------------------------------------------------+  |
|  Tiempo      Agua        Ratio       Estabilidad             |
|  3:05        250 g       1:16,7      82                      |
|                                                              |
|  | Jazmín y durazno, más dulce que ayer; la molienda a 22    |  nota de cata como cita, sin
|  | clics funcionó mejor que la de la semana pasada,          |  rótulo: 3 líneas, elipsis
|  | aunque el final quedó algo seco y…                        |
|  ----------------------------------------------------------  |
|  (cafe) Etiopía Guji Hambela                                 |  "día N" hasta la fecha de la
|         Café de Altura · día 9                   Dot Scale   |  preparación
+--------------------------------------------------------------+
```

- Sin valoración, el subtítulo ocupa todo el ancho. Si el subtítulo quedaría vacío (sin nombre ni método) y
  hay valoración, las estrellas van solas al principio de esa fila; sin ninguna de las dos, no hay fila.
- Sin nota de cata, desaparecen la nota y su separación, y la gráfica crece 78 dp.

**4. Comparación** (`CompareShareCard`)

```
+==============================================================+
|  (o) A · Mañana · 13 nov 2026, 8:30                          |  leyenda: punto con el color
|  (o) B · Tarde · 12 nov 2026, 17:10                          |  de A / B, 1 línea cada una
|  +--------------------------------------------------------+  |
|  |  (CompareChart de agua, A y B;                         |  |
|  |   weight 1, mínimo 144 dp; aquí 169 dp)                |  |
|  +--------------------------------------------------------+  |
|               A           B           B − A                  |  tabla: cabecera 20 + 4 filas
|  Tiempo       3:05        3:20        +0:15                  |  de 22; etiqueta 72 dp y tres
|  Agua         250 g       255 g       +5 g                   |  columnas weight 1 alineadas a
|  Ratio        1:16,7      1:17,0      +0,3                   |  la derecha
|  Estabilidad  82          74          −8                     |
|  ----------------------------------------------------------  |
|  (o) Etiopía Guji Hambela · Café de Altura · día 9           |  D4: con dos cafés, una línea
|  (o) Kenia Kiambu AA · Origen · día 4            Dot Scale   |  por café
+--------------------------------------------------------------+
```

- Solo la gráfica de agua (la de flujo no cabe) y 4 de las 8 métricas de `compareRows`: `TOTAL_TIME`,
  `TOTAL_WATER`, `RATIO` y `STABILITY`, con `formatValue` y `formatDiff`. Las demás siguen en la pantalla.
- Las series con curva ilegible se omiten, como en la pantalla; si lo son las dos, el hueco de la gráfica
  muestra el texto de curva ilegible.

**Presupuesto vertical de las tres imágenes de gráficas** (dp, peor caso: todos los elementos y pie con café;
en la comparación, dos cafés distintos):

| Elemento | Resumen | Detalle | Comparación |
|---|---|---|---|
| Acento + margen superior | 4 + 16 | 4 + 16 | 4 + 16 |
| Cabecera | título 28 + subtítulo 22 | título 28 + subtítulo 22 | leyenda 22 + 22 |
| Separación | 12 | 12 | 12 |
| Gráfica (mínimo) | 144 | 144 | 144 |
| Separación + métricas o tabla | 12 + 44 | 12 + 44 | 12 + 108 (20 + 4 × 22) |
| Separación + nota de cata | — | 12 + 66 | — |
| Separación + pie con la marca | 12 + 51 (1 + 8 + 22 + 20) | 12 + 51 | 12 + 57 (1 + 8 + 22 + 4 + 22) |
| Margen inferior | 16 | 16 | 16 |
| **Total con la gráfica al mínimo** | **361** | **439** | **425** |
| **Holgura (se suma a la gráfica)** | **89** | **11** | **25** |

**Pie del café (`ShareCoffeeFooter`)**: `AntDivider`, 8 dp y, con un café, una fila con `AntIcons.Coffee` de
20 dp en `primary.base` y la columna nombre (1 línea) + "Tostador · día N" (1 línea, `weight 1`, con la marca
a su derecha). Con dos cafés distintos en la comparación, una línea por café con su punto de color (D4). Solo
con café vinculado (§3.7); un texto antiguo sin vínculo no lleva pie.

**Nombres de archivo**: no cambian, porque no dependen del formato: `coffee-<id>.png`, `brew-<id>.png`,
`compare-<a>-<b>.png` y `summary.png`. Título del selector: "Compartir imagen".

#### F0.5 Entregables de código de F0

1. Línea base: `./gradlew :app:testDebugUnitTest` y `./gradlew :app:lintDebug`; anotar avisos y errores.
2. Los componentes e iconos de F0.2 y sus previews.
3. Textos de los componentes: `rate_description` ("Valoración: %1$d de %2$d"), `rate_none`, `rate_star`
   ("%1$d estrellas"), `date_picker_accept`, `date_picker_cancel` y `date_picker_clear`.

**Tests:**
- `test/.../ui/components/AntRateTest.kt`: `rateAfterTap` (tocar otra estrella la elige; tocar la marcada
  la quita con `allowClear` y no sin él).
- `test/.../ui/components/AntRateColorsTest.kt`: `warning.text` llega a 3:1 sobre `bgContainer` en los dos
  temas (patrón de `AntColorsContrastTest`).
- `test/.../ui/components/AntDatePickerTest.kt`: `toUtcMillis` y `utcMillisToLocalDate` de ida y vuelta, sin
  desfase por zona horaria.
- `androidTest/.../ui/components/AntComponentsTest.kt` (nuevo): `AntRate` interactiva (tocar 4 → 4; tocar 4
  otra vez → `null`) y su descripción de solo lectura; `AntFormItem` muestra error y `extra`;
  `AntSelectTrigger` llama a `onClick` y no lo hace deshabilitado; `AntDatePicker` abre el diálogo y "borrar"
  deja `null`; `AntAutoComplete(onSelect)` entrega la sugerencia; `AntCollapse` con `expanded = false` no
  compone `ant_collapse_content`, tocar `ant_collapse_header` llama a `onExpandedChange(true)` y, desplegado,
  el contenido se ve y `stateDescription` pasa de "Plegado" a "Desplegado".

**Verificación:** `./gradlew :app:compileDebugKotlin`,
`./gradlew :app:testDebugUnitTest --tests "dev.dotscale.ui.components.*"`,
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.components.AntComponentsTest`,
`./gradlew :app:lintDebug` y revisión de las previews en claro y oscuro.

Commit sugerido: `feat(ui): add form item, rate, select trigger and date picker components`.

### F1. Lógica pura

**Objetivo:** todo lo que no necesita Android, probado en la JVM.

**Archivos** (`app/src/main/kotlin/dev/dotscale/`):

| Archivo | Contenido |
|---|---|
| `coffee/CoffeeModels.kt` (nuevo) | `enum class CoffeeProcess { WASHED, NATURAL, HONEY, ANAEROBIC, WET_HULLED, OTHER }`, `enum class RoastLevel { LIGHT, MEDIUM, DARK }`. `data class CoffeeDraft(id: Long? = null, name: String, roaster, originCountry, originRegion, farm, varieties: List<String> = emptyList(), process, processOther, altitudeMinM: Int?, altitudeMaxM: Int?, roastLevel, roastDate: LocalDate?, roasterNotes, bagWeightG: Double?, price: Double?, openedDate: LocalDate?, photo1: String?, photo2: String?)` con `normalized()` (recorta, vacío → `null`, `processOther` solo con `OTHER`, altitud con un solo valor → los dos iguales, variedades sin vacías ni repetidas). `CoffeeListItem(id, name, roaster, originCountry, originRegion, roastDate, daysSinceRoast: Int?, stockG: Double?, soldOut: Boolean, photo1: String?, archived: Boolean)`. `CoffeeLogEntry(brewId, startedAtEpochMs, rating: Int?, notes: String?, daysSinceRoast: Int?, doseG: Double?)`. `PickedCoffee(id, name, roaster, roastDate)`. `CoffeeSuggestions(roasters, countries, regions, varieties: List<String>)`. |
| `coffee/CoffeeDerived.kt` (nuevo) | `stockOf(bagWeightG: Double?, dosedG: Double): Double?`, `isSoldOut(stockG: Double?): Boolean` (`stockG != null && stockG <= 0`), `displayStock(stockG: Double): Double` (`max(0, stockG)`), `daysSinceRoast(roastDate: LocalDate?, on: LocalDate): Int?` (negativo → `null`, D19), `brewDaysSinceRoast(roastDate, startedAtEpochMs, zone): Int?` y `newBagFrom(coffee: CoffeeDraft): CoffeeDraft` (`id`, `roastDate`, `openedDate`, `photo1` y `photo2` a `null`; `archived` y las marcas de tiempo no están en el borrador). |
| `coffee/CoffeeValidator.kt` (nuevo) | `enum class CoffeeError { NAME_REQUIRED, ALTITUDE_INVALID, ALTITUDE_RANGE, BAG_WEIGHT_INVALID, PRICE_INVALID, ROAST_DATE_FUTURE, OPENED_DATE_FUTURE }` y `object CoffeeValidator { fun validate(draft: CoffeeDraft, today: LocalDate): Set<CoffeeError> }`. |
| `coffee/CoffeeSpelling.kt` (nuevo) | `canonicalize(draft: CoffeeDraft, existing: CoffeeSuggestions): CoffeeDraft`: `canonicalCoffee` sobre tostador, país, región y cada variedad. |
| `coffee/CoffeeSearch.kt` (nuevo) | `fold(text: String): String` (minúsculas y sin marcas diacríticas) y `matches(item: CoffeeListItem, query: String): Boolean` (D18). |
| `coffee/label/LabelModels.kt` (nuevo) | `interface LabelOcr` y `OcrLine` tal como §3.3 del spec, y `LabelFields` del §3.3 más `originRegion`, `farm` y `processOther`, con `null` por defecto (D40). |
| `coffee/label/LabelKeywords.kt` (nuevo) | Diccionarios es/en plegados con `fold`, con las frases largas primero: procesos (D28), variedades (Geisha/Gesha, Bourbon con sus colores, Typica, Caturra, Catuaí, Pacamara, SL28, SL34, Heirloom, 74110, 74112, Castillo, Maragogipe, Mundo Novo, Pache, Villa Sarchi, Sidra, Java, Ruiru 11, Batian, Wush Wush, Laurina, Catimor, Parainema, Marsellesa), niveles de tueste y niveles combinados que no se rellenan (D38), países (nombre canónico en español), rótulos de campo (D40), rótulos de fecha y palabras que excluyen una línea de la búsqueda de fechas (D41). Palabras que descartan una línea como nombre: "tostado", "roasted", "peso", "net", "lote", "proceso", "variedad", "altitud", "notas", "est.", "molido", "grano", "ground", "whole bean"… |
| `coffee/label/LabelDates.kt` (nuevo) | `object LabelDates { fun findAll(lines: List<String>, today: LocalDate): List<LocalDate>; fun pick(dates: List<LocalDate>, today: LocalDate): LocalDate? }`: formatos y reglas de §3.3 (incluido `ddmmaa`) y D41. Recibe las líneas para saber si una fecha va junto a un rótulo de fecha o en una línea excluida. Meses en español e inglés (abreviados y completos, "sept" y "set", y "12 de octubre de 2026"). |
| `coffee/label/LabelParser.kt` (nuevo) | `object LabelParser { fun parse(lines: List<OcrLine>, today: LocalDate): LabelFields }`, en este orden: (1) valores con rótulo de campo (D40); (2) palabras clave sueltas para lo que siga vacío, saltando las líneas de notas y las de varias opciones (D37); (3) proceso (D28), nivel (D38), altitud y peso (D39) y fechas (`LabelDates`, D41); (4) nombre y tostador por altura de línea, con la unión del proceso al nombre (D28). Funciones internas puras y probadas por separado: `labeledValues(lines)`, `processesIn(line)`, `levelIn(line)`, `altitudeIn(line, labeled)` y `weightsIn(line)`. |
| `coffee/label/LabelScore.kt` (nuevo) | Puro; lo usan `LabelFixtureCaptureTest` (dispositivo) y `LabelParserTest` (JVM), que no comparten otro código. `LabelExpectation(file, name, roastDate: LocalDate?)` y `parseExpected(tsv: String)`: `archivo<TAB>nombre<TAB>aaaa-mm-dd`, con la fecha vacía = sin fecha. `sameName(expected, actual)`: iguales sin mayúsculas, tildes (`fold`) ni espacios. `score(expected, parsed: Map<String, LabelFields>): LabelScore(nameHits, nameTotal, noDateTotal, inventedDates: List<String>, handwritten: List<HandwrittenDate>)` y `meetsTarget(minNameHits = 12)`: nombres ≥ 12 y ninguna fecha inventada. Las etiquetas con fecha esperada son las 2 escritas a mano: su lectura se informa en `handwritten` y no cuenta. `toReport()` genera `report.txt`. |
| `ui/coffees/CoffeeForm.kt` (nuevo) | `enum class CoffeeField` (uno por campo editable). `data class CoffeeForm` con los textos tal como se escriben (altitudes, peso y precio), las fechas como `LocalDate?`, las variedades como lista más el texto pendiente, las fotos y `ocrFilled: Set<CoffeeField>`. `edit(field, transform)` quita la marca del campo. `applyOcr(fields: LabelFields): OcrResult(form, filled: Set<CoffeeField>)`: solo campos vacíos, y marca los rellenados (altitud cuenta como un campo y, con un único valor, rellena "desde" y "hasta"; proceso y su texto de `OTHER` cuentan como uno; también región y finca, D40). `errors(today): Map<CoffeeField, CoffeeError>` (parseo de D34 + `CoffeeValidator`), `toDraft()`, `isDirty(initial)` (sin contar las marcas) y `from(draft)`. |
| `ui/coffees/CoffeeFormatters.kt` (nuevo) | `formatAltitude(min, max)` ("1800–2000 m" o "1900 m", con U+2013), `formatPrice(price)` (D36), `formatGrams(g)` ("180 g"), `formatRoastLine(roastDate, days)` y las etiquetas `@Composable` de `CoffeeProcess` y `RoastLevel`. |

Nada de `coffee/` (salvo lo que llegue en F2 y F3) ni `ui/coffees/CoffeeForm.kt` importa `android.*` o
`androidx.*`.

**Tests** (`app/src/test/kotlin/dev/dotscale/`):

| Archivo | Casos |
|---|---|
| `coffee/CoffeeValidatorTest.kt` | Nombre vacío o de espacios. Peso, precio y altitud 0 o negativos. "Desde" mayor que "hasta". Un solo valor de altitud válido. Fechas de tueste y de apertura futuras; hoy es válida. |
| `coffee/CoffeeDerivedTest.kt` | Stock: sin peso → `null`; 250 − (15 + 18) = 217; dosis `null` no descuenta (se pasa ya sumado); 250 − 260 → agotado y se muestra 0; exactamente 0 → agotado. Días: 0 el día del tueste, 9 nueve días después, `null` sin fecha y con tueste posterior. Días de una preparación con la fecha local de su inicio (prueba con una zona UTC−5 cerca de medianoche). `newBagFrom` copia todo salvo id, fechas y fotos. |
| `coffee/CoffeeSpellingTest.kt` | "etiopía" → "Etiopía" existente; "café de altura" → "Café de Altura"; variedad nueva se queda; variedades repetidas sin distinguir mayúsculas se unen. |
| `coffee/CoffeeSearchTest.kt` | Coincide por nombre, tostador, país y región; "ETIOPIA" encuentra "Etiopía"; vacío coincide con todo; no busca en las notas. |
| `coffee/label/LabelDatesTest.kt` | `12/10/2026`, `12/10/26`, `2026-10-12`, "12 oct 2026", "Oct 12, 2026", "12 de octubre de 2026"; `03/04/26` → 3 de abril; `10/25/2026` → 25 de octubre (reserva `mm/dd`); `31/02/2026` descartada; varias fechas → la más reciente no futura; solo futuras → `null`. Con rótulo (D41): "Fecha de tueste: 120926" → 2026-09-12; "Tostado: 12.10" → 12 de octubre del año actual, o del anterior si sería futura; rótulo en una línea y fecha en la siguiente. Sin rótulo, nada: "120926", "Lote 120926", "12.10" y "12.5 oz". Fechas engañosas, nada: "EST. 2021", "EST. 2013", "2024: 7°", "Premio 2024" y "Since 2013". |
| `coffee/label/LabelParserTest.kt` | Casos sintéticos, con los formatos vistos en las 15 etiquetas reales. **Proceso (D28):** "Lavado", "Washed", "Natural", "Honey" → su valor; "Anaeróbico lavado" y "anaerobic natural" → `ANAEROBIC`; "Semi lavado", "Semilavado" y "wet hulled" → `WET_HULLED`, nunca `WASHED`; "Yellow honey" y "Red honey" → `HONEY`; "Fermentación láctica" → `OTHER` con `processOther = "Fermentación láctica"`; "notas de miel" no es proceso; "Lavado" en una línea y "Natural" en otra, sin rótulo → `null`. **Nombre (D28):** "Pacamara" + "Red honey" → "Pacamara Red Honey" y `HONEY`; "Geisha" + "Anaeróbico lavado" → "Geisha Anaeróbico Lavado" y `ANAEROBIC`; la línea siguiente que no es un proceso no se une; nombre = la línea más alta que no es fecha, peso, rótulo ni "Molido / Grano". **Opciones (D37):** "PROCESS: WASHED NATURAL HONEY" → proceso `null`; "200 gr / 400 gr" → peso `null`; "Claro Medio Oscuro" → nivel `null`. **Nivel (D38):** "Tueste medio" y "Medio" → `MEDIUM`; "Light - Medium" y "medio-oscuro" → `null`; "light" en una frase de notas → `null`. **Números (D39):** "1,560 msnm", "1,850 M.S.N.M.", "1,600 Mts.", "1,500 m s.n.m.", "Altura \| 1,200" y "Cultivado a: 1350 msnm" → 1560, 1850, 1600, 1500, 1200 y 1350 (mínimo y máximo iguales); "1800-2000", "1800 – 2000" y "1800 a 2000 m" → rango; "1.800 msnm" → 1800; "1,560" sin unidad ni rótulo → `null`; "15 min" → `null`; peso "12oz. / 340g." → 340, "454 g", "340g", "250G" y "0,5 kg" → 500; "15 g de café" → `null`. **Rótulos (D40):** "Variedad: Caturra, Chiroso" → las dos (Chiroso aunque no esté en el diccionario); "Process: Natural", "Origin: Colombia", "Región: Huila", "Finca: La Esperanza", "Productor: Juan Pérez" → su campo; rótulo con el valor en la línea siguiente; variedad en una línea de "Notas" → no cuenta. **Fechas (D41):** una etiqueta completa sin fecha y con "EST. 2021" y un premio "2024: 7°" → `roastDate = null`. País en inglés → español; "Colombia" solo como país. En F4 se le añaden los fixtures reales. |
| `coffee/label/LabelScoreTest.kt` | `parseExpected` con fecha vacía → `null` y con fecha ISO; líneas mal formadas → error con el número de línea. `sameName`: "Pacamara Red Honey" = "PACAMARA RED HONEY" = "pacamara redhoney"; "Etiopía" = "Etiopia"; nombres distintos no. `score`: 12 de 15 nombres y ninguna fecha inventada → `meetsTarget`; 11 de 15 → no; una fecha leída en una etiqueta sin fecha → no, y aparece en `inventedDates`; las fechas a mano mal leídas solo aparecen en `handwritten`. |
| `ui/coffees/CoffeeFormTest.kt` | `applyOcr` no pisa un campo escrito, rellena los vacíos y los marca; editar un campo marcado le quita la marca; dos lecturas seguidas: la segunda solo rellena lo que siga vacío; altitud única rellena los dos campos. Números de D34 ("18,50" y "18.50" válidos, "18,505" no; "1800,5" no es altitud). `toDraft` con un solo campo de altitud. `isDirty` no cuenta las marcas. `applyOcr` con `OTHER` rellena proceso y texto como un solo campo marcado; región y finca se rellenan y marcan. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.coffee.*" --tests "dev.dotscale.ui.coffees.*"`
y `grep -rln "import android" app/src/main/kotlin/dev/dotscale/coffee app/src/main/kotlin/dev/dotscale/ui/coffees/CoffeeForm.kt`
sin resultados.

Commit sugerido: `feat(coffee): add coffee model, validation, derived values and label parser`.

### F2. Base de datos v3 y repositorios

**Objetivo:** tabla `coffees`, columnas nuevas en `brews`, migración, repositorio de cafés y escritura del
vínculo. Es aditiva: el comportamiento actual del historial no cambia hasta F7.

**Archivos:**

| Archivo | Contenido |
|---|---|
| `coffee/CoffeeEntities.kt` (nuevo) | `CoffeeEntity` (`coffees`, campos de §3.1, `@Index("name")` y `@Index("archived")`) con `toDraft()`. `CoffeeListRow` (proyección de la lista con `stockG: Double?`). `CoffeeLogRow`. `CoffeeDetail(coffee: CoffeeEntity, stockG: Double?, soldOut: Boolean, daysSinceRoast: Int?, brews: List<CoffeeLogEntry>)`. `CoffeeOption(id, name, roastDate)`. |
| `coffee/CoffeeConverters.kt` (nuevo) | Los conversores de D17. |
| `coffee/CoffeeDao.kt` (nuevo) | `list(archived): Flow<List<CoffeeListRow>>` (D13, D19), `pickable(): Flow<List<CoffeeListRow>>` (no archivados), `observeRow(id)`, `observe(id): Flow<CoffeeEntity?>`, `get(id)`, `logEntries(id): Flow<List<CoffeeLogRow>>` (más reciente primero), las cuatro consultas de valores distintos (`roaster`, `originCountry`, `originRegion`, `varieties`) excluyendo un id, `exists(id)`, inserción, actualización y borrado. `@Transaction save(draft): SaveResult(id, inserted, removedPhotos)`: grafía canónica (`canonicalize`, sin contar la propia fila), alta o edición y, si cambió el nombre, `UPDATE brews SET coffee = :name WHERE coffeeId = :id`. `@Transaction delete(id): CoffeeEntity?`. |
| `coffee/CoffeeRepository.kt` (nuevo) | Contrato del §3.5 del spec más `created: SharedFlow<Long>` (D23), `get(id): CoffeeEntity?` (editor) y `suggestions()`. Constructor `CoffeeRepository(db: HistoryDatabase, photos: PhotoFiles, today: () -> LocalDate = LocalDate::now, zone: () -> ZoneId = ZoneId::systemDefault)`. `fun interface PhotoFiles { fun delete(names: Collection<String>) }` (en F2, un no-op; en F3, `CoffeePhotoStore`). `coffees(query, archived)` combina `list(archived)` con `CoffeeSearch.matches` en `Dispatchers.Default`. `save` y `delete` en `IO + NonCancellable`; los archivos se borran después de la transacción (D26). |
| `history/BrewEntities.kt` | `BrewEntity`: `coffeeId: Long? = null` (FK a `coffees.id`, `SET_NULL`, `Index("coffeeId")`) y `rating: Int? = null`. |
| `history/HistoryModels.kt` | `UsedCoffee(id: Long, name: String)`, `BrewContext.coffee: UsedCoffee? = null` y `BrewDetails.rating: Int? = null` (D21, D22). |
| `history/BrewDao.kt` | `insertBrew(brew, curveData, targetData, coffee: UsedCoffee?)`: comprueba que el café existe y recalcula su `lastUsedAtEpochMs` (D20, D21). `@Transaction linkCoffee(brewId, coffeeId: Long?)`: escribe `coffeeId` y la copia del nombre (o los dos `null`) y recalcula el café anterior y el nuevo. `applyDetails` escribe también `rating`. `deleteBrew` y `restore` recalculan `lastUsedAtEpochMs`. `coffeeOptions(): Flow<List<CoffeeOption>>` (D33). |
| `history/BrewRepository.kt` | `save` pasa `context.coffee` al DAO. `suspend fun setCoffee(brewId: Long, coffeeId: Long?)` (`IO + NonCancellable`). `coffeeOptions()`. |
| `history/Migrations.kt` | `val MIGRATION_2_3` (D16). |
| `history/HistoryDatabase.kt` | `version = 3`, `CoffeeEntity` en `entities`, `abstract fun coffeeDao(): CoffeeDao`, `@TypeConverters(HistoryConverters::class, CoffeeConverters::class)` y `.addMigrations(migration1To2(texts), MIGRATION_2_3)`. KDoc con la versión 3. |
| `settings/BrewSettings.kt` | `interface CoffeeStore` (D32), implementada por `BrewSettings`: `lastCoffeeId: StateFlow<Long?>` y `setLastCoffeeId(id: Long?)`, clave `last_coffee_id`. |
| `DotScaleApp.kt` | `val coffeeRepository by lazy { CoffeeRepository(historyDatabase, PhotoFiles { }) }`. |

**Esquema:** `app/schemas/dev.dotscale.history.HistoryDatabase/3.json` se genera al compilar y se añade al
repositorio. Los `CREATE TABLE` e índices de `MIGRATION_2_3` se copian de ahí.

**Tests** (`app/src/androidTest/kotlin/dev/dotscale/`):
- `coffee/CoffeeDaoTest.kt` (nuevo, Room en memoria y un `PhotoFiles` que registra lo borrado). Los casos del
  §5 del spec:
  - Búsqueda por los cuatro campos, sin mayúsculas ni tildes, y que no devuelve archivados en "Activos".
  - Archivar y desarchivar.
  - Stock: dos preparaciones con dosis y una sin dosis; cambiar `doseG` por SQL y borrar una preparación
    corrigen el stock sin más; sin peso → `null`; agotado.
  - Borrar un café con preparaciones: `coffeeId` pasa a `null`, `coffee` conserva el nombre y se piden borrar
    `photo1`, `photo2` y sus miniaturas.
  - `lastUsedAtEpochMs`: guardar, vincular una preparación antigua (no lo adelanta), desvincular y borrar la
    última lo recalculan.
  Además: renombrar actualiza `brews.coffee`; grafía canónica de tostador, país, región y variedades;
  `suggestions()` sin repetidos; reemplazar una foto pide borrar la anterior; `created` emite en el alta y no
  en la edición; guardar una preparación con un café ya borrado la deja sin vínculo y con el nombre;
  `pickable()` sin archivados y con los agotados.
- `history/HistoryMigrationTest.kt` (ampliado): crea la v2 con dos preparaciones por SQL, migra con
  `MIGRATION_2_3` y valida. Se conservan todas las columnas; `coffeeId` y `rating` valen `null`; `coffee`
  sigue con su texto; `coffees` está vacía; `brew_curves` y `brew_targets` intactas. Después, insertar un café,
  vincular y borrar el café deja `coffeeId = null` (la FK funciona en una base migrada). Una segunda prueba
  encadena 1 → 2 → 3. La prueba actual abre Room con las dos migraciones.
- `history/BrewDaoTest.kt` (ampliado): `insertBrew` con café, `linkCoffee` con `null` y la valoración en
  `applyDetails`.
- `history/TestBrews.kt`: `context(..., coffee: UsedCoffee? = null)` y un `coffeeDraft(...)` determinista.

**Verificación:** `./gradlew :app:compileDebugKotlin` (genera `3.json`) y
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.coffee.CoffeeDaoTest,dev.dotscale.history.HistoryMigrationTest,dev.dotscale.history.BrewDaoTest`.

Commit sugerido: `feat(coffee): migrate the database to v3 with the coffee catalog`.

### F3. Fotos, OCR y `FileProvider`

**Objetivo:** guardar fotos reducidas con miniatura, leerlas con ML Kit y exponer archivos a otras apps.

**Gradle** (`gradle/libs.versions.toml` y `app/build.gradle.kts`):
`mlkit-text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "mlkitTextRecognition" }`
con la última estable de Google Maven al empezar la fase (16.0.1 es la última que se conoce hoy) e
`implementation(libs.mlkit.text.recognition)`.

**Archivos:**

| Archivo | Contenido |
|---|---|
| `coffee/photo/PhotoSizing.kt` (nuevo, puro) | `inSampleSize(width, height, maxSide)`, `scaledSize(width, height, maxSide)`, `centerSquare(width, height)` y `exifRotationDegrees(orientation: Int)` (valores EXIF 1–8; las variantes espejo se tratan como su giro). |
| `coffee/photo/CoffeePhotoStore.kt` (nuevo) | `class CoffeePhotoStore(dir: File, cacheDir: File) : PhotoFiles`. `suspend fun import(open: () -> InputStream): String` (D25; en `Dispatchers.IO`; lanza `PhotoImportException` si no se puede leer o escribir), `file(name)`, `thumbFile(name)`, `override fun delete(names)` (foto y miniatura), `sweepOrphans(referenced: Set<String>, olderThanMs: Long)` y `newCameraTarget(): File` (`cacheDir/camera/capture-<n>.jpg`). |
| `coffee/photo/CoffeeImages.kt` (nuevo) | `class CoffeeImages(store, maxBytes)`: `suspend fun thumbnail(name): ImageBitmap?` y `suspend fun photo(name, maxSidePx): ImageBitmap?`, con `LruCache<String, ImageBitmap>` por bytes y decodificación en `Dispatchers.IO.limitedParallelism(2)`. `val LocalCoffeeImages` (D14). |
| `coffee/label/TaskAwait.kt` (nuevo) | `suspend fun <T> Task<T>.await(): T` (D27). |
| `coffee/label/MlKitLabelOcr.kt` (nuevo) | `class MlKitLabelOcr : LabelOcr, Closeable` (D27). |
| `res/xml/file_paths.xml` (nuevo) | `<cache-path name="camera" path="camera/" />` y `<cache-path name="shared" path="shared/" />`. |
| `AndroidManifest.xml` | `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">` con `FILE_PROVIDER_PATHS`, y la retirada de `INTERNET` de D29 si aparece. Sin `CAMERA`. |
| `DotScaleApp.kt` | `coffeePhotoStore` (`filesDir/coffee_photos`), `coffeeImages` (16 MB), `labelOcr` (perezoso) y el repositorio con `coffeePhotoStore`. En `onCreate`, `applicationScope.launch { coffeePhotoStore.sweepOrphans(…, 24 h) }`. |
| `MainActivity.kt` | `CompositionLocalProvider(LocalCoffeeImages provides app.coffeeImages)`. |

**Permiso `INTERNET` (§6.2 del spec):** `./gradlew :app:processDebugMainManifest` y buscar `INTERNET` en el
manifiesto fusionado (`find app/build/intermediates -path "*merged_manifest*" -name AndroidManifest.xml`).
Anotar en este plan qué permisos añade ML Kit y aplicar D29.

**Tests:**
- `test/.../coffee/photo/PhotoSizingTest.kt`: 4000 × 3000 → `inSampleSize` 2 y 1600 × 1200; 1200 × 900 sin
  cambios; cuadrado central; orientaciones 6 → 90 y 8 → 270.
- `androidTest/.../coffee/photo/CoffeePhotoStoreTest.kt`: un JPEG de 4000 × 3000 con EXIF 6 → foto de
  1200 × 1600 y miniatura de 256 × 256; `delete` borra las dos; bytes ilegibles → `PhotoImportException`;
  `sweepOrphans` respeta las referenciadas y las recientes.
- `androidTest/.../coffee/label/SyntheticLabel.kt` (nuevo, ayuda): dibuja con `Canvas` una etiqueta de
  1600 × 1200 ("ETIOPÍA GUJI" grande, "Café de Altura", "Tostado 12/10/2026", "Natural", "1900 msnm",
  "250 g").
- `androidTest/.../coffee/label/MlKitLabelOcrTest.kt`: la etiqueta sintética devuelve líneas con esos textos,
  la del nombre es la más alta, y `LabelParser` saca nombre, fecha, proceso, altitud y peso. Con el
  emulador en modo avión el resultado es el mismo (modelo incluido en el APK). **Rotación:** la misma
  etiqueta guardada en horizontal (píxeles girados 90° y EXIF 6), importada con `CoffeePhotoStore` y leída
  después, da las mismas líneas: la rotación EXIF se aplica antes del OCR (D25, D27). Una de las 15 fotos
  reales (`IMG_20261113_134720.jpg`) está tomada así.

**Verificación:** `./gradlew :app:compileDebugKotlin`, `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.coffee.photo.*"`,
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.coffee.photo.CoffeePhotoStoreTest,dev.dotscale.coffee.label.MlKitLabelOcrTest`,
la revisión del manifiesto fusionado y `zipalign -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk`
para las bibliotecas nativas de ML Kit (R3).

Commit sugerido: `feat(coffee): store label photos and read them with on-device OCR`.

### F4. Etiquetas reales

**Objetivo:** el procedimiento del §5 del spec con las 15 fotos del usuario en `~/Downloads/coffee-bags/`
(P1; cada foto es una bolsa distinta y se mide por foto): fixtures versionados del texto que da ML Kit y el
informe con los dos objetivos del spec.
- **Nombre:** bien extraído en al menos 12 de 15, comparado sin mayúsculas, tildes ni espacios
  (`LabelScore.sameName`).
- **Fecha:** en las 13 etiquetas que no la traen, el parser la deja vacía (13 de 13). Las 2 fechas escritas
  a mano se informan, pero no bloquean. La lectura de fechas impresas la cubren los casos sintéticos de F1.
- El tostador se informa, pero no cuenta: casi siempre es un logo y en 2 fotos no se ve (D28).

**Gradle:** `kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }`
y `testImplementation(libs.kotlinx.serialization.json)`. En `android.testOptions.unitTests.all`, la
propiedad del sistema `dotscale.labelsExpected` con `providers.gradleProperty("labelsExpected").getOrElse("")`.

**Archivos:**

| Archivo | Contenido |
|---|---|
| `androidTest/.../coffee/label/LabelFixtureCaptureTest.kt` (nuevo) | Solo corre con el argumento `labelsCapture=true` (D31). Lee las fotos de `getExternalFilesDir("labels")`, las importa con `CoffeePhotoStore` (en un directorio temporal) como en la app, así que se reducen y se giran según su EXIF antes del OCR, y las pasa por `MlKitLabelOcr`. Escribe `<nombre>.json` en `getExternalFilesDir("labels-out")`. Con `expected.tsv` en la misma carpeta, calcula `LabelScore` con `LabelParser.parse(lines, capturedOn)` y escribe `getExternalFilesDir("labels-report")/report.txt`, fuera de la salida de los JSON (y cada línea con `Log.i("LabelReport", …)`): por foto, nombre esperado y leído, acierto o fallo, fecha leída, tostador leído (informativo) y orientación EXIF; después, el total de nombres (objetivo 12 de 15), las fechas inventadas (objetivo: ninguna de 13) y las 2 fechas a mano, leída frente a esperada. Falla si no se cumple alguno de los dos objetivos, después de escribir los archivos. |
| `test/resources/labels/*.json` (nuevos) | Los JSON traídos del dispositivo, versionados (P2). Las fotos y `expected.tsv` no se versionan. |
| `test/.../coffee/label/LabelFixtures.kt` (nuevo) | Carga los JSON con `kotlinx-serialization-json`. |
| `test/.../coffee/label/LabelParserTest.kt` | Con cada fixture: no lanza excepciones, la fecha nunca es posterior a `capturedOn` y el nombre no está vacío. `realLabelsReachTheTarget()`: con `dotscale.labelsExpected`, `LabelScore.meetsTarget()` (nombres ≥ 12 de 15 y ninguna fecha inventada en las 13 sin fecha); el mensaje de fallo es el informe. Sin la propiedad, `assumeTrue` la salta. |

**`expected.tsv`:** una línea por foto, `archivo<TAB>nombre<TAB>aaaa-mm-dd`. Una fecha vacía significa "sin
fecha" y es la que cuenta para el 13 de 13; las dos con fecha son las escritas a mano.

**Lo que hay en las 15 etiquetas y qué regla lo cubre** (cada caso tiene su prueba sintética en F1):

| En las etiquetas | Regla |
|---|---|
| Altitud con coma de miles ("1,560", "1,850 M.S.N.M.", "1,600 Mts.", "1,500 m s.n.m."), "Altura \| 1,200", "Cultivado a: 1350 msnm" | D39 |
| Lavado, Honey, Natural, "Anaeróbico lavado", "Semi lavado"/"Semilavado", "Yellow honey"/"Red honey", "Fermentación láctica" | D28 |
| Título que es una variedad seguido del proceso ("Pacamara" + "Red honey") | D28 (§3.3 del spec) |
| Opciones impresas con casilla: "PROCESS: WASHED NATURAL HONEY", "200 gr / 400 gr", "Molido / Grano" | D37 |
| "Tueste medio", "Medio", "Light - Medium" | D38 |
| "12oz. / 340g.", "454 g", "340g", "250G" | D37, D39 |
| Rótulos "Variedad", "Proceso"/"Process", "Altura"/"Altitud", "Origin", "Región", "Finca", "Productor" | D40 |
| "EST. 2021", "EST. 2013", premios "2024: 7°"; "Fecha de tueste: 120926" | D41 |
| Tostador como logo (en 2 fotos no se ve) | D28: informativo |
| Foto en horizontal (`IMG_20261113_134720.jpg`) | D25 (rotación EXIF al importar) |

**Procedimiento** (`<carpeta>` = `~/Downloads/coffee-bags`, con las 15 fotos y `expected.tsv`; `<serial>` según
`adb devices -l`):

```
./gradlew :app:installDebug :app:installDebugAndroidTest
adb -s <serial> shell mkdir -p /sdcard/Android/data/dev.dotscale/files/labels
adb -s <serial> push <carpeta>/. /sdcard/Android/data/dev.dotscale/files/labels/
adb -s <serial> shell am instrument -w -e class dev.dotscale.coffee.label.LabelFixtureCaptureTest \
  -e labelsCapture true dev.dotscale.test/androidx.test.runner.AndroidJUnitRunner
adb -s <serial> pull /sdcard/Android/data/dev.dotscale/files/labels-out/. app/src/test/resources/labels/
adb -s <serial> pull /sdcard/Android/data/dev.dotscale/files/labels-report/report.txt <carpeta>/report.txt
./gradlew :app:testDebugUnitTest --tests "dev.dotscale.coffee.label.*" -PlabelsExpected=<carpeta>/expected.tsv
```

`report.txt` se trae a la carpeta de las fotos, fuera del repo, porque repite los nombres esperados.

**Ajuste:** cada nombre fallado o fecha inventada se reproduce como caso sintético en `LabelParserTest` y se
corrige la regla que lo produjo, sin cambiar el contrato de `LabelParser`. Una fecha inventada nunca se
acepta: se restringe la regla que la generó (D41). Tras cada ajuste se repite solo el último comando (los
fixtures ya están en el repo). La captura se repite solo si cambia la reducción de la foto o el OCR. El
resultado final (nombres, fechas inventadas y lectura de las 2 fechas a mano) se anota en el campo "Estado"
de este plan.

**Verificación:** los comandos anteriores, y `./gradlew :app:testDebugUnitTest` sin `-PlabelsExpected` (la
prueba de aciertos se salta y el resto pasa).

Commit sugerido: `test(coffee): add real label fixtures for the OCR parser`.

### F5. Pantallas del catálogo

**Objetivo:** Cafés, detalle y editor según F0.3, el `CoffeePicker` que usarán F6 y F7, y el acceso
provisional desde las barras superiores (F0.3).

**Navegación:**
- `NavigationKeys.kt`: `Coffees`, `CoffeeDetail(id)` y `CoffeeEdit(id, fromCoffeeId)` (§3.6 del spec). La
  clave `CoffeeDetail` convive con el modelo `dev.dotscale.coffee.CoffeeDetail`; ningún archivo necesita los
  dos (si hiciera falta, alias de importación).
- `Navigation.kt`: las tres entradas con sus ViewModels, `onOpenCoffees` en Scan y Scale, `onOpenBrew` desde
  la bitácora y la navegación de D24 (`backStack[backStack.lastIndex] = CoffeeDetail(id)` para "Nueva bolsa").

**Archivos** (`app/src/main/kotlin/dev/dotscale/ui/coffees/`, nuevos salvo indicación):

| Archivo | Contenido |
|---|---|
| `CoffeeThumbnail.kt` | `CoffeeThumbnail(photo: String?, size: Dp)` y `CoffeePhotoView(photo, modifier)`, con `produceState` sobre `LocalCoffeeImages` y el marcador sin foto. |
| `CoffeeRow.kt` | La fila de F0.3. |
| `CoffeesViewModel.kt` / `CoffeesScreen.kt` | `setQuery` y `setArchived` (guardados en la pantalla con `rememberSaveable`) y `items: StateFlow<List<CoffeeListItem>?>` con `flatMapLatest`. |
| `CoffeeLogRow.kt` | La fila de la bitácora. |
| `CoffeeDetailViewModel.kt` / `CoffeeDetailScreen.kt` | Estados `Loading`, `Loaded(detail)` y `Gone` (vuelve atrás). `setArchived(Boolean)` y `delete()`. La pantalla de F0.3 en su orden (aviso, fotos, estado, bitácora, datos plegables y acciones), con el plegable en `rememberSaveable`, sin el envío de la imagen (el icono de compartir llega en F8). |
| `CoffeeFacts.kt` | `CoffeeFacts(coffee: CoffeeEntity)`: las filas `CoffeeFact(label, value, testTag = "coffee_fact_<campo>")` del plegable, sin las vacías, y `factsSummary(coffee)` para el `extra` (proceso · nivel). |
| `CoffeeEditViewModel.kt` | `CoffeeEditViewModel(repository: CoffeeRepository, photos: CoffeePhotoStore, ocr: LabelOcr, id: Long?, fromCoffeeId: Long?, today: () -> LocalDate)`. Estados `Loading`, `Ready`, `Saving`, `Saved(id)` y `Gone`. `form`, `initial`, `suggestions`, `photoBusy: Set<Int>`, `ocrRunning` y `events` (`OcrFilled(n)`, `OcrEmpty`, `PhotoFailed`). `addPhoto(slot, open: () -> InputStream)` (importa y, si va bien, lee la etiqueta), `removePhoto(slot)`, `readLabel()` (foto 1 y luego foto 2), `update(field, transform)`, `addVariety(text)`, `removeVariety(text)`, `save()` y `discard()`. D26 en `discard` y `onCleared`. Con `fromCoffeeId`, el formulario parte de `newBagFrom(...)`. |
| `CoffeePhotoSlot.kt` | El hueco de F0.3, con `rememberLauncherForActivityResult(TakePicture())` hacia `photos.newCameraTarget()` (URI de `FileProvider`) y `PickVisualMedia(ImageOnly)`. Cancelar no hace nada. |
| `VarietiesField.kt` | Etiquetas con cierre y `AntAutoComplete(onSelect)` con "Añadir" (`ImeAction.Done` o el botón +). |
| `CoffeeEditScreen.kt` | El editor de F0.3, con `BackHandler` y `AntModal` de descarte. |
| `CoffeePicker.kt` / `CoffeePickerViewModel.kt` | El selector de F0.3. `CoffeePickerViewModel(repository)` expone `pickable: StateFlow<List<CoffeeListItem>>` con `WhileSubscribed`; la búsqueda del selector es local (`CoffeeSearch.matches`). |
| `ui/scan/ScanScreen.kt` | Parámetro `onOpenCoffees` y el icono de F0.3. |
| `ui/scale/ScaleScreen.kt` | `ScaleTopBar` y `ScaleScreen` ganan `onOpenCoffees`. |

**Textos:** prefijos de F0.1 en `values/` y `values-en/`.

**Tests de Compose** (`androidTest/.../ui/coffees/`, Room en memoria, `FakeLabelOcr` y un
`ActivityResultRegistry` de prueba en `LocalActivityResultRegistryOwner` que devuelve una imagen de
`cacheDir`):

| Archivo | Casos |
|---|---|
| `CoffeesScreenTest.kt` | Estado vacío con "Registrar café"; filas con nombre, tostador, "día N" y stock; "Agotado"; búsqueda; "Activos / Archivados"; búsqueda sin resultados. |
| `CoffeeEditScreenTest.kt` | **Alta desde la galería**: "Galería" → la foto aparece → `FakeLabelOcr` rellena nombre, fecha y proceso con sus marcas `ocr_mark_*` y el `AntAlert` → "Guardar" → el café está en la base con la foto en disco. Un nombre escrito antes no se pisa. Editar un campo marcado le quita la marca. "Leer etiqueta" deshabilitado sin fotos y en curso. OCR sin resultados → `AntMessage` y formulario igual. Errores de validación (nombre vacío, "desde" > "hasta", peso 0). Atrás con cambios → `AntModal`; "Descartar" borra la foto nueva. Recuento: "+", "Cámara" (el registro de prueba simula disparar y confirmar) y "Guardar". |
| `CoffeeDetailScreenTest.kt` | **Detalle con bitácora**: tres preparaciones vinculadas en orden con fecha, "Día N", valoración y notas; tocar una llama a `onOpenBrew`. **Orden**: con `performScrollToNode` sobre `coffee_detail_list`, la última `coffee_log_row` queda por encima de `coffee_detail_facts_header` y esta por encima de `coffee_detail_edit` (`top` de sus límites). **Datos plegados por defecto**: al abrir, `coffee_fact_process` no existe y la cabecera muestra "Natural · Claro"; tocar `coffee_detail_facts_header` muestra "Natural" y "1800–2000 m"; tocarla otra vez los oculta; desplegado, sobrevive a la recreación (`StateRestorationTester`); un café con solo el nombre no muestra el plegable. Agotado → `AntAlert` y "Archivar" → archivado. Las acciones se alcanzan desplazando la lista (`performScrollToNode(hasTestTag("coffee_detail_edit"))`): "Nueva bolsa" → `onNewBag(id)`; "Borrar" → `AntModal` con el número de preparaciones → la preparación conserva el nombre. |
| `CoffeePickerTest.kt` | Filas, "Elegido", "Sin café" solo con elección, buscador con más de 5 cafés, dos bolsas distinguidas por la fecha y el vinculado archivado con su etiqueta. |

`ScaleTopBarTest` y `SettingsNavigationTest` se adaptan al parámetro nuevo; el primero comprueba además el
icono de Cafés. `BrewChartTest` pasa `onOpenCoffees = {}`.

**Verificación:** `./gradlew :app:compileDebugKotlin`, `./gradlew :app:lintDebug` (sin `MissingTranslation`)
y los tests anteriores con `connectedDebugAndroidTest`.

Commit sugerido: `feat(coffee): add coffee list, detail and editor screens`.

### F6. Scale: elegir el café

**Objetivo:** el selector de §3.6 del spec con su regla de habilitado, la congelación en `Active` y el
guardado con el café.

**Archivos** (`app/src/main/kotlin/dev/dotscale/ui/scale/`):

| Archivo | Cambio |
|---|---|
| `ScaleCoffees.kt` (nuevo) | `interface ScaleCoffees` (D32), `RepositoryScaleCoffees(repository: CoffeeRepository)` y `object NoScaleCoffees`. |
| `ScaleUiState.kt` | `coffee: PickedCoffee? = null` (elegido fuera de `Active`; congelado en `Active`) y `lastBrew: LastBrew? = null` (`startedAtEpochMs` y `coffee` de la última sesión guardada; la imagen del resumen lo necesita). |
| `ScaleViewModel.kt` | Constructor con `coffeeStore: CoffeeStore = NoCoffeeStore` y `coffees: ScaleCoffees = NoScaleCoffees` tras `notifier`. `coffeeStore.lastCoffeeId.flatMapLatest { id -> coffees.coffee(id) }`: un café archivado o borrado no se preselecciona (§4). El café se congela con `frozenFor`, como el objetivo. `selectCoffee(id: Long?)` (ignorado en `Active`), `requestNewCoffee()` y la recogida de `created` (D23). `save` pasa `UsedCoffee` al `BrewContext` y deja `lastBrew`. `resolveBrew` sin `coffee` en el prellenado: se borra `coffee = entity.coffee` (línea 437) del `BrewDetails` (D21, §3.6 del spec). |
| `BrewChartCard.kt` | Los selectores de D5 con `onPickCoffee: (() -> Unit)? = null` y `coffee: PickedCoffee? = null`. El chip del objetivo deja `extra`. |
| `ScaleScreen.kt` | Estado del selector con `rememberSaveable`, `CoffeePicker` con `coffeePickerViewModel`, `onNewCoffee` y "Nuevo café" → `viewModel.requestNewCoffee()` + `onNewCoffee()`. |
| `Navigation.kt` | `coffeeStore = app.brewSettings`, `coffees = RepositoryScaleCoffees(app.coffeeRepository)`, `viewModel { CoffeePickerViewModel(app.coffeeRepository) }` y `onNewCoffee = { backStack.add(CoffeeEdit()) }`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../ui/scale/ScaleViewModelCoffeeTest.kt` (nuevo) | Con `FakeCoffeeStore` y `FakeScaleCoffees` (en `ScaleTestFakes.kt`): preselecciona `lastCoffeeId`; archivado o borrado → sin café; `selectCoffee` guarda en el store y se ignora en `Active`; cambiar el store en `Active` no cambia el café de la sesión, que es el que se guarda (`BrewContext.coffee`); en `Finished`, elegir otro aplica a la siguiente y `lastBrew` conserva el guardado; `requestNewCoffee` + `created` lo elige; `created` sin haberlo pedido no hace nada; el prellenado de una referencia de tipo preparación no copia el café. |
| `test/.../ui/scale/ScaleViewModelTargetTest.kt` | Las dos aserciones de `prefill.coffee` pasan a `null` (D21). |
| `androidTest/.../ui/scale/CoffeeSelectorTest.kt` (nuevo) | **Selector de café con la báscula simulada**: `ScaleScreen` con `SimulatedScaleConnection`, Room en memoria y un café → `coffee_trigger` → la fila → "+15 g" → "Fijar dosis" → "Simular vertido estable" → `coffee_trigger` deshabilitado en `Active` → "Terminar" → la preparación guardada tiene `coffeeId`, `coffee` y aparece en `coffee(id).brews` con el stock descontado. |
| `androidTest/.../ui/scale/BrewChartCardTargetTest.kt` | El chip pasa a la fila "Objetivo" (mismo `testTag`; el texto es el nombre). Fila "Café" deshabilitada en `Active`. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.ui.scale.*"`, `./gradlew :app:lintDebug`
y los dos tests de Compose con `connectedDebugAndroidTest`.

Commit sugerido: `feat(scale): pick the coffee before pouring`.

### F7. Historial

**Objetivo:** valoración, "Notas de cata", café del catálogo en la edición y el detalle, y el filtro "Café"
por catálogo.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `history/HistoryModels.kt` | `BrewFilter(coffeeId: Long? = null, method: BrewMethod? = null)`. |
| `history/BrewDao.kt` | `list` y `progress` filtran por `coffeeId`. `rating` en la proyección de la lista. `applyDetails` deja de escribir `coffee` (D22). Se borran `coffees()` y `coffeesExcept()`. |
| `history/BrewEntities.kt` | `rating` en `BrewListRow` y `BrewListItem`. |
| `history/BrewRepository.kt` | `coffees()` y `coffeesExcept()` dejan paso a `coffeeOptions()`. |
| `ui/history/HistoryFilters.kt` | `coffees: List<CoffeeOption>?`, etiquetas de D33 y `FilterSaver` con el id. |
| `ui/history/HistoryViewModel.kt`, `ComparePickViewModel.kt`, `ComparePickScreen.kt` | `coffeeOptions()` y el filtro inicial con el `coffeeId` de A. |
| `ui/history/HistoryRow.kt` | La valoración de F0.3. |
| `ui/history/BrewEditForm.kt` | `coffee: CoffeeChoice` (D22) y `rating: Int?`. `toDetails()` con la valoración. |
| `ui/history/BrewEditViewModel.kt` | `BrewEditViewModel(repository, coffees: CoffeeRepository, id)`. Carga la elección (`Linked` con el café vinculado aunque esté archivado, `Legacy` o `None`), `linkedArchived: CoffeeListItem?` para el selector, `requestNewCoffee()` + `created` (D23). `save()`: `updateDetails` y, si cambió la elección, `setCoffee`. |
| `ui/history/BrewEditScreen.kt` | El orden y los campos de F0.3 con `CoffeePicker` y `AntRate`. |
| `ui/history/BrewDetailViewModel.kt` | `BrewDetailViewModel(repository, coffees: CoffeeRepository, id)`. `Loaded(record, coffee: CoffeeEntity?)`, combinando con `coffees.coffee(coffeeId)`. |
| `ui/history/BrewDetailScreen.kt` | La tarjeta "Cata" (`onOpenCoffee: (Long) -> Unit`) y "Detalles" sin café ni notas. |
| `Navigation.kt` | Los ViewModels nuevos, `onOpenCoffee = { backStack.add(CoffeeDetail(it)) }` y `onNewCoffee` en `BrewEdit`. |
| `res/values*/strings.xml` | `brew_edit_notes` y `brew_detail_notes` → "Notas de cata" / "Tasting notes"; `brew_edit_rating`, `brew_edit_rating_clear`, `brew_edit_coffee_legacy` y `brew_detail_tasting`. |

**Tests:**
- `test/.../ui/history/BrewEditFormTest.kt` (ampliado): la valoración entra en `isDirty` y en `toDetails`;
  `Legacy` sin tocar no cuenta como cambio.
- `test/.../history/HistoryModelsTest.kt` (ampliado): `normalized()` deja la valoración entre 1 y 5 o `null`.
- Compose (`androidTest/.../ui/history/`), preparando el texto antiguo por SQL cuando hace falta:
  - `HistoryScreenTest`: **filtro del historial**: las opciones son los cafés con preparaciones vinculadas
    (incluido uno archivado); filtrar por uno muestra solo las suyas; una preparación antigua sin vínculo
    solo aparece con "Todos"; dos bolsas con el mismo nombre se distinguen por la fecha; la fila muestra la
    valoración (`history_row_rating`).
  - `BrewEditScreenTest`: **valoración**: poner 4, guardar y leer `rating = 4`; quitarla con la misma
    estrella y con "Quitar valoración"; "Notas de cata" como etiqueta; elegir un café del selector lo vincula;
    "Sin café" quita vínculo y nombre; una preparación antigua conserva su texto si no se toca.
  - `BrewDetailScreenTest`: tarjeta "Cata" con el café, "día N", valoración y notas; tocar el café llama a
    `onOpenCoffee`; "Detalles" ya no repite café ni notas.
  - `ComparePickScreenTest` y `BrewCompareScreenTest`: se siembran cafés del catálogo; el filtro inicial
    usa el café de A.
- `androidTest/.../history/BrewDaoTest.kt`: filtro por `coffeeId`, `coffeeOptions` con archivados y
  `applyDetails` sin tocar `coffee`.

**Verificación:** `./gradlew :app:compileDebugKotlin`, `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.ui.history.*" --tests "dev.dotscale.history.*"`,
`./gradlew :app:lintDebug` y los tests anteriores con `connectedDebugAndroidTest`.

Commit sugerido: `feat(history): link brews to coffees with rating and tasting notes`.

### F8. Compartir como imagen

**Objetivo:** las cuatro imágenes de F0.4 y su envío (§3.7 del spec).

**Archivos** (`app/src/main/kotlin/dev/dotscale/share/`, nuevos):

| Archivo | Contenido |
|---|---|
| `ShareModels.kt` (puro) | `CoffeeShareData` (sin precio ni stock), `BrewShareData` (resumen o detalle: muestras, resumen, unidad, curva objetivo, título, subtítulo, `fitScore: Int?`, `rating: Int?`, `notes: String?` y pie), `CompareShareData` y `ShareFooterLine(name, roaster, days: List<Int>, side: ShareSide?)`. `footerLines(a, b)` con la regla de D4 (mismo café: una línea sin lado con los dos días; una vinculada: una línea con su lado; dos cafés: dos líneas, forma compacta) y `shareFileName(...)`. |
| `ShareSpec.kt` | `val SHARE_SIZE = DpSize(360.dp, 450.dp)`, `SHARE_DENSITY = 3f` (D1) y `val SHARE_CHART_MIN_HEIGHT = 144.dp` (D2). Un solo formato para las cuatro imágenes. |
| `ShareFrame.kt` | El marco común de F0.4. |
| `CoffeeShareCard.kt`, `BrewShareCard.kt`, `CompareShareCard.kt`, `ShareCoffeeFooter.kt` | Las tarjetas de F0.4. `BrewShareCard` sirve para el resumen y el detalle (sin nota y con nota). La gráfica va con `Modifier.weight(1f).testTag("share_chart")` y `fillHeight = true`: `Chart` (`ui/scale`) en `BrewShareCard` y `CompareChart` (`CompareKind` de agua) en `CompareShareCard`. La tabla usa `compareRows` filtrado a `TOTAL_TIME`, `TOTAL_WATER`, `RATIO` y `STABILITY`; `formatValue` y `formatDiff` de `BrewCompareScreen.kt` pasan a `internal`. |
| `ShareCapture.kt` | `ShareCaptureHost(request: ShareRequest?, onCaptured: (ImageBitmap) -> Unit, onFailed: () -> Unit)` (D30) con `DotScaleTheme(darkTheme = false)` y `LocalDensity` fija. |
| `ShareImages.kt` | `class ShareImages(context)`: `suspend fun write(bitmap: ImageBitmap, name: String): File` (borra `cacheDir/shared/*` y escribe el PNG en `IO`), `uri(file)` (`FileProvider`, `${packageName}.fileprovider`) y `chooser(uri, title)`: `ACTION_SEND`, `image/png`, `EXTRA_STREAM`, `FLAG_GRANT_READ_URI_PERMISSION` y `Intent.createChooser`. |
| `ShareSender.kt` | `fun interface ShareSender` y `rememberShareSender()` (`context.startActivity`). |
| `ShareLauncher.kt` | `rememberShareLauncher(sender): ShareLauncher` que encadena carga de la foto (`CoffeeImages.photo`), captura, escritura y envío; un fallo muestra `AntMessage(Error, "No se pudo generar la imagen")` y no abre el selector (§4). |

**Integración:**

| Archivo | Cambio |
|---|---|
| `ui/scale/BrewChart.kt` | `BrewChart` gana `fillHeight: Boolean = false`: con `true` usa `fillMaxHeight()` en lugar de `height(ChartHeight)`, para que la tarjeta le dé el alto con `weight(1f)`. Con el valor por defecto no cambia nada en pantalla. |
| `ui/scale/BrewChartCard.kt` | `Chart` gana `modifier: Modifier = Modifier` y `fillHeight: Boolean = false`, que pasa a `BrewChart`. |
| `ui/history/CompareChart.kt` | `CompareChart` gana el mismo `fillHeight: Boolean = false`. |
| `ui/coffees/CoffeeDetailScreen.kt` | Icono de compartir (`coffee_detail_share`) y parámetro `shareSender: ShareSender? = null`. |
| `ui/scale/BrewSummaryCard.kt` | `onShare: (() -> Unit)? = null` y el botón de F0.3. |
| `ui/scale/ScaleScreen.kt` | Imagen de resumen con `ui.brew`, `ui.lastFit` y `ui.lastBrew`. |
| `ui/history/BrewDetailScreen.kt` | Icono `brew_detail_share`; la imagen usa `record` y el café del estado. |
| `ui/history/BrewCompareViewModel.kt` / `BrewCompareScreen.kt` | El ViewModel gana `CoffeeRepository` para resolver el café de A y de B (pie); icono `compare_share`. |
| `res/values*/strings.xml` | `share_*` ("Notas del tostador", "días %1$d y %2$d", "Compartir imagen" y "No se pudo generar la imagen"). El ajuste reutiliza `history_row_fit`. |

**Tests:**
- `test/.../share/ShareModelsTest.kt`: `CoffeeShareData` no tiene precio ni stock aunque el café los tenga;
  pie: sin café vinculado no hay líneas, con un texto antiguo tampoco, mismo café → una línea sin lado con
  los dos días ("días 9 y 11", o "día 9" si coinciden), una sola vinculada → una línea con su lado, dos cafés
  → dos líneas con su lado; nombres de archivo.
- `androidTest/.../share/ShareCardsLayoutTest.kt`: con los datos más largos posibles (nombre de 80
  caracteres, 6 variedades, notas de 500 caracteres, objetivo con ajuste, valoración, dos cafés con pie), las
  cuatro tarjetas capturan una imagen de exactamente 1080 × 1350 px; la marca queda dentro del alto; las
  notas no pasan de 3 líneas (`TextLayoutResult` vía `onTextLayout` en un `testTag`); y en el resumen, el
  detalle y la comparación `share_chart` mide al menos `SHARE_CHART_MIN_HEIGHT` (144 dp, es decir, 432 px).
  Con esos datos, la gráfica del detalle mide 155 dp y la de la comparación 169 dp (presupuestos de F0.4).
- `androidTest/.../share/ShareIntentTest.kt`: **compartir lanza un `ACTION_SEND` con un `image/png`**: en
  `CoffeeDetailScreen` con un `ShareSender` falso, un toque en `coffee_detail_share` produce un chooser cuyo
  `EXTRA_INTENT` es `ACTION_SEND`, `image/png`, con `EXTRA_STREAM` de la autoridad
  `dev.dotscale.fileprovider` y `FLAG_GRANT_READ_URI_PERMISSION`; el PNG se decodifica a 1080 × 1350.
  Compartir dos veces deja un solo archivo en `cacheDir/shared/`. Lo mismo para `BrewDetailScreen`,
  `BrewCompareScreen` y `BrewSummaryCard`, también de 1080 × 1350.

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.share.*"`, `./gradlew :app:lintDebug`
y los dos tests de Compose con `connectedDebugAndroidTest`.

Commit sugerido: `feat(share): share coffee cards and brew charts as images`.

### F9. Verificación

**Objetivo:** los criterios de rendimiento, el E2E y la calidad del §5 del spec, con los pasos de §7.

**Archivos:** `androidTest/.../ui/coffees/CoffeePerformanceTest.kt` (nuevo), descrito en §7.2.

**Cierre:** marcar el checklist del §5 del spec, pasar D1–D41 al spec como §3.8, enlazar este plan desde la
cabecera del spec y marcar el plan como completado con las medidas.

Commit sugerido: `test(coffee): add coffee performance test and verify the catalog flow`.

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| Alta, edición, búsqueda, archivar, desarchivar y borrar con los campos de §3.1 | F1, F2, F5 | `CoffeeValidatorTest`, `CoffeeDaoTest`, `CoffeesScreenTest`, `CoffeeEditScreenTest`, `CoffeeDetailScreenTest`, E2E 1, 10, 11 y 12 |
| Alta con foto de la cámara y "Guardar" en ≤ 5 toques | F0 (D7), F5 | `CoffeeEditScreenTest` (registro de prueba), E2E 1b |
| OCR (al añadir y con "Leer etiqueta") rellena solo vacíos y los marca | F1, F5 | `CoffeeFormTest`, `CoffeeEditScreenTest` |
| Nombre bien extraído en al menos 12 de las 15 etiquetas reales (sin mayúsculas, tildes ni espacios) | F1 (D28, D37–D40), F4 | `LabelScore`, `LabelFixtureCaptureTest` (`report.txt`), `LabelParserTest.realLabelsReachTheTarget` con `-PlabelsExpected` |
| Fecha vacía en las 13 etiquetas sin fecha (13 de 13); las 2 escritas a mano se informan; fechas impresas con casos sintéticos | F1 (D41), F4 | `LabelDatesTest`, `LabelParserTest` (sintéticos y fixtures), `LabelFixtureCaptureTest` (`report.txt`) |
| Preparación vinculada al terminar, en la bitácora y con la dosis descontada | F2, F6 | `ScaleViewModelCoffeeTest`, `CoffeeSelectorTest`, `CoffeeDaoTest`, E2E 3 a 5 |
| Borrar una preparación, o cambiar su `doseG` en la base, corrige el stock | F2 | `CoffeeDaoTest` (dosis cambiada por SQL; P3 resuelta), E2E 8 |
| "Agotado" y sugerencia de archivar | F5 | `CoffeesScreenTest`, `CoffeeDetailScreenTest`, E2E 11 |
| "Nueva bolsa" copia todo salvo fechas y fotos | F1, F5 | `CoffeeDerivedTest`, `CoffeeDetailScreenTest`, E2E 10 |
| Valoración y nota de cata editables y visibles en historial, detalle y bitácora | F7, F5 | `BrewEditScreenTest`, `HistoryScreenTest`, `BrewDetailScreenTest`, `CoffeeDetailScreenTest`, E2E 4 a 6 |
| Filtro "Café" con el catálogo | F7 | `BrewDaoTest`, `HistoryScreenTest`, E2E 6 |
| Tarjeta del café en ≤ 2 toques; resumen, detalle y comparación con el café al pie | F0 (D1–D4, D10), F8 | `ShareIntentTest`, `ShareModelsTest`, E2E 9 |
| Tarjeta sin precio ni stock; detalle con valoración y nota de 3 líneas como máximo | F0 (D3), F8 | `ShareModelsTest`, `ShareCardsLayoutTest` |
| Borrar un café conserva el nombre y elimina sus fotos | F2, F3 | `CoffeeDaoTest`, `CoffeePhotoStoreTest`, `CoffeeDetailScreenTest`, E2E 12 |
| Cafés con 200 cafés con foto: primera fila < 1 s | F9 | `CoffeePerformanceTest` |
| OCR de una foto de 1600 px < 3 s | F3, F9 | `CoffeePerformanceTest` (etiqueta sintética) |
| Imagen para compartir < 1 s | F8, F9 | `CoffeePerformanceTest` |
| `LabelParserTest` con fixtures reales y casos sintéticos | F1, F4 | `LabelParserTest` |
| Lectura de fechas (`dd/mm`, `mm/dd`, mes en texto, 2 cifras, futuras) | F1 | `LabelDatesTest` |
| `CoffeeValidator`, grafía canónica, stock, días y `newBagFrom` | F1 | `CoffeeValidatorTest`, `CoffeeSpellingTest`, `CoffeeDerivedTest` |
| Aplicación del OCR al formulario | F1 | `CoffeeFormTest` |
| `CoffeeDaoTest` (búsqueda, archivados, stock, `SET NULL` con nombre y `lastUsedAtEpochMs`) | F2 | `CoffeeDaoTest` |
| `HistoryMigrationTest` de 2 a 3 | F2 | `HistoryMigrationTest` |
| UI: alta desde la galería | F5 | `CoffeeEditScreenTest` |
| UI: detalle con bitácora | F5 | `CoffeeDetailScreenTest` |
| UI: selector de café en Scale con la báscula simulada | F6 | `CoffeeSelectorTest` |
| UI: valoración en "Editar preparación" | F7 | `BrewEditScreenTest` |
| UI: filtro del historial | F7 | `HistoryScreenTest` |
| UI: compartir lanza `ACTION_SEND` con `image/png` | F8 | `ShareIntentTest` |
| `CoffeePerformanceTest` con los tres objetivos | F9 | `CoffeePerformanceTest` |
| Fotos reales fuera del repo y subidas con `adb push` | F4 | Procedimiento de F4 |
| Test excluido que guarda líneas y alturas como JSON versionado | F4 | `LabelFixtureCaptureTest`, `app/src/test/resources/labels/` |
| Comparación con los esperados de fuera del repo (fecha vacía si no la trae) e informe con los dos objetivos | F4 | `LabelFixtureCaptureTest` (`report.txt`), `LabelScore` |
| E2E: alta con una foto real de la galería, revisión y guardado | F9 | E2E 1 |
| E2E: vertido simulado con café, valoración y nota; bitácora y stock | F9 | E2E 3 a 5 |
| E2E: compartir la tarjeta del café y una preparación | F9 | E2E 9 |
| Lint y formato sin avisos nuevos | F0, F9 | `lintDebug` frente a la línea base |
| §6.2: `INTERNET` revisado y decidido | F3 | Manifiesto fusionado, `MlKitLabelOcrTest` en modo avión, E2E 13 |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | ML Kit no está en la caché de Gradle y sin red F3 no compila. El APK crece unos megas por el modelo incluido. | Descargar al empezar F3. Anotar el tamaño del APK antes y después. Es el precio de funcionar sin conexión (§7 del spec). |
| R2 | Room rechaza la base migrada si la FK añadida con `ALTER TABLE` no coincide con la de `3.json`. | `runMigrationsAndValidate` lo detecta en F2. Si falla, recrear `brews` con `PRAGMA foreign_keys` desactivado **fuera** de la transacción de Room no es posible; la alternativa es una migración en dos pasos que copie también `brew_curves` y `brew_targets` a tablas temporales antes de recrear `brews`, con su propio test. |
| R3 | Las bibliotecas nativas de ML Kit no estén alineadas a 16 KB (`targetSdk 37`). | `zipalign -c -P 16` en F3. Si fallan, subir a una versión de ML Kit alineada o anotar la limitación (la app no se publica en Play). |
| R4 | Quitar `INTERNET` provoque errores en la telemetría de ML Kit. | `MlKitLabelOcrTest` y E2E 13 con `adb logcat -b crash -d`. Si algo falla, revertir D29 y anotarlo; el modelo sigue sin usar la red. |
| R5 | El OCR no llegue a 12 de 15 nombres o invente alguna fecha en las 13 etiquetas que no la traen. | Bucle de ajuste de F4: cada fallo se reproduce como caso sintético y se corrige la regla que lo produjo. Una fecha inventada se corrige restringiendo la regla que la generó (más exclusiones de D41 o exigir rótulo), nunca aceptando el fallo, porque el objetivo es 13 de 13. El tostador no cuenta, así que no se ajusta por él. Si los nombres no llegan, valorar en otro spec pasar los cafés del catálogo al parser (cambia su contrato). |
| R6 | La captura de un nodo de tamaño 0 no llegue a dibujarse y `record` no ocurra. | `ShareCardsLayoutTest` lo detecta. Alternativa: el nodo a tamaño real detrás del contenido, sin dibujar la capa (no se ve igual). |
| R7 | Generar y comprimir un PNG de 1080 × 1350 supere 1 s en el emulador. | Medirlo en F8 con `CoffeePerformanceTest`. Si no llega, la foto se precarga al abrir el detalle y la compresión va en `Dispatchers.Default`. |
| R8 | Decodificar fotos de 12 MP agote la memoria. | Primera pasada solo con límites e `inSampleSize` (D25). |
| R9 | La cámara del emulador o el selector de fotos cambien entre versiones y compliquen el E2E por adb. | El E2E usa la galería (§5 del spec) con la foto subida y escaneada; la cámara se cubre con el registro de prueba y con un paso manual (E2E 1b). |
| R10 | 200 cafés con stock calculado y búsqueda en Kotlin sean lentos. | `CoffeePerformanceTest`. La subconsulta usa `index_brews_coffeeId`; las miniaturas se cargan después de la fila. |
| R11 | `ModalBottomSheet` y `DatePickerDialog` en los tests de Compose (animaciones, foco). | Probar el contenido sin estado y una apertura con `mainClock.autoAdvance`, como en `TargetPickerTest`. |
| R12 | Con un icono más, el título de Scale queda muy corto a 360 dp. | `ScaleTopBarTest` comprueba que todas las acciones siguen visibles. El acceso es provisional y no redistribuye la barra (F0.3); si el título molesta, lo resuelve el rediseño de la navegación, fuera de SPEC-004 (§8). |
| R13 | Las medidas de rendimiento en el emulador son ruidosas. | Calentamiento y mediana de 5, como en SPEC-002 y SPEC-003. |
| R14 | Con una bitácora larga, los datos y las acciones del detalle del café quedan muy abajo (D10). | La lista es perezosa y "Archivar" también está en el aviso de agotado, que va arriba; "Compartir" está en la barra superior. Si en el uso real molesta, se valora mostrar las últimas N preparaciones con "Ver todas" o mover "Editar" a la barra superior; las dos cosas cambian el diseño aprobado y se decidirían con el usuario. |

## 6. Preguntas para el usuario

Todas resueltas; no quedan preguntas abiertas.

1. **P1. Etiquetas reales (bloquea F4).** *Resuelto: las fotos están en `~/Downloads/coffee-bags/`
   (15 JPEG, `IMG_20261113_*.jpg`), fuera del repo. El archivo de esperados es `expected.tsv` en esa
   misma carpeta, una línea por foto: `archivo<TAB>nombre<TAB>aaaa-mm-dd`; una fecha vacía significa "sin
   fecha". Cada foto es una bolsa distinta.*
2. **P2. Privacidad de los fixtures.** *Resuelto: se versionan los JSON en `app/src/test/resources/labels/`,
   aunque contengan todo el texto de la etiqueta (son etiquetas comerciales; §5 del spec). Las fotos y
   `expected.tsv` siguen fuera del repo.*
3. **P3. "Editar la dosis".** *Resuelto: la app no permite editar la dosis; el cambio de `doseG` se verifica a
   nivel de datos en `CoffeeDaoTest`, que la cambia por SQL y comprueba el stock (§5 del spec).*
4. **P4. Pie de la comparación con dos cafés (D4).** *Resuelto: una línea de pie por cada preparación con café
   vinculado, o una sola si las dos usan el mismo café (§3.7 del spec).*
5. **P5. Prellenado desde una referencia (D21).** *Resuelto: la referencia deja de copiar el café. En F6 se
   quita `coffee = entity.coffee` del prellenado en `ScaleViewModel.resolveBrew` (línea 437), y el café sale
   solo del selector de Scale (§3.6 del spec, "Referencia y café").*

## 7. Verificación final

### 7.1 Suites

```
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Lint se compara con la línea base de F0. `LabelFixtureCaptureTest` se salta solo (sin `labelsCapture`), y
la prueba de aciertos del parser, sin `-PlabelsExpected`.

### 7.2 Rendimiento (`CoffeePerformanceTest`, emulador con API 36 o superior)

Base en archivo (`coffee-perf.db`) y fotos en un directorio aparte, borrados en `@After`. Cada medida con
una apertura de calentamiento que no cuenta y la mediana de 5:
- **Lista:** 200 cafés con foto y miniatura (generadas con `SyntheticLabel`) y 3 preparaciones vinculadas
  cada uno; desde `setContent` de `CoffeesScreen` hasta el primer `coffee_row`. Menos de 1 s.
- **OCR:** `MlKitLabelOcr.read` de la etiqueta sintética de 1600 × 1200 px. Menos de 3 s.
- **Imagen:** desde el toque en `coffee_detail_share` (café con foto) y en `brew_detail_share` (preparación de
  10 min con objetivo) hasta la llamada al `ShareSender` falso, PNG escrito incluido. Menos de 1 s cada una.

### 7.3 E2E por adb (emulador, báscula simulada, sin ARTEMIS)

Antes: `adb devices -l` (si hay más de uno, preguntar cuál usar), `./gradlew :app:installDebug`,
`adb shell pm clear dev.dotscale`, `pm grant` de `BLUETOOTH_SCAN` y `BLUETOOTH_CONNECT`, "Fin automático" a
30 s, y una etiqueta real en la galería:
`adb push ~/Downloads/coffee-bags/<una foto>.jpg /sdcard/Pictures/` y
`adb shell content call --method scan_volume --uri content://media --arg external_primary`.
Se usa `uiautomator dump` para localizar, `input tap` y `screencap -d <display id>`. En el detalle del café,
la bitácora va antes que los datos y las acciones (D10): para llegar a "Editar", "Nueva bolsa", "Archivar"
o "Borrar" se desplaza con `adb shell input swipe 540 1800 540 600` y se vuelve a hacer `uiautomator dump`.

| Paso | Recorrido | Comprobaciones |
|---|---|---|
| 1 | Scan → Cafés → "Registrar café" → "Galería" → la foto | Campos leídos con "Leído de la etiqueta" y el aviso; corregir lo necesario; "Guardar". La fila muestra miniatura, tostador y "día N". Toques sin teclado: 4 |
| 1b | "+" → "Cámara" → disparador → confirmar → "Guardar" (manual, con la escena virtual del emulador) | 5 toques |
| 2 | Detalle → "Datos del café" (desplegar) → desplazar → "Editar" → peso 250 g → "Guardar" | Los datos estaban plegados al entrar y se ven al tocar la cabecera. "Quedan 250 g" y la barra llena |
| 3 | Atrás hasta Scan → báscula simulada → selector de café → el café | 2 toques. "Café: … · día N" en la tarjeta |
| 4 | "+15 g" → "Fijar dosis" → "Simular vertido estable" → fin → "Añadir detalles" → 4 estrellas y nota → "Guardar" | El selector se deshabilita durante el vertido |
| 5 | Scale → Cafés → el café | La bitácora se ve sin desplazar, debajo del estado, con la preparación (fecha, "Día N", 4 estrellas y la nota). "Quedan 235 g" |
| 6 | Historial → filtro "Café" → el café → la fila → el café en "Cata" | Solo esa preparación, con estrellas; la tarjeta "Cata" abre el detalle del café |
| 7 | `adb shell am force-stop dev.dotscale` → Scale | El café sigue preseleccionado (`lastCoffeeId`) |
| 8 | Detalle de la preparación → "Borrar" → Cafés → el café | "Quedan 250 g" |
| 9 | Detalle del café → compartir → elegir una app; después, detalle de una preparación → compartir | 2 toques. `adb shell run-as dev.dotscale ls cache/shared` muestra un solo PNG; se copia con `adb exec-out run-as dev.dotscale cat cache/shared/<archivo> > /tmp/...` y se revisa a ojo (las dos de 1080 × 1350, con el café al pie y la gráfica legible) |
| 10 | Detalle del café → "Nueva bolsa" → fecha de tueste → "Guardar" | Datos copiados sin fechas ni fotos; se abre el detalle de la bolsa nueva; en la lista, dos filas distinguidas por la fecha |
| 11 | Editar la bolsa nueva con peso 15 g → otro vertido con dosis 15 g → su detalle | "Agotado" y "Archivar" → archivada: desaparece del selector de Scale y sigue en el filtro del historial |
| 12 | Detalle del café original → "Borrar" → confirmar | El historial conserva el nombre en sus preparaciones; `adb shell run-as dev.dotscale ls files/coffee_photos` ya no tiene sus archivos |
| 13 | `adb shell cmd connectivity airplane-mode enable` → alta con foto | El OCR funciona sin red. `adb logcat -b crash -d` vacío durante todo el recorrido |

## 8. Seguimiento fuera de este plan

- Pasar los nombres del catálogo al parser si los nombres no llegan a 12 de 15 (R5); cambia el contrato de
  `LabelParser`.
- Calibración con la DOT física (SPEC-001 §6 y §6.7 de SPEC-003): no afecta a este spec.
- Rediseño de la navegación y de las barras superiores: el acceso a Cafés de este plan es provisional (F0.3).
- Fusionar `feat/coffee-catalog` en `develop` al terminar.
