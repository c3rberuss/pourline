# Plan de implementación: SPEC-006 Enmienda 1, conversión por calibración y curva de grosor

| Campo | Valor |
|---|---|
| Spec | [`spec-molinos.md`](spec-molinos.md) (SPEC-006), **Enmienda 1** (§3.3, §3.3.1, §3.3.2, §3.4, §3.5, §3.6, §3.7, §4 y §5) |
| Estado | Aprobado por el usuario (2026-11-17). Commits por fase autorizados. F3 y F4 avanzaron mientras F2 esperaba la decisión del usuario; la puerta se resolvió el 2026-11-17 (se quita la moka del A2) y F5 en adelante siguen |
| Fecha | 2026-11-17 |
| Resultado de la verificación | F0 a F8 hechas el 2026-11-17 en `feat/grind-calibration`, **salvo la verificación manual en el móvil del usuario (§7.4), pendiente de su permiso (P4)**. Todo en el emulador clon `SG_API34_calib` (`emulator-5560`), no en `emulator-5556` (lo usaba otra sesión). **JVM:** `:app:testDebugUnitTest` 865 tests, 1 omitido (`LabelFixtureCaptureTest`), 0 fallos. **Connected completo:** 549 tests, 4 fallos: `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap` (conocido), los dos casos de `SessionPerformanceTest` (en el clon fallan también en la línea base) y `BrewEditScreenTest.chooseAGrinderSetTheValueAndSaveItsIdAndLabel` (el desplegable no se abrió bajo carga; pasa 2 de 2 aislado). **Lint:** `:app:lintDebug` idéntico a la línea base (2 errores `StringFormatInvalid` y 21 avisos previos; ninguno nuevo, sin `UnusedResources`, `MissingTranslation` ni `ExtraTranslation`). **Greps:** ningún `import android`/`androidx` en los 15 archivos puros; ningún `converter_method`, `converter_no_method`, `byRange` ni `byAnchors` en `app/src`; ningún `it.method`/`key.method` en `Navigation.kt`; `AndroidManifest.xml` sin cambios. **Base de datos:** v7 (`develop` y `feat/guided-tour` siguen en v6). **Rendimiento** (`SessionPerformanceTest`, clon bajo carga del equipo, pasadas intercaladas rama/línea base `63ee07c`): Básico, 10 pares, mediana del p95 81,6 ms frente a 79,6 ms (diferencia pareada mediana +0,95 ms); Experto, 6 pares, 96,8 ms frente a 101,0 ms (−4,4 ms). El ruido entre pasadas es de decenas de ms, así que el umbral de +1 ms no es demostrable en el clon: no hay regresión medible, pero conviene repetirlo en un emulador sin carga. **E2E por adb (§7.3):** pasos 1 a 9 correctos (catálogo v2 sembrado, S3 20 → A2 1 · 5 aproximado, pares 1 · 10 y 1 · 35, S3 22 → A2 1 · 20 "per your calibration", sentido inverso, conflicto con "Replace", detalle con editar y borrar, mismo valor con método "Other"); `logcat -b crash` vacío. Capturas en `~/Downloads/grind-calibration-captures/` (claro y oscuro con letra 1,3; los pares compuestos se parten en dos líneas sin cortarse). |
| Rama | `feat/grind-calibration`, desde `develop` (`1015b12`), con el commit del spec (`53548c1`) |
| Módulos | `:app` (`:protocol` no cambia) |
| Plan anterior | [`plan-molinos.md`](plan-molinos.md) (D1 a D39). Este plan sigue desde D40 |

La enmienda cambia el corazón de la conversión: deja de depender del método y pasa a usar, por este orden, los pares
de calibración del usuario y una curva de grosor por molino. El orden de las fases va de dentro afuera, como en los
planes anteriores:

1. Primero el dominio puro (curva, pares, validador y la nueva `convert`), con tests JVM.
2. Después, la **puerta del Femobook A2** (§3.6). Es una parada obligatoria: se presentan `METHOD_LEVELS`, la curva
   implícita del A2 y la tabla S3 → A2, y no se toca la interfaz hasta que el usuario decida sobre el rango de moka.
3. Luego los datos (v7), el repositorio, la tarjeta, el convertidor y el detalle del molino.

La firma antigua `convert(value, method, from, to)` convive con la nueva hasta F6, para que cada fase compile y quede
en verde sin reescribir todo de golpe (D60).

Una estimación previa con el catálogo actual (§3 de F2) coincide con la del spec: S3 20 → A2 31 con el rango de moka
y → 45 sin él. Sirve como comprobación de que el cálculo del implementador es el mismo que se aprobó.

El código real contradice o no cubre algunos puntos del spec. Se listan en el §1.3 con el ajuste mínimo que propone
este plan. Lo más importante:

- `GrinderDao.syncCatalog` **borra** un predefinido retirado del catálogo si nadie lo usa, y la `CASCADE` borraría sus
  pares. Eso contradice "una actualización del catálogo no toca los pares" (D49).
- El §3.5 da al validador solo `Ok`, `Update` y `Conflict`, pero el §5 le pide rechazar molinos iguales y valores
  fuera de escala o de paso. Se añade `Invalid` (D47).

## 1. Punto de partida

### 1.1 Se reutiliza

| Pieza | Uso en la enmienda |
|---|---|
| `grinder/GrindConverter.kt`: `Conversion` (`Exact`, `Approximate`, `NoEquivalent`), `Conversion.valueOrNull`, `ConversionReason` y `suggested(method, grinder)` | Los tipos del resultado no cambian. `ConversionReason` gana `CALIBRATED`. `suggested` se mantiene tal cual (§3.3). |
| `grinder/GrindConverter.kt`: `interpolate(points, x)` (privado) | Interpolación por tramos con extrapolación por el tramo extremo. Pasa a `GrindMath.kt` como `internal` para que la usen la curva y la calibración (D42). |
| `grinder/GrindMath.kt`: `EPS`, `snap`, `clampToScale`, `roundTo6` | El mismo pipeline de redondeo y límite después de cualquier regla (D44). |
| `grinder/GrindModels.kt`: `Grinder`, `GrindLevel` (ordinal 0 a 6), `GrindDirection`, `GrindRange` | La curva usa el ordinal del nivel como coordenada `L` (§3.3.2). |
| `grinder/GrindCardState.kt`: `grindCardState`, `GrindOutcome`, `GrindFix`, `ConverterArgs`, `snapshot` | La tarjeta conserva su estructura. Cambian la llamada a `convert`, el `fix` de `NoEquivalent` y `ConverterArgs` (sin `method`). |
| `grinder/ConverterRows.kt` (`converterRows`, `ConverterRow`) | El convertidor sigue siendo una función pura. Gana los pares y la marca `correctable`. |
| `grinder/GrinderCatalogFile.kt`: `GrinderCatalogFile.parse`, `CatalogEntry.micronRange` | Los tests de `METHOD_LEVELS` y de la puerta leen las micras de `catalog.json` sin código nuevo en `main`. |
| `grinder/GrinderDao.kt`: `save` con `@Insert`/`@Update` (sin `REPLACE`), `deleteRelabeling` y el patrón `@Transaction open suspend fun` | Editar un molino no borra la fila y, por tanto, no dispara la `CASCADE` sobre los pares. Borrarlo sí, como pide el §3.3.1. |
| `session/GrindSelection.kt` (`GrindSelection`, `SessionGrinders`, `RepositorySessionGrinders`) | Los pares entran como un `Flow` más del `combine`, fuera del camino caliente (D50). |
| `ui/grinders/GrindInput.kt` (`GrindInput(value, grinder, onChange, start, modifier, testTag, clampToScale)`) | La introducción compuesta de los pares, en el convertidor y en el detalle. |
| `ui/grinders/GrindSettingField.kt` (`startValue(grinder, method)`) | El valor inicial de "Corregir" sin equivalencia: `startValue(grinder, null)`, el centro de la escala ajustado al paso (D52). |
| `ui/grinders/GrindCard.kt` (`ConversionTags`) | Las marcas de la tarjeta y del convertidor. Gana "según tu calibración". |
| `ui/components/`: `AntDrawer`, `AntModal`, `AntAlert`, `AntSelect`, `AntListItem`, `AntCard`, `AntTag`, `AntMessageHost` + `showAntMessage` | La hoja de los pares, la confirmación del borrado, el conflicto con "Reemplazar" y el mensaje "Equivalencia actualizada". |
| `history/Migrations.kt` y `HistoryMigrationTest` (`runMigrationsAndValidate` y las cinco aperturas con Room) | `MIGRATION_6_7` sigue el patrón de `MIGRATION_5_6`. |
| Tests: `GrinderFixtures` (JVM), `TestGrinders`, `HistoryDatabase.addGrinder`, `TestGrinderCatalog`, `GrindersUi` (instrumentados), `TestAppDependencies` | La base de los tests nuevos. |

### 1.2 Falta o hay que corregir

- `GrindConverter.convert(value, method, from, to)` recibe el método y usa `byRange` (posición en el rango) y
  `byAnchors` (anclas comunes). No hay curva de grosor ni pares.
- No existe `GrindCurve`, `METHOD_LEVELS`, `CalibrationPoint`, `GrindCalibration` ni `CalibrationValidator`.
- `HistoryDatabase` está en `version = 6`, sin `grind_calibrations`.
- `GrinderDao.isUsed` solo mira recetas y preparaciones (D49).
- `GrinderRepository` y `SessionGrinders` no exponen pares.
- **Usuarios del método en la conversión** (todos se eliminan en F5 y F6):

  | Dónde | Qué | Cómo se elimina |
  |---|---|---|
  | `grinder/GrindCardState.kt`: `convertOutcome` | `GrindConverter.convert(value, method, from, to)` | F5: la nueva `convert` con los pares orientados. |
  | `grinder/GrindCardState.kt`: `ConverterArgs(sourceId, method, value)` y su creación en `grindCardState` | Lleva el método al convertidor | F6: `ConverterArgs(sourceId, value)`. |
  | `ui/grinders/GrindSettingField.kt` | `ConverterArgs(current.id, method, choice.value)` (atajo `${tagPrefix}_grind_converter`) | F6: sin método. |
  | `grinder/ConverterRows.kt` | `converterRows(source, method, value, owned)` y su `convert` | F6: `converterRows(source, value, owned, pairs)`. |
  | `NavigationKeys.kt` | `GrindConverter(sourceId, method, value)` | F6: `GrindConverter(sourceId, value)` (D5 del spec, D59). |
  | `Navigation.kt`, líneas 168 (Prepare), 188 (Session) y 291 (RecipeEdit) | `stack.add(GrindConverter(it.sourceId, it.method, it.value))` | F6: `GrindConverter(it.sourceId, it.value)`. Las líneas 240 y 298 (`GrindConverter()`) no cambian. |
  | `Navigation.kt`, líneas 243 a 249 | `GrindConverterViewModel(deps.grinderRepository, key.sourceId, key.method, key.value)` | F6: sin `key.method`. |
  | `ui/grinders/GrindConverterViewModel.kt` | Parámetro `method`, `MutableStateFlow` `method`, `setMethod`, `GrindConverterUi.method` | F6: se quitan. |
  | `ui/grinders/GrindConverterScreen.kt` | `NO_METHOD`, `Methods`, el `AntSelect` `converter_method`, `startValue(ui.source, ui.method)` | F6: se quitan; `startValue(ui.source, null)`. |
  | `res/values*/strings.xml` | `converter_method`, `converter_no_method` | F6: se borran en los dos idiomas. |
  | `Navigation.kt`, `fixGrinderKey` (líneas 352 a 354), `GrindCard.kt` (línea 98) y `GrindConverterScreen.kt` (línea 180) | `if (fix == COMPLETE_RANGES) … else …` | F5: `when` exhaustivo con `ADD_CALIBRATION`. Si no, el tercer valor caería en "Duplicar" sin error de compilación. |

- **Tests que cambian de expectativa** (por la curva y por quitar el método):

  | Test | Hoy | Con la enmienda | Fase |
  |---|---|---|---|
  | `GrindConverterTest` (JVM) | Firma con método: rango, extrapolación, anclas comunes | Los casos de rango y anclas comunes se borran; `Exact` del mismo molino, redondeo, límite y `HIGHER_IS_FINER` se reescriben con la nueva firma | F1 (nuevos), F6 (borrar los antiguos) |
  | `GrindCardStateTest` (JVM) | Uno 20 → Dos 28; `ConverterArgs(1, V60, 20.0)` ×2; "Dos · 28 clics" | Uno 20 → Dos 30 (`ANCHORS`); `ConverterArgs(1, 20.0)`; "Dos · 30 clics" | F5, F6 |
  | `ConverterRowsTest` (JVM) | Con método | Sin método, con pares y `correctable` | F6 |
  | `session/GrindSelectionTest` (JVM) | 28 | 30 | F5 |
  | `GrindCardTest.changingGrindWithShowsTheConvertedSetting` | 28 | 30 | F5 |
  | `GrindBrewFlowTest.theBrewSavesTheChosenGrinderTheConvertedSettingAndItsCopy` | Uno 30 → Tres 130 | 150 ("3 · 3 · 0") | F5 |
  | `GrindConverterScreenTest.chooseASourceAMethodAndASettingAndEachOfMyOtherGrindersGetsARow` | Elige V60; Dos 28; Tres "2 · 0 · 0" | Sin método; Dos 30; Tres "2 · 2 · 0" (100). Se renombra a `chooseASourceAndASettingAndEachOfMyOtherGrindersGetsARow` | F6 |
  | `GrindShortcutTest.theCardShortcutOpensTheConverterWithTheSourceMethodAndSetting` | 28 | 30. Se renombra a `…WithTheSourceAndSetting` | F6 |
  | `HistoryMigrationTest.aFreshInstallIsCreatedAtVersion6WithTheBuiltIns` | v6 | v7 (o la versión libre, D40) | F3 |
  | `GrinderCatalogFileTest` | `assertEquals(1, data.version)` y A2 con `MOKA 10–18` | Solo si la puerta cambia el rango de moka: `version` 2 y los rangos aprobados | F2 |

  Los valores con curva salen de las anclas de `GrinderFixtures`/`TestGrinders`: Uno 8/20/32 y Dos 10/30/50 en
  `FINE`/`MEDIUM`/`COARSE`, y Tres 40/100/160. Uno 20 está en `MEDIUM` (L = 3), que en Dos es 30 y en Tres 100. Uno
  30 está en L = 3 + 10/12 × 2 = 4,667, que en Tres es 100 + 0,833 × 60 = 150.

- No hay detekt ni ktlint en el proyecto. Como en los planes anteriores, el control es `:app:lintDebug`.

### 1.3 Discrepancias entre el spec y el código, o dentro del spec

| # | Spec | Código real o problema | Ajuste que propone el plan |
|---|---|---|---|
| 1 | §3.3.1: "Una actualización del catálogo no toca los pares". | `GrinderDao.syncCatalog` borra con `deleteRow` un predefinido que ya no viene en el catálogo si no es mío, no es el predeterminado y `isUsed` es falso. La `CASCADE` borraría sus pares. Puede pasar: un par exige que los dos molinos sean míos al crearlo, pero después el usuario puede quitar uno de mis molinos. | `isUsed` también mira `grind_calibrations` (D49). Un predefinido con pares se **retira**, no se borra, y el §3.3.1 ("se retira del catálogo, el par se conserva y sigue aplicando") se cumple. |
| 2 | §3.5: `CalibrationValidator` devuelve `Ok`, `Update(existing)` o `Conflict(conflicts)`, y `saveCalibration` devuelve `Saved`, `Updated` o `Conflict`. | El mismo §3.5 dice que el validador también comprueba la escala y el paso, y el §5 le pide rechazar molinos iguales y valores fuera de escala o de paso. Con tres resultados, no hay forma de devolver el rechazo. | Cuarto resultado `Invalid(errors: List<CalibrationError>)` en el validador y en `saveCalibration` (D47). La interfaz nunca lo provoca (lo impiden `GrindInput` y el selector), pero se muestra si llega. |
| 3 | §3.3, regla 2: si `value` coincide con un par, `Exact(toValue)`. | Si una edición del molino deja el `toValue` del par fuera de la escala o fuera del paso (§3.3.1 lo permite), devolver `Exact` daría un valor no válido en el destino. | El pipeline de `snap` y límite se aplica también a ese valor: si lo cambia, el resultado es `Approximate` con `CALIBRATED` más `ROUNDED` o `AT_LIMIT` (D44). |
| 4 | §3.7: "Corregir" sin equivalencia abre la introducción con "el sugerido de D13". | El sugerido de D13 depende del método, y el convertidor ya no tiene método. | `startValue(grinder, null)`: el centro de la escala ajustado al paso, que es lo que D13 da sin rango (D52). |
| 5 | §3.3.1 y §3.7: ejemplos "S3 25 = A2 60" y "S3 20 = A2 60". | El A2 es compuesto (rotaciones › clics, 40 por rotación), así que 60 se muestra como "1 · 20". | Los pares se muestran con `GrindFormat.format`, como el resto de la app: "Chestnut S3 20 clics = A2 1 · 20" (D55, P2). |
| 6 | §3.3.1: un valor repetido actualiza el par existente. | Al **editar** un par P con un valor que ya tiene otro par Q, no está claro si se actualiza P, Q o los dos. | Se trata como conflicto con Q, con la opción "Reemplazar" (borra Q y guarda P editado) (D47). |
| 7 | §3.7: "Sin equivalencia" enlaza a completar los rangos o duplicar "el molino". Hoy la tarjeta usa el molino elegido. | Con la curva, "Sin equivalencia" sin pares aparece si **cualquiera** de los dos molinos no tiene curva. Si el que no la tiene es el origen, completar los rangos del elegido no arregla nada. | El enlace apunta al molino sin curva (primero el elegido, si los dos carecen de ella). `ADD_CALIBRATION` va siempre al molino elegido, como dice el spec (D45, P3). |
| 8 | §3.3.1: cada par se puede editar; los dos molinos de un par son míos. | Un par se conserva cuando uno de sus molinos deja de ser mío. Si se pudiera editar, se crearía de hecho un par con un molino que no es mío (fuera del alcance, §1). | Un par con un molino que ya no es mío solo se puede borrar. El validador devuelve `Invalid(NotOwned)` (D47, D54). |
| 9 | §3.3.1 y §3.7: "Contradice la equivalencia S3 20 = A2 60", en singular. | Un par nuevo puede contradecir a varios a la vez. | `grind_calibration_conflict` como `plurals`, con la lista de pares (D53). |
| 10 | §3.4: índices sobre `grinderAId` y `grinderBId`, y únicos (`grinderAId`, `grinderBId`, `valueA`) y (`grinderAId`, `grinderBId`, `valueB`). | El índice sobre `grinderAId` es redundante con los dos únicos, que empiezan por esa columna. | Se crea igual, como dice el spec. El coste es despreciable con pocos pares (D48). |
| 11 | §5: "Tests del repositorio". | El proyecto no tiene Robolectric. Los tests de Room son instrumentados (D18 de SPEC-007). | Los tests del DAO y del repositorio son instrumentados en `emulator-5556`. La lógica de los pares (validador, orientación) tiene tests JVM. |
| 12 | §5: "`SessionPerformanceTest` no empeora respecto a `develop`". | El p95 del caso Experto ya supera 32 ms en `develop` y oscila entre pasadas (verificación de `plan-molinos.md`). | Mismo criterio que D32: mediana del p95 de tres pasadas ≤ la de la línea base + 1 ms y, si hace falta, A/B intercalado (D57). |
| 13 | §5: "con un segundo par, otra fila intermedia muestra 'según tu calibración'". | El convertidor tiene una fila por molino, no filas intermedias. | Se interpreta como "otro ajuste de origen intermedio": con dos pares Uno↔Dos, un ajuste de Uno entre los dos da en la fila de Dos "según tu calibración" (F6). |

## 2. Decisiones que completan el spec

Marcadas como "(concreta)" si eligen entre opciones que el spec deja abiertas, y como "(amplía)" si añaden algo que
el spec no dice.

### 2.1 Dominio

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D40 | Versión de la base de datos y choque con SPEC-008 (concreta §3.4) | **Comprobado al escribir el plan:** `develop` (`1015b12`) está en `version = 6` con `6.json`, y `feat/guided-tour` (SPEC-008, worktree `/Users/josue/projects/dot-scale`) no toca `history/` ni `app/schemas/` (solo `GrindCard.kt`, `Navigation.kt`, `strings.xml`, `TestAppDependencies.kt` y otros de la UI). Se planifica **v7**: `MIGRATION_6_7` y `7.json`. F0 lo vuelve a comprobar. **Si `develop` ya está en v7 al empezar:** `git rebase develop` y se usa `MIGRATION_7_8`/`8.json`. **Si el choque aparece al fusionar** (otra rama llegó antes a `develop` con su v7): se conserva el `7.json` de `develop`; nuestra migración pasa a `val MIGRATION_7_8 = object : Migration(7, 8)` con el mismo SQL; `version = 8`; se borra nuestro `7.json` y se regenera `8.json` con `:app:compileDebugKotlin`; el KDoc de `HistoryDatabase` gana la línea de la v8; en `HistoryMigrationTest`, `migrates6To7…` pasa a `migrates7To8…`, la cadena llega a 8, `aFreshInstallIsCreatedAtVersion8…`, y `create()` y las cinco aperturas con Room añaden la migración nueva al final. **En el móvil del usuario no se instala nada hasta que la versión esté fijada** (D40 y R2). En el emulador se usa `pm clear`. | El spec pide usar la siguiente versión libre. Instalar en el móvil una v7 que luego cambie de número dejaría su base de datos con un esquema que ninguna migración conoce. |
| D41 | Archivos y pureza (amplía D10) | **Puros, sin `import android` ni `androidx`** (se comprueba con `grep` en F1 y F8): `grinder/GrindCurve.kt`, `grinder/GrindCalibration.kt` y `grinder/CalibrationValidator.kt`, además de los 12 de D10. **Room:** `grinder/GrindCalibrationEntity.kt` y `grinder/GrindCalibrationDao.kt`. **UI:** `ui/grinders/CalibrationEditor.kt`, `ui/grinders/CalibrationSheet.kt` y `ui/grinders/GrinderCalibrations.kt`. | Mismo reparto que D10: dominio puro y probado en la JVM, y Room y la UI aparte. |
| D42 | API de `GrindCurve` (concreta §3.3.2) | En `grinder/GrindCurve.kt`: `sealed interface GrindCurve { fun level(value: Double): Double; fun value(level: Double): Double; companion object { fun of(grinder: Grinder): GrindCurve? } }`. `data class Explicit(val points: List<Pair<Double, Double>>)` con los puntos `(valor, ordinal)` de **todas** las anclas (2 o más), ordenados por valor; `level` y `value` interpolan por tramos con `interpolate` (movida de `GrindConverter` a `GrindMath.kt` como `internal fun interpolate(points: List<Pair<Double, Double>>, x: Double): Double?`, sin cambios). `data class Linear(val a: Double, val b: Double)` con `level = (value − a) / b` y `value = a + b × level`. `of`: con 2 anclas o más, `Explicit`; si no, puntos `(METHOD_LEVELS[m], (min + max) / 2)` de cada rango con nivel, sin repetidos (igualdad con `EPS` en las dos coordenadas), mínimos cuadrados `b = Σ(L − L̄)(v − v̄) / Σ(L − L̄)²`, `a = v̄ − b·L̄`, y `null` con menos de 2 niveles distintos, con `b ≤ 0` en `HIGHER_IS_COARSER` o con `b ≥ 0` en `HIGHER_IS_FINER`. Un ancla suelta no se usa. | Una interfaz sellada deja probar cada variante por separado. Las anclas son estrictamente monótonas (`GrinderValidator`), así que la interpolación por tramos es invertible y `level` y `value` son inversas exactas, también al extrapolar. |
| D43 | `METHOD_LEVELS` (concreta §3.6) | `val METHOD_LEVELS: Map<BrewMethod, Double>` en `GrindCurve.kt`, con los 9 métodos con rango en el catálogo (sin `OTHER`). El cálculo vive **solo en los tests** (`test/.../grinder/CatalogLevels.kt`, nuevo): `fun methodLevels(entries: List<CatalogEntry>): Map<BrewMethod, Double>`. Para cada predefinido con `micronRange` y rango para el método: `µ(v) = µmin + (v − scaleMin) / (scaleMax − scaleMin) × (µmax − µmin)` (o `µmax − …` en `HIGHER_IS_FINER`), punto medio `µ((min + max) / 2)`, media entre molinos, `L = (µ − 100) / 200`, redondeo con `BigDecimal.valueOf(L).setScale(2, RoundingMode.HALF_UP)`. Valores provisionales (catálogo v1, calculados al escribir el plan; F1 los recalcula y manda lo que dé el test): V60 2,25; Kalita Wave, Chemex y Origami 2,81; AeroPress 2,64; Prensa francesa 3,93; Espresso 0,97; Cold brew 4,33 (9 molinos: el K-Ultra no tiene rango); Moka 2,06. El A2 no tiene `micronRange`, así que no participa: la decisión sobre su moka no cambia `METHOD_LEVELS`. | El spec dice que se calcula "al preparar la enmienda, no en la app". Dejar el cálculo en el test cumple eso y, a la vez, hace que un cambio del catálogo rompa el test (§3.6). `HALF_UP` fija el redondeo, que el spec no precisa. |
| D44 | Reglas y pipeline de `convert` (concreta §3.3 y D14) | Nueva sobrecarga `fun convert(value: Double, from: Grinder, to: Grinder, pairs: List<CalibrationPoint>): Conversion`, sin valor por defecto en `pairs`. Reglas: (1) mismo molino (`from.id != 0L && from.id == to.id`) → `Exact(value)`, sin pipeline, como hoy; (2) 2 pares o más → `interpolate` sobre `(fromValue, toValue)` ordenados por `fromValue`; si `abs(value − fromValue) < EPS` en un par, el valor es su `toValue` y la razón ninguna; si no, `CALIBRATED`; (3) 1 par `(x0, y0)` con las dos curvas → `c(v) = to.value(from.level(v))`, `v' = c(value) + (y0 − c(x0))`, `CALIBRATED`, o `y0` sin razón si `value` coincide con `x0`; sin alguna curva → `NoEquivalent`; (4) 0 pares y las dos curvas → `c(value)` con `ANCHORS`; (5) `NoEquivalent`. Después de (2), (3) y (4): `snap` → `ROUNDED` si cambia más de `1e-6` → si sale de la escala, `clampToScale` y `AT_LIMIT`. Sin razones, `Exact`; con alguna, `Approximate`. `CALIBRATED` y `ANCHORS` nunca van juntas: (2) y (3) solo añaden `CALIBRATED`. Si (2) o (3) caen en un par cuyo valor ya no es válido en el destino (escala editada), las razones son `CALIBRATED` más `ROUNDED`/`AT_LIMIT` (discrepancia 3). | Es el §3.3 con el mismo orden de pasos que D14 (redondear antes de limitar). El pipeline común evita devolver un valor inválido después de una edición del molino. |
| D45 | Destino del arreglo de "Sin equivalencia" (concreta §3.7; discrepancia 7) | En `GrindCardState.kt`: `data class FixTarget(val fix: GrindFix, val grinderId: Long)` y `fun fixFor(from: Grinder, to: Grinder, pairCount: Int): FixTarget`. Con 1 par: `FixTarget(ADD_CALIBRATION, to.id)`. Si no: el molino sin curva (`to` si los dos carecen de ella) con `COMPLETE_RANGES` si es propio y `DUPLICATE` si es predefinido. `GrindOutcome.NoEquivalent(fix: GrindFix, grinderId: Long)`. `GrindFix` gana `ADD_CALIBRATION`. | Un solo lugar decide el arreglo para la tarjeta y para el convertidor. Ningún predefinido del catálogo actual se queda sin curva (el A2 tiene la implícita), así que en la práctica el caso es el de un molino propio sin anclas ni rangos. |
| D46 | Modelo y orientación (concreta §3.4 y §3.5) | En `grinder/GrindCalibration.kt`: `data class CalibrationPoint(val fromValue: Double, val toValue: Double)`; `data class GrindCalibration(val id: Long, val grinderAId: Long, val valueA: Double, val grinderBId: Long, val valueB: Double)` con `fun involves(id: Long): Boolean`, `fun otherId(id: Long): Long`, `fun valueOf(id: Long): Double` y `fun pointFrom(fromId: Long): CalibrationPoint`; `fun List<GrindCalibration>.pointsBetween(fromId: Long, toId: Long): List<CalibrationPoint>`. Orden canónico `grinderAId < grinderBId`: lo aplica el DAO al escribir (`require(a != b)` y se intercambian lado y valor si hace falta). | El orden canónico hace que los índices únicos del §3.4 detecten los duplicados sin importar el orden en que los introdujo el usuario. La orientación es una función pura, así que la tarjeta y el convertidor no necesitan una consulta por pareja de molinos. |
| D47 | `CalibrationValidator` (concreta §3.3.1 y §3.5; discrepancias 2, 6 y 8) | `object CalibrationValidator { fun check(grinder: Grinder, value: Double, other: Grinder, otherValue: Double, existing: List<GrindCalibration>, editingId: Long? = null): CalibrationCheck }` con `sealed interface CalibrationCheck { data object Ok; data class Update(val existing: GrindCalibration); data class Conflict(val conflicts: List<GrindCalibration>); data class Invalid(val errors: List<CalibrationError>) }` y `sealed interface CalibrationError { data object SameGrinder; data class NotOwned(val grinderId: Long); data class OutOfScale(val grinderId: Long); data class OffStep(val grinderId: Long) }`. Orden: (a) `Invalid` si `grinder.id == other.id`, si alguno no es `owned`, si un valor sale de `[scaleMin, scaleMax]` (con `EPS`) o si `abs(snap(v) − v) > 1e-6`; (b) de `existing` se quita `editingId`; el candidato con el mismo valor (con `EPS`) en el lado A, o si no en el B, es el objetivo de `Update`, salvo si se está editando (entonces es un conflicto, discrepancia 6); (c) con la coordenada de grosor `g(v) = v` en `HIGHER_IS_COARSER` y `−v` en `HIGHER_IS_FINER`, un par `c` restante choca con el candidato `p` si `(gA(p) − gA(c)) × (gB(p) − gB(c)) ≤ 0`; (d) con choques, `Conflict(choques)` (aunque haya `Update`, §3.3.1); sin choques, `Update` u `Ok`. Un duplicado exacto es `Update` sin cambios. | El §5 pide probar los rechazos de escala, paso y molinos iguales, y el §3.5 dice que el validador también los comprueba. El producto ≤ 0 expresa "estrictamente creciente en las dos coordenadas" y cubre a la vez los molinos con direcciones opuestas. |

### 2.2 Datos y repositorio

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D48 | Entidad, DAO y repositorio (concreta §3.4 y §3.5) | `@Entity(tableName = "grind_calibrations", foreignKeys = [ForeignKey(GrinderEntity::class, ["id"], ["grinderAId"], onDelete = CASCADE), ForeignKey(…, ["grinderBId"], onDelete = CASCADE)], indices = [Index("grinderAId"), Index("grinderBId"), Index(value = ["grinderAId", "grinderBId", "valueA"], unique = true), Index(value = ["grinderAId", "grinderBId", "valueB"], unique = true)]) data class GrindCalibrationEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val grinderAId: Long, val valueA: Double, val grinderBId: Long, val valueB: Double, val updatedAtEpochMs: Long)`. `@Dao abstract class GrindCalibrationDao` con `observeOf(id: Long): Flow<List<GrindCalibrationEntity>>` (`WHERE grinderAId = :id OR grinderBId = :id`), `observeBetween(a: Long, b: Long)`, `between(a: Long, b: Long)`, `insert`, `update`, `deleteById(id)`, `deleteByIds(ids)` y dos transacciones: `@Transaction open suspend fun save(grinder: Grinder, value: Double, other: Grinder, otherValue: Double, editingId: Long?, nowMs: Long): CalibrationSaveResult` (lee `between`, llama al validador y escribe según el resultado) y `@Transaction open suspend fun replace(…mismos…): CalibrationSaveResult` (recalcula el choque dentro de la transacción, borra los pares en conflicto y aplica `Update` o inserción). En `GrindCalibration.kt`: `sealed interface CalibrationSaveResult { data class Saved(val id: Long); data class Updated(val id: Long); data class Conflict(val conflicts: List<GrindCalibration>); data class Invalid(val errors: List<CalibrationError>) }`. `GrinderRepository` gana `calibrations(grinderId: Long): Flow<List<GrindCalibration>>`, `calibrationPoints(fromId: Long, toId: Long): Flow<List<CalibrationPoint>>`, `suspend fun saveCalibration(grinderId: Long, value: Double, otherId: Long, otherValue: Double, editingId: Long? = null): CalibrationSaveResult`, `suspend fun replaceCalibration(…mismos…): CalibrationSaveResult` y `suspend fun deleteCalibration(id: Long)`. El repositorio carga los dos molinos con `GrinderDao.get`, ajusta los valores con `snap` y escribe en `Dispatchers.IO + NonCancellable`, como el resto de escrituras. | El patrón `@Transaction open suspend fun` en un DAO `abstract class` es el de `syncCatalog`. Recalcular el conflicto dentro de `replace` evita borrar una lista de ids que haya cambiado entre la pregunta y la respuesta. Los molinos se cargan fuera de la transacción porque la validación de escala y paso no depende de las filas de pares. |
| D49 | `isUsed` con pares (discrepancia 1) | `GrinderDao.isUsed` pasa a `SELECT EXISTS(…recipes…) OR EXISTS(…brews…) OR EXISTS(SELECT 1 FROM grind_calibrations WHERE grinderAId = :id OR grinderBId = :id)`. Su único llamador es `syncCatalog`. | Es el cambio mínimo para que una actualización del catálogo no borre pares. El KDoc de `isUsed` y el de `syncCatalog` se actualizan. |

### 2.3 Interfaz

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D50 | Pares en la tarjeta (concreta D25 y §4, rendimiento) | `SessionGrinders` gana `fun calibrations(grinderId: Long): Flow<List<GrindCalibration>>`, implementado en `RepositorySessionGrinders` (delegado al repositorio), en `NoSessionGrinders` (`flowOf(emptyList())`) y en el `FakeGrinders` de `GrindSelectionTest`. En `GrindSelection`, dentro del `flatMapLatest` de la fuente, el `combine` pasa de 4 a 5 flujos: `grinder(sourceId)`, `owned()`, `defaultGrinderId`, `choice` y `calibrations(sourceId)` (`flowOf(emptyList())` sin molino de origen). `grindCardState(source, sourceGrinder, owned, chosenId, defaultId, calibrations: List<GrindCalibration> = emptyList())` orienta con `pointsBetween(source.id, chosen.id)`. Nada cambia en `ScaleSessionManager`: sigue leyendo `grind.current()` al congelar. | Los pares solo se emiten cuando cambia la tabla, no con cada lectura de la báscula (10 Hz). Un solo `Flow` por molino de origen basta para cualquier "Moler con", sin volver a suscribirse al cambiar de molino. |
| D51 | Marcas (concreta §3.3 y §3.7) | `ConversionTags` pinta, en este orden: "sugerido" (`Primary`), "según tu calibración" (`CALIBRATED`, `AntTagColor.Success`, cadena `grind_card_calibrated`), "aproximado" (`ANCHORS`, `Default`) y "al límite del molino" (`AT_LIMIT`, `Warning`). El prefijo "≈" sigue saliendo solo con `ROUNDED`. Nueva función `@StringRes fun GrindFix.linkText(): Int` en `GrindLabels.kt` (`COMPLETE_RANGES` → `grind_card_fix_complete`, `DUPLICATE` → `grind_card_fix_duplicate`, `ADD_CALIBRATION` → `grind_card_fix_calibration`), que usan la tarjeta y el convertidor. | El verde distingue un dato del usuario de una estimación. El `when` exhaustivo evita que el tercer valor caiga en "Duplicar". |
| D52 | Convertidor (concreta §3.7 y D26) | `data class ConverterRow(val grinder: Grinder, val conversion: Conversion, val fix: FixTarget?, val correctable: Boolean)`; `fun converterRows(source: Grinder, value: Double, owned: List<Grinder>, pairs: List<GrindCalibration>): List<ConverterRow>`, con `correctable = source.owned` y `fix = fixFor(source, row, n)` solo en `NoEquivalent`. Cada fila con `correctable` lleva un botón de texto (`converter_correct_<id>`): "Corregir" (`converter_correct`) si hay valor, y "Añadir equivalencia" (`grind_calibration_add`) si no. El enlace del arreglo (`converter_fix_<id>`) se mantiene para `COMPLETE_RANGES` y `DUPLICATE`. Para `ADD_CALIBRATION` solo se muestra si la fila no es `correctable` (si lo es, "Añadir equivalencia" ya hace lo mismo en el sitio). La hoja de "Corregir" fija el molino y el ajuste de origen y solo pide el molino de la fila: con el valor mostrado o, sin equivalencia, `startValue(grinder, null)` (discrepancia 4). `GrindConverterViewModel(repository, sourceId, value)` combina `grinder(sourceId)`, `value`, `owned()`, `catalog()` y `calibrations(sourceId)` (5 flujos, el mismo número que hoy). | Corregir sin salir del convertidor es el recorrido del §3.8, paso 5. Mantener el enlace de los rangos deja las dos salidas de "Sin equivalencia" (completar la curva o calibrar). |
| D53 | Editor de pares compartido y mensajes (concreta §3.3.1 y §3.7; discrepancia 9) | `ui/grinders/CalibrationEditor.kt` (nuevo): `class CalibrationEditor(private val repository: GrinderRepository, private val scope: CoroutineScope)` con `val draft: StateFlow<CalibrationDraft?>`, `val events: Flow<CalibrationEvent>` (`Channel`), `fun open(draft: CalibrationDraft)`, `fun update(transform: (CalibrationDraft) -> CalibrationDraft)`, `fun save()`, `fun replace()` y `fun dismiss()`. `data class CalibrationDraft(val grinderId: Long, val value: Double?, val otherId: Long?, val otherValue: Double?, val editingId: Long? = null, val lockOther: Boolean, val conflicts: List<GrindCalibration> = emptyList(), val invalid: Boolean = false)`. Resultados: `Saved` → cierra sin mensaje (la fila o la lista cambian a la vista); `Updated` → cierra y emite `CalibrationEvent.Updated` (mensaje `grind_calibration_updated`, `AntStatus.Success`); `Conflict` → la hoja sigue abierta con un `AntAlert` de error (`calibration_conflict`) con `pluralStringResource(R.plurals.grind_calibration_conflict, n, lista)` y el botón "Reemplazar" (`calibration_replace`); `Invalid` → `AntAlert` con `grind_calibration_invalid`. Lo usan `GrindConverterViewModel` y `GrinderDetailViewModel`. `ui/grinders/CalibrationSheet.kt` (nuevo): `@Composable fun CalibrationSheet(draft: CalibrationDraft, grinder: Grinder, others: List<Grinder>, texts: GrindTexts, onChange: ((CalibrationDraft) -> CalibrationDraft) -> Unit, onSave: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit)` sobre `AntDrawer` (`testTag("calibration_sheet")`): selector del otro molino (`calibration_grinder`, solo si `!lockOther`), `GrindInput` del molino (`calibration_value`, solo en el detalle) y del otro (`calibration_other_value`), y "Guardar equivalencia" (`calibration_save`, habilitado con los dos valores y el otro molino). Las pantallas ganan `snackbarHost = { AntMessageHost(snackbar) }`. | Una sola máquina de estados para los dos sitios donde se guardan pares, con los tres mensajes del §3.3.1 en un único lugar. `AntDrawer` es el mismo contenedor que `GrinderPicker`. |
| D54 | Detalle: sección "Equivalencias" (concreta §3.7; discrepancia 8; P1) | `ui/grinders/GrinderCalibrations.kt` (nuevo): `@Composable fun GrinderCalibrations(grinder: Grinder, groups: List<CalibrationGroup>, canAdd: Boolean, texts: GrindTexts, onAdd: () -> Unit, onEdit: (GrindCalibration) -> Unit, onDelete: (GrindCalibration) -> Unit, modifier: Modifier)` en una `AntCard` (`testTag("grinder_calibrations")`), entre `Ranges` y `GrindAttribution`. Aparece si `grinder.owned` o si hay pares. `data class CalibrationGroup(val other: Grinder, val pairs: List<GrindCalibration>, val editable: Boolean)`: un grupo por molino (cabecera con `displayName`), pares ordenados por `valueOf(grinder.id)`, grupos en el orden de `GrinderRepository.sorted`. Cada par (`grinder_calibration_<id>`) con editar (`…_edit_<id>`, solo si los dos molinos son míos) y borrar (`…_delete_<id>`, siempre). Un valor fuera de la escala de su molino lleva el `AntTag` `Warning` `grind_out_of_scale` (`…_out_of_scale_<id>`, D30). Sin pares: `grinder_detail_calibrations_empty` (`grinder_calibrations_empty`). "Añadir equivalencia" (`grinder_calibrations_add`) solo si el molino es mío y hay otro molino mío. Borrar abre un `AntModal` (`grinder_calibration_delete_dialog`, `danger = true`), igual que el borrado del molino en esta pantalla. `GrinderDetail(val id: Long, val toCalibrations: Boolean = false)`: con `true`, un `BringIntoViewRequester` lleva la sección a la vista una sola vez (`rememberSaveable`). `GrinderDetailViewModel` combina además `calibrations(id)` y `all()` (los pares pueden apuntar a molinos que ya no son míos), y expone el `CalibrationEditor`. | El §3.7 permite confirmación o "Deshacer", y la única acción de borrar de esta pantalla ya usa `AntModal`. `toCalibrations` hace que "Añade otra equivalencia" lleve a la sección, como pide el §3.7. |
| D55 | Texto de un par (concreta §3.7; discrepancia 5; P2) | `grind_calibration_pair` = `"%1$s %2$s = %3$s %4$s"` con el modelo y `GrindFormat.format` de cada lado; en el detalle, el molino de la pantalla va primero ("Chestnut S3 20 clics = A2 1 · 20"); en el conflicto, el orden del par nuevo. | Reutiliza el formato de toda la app, incluidos los compuestos, sin un formato nuevo. |
| D56 | Fixtures de pares (amplía) | `androidTest/.../grinder/TestGrinders.kt` gana `suspend fun HistoryDatabase.addCalibration(aId: Long, valueA: Double, bId: Long, valueB: Double, nowMs: Long = 1_000L): Long`, que inserta ya en orden canónico. `test/.../grinder/GrinderFixtures.kt` gana `soloRangos` (sin anclas, rangos V60 50–80, AeroPress 40–50 y Prensa francesa 80–120: curva implícita) y `sinCurva` (sin anclas, solo V60). | Los tests de tarjeta, convertidor, detalle y rendimiento necesitan pares sin pasar por la interfaz. |
| D57 | Rendimiento (concreta §4; discrepancia 12) | Desde F5, el montaje de `SessionPerformanceTest` añade dos pares Uno↔Dos (Uno 10 = Dos 14 y Uno 30 = Dos 50) con `addCalibration`, para que la tarjeta trabaje con calibración. Línea base en F0 (tres pasadas por caso, sin código de la enmienda) y comparación en F8 con el criterio de D32. | Medir la tarjeta sin pares no probaría el camino nuevo. |
| D58 | Puerta del A2 (concreta §3.6) | `test/.../grinder/CatalogCurveTest.kt` (nuevo) lee `catalog.json` con `GrinderCatalogFile.parse`. Antes de la decisión, imprime en `System.out` (queda en `app/build/test-results/testDebugUnitTest/TEST-dev.josueamaya.pourline.grinder.CatalogCurveTest.xml`, `<system-out>`) `METHOD_LEVELS`, la recta del A2 con y sin `MOKA` (`a`, `b` y el valor en los 7 niveles) y las tablas S3 → A2 (0, 10 … 90) y A2 → S3 (0, 20 … 120) con valor y razones, y solo comprueba que las dos curvas existan y crezcan. Después de la decisión, afirma la tabla aprobada en los dos sentidos con `ANCHORS`. El informe va en la respuesta del implementador, sin documento aparte. Si el rango de moka cambia o se quita: `catalog.json` pasa a `"version": 2`, la nota del A2 lo explica y `GrinderCatalogFileTest` se ajusta. Haya cambio o no, la última frase de la nota del A2 ("la conversión da Sin equivalencia…") se corrige, porque ya no es cierta (las notas no se siembran, así que eso solo no sube la versión). | La tabla queda como test de regresión: un cambio del catálogo o de la curva que mueva la conversión S3↔A2 se ve en un test. |
| D59 | Compatibilidad de las claves (concreta D5) | `GrindConverter` pierde `method` y `GrinderDetail` gana `toCalibrations = false`. No se añade `ignoreUnknownKeys`. | Una pila guardada con `method` solo se restauraría tras una muerte del proceso con la versión anterior instalada, y una actualización de la app no restaura la pila. El riesgo es bajo (R6). |
| D60 | Retirada de la API antigua (amplía) | La sobrecarga antigua `convert(value, method, from, to)` convive con la nueva de F1 a F5 (con `@Deprecated("Enmienda 1: usa convert(value, from, to, pairs)")`). F6 la borra junto con `byRange`, `byAnchors`, `fineEnd`, `coarseEnd`, sus tests y las cadenas `converter_method`/`converter_no_method`. Al final, `grep -rn "convert(.*method" app/src` no encuentra nada. | Cada fase compila y queda en verde sin cambiar la tarjeta y el convertidor a la vez. |

## 3. Fases

Orden: F0 → F1 → F2 (**parada**) → F3 → F4 → F5 → F6 → F7 → F8. F3 y F4 no tocan la interfaz, pero solo se adelantan
durante la espera de la puerta si el usuario lo autoriza de forma explícita. F5 y las siguientes nunca empiezan antes
de su decisión. F7 depende de F4 y de `CalibrationEditor`/`CalibrationSheet` (F6).

| Fase | Contenido | Tamaño | Estado |
|---|---|---|---|
| F0 | Línea base, dispositivos y versión de la base de datos | S | Pendiente |
| F1 | Dominio puro: `GrindCurve`, `METHOD_LEVELS`, pares, validador y la nueva `convert` | L | Pendiente |
| F2 | **Puerta del Femobook A2** (parada) y catálogo | S | Hecha: decidido quitar la moka (catálogo versión 2) |
| F3 | Datos v7: entidad, DAO, migración 6 → 7 e `isUsed` | M | Pendiente |
| F4 | Repositorio y `SessionGrinders` | M | Pendiente |
| F5 | Tarjeta y sesión con calibración | M | Pendiente |
| F6 | Convertidor sin método, "Corregir" y retirada de la API antigua | L | Pendiente |
| F7 | Detalle del molino: sección "Equivalencias" | M | Pendiente |
| F8 | Verificación: suites, rendimiento, E2E por adb, móvil del usuario y cierre | M | Pendiente |

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app`, nunca con
`./gradlew build` ni con builds limpios.

- **Tests JVM:** `./gradlew :app:testDebugUnitTest --tests "<clase>"`.
- **Tests instrumentados:** siempre con `ANDROID_SERIAL=emulator-5556` (AVD `SG_API34`, en `en-US`):
  `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<clases>`.
  El `emulator-5554` es del usuario y no se usa. Si `adb devices -l` muestra otro dispositivo, se pregunta.
- **Sin ARTEMIS.** Los tests de interfaz localizan por `testTag` y leen los textos esperados con `stringResource` del
  contexto de la app, por el idioma del emulador.
- **Fallos previos que no bloquean:** `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap` (idioma del
  emulador) y la oscilación del caso Experto de `SessionPerformanceTest` (su p95 ya supera 32 ms en `develop`).
- **Lint:** `./gradlew :app:lintDebug` en F5, F6, F7 y F8, sin avisos nuevos en los archivos tocados.
- **Cadenas nuevas:** en `values/strings.xml` y `values-en/strings.xml`, junto a las `grind_card_*` y `converter_*`
  (no al final del archivo), para reducir el choque con SPEC-008, que añade unas 200 líneas a los dos archivos.
- **Commits:** por defecto el implementador no hace commits. Si se le permiten, uno por fase, en Conventional Commits y
  en inglés, sin `Co-Authored-By` ni atribución. El del plan, cuando se apruebe:
  `docs(specs): add grind calibration implementation plan (SPEC-006 amendment 1)`.

### F0. Línea base, dispositivos y versión de la base de datos

**Objetivo:** conocer el punto de partida y fijar la versión de la base de datos antes de tocar código.

**Pasos:**

1. `adb devices -l`: confirmar `emulator-5556`. Si hay más dispositivos además de `emulator-5554`, preguntar.
2. Versión de la base de datos (D40):
   - `git fetch` (si hay remoto) y `git show develop:app/src/main/kotlin/dev/josueamaya/pourline/history/HistoryDatabase.kt | grep "version ="`;
   - `git ls-tree --name-only develop app/schemas/dev.josueamaya.pourline.history.HistoryDatabase/`;
   - `git -C /Users/josue/projects/dot-scale status --short -- app/src/main/kotlin/dev/josueamaya/pourline/history app/schemas`
     y `git log develop..feat/guided-tour --name-only -- app/src/main/kotlin/dev/josueamaya/pourline/history app/schemas`.
   - Si `develop` sigue en v6 y SPEC-008 no toca la base de datos, se sigue con v7. Si no, se aplica D40 antes de F3.
3. `./gradlew :app:testDebugUnitTest`: anotar el número de tests, fallos y omitidos.
4. `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest`: anotar el total y los fallos conocidos.
5. `./gradlew :app:lintDebug`: anotar los errores y los avisos.
6. `SessionPerformanceTest`, tres pasadas por caso (`basicPourKeepsTheFrameTimeUnderTheLimit` y
   `expertPourWithTheChartKeepsTheFrameTimeUnderTheLimit`), leyendo `adb -s emulator-5556 logcat -d -s SessionPerf`:
   p50, p95, `over32` y `max` de cada pasada, y la mediana del p95 (§7.2).

**Salida:** las cifras van a la cabecera del plan (sección "Línea base"). No hay cambios de código ni commit.

### F1. Dominio puro

**Objetivo:** la curva de grosor, `METHOD_LEVELS`, los pares, el validador y la nueva `convert`, sin Android y con
tests JVM. La tarjeta y el convertidor siguen con la firma antigua.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `grinder/GrindMath.kt` | `internal fun interpolate(points: List<Pair<Double, Double>>, x: Double): Double?`, movida desde `GrindConverter` sin cambios (D42). |
| `grinder/GrindCurve.kt` (nuevo) | `METHOD_LEVELS` (D43) y `GrindCurve` con `Explicit`, `Linear` y `of(grinder)` (D42). |
| `grinder/GrindCalibration.kt` (nuevo) | `CalibrationPoint`, `GrindCalibration` y sus funciones de orientación, `pointsBetween` y `CalibrationSaveResult` (D46 y D48). |
| `grinder/CalibrationValidator.kt` (nuevo) | `CalibrationValidator.check`, `CalibrationCheck` y `CalibrationError` (D47). |
| `grinder/GrindConverter.kt` | `ConversionReason.CALIBRATED`; nueva `convert(value, from, to, pairs)` (D44); la antigua con `@Deprecated` (D60). |
| `grinder/GrindCardState.kt` | `GrindFix.ADD_CALIBRATION`, `FixTarget` y `fixFor(from, to, pairCount)` (D45). Nada más cambia todavía. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../grinder/GrinderFixtures.kt` | `soloRangos` y `sinCurva` (D56). |
| `test/.../grinder/GrindCurveTest.kt` (nuevo) | `` `an explicit curve interpolates every anchor of the grinder` `` (Uno: 8 → 1, 14 → 2, 20 → 3, 32 → 5); `` `an explicit curve extrapolates with the end segments` `` (Uno 2 → 0,0 y 38 → 6,0); `` `an inverted grinder gives growing levels for lower settings` `` (Inverso 40 → 1, 25 → 3, 10 → 5); `` `a single anchor is ignored and the ranges decide` ``; `` `an implicit curve is the least squares line of the range midpoints` `` (`soloRangos`, `a` y `b` contra el cálculo a mano con `METHOD_LEVELS`); `` `repeated points count once` `` (Kalita Wave, Chemex y Origami con el mismo rango dan la misma recta que uno solo); `` `no curve with fewer than two distinct levels` `` (`sinCurva`); `` `no curve when the slope contradicts the direction` `` (rangos que bajan en `HIGHER_IS_COARSER` y suben en `HIGHER_IS_FINER`); `` `level and value are inverse` `` (para Uno, Dos, Tres, Inverso y `soloRangos`, de `scaleMin − 5` a `scaleMax + 5` cada 0,5: `value(level(v)) == v` con 1e-9). |
| `test/.../grinder/CatalogLevels.kt` (nuevo, ayuda de test) | `fun methodLevels(entries: List<CatalogEntry>): Map<BrewMethod, Double>` (D43). |
| `test/.../grinder/MethodLevelsTest.kt` (nuevo) | `` `METHOD_LEVELS matches the catalog` ``: `methodLevels(GrinderCatalogFile.parse(File("src/main/assets/grinders/catalog.json").readText()).getOrThrow().entries)` es igual a `METHOD_LEVELS` (mismas claves; cada valor con 1e-9). `` `methods without any predefined range have no level` `` (`OTHER`). |
| `test/.../grinder/CalibrationValidatorTest.kt` (nuevo) | `` `a valid pair is ok` ``; `` `the same value on one side updates that pair` `` (en A y en B); `` `an exact duplicate is an update` ``; `` `a pair that breaks monotonicity is a conflict listing the pair` `` (el ejemplo del §3.3.1, con Uno y Dos); `` `a pair that contradicts several pairs lists all of them` ``; `` `an update that contradicts another pair is a conflict` ``; `` `editing a pair to the value of another pair is a conflict` ``; `` `the edited pair itself is ignored` ``; `` `the same grinder twice is invalid` ``; `` `a grinder that is not owned is invalid` ``; `` `a value outside the scale is invalid` ``; `` `a value off the step is invalid` `` (Dos 30,25); `` `opposite directions are consistent when coarser matches coarser` `` (Uno↔Inverso: (10, 40) y (30, 10) son válidos; (10, 10) con (30, 40) es conflicto). |
| `test/.../grinder/GrindConverterTest.kt` | Casos nuevos con la firma nueva (los antiguos siguen hasta F6): `` `the same source gives the same result for every method` `` (la firma no tiene método: se comprueba que `GrindCardState` y `converterRows` darán lo mismo; aquí, que Uno 20 → Dos sea 30 con `ANCHORS` sin que importe `ranges`); `` `curves convert between grinders with no anchors in common` `` (Uno → `soloRangos`, con `ANCHORS`); `` `curves keep the coarse to fine position with an inverted grinder` `` (Uno 20 → Inverso 25 y vuelta, criterio rehecho); `` `two or more pairs interpolate, are exact on a pair and extrapolate` `` (Uno 10 = Dos 14 y Uno 30 = Dos 50: 20 → 32 `CALIBRATED`; 10 → `Exact(14)`; 35 → 59 `CALIBRATED`; nunca `ANCHORS`); `` `pairs work in both directions` `` (Dos 32 → Uno 20); `` `calibration keeps the position with an inverted grinder` `` (pares Uno↔Inverso en orden inverso); `` `one pair shifts the curve conversion through the pair` `` (Uno 20 = Dos 31: Uno 20 → `Exact(31)`; Uno 30 → 46,667 + 1 → 47,5 con `CALIBRATED` y `ROUNDED`); `` `one pair without a curve has no equivalent` `` (Uno → `sinCurva` con un par: `NoEquivalent`); `` `rounding and the limit still apply after calibrating` `` (Uno 40 con los dos pares → 68 → 60 con `CALIBRATED`, `AT_LIMIT`); `` `a pair outside the edited scale is rounded or limited` `` (discrepancia 3); `` `the same grinder is exact without pairs or curves` ``. |
| `test/.../grinder/GrindCardStateTest.kt` | Solo `fixFor`: `` `one pair gives add calibration on the chosen grinder` ``; `` `the fix points to the grinder without a curve` `` (origen sin curva y elegido con curva; los dos sin curva → el elegido); `` `an own grinder gets complete ranges and a predefined one duplicate` ``. |

**Verificación:**
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.grinder.*"`.
`grep -n "import android\|import androidx" app/src/main/kotlin/dev/josueamaya/pourline/grinder/GrindCurve.kt app/src/main/kotlin/dev/josueamaya/pourline/grinder/GrindCalibration.kt app/src/main/kotlin/dev/josueamaya/pourline/grinder/CalibrationValidator.kt app/src/main/kotlin/dev/josueamaya/pourline/grinder/GrindConverter.kt`
no encuentra nada.

**Salida:** todos los tests de `grinder.*` en verde, incluidos los antiguos de `GrindConverterTest`; `METHOD_LEVELS`
recalculado coincide con la constante.

Commit sugerido: `feat(grinder): add grind curves, calibration pairs and method-independent conversion`.

### F2. Puerta del Femobook A2 (parada) y catálogo

**Objetivo:** presentar al usuario `METHOD_LEVELS`, la curva implícita del A2 y la tabla S3 → A2, y **detenerse**
hasta que decida si el rango de moka del A2 se mantiene, se corrige o se quita (§3.6).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `test/.../grinder/CatalogCurveTest.kt` (nuevo) | Antes de la decisión: `` `gate report` `` (imprime, D58) y `` `the A2 has a growing curve with and without moka` ``. El A2 sin moka se construye con `a2.copy(ranges = a2.ranges - BrewMethod.MOKA)`. Las conversiones usan `GrindConverter.convert(v, s3, a2, emptyList())` con los molinos del catálogo con ids distintos de 0. |

**Pasos:**

1. `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.grinder.CatalogCurveTest"` y leer el
   `<system-out>` del XML de resultados (D58).
2. Presentar en la respuesta, sin documento aparte:
   - `METHOD_LEVELS` (los 9 métodos);
   - la recta del A2 con y sin moka (`a`, `b`) y las anclas equivalentes de `EXTRA_FINE` a `EXTRA_COARSE`;
   - la tabla S3 → A2 para 0, 10 … 90 clics del S3, con y sin moka, con el valor en formato del A2 ("1 · 20") y sus
     marcas (`ROUNDED`, `AT_LIMIT`);
   - la tabla A2 → S3 para 0, 20 … 120, con y sin moka;
   - la recomendación del implementador.
3. **Parada.** No se toca la interfaz (F5 en adelante) hasta la decisión. F3 y F4 solo con autorización explícita.

**Estimación previa** (catálogo v1, calculada al escribir el plan; manda lo que dé el test):

| | `a` | `b` | EF | F | MF | M | MC | C | EG |
|---|---|---|---|---|---|---|---|---|---|
| A2 con moka | −42,63 | 36,68 | −42,6 | −6,0 | 30,7 | 67,4 | 104,1 | 140,8 | 177,5 |
| A2 sin moka | −7,98 | 26,39 | −8,0 | 18,4 | 44,8 | 71,2 | 97,6 | 124,0 | 150,4 |

| S3 (clics) | 0 | 10 | 20 | 30 | 40 | 50 | 60 | 70 | 80 | 90 |
|---|---|---|---|---|---|---|---|---|---|---|
| A2 con moka | 0 (límite) | 15 | 31 | 47 | 63 | 79 | 95 | 111 | 120 (límite) | 120 (límite) |
| A2 sin moka | 22 | 34 | 45 | 57 | 68 | 80 | 91 | 102 | 114 | 120 (límite) |

| A2 (clics) | 0 | 20 | 40 | 60 | 80 | 100 | 120 |
|---|---|---|---|---|---|---|---|
| S3 con moka | 0 | 13 | 25 | 38 | 51 | 63 | 76 |
| S3 sin moka | 0 (límite) | 0 (límite) | 15 | 33 | 50 | 68 | 85 |

Con moka, el A2 se queda sin margen por arriba a partir de S3 80 (prensa francesa). Sin moka, el S3 se queda sin
margen por abajo a partir de A2 20.

**Después de la decisión:**

| Decisión | Cambios |
|---|---|
| Mantener `MOKA 10–18` | `catalog.json` sigue en `version` 1. Solo se corrige la última frase de la nota del A2 (D58). |
| Corregir el rango (con otra fuente citada) | `catalog.json` → `"version": 2`; el rango nuevo y la fuente en la nota del A2; `GrinderCatalogFileTest` (`assertEquals(2, data.version)` y el rango del A2). |
| Quitarlo | `catalog.json` → `"version": 2`; sin `MOKA` en el A2 y el motivo en la nota; `GrinderCatalogFileTest` igual que arriba. |

En los tres casos, `CatalogCurveTest` sustituye `gate report` por `` `S3 and A2 convert both ways with the approved curves` ``,
que afirma la tabla aprobada S3 → A2 (0 a 90) y A2 → S3 (0 a 120), con `ANCHORS` en todos los valores. Con
`version` 2, `RealCatalogSeedTest` y `GrinderCatalogSeederTest` se vuelven a pasar (el sembrador sincroniza al cambiar
la versión).

**Verificación:**
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.grinder.*"` y, con `version` 2,
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.grinder.RealCatalogSeedTest,dev.josueamaya.pourline.grinder.GrinderCatalogSeederTest`.

**Salida:** el usuario ha decidido sobre el rango de moka; `CatalogCurveTest` afirma la tabla aprobada.

Commit sugerido: `feat(grinder): pin the approved Femobook A2 grind curve` (y, si cambia el catálogo,
`fix(grinder): revise the Femobook A2 moka range in the catalog`).

### F3. Datos v7

**Objetivo:** la tabla `grind_calibrations` con la migración 6 → 7 (o la versión libre de D40), el DAO con sus
transacciones e `isUsed` con pares.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `grinder/GrindCalibrationEntity.kt` (nuevo) | `GrindCalibrationEntity` (D48) y `fun GrindCalibrationEntity.toCalibration(): GrindCalibration`. |
| `grinder/GrindCalibrationDao.kt` (nuevo) | `GrindCalibrationDao` con las consultas, `save` y `replace` transaccionales y el orden canónico (D46 y D48). |
| `history/HistoryDatabase.kt` | `version = 7`; `GrindCalibrationEntity::class` en `entities`; `abstract fun grindCalibrationDao(): GrindCalibrationDao`; la v7 en el KDoc; `MIGRATION_6_7` al final de `addMigrations` en `create()`. |
| `history/Migrations.kt` | `val MIGRATION_6_7: Migration = object : Migration(6, 7) { … }` con el `CREATE TABLE` y los 4 `CREATE INDEX` copiados de `7.json` (como `MIGRATION_5_6`). Sin tocar datos. |
| `grinder/GrinderDao.kt` | `isUsed` con `grind_calibrations` y los KDoc de `isUsed` y `syncCatalog` (D49). |
| `app/schemas/dev.josueamaya.pourline.history.HistoryDatabase/7.json` (nuevo, generado) | Lo genera `:app:compileDebugKotlin`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../history/HistoryMigrationTest.kt` | `migrates6To7CreatingGrindCalibrations`: una v6 con molinos, una receta y una preparación con molino; tras `runMigrationsAndValidate(NAME, 7, true, MIGRATION_6_7)`, `grind_calibrations` existe vacía; `PRAGMA foreign_key_list` da las dos FK a `grinders` con `CASCADE`; `PRAGMA index_list` da los 4 índices, dos de ellos únicos; el resto de filas no cambia. `migrates1To2To3To4To5To6To7InAChain`. `migrates6To7ThenRoomReadsEverything`: Room lee molinos, recetas y preparaciones e inserta un par. `aFreshInstallIsCreatedAtVersion6WithTheBuiltIns` pasa a `…Version7…`. Las cinco aperturas con Room (líneas 131, 327, 401, 514 y 668) añaden `MIGRATION_6_7`. Helper `seedV6` (nuevo). |
| `androidTest/.../grinder/GrindCalibrationDaoTest.kt` (nuevo) | `saveStoresThePairInCanonicalOrder` (se guarda B < A al revés y se lee con A < B); `observeOfListsThePairsOnEitherSide`; `saveWithARepeatedValueUpdatesThePair`; `saveWithAConflictWritesNothing`; `replaceDeletesTheConflictsAndSavesInOneTransaction`; `replaceRollsBackWhenTheInsertFails` (un id de molino inexistente hace fallar la FK; los pares en conflicto siguen ahí); `uniqueIndexesRejectADuplicateValue` (inserción directa); `deletingAGrinderDeletesItsPairs` (`deleteRelabeling`); `editingAGrinderKeepsItsPairs` (`GrinderDao.save` con el mismo id: `@Update`, sin `REPLACE`). |
| `androidTest/.../grinder/GrinderDaoTest.kt` | `isUsedIsTrueForAGrinderWithPairs`. |
| `androidTest/.../grinder/TestGrinders.kt` | `HistoryDatabase.addCalibration(...)` (D56). |

**Verificación:** `./gradlew :app:compileDebugKotlin` (genera `7.json`).
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.history.HistoryMigrationTest,dev.josueamaya.pourline.grinder.GrindCalibrationDaoTest,dev.josueamaya.pourline.grinder.GrinderDaoTest`.

**Salida:** la base de datos en v7 con `7.json` versionado y los tests de migración y del DAO en verde.

Commit sugerido: `feat(grinder): store grind calibration pairs, migrating the database to v7`.

### F4. Repositorio y `SessionGrinders`

**Objetivo:** exponer los pares como `Flow`, guardar, actualizar, reemplazar y borrar desde el repositorio, y llevar
los pares a la sesión sin tocar todavía la tarjeta.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `grinder/GrinderRepository.kt` | `calibrations`, `calibrationPoints`, `saveCalibration`, `replaceCalibration` y `deleteCalibration` (D48). `duplicateDraft` no cambia (no copia pares). |
| `session/GrindSelection.kt` | `SessionGrinders.calibrations(grinderId)`; implementación en `RepositorySessionGrinders` y `NoSessionGrinders` (D50). `GrindSelection` todavía no la usa. |
| `test/.../session/GrindSelectionTest.kt` | `FakeGrinders` implementa `calibrations` con un `MutableStateFlow`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../grinder/GrinderRepositoryTest.kt` | `saveCalibrationReturnsSavedAndTheFlowEmitsIt`; `saveCalibrationSnapsTheValues`; `aRepeatedValueReturnsUpdated`; `aContradictionReturnsConflictAndWritesNothing`; `replaceCalibrationDeletesTheConflictsAndSaves`; `aGrinderThatIsNotOwnedReturnsInvalid`; `calibrationPointsAreOrientedFromTheFirstGrinder` (los dos sentidos); `deletingAGrinderDeletesItsPairs` (`delete`); `duplicatingAPredefinedDoesNotCopyItsPairs`; `deleteCalibrationRemovesOnlyThatPair`. |
| `androidTest/.../grinder/GrinderCatalogSeederTest.kt` | `aCatalogUpdateDoesNotTouchThePairs` (otra versión del catálogo con los mismos molinos: los pares siguen igual); `aPredefinedWithPairsThatLeavesTheCatalogIsRetiredNotDeleted` (dos predefinidos con un par, uno se quita de mis molinos y desaparece del catálogo: queda `retired` y el par sigue, D49). |

**Verificación:**
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.session.GrindSelectionTest"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.grinder.GrinderRepositoryTest,dev.josueamaya.pourline.grinder.GrinderCatalogSeederTest`.

**Salida:** el repositorio cubre todos los casos del criterio "Tests del repositorio" del §5.

Commit sugerido: `feat(grinder): expose calibration pairs from the grinder repository`.

### F5. Tarjeta y sesión con calibración

**Objetivo:** la tarjeta de molienda de Preparar y del paso 2 convierte con la nueva `convert`, sin método, con los
pares como `Flow`, y muestra "según tu calibración" y "Añade otra equivalencia". **Solo empieza después de la
decisión de F2.**

**Archivos:**

| Archivo | Cambio |
|---|---|
| `grinder/GrindCardState.kt` | `grindCardState(…, calibrations: List<GrindCalibration> = emptyList())`; `convertOutcome` usa `GrindConverter.convert(value, from, to, calibrations.pointsBetween(from.id, to.id))`; `GrindOutcome.NoEquivalent(fix: GrindFix, grinderId: Long)` con `fixFor` (D45). `ConverterArgs` todavía con `method` (cambia en F6). |
| `session/GrindSelection.kt` | `combine` de 5 flujos con `calibrations(sourceId)` (D50). |
| `ui/grinders/GrindLabels.kt` | `GrindFix.linkText()` (D51). |
| `ui/grinders/GrindCard.kt` | `ConversionTags` con `CALIBRATED` (D51); el enlace usa `outcome.fix.linkText()` y `onFix(outcome.fix, outcome.grinderId)`. Se conserva el `testTag("grind_card_ruler")` (SPEC-008 le añade `tourAnchor`). |
| `NavigationKeys.kt` | `GrinderDetail(val id: Long, val toCalibrations: Boolean = false)` (D54, D59). |
| `Navigation.kt` | `fixGrinderKey` con `when`: `COMPLETE_RANGES` → `GrinderEdit(id = id, openRanges = true)`, `DUPLICATE` → `GrinderEdit(fromId = id, openRanges = true)`, `ADD_CALIBRATION` → `GrinderDetail(id, toCalibrations = true)`. Hasta F7, el detalle se abre arriba del todo: la pantalla recibe `key.toCalibrations` cuando exista la sección. |
| `res/values*/strings.xml` | `grind_card_calibrated` y `grind_card_fix_calibration` (§3.6 del plan). |
| `androidTest/.../ui/session/SessionPerformanceTest.kt` | Dos pares Uno↔Dos en el montaje (D57). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../grinder/GrindCardStateTest.kt` | Las expectativas de 28 pasan a 30 y "Dos · 28 clics" a "Dos · 30 clics" (§1.2). Nuevos: `` `the method of the recipe does not change the conversion` `` (V60, `OTHER` y `null` dan lo mismo); `` `two pairs give a calibrated value` `` (Uno 20 → Dos 32 con `CALIBRATED`); `` `pairs with other grinders are ignored` ``; `` `one pair and no curve gives add calibration on the chosen grinder` ``; `` `no equivalent points to the grinder without a curve` ``; `` `the snapshot saves the calibrated value` ``. |
| `test/.../session/GrindSelectionTest.kt` | 28 → 30. `` `new pairs recompute the card without changing the source` ``: emitir dos pares en el `FakeGrinders` cambia el estado a 32 con `CALIBRATED`. |
| `androidTest/.../ui/grinders/GrindCardTest.kt` | `changingGrindWithShowsTheConvertedSetting`: 28 → 30 (el comentario explica las anclas). Nuevo `twoPairsShowPerYourCalibration`: con Uno 10 = Dos 14 y Uno 30 = Dos 50, la receta Uno 20 con "Moler con" Dos muestra 32 y `grind_card_marks` contiene `grind_card_calibrated`. Nuevo `onePairWithoutACurveOffersToAddAnotherEquivalence`: un molino propio sin curva y un par → `grind_card_fix` con `grind_card_fix_calibration` → abre `GrinderDetail` (`grinder_detail` visible). `noEquivalenceOffersToCompleteTheRangesOfMyGrinder` no cambia (el molino sin curva es el elegido). |
| `androidTest/.../ui/session/GrindBrewFlowTest.kt` | 130 → 150 y el comentario (Uno 30 está en L = 4,667; Tres 150). |

**Verificación:**
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.grinder.*" --tests "dev.josueamaya.pourline.session.*" --tests "dev.josueamaya.pourline.TabNavigationTest"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.grinders.GrindCardTest,dev.josueamaya.pourline.ui.session.GrindBrewFlowTest`.
Una pasada de `SessionPerformanceTest` para ver que el montaje con pares funciona (la comparación es en F8).
`./gradlew :app:lintDebug`.

**Salida:** la tarjeta convierte sin método y con calibración; el convertidor sigue con la firma antigua.

Commit sugerido: `feat(grinder): convert the grind card with calibration pairs and grind curves`.

### F6. Convertidor sin método, "Corregir" y retirada de la API antigua

**Objetivo:** el convertidor sin selector de método, con "Corregir"/"Añadir equivalencia" y la hoja de pares;
`ConverterArgs` y la clave sin `method`; y fuera la conversión por rango del método.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `grinder/ConverterRows.kt` | `ConverterRow(grinder, conversion, fix: FixTarget?, correctable)` y `converterRows(source, value, owned, pairs)` (D52). |
| `grinder/GrindCardState.kt` | `ConverterArgs(val sourceId: Long, val value: Double)`; `grindCardState` lo crea sin método. |
| `grinder/GrindConverter.kt` | Se borran la sobrecarga antigua, `byRange`, `byAnchors`, `fineEnd` y `coarseEnd` (D60). |
| `NavigationKeys.kt` | `GrindConverter(val sourceId: Long? = null, val value: Double? = null)`. |
| `Navigation.kt` | Líneas 168, 188 y 291: `GrindConverter(it.sourceId, it.value)`. Entrada del convertidor: `GrindConverterViewModel(deps.grinderRepository, key.sourceId, key.value)`; `onFix` con `fixGrinderKey`. |
| `ui/grinders/GrindSettingField.kt` | `ConverterArgs(current.id, choice.value)`. |
| `ui/grinders/CalibrationEditor.kt` (nuevo) | D53. |
| `ui/grinders/CalibrationSheet.kt` (nuevo) | D53. |
| `ui/grinders/GrindConverterViewModel.kt` | Sin `method`; `calibrations(sourceId)` en el `combine`; `val editor = CalibrationEditor(repository, viewModelScope)`; `fun correct(row: ConverterRow)` abre el borrador con `grinderId = source.id`, `value = valor de origen`, `otherId = row.grinder.id`, `otherValue = row.conversion.valueOrNull`, `lockOther = true`. |
| `ui/grinders/GrindConverterScreen.kt` | Sin `NO_METHOD`, `Methods` ni el `AntSelect` `converter_method`; `GrindInput(start = startValue(ui.source, null), testTag = "converter_value")`; en `ConverterRowItem`, el botón `converter_correct_<id>` y el enlace `converter_fix_<id>` según D52; `CalibrationSheet` si hay borrador; `snackbarHost = { AntMessageHost(snackbar) }` y un `LaunchedEffect` que muestra `grind_calibration_updated`. Se conservan los tags `converter_source`, `converter_value`, `converter_row_<id>`, `converter_value_<id>` y `converter_attribution`. |
| `res/values*/strings.xml` | Fuera `converter_method` y `converter_no_method`; nuevas `converter_correct`, `grind_calibration_*` (§3.6 del plan). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../grinder/ConverterRowsTest.kt` | Reescrito sin método: una fila por molino mío distinto del origen, en el orden de mis molinos; `` `each row uses only the pairs with its grinder` ``; `` `rows are correctable only when the source is mine` ``; `` `a row without equivalent carries the fix of the grinder without a curve` ``; `` `one pair without a curve gives add calibration` ``. |
| `test/.../grinder/GrindConverterTest.kt` | Se borran los casos de la firma antigua (rango, extrapolación del rango y anclas comunes). Se conservan, con la firma nueva, el redondeo al paso (entero y 0,5), `AT_LIMIT` y el mismo molino. |
| `test/.../grinder/GrindCardStateTest.kt` | `ConverterArgs(1, 20.0)` en lugar de `ConverterArgs(1, V60, 20.0)` (dos sitios). |
| `androidTest/.../ui/grinders/GrindConverterScreenTest.kt` | `chooseASourceAndASettingAndEachOfMyOtherGrindersGetsARow` (renombrado): sin `converter_method` (se comprueba que no existe); Dos 30 y Tres "2 · 2 · 0". Nuevos: `correctSavesAPairAndTheRowShowsTheCorrectedValue` (origen Uno 20, propio; `converter_correct_$dos` → `calibration_sheet` con 30 → `calibration_other_value_0_inc` dos veces → 31 → `calibration_save` → la fila muestra 31, sin marcas); `aSecondPairGivesPerYourCalibrationInBetween` (sigue: Uno 30 → corregir a 48 → Uno 25 → la fila de Dos muestra 39,5 y `grind_card_calibrated`; discrepancia 13); `aContradictingCorrectionShowsTheConflictAndReplaceSavesIt` (`calibration_conflict` y `calibration_replace`); `aRepeatedValueShowsEquivalenceUpdated` (mensaje `grind_calibration_updated`); `noCorrectWhenTheSourceIsNotMine` (origen del catálogo sin "Lo tengo": ningún `converter_correct_*`); `noEquivalenceOffersAddEquivalenceWhenTheSourceIsMine`. `aPredefinedGrinderShowsTheSourceAndNoEquivalenceOffersTheFix` sigue igual y además comprueba `converter_correct_$bareId`. |
| `androidTest/.../ui/grinders/GrindShortcutTest.kt` | `theCardShortcutOpensTheConverterWithTheSourceAndSetting` (renombrado): 28 → 30. |
| `androidTest/.../ui/tools/ToolsScreenTest.kt` | `theGrindConverterOpensFromTheToolsAndBackReturns` se vuelve a pasar sin cambios. |

**Verificación:**
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.grinder.*" --tests "dev.josueamaya.pourline.session.*" --tests "dev.josueamaya.pourline.TabNavigationTest"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.grinders.GrindConverterScreenTest,dev.josueamaya.pourline.ui.grinders.GrindShortcutTest,dev.josueamaya.pourline.ui.grinders.GrindCardTest,dev.josueamaya.pourline.ui.tools.ToolsScreenTest`.
`grep -rn "converter_method\|converter_no_method\|byRange\|byAnchors" app/src` y
`grep -rn "\.method, it\.value\|key\.method" app/src/main/kotlin/dev/josueamaya/pourline/Navigation.kt` no encuentran nada.
`./gradlew :app:lintDebug` (sin `UnusedResources` nuevos).

**Salida:** no queda ningún uso del método en la conversión (§1.2); el convertidor guarda pares.

Commit sugerido: `feat(grinder): drop the method from the converter and correct rows with calibration pairs`.

### F7. Detalle del molino: sección "Equivalencias"

**Objetivo:** listar, añadir, editar y borrar pares desde el detalle de un molino, con los mensajes del §3.3.1, y que
"Añade otra equivalencia" lleve a la sección.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/grinders/GrindersViewModel.kt` | `GrinderDetailUi.Ready` gana `groups: List<CalibrationGroup>`, `canAddCalibration: Boolean` y `others: List<Grinder>` (mis otros molinos sin retirados); `GrinderDetailViewModel` combina además `repository.calibrations(id)` y `repository.all()`; `val editor = CalibrationEditor(repository, viewModelScope)`; `fun addCalibration()`, `fun editCalibration(pair: GrindCalibration)`, `fun deleteCalibration(id: Long)`. |
| `ui/grinders/GrinderCalibrations.kt` (nuevo) | `GrinderCalibrations` y `CalibrationGroup` (D54) y el texto del par (D55). |
| `Navigation.kt` | La entrada de `GrinderDetail` pasa `key.toCalibrations` a la pantalla. |
| `ui/grinders/GrinderDetailScreen.kt` | Parámetro `toCalibrations: Boolean = false`; la sección entre `Ranges` y `GrindAttribution`; `BringIntoViewRequester` con `toCalibrations`; `CalibrationSheet` (en el detalle, con `calibration_value` y, al añadir, `calibration_grinder`); `AntModal` del borrado del par; `snackbarHost = { AntMessageHost(snackbar) }`. |
| `res/values*/strings.xml` | `grinder_detail_calibrations`, `grinder_detail_calibrations_empty`, `grind_calibration_delete_title`, `grind_calibration_edit_description`, `grind_calibration_delete_description` (§3.6 del plan). |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/grinders/GrinderDetailScreenTest.kt` | `anOwnGrinderWithoutPairsShowsTheEmptyText`; `aPredefinedIDoNotHaveWithoutPairsHasNoSection`; `addingAPairListsItGroupedByGrinderAndSortedByThisGrinder` (dos pares con Dos y uno con Tres: dos grupos; los de Dos ordenados por el valor de Uno); `editingAPairChangesItsValues`; `deletingAPairAsksAndRemovesIt` (`grinder_calibration_delete_dialog`); `aContradictingPairShowsTheConflictAndReplaceSavesIt`; `aRepeatedValueShowsEquivalenceUpdated`; `aPairWithAGrinderThatIsNoLongerMineCanOnlyBeDeleted` (sin `grinder_calibration_edit_<id>`); `aValueOutsideTheEditedScaleShowsOutOfScale` (se edita la escala de un molino propio por el repositorio); `noAddWithoutAnotherOwnedGrinder`; `addAnotherEquivalenceOpensTheSection` (`GrinderDetail(id, toCalibrations = true)`: `grinder_calibrations` visible sin desplazar). |

**Verificación:**
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.grinders.GrinderDetailScreenTest,dev.josueamaya.pourline.ui.grinders.GrindCardTest`.
`./gradlew :app:lintDebug`.

**Salida:** la sección cumple todos los puntos del §3.7 y del criterio de interfaz del §5.

Commit sugerido: `feat(grinder): manage calibration pairs from the grinder detail`.

### F8. Verificación y cierre

**Objetivo:** las suites, lint, el rendimiento, el recorrido por adb en el emulador y la verificación manual en el
móvil del usuario.

**Pasos:** el §7 completo.

**Salida:** todos los criterios de la Enmienda 1 del §5 marcados, con las cifras en la cabecera del plan
("Resultado de la verificación", como en `plan-molinos.md`).

Commit sugerido: `docs(specs): record the grind calibration verification (SPEC-006 amendment 1)`.

### 3.6 Cadenas nuevas (`values` / `values-en`)

| Clave | Español | Inglés | Fase |
|---|---|---|---|
| `grind_card_calibrated` | según tu calibración | per your calibration | F5 |
| `grind_card_fix_calibration` | Añade otra equivalencia | Add another equivalence | F5 |
| `converter_correct` | Corregir | Correct | F6 |
| `grind_calibration_add` | Añadir equivalencia | Add equivalence | F6 |
| `grind_calibration_save` | Guardar equivalencia | Save equivalence | F6 |
| `grind_calibration_sheet_title` | Equivalencia | Equivalence | F6 |
| `grind_calibration_other` | Otro molino | Other grinder | F6 |
| `grind_calibration_updated` | Equivalencia actualizada | Equivalence updated | F6 |
| `grind_calibration_conflict` (`plurals`) | one: "Contradice la equivalencia %s"; other: "Contradice las equivalencias %s" | one: "Contradicts the equivalence %s"; other: "Contradicts the equivalences %s" | F6 |
| `grind_calibration_replace` | Reemplazar | Replace | F6 |
| `grind_calibration_invalid` | Revisa los ajustes: cada uno tiene que estar en la escala de su molino | Check the settings: each one must be within its grinder's scale | F6 |
| `grind_calibration_pair` | %1$s %2$s = %3$s %4$s | %1$s %2$s = %3$s %4$s | F6 |
| `grinder_detail_calibrations` | Equivalencias | Equivalences | F7 |
| `grinder_detail_calibrations_empty` | Guarda los ajustes que muelen igual en tus molinos para afinar la conversión | Save the settings that grind the same on your grinders to fine-tune the conversion | F7 |
| `grind_calibration_delete_title` | ¿Borrar la equivalencia %s? | Delete the equivalence %s? | F7 |
| `grind_calibration_edit_description` | Editar %s | Edit %s | F7 |
| `grind_calibration_delete_description` | Borrar %s | Delete %s | F7 |

Se reutilizan `grind_out_of_scale`, `grinder_delete_confirm` y `grinder_delete_cancel`. La lista de pares de
`grind_calibration_conflict` se une con ", ". `grind_calibration_pair` lleva `translatable="false"` solo si lint lo
pide; si no, va en los dos archivos.

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| Puerta del §3.6 superada: `METHOD_LEVELS`, curva implícita del A2 y decisión sobre su moka antes de tocar la interfaz | F2 | Informe de `CatalogCurveTest` y decisión del usuario; F5 no empieza antes |
| `HistoryDatabase` en v7 (o la siguiente libre); `HistoryMigrationTest` cubre 6 → 7 con FK, índices y únicos, y conserva los datos | F0, F3 | `HistoryMigrationTest.migrates6To7CreatingGrindCalibrations`, la cadena 1 → 7 y `migrates6To7ThenRoomReadsEverything`; D40 |
| Tests JVM de `GrindCurve`: explícita con todas las anclas y extrapolación; implícita por mínimos cuadrados con repetidos una vez; sin curva con menos de 2 niveles o pendiente al revés; `level` y `value` inversas | F1 | `GrindCurveTest` |
| Un test JVM recalcula `METHOD_LEVELS` desde `catalog.json` y coincide con la constante | F1 (y F2 si cambia el catálogo) | `MethodLevelsTest` |
| `GrindConverter`: mismo resultado con cualquier método (también `OTHER` y sin método) | F1, F5 | `GrindConverterTest.the same source gives the same result for every method` y `GrindCardStateTest.the method of the recipe does not change the conversion` |
| `GrindConverter`: S3 ↔ A2 en los dos sentidos con las curvas y `ANCHORS` | F2 | `CatalogCurveTest.S3 and A2 convert both ways with the approved curves` |
| `GrindConverter`: con 2 o más pares, interpolación, `Exact` en un par, extrapolación, `CALIBRATED` y nunca `ANCHORS` | F1 | `GrindConverterTest.two or more pairs…` y `pairs work in both directions` |
| `GrindConverter`: con 1 par, pasa por el par y se desplaza con las curvas; sin curva, `NoEquivalent` con `ADD_CALIBRATION` | F1, F5 | `GrindConverterTest.one pair…`, `GrindCardStateTest.one pair gives add calibration…` y `fixFor` |
| `GrindConverter`: `ROUNDED` y `AT_LIMIT` después de calibrar | F1 | `GrindConverterTest.rounding and the limit still apply after calibrating` |
| `HIGHER_IS_FINER` mantiene la posición de fino a grueso por curvas y por calibración (rehecho) | F1 | `GrindConverterTest.curves keep the coarse to fine position…` y `calibration keeps the position…` |
| `CalibrationValidator`: válido, `Update`, `Conflict`, molinos iguales, fuera de escala o de paso, direcciones opuestas | F1 | `CalibrationValidatorTest` (con `Invalid`, discrepancia 2) |
| Repositorio: guardar, actualizar, reemplazar en una transacción, borrado en cascada; duplicar no copia pares; la sincronización del catálogo no los toca | F3, F4 | `GrindCalibrationDaoTest`, `GrinderRepositoryTest` y `GrinderCatalogSeederTest` (instrumentados, discrepancia 11) |
| Convertidor: "Corregir" guarda un par y la fila muestra el valor corregido; con un segundo par, un ajuste intermedio da "según tu calibración" | F6 | `GrindConverterScreenTest.correctSavesAPair…` y `aSecondPairGivesPerYourCalibrationInBetween` (discrepancia 13) |
| Sin "Corregir" cuando el origen no está en mis molinos | F6 | `GrindConverterScreenTest.noCorrectWhenTheSourceIsNotMine` |
| Detalle: "Equivalencias" lista, añade, edita y borra, y muestra el conflicto con "Reemplazar" | F7 | `GrinderDetailScreenTest` (F7) |
| La tarjeta muestra "según tu calibración" con 2 pares | F5 | `GrindCardTest.twoPairsShowPerYourCalibration` |
| Convertidor sin selector de método, con el equivalente en cada uno de mis molinos (rehecho) | F6 | `GrindConverterScreenTest.chooseASourceAndASettingAndEachOfMyOtherGrindersGetsARow` |
| Atajo "Ver en mis otros molinos" desde la tarjeta y el detalle de la receta, relleno con molino y ajuste (rehecho) | F6 | `GrindShortcutTest` (los cuatro casos) |
| `SessionPerformanceTest` no empeora respecto a `develop` | F0, F5, F8 | §7.2 (D57) |
| Verificación manual en el móvil del usuario con 2 pares reales S3 ↔ A2: un único valor "según tu calibración" en los dos sentidos, sin selector de método, con capturas del convertidor y de "Equivalencias" | F8 | §7.4 |
| `:app:testDebugUnitTest` y `:app:connectedDebugAndroidTest` pasan (salvo los fallos conocidos); lint y formato en los archivos tocados | Todas, F8 | §7.1; `:app:lintDebug` en F5 a F8 (no hay detekt, §1.2) |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | Otra rama (SPEC-008 u otra) sube antes la versión de la base de datos. | Hoy SPEC-008 no toca `history/` ni `app/schemas/` (comprobado al escribir el plan). F0 lo vuelve a mirar, y D40 describe el cambio a v8 paso a paso. La migración solo crea una tabla, así que renumerarla no cambia su SQL. |
| R2 | Instalar en el móvil una v7 que después cambie de número deja su base de datos sin migración posible. | D40: no se instala en el móvil hasta fijar la versión. Antes de instalar se copia la base de datos con `run-as` (§7.4). Si `adb install -r` falla por la firma, se para y se pregunta: nunca se desinstala (borraría los datos). |
| R3 | La curva implícita del A2 da equivalentes malos por sus rangos contradictorios. | La puerta de F2 lo decide con los números delante. La calibración del usuario se impone en cualquier caso (§3.6), y la verificación final usa pares reales. |
| R4 | Cambiar `convert` rompe a la vez la tarjeta, el convertidor y sus tests. | Las dos firmas conviven hasta F6 (D60): F5 cambia la tarjeta y F6 el convertidor, cada una con sus tests. El §1.2 lista cada test y su nuevo valor. |
| R5 | La `CASCADE` borra pares en sitios inesperados. | `GrinderDao.save` usa `@Insert`/`@Update`, nunca `REPLACE` (comprobado), y `GrindCalibrationDaoTest.editingAGrinderKeepsItsPairs` lo protege. `syncCatalog` ya no borra un predefinido con pares (D49), con su test. |
| R6 | Una pila de navegación guardada con `GrindConverter(method=…)` falla al restaurarse. | Solo pasaría restaurando tras una muerte del proceso con la versión anterior; una actualización no restaura la pila (D59). Si apareciera, se añadiría `ignoreUnknownKeys` en la configuración de la pila, sin cambiar las claves. |
| R7 | La tarjeta empeora el rendimiento de la sesión. | Los pares llegan como `Flow` por el molino de origen, no por lectura de la báscula; la orientación es aritmética sobre una lista corta (D50). La línea base de F0 y tres pasadas en F8 con el criterio de D32 (D57). |
| R8 | Conflictos de texto al fusionar con SPEC-008 (`GrindCard.kt`, `GrindConverterScreen.kt`, `Navigation.kt`, `strings.xml`, `TestAppDependencies.kt`). | Cambios mínimos en esos archivos; se conservan los tags (`grind_card_ruler`, `converter_source`, `converter_row_<id>`); las cadenas van junto a las `grind_card_*`/`converter_*`. Quien fusione después resuelve con los tests de F5 y F6. |
| R9 | `METHOD_LEVELS` provisionales distintos de los del test por diferencias de redondeo. | Manda el test (D43). La constante se escribe con lo que da `methodLevels` sobre `catalog.json`, y la estimación del plan solo sirve de comprobación. |
| R10 | El emulador en `en-US` hace fallar comparaciones de textos. | `testTag` y `stringResource` del contexto de la app. |
| R11 | Los valores esperados con curva (30, 150, 32…) difieren por un error de cálculo del plan. | El implementador los comprueba con la propia `convert` y la fixture antes de fijarlos en los tests, y anota cualquier diferencia en el plan. |

## 6. Preguntas para el usuario

Cada una tiene una propuesta; si se aprueba el plan sin comentarios, se aplica la propuesta.

**Puerta de F2 (2026-11-17):** el usuario decidió **quitar el rango de moka del Femobook A2** (`MOKA 10–18` choca con V60 50–80 y AeroPress 40–50 y desviaba la curva implícita). `catalog.json` pasa a `"version": 2`, con el motivo en `notes` y en `missing`; `METHOD_LEVELS` no cambia (el A2 no tiene `micronRange`). La tabla aprobada es la columna "sin moka" de la estimación de F2 y `CatalogCurveTest` la afirma.

**Respuestas (2026-11-17):** P1 y P2, el usuario eligió la propuesta (`AntModal`; formato del molino). P3, se aplica la propuesta. P4 sigue abierta: en F8 el implementador se para y pregunta el serial y el permiso de instalación.

1. **P1. Borrar un par (D54).** *Propuesta: `AntModal` de confirmación, como el borrado del molino en la misma
   pantalla.* Alternativa: "Deshacer" con `AntMessageHost`, como las listas de recetas y cafés.
2. **P2. Texto de un par (D55).** *Propuesta: modelo y `GrindFormat.format` de cada lado: "Chestnut S3 20 clics = A2
   1 · 20".* Alternativa: el valor en unidades base ("A2 60"), que coincide con los ejemplos del spec pero no con cómo
   se muestra el A2 en el resto de la app.
3. **P3. Enlace de "Sin equivalencia" (D45).** *Propuesta: apunta al molino que no tiene curva (el elegido si ninguno
   la tiene). Con un solo par, "Añade otra equivalencia" lleva al molino elegido, como dice el spec.* Alternativa:
   apuntar siempre al elegido, como hoy, aunque el que no tenga curva sea el origen.
4. **P4. Verificación en tu móvil (§7.4).** *Propuesta: instalar el APK de depuración con `adb install -r`, que
   conserva tus datos, solo después de fijar la versión de la base de datos (D40), y antes copiar tu base de datos con
   `run-as` a `~/Downloads/`.* Hace falta que confirmes el serial del móvil y que la app instalada sea de depuración y
   esté firmada con la clave de este equipo. Si no, se para y se pregunta.

## 7. Verificación final

### 7.1 Suites

```
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Se compara con la línea base de F0:

- **Tests:** solo suben los totales, por los tests nuevos. Las expectativas que cambian son las del §1.2.
- **Lint:** sin errores ni avisos nuevos, y `MissingTranslation`, `ExtraTranslation` y `UnusedResources` a cero en
  las cadenas nuevas y sin restos de `converter_method`/`converter_no_method`.
- **Fallos que no bloquean:** `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap` y, si oscila, el caso
  Experto de `SessionPerformanceTest`.

Comprobaciones con `grep`:

- ningún `import android` ni `androidx` en los 15 archivos puros (los 12 de D10 más los 3 de D41);
- ningún `converter_method`, `converter_no_method`, `byRange`, `byAnchors` ni `it.method` en las llamadas al
  convertidor (`Navigation.kt`);
- `git diff develop --stat -- app/src/main/AndroidManifest.xml` vacío (sin permisos nuevos, §4 del spec).

### 7.2 Rendimiento (`SessionPerformanceTest`, `emulator-5556`)

- **Montaje:** el de SPEC-006 (Uno, Dos y una receta con Uno 20) más dos pares Uno↔Dos (D57).
- **Pasadas:** tres por caso en F8, leyendo `adb -s emulator-5556 logcat -d -s SessionPerf`.
- **Criterio:** la mediana del p95 de cada caso ≤ la de la línea base de F0 + 1 ms, con los mismos umbrales de
  SPEC-005 (p95 ≤ 32 ms, ≤ 5 % por encima de 32 ms, ninguno por encima de 250 ms). El caso Experto ya supera 32 ms
  en `develop`, así que su criterio es solo la comparación con la línea base.
- **Si Experto sube más de 1 ms:** pasadas intercaladas de `develop` (en un worktree temporal) y de la rama, solo del
  caso Experto, como en la verificación de `plan-molinos.md`.

### 7.3 E2E por adb en el emulador (sin ARTEMIS)

**Antes de empezar:**

1. `adb devices -l` y `adb -s emulator-5556 shell wm size`.
2. `ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug`.
3. `adb -s emulator-5556 shell pm clear dev.josueamaya.pourline` (catálogo real, sin datos).

**Herramientas:** `uiautomator dump` para localizar, `input tap` para tocar y `screencap` para capturar. El id del
display sale de `adb shell dumpsys SurfaceFlinger --display-id`. `adb logcat -b crash -d` debe quedar vacío. El
emulador está en inglés: los textos son los de `values-en`.

| Paso | Recorrido | Comprobaciones |
|---|---|---|
| 1 | Onboarding → Básico → elegir el Chestnut S3. Ajustes → Molinos → "Añadir molino" → "Lo tengo" en el Femobook A2 | S3 y A2 en mis molinos. `logcat -s GrinderCatalogSeeder` muestra `Synced` con la versión del catálogo de F2. |
| 2 | Herramientas → Convertidor → origen S3 → 20 clics | No hay selector de método. La fila del A2 muestra el valor aprobado en F2 con "approximate". Captura. |
| 3 | "Correct" en la fila del A2 → 1 · 10 (50) → "Save equivalence" | La fila muestra "1 · 10" sin marcas. |
| 4 | Origen S3 30 → "Correct" → 1 · 30 (70) → guardar. Después, S3 25 | La fila muestra "1 · 20" (60) con "per your calibration". Captura. |
| 5 | Origen A2 → 1 · 20 (60) | La fila del S3 muestra 25 clics con "per your calibration" (el otro sentido). Captura. |
| 6 | Origen S3 25 → "Correct" → 1 · 35 (75) → guardar | Aviso "Contradicts the equivalence…" con "Replace". "Replace" borra el par en conflicto y guarda el nuevo. |
| 7 | Ajustes → Molinos → Chestnut S3 | "Equivalences" con el grupo "Femobook A2" y los pares S3 20 = A2 1 · 10 y S3 25 = A2 1 · 35, en ese orden, con editar y borrar. Borrar uno pide confirmación (se cancela). Captura. |
| 8 | Preparar → una receta V60 con "Chestnut S3 · 22 clicks" → "Grind with" el A2 | La tarjeta muestra "1 · 20" (60, entre los dos pares) con "per your calibration". Captura. |
| 9 | Preparar → una receta con método "Other" y la misma molienda | La tarjeta da el mismo valor que en el paso 8 (US-12). |

### 7.4 Verificación manual en el móvil del usuario

Es el último criterio del §5. Se hace después de fijar la versión de la base de datos (D40, P4).

1. `adb devices -l`. Se pregunta al usuario el serial del móvil; nunca se elige solo.
2. Copia de seguridad de su base de datos (la app instalada tiene que ser de depuración):
   `adb -s <serial> exec-out run-as dev.josueamaya.pourline tar c databases > ~/Downloads/pourline-db-<fecha>.tar`.
   Si falla, se para y se pregunta.
3. `./gradlew :app:assembleDebug` y `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`. **Sin
   `pm clear`** y sin desinstalar. Si falla por la firma (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), se para y se pregunta.
4. Se abre la app y se comprueba que sus datos siguen (recetas, historial, molinos). `adb -s <serial> logcat -b crash -d`
   vacío tras la migración.
5. El usuario introduce, desde el convertidor o desde el detalle, **2 pares reales Chestnut S3 ↔ Femobook A2** que
   haya comprobado moliendo.
6. En el convertidor, sin selector de método: con un ajuste del S3 entre los dos pares, la fila del A2 da un único
   valor con "según tu calibración"; con el origen en el A2, la fila del S3 da el valor inverso, también "según tu
   calibración".
7. Capturas con `adb -s <serial> exec-out screencap -p > …` del convertidor en los dos sentidos y de la sección
   "Equivalencias" del S3 y del A2.

### 7.5 Capturas

En claro y en oscuro (cambiando "Apariencia" en Ajustes), en el emulador: el convertidor sin selector con una fila
"aproximado", una fila "según tu calibración" y una "Sin equivalencia" con "Añadir equivalencia"; la hoja de corregir
con el conflicto y "Reemplazar"; la sección "Equivalencias" con pares, vacía y con un valor "Fuera de la escala"; y la
tarjeta con "según tu calibración" en Básico y en Experto.

- **Dónde:** fuera del repo, en `~/Downloads/grind-calibration-captures/` (emulador y móvil).
- **Qué revisar a ojo:**
  - que los textos de los pares compuestos no se corten a 360 dp ni con la escala de letra a 1,3
    (`adb shell settings put system font_scale 1.3`, y después `1.0`);
  - el contraste de la etiqueta verde "según tu calibración" en los dos temas.

## 8. Seguimiento fuera de este plan

- **Cambios que conviene llevar al spec al cerrar.** No se tocan ahora.
  - `Invalid` en el validador y en `saveCalibration` (discrepancia 2, D47).
  - `isUsed` con pares: un predefinido con pares se retira, no se borra (discrepancia 1, D49).
  - El pipeline de redondeo y límite también tras un par exacto (discrepancia 3, D44).
  - El enlace de "Sin equivalencia" al molino sin curva (discrepancia 7, D45).
  - Un par con un molino que ya no es mío solo se borra (discrepancia 8).
  - `METHOD_LEVELS` definitivos y la decisión sobre la moka del A2 (F2).
  - Las decisiones D40 a D60, al §3.9, cuando se apruebe la implementación.
- **Fuera del alcance (§1 del spec):** encadenar calibraciones a través de un tercer molino, mostrar las anclas
  implícitas en la regla o en el editor y pares con molinos que no son míos.
- **Antes de fusionar en `develop`:** revisar la rama con `/code-review` y comprobar otra vez la versión de la base
  de datos (D40).
