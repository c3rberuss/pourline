# SPEC-010: Paso "Acción" y tiempo total de la receta

| Campo | Valor |
|---|---|
| Estado | Aprobado (2026-11-18). Plan aprobado: [`plan-paso-accion.md`](plan-paso-accion.md). Pendiente de implementación |
| Fecha | 2026-11-18 |
| Rama | `feat/step-action` |
| Deriva de | Uso real: recetas de AeroPress con pasos como "Pon la tapa", y recetas sin duración visible |
| Módulos afectados | `:app`: `recipe`, `recipe/share`, `share`, `session`, `ui/recipes`, `ui/session`, `ui/components`, `ui/theme` |
| Specs relacionados | [SPEC-007](spec-sin-bascula.md), [SPEC-009](spec-compartir-receta.md) |

## 1. Resumen y Contexto

- **Problema:**
  - Una receta solo admite tres tipos de paso (`StageType { POUR, SWIRL, WAIT }`, `recipe/Recipe.kt:33`).
    Pasos como "Pon la tapa", "Invierte" o "Presiona" de una AeroPress no son ninguno de ellos. Hoy hay que
    escribirlos como `WAIT` con instrucción, y la sesión los muestra con la etiqueta, el icono y el color de
    "Esperar", lo que confunde.
  - Las recetas no muestran cuánto dura la preparación. Ni la fila de la lista, ni el editor, ni la tarjeta para
    compartir, ni la pantalla de importar. El recorrido guiado ya dice que los totales del editor incluyen "la
    duración de la receta" (`tour_recipe_edit_totals_body`), pero no se muestra.
- **Solución:**
  - Un cuarto tipo de paso genérico, `StageType.ACTION` ("Acción"), con color magenta e icono de mano propios. La
    instrucción del paso describe qué hacer.
  - El tiempo total de la receta (fin del último paso), en formato `m:ss`, en la lista de recetas, el editor, la
    tarjeta para compartir y la pantalla de importar.
- **Éxito:**
  - Se puede crear una receta de AeroPress con un paso Acción "Pon la tapa". La sesión, con y sin báscula, lo muestra
    con su instrucción, su color y su icono.
  - Cada receta muestra su duración total donde aparecen el agua y el ratio.
  - Las recetas existentes y los QR existentes se comportan igual que antes.
- **Alcance (In):**
  - `StageType.ACTION` en el modelo, Room, validador, editor, sesión (con y sin báscula), notificación, QR, tarjeta
    para compartir y pantalla de importar.
  - Tiempo total en `RecipeRow` (lista de Recetas y selector de objetivo de Scale), la línea de totales del editor,
    la tarjeta para compartir y la pantalla de importar.
  - Textos en `values` (español) y `values-en`.
- **Alcance (Out):**
  - Una receta predefinida de AeroPress.
  - Pasos de avance manual (la sesión espera un "Hecho"): la Acción avanza al acabar su tiempo, como el resto.
  - Un sonido o una vibración propios de la Acción.
  - Un cambio de versión del QR (sigue `v=1`, ver §3.5).
  - Más tipos específicos (tapa, invertir, presionar…).

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como usuario, quiero añadir a una receta un paso de tipo Acción con la instrucción "Pon la tapa" para que
  la sesión me diga qué hacer en ese momento sin hacerlo pasar por una espera.
- **US-2:** Como usuario, quiero ver la duración total de cada receta en la lista para elegir una según el tiempo que
  tengo.
- **US-3:** Como usuario, quiero ver la duración total mientras edito los pasos para ajustar la receta a un tiempo
  objetivo.
- **US-4:** Como usuario, quiero compartir e importar por QR una receta con pasos Acción, viendo su duración total en
  la tarjeta y en la pantalla de importar.

## 3. Especificación Técnica

### 3.1 Modelo

- `enum class StageType { POUR, SWIRL, WAIT, ACTION }` en `recipe/Recipe.kt`, con `ACTION` al final.
- `ACTION` no añade agua: `withDerivedWater()` le da el acumulado del `POUR` anterior, como a `SWIRL` y `WAIT`. No
  cambia `Recipe.totalWaterG` ni `Recipe.ratio`.
- `Recipe` gana `val totalDurationMs: Long`: el `endMs` mayor de sus pasos (`stages.maxOfOrNull { it.endMs } ?: 0`).
  Como los pasos no se solapan y van ordenados por `startMs`, coincide con el `endMs` del último paso. Incluye la
  espera o el drawdown final. No se confunde con `TargetCurve.endMs`, que termina en el último vertido y no cambia.
- `RecipeListItem` (`recipe/RecipeRepository.kt:19`) gana `val totalDurationMs: Long`, calculado en el repositorio a
  partir de los pasos que ya carga (no hace falta cambiar la consulta del DAO).

### 3.2 Persistencia

- Room guarda el tipo por nombre (`RecipeStageEntity.type`, `RecipeEntities.kt:71`). `"ACTION"` es un valor nuevo de
  la misma columna: no hay migración ni cambio de versión de la base de datos.
- El historial no cambia: `TargetCurveCodec` solo escribe las etapas `POUR` (D21 de SPEC-003), y
  `HistoryConverters.stringToStageType` ya cae en `POUR` ante un nombre desconocido.

### 3.3 Validación (`RecipeValidator`)

- `ACTION` dura entre `MIN_DURATION_MS` (1 s) y `MAX_DURATION_MS` (120 s), como `POUR` y `SWIRL`. Solo `WAIT` llega
  a `MAX_WAIT_MS`.
- La instrucción es opcional, como en el resto de tipos (hasta 200 caracteres). Una receta importada con las
  instrucciones recortadas (SPEC-009) sigue siendo válida.
- Sigue haciendo falta al menos un `POUR` (`RecipeError.NoPour`): una receta solo con Acciones no es válida.
- No hay más reglas nuevas: solapes, orden y número máximo de pasos se validan igual.

### 3.4 Presentación del tipo

- `stepTypeLabel(ACTION)` es `R.string.step_type_action`: "Acción" / "Action".
- `stepTypeIcon(ACTION)` es un `AntIcons.Action` nuevo, una mano o un gesto de toque, dibujado con el mismo estilo
  y grosor que `Pour`, `Swirl` y `Wait` (`ui/components/AntIcons.kt`).
- `stepTypeColors(ACTION)` es un rol nuevo `magenta` en `AntColors`, a partir del magenta de Ant Design `#EB2F96`,
  con su variante oscura en el tema de noche como `purple` y `cyan`. Se actualiza el KDoc de `stepTypeColors`:
  ningún tipo usa verde, naranja ni rojo.
- El color aparece donde ya lo usan los otros tipos, solo en la sesión: el aro sin báscula, la tarjeta del paso, la
  lista de pasos (`StepList`) y la cuenta atrás de 5 s.
- El icono aparece además, sin color, donde ya aparecen los de los otros tipos: el selector del editor, la pantalla
  de importar y la tarjeta para compartir.

### 3.5 QR (`recipe/share/RecipeSharePayload.kt`)

- `ACTION` se codifica con el código de tipo `3` en los 2 bits de `S_TYPE_MASK`, y `3` se decodifica como `ACTION`.
  Ya no queda ningún código de tipo libre.
- Como `SWIRL` y `WAIT`, `ACTION` no escribe agua.
- La versión sigue en `v=1` (`RecipeShareCodec.VERSION`). **Riesgo aceptado:** una app sin este cambio rechaza un QR
  con Acción como corrupto (`MalformedPayload("tipo de paso desconocido")` → `Corrupt` → "No se pudo leer la
  receta del QR"), no como "versión más nueva". Se aparta de la regla de SPEC-009 §4 ("un código nuevo exige `v=2`"),
  por decisión del usuario.
  Los QR sin Acción no cambian.
- El recorte para que quepa en el QR (SPEC-009 y su enmienda 1) trata la instrucción de una Acción como cualquier otra.

### 3.6 Editor (`ui/recipes`)

- El selector de tipo (`AntSegmented`, `RecipeEditScreen.kt:337`) gana una cuarta opción, Acción. Con cuatro
  etiquetas no cabe a 360 dp ("Remover" ya se corta hoy con la letra a 1,3), así que la primera fila de cada paso
  pasa a ser: a la izquierda, el nombre del tipo elegido; a la derecha, las cuatro opciones solo con icono, de 48 dp
  cada una y con descripción para TalkBack. `AntSegmented` gana `iconOnly: Boolean = false`; sus otros usos no
  cambian (D15 del plan).
- `RecipeEditForm.setStageType(i, ACTION)` vacía el agua, como al cambiar a `SWIRL` o `WAIT`.
- Con un paso Acción sin instrucción, el editor muestra un aviso no bloqueante bajo el campo de instrucción
  ("Describe la acción, por ejemplo «Pon la tapa»"). No impide guardar.
- Línea de totales (`recipe_edit_totals`, `RecipeEditScreen.kt:427`): añade el tiempo total, por ejemplo
  "Agua total 250 g · Ratio 1:16,7 · 3:30". Se recalcula en vivo con cada cambio. Solo aparece cuando todos los
  pasos tienen inicio y duración que se pueden leer como número. Si no, se muestra el resto de la línea sin el tiempo.

### 3.7 Sesión (`ui/session`, `session`)

- Con báscula y sin báscula, un paso `ACTION` se comporta como `SWIRL` y `WAIT`: muestra su instrucción o, si no la
  tiene, "Acción" (`ScalelessPanels.kt:193` y equivalentes). No hay objetivo de agua y la meta sigue en el acumulado.
- La transición al paso Acción usa el mismo sonido (`TONE_PROP_ACK`) y la misma doble vibración que el resto.
- La notificación de `BrewService`/`BrewKeepAlive` muestra el tipo "Acción" y, como en los otros pasos que no son
  vertidos, sin agua.
- El paso avanza al terminar su duración. No hay avance manual.

### 3.8 Tiempo total en lista, tarjeta e importación

- Formato `m:ss` con `formatClock` (`ui/session/SessionFormat.kt:16`): `210_000` → `"3:30"`, `3_600_000` → `"60:00"`.
- `RecipeRow`: `recipeSummary` gana el tiempo como último elemento, "15 g · 250 g · 1:16,7 · 6 pasos · 3:30". El
  recuento "N pasos" cuenta todos los pasos (vertidos, remover, esperar y acción), no solo los vertidos como hasta
  ahora (`RecipeRepository.list`). La
  fila no trunca el resumen: si no cabe, pasa a una segunda línea. Así se ve igual en la lista de Recetas y en el
  selector de objetivo de Scale.
- `RecipeShareCard`: un elemento más junto al agua y el ratio, con icono de reloj y `testTag("share_recipe_duration")`.
  La tarjeta mantiene su tamaño (ver `ShareCardsLayoutTest`).
- `RecipeImportScreen`: el tiempo, junto al agua y el ratio (`RecipeImportScreen.kt:158`), con el mismo icono de
  reloj que la tarjeta para que "1:55" no se confunda con el ratio (D9 del plan).

### 3.9 Textos nuevos

| Clave | Español | Inglés |
|---|---|---|
| `step_type_action` | Acción | Action |
| `recipe_action_instruction_hint` | Describe la acción, por ejemplo «Pon la tapa» | Describe the action, for example "Put the cap on" |
| Formato del resumen y de los totales con tiempo | Según §3.6 y §3.8 | Equivalente |
| Descripción accesible del tiempo total | Duración total {m:ss} | Total time {m:ss} |

### 3.10 Flujo

1. El usuario edita una receta, añade un paso, elige Acción y escribe "Pon la tapa". Ve el tiempo total en la línea
   de totales.
2. Guarda. La fila de la lista muestra "… · 3:30".
3. La prepara (con o sin báscula). Al llegar al paso, la sesión muestra "Pon la tapa" en magenta con el icono de mano.
4. La comparte por QR. Otro móvil con la app actualizada la importa con su paso Acción y su tiempo total.

## 4. Casos Límite y Manejo de Errores

- Acción sin instrucción → se muestra "Acción". El editor muestra un aviso, pero guarda.
- Acción de 0 s o de más de 120 s → `RecipeError.DurationOutOfRange` en ese paso, como en un `SWIRL`.
- Receta solo con Acciones, o con Acciones y esperas → `RecipeError.NoPour`.
- Cambiar un paso con agua a Acción → el agua se vacía. Volver a Verter propone un agua, como hoy.
- QR con Acción leído por una app sin este cambio → "No se pudo leer la receta del QR" (riesgo aceptado, §3.5).
- QR con Acción e instrucciones recortadas → se importa como Acción sin instrucción y muestra "Acción", con el aviso
  de recorte parcial de SPEC-009.
- Pasos con huecos entre ellos → el tiempo total incluye los huecos, porque es el fin del último paso.
- Receta con una duración de 60 minutos o más → `formatClock` da minutos de más de dos cifras ("62:00"), sin horas.
- Editor con un paso a medio rellenar → la línea de totales se muestra sin el tiempo hasta que se pueda calcular.
- Recetas existentes → no tienen Acciones y su comportamiento no cambia, salvo que ahora muestran su tiempo total.

## 5. Criterios de Aceptación (Definition of Done)

- [ ] Tests unitarios (`app/src/test`):
  - [ ] `RecipeValidatorTest`: una Acción de 1 s y una de 120 s son válidas. Una de 0 s y una de 121 s dan
        `DurationOutOfRange`. Una receta solo con Acciones da `NoPour`. Una Acción sin instrucción es válida.
  - [ ] `RecipeShareCodecTest`: una receta con Acción hace el viaje de ida y vuelta sin cambios. El código de
        tipo `3` se decodifica como `ACTION`. Una Acción no escribe agua. Los fixtures sin Acción producen los
        mismos bytes que antes.
  - [ ] `RecipeEditFormTest`: al cambiar a Acción se vacía el agua. El tiempo total en vivo es correcto y es `null`
        con un paso que no se puede leer.
  - [ ] `withDerivedWater` y `Recipe.totalDurationMs`: el agua de una Acción es la del vertido anterior, y el tiempo
        total es el fin del último paso, incluidos los huecos y la espera final.
  - [ ] `SessionFormatTest`: la etiqueta, el icono y el color de `ACTION`.
- [ ] Tests instrumentados (`app/src/androidTest`):
  - [ ] El editor ofrece Acción, guarda una Acción con instrucción y muestra el tiempo total en vivo.
  - [ ] La sesión, con y sin báscula, muestra la instrucción de la Acción, o "Acción" si no la tiene.
  - [ ] `RecipeRow` muestra el tiempo total y el recuento de todos los pasos (una receta con 1 vertido y 5 pasos más
        dice "6 pasos"). `RecipeImportScreen` y `RecipeShareCard` muestran el tiempo total y
        los pasos Acción.
  - [ ] `ShareCardsLayoutTest` sigue cabiendo con una receta con Acciones y el tiempo total.
- [ ] **Ejecución de tests:** solo los tests relacionados con este spec (los anteriores y los que toquen los
      archivos modificados), filtrados por clase (`--tests` en los unitarios y
      `-Pandroid.testInstrumentationRunnerArguments.class=...` en los instrumentados). No se ejecuta la batería
      completa de la app.
- [ ] Compila el módulo `:app` y pasa el lint/format del proyecto en los archivos tocados.
- [ ] Verificación manual en el móvil físico del usuario: crear una receta con una Acción "Pon la tapa", prepararla
      con y sin báscula, y compartirla e importarla por QR.

## 6. Preguntas abiertas

Ninguna. Las dos del borrador se resolvieron en el plan: el selector de tipo (§3.6, D15) y el dibujo del icono de
mano (D12: 24×24, trazo de 1,75 con extremos redondeados, como los demás `AntIcons`).

## 7. Alternativas descartadas

- **Varios tipos específicos (tapa, invertir, presionar):** hacen crecer el enum, las traducciones y el QR, y en el QR
  solo quedaba un código libre.
- **Reutilizar `WAIT` con la instrucción como etiqueta:** mezcla esperar con hacer algo y mantiene el color y el icono
  de espera.
- **Avance manual de la Acción:** cambia el modelo de tiempo de la sesión y de la curva objetivo.
- **`v=2` en el QR solo con Acciones:** daría a las apps antiguas un mensaje mejor ("versión más nueva"). El usuario
  prefirió mantener `v=1`.
- **Tiempo total hasta el último vertido (`TargetCurve.endMs`):** no cuenta la espera final, así que no es lo que dura
  la preparación.
