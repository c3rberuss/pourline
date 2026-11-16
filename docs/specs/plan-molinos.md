# Plan de implementación: SPEC-006 Molinos y conversión de molienda

| Campo | Valor |
|---|---|
| Spec | [`spec-molinos.md`](spec-molinos.md) (SPEC-006) |
| Estado | Aprobado el 2026-11-16, con las propuestas de P1 a P6 (§6) aceptadas. Además, F10 (sembrado del catálogo) queda bloqueada por la puerta de F2 hasta que el usuario apruebe o corrija la tabla de molinos. |
| Fecha | 2026-11-16 |
| Rama | `feat/grinders`, creada desde `develop` (`1a5512e`), con el commit del spec (`e392772`) |
| Módulos | `:app` (`:protocol` no cambia) |

El spec no trae fases, así que este plan las define. El orden separa el trabajo que **no** depende de los datos
reales de los molinos del que sí depende:

- **Antes de la puerta:** F0 fija el diseño y la línea base, y F1 escribe todo el dominio puro (modelo, formato
  compuesto, acarreo, conversión, validador y lector del archivo del catálogo) con tests JVM.
- **La puerta:** F2 es la investigación de Honest Coffee Guide. Produce el borrador de `catalog.json` y la tabla
  por molino, y el implementador **se detiene** a presentarla. Va pronto a propósito: si la fuente obliga a
  cambiar el modelo (jerarquías, diales que empiezan en 1, medios pasos), conviene saberlo antes de la base de
  datos y de la interfaz.
- **En paralelo a la puerta:** F3 a F9 (base de datos v5, repositorio y sembrador, pantallas de molinos, recetas y
  preparaciones, tarjeta de molienda, convertidor y onboarding) se prueban con un **catálogo de prueba** de marcas
  ficticias (D36) y no leen el archivo real.
- **Después de la puerta:** F10 siembra el catálogo aprobado y F11 es la verificación final (rendimiento, E2E y
  capturas por adb, sin ARTEMIS).

Antes de las fases están las decisiones que el spec deja abiertas (§2). Varias concretan o amplían el spec:
D8, D12, D15, D16, D17, D18, D23 y D37. Conviene revisarlas, y las preguntas del §6, antes de empezar F1.

## 1. Punto de partida

**Se reutiliza:**
- El patrón de migraciones de `history/Migrations.kt` (SQL copiado de `app/schemas/.../N.json`, sin `DEFAULT`
  donde Room no lo pone) y `HistoryMigrationTest` con `MigrationTestHelper` (`runMigrationsAndValidate`). Los tests
  que abren Room enumeran las migraciones en tres sitios (`addMigrations(migration1To2(texts), MIGRATION_2_3,
  MIGRATION_3_4)`): hay que añadir `MIGRATION_4_5` en todos.
- La seguridad de claves foráneas al escribir de SPEC-004 (D21): `BrewDao.insertBrew` y `BrewDao.restore`
  comprueban que el café existe antes de vincularlo. Se repite para el molino (D18).
- El patrón del café en el gestor: `selectedCoffee`, `sessionCoffee` y `coffeeFrozenFor` congelan la elección al
  pasar a `Active` (`ScaleSessionManager.withCoffee`). El ajuste de molienda se congela en el mismo punto (D23).
- Preferencias: `ExperienceSettings` (`StateFlow` + `SharedPreferences "settings"`, `BrewSettings.PREFS_NAME`,
  parámetro `prefsName` para los tests) es el modelo de `GrinderSettings`.
- `fold(text)` (`coffee/CoffeeSearch.kt`) pliega mayúsculas y tildes: sirve para buscar molinos por marca o modelo.
- `CoffeePicker` (`AntDrawer` con buscador y `LazyColumn`) es el modelo del selector de molino del convertidor.
- Componentes: `AntSelect`, `AntSelectTrigger`, `AntSegmented`, `AntDrawer`, `AntModal`, `AntChoiceModal`,
  `AntEmpty(action)`, `AntListItem`, `AntCard`, `AntTag` (`AntTagColor.Warning`, `Primary`…), `AntAlert`,
  `AntFormItem`, `AntInput`, `AntButton` (`AntButtonType.Link`, `Dashed`), `AntIconButton`, `AntSpin`, `AntTopBar`.
- `LocalExperienceMode` y `ExpertFeature` (`settings/ExperienceMode.kt`); `ExperienceModeTest` recorre todas las
  entradas del enum, así que cubre la nueva sola.
- Tests: `TestAppDependencies` (Room en memoria, preferencias `"settings_test"`, báscula simulada), `SessionDriver`,
  `HistoryDatabase.inMemory`, `ScaleTestFakes.kt` (JVM), `RecipeTestFixtures.kt`, `FakeRecipeTexts` y la
  metodología de `SessionPerformanceTest` (`FrameMetrics`, 120 s de vertido tras 5 s de calentamiento).
- `kotlinx-serialization-json` 1.8.1 ya está en el catálogo de versiones y en la caché de Gradle (hoy solo como
  `testImplementation`); el plugin `kotlin.serialization` ya está aplicado en `:app`.

**Falta o hay que corregir:**
- `grind: String?` aparece en `RecipeEntity`, `BrewEntity`, `Recipe`, `BrewDetails` (`normalized()`),
  `RecipeRepository` (`toRecipe`, `toEntity`), `BrewRepository.save`, `BrewDao.updateDetailsRow`/`applyDetails`,
  `RecipeFromBrew.convert`, `ScaleSessionManager.resolveRecipe`/`resolveBrew`, `RecipeEditForm`,
  `RecipeEditViewModel`, `RecipeEditScreen` (`recipe_edit_grind`), `BrewEditForm`, `BrewEditViewModel`,
  `BrewEditScreen` (`brew_edit_grind`), `BrewDetailScreen.DetailsCard` y el `INSERT` de `BuiltInRecipeSeeder`.
  En tests: `RecipeEditFormTest`, `BrewEditFormTest`, `RecipeFromBrewTest`, `HistoryModelsTest`,
  `ScaleSessionManagerTargetTest`, `ScaleTestFakes.kt` (JVM), `BrewEditScreenTest`, `BrewDetailScreenTest`,
  `RecipeDaoTest` y `BrewDaoTest`.
- **SQLite sin `DROP COLUMN`:** `minSdk = 26` trae SQLite 3.18 y `ALTER TABLE … DROP COLUMN` llega con la 3.35
  (API 34). `recipes` y `brews` se reconstruyen, como pide el §3.4. `recipe_stages` (de `recipes`) y `brew_curves`
  y `brew_targets` (de `brews`) tienen `ON DELETE CASCADE`: un `DROP TABLE` con las claves foráneas activas
  borraría sus filas (D19, R1). Las dos tablas son `AUTOINCREMENT`: reconstruirlas sin más reinicia el contador de
  `sqlite_sequence` y podría reutilizar ids borrados.
- No existe un "detalle de receta": tocar una receta abre `RecipeEditScreen` (`RecipeEdit(id)`). El spec habla del
  detalle de receta en el §3.7 y en un criterio (D8, P6).
- `OnboardingScreen(onChoose)` tiene un solo paso y `MainNavigation` llama a `completeOnboarding` al elegir.
- `showsBottomBar(key)` solo mira la cima de la pila: abrir Molinos encima de la sesión mostraría la barra (D5).
- `RecipeRow` muestra el método con `fixedName ?: methodOther ?: "Otro"`: un método con nombre traducido saldría
  como "Otro" (D27).
- Las tarjetas para compartir (`share/`) no muestran el método: el criterio "métodos nuevos en las tarjetas para
  compartir" no exige cambios (§8).
- No hay componente de stepper, de pestañas de página ni icono de restar (`AntIcons` tiene `Plus`, no `Minus`).
- `kotlinx-serialization-json` no está en `implementation` y `org.json` está simulado en los tests JVM
  (`isReturnDefaultValues`): sin la dependencia, el lector del catálogo no se podría probar en la JVM (D33).
- **La fuente**, consultada el 2026-11-16: la página del Timemore C2 da "0 – 950 microns" y una tabla "Clicks
  from zero" (V60 13–22, Pour Over 13–29, AeroPress 11–30, French Press 22–30, Espresso 6–12, Moka Pot 12–20,
  Cold Brew 26–30, y métodos que no usamos: Turkish, Siphon, Cupping…). No da el número total de clics ni el
  paso. La del 1Zpresso JX-Pro da "0 – 915 microns" y una tabla "Rotations from zero" en formato
  `rotation.number.tick` (V60 "2.2.0 – 3.8.1", máximo "5.0.0"), sin decir cuántos números hay por rotación ni
  cuántos ticks por número; los rangos sugieren 10 números (0–9) y 4 ticks (0–3). Esto no coincide con el ejemplo
  del §3.1 del spec (números con `perParent = 4`, clics con `perParent = 10`), que solo se usa como molino de
  prueba en los tests (§8). La escala y la jerarquía de cada predefinido se resuelven en F2 (D37).

## 2. Decisiones que completan el spec

Se aprueban junto con el plan. Al terminar se pasan al spec como §3.9, como en los specs anteriores. Las marcadas
**(amplía)** añaden algo que el spec no dice; conviene anotarlas en el spec al cerrar (§8).

### 2.1 Diseño visual

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Componentes nuevos | `AntStepper` (vertical: [+] arriba, valor, [−] abajo y la etiqueta de la unidad debajo; botones de 40 dp con área táctil de 48 dp), `AntTabs` (pestañas de línea, como `Tabs` de antd, con indicador inferior de 2 dp en `primary.base`) e iconos `Minus`, `Grinder` (molino manual) y `Convert` (dos flechas opuestas). Bocetos en F0.3. | Las imágenes de referencia del spec no están en el repo (P4). Tres steppers verticales caben de sobra a 360 dp (3 × 48 dp) y dejan la etiqueta de la unidad bajo cada parte, como pide el §3.2. |
| D2 | Introducción compuesta (`GrindInput`) | Un `AntStepper` por unidad, en el orden de la jerarquía, con " · " entre columnas. Con valor `null` cada parte muestra "—" y el primer toque parte del valor inicial (D13). La semántica del conjunto es la forma larga ("1 rotación, 2 números, 3 clics"); cada botón se lee "Aumentar rotaciones"/"Disminuir clics". | §3.2. TalkBack lee el ajuste completo y no tres números sueltos. |
| D3 | Regla de molienda (`GrindRuler`) | Alto 64 dp. Pista horizontal con marcas (D11 y F1); **grueso a la izquierda, fino a la derecha** siempre. Rango del método como banda `primary.bg` con su nombre encima; ajuste actual como marcador triangular `primary.base` con el valor; anclas como puntos de 6 dp con su abreviatura (F0.3); "Grueso" y "Fino" en los extremos (`Secondary`). Semántica: "Regla de molienda, de grueso a fino. V60 de 13 a 22 clics. Ajuste: 30 clics". | §3.7. Una sola regla para el detalle, el editor y la tarjeta de Experto. |
| D4 | Tarjeta de molienda (`GrindCard`) | `AntCard` "Molienda": línea de la receta, selector "Moler con", ajuste del molino elegido con `AntTitle(level = 2)` y las marcas como `AntTag` ("≈" va delante del número; "aproximado", "sugerido" y "al límite del molino" como etiquetas), la regla solo en Experto, el enlace "Ver en mis otros molinos" y la línea de la fuente. Los estados y sus textos están en D25 y F0.3. En la sesión va en el paso 2, debajo de la barra de estabilidad. | §3.7. En el paso 2 la sesión ya tiene desplazamiento vertical (`PrepPanel`). |
| D5 | Navegación | Claves nuevas: `Grinders`, `GrinderCatalog`, `GrinderDetail(id)`, `GrinderEdit(id: Long? = null, fromId: Long? = null, openRanges: Boolean = false)` y `GrindConverter(sourceId: Long? = null, method: BrewMethod? = null, value: Double? = null)`. Ajustes → Molinos se apila en la pila de Preparar (como Ajustes). Los atajos se apilan en la pila de la pestaña actual. `showsBottomBar` pasa a recibir la pila: **sin barra si hay una `Session` en la pila** o si la cima es un editor (`CoffeeEdit`, `RecipeEdit`, `BrewEdit`, `GrinderEdit`) o el convertidor. | Desde el paso 2 de la sesión se puede abrir Molinos o el convertidor; con la regla de la cima sola, la barra aparecería con la sesión abierta debajo. El convertidor es una herramienta a pantalla completa con su flecha atrás. |
| D6 | Onboarding | Dos pasos dentro de la misma puerta de `MainNavigation` (no son claves de navegación, D11 de SPEC-005). El paso 1 no cambia; elegir Básico o Experto pasa al paso 2 sin guardar nada. "Saltar" del paso 1 termina el onboarding en Básico **sin** mostrar el paso 2. En el paso 2, "Atrás" vuelve al 1; elegir un molino o "Omitir" guardan el modo y `onboarding_done` en una escritura (`completeOnboarding`). El paso se guarda con `rememberSaveable`. | §3.7: el paso del molino aparece "después de elegir Básico o Experto". "Saltar" ya significa "no me preguntes" (P5). Si el proceso muere en el paso 2, se repite el onboarding entero: no queda un modo a medias. |
| D7 | Catálogo y detalle | En "Añadir molino", tocar una fila abre su detalle y el botón "Lo tengo" lo añade sin salir de la lista (pasa a "En mis molinos", deshabilitado). Acciones del detalle: predefinido de mis molinos → "Usar como predeterminado", "Duplicar para personalizar" y "Quitar de mis molinos"; predefinido que no tengo → "Lo tengo" y "Duplicar para personalizar"; propio → "Usar como predeterminado", "Editar", "Duplicar para personalizar" y "Borrar". "Usar como predeterminado" solo existe para mis molinos y no aparece en el que ya lo es. | El predeterminado tiene que ser de mis molinos: el §4 lo anula al quitarlo de ellos. Ver el detalle antes de añadirlo ayuda a elegir entre variantes (C3 y C3S). |
| D8 | "Detalle de receta" **(concreta)** | Es `RecipeEditScreen`, que es lo que abre una receta: el campo de molienda muestra "Molino · ajuste" (o la copia con "molino eliminado") y debajo el atajo "Ver en mis otros molinos". No se crea una pantalla de detalle de receta. | No existe otra pantalla de receta (P6). |
| D9 | Fuente visible | Una línea `Secondary` "Fuente: Honest Coffee Guide · valores orientativos" seguida de un enlace (`AntButton` `Link`) por cada predefinido que interviene, con su modelo, que abre `sourceUrl` en el navegador del sistema (`LocalUriHandler`). Aparece en el detalle de un predefinido, en la tarjeta si el molino de la receta o el elegido son predefinidos y en el convertidor si lo es el origen o alguna fila. | §3.6. Con dos predefinidos hay dos páginas de origen distintas. |

### 2.2 Implementación

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D10 | Paquetes | `dev.dotscale.grinder`: dominio, Room, repositorio, sembrador y textos. **Puros** (sin `import android`, se comprueba con `grep` en F1 y F11): `GrindModels.kt`, `GrindMath.kt`, `GrindFormat.kt`, `GrindTexts.kt`, `GrindStepper.kt`, `GrindConverter.kt`, `GrinderValidator.kt`, `GrindSetting.kt`, `GrinderCatalogFile.kt`, `GrindRulerModel.kt`, `GrindCardState.kt` y `ConverterRows.kt`. `dev.dotscale.ui.grinders`: pantallas y componentes. `GrindSelection` en `dev.dotscale.session`. `GrinderSettings` en `dev.dotscale.settings`. | Como en `history` y `coffee` (D15 de SPEC-004). `ReferenceKey` vive en un archivo con `import android`, así que los archivos puros no la usan. |
| D11 | Aritmética del valor | `GrindMath.snap(v, g) = g.scaleMin + round((v − g.scaleMin) / g.step) × g.step`, redondeado a 6 decimales para quitar el ruido de coma flotante (0,1 × 3). Igualdad con `EPS = 1e-9`. Decimales que se muestran: los del paso (1 → 0; 0,5 y 0,1 → 1; 0,25 → 2; máximo 3). `decompose` devuelve `List<Double>`: todas las partes son enteras salvo la última, que puede llevar el decimal del paso. Los multiplicadores son el producto de los `perParent` siguientes (`Long`). | §3.2 y §4 ("el valor nunca queda fuera de los múltiplos de `step`"). Kotlin no tiene el `Int|Double` del spec. |
| D12 | Formato **(amplía la firma)** | `GrindFormat.format(value, grinder, texts: GrindTexts)`: una unidad → "30 clics" (plural del idioma); varias → "1 · 2 · 3". `describe(value, grinder, texts)` → "1 rotación, 2 números, 3 clics". `label(grinder, value, texts)` → `"<modelo> · <format>"` ("Chestnut S3 · 30 clics") o solo `"<modelo>"` sin valor. `interface GrindTexts { fun quantity(unit: GrindUnit, value: Double): String; fun unitName(unit: GrindUnit): String; fun number(value: Double, decimals: Int): String }`; `ResourcesGrindTexts(context)` usa `plurals` (`grind_unit_clicks`…, con `other` para los no enteros). | Los nombres de las unidades y los plurales dependen del idioma y el paquete es puro. El spec firma `format(value, grinder)`: el parámetro extra es la única forma de no importar Android. |
| D13 | Acarreo (`GrindStepper`) | `GrindStepper.step(value: Double?, unitIndex: Int, delta: Int, grinder: Grinder, start: Double): Double`. En una unidad superior suma `delta × multiplicador`; en la base, `delta × step`. Como el valor es absoluto, el acarreo y el préstamo salen solos. Después, `snap` y límite a la escala. Con `value == null` devuelve `start` sin sumar: el primer toque fija el valor inicial, que es `GrindConverter.suggested(method, grinder)` o, sin rango, el centro de la escala ajustado al paso. | §3.2. Un primer toque que ya sumara partiría de un valor que el usuario no ha visto. |
| D14 | Conversión | `GrindConverter.convert(value, method, from, to): Conversion` con `sealed interface Conversion { data class Exact(val value: Double); data class Approximate(val value: Double, val reasons: Set<ConversionReason>); data object NoEquivalent }` y `enum class ConversionReason { ANCHORS, ROUNDED, AT_LIMIT }`. "Mismo molino" = mismo `id` distinto de 0. Extremo fino de un rango: `min` en `HIGHER_IS_COARSER` y `max` en `HIGHER_IS_FINER` (la fórmula del §3.3 vale tal cual en los dos sentidos). Anclas: coordenada de nivel `ordinal` (0–6) y tramos lineales sobre las anclas comunes ordenadas por nivel, con extrapolación por el tramo extremo. Orden de los pasos: valor calculado → `snap` (`ROUNDED` si cambia) → si sale de `[scaleMin, scaleMax]`, el extremo y `AT_LIMIT`. `GrindConverter.suggested(method, grinder): Double?` = `snap` del punto medio del rango, o `null`. | §3.3. Redondear antes de limitar garantiza que el extremo sea siempre un valor válido (con D15). |
| D15 | Validador **(amplía)** | `GrinderValidator.validate(grinder): List<GrinderError>` con las reglas del §3.5 y tres más: (a) `(scaleMax − scaleMin)` es múltiplo de `step`; (b) con más de una unidad, `scaleMin ≥ 0`; (c) la unidad primaria no lleva `perParent` y las secundarias sí. Las anclas tienen que ser **estrictamente** monótonas. No hay rango para `OTHER`. | (a) sin ella, limitar a `scaleMax` daría un valor fuera de los múltiplos del paso, que prohíbe el §4. (b) la descomposición de un negativo en partes ("−1 · 3 · 9") no se puede leer. Anclas iguales harían la interpolación no invertible (P2). |
| D16 | Ajuste guardado **(amplía)** | `data class GrindSetting(val grinderId: Long?, val value: Double?, val label: String?)` en `Recipe.grind` y `BrewDetails.grind` (`null` = sin molienda). Estados: con molino y ajuste; **con molino y sin ajuste** ("Chestnut S3"); molino eliminado (`grinderId = null`, `label` presente). Los formularios usan `sealed interface GrindChoice { None; Live(grinderId: Long, value: Double?); Orphan(setting: GrindSetting) }`: `Orphan` solo existe si ya lo era al cargar y se conserva tal cual hasta que se elige un molino o "Sin molino". | El spec no dice qué guarda una preparación sin receta con un molino en "Moler con" (§3.7: "se guardan el molino elegido y el ajuste mostrado", pero no hay ajuste que mostrar). Guardar el molino sin ajuste conserva lo que sí se sabe y se completa en "Editar preparación" (P1). |
| D17 | `grindLabel` **(amplía)** | Se calcula con `GrindFormat.label` y el molino vivo al guardar en `RecipeEditViewModel`, `BrewEditViewModel` y al congelar el ajuste de la sesión (D23). Un `Orphan` conserva su copia. Al **borrar** un molino propio, `GrinderRepository.delete` regenera primero la copia de cada receta y preparación que lo usan con el nombre y el formato actuales, y borra después; la clave foránea pone `grinderId = NULL`. El idioma de la copia es el del momento del guardado, como `referenceName`. | La copia solo se ve cuando el molino ya no existe: si se renombró después de guardar la receta, "molino eliminado" mostraría un nombre que el usuario ya no reconoce. |
| D18 | Claves foráneas al escribir **(amplía)** | `RecipeDao.save` y `restore`, y `BrewDao.insertBrew`, `restore` y `applyDetails`, comprueban `SELECT EXISTS(SELECT 1 FROM grinders WHERE id = :id)`. Si el molino ya no existe, escriben `grinderId = NULL` y conservan `grindValue` y `grindLabel`. | Igual que el café (D21 de SPEC-004): se puede borrar el molino mientras se edita una receta, o "Deshacer" el borrado de una receta cuyo molino ya no existe, y la FK haría fallar la escritura. |
| D19 | Migración 4 → 5 | `val MIGRATION_4_5` (constante). (1) `CREATE TABLE` de las cuatro tablas de molinos y el índice único `index_grinders_catalogKey`, copiados de `5.json`. (2) Por cada una de `recipes` y `brews`: lee `seq` de `sqlite_sequence`; `CREATE TABLE <t>_new` con el SQL de `5.json`; `INSERT INTO <t>_new (<columnas sin grind>) SELECT <mismas> FROM <t>`; `DROP TABLE <t>`; `ALTER TABLE <t>_new RENAME TO <t>`; recrea sus índices (`index_recipes_builtInKey`, `index_recipes_grinderId`; `index_brews_startedAtEpochMs`, `index_brews_coffeeId`, `index_brews_grinderId`); y repone `seq`. (3) `PRAGMA foreign_key_check` de las dos tablas: si devuelve filas, lanza `IllegalStateException`. Antes de nada comprueba `PRAGMA foreign_keys`: si vale 1, lanza `IllegalStateException` sin tocar nada (la transacción se deshace). El catálogo no se siembra aquí. | Es el procedimiento de SQLite para cambiar una tabla (crear, copiar, borrar, renombrar) y el que usan las automigraciones de Room, que ejecutan las migraciones con las claves foráneas desactivadas (las activa `onOpen`, después). Con ellas activas, el `DROP` borraría etapas, curvas y objetivos: la comprobación convierte esa pérdida en un error recuperable (R1). Reponer `seq` evita reutilizar ids de recetas borradas, que `ReferenceKey` y `referenceId` guardan sin clave foránea. |
| D20 | Entidades y conversores | `GrinderEntity` (`grinders`), `GrinderUnitEntity` (`grinder_units`), `GrinderMethodRangeEntity` (`grinder_method_ranges`, `method` con el conversor de `BrewMethod` de `HistoryConverters`), `GrinderAnchorEntity` (`grinder_anchors`) y `GrinderWithDetails` (`@Embedded` + tres `@Relation`, consultas con `@Transaction`). `GrinderConverters`: `GrindDirection`, `GrindUnit` y `GrindLevel` por nombre; un nombre desconocido se lee como `HIGHER_IS_COARSER`, `CLICKS` y la fila de ancla se ignora al mapear al dominio. | Mismo criterio que `HistoryConverters`: volver a una versión anterior no rompe la lectura. |
| D21 | Repositorio y predeterminado | `GrinderRepository(db, defaults: DefaultGrinderStore, texts: GrindTexts)` con escrituras en `Dispatchers.IO + NonCancellable`. `setOwned(id, false)` y `delete(id)` ponen `default_grinder_id = null` si era ese. `save(grinder)` valida con `GrinderValidator` y rechaza los predefinidos (`catalogKey != null`). Una receta nueva preselecciona el predeterminado con valor `null` (D13): el ajuste lo fija el primer toque. | §4. Proponer un valor que nadie ha elegido lo guardaría sin que el usuario lo vea. |
| D22 | Duplicar | "Duplicar para personalizar" abre `GrinderEdit(fromId = id)` con una copia en memoria: `id = 0`, `catalogKey = null`, `owned = true`, `retired = false`, `sourceUrl = null`, modelo "<modelo> (copia)" (`grinder_copy_name`) y `duplicatedFromKey = original.catalogKey ?: original.duplicatedFromKey`. Se inserta al pulsar "Guardar". | Si el usuario sale sin guardar no queda una copia huérfana en mis molinos. Con el mismo nombre, dos filas de mis molinos serían indistinguibles. |
| D23 | Selección de "Moler con" y la sesión **(concreta)** | `GrindSelection` (objeto de la app, en `session/`): combina `manager.ui.map { it.target }` (con `distinctUntilChanged` sobre la fuente, D24), `SessionGrinders` (mis molinos y un molino por id), `DefaultGrinderStore` y la elección del usuario, y expone `state: StateFlow<GrindCardState>` con `SharingStarted.Eagerly` en `Dispatchers.Default`. La elección se guarda junto con la `ReferenceKey` de la receta: al cambiar de receta vuelve a la preselección del §3.7; sobrevive a cambiar de pestaña, a girar y al paso entre Preparar y la sesión, pero no a la muerte del proceso. El gestor recibe `grind: SessionGrind = NoSessionGrind` (`fun interface SessionGrind { fun current(): GrindSetting? }`) y congela `grind.current()` al pasar a `Active`, en el mismo punto que el café; `save` lo escribe en `details.grind`. En `DotScaleApp`, el gestor recibe `SessionGrind { grindSelection.current() }` para romper la dependencia circular. | Fuera del gestor, su camino caliente (10 Hz) no cambia: el rendimiento de la sesión no depende de la tarjeta (§4). Congelar en `Active` es coherente con el objetivo y el café, y la tarjeta ya no se ve después del paso 2. |
| D24 | Origen de la tarjeta | La fuente es la referencia activa, sea una receta o una preparación (objetivo "desde el historial" de Experto): su método (`prefill.method`) y su ajuste (`ActiveTarget.sourceGrind`, campo nuevo). `prefill` deja de llevar la molienda: la preparación guarda la de "Moler con", no la de la referencia. | El spec habla de "receta", pero `TargetPicker` también admite preparaciones como objetivo. Copiar la molienda de la referencia contradiría el §3.7. |
| D25 | Estados de la tarjeta | Función pura `grindCardState(source: GrindSource?, sourceGrinder: Grinder?, owned: List<Grinder>, chosenId: Long?, defaultId: Long?): GrindCardState`. `GrindOutcome`: `Value(value, exact, reasons, suggested)`; `NoSetting` ("La receta no indica molienda", sin rango para sugerir); `NoEquivalent(fix: GrindFix)` con `COMPLETE_RANGES` (propio) o `DUPLICATE` (predefinido); `Orphan` (la receta tiene "molino eliminado": "Asigna un molino a la receta para convertir"); `NoReference` (sin receta: solo "Moler con"). Sin molinos propios: la línea de la receta y el enlace "Añade tu molino para ver el equivalente", sin selector. `GrindCardState.snapshot(texts): GrindSetting?` es lo que se guarda: molino elegido, valor mostrado (convertido o sugerido, o `null`) y su copia. | §3.3 y §3.7. Una receta con molino eliminado no se puede convertir (§3.4): necesita un texto propio, no "Sin equivalencia", que invitaría a completar rangos de otro molino. |
| D26 | Convertidor | Origen: `GrinderPicker` (`AntDrawer` con buscador) con dos secciones, "Mis molinos" y "Catálogo" (sin retirados). Método: `AntSelect` con los métodos y "Sin método" (respaldo por anclas). Ajuste: `GrindInput` del origen. Salida: función pura `converterRows(source, method, value, owned): List<ConverterRow>`, una fila por cada molino de mis molinos distinto del origen, en el orden de mis molinos. Sin mis molinos: `AntEmpty` con el enlace a Ajustes → Molinos. Cambiar el origen deja el valor en `null`. | §3.7. Un valor de otro molino no tiene sentido en el nuevo origen. |
| D27 | Nombres de los métodos | `ESPRESSO` como nombre fijo ("Espresso"); `FRENCH_PRESS`, `COLD_BREW` y `MOKA` con cadenas: "Prensa francesa"/"French press", "Cold brew"/"Cold brew" y "Moka"/"Moka pot". `RecipeRow` pasa a usar `label()`. | El §3.4 da los nombres en español; "Moka" y "Prensa francesa" cambian en inglés. |
| D28 | Búsqueda y orden | Búsqueda con `fold()` sobre marca, modelo y "marca modelo". Mis molinos y el catálogo, por marca y modelo con `Collator` (fuerza `SECONDARY`, como `RecipeRepository.list`). El predeterminado lleva la etiqueta, no sube al principio. | Orden estable que no salta al cambiar el predeterminado. |
| D29 | Regla en Experto | `ExpertFeature.GRIND_RULER`: la regla de la tarjeta de molienda. | Mismo mecanismo que el resto de reglas de visibilidad. |
| D30 | Valores fuera de escala | Si un `grindValue` vivo queda fuera de la escala actual de su molino (§4), se muestra igual con un `AntTag` `Warning` "Fuera de la escala"; ni el editor ni la conversión lo corrigen solos (`GrindInput` lo limita al primer toque). | §4. |
| D31 | Retirados | No salen en "Añadir molino" ni en el origen del convertidor; en mis molinos y en el detalle llevan `AntTag` "Retirado del catálogo". Si un archivo posterior vuelve a traer su clave, se actualiza y `retired` pasa a `false`. | §4. El spec no dice qué pasa si vuelve. |
| D32 | Rendimiento | La línea base de `SessionPerformanceTest` se toma en F0 sobre `develop`, tres pasadas por caso. En F7 la prueba añade al montaje dos molinos propios y una receta con molino, para que `GrindSelection` y la tarjeta trabajen. En F11, tres pasadas más. "No empeora" = mediana del p95 de cada caso ≤ la de la línea base + 1 ms, y mismas condiciones de umbral. | El caso Experto oscila cerca de 32 ms en este emulador (spec-005): una sola pasada no basta para comparar. |

### 2.3 Catálogo y puerta

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D33 | Formato del archivo y dependencia | `app/src/main/assets/grinders/catalog.json`, leído con `kotlinx-serialization-json` (`implementation(libs.kotlinx.serialization.json)`, ya en la caché). Esquema: `{ "version": Int, "grinders": [ { "key", "brand", "model", "units": [ { "unit": "ROTATIONS" }, { "unit": "NUMBERS", "perParent": 10 }, … ], "scale": { "min", "max" }, "step", "direction", "micronRange": { "min", "max" } \| null, "ranges": { "V60": { "min", "max", "source": "13 – 22" }, … }, "anchors": { "FINE": 9.5, … }, "anchorsManual": false, "sourceUrl", "missing": [String], "notes": String? } ] }`. Valores en unidades base; `source` guarda el texto de la fuente ("2.2.0 – 3.8.1") para revisarlo. `GrinderCatalogFile.parse(json): Result<CatalogData>` rechaza el archivo **entero** si una entrada es inválida (`GrinderValidator`, clave repetida, método desconocido o `OTHER`, `sourceUrl` vacía). | Un solo lector, en `main` y probado en la JVM, que usa el test del archivo y el sembrador. Con `org.json` (simulado en la JVM) el test validaría un lector distinto del de la app. |
| D34 | Sembrado | `GrinderCatalogSeeder(dao, source: CatalogSource, versions: CatalogVersionStore, defaults: DefaultGrinderStore)`, `suspend fun sync(): SyncResult` (`UpToDate`, `Synced(version, added, updated, retired, deleted)`, `Failed(reason)`). Todo en una transacción de `GrinderDao.syncCatalog`; la versión se escribe en las preferencias **después** del commit. Un predefinido "en uso" es el que tiene recetas o preparaciones, `owned = true` o es el predeterminado. Se llama desde `DotScaleApp.onCreate` en `applicationScope` con `Dispatchers.IO` (en F10, no antes). Los errores van a `Log.w`. | §3.6 y §4. Si el proceso muere entre el commit y la preferencia, el siguiente arranque repite la sincronización, que es idempotente. |
| D35 | Borrador y puerta | El borrador de F2 se escribe ya en la ruta final, pero **nada lo lee en la app** hasta F10 (el sembrador no se invoca al arrancar). `GrinderCatalogFileTest` lo valida desde F2. F10 solo empieza con la tabla aprobada. | La tabla vive solo en el JSON y en el informe (§3.6). Así el borrador se valida con el mismo test que el final y no se siembra nada antes de tiempo. |
| D36 | Catálogo de prueba | `androidTest/.../grinder/TestGrinderCatalog.kt` y `test/.../grinder/GrinderFixtures.kt` con marcas ficticias ("Acme Uno" de clics, "Acme Dos" de clics con medio paso, "Acme Tres" compuesto de rotaciones, números y clics y "Acme Inverso" `HIGHER_IS_FINER`), con rangos y anclas inventados a propósito. `TestAppDependencies.seedGrinders(json = TestGrinderCatalog.JSON)` lo siembra con el sembrador real. | F5 a F9 avanzan sin los datos reales, y nadie confunde un dato de prueba con uno de la fuente. |
| D37 | Escala, jerarquía y anclas de los predefinidos **(concreta)** | La fuente no da el total de clics ni la jerarquía. Regla: la **escala** es la del gráfico de la fuente, cuyo máximo corresponde al máximo en micras (el C2: 0–30 clics para 0–950 µm); si el fabricante publica otra, se anota en `notes` y en la tabla de la puerta para que decida el usuario (P3). La **jerarquía** sale del formato de la tabla de la fuente y de los máximos que aparecen en sus rangos, y se confirma con el manual del fabricante, citado en `notes`. Un dial que empieza en 1 (Fellow Ode) se modela con `scaleMin` > 0 y la descomposición absoluta (D11), y los pasos intermedios del Ode como unidad base `STEPS` con `perParent = 3`, sin fracciones de un tercio. Las **anclas** se calculan con `scaleMin + (µ − µmin) / (µmax − µmin) × (scaleMax − scaleMin)` sobre los puntos medios del §3.6, redondeadas a 1 decimal, y se omiten fuera del rango en micras; con `anchorsManual: true` se pueden ajustar a mano (§6 del spec). Nada se inventa: lo que falta va a `missing`. | Las anclas del §3.6 suponen un molino lineal entre su rango en micras y su escala: usar otra escala que la del gráfico de la fuente rompería esa correspondencia. |

## 3. Fases

Orden recomendado para un solo implementador: F0 → F1 → F2 (informe y **parada**) → F3 → F4 → F5 → F6 → F7 → F8 →
F9 → F10 (solo con la tabla aprobada) → F11. F2 solo toca el JSON del catálogo, así que puede ir en paralelo con F3
a F9 (otra sesión en un worktree, o el usuario indica "sigue con F3 mientras reviso"). F8 y F9 no dependen entre sí.
F10 necesita F2 aprobada, F4 y F9.

| Fase | Contenido | Tamaño |
|---|---|---|
| F0 | Diseño visual, componentes nuevos y línea base (tests, lint y rendimiento) | M |
| F1 | Dominio puro: modelo, formato, acarreo, conversión, validador, lector del catálogo y métodos nuevos | L |
| F2 | Investigación del catálogo en Honest Coffee Guide y **puerta** de revisión | M |
| F3 | Base de datos v5: tablas de molinos, migración 4 → 5 y ajuste estructurado en recetas y preparaciones | L |
| F4 | `GrinderDao`, `GrinderRepository`, `GrinderSettings` y `GrinderCatalogSeeder` (con el catálogo de prueba) | M |
| F5 | Ajustes → Molinos: lista, catálogo, detalle, editor con dos pestañas, regla e introducción compuesta | L |
| F6 | Molino y ajuste en el editor de recetas, "Editar preparación" y los detalles; borrado con aviso | M |
| F7 | Tarjeta de molienda en Preparar y en el paso 2, "Moler con" y el ajuste guardado en la preparación | L |
| F8 | Convertidor suelto, atajos "Ver en mis otros molinos" y fuente visible | M |
| F9 | Onboarding: "¿Qué molino usas?" | S |
| F10 | Sembrar el catálogo aprobado | S |
| F11 | Verificación: rendimiento, E2E y capturas por adb, lint y cierre | M |

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app` (nunca `./gradlew build`
ni builds limpios). Los tests instrumentados se lanzan siempre con `ANDROID_SERIAL=emulator-5556` (AVD headless
`SG_API34`, en `en-US`): el `emulator-5554` es del usuario y no se usa; si `adb devices -l` muestra otro
dispositivo, se pregunta. Sin ARTEMIS. Los tests de interfaz localizan por `testTag` y leen los textos esperados con
`stringResource` del contexto de la app, no con literales en español, por el idioma del emulador. Fallos previos que
no bloquean: `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap` (idioma) y la oscilación del caso
Experto de `SessionPerformanceTest`. Commits: uno por fase, en Conventional Commits y en inglés, sin
`Co-Authored-By` ni atribución, y solo si se piden. El plan, cuando se apruebe:
`docs(specs): add grinders implementation plan (SPEC-006)`.

### F0. Diseño visual y línea base

**Objetivo:** fijar la disposición de las pantallas nuevas y de las piezas que cambian, construir los componentes
que faltan y medir la línea base. El diseño se aprueba con el plan; F5 a F9 lo implementan tal cual.

#### F0.1 Reglas comunes

- Ancho de referencia 360 dp. Margen `spacing.base` (16 dp), separación entre tarjetas `spacing.sm`.
- Pantallas nuevas: `Scaffold(containerColor = bgLayout)`, `AntTopBar` con flecha atrás y `AntMessageHost`.
- Textos en `values/` y `values-en/`. Prefijos nuevos: `grinder_`, `grinders_`, `grind_`, `converter_`,
  `onboarding_grinder_` y `brew_method_`. Cada pantalla nueva lleva `@Preview` en claro y oscuro.
- Nombres de las anclas: Extrafino, Fino, Medio-fino, Medio, Medio-grueso, Grueso y Extragrueso; abreviaturas en
  la regla: EF, F, MF, M, MG, G, EG (en inglés: XF, F, MF, M, MC, C, XC).
- Unidades: Clics, Puntos, Muescas, Números, Rotaciones y Pasos (en inglés: Clicks, Dots, Notches, Numbers,
  Rotations, Steps).

#### F0.2 Componentes nuevos (`ui/components/`)

| Archivo | Firma |
|---|---|
| `AntStepper.kt` (nuevo) | `AntStepper(value: String, label: String, onIncrement: () -> Unit, onDecrement: () -> Unit, modifier: Modifier = Modifier, incrementEnabled: Boolean = true, decrementEnabled: Boolean = true, incrementDescription: String, decrementDescription: String, testTag: String? = null)`. Botones con `AntIcons.Plus` y `AntIcons.Minus`; el botón se repite mientras se mantiene pulsado (cada 120 ms tras 400 ms). Tags `<testTag>_inc`, `<testTag>_dec` y `<testTag>_value`. |
| `AntTabs.kt` (nuevo) | `data class AntTabItem<T>(val key: T, val label: String, val testTag: String)` y `AntTabs(items: List<AntTabItem<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier)`. `Role.Tab` y `selected` en la semántica. |
| `AntIcons.kt` | `Minus`, `Grinder` y `Convert` con el trazo de 24 × 24 de los actuales. |
| `AntComponentsPreview.kt` | `AntStepper` (normal, con `decrementEnabled = false`) y `AntTabs` en claro y oscuro. |

#### F0.3 Pantallas

Bocetos a 360 dp. `[x]` es un botón, `(x)` un icono, `v` el desplegable de un selector.

**Ajustes** (fila nueva, debajo de "Modo" y "Apariencia")

```
| Molinos                                          >  |  AntListItem, "settings_grinders"
|   Chestnut S3 · 2 molinos                           |  descripción: predeterminado y número
```

**Mis molinos (`GrindersScreen`)**

```
+------------------------------------------------------+
| <- Molinos                                  (convert)|  "grinders_converter"
+------------------------------------------------------+
| Timemore Chestnut S3        [Predeterminado]      > |  AntListItem, "grinder_row_<id>"
| Clics                                               |
| Comandante C40                                    > |
| Clics                                               |
| Mi JX                       [Propio]              > |
| Rotaciones · Números · Clics                        |
| [ + Añadir molino ]                                 |  AntButton Dashed, "grinders_add"
+------------------------------------------------------+
```

Sin molinos: `AntEmpty` "Aún no tienes molinos" con "Añadir molino". Retirado: `AntTag` "Retirado del catálogo".

**Añadir molino (`GrinderCatalogScreen`)**

```
| <- Añadir molino                                    |
| [ (lupa) Buscar marca o modelo                ]     |  "grinder_catalog_search"
| Baratza Encore                       [Lo tengo]     |  fila "grinder_catalog_<key>", botón "…_own_<key>"
| Clics                                               |
| Comandante C40                (check) En mis molinos|
| ...                                                 |
| [ Crear molino propio ]                             |  "grinder_catalog_create", al final de la lista
```

**Detalle (`GrinderDetailScreen`)**

```
| <- Chestnut S3                                      |
| Timemore   [Predefinido]                            |  o [Propio]; [Retirado del catálogo]
| Escala      0 – 30 clics · paso 1                   |  "grinder_detail_scale"
| Dirección   Mayor = más grueso                      |
| Unidades    Clics                                   |
| Método      [V60                          v]        |  "grinder_detail_method"
| [ Grueso ===|=====[ V60 ]=====|=== Fino ]           |  GrindRuler, "grinder_detail_ruler"
| Rangos      V60 13–22 · Pour over 13–29 · …         |  una fila por método con rango
| Anclas      Fino 9,5 · Medio-fino 15,8 · …          |
| Fuente: Honest Coffee Guide · valores orientativos  |  "grinder_attribution" + enlace
|   Chestnut S3                                       |
| [ Usar como predeterminado ]                        |  "grinder_detail_default"
| [ Duplicar para personalizar ]                      |  "grinder_detail_duplicate"
| [ Quitar de mis molinos ]                           |  "grinder_detail_unown" (o "…_own", "…_edit", "…_delete")
```

"Borrar" abre `AntModal` "¿Borrar Mi JX?" con "Se usa en 2 recetas. Las recetas y preparaciones conservan el
ajuste como copia." (o "No se usa en ninguna receta."), tags `grinder_delete_dialog` y `grinder_delete_usage`.

**Editor (`GrinderEditScreen`), pestaña Descripción**

```
| <- Nuevo molino                          [Guardar]  |  "grinder_edit_save" en la barra
| [ Descripción ]  [ Rangos ]                         |  AntTabs, "grinder_edit_tab_description/ranges"
| Fabricante   [                         ]            |  "grinder_edit_brand"
| Modelo       [                         ]            |  "grinder_edit_model"
| Unidad principal [Rotaciones            v]          |  "grinder_edit_unit_0"
| Números  [v]   por rotación [10]          (x)       |  "grinder_edit_unit_1", "…_per_parent_1"
| Clics    [v]   por número   [ 4]          (x)       |
| [ + Agregar unidad ]                                |  "grinder_edit_add_unit"
| Paso mínimo  [1   ] clics                           |  "grinder_edit_step"
| Dirección    [ Mayor = más grueso | Mayor = más fino ] |  AntSegmented, "grinder_edit_direction"
```

Errores bajo cada campo (`AntFormItem`): "Indica la marca", "Cada unidad debe contener al menos 2 de la
siguiente", "Unidad repetida", "El paso debe ser mayor que 0".

**Editor, pestaña Rangos**

```
| [ GrindRuler de vista previa: escala, rango del     |  fija arriba, "grinder_edit_preview"
|   método seleccionado y anclas                    ] |
| Escala total   de [GrindInput]  a [GrindInput]      |  "grinder_edit_scale_min/max"
| ---- Rangos por método ----                         |
| V60            de [GrindInput]  a [GrindInput] (x)  |  "grinder_edit_range_<METHOD>_min/max"
| Kalita Wave    [ + Añadir rango ]                   |  tocar una fila la selecciona en la vista previa
| ... (los 9 métodos)                                 |
| ---- Anclas de grosor ----                          |
| Extrafino      [ + Añadir ]                         |  "grinder_edit_anchor_<LEVEL>"
| Fino           [GrindInput]                    (x)  |
```

Los `GrindInput` de la escala solo limitan a ≥ 0 en molinos compuestos (D15); los de rangos y anclas, a la escala
actual. Errores junto al campo: "El mínimo debe ser menor que el máximo", "Fuera de la escala", "Las anclas deben
ir de fino a grueso".

**Introducción compuesta (`GrindInput`)**

```
|   [+]       [+]       [+]   |
|    2    ·    5    ·    0    |  "grind_input_<i>_value"
|   [-]       [-]       [-]   |
| Rotaciones Números   Clics  |
```

**Tarjeta de molienda (`GrindCard`), Preparar y paso 2 de la sesión**

```
| Molienda                                            |  AntCard, "grind_card"
| Receta: Chestnut S3 · 30 clics                      |  "grind_card_source"
| Moler con  [Comandante C40                    v]    |  "grind_card_grinder"
|                 ≈ 21 clics                          |  "grind_card_value", AntTitle(2)
|        [aproximado] [al límite del molino]          |  "grind_card_marks"
| [ GrindRuler ]                    (solo Experto)    |  "grind_card_ruler"
| Ver en mis otros molinos >                          |  "grind_card_converter"
| Fuente: Honest Coffee Guide · valores orientativos  |  "grind_card_attribution"
|   Chestnut S3 · Comandante C40                      |
```

| Caso | Línea de la receta | Ajuste |
|---|---|---|
| Mismo molino | "Receta: Chestnut S3 · 30 clics" | "30 clics" |
| Convertido | ídem | "≈ 21 clics", marcas según `reasons` |
| Receta sin molienda, con rango | "La receta no indica molienda" | "22 clics" + [sugerido] |
| Receta sin molienda, sin rango | "La receta no indica molienda" | (nada) |
| Sin equivalencia | ídem | "Sin equivalencia" + enlace "Completar los rangos" (propio) o "Duplicar para personalizar" (predefinido), "grind_card_fix" |
| Molino de la receta eliminado | "Chestnut S3 · 30 clics" [molino eliminado] | "Asigna un molino a la receta para convertir" |
| Sin receta | (nada) | "Sin receta: no hay ajuste que convertir" |
| Sin molinos propios | la de la receta | enlace "Añade tu molino para ver el equivalente", "grind_card_add" (sin selector) |

En la sesión, la tarjeta va debajo de `prep_stability` en el paso 2 y no en los demás pasos.

**Campo de molienda en `RecipeEditScreen` y `BrewEditScreen`** (sustituye a `recipe_edit_grind` y `brew_edit_grind`)

```
| Molino   [Chestnut S3                         v]    |  "recipe_edit_grinder" / "brew_edit_grinder"
| Ajuste   [GrindInput]                               |  "recipe_edit_grind_value" / "brew_edit_grind_value"
| Ver en mis otros molinos >                          |  solo en la receta, "recipe_edit_grind_converter"
```

Con molino eliminado: "Chestnut S3 · 30 clics" con `AntTag` "molino eliminado" y el selector en "Elige un
molino". Opciones del selector: "Sin molino", mis molinos y, si el actual ya no es de mis molinos, también él.

**Convertidor (`GrindConverterScreen`)**

```
| <- Convertidor                                      |
| Molino de origen [1Zpresso JX-Pro              v]   |  "converter_source" -> GrinderPicker
| Método           [V60                          v]   |  "converter_method"
| Ajuste           [GrindInput]                       |  "converter_value"
| ---- En mis molinos ----                            |
| Chestnut S3                         18 clics        |  "converter_row_<id>"
| Comandante C40                  ≈ 21 clics [aprox.] |
| Mi molino           Sin equivalencia  [Completar]   |
| Fuente: Honest Coffee Guide · valores orientativos  |  "converter_attribution"
```

**Onboarding, paso 2**

```
| <-                                        [Omitir]  |  "onboarding_grinder_back", "onboarding_grinder_skip"
| ¿Qué molino usas?                                   |  AntTitle(3)
| Lo usaremos para convertir los ajustes de las       |  Secondary
| recetas a tu molino.                                |
| [ (lupa) Buscar marca o modelo                ]     |  "onboarding_grinder_search"
| Baratza Encore                                      |  "onboarding_grinder_<key>"
| ...                                                 |
| Si el tuyo no está, créalo en Ajustes → Molinos.    |
```

#### F0.4 Entregables de código de F0

1. Línea base sobre `feat/grinders` antes de tocar código: `./gradlew :app:testDebugUnitTest` (número de tests),
   `./gradlew :app:lintDebug` (errores y avisos) y `SessionPerformanceTest` tres veces por caso (D32), anotados en
   la cabecera del plan.
2. Los componentes e iconos de F0.2 con sus previews.

**Tests:** `androidTest/.../ui/components/AntComponentsTest.kt`: `AntStepper` llama a cada acción, no lo hace
deshabilitado y repite al mantener pulsado; `AntTabs` llama a `onSelect` y marca `selected`.

**Verificación:** `./gradlew :app:compileDebugKotlin`,
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.components.AntComponentsTest`,
`./gradlew :app:lintDebug` y revisión de las previews en claro y oscuro.

Commit sugerido: `feat(ui): add stepper and tabs components for the grinder screens`.

### F1. Dominio puro y métodos nuevos

**Objetivo:** todo lo que no depende de Android ni de los datos reales: el modelo del §3.1, `GrindFormat`,
`GrindStepper`, `GrindConverter`, `GrinderValidator`, el lector del archivo del catálogo, la geometría de la regla
y los cuatro métodos nuevos.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `app/build.gradle.kts` | `implementation(libs.kotlinx.serialization.json)` (D33). Se conserva `debugImplementation(libs.kotlinx.serialization.core)`. |
| `history/HistoryModels.kt` | `enum class BrewMethod { V60, KALITA_WAVE, CHEMEX, ORIGAMI, AEROPRESS, FRENCH_PRESS, ESPRESSO, COLD_BREW, MOKA, OTHER }`. |
| `ui/history/HistoryFormatters.kt` | `fixedName` y `label()` según D27. |
| `ui/recipes/RecipeRow.kt` | El método con `label()` (o `methodOther` con `OTHER`). |
| `res/values*/strings.xml` | `brew_method_french_press`, `brew_method_cold_brew`, `brew_method_moka`; plurales `grind_unit_clicks`, `grind_unit_points`, `grind_unit_notches`, `grind_unit_numbers`, `grind_unit_rotations`, `grind_unit_steps`; `grind_unit_name_*`; `grind_level_*` y `grind_level_short_*`. |
| `grinder/GrindModels.kt` (nuevo) | `enum class GrindUnit { CLICKS, POINTS, NOTCHES, NUMBERS, ROTATIONS, STEPS }`, `enum class GrindDirection { HIGHER_IS_COARSER, HIGHER_IS_FINER }`, `enum class GrindLevel { EXTRA_FINE, FINE, MEDIUM_FINE, MEDIUM, MEDIUM_COARSE, COARSE, EXTRA_COARSE }`, `data class GrindUnitLevel(val unit: GrindUnit, val perParent: Int?)`, `data class GrindRange(val min: Double, val max: Double)`, `data class Grinder(val id: Long = 0, val catalogKey: String? = null, val brand: String, val model: String, val units: List<GrindUnitLevel>, val scaleMin: Double, val scaleMax: Double, val step: Double = 1.0, val direction: GrindDirection = GrindDirection.HIGHER_IS_COARSER, val ranges: Map<BrewMethod, GrindRange> = emptyMap(), val anchors: Map<GrindLevel, Double> = emptyMap(), val owned: Boolean = false, val retired: Boolean = false, val sourceUrl: String? = null, val duplicatedFromKey: String? = null)` con `isPredefined`, `primaryUnit` y `baseUnit`. |
| `grinder/GrindMath.kt` (nuevo) | `EPS`, `snap(value, grinder)`, `clampToScale`, `decimalsOf(step)`, `multipliers(units): List<Long>` (D11). |
| `grinder/GrindTexts.kt` (nuevo) | `interface GrindTexts` (D12). |
| `grinder/GrindFormat.kt` (nuevo) | `object GrindFormat { fun decompose(value: Double, units: List<GrindUnitLevel>): List<Double>; fun compose(parts: List<Double>, units: List<GrindUnitLevel>): Double; fun format(value: Double, grinder: Grinder, texts: GrindTexts): String; fun describe(value: Double, grinder: Grinder, texts: GrindTexts): String; fun label(grinder: Grinder, value: Double?, texts: GrindTexts): String }`. |
| `grinder/GrindStepper.kt` (nuevo) | D13. |
| `grinder/GrindConverter.kt` (nuevo) | `Conversion`, `ConversionReason` y `object GrindConverter { fun convert(…): Conversion; fun suggested(method: BrewMethod?, grinder: Grinder): Double? }` (D14). |
| `grinder/GrinderValidator.kt` (nuevo) | `sealed interface GrinderError` (`BlankBrand`, `BlankModel`, `NoUnits`, `ScaleOrder`, `StepNotPositive`, `ScaleNotMultipleOfStep`, `NegativeCompoundScale`, `DuplicateUnit(index)`, `PrimaryWithPerParent`, `PerParentTooSmall(index)`, `RangeOrder(method)`, `RangeOutsideScale(method)`, `RangeForOther`, `AnchorOutsideScale(level)`, `AnchorsNotMonotonic(level)`) y `object GrinderValidator { fun validate(grinder: Grinder): List<GrinderError> }` (D15). |
| `grinder/GrindSetting.kt` (nuevo) | `GrindSetting`, `GrindChoice` y `fun GrindChoice.toSetting(grinder: Grinder?, texts: GrindTexts): GrindSetting?` (D16, D17). |
| `grinder/GrinderCatalogFile.kt` (nuevo) | DTO `@Serializable` (`CatalogFileDto`, `CatalogGrinderDto`, …), `sealed interface CatalogError` y `object GrinderCatalogFile { fun parse(json: String): Result<CatalogData> }` con `data class CatalogData(val version: Int, val grinders: List<Grinder>)` (D33). `Json { ignoreUnknownKeys = false }`. |
| `grinder/GrindRulerModel.kt` (nuevo) | `data class RulerTick(val value: Double, val major: Boolean, val label: String?)` y `object GrindRulerModel { fun fraction(value: Double, grinder: Grinder): Float; fun ticks(grinder: Grinder): List<RulerTick> }`: fracción 0 = grueso (izquierda) en las dos direcciones; marcas mayores con número cada unidad primaria en los compuestos (cada 2 o 5 si salen más de 8) y cada 1, 2, 5 o 10 × 10ⁿ en los simples, con hasta 4 menores entre mayores. |

**Tests (JVM):**

| Archivo | Casos |
|---|---|
| `test/.../grinder/GrinderFixtures.kt` (nuevo) | Los molinos de prueba de D36 y el del §5 del spec (rotaciones; números `perParent = 4`; clics `perParent = 10`), y `FakeGrindTexts` en español. |
| `test/.../grinder/GrindFormatTest.kt` (nuevo) | "1 · 2 · 3" → 63 clics → "1 · 2 · 3"; ida y vuelta de todos los múltiplos del paso de la escala en los cuatro molinos de prueba (paso 1, 0,5 y 0,1); "30 clics", "1 clic", "15,5 clics"; forma larga; `label` con y sin valor; escala que empieza en 3 ("1 · 0"). |
| `test/.../grinder/GrindStepperTest.kt` (nuevo) | Acarreo hacia arriba (1 · 3 · 9 + 1 clic = 2 · 0 · 0) y hacia abajo (2 · 0 · 0 − 1 = 1 · 3 · 9); +1 en la unidad superior; límite en los dos extremos; paso 0,5; con `null` devuelve `start`. |
| `test/.../grinder/GrindConverterTest.kt` (nuevo) | Cada criterio del §5: posición lineal igual en los dos rangos; extrapolación sin `AT_LIMIT` dentro de la escala; `AT_LIMIT` en el extremo; anclas con `ANCHORS` sin rango y con `OTHER` y con método `null`; `NoEquivalent` con menos de 2 anclas comunes; redondeo a 1 y a 0,5 con `ROUNDED` solo si cambia; invertido frente a normal conserva la posición de fino a grueso; mismo molino `Exact`; `suggested` es el punto medio redondeado o `null`. |
| `test/.../grinder/GrinderValidatorTest.kt` (nuevo) | Un caso por cada `GrinderError` y un molino válido sin errores. |
| `test/.../grinder/GrinderCatalogParserTest.kt` (nuevo) | JSON de prueba válido; JSON roto; método desconocido; rango de `OTHER`; un molino inválido rechaza el archivo; clave repetida; `sourceUrl` vacía; unidad compuesta y valores en unidades base. |
| `test/.../grinder/GrinderCatalogFileTest.kt` (nuevo) | Lee `File("src/main/assets/grinders/catalog.json")`; hasta F10, `assumeTrue(file.exists())`. Comprueba: se lee con `GrinderCatalogFile.parse`; cada molino pasa `GrinderValidator`; claves únicas; `sourceUrl` en `https://honestcoffeeguide.com/`; rangos dentro de la escala; anclas monótonas; sin `anchorsManual`, cada ancla es la fórmula de D37 (±0,05) y las que caen fuera del rango en micras no están. |
| `test/.../grinder/GrindRulerModelTest.kt` (nuevo) | Fracción en las dos direcciones (grueso a la izquierda); marcas de un molino simple y de uno compuesto; nunca más de 8 etiquetas. |
| `test/.../history/HistoryConvertersTest.kt` (nuevo) | Ida y vuelta por nombre de todos los `BrewMethod`; un nombre desconocido se lee como `OTHER`. |

**Tests (instrumentados):** `RecipeEditScreenTest` y `BrewEditScreenTest`: el selector de método ofrece "Prensa
francesa", "Espresso", "Cold brew" y "Moka" y guarda el elegido; `HistoryScreenTest`: con una preparación
`FRENCH_PRESS`, el filtro de método la ofrece y filtra.

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.grinder.*" --tests "dev.dotscale.history.*" --tests "dev.dotscale.ui.recipes.*"`,
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.recipes.RecipeEditScreenTest,dev.dotscale.ui.history.BrewEditScreenTest,dev.dotscale.ui.history.HistoryScreenTest`,
`./gradlew :app:lintDebug` y `grep -l "import android" app/src/main/kotlin/dev/dotscale/grinder/{GrindModels,GrindMath,GrindFormat,GrindTexts,GrindStepper,GrindConverter,GrinderValidator,GrindSetting,GrinderCatalogFile,GrindRulerModel}.kt`
sin resultados.

Commit sugerido: `feat(grinder): add the grind setting model, compound format and converter`.

### F2. Investigación del catálogo y puerta

**Objetivo:** el borrador de `catalog.json` con los 11 molinos del §3.6 y la tabla de revisión. **Termina con una
parada:** el implementador presenta la tabla y no siembra el catálogo (F10) hasta que el usuario la apruebe o la
corrija. Necesita F1 (lector y validador) y acceso a internet. Puede ir en paralelo con F3 a F9.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `app/src/main/assets/grinders/catalog.json` (nuevo, borrador) | `version: 1` y los molinos con el esquema de D33. Nada lo lee en la app hasta F10 (D35). |

**Molinos y claves:** `timemore-chestnut-c2`, `timemore-chestnut-c3s` (C3/C3S, una entrada si la fuente las trata
igual; si no, dos, y se indica), `timemore-chestnut-s3`, `comandante-c40`, `1zpresso-jx-pro`, `1zpresso-k-ultra`,
`1zpresso-q2`, `kingrinder-k6`, `baratza-encore`, `fellow-ode-gen-2` y `femobook-a2`.

**Procedimiento por molino:**
1. Buscar la página de Honest Coffee Guide (`https://honestcoffeeguide.com/<modelo>-grind-settings/`; si la ruta no
   existe, buscar en el sitio). Si no hay página, el molino queda con todos sus datos ausentes y se dice.
2. Anotar el rango en micras, el formato de la tabla ("Clicks from zero", "Rotations from zero", números del dial…)
   y los rangos de V60, Pour Over, AeroPress, French Press, Espresso, Cold Brew y Moka Pot, con el texto literal en
   `source`. Los demás métodos de la fuente (Turkish, Siphon, Cupping…) se ignoran.
3. Pasar los rangos a unidades base con `GrindFormat.compose` según la jerarquía, y asignarlos con la tabla del §3.6
   (Pour Over → `KALITA_WAVE`, `CHEMEX` y `ORIGAMI`).
4. Escala, jerarquía, paso y dirección según D37, con la página del fabricante citada en `notes` cuando se use.
5. Anclas con la fórmula de D37.
6. Todo lo que falte va a `missing` (por ejemplo, `"COLD_BREW"`, `"scale.max: el gráfico no lo muestra"`). No se
   inventa ningún valor.
7. `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.grinder.GrinderCatalogFileTest"` en verde.

**Informe de la puerta** (en la respuesta del implementador, sin documento aparte, §3.6). Una tabla por molino:

| Campo | Valor | Origen |
|---|---|---|
| Clave, marca, modelo | `timemore-chestnut-c2`, Timemore, Chestnut C2 | — |
| Unidades | Clics | fuente: "Clicks from zero" |
| Escala | 0 – 30 clics | derivada: máximo del gráfico (0–950 µm) |
| Paso | 1 | fabricante (URL) |
| Dirección | Mayor = más grueso | fuente: "from zero" |
| Rango en micras | 0 – 950 µm | fuente |
| V60 / Kalita, Chemex, Origami / AeroPress / Prensa francesa / Espresso / Cold brew / Moka | 13–22 / 13–29 / 11–30 / 22–30 / 6–12 / 26–30 / 12–20 | fuente (texto literal) |
| Anclas (EF, F, MF, M, MG, G, EG) | 3,2 · 9,5 · 15,8 · 22,1 · 28,4 · — · — | calculadas; G y EG fuera del rango en micras |
| URL | `https://honestcoffeeguide.com/timemore-c2-grind-settings/` | — |
| Datos ausentes | ninguno, o la lista | — |
| Diferencias con el fabricante | p. ej., "el fabricante da 36 clics" | P3 |

Al final del informe: los molinos sin página, las decisiones de jerarquía (con su justificación) y las preguntas
concretas para el usuario. **Después, el implementador se detiene.** Si el usuario lo autoriza, sigue con F3 a F9
mientras revisa; F10 no empieza sin la aprobación.

**Verificación:** el test del paso 7. La tabla del informe y el JSON coinciden valor a valor.

Commit sugerido (tras la aprobación, con las correcciones aplicadas): `feat(grinder): add the reviewed grinder catalog data`.

### F3. Base de datos v5 y ajuste estructurado

**Objetivo:** las tablas de molinos, la migración 4 → 5 y el ajuste estructurado en recetas y preparaciones. Los
editores conservan el molino y el ajuste aunque todavía no los muestren (F6): el campo de texto "Molienda"
desaparece en esta fase.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `grinder/GrinderEntities.kt` (nuevo) | Entidades y relación de D20, con los nombres de tablas y columnas del §3.4, y `GrinderWithDetails.toGrinder()` / `Grinder.toRows()`. |
| `grinder/GrinderConverters.kt` (nuevo) | D20. |
| `grinder/GrinderDao.kt` (nuevo, mínimo) | `observe(id)`, `get(id)`, `exists(id): Boolean` e `insert` de molino con sus hijos (lo necesitan los tests). El resto, en F4. |
| `recipe/RecipeEntities.kt` | `RecipeEntity` sin `grind`; `grinderId: Long? = null`, `grindValue: Double? = null`, `grindLabel: String? = null`; `ForeignKey(GrinderEntity, ["id"], ["grinderId"], onDelete = SET_NULL)` e `Index("grinderId")`. |
| `history/BrewEntities.kt` | `BrewEntity` igual; la FK y el índice nuevos junto a los de `coffeeId`. |
| `history/HistoryDatabase.kt` | `version = 5`, las cuatro entidades nuevas, `abstract fun grinderDao(): GrinderDao`, `@TypeConverters(HistoryConverters::class, CoffeeConverters::class, GrinderConverters::class)`, `addMigrations(…, MIGRATION_4_5)` y la línea de la versión 5 en el KDoc. Se genera `app/schemas/dev.dotscale.history.HistoryDatabase/5.json`. |
| `history/Migrations.kt` | `val MIGRATION_4_5` (D19). |
| `recipe/Recipe.kt` | `grind: GrindSetting? = null`. |
| `history/HistoryModels.kt` | `BrewDetails.grind: GrindSetting? = null`; `normalized()` deja `null` si las tres partes lo son y recorta `label`. |
| `recipe/RecipeRepository.kt` | `toRecipe` y `toEntity` con las tres columnas. |
| `recipe/RecipeDao.kt` | `save` y `restore` con la comprobación de D18. |
| `history/BrewRepository.kt` | `save` escribe `details?.grind` en las tres columnas. |
| `history/BrewDao.kt` | `updateDetailsRow` con `grinderId`, `grindValue` y `grindLabel`; `insertBrew`, `restore` y `applyDetails` con D18. |
| `recipe/BuiltInRecipeSeeder.kt` | El `INSERT` de `recipes` sin `grind` (sirve igual para el esquema v2 de `migration1To2`, donde la columna admite `NULL`). |
| `recipe/RecipeFromBrew.kt` | Sin cambios de lógica: `grind = details.grind` pasa a ser `GrindSetting?`. |
| `session/ScaleUiState.kt` | `ActiveTarget.sourceGrind: GrindSetting? = null`. |
| `session/ScaleSessionManager.kt` | `TargetBase.sourceGrind`; `resolveRecipe` y `resolveBrew` lo rellenan y `prefill` deja de llevar la molienda (D24); `scaledTarget` lo copia. |
| `ui/recipes/RecipeEditForm.kt`, `RecipeEditViewModel.kt`, `RecipeEditScreen.kt` | `grind: GrindChoice = GrindChoice.None` en el formulario; `from(recipe)` y `toRecipe` lo conservan (con `label` sin regenerar hasta F6); fuera el campo de texto y `recipe_edit_grind`. |
| `ui/history/BrewEditForm.kt`, `BrewEditViewModel.kt`, `BrewEditScreen.kt` | Igual con `brew_edit_grind`. |
| `ui/history/BrewDetailScreen.kt` | `DetailsCard` muestra `grindLabel` de forma provisional (F6 lo sustituye por el molino vivo). |
| `res/values*/strings.xml` | Se borran `recipe_edit_grind_hint` y `brew_edit_grind_hint`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../history/HistoryMigrationTest.kt` | `migrates4To5RebuildingRecipesAndBrews`: una v4 con recetas (con `grind`, etapas de los tres tipos y una predefinida) y preparaciones (con `grind`, curva, objetivo y café vinculado). Tras migrar: `PRAGMA table_info` sin `grind` y con las tres columnas a `NULL`; el resto de columnas igual fila a fila; mismo número de filas en `recipe_stages`, `brew_curves` y `brew_targets`; `PRAGMA foreign_key_list` de `recipes` y `brews` con `grinders` (`SET NULL`) y, en `brews`, `coffees`; `PRAGMA foreign_key_check` vacío; tablas de molinos vacías; índices presentes. `migrates4To5KeepsTheAutoincrementCounters`: con la receta y la preparación de id más alto borradas, el siguiente alta tras migrar no reutiliza su id. `migrates4To5ThenRoomReadsEverything`: abre con Room y todas las migraciones; las recetas se leen con sus pasos y las curvas se decodifican. `migrates1To2To3To4To5InAChain`. `aFreshInstallIsCreatedAtVersion4WithTheBuiltIns` pasa a `…Version5…` (sin molino). Las aperturas con Room añaden `MIGRATION_4_5`. |
| `androidTest/.../recipe/RecipeDaoTest.kt` | Las tres columnas se guardan y se leen; guardar y "Deshacer" con un molino que ya no existe deja `grinderId = null` y conserva valor y copia. |
| `androidTest/.../history/BrewDaoTest.kt` | `insertBrew` con molino, sin él y con un id inexistente; `applyDetails` y `restore` con D18. |
| `androidTest/.../history/TestBrews.kt`, `BrewDetailScreenTest.kt`, `BrewEditScreenTest.kt` | Sin `grind` de texto; el test que escribía "21 clics" pasa a F6. |
| `test/.../ui/recipes/RecipeEditFormTest.kt`, `test/.../ui/history/BrewEditFormTest.kt` | Ida y vuelta de `GrindChoice` (`None`, `Live`, `Orphan`). |
| `test/.../history/HistoryModelsTest.kt` | `normalized()` del ajuste. |
| `test/.../recipe/RecipeFromBrewTest.kt` | La receta lleva el `GrindSetting` de la preparación. |
| `test/.../session/ScaleSessionManagerTargetTest.kt`, `ScaleTestFakes.kt` | El `prefill` ya no lleva molienda; `ActiveTarget.sourceGrind` es el de la receta o el de la preparación de referencia. |

**Verificación:** `./gradlew :app:compileDebugKotlin` (genera `5.json`),
`./gradlew :app:testDebugUnitTest --tests "dev.dotscale.recipe.*" --tests "dev.dotscale.history.*" --tests "dev.dotscale.session.*" --tests "dev.dotscale.ui.recipes.*" --tests "dev.dotscale.ui.history.*"`
y `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.history.HistoryMigrationTest,dev.dotscale.recipe.RecipeDaoTest,dev.dotscale.history.BrewDaoTest,dev.dotscale.ui.history.BrewDetailScreenTest,dev.dotscale.ui.history.BrewEditScreenTest,dev.dotscale.ui.recipes.RecipeEditScreenTest`.
Además, `grep -rn "\.grind\b\|grind =" app/src` solo encuentra el `GrindSetting`.

Commit sugerido: `feat(history): add grinder tables and structured grind settings, migrating the database to v5`.

### F4. Repositorio, preferencias y sembrador

**Objetivo:** el contrato del §3.5 (`GrinderDao`, `GrinderRepository`), `GrinderSettings` y `GrinderCatalogSeeder`,
probados con el catálogo de prueba. El sembrador **no** se invoca al arrancar (F10).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `grinder/GrinderDao.kt` | `observeOwned()`, `observeCatalog()` (predefinidos sin retirar), `observeAll()`, `observe(id)`, `get(id)`, `byCatalogKey(key)`, `catalogKeys()`, `@Transaction save(grinder, units, ranges, anchors): Long` (inserta o actualiza y reemplaza los hijos), `setOwned(id, owned)`, `recipeCount(id): Flow<Int>`, `isUsed(id): Boolean` (recetas o preparaciones), `recipesUsing(id)` y `brewsUsing(id)` (id y `grindValue`), `@Transaction deleteRelabeling(id, recipeLabels: Map<Long, String>, brewLabels: Map<Long, String>)` y `@Transaction syncCatalog(grinders: List<GrinderRows>, defaultId: Long?): SyncCounts`. |
| `grinder/GrinderRepository.kt` (nuevo) | `owned(): Flow<List<Grinder>>`, `catalog(): Flow<List<Grinder>>`, `all(): Flow<List<Grinder>>`, `grinder(id: Long?): Flow<Grinder?>`, `get(id)`, `save(grinder): Long` (D21), `duplicateDraft(id): Grinder?` (D22), `setOwned(id, owned)`, `delete(id)` (D17, D21), `recipeCount(id): Flow<Int>`. Ordena según D28. |
| `grinder/GrinderCatalogSeeder.kt` (nuevo) | `fun interface CatalogSource { fun read(): String? }`, `class AssetCatalogSource(context: Context, path: String = "grinders/catalog.json")`, `sealed interface SyncResult` y `class GrinderCatalogSeeder` (D34). |
| `grinder/ResourcesGrindTexts.kt` (nuevo) | `class ResourcesGrindTexts(context: Context) : GrindTexts`. |
| `settings/GrinderSettings.kt` (nuevo) | `interface DefaultGrinderStore { val defaultGrinderId: StateFlow<Long?>; fun setDefaultGrinderId(id: Long?) }`, `interface CatalogVersionStore { val catalogVersion: Int; fun setCatalogVersion(version: Int) }` y `class GrinderSettings(context, prefsName = BrewSettings.PREFS_NAME) : DefaultGrinderStore, CatalogVersionStore`, claves `default_grinder_id` y `grinder_catalog_version`. |
| `AppDependencies.kt`, `DotScaleApp.kt` | `grinderSettings`, `grinderRepository` y `grindTexts`. `grinderCatalogSeeder` se crea, sin llamarlo. |
| `androidTest/.../ui/TestAppDependencies.kt` | Lo mismo, y `seedGrinders(json: String = TestGrinderCatalog.JSON)`. |
| `androidTest/.../grinder/TestGrinderCatalog.kt` (nuevo) | D36. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../grinder/GrinderDaoTest.kt` (nuevo) | Alta con hijos y lectura; editar reemplaza unidades, rangos y anclas; mis molinos y catálogo; `setOwned`; `recipeCount`; borrar deja `grinderId = NULL` en recetas y preparaciones, conserva el valor y regenera la copia con el nombre actual (renombrado antes de borrar). |
| `androidTest/.../grinder/GrinderRepositoryTest.kt` (nuevo) | `duplicateDraft` según D22; `save` rechaza un inválido y un predefinido; quitar de mis molinos y borrar anulan el predeterminado si era ese y no si era otro. |
| `androidTest/.../grinder/GrinderCatalogSeederTest.kt` (nuevo) | Primer arranque: siembra y guarda la versión. Misma versión: no escribe. Versión mayor: actualiza por clave conservando el id y `owned` y reemplazando los hijos; no toca los propios ni los duplicados. Clave que desaparece: con receta, con preparación, `owned` o predeterminado → `retired`; sin uso → borrado. Clave que vuelve → `retired = false`. JSON roto, molino inválido o archivo ausente → `Failed`, sin cambios en la base ni en la versión. |
| `androidTest/.../settings/GrinderSettingsTest.kt` (nuevo) | Sin valor, `null` y versión 0; persistencia entre instancias; `setDefaultGrinderId(null)` borra la clave. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.grinder.*"` y
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.grinder.GrinderDaoTest,dev.dotscale.grinder.GrinderRepositoryTest,dev.dotscale.grinder.GrinderCatalogSeederTest,dev.dotscale.settings.GrinderSettingsTest`.

Commit sugerido: `feat(grinder): add the grinder repository, settings and catalog seeder`.

### F5. Ajustes → Molinos

**Objetivo:** la lista de mis molinos, el catálogo con búsqueda, el detalle, el editor con dos pestañas, la regla y
la introducción compuesta (§3.7), con el catálogo de prueba.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `NavigationKeys.kt` | `Grinders`, `GrinderCatalog`, `GrinderDetail(id)` y `GrinderEdit(id, fromId, openRanges)` (D5). |
| `TabNavigation.kt` | `fun showsBottomBar(stack: List<NavKey>): Boolean` (D5); `Navigation.kt` lo llama con la pila activa. |
| `Navigation.kt` | Entradas nuevas; `Settings` recibe `onOpenGrinders = { stack.add(Grinders) }`. |
| `ui/settings/SettingsScreen.kt` | Parámetro `onOpenGrinders: () -> Unit` y la fila `settings_grinders` (F0.3); la descripción sale de `GrinderRepository.owned()` y `defaultGrinderId`. |
| `ui/grinders/GrindersScreen.kt`, `GrindersViewModel.kt` (nuevos) | Lista (D28, D31) y estado vacío. |
| `ui/grinders/GrinderCatalogScreen.kt`, `GrinderCatalogViewModel.kt` (nuevos) | Búsqueda con `fold()`; "Lo tengo"; "Crear molino propio" → `GrinderEdit()`. |
| `ui/grinders/GrinderDetailScreen.kt`, `GrinderDetailViewModel.kt` (nuevos) | D7; borrado con `AntModal` y `recipeCount` (F0.3). |
| `ui/grinders/GrinderEditForm.kt` (nuevo, puro) | `data class GrinderEditForm(brand, model, units: List<UnitForm>, step: String, direction, scaleMin: Double?, scaleMax: Double?, ranges: Map<BrewMethod, RangeForm>, anchors: Map<GrindLevel, Double?>)`, `addUnit()`, `removeUnit(i)`, `toGrinder(id, base: Grinder?): Grinder?` y `errors(): Map<GrinderField, GrinderError>` desde `GrinderValidator`. |
| `ui/grinders/GrinderEditScreen.kt`, `GrinderEditViewModel.kt` (nuevos) | Pestañas con `AntTabs`; `openRanges` abre la segunda; alta, edición y duplicado (D22); diálogo de cambios sin guardar como en `RecipeEditScreen`. |
| `ui/grinders/GrindInput.kt` (nuevo) | `GrindInput(value: Double?, grinder: Grinder, onChange: (Double) -> Unit, start: Double, modifier, testTag: String, clampToScale: Boolean = true)` (D2, D13). |
| `ui/grinders/GrindRuler.kt` (nuevo) | `GrindRuler(grinder: Grinder, method: BrewMethod?, value: Double?, showAnchors: Boolean, modifier, testTag)` (D3). |
| `ui/grinders/GrindTexts.kt` (nuevo) | `@Composable` `rememberGrindTexts()`, `GrindUnit.label()`, `GrindLevel.label()` y `GrindDirection.label()`. |
| `ui/grinders/GrindAttribution.kt` (nuevo) | D9. |
| `res/values*/strings.xml` | `grinders_*`, `grinder_*` y `settings_grinders`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../ui/grinders/GrinderEditFormTest.kt` (nuevo) | Formulario a `Grinder` (simple y compuesto); cada error en su campo; añadir y quitar unidades; cambiar unidades o escala no toca los rangos guardados. |
| `test/.../TabNavigationTest.kt` | `showsBottomBar` con pilas: sin barra con `Session` en cualquier posición, con un editor o el convertidor en la cima; con barra en `Grinders` sin sesión. |
| `androidTest/.../ui/grinders/GrindersScreenTest.kt` (nuevo) | Fila con unidad primaria y "Predeterminado"; estado vacío; "Añadir molino" abre el catálogo. |
| `androidTest/.../ui/grinders/GrinderCatalogScreenTest.kt` (nuevo) | "acme tres" encuentra "Acme Tres" y "ACMÉ" también; "Lo tengo" lo añade; un retirado no aparece; "Crear molino propio" abre el editor. |
| `androidTest/.../ui/grinders/GrinderEditScreenTest.kt` (nuevo) | **Criterio:** crear un molino con rotaciones, números y clics, rango de V60 y dos anclas, guardar y verlo en mis molinos. Errores junto al campo; "Guardar" no guarda con errores; la vista previa muestra el rango del método tocado. |
| `androidTest/.../ui/grinders/GrinderDetailScreenTest.kt` (nuevo) | Predefinido: sin "Editar" ni "Borrar" y con la fuente (**criterio**). **Criterio:** duplicar abre el editor con "(copia)", guardar la deja en mis molinos editable y el original sigue sin "Editar". Propio: "Editar" y "Borrar"; el diálogo dice "Se usa en 2 recetas" con dos recetas sembradas. "Quitar de mis molinos" del predeterminado lo anula. |
| `androidTest/.../ui/grinders/GrindInputTest.kt` (nuevo) | Acarreo con toques en los dos sentidos; límite de la escala deshabilita el botón; descripción larga en la semántica. |
| `androidTest/.../ui/SettingsScreenTest.kt`, `SettingsNavigationTest.kt` | La fila "Molinos" abre la lista. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.ui.grinders.*" --tests "dev.dotscale.TabNavigationTest"`,
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=dev.dotscale.ui.grinders`,
la misma con `class=dev.dotscale.ui.SettingsScreenTest,dev.dotscale.ui.SettingsNavigationTest,dev.dotscale.ui.TabsNavigationTest`
y `./gradlew :app:lintDebug`.

Commit sugerido: `feat(grinder): add grinder list, catalog, detail and editor screens`.

### F6. Recetas y preparaciones

**Objetivo:** el selector de molino y la introducción compuesta en el editor de recetas y en "Editar preparación",
"Molino · ajuste" en los detalles y el borrado de un molino usado (§3.7, §4).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/grinders/GrindSettingField.kt` (nuevo) | `GrindSettingField(choice: GrindChoice, options: List<Grinder>, current: Grinder?, method: BrewMethod?, onChange: (GrindChoice) -> Unit, tagPrefix: String)` (F0.3). |
| `ui/grinders/GrindSettingText.kt` (nuevo) | `GrindSettingText(setting: GrindSetting?, grinder: Grinder?, modifier, testTag)`: "Molino · ajuste" con el molino vivo, la copia con "molino eliminado" o el aviso de D30. |
| `ui/recipes/RecipeEditForm.kt` | `withGrinder(grinderId: Long?)` (a `Live(id, null)` o `None`) y `withGrindValue(value)`. |
| `ui/recipes/RecipeEditViewModel.kt` | Recibe `GrinderRepository`, `DefaultGrinderStore` y `GrindTexts`; expone mis molinos y el molino actual; receta nueva con el predeterminado (D21); al guardar, `GrindChoice.toSetting` con el molino vivo (D17). |
| `ui/recipes/RecipeEditScreen.kt` | `GrindSettingField` tras el método. |
| `ui/history/BrewEditForm.kt`, `BrewEditViewModel.kt`, `BrewEditScreen.kt` | Igual, sin preselección. |
| `ui/history/BrewDetailViewModel.kt`, `BrewDetailScreen.kt` | `BrewDetailViewModel` recibe `GrinderRepository` y observa el molino; `DetailsCard` usa `GrindSettingText` (`brew_detail_grind`). |
| `Navigation.kt` | Constructores de los ViewModels. |
| `res/values*/strings.xml` | `grind_none`, `grind_choose`, `grind_orphan`, `grind_out_of_scale` y `grind_value_hint`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../ui/recipes/RecipeEditFormTest.kt`, `test/.../ui/history/BrewEditFormTest.kt` | Elegir molino deja el valor en `null`; "Sin molino"; `Orphan` se conserva hasta elegir y desaparece al elegir; la copia se regenera con el molino vivo. |
| `androidTest/.../ui/recipes/RecipeEditScreenTest.kt` | Receta nueva con el predeterminado; elegir otro molino; tocar "+" fija el valor sugerido; guardar escribe molino, valor y copia; "Sin molino" deja las tres a `NULL`. |
| `androidTest/.../ui/history/BrewEditScreenTest.kt` | Cambiar molino y ajuste y guardar. |
| `androidTest/.../ui/history/BrewDetailScreenTest.kt` | "Comandante C40 · 21 clics" con el molino vivo; copia con "molino eliminado"; aviso fuera de escala. |
| `androidTest/.../ui/grinders/GrinderDeleteTest.kt` (nuevo) | **Criterio:** borrar un molino propio usado en una receta muestra "Se usa en 1 receta"; tras confirmar, el editor de esa receta muestra la copia con "molino eliminado" y la preparación que lo usaba también; el predeterminado queda a `null`. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.ui.recipes.*" --tests "dev.dotscale.ui.history.*"`,
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.recipes.RecipeEditScreenTest,dev.dotscale.ui.history.BrewEditScreenTest,dev.dotscale.ui.history.BrewDetailScreenTest,dev.dotscale.ui.grinders.GrinderDeleteTest`
y `./gradlew :app:lintDebug` (sin `UnusedResources` de las cadenas borradas en F3).

Commit sugerido: `feat(recipes): pick the grinder and grind setting in recipes and brews`.

### F7. Tarjeta de molienda y la preparación guardada

**Objetivo:** la tarjeta de molienda en Preparar y en el paso 2, "Moler con", la regla en Experto y el ajuste
guardado en la preparación (§3.7, §3.8 pasos 4 a 7).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `grinder/GrindCardState.kt` (nuevo, puro) | `GrindSource(method: BrewMethod?, setting: GrindSetting?)`, `GrindFix`, `GrindOutcome`, `ConverterArgs(sourceId: Long, method: BrewMethod?, value: Double)`, `GrindCardState`, `grindCardState(…)` y `snapshot(texts)` (D25). |
| `session/GrindSelection.kt` (nuevo) | `fun interface SessionGrind`, `NoSessionGrind`, `interface SessionGrinders { fun owned(): Flow<List<Grinder>>; fun grinder(id: Long?): Flow<Grinder?> }`, `RepositorySessionGrinders(repository)` y `class GrindSelection(targets: Flow<ActiveTarget?>, grinders: SessionGrinders, defaults: DefaultGrinderStore, texts: GrindTexts, scope: CoroutineScope) : SessionGrind` con `state` y `choose(grinderId)` (D23). |
| `session/ScaleSessionManager.kt` | Parámetro `grind: SessionGrind = NoSessionGrind`; `sessionGrind` y `grindFrozenFor` junto a los del café; `save` con `details = (frozen?.prefill ?: BrewDetails()).copy(grind = sessionGrind)` (y `null` si todo queda vacío); `forgetFrozen` los olvida. |
| `settings/ExperienceMode.kt` | `ExpertFeature.GRIND_RULER` (D29). |
| `ui/grinders/GrindCard.kt` (nuevo) | `GrindCard(state: GrindCardState, mode: ExperienceMode, onChoose: (Long) -> Unit, onAddGrinder: () -> Unit, onFix: (GrindFix, Long) -> Unit, onOpenConverter: ((ConverterArgs) -> Unit)?, modifier)`; "Moler con" con `AntSelect`. `onOpenConverter` es `null` hasta F8. |
| `ui/prepare/PrepareViewModel.kt`, `PrepareScreen.kt` | El ViewModel recibe `GrindSelection` y expone `grind`; la tarjeta va entre `DoseCard` y "Empezar". |
| `ui/session/SessionScreen.kt` | `SessionScreen(…, grind: GrindSelection, onAddGrinder, onFixGrinder, onOpenConverter)`; `PrepPanel` muestra la tarjeta en `PrepStep.DOSE`. |
| `AppDependencies.kt`, `DotScaleApp.kt`, `TestAppDependencies.kt` | `grindSelection`; el gestor recibe `SessionGrind { grindSelection.current() }` (D23). |
| `Navigation.kt` | Entradas `Prepare` y `Session`: `onAddGrinder = { stack.add(Grinders) }` y `onFix` → `GrinderEdit(id, openRanges = true)` o `GrinderEdit(fromId = id, openRanges = true)`. |
| `androidTest/.../ui/session/SessionPerformanceTest.kt` | El montaje siembra el catálogo de prueba, marca dos molinos como míos y da molino a la receta larga (D32). |
| `res/values*/strings.xml` | `grind_card_*`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../grinder/GrindCardStateTest.kt` (nuevo) | Preselección 1-2-3 del §3.7; la elección explícita manda; cada fila de la tabla de F0.3; `fix` según propio o predefinido; fuente si interviene un predefinido; sin `ConverterArgs` si la receta no tiene molino y ajuste; `snapshot` (convertido, sugerido, sin ajuste, sin molino). |
| `test/.../session/GrindSelectionTest.kt` (nuevo) | Con `TestScope`: la elección sobrevive a cambios del objetivo con la misma clave y vuelve a la preselección al cambiar de receta; un predeterminado quitado de mis molinos cae al primero; `current()` coincide con lo publicado. |
| `test/.../session/ScaleSessionManagerGrindTest.kt` (nuevo) | El ajuste se congela al pasar a `Active` y es el que se guarda en `details.grind`; elegir otro molino después no cambia lo guardado; sin `SessionGrind`, como hoy; preparación sin receta con molino → molino sin ajuste (D16). |
| `androidTest/.../ui/grinders/GrindCardTest.kt` (nuevo) | **Criterio:** Básico sin `grind_card_ruler`; Experto con ella; cambiar "Moler con" muestra el ajuste convertido; sin molinos propios, `grind_card_add` y sin selector. Además: "Sin equivalencia" con su enlace; fuente si interviene un predefinido. |
| `androidTest/.../ui/prepare/PrepareScreenTest.kt`, `androidTest/.../ui/session/SessionScreenTest.kt` | La tarjeta aparece en Preparar y en el paso 2 (no en el 1, el 3 ni el 4), con la misma elección; en Experto, con la regla. |
| `androidTest/.../ui/session/GrindBrewFlowTest.kt` (nuevo) | **Criterio:** con la simulada, receta V60 con "Acme Uno · 30 clics", "Moler con" en "Acme Tres", sesión completa: la preparación guarda `grinderId` de "Acme Tres", el valor convertido y la copia, y `BrewDetailScreen` muestra "Acme Tres · …". |
| `androidTest/.../ui/TabsNavigationTest.kt` | Desde el paso 2, "Añade tu molino" abre Molinos sin barra inferior; "Atrás" vuelve a la sesión en el paso 2. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.grinder.*" --tests "dev.dotscale.session.*" --tests "dev.dotscale.settings.*"`,
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.grinders.GrindCardTest,dev.dotscale.ui.prepare.PrepareScreenTest,dev.dotscale.ui.session.SessionScreenTest,dev.dotscale.ui.session.GrindBrewFlowTest,dev.dotscale.ui.TabsNavigationTest`,
una pasada de `SessionPerformanceTest` (aviso temprano, no cuenta para D32) y `./gradlew :app:lintDebug`.

Commit sugerido: `feat(session): show the grind card with grinder conversion in Prepare and the dose step`.

### F8. Convertidor, atajos y fuente

**Objetivo:** el convertidor suelto, el atajo "Ver en mis otros molinos" desde la tarjeta y desde la receta, y la
fuente en el resultado (§3.7).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `grinder/ConverterRows.kt` (nuevo, puro) | `data class ConverterRow(val grinder: Grinder, val conversion: Conversion, val fix: GrindFix?)` y `fun converterRows(source: Grinder, method: BrewMethod?, value: Double, owned: List<Grinder>): List<ConverterRow>` (D26). |
| `NavigationKeys.kt` | `GrindConverter(sourceId, method, value)`. |
| `ui/grinders/GrinderPicker.kt` (nuevo) | `GrinderPicker(owned: List<Grinder>, catalog: List<Grinder>, selectedId: Long?, onSelect: (Long) -> Unit, onClose: () -> Unit)`. |
| `ui/grinders/GrindConverterScreen.kt`, `GrindConverterViewModel.kt` (nuevos) | F0.3; el ViewModel parte de los argumentos de la clave. |
| `ui/grinders/GrindersScreen.kt` | El icono `grinders_converter` abre `GrindConverter()`. |
| `ui/grinders/GrindCard.kt`, `ui/prepare/PrepareScreen.kt`, `ui/session/SessionScreen.kt`, `Navigation.kt` | `onOpenConverter = { stack.add(GrindConverter(it.sourceId, it.method, it.value)) }`. |
| `ui/recipes/RecipeEditScreen.kt` | El atajo `recipe_edit_grind_converter` con el molino, el método y el ajuste del formulario; no aparece sin molino o sin ajuste ni con "molino eliminado". |
| `res/values*/strings.xml` | `converter_*` y `grind_see_others`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../grinder/ConverterRowsTest.kt` (nuevo) | Una fila por cada molino mío salvo el origen; marcas de cada caso; `fix` en "Sin equivalencia"; sin molinos, lista vacía. |
| `androidTest/.../ui/grinders/GrindConverterScreenTest.kt` (nuevo) | **Criterio:** con origen, método y ajuste, una fila por cada molino mío con su equivalente. Estado vacío con el enlace; el selector de origen ofrece mis molinos y el catálogo sin retirados; cambiar el origen vacía el ajuste; la fuente aparece si interviene un predefinido (**criterio**) y no con solo propios. |
| `androidTest/.../ui/grinders/GrindShortcutTest.kt` (nuevo) | **Criterio:** desde la tarjeta de Preparar y desde el editor de la receta, el atajo abre el convertidor con el molino, el método y el ajuste de origen; no aparece si la receta no tiene molino y ajuste. Con `TestAppDependencies` y `MainNavigation`. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.grinder.*"`,
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.grinders.GrindConverterScreenTest,dev.dotscale.ui.grinders.GrindShortcutTest,dev.dotscale.ui.grinders.GrindCardTest`
y `./gradlew :app:lintDebug`.

Commit sugerido: `feat(grinder): add the standalone grind converter and its shortcuts`.

### F9. Onboarding

**Objetivo:** el paso opcional "¿Qué molino usas?" (§3.7, D6).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/onboarding/OnboardingScreen.kt` | `OnboardingScreen(catalog: List<Grinder>?, onFinish: (mode: ExperienceMode, grinderId: Long?) -> Unit, modifier)` con los dos pasos de D6; el segundo, con buscador (`fold()`), `AntSpin` mientras `catalog` es `null` y `AntEmpty` si está vacío. |
| `ui/onboarding/OnboardingViewModel.kt` (nuevo) | `OnboardingViewModel(grinders: GrinderRepository, defaults: DefaultGrinderStore, experience: ExperienceSettings)` con `catalog` y `finish(mode, grinderId)`: `setOwned(id, true)` y `setDefaultGrinderId(id)`, y después `completeOnboarding(mode)`. |
| `Navigation.kt` | `MainNavigation` usa el ViewModel. |
| `res/values*/strings.xml` | `onboarding_grinder_*`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/onboarding/OnboardingTest.kt` | Elegir modo lleva al paso 2; **criterio:** elegir un molino lo deja en mis molinos y como predeterminado y termina; **criterio:** "Omitir" termina sin molino ni predeterminado, con el modo elegido; "Saltar" del paso 1 termina en Básico sin pasar por el 2; "Atrás" del paso 2 vuelve al 1; con `onboarding_done = true` no aparece; con el catálogo vacío, el estado vacío y "Omitir". |
| `androidTest/.../ui/session/NewUserJourneyTest.kt` | Pasa por el paso 2 con "Omitir". |

**Verificación:** `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.onboarding.OnboardingTest,dev.dotscale.ui.session.NewUserJourneyTest`
y `./gradlew :app:lintDebug`.

Commit sugerido: `feat(onboarding): ask for the user's grinder after choosing the mode`.

### F10. Sembrar el catálogo aprobado

**Objetivo:** el catálogo real en la app. **Requisito:** el usuario aprobó la tabla de F2, o sus correcciones están
aplicadas en el JSON y aprobadas.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `app/src/main/assets/grinders/catalog.json` | La versión aprobada, `version: 1`. |
| `DotScaleApp.kt` | `onCreate` lanza `grinderCatalogSeeder.sync()` en `applicationScope` con `Dispatchers.IO` (D34). |
| `test/.../grinder/GrinderCatalogFileTest.kt` | Sin `assumeTrue`: el archivo es obligatorio. Comprueba además la lista exacta de claves aprobadas. |
| `androidTest/.../grinder/RealCatalogSeedTest.kt` (nuevo) | Siembra el asset real (`AssetCatalogSource`) en una base en memoria: tantos predefinidos como claves aprobadas, cada uno igual al del JSON, y una segunda llamada da `UpToDate`. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.grinder.*"`,
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.grinder.RealCatalogSeedTest`
y por adb: `./gradlew :app:installDebug`, `adb -s emulator-5556 shell pm clear dev.dotscale`, abrir, "Básico" y el
paso 2 lista los molinos aprobados; `adb -s emulator-5556 logcat -d -s GrinderCatalogSeeder` sin errores.

Commit sugerido: `feat(grinder): seed the predefined grinder catalog on startup`.

### F11. Verificación

**Objetivo:** los criterios de cierre del §5: suites, rendimiento, lint, E2E y capturas por adb (§7).

**Cierre:** marcar el checklist del §5 del spec, pasar D1–D37 al spec como §3.9, enlazar este plan desde la
cabecera del spec, anotar en la cabecera de este plan las cifras de rendimiento, el resultado de lint y de las
suites, y marcarlo como completado.

Commit sugerido: `test(grinder): verify the grinder flows end to end`.

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| `HistoryDatabase` en v5; migración 4 → 5 crea las tablas, quita `grind`, añade las tres columnas y conserva columnas, filas y FK | F3 | `HistoryMigrationTest` (`migrates4To5…`, cadena 1 → 5, contadores) |
| `BrewMethod` con los cuatro métodos, nombre visible, selectores de recetas y preparaciones y filtro del historial | F1 | `HistoryConvertersTest`, `RecipeEditScreenTest`, `BrewEditScreenTest`, `HistoryScreenTest` |
| Recetas integradas sin `grind` y sin molino | F3 | `HistoryMigrationTest.aFreshInstallIsCreatedAtVersion5…`, `RecipeDaoTest` |
| Tabla de molinos revisada y aprobada antes de sembrarla | F2, F10 | Puerta de F2 (informe y parada); F10 exige la aprobación |
| Test unitario del archivo: validador, claves únicas, `sourceUrl`, rangos en la escala, anclas monótonas | F1, F2, F10 | `GrinderCatalogFileTest` (obligatorio desde F10) |
| `GrinderCatalogSeeder`: primer arranque, versión mayor por clave sin tocar propios ni `owned`, retirados en uso, retirados sin uso | F4 | `GrinderCatalogSeederTest`; con el archivo real, `RealCatalogSeedTest` (F10) |
| Conversión lineal con el mismo porcentaje | F1 | `GrindConverterTest` |
| Extrapolación sin `AT_LIMIT` dentro de la escala | F1 | `GrindConverterTest` |
| Límite físico con `AT_LIMIT` | F1 | `GrindConverterTest` |
| Respaldo por anclas con `ANCHORS` (sin rango u `OTHER`) | F1 | `GrindConverterTest` |
| `NoEquivalent` sin rango y con menos de 2 anclas comunes | F1 | `GrindConverterTest` |
| Redondeo al paso (1 y 0,5) con `ROUNDED` solo si cambia | F1 | `GrindConverterTest` |
| Molino `HIGHER_IS_FINER` frente a uno normal | F1 | `GrindConverterTest` |
| Mismo molino: `Exact` | F1 | `GrindConverterTest` |
| Punto medio "sugerido" para una receta sin ajuste | F1, F7 | `GrindConverterTest`, `GrindCardStateTest` |
| "1 · 2 · 3" = 63 clics y vuelta; acarreo en los dos sentidos | F1 | `GrindFormatTest`, `GrindStepperTest`, `GrindInputTest` (F5) |
| `GrinderValidator` rechaza cada regla | F1 | `GrinderValidatorTest` |
| Crear un molino propio compuesto con rangos y verlo en mis molinos | F5 | `GrinderEditScreenTest` |
| Duplicar un predefinido: copia editable, original de solo lectura | F5 | `GrinderDetailScreenTest` |
| Onboarding: elegir deja predeterminado; "Omitir" termina sin molino | F9 | `OnboardingTest` |
| Tarjeta en Preparar y en el paso 2: Básico sin regla, Experto con regla, "Moler con" convierte, sin molinos el enlace | F7 | `GrindCardTest`, `PrepareScreenTest`, `SessionScreenTest` |
| La preparación guardada muestra molino y ajuste usados | F7 | `GrindBrewFlowTest`, `ScaleSessionManagerGrindTest` |
| Convertidor suelto lista el equivalente en cada uno de mis molinos | F8 | `GrindConverterScreenTest`, `ConverterRowsTest` |
| Atajo desde la tarjeta y desde el detalle de la receta abre el convertidor relleno | F8 | `GrindShortcutTest` (D8) |
| Borrar un molino usado: "Se usa en N recetas" y "molino eliminado" | F5, F6 | `GrinderDetailScreenTest`, `GrinderDeleteTest` |
| Fuente en el detalle de un predefinido y en una conversión con uno | F5, F7, F8 | `GrinderDetailScreenTest`, `GrindCardTest`, `GrindConverterScreenTest` |
| `SessionPerformanceTest` no empeora respecto a `develop` | F0, F11 | Línea base de F0 y tres pasadas en F11 (D32, §7.2) |
| Suites JVM e instrumentadas en verde salvo los fallos conocidos | F11 | §7.1 |
| Lint y formato en los archivos tocados | Todas | `lintDebug` frente a la línea base de F0 |
| Revisión manual con capturas por adb de mis molinos, el editor (dos pestañas), la tarjeta en Básico y Experto, el convertidor y el paso del onboarding | F11 | §7.3 y §7.4 |

Casos límite del §4: borrar un molino usado (`GrinderDeleteTest`, `GrinderDaoTest`), quitar un predefinido de mis
molinos (`GrinderDetailScreenTest`, `GrinderRepositoryTest`), editar unidades o escala con valores guardados
(`GrinderEditFormTest`, `BrewDetailScreenTest`, D30), destino sin rango ni anclas (`GrindConverterTest`,
`GrindCardTest`), `OTHER` o sin método (`GrindConverterTest`), fuera de escala (`GrindConverterTest`), fuera del
rango del método (`GrindConverterTest`), paso fraccionario (`GrindFormatTest`, `GrindStepperTest`), archivo inválido
(`GrinderCatalogSeederTest`), predefinido retirado (`GrinderCatalogSeederTest`, `GrinderCatalogScreenTest`), sin
molinos propios (`GrindCardTest`, `GrindConverterScreenTest`), predeterminado borrado o quitado
(`GrinderRepositoryTest`, `GrindSelectionTest`), rendimiento (D32) y privacidad (sin permisos nuevos: se comprueba
el manifiesto fusionado en F11).

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | Reconstruir `recipes` o `brews` con las claves foráneas activas borraría en cascada etapas, curvas y objetivos. | D19: la migración aborta sin tocar nada si `PRAGMA foreign_keys` vale 1 (la transacción se deshace); `HistoryMigrationTest` cuenta las filas hijas tras migrar con `MigrationTestHelper` y tras abrir con Room, y comprueba `foreign_key_check`. |
| R2 | Room rechace el esquema migrado (orden de las FK, nombres de índices, `DEFAULT`). | El SQL se copia de `5.json` generado en F3 y `runMigrationsAndValidate` lo compara. |
| R3 | La fuente no tenga algún molino o método, no dé la escala o la jerarquía, o la página cambie. | Puerta de F2 con `missing` y `notes` (URL y fecha de consulta), D37 y P3; nada se inventa. F2 va antes de la base de datos para descubrir pronto si el modelo no basta. |
| R4 | Ruido de coma flotante con pasos de 0,1 o de un tercio. | `snap` con redondeo a 6 decimales y `EPS` (D11); el Fellow Ode se modela con una unidad base entera (D37); ida y vuelta probada con pasos de 1, 0,5 y 0,1. |
| R5 | La tarjeta o `GrindSelection` empeoren el rendimiento de la sesión. | La tarjeta no se compone en `Active`; `GrindSelection` vive fuera del gestor y solo reacciona a cambios de la fuente (D23); comparación de tres pasadas (D32). |
| R6 | Dependencia circular entre `GrindSelection` y `ScaleSessionManager` en `DotScaleApp`. | El gestor recibe una lambda que lee `grindSelection` solo al congelar (D23). |
| R7 | `kotlinx-serialization-json` en `implementation` cambie la resolución del classpath de debug (el conflicto que documenta `app/build.gradle.kts` con `room-migration`). | Se conserva `debugImplementation(libs.kotlinx.serialization.core)`; F1 compila también `:app:compileDebugAndroidTestKotlin`. |
| R8 | El sembrado asíncrono al arrancar llegue tarde al paso 2 del onboarding. | La lista observa un `Flow` de Room y se rellena al terminar; `AntSpin` mientras es `null` (F9). |
| R9 | Tests antiguos que dependían del texto de la molienda queden rotos u olvidados. | Lista en el §1 y su destino en F3 y F6; `grep` de `.grind` en F3. |
| R10 | El emulador en `en-US` haga fallar comparaciones de textos. | Tags y `stringResource` del contexto de la app (§3). |
| R11 | Navegar a Molinos o al convertidor desde el paso 2 deje la barra visible o rompa D44 de SPEC-005 (retirar una `Session` restaurada sin sesión). | `showsBottomBar(stack)` (D5); D44 solo actúa si la cima es `Session`; `TabsNavigationTest` cubre ir y volver. |
| R12 | La copia `grindLabel` quede en un idioma distinto del actual. | Es una copia, como `referenceName`; solo se ve con el molino borrado (D17). |
| R13 | Un archivo de catálogo inválido en una actualización deje la app sin catálogo nuevo. | `GrinderCatalogFileTest` impide publicarlo; en ejecución, el sembrador no toca nada y registra el error (D34). |

## 6. Preguntas para el usuario

Cada una tiene una propuesta; si se aprueba el plan sin comentarios, se aplica la propuesta.

1. **P1. Preparación sin receta (D16, D25).** El §3.7 dice que se guardan "el molino elegido en 'Moler con' y el
   ajuste mostrado", pero sin receta no hay ajuste que mostrar (y tampoco con una receta sin molienda y un molino
   sin rango). *Propuesta: guardar el molino sin ajuste ("Chestnut S3") y completarlo en "Editar preparación"; los
   editores también admiten "molino sin ajuste".* Alternativa: no guardar nada de molienda en esos casos.
2. **P2. Reglas del validador (D15).** *Propuesta: añadir al §3.5 que la escala es múltiplo del paso, que un molino
   compuesto no admite escala negativa y que las anclas son estrictamente monótonas.* Sin la primera, "al límite
   del molino" podría dar un valor fuera de los múltiplos del paso, que el §4 prohíbe.
3. **P3. Escala de los predefinidos (D37).** Honest Coffee Guide no da el número total de clics; su gráfico va hasta
   el ajuste del máximo en micras (el C2, 0–30 clics para 0–950 µm), que puede no coincidir con el físico del
   fabricante. *Propuesta: usar la escala del gráfico de la fuente, coherente con las anclas del §3.6, y señalar
   en la tabla de la puerta cada diferencia con el fabricante para decidir caso por caso.*
4. **P4. Imágenes de referencia (D1, D2).** El spec cita "las imágenes de referencia" para los steppers y las dos
   pestañas del editor, pero no están en el repo. *Propuesta: aprobar los bocetos de F0.3 (stepper vertical y
   pestañas de línea).* Si las imágenes existen, indicar dónde están y F0 las sigue.
5. **P5. "Saltar" en el primer paso del onboarding (D6).** *Propuesta: termina el onboarding en Básico sin
   preguntar por el molino.* Alternativa: pasar igualmente al paso del molino.
6. **P6. Detalle de receta (D8).** No hay pantalla de detalle de receta: tocar una receta abre el editor.
   *Propuesta: el "Molino · ajuste" y el atajo "Ver en mis otros molinos" van en el editor.* Alternativa: crear una
   pantalla de detalle de receta (fuera del alcance del spec).

## 7. Verificación final

### 7.1 Suites

```
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Lint se compara con la línea base de F0: sin errores ni avisos nuevos, `MissingTranslation`, `ExtraTranslation` y
`UnusedResources` a cero en lo nuevo. Fallos que no bloquean: `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap`
y, si oscila, el caso Experto de `SessionPerformanceTest` (se anotan las cifras, §7.2). Comprobaciones con `grep`:
ningún `import android` en los archivos puros de D10, ningún resto de `recipe_edit_grind_hint`,
`brew_edit_grind_hint` ni de `grind: String`. El manifiesto fusionado no gana permisos.

### 7.2 Rendimiento (`SessionPerformanceTest`, `emulator-5556`)

- **Montaje:** el de SPEC-005 más el de F7 (catálogo de prueba, dos molinos míos y la receta larga con molino).
- **Medida y umbral:** los de SPEC-005 (`FrameMetrics.TOTAL_DURATION`, 120 s tras 5 s de calentamiento; p95 ≤ 32 ms,
  ≤ 5 % de fotogramas > 32 ms, ninguno > 250 ms).
- **Comparación (D32):** tres pasadas por caso en F0 (línea base sobre `develop`) y tres en F11. Cumple si la
  mediana del p95 de cada caso no supera la de la línea base en más de 1 ms. Se anotan fotogramas, p50, p90, p95,
  p99, máximo y porcentaje > 32 ms de cada pasada en la cabecera del plan.

### 7.3 E2E por adb (emulador, báscula simulada, sin ARTEMIS)

Antes: `adb devices -l` (si hay más de uno además de `emulator-5554`, preguntar), `adb -s emulator-5556 shell wm size`
(se anota el tamaño para convertir coordenadas), `./gradlew :app:installDebug` y `adb -s emulator-5556 shell pm clear dev.dotscale`. Se localiza con
`uiautomator dump`, se toca con `input tap` y se captura con `screencap -d <display id>` (el id sale de
`adb shell dumpsys SurfaceFlinger --display-id`). `adb logcat -b crash -d` debe quedar vacío en todo el recorrido.
El emulador está en inglés: los textos esperados son los de `values-en`.

| Paso | Recorrido | Comprobaciones |
|---|---|---|
| 1 | Abrir la app → "Basic" | Aparece "Which grinder do you use?" con el catálogo aprobado; buscar "s3" → Chestnut S3. Captura del paso del onboarding. |
| 2 | Preparar → Ajustes → Grinders | Chestnut S3 con "Default". "Add grinder" → buscar "c40" → "I have it". Captura de mis molinos. |
| 3 | Recetas → nueva receta V60 | El molino preseleccionado es el Chestnut S3; fijar 30 clics; guardar. |
| 4 | Preparar → receta → tarjeta | "Recipe: Chestnut S3 · 30 clicks"; "Grind with" en Chestnut S3; "30 clicks". Captura de la tarjeta en Básico. |
| 5 | "Grind with" → Comandante C40 | Ajuste convertido del C40 y la línea de la fuente con los dos enlaces; tocar un enlace abre el navegador (volver con `KEYCODE_BACK`). |
| 6 | Conectar la simulada → "Start" → paso 1 → "+15 g" | En el paso 2, la misma tarjeta con el C40. ⋯ → modo experto: aparece la regla. Captura de la tarjeta en Experto. |
| 7 | Terminar la preparación (seguir objetivo y "Finish") → Historial → la preparación | "Comandante C40 · <ajuste del paso 5>". |
| 8 | Ajustes → Grinders → convertidor | Origen 1Zpresso JX-Pro, V60, un ajuste compuesto: una fila por cada molino mío. Captura del convertidor. |
| 9 | Detalle del C40 → "Duplicate to customize" → editar el rango de V60 → guardar | La copia está en mis molinos y es editable; el C40 sigue sin "Edit". Capturas del editor en las dos pestañas. |
| 10 | "Add grinder" → "Create my own grinder": rotaciones, números (10) y clics (4), escala y rango de V60 → guardar; usarlo en una receta; borrarlo | "Used in 1 recipe"; tras confirmar, la receta muestra la copia con "grinder deleted". |
| 11 | Girar la pantalla en el editor de molino con cambios | Los cambios siguen (`adb shell settings put system user_rotation 1` y volver a 0). |
| 12 | `adb shell am force-stop dev.dotscale` y abrir | No vuelve el onboarding; el catálogo no se vuelve a sembrar (`logcat -s GrinderCatalogSeeder`: `UpToDate`). |

### 7.4 Capturas

Las del §5 del spec, en claro y en oscuro (cambiando "Apariencia" en Ajustes): mis molinos, el editor (Descripción y
Rangos), la tarjeta en Básico y en Experto, el convertidor y el paso del onboarding. Se guardan fuera del repo
(`adb pull` a una carpeta del usuario) y se revisan a ojo: textos que no se corten a 360 dp, la regla de grueso a
fino, contraste de las marcas y de la banda del rango.

## 8. Seguimiento fuera de este plan

- Cambios que conviene llevar al spec al cerrar (no se tocan ahora): la firma de `GrindFormat.format` con `texts`
  (D12), las reglas añadidas al validador (D15, P2), el molino sin ajuste (D16, P1), la regeneración de la copia al
  borrar (D17), la seguridad de las FK al escribir (D18), que la fuente de la tarjeta también puede ser una
  preparación (D24), que el detalle de receta es el editor (D8, P6) y que el ejemplo de jerarquía del §3.1
  (números con `perParent = 4`, clics con `perParent = 10`) es solo un molino de prueba: el JX-Pro de la fuente
  usa `rotation.number.tick` con 10 números y 4 ticks.
- El criterio "métodos nuevos en las tarjetas para compartir" no exige cambios porque las tarjetas no muestran el
  método; si se quiere que lo muestren, es un cambio aparte.
- Contrastar los valores de los predefinidos con molinos reales y ajustar las anclas a mano si la linealidad no se
  cumple (§6 del spec).
- Conversión por micras (descartada en el §7 del spec), si alguna fuente llega a dar micras por clic fiables.
- Revisar la rama con `/code-review` antes de fusionarla en `develop`.
