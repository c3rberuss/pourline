# Plan de implementación: SPEC-011 Copia de seguridad (exportar e importar todos los datos)

| Campo | Valor |
|---|---|
| Spec | [`spec-exportar-datos.md`](spec-exportar-datos.md) (SPEC-011), aprobado el 2026-11-19 |
| Estado | Aprobado el 2026-11-19 (P1 a P7 resueltas, §6). En implementación en `feat/data-backup` |
| Fecha | 2026-11-19 |
| Rama | `feat/data-backup`, desde `develop` (`3dd50f6`). El spec está sin commitear en `develop`: se commitea en la rama junto con este plan cuando el usuario lo autorice |
| Módulos | `:app` (`:protocol` no cambia) |
| Base de datos | Sin cambios de esquema: `HistoryDatabase` sigue en versión 7 y `app/schemas` no cambia. Se añade un DAO (`BackupDao`), que no forma parte del esquema, así que no hace falta `Migration` |
| Formato | `formatVersion = 1` (§3.1) |
| Límites de `TOO_LARGE` | `data.json` ≤ 64 MiB, cada foto ≤ 16 MiB, `manifest.json` y `settings.json` ≤ 256 KiB cada uno, ≤ 10 000 entradas y ≤ 1 GiB descomprimido en total. Se comprueban al importar **y al exportar** (D11) |
| Línea base | Pendiente de F0 |

El spec no trae fases, así que este plan las define. El orden va de dentro afuera, como pidió el usuario: primero el
formato y la validación en la JVM (F1 a F3), después los datos y el gestor con la aplicación atómica (F4 a F6), luego
la interfaz (F7), las reglas de la copia automática de Android (F8) y el rendimiento (F9). F0 mide la línea base y F10
es la verificación. Cada capa se prueba antes de que la siguiente la use.

Al recorrer el código salieron siete cosas que condicionan el plan:

- **Las clases de `settings` no pueden recibir una copia.** Los setters exigen valores válidos con `require` (por
  ejemplo, `BrewSettings.setAutoFinishSeconds`) y ninguna borra todas sus claves. El §3.2 pide escribir a través de
  ellas, borrar las claves ausentes y que sus `StateFlow` cambien sin recrearlas. Cada clase gana `exportInto` y
  `restoreFrom` (D21).
- **"Preparación en curso" es `ScaleUiState.hasSession`, casi.** `BrewService` solo corre mientras `needsKeepAlive()`
  (`BrewKeepAlive.kt:21`), que ya exige `hasSession`. Pero tras "Listo" (`endSession`, `ScaleSessionManager.kt:651`)
  `plan` pasa a `null` mientras el guardado de la preparación puede seguir en `applicationScope` con
  `savedBrew == SavedBrew.Saving` (`ScaleSessionManager.save`, `ScaleSessionManager.kt:524-566`). El plan bloquea también ese caso (D23, P7).
- **Hay estado en memoria que Room no invalida.** `BrewRepository.pendingUndo` y `RecipeRepository.pendingUndo`
  reinsertarían una fila con un id que tras importar es de otra; los editores abiertos en otras pestañas guardarían un
  borrador sobre filas importadas, y `CoffeeRepository.save` borraría además los archivos de las fotos "quitadas" del
  café importado (D22, P6).
- **La tabla `grinders` incluye el catálogo.** Los 11 predefinidos del catálogo v2 están en la base con
  `owned = false`. Un recuento de todas las filas diría "14 molinos" a quien tiene 3 (D5, P5).
- **La copia automática en la nube tiene una cuota de 25 MB por app.** Con fotos, el caso grande la supera, y entonces
  Android no guarda nada en la nube. Por decisión del usuario (P1), las fotos van solo en `device-transfer` (D31, R1).
- **`PourlineApp.onCreate` lanza el barrido de fotos huérfanas y la sincronización del catálogo en paralelo**
  (`PourlineApp.kt:76-85`). La recuperación de una importación interrumpida tiene que ir antes que los dos (D19).
- **Android 14 valida las rutas del ZIP.** Con `targetSdk` 34 o más, `ZipInputStream.getNextEntry()` lanza
  `ZipException` si un nombre contiene `..` o empieza por `/`, antes de que la app lo vea. El lector lo trata como
  `CORRUPT` (D9).

No hay detekt ni ktlint en el proyecto. Como en los planes anteriores, el control es `:app:lintDebug`, y el estilo es
el del código que rodea a cada cambio (dos espacios, KDoc en español).

## 1. Punto de partida

### 1.1 Se reutiliza

| Pieza | Uso |
|---|---|
| Las 11 entidades de Room (`BrewEntity`, `BrewCurveEntity`, `BrewTargetEntity`, `RecipeEntity`, `RecipeStageEntity`, `CoffeeEntity`, `GrinderEntity`, `GrinderUnitEntity`, `GrinderMethodRangeEntity`, `GrinderAnchorEntity`, `GrindCalibrationEntity`) | Las filas que lee y escribe `BackupDao`. Los DTO se mapean desde y hacia ellas (D3). |
| `HistoryConverters`, `CoffeeConverters`, `GrinderConverters` | Los enums se guardan por nombre, con los mismos valores de reserva ante un nombre desconocido (D4). |
| `BrewCurveCodec`, `TargetCurveCodec` | Curvas reales en los fixtures JVM de ida y vuelta. |
| `GrinderCatalogSeeder.sync()` | Paso 4 de la aplicación (§3.5). Ya es idempotente y lee la versión de `GrinderSettings` en cada llamada. |
| `CoffeePhotoStore` (`file`, `thumbFile`, `sweepOrphans`) y `coffee/photo/PhotoSizing.kt` (`centerSquare`, `THUMB_SIDE`) | Rutas de las fotos y regeneración de la miniatura (D14). |
| `CoffeeDao.photoNames()` | Los nombres de foto referenciados, para la huella de la importación (D18). |
| `ScaleUiState.hasSession`, `SavedBrew` | La condición de "preparación en curso" (D23). |
| `BrewRepository.clearPendingUndo()`, `RecipeRepository.clearPendingUndo()` | Invalidación tras importar (D22). |
| `TabNavigation.kt` (`popToRoot`) | Vaciar las pilas de las otras pestañas tras importar (D22). |
| `AntCard`, `AntButton`, `AntModal`, `AntProgress`, `AntSpin`, `AntText`, `AntMessageHost`/`showAntMessage`, `ReportModalPresence` | La tarjeta, los diálogos y los mensajes (D27 a D30). |
| `ui/history/HistoryFormatters.kt` (`formatBrewDateTime`) | La fecha de la copia en el diálogo de confirmación. |
| Tests: `FakeSharedPreferences`, `TestAppDependencies`, el `ActivityResultRegistry` falso de `RecipesImportTest` y `FakeActivityResults`, `SyntheticLabel.writeJpeg`, `TestBrews`, `TestGrinders` (`addGrinder`, `addCalibration`), `TestGrinderCatalog`, `TestRecipes.aeroPressWithActions`, el patrón de `HistoryPerformanceTest` (base en archivo aparte) | La base de los tests nuevos. |

### 1.2 Falta o hay que corregir

- No existe el paquete `backup` ni un DAO que lea o reemplace las 11 tablas.
- Las clases de `settings` no exportan ni restauran sus claves (ver la introducción). Solo `ConnectionSettings` y
  `TourSettings` aceptan un `SharedPreferences` en el constructor, que es lo que permite probarlas en la JVM.
- `CoffeePhotoStore` genera la miniatura dentro de `import` y `thumbName` es privada: no se puede regenerar una
  miniatura sola.
- `CoffeeImages` no tiene `clear()` y `GrindSelection` no puede olvidar la elección de "Moler con".
- `PourlineApp.onCreate` no tiene un sitio para la recuperación antes del barrido y del catálogo.
- No hay un predicado de "preparación en curso" que cubra el guardado tras "Listo".
- `res/xml/backup_rules.xml` y `res/xml/data_extraction_rules.xml` son las plantillas vacías y el manifiesto no las
  referencia (confirmado en `AndroidManifest.xml`).
- `HistoryDatabase` tiene la versión como literal (`version = 7`): el manifiesto necesita `schemaVersion`.
- `AppDependencies` no tiene la copia de seguridad, y `TestAppDependencies` construye `AppDependencies`.
- `AntModal` fija su `testTag` en `"ant_modal"`.
- Ninguna cadena de la copia de seguridad.

### 1.3 Discrepancias entre el spec y el código

Todas quedaron resueltas el 2026-11-19 (§6) y el spec ya recoge la resolución. La columna "Spec" describe el texto
anterior; la tabla se conserva como registro.

| # | Spec | Código real | Propuesta del plan |
|---|---|---|---|
| 1 | §3.1: `"grinders": 3` en los recuentos y "3 molinos" en el diálogo. | `grinders` incluye los predefinidos del catálogo (11 en la versión 2, `owned = false`). | `grinders` cuenta solo "Mis molinos" (`owned = true`), como la fila "Molinos" de Ajustes. La copia lleva todas las filas igualmente (D5). **P5, resuelta.** |
| 2 | §3.1: `"photos": 15`. | `photos/` lleva cada foto y su miniatura. | `photos` cuenta las fotos principales distintas incluidas, sin miniaturas (D5). **P5, resuelta.** |
| 3 | §4: "preparación en curso" es "`BrewService` activo o sesión abierta en `ScaleSessionManager`". | `BrewService` activo implica `hasSession`. Tras "Listo", `hasSession` es falso pero el guardado puede seguir (`SavedBrew.Saving`), y una preparación guardada después de importar se vincularía por id a un café o molino distinto. | `blocksDataImport = hasSession \|\| savedBrew == SavedBrew.Saving` (D23). **P7, resuelta.** |
| 4 | §3.5: "las cachés en memoria que no lo hagan deben invalidarse". | Además de cachés, los editores abiertos en otras pestañas guardan borradores con ids que, tras importar, son de otras filas. | Vaciar las pilas de las otras pestañas e invalidar `pendingUndo`, `GrindSelection` y `CoffeeImages` (D22). **P6, resuelta.** |
| 5 | §3.6: `coffee_photos/` en `cloud-backup`. | La copia automática en la nube admite 25 MB por app; si se supera, no se guarda nada. | Fotos solo en `device-transfer`; en `cloud-backup` y en `backup_rules.xml`, solo la base y los ajustes (D31). **P1, resuelta.** |
| 6 | §5: el test de rendimiento corre "en el móvil del usuario". | Por norma del proyecto, el agente no instala nada en el teléfono del usuario, y `connectedDebugAndroidTest` desinstala la app al terminar (borraría sus datos). | El rendimiento se mide solo en `emulator-5556`, como referencia; en el teléfono del usuario no se ejecuta nada (D34, F9). **P2, resuelta**: el criterio 8 del spec dice ahora "en el emulador". |
| 7 | §3.4: no menciona claves duplicadas. | Una clave primaria o única repetida hace fallar la transacción de Room, que el §4 informa como `IO` ("No se pudo leer o escribir el archivo"), cuando lo que está mal es la copia. | Detectarlas al validar y rechazar con `CORRUPT` (D13). **P4, resuelta.** |
| 8 | §2: `OpenDocument` con `application/zip` y `application/octet-stream`. | Algunos proveedores (ZIP subidos desde Windows) informan `application/x-zip-compressed`, y el archivo no se podría elegir. | Se añade `application/x-zip-compressed` a los tipos del selector (D27). **P3, resuelta.** |
| 9 | §3.7: mensajes por cada `BackupError`. | No hay texto para `TOO_LARGE`. | Dos textos, uno al importar y otro al exportar (D30), ya en el §3.7 del spec. |
| 10 | §3.4: "si falta solo la miniatura, se regenera"; §3.3: la lógica de formato se prueba sin Android. | Regenerar exige `Bitmap`. | El lector recibe un `ThumbnailMaker`; en la JVM, uno falso (D14). |

## 2. Decisiones que completan el spec

Marcadas como "(concreta)" si eligen entre opciones que el spec deja abiertas, y como "(amplía)" si añaden algo que
el spec no dice.

### 2.1 Formato

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Paquete y archivos (concreta §3.3) | `dev.josueamaya.pourline.backup`. **JVM puros** (sin `import android.`): `BackupModels.kt` (contratos del §3.3, `BackupRows`, `BackupFingerprint`, `AppVersion` y la excepción interna `BackupFailure(reason)`), `BackupFormat.kt` (constantes, `BackupJson`, `backupFileName`, `formatCreatedAt`), `BackupDtos.kt`, `BackupMapping.kt`, `BackupValidator.kt`, `BackupArchive.kt` (`BackupLimits`, `BackupWriter`, `BackupReader`), `BackupPhotos.kt` (`planPhotos`, `ThumbnailMaker`), `ImportMarker.kt` y `PhotoSwap.kt`. **Android:** `BackupDao.kt`, `BackupStreams.kt`, `BackupOperations.kt` y `BackupManager.kt`. | Es la separación que pide el §3.3: formato, validación e ida y vuelta se prueban en la JVM. Las entidades de Room se pueden usar en la JVM (ya lo hace `brewEntity` en `BrewRepositoryMappingTest`). |
| D2 | JSON (concreta §3.1) | `internal val BackupJson = Json { explicitNulls = false; ignoreUnknownKeys = false; allowSpecialFloatingPointValues = true }`. Se escribe y se lee por flujo (`encodeToStream` y `decodeFromStream`, con `@OptIn(ExperimentalSerializationApi::class)`: en kotlinx-serialization 1.8.1 son experimentales). Sin `prettyPrint`. | `explicitNulls = false`: un `null` no se escribe y una clave anulable ausente se lee como `null`; una no anulable ausente es un error, que es "faltan campos obligatorios" (`CORRUPT`). `ignoreUnknownKeys = false`: con la misma `formatVersion`, los mismos campos; así nunca se descarta un dato en silencio (ver §8 sobre el §3.8). `allowSpecialFloatingPointValues`: un `Infinity` guardado en un `REAL` no rompe la exportación. El flujo evita tener el JSON entero en un `String`. |
| D3 | `data.json` (concreta §3.1) | Un objeto con una clave por tabla, con el nombre de la tabla de Room (`brews`, `brew_curves`, `brew_targets`, `recipes`, `recipe_stages`, `coffees`, `grinders`, `grinder_units`, `grinder_method_ranges`, `grinder_anchors`, `grind_calibrations`), y una lista de filas. Cada DTO (`BrewDto`, `BrewCurveDto`, `BrewTargetDto`, `RecipeDto`, `RecipeStageDto`, `CoffeeDto`, `GrinderDto`, `GrinderUnitDto`, `GrinderMethodRangeDto`, `GrinderAnchorDto`, `GrindCalibrationDto`) tiene un campo por columna, con el nombre de la columna y sin valores por defecto. Las columnas `NOT NULL` son tipos no anulables; las demás, anulables. Las filas van ordenadas por clave primaria. | Los nombres de la base son los que ya documentan los esquemas de `app/schemas`. Sin valores por defecto, un campo obligatorio ausente es un error en lugar de un cero inventado. |
| D4 | Tipos (concreta §3.1) | `ByteArray` (`BrewEntity.preview`, `BrewCurveEntity.data`, `BrewTargetEntity.data`) con `Base64ByteArraySerializer` (`java.util.Base64`, sin saltos de línea), que decodifica directamente a `ByteArray`. Enums como `String` con su `name`; al leer, las funciones de `HistoryConverters`, `CoffeeConverters` y `GrinderConverters` (un nombre desconocido cae en el mismo valor que en Room). `LocalDate` (`roastDate`, `openedDate`) como `"yyyy-MM-dd"`; una fecha mal formada es `CORRUPT`. `varieties` como lista de cadenas. | Es el §3.1 ("igual que en los `TypeConverters`"). Decodificar el Base64 en el serializador evita una copia en `String` de cada curva. `java.util.Base64` existe desde la API 26, la mínima de la app. |
| D5 | `manifest.json` y recuentos (concreta §3.1; P5) | `ManifestDto(app, formatVersion, schemaVersion, appVersionName, appVersionCode: Long, createdAt, counts: CountsDto)`. `createdAt` con `ISO_OFFSET_DATE_TIME` truncado a segundos (`"2026-11-19T18:45:12-06:00"`). `schemaVersion = HistoryDatabase.VERSION` (constante nueva, D20). La versión de la app sale de `PackageManager` (`buildConfig = false`), en `AppVersion(name, code)`. Recuentos: `coffees` todas las filas (también archivados); `grinders` solo `owned = true`; `grindCalibrations`, `recipes` (también las predefinidas) y `brews`, todas; `photos`, las fotos principales distintas incluidas. Al importar, el resumen **se recalcula** con los datos validados y las fotos presentes; los recuentos del manifiesto son informativos. | Ver las discrepancias 1 y 2. Recalcular evita mostrar en el diálogo un número que el archivo no cumple. |
| D6 | `settings.json` (concreta §3.2) | `SettingsDto` con un campo anulable por clave del §3.2, con `@SerialName` igual a la clave de `SharedPreferences` y el tipo guardado: `theme_mode`, `experience_mode`, `last_scale_address`, `last_scale_name` y `target_ref_type` como `String`; `onboarding_done`, `use_scale`, `stage_vibration` y `stage_sound` como `Boolean`; `auto_finish_seconds`, `countdown_seconds` y `grinder_catalog_version` como `Int`; `last_coffee_id`, `target_ref_id` y `default_grinder_id` como `Long`; `default_dose_g` y `default_ratio` como `Float`. `null` es "clave ausente" y no se escribe. Su espejo en `settings` es `SettingsSnapshot` (D21). | "Solo van las claves presentes en el origen". El `Float` evita que 15,3 se lea como 15,300000190734863. Un tipo distinto (una cadena donde va un `Int`) es `CORRUPT`. |
| D7 | Nombre del archivo (concreta §2) | `fun backupFileName(now: LocalDateTime): String` con `DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.ROOT)`: `pourline-backup-2026-11-19-1845.zip`. La hora local la da el `ViewModel` (`now: () -> LocalDateTime`), para poder fijarla en los tests. | `Locale.ROOT` evita dígitos de otro sistema de numeración. |
| D8 | Disposición del ZIP (concreta §3.1) | Orden: `manifest.json`, `data.json`, `settings.json` y las fotos ordenadas por nombre (`photos/<uuid>.jpg` y `photos/<uuid>_thumb.jpg`). Sin entradas de directorio. Los JSON en `DEFLATED` (nivel por defecto); las fotos en `STORED`, con el CRC32 y el tamaño calculados antes con una lectura previa del archivo. Solo se incluyen las fotos referenciadas por `photo1` o `photo2` que existen en `coffee_photos`, con su miniatura si existe. | Un JPEG no se comprime: `STORED` ahorra CPU en el caso grande (≈ 42 MB de fotos). `ZipInputStream` lee `STORED` porque `ZipOutputStream` escribe los tamaños en la cabecera local. |

### 2.2 Lectura, validación y límites

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D9 | Lectura en una pasada (concreta §3.4) | `BackupReader.read(input: InputStream, staging: File, onRead: (Long) -> Unit): ReadArchive` sobre `ZipInputStream`. La primera entrada tiene que ser `manifest.json`; si no lo es, o no hay entradas, es `NOT_A_BACKUP`. El manifiesto se lee primero como `JsonObject`: si no es JSON, o `app` falta o no es `"pourline"`, `NOT_A_BACKUP`; si `formatVersion` es mayor que `FORMAT_VERSION`, `NEWER_FORMAT`; después se decodifica estricto y un fallo, o una `formatVersion` menor que 1, es `CORRUPT`. **Excepciones:** `ZipException` o `EOFException` antes de validar el manifiesto, `NOT_A_BACKUP`; después, `CORRUPT` (incluye el CRC inválido, el archivo truncado y la `ZipException` de Android 14 por `..` o `/`); `SerializationException`, `IllegalArgumentException` y `DateTimeParseException`, `CORRUPT`; el resto de `IOException` y `SecurityException`, `IO`; el límite superado, `TOO_LARGE`; `OutOfMemoryError`, `TOO_LARGE`. | Rechaza pronto lo que no es una copia, sin descomprimirla. Leer el manifiesto en dos pasos permite decir `NEWER_FORMAT` aunque el manifiesto nuevo tenga campos que esta versión no conoce. |
| D10 | Entradas admitidas (concreta §3.4) | Exactamente una vez `manifest.json`, `data.json` y `settings.json`; las fotos, con el nombre `photos/` + uno que cumpla `^[A-Za-z0-9-]{1,64}(_thumb)?\.jpg$`. Una entrada de directorio exactamente `photos/` se ignora. Cualquier otra (otra ruta, subdirectorio, `..`, `/` inicial, barra invertida, extensión distinta o un nombre repetido) es `CORRUPT`. Falta `data.json` o `settings.json`: `CORRUPT`. Además, el archivo de destino se comprueba con `canonicalPath` dentro de `staging`. | El patrón no admite separadores ni `..`, así que no hay "zip slip" posible. La comprobación canónica es una segunda barrera. Los nombres de foto salen de `UUID.randomUUID()`. |
| D11 | Límites de `TOO_LARGE` (concreta §3.4 y §6; resuelve la pregunta abierta) | `data class BackupLimits(maxDataBytes = 64 MiB, maxSmallEntryBytes = 256 KiB, maxPhotoBytes = 16 MiB, maxTotalBytes = 1 GiB, maxEntries = 10_000)`. Se cuentan los bytes **descomprimidos** al leer cada entrada (no se confía en los tamaños declarados del ZIP), y el total de todas. **También al exportar:** `BackupWriter` cuenta lo que escribe y, si supera un límite, `ExportResult.Failed(TOO_LARGE)` y se borra el documento. Los tests usan límites pequeños. Estimación en la tabla de abajo. | Con un margen de unas 5 veces sobre el caso grande en `data.json` y de unas 18 en el total. Comprobarlos al exportar garantiza que la app nunca escribe una copia que ella misma rechazaría. El límite de `data.json` es el que protege la memoria: la importación guarda las filas en memoria para validarlas antes de tocar nada. |
| D12 | Referencias (concreta §3.4) | `BROKEN_REFERENCES` si alguna de estas no existe en la copia: `brews.coffeeId` → `coffees.id`; `brews.grinderId` → `grinders.id`; `brew_curves.brewId` y `brew_targets.brewId` → `brews.id`; `recipes.grinderId` → `grinders.id`; `recipe_stages.recipeId` → `recipes.id`; `grinder_units`, `grinder_method_ranges` y `grinder_anchors`.`grinderId` → `grinders.id`; `grind_calibrations.grinderAId` y `grinderBId` → `grinders.id`. Las anulables solo si tienen valor. **No** se validan `brews.referenceId` (sin clave foránea: "el origen puede borrarse") ni los ids de los ajustes. | Son exactamente las claves foráneas del esquema 7 (`app/schemas/.../7.json`). Los ids de los ajustes ya se toleran colgando en el origen (Preparar no preselecciona un café borrado). |
| D13 | Estructura (amplía §3.4; P4) | `CORRUPT` si: un id de las cinco tablas con `autoGenerate` es ≤ 0; se repite una clave primaria (también las compuestas: `(recipeId, position)`, `(grinderId, position)`, `(grinderId, method)`, `(grinderId, level)`); se repite una clave única (`recipes.builtInKey`, `grinders.catalogKey`, `grind_calibrations` por `(grinderAId, grinderBId, valueA)` y por `(grinderAId, grinderBId, valueB)`); o `photo1`/`photo2` no cumple el patrón de D10. Se comprueba antes que las referencias. | Un id 0 se insertaría con un id nuevo y rompería las referencias; un duplicado haría fallar la transacción y el usuario leería "No se pudo leer o escribir el archivo". |
| D14 | Fotos (concreta §3.4) | `planPhotos(rows, staged: Set<String>): PhotoPlan`. Una foto referenciada sin su principal en la copia: el campo (`photo1` o `photo2`) pasa a `null`, sin mover la otra, y cuenta en `missingPhotos` (nombres distintos). Con la principal y sin miniatura: `ThumbnailMaker.make(photo, thumb)` en el staging. Lo no referenciado se borra del staging. Si la regeneración falla, el café queda sin miniatura y no cuenta como foto perdida. Para Android, `CoffeePhotoStore.kt` gana `fun regenerateThumbnail(photo: File, thumb: File): Boolean` (decodifica con `inSampleSize`, recorta con `centerSquare`, escala a `THUMB_SIDE` y escribe JPEG 80), y `import` usa el mismo código de escritura. `fun thumbNameOf(name: String): String` pasa a `PhotoSizing.kt` (puro) y la usan `CoffeePhotoStore` y `backup`. | `photo2` sin `photo1` ya es un estado válido (`CoffeeDetailScreen`, `CoffeeEditScreen`). Compartir el código de la miniatura evita dos recortes distintos. |

**Estimación para D11.**

| Contenido | Caso grande (§4) | Base |
|---|---|---|
| `data.json` | ≈ 13 MB | 500 preparaciones de 4 min con curva objetivo ocupan 4,4 MiB en `history-perf.db` (`plan-curva-objetivo.md`): ≈ 9 KB de BLOB por preparación. Por 1000 y en Base64 (× 4/3), ≈ 12 MB, más ≈ 1 KB de campos por fila. |
| Fotos | ≈ 42 MB | 100 fotos de lado mayor 1600 px en JPEG 85 (≈ 400 KB) con su miniatura de 256 px (≈ 20 KB). F0 lo confirma con una foto real. |
| `manifest.json` y `settings.json` | < 2 KB | |
| Total descomprimido | ≈ 55 MB | |

64 MiB de `data.json` equivalen a unas 4 800 preparaciones como esas (más de cuatro años a tres al día). F9 mide los
tamaños reales y los anota en la cabecera; si `data.json` del caso grande pasa de 16 MiB (un cuarto del límite), se
revisan los límites con el usuario antes de cerrar.

### 2.3 Aplicación atómica, marcador y recuperación

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D15 | Concurrencia (concreta §3.3) | Un `Mutex` en `BackupManager` serializa `export`, `prepareImport`, `apply`, `discard` y la recuperación. Todo corre en `Dispatchers.IO`. `BackupOperations` expone `applying: StateFlow<Boolean>` (verdadero durante `apply`) e `imported: SharedFlow<Unit>` (emite al terminar una importación con éxito; `extraBufferCapacity = 1`). | La recuperación al arrancar y una importación del usuario nunca se pisan. `applying` y `imported` son globales para que una pantalla recreada vea el mismo estado. |
| D16 | Directorio de trabajo (concreta §3.4 y §3.5) | `filesDir/backup_import/`, en el mismo volumen que `filesDir/coffee_photos`: `staging/` (fotos de la copia), `old_photos/` (el `coffee_photos` anterior durante el cambio) e `import.json` (el marcador). `prepareImport` vacía `staging/` al empezar. El nombre `coffee_photos` pasa a una constante (`COFFEE_PHOTOS_DIR`) que usan `CoffeePhotoStore` en `PourlineApp` y `BackupManager`. | Mismo volumen para que `renameTo` sea atómico (§3.4). Todo lo temporal en un solo directorio, fuera de las reglas de la copia automática (§3.6). |
| D17 | Orden de `apply` (concreta §3.5) | 1. Escribe `import.json` con `state = APPLYING`, los ajustes de la copia (con `onboarding_done = true`) y la huella esperada (D18). 2. `backupDao().replaceAll(rows)` en una transacción; si falla, borra el marcador y el staging y devuelve `Failed(IO)`. 3. Pasa el marcador a `DB_DONE`. 4. `finishImport(marker)`: `swapPhotos` (D19), `restoreFrom` en cada clase de ajustes, `seeder.sync()`, `invalidateCaches()`, borra el marcador y `backup_import/`, y emite `imported`. **Tras el paso 2, `apply` nunca devuelve `Failed`:** si el paso 4 lanza, el marcador queda en `DB_DONE`, se registra el error y el siguiente arranque lo termina; `apply` devuelve `Success`, porque los datos ya cambiaron. | Es el orden del §3.5, con el marcador que el spec deja al plan. `ImportResult.Failed` promete que "los datos no cambiaron". |
| D18 | Huella de la importación (amplía §3.5) | `BackupFingerprint(counts: List<Int>, maxIds: List<Long>, photoNames: List<String>)`: los recuentos de las 11 tablas, el mayor id de `brews`, `recipes`, `coffees`, `grinders` y `grind_calibrations`, y los nombres de foto referenciados ordenados. La esperada sale de las filas de la copia; la actual, de `BackupDao.fingerprintRow()` y `CoffeeDao.photoNames()`. | Es lo que permite saber al arrancar si la transacción llegó a confirmarse cuando el proceso murió con el marcador en `APPLYING` (entre el paso 2 y el 3). Sin cambiar el esquema no hay otra forma de marcar la base dentro de la misma transacción. |
| D19 | Recuperación al arrancar (concreta §3.5) | `BackupManager.recoverInterruptedImport()`, llamada en `PourlineApp.onCreate` en una sola corrutina de IO **antes** de lanzar `sweepOrphans` y `grinderCatalogSeeder.sync()`. Sin `backup_import/`: nada (no toca la base). Sin marcador: borra `backup_import/` (staging de una confirmación que no llegó). Marcador ilegible: lo trata como `APPLYING` sin huella coincidente. `APPLYING`: compara huellas; si coinciden, sigue como `DB_DONE`; si no, la transacción se revirtió: borra el staging y el marcador. `DB_DONE`: `finishImport(marker)` sin emitir `imported`. `swapPhotos(photos, staging, old)` es idempotente: staging sin `old_photos` → renombra `coffee_photos` (si existe) a `old_photos`, `staging` a `coffee_photos` y borra `old_photos`; staging y `old_photos` sin `coffee_photos` → renombra `staging` y borra `old_photos`; sin staging y con `old_photos` → borra `old_photos`; los tres a la vez (algo recreó `coffee_photos`) → borra ese `coffee_photos`, renombra `staging` y borra `old_photos`. Un `renameTo` fallido lanza `IOException`. | "La base ya importada es la fuente de verdad" (§3.5). Con la recuperación antes, `sweepOrphans` no borra fotos a mitad del cambio ni la sincronización del catálogo lee una versión a medio restaurar. |
| D20 | `BackupDao` (concreta §3.5) | `@Dao abstract class BackupDao` con una consulta `ORDER BY` clave primaria por tabla; `@Transaction open suspend fun readAll(): BackupRows`; `@Transaction open suspend fun replaceAll(rows: BackupRows)`, que borra de hijas a padres (`brew_targets`, `brew_curves`, `brews`, `recipe_stages`, `recipes`, `grind_calibrations`, `grinder_anchors`, `grinder_method_ranges`, `grinder_units`, `grinders`, `coffees`) e inserta de padres a hijas con `@Insert` (conflicto `ABORT`); y `suspend fun fingerprintRow(): FingerprintRow` (una consulta con subconsultas `COUNT(*)` e `IFNULL(MAX(id), 0)`). `HistoryDatabase` gana `abstract fun backupDao(): BackupDao` y `const val VERSION = 7`, con `@Database(version = VERSION)`. | Una transacción de DAO es el patrón del proyecto (`GrinderDao.save`, `RecipeDao.save`). Un DAO no cambia el hash del esquema. `readAll` en una transacción da una foto coherente aunque se guarde una preparación a la vez. No se usa `clearAllTables()`, que abre su propia transacción. |

### 2.4 Ajustes y preparación en curso

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D21 | Ajustes (concreta §3.2 y §3.5) | `settings/SettingsSnapshot.kt` (nuevo): `data class SettingsSnapshot` con los 17 campos anulables del §3.2 (`null` = clave ausente) y `interface BackupableSettings { fun exportInto(snapshot: SettingsSnapshot): SettingsSnapshot; fun restoreFrom(snapshot: SettingsSnapshot) }`. La implementan `ThemeSettings`, `ExperienceSettings`, `ConnectionSettings`, `BrewSettings`, `TargetSettings` y `GrinderSettings`, cada una con sus claves; `TourSettings` no. `exportInto` copia los valores **tal como están guardados** (sin validar; un tipo inesperado se trata como ausente). `restoreFrom` escribe sus claves en **una** edición `prefs.edit(commit = true) { ... }`, con `remove` para las `null`, y después recalcula cada `StateFlow` con la misma lectura que usa el constructor (`readAutoFinish`, `readMode`, `read`, etc.), así que un valor fuera de rango cae en el de por defecto sin lanzar. `ConnectionSettings` recalcula también `useScale` y `scaleModeChosen`. `BrewSettings`, `ExperienceSettings`, `GrinderSettings`, `TargetSettings` y `ThemeSettings` ganan un `internal constructor(prefs: SharedPreferences)`, como `ConnectionSettings`. `BackupManager` fuerza `onboardingDone = true` antes de restaurar. | Es el §3.2 y el §3.5: a través de las clases, borrando lo ausente y con los `StateFlow` al día. `commit = true` (en IO) deja los ajustes en disco antes de borrar el marcador. Sin `require`, una copia con un valor raro no tumba la app. |
| D22 | Invalidación tras importar (amplía §3.5; P6) | `BackupManager` recibe `invalidateCaches: () -> Unit`; en `PourlineApp`: `brewRepository.clearPendingUndo()`, `recipeRepository.clearPendingUndo()`, `grindSelection.clearChoice()` (nuevo: `choice.value = null`) y `coffeeImages.clear()` (nuevo: `cache.evictAll()`). Además, `TabsNavigation` recoge `deps.backup.imported` y, para cada pestaña distinta de `PREPARE`, hace `popToRoot()` en su pila; la de `PREPARE` (`[Prepare, Settings]`) no cambia. En el mismo colector, si `!deps.scaleMode.useScale.value && !deps.scaleSession.isArmed`, `deps.scaleSession.disconnect()`, como `onScaleOff` en Ajustes. | Un deshacer pendiente reinsertaría un id ocupado; un editor abierto guardaría un borrador sobre otra fila y borraría sus fotos. Ajustes solo se abre desde la raíz de Preparar (`Navigation.kt:290-306`), así que su pila es siempre `[Prepare, Settings]`. |
| D23 | Preparación en curso (concreta §4; P7) | `val blocksDataImport: Boolean get() = hasSession \|\| savedBrew == SavedBrew.Saving` en `ScaleUiState`, con KDoc que explica por qué incluye a `BrewService` (`needsKeepAlive` implica `hasSession`). El `ViewModel` la observa para deshabilitar "Importar datos" y la vuelve a mirar al pulsar "Reemplazar": si ya es verdadera, descarta la copia preparada y avisa con el texto del bloqueo. Exportar no se bloquea. | Ver la discrepancia 3. Mirarla otra vez antes de aplicar cubre una preparación empezada mientras el diálogo estaba abierto. |

### 2.5 Interfaz

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D24 | Acceso a los documentos (concreta §2 y §3.3) | `interface BackupStreams { fun openInput(uri: Uri): InputStream; fun openOutput(uri: Uri): OutputStream; fun size(uri: Uri): Long?; fun deletePartial(uri: Uri) }` y `ContentResolverStreams(resolver)`: `openOutput` con modo `"wt"` y, si el proveedor lo rechaza (`FileNotFoundException` o `IllegalArgumentException`), `"w"`; `size` con `OpenableColumns.SIZE`; `deletePartial` con `File.delete()` si el esquema es `file` y, si no, `DocumentsContract.deleteDocument`, todo dentro de un `try` que ignora cualquier fallo. | Los tests usan `Uri.fromFile` (el `ContentResolver` abre `file://`) y uno falso que falla a mitad. `"wt"` trunca si el usuario sobrescribe un archivo existente. |
| D25 | Progreso (concreta §2) | `onProgress(Float)` de 0 a 1, monótono y como mucho cada 1 %. Exportar: bytes escritos sobre el total estimado (tamaño de las fotos más una estimación del JSON). Leer: bytes comprimidos leídos sobre `size(uri)`; sin tamaño, `null` en el estado (indicador indeterminado). Aplicar: por pasos (0,1 antes de la transacción, 0,7 tras ella, 0,8 tras las fotos, 0,85 tras los ajustes y 1 tras el catálogo). | Room no da progreso dentro de una transacción. Limitar las actualizaciones evita recomponer en cada bloque leído. |
| D26 | `BackupViewModel` (concreta §2 y §3.7) | `ui/settings/BackupViewModel.kt`: `BackupViewModel(backup: BackupOperations, sessionBusy: Flow<Boolean>, isSessionBusy: () -> Boolean, now: () -> LocalDateTime = LocalDateTime::now)`. Estado `BackupUiState(task: BackupTask?, progress: Float?, importBlocked: Boolean, confirm: BackupSummary?)` con `enum class BackupTask { EXPORT, READ, APPLY }`. Mensajes por `Channel` (`Exported(counts)`, `Imported(counts, missingPhotos)`, `ExportFailed(reason)`, `ImportFailed(reason)`, `ImportBlocked`). `fun suggestedFileName()`, `fun onExportTarget(uri: Uri?)`, `fun onImportSource(uri: Uri?)` (`null` = selector cancelado: no pasa nada), `fun confirmReplace()`, `fun cancelReplace()` (descarta) y `onCleared()` (descarta la copia preparada). Exportar y aplicar corren en `viewModelScope` dentro de `withContext(NonCancellable)`; leer se cancela si se sale de Ajustes, y entonces el staging se borra. | Los tres botones del §2 y la confirmación. Una exportación o una importación empezadas terminan aunque se salga de la pantalla; una lectura sin confirmar no tiene por qué. |
| D27 | Tarjeta (concreta §3.7) | `ui/settings/BackupCard.kt`: `@Composable fun BackupCard(viewModel: BackupViewModel, snackbar: SnackbarHostState)`. `AntCard` con título `backup_title`, el texto `backup_hint` y los botones "Exportar datos" (`settings_backup_export`) e "Importar datos" (`settings_backup_import`), a todo el ancho. Con `importBlocked`, el segundo deshabilitado y debajo `backup_import_blocked` (`settings_backup_import_blocked`, texto secundario). Con `task` en `EXPORT` o `READ`, los dos botones deshabilitados y una fila con `AntProgress` (o `AntSpin` sin progreso) y `backup_exporting`/`backup_reading` (`backup_progress`). Los selectores: `rememberLauncherForActivityResult(CreateDocument("application/zip"))` lanzado con `suggestedFileName()`, y `OpenDocument()` con `arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")` (P3). `SettingsScreen` gana `backup: BackupViewModel? = null` y compone la tarjeta tras `ReplayTourRow` (línea 200) y antes de la tarjeta de recetas de Experto, sin `ExpertFeature`. | Visible en los dos modos (§3.7). Con `null` por defecto, `SettingsScreenTest` no cambia. El recorrido de Ajustes no cambia: el spec no lo pide. |
| D28 | Diálogos (concreta §2 y §3.5) | Confirmación con `AntModal(title = backup_confirm_title, text = backup_confirm_text, confirmText = "Reemplazar", cancelText = "Cancelar", danger = true, testTag = "backup_confirm")`: `AntModal` gana `testTag: String = "ant_modal"` (los demás usos no cambian). El texto lleva la fecha (`formatBrewDateTime(createdAt.toInstant().toEpochMilli())`), la versión (`appVersionName`) y los recuentos. Durante `APPLY` (o mientras `backup.applying` sea verdadero), un `Dialog` propio con `DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)`, `ReportModalPresence()`, `AntProgress` y `backup_applying` (`backup_apply_progress`). En `TabsNavigation`, la apertura de una receta importada espera también a `!applying` (`LaunchedEffect(pendingImport, hasSession, applying)`, `Navigation.kt:140`). | El diálogo modal tapa la barra de pestañas y se traga "Atrás": no se puede salir de Ajustes ni ir a Preparar (§3.5). La receta que llega por un enlace es la única forma de navegar sin tocar la pantalla. |
| D29 | Mensajes (concreta §2 y §3.7) | En el `AntMessageHost` de Ajustes. Exportar bien: `backup_export_done` con los recuentos (`Success`). Importar bien: `backup_import_done` con los recuentos (`Success`) y, si `missingPhotos > 0`, después `backup_import_missing_photos` (`Warning`). Errores (`Error`): uno por `BackupError` (D30). Bloqueo al confirmar: `backup_import_blocked` (`Warning`). Los recuentos: `backup_counts` con cinco plurales (cafés, molinos, recetas, preparaciones y fotos), como el ejemplo del §2. `ui/settings/BackupTexts.kt` reúne `@StringRes fun backupErrorText(reason: BackupError, export: Boolean): Int` y `@Composable fun backupCountsText(counts: BackupCounts): String`. | Es el §2 (pasos 5 y 6 de cada flujo). Las calibraciones no salen en el texto, como en el ejemplo del spec. |
| D30 | Textos de error (concreta §3.7) | `NOT_A_BACKUP`, `NEWER_FORMAT`, `CORRUPT` y `BROKEN_REFERENCES` (el mismo) e `IO`, los del §3.7. `TOO_LARGE` al importar: "La copia es demasiado grande para importarla. Tus datos no se han modificado."; al exportar: "Tus datos superan el tamaño máximo de una copia.". Ver §3.1. | El §3.7 no da texto para `TOO_LARGE` (discrepancia 9). |

### 2.6 Copia automática de Android

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D31 | Reglas (concreta §3.6; P1) | `data_extraction_rules.xml` (API 31+): `<cloud-backup>` con cuatro reglas, `<include domain="database" path="history.db"/>`, `history.db-wal`, `history.db-shm` y `<include domain="sharedpref" path="settings.xml"/>`; `<device-transfer>` con esas cuatro y `<include domain="file" path="coffee_photos/"/>`. `backup_rules.xml` (API 26 a 30, sin distinción entre nube y dispositivo): `<full-backup-content>` con las cuatro de `cloud-backup`, sin las fotos. Sin `exclude`: con `include`, todo lo demás queda fuera (cachés, `backup_import/`, `settings_test*.xml` y las bases `*-perf*.db`). Manifiesto: `android:dataExtractionRules="@xml/data_extraction_rules"`, `android:fullBackupContent="@xml/backup_rules"` y `tools:targetApi="31"` en `<application>`; `allowBackup` sigue en `true`. Un comentario en el XML recuerda que `coffee_photos` es `COFFEE_PHOTOS_DIR`. | Es el §3.6 con la decisión de P1: las fotos superan la cuota de 25 MB de la nube y, si se supera, Android no copia nada. La exportación manual siempre lleva las fotos. `tools:targetApi` evita el aviso de lint por un atributo de la API 31 con `minSdk` 26. **Matiz, no aplicado:** desde la API 28, un `<include>` de `full-backup-content` admite `requireFlags="deviceToDeviceTransfer"`, que llevaría las fotos solo en la transferencia entre dispositivos también en las API 28 a 30 (a confirmar en la documentación de Auto Backup antes de usarlo). Se deja fuera por la decisión del usuario; añadirlo sería una línea en `backup_rules.xml`. |

### 2.7 Pruebas

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D32 | Datos de prueba (concreta §5) | JVM: `test/.../backup/BackupFixtures.kt` con `fullRows(): BackupRows` (al menos dos filas por tabla: una con todos los campos opcionales y otra con todos a `null` donde se pueda; curvas reales con `BrewCurveCodec` y `TargetCurveCodec`; un `preview` de 48 bytes; pasos de los cuatro `StageType`; un café con dos fotos y otro con solo `photo2`; dos molinos con unidades, rangos y anclas, y uno del catálogo; dos calibraciones) y `assertRowsEqual(expected, actual)`, que compara los `ByteArray` por contenido. Instrumentado: `androidTest/.../backup/BackupTestData.kt` con `seedEverything(db, photosDir, settings)` (los mismos casos, sembrados con los DAO, `TestBrews`, `addGrinder`, `addCalibration`, `aeroPressWithActions()` y `SyntheticLabel.writeJpeg`) y su propio `assertRowsEqual`. Los tests nunca tocan `history.db`, `coffee_photos` ni `settings.xml` reales: Room en memoria, directorios en `cacheDir` y preferencias `settings_test_backup`. | Las entidades con `ByteArray` comparan por referencia en `equals`. Los tests instrumentados no ven las fixtures JVM (como en D20 de `plan-paso-accion.md`). |
| D33 | Fallo en la transacción (concreta §5) | El test de atomicidad crea en la base en memoria del test `CREATE TRIGGER backup_test_fail BEFORE INSERT ON brew_targets BEGIN SELECT RAISE(ABORT, 'test'); END` (con `db.openHelper.writableDatabase.execSQL`), así que la última tabla falla dentro de la transacción. | No añade ganchos de prueba al código de producción y falla en el punto más tardío posible. |
| D34 | Dispositivos y ejecución (concreta §5) | Instrumentados en `emulator-5556` (`SG_API34`, `en-US`), siempre filtrados con `-Pandroid.testInstrumentationRunnerArguments.class=`, y JVM con `--tests`. Nunca la batería completa. `emulator-5554` no se toca. En el teléfono del usuario **no se ejecuta nada** (P2): el rendimiento se mide solo en `emulator-5556`, como referencia, y la única prueba en el teléfono es la manual del usuario (§7.6). | Es la norma del proyecto y la decisión del usuario. Además, `connectedDebugAndroidTest` desinstala la app al terminar y borraría sus datos. |

## 3. Fases

Orden: F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → F9 → F10. F3 y F4 no dependen de F2 y se pueden adelantar; F5
depende de F1 a F4; F6 de F5; F7 de F6; F8 es independiente de F5 a F7, pero va después por el orden pedido.

| Fase | Contenido | Tamaño | Estado |
|---|---|---|---|
| F0 | Línea base, dispositivos y tamaño de una foto real | S | Pendiente |
| F1 | Formato puro: modelos, DTO, mapeo e ida y vuelta (JVM) | M | Pendiente |
| F2 | Archivo ZIP: escritura, lectura, validación, límites y fotos (JVM) | L | Pendiente |
| F3 | Ajustes exportables y restaurables, y preparación en curso (JVM) | M | Pendiente |
| F4 | `BackupDao` y constante de versión de la base | S | Pendiente |
| F5 | Gestor: exportar, leer y descartar; conexión con la app | M | Pendiente |
| F6 | Aplicación atómica, marcador, recuperación e invalidación | L | Pendiente |
| F7 | Interfaz: tarjeta, diálogos, mensajes y navegación | L | Pendiente |
| F8 | Reglas de la copia automática de Android | S | Pendiente |
| F9 | Rendimiento del caso grande | M | Pendiente |
| F10 | Verificación final y cierre | M | Pendiente |

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app`, nunca con `./gradlew build`
ni con builds limpios.

- **Compilar:** `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`.
- **Tests JVM:** `./gradlew :app:testDebugUnitTest --tests "<clase>" [--tests "<clase>" ...]`, solo las clases de la
  fase.
- **Tests instrumentados:** `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<clases separadas por comas>`.
  Solo las clases del código nuevo o modificado por este spec. Nunca dos ejecuciones a la vez en el mismo emulador.
- **Sin ARTEMIS.** Tests JVM, tests instrumentados de Compose y adb directo (`uiautomator dump`, `input tap`,
  `screencap`).
- **Lint:** `./gradlew :app:lintDebug` en F0, F7, F8 y F10, sin errores ni avisos nuevos respecto a F0 y sin
  `MissingTranslation`, `ExtraTranslation` ni `UnusedResources` en las cadenas nuevas.
- **Cadenas:** en `res/values/strings.xml` (es) y `res/values-en/strings.xml` (en), junto a las `settings_recipes_*`.
- **Base de datos:** sin cambios de esquema. Si una fase necesitara uno, se para y se consulta: `HistoryDatabase` exige
  `Migration` con test.
- **Commits:** solo con permiso explícito del usuario. Si se permiten, uno por fase al cerrarla en verde, en
  Conventional Commits y en inglés, sin `Co-Authored-By` ni atribución, en `feat/data-backup` (nunca en `main` ni
  `develop`) y sin archivos de credenciales. El del plan: `docs(specs): add the SPEC-011 implementation plan`.

### F0. Línea base, dispositivos y tamaño de una foto real

**Objetivo:** conocer el punto de partida antes de tocar código.

**Pasos:**

1. `adb devices -l`: tiene que estar `emulator-5556`. Si no está, se pide al usuario que lo arranque o que indique otro.
   `emulator-5554` y el teléfono del usuario no se usan.
2. JVM, sin cambios:
   `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.settings.*" --tests "dev.josueamaya.pourline.session.ScaleUiStateTest" --tests "dev.josueamaya.pourline.history.HistoryConvertersTest" --tests "dev.josueamaya.pourline.coffee.photo.PhotoSizingTest"`.
3. `emulator-5556`: `ui.SettingsScreenTest`, `ui.SettingsNavigationTest`, `ui.TabsNavigationTest`,
   `ui.RecipeImportNavigationTest`, `settings.ExperienceSettingsTest`, `settings.FreeBrewDefaultsTest`,
   `settings.GrinderSettingsTest`, `settings.TourSettingsPersistenceTest`, `grinder.GrinderCatalogSeederTest`,
   `coffee.photo.CoffeePhotoStoreTest`, `history.HistoryMigrationTest` y `ManifestPermissionsTest` (con el prefijo
   `dev.josueamaya.pourline.`).
4. `./gradlew :app:lintDebug`: errores y avisos por id. Se anota si hay un aviso de `DataExtractionRules` o de
   `AllowBackup`, que F8 debería quitar.
5. **Informativo (D11):** `ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug`; `adb push` de una foto de
   `~/Downloads/coffee-bags` a `/sdcard/Pictures/`, un café nuevo con esa foto desde la galería y
   `adb -s emulator-5556 shell run-as dev.josueamaya.pourline ls -l files/coffee_photos`: tamaño de la principal y de la
   miniatura. Después, `adb -s emulator-5556 shell pm clear dev.josueamaya.pourline`.

**Salida:** las cifras en la cabecera ("Línea base"). Sin cambios de código ni commit.

### F1. Formato puro: modelos, DTO, mapeo e ida y vuelta

**Objetivo:** el formato del §3.1 y el §3.2 en Kotlin puro, con su test de ida y vuelta.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `backup/BackupModels.kt` (nuevo) | `BackupCounts(coffees, grinders, grindCalibrations, recipes, brews, photos)`, `BackupSummary(createdAt: OffsetDateTime, appVersionName: String, counts)`, `BackupError` (los seis valores del §3.3), `ExportResult`, `PrepareResult`, `ImportResult` (como el §3.3), `PreparedImport` (constructor `internal`: `summary` público; `rows`, `settings: SettingsSnapshot`, `staging: File`, `missingPhotos: Int` y un estado atómico `OPEN`/`APPLIED`/`DISCARDED` internos), `BackupRows` (las 11 listas de entidades), `BackupFingerprint` (`@Serializable`), `AppVersion(name: String, code: Long)` e `internal class BackupFailure(val reason: BackupError, cause: Throwable? = null) : Exception(cause)`. |
| `backup/BackupFormat.kt` (nuevo) | `APP_ID = "pourline"`, `FORMAT_VERSION = 1`, `MANIFEST_ENTRY`, `DATA_ENTRY`, `SETTINGS_ENTRY`, `PHOTOS_PREFIX = "photos/"`, `PHOTO_NAME` (D10), `BackupJson` (D2), `backupFileName(now: LocalDateTime)` (D7) y `formatCreatedAt(at: OffsetDateTime)` (D5). |
| `backup/BackupDtos.kt` (nuevo) | `ManifestDto`, `CountsDto`, `DataDto`, los 11 DTO de fila (D3), `SettingsDto` (D6) y `Base64ByteArraySerializer` (D4). |
| `backup/BackupMapping.kt` (nuevo) | `fun BackupRows.toDto(): DataDto`, `fun DataDto.toRows(): BackupRows` (lanza `BackupFailure(CORRUPT)` ante una fecha o un Base64 inválidos), `fun SettingsSnapshot.toDto(): SettingsDto`, `fun SettingsDto.toSnapshot(): SettingsSnapshot` y `fun BackupRows.counts(presentPhotos: Int): BackupCounts` (D5). |
| `settings/SettingsSnapshot.kt` (nuevo) | Solo la `data class SettingsSnapshot` (D21); la interfaz llega en F3. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../backup/BackupFixtures.kt` (nuevo) | `fullRows()` y `assertRowsEqual` (D32). |
| `test/.../backup/BackupMappingTest.kt` (nuevo) | `` `every table round trips through json without differences` `` (criterio 3 del §5: `fullRows()` → DTO → `BackupJson` → DTO → filas). `` `ids, foreign keys and null columns are kept` ``. `` `byte arrays are written as base64 and read back byte for byte` `` (también uno vacío). `` `enums are written by name and an unknown name falls back like the converters` `` (`"method":"SIPHON"` → `OTHER`, un tipo de paso `"KNEAD"` → `POUR`, un proceso desconocido → `null`). `` `dates are iso days and a malformed one is corrupt` ``. `` `a missing required field or a wrong type is corrupt` ``. `` `null fields are not written and absent nullable fields read as null` ``. `` `settings keep only the present keys with their stored types` `` (un `Float` 15.3 vuelve como 15.3f). |
| `test/.../backup/BackupFormatTest.kt` (nuevo) | `` `the file name uses the local date and time` `` (2026-11-19 18:45 → `pourline-backup-2026-11-19-1845.zip`; 2026-01-02 03:04 → `…-2026-01-02-0304.zip`). `` `created at is iso 8601 with offset and seconds` ``. `` `the manifest has the fields of the format` `` (las claves del §3.1, también las de `counts`). |

**Verificación:** compilar. `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.backup.*"`.
`grep -n "^import android\." app/src/main/kotlin/dev/josueamaya/pourline/backup/*.kt` no encuentra nada.

Commit sugerido: `feat(backup): add the backup file format with its DTOs and JSON mapping`.

### F2. Archivo ZIP: escritura, lectura, validación, límites y fotos

**Objetivo:** escribir y leer el ZIP del §3.1 con todas las reglas del §3.4, en la JVM.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `backup/BackupValidator.kt` (nuevo) | `fun validate(rows: BackupRows): BackupError?` (D13 y después D12) y `fun referencedPhotos(rows: BackupRows): Set<String>`. |
| `backup/BackupArchive.kt` (nuevo) | `BackupLimits` (D11); `class BackupWriter(limits)` con `fun write(out: OutputStream, manifest: ManifestDto, data: DataDto, settings: SettingsDto, photos: List<PhotoEntry>, onWritten: (Long) -> Unit)` y `data class PhotoEntry(val name: String, val file: File)` (D8); `class BackupReader(limits)` con `fun read(input: InputStream, staging: File, onRead: (Long) -> Unit): ReadArchive` y `data class ReadArchive(val manifest: ManifestDto, val data: DataDto, val settings: SettingsDto, val staged: Set<String>)` (D9 y D10). Envoltorios privados: uno que cuenta y limita los bytes de cada entrada y del total, y uno que no cierra el `ZipInputStream` al decodificar. |
| `backup/BackupPhotos.kt` (nuevo) | `fun interface ThumbnailMaker { fun make(photo: File, thumb: File): Boolean }`, `data class PhotoPlan(val rows: BackupRows, val missing: Int, val present: Set<String>, val regenerate: List<String>, val unreferenced: List<String>)` y `fun planPhotos(rows: BackupRows, staged: Set<String>): PhotoPlan` (D14). |
| `coffee/photo/PhotoSizing.kt` | `fun thumbNameOf(name: String): String` (D14). |
| `coffee/photo/CoffeePhotoStore.kt` | `thumbName` privada pasa a llamar a `thumbNameOf`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../backup/BackupArchiveTest.kt` (nuevo, con `TemporaryFolder`) | `` `a written backup has the manifest first and only the entries of the format` `` (orden de D8, sin directorios, fotos `STORED` y JSON `DEFLATED`). `` `a written backup reads back with the same data, settings and photos` ``. `` `a file that is not a zip is not a backup` `` (bytes al azar, archivo vacío y un PNG). `` `a zip without manifest or with another first entry is not a backup` ``. `` `a manifest of another app is not a backup` `` (`app` distinto, sin `app` y JSON inválido). `` `a newer format is rejected and an older or equal one is read` `` (`formatVersion` 2 con campos desconocidos → `NEWER_FORMAT`; 1 → se lee; 0 → `CORRUPT`). `` `corrupt json is corrupt` `` (`data.json` truncado, un `Int` como cadena en `settings.json`, sin `data.json`, sin `settings.json`). `` `an unexpected entry is corrupt` `` (`readme.txt`, `photos/sub/a.jpg`, `photos/a.png`, `data.json` repetido). `` `an unsafe path is corrupt and nothing is written outside staging` `` (`photos/../evil.jpg`, `../evil.jpg`, `/data/evil.jpg`, `photos\..\evil.jpg`). `` `a broken reference is rejected` `` (lector y validador: un `brew` con un `coffeeId` inexistente → `BROKEN_REFERENCES`). `` `entries over the limits are too large` `` (con `BackupLimits` pequeños: `data.json`, una foto, el total y el número de entradas). `` `the writer refuses to go over the limits` `` → `TOO_LARGE`. `` `a referenced photo missing from the backup is tolerated and counted` `` (`missing` 1, `photo1` a `null`, `photo2` intacta). `` `a missing thumbnail is regenerated from the main photo` `` (un `ThumbnailMaker` falso apunta la llamada y escribe el archivo). `` `photos no coffee references are dropped and not counted` ``. |
| `test/.../backup/BackupValidatorTest.kt` (nuevo) | `` `each foreign key must point to an existing row` `` (los 12 casos de D12 → `BROKEN_REFERENCES`). `` `null references are not checked` ``. `` `reference ids of brews and setting ids are not foreign keys` ``. `` `duplicate primary or unique keys are corrupt` `` (todos los de D13). `` `ids must be positive` ``. `` `photo names must be plain file names` ``. `` `a valid data set passes` ``. |
| `test/.../coffee/photo/PhotoSizingTest.kt` | `` `the thumbnail name keeps the uuid and adds the suffix` ``. |

**Verificación:** compilar.
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.backup.*" --tests "dev.josueamaya.pourline.coffee.photo.PhotoSizingTest"`.
El `grep` de `import android.` de F1 sigue vacío.

Commit sugerido: `feat(backup): write, read and validate backup archives`.

### F3. Ajustes exportables y restaurables, y preparación en curso

**Objetivo:** las clases de `settings` exportan y restauran sus claves del §3.2, y existe el predicado de D23.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `settings/SettingsSnapshot.kt` | `interface BackupableSettings` (D21). |
| `settings/ThemeSettings.kt`, `settings/ExperienceSettings.kt`, `settings/BrewSettings.kt`, `settings/TargetSettings.kt`, `settings/GrinderSettings.kt` | `internal constructor(prefs: SharedPreferences)` más el constructor público de siempre; `BackupableSettings` con sus claves. |
| `settings/ConnectionSettings.kt` | `BackupableSettings` con `use_scale`, `last_scale_address` y `last_scale_name`; recalcula `lastScale`, `useScale` y `scaleModeChosen`. |
| `session/ScaleUiState.kt` | `blocksDataImport` (D23). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../settings/SettingsSnapshotTest.kt` (nuevo, con `FakeSharedPreferences`) | `` `each class exports only its present keys with their stored values` `` (también uno fuera de rango, como `auto_finish_seconds` 7). `` `restoring writes the present keys, removes the absent ones and updates the flows` `` (las seis clases). `` `restoring an out of range or unknown value falls back like a cold read without throwing` `` (`auto_finish_seconds` 7 → 60, `theme_mode` `"PURPLE"` → `SYSTEM`, `default_ratio` 99 → 16,0). `` `restoring the scale keys recomputes the derived flows` `` (sin `use_scale` y con báscula recordada: `useScale` verdadero y `scaleModeChosen` falso). `` `restoring never touches tour_seen` ``. `` `each class restores in a single write` `` (`writes` sube en 1). |
| `test/.../session/ScaleUiStateTest.kt` | `` `data import is blocked during a session and while the last brew is saving` `` (sin `plan` → falso; con `plan`, guiada, sin báscula o en cuenta atrás → verdadero; `Finished` con `plan` → verdadero; sin `plan` y `Saving` → verdadero; sin `plan` y `Saved` → falso). |

**Verificación:** compilar.
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.settings.*" --tests "dev.josueamaya.pourline.session.ScaleUiStateTest"`.
`emulator-5556`: `settings.ExperienceSettingsTest`, `settings.FreeBrewDefaultsTest`, `settings.GrinderSettingsTest`,
`settings.TourSettingsPersistenceTest` y `ui.SettingsScreenTest`, sin cambios de expectativa (los constructores
públicos no cambian).

Commit sugerido: `feat(settings): export and restore the settings of a backup`.

### F4. `BackupDao` y constante de versión de la base

**Objetivo:** leer y reemplazar las 11 tablas en una transacción, sin tocar el esquema.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `backup/BackupDao.kt` (nuevo) | D20, con `data class FingerprintRow` (11 recuentos y 5 ids máximos) y `fun BackupRows.fingerprint(): BackupFingerprint` / `fun FingerprintRow.fingerprint(photoNames: List<String>): BackupFingerprint`. |
| `history/HistoryDatabase.kt` | `abstract fun backupDao(): BackupDao`; `const val VERSION = 7` y `version = VERSION`; KDoc: una migración nueva obliga a revisar los DTO de `backup` (§3.8). |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../backup/BackupTestData.kt` (nuevo) | `seedEverything` y `assertRowsEqual` (D32). |
| `androidTest/.../backup/BackupDaoTest.kt` (nuevo, `emulator-5556`) | `readAllReturnsEveryRowOrderedByKey`. `replaceAllLeavesExactlyTheGivenRowsWithTheirIds` (sobre una base con otros datos y las predefinidas). `replaceAllNotifiesTheObservers` (`RecipeRepository.list()`, `CoffeeRepository.pickable()` y `BrewRepository.count()` emiten lo nuevo). `aFailingInsertRollsEverythingBack` (disparador de D33). `theFingerprintFollowsCountsMaxIdsAndPhotoNames`. |

**Verificación:** compilar. `git diff develop -- app/schemas` vacío.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.backup.BackupDaoTest,dev.josueamaya.pourline.history.HistoryMigrationTest`.

Commit sugerido: `feat(backup): add a DAO that reads and replaces every table`.

### F5. Gestor: exportar, leer y descartar; conexión con la app

**Objetivo:** `export`, `prepareImport` y `discard` contra Room, las fotos y un documento, sin modificar nada al leer.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `backup/BackupStreams.kt` (nuevo) | `BackupStreams` y `ContentResolverStreams` (D24). |
| `backup/BackupOperations.kt` (nuevo) | `interface BackupOperations` con `export`, `prepareImport` y `discard` (las firmas del §3.3). `apply`, `applying` e `imported` llegan en F6. |
| `backup/BackupManager.kt` (nuevo) | `class BackupManager(db: HistoryDatabase, photosDir: File, workDir: File, settings: List<BackupableSettings>, seeder: GrinderCatalogSeeder, streams: BackupStreams, thumbnails: ThumbnailMaker, appVersion: AppVersion, scope: CoroutineScope, invalidateCaches: () -> Unit, clock: Clock = Clock.systemDefaultZone(), limits: BackupLimits = BackupLimits(), io: CoroutineDispatcher = Dispatchers.IO) : BackupOperations`. `export`: `readAll()`, los ajustes con `exportInto`, las fotos referenciadas que existen, el manifiesto y `BackupWriter`; ante cualquier fallo, `deletePartial`. `prepareImport`: vacía `staging/`, `BackupReader`, `toRows`, `validate`, `planPhotos`, regenera miniaturas, borra lo no referenciado y devuelve `Ready` con el resumen recalculado (D5). `discard`: marca la copia y borra su staging en `scope` (IO, bajo el `Mutex`). |
| `coffee/photo/CoffeePhotoStore.kt` | `fun regenerateThumbnail(photo: File, thumb: File): Boolean` y la escritura de la miniatura compartida con `import` (D14). |
| `PourlineApp.kt` | `COFFEE_PHOTOS_DIR` (D16), `val backupManager by lazy { ... }` con las seis clases de ajustes, `grinderCatalogSeeder`, `ContentResolverStreams(contentResolver)`, `ThumbnailMaker(::regenerateThumbnail)`, la versión de `PackageManager` (`@Suppress("DEPRECATION")` en `getPackageInfo`, `PackageInfoCompat.getLongVersionCode`) y `applicationScope`; `backup = backupManager` en `dependencies`. |
| `AppDependencies.kt` | `val backup: BackupOperations`. |
| `androidTest/.../ui/TestAppDependencies.kt` | Parámetro `backup: BackupOperations? = null`; por defecto un `BackupManager` sobre `cacheDir/test_app_photos` y `cacheDir/test_app_backup` (mismo volumen), sus ajustes, un `GrinderCatalogSeeder` con `TestGrinderCatalog.JSON` y `ContentResolverStreams`. `close()` borra `test_app_backup`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../backup/BackupManagerTest.kt` (nuevo, `emulator-5556`) | `exportingWritesTheManifestDataSettingsAndEveryPhoto` (entradas y orden; `counts.grinders` solo los propios; `counts.photos` las principales; en `settings.json` están las claves presentes y no `tour_seen`). `exportingAnEmptyAppGivesAValidBackupWithZeroCounts` (y `prepareImport` la acepta). `aFailedExportDeletesTheHalfWrittenFile` (`BackupStreams` falso que falla tras 64 KiB → `Failed(IO)` y el archivo ya no existe). `anExportOverTheLimitIsTooLargeAndDeletesTheFile`. `preparingAValidBackupChangesNothingAndSummarizesIt` (base, `coffee_photos` y preferencias iguales; resumen con fecha, versión y recuentos). `preparingAnInvalidBackupLeavesNoStaging`. `aMissingThumbnailIsRegeneratedWithARealBitmap` (256 × 256). `discardingRemovesTheStaging`. |
| `androidTest/.../coffee/photo/CoffeePhotoStoreTest.kt` | Los existentes sin cambios y `regenerateThumbnailWritesA256SquareJpeg`. |

**Verificación:** compilar (los tres conjuntos, por `TestAppDependencies`).
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.backup.BackupManagerTest,dev.josueamaya.pourline.coffee.photo.CoffeePhotoStoreTest,dev.josueamaya.pourline.ui.TabsNavigationTest,dev.josueamaya.pourline.ui.SettingsScreenTest`.

Commit sugerido: `feat(backup): export a backup and prepare an import without changing data`.

### F6. Aplicación atómica, marcador, recuperación e invalidación

**Objetivo:** `apply` según D17, la recuperación de D19 y la invalidación de D22 (sin la parte de navegación, que va en
F7).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `backup/ImportMarker.kt` (nuevo) | `@Serializable internal data class ImportMarker(val state: State, val settings: SettingsDto, val fingerprint: BackupFingerprint)` con `enum class State { APPLYING, DB_DONE }`, y `write` (temporal, `fd.sync()` y `renameTo`), `read` (`null` si no existe o no se puede leer) y `delete`. |
| `backup/PhotoSwap.kt` (nuevo) | `internal fun swapPhotos(photos: File, staging: File, old: File)` (D19). |
| `backup/BackupOperations.kt` | `apply`, `applying` e `imported` (D15). |
| `backup/BackupManager.kt` | `apply`, `finishImport` y `recoverInterruptedImport()` (D17 a D19). |
| `session/GrindSelection.kt` | `fun clearChoice()`. |
| `coffee/photo/CoffeeImages.kt` | `fun clear()`. |
| `PourlineApp.kt` | `invalidateCaches` (D22). `onCreate`: una corrutina de IO que llama a `backupManager.recoverInterruptedImport()` y después lanza el barrido y la sincronización del catálogo de siempre (D19). |
| `androidTest/.../ui/TestAppDependencies.kt` | `invalidateCaches` con los mismos objetos del test. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../backup/PhotoSwapTest.kt` (nuevo) | `` `each intermediate state ends with the staging as the photos dir` `` (los cuatro estados de D19). `` `without a previous photos dir the staging just moves` ``. `` `without staging the photos dir ends empty` ``. `` `running it twice changes nothing` ``. |
| `test/.../backup/ImportMarkerTest.kt` (nuevo) | `` `the marker round trips` ``. `` `a leftover temporary file is ignored` ``. `` `an unreadable marker reads as null` ``. |
| `androidTest/.../backup/BackupManagerTest.kt` | `exportWipeAndImportRestoreTheDataPhotosAndSettingsWithTheirIds` (criterio 5: sembrar, exportar, borrar todo y cambiar cada ajuste y `tour_seen`, importar; `readAll` igual al original, `coffee_photos` con los mismos nombres y bytes, ajustes iguales salvo `onboarding_done = true`, y `tour_seen` como estaba justo antes de importar). `importingReplacesTheCurrentDataAndRemovesTheAbsentKeys`. `theSettingsFlowsEmitTheImportedValuesWithoutRecreatingThem` (criterio 7). `importRunsTheCatalogSyncWhenTheBackupCatalogIsOlder` (copia con `grinder_catalog_version` 1 y catálogo de prueba en 2: tras importar, versión 2 y los predefinidos al día, con `owned` intacto). `importClearsPendingUndoAndEmitsImportedOnce`. `aMissingPhotoIsCountedAndTheCoffeeLosesOnlyThatPhoto`. `applyingTwiceTheSamePreparedImportFails` (`Failed(IO)` sin tocar nada). |
| `androidTest/.../backup/BackupAtomicityTest.kt` (nuevo) | `aFailureInsideTheTransactionLeavesDataPhotosAndSettingsAsBefore` (criterio 6, disparador de D33: `Failed(IO)`; `readAll`, `coffee_photos` y ajustes iguales; sin staging ni marcador; `applying` falso). |
| `androidTest/.../backup/BackupRecoveryTest.kt` (nuevo) | `withoutMarkerALeftoverStagingIsRemoved`. `anApplyingMarkerWhoseDataDidNotCommitIsRolledBack`. `anApplyingMarkerWhoseDataCommittedIsFinished` (fotos cambiadas, ajustes restaurados, catálogo sincronizado y sin marcador). `aDbDoneMarkerFinishesFromEachIntermediatePhotoState`. `withoutWorkDirNothingIsTouched`. |

**Verificación:** compilar.
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.backup.*"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.backup.BackupManagerTest,dev.josueamaya.pourline.backup.BackupAtomicityTest,dev.josueamaya.pourline.backup.BackupRecoveryTest,dev.josueamaya.pourline.backup.BackupDaoTest,dev.josueamaya.pourline.ui.TabsNavigationTest`.

Commit sugerido: `feat(backup): apply an import atomically and recover an interrupted one`.

### F7. Interfaz: tarjeta, diálogos, mensajes y navegación

**Objetivo:** la tarjeta "Copia de seguridad" en Ajustes con los dos flujos del §2, y la navegación de D22 y D28.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/settings/BackupViewModel.kt` (nuevo) | D26. |
| `ui/settings/BackupCard.kt` (nuevo) | D27 y D28 (tarjeta, selectores, confirmación, diálogo de progreso y recogida de mensajes). |
| `ui/settings/BackupTexts.kt` (nuevo) | D29 y D30. |
| `ui/settings/SettingsScreen.kt` | `backup: BackupViewModel? = null` y `BackupCard(it, snackbar)` (D27). |
| `ui/components/AntModal.kt` | `testTag: String = "ant_modal"` (D28). |
| `Navigation.kt` | Entrada `Settings`: `backup = viewModel { BackupViewModel(deps.backup, deps.scaleSession.ui.map { it.blocksDataImport }.distinctUntilChanged(), { deps.scaleSession.ui.value.blocksDataImport }) }`. `TabsNavigation`: `applying` en la apertura de recetas importadas (D28) y el colector de `imported` (D22). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | Las cadenas del §3.1. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../ui/settings/BackupTextsTest.kt` (nuevo) | `` `each backup error has its message` `` (`CORRUPT` y `BROKEN_REFERENCES` comparten; `TOO_LARGE` cambia entre exportar e importar; ninguno es 0). |
| `androidTest/.../ui/settings/FakeBackupOperations.kt` (nuevo) | Resultados programables, compuertas con `CompletableDeferred` para ver el progreso, llamadas apuntadas y `applying`/`imported` controlables. Construye `PreparedImport` con el constructor `internal` (los tests compilan como módulo amigo del principal). |
| `androidTest/.../ui/settings/BackupCardTest.kt` (nuevo, `emulator-5556`) | `theCardIsShownInBasicAndExpertWithBothButtons` (criterio 1). `exportOffersTheDatedFileNameAndReportsTheCounts` (un `ActivityResultRegistry` falso apunta la entrada de `CreateDocument`: `pourline-backup-2026-11-19-1845.zip` con el reloj fijo; `backup_progress` mientras la compuerta espera; el mensaje con los recuentos). `cancellingThePickerDoesNothing`. `importShowsTheDateVersionAndCountsBeforeReplacing` (criterio 10: `backup_confirm` con la fecha formateada, "1.0" y los recuentos; la entrada de `OpenDocument` es `["application/zip", "application/x-zip-compressed", "application/octet-stream"]`). `cancellingTheConfirmationDiscardsTheBackup`. `replacingShowsABlockingProgressAndThenTheResult` (`backup_apply_progress` mientras espera; `Espresso.pressBack()` no lo cierra; después el mensaje y el aviso de fotos perdidas). `eachErrorShowsItsMessage` (todos los `BackupError`, al exportar y al importar). `importIsDisabledWithItsExplanationDuringABrew` (criterio 9: con `sessionBusy`, "Importar datos" deshabilitado y `settings_backup_import_blocked`; "Exportar datos" habilitado). `aBrewStartedBeforeReplacingCancelsTheImport`. |
| `androidTest/.../ui/BackupNavigationTest.kt` (nuevo, `TestApp` con `FakeBackupOperations`) | `afterImportingTheOtherTabsGoBackToTheirRoots` (un café abierto en Cafés y un editor en Recetas; Preparar → Ajustes; `imported` emite; Cafés y Recetas muestran su lista y Ajustes sigue arriba en Preparar). `aRecipeLinkWaitsWhileImporting` (con `applying` verdadero, `recipeImports.submit` no navega; al pasar a falso se abre la vista previa). |
| `androidTest/.../ui/SettingsScreenTest.kt` | Sin cambios: sin `backup`, no hay tarjeta. |

**Verificación:** compilar.
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.ui.settings.BackupTextsTest"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.settings.BackupCardTest,dev.josueamaya.pourline.ui.BackupNavigationTest,dev.josueamaya.pourline.ui.SettingsScreenTest,dev.josueamaya.pourline.ui.SettingsNavigationTest,dev.josueamaya.pourline.ui.TabsNavigationTest,dev.josueamaya.pourline.ui.RecipeImportNavigationTest`.
`./gradlew :app:lintDebug`.

Commit sugerido: `feat(settings): add the backup card to export and import every data`.

### F8. Reglas de la copia automática de Android

**Objetivo:** el §3.6, con las fotos solo en `device-transfer` (P1).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `res/xml/data_extraction_rules.xml` | D31: `cloud-backup` (base y ajustes) y `device-transfer` (base, ajustes y fotos). Se quitan los comentarios de la plantilla. |
| `res/xml/backup_rules.xml` | D31: `full-backup-content` con la base y los ajustes, sin las fotos. |
| `AndroidManifest.xml` | `android:dataExtractionRules`, `android:fullBackupContent` y `tools:targetApi="31"` en `<application>`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../backup/BackupRulesTest.kt` (nuevo, `emulator-5556`) | `cloudAndLegacyRulesIncludeOnlyTheDatabaseAndTheSettings` (con `resources.getXml`: en `cloud-backup` y en `full-backup-content`, el conjunto de `(domain, path)` son las cuatro reglas de la base y los ajustes, sin `coffee_photos/`, y no hay `exclude`). `deviceTransferAlsoIncludesThePhotos` (en `device-transfer`, esas cuatro y `coffee_photos/`). `theAppAllowsBackup` (`ApplicationInfo.FLAG_ALLOW_BACKUP`). |

**Verificación:** `./gradlew :app:processDebugMainManifest` y `grep -rE "dataExtractionRules|fullBackupContent"` sobre
el `AndroidManifest.xml` fusionado de `app/build/intermediates` (se localiza con `find app/build/intermediates -path "*debug*" -name AndroidManifest.xml`).
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.backup.BackupRulesTest,dev.josueamaya.pourline.ManifestPermissionsTest`.
`./gradlew :app:lintDebug` (sin avisos nuevos de `FullBackupContent`, `DataExtractionRules` ni `UnusedAttribute`).
El recorrido de `bmgr` del §7.4 en `emulator-5556`.

Commit sugerido: `feat(backup): set explicit rules for the Android auto backup`.

### F9. Rendimiento del caso grande

**Objetivo:** el criterio 8 del §5 (medido en `emulator-5556`, como referencia: P2) y la comprobación de la estimación
de D11.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `androidTest/.../backup/BackupPerformanceTest.kt` (nuevo) | Origen en `backup-perf-src.db` (`HistoryDatabase.create(context, "backup-perf-src.db")`): 1000 preparaciones `TestBrews.finished(totalMs = 240_000, seed = i.toLong())`, todas con curva objetivo como `HistoryPerformanceTest` y vinculadas a cafés; 100 cafés con una foto (un JPEG de 1600 × 1200 con ruido, para que no se comprima de más, copiado con 100 nombres UUID, cada uno con su miniatura); 20 recetas (las dos predefinidas y 18 copias de `aeroPressWithActions()`). Destino en `backup-perf-dst.db`, con fotos y preferencias propias. Mide `export` (a `Uri.fromFile` en `cacheDir`), `prepareImport` y `apply`. Asserts: exportar < 15 000 ms; leer y aplicar < 15 000 ms. Registra con la etiqueta `BackupPerf` los tiempos, el tamaño del ZIP, el de `data.json`, el total descomprimido y la memoria Java usada tras importar. `@After` borra las dos bases, el ZIP y los directorios. |

**Pasos:**

1. `emulator-5556`, como referencia:
   `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.backup.BackupPerformanceTest`
   y `adb -s emulator-5556 logcat -d -s BackupPerf`.
2. Comprobar D11: `data.json` del caso grande < 16 MiB. Si no, parar y consultar.

Nada se ejecuta en el teléfono del usuario (P2).

**Salida:** las cifras del emulador en la cabecera.

Commit sugerido: `test(backup): measure the export and import of the large data set`.

### F10. Verificación final y cierre

**Objetivo:** el §7 completo.

**Archivos:** este plan (cabecera, columna Estado y un "Resultado de F10"). El spec no se toca más allá del campo
Estado; los cambios que conviene llevarle están en el §8.

Commit sugerido: `docs(specs): record the SPEC-011 verification`.

### 3.1 Cadenas nuevas (`values` / `values-en`)

| Clave | Español | Inglés |
|---|---|---|
| `backup_title` | Copia de seguridad | Backup |
| `backup_hint` | Guarda en un archivo tus cafés con sus fotos, molinos, calibraciones, recetas, historial y ajustes, o recupéralos desde uno. Importar reemplaza todos los datos actuales. | Save your coffees with their photos, grinders, calibrations, recipes, history and settings to a file, or restore them from one. Importing replaces all your current data. |
| `backup_export` | Exportar datos | Export data |
| `backup_import` | Importar datos | Import data |
| `backup_import_blocked` | Termina la preparación en curso para importar | Finish the brew in progress to import |
| `backup_exporting` | Exportando… | Exporting… |
| `backup_reading` | Leyendo la copia… | Reading the backup… |
| `backup_applying` | Importando datos. No cierres la app. | Importing data. Don\'t close the app. |
| `backup_confirm_title` | ¿Reemplazar tus datos? | Replace your data? |
| `backup_confirm_text` | Copia del %1$s, creada con Pourline %2$s: %3$s.\n\nTodos tus datos actuales se reemplazarán por los de la copia. | Backup from %1$s, created with Pourline %2$s: %3$s.\n\nAll your current data will be replaced with the backup\'s. |
| `backup_confirm_replace` | Reemplazar | Replace |
| `backup_confirm_cancel` | Cancelar | Cancel |
| `backup_counts` | %1$s, %2$s, %3$s, %4$s, %5$s | %1$s, %2$s, %3$s, %4$s, %5$s |
| `backup_count_coffees` (plurals) | `%1$d café` / `%1$d cafés` | `%1$d coffee` / `%1$d coffees` |
| `backup_count_grinders` (plurals) | `%1$d molino` / `%1$d molinos` | `%1$d grinder` / `%1$d grinders` |
| `backup_count_recipes` (plurals) | `%1$d receta` / `%1$d recetas` | `%1$d recipe` / `%1$d recipes` |
| `backup_count_brews` (plurals) | `%1$d preparación` / `%1$d preparaciones` | `%1$d brew` / `%1$d brews` |
| `backup_count_photos` (plurals) | `%1$d foto` / `%1$d fotos` | `%1$d photo` / `%1$d photos` |
| `backup_export_done` | Copia guardada: %1$s | Backup saved: %1$s |
| `backup_import_done` | Datos importados: %1$s | Data imported: %1$s |
| `backup_import_missing_photos` (plurals) | `%1$d foto no se pudo restaurar` / `%1$d fotos no se pudieron restaurar` | `%1$d photo could not be restored` / `%1$d photos could not be restored` |
| `backup_error_not_a_backup` | Este archivo no es una copia de Pourline. | This file is not a Pourline backup. |
| `backup_error_newer_format` | Esta copia se creó con una versión más nueva de Pourline. Actualiza la app para importarla. | This backup was created with a newer version of Pourline. Update the app to import it. |
| `backup_error_corrupt` | La copia está dañada. Tus datos no se han modificado. | The backup is damaged. Your data has not been changed. |
| `backup_error_too_large` | La copia es demasiado grande para importarla. Tus datos no se han modificado. | The backup is too large to import. Your data has not been changed. |
| `backup_error_export_too_large` | Tus datos superan el tamaño máximo de una copia. | Your data exceeds the maximum size of a backup. |
| `backup_error_io` | No se pudo leer o escribir el archivo. | The file could not be read or written. |

Los plurales llevan `one` y `other`, como `recipe_stage_count`. En `values-en` el apóstrofo va escapado (`\'`).

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| 1. Tarjeta "Copia de seguridad" en Básico y Experto con los dos botones | F7 | `BackupCardTest.theCardIsShownInBasicAndExpertWithBothButtons` |
| 2. Exportar abre el selector con `pourline-backup-AAAA-MM-DD-HHmm.zip` y escribe un ZIP conforme a §3.1 y §3.2 | F1, F2, F5, F7, F10 | `BackupFormatTest` (nombre), `BackupArchiveTest` (entradas y orden), `BackupManagerTest.exportingWritesTheManifestDataSettingsAndEveryPhoto`, `BackupCardTest.exportOffersTheDatedFileNameAndReportsTheCounts`; `unzip -l` en el E2E (§7.5) |
| 3. Test unitario de ida y vuelta con filas en las 11 tablas | F1 | `BackupMappingTest.every table round trips through json without differences` con `fullRows()` (curvas, objetivos, los cuatro tipos de paso, café con dos fotos, molinos con unidades, rangos y anclas, calibraciones) |
| 4. Tests unitarios de validación (no ZIP, sin manifiesto, `app` distinto, `formatVersion` mayor, JSON corrupto, entrada inesperada, `..`, referencia rota) y foto faltante tolerada | F2 | `BackupArchiveTest` (un caso por error) y `BackupValidatorTest`; `a referenced photo missing from the backup is tolerated and counted` |
| 5. Instrumentado: exportar, borrar e importar deja base, fotos y ajustes idénticos (IDs incluidos) y `tour_seen` sin cambiar | F5, F6 | `BackupManagerTest.exportWipeAndImportRestoreTheDataPhotosAndSettingsWithTheirIds` |
| 6. Instrumentado de atomicidad | F6 | `BackupAtomicityTest` (disparador de D33) y `BackupDaoTest.aFailingInsertRollsEverythingBack` |
| 7. Instrumentado: los `StateFlow` de `settings` emiten lo importado sin recrearlas | F3, F6 | `SettingsSnapshotTest` (JVM) y `BackupManagerTest.theSettingsFlowsEmitTheImportedValuesWithoutRecreatingThem` |
| 8. Rendimiento: el caso grande exporta e importa en menos de 15 s cada uno en el emulador, como referencia | F9 | `BackupPerformanceTest` en `emulator-5556` (§7.3) |
| 9. "Importar datos" deshabilitado con su explicación durante una preparación | F3, F7 | `ScaleUiStateTest` (`blocksDataImport`) y `BackupCardTest.importIsDisabledWithItsExplanationDuringABrew` |
| 10. El diálogo muestra la fecha, la versión y los recuentos | F7 | `BackupCardTest.importShowsTheDateVersionAndCountsBeforeReplacing` |
| 11. El manifiesto referencia las dos reglas con el contenido del §3.6 (fotos solo en `device-transfer`); `bmgr` | F8 | `BackupRulesTest`, `grep` del manifiesto fusionado y §7.4 (copia en la nube sin fotos y transferencia con fotos) |
| 12. Verificación manual en el móvil del usuario | F10 (usuario) | §7.6 |
| 13. Textos en español e inglés; sin avisos nuevos de lint (no hay detekt) | F7, F8, F10 | `:app:lintDebug` frente a F0, sin `MissingTranslation` ni `UnusedResources` |

Puntos del §3 y del §4 sin criterio propio, también cubiertos:

| Punto | Fase | Prueba |
|---|---|---|
| §3.4 `TOO_LARGE` (bomba ZIP) y límites al exportar | F2, F5 | `BackupArchiveTest.entries over the limits are too large`, `the writer refuses to go over the limits`; `BackupManagerTest.anExportOverTheLimitIsTooLargeAndDeletesTheFile` |
| §3.4 miniatura regenerada; fotos no referenciadas ignoradas | F2, F5 | `BackupArchiveTest` (falso) y `BackupManagerTest.aMissingThumbnailIsRegeneratedWithARealBitmap` |
| §3.5 la app muere durante la importación | F6 | `BackupRecoveryTest` y `PhotoSwapTest` |
| §3.5 `sync()` tras importar | F6 | `BackupManagerTest.importRunsTheCatalogSyncWhenTheBackupCatalogIsOlder` |
| §3.5 la interfaz refleja lo importado; cachés invalidadas | F4, F6, F7 | `BackupDaoTest.replaceAllNotifiesTheObservers`, `BackupManagerTest.importClearsPendingUndoAndEmitsImportedOnce`, `BackupNavigationTest.afterImportingTheOtherTabsGoBackToTheirRoots` |
| §3.5 durante `apply` no se sale de Ajustes ni se empieza una preparación | F7 | `BackupCardTest.replacingShowsABlockingProgressAndThenTheResult`, `BackupNavigationTest.aRecipeLinkWaitsWhileImporting` |
| §3.2 `onboarding_done` a `true`; clave ausente borrada | F3, F6 | `SettingsSnapshotTest`; `BackupManagerTest.importingReplacesTheCurrentDataAndRemovesTheAbsentKeys` |
| §2 y §4 documento a medio escribir borrado | F5 | `BackupManagerTest.aFailedExportDeletesTheHalfWrittenFile` |
| §4 app sin datos | F5 | `BackupManagerTest.exportingAnEmptyAppGivesAValidBackupWithZeroCounts` |
| §4 cancelar el selector o el diálogo | F5, F7 | `BackupManagerTest.discardingRemovesTheStaging`; `BackupCardTest.cancellingThePickerDoesNothing`, `cancellingTheConfirmationDiscardsTheBackup` |
| §3.6 sin cambio de esquema | F4 | `git diff develop -- app/schemas` vacío; `HistoryMigrationTest` |
| Ejecutar solo los tests del spec | Todas | Cada fase lista sus clases (D34) |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | Con las fotos en `cloud-backup`, la copia automática superaría la cuota de 25 MB y Android no guardaría nada en la nube, ni la base ni los ajustes. | Resuelto con P1: las fotos van solo en `device-transfer`, que no tiene esa cuota (D31). La exportación manual siempre las lleva. |
| R2 | Memoria al importar o exportar mucho historial. | Lectura y escritura por flujo, Base64 directo a `ByteArray` (D4), límite de 64 MiB en `data.json` (D11), `OutOfMemoryError` → `TOO_LARGE`. F9 anota la memoria usada. |
| R3 | Ejecutar tests instrumentados en el teléfono del usuario con `connectedDebugAndroidTest` desinstala la app y borra sus datos reales. | En el teléfono no se ejecuta nada (P2, D34). El rendimiento se mide en `emulator-5556` y la única prueba en el teléfono es la manual del usuario (§7.6). |
| R4 | Un editor abierto en otra pestaña guarda un borrador sobre una fila importada y borra sus fotos. | Vaciar las otras pilas al importar e invalidar `pendingUndo` (D22, P6). |
| R5 | El proceso muere durante `apply`. | Marcador, huella y recuperación idempotente antes del barrido (D17 a D19); `BackupRecoveryTest`. |
| R6 | `sweepOrphans` o la sincronización del catálogo corren a la vez que la recuperación. | Una sola corrutina al arrancar: primero la recuperación (D19). |
| R7 | Rarezas de los proveedores: `"wt"` no admitido, Drive sin conexión, tipo MIME no informado o distinto. | `"w"` de reserva (D24), `IO` con su mensaje, y `application/x-zip-compressed` y `application/octet-stream` en el selector (D27). |
| R8 | R8 quita los serializadores de los DTO en la release. | kotlinx-serialization trae sus reglas (ya funciona con `GrinderCatalogFile`); prueba de humo con `:app:assembleRelease` en el §7.5. |
| R9 | Android 14 lanza `ZipException` por `..` antes de la comprobación propia. | Se mapea a `CORRUPT` tras el manifiesto (D9); los tests JVM cubren la comprobación propia y los instrumentados corren en API 34. |
| R10 | Un usuario con muchos años de historial llega al límite al exportar. | `TOO_LARGE` con un texto claro al exportar, nunca una copia que no se pueda importar (D11). El límite no es parte del formato y se puede subir en otra versión. |
| R11 | Una copia con un valor de ajuste raro tumba una clase de `settings`. | `restoreFrom` escribe sin `require` y lee con la reserva de siempre (D21). |
| R12 | Una copia con un catálogo de molinos antiguo cambia los predefinidos al importar. | Es lo que pide el §3.5 (`sync()`); `owned` y los ids se conservan. |
| R13 | Al reinstalar para la prueba manual, Android restaura la copia automática (la base y los ajustes, sin fotos) y la importación se nota menos. | En el §7.6, borrar un café antes de importar para comprobar que vuelve, y comprobar que las fotos vuelven con la importación. |
| R14 | La dosis escrita en Preparar para una receta (`plannedDose` en `ScaleSessionManager`) queda atada a un id que tras importar es otra receta. | Aceptado: la dosis sigue visible en Preparar y se cambia a mano. |
| R15 | `last_scale_address` no sirve en otro móvil (§6 del spec). | Se comprueba en la prueba manual; si falla, cambio del spec fuera de este plan. |
| R16 | Fallos intermitentes del emulador en tests de interfaz largos. | Repetir la clase sola antes de darla por rota y comparar con F0. |
| R17 | `internal` no fuera visible desde `androidTest`. | Plan B: `FakeBackupOperations` obtiene un `PreparedImport` real de un `BackupManager` de prueba con un ZIP pequeño. |
| R18 | Tras una restauración desde la nube (sin fotos, P1), los cafés conservan `photo1` y `photo2` con nombres de archivos que no existen. | Aceptado: `CoffeeImages` devuelve `null` si el archivo no existe y la interfaz dibuja el marcador sin foto. Una exportación desde ese estado solo incluye las fotos que existen, y al importarla las referencias sin archivo pasan a `null` y cuentan en `missingPhotos` (D8, D14). El §7.4 lo comprueba. |

## 6. Preguntas para el usuario

Todas resueltas por el usuario el 2026-11-19. Las decisiones ya están en el spec y en este plan.

1. **P1. Fotos en la copia automática en la nube (§3.6, discrepancia 5). Resuelta.** En `cloud-backup` van solo
   `history.db*` y `settings.xml`; `coffee_photos/` va solo en `device-transfer` (API 31+). En `backup_rules.xml`
   (API 26 a 30) tampoco van las fotos. La exportación manual siempre las lleva. Afecta a D31, F8, el criterio 11 y
   R18. El matiz de `requireFlags="deviceToDeviceTransfer"` para las API 28 a 30 queda anotado en D31, sin aplicar.
2. **P2. Rendimiento en el móvil del usuario (§5, discrepancia 6). Resuelta.** El rendimiento se mide solo en
   `emulator-5556` y se acepta como referencia; en el teléfono del usuario no se ejecuta nada. El criterio 8 del spec
   dice ahora "en el emulador". La prueba manual no cambia: el usuario exporta, desinstala, reinstala e importa en su
   móvil (§7.6). Afecta a D34, F9, §7.3 y R3.
3. **P3. Tipos del selector al importar (§2, discrepancia 8). Resuelta: se acepta.** `OpenDocument` admite
   `application/zip`, `application/x-zip-compressed` y `application/octet-stream` (D27).
4. **P4. Claves repetidas (§3.4, discrepancia 7). Resuelta: se acepta.** Una clave primaria o única repetida en la
   copia es `CORRUPT` y se detecta antes de tocar nada (D13).
5. **P5. Recuentos (§3.1, discrepancias 1 y 2). Resuelta: se acepta.** "Molinos" cuenta solo los propios y "fotos",
   las fotos principales sin miniaturas. El diálogo usa los recuentos calculados con los datos validados (D5).
6. **P6. Pantallas abiertas al importar (§3.5, discrepancia 4). Resuelta: se acepta.** Al terminar una importación,
   las pestañas Cafés, Recetas, Historial y Herramientas vuelven a su raíz, y se olvidan los deshacer pendientes, la
   elección de "Moler con" y la caché de imágenes (D22).
7. **P7. Guardado tras "Listo" (§4, discrepancia 3). Resuelta: se acepta.** "Importar datos" también se deshabilita
   mientras la última preparación se guarda después de pulsar "Listo" (D23).

## 7. Verificación final

### 7.1 Clases afectadas

Solo las clases del código nuevo o modificado por el spec, nunca la batería completa (D34). Una ejecución cada vez por
emulador.

```
./gradlew :app:testDebugUnitTest \
  --tests "dev.josueamaya.pourline.backup.*" \
  --tests "dev.josueamaya.pourline.settings.*" \
  --tests "dev.josueamaya.pourline.session.ScaleUiStateTest" \
  --tests "dev.josueamaya.pourline.coffee.photo.PhotoSizingTest" \
  --tests "dev.josueamaya.pourline.ui.settings.BackupTextsTest"

ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.backup.BackupDaoTest,dev.josueamaya.pourline.backup.BackupManagerTest,dev.josueamaya.pourline.backup.BackupAtomicityTest,dev.josueamaya.pourline.backup.BackupRecoveryTest,dev.josueamaya.pourline.backup.BackupRulesTest,dev.josueamaya.pourline.ui.settings.BackupCardTest,dev.josueamaya.pourline.ui.BackupNavigationTest,dev.josueamaya.pourline.ui.SettingsScreenTest,dev.josueamaya.pourline.ui.SettingsNavigationTest,dev.josueamaya.pourline.ui.TabsNavigationTest,dev.josueamaya.pourline.ui.RecipeImportNavigationTest,dev.josueamaya.pourline.settings.ExperienceSettingsTest,dev.josueamaya.pourline.settings.FreeBrewDefaultsTest,dev.josueamaya.pourline.settings.GrinderSettingsTest,dev.josueamaya.pourline.settings.TourSettingsPersistenceTest,dev.josueamaya.pourline.coffee.photo.CoffeePhotoStoreTest,dev.josueamaya.pourline.history.HistoryMigrationTest,dev.josueamaya.pourline.ManifestPermissionsTest

ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.backup.BackupPerformanceTest

./gradlew :app:lintDebug
```

Se compara con F0: los totales solo suben por los tests nuevos y lint no tiene errores ni avisos nuevos.

### 7.2 Comprobaciones

- `git diff develop -- app/schemas` está vacío; `HistoryDatabase` tiene `version = VERSION` con `VERSION = 7`.
- `grep -n "^import android\." app/src/main/kotlin/dev/josueamaya/pourline/backup/{BackupModels,BackupFormat,BackupDtos,BackupMapping,BackupValidator,BackupArchive,BackupPhotos,ImportMarker,PhotoSwap}.kt`
  no encuentra nada.
- `grep -rn "getSharedPreferences\|tour_seen\|TourSettings" app/src/main/kotlin/dev/josueamaya/pourline/backup` no
  encuentra nada (los ajustes van por las clases y `tour_seen` no viaja).
- `grep -n "ExpertFeature" app/src/main/kotlin/dev/josueamaya/pourline/ui/settings/BackupCard.kt` no encuentra nada.
- El manifiesto fusionado tiene `dataExtractionRules` y `fullBackupContent` (F8).
- `grep -rn "\"coffee_photos\"" app/src/main/kotlin` solo encuentra la constante `COFFEE_PHOTOS_DIR`.

### 7.3 Rendimiento

Solo en `emulator-5556`, como referencia (P2), con la clase de F9 (§7.1) y
`adb -s emulator-5556 logcat -d -s BackupPerf`. Los tiempos de exportar y de leer y aplicar quedan por debajo de
15 000 ms cada uno, y las cifras (tiempos, tamaño del ZIP, de `data.json` y total descomprimido) se anotan en la
cabecera. En el teléfono del usuario no se ejecuta nada.

### 7.4 Copia automática de Android con `bmgr` (`emulator-5556`)

1. App de debug instalada, con datos (un café con foto, una receta propia, un ajuste cambiado) y un archivo de prueba
   fuera de las reglas: `adb -s emulator-5556 shell run-as dev.josueamaya.pourline sh -c 'echo x > files/fuera.txt'`.
2. `adb -s emulator-5556 shell bmgr enable true` y
   `adb -s emulator-5556 shell bmgr transport com.android.localtransport/.LocalTransport`.
3. `adb -s emulator-5556 shell setprop log.tag.BackupXmlParserLogging VERBOSE`,
   `adb -s emulator-5556 shell bmgr backupnow dev.josueamaya.pourline` y
   `adb -s emulator-5556 logcat -d -s BackupXmlParserLogging`: las cuatro reglas de `cloud-backup` (base y ajustes) y
   nada más; `coffee_photos/` no aparece.
4. Restaurar: `adb -s emulator-5556 shell bmgr list sets` (token) y
   `adb -s emulator-5556 shell bmgr restore <token> dev.josueamaya.pourline`, o desinstalar y reinstalar el APK.
5. Comprobar: el café, la receta y el ajuste vuelven; el café sin su foto (marcador sin foto, sin cierres: R18);
   `files/fuera.txt` no vuelve.
6. `device-transfer`:
   `adb -s emulator-5556 shell settings put secure backup_local_transport_parameters 'is_device_transfer=true'` y
   repetir los pasos 3 a 5. Ahora el registro muestra también `coffee_photos/` y el café vuelve con su foto. Al
   terminar, `adb -s emulator-5556 shell settings delete secure backup_local_transport_parameters`.

### 7.5 E2E por adb (sin ARTEMIS, `emulator-5556`)

**Antes de empezar:** `adb devices -l` (`emulator-5554` y el teléfono no se tocan);
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug`; `adb -s emulator-5556 shell pm clear dev.josueamaya.pourline`.
**Herramientas:** `uiautomator dump`, `input tap`, `input text`, `screencap`. `adb -s emulator-5556 logcat -b crash -d`
tiene que quedar vacío en todo el recorrido.

| Paso | Recorrido | Comprobaciones |
|---|---|---|
| 1 | Onboarding (Experto) → un café con foto (imagen subida con `adb push` a `/sdcard/Pictures/`), un molino propio, una receta propia y una preparación sin báscula. Ajustes: tema oscuro y cuenta atrás de 5 s | Datos visibles en sus listas. |
| 2 | Ajustes → "Exportar datos" → en DocumentsUI, Descargas → Guardar | El nombre propuesto es `pourline-backup-<fecha>-<hora>.zip`; mensaje de éxito con los recuentos. Captura. |
| 3 | `adb -s emulator-5556 pull /sdcard/Download/pourline-backup-*.zip`, `unzip -l` y `unzip -p <zip> manifest.json` | Entradas y orden del §3.1; manifiesto con `app`, `formatVersion` 1, `schemaVersion` 7 y recuentos. |
| 4 | `pm clear` → onboarding en Básico → Ajustes → "Importar datos" → el ZIP de Descargas | Diálogo con la fecha, "Pourline 1.0" y los recuentos. "Cancelar": nada cambia. Captura. |
| 5 | Otra vez → "Reemplazar" | Progreso modal; al terminar, mensaje con los recuentos; tema oscuro, cuenta atrás de 5 s y modo Experto restaurados sin reiniciar; el café con su foto, el molino, la receta y la preparación en sus pestañas. Capturas. |
| 6 | Iniciar una preparación sin báscula → volver a Ajustes | "Importar datos" deshabilitado con "Termina la preparación en curso para importar"; "Exportar datos" habilitado. Descartar la preparación. |
| 7 | Importar un archivo que no es una copia (una foto de Descargas, elegida con el filtro "Todos") | "Este archivo no es una copia de Pourline." |
| 8 | `./gradlew :app:assembleRelease`, instalar la release y repetir los pasos 2 y 5 | Igual que con debug (R8, R8 en el §5). |

### 7.6 Verificación manual del usuario

La hace el usuario en su teléfono, con el APK de esta rama. El agente no instala ni ejecuta nada en él (P2).

1. Exportar con sus datos reales a Drive o a una carpeta local.
2. Desinstalar la app y reinstalarla. Si Android restaura algo automáticamente (copia automática: la base y los
   ajustes, sin fotos), borrar un café antes de importar para comprobar que vuelve.
3. Importar la copia: los cafés con sus fotos, los molinos, las calibraciones, las recetas, el historial y los ajustes
   de uso coinciden.
4. Conectar la báscula recordada sin buscarla (pregunta abierta del §6 del spec). Si no conecta, se anota para un
   cambio del spec.

## 8. Seguimiento fuera de este plan

- **Cambios llevados al spec el 2026-11-19**, con las decisiones de P1 a P7:
  - §1: el estado real del manifiesto (solo `android:allowBackup="true"`, sin referencias a las reglas).
  - §2: `application/x-zip-compressed` en el selector al importar (P3).
  - §3.1: la semántica de los recuentos (`grinders` solo los propios, `photos` sin miniaturas) y que el diálogo usa los
    recuentos recalculados (P5).
  - §3.3 y §3.4: claves repetidas → `CORRUPT` (P4) y los límites de `TOO_LARGE` de D11, también al exportar.
  - §3.5: el mecanismo de recuperación (marcador `import.json` con huella) y la invalidación de D22 (P6).
  - §3.6: las fotos solo en `device-transfer` (P1).
  - §3.7: los textos de `TOO_LARGE`.
  - §3.8: con `ignoreUnknownKeys = false` (D2), cualquier campo nuevo en los DTO obliga a subir `formatVersion`.
  - §4: la condición de "preparación en curso" de D23 (P7), el límite al exportar y el rendimiento en el emulador (P2).
  - §5: los criterios 8 (emulador) y 11 (fotos solo en `device-transfer`).
  - §6: sin la pregunta del límite de tamaño, ya resuelta.
- **Fuera del alcance (§1 del spec):** copias automáticas o programadas, cifrado, CSV, fusión, "Abrir con" y la
  restauración en el onboarding.
- **Antes de fusionar en `develop`:** revisar la rama con `/code-review`.
