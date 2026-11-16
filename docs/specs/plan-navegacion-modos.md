# Plan de implementación: SPEC-005 Navegación por pestañas, sesión guiada y modos Básico/Experto

| Campo | Valor |
|---|---|
| Spec | [`spec-navegacion-modos.md`](spec-navegacion-modos.md) (SPEC-005) |
| Estado | Completado (F0 a F8) el 2026-11-14. Pendiente solo lo que exige una báscula o un teléfono reales (ver «Resultado de la implementación»). Aprobado con las propuestas de P1 a P4 (§6); commits por fase autorizados, sin push. |
| Fecha | 2026-11-14 |
| Rama | `feat/navigation-modes`, creada desde `feat/coffee-catalog` (`a3f142a`, SPEC-004 sin fusionar), con el commit del spec (`ac390ca`) |
| Módulos | `:app` (`:protocol` no cambia) |

El spec no trae fases, así que este plan las define. El orden ataca primero el riesgo mayor: sacar la conexión
y la `BrewSession` del `ScaleViewModel` a un gestor de la app (F1 y F2), con los 53 tests JVM actuales de
`ScaleViewModel*Test` como red. Después vienen los pasos con tipo y la base de datos v4 (F3 y F4), los modos
(F5), la navegación con Preparar (F6) y la sesión inmersiva (F7). F0 fija el diseño visual y los componentes
que faltan, y F8 es la verificación (rendimiento, recorrido del usuario nuevo, capturas y E2E por adb, sin
ARTEMIS). Antes de las fases están las decisiones que el spec deja abiertas (§2), incluidas las cuatro
preguntas de su §6. Varias amplían o concretan el spec (D9, D12, D13, D17, D19, D24, D27, D37 y D40);
conviene revisarlas antes de empezar F1.

## Resultado de la implementación

Todo se verificó en `emulator-5556` (AVD `SG_API34`, API 34, 1080 × 2400, 420 dpi) con la báscula simulada. No hay
báscula ni teléfono físicos. El plan pedía API 36 para el rendimiento: no hay un emulador así en este equipo.

**Pruebas.** JVM: 600 tests, 1 ignorado, 0 fallos (línea base de F0: 511). Instrumentados: 323 tests en la suite
completa antes de F8 con un solo fallo (y 335 al final de F8, con dos fallos: este y el caso Experto de rendimiento), el ya conocido `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap`
(`'Natural · Claro'` no se muestra; viene de SPEC-004 y no se tocó). `SessionScreenTest` pasa (21), `SessionLayoutTest`
(19 pasos caben sin desplazar en Básico y en Experto), `SessionTopBarTest`, `PrepareCoffeeTest`, `NewUserJourneyTest` y
`SessionPerformanceTest` (Básico pasa siempre; Experto pasó una vez y falló otra, ver «Rendimiento»).

**Lint (`lintDebug`).** 0 errores y 21 avisos, igual que la línea base de F0 (los 19 de dependencias y SDK de siempre
más 2 `UnusedResources` de `backup_rules` y `data_extraction_rules`). Se borraron las 30 cadenas que dejó sin uso
la sesión nueva.

**Rendimiento** (`SessionPerformanceTest`: `FrameMetrics.TOTAL_DURATION`, 120 s de vertido tras 5 s de calentamiento;
umbral p95 ≤ 32 ms, ≤ 5 % de fotogramas por encima de 32 ms y ninguno de más de 250 ms):

| Caso | Fotogramas | p50 | p90 | p95 | p99 | Máx. | > 32 ms |
|---|---|---|---|---|---|---|---|
| Básico (dial) | 7161 | 17,8 | 21,7 | 22,5 | 27,6 | 81,6 | 0,53 % |
| Experto, página 2 (gráfica) | 1302 | 17,5 | 19,0 | 20,9 | 34,6 | 51,9 | 2,69 % |
| Experto, página 2, segunda pasada (suite completa) | 1297 | 17,5 | 19,6 | 33,8 | 36,5 | 51,5 | 5,86 % |

La primera versión de la gráfica en vivo no cumplía (Experto: p95 de 35,4 ms y 25 % de fotogramas por encima de
32 ms). Con `FrameMetrics` por fase, los fotogramas lentos eran dibujo (10 ms) y GPU (15 ms). Se arregló en
`BrewChart`: la rejilla y las etiquetas van en una capa propia (`graphicsLayer`) que no se vuelve a grabar con cada
muestra, y el flujo se dibuja en tramos de un mismo color con un `Path` en lugar de un `drawLine` por punto.

**El caso Experto está en el límite y no es estable.** La primera pasada con el arreglo dio p95 de 20,9 ms; la segunda,
dentro de la suite completa, 33,8 ms con un 5,86 % de fotogramas por encima de 32 ms, es decir, no cumple por muy poco
ni el p95 ni el 5 %. En los fotogramas lentos el dibujo en el hilo de la interfaz es de 2 ms y la GPU, de 16: el
tiempo lo pone la GPU emulada del emulador, no el código. `SessionPerformanceTest.expertPour…` mantiene los umbrales
del plan y por eso puede fallar de forma intermitente en este emulador; no se ha relajado.

**Contraste con `dumpsys gfxinfo` (E2E, paso 12, Experto en la página 2, 110 s, compilación debug).** 1192
fotogramas, p50 17 ms, p90 34 ms, p95 34 ms, p99 40 ms; unos 365 fotogramas (30 %) entre 32 y 36 ms. No coincide con
el test instrumentado, y por encima del umbral de 32 ms. `gfxinfo` mide desde el vsync previsto y en un test de Compose
la recomposición la lleva el reloj del test, no el `Choreographer`; el emulador tiene GPU emulada. No se sabe cuál
refleja mejor un teléfono: queda pendiente medirlo en uno real (ver «Pendiente»).

**Capturas.** `SessionScreenshotCapture` (solo con `-e screenshots true`, lanzado con `am instrument`) guarda 26 PNG en
`files/session-screens` del almacenamiento externo de la app: Preparar, los tres pasos previos, el vertido y el resumen
en Básico y en Experto, y la gráfica en Experto, en claro y en oscuro. Se revisaron a ojo.

**E2E por adb (§7.3).** Paso 1 a 13 recorridos en el emulador, sin ARTEMIS, con `adb logcat -b crash` vacío:

| Paso | Resultado |
|---|---|
| 1 | Cumple: elección de modo, Preparar, tarjeta de permiso, «Dar permisos» y «Conecta tu báscula». |
| 2 | Cumple: hoja con «Buscar», «Mostrar todos» y la simulada; se cierra y la tarjeta queda «conectada». El estado «Conectando…» dura menos que una captura de `uiautomator` y no se vio. |
| 3 | Cumple en parte: la tarjeta sigue conectada al cambiar de pestaña y «Atrás» en Cafés vuelve a Preparar. No había cafés, así que no se abrió el detalle de uno; la pila y el segundo toque en la pestaña los cubre `TabsNavigationTest`. |
| 4 | Cumple: 18 g y 1:15 dan 270 g, sobreviven a `force-stop`, no vuelve a salir el primer arranque y la simulada no reconecta. |
| 5 | Cumple: sin barra inferior; timer, agua, paso actual con instrucción y siguiente a la vez; el modo experto no reinicia el timer, la gráfica sale en la página 2 y al desactivarlo vuelve a la 1; con `font_scale` 1,3 sigue todo visible. |
| 6 | Cumple: «Descartar» vuelve a Preparar y el historial no tiene esa preparación. |
| 7 | Cumple: «Bluetooth apagado» con «Activar» y el enlace a la simulada; al permitir, «Conecta tu báscula». |
| 8 | Cumple: la sesión sigue con el mismo timer y los mismos pasos al girar. En horizontal el panel de pasos queda fuera de pantalla (no es un criterio, ver «Hallazgos»). |
| 9 | Cumple: tras `am kill` desde segundo plano, Preparar sin sesión. |
| 10 | Cumple: la V60 clásica tiene 5 pasos con 2 Esperar; con un paso Remover («Swirl» en el emulador, que está en inglés) con instrucción, la lista muestra su icono y la sesión su instrucción al llegar. |
| 11 | Cumple: en Experto, el detalle muestra la gráfica, la estabilidad, el ajuste, «Comparar» y «Guardar como receta». |
| 12 | Hecho, con la discrepancia del apartado de rendimiento. |
| 13 | Hecho con 26 capturas en lugar de 12 (ver «Desviaciones de F7 y F8»). |

**Pendiente (hardware).** Todo sobre una DOT real: la trama de tara, el peso estable de la dosis (la ventana de 2 s está
calibrada sin datos reales), la reconexión a una báscula recordada y el sonido y la vibración de los avisos. El
rendimiento de la sesión en un teléfono real, sobre todo Experto con la gráfica.

**Hallazgos (no cambiados).**
- El interruptor «Modo experto» del menú ⋯ no tiene nombre accesible (`uiautomator` lo marca `NAF`): la fila no lo
  asocia a su etiqueta.
- La fila de una receta cuenta solo los vertidos (D34): la V60 clásica con 5 pasos figura con «3 pasos», y la 4:6, con 9
  pasos, con «5 pasos». Antes decía «etapas» y era correcto.
- En horizontal, la sesión no muestra el paso actual ni la lista sin desplazar.
- `SessionOptionsSheet` y el resto de la sesión no tienen tests de accesibilidad.

## Desviaciones de F0 a F8

Las de F0 a F6 las dio quien encargó F7 y F8; las de F7 y F8 son de esta ejecución.

- **F0 a F6:** `AntTabBarItem.id`; los tests del gestor con `ScaleTarget.Real`; `connectGate` y `disconnects` en
  `FakeScaleConnection`; `close()` pasó a `disconnect()` con `notifier.release()`; error «Entre 1 y 20 pasos»;
  `MergedPours` «10 vertidos»; `fromBrew` da 5 pasos; `TargetCurveCodec` solo guarda `POUR`; `ExperienceSettingsTest`;
  `AutoReconnect` con `scope` y `onForeground()`; `DeviceScanner` y `ScanViewModel`; `lastFailure`, `userDisconnected`
  y `nominalDoseG`; `TargetSelectRow` y `CoffeeSelectRow` con `modifier` y `label`; `FreeBrewDefaultsTest`,
  `TestAppDependencies` y `TestApp`.
- **F7:** `StabilitySection` pasó a `internal`. `BrewSession` gana un `beginActive` interno que comparten `startNow` y el arranque por peso. Cinco tests del gestor se adaptaron a la compuerta de sesión (`beginArmedSession`). Los tags de
  la sesión son los de `SessionScreen.kt`. `SessionLayoutTest` usa `maximumWindowMetrics` en lugar de
  `displayMetrics`, que no da los 2400 px.
- **F8:** `NewUserJourneyTest` vive en `ui/session/` y no pasa por la hoja de conexión (usa `SessionDriver.connect()`),
  no comprueba la fila del Historial ni la ausencia de la clave `Settings`. `SessionPerformanceTest` mide con
  `waitUntil` (con `Thread.sleep` la interfaz no avanza y no hay fotogramas) y una receta propia de 200 s en lugar de
  la V60 clásica de 115 s. `SessionScreenshotCapture` guarda 26 capturas en `session-screens`, no 12 en
  `screenshots`. `TestApp` respeta ahora `ThemeSettings` (antes forzaba claro). `BrewChart` se tocó por rendimiento.
  Texto de la fila de recetas: «etapa» pasó a «paso» en `recipe_stage_count`.

## 1. Punto de partida

**Se reutiliza:**
- `BrewSession` (máquina de estados pura, `onWeight`, `onTared`, `onTimer`, `onDisconnected`, `onTick`,
  `finish`, `setDoseGrams`) y sus efectos (`BrewEffect.StartTimer`, `PauseTimer`, `ResetTimer`, `Finished`).
- `TargetCurve.fromRecipe` ya dibuja una meseta en los huecos entre etapas y su función `add` sustituye el
  punto con el mismo `tMs`: con esperas explícitas los puntos salen idénticos (§2.3, D20).
- `TargetGuide.at` y `GuideState` (`stage`, `nextStage`, `countdownS`, `deviationG`, `deviationLevel`,
  `allStagesDone`), `StageAlertTracker` (ya avisa de cualquier etapa con `startMs > 0`), `brewFit` y
  `TargetConfig`.
- `ScaleViewModel`: toda su lógica (objetivo resuelto y escalado, congelación en `Active` con `frozenFor` y
  `coffeeFrozenFor`, `saveGeneration`, `save`, `resolveRecipe`, `resolveBrew`) pasa tal cual al gestor.
  Sus dependencias inyectadas (`ScaleConnection`, `BrewSaver`, `ReferenceStore`, `TargetReferences`,
  `StageNotifier`, `CoffeeStore`, `ScaleCoffees`, `clock`, `wallClock`, `TargetConfig`) se conservan.
- UI de Scale que se reutiliza en la sesión y en Preparar: `BrewChart`, la función `internal` `Chart` de
  `BrewChartCard.kt`, `levelText`, `summaryStability`, `tagColor`, `FitBlock` y `fitView`,
  `formatGuideAmount` y `formatDeviation` (`TargetGuideBlock.kt`), `formatNumber`, `formatDuration`,
  `TargetPicker` + `TargetPickerViewModel`, `CoffeePicker` + `CoffeePickerViewModel`, `KeepScreenOn`,
  `BatteryTag` y `summaryShareData` (privados en `ScaleScreen.kt`), y `StabilitySection` (privado en
  `BrewSummaryCard.kt`).
- `ScanViewModel`, `ScaleScanner` y `DevicesCard`/`DeviceRow` (privados en `ScanScreen.kt`) para la hoja de
  conexión.
- `SimulatedScaleConnection`: `addWeight`, `simulatePour(profile)`, `simulatePour(curve, mode)`, y responde a
  la tara con la trama de confirmación (`Cmd.TARE`, que el parser convierte en `ScaleMessage.Tared(true)`).
- Componentes: `AntDrawer` (hoja inferior), `AntModal`, `AntSegmented`, `AntSwitch`, `AntCard`, `AntAlert`
  (con `action`), `AntInput`, `AntProgress`, `AntStatistic`, `AntTag`, `AntTopBar(navigationIcon, actions)`,
  `AntButton`, `AntIconButton`, `AntMessageHost` + `showAntMessage`. Tema: `DotScaleTheme(darkTheme)` y
  `AntTheme`.
- Navigation 3 1.0.1: `rememberNavBackStack`, `rememberDecoratedNavEntries(backStack, entryDecorators,
  entryProvider)`, `NavDisplay(entries, ..., onBack)`, `rememberSaveableStateHolderNavEntryDecorator` y
  `rememberViewModelStoreNavEntryDecorator` (lifecycle-viewmodel-navigation3 2.10.0).
- Tests: `ScaleTestFakes.kt` (JVM: `FakeScaleConnection`, `FakeBrewSaver`, `FakeReferenceStore`,
  `FakeTargetReferences`, `FakeCoffeeStore`, `FakeScaleCoffees`, `FakeNotifier`), su copia en `androidTest`,
  `RecipeTestFixtures.kt`, `FakeRecipeTexts` (JVM y `androidTest`), `HistoryDatabase.inMemory`,
  `MigrationTestHelper` (`HistoryMigrationTest`) y la metodología de `CoffeePerformanceTest`.

**Falta o hay que corregir:**
- La conexión y la sesión viven en el `ScaleViewModel` de la entrada `Scale`; su `onCleared` termina la sesión,
  la guarda y desconecta. La navegación es una sola pila (`rememberNavBackStack(Scan)`) y todas las entradas
  sacan las dependencias con `appContext as DotScaleApp`.
- `ThemeSettings` se crea en `MainActivity` y se expone con `LocalThemeSettings`; `ThemeModeButton` está en
  los topbars de Scan y Scale, junto a los iconos de sección (`scan_open_coffees`, `scale_open_coffees`).
- **El protocolo no trae bandera de peso estable** (`ScaleMessage.Weight`: `rawWeight`, `rawFlow`, `seconds`,
  `outOfRange`) y el código solo calcula la estabilidad del **flujo** durante un vertido (`Stability.kt`). La
  frase del §3.4 del spec ("reutiliza el de la báscula, si lo envía, o el de la estabilidad de peso que ya
  calcula el código") no tiene base: el plan define el criterio (D17).
- `BrewSession` nace armada y se rearma al tarar en `Finished`: con la conexión viva en otras pestañas, un
  vertido en cualquier pantalla empezaría y guardaría una sesión. No tiene arranque manual ni descarte.
- El timer que se ve hoy es `ScaleUiState.seconds`, el contador de la báscula (sin verificar con la DOT).
- `AntModal` solo tiene dos botones. No hay barra inferior, progreso circular, indicador de páginas ni iconos
  de tipo de paso, de "más" (⋯) o de Preparar.
- `RecipeStage` no tiene tipo; `RecipeValidator.MAX_STAGES = 10` y exige agua creciente en todas las etapas;
  `RecipeFromBrew` limita con `MAX_STAGES`. Los nombres traducidos de las predefinidas se aplican por índice
  (`RecipeRepository.toRecipe` con `texts.builtInStageNames(key)`).
- `migration1To2` llama a `BuiltInRecipeSeeder.seed`: si el sembrador escribe las columnas nuevas, la migración
  1 → 2 falla (la tabla v2 no las tiene).
- `SimulatedTargetPour` toma el origen y el retraso del modo `DEVIATED` de todas las etapas.
- No hay preferencias de última báscula, dosis ni ratio por defecto, ni de modo. `ScaleScanner` solo expone
  `isBluetoothEnabled` como getter (no avisa cuando cambia).
- Los decoradores de Navigation 3 indexan el estado por `contentKey`: la misma clave (`BrewDetail(5)`) en dos
  pilas compartiría `ViewModel` y estado guardado.
- Dependen de Scan o Scale: `BrewChartTest`, `CoffeeSelectorTest`, `BrewChartCardTargetTest`,
  `ScaleTopBarTest` y `SettingsNavigationTest`.

## 2. Decisiones que completan el spec

Se aprobaron junto con el plan y, al terminar, pasaron al spec como §3.8, como en los specs anteriores.

Las decisiones D1 a D45 pasaron al §3.8 del spec ([`spec-navegacion-modos.md`](spec-navegacion-modos.md#38-decisiones-de-implementación-d1-a-d45)), que es ahora su fuente de verdad. Este plan las cita por su número.

### 2.3 Por qué la curva migrada es idéntica (D20, D35)

Para un hueco entre el `POUR` *i* (fin `e`, agua `w`) y el `POUR` *i+1* (inicio `s`), hoy `fromRecipe` añade
`(e, w)` y `(s, w)`. Con un `WAIT` explícito de `e` a `s`, la subsecuencia de `POUR` es la misma y D20 solo
genera puntos con ella, así que la lista de puntos es la misma valor a valor. El test de la migración lo
comprueba con las recetas reales leídas antes y después (F3).

## 3. Fases

Orden: F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8. F3 y F4 no dependen de F1 y F2 y se pueden adelantar si
P1 se resuelve antes. F7 necesita F2, F3, F5 y F6.

| Fase | Contenido | Tamaño |
|---|---|---|
| F0 | Diseño visual, componentes e iconos nuevos y línea base | M |
| F1 | Extraer `ScaleSessionManager` del `ScaleViewModel` sin cambiar comportamiento | M |
| F2 | Gestor a nivel de app: conexión bajo demanda, compuerta, última báscula | L |
| F3 | Pasos con tipo: dominio, curva, predefinidas y base de datos v4 | L |
| F4 | Editor de recetas con tipo e instrucción | M |
| F5 | Modo Básico/Experto, Ajustes (modo y tema) y reglas de visibilidad | M |
| F6 | Pestañas, Preparar, hoja de conexión, reconexión y primer arranque | L |
| F7 | Sesión inmersiva: pasos previos, dial, menú ⋯, salida, resumen | L |
| F8 | Verificación: rendimiento, recorrido del usuario nuevo, capturas, E2E por adb, lint y cierre | M |

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app` (nunca
`./gradlew build`). Las pruebas instrumentadas y el E2E usan el emulador con la báscula simulada; no se usa
ARTEMIS. Si hay más de un dispositivo en `adb devices -l`, se pregunta cuál usar.

### F0. Diseño visual

**Objetivo:** fijar la disposición de Preparar, la sesión, el resumen, el primer arranque y las piezas que
cambian en Ajustes, Historial y el editor, y construir los componentes que faltan. El diseño se aprueba con el
plan; F4 a F7 lo implementan tal cual.

#### F0.1 Reglas comunes

- Ancho de referencia 360 dp para listas y formularios. La sesión se diseña para 411 × 914 dp (1080 × 2400 a
  420 dpi) y se comprueba también a 360 dp de ancho.
- Textos en `values/` y `values-en/`. Prefijos nuevos: `tab_`, `prepare_`, `scale_card_`, `connect_`,
  `onboarding_`, `mode_`, `session_`, `prep_`, `step_type_`, `summary_` y `exit_`.
- Cada pantalla nueva lleva `@Preview` en claro y oscuro con datos privados del archivo.
- Accesibilidad: el dial se lee "152 g de 250 g, 61 %"; el paso actual "Paso 2 de 5, Verter, 2.º vertido";
  la barra inferior usa `Role.Tab` y `selected`.

#### F0.2 Componentes nuevos y cambios (`ui/components/`)

| Archivo | Firma o cambio |
|---|---|
| `AntTabBar.kt` (nuevo) | `data class AntTabBarItem<T>(val key: T, val label: String, val icon: ImageVector, val testTag: String)` y `AntTabBar(items: List<AntTabBarItem<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier)`. Estilo de D1. Respeta `WindowInsets.navigationBars`. |
| `AntProgressCircle.kt` (nuevo) | `AntProgressCircle(fraction: Float, modifier: Modifier = Modifier, strokeWidth: Dp = 12.dp, segments: List<Float> = emptyList(), color: Color = AntTheme.colors.primary.base, trackColor: Color = AntTheme.colors.fillSecondary, content: @Composable BoxScope.() -> Unit = {})`. `fraction` se recorta a 0..1; `segments` son fronteras en 0..1 dibujadas como huecos de 2°; animación con `motion.durationMid`. Semántica `progressBarRangeInfo`. |
| `AntPageIndicator.kt` (nuevo) | `AntPageIndicator(count: Int, selected: Int, modifier: Modifier = Modifier)`: puntos de 6 dp, el activo en `primary.base`. |
| `AntModal.kt` | Nuevo `data class AntModalChoice(val text: String, val onClick: () -> Unit, val testTag: String, val type: AntButtonType = AntButtonType.Default, val danger: Boolean = false)` y `AntChoiceModal(title: String, text: String?, choices: List<AntModalChoice>, onDismiss: () -> Unit, modifier: Modifier = Modifier)` con los botones apilados (D3). `AntModal` no cambia. |
| `AntIcons.kt` | Trazo de 24 × 24 como los actuales: `Dripper` (gotero, pestaña Preparar y tarjeta "Básico"), `Chart` (línea con ejes, tarjeta "Experto"), `More` (tres puntos horizontales), `Pour` (gota), `Swirl` (flecha circular), `Wait` (reloj de arena) y `Scale` (báscula, tarjeta de Preparar). |
| `AntComponentsPreview.kt` | Ejemplos nuevos en claro y oscuro: `AntTabBar` con la segunda pestaña activa, `AntProgressCircle` al 0, 61 y 100 % con tres segmentos, `AntPageIndicator` y `AntChoiceModal`. |

#### F0.3 Pantallas

Bocetos a 360 dp. `[x]` es un botón o icono, `(x)` un icono, `v` el desplegable de un selector.

**Barra inferior** (raíces y pantallas de lista y detalle)

```
+------------------------------------------------------+
|  (gotero)      (taza)       (receta)     (reloj)     |  AntTabBar; "tab_prepare", "tab_coffees",
|  Preparar      Cafés        Recetas      Historial   |  "tab_recipes", "tab_history"
+------------------------------------------------------+
```

Los topbars de las raíces pierden la flecha atrás (`onBack` pasa a ser `(() -> Unit)?` en `CoffeesScreen`,
`RecipesScreen` y `HistoryScreen`; con `null` no hay icono).

**Primer arranque (`OnboardingScreen`)**

```
+------------------------------------------------------+
|                                           [Saltar]   |  AntButton Link, "onboarding_skip"
|  ¿Cómo preparas tu café?                             |  AntTitle(3)
|  Puedes cambiarlo cuando quieras en Ajustes.         |  Secondary
| +--------------------------------------------------+ |
| | (gotero) Básico                                  | |  AntCard(onClick), "onboarding_basic"
| |          Te guiamos paso a paso.                 | |
| |          Solo lo esencial.                       | |
| +--------------------------------------------------+ |
| +--------------------------------------------------+ |
| | (gráfica) Experto                                | |  "onboarding_expert"
| |          Gráficas, estabilidad del flujo y       | |
| |          comparación de preparaciones.           | |
| +--------------------------------------------------+ |
+------------------------------------------------------+
```

**Preparar (`PrepareScreen`)**

```
+------------------------------------------------------+
| Preparar                                      [cfg]  |  AntTopBar, "prepare_settings"
+------------------------------------------------------+
| +--------------------------------------------------+ |
| | (bascula) DOT-A1B2 conectada     [80 %]   [...]  | |  ScaleCard, "scale_card"; BatteryTag;
| +--------------------------------------------------+ |  menú: Desconectar / Cambiar báscula
| Receta   [V60 clásica                          v]    |  "prepare_recipe" (TargetPicker)
| Café     [Etiopía Guji · día 9                 v]    |  "prepare_coffee" (CoffeePicker)
| +--------------------------------------------------+ |
| | Dosis            Ratio             Agua          | |  sin receta: dos AntInput y el agua calculada
| | [ 15,0  g ]      [1: 16,0 ]        240 g         | |  "prepare_dose", "prepare_ratio", "prepare_water"
| +--------------------------------------------------+ |  con receta: tres AntStatistic de solo lectura
|                                                      |
| [                  Empezar                       ]   |  AntButton Primary Large, "prepare_start"
|  Conecta la báscula para empezar.                    |  solo si está deshabilitado, Secondary
+------------------------------------------------------+
| barra inferior                                       |
+------------------------------------------------------+
```

Estados de la tarjeta (una fila de icono, texto y acción a la derecha o debajo si no cabe):

```
| (bt) Necesitamos permiso de Bluetooth  [Dar permisos] |  + enlace "Usar báscula simulada" (D13)
| (bt) Bluetooth apagado                 [Activar]      |  + enlace "Usar báscula simulada" (D13)
| (bascula) Conecta tu báscula           [Conectar]     |  abre la hoja
| (spin) Conectando a DOT-A1B2…                         |  debajo, el paso (ConnectionStep), Secondary
|        Negociando                                     |
| (bascula) DOT-A1B2 conectada     [80 %]       [...]   |
| (!) No se pudo conectar a DOT-A1B2                    |  AntAlert Warning dentro de la tarjeta
|     [Reintentar]  [Cambiar báscula]                   |  (o "DOT-A1B2 desconectada" + [Conectar], D12)
```

**Hoja de conexión (`ConnectionSheet`, `AntDrawer` "Conectar báscula")**

```
| Conectar báscula                                 (x) |
| [Buscar / Detener]           Mostrar todos [switch]  |  contenido de DevicesCard
| DOT-A1B2                               -58 dBm       |  DeviceRow; tocar conecta y cierra la hoja
| ---------------------------------------------------- |
| [ (play) Usar báscula simulada                     ] |  AntButton Dashed, "connect_simulated"
```

El escaneo empieza al abrir la hoja (si hay permisos y Bluetooth) y se detiene al cerrarla.

**Ajustes**

```
| Modo            [ Básico | Experto ]                 |  AntSegmented, "settings_mode"
| Apariencia      [ Sistema | Claro | Oscuro ]         |  "settings_theme" + theme_hint
| Fin automático … (como hoy)                          |
| Vibración y sonido de etapas … (como hoy)            |
| [Restaurar recetas predefinidas]                     |  solo en Experto
```

**Sesión: pasos previos**

```
+------------------------------------------------------+
|[X]               V60 clásica                   [...] |  "session_close", "session_options"
+------------------------------------------------------+
|   (1)----(2)----(3)----(4)                           |  PrepStepper: hecho (check), actual, pendiente
|                                                      |
|                     14,8 g                           |  peso en vivo, "prep_weight"
|                                                      |
|  Paso 2 de 4 · Dosis                                 |  Secondary
|  Añade el café molido (15 g)                         |  AntTitle(4), "prep_instruction"
|  [==================--------]                        |  estabilidad del peso (D17), solo en este paso
|                                                      |
|  [ Capturar ahora ]       [ Saltar (sin dosis) ]     |  "prep_capture", "prep_skip_dose"
+------------------------------------------------------+
```

Paso 1 y 3: botones "Tarar" (`prep_tare`, principal) y "Saltar" (`prep_skip`). En el paso 3 se ve "Dosis:
15,2 g" (o "Sin dosis"). Paso 4: "Empieza a verter" y "Empezar timer" (`prep_start_timer`). Con la sesión
desconectada, un `AntAlert` arriba con "Reintentar" (`session_retry`).

**Sesión: vertido (`Active`), Básico y página 1 de Experto**

```
+------------------------------------------------------+
|[X]               V60 clásica                   [...] |
+------------------------------------------------------+
|                       1:23                           |  48 sp, "session_timer"
|                .-~~~~~~~~~~~~~-.                     |
|              /    segmentos      \                   |  BrewDial (D4), "session_dial"
|             |       152,3 g       |                  |  "session_water"
|             |   1:10,2 · 4,1 g/s  |                  |  ratio "—" sin dosis
|              \                   /                   |
|                '-.___________.-'                     |
|                      ● ○                             |  solo Experto (D5)
|        Estable 82 · Desvío +3 g                      |  solo Experto, "session_metrics"
| +--------------------------------------------------+ |
| | (gota) Verter · 2.º vertido            hasta 150 g| |  "session_current_step"
| | Vierte en espiral desde el centro.               | |  instrucción, 2 líneas, elipsis
| +--------------------------------------------------+ |
|  Siguiente: Esperar en 0:12                          |  "session_next_step" (D6)
| +--------------------------------------------------+ |
| | (check)(gota)  Bloom                40 g    0:10 | |  StepList, weight(1f), scroll propio,
| | (>)    (gota)  2.º vertido         150 g    0:20 | |  "session_steps"; se desplaza solo al
| |        (reloj) —                            0:25 | |  paso actual
| |        (gota)  3.er vertido        250 g    0:25 | |
| +--------------------------------------------------+ |
|                [      Terminar      ]                |  "session_finish"
+------------------------------------------------------+
```

Alturas a 411 × 914 dp: topbar 56, timer 64, dial 240, indicador y métricas 44 (Experto), paso actual 88,
siguiente 28, botón 72, márgenes 64: 656 dp. La lista recibe el resto (al menos 160 dp, cuatro filas de 40). A
360 × 800 dp el dial baja a 200 dp por `BoxWithConstraints`.

**Sesión: página 2 (Experto)**

```
|          [ BrewChart con la curva objetivo ]         |  misma altura que el dial, "session_chart"
|                      ○ ●                             |
```

**Opciones (⋯, `AntDrawer` "Opciones")**

```
| Opciones                                         (x) |
| Modo experto                             [switch]    |  "session_expert_switch"
| Unidad                               [ g | oz ]      |  habilitado solo en Ready, como UnitCard
| [Reiniciar]                                          |  "session_restart" (P2)
| ---- Simulación ---- (solo con la simulada)          |
| [+15 g] [+100 g] [+300 g]                            |
| [Vertido estable] [Vertido irregular]                |
| [Seguir objetivo] [Desviarse]                        |  tags actuales: "sim_follow_target", ...
```

**Diálogo de la X en `Active`** (`AntChoiceModal`, "exit_dialog")

```
| ¿Terminar la preparación?                            |
| Puedes guardarla o descartarla.                      |
| [            Terminar y guardar            ]         |  "exit_save" (Primary)
| [               Descartar                  ]         |  "exit_discard" (danger)
| [            Seguir preparando             ]         |  "exit_continue"
```

**Resumen (`Finished`)**

```
+------------------------------------------------------+
|[X]               V60 clásica                   [...] |
+------------------------------------------------------+
|  (check) Preparación guardada                        |  AntAlert Success (o Saving / Failed)
| +--------------------------------------------------+ |
| | Tiempo total     Agua            Dosis           | |  AntStatistic x3
| | 2:58             251 g           15,0 g          | |
| | Ratio 1:16,7 · Café Etiopía Guji                 | |
| | Valoración ****-  · "Dulce, algo ácido"          | |  tras "Añadir detalles"
| +--------------------------------------------------+ |
|  Experto: flujo medio y máximo, StabilitySection,    |
|  FitBlock y motivo de fin                            |
|                                                      |
| [ Añadir detalles ]  [ Compartir ]                   |  "summary_details", "summary_share"
| [               Listo                ]               |  "summary_done", Primary
+------------------------------------------------------+
```

**Detalle de preparación en Básico (`BrewFactsCard`, D9)**

```
| Fecha        13 nov 2026, 8:30                       |  "brew_detail_facts"
| Receta       V60 clásica                             |
| Café         Etiopía Guji ›                          |  enlace al café
| Dosis        15,0 g   Agua 251 g   Ratio 1:16,7      |
| Tiempo total 2:58                                    |
| Valoración   ****-                                   |
| Notas        Dulce, algo ácido                       |
```

**Editor: tarjeta de paso (`StageCard`)**

```
| [ (gota) Verter | (giro) Remover | (reloj) Esperar ] |  AntSegmented, "recipe_stage_type_<i>"
| Nombre      [ 2.º vertido                     ]      |
| Inicio [0:45]   Duración [20 s]   Agua [150 g]       |  agua solo en Verter
| Instrucción [ Vierte en espiral…            ] 31/200 |  multilínea, "recipe_stage_instruction_<i>"
```

En Básico no se muestra la vista previa de la curva (`recipe_edit_preview`).

#### F0.4 Entregables de código de F0

1. Línea base: `./gradlew :app:testDebugUnitTest` y `./gradlew :app:lintDebug`; anotar errores y avisos.
2. Los componentes e iconos de F0.2 con sus previews.

**Tests:**
- `test/.../ui/components/AntProgressCircleTest.kt`: `internal fun sweepAngles(fraction, segments, gapDegrees)`
  recorta a 0..1, coloca los huecos en las fronteras y no dibuja huecos fuera del arco.
- `androidTest/.../ui/components/AntComponentsTest.kt`: `AntTabBar` llama a `onSelect` y marca `selected`;
  `AntChoiceModal` llama a cada opción y tocar fuera llama a `onDismiss`; `AntProgressCircle` expone
  `progressBarRangeInfo`.

**Verificación:** `./gradlew :app:compileDebugKotlin`,
`./gradlew :app:testDebugUnitTest --tests "dev.dotscale.ui.components.*"`,
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.components.AntComponentsTest`,
`./gradlew :app:lintDebug` y revisión de las previews en claro y oscuro.

Commit sugerido: `feat(ui): add tab bar, circular progress, page indicator and choice modal components`.

### F1. Extraer el gestor

**Objetivo:** mover la lógica de `ScaleViewModel` a `ScaleSessionManager` **sin cambiar comportamiento**, con
los tests actuales intactos como red.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `session/ScaleSessionManager.kt` (nuevo) | `class ScaleSessionManager(connection: ScaleConnection, scope: CoroutineScope, autoFinishSeconds: StateFlow<Int>, saver: BrewSaver, applicationScope: CoroutineScope, references: ReferenceStore, targets: TargetReferences, notifier: StageNotifier, coffeeStore: CoffeeStore = NoCoffeeStore, coffees: ScaleCoffees = NoScaleCoffees, clock: () -> Long = SystemClock::elapsedRealtime, wallClock: () -> Long = System::currentTimeMillis, targetConfig: TargetConfig = TargetConfig())`. Públicos: `ui: StateFlow<ScaleUiState>`, `errors: Flow<ScaleError>`, `internal val targetBuilds`, `connect()`, `tare()`, `toggleTimer()`, `resetTimer()`, `toggleUnit()`, `captureDose()`, `clearDose()`, `finishBrew()`, `selectTarget(key)`, `selectCoffee(id)`, `requestNewCoffee()`, `simulateAdd`, `simulatePour`, `simulateTargetPour` y `close()` (lo que hoy hace `onCleared`). Las corrutinas de `init` se lanzan en `scope`. `TargetBase`, `Resolution`, `resolveRecipe` y `resolveBrew` pasan como privados. |
| `ui/scale/ScaleViewModel.kt` | Mismo constructor; crea `ScaleSessionManager(connection, viewModelScope, …)` y delega cada miembro; `onCleared()` llama a `manager.close()`. |

**Tests:**
- Los 53 de `ScaleViewModelBrewTest`, `ScaleViewModelTargetTest` y `ScaleViewModelCoffeeTest`, y
  `ScaleUiStateTest`, **sin tocar**.
- `test/.../session/ScaleSessionManagerSmokeTest.kt` (nuevo): construido con `TestScope.backgroundScope` y sin
  `ViewModel`: conecta, publica un peso y `close()` termina y guarda una sesión `Active` con
  `FinishReason.MANUAL`.

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.ui.scale.*" --tests "dev.dotscale.session.*"` y
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.BrewChartTest,dev.dotscale.ui.scale.CoffeeSelectorTest`.

Commit sugerido: `refactor(scale): extract the brew session logic into ScaleSessionManager`.

### F2. Gestor a nivel de app

**Objetivo:** el gestor vive en `DotScaleApp`, crea la conexión al conectar, recuerda la última báscula real y
solo alimenta la `BrewSession` cuando está armado (D15, D16, D28). La app sigue funcionando como hoy: Scan →
Scale, y salir de Scale con "Atrás" desconecta (provisional hasta F6).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `session/ScaleTarget.kt` (nuevo) | `sealed interface ScaleTarget { val name: String; data class Real(val address: String, override val name: String) : ScaleTarget; data class Simulated(override val name: String) : ScaleTarget }` y `fun interface ScaleConnectionFactory`. |
| `session/AndroidScaleConnections.kt` (nuevo) | `class AndroidScaleConnections(context: Context) : ScaleConnectionFactory` (D15). |
| `session/ScaleMemory.kt` (nuevo) | `data class RememberedScale(val address: String, val name: String)`, `interface ScaleMemory` y `object NoScaleMemory`. |
| `settings/ConnectionSettings.kt` (nuevo) | `class ConnectionSettings(context: Context, prefsName: String = BrewSettings.PREFS_NAME) : ScaleMemory`. |
| `session/ScaleSessionManager.kt` | El constructor cambia `connection` por `connections: ScaleConnectionFactory` y añade `memory: ScaleMemory = NoScaleMemory`. Nuevos: `val target: StateFlow<ScaleTarget?>`, `fun connect(target: ScaleTarget)` (si hay otra, termina y guarda una sesión `Active` y la cierra), `fun retry()`, `fun disconnect()` (termina y guarda una sesión `Active` con `MANUAL`, como hoy al salir, y desarma), `fun arm()`, `fun disarm()` (D16) y `val isArmed`. `remember` al llegar a `Ready` con `ScaleTarget.Real`. `isSimulated` sale del destino. El tick de 1 s corre siempre. `connect()` sin argumentos y `close()` desaparecen. |
| `session/ScaleUiState.kt`, `session/ScaleCoffees.kt`, `session/TargetReferences.kt` | `git mv` desde `ui/scale/` (D14). |
| `ui/scale/ScaleViewModel.kt` | Se borra. |
| `DotScaleApp.kt` | `connectionSettings` y `scaleSession` (`MainScope()`, `AndroidScaleConnections(this)`, `memory = connectionSettings` y las dependencias de hoy). |
| `ui/scale/ScaleScreen.kt` | Recibe `session: ScaleSessionManager` en lugar del `ViewModel`; `LaunchedEffect(Unit) { session.arm() }`. |
| `Navigation.kt` | `entry<Scale>`: `LaunchedEffect(key) { app.scaleSession.connect(key.toTarget(simulatedName)) }` y un `ViewModel` de concesión (`private class ScaleLease(val session: ScaleSessionManager) : ViewModel() { override fun onCleared() = session.disconnect() }`) que desconecta al sacar la entrada de la pila (no al rotar). Provisional hasta F6. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../session/ScaleSessionManagerBrewTest.kt`, `…TargetTest.kt`, `…CoffeeTest.kt`, `ScaleUiStateTest.kt` | `git mv` de los de `ui/scale`. El montaje cambia a `ScaleSessionManager(connections = { connection }, scope = CoroutineScope(dispatcher), …)` + `connect(ScaleTarget.Simulated("Test"))` + `arm()`; `store.clear()` pasa a `manager.disconnect()`. Las aserciones no cambian. |
| `test/.../session/ScaleTestFakes.kt` | Se mueve; añade `FakeScaleMemory` y `RecordingConnectionFactory` (guarda los destinos y da una `FakeScaleConnection` por llamada). |
| `test/.../session/ScaleSessionManagerConnectionTest.kt` (nuevo) | Recuerda la real al llegar a `Ready` y no antes; la simulada nunca; conectar otra cierra la anterior y guarda la sesión `Active`; `retry` reutiliza el destino; desarmado, un vertido no crea sesión ni guarda nada; armado, sí; desconexión en `Armed` limpia sin guardar; desconexión en `Active` guarda con `DISCONNECTED`; `disconnect()` en `Active` guarda con `MANUAL`. |
| `androidTest/.../ui/scale/ScaleTestFakes.kt`, `BrewChartTest`, `CoffeeSelectorTest` | Montan el gestor con una fábrica a `SimulatedScaleConnection`, `connect(Simulated)` y pasan el gestor a `ScaleScreen`. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.session.*" --tests "dev.dotscale.ui.scale.*"`,
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.BrewChartTest,dev.dotscale.ui.scale.CoffeeSelectorTest,dev.dotscale.ui.SettingsNavigationTest`
y un humo por adb: Scan → simulada → "+15 g" → "Fijar dosis" → vertido; rotar
(`adb shell settings put system accelerometer_rotation 0`, `adb shell settings put system user_rotation 1`) y
comprobar que la sesión sigue; "Atrás" vuelve a Scan y desconecta.

Commit sugerido: `refactor(scale): move the scale connection and brew session to an app-level manager`.

### F3. Pasos con tipo: dominio y base de datos v4

**Objetivo:** el modelo con tipo e instrucción, la curva, el validador, las predefinidas y la migración
(§3.5). El editor conserva el tipo y la instrucción de cada paso aunque todavía no los muestre. **Requiere P1**
para la regla de duración de `WAIT` (D37).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `recipe/Recipe.kt` | `StageType`, campos nuevos de `RecipeStage`, `withDerivedWater()`, `totalWaterG` del último `POUR` (D34). |
| `recipe/StageGaps.kt` (nuevo) | `object StageGaps { fun fillWaits(stages: List<RecipeStage>): List<RecipeStage> }` (D36). |
| `recipe/RecipeValidator.kt` | `MAX_STAGES = 20`; `MAX_WAIT_MS` (P1); errores nuevos `RecipeError.NoPour` e `InstructionTooLong(index)` (`MAX_INSTRUCTION = 200`); `WaterOutOfRange` y `WaterNotIncreasing` solo entre `POUR` (contra el `POUR` anterior); `DurationOutOfRange` según el tipo; `StartOverlaps` para todos. |
| `recipe/RecipeEntities.kt` | `RecipeStageEntity` gana `@ColumnInfo(defaultValue = "POUR") val type: StageType = StageType.POUR` y `val instruction: String? = null`. |
| `history/HistoryConverters.kt` | `StageType` por nombre; un nombre desconocido se lee como `POUR`. |
| `history/Migrations.kt` | `val MIGRATION_3_4` (D35). `migration1To2` siembra con `SeedSchema.V2` (D38). |
| `history/HistoryDatabase.kt` | `version = 4`; `.addMigrations(migration1To2(texts), MIGRATION_2_3, MIGRATION_3_4)`. Se genera `app/schemas/dev.dotscale.history.HistoryDatabase/4.json`. |
| `recipe/BuiltInRecipeSeeder.kt` | `enum class SeedSchema { V2, CURRENT }` y el parámetro de D38. |
| `recipe/BuiltInRecipes.kt` | Definiciones de D39 (tipo, inicio, agua, duración), nombres e instrucciones por índice. |
| `recipe/RecipeTexts.kt`, `recipe/ResourcesRecipeTexts.kt` | `builtInStageNames(key): List<String?>` y `builtInStageInstructions(key): List<String?>`. |
| `res/values*/strings.xml` | `recipe_builtin_v60_classic_stages` y `recipe_builtin_four_six_stages` con los elementos vacíos de los `WAIT`; `recipe_builtin_v60_classic_instructions` y `recipe_builtin_four_six_instructions` (es y en). |
| `recipe/RecipeRepository.kt` | `toRecipe` y `toStageEntities` con tipo e instrucción (traducida mientras `localizedNames`); `withDerivedWater()` al guardar; `RecipeListItem.totalWaterG` del último `POUR` y `stageCount` de vertidos (D34). |
| `recipe/RecipeFromBrew.kt` | D40. |
| `brew/TargetCurve.kt` | `TargetStage.type`, `TargetStage.instruction`, `fromRecipe` (D20), `endMs` y `lastStepEndMs`. |
| `brew/TargetGuide.kt` | `allStagesDone = tMs >= target.lastStepEndMs`. |
| `history/TargetCurveCodec.kt` | `encode` solo con las etapas `POUR` (D21). |
| `sim/SimulatedTargetPour.kt` | D41. |
| `ui/recipes/RecipeEditForm.kt` | `StageForm` gana `type: StageType = POUR` e `instruction: String = ""`; `toRecipe` y la carga los conservan; para `SWIRL`/`WAIT` el agua no se pide ni se valida; `keepsLocalizedNames` compara también tipos e instrucciones. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/StageGapsTest.kt` (nuevo) | Sin huecos no cambia; un hueco → un `WAIT` con el agua anterior; hueco inicial desde 0 con agua 0; solape no inserta nada; 10 vertidos con 9 huecos y uno inicial → 20 pasos; nombre e instrucción `null`. |
| `test/.../recipe/RecipeValidatorTest.kt` | 20 pasos válidos y 21 no; sin `POUR` → `NoPour`; agua creciente solo entre `POUR` (un `WAIT` con el mismo acumulado es válido); instrucción de 200 y de 201; duración por tipo (P1); `StartOverlaps` con un `SWIRL`. |
| `test/.../brew/TargetCurveTest.kt` | Meseta en `SWIRL` y `WAIT`; `fromRecipe(fillWaits(r))` tiene los mismos puntos que `fromRecipe(r)` para recetas con y sin hueco inicial; `endMs` es el fin del último `POUR` con un `WAIT` final; `lastStepEndMs`; las etapas incluyen todos los tipos con `type` e `instruction`. |
| `test/.../brew/TargetGuideTest.kt` | En un hueco, la etapa actual es el `WAIT` y la siguiente el `POUR`; `countdownS` antes de un `WAIT`; `allStagesDone` con `WAIT` final. |
| `test/.../brew/StageAlertTrackerTest.kt` | Avisa al empezar un `WAIT` y un `SWIRL`; no avisa del `WAIT` inicial que empieza en 0. |
| `test/.../recipe/BuiltInRecipesTest.kt` | Las definiciones nuevas son `fillWaits` de las antiguas (copiadas en el test) y sus curvas tienen los mismos puntos; nombres e instrucciones con el tamaño de los pasos; `WAIT` sin nombre. |
| `test/.../recipe/RecipeFromBrewTest.kt` | 12 vertidos → 10 y `MergedPours(2)`; huecos rellenados con `WAIT`; 19 pasos como máximo. |
| `test/.../sim/SimulatedTargetPourTest.kt` | El agua total y el ajuste de `FAITHFUL` son iguales con y sin `WAIT` explícitos; un `WAIT` inicial no retrasa el primer vertido; `DEVIATED` retrasa desde el 2.º `POUR`. |
| `test/.../history/TargetCurveCodecTest.kt` | `encode` de una curva con `WAIT` produce los mismos bytes que la de solo `POUR`; los bytes antiguos se leen igual. |
| `test/.../ui/recipes/RecipeEditFormTest.kt` | Ida y vuelta de tipo e instrucción; un `WAIT` con el campo de agua vacío es válido; cambiar la instrucción de una predefinida quita `localizedNames`. |
| `test/.../recipe/RecipeTestFixtures.kt`, `androidTest/.../recipe/FakeRecipeTexts.kt` | `FakeRecipeTexts` con los nombres de la definición nueva (con `null` en los `WAIT`) y `builtInStageInstructions` en es y en. |
| `androidTest/.../history/HistoryMigrationTest.kt` | `migrates3To4…`: con recetas v3 sembradas por SQL (con huecos, con hueco inicial, con solape y una de 10 vertidos), los pasos quedan `POUR` sin instrucción, los huecos son `WAIT` (incluido el inicial), el solape no falla y conserva los pasos, `position` va de 0 a n−1, y la curva de cada receta leída con Room tras migrar es **idéntica punto a punto** a la calculada con las filas v3. `migrates1To2To3To4InAChain` y `aFreshInstallIsCreatedAtVersion4WithTheBuiltIns` (5 y 9 pasos). Los tests que abren Room añaden `MIGRATION_3_4`. |
| `androidTest/.../recipe/RecipeDaoTest.kt` | Tipo e instrucción se guardan y se leen; "Restaurar" deja las predefinidas con sus `WAIT`; la predefinida en inglés trae nombres e instrucciones en inglés. |

**Verificación:** `./gradlew :app:compileDebugKotlin` (genera `4.json`),
`./gradlew :app:testDebugUnitTest --tests "dev.dotscale.recipe.*" --tests "dev.dotscale.brew.*" --tests "dev.dotscale.history.*" --tests "dev.dotscale.sim.*" --tests "dev.dotscale.ui.recipes.*"` y
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.history.HistoryMigrationTest,dev.dotscale.recipe.RecipeDaoTest,dev.dotscale.ui.SettingsScreenTest,dev.dotscale.ui.recipes.RecipeEditScreenTest`.

Commit sugerido: `feat(recipe): add typed recipe steps with instructions and migrate the database to v4`.

### F4. Editor de recetas

**Objetivo:** elegir el tipo de cada paso y escribir su instrucción (§3.5, US-9).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/recipes/RecipeEditForm.kt` | `addStage()`: inicio en el fin del paso anterior, tipo `POUR`, agua del último `POUR` + 50 g y 10 s. `setStageType(index, type)`: a `SWIRL`/`WAIT` vacía el agua; a `POUR` propone el último `POUR` + 50 g. `FieldError.INSTRUCTION_TOO_LONG` y `NO_POUR` (general). `totalWaterG()` del último `POUR`. `canAddStage` con `MAX_STAGES = 20`. |
| `ui/recipes/RecipeEditScreen.kt` | `StageCard` según F0.3: `AntSegmented` con icono por tipo, el campo de agua solo en `POUR`, la instrucción multilínea con contador. Tags `recipe_stage_type_<i>`, `recipe_stage_water_<i>` y `recipe_stage_instruction_<i>`. |
| `res/values*/strings.xml` | `step_type_pour` ("Verter"), `step_type_swirl` ("Remover"), `step_type_wait` ("Esperar"), `recipe_stage_instruction`, `recipe_stage_instruction_counter`, `recipe_error_no_pour` y `recipe_error_instruction_long`. |

**Tests:**
- `test/.../ui/recipes/RecipeEditFormTest.kt`: "Añadir paso" propone el fin del anterior; cambiar a `WAIT` y
  volver a `POUR`; `NO_POUR`; instrucción de 201 caracteres; con 20 pasos no se puede añadir.
- `androidTest/.../ui/recipes/RecipeEditScreenTest.kt`: elegir "Esperar" oculta el agua; escribir una
  instrucción y guardar la conserva; la V60 clásica muestra sus 5 pasos con 2 `WAIT`; una receta de 21 pasos
  insertada por SQL muestra el error de recuento y no se guarda hasta reducirla (§4).

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.ui.recipes.*"`,
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.recipes.RecipeEditScreenTest`
y `./gradlew :app:lintDebug`.

Commit sugerido: `feat(recipes): edit step type and instruction in the recipe editor`.

### F5. Modo Básico/Experto y Ajustes

**Objetivo:** las preferencias de modo y primer arranque, el modo y el tema en Ajustes, y las reglas de §3.2 y
§3.6 en historial, detalle, recetas, selector de objetivo y ajustes. Las de la sesión y el resumen se aplican
en F7 con la misma función.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `settings/ExperienceMode.kt` (nuevo) | `enum class ExperienceMode { BASIC, EXPERT; fun shows(feature: ExpertFeature): Boolean }`; `enum class ExpertFeature { SESSION_CHART_PAGE, SESSION_STABILITY, SESSION_DEVIATION, TARGET_FROM_BREW, SUMMARY_FLOW, SUMMARY_STABILITY, SUMMARY_FIT, SUMMARY_FINISH_REASON, BREW_DETAIL_CHART, BREW_DETAIL_FIT, BREW_COMPARE, BREW_SAVE_AS_RECIPE, HISTORY_STABILITY_PROGRESS, HISTORY_ROW_METRICS, RECIPE_CURVE_PREVIEW, RESTORE_BUILT_INS }`; `val LocalExperienceMode` (D33). |
| `settings/ExperienceSettings.kt` (nuevo) | `interface ModeStore { val mode: StateFlow<ExperienceMode>; fun setMode(mode: ExperienceMode) }` y `class ExperienceSettings(context, prefsName = BrewSettings.PREFS_NAME) : ModeStore` con `onboardingDone: StateFlow<Boolean>` y `completeOnboarding(mode: ExperienceMode)`. Claves `experience_mode` y `onboarding_done`; un valor desconocido se lee como `BASIC`. |
| `settings/ThemeSettings.kt`, `BrewSettings.kt`, `TargetSettings.kt` | Parámetro `prefsName` (D32). `LocalThemeSettings` se borra. |
| `settings/ThemeModeButton.kt` | Se borra; se quita de `ScanScreen` y de `ScaleTopBar`. |
| `DotScaleApp.kt`, `MainActivity.kt` | `themeSettings` y `experienceSettings` en la app; `MainActivity` los lee de ahí y provee `LocalExperienceMode`. |
| `ui/settings/SettingsScreen.kt` | Filas "Modo" y "Apariencia" (F0.3); "Restaurar recetas predefinidas" solo con `RESTORE_BUILT_INS`. Firma: `SettingsScreen(brewSettings, targetSettings, experienceSettings: ModeStore, themeSettings: ThemeSettings, onRestoreRecipes, onBack: (() -> Unit)?)`. |
| `ui/history/BrewDetailScreen.kt` | `BrewFactsCard` (nueva) y las reglas de D9. |
| `ui/history/HistoryScreen.kt`, `HistoryRow.kt` | Tarjeta de progreso con `HISTORY_STABILITY_PROGRESS`; `HistoryRow(…, showMetrics: Boolean = true)` (D10). |
| `ui/recipes/RecipeEditScreen.kt` | `Preview` con `RECIPE_CURVE_PREVIEW`. |
| `ui/scale/TargetPicker.kt` | `showBrews: Boolean = true`; en Básico, solo la lista de recetas, sin el segmentado (`TARGET_FROM_BREW`). |
| `res/values*/strings.xml` | `mode_title`, `mode_basic`, `mode_expert`, `theme_title` (ya existe) y los rótulos de `BrewFactsCard`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../settings/ExperienceModeTest.kt` (nuevo) | Tabla: cada `ExpertFeature` se oculta en `BASIC` y se muestra en `EXPERT`. |
| `androidTest/.../ui/SettingsScreenTest.kt` | El segmentado de modo guarda `experience_mode`; el de tema guarda `theme_mode`; en Básico no aparece `settings_recipes_restore` y en Experto sí. |
| `androidTest/.../ui/history/BrewDetailScreenTest.kt` | Básico: sin `brew_chart`, `brew_detail_fit`, `brew_detail_compare` ni `brew_detail_save_recipe`; con `brew_detail_facts` y sus filas; el café abre `onOpenCoffee`. **US-10:** la misma preparación, al cambiar el modo a Experto en la misma composición, muestra la gráfica, la estabilidad y el ajuste. |
| `androidTest/.../ui/history/HistoryScreenTest.kt` | Básico: sin la tarjeta de progreso ni las etiquetas de estabilidad y ajuste en las filas. |
| `androidTest/.../ui/recipes/RecipeEditScreenTest.kt` | Básico: sin `recipe_edit_preview`. |
| `androidTest/.../ui/scale/TargetPickerTest.kt` | Con `showBrews = false`, solo recetas. |
| `androidTest/.../ui/ScaleTopBarTest.kt` | Sin `LocalThemeSettings` ni la aserción de `theme_change`; el resto igual (los iconos de sección se quitan en F6). |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.settings.*"`,
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.SettingsScreenTest,dev.dotscale.ui.history.BrewDetailScreenTest,dev.dotscale.ui.history.HistoryScreenTest,dev.dotscale.ui.recipes.RecipeEditScreenTest,dev.dotscale.ui.scale.TargetPickerTest,dev.dotscale.ui.ScaleTopBarTest`
y `./gradlew :app:lintDebug`.

Commit sugerido: `feat(settings): add basic and expert modes with visibility rules`.

### F6. Pestañas, Preparar y primer arranque

**Objetivo:** la barra inferior con su pila por pestaña, Preparar como inicio con la tarjeta de báscula, la
hoja de conexión, la reconexión automática, la configuración (receta, café, dosis y ratio) y el primer
arranque. "Empezar" abre la sesión **provisional**: `ScaleScreen` sin barra inferior, que F7 sustituye.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `NavigationKeys.kt` | Se borran `Scan` y `Scale`; nuevos `@Serializable data object Prepare : NavKey` y `@Serializable data object Session : NavKey`. |
| `Tabs.kt` (nuevo, `dev.dotscale`) | `enum class Tab(val root: NavKey, @StringRes val label: Int, val testTag: String) { PREPARE, COFFEES, RECIPES, HISTORY }` y su icono. |
| `TabNavigation.kt` (nuevo, puro) | `showsBottomBar`, `sealed interface BackAction { data object Pop; data object SelectPrepare; data object Exit }`, `backAction(tab, stackSize)` y `fun MutableList<NavKey>.popToRoot()` (D31). |
| `AppDependencies.kt` (nuevo) | D32. `DotScaleApp.dependencies` por `lazy`. |
| `Navigation.kt` | `MainNavigation(deps: AppDependencies)`: puerta del primer arranque (D11) y pestañas (D30). Las entradas usan `deps`. `entry<Prepare>`, `entry<Session>` (provisional: `ScaleScreen(deps.scaleSession, …)` con un `onBack` que termina y guarda una sesión `Active` con `finishBrew()`, desarma y hace `pop`; D44 aún no aplica porque la pantalla provisional no depende de una sesión abierta), `entry<Settings>` con los ajustes nuevos. Se borra la concesión de F2: salir de la sesión ya no desconecta. |
| `MainActivity.kt` | `MainNavigation(app.dependencies)`; `LifecycleEventEffect(ON_START)` → `deps.autoReconnect.onTrigger(…)`: el primer `ON_START` del proceso cuenta como `APP_START` y los siguientes como `APP_RESUMED`. `AutoReconnect` recoge `BluetoothEnvironment.enabled` y dispara `BLUETOOTH_ON` en el paso de apagado a encendido. |
| `ble/BlePermissions.kt` (nuevo) | `BLUETOOTH_PERMISSIONS` y `Context.hasBluetoothPermissions()`, movidos de `ScanScreen.kt`. |
| `ble/BluetoothEnvironment.kt` (nuevo) | `interface BluetoothEnvironment { fun permissionsGranted(): Boolean; val enabled: StateFlow<Boolean> }` y `AndroidBluetoothEnvironment(context, scope)` (receptor de `ACTION_STATE_CHANGED`). |
| `session/AutoReconnect.kt` (nuevo) | `enum class ReconnectTrigger { APP_START, APP_RESUMED, BLUETOOTH_ON }`, `class AutoReconnect(manager, memory, bluetooth)` con `onTrigger(trigger)` y la política pura `fun shouldReconnect(…): Boolean` (D29). El gestor gana `val userDisconnected` y `val lastFailure`. |
| `session/BrewPlan.kt` (nuevo) | D26. |
| `settings/BrewSettings.kt` | `interface FreeBrewDefaults` (D27), claves `default_dose_g` y `default_ratio` (`Float`). |
| `ui/prepare/ScaleCardState.kt` (nuevo, puro) | `sealed interface ScaleCardState { NoPermission; BluetoothOff; NoScale; Connecting(name, step); Connected(name, batteryPercent, simulated); Failed(name); Disconnected(name) }` y `fun scaleCardState(permissions: Boolean, bluetoothOn: Boolean, remembered: RememberedScale?, target: ScaleTarget?, connection: ConnectionState, lastFailure: Boolean): ScaleCardState`. |
| `ui/prepare/PrepareViewModel.kt` (nuevo) | Combina `manager.ui`, `memory.lastScale`, `FreeBrewDefaults`, `BluetoothEnvironment` y el modo en `PrepareUiState` (tarjeta, objetivo, café, dosis, ratio, agua, `canStart`). Acciones: conectar, reintentar, desconectar, elegir receta y café (delegan en el gestor), `setDose`, `setRatio`. |
| `ui/prepare/PrepareScreen.kt`, `ScaleCard.kt`, `ConnectionSheet.kt` (nuevos) | F0.3. Los errores del gestor (`TargetRemoved`, `SaveFailed`, `Command`) se muestran con `AntMessageHost`, como hoy en Scale. `ConnectionSheet` contiene `DevicesCard`/`DeviceRow` movidos de `ScanScreen.kt`. "Dar permisos" con `RequestMultiplePermissions`; "Activar" con `BluetoothAdapter.ACTION_REQUEST_ENABLE`. |
| `ui/scale/BrewSetupRows.kt` (nuevo) | `TargetSelectRow` y `CoffeeSelectRow`, extraídos de `BrewChartCard.kt` (conservan `target_chip` y `coffee_trigger`); los usan Preparar y, hasta F7, `BrewChartCard`. |
| `ui/onboarding/OnboardingScreen.kt` (nuevo) | F0.3; elegir o "Saltar" llama a `completeOnboarding`. |
| `ui/scan/ScanScreen.kt` | Se borra. |
| `ui/scale/ScaleScreen.kt` | `ScaleTopBar` pierde `onOpenSettings`, `onOpenHistory` y `onOpenCoffees` y sus iconos (`scale_open_coffees`). |
| `ui/coffees/CoffeesScreen.kt`, `ui/recipes/RecipesScreen.kt`, `ui/history/HistoryScreen.kt` | `onBack: (() -> Unit)?`; en la raíz de la pestaña, `null`. |
| `res/values*/strings.xml` | `tab_*`, `prepare_*`, `scale_card_*`, `connect_*` y `onboarding_*`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../TabNavigationTest.kt` (nuevo) | `showsBottomBar` de cada clave; `backAction`: pila > 1 → `Pop`, raíz de Cafés/Recetas/Historial → `SelectPrepare`, raíz de Preparar → `Exit`; `popToRoot`. |
| `test/.../ui/prepare/ScaleCardStateTest.kt` (nuevo) | Cada fila de la tabla de §3.3 y las variantes de D12; la simulada conectada. |
| `test/.../session/BrewPlanTest.kt` (nuevo) | 15 g × 16 = 240 g; con dosis capturada; con receta, el agua escalada; saltada la dosis sin receta, la de Preparar. |
| `test/.../session/AutoReconnectTest.kt` (nuevo) | Con real recordada y permisos, `APP_START` conecta; sin permisos o con Bluetooth apagado, no, y `BLUETOOTH_ON` sí; tras un fallo no reintenta hasta el siguiente disparador; sin recordada (o solo simulada usada) no conecta; tras "Desconectar", `APP_RESUMED` no reconecta; ya conectado o conectando, nada. |
| `androidTest/.../ui/TestAppDependencies.kt` (nuevo) | `AppDependencies` con Room en memoria, preferencias `"settings_test"` (borradas en `@Before` y `@After`), fábrica a `SimulatedScaleConnection` y `BluetoothEnvironment` falso. |
| `androidTest/.../ui/TabsNavigationTest.kt` (nuevo) | Cuatro pestañas con icono y texto; abrir un café, ir a Historial y volver conserva el detalle; tocar la pestaña activa vuelve a la raíz; atrás en la raíz de Cafés selecciona Preparar; atrás en Preparar raíz termina la actividad (`createAndroidComposeRule` + `activity.isFinishing`); el café de una preparación se abre en la pila de Historial; la barra se oculta en `CoffeeEdit`, `RecipeEdit` y `BrewEdit`; con la simulada conectada, recorrer las cuatro pestañas no desconecta. |
| `androidTest/.../ui/onboarding/OnboardingTest.kt` (nuevo) | Primera vez se muestra; "Básico" y "Experto" guardan el modo y abren Preparar; "Saltar" deja `BASIC`; recompuesto con `onboarding_done = true` no aparece. |
| `androidTest/.../ui/prepare/PrepareScreenTest.kt` (nuevo) | Cada estado de la tarjeta con su acción; la hoja muestra "Buscar", "Mostrar todos" y `connect_simulated`, y elegir la simulada cierra la hoja y conecta; "Empezar" deshabilitado hasta `Ready` con su explicación; dosis 18 y ratio 15 → 270 g, y siguen tras recrear; con receta, valores de solo lectura; filas de objetivo y café (casos de `BrewChartCardTargetTest` que pasan aquí); receta borrada → "Sin receta" y el aviso `TargetRemoved`. |
| `androidTest/.../ui/SettingsNavigationTest.kt` | Pasa a `gearOpensSettingsFromPrepare` y `noTopBarHasSectionIcons` (en las cuatro raíces no hay nodos con `coffees_open`, `recipe_open`, `history_open` ni `theme_change`). Una regla escribe `onboarding_done = true` antes de lanzar la actividad. |
| `androidTest/.../ui/ScaleTopBarTest.kt` | Sin iconos de sección: título largo recortado, flecha y batería visibles. |

**Verificación:** `./gradlew :app:testDebugUnitTest`,
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.TabsNavigationTest,dev.dotscale.ui.onboarding.OnboardingTest,dev.dotscale.ui.prepare.PrepareScreenTest,dev.dotscale.ui.SettingsNavigationTest,dev.dotscale.ui.ScaleTopBarTest,dev.dotscale.ui.scale.CoffeeSelectorTest`,
`./gradlew :app:lintDebug` y humo por adb: `pm clear`, primer arranque, Preparar, hoja, simulada, "Empezar"
(sesión provisional sin barra), atrás, pestañas.

Commit sugerido: `feat(navigation): add bottom tabs, the Prepare screen and first-run mode choice`.

### F7. Sesión inmersiva

**Objetivo:** la sesión de §3.4: pasos previos con `PrepFlow`, vertido con dial, paso actual, siguiente y
lista, página de la gráfica en Experto, menú ⋯, salida con diálogo, desconexión y resumen. Se borra la pantalla
Scale.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `session/PrepFlow.kt` (nuevo) | `enum class PrepStep { TARE, DOSE, TARE_FOR_POUR, POUR }`; `data class PrepState(val step: PrepStep, val doseG: Double?, val doseSkipped: Boolean, val stableProgress: Float, val waitingTare: Boolean, val interrupted: Boolean)`; `sealed interface PrepEffect`; `data class PrepConfig(stableWindowMs = 2_000, stableToleranceG = 0.3, stableMinSamples = 3, minDoseG = 1.0, autoCaptureMinShare = 0.5, recaptureMaxShare = 1.5, recaptureDeltaG = 0.3, zeroToleranceG = 0.3, tareSettleMs = 1_000, tareTimeoutMs = 5_000)`; `class PrepFlow(objectiveDoseG: Double, config: PrepConfig = PrepConfig())` con `state`, `start(knownDoseG: Double?)`, `onWeight(grams: Double, valid: Boolean, nowMs: Long)`, `onTared(nowMs)`, `tare(nowMs)`, `skip()`, `captureNow(grams)`, `skipDose()`, `startTimer()`, `onDisconnected()` y `onReconnected()`, cada uno devolviendo `List<PrepEffect>` (D17, D19, D24, D42). |
| `brew/BrewSession.kt` | `reset()`, `discard(nowMs)` y `startNow(nowMs)` (D23). |
| `session/ScaleSessionManager.kt` | `startSession(plan: BrewPlan)`, `endSession()` ("Listo" o X antes de `Active`), `discardSession()` (D22), `restartPrep()` (D43), acciones de `PrepFlow` (`prepTare`, `prepSkip`, `prepCaptureNow`, `prepSkipDose`, `prepStartTimer`), `captureDose(tare: Boolean = true)` (D18). La compuerta se cierra al pasar a `Finished`. `ScaleUiState` gana `prep: PrepState? = null`, `elapsedMs: Long? = null`, `plan: BrewPlan? = null` y `targetWaterG: Double? = null`, y `val hasSession: Boolean`. |
| `ui/session/SessionScreen.kt` (nuevo) | Topbar (X, título, ⋯); contenido por estado: `PrepPanel`, `ActivePanel` y `SummaryPanel`; `DisconnectedNotice`; `BackHandler` igual que la X; `KeepScreenOn` (movido). Firma: `SessionScreen(session: ScaleSessionManager, modeStore: ModeStore, brews: BrewRepository, onAddDetails: (Long) -> Unit, onDone: () -> Unit)`, con las reglas de `ExperienceMode.shows` y el modo leído de `LocalExperienceMode`. |
| `ui/session/BrewDial.kt` (nuevo) | `BrewDial(waterG: Double, targetWaterG: Double?, segments: List<Float>, ratio: Double?, flowGps: Double, unit: WeightUnit, modifier)` y `internal fun dialSegments(curve: TargetCurve): List<Float>` (D4). |
| `ui/session/StepList.kt` (nuevo) | `enum class StepState { DONE, CURRENT, PENDING }`, `internal fun stepStates(stageCount: Int, currentIndex: Int?, finished: Boolean): List<StepState>` y `StepList(stages, states, unit)` con `LazyColumn` y desplazamiento al paso actual. |
| `ui/session/SessionOptionsSheet.kt` (nuevo) | D2; la sección de simulación sale de `SimulationCard`. |
| `ui/session/SessionSummary.kt` (nuevo) | D8. Observa la preparación guardada (`BrewRepository.observe(id)`) para la valoración y las notas. Usa `StabilitySection` (pasa a `internal`) y `FitBlock`. |
| `ui/session/SessionFormat.kt` (nuevo, puro salvo los `@StringRes`) | `nextStepText` (D6), `formatClock(ms)` (D25), `stepTypeLabel(type)` y `stepTypeIcon(type)`. |
| `ui/session/SessionShare.kt` (nuevo) | `summaryShareData`, movido de `ScaleScreen.kt`, para "Compartir" del resumen. |
| `Navigation.kt` | `entry<Session>` → `SessionScreen`; "Empezar" de Preparar → `session.startSession(plan)` y `push(Session)`; "Añadir detalles" → `push(BrewEdit(id))` en la pila de Preparar; "Listo" y la X fuera de `Active` → `popToRoot()` de Preparar; `Session` restaurada sin sesión se retira sola (D44). |
| `ui/scale/ScaleScreen.kt`, `BrewChartCard` (composable), `TargetGuideBlock` (composable) | Se borran con lo que quede sin uso (`WeightCard`, `BrewControls`, `DoseCard`, `UnitCard`, `ConnectionBanner`, `SimulationCard`, `ScaleTopBar`). Se conservan `Chart`, `BrewChart` y los formateadores. |
| `res/values*/strings.xml` | `session_*`, `prep_*`, `summary_*` y `exit_*`; se borran las que queden sin uso (`scale_*` de la pantalla borrada), comprobado con lint `UnusedResources`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../session/PrepFlowTest.kt` (nuevo) | Tara por trama, por peso tras "Tarar" y por botón físico; sin transición no confirma; "Saltar" en 1 y 3; dosis estable a los 2 s y no antes; la primera cucharada (< 50 %) no se captura; ruido de ±0,05 g no rompe la estabilidad; recaptura en el paso 3 antes de tarar y no con agua vertida (> 150 %); "Capturar ahora" y "Saltar (sin dosis)"; con dosis conocida el paso 2 se da por hecho; llegar a `POUR` emite `Arm`; "Empezar timer" emite `StartNow`; desconexión y reconexión vuelven a `TARE` con la dosis. |
| `test/.../brew/BrewSessionTest.kt` | `discard` en `Active` no emite `Finished`, emite `PauseTimer` si corría y deja `Armed`; tras `discard`, un vertido empieza otra sesión; `startNow` pasa a `Active` con `t0` y `StartTimer` (o `ResetTimer` + `StartTimer` desde `PAUSED`); `reset` desde `Finished`. |
| `test/.../session/ScaleSessionManagerSessionTest.kt` (nuevo) | `startSession` crea `prep` en `TARE`; un vertido antes de `POUR` no crea sesión; `Arm` arma y el vertido la crea; en `Finished`, una tara no rearma y otro vertido no crea sesión; `discardSession` no llama al `BrewSaver` y limpia dosis, objetivo y café congelados; `endSession` limpia la dosis; salir antes de `Active` la conserva y el siguiente `startSession` salta el paso 2; desconexión en los pasos previos no guarda; en `Active` guarda con `DISCONNECTED`; `elapsedMs` sigue al reloj; `restartPrep` según D43; el gestor no depende del modo. |
| `test/.../ui/session/SessionFormatTest.kt`, `StepStatesTest.kt`, `BrewDialTest.kt` (nuevos) | Texto de "Siguiente" con y sin nombre, fuera y dentro de los 5 s; `m:ss`; estados de la lista; segmentos de la V60 clásica (40/250 y 150/250) y saturación al 100 %. |
| `androidTest/.../ui/session/SessionScreenTest.kt` (nuevo) | Con `TestAppDependencies` y la simulada: la sesión no muestra la barra inferior; los pasos previos avanzan solos ("Tarar", "+15 g" en la hoja de simulación, captura, "Tarar") y cada uno se puede saltar; **Básico** completo: sin `session_chart` ni `session_metrics`, resumen sin `StabilitySection`; **Experto** completo: deslizar a la página 2 muestra `session_chart` con el objetivo, y `session_metrics`; resumen con estabilidad y ajuste; el switch del ⋯ cambia el modo al instante, el timer sigue y la página vuelve a 1; X antes de `Active` sale sin diálogo; X en `Active` abre `exit_dialog`; "Descartar" no deja la preparación en el historial; "Terminar y guardar" muestra el resumen; "Atrás" del sistema igual que la X; desconexión: aviso con "Reintentar"; "Listo" llama a `onDone`; el paso actual muestra la instrucción y la lista el icono de cada tipo; `FakeNotifier` recibe un aviso por cada paso de la V60 (incluidos los `WAIT`). |
| `androidTest/.../ui/session/SessionLayoutTest.kt` (nuevo) | En 1080 × 2400 (comprueba `displayMetrics` y si no coinciden se salta con `assumeTrue`), con una receta de 19 pasos en Básico y en Experto: `session_timer`, `session_water`, `session_current_step` y `session_next_step` están dentro de la ventana y visibles sin desplazar; la lista se desplaza sola al paso actual. |
| `androidTest/.../ui/session/SessionTopBarTest.kt` (nuevo, sustituye a `ScaleTopBarTest`) | A 360 dp, un nombre de receta largo se recorta y la X y el ⋯ siguen visibles y pulsables. |
| `androidTest/.../ui/prepare/PrepareCoffeeTest.kt` (nuevo, sustituye a `CoffeeSelectorTest`) | Elegir un café en Preparar, hacer la sesión guiada y la preparación queda vinculada con el stock descontado; "Nuevo café" desde el selector abre el editor en la pila de Preparar. |
| Se borran | `BrewChartTest` (sus casos pasan a `SessionScreenTest`), `BrewChartCardTargetTest` (a `PrepareScreenTest` en F6), `CoffeeSelectorTest` y `ScaleTopBarTest`. |

**Verificación:** `./gradlew :app:testDebugUnitTest`,
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.ui.session.SessionScreenTest,dev.dotscale.ui.session.SessionLayoutTest,dev.dotscale.ui.session.SessionTopBarTest,dev.dotscale.ui.prepare.PrepareCoffeeTest,dev.dotscale.ui.TabsNavigationTest`
y `./gradlew :app:lintDebug` (sin `UnusedResources` nuevos ni `MissingTranslation`).

Commit sugerido: `feat(session): add the immersive guided brew session`.

### F8. Verificación

**Objetivo:** los criterios de rendimiento, recorrido, capturas, E2E y calidad del §5 del spec, con los pasos
de §7.

**Archivos:**
- `androidTest/.../ui/session/SessionPerformanceTest.kt` (nuevo), descrito en §7.2.
- `androidTest/.../NewUserJourneyTest.kt` (nuevo): con `TestAppDependencies` y `onboarding_done = false`:
  "Básico" → Preparar → tarjeta → hoja → "Usar báscula simulada" → elegir la V60 clásica → "Empezar" →
  "Tarar" → "+15 g" (hoja de simulación) → captura automática → "Tarar" → "Seguir objetivo" → "Terminar" →
  resumen → "Listo" → pestaña Historial: una fila con la V60 clásica. Comprueba que la clave `Settings` nunca
  entró en ninguna pila.
- `androidTest/.../ui/session/SessionScreenshotCapture.kt` (nuevo, solo con `-e screenshots true`): recorre
  con la simulada Preparar (conectada), la sesión en Básico (`Active` a mitad del 2.º vertido), la sesión en
  Experto (página 1 y página 2) y el resumen en Experto y en Básico, en claro y en oscuro, y guarda cada
  `onRoot().captureToImage()` como PNG en `getExternalFilesDir("screenshots")`.

**Cierre:** marcar el checklist del §5 del spec, pasar D1–D45 al spec como §3.8, enlazar este plan desde la
cabecera del spec, anotar en la cabecera de este plan las medidas de rendimiento y el resultado de lint, y
marcarlo como completado.

**Resultado (F8):** se completa al ejecutar la fase (medidas, capturas revisadas y pasos E2E).

Commit sugerido: `test(session): add session performance test and verify the navigation flow`.

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| Arranca en Preparar (o en la elección de modo), nunca en la lista BLE | F6 | `OnboardingTest`, `TabsNavigationTest`, `NewUserJourneyTest`, E2E 1 |
| Barra inferior con Preparar, Cafés, Recetas e Historial, con icono y texto | F0, F6 | `AntComponentsTest`, `TabsNavigationTest` |
| Pila por pestaña; tocar la activa vuelve a la raíz | F6 | `TabNavigationTest`, `TabsNavigationTest`, E2E 3 |
| "Atrás" en las raíces selecciona Preparar; desde Preparar sale | F6 | `TabNavigationTest`, `TabsNavigationTest` |
| Ningún topbar con iconos de sección; el tema en Ajustes | F5, F6 | `SettingsNavigationTest.noTopBarHasSectionIcons`, `SettingsScreenTest` |
| Elección de modo del primer arranque; "Saltar" deja Básico; no se repite | F5, F6 | `OnboardingTest`, E2E 1 |
| Modo desde Ajustes y desde el ⋯, al instante y sin reiniciar la sesión | F5, F7 | `SettingsScreenTest`, `SessionScreenTest`, `ScaleSessionManagerSessionTest` (el gestor no depende del modo), E2E 5 |
| Reglas de §3.2 en sesión, resumen, historial, detalle, recetas y ajustes, en los dos modos | F5, F7 | `ExperienceModeTest`, `BrewDetailScreenTest`, `HistoryScreenTest`, `RecipeEditScreenTest`, `TargetPickerTest`, `SettingsScreenTest`, `SessionScreenTest` |
| Una preparación hecha en Básico muestra gráfica, estabilidad y ajuste en Experto | F5 | `BrewDetailScreenTest` (US-10), E2E 11 |
| Reconexión a la real recordada; la simulada no se recuerda | F2, F6 | `ScaleSessionManagerConnectionTest`, `AutoReconnectTest`, E2E 4 (simulada) |
| La tarjeta muestra cada estado de §3.3 con su acción | F6 | `ScaleCardStateTest`, `PrepareScreenTest`, E2E 1, 2 y 7 |
| La hoja ofrece escaneo, "Mostrar todos" y "Usar báscula simulada" | F6 | `PrepareScreenTest`, E2E 2 |
| Cambiar de pestaña no desconecta | F2, F6 | `TabsNavigationTest`, E2E 3 |
| Sin receta, agua = dosis × ratio, 15 g y 1:16 por defecto, persistidos | F6 | `BrewPlanTest`, `PrepareScreenTest`, E2E 4 |
| La sesión oculta la barra inferior | F6, F7 | `TabNavigationTest`, `SessionScreenTest` |
| Pasos previos automáticos (tara, dosis estable) y cada uno se puede saltar | F7 | `PrepFlowTest`, `SessionScreenTest`, E2E 5 |
| Timer, agua, paso actual y siguiente a la vez sin scroll en 1080 × 2400, en los dos modos | F0 (D4), F7 | `SessionLayoutTest`, capturas de F8, E2E 5 |
| Experto: página 2 con la gráfica en vivo y la curva objetivo | F7 | `SessionScreenTest`, capturas de F8 |
| X en `Active` con tres opciones; "Descartar" no guarda; antes de `Active` sale sin preguntar | F7 | `BrewSessionTest`, `ScaleSessionManagerSessionTest`, `SessionScreenTest`, E2E 6 |
| Avisos de etapa en cada paso de cualquier tipo | F3, F7 | `StageAlertTrackerTest`, `SessionScreenTest` (`FakeNotifier`) |
| Editor con tipo e instrucción; agua solo en Verter | F3, F4 | `RecipeEditFormTest`, `RecipeEditScreenTest`, E2E 10 |
| La sesión muestra la instrucción del paso actual y el tipo con icono en la lista | F7 | `SessionScreenTest`, E2E 10 |
| Predefinidas con pasos Esperar e instrucciones en es y en | F3 | `BuiltInRecipesTest`, `RecipeDaoTest`, `HistoryMigrationTest` |
| JVM: reglas de visibilidad | F5 | `ExperienceModeTest` |
| JVM: `PrepFlow` (avances, "Saltar", desconexión) | F7 | `PrepFlowTest` |
| JVM: agua objetivo desde dosis y ratio | F6 | `BrewPlanTest` |
| JVM: `TargetCurve.fromRecipe` con `SWIRL` y `WAIT` | F3 | `TargetCurveTest` |
| JVM: `RecipeValidator` con tipos y el nuevo límite | F3 | `RecipeValidatorTest` |
| JVM: gestor de conexión (reconexión, no recordar la simulada) | F2, F6 | `ScaleSessionManagerConnectionTest`, `AutoReconnectTest` |
| Migración v3 → v4: `POUR`, huecos a `WAIT` (incluido el inicial), solapes sin fallo, curva idéntica punto a punto | F3 | `HistoryMigrationTest` |
| Instrumentados: pestañas y pilas | F6 | `TabsNavigationTest` |
| Instrumentados: primer arranque | F6 | `OnboardingTest` |
| Instrumentados: sesión completa en Básico y en Experto | F7 | `SessionScreenTest` |
| Instrumentados: interruptor del ⋯ | F7 | `SessionScreenTest` |
| Instrumentados: diálogo de la X | F7 | `SessionScreenTest` |
| Instrumentados: detalle del historial sin gráfica en Básico | F5 | `BrewDetailScreenTest` |
| Recorrido del usuario nuevo sin abrir Ajustes | F8 | `NewUserJourneyTest` |
| Rendimiento de la sesión de 2 min con `FrameMetrics`, umbral fijado y resultado anotado | F8 | `SessionPerformanceTest` (§7.2), cabecera del plan |
| Tests que dependen de iconos del topbar, `Scan` y `Scale` adaptados (`ScaleTopBarTest`, `SettingsNavigationTest`, `scan_open_coffees`, `scale_open_coffees`) | F6, F7 | `SettingsNavigationTest`, `SessionTopBarTest`, `PrepareCoffeeTest`, `SessionScreenTest`; `grep` sin restos de los tags |
| Capturas claro y oscuro de Preparar, sesión Básico, sesión Experto y resumen, con tests y adb, sin ARTEMIS | F8 | `SessionScreenshotCapture` + `adb pull` (§7.3) |
| Cadenas nuevas en `values` y `values-en` | F0–F7 | lint `MissingTranslation`, comprobación de §7.1 |
| `:app` compila y pasa lint sin avisos nuevos | Todas | `lintDebug` frente a la línea base de F0 |

Casos límite del §4 del spec: permisos denegados y Bluetooth apagado (`ScaleCardStateTest`, `AutoReconnectTest`,
E2E 1 y 7), reconexión fallida sin bucles (`AutoReconnectTest`), última simulada (`AutoReconnectTest`, E2E 4),
"Empezar" sin `Ready` (`PrepareScreenTest`), desconexión en los pasos previos y en `Active`
(`ScaleSessionManagerSessionTest`, `SessionScreenTest`), dosis que no se estabiliza (`PrepFlowTest`), receta
con otra dosis (`ScaleSessionManagerTargetTest`, sin cambios), cambio de modo en plena sesión
(`SessionScreenTest`), cambio de pestaña (`TabsNavigationTest`), "Atrás" en la sesión (`SessionScreenTest`),
proceso muerto (D44, E2E 9), receta borrada (`PrepareScreenTest`), migración con solapes y receta de más de
20 pasos (`HistoryMigrationTest`, `RecipeEditScreenTest`) y rotación (E2E 8).

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | La extracción del gestor cambie algo sutil (generación del guardado, objetivo congelado, orden de efectos del temporizador). | F1 es un movimiento sin cambios con los 53 tests intactos; F2 mueve los tests con `git mv` y solo cambia su montaje. |
| R2 | Las pilas múltiples de Navigation 3 (claves repetidas entre pilas, cuándo limpia el decorador de `ViewModel`, estado restaurado) se comporten distinto de lo previsto. | `contentKey` con prefijo de pestaña (D30) y `TabsNavigationTest` (estado conservado al cambiar, limpieza al sacar de la pila, restauración tras recrear la actividad). Alternativa: un `NavDisplay` por pestaña dentro de `key(tab)`, sin animación entre pestañas. |
| R3 | Room rechace el esquema migrado (valor por defecto de `type`) o la renumeración choque con la clave primaria. | `@ColumnInfo(defaultValue = "POUR")` y `runMigrationsAndValidate`; borrar e insertar por receta (D35). |
| R4 | La cadena 1 → 2 → 3 → 4 se rompa por el sembrador nuevo. | `SeedSchema.V2` (D38) y `migrates1To2To3To4InAChain`. |
| R5 | Las suposiciones BLE sin verificar (trama de tara, cadencia del peso, contador de la báscula) fallen con la DOT física. | Ningún flujo depende solo de ellas: tara por peso y por botón (D19), estabilidad calculada en la app (D17), timer con el reloj de la sesión (D25) y botones manuales en cada paso. Las constantes están en `PrepConfig` para calibrarlas (§8). |
| R6 | La dosis se capture antes de tiempo (varias cucharadas) o se tome agua como café. | Umbral del 50 %, ventana de 2 s y recaptura acotada al 150 % (D17), probados en `PrepFlowTest`. |
| R7 | Con la conexión viva fuera de la sesión, un vertido en otra pantalla cree una sesión fantasma. | Compuerta (D16), probada en `ScaleSessionManagerConnectionTest` y `ScaleSessionManagerSessionTest`. |
| R8 | En Experto o con fuente grande, el bloque fijo no quepa en 1080 × 2400. | Dial adaptable (D4), instrucción a 2 líneas, lista con `weight(1f)` y `SessionLayoutTest` en los dos modos. Con escala de fuente 1,3 se revisa a mano en E2E 5; si no cabe, el dial baja a su mínimo de 180 dp. |
| R9 | Recomponer la sesión a 10 Hz con la gráfica cause tirones. | El pager no compone la gráfica en la página 1 (D5); lecturas derivadas con `derivedStateOf`; `SessionPerformanceTest` en los dos casos. |
| R10 | El reloj de pruebas de Compose altere la medida de frames. | La medida usa `FrameMetrics` de la ventana real; se contrasta con `adb shell dumpsys gfxinfo dev.dotscale` en E2E 12. Si difieren mucho, la prueba se lanza con `ActivityScenario` y `TestAppDependencies` sin el reloj de Compose. |
| R11 | La conexión BLE siga abierta con la app en segundo plano, o Android la cierre. | Fuera de alcance (§1 del spec, sin servicio). Al volver, `APP_RESUMED` reconecta si se perdió (D29). |
| R12 | Tests y recursos que dependen de Scan y Scale queden rotos o huérfanos. | Lista en §1 y su destino en F6 y F7; `grep` de `scan_open_coffees`, `scale_open_coffees`, `ScanScreen` y `ScaleScreen` sin resultados al final de F7; lint `UnusedResources`. |
| R13 | El modo por defecto de `LocalExperienceMode` (`EXPERT`) oculte en un test un fallo de Básico. | Los tests de Básico proveen el modo explícitamente; `OnboardingTest` y `NewUserJourneyTest` usan la preferencia real (`BASIC` por defecto). |
| R14 | La reconexión a la real recordada no se puede probar de punta a punta sin báscula física. | `AutoReconnectTest` y `ScaleSessionManagerConnectionTest` con falsos; queda en el seguimiento con la DOT física (§8). |
| R15 | Medidas de rendimiento ruidosas en el emulador. | Calentamiento de 5 s que no cuenta, umbral en percentiles y anotación de las cifras; si una pasada falla por un pico aislado, se repite una vez y se anotan las dos. |

## 6. Preguntas para el usuario

Cada una tiene una propuesta; si se aprueba el plan sin comentarios, se aplica la propuesta.

1. **P1. Duración de los pasos Esperar (bloquea F3).** El §3.5 fija "duración de 1 a 120 s para todos los
   tipos", pero la migración crea un `WAIT` por hueco sin límite: un hueco de 3 min daría un paso que el
   validador rechaza al editar la receta. *Propuesta (D37): `WAIT` de 1 a 600 s y `POUR`/`SWIRL` de 1 a
   120 s.* Alternativas: partir el hueco en varios `WAIT` de 120 s (puede superar 20 pasos) o recortar el
   `WAIT` (cambia la curva).
2. **P2. "Reiniciar" del menú ⋯ (solo afecta a F7).** El spec no dice qué reinicia. *Propuesta (D43): antes de
   `Active`, vuelve al paso 1 y olvida la dosis; en `Active`, deshabilitado; en `Finished`, otra sesión con la
   misma receta y café.* Alternativa: poner a cero el temporizador de la báscula, como hoy (en `Active`
   terminaría la sesión con `TIMER_RESET`).
3. **P3. Báscula simulada sin permisos o con Bluetooth apagado.** La tabla de §3.3 solo da "Dar permisos" y
   "Activar". *Propuesta (D13): añadir el enlace "Usar báscula simulada" en esos dos estados*, porque no hay
   báscula física y hoy `ScanScreen` lo permite siempre.
4. **P4. "La última báscula fue la simulada".** Como la simulada nunca se recuerda, si antes se conectó una
   real, ¿se sigue reconectando a la real? *Propuesta (D28): sí; usar la simulada no borra la real
   recordada.* Alternativa: borrar la recordada al usar la simulada.

## 7. Verificación final

### 7.1 Suites

```
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Lint se compara con la línea base de F0 (sin errores nuevos ni avisos nuevos; `MissingTranslation` y
`UnusedResources` a cero en lo nuevo). Cadenas: cada `name` de `values/strings.xml` existe en
`values-en/strings.xml` y al revés (lint `MissingTranslation` y `ExtraTranslation`). `SessionScreenshotCapture`
se salta solo sin `-e screenshots true`. Al final de F7, `grep -rn -e scan_open_coffees -e scale_open_coffees -e ScanScreen -e ScaleScreen app/src`
no devuelve nada.

### 7.2 Rendimiento (`SessionPerformanceTest`, emulador API 36, 1080 × 2400, 420 dpi)

- **Montaje:** `createAndroidComposeRule<ComponentActivity>()`, `TestAppDependencies` con la simulada y la V60
  clásica (15 g), `SessionScreen` ya en el paso 4. El vertido lo lanza `session.simulateTargetPour(FAITHFUL)`
  (115 s de guion, sin tocar la UI).
- **Medida:** `window.addOnFrameMetricsAvailableListener` (en un `HandlerThread`), `FrameMetrics.TOTAL_DURATION`
  de cada frame durante 120 s de `Active`, tras 5 s de calentamiento que no cuentan.
- **Casos:** Básico (dial, página 1) y Experto con la página 2 (gráfica en vivo con la curva objetivo).
- **Umbral (los dos casos):** p95 ≤ 32 ms (dos intervalos de 60 Hz), frames > 32 ms ≤ 5 % del total, ningún
  frame > 250 ms y ninguno congelado (> 700 ms).
- **Anotación:** número de frames, p50, p90, p95, p99, máximo y porcentaje > 32 ms de cada caso, en la cabecera
  de este plan y en el resultado de F8.

### 7.3 E2E por adb (emulador, báscula simulada, sin ARTEMIS)

Antes: `adb devices -l` (si hay más de uno, preguntar cuál), `adb shell wm size` (debe ser 1080x2400; si no,
`adb shell wm size 1080x2400` y `adb shell wm density 420`, y `wm size reset` y `wm density reset` al acabar),
`./gradlew :app:installDebug` y `adb shell pm clear dev.dotscale`. Sin conceder permisos al principio. Se usa
`uiautomator dump` para localizar, `input tap` y `screencap -d <display id>`; `adb logcat -b crash -d` debe
quedar vacío en todo el recorrido.

| Paso | Recorrido | Comprobaciones |
|---|---|---|
| 1 | Abrir la app | "¿Cómo preparas tu café?"; "Básico" → Preparar (nunca la lista BLE). Tarjeta "Necesitamos permiso de Bluetooth" → "Dar permisos" → permitir → "Conecta tu báscula" |
| 2 | Tarjeta → hoja | "Buscar", "Mostrar todos" y "Usar báscula simulada"; elegirla cierra la hoja y la tarjeta pasa a "Conectando…" y a "Báscula simulada conectada" |
| 3 | Cafés → un café → Historial → Recetas → Preparar; tocar Cafés dos veces | La tarjeta sigue conectada sin reconectar; Cafés vuelve a su raíz; atrás en Cafés raíz selecciona Preparar |
| 4 | Sin receta: dosis 18, ratio 15 → agua 270 g. `adb shell am force-stop dev.dotscale` y abrir | Siguen 18 y 1:15; no se muestra el primer arranque; la simulada no reconecta: "Conecta tu báscula" |
| 5 | Simulada → receta V60 clásica → "Empezar" → "Tarar" → ⋯ → Simulación "+15 g" → captura → "Tarar" → "Seguir objetivo" | Sin barra inferior. Timer, agua, paso actual (con instrucción) y siguiente visibles a la vez; captura. ⋯ → "Modo experto": el timer no se reinicia; deslizar a la gráfica; ⋯ → desactivar: vuelve a la página 1. Con `adb shell settings put system font_scale 1.3`, repetir la comprobación y volver a 1.0 |
| 6 | Otra sesión → en `Active`, X → "Descartar" | Vuelve a Preparar; Historial no tiene esa preparación |
| 7 | `adb shell cmd bluetooth_manager disable` → Preparar → "Activar" | La tarjeta muestra "Bluetooth apagado" con su acción y el enlace a la simulada; tras activar, "Conecta tu báscula" |
| 8 | Sesión en `Active` → `adb shell settings put system accelerometer_rotation 0` y `user_rotation 1` → volver a 0 | La sesión sigue con el mismo timer y los mismos pasos |
| 9 | Sesión en curso → `adb shell input keyevent KEYCODE_HOME` → `adb shell am kill dev.dotscale` → abrir | Preparar sin sesión |
| 10 | Recetas → V60 clásica | 5 pasos con 2 Esperar e instrucciones; añadir un paso Remover con instrucción → guardar → sesión: icono de Remover en la lista e instrucción en el paso actual |
| 11 | Ajustes → Experto → Historial → la preparación del paso 5 | Gráfica, estabilidad, ajuste, "Comparar" y "Guardar como receta" visibles |
| 12 | `adb shell dumpsys gfxinfo dev.dotscale reset` → sesión de 2 min en Experto, página 2 → `adb shell dumpsys gfxinfo dev.dotscale` | Contraste de R10: porcentaje de frames lentos y percentiles anotados junto a §7.2 |
| 13 | Capturas: `./gradlew :app:installDebug :app:installDebugAndroidTest` y `adb shell am instrument -w -e class dev.dotscale.ui.session.SessionScreenshotCapture -e screenshots true dev.dotscale.test/androidx.test.runner.AndroidJUnitRunner`; `adb pull /sdcard/Android/data/dev.dotscale/files/screenshots` | 12 PNG (Preparar, sesión Básico, sesión Experto páginas 1 y 2, resumen Básico y Experto, en claro y oscuro), revisados a ojo. Se lanza con `am instrument` para que la desinstalación de `connectedDebugAndroidTest` no borre los archivos (D31 de SPEC-004) |

## 8. Seguimiento fuera de este plan

- Calibrar con la DOT física `PrepConfig` (estabilidad y tara), comprobar si envía la trama de tara al pulsar
  su botón y a qué ritmo manda el peso, y probar la reconexión automática a una báscula real (R5, R14).
- Cambios que conviene llevar al spec al cerrar (no se tocan ahora): la frase del §3.4 sobre el criterio de
  estable (no hay bandera de la báscula ni estabilidad de peso previa; D17), la duración de `WAIT` según P1,
  la confirmación de tara ampliada (D19), las ampliaciones de D9 y D13, y que el primer arranque no es una
  clave de navegación (D11).
- Servicio en primer plano para la sesión (§7 del spec), si el uso real lo pide.
- Fusionar `feat/coffee-catalog` antes que esta rama.
