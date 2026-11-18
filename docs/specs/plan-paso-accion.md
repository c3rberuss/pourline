# Plan de implementación: SPEC-010 Paso "Acción" y tiempo total de la receta

| Campo | Valor |
|---|---|
| Spec | [`spec-paso-accion.md`](spec-paso-accion.md) (SPEC-010), aprobado el 2026-11-18 |
| Estado | Aprobado (2026-11-18), pendiente de implementación |
| Fecha | 2026-11-18 |
| Rama | `feat/step-action`, desde `develop` (`00eb163`), con el spec commiteado (`a0a71b9`) |
| Módulos | `:app` (`:protocol` no cambia) |
| Base de datos | Sin cambios: `HistoryDatabase` sigue en `version = 7` y `app/schemas` no cambia (§3.2) |
| Formato del QR | Sigue en `v=1`. El código de tipo `3`, que hoy es `Corrupt`, pasa a ser `ACTION` (§3.5) |
| Línea base (F0, sobre `5cb2207`) | JVM: 220 tests en 16 clases, 0 fallos. `emulator-5556`: 133 tests, 0 fallos. `emulator-5560`: 32 tests, 0 fallos. `:app:lintDebug`: 2 errores (`StringFormatInvalid`) y 21 avisos, los mismos de [`plan-qr-grande.md`](plan-qr-grande.md) |

El spec cambia dos cosas independientes, y el plan las hace en este orden:

1. **El tiempo total (F1 y F2).** Primero el cálculo (`Recipe`, `RecipeListItem` y el editor), junto con el recuento
   de la fila, que pasa a contar todos los pasos (P4). Después la fila de la lista, la línea de totales, la tarjeta
   para compartir y la pantalla de importar. No depende de la Acción, así que se puede probar solo con las recetas
   actuales.
2. **El tipo Acción (F3 a F6).** F3 añade el valor del enum con todo lo que necesita para compilar: la etiqueta, el
   icono, el color, la línea de acción de la sesión sin báscula y el código del QR. F4 lo lleva al editor. F5 y F6 lo
   prueban en la sesión, la tarjeta, el QR y la importación.

F0 mide la línea base y congela los bytes del QR de las recetas actuales antes de tocar código. F7 es la
verificación.

Al recorrer el código salieron cuatro cosas que condicionan el plan:

- **Añadir `ACTION` rompe la compilación en cinco `when` exhaustivos:** `stepTypeLabel`, `stepTypeIcon` y
  `stepTypeColors` (`ui/session/SessionFormat.kt:31`, `:37` y `:47`), `stageAction` (`ui/session/ScalelessPanels.kt:191`)
  y el código de tipo de `encodePayload` (`recipe/share/RecipeSharePayload.kt:92`). Por eso F3 los cambia todos a la
  vez, y la decodificación del código `3` va en la misma fase (si no, la app escribiría QR que ella misma no lee).
- **Casi todo lo demás ya es genérico.** El validador ya da 1-120 s a todo lo que no es `WAIT` y ya exige un `POUR`.
  `withDerivedWater`, `TargetCurve.fromRecipe`, `StageAlertTracker`, `AndroidStageNotifier.stageStarted`,
  `CurrentStep`, `NextStep`, `StepList`, `BrewKeepAlive` y `BrewService` solo distinguen `POUR` con `if`, o no
  distinguen nada. La Acción hereda ese comportamiento sin código propio.
- **Las cuatro etiquetas no caben en el selector.** A 360 dp cada opción deja unos 37 dp para la etiqueta, y "Remover"
  necesita unos 55. Además, con la escala de letra a 1,3, "Remover" ya se corta hoy con tres opciones. D15 propone
  una etiqueta con el tipo elegido y cuatro opciones solo con icono.
- **Una app sin este cambio no dice "receta no válida".** `parsePayload` lanza `MalformedPayload`, `decodePayload` lo
  convierte en `DecodeResult.Corrupt` y la pantalla muestra `recipe_import_corrupt` ("No se pudo leer la receta del
  QR"). El riesgo aceptado del §3.5 es el mismo; solo cambia el texto (discrepancia 2).

No hay detekt ni ktlint en el proyecto. Como en los planes anteriores, el control es `:app:lintDebug`, y el estilo es
el del código que rodea a cada cambio (dos espacios, KDoc en español).

## 1. Punto de partida

### 1.1 Se reutiliza

| Pieza | Uso |
|---|---|
| `recipe/Recipe.kt`: `withDerivedWater`, `RecipeStage.endMs` | `withDerivedWater` ya da a todo paso que no es `POUR` el agua del vertido anterior. `endMs` es la base del tiempo total. |
| `recipe/RecipeValidator.kt` | `maxDuration = if (stage.type == StageType.WAIT) MAX_WAIT_MS else MAX_DURATION_MS` ya da 1-120 s a la Acción, y `RecipeError.NoPour` ya exige un vertido. Solo cambia el KDoc. |
| `brew/TargetCurve.kt` (`fromRecipe`), `brew/TargetGuide.kt`, `brew/StageAlertTracker.kt`, `ui/scale/AndroidStageNotifier.kt` | Meseta en todo lo que no es `POUR`. El aviso de cambio de paso (`stepCue()`: `TONE_PROP_ACK` y doble vibración) no mira el tipo. |
| `ui/session/SessionScreen.kt` (`CurrentStep`, `NextStep`, `NextStepCountdown`), `ui/session/StepList.kt`, `ui/session/BrewDial.kt`, `ui/session/ScalelessPanels.kt` (aro de `ScalelessActivePanel`) | Usan `stepTypeLabel`, `stepTypeIcon` y `stepTypeColors`. Con las tres funciones al día, la Acción sale con su etiqueta, su icono y su color, y sin "hasta X g". |
| `session/BrewKeepAlive.kt` (`toScalelessNotice`, `BrewNoticeStep`) y `session/BrewService.kt` (`scalelessText`) | El tipo sale de `stepTypeLabel` y el agua solo se rellena en `POUR`. |
| `history/HistoryConverters.kt` (`stageTypeToString`, `stringToStageType`) y `RecipeStageEntity.type` | El tipo se guarda por nombre. `"ACTION"` es un valor más de la misma columna. |
| `history/TargetCurveCodec.kt` | Solo escribe las etapas `POUR`: el historial no cambia. |
| `ui/session/SessionFormat.kt` (`formatClock`) | El formato `m:ss` del tiempo total. Ya da `"60:00"` con 3 600 000 ms. |
| `share/RecipeShareCard.kt` (`StepRow`, `RecipeFacts`, `Separator`, `compactStyle`) y `ui/recipes/RecipeImportScreen.kt` (`StageRow`, `RecipeSummary`) | Las filas de paso ya usan `stepTypeIcon` y `stepTypeLabel`, y el agua solo en `POUR`. |
| `recipe/share/RecipeSharePayload.kt` (`encodePayload`, `parsePayload`, `trimPlans`) | El agua solo se escribe en `POUR`. El recorte de instrucciones no mira el tipo. |
| `ui/recipes/RecipeEditForm.kt` (`setStageType`, `parse`, `from`, `parseStart`, `parseSeconds`) | Cambiar a cualquier tipo que no sea `POUR` ya vacía el agua. `parseStart` y `parseSeconds` sirven para el tiempo en vivo. |
| `ui/recipes/RecipeRow.kt` | El resumen es un `AntText` sin `maxLines`: ya pasa a una segunda línea si no cabe (§3.8). |
| Tests: `RecipeTestFixtures.kt` (`stage`, `recipeOf`, `v60Classic`), `RecipeShareFixtures.kt`, `ShareCardsLayoutTest` (`capture`, `node`, `textOf`, `stepsAbove`, `brandInside`), `RecipeImportScreenTest` (`uriOf` y el precedente de `fontScale = 1.3f`), `ScalelessSessionScreenTest` (`at` y el reloj de prueba), `ScaleSessionManagerScalelessTest` (`FakeTargetReferences.putRecipe`), `SessionDriver`, `TestAppDependencies` | La base de los tests nuevos. |

### 1.2 Falta o hay que corregir

- `StageType` tiene tres valores, y los cinco `when` exhaustivos de la introducción no compilan con un cuarto.
- `AntColors` no tiene `magenta`. `AntIcons` no tiene una mano ni un reloj.
- `Recipe` y `RecipeListItem` no tienen duración. `RecipeEditForm` no calcula el tiempo en vivo.
- `RecipeRepository.list()` calcula `stageCount` con `with.stages.count { it.type == StageType.POUR }`: la fila dice
  "N pasos" pero solo cuenta los vertidos (P4).
- `recipe_summary` tiene cuatro argumentos. `Totals` (`RecipeEditScreen.kt:427`) no muestra el tiempo. La tarjeta y la
  pantalla de importar tampoco.
- El selector de tipo (`RecipeEditScreen.kt:336`) tiene tres opciones con etiqueta e icono. Con cuatro no caben (D15).
- No hay aviso para una Acción sin instrucción.
- `parsePayload` trata el código de tipo `3` como `Corrupt`.
- `tour_recipe_edit_stage_body` dice que cada paso es "un vertido, un removido o una espera".
- **Tests que cambian de expectativa.** Solo los que obliga el spec:

  | Test | Hoy | Con el spec | Fase |
  |---|---|---|---|
  | `TargetPickerTest` (los dos `RecipeListItem` de `recipes`) | Ocho argumentos | Con `totalDurationMs` (D3) | F1 |
  | `RecipeDaoTest.typeAndInstructionAreSavedAndRead` | `stageCount` 2 ("solo los vertidos") | 4: todos los pasos (D3) | F1 |
  | `RecipeEditScreenTest.saveAsRecipeFromABrewFillsOneStagePerPourAndWarnsAboutAMissingDose` | `stageCount` 3 | 5: tres vertidos y dos esperas (D3) | F1 |
  | `RecipesScreenTest.theTwoBuiltInsShowTheirNamesSummaryAndTag` | `recipe_summary` con cuatro argumentos y "3 pasos" | "5 pasos" (F1); cinco argumentos, con "1:55" (F2) | F1, F2 |
  | `RecipeEditScreenTest.liveTotalsAndAPreviewAppearWithAValidRecipe` | `recipe_edit_totals` | `recipe_edit_totals_time` con "0:10" | F2 |
  | `SessionFormatTest.each step type has its own label and icon` | 3 iconos distintos | 4, y la etiqueta de `ACTION` | F3 |
  | `RecipeShareCodecTest.an unknown method code or stage type is corrupt` | `minimal(stageFlags = 3)` es `Corrupt` | El `3` es `ACTION`. Se renombra `an unknown method code is corrupt` y ese caso pasa a `type code 3 is an action` (con una sola Acción, `Invalid` por `NoPour`) | F3 |
  | `RecipeEditScreenTest.chooseType` (ayuda) | Busca la opción con `hasText(etiqueta)` | `hasContentDescription(etiqueta)`: las opciones solo llevan icono (D15) | F4 |

### 1.3 Discrepancias entre el spec y el código

| # | Spec | Código real | Ajuste que propone el plan |
|---|---|---|---|
| 1 | §3.4: "el color y el icono" aparecen también en el selector del editor, la pantalla de importar y la tarjeta para compartir. | En esos tres sitios los tipos solo llevan el icono, en gris (`textSecondary` en la tarjeta y la importación, el color del texto en el selector). El color del tipo solo está en la sesión. | La Acción recibe el mismo trato que los otros tipos en cada sitio: color e icono en la sesión, solo icono en los otros tres. Colorear esos tres sitios cambiaría también los tipos existentes. |
| 2 | §3.5: una app sin este cambio rechaza el QR como "receta no válida". | Lo rechaza como `DecodeResult.Corrupt`: "No se pudo leer la receta del QR" (`recipe_import_corrupt`). | Ninguno: no se puede cambiar una app ya instalada. El riesgo aceptado es el mismo. Conviene corregir el texto del §3.5 al cerrar (§8). |
| 3 | §3.6: el tiempo solo aparece si se pueden leer todos los inicios y duraciones; "si no, se muestra el resto de la línea". | `Totals` no muestra nada si no se puede leer el agua del último vertido. | Sin agua legible, la línea sigue sin aparecer, también sin el tiempo (D5). |
| 4 | §4: una receta de 60 minutos o más. | El validador limita el último inicio a menos de 10:00 y la duración a 600 s, así que una receta válida acaba antes de 20:00. | El caso solo se prueba en `formatClock` (`SessionFormatTest`). |
| 5 | §3.8: el ejemplo dice "3 etapas". | La cadena dice "3 pasos" y `RecipeRepository.list` solo cuenta los `POUR`. Una AeroPress con seis pasos y un vertido diría "1 paso". | Decidido por el usuario (P4): `stageCount` cuenta todos los pasos, de cualquier tipo (D3). La V60 clásica pasa a "5 pasos" y la 4:6 a "9 pasos". La cadena `recipe_stage_count` no cambia. |
| 6 | §3.5: el código `3` pasa a ser `ACTION`. | La tabla del §3.1 de SPEC-009 dice que el `3` es `Corrupt`. | No se modifica SPEC-009 ahora; se anota en el §8. |
| 7 | §3.6: el selector "tiene que caber en el ancho mínimo sin cortar etiquetas". | Hoy, a 360 dp y con la letra a 1,3, "Remover" ya se corta con tres opciones (unos 72 dp para unos 62 disponibles). | La propuesta de D15 lo resuelve también para los tipos existentes. |

## 2. Decisiones que completan el spec

Marcadas como "(concreta)" si eligen entre opciones que el spec deja abiertas, y como "(amplía)" si añaden algo que
el spec no dice.

### 2.1 Tiempo total

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Orden (concreta) | El tiempo total (F1 y F2) va antes que la Acción (F3 a F6). | Es independiente y más pequeño. Así, F3 solo tiene que cuidar los cinco `when` y el QR, y los tests de interfaz de F2 no dependen de la Acción. |
| D2 | `Recipe.totalDurationMs` (concreta §3.1) | `val totalDurationMs: Long get() = stages.maxOfOrNull { it.endMs } ?: 0L` en `Recipe`, con un KDoc que dice que incluye los huecos y la espera final, y que no es `TargetCurve.endMs` (último vertido) sino `TargetCurve.lastStepEndMs`. | Es la definición del §3.1. Con `maxOfOrNull` no depende de que los pasos estén ordenados. |
| D3 | `RecipeListItem`: duración y recuento (concreta §3.1; P4, decisión del usuario) | `val totalDurationMs: Long` al final de `RecipeListItem`, sin valor por defecto. En `RecipeRepository.list()`: `totalDurationMs = with.stages.maxOfOrNull { it.startMs + it.durationMs } ?: 0L`, con los `RecipeStageEntity` que ya carga, y `stageCount = with.stages.size` (todos los pasos: verter, remover, esperar y Acción), con KDoc en `stageCount`. `TargetPickerTest` pasa `115_000` y `210_000`. | Sin valor por defecto, el compilador encuentra cualquier constructor olvidado, que si no mostraría "0:00". La consulta del DAO no cambia. `stageCount` solo lo lee `RecipeRow` (`grep`), así que el recuento cambia en la lista de Recetas y en el selector de objetivo, y en ningún otro sitio. |
| D4 | Tiempo en vivo del editor (concreta §3.6) | `fun totalDurationMs(): Long?` en `RecipeEditForm`: el mayor `parseStart(start) + parseSeconds(duration)` de los pasos, o `null` si algún inicio o alguna duración no se puede leer. Una duración vacía no se puede leer. No mira el tipo ni el agua. | Es la regla del §3.6. `parseStart` y `parseSeconds` son los mismos que usan los errores del formulario, así que el tiempo aparece y desaparece a la vez que los errores de formato. |
| D5 | Línea de totales (concreta §3.6; discrepancia 3) | `Totals(form)`: si `totalWaterG()` es `null`, no se compone nada, como hoy. Si no, con `time = form.totalDurationMs()?.let(::formatClock)`: con ratio y tiempo, `recipe_edit_totals_time`; con ratio y sin tiempo, `recipe_edit_totals` (como hoy); sin ratio y con tiempo, `recipe_edit_totals_water_time`; sin ninguno, `recipe_edit_totals_water`. El `contentDescription` es la misma cadena con `recipe_total_time_description` en lugar del tiempo (D7). `tourAnchor("recipe_edit_totals")` y `testTag("recipe_edit_totals")` no cambian. | Cuatro cadenas completas permiten traducir el orden y el separador. Mostrar el tiempo sin el agua obligaría a una quinta forma de la línea que el spec no pide. |
| D6 | Resumen de la fila (concreta §3.8) | `recipe_summary` pasa a `%1$s g · %2$s g · %3$s · %4$s · %5$s` en los dos idiomas. `recipeSummary(doseG: Double, totalWaterG: Double, stageCount: Int, time: String): String`. `RecipeRow` la llama dos veces: con `formatClock(item.totalDurationMs)` para el texto y con `stringResource(R.string.recipe_total_time_description, formatClock(...))` para el `contentDescription` del mismo `AntText`. El `AntText` sigue sin `maxLines`. | Solo `RecipeRow` usa la cadena (lo comprueba un `grep` en F2). `TargetPicker` usa `RecipeRow`, así que el selector de objetivo lo hereda sin cambios. |
| D7 | Accesibilidad (concreta §3.9) | `recipe_total_time_description` ("Duración total %1$s" / "Total time %1$s") sustituye al `m:ss` en el `contentDescription` de la fila, de la línea de totales y del tiempo de la pantalla de importar. En la tarjeta para compartir no hace falta: es una imagen. | Un "3:30" suelto, justo después del ratio "1:16,7", es ambiguo para TalkBack. |
| D8 | Tarjeta para compartir (concreta §3.8; amplía con `AntIcons.Clock`) | En `RecipeFacts`, tras el ratio: `Separator(style)`, `Icon(AntIcons.Clock, null, Modifier.size(StepIconSize), tint = AntTheme.colors.textSecondary)` y `AntText(formatClock(recipe.totalDurationMs), Modifier.testTag("share_recipe_duration"), style = style, maxLines = 1)`, dentro del mismo `Row` de `share_recipe_facts`. `AntIcons.Clock` (nuevo) es `strokeIcon("Clock", "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12Z", "M12 7V12L15 14")`, el círculo de `Info` con las agujas de `History`. | El icono de 12 dp cabe en la línea de 16 sp de `compactStyle()`, así que la tarjeta no crece. El `testTag` en el texto deja leerlo con `textOf`, como `share_recipe_ratio`. `History` ya es un reloj, pero con la flecha del historial. |
| D9 | Pantalla de importar (concreta §3.8; P2) | `RecipeSummary` cambia el `AntText` único de `recipe_import_facts` por un `FlowRow` con el mismo `testTag`, con los elementos dosis, agua, ratio, tiempo y temperatura separados por "·". El tiempo es `Icon(AntIcons.Clock, null, 16 dp, textSecondary)` y `AntText(formatClock(...), Modifier.testTag("recipe_import_duration").semantics { contentDescription = <D7> })`. | La vista previa es lo que se ve justo después de escanear la tarjeta: el mismo icono evita confundir "1:55" con el ratio. El `FlowRow` pasa a otra línea con la letra grande. Ningún test lee hoy el texto de `recipe_import_facts`. |

### 2.2 Tipo Acción

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D10 | Enum y `when` (concreta §3.1) | `enum class StageType { POUR, SWIRL, WAIT, ACTION }`. En F3 cambian juntos: `stepTypeLabel` (`ACTION -> R.string.step_type_action`), `stepTypeIcon` (`ACTION -> AntIcons.Action`), `stepTypeColors` (`ACTION -> colors.magenta`), `stageAction` (`StageType.SWIRL, StageType.WAIT, StageType.ACTION -> instrucción o etiqueta`) y `encodePayload` (`StageType.ACTION -> 3`). KDoc al día en `StageType`, `RecipeStage.targetWaterG`, `withDerivedWater`, `RecipeError.DurationOutOfRange` ("1-120 s en `POUR`, `SWIRL` y `ACTION`"), `TargetStage.targetWaterG`, `StageForm`, `RecipeEditForm.setStageType` y `stageAction`. | Son los únicos `when` exhaustivos sobre `StageType` (`grep`). El resto distingue `POUR` con `if` y ya trata la Acción como un remover o una espera. |
| D11 | Color (concreta §3.4) | `AntSeedColors.magenta: Color = Color(0xFFEB2F96)` y `AntColors.magenta: AntColorRole`, con `seeds.magenta.toRole(dark = false, container = LIGHT_CONTAINER)` en `antLightColors` y `toRole(dark = true, container = DARK_CONTAINER)` en `antDarkColors`, como `purple` y `cyan`. KDoc de `stepTypeColors`: "Verter en el primario, Remover en morado, Esperar en cian y Acción en magenta. Ninguno es verde, naranja ni rojo, que en la sesión significan estabilidad". `AntColorsContrastTest.roles()` gana `"magenta" to magenta` (amplía). | La cuenta atrás de 5 s escribe con `colors.text` sobre `bg` (`NextStepCountdown`): el test comprueba que ese par cumple 4,5:1 en claro y en oscuro. |
| D12 | Icono de mano (concreta §3.4 y §6) | `AntIcons.Action`, con `strokeIcon` como `Pour`, `Swirl` y `Wait`: viewport 24 × 24, solo trazo (`STROKE_WIDTH` = 1,75, extremos y uniones redondeados), comandos absolutos en mayúscula, sin rellenos y dentro de 2..22 en los dos ejes. Dibujo de partida, un índice que toca con un arco de toque encima: `"M6.5 4.5A4.5 4.5 0 0 1 13.5 4.5"` (toque), `"M8.5 15.5V6.5A1.5 1.5 0 0 1 11.5 6.5V12"` (índice), `"M11.5 11A1.5 1.5 0 0 1 14.5 11V12"` (corazón) y `"M14.5 11.5A1.5 1.5 0 0 1 17.5 11.5V15.5A5.5 5.5 0 0 1 12 21H11.5A5 5 0 0 1 7.6 19.1L5.2 16A1.4 1.4 0 0 1 7.4 14.3L8.5 15.5"` (anular, palma y pulgar). En la implementación se puede desplazar medio punto para centrarlo y simplificar los nudillos si a 12 dp se empastan. KDoc: "Una mano que toca: paso Acción". | Cuatro trazos como mucho, igual que `Wait`, para que se lea a 12 dp (tarjeta), 16 dp (lista e importación) y 20 dp (tarjeta del paso y selector). La comprobación es a ojo, en las capturas del §7.6. |
| D13 | QR (concreta §3.5) | `encodePayload`: `StageType.ACTION -> 3`. `parsePayload`: `3 -> StageType.ACTION`; la rama `else -> throw MalformedPayload(...)` se queda para que el `when` sobre `Int` siga siendo exhaustivo, aunque con 2 bits ya no se alcanza. El agua solo se escribe y se lee en `POUR`, como hoy. KDoc de `ShareFormat.S_TYPE_MASK`: "0 `POUR`, 1 `SWIRL`, 2 `WAIT` y 3 `ACTION`". `RecipeShareCodec.VERSION` no cambia. | Es el §3.5. Fuera del KDoc, `RecipeShareBinary.kt` no cambia. |
| D14 | Bytes de las recetas sin Acción (concreta §5) | `test/.../recipe/share/RecipeSharePayloadStabilityTest.kt` (nuevo, F0): para cada fixture (`v60Classic()`, el 4:6 de fábrica con `FakeRecipeTexts()`, `everyField(1_000)`, `everyField(100)`, `everyField(1)`, `worstCaseRecipe()` con `catalogGrinder`, `fourSixUserRecipe()` con `femobookA2` y `withLongTexts(300, 120, 10)`), concatena `encodePayload(recipe, grinder, plan).bytes` de todos los `plan` de `trimPlans(recipe)`, con la longitud de cada uno delante, y compara el SHA-256 en hexadecimal con un mapa congelado. F0 calcula los valores con el código de `a0a71b9`, antes de tocar nada. | `GOLDEN_V60_PAYLOAD` solo congela una receta. El hash cubre todas las fixtures y todos los recortes sin pegar cientos de bytes en hexadecimal. Los payloads son anteriores a `deflate`, así que no dependen de `zlib`. |
| D15 | Selector del editor (concreta §3.6 y §6; P1) | La primera fila de `StageCard` pasa a ser un `Row`: a la izquierda, el nombre del tipo elegido (`AntText(stringResource(stepTypeLabel(stage.type)), Modifier.weight(1f).testTag("recipe_stage_type_label_$index"), strong = true, maxLines = 2)`); a la derecha, `AntSegmented(..., iconOnly = true)` con las cuatro opciones, de 48 dp cada una. Las opciones salen de `StageType.entries.map { AntSegmentedOption(it, stringResource(stepTypeLabel(it)), stepTypeIcon(it)) }`, en el orden del enum. El `testTag("recipe_stage_type_$index")` sigue en el segmentado. Medidas a 360 dp, en la tabla siguiente. | Es la única forma probada de las cuatro que no corta ninguna etiqueta a 360 dp con la letra a 1,3, y conserva un texto visible con el tipo. Las opciones solo con icono son el ejemplo del §3.6; la etiqueta del tipo elegido evita depender solo de los iconos. |
| D16 | `AntSegmented` con solo iconos (amplía) | `AntSegmented` gana `iconOnly: Boolean = false`. Con `true`: cada opción exige icono (`requireNotNull(option.icon)`), lo dibuja a 20 dp y no compone el `Text`; la `Row` de cada opción lleva `semantics { contentDescription = option.label }` junto al `selectable` (que ya fusiona los descendientes con `Role.RadioButton`); y el componente toma su propio ancho, `options.size × 48 dp + 2 × TrackPadding`. Con `false` no cambia nada, así que los otros ocho usos (`SettingsScreen`, `CoffeesScreen`, `TargetPicker`, `FourSixScreen`, `GrinderEditScreen`, `SessionOptionsSheet` y `AntComponentsPreview`) quedan igual. `AntComponentsPreview` gana un ejemplo con `iconOnly`. | 48 dp es el área táctil mínima. TalkBack lee "Acción, botón de opción, seleccionado" igual que hoy con el texto. |
| D17 | Aviso de Acción sin instrucción (concreta §3.6) | Bajo el campo de instrucción, el contador pasa a una `Row`: a la izquierda, solo si `stage.type == StageType.ACTION && stage.instruction.isBlank()`, `AntText(stringResource(R.string.recipe_action_instruction_hint), Modifier.weight(1f).testTag("recipe_stage_action_hint_$index"), type = AntTextType.Warning, style = AntTheme.typography.textSM)`; si no, un `Spacer(Modifier.weight(1f))`. A la derecha, el contador de siempre, con su `testTag`. No cambia `RecipeEditForm.errors()` ni el estado del `AntInput`. | "Bajo el campo", sin una línea más cuando no hay aviso. El color de aviso (y no el de error) dice que se puede guardar. |
| D18 | Texto del recorrido (amplía; P3) | `tour_recipe_edit_stage_body`: "Cada paso es un vertido, un removido, una espera o una acción, con su tiempo." / "Each step is a pour, a swirl, a wait or an action, with its timing." `tour_recipe_edit_totals_body` ya habla de la duración y no cambia. | El paso del recorrido señala la tarjeta del paso, que ahora ofrece cuatro tipos. Con iconos sin texto (D15), es el sitio donde se nombran todos. |
| D19 | Sesión y notificación (concreta §3.7) | Solo cambia `stageAction` (D10). Con báscula, `CurrentStep` ya muestra "Acción" (o "Acción · nombre") como título y la instrucción debajo (`session_instruction`), sin "hasta X g". `StepList`, `NextStep`, `NextStepCountdown`, el aro de `ScalelessActivePanel` y la notificación (`brew_notice_step`, sin agua) salen de `stepTypeLabel` y `stepTypeColors`. El aviso de paso es el de siempre (`stageStarted` → `stepCue()`). | Ver §1.1. Los tests de F5 lo comprueban en lugar de cambiar código que ya funciona. |

**Medidas del selector (D15).** Anchos estimados con las métricas de Roboto en `typography.text` (14 sp). F4 los
comprueba en el emulador con un test a 360 dp y letra a 1,3.

| Etiqueta | es (1,0) | es (1,3) | en (1,0) | en (1,3) |
|---|---|---|---|---|
| Verter / Pour | ≈ 38 dp | ≈ 49 dp | ≈ 29 dp | ≈ 38 dp |
| Remover / Swirl | ≈ 55 dp | ≈ 72 dp | ≈ 30 dp | ≈ 39 dp |
| Esperar / Wait | ≈ 48 dp | ≈ 62 dp | ≈ 28 dp | ≈ 36 dp |
| Acción / Action | ≈ 43 dp | ≈ 56 dp | ≈ 40 dp | ≈ 52 dp |

A 360 dp quedan 304 dp de contenido en la tarjeta del paso (16 dp de página y 12 dp de tarjeta pequeña a cada lado) y
300 dp para las opciones (2 dp de pista a cada lado). Cada opción tiene 8 dp de relleno a cada lado, y el icono ocupa
16 dp más 6 dp de separación.

| Diseño | Ancho por opción | Sitio para la etiqueta | A 1,0 | A 1,3 |
|---|---|---|---|---|
| 3 opciones con icono (hoy) | 100 dp | ≈ 62 dp | Cabe | Se corta "Remover" |
| 4 opciones con icono | 75 dp | ≈ 37 dp | Solo caben "Pour", "Swirl" y "Wait" | No cabe ninguna |
| 4 opciones sin icono | 75 dp | ≈ 59 dp | Cabe, justo ("Remover") | Se cortan "Remover" y "Esperar" |
| 4 opciones solo con icono, a todo el ancho | 75 dp | Sin etiqueta visible | Cabe | Cabe |
| **Propuesta:** tipo elegido + 4 iconos de 48 dp | 48 dp | 304 − 196 − 8 = 100 dp para el nombre | Cabe | Cabe ("Remover" en seminegrita, ≈ 75 dp) |

Con la letra a 2,0 (el máximo de Android 14), "Remover" ocupa unos 114 dp y pasaría a dos líneas. El proyecto
comprueba la letra a 1,3; no se cubre más allá.

### 2.3 Pruebas

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D20 | Recetas de prueba (amplía) | JVM, en `test/.../recipe/RecipeTestFixtures.kt`: `fun aeroPressWithActions(): Recipe`, "AeroPress invertida", `BrewMethod.AEROPRESS`, 15 g, 85 °C y seis pasos: `POUR` 0-10 s hasta 200 g; `SWIRL` 10-20 s; `ACTION` 20-25 s "Pon la tapa"; `WAIT` 25-85 s; `ACTION` 85-90 s "Invierte"; y `ACTION` 90-120 s sin nombre ni instrucción. Con `withDerivedWater()`. Tiempo total 2:00, 200 g y ratio 1:13,3. En `androidTest/.../recipe/TestRecipes.kt` (nuevo), la misma receta y `fun actionFirstRecipe(instruction: String?): Recipe` ("Acción primero": `ACTION` 0-30 s con la instrucción, y `POUR` 30-50 s hasta 200 g). | Cubre a la vez una Acción con instrucción, otra sin ella, un remover, una espera larga y la espera entre medias. La receta que empieza con una Acción deja probar la sesión con báscula en tiempo real sin esperar al paso. Los tests instrumentados no ven las fixtures JVM, como en D42 de `plan-qr-grande.md`. |
| D21 | Emuladores y ejecución filtrada (concreta §5) | Los tests instrumentados van en `emulator-5556` (`SG_API34`, `en-US`), salvo `RecipeImportScreenTest` y `RecipeShareTest`, que van en `emulator-5560` (`SG_API36_play`), como en `plan-qr-grande.md`. Siempre con `-Pandroid.testInstrumentationRunnerArguments.class=<clases>`, y en la JVM con `--tests`. Nunca la batería completa. `SessionPerformanceTest` no se ejecuta: falla también en `develop` en `emulator-5556`. | Es la norma del §5 y del encargo. Los tests de interfaz localizan por `testTag` y leen los textos con `context.getString(...)`, así que no dependen del idioma del emulador. |

## 3. Fases

Orden: F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7. F2 depende de F1. F3 no depende de F1 ni de F2, pero va después (D1).
F4, F5 y F6 dependen de F3 y no dependen entre sí.

| Fase | Contenido | Tamaño | Estado |
|---|---|---|---|
| F0 | Línea base y bytes congelados del QR | S | Hecha |
| F1 | Tiempo total: cálculo en `Recipe`, `RecipeListItem` y el editor; recuento de todos los pasos | S | Hecha |
| F2 | Tiempo total: lista, editor, tarjeta e importación | M | Hecha |
| F3 | Tipo Acción: modelo, etiqueta, icono, color, línea de acción y QR | M | Hecha |
| F4 | Editor: selector de cuatro tipos, aviso y recorrido | M | Pendiente |
| F5 | Sesión con y sin báscula, aviso de paso y notificación | M | Pendiente |
| F6 | Tarjeta, QR e importación con Acción | S | Pendiente |
| F7 | Verificación: clases afectadas, lint, E2E por adb, capturas y prueba del usuario | M | Pendiente |

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app`, nunca con `./gradlew build`
ni con builds limpios.

- **Compilar:** `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`.
- **Tests JVM:** `./gradlew :app:testDebugUnitTest --tests "<clase>" [--tests "<clase>" ...]`, solo las clases de cada
  fase.
- **Tests instrumentados:** `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<clases separadas por comas>`,
  en el emulador de D21. Nunca dos ejecuciones a la vez en el mismo emulador. `emulator-5554` y el teléfono del
  usuario (Xiaomi, por adb inalámbrico) no se tocan; si `adb devices -l` muestra otro dispositivo, se pregunta.
- **Sin ARTEMIS.** Tests JVM, tests instrumentados y adb (`uiautomator dump`, `input`, `screencap`).
- **Fallos previos que no bloquean:** `SessionPerformanceTest` (no se ejecuta) y, si aparecen, los intermitentes que
  anota la línea base de F0.
- **Lint:** `./gradlew :app:lintDebug` en F0, F2, F3, F4 y F7, sin errores ni avisos nuevos respecto a F0, y sin
  `MissingTranslation`, `ExtraTranslation` ni `UnusedResources` en las cadenas nuevas.
- **Commits:** el usuario autoriza un commit por fase, que hace el implementador al cerrarla con la fase en verde.
  Conventional Commits en inglés, sin `Co-Authored-By` ni atribución de herramientas, en `feat/step-action` (nunca en
  `main` ni `develop`), sin archivos de credenciales. Cada fase propone su mensaje. El del plan:
  `docs(specs): add the SPEC-010 implementation plan`.

### F0. Línea base y bytes congelados del QR

**Objetivo:** conocer el punto de partida y congelar los bytes del QR de las recetas actuales antes de tocar código.

**Pasos:**

1. `adb devices -l`: `emulator-5556` y `emulator-5560`. `emulator-5554` y el Xiaomi no se usan.
2. JVM, sin cambios de código:
   `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.RecipeValidatorTest" --tests "dev.josueamaya.pourline.recipe.StageGapsTest" --tests "dev.josueamaya.pourline.recipe.share.*" --tests "dev.josueamaya.pourline.ui.recipes.RecipeEditFormTest" --tests "dev.josueamaya.pourline.ui.session.SessionFormatTest" --tests "dev.josueamaya.pourline.ui.theme.AntColorsContrastTest" --tests "dev.josueamaya.pourline.brew.StageAlertTrackerTest" --tests "dev.josueamaya.pourline.brew.TargetCurveTest" --tests "dev.josueamaya.pourline.history.HistoryConvertersTest" --tests "dev.josueamaya.pourline.session.BrewKeepAliveTest" --tests "dev.josueamaya.pourline.session.ScaleSessionManagerScalelessTest"`.
   Se anotan los totales y los fallos.
3. `emulator-5556`: `RecipeDaoTest`, `RecipeEditScreenTest`, `RecipesScreenTest`, `TargetPickerTest`,
   `ShareCardsLayoutTest`, `RecipesHistoryTourTest`, `SessionScreenTest` y `ScalelessSessionScreenTest` (nombres
   completos en el §7.1).
   `emulator-5560`: `RecipeImportScreenTest` y `RecipeShareTest`. Se anotan los totales y los fallos.
4. `./gradlew :app:lintDebug`: se anotan los errores y los avisos.
5. **Bytes del QR (D14).** Se escribe `RecipeSharePayloadStabilityTest` con el mapa vacío y una línea `println` por
   fixture con el hash calculado; se ejecuta sobre el código sin tocar; se copian los valores al mapa y se vuelve a
   ejecutar en verde. El test no imprime nada en su forma final.
6. **Informativo.** En `emulator-5556`, con `adb shell wm density 480` (1080 px de ancho son 360 dp) y
   `adb shell settings put system font_scale 1.3`: abrir una receta en el editor y capturar el selector de tres tipos
   (discrepancia 7). Después, `adb shell wm density reset` y `font_scale 1.0`.

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/share/RecipeSharePayloadStabilityTest.kt` (nuevo) | `` `the payloads of the recipes without actions keep their bytes` `` (D14). |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.share.RecipeSharePayloadStabilityTest"`.

**Criterio de salida:** las cifras de la línea base en la cabecera del plan, y el test de bytes en verde sobre el código
de `a0a71b9`.

Commit sugerido: `test(recipes): freeze the QR payload bytes of the current recipes`.

### F1. Tiempo total: cálculo

**Objetivo:** el tiempo total de una receta, de una fila de la lista y del editor en vivo, sin interfaz nueva; y el
recuento de la fila con todos los pasos (D3, P4).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `recipe/Recipe.kt` | `Recipe.totalDurationMs` (D2). |
| `recipe/RecipeRepository.kt` | `RecipeListItem.totalDurationMs` y su cálculo en `list()`; `stageCount = with.stages.size` y su KDoc (D3). |
| `ui/recipes/RecipeEditForm.kt` | `fun totalDurationMs(): Long?` (D4). |
| `androidTest/.../ui/scale/TargetPickerTest.kt` | Los dos `RecipeListItem` con `totalDurationMs = 115_000` y `210_000`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/RecipeTest.kt` (nuevo) | `` `the total duration is the end of the last step, gaps and final wait included` `` (V60 clásica: 115 000; una receta con un hueco de 20 s entre pasos y una espera final de 30 s: el fin de esa espera). `` `the total duration does not depend on the order of the stages` ``. `` `a recipe without stages lasts zero` ``. `` `the total duration is not the end of the last pour` `` (frente a `TargetCurve.fromRecipe(...).endMs`). |
| `test/.../ui/recipes/RecipeEditFormTest.kt` | `` `the live total time is the end of the last step, gaps included` `` (un paso 0:00 + 10 s → 10 000; añadir un paso que empieza en 0:30 con 20 s → 50 000; cambiar una duración desplaza el resto y el total). `` `the live total time is null while a start or a duration cannot be read` `` (inicio "1:7", inicio vacío, duración "abc" y duración vacía; al corregirlo, vuelve). |
| `androidTest/.../recipe/RecipeDaoTest.kt` (`emulator-5556`) | `typeAndInstructionAreSavedAndRead`: `stageCount` 4 (antes 2, "solo los vertidos") y `totalDurationMs` 65 000. `saveWithStagesAndRead`: `stageCount` sigue en 2 (dos vertidos) y `totalDurationMs` 65 000. |
| `androidTest/.../ui/recipes/RecipesScreenTest.kt` (`emulator-5556`) | `theTwoBuiltInsShowTheirNamesSummaryAndTag`: `recipe_stage_count` con 5 (antes 3), todavía con cuatro argumentos; el comentario pasa a "5 pasos". |
| `androidTest/.../ui/recipes/RecipeEditScreenTest.kt` (`emulator-5556`) | `saveAsRecipeFromABrewFillsOneStagePerPourAndWarnsAboutAMissingDose`: `saved.stageCount` 5 (antes 3; tres vertidos y dos esperas). |

**Verificación:** compilar (incluye `compileDebugAndroidTestKotlin`, por `TargetPickerTest`).
`grep -rn "stageCount" app/src/main` solo encuentra `RecipeRepository.kt`, `RecipeRow.kt`, `RecipeFormatters.kt` y los
`stageCount` locales de los códecs y de `stepStates`, que no son el de la fila.
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.RecipeTest" --tests "dev.josueamaya.pourline.ui.recipes.RecipeEditFormTest"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.recipe.RecipeDaoTest,dev.josueamaya.pourline.ui.recipes.RecipesScreenTest,dev.josueamaya.pourline.ui.recipes.RecipeEditScreenTest,dev.josueamaya.pourline.ui.scale.TargetPickerTest`.

Commit sugerido: `feat(recipes): compute the total time of a recipe and count every step`.

### F2. Tiempo total: lista, editor, tarjeta e importación

**Objetivo:** el tiempo total en la fila (lista de Recetas y selector de objetivo), la línea de totales, la tarjeta
para compartir y la pantalla de importar (US-2, US-3 y parte de US-4).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/components/AntIcons.kt` | `AntIcons.Clock` (D8). |
| `ui/recipes/RecipeFormatters.kt` | `recipeSummary(doseG, totalWaterG, stageCount, time: String)` (D6). |
| `ui/recipes/RecipeRow.kt` | El resumen con el tiempo y su `contentDescription` (D6 y D7). KDoc: "15 g · 250 g · 1:16,7 · 5 pasos · 1:55". |
| `ui/recipes/RecipeEditScreen.kt` | `Totals` con las cuatro formas de D5. |
| `share/RecipeShareCard.kt` | El icono de reloj y `share_recipe_duration` en `RecipeFacts` (D8). KDoc de `RecipeShareCard`. |
| `ui/recipes/RecipeImportScreen.kt` | `RecipeSummary` con el `FlowRow` y `recipe_import_duration` (D9). |
| `ui/session/SessionFormat.kt` | Solo el KDoc de `formatClock`: también es el formato del tiempo total de una receta. |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `recipe_summary` (cambia), `recipe_edit_totals_time`, `recipe_edit_totals_water_time` y `recipe_total_time_description` (§3.1). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../ui/session/SessionFormatTest.kt` | `` `the clock formats recipe totals with minutes over 59` `` (`210_000` → "3:30", `3_600_000` → "60:00" y `3_720_000` → "62:00", §3.8 y §4). |
| `androidTest/.../ui/recipes/RecipesScreenTest.kt` | `theTwoBuiltInsShowTheirNamesSummaryAndTag` con el tiempo ("1:55"; comentario "15 g · 250 g · 1:16,7 · 5 pasos · 1:55"). `theRowsShowTheirTotalTime` (la 4:6 de fábrica: "9 pasos" y "3:10"; la fila tiene la descripción "Total time 3:10"). |
| `androidTest/.../ui/scale/TargetPickerTest.kt` | `aRecipeRowShowsItsTotalTime` ("1:55" y "3:30" en las dos filas). |
| `androidTest/.../ui/recipes/RecipeEditScreenTest.kt` | `liveTotalsAndAPreviewAppearWithAValidRecipe` con `recipe_edit_totals_time` y "0:10". `theTotalTimeFollowsTheStepsAndHidesWhileAStepCannotBeRead` (cambiar la duración a 25 → "0:25"; añadir un paso → "0:35"; vaciar el inicio del segundo paso → la línea es `recipe_edit_totals`, sin tiempo; volver a escribirlo → vuelve; el `contentDescription` dice `recipe_total_time_description`). |
| `androidTest/.../share/ShareCardsLayoutTest.kt` (`emulator-5556`) | `theTotalTimeIsOnTheFactsLineAndTheCardKeepsItsSize` (`recipe(4)`: `textOf("share_recipe_duration")` es `formatClock(recipe.totalDurationMs)`, la misma `y` que `share_recipe_ratio` y 1080 × 1350). `aShortRecipeCardKeepsTheMinimum1080x1350WithTheQrBelowTheSteps` y `theTallestRecipeCardShowsEveryStepAndTheQr`, sin cambios. |
| `androidTest/.../ui/recipes/RecipeImportScreenTest.kt` (`emulator-5560`) | `showsTheTotalTimeNextToTheWaterAndTheRatio` (`recipe_import_duration` con el tiempo y su descripción). `theStartTimesAreNotCutWithALargeFontScale`, sin cambios. |

**Verificación:** compilar. `grep -rn "recipe_summary\|recipeSummary(" app/src/main` solo encuentra `RecipeFormatters.kt`,
`RecipeRow.kt` y las cadenas.
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.ui.session.SessionFormatTest" --tests "dev.josueamaya.pourline.ui.recipes.RecipeEditFormTest"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipesScreenTest,dev.josueamaya.pourline.ui.scale.TargetPickerTest,dev.josueamaya.pourline.ui.recipes.RecipeEditScreenTest,dev.josueamaya.pourline.share.ShareCardsLayoutTest,dev.josueamaya.pourline.ui.tour.RecipesHistoryTourTest`.
`ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipeImportScreenTest`.
`./gradlew :app:lintDebug`.

Commit sugerido: `feat(recipes): show the total recipe time in the list, the editor, the card and the import preview`.

### F3. Tipo Acción: modelo, presentación y QR

**Objetivo:** `StageType.ACTION` con su etiqueta, su icono, su color, su línea en la sesión sin báscula y su código
en el QR. Todo lo que necesita el enum para compilar va en esta fase (D10).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `recipe/Recipe.kt` | `ACTION` al final del enum y los KDoc de D10. |
| `recipe/RecipeValidator.kt` | Solo el KDoc de `DurationOutOfRange` (D10). |
| `ui/theme/AntColors.kt` | `AntSeedColors.magenta` y `AntColors.magenta` (D11). |
| `ui/components/AntIcons.kt` | `AntIcons.Action` (D12). |
| `ui/session/SessionFormat.kt` | `ACTION` en `stepTypeLabel`, `stepTypeIcon` y `stepTypeColors`, y el KDoc (D10 y D11). |
| `ui/session/ScalelessPanels.kt` | `ACTION` en `stageAction` y su KDoc (D10). |
| `recipe/share/RecipeSharePayload.kt` | `ACTION` ↔ `3` en `encodePayload` y `parsePayload` (D13). |
| `recipe/share/RecipeShareBinary.kt` | Solo el KDoc de `S_TYPE_MASK` (D13). |
| `brew/TargetCurve.kt`, `ui/recipes/RecipeEditForm.kt` | Solo KDoc (D10). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `step_type_action`, junto a `step_type_wait`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/RecipeTestFixtures.kt` | `aeroPressWithActions()` (D20). |
| `test/.../recipe/RecipeTest.kt` | `` `an action takes the water of the previous pour` `` (`withDerivedWater`, también con una Acción antes del primer vertido: 0). `` `an action adds no water and does not change the ratio` `` (`totalWaterG` 200 y `ratio` 200/15 en `aeroPressWithActions()`). `` `the total duration of the aeropress is two minutes` ``. |
| `test/.../recipe/RecipeValidatorTest.kt` | `` `an action lasts from 1 to 120 s` `` (1 s y 120 s válidas; 0 s y 121 s dan `DurationOutOfRange(i)`). `` `a recipe with only actions, or with actions and waits, has no pour` `` (`NoPour`). `` `an action without instruction is valid` ``. `` `an action that starts before the previous step ends overlaps` `` (`StartOverlaps`). |
| `test/.../ui/recipes/RecipeEditFormTest.kt` | `` `changing a step to an action empties the water and back to a pour proposes the last pour plus 50 g` ``. `` `an action round trips with its instruction and without water` `` (`from` → `toRecipe`). |
| `test/.../ui/session/SessionFormatTest.kt` | `` `each step type has its own label and icon` `` (cuatro iconos distintos, `stepTypeLabel(ACTION) == R.string.step_type_action` y `stepTypeIcon(ACTION) == AntIcons.Action`). `` `each step type has its own color and none is a status color` `` (en `antLightColors()` y `antDarkColors()`: `ACTION` es `magenta`; los cuatro roles son distintos y ninguno es `success`, `warning` ni `error`). |
| `test/.../ui/theme/AntColorsContrastTest.kt` | `magenta` en `roles()` (D11). |
| `test/.../recipe/share/RecipeShareCodecTest.kt` | `` `a recipe with actions round trips` `` (`aeroPressWithActions()`). `` `type code 3 is an action` `` (un `POUR` de 40 g y un paso con `sflags` = 3 se decodifican con `ACTION` y 40 g derivados; una receta con una sola Acción es `Invalid`). `` `an action writes no water` `` (los bytes de `encodePayload` de un `POUR` de 0-10 s con 40 g y una `ACTION` de 10-15 s con "Pon la tapa" coinciden con los escritos a mano con `ByteWriter`, sin `uvar` de agua tras la Acción). `` `an action instruction is trimmed like any other` `` (con el tope justo por debajo de la longitud sin notas, se quita la instrucción del último paso que la tiene, y la Acción se decodifica sin instrucción). `` `an unknown method code is corrupt` `` (antes `… or stage type …`, ver §1.2). |
| `test/.../recipe/share/RecipeSharePayloadStabilityTest.kt` | Sin cambios; tiene que seguir en verde. |
| `test/.../brew/StageAlertTrackerTest.kt` | `` `an action alerts like any other step` ``. |
| `test/.../brew/TargetCurveTest.kt` | `` `an action is a plateau and carries its type and instruction` ``. |
| `test/.../history/HistoryConvertersTest.kt` | `` `stage types round trip by name and an unknown one is read as a pour` `` (los cuatro valores, y `"OTRO"` → `POUR`). |

**Verificación:** compilar.
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.RecipeTest" --tests "dev.josueamaya.pourline.recipe.RecipeValidatorTest" --tests "dev.josueamaya.pourline.recipe.StageGapsTest" --tests "dev.josueamaya.pourline.recipe.share.*" --tests "dev.josueamaya.pourline.ui.recipes.RecipeEditFormTest" --tests "dev.josueamaya.pourline.ui.session.SessionFormatTest" --tests "dev.josueamaya.pourline.ui.theme.AntColorsContrastTest" --tests "dev.josueamaya.pourline.brew.StageAlertTrackerTest" --tests "dev.josueamaya.pourline.brew.TargetCurveTest" --tests "dev.josueamaya.pourline.history.HistoryConvertersTest"`.
`grep -rn "import android" app/src/main/kotlin/dev/josueamaya/pourline/recipe/share` no encuentra nada.
`git diff develop -- app/schemas` está vacío.
`./gradlew :app:lintDebug`.
Sin instrumentados: el comportamiento de los tipos existentes no cambia, y la Acción se prueba en pantalla en F4 a F6.

Commit sugerido: `feat(recipes): add the action step type with its color, icon and QR code`.

### F4. Editor: selector, aviso y recorrido

**Objetivo:** elegir Acción en el editor (US-1), con el selector de D15, el aviso de D17 y el texto del recorrido.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/components/AntSegmented.kt` | `iconOnly` (D16). |
| `ui/components/AntComponentsPreview.kt` | Un ejemplo con `iconOnly = true`. |
| `ui/recipes/RecipeEditScreen.kt` | La primera fila de `StageCard` (D15) y el aviso bajo la instrucción (D17). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `recipe_action_instruction_hint` y `tour_recipe_edit_stage_body` (D18). |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/recipes/RecipeEditScreenTest.kt` (`emulator-5556`) | `chooseType` busca con `hasContentDescription` (§1.2). `theTypeSelectorOffersActionAndSavesAnActionWithItsInstruction` (añadir un paso, elegir Acción: desaparece `recipe_stage_water_1` y `recipe_stage_type_label_1` dice "Action"; escribir "Pon la tapa"; guardar; el repositorio devuelve `ACTION` con esa instrucción y el agua del vertido anterior). `anActionWithoutInstructionShowsTheHintAndStillSaves` (`recipe_stage_action_hint_1` con el texto de la cadena; guardar habilitado y la receta se guarda; al escribir, el aviso desaparece; en un `POUR` sin instrucción no hay aviso). `theTypeSelectorShowsTheChosenTypeAndFourOptionsAt360dpWithALargeFont` (`Box(Modifier.width(360.dp))` y `Density(density.density, fontScale = 1.3f)`, como `RecipeImportScreenTest.theStartTimesAreNotCutWithALargeFontScale`; para cada tipo: elegirlo, `recipe_stage_type_label_0` dice su etiqueta en una línea y sin `hasVisualOverflow`; hay cuatro opciones con su `contentDescription`, de 48 dp o más, dentro de los límites de `recipe_stage_0`). `inBasicModeThereIsNoCurvePreviewButTheStepsAreEditable` y `choosingWaitHidesTheWaterAndChoosingPourBringsItBackWithAProposal`, sin más cambio que la ayuda. |
| `androidTest/.../ui/tour/RecipesHistoryTourTest.kt` (`emulator-5556`) | Sin cambios: las anclas del recorrido no cambian. |

**Verificación:** compilar.
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.ui.recipes.RecipeEditFormTest"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipeEditScreenTest,dev.josueamaya.pourline.ui.tour.RecipesHistoryTourTest`.
`./gradlew :app:lintDebug`.

**Criterio de salida:** todo en verde, y en el informe los anchos que mide el test de 360 dp (la etiqueta más ancha
en es y en, a 1,3), para confirmar la tabla de D15.

Commit sugerido: `feat(recipes): pick the action type in the recipe editor`.

### F5. Sesión con y sin báscula, aviso de paso y notificación

**Objetivo:** comprobar que la sesión muestra la Acción con su instrucción (o "Acción"), su icono y su color, que
avisa al empezar el paso como en los otros y que la notificación la nombra sin agua (§3.7). No se espera código nuevo;
si un test lo encuentra, se corrige aquí.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `androidTest/.../recipe/TestRecipes.kt` (nuevo) | `aeroPressWithActions()` y `actionFirstRecipe(instruction)` (D20). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../session/BrewKeepAliveTest.kt` | `` `a scaleless notice of an action step has its type and no water` `` (con la curva de `aeroPressWithActions()` en el paso 3: `type == ACTION`, `targetWaterG == null`, `number == 3` y `count == 6`). |
| `test/.../session/ScaleSessionManagerScalelessTest.kt` | `` `an action step alerts at its start like the other steps` `` (como `startWithV60()`, con `aeroPressWithActions()` en `FakeTargetReferences`: avisos a los 10, 20, 25, 85 y 90 s, y el de los 20 s es de una `ACTION`). |
| `androidTest/.../ui/session/SessionScreenTest.kt` (`emulator-5556`, báscula simulada) | `show(recipePart)` admite otra receta. `anActionStepShowsItsTypeAndInstructionWithTheScale` (`actionFirstRecipe("Pon la tapa")` guardada; `reachPourStep()`, `prep_start_timer` y `session_timer`; `session_current_step` contiene la etiqueta de la Acción, `session_instruction` dice "Pon la tapa", no hay `session_step_up_to` y la lista tiene una fila cuya descripción contiene la etiqueta). `anActionWithoutInstructionShowsItsTypeWithTheScale` (`actionFirstRecipe(null)`: la etiqueta y ningún `session_instruction`). La Acción dura 30 s, así que las comprobaciones caben de sobra antes del siguiente paso. |
| `androidTest/.../ui/session/ScalelessSessionScreenTest.kt` (`emulator-5556`, reloj de prueba) | `show(recipePart)` admite otra receta. `anActionStepShowsItsInstructionOrItsType` (`aeroPressWithActions()`: `at(16_000)` → `session_next_step` contiene la etiqueta de la Acción; `at(21_000)` → `scaleless_action` dice "Pon la tapa", `scaleless_step_progress` es `(3, 6, "0:04")` y no hay `session_step_up_to`; `at(95_000)` → `scaleless_action` dice la etiqueta de la Acción). |

**Verificación:**
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.session.BrewKeepAliveTest" --tests "dev.josueamaya.pourline.session.ScaleSessionManagerScalelessTest"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.session.SessionScreenTest,dev.josueamaya.pourline.ui.session.ScalelessSessionScreenTest`.

El color (magenta en el aro, la lista, la tarjeta del paso y la cuenta atrás) y el icono de mano no se pueden leer en
la semántica: los cubren `SessionFormatTest` (F3) y las capturas del §7.6.

Commit sugerido: `test(session): cover action steps with and without a scale`.

### F6. Tarjeta, QR e importación con Acción

**Objetivo:** compartir e importar una receta con Acciones, con su tiempo total en la tarjeta y en la vista previa
(US-4).

**Archivos:** solo tests, salvo que alguno encuentre un fallo. `androidTest/.../recipe/TestRecipes.kt` es el de F5.

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../share/ShareCardsLayoutTest.kt` (`emulator-5556`) | `aRecipeCardWithActionsKeepsTheMinimumSizeAndShowsTheTotalTime` (`aeroPressWithActions()` con QR: 1080 × 1350, `stepsAbove("share_recipe_qr_strip", 6)`, `brandInside()`, las filas 2, 4 y 5 contienen la etiqueta de la Acción y `textOf("share_recipe_duration") == "2:00"`). |
| `androidTest/.../ui/recipes/RecipeImportScreenTest.kt` (`emulator-5560`) | `showsActionStepsWithTheirTypeAndInstructionAndSavesThem` (URI de `aeroPressWithActions()`: `recipe_import_step_2` contiene la etiqueta de la Acción, `recipe_import_step_instruction_2` dice "Pon la tapa", el paso 5 no tiene instrucción y `recipe_import_duration` dice "2:00"; al guardar, la receta tiene las tres `ACTION` con sus instrucciones). `aTrimmedActionShowsItsTypeAndThePartialWarning` (URI con recorte parcial generado con el códec, como `showsThePartialInstructionsWarning`, con instrucciones largas en las Acciones: el aviso dice `recipe_trimmed_some_instructions` y la última Acción se muestra sin instrucción, §4). |
| `androidTest/.../ui/recipes/RecipeShareTest.kt` (`emulator-5560`) | `aRecipeWithActionsSharesAQrThatKeepsTheActions` (`aeroPressWithActions()` guardada; compartir sin diálogo; el QR del PNG, leído con ML Kit por `FileProvider`, se decodifica con los tipos `[POUR, SWIRL, ACTION, WAIT, ACTION, ACTION]`, "Pon la tapa", "Invierte" y la última sin instrucción). |

**Verificación:**
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.share.ShareCardsLayoutTest`.
`ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipeImportScreenTest,dev.josueamaya.pourline.ui.recipes.RecipeShareTest`.

Commit sugerido: `test(recipes): cover action steps in the share card, the QR and the import preview`.

### F7. Verificación y cierre

**Objetivo:** el §7 completo: las clases afectadas, lint, las comprobaciones, el E2E por adb, las capturas y la
prueba del usuario.

**Archivos:** este plan (cabecera, columna Estado y un "Resultado de F7" con las cifras). El spec no se toca; los
cambios que conviene llevarle están en el §8.

Commit sugerido: `docs(specs): record the SPEC-010 verification`.

### 3.1 Cadenas nuevas o cambiadas (`values` / `values-en`)

| Clave | Español | Inglés | Fase |
|---|---|---|---|
| `recipe_summary` (cambia) | `%1$s g · %2$s g · %3$s · %4$s · %5$s` | `%1$s g · %2$s g · %3$s · %4$s · %5$s` | F2 |
| `recipe_edit_totals_time` | Agua total %1$s g · Ratio 1:%2$s · %3$s | Total water %1$s g · Ratio 1:%2$s · %3$s | F2 |
| `recipe_edit_totals_water_time` | Agua total %1$s g · %2$s | Total water %1$s g · %2$s | F2 |
| `recipe_total_time_description` | Duración total %1$s | Total time %1$s | F2 |
| `step_type_action` | Acción | Action | F3 |
| `recipe_action_instruction_hint` | Describe la acción, por ejemplo «Pon la tapa» | Describe the action, for example \"Put the cap on\" | F4 |
| `tour_recipe_edit_stage_body` (cambia) | Cada paso es un vertido, un removido, una espera o una acción, con su tiempo. | Each step is a pour, a swirl, a wait or an action, with its timing. | F4 |

No se borra ninguna cadena: `recipe_edit_totals` y `recipe_edit_totals_water` se siguen usando cuando no hay tiempo
(D5). En `values-en`, las comillas van escapadas (`\"`).

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| `RecipeValidatorTest`: Acción de 1 s y de 120 s válidas; de 0 s y de 121 s, `DurationOutOfRange`; solo Acciones, `NoPour`; sin instrucción, válida | F3 | `RecipeValidatorTest` (`an action lasts from 1 to 120 s`, `a recipe with only actions…`, `an action without instruction is valid`) |
| `RecipeShareCodecTest`: ida y vuelta con Acción; el `3` es `ACTION`; la Acción no escribe agua; los fixtures sin Acción dan los mismos bytes | F0, F3 | `RecipeShareCodecTest` (`a recipe with actions round trips`, `type code 3 is an action`, `an action writes no water`); `RecipeSharePayloadStabilityTest` (congelado en F0) y `the classic V60 payload matches the golden bytes` |
| `RecipeEditFormTest`: cambiar a Acción vacía el agua; el tiempo en vivo es correcto y `null` con un paso ilegible | F1, F3 | `RecipeEditFormTest` (`the live total time…` ×2 en F1; `changing a step to an action…` en F3) |
| `withDerivedWater` y `Recipe.totalDurationMs`: el agua de la Acción es la del vertido anterior; el total es el fin del último paso con huecos y espera final | F1, F3 | `RecipeTest` |
| `SessionFormatTest`: la etiqueta, el icono y el color de `ACTION` | F3 | `SessionFormatTest` (`each step type has its own label and icon`, `…own color and none is a status color`) |
| Instrumentado: el editor ofrece Acción, guarda una Acción con instrucción y muestra el tiempo total en vivo | F2, F4 | `RecipeEditScreenTest` (`theTypeSelectorOffersActionAndSavesAnActionWithItsInstruction`, `theTotalTimeFollowsTheStepsAndHidesWhileAStepCannotBeRead`, `theTypeSelectorShowsTheChosenTypeAndFourOptionsAt360dpWithALargeFont`) |
| Instrumentado: la sesión, con y sin báscula, muestra la instrucción de la Acción o "Acción" | F5 | `SessionScreenTest` (`anActionStep…WithTheScale` ×2) y `ScalelessSessionScreenTest.anActionStepShowsItsInstructionOrItsType` |
| Instrumentado: `RecipeRow` muestra el tiempo total; `RecipeImportScreen` y `RecipeShareCard` muestran el tiempo total y los pasos Acción | F2, F6 | `RecipesScreenTest`, `TargetPickerTest.aRecipeRowShowsItsTotalTime`, `RecipeImportScreenTest` (`showsTheTotalTime…`, `showsActionSteps…`), `ShareCardsLayoutTest` (`theTotalTimeIsOnTheFactsLine…`, `aRecipeCardWithActions…`) |
| `ShareCardsLayoutTest` sigue cabiendo con Acciones y el tiempo total | F2, F6 | `ShareCardsLayoutTest.aRecipeCardWithActionsKeepsTheMinimumSizeAndShowsTheTotalTime` y los de tamaño existentes |
| Ejecución solo de los tests relacionados, filtrados por clase | Todas | Cada fase lista sus clases (`--tests` y `-Pandroid.testInstrumentationRunnerArguments.class=`); §7.1. Nunca la batería completa (D21) |
| Compila `:app` y pasa el lint en los archivos tocados | Todas, F7 | Cada fase compila solo `:app`; `:app:lintDebug` en F0, F2, F3, F4 y F7 |
| Prueba manual en el móvil del usuario: Acción "Pon la tapa", con y sin báscula, y compartir e importar por QR | F7 (usuario) | §7.5 |

Puntos del §3 sin criterio propio en el §5, también cubiertos:

| Punto | Fase | Prueba |
|---|---|---|
| §3.2: sin migración ni cambio de esquema | F3, F4 | `git diff develop -- app/schemas` vacío; `HistoryConvertersTest`; el editor guarda y relee la Acción con Room en memoria (`RecipeEditScreenTest`) |
| §3.5: el recorte trata la instrucción de una Acción como cualquier otra | F3, F6 | `RecipeShareCodecTest.an action instruction is trimmed like any other`; `RecipeImportScreenTest.aTrimmedActionShowsItsTypeAndThePartialWarning` |
| §3.6: aviso no bloqueante con una Acción sin instrucción | F4 | `RecipeEditScreenTest.anActionWithoutInstructionShowsTheHintAndStillSaves` |
| §3.7: mismo sonido y vibración al empezar el paso | F3, F5 | `StageAlertTrackerTest.an action alerts like any other step`; `ScaleSessionManagerScalelessTest.an action step alerts…`; `AndroidStageNotifier.stageStarted` no mira el tipo |
| §3.7: la notificación muestra "Acción" sin agua | F5 | `BrewKeepAliveTest.a scaleless notice of an action step…`; §7.4, paso 5 |
| §3.8: 3 600 000 ms → "60:00" | F2 | `SessionFormatTest.the clock formats recipe totals with minutes over 59` |
| Recuento de la fila: todos los pasos, de cualquier tipo (P4, decisión del usuario) | F1, F2 | `RecipeDaoTest.typeAndInstructionAreSavedAndRead` (4 pasos con un remover y una espera); `RecipesScreenTest` ("5 pasos" en la V60 clásica y "9 pasos" en la 4:6); `RecipeEditScreenTest.saveAsRecipeFromABrew…` (5) |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | Algún `when` exhaustivo sobre `StageType` queda fuera de F3 y rompe la compilación a mitad. | Los cinco salen de un `grep` (D10). F3 compila los tres conjuntos de fuentes antes de cualquier test. |
| R2 | Una app sin este cambio rechaza el QR con Acción como "No se pudo leer la receta del QR". | Riesgo aceptado por el spec (§3.5, discrepancia 2). Los QR sin Acción no cambian (D14). La prueba con dos móviles se hace con la app actualizada en los dos (§7.5). |
| R3 | Un selector solo con iconos se entiende peor que uno con texto. | La etiqueta del tipo elegido junto al selector (D15), la descripción para TalkBack (D16), el aviso que nombra la acción (D17) y el recorrido (D18). La lista de pasos, la sesión, la tarjeta y la importación muestran siempre icono y texto juntos. P1 deja elegir otra forma. |
| R4 | El magenta se confunde con el rojo de error o no contrasta en oscuro. | `AntColorsContrastTest` con `magenta` (D11) y las capturas en claro y en oscuro del §7.6. El rojo de error no se usa en la sesión para los pasos. |
| R5 | La mano no se lee a 12 dp en la tarjeta. | Cuatro trazos como mucho (D12). Se revisa en el PNG de la tarjeta a tamaño real (§7.6) y se simplifica si se empasta. |
| R6 | Cambiar `recipe_summary` o el significado de `stageCount` rompe otro uso. | Solo `RecipeRow` usa la cadena y lee `stageCount` (`grep` en F1 y F2). `RecipesScreenTest` comprueba la fila con cinco argumentos y el recuento nuevo, y `RecipeDaoTest` el recuento del repositorio. |
| R7 | El tiempo hace crecer la tarjeta para compartir. | El icono de 12 dp va en la línea de 16 sp de los datos (D8). `ShareCardsLayoutTest` exige 1080 × 1350 con y sin Acciones. |
| R8 | Un test de sesión con báscula en tiempo real llega tarde al paso. | `actionFirstRecipe` empieza con la Acción y la mantiene 30 s (D20). La sesión sin báscula usa el reloj de prueba. |
| R9 | `iconOnly` cambia otros segmentados. | El valor por defecto es `false` y conserva el código actual (D16). F4 ejecuta `RecipeEditScreenTest` y el recorrido; las otras pantallas no pasan el parámetro. |
| R10 | Al desactualizar la app (por ejemplo, instalando un APK anterior encima), Room lee `"ACTION"` como `POUR`. | `stringToStageType` ya cae en `POUR` ante un nombre desconocido: la receta se abre, aunque con el tipo cambiado. Solo pasa al volver a una versión anterior, y queda fuera del alcance. |
| R11 | Los emuladores fallan de forma intermitente en tests de interfaz largos. | Las clases de cada fase se repiten solas antes de darlas por rotas, y se comparan con la línea base de F0, como en `plan-qr-grande.md`. |

## 6. Preguntas para el usuario

Resueltas por el usuario al aprobar el plan, el 2026-11-18. No queda ninguna abierta.

1. **P1. Selector de tipo (D15, §6 del spec).** Aceptada la propuesta: a la izquierda, el nombre del tipo elegido; a
   la derecha, cuatro opciones solo con icono de 48 dp, cada una con su descripción para TalkBack.
2. **P2. Tiempo en la pantalla de importar (D9).** Aceptada la propuesta: con el mismo icono de reloj que la tarjeta,
   para no confundir "1:55" con el ratio "1:16,7".
3. **P3. Texto del recorrido (D18).** Aceptada la propuesta: `tour_recipe_edit_stage_body` nombra los cuatro tipos.
4. **P4. Recuento de la fila (discrepancia 5).** Cambia respecto a la propuesta: la fila cuenta todos los pasos
   (verter, remover, esperar y Acción), no solo los vertidos. La V60 clásica pasa a "5 pasos", la 4:6 a "9 pasos" y la
   AeroPress de prueba a "6 pasos". Se hace en F1 (D3).

## 7. Verificación final

### 7.1 Clases afectadas

Solo las clases relacionadas con el spec, nunca la batería completa (D21). Una ejecución cada vez por emulador.

```
./gradlew :app:testDebugUnitTest \
  --tests "dev.josueamaya.pourline.recipe.RecipeTest" \
  --tests "dev.josueamaya.pourline.recipe.RecipeValidatorTest" \
  --tests "dev.josueamaya.pourline.recipe.StageGapsTest" \
  --tests "dev.josueamaya.pourline.recipe.share.*" \
  --tests "dev.josueamaya.pourline.ui.recipes.RecipeEditFormTest" \
  --tests "dev.josueamaya.pourline.ui.session.SessionFormatTest" \
  --tests "dev.josueamaya.pourline.ui.theme.AntColorsContrastTest" \
  --tests "dev.josueamaya.pourline.brew.StageAlertTrackerTest" \
  --tests "dev.josueamaya.pourline.brew.TargetCurveTest" \
  --tests "dev.josueamaya.pourline.history.HistoryConvertersTest" \
  --tests "dev.josueamaya.pourline.session.BrewKeepAliveTest" \
  --tests "dev.josueamaya.pourline.session.ScaleSessionManagerScalelessTest"

ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.recipe.RecipeDaoTest,dev.josueamaya.pourline.ui.recipes.RecipeEditScreenTest,dev.josueamaya.pourline.ui.recipes.RecipesScreenTest,dev.josueamaya.pourline.ui.scale.TargetPickerTest,dev.josueamaya.pourline.share.ShareCardsLayoutTest,dev.josueamaya.pourline.ui.tour.RecipesHistoryTourTest,dev.josueamaya.pourline.ui.session.SessionScreenTest,dev.josueamaya.pourline.ui.session.ScalelessSessionScreenTest

ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.recipes.RecipeImportScreenTest,dev.josueamaya.pourline.ui.recipes.RecipeShareTest

./gradlew :app:lintDebug
```

Se compara con la línea base de F0: los totales solo suben por los tests nuevos, las expectativas que cambian son las
de la tabla del §1.2, y lint no tiene errores ni avisos nuevos.

### 7.2 Comprobaciones

- `grep -rn "StageType.SWIRL, StageType.WAIT ->" app/src/main` no encuentra nada (la rama de `stageAction` incluye
  `ACTION`).
- `git diff develop -- app/src/main/kotlin/dev/josueamaya/pourline/recipe/share/RecipeShareBinary.kt` solo muestra el
  KDoc de `S_TYPE_MASK`. `the classic V60 payload matches the golden bytes` y `RecipeSharePayloadStabilityTest` pasan.
- `git diff develop -- app/schemas app/src/main/AndroidManifest.xml` está vacío.
- `grep -rn "import android" app/src/main/kotlin/dev/josueamaya/pourline/recipe/share` no encuentra nada.
- `grep -rn "iconOnly" app/src/main` solo encuentra `AntSegmented.kt`, `RecipeEditScreen.kt` y `AntComponentsPreview.kt`.

### 7.3 Rendimiento

El spec no pide medidas. Lo único nuevo en un camino frecuente es un `maxOfOrNull` por receta en
`RecipeRepository.list()` y otro por recomposición del editor, sobre 20 pasos como mucho. La sesión no cambia de
lógica. `SessionPerformanceTest` no se ejecuta (D21).

### 7.4 E2E por adb (sin ARTEMIS)

Todo en `emulator-5560`, porque importar desde la galería necesita el modelo de Play Services.

**Antes de empezar:**

1. `adb devices -l`. El teléfono del usuario y `emulator-5554` no se tocan.
2. `ANDROID_SERIAL=emulator-5560 ./gradlew :app:installDebug` y `adb -s emulator-5560 shell pm clear dev.josueamaya.pourline`.
3. `adb -s emulator-5560 shell pm grant dev.josueamaya.pourline android.permission.POST_NOTIFICATIONS`.
4. El URI de `aeroPressWithActions()`: `RecipeShareCodecTest.a recipe with actions round trips` lo imprime con
   `println`, y queda en `app/build/test-results/testDebugUnitTest/TEST-dev.josueamaya.pourline.recipe.share.RecipeShareCodecTest.xml`.

**Herramientas:** `uiautomator dump`, `input tap`, `input text` (con `%s` para los espacios) y `screencap`. El PNG
compartido se saca con `adb -s emulator-5560 exec-out run-as dev.josueamaya.pourline cat cache/shared/recipe-<id>.png > <archivo>.png`.
`adb -s emulator-5560 logcat -b crash -d` tiene que quedar vacío en todo el recorrido.

| Paso | Recorrido | Comprobaciones |
|---|---|---|
| 1 | Onboarding → Recetas → nueva receta "Prueba tapa", 15 g, paso 1 vertido 0:00, 200 g, 10 s → Añadir paso → icono de mano → instrucción "Pon la tapa" | La etiqueta junto al selector dice "Action", desaparece "Up to", y el aviso aparece antes de escribir y desaparece después. La línea de totales dice "Total water 200 g · Ratio 1:13.3 · 0:20" y cambia al tocar la duración. Captura. |
| 2 | Guardar | La fila dice "15 g · 200 g · 1:13.3 · 2 steps · 0:20", y las predefinidas "5 steps · 1:55" y "9 steps · 3:10". Captura. |
| 3 | `adb -s emulator-5560 shell "am start -a android.intent.action.VIEW -d '<URI de la AeroPress>'"` → Guardar | La vista previa muestra los seis pasos (tres con la mano y "Action", "Pon la tapa" e "Invierte" debajo) y el reloj con "2:00". La fila dice "6 steps · 2:00". |
| 4 | Preparar → AeroPress → "Start without scale" → "Start timer" | Hacia los 15 s, la cuenta atrás "Next Action" en magenta. A los 20 s, "Pon la tapa" en el dial, el aro en magenta, la mano en la tarjeta del paso y en la lista, y el sonido de cambio de paso. Hacia los 90 s, "Action" en el dial. Capturas. |
| 5 | Durante el paso 4, a los 22 s: `adb -s emulator-5560 shell dumpsys notification --noredact` | La notificación dice "Step 3/6 · Action", sin agua. |
| 6 | Terminar y descartar. Conectar la báscula simulada y preparar "Prueba tapa" con báscula hasta "Start timer" | Hacia los 10 s, la tarjeta del paso dice "Action" con "Pon la tapa" debajo y sin "up to". Captura. |
| 7 | Editor de la AeroPress → Compartir; sacar el PNG | La línea de datos con el reloj y "2:00", y las filas de las Acciones con la mano y "Action". 1080 × 1350 (`sips -g pixelWidth -g pixelHeight`). |
| 8 | `adb push` del PNG a `/sdcard/Download/` y `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/<archivo>`; Recetas → Importar → "Desde una imagen" | La vista previa del paso 3. Al guardar, el nombre lleva " (2)". |

### 7.5 Prueba manual del usuario (Xiaomi y un segundo móvil)

La hace el usuario, con el APK de esta rama en los dos móviles. El agente no instala nada en su teléfono.

- Crear una receta de AeroPress con un paso Acción "Pon la tapa" y otro sin instrucción.
- Prepararla con la TIMEMORE DOT y sin báscula: la Acción se ve en magenta con la mano, con su instrucción o "Acción",
  y suena y vibra al empezar.
- Compartirla por WhatsApp e importarla en el otro móvil: llegan las Acciones y el tiempo total.
- Si tiene a mano un móvil con la versión anterior, comprobar que dice "No se pudo leer la receta del QR"
  (discrepancia 2).

### 7.6 Capturas

En `~/Downloads/pourline-step-action/{es,en}/{claro,oscuro}/`, fuera del repo. El idioma, con
`adb shell cmd locale set-app-locales dev.josueamaya.pourline --locales es-ES` (y `en-US` para volver). El tema, desde
Ajustes → Apariencia.

- La tarjeta del paso en el editor, con cada tipo elegido, también a 360 dp y con la letra a 1,3
  (`adb shell wm density 480` y `adb shell settings put system font_scale 1.3`; después, `wm density reset` y `1.0`).
- El aviso de una Acción sin instrucción y la línea de totales con el tiempo.
- La fila de la lista.
- La sesión sin báscula en una Acción (aro, tarjeta del paso y lista) y la cuenta atrás hacia una Acción; la sesión con
  báscula en una Acción.
- El PNG de la tarjeta y la vista previa de importar.
- **Qué revisar a ojo:** que la mano se lea a 12, 16 y 20 dp; que el magenta no se confunda con el rojo y contraste
  en oscuro; que ningún texto se corte a 360 dp con la letra a 1,3; que el reloj de la tarjeta quede alineado con el
  texto.

## 8. Seguimiento fuera de este plan

- **Cambios que conviene llevar al spec al cerrar.** No se tocan ahora.
  - §3.5: el mensaje de una app antigua es "No se pudo leer la receta del QR" (`Corrupt`), no "receta no válida"
    (discrepancia 2).
  - §3.4: en el editor, la importación y la tarjeta solo aparece el icono, sin color, como en los otros tipos
    (discrepancia 1).
  - §3.6: la forma final del selector (D15 y P1) y el icono de reloj de la importación (D9 y P2).
  - §3.8: la fila cuenta todos los pasos, de cualquier tipo (P4 y D3), y el ejemplo dice "pasos", no "etapas".
  - SPEC-009 §3.1: el código de tipo `3` es `ACTION` (discrepancia 6).
- **Fuera del alcance (§1 del spec):** una receta predefinida de AeroPress, el avance manual, un sonido propio para la
  Acción, un QR `v=2` y más tipos específicos.
- **Antes de fusionar en `develop`:** revisar la rama con `/code-review`.
