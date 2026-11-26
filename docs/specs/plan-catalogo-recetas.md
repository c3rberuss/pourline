# Plan de implementación: SPEC-012 Catálogo de recetas recomendadas

| Campo | Valor |
|---|---|
| Spec | [`spec-catalogo-recetas.md`](spec-catalogo-recetas.md) (SPEC-012), aprobado el 2026-11-25, y su anexo [`anexo-catalogo-recetas.md`](anexo-catalogo-recetas.md) (prevalece su sección "Decisiones del spec que prevalecen") |
| Estado | Aprobado el 2026-11-25, con commits por fase autorizados. P1 a P4 respondidas el mismo día (§6) |
| Fecha | 2026-11-25 |
| Rama | `feat/recipe-catalog` (el spec y el anexo están en `a22c92f`). El plan se commitea en la rama cuando el usuario lo autorice |
| Módulos | `:app` (`:protocol` no cambia). En la raíz del repo: `catalog/` (nuevo), `.github/workflows/catalog.yml` (nuevo) y `README.md` |
| Base de datos | `HistoryDatabase` 7 → 8 con `migration7To8(texts)` y su test; nuevo `app/schemas/dev.josueamaya.pourline.history.HistoryDatabase/8.json` |
| Copia de seguridad | `FORMAT_VERSION` 1 → 2 (D25). Las copias con formato 1 se siguen importando |
| Catálogo | `schemaVersion = 1`, `revision = 1`. 35 recetas si se completan las 3 reservas; si alguna no se puede completar con fuente fiable, se queda fuera (§3.10 del spec) |
| Línea base (F0, 2026-11-25) | JVM del paso 2: 245 tests, 0 fallos. Instrumentados del paso 3 en `emulator-5556` (AVD `SG_API34`, Android 14, con `adb root`): 197 tests, 0 fallos (ni `SettingsNavigationTest` ni casos intermitentes de `SettingsScreenTest` fallaron). `:app:lintDebug`: 0 errores y 20 avisos (`GradleDependency` 8, `NewerVersionAvailable` 6, `ExifInterface` 2, `AndroidGradlePluginVersion` 2, `PluralsCandidate` 1, `ObsoleteSdkInt` 1). Telemetría de ML Kit: ver "Resultado de F0" |

El spec no trae fases, así que este plan las define. El orden va de dentro afuera: primero el formato del catálogo y su
contenido en la JVM (F1 y F2), después la descarga y la caché (F3), los datos de Room y la retirada de las recetas de
fábrica (F4), la referencia de catálogo en Preparar (F5) y la copia de seguridad (F6). La interfaz va al final (F7 a
F9), y la red se abre en F10, cuando ya hay algo que la usa. F0 mide la línea base e investiga la telemetría; F11 es
la verificación.

Al recorrer el código salieron nueve cosas que condicionan el plan:

- **La copia de seguridad rechaza las claves desconocidas.** `BackupJson` tiene `ignoreUnknownKeys = false`
  (`backup/BackupFormat.kt`) y `BackupMappingTest` lo comprueba. Una copia nueva con `author` o `referenceKey` haría
  que una versión anterior de la app dijera "copia dañada" (`CORRUPT`). Con `FORMAT_VERSION = 2` dirá "creada con una
  versión más nueva" (`NEWER_FORMAT`), que es lo correcto (D25).
- **La migración 1 → 2 siembra las recetas de fábrica.** `migration1To2` llama a `BuiltInRecipeSeeder.seed(...,
  SeedSchema.V2)`, que lee `BuiltInRecipes`. Retirar las dos clases rompería la cadena 1 → 8. Sus definiciones de la
  versión 2 se congelan en un archivo de migración (D18).
- **Unos veinte tests instrumentados dan por hecho que existe la "V60 clásica".** `TestAppDependencies` usa
  `HistoryDatabase.inMemory`, que hoy siembra, y `SessionDriver.recipeId("V60")` la busca por nombre. Sin siembra,
  `TestAppDependencies` inserta por defecto dos recetas propias equivalentes (D47).
- **Una molienda solo de texto se muestra como "molino eliminado".** `GrindSettingText` y `GrindCard.SourceLine`
  ponen esa etiqueta a todo `GrindSetting` sin `grinderId`. 33 de las 35 recetas del catálogo solo traen
  `grind.text`, así que su copia y su preparación dirían "Media-fina · molino eliminado". Se quita la etiqueta para el texto puro (D31, P1 resuelta).
- **`GrindCard` ya convierte la molienda de la referencia al molino elegido.** Los molinos del catálogo de molinos
  están en la tabla `grinders` (`owned = false`), así que la molienda de una receta del catálogo puede apuntar a esa
  fila y la tarjeta muestra la conversión con "≈". Solo falta preseleccionar el primer molino convertible (D30).
- **El manifiesto fusionado trae tres componentes de `com.google.android.datatransport`**
  (`TransportBackendDiscovery` con el backend `cct`, `JobInfoSchedulerService` y
  `AlarmManagerSchedulerBroadcastReceiver`) y el permiso `ACCESS_NETWORK_STATE`. Hoy no envían nada porque falta
  `INTERNET` (D43).
- **Debug y release comparten `applicationId`.** Una APK de debug no se puede instalar sobre la release del teléfono
  del usuario sin desinstalarla (y perder sus datos). La comprobación en el teléfono se hace con la release firmada, que el
  usuario autorizó instalar con `adb install -r` (P3 resuelta).
- **La URL apunta a `main`.** Hasta que `catalog/recipes.json` llegue a `main`, cada descarga da 404 y la app muestra
  "No se pudo actualizar". La descarga real se prueba tras el push de la rama, ya autorizado (P4 resuelta).
- **El paso del recorrido se recuerda por id.** `pendingSteps` filtra los ids ya vistos. Si el paso conserva el id
  `recipes.builtin`, quien ya hizo el recorrido de Recetas no verá el nuevo. Se usa un id nuevo, `recipes.catalog` (D40, P2 resuelta).

No hay detekt ni ktlint en el proyecto. Como en los planes anteriores, el control es `:app:lintDebug`, y el estilo es
el del código que rodea a cada cambio (dos espacios, KDoc en español).

## 1. Punto de partida

### 1.1 Se reutiliza

| Pieza | Uso |
|---|---|
| `Recipe`, `RecipeStage`, `StageType`, `List<RecipeStage>.withDerivedWater()` (`recipe/Recipe.kt`) | Destino de la conversión del §3.4. Los totales de la UI salen de `totalWaterG`, `ratio` y `totalDurationMs`. |
| `RecipeValidator.validate` y sus límites | Validación de cada receta del catálogo en cada idioma (§3.1). Gana dos errores (D6). |
| `BrewMethod` (`history/HistoryModels.kt`) | Valores admitidos de `method`. |
| `GrinderCatalogFile.parse` y `AssetCatalogSource(context, path)` (`grinder/`) | El catálogo de molinos con el que se validan `grind.grinder` (D4) y la lectura de la copia del APK (`path = "catalog/recipes.json"`). |
| `resolveSharedGrind`, `GrindResolution`, `GrindPick`, `defaultPick`, `settingFor`, `sharedGrindText` (`grinder/SharedGrindResolution.kt`) y `SharedGrind` (`recipe/share/SharedRecipe.kt`) | La molienda de la copia (§3.6) y el molino preferido en Preparar (§3.7). |
| `GrindFormat.SEPARATOR` y `GrindFormat.format` | El texto " · <marca> <modelo> <ajuste>" (D28). |
| `GrinderRepository.owned()`, `byCatalogKey`, `calibrations` | Molinos propios en el orden de la pantalla Molinos, fila del molino del catálogo y sus pares. |
| `GrindSelection` y `grindCardState` | La tarjeta de molienda de Preparar con una referencia de catálogo (D30). |
| `uniqueName` y `nameKey` (`recipe/share/RecipeImportRules.kt`) | Sufijo " (2)" de la copia (§3.6). |
| `fold` (`coffee/CoffeeSearch.kt`) | Búsqueda sin mayúsculas ni acentos (D33). |
| `TargetCurve.fromRecipe`, `BrewChart` | Vista previa de la curva en Experto (D41). |
| `RecipeSummary`, `StageRow`, `GrindSection` (privados en `ui/recipes/RecipeImportScreen.kt`) | Se extraen para el detalle (D42). |
| `ExpertFeature.RECIPE_CURVE_PREVIEW`, `LocalExperienceMode` | La curva del detalle solo en Experto (D41). |
| `AntSegmented`, `AntSelect`, `AntInput`, `AntAlert`, `AntEmpty`, `AntTag`, `AntDrawer`, `AntMessage` | Componentes de la interfaz nueva. |
| `formatBrewDateTime` (`ui/history/HistoryFormatters.kt`) | "Última actualización: <fecha>". |
| `ScaleSessionManager.selectTarget`, `Resolution.Removed`, `ScaleError.TargetRemoved` | "Preparar" y el aviso de receta desaparecida (§3.7). |
| `MigrationTestHelper` y `HistoryMigrationTest` | Test 7 → 8. |
| `TestAppDependencies`, `TestGrinderCatalog`, `SessionDriver` | Tests instrumentados de la interfaz y de la preparación. |
| `release.yml` | Pasos de `checkout`, `setup-java` y `setup-gradle` del workflow nuevo. |

### 1.2 Falta o hay que corregir

| Pieza | Situación |
|---|---|
| Paquete `recipe/catalog` | No existe: modelo, parser, conversión, molienda, copia, política, descarga, caché, repositorio y filtro. |
| `catalog/recipes.json`, `catalog/README.md` | No existen. |
| Copia del catálogo en el APK | No hay tarea de build. |
| `BuildConfig` | `buildConfig = false` en `app/build.gradle.kts`. |
| `Recipe`, `RecipeEntity` | Sin `author`, `sourceUrl` ni `catalogKey`. |
| `BrewEntity` | Sin `referenceKey`. |
| `ReferenceType`, `ReferenceKey`, `UsedTarget` | Sin `CATALOG`, sin `Catalog(key)`; `UsedTarget.id` es `Long` no nulo. |
| `TargetSettings.readReference` | Devuelve `null` si no hay `target_ref_id`, así que no puede leer una referencia por clave. |
| `TargetReferences` | Solo `recipe(id)` y `brew(id)`. |
| `ScaleSessionManager` | `ReferenceKey.id` (extensión privada) y el `when` del colector de referencias no contemplan una clave de texto. |
| `GrindSource`, `ActiveTarget` | Sin molino preferido (D30). |
| `BrewFactsCard` | Solo muestra el nombre de la referencia con `RECIPE`. |
| `RecipeRepository` | Traduce al vuelo las predefinidas, `restoreBuiltIns`, `RecipeListItem.builtIn`. Falta `catalogKeys()`. |
| Editor de recetas | Sin "Autor" ni "Fuente"; `keepsLocalizedNames` deja de tener sentido. |
| `SettingsScreen` | Tarjeta "Restaurar recetas predefinidas" bajo `ExpertFeature.RESTORE_BUILT_INS`. |
| `RecipesScreen`, `RecipeRow` | Sin selector; etiqueta "Predefinida" y su ancla del recorrido. |
| `TargetPicker` | Pestañas `RECIPES` e `HISTORY`; el segmentado solo aparece en Experto. |
| `AntSegmented` | No admite un `Modifier` por opción (ancla del recorrido y `testTag`, D32). |
| Manifiesto | `INTERNET` con `tools:node="remove"`; `ManifestPermissionsTest.theAppRequestsNeitherInternetNorCamera` lo exige. |
| Copia de seguridad | DTO sin los campos nuevos; `FORMAT_VERSION = 1`; sin clave `target_ref_key`. |
| CI | Solo `release.yml`. |

### 1.3 Discrepancias entre el spec y el código

Todas quedaron resueltas el 2026-11-25 (§6). El spec ya recoge las 1 a 4 y el total de recetas condicionado a las
reservas (§1 y §3.10); las 5 y 6 se resuelven con el push autorizado, sin cambiar el spec, y la 7 no cambia nada.
La columna "Spec" describe el texto anterior; la tabla se conserva como registro.

| # | Spec | Código real | Ajuste del plan |
|---|---|---|---|
| 1 | §3.5: la referencia "se guarda como tipo `catalog`". | `TargetSettings` guarda `"RECIPE"` y `"BREW"` (nombre del valor, en mayúsculas). | Se guarda `"CATALOG"`, con la clave en `target_ref_key` (D21). El spec dice ahora `CATALOG`. |
| 2 | §3.6 y §3.7: el texto de la molienda, cuando no se enlaza a un molino, es `grind.text`. | Un `GrindSetting(grinderId = null, …)` se muestra con la etiqueta "molino eliminado" (`GrindSettingText`, `GrindCard.SourceLine`) y, en Preparar, con "Asigna un molino a la receta para convertir". | D31. **P1, resuelta**: se aplica, también a las recetas importadas "como texto" de SPEC-009; el spec lo dice en §3.6 y §3.7. |
| 3 | §3.8: "el paso `recipes.builtin` se ancla al segmento Recomendadas". | `pendingSteps` filtra por id visto. | Id nuevo `recipes.catalog` para que lo vea también quien ya hizo el recorrido (D40). **P2, resuelta**; el spec lo dice en §3.8. |
| 4 | §5: "verificado en el móvil (F0) que ML Kit no envía telemetría". | Sin root no se puede atribuir el tráfico por destino en el teléfono, y el agente no instala nada en el teléfono del usuario sin permiso (debug y release comparten `applicationId`). | La atribución por destino se mide en `emulator-5556` con `iptables` (root); en el teléfono, comprobaciones sin root sobre la release firmada (D46, §7.6). **P3, resuelta**: el agente instala la release firmada con `adb install -r` y lanza los `dumpsys`; el criterio 13 del spec describe ahora esta verificación. |
| 5 | §3.9: el workflow se ejecuta en cada `push` que toque `catalog/**`. | Ver que se ejecuta exige un push, que solo se hace con permiso. | El fallo con una receta inválida se comprueba con un test (§5 del spec); la ejecución en GitHub, tras el push de la rama (**P4, resuelta**: autorizado). |
| 6 | §3.1: URL de `main`. | Hasta la fusión con `main` la descarga da 404. | Sin cambio de producto. Para probar la descarga antes, la URL admite una propiedad de Gradle (D9, **P4, resuelta**). |
| 7 | §3.9: el tráfico de la app. | El escáner de QR (`play-services-code-scanner`) y la descarga del modelo de códigos de barras corren en Google Play services, con su propio UID. | No es tráfico de la app ni cambia con este spec: queda fuera de la comprobación por UID (§7.4) y se dice en el README (D45). |

## 2. Decisiones que completan el spec

Marcadas como "(concreta)" si eligen entre opciones que el spec deja abiertas, y como "(amplía)" si añaden algo que
el spec no dice.

### 2.1 Catálogo: modelo, parser y conversión

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Paquete y archivos (concreta §3.3) | `dev.josueamaya.pourline.recipe.catalog`, todo en Kotlin puro salvo `ResourcesCatalogLanguage`: `CatalogModels.kt`, `RecipeCatalogDtos.kt`, `RecipeCatalogParser.kt`, `CatalogConversion.kt`, `CatalogGrind.kt`, `CatalogCopy.kt`, `CatalogPolicy.kt`, `CatalogFetcher.kt` (interfaz y `HttpCatalogFetcher`, con `java.net`), `CatalogCache.kt` (`java.io`), `RecipeCatalogRepository.kt`, `CatalogFilter.kt`, `CatalogTargetSource.kt` y `ResourcesCatalogLanguage.kt`. Los nombres evitan los de `grinder/` (`CatalogEntry`, `CatalogData`, `CatalogFileDto`, `CatalogGrinderDto` ya existen allí). | Es la separación del §3.3: parser, conversión, política y descarga se prueban en la JVM, incluso la descarga real contra un servidor local (D10). |
| D2 | JSON (concreta §3.1) | `internal val CatalogJson = Json { ignoreUnknownKeys = true; explicitNulls = false }`. La raíz se lee primero como `JsonObject`: `schemaVersion` entero obligatorio; si es mayor que `RecipeCatalogParser.SCHEMA_VERSION = 1`, `NewerSchema` antes de mirar nada más; `revision` entero ≥ 1 y `recipes` lista obligatorios; si falta alguno, o no es JSON, `Corrupt`. Cada receta se decodifica por separado (`decodeFromJsonElement<RecipeCatalogRecipeDto>`): si falla, se descarta esa receta. | "Los campos desconocidos se ignoran" (§3.1) y una receta rota no tumba el resto (§4). Leer `schemaVersion` primero permite avisar "Actualiza la app" aunque el formato nuevo no se pueda decodificar. |
| D3 | Textos localizados (concreta §3.1 y §3.2) | `RecipeCatalogLocalizedDto(es: String? = null, en: String? = null)`; otras claves se ignoran. `LocalizedText(es, en)` exige las dos, recortadas y no vacías. `enum class CatalogLanguage { ES, EN }` con `fun of(code: String)` (`"en"` → `EN`, cualquier otro → `ES`). | La carpeta por defecto es la española (§3.2). |
| D4 | Validación de cada receta (concreta §3.1) | Se descarta la receta si: `key` no cumple `^[a-z0-9_]{1,64}$`; `method` o algún `type` no es un valor de `BrewMethod` o `StageType`; `methodOther` está sin `OTHER` o falta con `OTHER`; falta un idioma en un texto localizado; `waterG` falta en un `POUR` o aparece en otro tipo; `startS` o `durationS` negativos; `grind.grinder.catalogKey` no está en el catálogo de molinos o `value` cae fuera de `[scaleMin, scaleMax]`; `sourceUrl` no empieza por `https://`; hay `author` sin `sourceUrl`; o la `Recipe` de §3.4 en `ES` o en `EN` no pasa `RecipeValidator.validate`. **Claves repetidas:** se descartan todas las recetas con esa clave. Cada descarte lleva `DiscardedRecipe(index: Int, key: String?, reason: String)`. Si no queda ninguna receta válida, el resultado es `Corrupt` (§4). | Un catálogo con dos recetas de la misma clave es ambiguo: no se adivina cuál es la buena, y el test del repo lo detecta (D7). `reason` sirve al test del repo para decir qué falla. |
| D5 | API del parser (concreta §3.3) | `object RecipeCatalogParser { const val SCHEMA_VERSION = 1; fun parse(text: String, grinders: Map<String, Grinder>): CatalogParseResult }` con `sealed interface CatalogParseResult { data class Parsed(val catalog: ParsedCatalog); data class NewerSchema(val schemaVersion: Int); data object Corrupt }` y `ParsedCatalog(schemaVersion: Int, revision: Int, entries: List<CatalogRecipeEntry>, discarded: List<DiscardedRecipe>)`. `CatalogRecipeEntry` guarda los dos idiomas y `fun inLanguage(language: CatalogLanguage): CatalogRecipe`. | El repositorio guarda las entradas en los dos idiomas y elige el idioma al usarlas (D16). |
| D6 | Campos nuevos de `Recipe` y límites (concreta §3.5) | `Recipe` gana al final `author: String? = null`, `sourceUrl: String? = null` y `catalogKey: String? = null`; conserva `builtInKey` y `localizedNames` (§3.4), que dejan de usarse. `RecipeValidator`: `MAX_AUTHOR = 100`, `MAX_SOURCE_URL = 500`; errores nuevos `RecipeError.AuthorTooLong` y `RecipeError.SourceUrlInvalid`. Una fuente es válida si, recortada, mide ≤ 500, no tiene espacios, su esquema es `http` o `https` (sin distinguir mayúsculas) y `java.net.URI(url).host` no está vacío. | Los autores del §3.10 miden menos de 40 caracteres y las fuentes menos de 160. `java.net.URI` funciona en la JVM y en Android. El parser añade la exigencia de `https://` (D4); el editor admite también `http://` (§3.5). |
| D7 | Test del repo (concreta §3.9) | `test/.../recipe/catalog/RecipeCatalogFileTest.kt` lee `File("../catalog/recipes.json")` y `File("src/main/assets/grinders/catalog.json")` (el directorio de trabajo de los tests de `:app`, como `GrinderCatalogFileTest`). La comprobación vive en `test/.../recipe/catalog/RecipeCatalogCheck.kt`: `fun catalogProblems(text: String, grinderCatalog: String): List<String>`, vacía si todo está bien (raíz con `schemaVersion == 1` y `revision`, ninguna receta descartada con su motivo, `four_six` y `v60_classic` presentes). El test del repo exige `catalogProblems(...) == emptyList()`. **No fija la lista de claves**. | Así se puede añadir una receta sin tocar código (US-8). `four_six` y `v60_classic` sí se exigen: las copias migradas (§3.5) apuntan a ellas. El contenido inicial del §3.10 se comprueba una vez en F2. |
| D8 | Conversión (concreta §3.4) | `fun CatalogRecipe.toRecipe(): Recipe` en `CatalogConversion.kt`: pasos ordenados por `startMs`, `startS × 1000`, `durationS × 1000`, el agua del `POUR` y `withDerivedWater()` para el resto; `author`, `sourceUrl`, `catalogKey = key`; `id = 0`, `builtInKey = null`, `localizedNames = false`; `grind = null` (la molienda la pone quien usa la receta, D29 y D30). `context` no pasa a `Recipe`: solo se muestra en el detalle. | Es la tabla del §3.4. Separar la molienda deja la conversión sin dependencias de molinos. |

### 2.2 Copia empaquetada, descarga y caché

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D9 | Constante de la URL (concreta §3.1; amplía) | `buildFeatures { buildConfig = true }` y `buildConfigField("String", "RECIPE_CATALOG_URL", "\"$catalogUrl\"")` con `catalogUrl = providers.gradleProperty("pourline.catalogUrl").getOrElse("https://raw.githubusercontent.com/c3rberuss/pourline/main/catalog/recipes.json")` y `require(catalogUrl.startsWith("https://"))`. | Es una constante de build, como pide el §3.1. La propiedad permite probar la descarga con la URL de la rama antes de fusionar con `main` sin tocar código; la release del workflow no la pasa. El usuario la aceptó el 2026-11-25, solo para builds locales (P4). |
| D10 | Descarga (concreta §3.3) | `interface CatalogFetcher { suspend fun fetch(etag: String?): FetchResult }` y `sealed interface FetchResult { data class Fetched(val body: String, val etag: String?); data object NotModified; data object Failed }`. `HttpCatalogFetcher(url: String, timeoutMs: Int = 10_000, maxBytes: Int = 1_048_576)`: GET en `Dispatchers.IO`, `connectTimeout` y `readTimeout` de 10 s, `useCaches = false`, cabeceras solo `Accept: application/json`, `User-Agent: Pourline` e `If-None-Match` si hay ETag. 200 → cuerpo UTF-8 (más de `maxBytes`, `Failed`); 304 → `NotModified`; otro código → `Failed`. `IOException` y `SecurityException` → `Failed`. `disconnect()` en `finally`. | El `User-Agent` por defecto de Android (`Dalvik/… Android 14; <modelo>`) lleva el modelo del teléfono: el §3.3 pide "sin cabeceras con datos del usuario". El límite evita leer en memoria un archivo absurdo. Sin `INTERNET` (antes de F10) la conexión lanza y el resultado es `Failed`. |
| D11 | Caché (concreta §3.3) | `CatalogCache(dir: File)` con `dir = File(filesDir, "catalog")`: `recipes.json` y `recipes.meta.json` (`{"downloadedAtEpochMs":…,"etag":…}`). `read(): CachedCatalog?` (texto y metadatos; `null` si no hay archivo; lanza `CatalogCacheUnreadable` si no se puede leer), `write(text, etag, downloadedAtMs)` (cada archivo a `*.tmp` en el mismo directorio y `renameTo`; primero el catálogo, después los metadatos), `markChecked(downloadedAtMs, etag)` (304) y `clear()`. | Escritura atómica del §3.3. Un fallo entre los dos renombrados deja un catálogo válido con la hora anterior: como mucho, una descarga de más. `filesDir/catalog/` no está en las reglas de la copia automática de Android (SPEC-011 §3.6, solo incluye lo que nombra). |
| D12 | Qué copia se usa (concreta §3.3 y §4) | `CatalogPolicy.choose(cached: ParsedCatalog?, bundled: ParsedCatalog?): ParsedCatalog?`: la de `revision` mayor; con empate, la caché. Una caché ilegible, `Corrupt` o `NewerSchema` se borra (`clear()`) y se usa la del APK. | §3.3 y §4 ("caché ilegible → copia del APK y se borra la caché"). |
| D13 | Cuándo se descarga (concreta §3.3) | `CatalogPolicy.isStale(lastDownloadMs: Long?, nowMs: Long) = lastDownloadMs == null \|\| nowMs - lastDownloadMs >= 24 h \|\| nowMs < lastDownloadMs`. La interfaz llama a `refreshIfStale()` al mostrar "Recomendadas" (pestaña Recetas o `TargetPicker`) y a `refresh(force = true)` al deslizar. Las dos van a `refresh(force)`, que devuelve el `Deferred` en curso si lo hay y si no lanza uno nuevo en el ámbito del repositorio (`applicationScope`). | Unir peticiones (§3.3) y que la descarga siga tras rotar o salir (§4). Un reloj que va hacia atrás no bloquea las descargas. |
| D14 | Resultado de una descarga (concreta §3.3 y §4) | `Fetched` → `parse`: `Parsed` → `cache.write`, se vuelve a elegir con `choose` y `lastDownloadMs = now`, sin error. `NotModified` → `markChecked`, sin error. `Corrupt` o `Failed` → error `UPDATE_FAILED`, sin tocar la caché. `NewerSchema` → error `NEWER_SCHEMA`, sin tocar la caché ni la hora (la siguiente visita lo intenta otra vez). | Es la tabla del §4. Un catálogo válido pero sin recetas válidas ya es `Corrupt` (D4). |
| D15 | Estado del repositorio (concreta §3.3) | `class RecipeCatalogRepository(bundled: CatalogSource, cache: CatalogCache, fetcher: CatalogFetcher, grinders: () -> Map<String, Grinder>, scope: CoroutineScope, io: CoroutineDispatcher = Dispatchers.IO, now: () -> Long = System::currentTimeMillis)` con `val state: StateFlow<CatalogState>`, `fun ensureLoaded()`, `fun refreshIfStale()`, `fun refresh(force: Boolean): Deferred<Unit>` y `fun entry(key: String): Flow<CatalogRecipeEntry?>`. `data class CatalogState(val loaded: Boolean, val entries: List<CatalogRecipeEntry>, val revision: Int?, val lastDownloadMs: Long?, val refreshing: Boolean, val error: CatalogError?)` y `enum class CatalogError { UPDATE_FAILED, NEWER_SCHEMA }`. `ensureLoaded` es idempotente (con `Mutex`) y lee la copia local en `io`. `entry(key)` llama a `ensureLoaded` y no emite hasta `loaded = true`. | Sin la espera, una referencia de catálogo activa al arrancar se resolvería como `Removed` antes de leer el catálogo y se borraría. |
| D16 | Idioma (concreta §3.2) | Recurso `catalog_language` (`es` en `values`, `en` en `values-en`). `fun interface CatalogLanguageSource { fun current(): CatalogLanguage }` y `ResourcesCatalogLanguage(context)`, que lee el recurso del contexto de la aplicación en cada llamada. El idioma se elige al mostrar, al guardar la copia y al empezar a preparar; lo guardado no se vuelve a traducir. | Si el usuario cambia el idioma del sistema con la app abierta, la lista sigue el idioma nuevo sin recrear el repositorio. |

### 2.3 Datos: Room, recetas de fábrica y referencias

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D17 | Esquema 8 (concreta §3.5) | `RecipeEntity` gana `author`, `sourceUrl`, `catalogKey` (`String? = null`), sin índice; `BrewEntity` gana `referenceKey: String? = null`. `HistoryDatabase.VERSION = 8`. `fun migration7To8(texts: RecipeTexts): Migration` en `history/Migrations.kt`: cuatro `ALTER TABLE … ADD COLUMN … TEXT`; después, por cada fila de `recipes` con `builtInKey` no nulo: si la clave es de `LegacyBuiltIns.KEYS` y `localizedNames = 1`, `name = texts.builtInName(key)` y, por posición `i`, `name = texts.builtInStageNames(key).getOrNull(i) ?: <guardado>` e `instruction = texts.builtInStageInstructions(key).getOrNull(i) ?: <guardado>` (la regla de `RecipeRepository.toRecipe` de hoy); en todos los casos `localizedNames = 0`, `builtInKey = NULL`, `catalogKey = <clave antigua>`. `id` y `updatedAtEpochMs` no cambian. | Es el §3.5. Sin índice: la tabla es pequeña y `catalogKeys()` lee una columna. Una clave desconocida (no debería existir) no llama a `ResourcesRecipeTexts`, que fallaría, y solo se limpia. |
| D18 | Retirada de las recetas de fábrica (concreta §3.5; amplía) | Se borran `recipe/BuiltInRecipes.kt` y `recipe/BuiltInRecipeSeeder.kt`. Nuevo `history/LegacyBuiltIns.kt`: `internal object LegacyBuiltIns { const val V60_CLASSIC = "v60_classic"; const val FOUR_SIX = "four_six"; val KEYS: Set<String>; fun seedV2(db: SupportSQLiteDatabase, texts: RecipeTexts, nowMs: Long) }` con los pasos `POUR` de las dos recetas (la rama `SeedSchema.V2` de hoy), que usa `migration1To2`. `HistoryDatabase.create` pierde `seedOnCreate`; `inMemory(context)` pierde el parámetro `texts` y no siembra. `RecipeTexts.builtInName`, `builtInStageNames`, `builtInStageInstructions`, `ResourcesRecipeTexts` y los recursos `recipe_builtin_*` se quedan, con un KDoc que dice que solo los usan las migraciones 1 → 2 y 7 → 8. | La cadena 1 → 8 sigue funcionando y la migración 3 → 4 sigue completando los pasos de espera. Los textos los exige el §3.5 mientras exista la migración. |
| D19 | `RecipeRepository` y `RecipeDao` (concreta §3.5) | Sin traducción al vuelo (`displayName` y la rama de `toRecipe`), sin `restoreBuiltIns`; `RecipeListItem` pierde `builtIn`. `toEntity` y `toRecipe` llevan los tres campos nuevos (`cleaned()`). `duplicate` conserva `author`, `sourceUrl` y `catalogKey`. Nuevo `fun catalogKeys(): Flow<Set<String>>` sobre `@Query("SELECT DISTINCT catalogKey FROM recipes WHERE catalogKey IS NOT NULL") fun observeCatalogKeys(): Flow<List<String>>`. `RecipeDao` pierde `byBuiltInKey` y `restoreBuiltIns`. | "Duplicar" una copia sigue siendo la misma receta de origen: la etiqueta "Guardada" se mantiene. |
| D20 | Editor (concreta §3.5 y §3.8) | `RecipeEditForm` gana `author: String = ""` y `sourceUrl: String = ""`; `RecipeFormErrors` gana `author` y `sourceUrl`; `FieldError` gana `AUTHOR_TOO_LONG` y `SOURCE_URL_INVALID`. `toRecipe(id: Long = 0, catalogKey: String? = null)` sustituye a `toRecipe(id, builtInKey, localizedNames)`; se borra `keepsLocalizedNames`. `RecipeEditViewModel.save` pasa `loaded?.catalogKey`. En pantalla, después de "Notas": "Autor" (`LabeledInput`, una línea) y "Fuente" (`KeyboardType.Uri`), y un botón de enlace "Ver fuente" cuando la fuente es válida. | Cualquier receta propia puede tenerlos (§3.5). La copia de catálogo conserva su `catalogKey` al editarse. |
| D21 | `ReferenceKey.Catalog` (concreta §3.5) | `data class Catalog(val key: String) : ReferenceKey`. `TargetSettings`: tipo `"CATALOG"` y clave nueva `target_ref_key`. `readReference()`: con `"CATALOG"` lee `target_ref_key` (sin ella, `null`); con `"RECIPE"` o `"BREW"`, `target_ref_id` como hoy. `setReference` borra la clave que no usa. `SettingsSnapshot` gana `targetRefKey: String? = null`, y `exportInto` y `restoreFrom` la llevan. | Discrepancia 1. Borrar la clave sobrante evita que una referencia vieja se lea con el tipo nuevo. |
| D22 | `ReferenceType.CATALOG` y `UsedTarget` (concreta §3.5) | `enum class ReferenceType { RECIPE, BREW, CATALOG }`. `UsedTarget(type, id: Long?, name, curve, fit, key: String? = null)` (el `key` al final, para no tocar las llamadas posicionales de los tests). `BrewRepository` escribe `referenceKey = target?.key`. En `ScaleSessionManager`, la extensión privada `ReferenceKey.id: Long` se sustituye por `refId: Long?` (`Recipe`, `Brew`) y `refKey: String?` (`Catalog`). | `HistoryConverters` ya guarda los enums por nombre; un nombre desconocido se lee como `null`. |
| D23 | Resolución en Preparar (concreta §3.7) | `TargetReferences` gana `fun catalog(key: String): Flow<CatalogTarget?>` con `data class CatalogTarget(val recipe: Recipe, val preferredGrinderId: Long?)`. La implementa `CatalogTargetSource(catalog: RecipeCatalogRepository, grinders: GrinderRepository, language: CatalogLanguageSource, texts: GrindTexts)`: combina `catalog.entry(key)`, `grinders.owned()` y la fila y los pares del molino del catálogo; `null` si la clave no está (ya cargado). `RepositoryTargetReferences(recipes, brews, catalog: CatalogTargetSource)`. `ScaleSessionManager` gana `resolveCatalog(key, target)`: `null` → `Resolution.Removed`; si no, `TargetBase(key, ReferenceType.CATALOG, recipe.name, TargetCurve.fromRecipe(recipe), recipe.doseG, prefill, recipe.grind, preferredGrinderId)`. `TargetBase` y `ActiveTarget` ganan `preferredGrinderId: Long? = null`. | Es el §3.7. El nombre y la curva se congelan al empezar, como con las demás referencias. |
| D24 | Historial (concreta §3.7) | `BrewFactsCard` muestra `referenceName` con `RECIPE` o `CATALOG`. `BrewDetailScreen` (`FitCard`) ya lo muestra con cualquier tipo. | §3.7. |

### 2.4 Copia de seguridad

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D25 | Formato 2 (concreta §3.5) | `FORMAT_VERSION = 2`. `RecipeDto` gana `author`, `sourceUrl`, `catalogKey`; `BrewDto` gana `referenceKey`; todos `String? = null`. El lector sigue aceptando `formatVersion` 1 (ya acepta las anteriores). | El lector rechaza claves desconocidas (`ignoreUnknownKeys = false`, SPEC-011 D2). Sin subir la versión, una app anterior diría "copia dañada" ante una copia nueva; con la subida, "creada con una versión más nueva". Con `explicitNulls = false`, una copia 1 sin esas claves se lee con `null`. |
| D26 | Ajuste `target_ref_key` (amplía §3.5) | `SettingsDto` gana `@SerialName("target_ref_key") val targetRefKey: String? = null`, con su mapeo a `SettingsSnapshot.targetRefKey` (D21). | Sin ella, una referencia de catálogo activa se perdería al importar. |
| D27 | Recetas de fábrica en copias con formato 1 (amplía §3.5) | `fun BackupRows.withLegacyBuiltInsConverted(): BackupRows` (`backup/BackupLegacy.kt`, JVM): cada receta con `builtInKey` pasa a `catalogKey = builtInKey`, `builtInKey = null`, `localizedNames = false`, sin traducir. `BackupManager.readBackup` la aplica si `manifest.formatVersion == 1`. | Una copia anterior traería filas de fábrica a una base 8 que ya no las traduce ni las usa. Sin traducir: el texto guardado es el del idioma en que se sembró, el mismo que el usuario veía. |

### 2.5 Molienda

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D28 | Texto de la molienda (concreta §3.6) | `fun CatalogRecipe.toSharedGrind(catalogGrinder: Grinder?, texts: GrindTexts): SharedGrind?` (`CatalogGrind.kt`): `null` sin `grind`; con `grinderRef`, `SharedGrind(catalogKey, brand, model, value, label)`; `label` = `grindText` + `GrindFormat.SEPARATOR` + "<marca> <modelo>" + `GrindFormat.SEPARATOR` + `GrindFormat.format(value, catalogGrinder, texts)`, o solo la parte del molino si no hay `grindText`; sin `grinderRef`, `label = grindText`. Ejemplo: "Media-fina · Comandante C40 MK4 · 27 clics". | §3.6 (" · <marca> <modelo> <ajuste>"), con el mismo separador que el resto de la app. Pasar el texto en `label` hace que `sharedGrindText` lo use tal cual en `Convertible.textLabel` y `Text.label`. |
| D29 | Copia (concreta §3.6) | `fun catalogCopy(recipe: CatalogRecipe, resolution: GrindResolution, pick: GrindPick?, owned: List<Grinder>, existingNames: Collection<String>, texts: GrindTexts): Recipe` (`CatalogCopy.kt`) = `recipe.toRecipe().copy(name = uniqueName(recipe.name, existingNames), grind = resolution.settingFor(pick, owned, texts))`. La resolución es `resolveSharedGrind(recipe.toSharedGrind(...), owned, catalogGrinder, calibrations, texts)` con `owned` de `GrinderRepository.owned().first()`, `catalogGrinder` de `byCatalogKey` y los pares de ese molino. El selector se muestra solo con `Convertible` (siempre tiene al menos un molino y "Guardar como texto"). `fun hasCopy(key: String, catalogKeys: Set<String>): Boolean`. | Es la lógica de SPEC-009 §3.6 sin duplicarla. Las funciones puras cubren el criterio "molino propio, convertible y texto; sufijo; aviso" en la JVM. |
| D30 | Molienda en Preparar (concreta §3.7) | `recipe.grind` de la referencia: con `grinderRef` y la fila del molino del catálogo presente, `GrindSetting(catalogGrinder.id, value, label de D28)`; si no, `GrindSetting(null, null, label)`; sin `grind`, `null`. `preferredGrinderId` = el molino de `Linked`, el de `defaultPick()` en `Convertible`, o `null`. `GrindSource` gana `preferredGrinderId: Long? = null` y `preselected()` (`grinder/GrindCardState.kt`) pasa a: elegido → molino de la referencia si es propio → **preferido** → predeterminado → primero. `GrindSelection` lo copia de `ActiveTarget`. | La tarjeta ya convierte desde el molino de la referencia y marca "≈" al redondear. Con el preferido delante del predeterminado se cumplen los tres casos del §3.7: mismo molino, primer convertible en el orden de Molinos, o texto. Cambiar "Moler con" convierte desde el ajuste original, no desde uno ya convertido. Para las demás referencias `preferredGrinderId` es `null` y nada cambia. |
| D31 | Molienda solo de texto (amplía; P1 resuelta) | Un `GrindSetting` con `grinderId == null` y `value == null` es "texto", no "molino eliminado": `GrindSettingText` y `GrindCard.SourceLine` lo muestran sin la etiqueta `grinder_tag_deleted`, y `grindCardState` da la sugerencia del método (`suggestion`) en lugar de `GrindOutcome.Orphan`. Un molino borrado con ajuste (`value != null`) sigue igual. | Sin esto, 33 recetas del catálogo dirían "molino eliminado" en la copia, en Preparar y en el detalle de la preparación. Afecta también a las recetas importadas "como texto" de SPEC-009, que tampoco tenían un molino del usuario. El usuario lo aprobó el 2026-11-25 (P1), también para las importadas de SPEC-009. |

### 2.6 Interfaz

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D32 | Selector "Mis recetas / Recomendadas" (concreta §3.8) | `enum class RecipesSegment { MINE, CATALOG }` en `RecipesViewModel` (`segment: StateFlow<RecipesSegment>`, `selectSegment`). `AntSegmented` gana `itemModifier: (T) -> Modifier = { Modifier }`, aplicado a cada opción: la de "Recomendadas" lleva `tourAnchor("recipes_catalog_segment")` y `testTag("recipes_segment_catalog")`; la otra, `testTag("recipes_segment_mine")`. | El `ViewModel` de la raíz de Recetas vive mientras viva la app (la raíz nunca se quita de la pila): "se recuerda durante la sesión de la app". El ancla va a la opción, no al control entero, como pide el §3.8. |
| D33 | Lista de recomendadas (concreta §3.8) | `class CatalogBrowser(catalog: RecipeCatalogRepository, recipes: RecipeRepository, language: CatalogLanguageSource, scope: CoroutineScope)` (`ui/recipes/catalog/`), con `ui: StateFlow<CatalogListUi>`, `setQuery`, `setMethod`, `clearFilters`, `onShown()` (`refreshIfStale`) y `refresh()` (`force`). Lo usan `RecipesViewModel` y `TargetPickerViewModel`. El filtro es puro: `fun filterCatalog(items: List<CatalogListItem>, query: String, method: BrewMethod?): List<CatalogListItem>` y `fun catalogMethods(items): List<BrewMethod>` (en el orden de `BrewMethod`). La búsqueda compara `fold()` del texto con `fold()` del nombre y del autor, más `ø→o`, `æ→ae`, `œ→oe`, `ß→ss`, `ł→l`, `đ→d`. | "Ole Kristian Bøen" se encuentra escribiendo "boen": `ø` no es un acento combinable y `fold` no la cambia. Un solo componente evita dos listas distintas. |
| D34 | Avisos (concreta §3.8 y §4) | Encima de la lista: `AntAlert` de tipo aviso con `catalog_update_failed` ("No se pudo actualizar · Última actualización: <fecha>") si hay error y alguna descarga correcta, `catalog_using_bundled` si hay error y ninguna, o `catalog_newer_schema`; sin error y con descarga, `AntText` secundario con `catalog_last_update`. Fecha con `formatBrewDateTime`. | "Aviso discreto" (§3.8). |
| D35 | Deslizar para actualizar (concreta §3.8) | `PullToRefreshBox` de Material 3 (`isRefreshing = state.refreshing`), que también gira durante la descarga automática. | No hay otro "pull to refresh" en la app; la BOM 2026.03.01 lo trae estable. |
| D36 | Detalle (concreta §3.8) | `@Serializable data class CatalogRecipeDetail(val key: String) : NavKey`, en la pila de Recetas, con la barra inferior visible (no se añade a `showsBottomBar`). `CatalogRecipeDetailViewModel(key, catalog, recipes, grinders, grindTexts, language)` con `ui: StateFlow<CatalogDetailUi>` (`Loading`, `Missing`, `Ready(recipe: CatalogRecipe, display: Recipe, resolution: GrindResolution, pick: GrindPick?, alreadySaved: Boolean, saving: Boolean)`), `pick(GrindPick)` y `save()`, y un evento `Saved` para el mensaje. `Missing` (la clave desapareció tras actualizar) muestra `AntEmpty` con `catalog_detail_missing`. "Ver fuente" usa `fun openSourceUrl(context: Context, url: String): Boolean` (`ui/recipes/SourceLink.kt`, `ACTION_VIEW`, `false` con `ActivityNotFoundException`) y muestra `catalog_no_browser` si devuelve `false`. La pantalla recibe `openUrl: (String) -> Boolean` para los tests. | §3.8 y §4. `Missing` cubre una actualización con la pantalla abierta. |
| D37 | "Preparar" desde el detalle (concreta §3.7) | `deps.scaleSession.selectTarget(ReferenceKey.Catalog(key))` y `selectTab(Tab.PREPARE)`. Con una sesión activa, `selectTarget` se ignora como hoy y solo cambia de pestaña. | "Cambiar la referencia sigue las reglas actuales de Preparar" (§4). |
| D38 | `TargetPicker` (concreta §3.8) | `PickerTab { RECIPES, CATALOG, HISTORY }`; el segmentado se muestra siempre; `HISTORY` solo con `showBrews`. La pestaña `CATALOG` reutiliza la búsqueda, el filtro y las filas de D33 dentro de `ListMaxHeight`; elegir una fila llama a `onSelect(ReferenceKey.Catalog(key))`; la fila activa se resalta como las demás. Al mostrar la pestaña, `onCatalogShown()` (`refreshIfStale`). `TargetPickerViewModel` gana `catalog: CatalogBrowser`. | En Básico quedan "Recetas" y "Recomendadas" (§3.8). |
| D39 | Ajustes (concreta §3.5 y §3.8) | Se quitan la tarjeta de recetas de `SettingsScreen`, el parámetro `onRestoreRecipes`, `ExpertFeature.RESTORE_BUILT_INS` y los recursos `settings_recipes_*`. | §3.5. `ExperienceModeTest` lista los valores de `ExpertFeature` y se actualiza. |
| D40 | Recorrido (concreta §3.8; P2 resuelta) | `Tours.recipes`: el primer paso pasa a `TourStep("recipes.catalog", "recipes_catalog_segment", R.string.tour_recipes_catalog_title, R.string.tour_recipes_catalog_body)`. Se borran `tour_recipes_builtin_*`, el `TourLazyAnchor("recipe_builtin_tag", …)` de `RecipesScreen` y la etiqueta `recipe_builtin_tag` de `RecipeRow`. | Con un id nuevo, quien ya vio el recorrido de Recetas ve solo este paso la próxima vez (`pendingSteps` excluye los vistos). El usuario aprobó el id nuevo el 2026-11-25 (P2). |
| D41 | Curva en Experto (concreta §3.8) | El detalle usa `ExpertFeature.RECIPE_CURVE_PREVIEW`. `Preview(form)` de `RecipeEditScreen` se divide: `@Composable internal fun RecipeCurvePreview(recipe: Recipe, modifier: Modifier = Modifier, testTag: String)` en `ui/recipes/RecipeCurvePreview.kt`, que usan el editor (`recipe_edit_preview`, mismo ancla) y el detalle (`catalog_detail_preview`). | Es "como en el editor" (§3.8), con la misma regla de modo. |
| D42 | Piezas compartidas con la importación (amplía) | `RecipeSummary`, `StageRow` y `GrindSection` salen de `RecipeImportScreen.kt` a `ui/recipes/RecipePreviewParts.kt` como `internal`. `GrindSection` recibe `resolution: GrindResolution, pick: GrindPick?, onPick: (GrindPick) -> Unit` en lugar de `RecipeImportUi.Ready`. | El detalle muestra lo mismo que la vista previa de importación; `RecipeImportScreenTest` protege el cambio. |

### 2.7 Plataforma, red y CI

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D43 | Manifiesto (concreta §3.9; lo confirma F0) | `INTERNET` sin `tools:node="remove"` y con el comentario del §3.9. `tools:node="remove"` en `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery`, `com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService` y `com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver`. `ACCESS_NETWORK_STATE` se queda. | Sin `TransportBackendDiscovery`, el runtime de datatransport no encuentra el backend `cct` y descarta cada evento antes de guardarlo o programar un envío. Quitar también el servicio y el receptor deja sin camino de subida aunque algo programe trabajo. `ACCESS_NETWORK_STATE` no da red y quitarlo podría provocar una `SecurityException` en código de datatransport que lo consulte. Si F0 muestra un fallo, el plan para y aplica la alternativa de F0. |
| D44 | Workflow (concreta §3.9; amplía) | `.github/workflows/catalog.yml`: `on.push.paths` y `on.pull_request.paths` = `catalog/**` y `.github/workflows/catalog.yml`; `permissions: contents: read`; `concurrency` por rama; `ubuntu-latest`, `timeout-minutes: 20`; pasos `actions/checkout@v7`, `actions/setup-java@v6` (Temurin 17), `gradle/actions/setup-gradle@v6` y `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.RecipeCatalogFileTest"`. | Las versiones de las acciones y del JDK son las de `release.yml`. Incluir el propio workflow hace que un cambio en él se pruebe. No necesita secretos. |
| D45 | README (concreta §3.9) | Raíz: una viñeta "Recetas recomendadas" en "Qué hace" y una sección "Privacidad y red": la app solo descarga `catalog/recipes.json` de `raw.githubusercontent.com` al abrir "Recomendadas" (como mucho una vez al día, o al deslizar); sin cuentas ni telemetría (la de ML Kit está desactivada); los datos del usuario no salen del teléfono salvo en la copia que él exporta; el escáner de QR usa Google Play services, que es otra app. `catalog/README.md`: formato del §3.1, reglas de validación, cómo añadir una receta, incrementar `revision`, no reutilizar claves, cuándo subir `schemaVersion`, el test local y el retraso de unos minutos de `raw.githubusercontent.com`. | §3.9. |
| D46 | Comprobación de la red (concreta §5; discrepancia 4) | **Emulador** (`emulator-5556`, imagen `google_apis` con root): cadena `pourline_out` en `iptables` y `ip6tables` para el UID de la app, que deja pasar `185.199.108.0/22` (y `2606:50c0::/32` en IPv6) y cuenta y registra (`LOG --log-prefix "pourline_other "`) todo lo demás que abre conexión (TCP `SYN` y UDP salvo el 53). Criterio: el contador "otro" sigue a 0 tras usar el OCR y descargar el catálogo. **Teléfono** (`22081212UG`, sin root; el agente instala la release firmada con `adb install -r`, autorizado en P3): `dumpsys package` sin componentes de datatransport, `dumpsys jobscheduler` sin trabajos de datatransport tras usar el OCR, y `dumpsys netstats detail` sin bytes nuevos del UID de la app tras usar el OCR sin abrir "Recomendadas". | Solo con root se atribuye el tráfico a un destino. En el teléfono, cero bytes del UID mientras se usa el OCR demuestra que ML Kit no envía nada. Las consultas DNS las hace el sistema y no envían datos. |

### 2.8 Pruebas

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D47 | Recetas en los tests instrumentados (amplía) | `TestAppDependencies` gana `seedRecipes: Boolean = true`, que inserta con `recipes.save` las dos recetas de `androidTest/.../recipe/TestRecipes.kt` (nuevo): `fun v60Classic(texts: RecipeTexts): Recipe` y `fun fourSix(texts: RecipeTexts): Recipe`, con los pasos de la antigua `BuiltInRecipes` y los textos de `texts`, como recetas propias (`catalogKey = null`). Los tests de instalación nueva pasan `seedRecipes = false`. | Mantiene sin cambios unos veinte tests que buscan "V60" (`SessionDriver`, `PrepareScreenTest`, `GrindBrewFlowTest`…). |
| D48 | Catálogo en los tests instrumentados (amplía) | `androidTest/.../recipe/catalog/TestRecipeCatalog.kt` (nuevo): `JSON` con cuatro recetas (una V60 con autor "Némo Pop", una AeroPress con un paso `ACTION`, una `OTHER` "Clever" y una con `grind.grinder` = `TestGrinderCatalog.KEY_UNO`) y `FakeCatalogFetcher` (resultados guionizados, por defecto `Failed`, y un contador de llamadas). `TestAppDependencies` gana `catalogJson: String = TestRecipeCatalog.JSON` y `catalogFetcher: CatalogFetcher = FakeCatalogFetcher()`, con la caché en `cacheDir/test_catalog` (borrada al empezar) y el validador de molinos de `TestGrinderCatalog`. | Los tests no dependen de la red ni del contenido real del catálogo. |
| D49 | Fixtures JVM (concreta) | `RecipeTestFixtures.v60Classic()` pasa a ser una `Recipe` literal igual a la que daba `BuiltInRecipes.recipe(V60_CLASSIC, FakeRecipeTexts())`, con `builtInKey = null` y `localizedNames = false`. `FourSixTest` compara con una lista literal de vertidos. `BackupFixtures.fullRows()` rellena los campos nuevos y pasa la receta de `"V60_CLASSIC"` a `builtInKey = null`, `catalogKey = "v60_classic"`. Se borra `BuiltInRecipesTest`. | `RecipeSharePayloadStabilityTest` compara bytes dorados del QR `v=1`: si cambiaran, el formato habría cambiado, y eso está fuera de alcance. Si falla, se para. |

## 3. Fases

Orden: F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → F9 → F10 → F11. F4 no depende de F2 ni de F3 y se puede
adelantar tras F1; F5 depende de F3 y F4; F6 de F4 y F5; F7 de F3 y F4; F8 de F5 y F7; F9 de F7 y F8; F10 de F3 (y de
F7 para probar la descarga desde la interfaz).

| Fase | Contenido | Tamaño | Estado |
|---|---|---|---|
| F0 | Línea base, dispositivos y telemetría de ML Kit | M | Hecha (sin commit de código) |
| F1 | Modelo, parser y conversión del catálogo (JVM) | M | Hecha |
| F2 | Contenido del catálogo, copia en el APK, test del repo y workflow | L | Hecha |
| F3 | Descarga, caché, repositorio y constante de la URL | M | Pendiente |
| F4 | Room 7 → 8, campos de receta y retirada de las recetas de fábrica | L | Pendiente |
| F5 | Molienda del catálogo, copia y referencia de catálogo en Preparar | M | Pendiente |
| F6 | Copia de seguridad con formato 2 | S | Pendiente |
| F7 | Pestaña Recetas: selector, Recomendadas y recorrido | L | Pendiente |
| F8 | Detalle, "Preparar" y "Guardar en mis recetas" | L | Pendiente |
| F9 | Selector de Preparar y editor (autor y fuente) | M | Pendiente |
| F10 | Red: manifiesto, telemetría y README | M | Pendiente |
| F11 | Verificación final y cierre | M | Pendiente |

Rutas abreviadas: `main/` es `app/src/main/kotlin/dev/josueamaya/pourline/`, `test/` es
`app/src/test/kotlin/dev/josueamaya/pourline/` y `androidTest/` es
`app/src/androidTest/kotlin/dev/josueamaya/pourline/`.

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app`, nunca con `./gradlew build`
ni con builds limpios.

- **Compilar:** `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`.
- **Tests JVM:** `./gradlew :app:testDebugUnitTest --tests "<clase>" [--tests "<clase>" ...]`, solo las clases de la
  fase.
- **Tests instrumentados:** `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<clases separadas por comas>`.
  Solo las clases del código nuevo o modificado por este spec. Nunca dos ejecuciones a la vez en el mismo emulador.
  `emulator-5554` y el teléfono del usuario no se usan para tests.
- **Sin ARTEMIS.** Tests JVM, tests instrumentados de Compose y adb directo (`uiautomator dump`, `input tap`,
  `screencap`, `dumpsys`, `iptables`).
- **Lint:** `./gradlew :app:lintDebug` en F0, F4, F7, F9, F10 y F11, sin errores ni avisos nuevos respecto a F0 y sin
  `MissingTranslation`, `ExtraTranslation` ni `UnusedResources` en las cadenas nuevas o retiradas.
- **Cadenas:** en `res/values/strings.xml` (es) y `res/values-en/strings.xml` (en). Lista en §3.1.
- **Base de datos:** solo el cambio 7 → 8 de F4. Cualquier otro cambio de esquema se para y se consulta.
- **Commits:** solo con permiso explícito del usuario. Si se permiten, uno por fase al cerrarla en verde, en
  Conventional Commits y en inglés, sin `Co-Authored-By` ni atribución, en `feat/recipe-catalog` (nunca en `main` ni
  `develop`) y sin `keystore.properties`, `local.properties` ni ningún archivo de credenciales. El del plan:
  `docs(specs): add the SPEC-012 implementation plan`.

### F0. Línea base, dispositivos y telemetría de ML Kit

**Objetivo:** conocer el punto de partida y decidir, con pruebas, cómo se desactiva la telemetría de ML Kit (§3.9,
pregunta abierta 2 del spec). No deja cambios de código.

**Pasos:**

1. `adb devices -l`: tiene que estar `emulator-5556`. Si no está, se pide al usuario que lo arranque o que indique
   otro. `adb -s emulator-5556 root` tiene que funcionar (imagen `google_apis`); si no, se anota y el paso 5 usa el
   plan B.
2. JVM, sin cambios: `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.*" --tests "dev.josueamaya.pourline.backup.*" --tests "dev.josueamaya.pourline.session.ScaleSessionManagerTargetTest" --tests "dev.josueamaya.pourline.session.GrindSelectionTest" --tests "dev.josueamaya.pourline.grinder.GrindCardStateTest" --tests "dev.josueamaya.pourline.settings.SettingsSnapshotTest" --tests "dev.josueamaya.pourline.ui.tour.ToursCatalogTest"`.
3. `emulator-5556`, con el prefijo `dev.josueamaya.pourline.`: `history.HistoryMigrationTest`,
   `recipe.RecipeDaoTest`, `ManifestPermissionsTest`, `ui.recipes.RecipesScreenTest`,
   `ui.recipes.RecipeEditScreenTest`, `ui.recipes.RecipeImportScreenTest`, `ui.scale.TargetPickerTest`,
   `ui.SettingsScreenTest`, `ui.tour.RecipesHistoryTourTest`, `ui.prepare.PrepareScreenTest`,
   `ui.grinders.GrindCardTest`, `backup.BackupManagerTest` y `coffee.label.MlKitLabelOcrTest`. Se anotan los que ya
   fallan (en planes anteriores: `SettingsNavigationTest` y casos intermitentes de `SettingsScreenTest`).
4. `./gradlew :app:lintDebug`: errores y avisos por id (en SPEC-011 eran 2 errores `StringFormatInvalid` de
   `four_six_split_*` y 22 avisos).
5. **Telemetría, control positivo.** Cambio local sin commitear: se quita `tools:node="remove"` de `INTERNET`.
   `ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug :app:installDebugAndroidTest`. UID:
   `adb -s emulator-5556 shell cmd package list packages -U dev.josueamaya.pourline`. Cadena `pourline_out` de D46
   (`iptables -N`, `-I OUTPUT -m owner --uid-owner <uid> -j pourline_out`, `RETURN` para `185.199.108.0/22`, `LOG` y
   `RETURN` para el resto; lo mismo con `ip6tables`). OCR con la app instalada (no con `connectedDebugAndroidTest`,
   que desinstala al terminar): `adb -s emulator-5556 shell am instrument -w -e class dev.josueamaya.pourline.coffee.label.MlKitLabelOcrTest dev.josueamaya.pourline.test/dev.josueamaya.pourline.PourlineTestRunner`.
   Después: `adb shell dumpsys jobscheduler | grep -B2 -A12 "dev.josueamaya.pourline"` (trabajo de
   `JobInfoSchedulerService`), forzarlo con `adb shell cmd jobscheduler run -f dev.josueamaya.pourline <id>`, esperar
   un minuto y leer `iptables -L pourline_out -v -n -x`, `dmesg | grep pourline_other` y
   `logcat -d | grep -i -E "TRuntime|datatransport|firebaselogging"`. Se anota si ML Kit envía algo y a dónde.
6. **Mecanismo (D43).** Mismo cambio local más `tools:node="remove"` en los tres componentes de datatransport. Se
   reinstala, se ponen a cero los contadores (`iptables -Z pourline_out`) y se repite el paso 5. Se espera: el OCR
   pasa, no hay trabajo de datatransport en `jobscheduler`, `logcat` dice que el backend `cct` no está registrado, y
   el contador "otro" sigue a 0. Se comprueba también el manifiesto fusionado de release
   (`app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`, con
   `./gradlew :app:processReleaseManifest`): sin los tres componentes.
7. **Si el paso 6 falla** (excepción en el OCR o tráfico a otro destino): plan B, `exclude(group =
   "com.google.android.datatransport", module = "transport-backend-cct")` en `configurations.configureEach` de
   `app/build.gradle.kts`, repitiendo el paso 6 (atento a `NoClassDefFoundError`). Si tampoco sirve, se para y se
   pregunta al usuario.
8. Se deshacen los cambios locales (`git checkout -- app/src/main/AndroidManifest.xml app/build.gradle.kts`), se
   borra la cadena (`iptables -D OUTPUT …`, `-F`, `-X`) y `adb -s emulator-5556 shell pm clear dev.josueamaya.pourline`.

**Salida:** la línea base y el resultado de los pasos 5 a 7 en la cabecera ("Línea base") y, si el mecanismo cambia,
en D43. Sin commit de código.

**Resultado de F0 (2026-11-25).** `emulator-5556` no estaba arrancado; el agente lo lanzó en segundo plano
(`emulator -avd SG_API34 -port 5556 -no-snapshot-save -no-audio`, imagen `google_apis` arm64, Android 14) y `adb root`
funcionó. La imagen no trae el módulo `LOG` de `iptables` ("unknown option --log-prefix"), así que la cadena
`pourline_out` no registra: el criterio "otro" son los contadores de las reglas finales (`tcp --syn`, `udp` y el resto), que
se comprueban con `-L -v -n -x`, más `/proc/net/tcp*` filtrado por UID. La cadena cubre los UID 10220 (app) y 10221
(APK de test).

- **Control positivo** (`INTERNET` sin `tools:node="remove"`, nada más): el OCR (`MlKitLabelOcrTest`, 3 tests) guarda
  eventos `FIREBASE_ML_SDK` en `TransportRuntime.SQLiteEventStore` para el destino `cct`, programa `JobInfoSchedulerService`
  (trabajo de datatransport en `dumpsys jobscheduler`) y el UID de la app abre una conexión TCP a `172.217.118.4:443`
  (Google), contada en el contador "otro". Es decir, con el permiso de vuelta ML Kit envía telemetría.
- **Mecanismo D43** (además, `tools:node="remove"` en `TransportBackendDiscovery`, `JobInfoSchedulerService` y
  `AlarmManagerSchedulerBroadcastReceiver`; `pm clear` antes): el OCR pasa (3 tests), `logcat` dice `Transport backend
  'cct' is not registered`, `dumpsys jobscheduler` no tiene trabajos del UID, los contadores de `iptables` e `ip6tables`
  quedan a 0 y no hay conexiones TCP del UID. Los manifiestos fusionados de debug y release no contienen
  `datatransport`. **D43 queda confirmado tal cual; no hace falta el plan B** (`exclude` de `transport-backend-cct`).
- Los cambios locales se deshicieron (`git checkout`); el diff del manifiesto se conserva para F10.

### F1. Modelo, parser y conversión del catálogo

**Objetivo:** el contrato del §3.1 y la conversión del §3.4 en Kotlin puro, con sus tests, y los campos nuevos de
`Recipe` con su validación.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `main/recipe/Recipe.kt` | `Recipe` gana `author`, `sourceUrl`, `catalogKey` (D6). |
| `main/recipe/RecipeValidator.kt` | `MAX_AUTHOR`, `MAX_SOURCE_URL`, `RecipeError.AuthorTooLong`, `RecipeError.SourceUrlInvalid`, `fun isValidSourceUrl(url: String): Boolean` (D6). |
| `main/recipe/catalog/CatalogModels.kt` (nuevo) | `CatalogLanguage`, `LocalizedText`, `CatalogGrinderRef(catalogKey: String, value: Double)`, `CatalogRecipe(key, name, author, context, method: BrewMethod, methodOther, doseG, waterTempC, grindText, grinderRef, notes, sourceUrl, stages: List<RecipeStage>)`, `CatalogRecipeEntry`, `ParsedCatalog`, `CatalogParseResult`, `DiscardedRecipe` (D3, D5). |
| `main/recipe/catalog/RecipeCatalogDtos.kt` (nuevo) | `internal` `@Serializable`: `RecipeCatalogLocalizedDto`, `RecipeCatalogGrinderRefDto`, `RecipeCatalogGrindDto`, `RecipeCatalogStageDto` (`type: String`, `startS: Int`, `durationS: Int`, `waterG: Double?`, `name`, `instruction`), `RecipeCatalogRecipeDto` (`method: String`) y `CatalogJson` (D2). |
| `main/recipe/catalog/RecipeCatalogParser.kt` (nuevo) | `RecipeCatalogParser.parse(text, grinders)` (D2, D4, D5). |
| `main/recipe/catalog/CatalogConversion.kt` (nuevo) | `CatalogRecipeEntry.inLanguage(language)` y `CatalogRecipe.toRecipe()` (D8). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/catalog/CatalogTestJson.kt` (nuevo) | Constructores de JSON de prueba (`catalogJson(vararg recipes)`, `recipeJson(...)` con valores por defecto válidos) y `testGrinders()` (un molino `comandante-c40` de 0 a 40 clics). |
| `test/.../recipe/catalog/RecipeCatalogParserTest.kt` (nuevo) | `` `a valid catalog is parsed with its revision and recipes in file order` ``. `` `a newer schema version is reported before reading anything else` ``. `` `a root without schema version, revision or recipes is corrupt` `` (y un texto que no es JSON). `` `an unknown method discards only that recipe` ``. `` `an unknown stage type discards only that recipe` ``. `` `a missing language discards the recipe` `` (en `name`, `notes`, `methodOther` y un `instruction`). `` `a recipe that fails the validator is discarded` `` (agua que baja, dosis 0, instrucción de 201 caracteres, solapes). `` `method other requires its text and other methods reject it` ``. `` `water is required in pours and rejected elsewhere` ``. `` `an unknown grinder or a value out of its scale discards the recipe` ``. `` `a source url must be https and an author requires a source` ``. `` `duplicated keys discard every recipe with that key` ``. `` `unknown fields are ignored at every level` `` (raíz, receta, paso, molienda, texto localizado con `"fr"`). `` `a catalog without any valid recipe is corrupt` ``. `` `each discarded recipe reports its index, key and reason` ``. |
| `test/.../recipe/catalog/CatalogConversionTest.kt` (nuevo) | `` `stage times become milliseconds and stages are sorted by start` ``. `` `non pour stages take the water of the previous pour` `` (un `ACTION` tras el último vertido, como la dilución de AeroPress). `` `texts come in the chosen language` `` (`ES` y `EN`, también los pasos). `` `author, source url and catalog key are copied` ``. `` `the recipe is new, not built in and not localized` `` (`id = 0`, `builtInKey = null`, `localizedNames = false`, `grind = null`). `` `totals come from the recipe` `` (`totalWaterG`, `ratio`, `totalDurationMs`). |
| `test/.../recipe/RecipeValidatorTest.kt` | Casos nuevos: `` `an author longer than the limit is rejected` ``, `` `a source url must be http or https with a host and no spaces` `` (`http://a.b`, `HTTPS://a.b/c` válidas; `ftp://a.b`, `https://`, `https://a b`, 501 caracteres no válidas), `` `blank author and source are not errors` ``. |

**Verificación:** compilar. `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.*" --tests "dev.josueamaya.pourline.recipe.RecipeValidatorTest" --tests "dev.josueamaya.pourline.recipe.RecipeTest"`.
`grep -n "^import android\." app/src/main/kotlin/dev/josueamaya/pourline/recipe/catalog/*.kt` no encuentra nada.

Commit sugerido: `feat(catalog): add the recipe catalog model, parser and conversion`.

### F2. Contenido del catálogo, copia en el APK, test del repo y workflow

**Objetivo:** `catalog/recipes.json` con el contenido del §3.10, su copia generada en el APK, el test del repo que lo
valida y el workflow que lo ejecuta.

**Pasos de contenido:**

1. Recetas 1 a 15, 17 a 25 y 27 a 35 desde el anexo (sus tablas, con las "Decisiones del spec que prevalecen"):
   `four_six` con esa clave; `v60_classic` sin autor ni fuente, con los pasos de la antigua `BuiltInRecipes` (15 g;
   vertidos hasta 40, 150 y 250 g y sus esperas) y los nombres e instrucciones de `recipe_builtin_v60_classic_*` en
   los dos idiomas; `grind.grinder` solo en `merikanto_wac_2021` (`comandante-c40`, 27) y `fukahori_switch`
   (`comandante-c40`, 25).
2. **Dilución de AeroPress** (`van_bunnik_wac_2019`, `stanica_wac_2024`, `wipvasutt_wac_2023`, `nemo_pop_wac_2025`
   y, si entra, `ibarra_wac_2018`): el vertido final de dilución de las tablas del anexo pasa a ser un paso `ACTION`
   con los gramos y la temperatura en la instrucción ("Añade 60 g de agua a 96 °C" / "Add 60 g of water at 96 °C"),
   sin `waterG`. La curva termina en el agua de infusión; las notas dan el ratio final. El bypass de Némo Pop va al
   final, como dice el anexo.
3. **Reservas** (`origami_kopikalyan`, `ibarra_wac_2018`, `five_senses_clever`): se completan sus pasos desde la
   fuente del §3.10 (con `WebFetch`), marcando lo inferido. Si una fuente no da los datos o no se puede leer, la
   receta se queda fuera y no se repone (§3.10). Lo completado y lo inferido se anota en este plan ("Resultado de
   F2"); el anexo no se toca sin permiso del usuario.
4. Textos en `es` y `en` en todos los campos localizados; `instruction` ≤ 200 caracteres por idioma; `revision: 1`.
   Orden del archivo: el de la tabla del §3.10.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `catalog/recipes.json` (nuevo) | El catálogo. |
| `catalog/README.md` (nuevo) | Mantenimiento (D45). |
| `app/build.gradle.kts` | `abstract class CopyRecipeCatalogTask : DefaultTask()` (`@get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val catalogFile: RegularFileProperty`, `@get:OutputDirectory abstract val outputDir: DirectoryProperty`, `@TaskAction` que copia a `outputDir/catalog/recipes.json`); `val copyRecipeCatalog = tasks.register<CopyRecipeCatalogTask>("copyRecipeCatalog") { catalogFile.set(rootProject.layout.projectDirectory.file("catalog/recipes.json")); outputDir.set(layout.buildDirectory.dir("generated/recipeCatalog")) }`; `androidComponents { onVariants { it.sources.assets?.addGeneratedSourceDirectory(copyRecipeCatalog, CopyRecipeCatalogTask::outputDir) } }`. Si AGP 9 no admite `addGeneratedSourceDirectory` para `assets`, alternativa: `android.sourceSets.getByName("main").assets.srcDir(copyRecipeCatalog.flatMap { it.outputDir })` (R6). |
| `.github/workflows/catalog.yml` (nuevo) | D44. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/catalog/RecipeCatalogCheck.kt` (nuevo) | `catalogProblems(text, grinderCatalog)` (D7). |
| `test/.../recipe/catalog/RecipeCatalogFileTest.kt` (nuevo) | `` `the repository catalog is valid in both languages` `` (`catalogProblems(...)` vacío; si no, el mensaje lista cada problema). `` `an invalid recipe makes the catalog check fail` `` (el catálogo del repo con una receta añadida de agua decreciente, otra con método `"SIPHON"` y una clave repetida: `catalogProblems` nombra las tres). Este segundo caso es el que demuestra que el workflow falla con una receta inválida sin romper `main` (§5). |

**Verificación:**

- Compilar. `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.*"`.
- Contenido del §3.10: `jq -r '.recipes[] | [.key, .method, (.author // "-")] | @tsv' catalog/recipes.json` (o
  `python3 -I` si no hay `jq`) contra la tabla del §3.10: claves, métodos, autores y reparto (V60 9, Kalita 3, Origami
  4, Chemex 1, prensa 2, AeroPress 8, Otro 8, menos las reservas que queden fuera). Los pasos de cada receta, contra
  el anexo.
- Copia en el APK: `./gradlew :app:assembleDebug`;
  `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep assets/catalog/recipes.json` y
  `unzip -p app/build/outputs/apk/debug/app-debug.apk assets/catalog/recipes.json | cmp - catalog/recipes.json`.
  `git status` no muestra una segunda copia en `app/src/main/assets/`.
- Workflow: `actionlint` si está instalado; si no, revisión manual de la sintaxis. Con F2 commiteada (los commits
  siguen la regla general), `git push -u origin feat/recipe-catalog` (autorizado, P4) y
  `gh run list --workflow catalog.yml --branch feat/recipe-catalog`: el workflow se ejecuta y pasa (§7.2).

Commit sugerido: `feat(catalog): add the recommended recipe catalog with its repository test and workflow`.

**Resultado de F2 (2026-11-25).**

- **Contenido:** 35 recetas en el orden de la tabla del §3.10 (V60 9, AeroPress 8, Otro 8, Origami 4, Kalita Wave 3,
  prensa francesa 2, Chemex 1; coincide con el reparto del spec): las tres reservas se completaron y entran. Los textos,
  nombres y datos de las 31 recetas del anexo salen de sus tablas con un script desechable que no se commitea; las
  instrucciones en inglés, los contextos, las moliendas y las notas de cada receta están escritos a mano en `es` y `en`.
  `v60_classic` conserva los pasos y los textos de la antigua receta de fábrica. Solo `merikanto_wac_2021` y
  `fukahori_switch` llevan `grind.grinder` (`comandante-c40`, 27 y 25 clics); `merikanto_wac_2021` omite `grind.text` y
  su molienda de competición (Macap Labo 70D a 7,0) va en las notas.
- **Dilución de AeroPress:** los vertidos de dilución pasaron a pasos `ACTION` con gramos (y la temperatura que da la
  fuente) en `van_bunnik_wac_2019` (dos pasos: 100 g y "al gusto"), `stanica_wac_2024` (55 g tibia y 25 g ambiente),
  `wipvasutt_wac_2023` (40 g ambiente y 40 g caliente) y `nemo_pop_wac_2025` (70 g a 50 °C, al final). Las notas dan el
  ratio final (1:7,3, 1:10, 1:10 y 1:9,4).
- **Reservas, completadas con `WebFetch` el 2026-11-25:**
  - `origami_kopikalyan` (fuente: Origami, "con KOPIKALYAN TOKYO vol. 3"): 18 g, 300 g, 92 °C, Origami Air S con filtro
    cónico, cinco vertidos cada 30 s con 40, 60, 60, 60 y 80 g [fuente]. **Inferido:** la duración de cada vertido (10 s)
    y el drawdown hasta 3:00 (la fuente no da el tiempo final). La molienda ("24~5 clics" de Comandante) es ambigua: va
    solo como texto "24-25 clics", sin `grind.grinder`. Va en las notas.
  - `ibarra_wac_2018` (fuente: aeropress.com, WAC): 34,9 g, rueda 8/10, 85 °C, invertida, vertido de 100 g durante 30 s,
    remover 30 s con palillos, tapa, voltear y presionar 30 s (total 1:30) y 60 g a 85 °C más 40 g a temperatura ambiente
    [fuente]. **Inferido:** la posición exacta del paso de dilución (a 1:30, 10 s) y que la dilución se hace tras
    presionar. Ratio final 1:5,7 (200 g) en las notas.
  - `five_senses_clever` (fuente: Five Senses Coffee): 24 g, 400 g, ~94 °C, Baratza Encore a 18-22, romper la costra a
    1:00 con 3 pasadas y drenar a 4:00 [fuente]. **Inferido:** la duración del vertido (30 s) y del drenado (60 s, el
    último paso termina hacia 5:05). La molienda va como texto, sin `grind.grinder` (rango, no un valor).
  Ninguna se quedó fuera. El anexo no se ha tocado.
- **Build:** `CopyRecipeCatalogTask` + `androidComponents.onVariants { sources.assets.addGeneratedSourceDirectory }`
  funciona con AGP 9.0.1 (R6 no se materializa; no hace falta la alternativa de `sourceSets`). `assembleDebug` deja
  `assets/catalog/recipes.json` idéntico a `catalog/recipes.json` (`cmp`) y no hay segunda copia en `app/src/main/assets`.
- **Workflow:** `actionlint` no está instalado: revisión manual de la sintaxis. Resultado de su ejecución en GitHub:
  ver "Push de la rama" más abajo.

### F3. Descarga, caché, repositorio y constante de la URL

**Objetivo:** obtener, guardar y elegir el catálogo según el §3.3 y el §4, probado en la JVM, y conectado a la app sin
mostrarlo todavía.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `app/build.gradle.kts` | `buildConfig = true` y `RECIPE_CATALOG_URL` (D9). |
| `main/recipe/catalog/CatalogFetcher.kt` (nuevo) | `CatalogFetcher`, `FetchResult`, `HttpCatalogFetcher` (D10). |
| `main/recipe/catalog/CatalogCache.kt` (nuevo) | `CatalogCache`, `CachedCatalog(text: String, downloadedAtMs: Long?, etag: String?)`, `CatalogCacheUnreadable` (D11). |
| `main/recipe/catalog/CatalogPolicy.kt` (nuevo) | `object CatalogPolicy { const val MAX_AGE_MS = 24 * 60 * 60 * 1000L; fun isStale(...); fun choose(...) }` (D12, D13). |
| `main/recipe/catalog/RecipeCatalogRepository.kt` (nuevo) | `RecipeCatalogRepository`, `CatalogState`, `CatalogError` (D14, D15). |
| `main/recipe/catalog/ResourcesCatalogLanguage.kt` (nuevo) | `CatalogLanguageSource` y `ResourcesCatalogLanguage` (D16). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `catalog_language` (`es` / `en`). |
| `main/AppDependencies.kt` | Parámetros `recipeCatalog: RecipeCatalogRepository` y `catalogLanguage: CatalogLanguageSource`. |
| `main/PourlineApp.kt` | `RecipeCatalogRepository(AssetCatalogSource(this, "catalog/recipes.json"), CatalogCache(File(filesDir, "catalog")), HttpCatalogFetcher(BuildConfig.RECIPE_CATALOG_URL), grinders = { <mapa por catalogKey de GrinderCatalogFile.parse(AssetCatalogSource(this).read())> }, scope = applicationScope)`, perezoso. Nada lo llama todavía. |
| `androidTest/.../recipe/catalog/TestRecipeCatalog.kt` (nuevo) | `JSON` y `FakeCatalogFetcher` (D48). |
| `androidTest/.../ui/TestAppDependencies.kt` | `catalogJson`, `catalogFetcher` y el repositorio de prueba (D48). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/catalog/CatalogPolicyTest.kt` (nuevo) | `` `a download older than 24 hours or missing is stale` `` (23 h 59 min no, 24 h sí, `null` sí, reloj hacia atrás sí). `` `the higher revision wins and a tie keeps the cache` ``. |
| `test/.../recipe/catalog/CatalogCacheTest.kt` (nuevo) | `` `a written catalog is read back with its time and etag` ``. `` `writing replaces the files without leaving temporary ones` ``. `` `an unreadable cache is reported and cleared` `` (directorio en lugar de archivo). `` `marking a check keeps the text and updates the time and etag` ``. |
| `test/.../recipe/catalog/HttpCatalogFetcherTest.kt` (nuevo) | Con `com.sun.net.httpserver.HttpServer` en un puerto local: `` `a 200 returns the body and its etag` ``. `` `the etag is sent and a 304 is not modified` ``. `` `an http error, a refused connection or a timeout fail` `` (tiempo de espera de 200 ms en el test). `` `a body over the limit fails` ``. `` `only the accept, user agent and etag headers are sent` `` (`User-Agent: Pourline`; ninguna cabecera con el modelo, el idioma o la versión de Android). |
| `test/.../recipe/catalog/RecipeCatalogRepositoryTest.kt` (nuevo) | Con `runTest`, caché en `TemporaryFolder` y un `CatalogFetcher` falso: `` `the bundled copy is used until something newer is downloaded` ``. `` `a downloaded catalog with a higher revision replaces the bundled one` ``. `` `an app update with a newer bundled catalog beats an older cache` ``. `` `a fresh download is skipped within 24 hours and forced by refresh` ``. `` `a corrupt download keeps the cache and reports the failure` ``. `` `a catalog without valid recipes does not replace the cache` ``. `` `a newer schema keeps the cache and asks to update the app` ``. `` `an unreadable cache falls back to the bundled copy and is deleted` ``. `` `two refreshes at the same time make a single request` ``. `` `a not modified answer updates the time without an error` ``. `` `entry waits for the first load and then emits null for an unknown key` ``. |

**Verificación:** compilar. `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.*"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipesScreenTest`
(humo: `TestAppDependencies` sigue construyendo la app).

Commit sugerido: `feat(catalog): download, cache and pick the recipe catalog`.

### F4. Room 7 → 8, campos de receta y retirada de las recetas de fábrica

**Objetivo:** el esquema 8 con su migración, los campos nuevos persistidos y las recetas de fábrica retiradas (§3.5),
sin romper los tests que las usaban.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `main/recipe/RecipeEntities.kt` | `RecipeEntity` gana `author`, `sourceUrl`, `catalogKey` (D17). |
| `main/history/BrewEntities.kt` | `BrewEntity` gana `referenceKey` (D17). |
| `main/history/HistoryDatabase.kt` | `VERSION = 8`; `migration7To8(texts)` en `create`; sin `seedOnCreate`; `inMemory(context)` sin `texts` (D18). KDoc. |
| `main/history/Migrations.kt` | `migration7To8(texts)` (D17); `migration1To2` usa `LegacyBuiltIns.seedV2`. |
| `main/history/LegacyBuiltIns.kt` (nuevo) | D18. |
| `main/recipe/BuiltInRecipes.kt`, `main/recipe/BuiltInRecipeSeeder.kt` | Se borran. |
| `main/recipe/RecipeTexts.kt`, `main/recipe/ResourcesRecipeTexts.kt` | KDoc: los textos de fábrica solo los usan las migraciones (D18). |
| `main/recipe/RecipeDao.kt`, `main/recipe/RecipeRepository.kt` | D19. |
| `main/ui/recipes/RecipeRow.kt`, `main/ui/recipes/RecipesScreen.kt` | Sin la etiqueta "Predefinida" ni su `TourLazyAnchor` (D40). |
| `main/ui/recipes/RecipeEditForm.kt`, `main/ui/recipes/RecipeEditViewModel.kt`, `main/ui/recipes/RecipeImportViewModel.kt` | `toRecipe(id, catalogKey)` y sin `keepsLocalizedNames` (D20, solo la firma; los campos del editor llegan en F9). |
| `main/ui/settings/SettingsScreen.kt`, `main/Navigation.kt`, `main/settings/ExperienceMode.kt` | Sin la tarjeta de restaurar ni `RESTORE_BUILT_INS` (D39). |
| `main/ui/tour/Tours.kt` | Se quita temporalmente el paso `recipes.builtin` (vuelve como `recipes.catalog` en F7). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | Se borran `recipe_builtin_tag`, `settings_recipes_*` y `tour_recipes_builtin_*`. Se quedan los `recipe_builtin_*` de nombres y pasos. |
| `app/schemas/dev.josueamaya.pourline.history.HistoryDatabase/8.json` (nuevo, generado) | Esquema 8. |
| `androidTest/.../recipe/TestRecipes.kt` (nuevo), `androidTest/.../ui/TestAppDependencies.kt` | D47. |
| Tests que usaban las recetas de fábrica | JVM: `RecipeTestFixtures`, `FourSixTest`, `BrewRepositoryMappingTest`, `RecipeShareFixtures`, `RecipeShareCodecTest`, `RecipeSharePayloadStabilityTest`, `SimulatedTargetPourTest`, `BrewKeepAliveTest`, `BrewPlanTest`, `BrewDialTest`, `SessionFormatTest`, `RecipeEditFormTest`, `ExperienceModeTest`, `ToursCatalogTest`; se borra `BuiltInRecipesTest`. Instrumentados: `HistoryMigrationTest`, `RecipeDaoTest`, `BrewDaoTest`, `TestBrews`, `BackupDaoTest`, `RecipesScreenTest`, `RecipeEditScreenTest`, `RecipeImportScreenTest`, `RecipeShareTest`, `ShareCardsLayoutTest`, `TargetPickerTest`, `SettingsScreenTest`, `RecipesHistoryTourTest`, y los 10 que llaman a `HistoryDatabase.inMemory(context, texts)`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../history/HistoryMigrationTest.kt` | `aFreshInstallIsCreatedAtVersion7WithTheBuiltIns` pasa a `aFreshInstallIsCreatedAtVersion8WithoutRecipes` (0 recetas, versión 8). Nuevos: `migrates7To8AddsTheNewColumnsAsNull`; `migrates7To8TurnsTheBuiltInsIntoOwnRecipesWithTranslatedTexts` (filas de fábrica con `localizedNames = 1` y textos de otro idioma: quedan con el nombre, los nombres de paso y las instrucciones de `FakeRecipeTexts`, `localizedNames = 0`, `builtInKey = NULL`, `catalogKey` = la clave y el mismo `id`); `migrates7To8KeepsTheTextsOfAnEditedBuiltIn` (`localizedNames = 0`: textos intactos, `catalogKey` puesto); `migrates7To8KeepsTheBrewsLinkedToTheFormerBuiltIns` (`referenceType = RECIPE`, `referenceId` igual, `referenceKey` nulo); `migrates1To8ThenRoomReadsEverything` (cadena completa con `LegacyBuiltIns`). Los demás casos que comprueban las predefinidas en versiones intermedias usan `LegacyBuiltIns`. |
| `androidTest/.../recipe/RecipeDaoTest.kt` | Se quitan los casos de `restoreBuiltIns` y `byBuiltInKey`. Nuevos: `authorSourceAndCatalogKeyRoundTrip`, `catalogKeysListsEachSavedCatalogKeyOnce`, `duplicateKeepsAuthorSourceAndCatalogKey`. |
| `androidTest/.../ui/recipes/RecipesScreenTest.kt` | Sin la etiqueta "Predefinida"; con `seedRecipes = false`, `aFreshInstallShowsAnEmptyList` (el enlace "Ver recomendadas" llega en F7). |
| `androidTest/.../ui/SettingsScreenTest.kt` | Se quitan los casos de restaurar; `theRecipesCardIsGoneInBothModes`. |
| `test/.../recipe/RecipeEditFormTest.kt` | Se quitan los casos de `keepsLocalizedNames`; `` `the catalog key is kept when saving` ``. |

**Verificación:**

- Compilar (genera `8.json`; se revisa que solo añade las cuatro columnas).
- JVM: `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.*" --tests "dev.josueamaya.pourline.history.*" --tests "dev.josueamaya.pourline.session.*" --tests "dev.josueamaya.pourline.sim.SimulatedTargetPourTest" --tests "dev.josueamaya.pourline.tools.FourSixTest" --tests "dev.josueamaya.pourline.ui.session.*" --tests "dev.josueamaya.pourline.ui.recipes.*" --tests "dev.josueamaya.pourline.settings.ExperienceModeTest" --tests "dev.josueamaya.pourline.ui.tour.ToursCatalogTest"`.
- Instrumentados: los de la tabla de archivos, más los que salgan de
  `grep -rln "HistoryDatabase.inMemory\|TestAppDependencies" app/src/androidTest | xargs grep -ln -i "recipe"` y no
  estén ya en la lista.
- `grep -rn "BuiltInRecipes\|BuiltInRecipeSeeder\|restoreBuiltIns\|RESTORE_BUILT_INS" app/src` no encuentra nada.
- `./gradlew :app:lintDebug`.

Commit sugerido: `feat(recipes)!: migrate the built-in recipes to own recipes and stop seeding them`, con un pie
`BREAKING CHANGE` que explica que la base pasa a la versión 8.

### F5. Molienda del catálogo, copia y referencia de catálogo en Preparar

**Objetivo:** las funciones puras de la molienda y de la copia (§3.6), y preparar una receta del catálogo sin
guardarla (§3.7), con la preparación guardada como `CATALOG`.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `main/recipe/catalog/CatalogGrind.kt` (nuevo) | `toSharedGrind` (D28), `fun catalogGrindResolution(recipe: CatalogRecipe, owned: List<Grinder>, catalogGrinder: Grinder?, calibrations: List<GrindCalibration>, texts: GrindTexts): GrindResolution`, `fun catalogSourceGrind(recipe: CatalogRecipe, catalogGrinder: Grinder?, texts: GrindTexts): GrindSetting?` y `fun GrindResolution.preferredGrinderId(): Long?` (D30). |
| `main/recipe/catalog/CatalogCopy.kt` (nuevo) | `catalogCopy` y `hasCopy` (D29). |
| `main/recipe/catalog/CatalogTargetSource.kt` (nuevo) | `CatalogTargetSource` (D23). |
| `main/settings/TargetSettings.kt`, `main/settings/SettingsSnapshot.kt` | `ReferenceKey.Catalog`, `target_ref_key`, `targetRefKey` (D21). |
| `main/history/HistoryModels.kt`, `main/history/BrewRepository.kt` | `ReferenceType.CATALOG`, `UsedTarget.id: Long?` y `key`, `referenceKey` (D22). |
| `main/session/TargetReferences.kt` | `catalog(key)`, `CatalogTarget`; `RepositoryTargetReferences(recipes, brews, catalog)` (D23). |
| `main/session/ScaleSessionManager.kt`, `main/session/ScaleUiState.kt` | `resolveCatalog`, `refId`/`refKey`, `preferredGrinderId` en `TargetBase` y `ActiveTarget` (D22, D23). |
| `main/grinder/GrindCardState.kt`, `main/session/GrindSelection.kt` | `GrindSource.preferredGrinderId` y `preselected()` (D30). `grindCardState` da la sugerencia del método, no `Orphan`, para el texto puro (D31). |
| `main/ui/grinders/GrindCard.kt`, `main/ui/grinders/GrindSettingText.kt` | Sin "molino eliminado" para el texto puro (D31); lo ven Preparar, el editor (`GrindSettingField`) y el detalle de la preparación. |
| `main/ui/history/BrewFactsCard.kt` | D24. |
| `main/PourlineApp.kt`, `androidTest/.../ui/TestAppDependencies.kt` | `RepositoryTargetReferences(…, CatalogTargetSource(…))`. |
| `test/.../session/ScaleTestFakes.kt` | `FakeTargetReferences.putCatalog(key, target)` y `catalog(key)`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/catalog/CatalogGrindTest.kt` (nuevo) | `` `the grind text adds the brand, model and setting of the catalog grinder` `` ("Media-fina · Comandante C40 MK4 · 27 clics"). `` `without a grinder the grind is only its text` ``. `` `the same grinder is linked when the user owns it` ``. `` `the first convertible grinder in the grinders order is preferred` `` (molinos A sin equivalencia y B convertible: B; con A predeterminado, sigue B). `` `without a convertible grinder there is no preferred one and the text is kept` ``. `` `the source grind points to the catalog grinder row` ``. |
| `test/.../recipe/catalog/CatalogCopyTest.kt` (nuevo) | `` `a copy with an owned grinder is linked to it with the same setting` ``. `` `a copy converted to another grinder uses the picked option` `` (y "Guardar como texto" con `GrindPick.AsText`). `` `a copy without a convertible grinder saves the grind as text` ``. `` `a copy without grind has no grind` ``. `` `a repeated name gets the next suffix` `` (" (2)" y, con " (2)" ya usado, " (3)"; sin distinguir mayúsculas ni espacios). `` `an existing copy is detected by catalog key` ``. `` `the copy keeps author, source and catalog key` ``. |
| `test/.../settings/SettingsSnapshotTest.kt` | `` `a catalog reference is stored with its key and read back` ``; `` `switching from a catalog to a recipe reference removes the key` ``; `` `a catalog type without key reads as no reference` ``; `` `the catalog key is exported and restored` ``. |
| `test/.../session/ScaleSessionManagerTargetTest.kt` | `` `a catalog reference resolves like a recipe with the catalog type` `` (nombre, curva, dosis nominal, método y temperatura). `` `a catalog key that disappears is removed with the usual error` ``. `` `a brew made with a catalog reference is saved with its key and no id` ``. |
| `test/.../grinder/GrindCardStateTest.kt` | `` `the preferred grinder is preselected after the source grinder and before the default` ``. `` `a text only grind gets the method suggestion instead of orphan` ``. `` `a deleted grinder with a setting is still an orphan` ``. |
| `test/.../session/GrindSelectionTest.kt` | `` `the preferred grinder of the target is preselected` ``. |
| `test/.../history/BrewRepositoryMappingTest.kt` | `` `a catalog target is stored with its key and name` ``. |
| `androidTest/.../ui/history/BrewDetailScreenTest.kt` | `theRecipeNameIsShownInTheFactsForCatalogReferences` (`BrewFactsCard` con `referenceType = CATALOG`); `aTextOnlyGrindIsShownWithoutTheDeletedTag`. |
| `androidTest/.../ui/grinders/GrindCardTest.kt` | `aTextOnlyGrindShowsTheSuggestionWithoutTheDeletedTag`; `aDeletedGrinderWithASettingKeepsTheDeletedTag`. |
| `androidTest/.../ui/recipes/RecipeEditScreenTest.kt` | `aRecipeWithATextOnlyGrindIsShownWithoutTheDeletedTag` (una receta importada "como texto", SPEC-009). |

**Verificación:** compilar. `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.*" --tests "dev.josueamaya.pourline.settings.SettingsSnapshotTest" --tests "dev.josueamaya.pourline.session.*" --tests "dev.josueamaya.pourline.grinder.GrindCardStateTest" --tests "dev.josueamaya.pourline.history.BrewRepositoryMappingTest"`.
Instrumentados: `ui.history.BrewDetailScreenTest`, `ui.grinders.GrindCardTest`, `ui.prepare.PrepareScreenTest`,
`ui.session.GrindBrewFlowTest`, `ui.recipes.RecipeEditScreenTest`, `ui.recipes.RecipeImportScreenTest`.

Commit sugerido: `feat(catalog): brew a catalog recipe without saving it`.

### F6. Copia de seguridad con formato 2

**Objetivo:** que la copia lleve los campos nuevos y que las copias anteriores se sigan importando (§3.5).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `main/backup/BackupFormat.kt` | `FORMAT_VERSION = 2` (D25). |
| `main/backup/BackupDtos.kt`, `main/backup/BackupMapping.kt` | Campos nuevos de `RecipeDto`, `BrewDto` y `SettingsDto` con su mapeo (D25, D26). |
| `main/backup/BackupLegacy.kt` (nuevo) | `withLegacyBuiltInsConverted` (D27). |
| `main/backup/BackupManager.kt` | La aplica a las copias con `formatVersion == 1` (D27). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../backup/BackupFixtures.kt` | `fullRows()` con `author`, `sourceUrl`, `catalogKey` y `referenceKey` (D49). |
| `test/.../backup/BackupMappingTest.kt` | La ida y vuelta existente cubre los campos nuevos. `` `the catalog fields and the reference key round trip` ``. `` `the catalog reference key setting round trips` ``. |
| `test/.../backup/BackupArchiveTest.kt` | `a newer format is rejected…` usa `FORMAT_VERSION + 1` en lugar de `2`. Nuevo: `` `a format 1 backup without the new fields is read with nulls` ``. |
| `test/.../backup/BackupLegacyTest.kt` (nuevo) | `` `a format 1 built in recipe becomes an own recipe with its catalog key` ``. `` `other recipes are untouched` ``. |
| `test/.../backup/BackupFormatTest.kt` | `FORMAT_VERSION == 2`. |
| `androidTest/.../backup/BackupManagerTest.kt` | `importsAFormat1BackupWithBuiltInsAsOwnRecipes`; `exportsAndImportsTheCatalogFields` (una receta copiada del catálogo, una preparación `CATALOG` y la referencia activa de catálogo). |

**Verificación:** compilar. `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.backup.*" --tests "dev.josueamaya.pourline.settings.SettingsSnapshotTest"`.
Instrumentados: `backup.BackupManagerTest`, `backup.BackupDaoTest`.

Commit sugerido: `feat(backup): carry the catalog fields in backup format 2`.

### F7. Pestaña Recetas: selector, Recomendadas y recorrido

**Objetivo:** el selector "Mis recetas / Recomendadas", el listado con búsqueda, filtro, avisos y deslizar para
actualizar, el estado vacío con "Ver recomendadas" y el paso del recorrido (§3.8).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `main/ui/components/AntSegmented.kt` | `itemModifier` (D32). |
| `main/recipe/catalog/CatalogFilter.kt` (nuevo) | `CatalogListItem(key, name, author, method, methodOther, doseG, totalWaterG, ratio, totalDurationMs, searchKey)`, `fun CatalogRecipe.toListItem()`, `filterCatalog`, `catalogMethods`, `fun catalogSearchKey(text: String): String` (D33). |
| `main/ui/recipes/catalog/CatalogBrowser.kt` (nuevo) | `CatalogBrowser` y `CatalogListUi(items, methods, query, method, savedKeys: Set<String>, notice: CatalogNotice?, lastDownloadMs, refreshing, loaded)` (D33, D34). |
| `main/ui/recipes/catalog/CatalogRecipeList.kt` (nuevo) | `CatalogSearchBar` (`AntInput`, `catalog_search`), `CatalogMethodFilter` (`AntSelect`, `catalog_method_filter`), `CatalogNoticeBar` (`catalog_notice`), `CatalogRecipeRow(item, saved, active, onClick)` (`catalog_row_<key>`, etiqueta `catalog_saved_tag`), estado sin resultados con "Quitar filtros" (`catalog_clear_filters`) y la lista con `PullToRefreshBox` (`catalog_list`). |
| `main/ui/recipes/RecipesViewModel.kt` | `segment`, `selectSegment`, `catalog: CatalogBrowser` (D32, D33). |
| `main/ui/recipes/RecipesScreen.kt` | `AntSegmented` arriba; "Mis recetas" como hoy; en su estado vacío, un botón de enlace "Ver recomendadas" (`recipes_empty_see_catalog`); "Recomendadas" con `CatalogRecipeList`; `LaunchedEffect(segment)` llama a `catalog.onShown()` con `CATALOG`; parámetro `onOpenCatalogRecipe: (String) -> Unit` (aún sin destino: lo conecta F8). |
| `main/ui/tour/Tours.kt` | Paso `recipes.catalog` (D40). |
| `main/Navigation.kt` | `RecipesViewModel(deps.recipeRepository, deps.recipeCatalog, deps.catalogLanguage)`. |
| `res/values*/strings.xml` | Cadenas de §3.1 de esta fase. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/catalog/CatalogFilterTest.kt` (nuevo) | `` `the search matches the name or the author without case or accents` `` ("nemo" encuentra "Némo Pop", "HOFFMANN" a "James Hoffmann"). `` `special letters are folded` `` ("boen" encuentra "Bøen"). `` `the method filter keeps only that method` ``. `` `search and method filter combine` ``. `` `the methods are those present in brew method order` ``. `` `the file order is kept` ``. |
| `androidTest/.../ui/recipes/RecipesCatalogScreenTest.kt` (nuevo) | `theSegmentSwitchesBetweenMyRecipesAndRecommended`; `theSegmentIsRememberedWhenComingBackToTheTab`; `aFreshInstallShowsTheEmptyListWithSeeRecommended` (`seedRecipes = false`; el enlace cambia el selector); `theRecommendedListShowsNameAuthorMethodSummaryAndTime`; `theMethodFilterShowsOnlyThatMethod`; `theSearchFindsByNameAndByAuthorWithoutAccents`; `noResultsShowsTheMessageAndClearFiltersRestoresTheList`; `aSavedCopyShowsTheSavedTag` (receta propia con el `catalogKey` de una del catálogo); `pullToRefreshForcesADownload` (el `FakeCatalogFetcher` cuenta una llamada más); `aFailedDownloadWithoutPreviousOneShowsUsingBundledRecipes`; `aNewerSchemaAsksToUpdateTheApp`; `showingRecommendedDownloadsOnlyWhenStale`. |
| `androidTest/.../ui/tour/RecipesHistoryTourTest.kt` | El primer paso del recorrido de Recetas tiene el título `tour_recipes_catalog_title` y su ancla es la opción "Recomendadas". |
| `test/.../ui/tour/ToursCatalogTest.kt` | Ids y anclas actualizados. |

**Verificación:** compilar. `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.CatalogFilterTest" --tests "dev.josueamaya.pourline.ui.tour.ToursCatalogTest"`.
Instrumentados: `ui.recipes.RecipesCatalogScreenTest`, `ui.recipes.RecipesScreenTest`,
`ui.tour.RecipesHistoryTourTest`, `ui.TabsNavigationTest`. `./gradlew :app:lintDebug`. Captura con `adb exec-out
screencap -p` de la lista en claro y en oscuro, en `emulator-5556`, para revisión visual.

Commit sugerido: `feat(recipes): browse the recommended recipes from the recipes tab`.

### F8. Detalle, "Preparar" y "Guardar en mis recetas"

**Objetivo:** el detalle de solo lectura con "Ver fuente", la curva en Experto, "Preparar" sin guardar y "Guardar en
mis recetas" con su molienda (§3.6 a §3.8).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `main/NavigationKeys.kt` | `CatalogRecipeDetail(key: String)` (D36). |
| `main/ui/recipes/RecipePreviewParts.kt` (nuevo), `main/ui/recipes/RecipeImportScreen.kt` | Piezas extraídas (D42). |
| `main/ui/recipes/RecipeCurvePreview.kt` (nuevo), `main/ui/recipes/RecipeEditScreen.kt` | D41. |
| `main/ui/recipes/SourceLink.kt` (nuevo) | `openSourceUrl` (D36). |
| `main/ui/recipes/catalog/CatalogRecipeDetailViewModel.kt` (nuevo) | D36, D29. |
| `main/ui/recipes/catalog/CatalogRecipeDetailScreen.kt` (nuevo) | `CatalogRecipeDetailScreen(viewModel, onBack, onPrepare: (String) -> Unit, openUrl: (String) -> Boolean, unit: WeightUnit)`: nombre, autor y contexto (`catalog_detail_author`, `catalog_detail_context`), método, dosis, agua total, ratio, temperatura, molienda (`GrindSection` si es `Convertible`; texto en los demás casos), tiempo total, notas, pasos (`StageRow`), "Ver fuente" (`catalog_detail_source`), aviso "Ya tienes una copia" (`catalog_detail_already_saved`), curva en Experto (`catalog_detail_preview`) y los botones "Preparar" (`catalog_detail_prepare`, principal) y "Guardar en mis recetas" (`catalog_detail_save`). Mensaje "Receta guardada en Mis recetas" con `AntMessage`. |
| `main/Navigation.kt` | Entrada `tabEntry<CatalogRecipeDetail>`; `onOpenCatalogRecipe = { stack.add(CatalogRecipeDetail(it)) }`; `onPrepare` según D37. |
| `res/values*/strings.xml` | Cadenas de §3.1 de esta fase. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/recipes/CatalogRecipeDetailScreenTest.kt` (nuevo) | `theDetailShowsEveryFieldAndTheSteps`; `viewSourceOpensTheUrl` (el `openUrl` de prueba recibe la URL); `withoutABrowserTheMessageIsShown` (`openUrl` devuelve `false`); `theCurveIsShownInExpertAndHiddenInBasic`; `saveCreatesACopyShowsTheMessageAndTheSavedTag` (vuelve a la lista: "Guardada"); `anExistingCopyShowsTheWarningAndStillSaves` (queda " (2)"); `theGrindPickerAppearsOnlyWithMoreThanOneOption` (molino del catálogo no propio y un molino propio convertible: selector; sin molinos: no); `aSavedCopyWithTheUserGrinderIsLinkedToIt`; `aRecipeRemovedFromTheCatalogShowsTheMissingState`. |
| `androidTest/.../ui/recipes/CatalogPrepareFlowTest.kt` (nuevo) | `prepareSelectsTheCatalogRecipeAndOpensPrepare` (Preparar muestra su nombre); `aBrewMadeFromTheCatalogIsSavedWithTheCatalogType` (sesión sin báscula con el reloj de `TestAppDependencies`: la preparación guardada tiene `referenceType = CATALOG`, `referenceKey` y `referenceName`; el detalle de la preparación muestra el nombre); `prepareUsesTheGrindConvertedToTheFirstCompatibleGrinder` (molinos propios A sin equivalencia y B convertible: "Moler con" = B y `grind_card_value` con el ajuste convertido); `aTextOnlyGrindShowsItsTextAndTheMethodSuggestion` (sin "molino eliminado"); `nothingIsAddedToMyRecipes`. |
| `androidTest/.../ui/recipes/RecipeImportScreenTest.kt`, `RecipeEditScreenTest.kt` | Sin cambios de casos: protegen la extracción de D41 y D42. |

**Verificación:** compilar. Instrumentados: `ui.recipes.CatalogRecipeDetailScreenTest`,
`ui.recipes.CatalogPrepareFlowTest`, `ui.recipes.RecipeImportScreenTest`, `ui.recipes.RecipeEditScreenTest`,
`ui.recipes.RecipesCatalogScreenTest`.

Commit sugerido: `feat(recipes): show a recommended recipe and brew or save it`.

### F9. Selector de Preparar y editor (autor y fuente)

**Objetivo:** la pestaña "Recomendadas" del `TargetPicker` en los dos modos y los campos "Autor" y "Fuente" del editor
(§3.5 y §3.8).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `main/ui/scale/TargetPicker.kt` | `PickerTab.CATALOG`, segmentado siempre, contenido de catálogo (D38). Parámetros nuevos `catalog: CatalogListUi`, `onCatalogQuery`, `onCatalogMethod`, `onCatalogClearFilters`, `onCatalogShown`. |
| `main/ui/scale/TargetPickerViewModel.kt` | `TargetPickerViewModel(recipeRepository, brewRepository, catalog: RecipeCatalogRepository, language: CatalogLanguageSource)` con `catalog: CatalogBrowser`. |
| `main/ui/prepare/PrepareScreen.kt`, `main/Navigation.kt` | Paso de los parámetros nuevos. |
| `main/ui/recipes/RecipeEditForm.kt`, `main/ui/recipes/RecipeEditViewModel.kt`, `main/ui/recipes/RecipeEditScreen.kt` | Campos, errores y "Ver fuente" (D20). |
| `res/values*/strings.xml` | Cadenas de §3.1 de esta fase. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/scale/TargetPickerTest.kt` | `theRecommendedTabIsShownInBasicWithRecipes` (dos opciones); `theRecommendedTabIsShownInExpertWithRecipesAndHistory` (tres); `pickingARecommendedRecipeSelectsACatalogReference`; `theRecommendedTabSearchesAndFilters`; `theActiveCatalogRecipeIsHighlighted`. |
| `androidTest/.../ui/prepare/PrepareScreenTest.kt` | `aCatalogRecipePickedInPrepareShowsItsName`. |
| `test/.../ui/recipes/RecipeEditFormTest.kt` | `` `author and source are optional` ``; `` `an author over the limit is an error` ``; `` `an invalid source is an error and http or https are valid` ``; `` `author and source round trip through the form` ``. |
| `androidTest/.../ui/recipes/RecipeEditScreenTest.kt` | `authorAndSourceAreSavedAndShownAgain`; `anInvalidSourceShowsTheErrorAndBlocksSaving`; `viewSourceIsShownForAValidSource`. |

**Verificación:** compilar. `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.ui.recipes.RecipeEditFormTest"`.
Instrumentados: `ui.scale.TargetPickerTest`, `ui.prepare.PrepareScreenTest`, `ui.recipes.RecipeEditScreenTest`.
`./gradlew :app:lintDebug`.

Commit sugerido: `feat(recipes): pick recommended recipes in prepare and edit recipe author and source`.

### F10. Red: manifiesto, telemetría y README

**Objetivo:** abrir la red con la telemetría de ML Kit desactivada (mecanismo de F0) y documentarlo (§3.9).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `app/src/main/AndroidManifest.xml` | D43 con el resultado de F0. |
| `app/build.gradle.kts` | Solo si F0 eligió el plan B. |
| `README.md` | D45. |
| `androidTest/.../ManifestPermissionsTest.kt` | `theAppRequestsNeitherInternetNorCamera` pasa a `theAppRequestsInternetButNotCamera`; nuevo `noDataTransportComponentIsDeclared` (`GET_SERVICES or GET_RECEIVERS`: ningún nombre empieza por `com.google.android.datatransport`). KDoc actualizado. |

**Verificación:**

- Compilar. Instrumentados: `ManifestPermissionsTest`, `coffee.label.MlKitLabelOcrTest`.
- `./gradlew :app:processDebugManifest :app:processReleaseManifest` y `grep -c datatransport` sobre los dos
  manifiestos fusionados: 0.
- `emulator-5556`, paso 5 de F0 con la app real (debug instalada, sin cambios locales): cadena `pourline_out`, OCR con
  `am instrument`, después la app abierta en Recetas → Recomendadas y deslizar para actualizar (con la red del
  emulador). El contador "otro" sigue a 0; el de GitHub sube (o, si el catálogo aún no está en `main`, la app muestra
  "No se pudo actualizar" y la conexión fue a `185.199.108.0/22`).
- `./gradlew :app:lintDebug`.

Commit sugerido: `feat(app): allow network access only for the recipe catalog`.

### F11. Verificación final y cierre

**Objetivo:** comprobar todos los criterios del §5 que no exigen al usuario y dejar preparada su prueba manual.

**Pasos:** los de §7 en orden, incluida la instalación de la release firmada en `22081212UG` y sus comprobaciones
de solo lectura (§7.6, autorizadas en P3); los resultados se anotan en este plan ("Resultado de F11"), con la línea
base de F0 para separar fallos previos de fallos nuevos. Estado de la cabecera actualizado.

Commit sugerido: `docs(specs): record the SPEC-012 verification`.

### 3.1 Cadenas nuevas (`values` / `values-en`)

| Clave | Español | Inglés | Fase |
|---|---|---|---|
| `catalog_language` | es | en | F3 |
| `recipes_segment_mine` | Mis recetas | My recipes | F7 |
| `recipes_segment_catalog` | Recomendadas | Recommended | F7 |
| `recipes_empty_see_catalog` | Ver recomendadas | See recommended | F7 |
| `catalog_search_placeholder` | Buscar por nombre o autor | Search by name or author | F7 |
| `catalog_filter_all` | Todos | All | F7 |
| `catalog_saved_tag` | Guardada | Saved | F7 |
| `catalog_no_results` | No hay recetas con estos filtros | No recipes match these filters | F7 |
| `catalog_clear_filters` | Quitar filtros | Clear filters | F7 |
| `catalog_update_failed` | No se pudo actualizar · Última actualización: %1$s | Could not update · Last updated: %1$s | F7 |
| `catalog_using_bundled` | Usando las recetas incluidas en la app | Using the recipes included in the app | F7 |
| `catalog_newer_schema` | Actualiza la app para ver las recetas nuevas | Update the app to see the new recipes | F7 |
| `catalog_last_update` | Última actualización: %1$s | Last updated: %1$s | F7 |
| `tour_recipes_catalog_title` | Recomendadas | Recommended | F7 |
| `tour_recipes_catalog_body` | Recetas de baristas y campeonatos. Prepáralas tal cual o guarda una copia para ajustarla. | Recipes from baristas and championships. Brew them as they are or save a copy to adjust it. | F7 |
| `catalog_detail_prepare` | Preparar | Brew | F8 |
| `catalog_detail_save` | Guardar en mis recetas | Save to my recipes | F8 |
| `catalog_detail_saved` | Receta guardada en Mis recetas | Recipe saved to My recipes | F8 |
| `catalog_detail_already_saved` | Ya tienes una copia | You already have a copy | F8 |
| `catalog_detail_source` | Ver fuente | View source | F8 |
| `catalog_detail_missing` | Esta receta ya no está en las recomendadas | This recipe is no longer among the recommended ones | F8 |
| `catalog_no_browser` | No hay ninguna app para abrir el enlace | No app can open the link | F8 |
| `recipe_edit_author` | Autor | Author | F9 |
| `recipe_edit_source` | Fuente | Source | F9 |
| `recipe_edit_source_placeholder` | https://… | https://… | F9 |
| `recipe_error_author` | Máximo 100 caracteres | 100 characters at most | F9 |
| `recipe_error_source` | Escribe un enlace que empiece por http:// o https:// | Enter a link that starts with http:// or https:// | F9 |

"Preparar" en inglés es "Brew", como `tab_prepare`. Se borran (F4): `recipe_builtin_tag`, `settings_recipes_title`,
`settings_recipes_restore`, `settings_recipes_restore_title`, `settings_recipes_restore_text`,
`settings_recipes_restore_confirm`, `settings_recipes_restore_cancel`, `settings_recipes_restored`,
`tour_recipes_builtin_title` y `tour_recipes_builtin_body`. Se quedan `recipe_builtin_v60_classic`,
`recipe_builtin_four_six` y sus `string-array` de pasos e instrucciones (D18).

## 4. Trazabilidad con el §5 del spec

| # | Criterio del §5 | Fases | Cómo se comprueba |
|---|---|---|---|
| 1 | `catalog/recipes.json` con el contenido del §3.10, pasa su test, y el APK lleva la misma copia generada en el build. | F2, F11 | `RecipeCatalogFileTest`; revisión de claves y pasos contra el §3.10 y el anexo; `unzip -p … \| cmp` en debug y release. |
| 2 | El workflow se ejecuta en un `push` que toca `catalog/**` y falla con una receta inválida (caso de prueba del parser, sin romper `main`). | F2, F11 | `RecipeCatalogFileTest.an invalid recipe makes the catalog check fail`; ejecución en GitHub tras el push de la rama (autorizado, P4). |
| 3 | Tests JVM del parser. | F1 | `RecipeCatalogParserTest`. |
| 4 | Tests JVM de la conversión a `Recipe`. | F1 | `CatalogConversionTest`. |
| 5 | Tests JVM de la copia. | F5 | `CatalogCopyTest`, `CatalogGrindTest`. |
| 6 | Tests JVM de la política de copias. | F3 | `CatalogPolicyTest`, `RecipeCatalogRepositoryTest`, `CatalogCacheTest`, `HttpCatalogFetcherTest`. |
| 7 | Test de migración 7 → 8. | F4 | `HistoryMigrationTest` (`migrates7To8*`, `migrates1To8ThenRoomReadsEverything`). |
| 8 | La copia de seguridad exporta e importa los campos nuevos e importa una copia anterior sin ellos. | F6 | `BackupMappingTest`, `BackupArchiveTest`, `BackupLegacyTest`, `BackupManagerTest`. |
| 9 | Instalación nueva: "Mis recetas" vacía con "Ver recomendadas"; no se siembra nada. | F4, F7 | `HistoryMigrationTest.aFreshInstallIsCreatedAtVersion8WithoutRecipes`; `RecipesCatalogScreenTest.aFreshInstallShowsTheEmptyListWithSeeRecommended`; E2E §7.5. |
| 10 | Tests instrumentados: selector, filtro, búsqueda sin acentos, detalle, "Preparar" con `CATALOG`, "Guardar" con "Guardada", pestaña en el `TargetPicker` en Básico y Experto. | F7, F8, F9 | `RecipesCatalogScreenTest`, `CatalogRecipeDetailScreenTest`, `CatalogPrepareFlowTest`, `TargetPickerTest`. |
| 11 | El editor edita autor y fuente (validada) y guarda; "Preparar" sin guardar usa la molienda convertida al primer molino compatible. | F5, F8, F9 | `RecipeEditFormTest`, `RecipeEditScreenTest`; `CatalogGrindTest`, `GrindCardStateTest`, `CatalogPrepareFlowTest.prepareUsesTheGrindConvertedToTheFirstCompatibleGrinder`. |
| 12 | Ajustes sin "Restaurar recetas predefinidas"; el recorrido señala "Recomendadas". | F4, F7 | `SettingsScreenTest.theRecipesCardIsGoneInBothModes`; `RecipesHistoryTourTest`. |
| 13 | La app solo contacta con `raw.githubusercontent.com`; verificado que ML Kit no envía telemetría tras añadir `INTERNET`. | F0, F10, F11 | Investigación de F0; `ManifestPermissionsTest`; contadores de `iptables` en `emulator-5556` (§7.4); release firmada instalada en el teléfono con `adb install -r` y comprobaciones sin root (§7.6, autorizado en P3). |
| 14 | Verificación manual en el móvil: modo avión muestra la copia del APK; deslizar descarga; un cambio en `main` aparece tras actualizar. | F11 | §7.5 (emulador, adb) y §7.6 (usuario, teléfono). |
| 15 | Textos en español e inglés; lint sin avisos nuevos; solo los tests del spec. | Todas | §3.1; `lintDebug` en F0, F4, F7, F9, F10 y F11; ejecuciones con `--tests` y con la lista de clases. |

## 5. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| R1 | Quitar los componentes de datatransport provoca una excepción en ML Kit o no corta la telemetría. | F0 lo prueba con control positivo antes de tocar código; plan B (excluir `transport-backend-cct`); si ninguno sirve, se para y se pregunta. `MlKitLabelOcrTest` en F10 y F11. |
| R2 | El contenido del catálogo tiene errores de datos (tiempos inferidos, traducciones) que el validador no detecta. | Revisión receta a receta contra el anexo en F2; las de confianza media se corrigen después en el JSON sin publicar versión (pregunta abierta 1 del spec). |
| R3 | Las reservas no se pueden completar (fuentes que no cargan o incompletas). | Se quedan fuera, como dice el §3.10; el plan anota cuáles y el total final. |
| R4 | La migración 7 → 8 traduce mal las recetas de fábrica (posiciones desalineadas con los arrays de recursos). | La regla es la de `RecipeRepository.toRecipe` de hoy (`getOrNull(i) ?: guardado`); `HistoryMigrationTest` la prueba con textos de otro idioma y con una predefinida editada. |
| R5 | Retirar la siembra rompe tests instrumentados que dan por hecha la "V60 clásica". | `TestAppDependencies(seedRecipes = true)` (D47) y el `grep` de F4 para encontrar el resto. |
| R6 | AGP 9 no acepta `addGeneratedSourceDirectory` para `assets` o la tarea no corre antes de empaquetar. | Alternativa con `sourceSets.main.assets.srcDir(task.flatMap { it.outputDir })`; F2 lo comprueba con `unzip` en el APK. |
| R7 | El catálogo no está en `main` cuando se publique una versión con esta función. | Mientras tanto la app usa la copia del APK y avisa "No se pudo actualizar"; el plan recuerda (§8) que la fusión con `main` debe llevar `catalog/recipes.json` antes o a la vez que la release. |
| R8 | Una referencia de catálogo activa se resuelve como `Removed` al arrancar, antes de leer el catálogo, y se borra. | `entry(key)` no emite hasta `loaded` (D15); caso en `RecipeCatalogRepositoryTest`. |
| R9 | `PullToRefreshBox` interfiere con el desplazamiento de la `LazyColumn` o con el recorrido guiado. | Test instrumentado de deslizar; capturas en F7; el ancla del recorrido está en el selector, fuera de la lista. |
| R10 | R8 (el ofuscador) rompe la lectura del catálogo en release (DTO de `kotlinx-serialization`). | Las DTO de la copia de seguridad ya funcionan en release con el plugin de serialización. F11 instala la release en `emulator-5556` y abre "Recomendadas". |
| R11 | `RecipeSharePayloadStabilityTest` cambia al sustituir `BuiltInRecipes` por la receta literal. | La receta literal reproduce la de antes; si los bytes cambian, se para: el formato `v=1` del QR no puede cambiar. |
| R12 | Cambiar la preselección de "Moler con" altera el comportamiento de las recetas propias. | `preferredGrinderId` solo lo da una referencia de catálogo; para las demás es `null` y `preselected()` queda igual. `GrindCardStateTest` y `GrindSelectionTest` lo cubren. |
| R13 | El usuario cambia el idioma de la app y la lista o la sesión quedan en otro idioma. | El idioma se lee en cada uso (D16); lo guardado o empezado se congela, como dice el §3.2. |

## 6. Preguntas para el usuario

Todas resueltas por el usuario el 2026-11-25. Las respuestas ya están en el spec (§1, §3.5 a §3.8, §3.10 y §5) y en
este plan.

1. **P1. Molienda solo de texto (D31, discrepancia 2). Resuelta: se aplica.** Un `GrindSetting` sin molino ni ajuste
   se muestra sin la etiqueta "molino eliminado", y la tarjeta de Preparar da la sugerencia del método en lugar de
   "Asigna un molino a la receta para convertir". También vale para las recetas importadas "como texto" de SPEC-009.
   Afecta a D31, F5 y F8.
2. **P2. Id del paso del recorrido (D40, discrepancia 3). Resuelta: id nuevo `recipes.catalog`**, anclado a
   `recipes_catalog_segment`. Quien ya hizo el recorrido de Recetas lo ve una vez. Afecta a D40 y F7.
3. **P3. Teléfono (D46, discrepancia 4, criterios 13 y 14). Resuelta: autorizado.** El agente implementador puede
   instalar la release firmada en `22081212UG` con `adb install -r` (conserva los datos) y lanzar los `dumpsys` de
   solo lectura. Si `install -r` falla por la firma, se para: nunca se desinstala. Los pasos 1 a 3 de §7.6 siguen
   siendo la prueba manual del usuario. Afecta a D46, F11, §4 y §7.6.
4. **P4. Push y URL de prueba (D9, D44, discrepancias 5 y 6). Resuelta: autorizado.** Se puede subir
   `feat/recipe-catalog` (los commits siguen necesitando el permiso de la regla general) y usar
   `-Ppourline.catalogUrl`, solo en builds locales, para probar la descarga contra la rama antes de fusionar con
   `main`. Afecta a D9, F2, §4, §7.2 y §7.5.

## 7. Verificación final

### 7.1 Clases afectadas

- **JVM:** `dev.josueamaya.pourline.recipe.catalog.*`, `recipe.RecipeValidatorTest`, `recipe.RecipeTest`,
  `recipe.RecipeFromBrewTest`, `recipe.share.*`, `backup.*`, `settings.SettingsSnapshotTest`,
  `settings.ExperienceModeTest`, `session.*`, `grinder.GrindCardStateTest`, `grinder.SharedGrindResolutionTest`,
  `history.BrewRepositoryMappingTest`, `sim.SimulatedTargetPourTest`, `tools.FourSixTest`, `ui.session.*`,
  `ui.recipes.RecipeEditFormTest`, `ui.tour.ToursCatalogTest`.
- **Instrumentados** (`dev.josueamaya.pourline.`): `history.HistoryMigrationTest`, `history.BrewDaoTest`,
  `recipe.RecipeDaoTest`, `backup.BackupManagerTest`, `backup.BackupDaoTest`, `ManifestPermissionsTest`,
  `coffee.label.MlKitLabelOcrTest`, `ui.recipes.RecipesScreenTest`, `ui.recipes.RecipesCatalogScreenTest`,
  `ui.recipes.CatalogRecipeDetailScreenTest`, `ui.recipes.CatalogPrepareFlowTest`, `ui.recipes.RecipeEditScreenTest`,
  `ui.recipes.RecipeImportScreenTest`, `ui.recipes.RecipeShareTest`, `share.ShareCardsLayoutTest`,
  `ui.scale.TargetPickerTest`, `ui.prepare.PrepareScreenTest`, `ui.grinders.GrindCardTest`,
  `ui.session.GrindBrewFlowTest`, `ui.session.SessionScreenTest`, `ui.session.ScalelessJourneyTest`,
  `ui.history.BrewDetailScreenTest`, `ui.SettingsScreenTest`, `ui.tour.RecipesHistoryTourTest`,
  `ui.TabsNavigationTest`, más las que añadió el `grep` de F4.

### 7.2 Comprobaciones

1. Las dos listas de §7.1 en verde (con la línea base de F0 para los fallos previos).
2. `./gradlew :app:lintDebug`: sin errores ni avisos nuevos respecto a F0.
3. `grep -rn "BuiltInRecipes\|BuiltInRecipeSeeder\|restoreBuiltIns\|RESTORE_BUILT_INS\|recipe_builtin_tag" app/src`
   vacío.
4. `unzip -p` de las APK de debug y release: `assets/catalog/recipes.json` idéntico a `catalog/recipes.json`.
5. Push de la rama (autorizado, P4): el workflow "Catalog" aparece y pasa
   (`gh run list --workflow catalog.yml --branch feat/recipe-catalog`), y no se ejecuta en un push que no toca
   `catalog/**`.
6. Manifiestos fusionados de debug y release: `INTERNET` presente, `CAMERA` ausente, sin `datatransport`.

### 7.3 Rendimiento

El spec no fija cifras. Como referencia, en `emulator-5556`: tiempo de `RecipeCatalogParser.parse` del catálogo
empaquetado (log temporal o test instrumentado de una sola medida, que no se commitea) y que la lista de recomendadas
desplaza sin saltos con las 35 recetas. Si el parseo pasara de 100 ms, se haría siempre fuera del hilo principal (ya
lo está, D15) y se anotaría.

### 7.4 Red y telemetría (`emulator-5556`, root)

1. `ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug :app:installDebugAndroidTest`.
2. Cadena `pourline_out` de D46 para el UID de la app, en `iptables` e `ip6tables`.
3. OCR con `am instrument` (`MlKitLabelOcrTest`), forzar cualquier trabajo del UID en `jobscheduler` y esperar un
   minuto.
4. Abrir Recetas → Recomendadas (`am start -n dev.josueamaya.pourline/.MainActivity`, `input tap` en las
   coordenadas de `uiautomator dump`) y deslizar para actualizar.
5. `iptables -L pourline_out -v -n -x` e `ip6tables …`: "otro" a 0; `dmesg | grep pourline_other` vacío.
6. Release (R10): `./gradlew :app:assembleRelease`, desinstalar la debug del emulador, `adb install` de la release y
   repetir 2 a 5.
7. Limpiar la cadena y `pm clear`.

### 7.5 E2E por adb (sin ARTEMIS, `emulator-5556`)

1. Instalación nueva (`pm clear`), modo avión (`adb shell cmd connectivity airplane-mode enable`), pasar el
   onboarding con `input tap`.
2. Recetas: "Mis recetas" vacía con "Ver recomendadas"; tocarlo cambia a "Recomendadas" y muestra las recetas del APK
   con "Usando las recetas incluidas en la app".
3. Buscar "nemo" (sin acento) y filtrar por AeroPress: aparece la de Némo Pop. "Quitar filtros".
4. Abrir `four_six`: detalle completo; "Guardar en mis recetas" → mensaje y "Guardada" en la lista; volver a guardar
   → "Ya tienes una copia" y " (2)" en "Mis recetas".
5. Abrir otra receta → "Preparar": Preparar muestra su nombre; empezar sin báscula y terminar; el historial muestra el
   nombre en el detalle de la preparación.
6. Quitar el modo avión y deslizar: "Última actualización: <fecha>" (o "No se pudo actualizar" si el catálogo aún no
   está en `main`). Con la rama subida (P4), repetir con la APK construida con
   `-Ppourline.catalogUrl=https://raw.githubusercontent.com/c3rberuss/pourline/feat/recipe-catalog/catalog/recipes.json`:
   la descarga funciona y aparece "Última actualización".
7. Ajustes: no está "Restaurar recetas predefinidas". Recorrido de Recetas repetido desde Ajustes: el primer paso
   señala "Recomendadas".
8. Capturas (`screencap`) de cada paso en el directorio temporal de la sesión, para la revisión del usuario.

### 7.6 Teléfono (`22081212UG`)

El agente construye la release firmada (`./gradlew :app:assembleRelease`, con `keystore.properties`) y la instala con
`adb -s 22081212UG install -r app/build/outputs/apk/release/app-release.apk`, que conserva los datos (autorizado en
P3). Si la instalación falla por la firma, se para y se avisa: nunca se desinstala. El paso 4 lo hace el agente; los
pasos 1 a 3 son la verificación manual del usuario.

1. Primer arranque de "Recomendadas" en modo avión: se ve la copia del APK y el aviso.
2. Sin modo avión, deslizar: se descarga y aparece "Última actualización".
3. Tras fusionar con `main`, cambiar algo en `catalog/recipes.json` de `main` (subiendo `revision`), esperar unos
   minutos (caché de `raw.githubusercontent.com`) y deslizar: el cambio aparece.
4. Telemetría (sin root): `adb -s 22081212UG shell dumpsys package dev.josueamaya.pourline | grep -i datatransport`
   vacío; usar "Leer etiqueta" con una foto de `~/Downloads/coffee-bags` sin abrir "Recomendadas" (o pedir al usuario
   que lo haga si la cámara o la galería necesitan su mano); después
   `dumpsys jobscheduler | grep -i -A3 "dev.josueamaya.pourline"` sin trabajos de datatransport y
   `dumpsys netstats detail | grep -A4 "uid=<uid>"` sin bytes nuevos del UID de la app.
5. Si el usuario quiere probar recetas de confianza media (pregunta abierta 1 del spec), las correcciones van al
   JSON.

## 8. Seguimiento fuera de este plan

- La fusión `develop` → `main` debe llevar `catalog/recipes.json` antes o a la vez que la primera release con esta
  función (R7).
- El anexo no recoge todavía los datos completados de las reservas; si el usuario quiere, se actualiza con lo anotado
  en "Resultado de F2".
- `/code-review` de la rama antes de fusionar.
