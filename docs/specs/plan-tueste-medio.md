# Plan de implementación: SPEC-012 Enmienda 1, tueste medio e instrucciones claras

| Campo | Valor |
|---|---|
| Spec | [`spec-catalogo-recetas.md`](spec-catalogo-recetas.md) (SPEC-012), **Enmienda 1** (§3.1, §3.8, §3.9, §3.10, §5 y §7), y la sección "Enmienda 1 (2026-11-26): tueste medio" del [anexo](anexo-catalogo-recetas.md), que manda sobre el resto del anexo. El spec manda sobre los dos |
| Estado | Implementado (F0 a F6, 2026-11-26) y verificado en `emulator-5556`. Probado por el usuario en su teléfono y fusionado a `develop` el 2026-11-26 |
| Fecha | 2026-11-26 |
| Rama | `feat/recipe-catalog`, con el SPEC-012 implementado (F0 a F11 del plan anterior, hasta `712e9f1`) y la enmienda commiteada (`97060db`). Sin fusionar |
| Módulos | `:app` (`:protocol` no cambia). En la raíz: `catalog/recipes.json` y `catalog/README.md`. `.github/workflows/catalog.yml` no cambia |
| Plan anterior | [`plan-catalogo-recetas.md`](plan-catalogo-recetas.md) (D1 a D49, R1 a R13). Este plan sigue desde D50; riesgos y preguntas empiezan de nuevo |
| Catálogo | `schemaVersion` 1, sin cambios (`roasts` es opcional). `revision` 1 → 2, una sola vez (D61). 35 → 34 recetas |
| Base de datos y copia de seguridad | Sin cambios: `HistoryDatabase` sigue en 8 y `FORMAT_VERSION` en 2. El tueste no llega a `Recipe` (§7 del spec) |
| Línea base | La de F11 del plan anterior (2026-11-26): JVM 671 tests en 55 clases, 0 fallos; instrumentados en `emulator-5556` 519 tests, fallan solo los 2 de `SessionPerformanceTest`; `:app:lintDebug` 0 errores y 20 avisos. F0 repite solo las clases afectadas |

La enmienda toca tres cosas y el plan las ordena de dentro afuera:

1. **Lectura y filtro (F1).** El campo `roasts` en el DTO, el modelo y la fila; el filtro por tueste en código puro y en
   `CatalogBrowser`. Todo JVM. El catálogo publicado todavía no tiene `roasts`, así que todas sus recetas cuentan como
   "cualquier tueste" y nada cambia para el usuario.
2. **El test del repo (F2) y el contenido (F3).** F2 escribe las reglas nuevas del test (valores de `roasts`, proporción,
   términos prohibidos y tiempos `m:ss`) con sus tests sobre catálogos sintéticos, y activa las de tueste, que el
   catálogo de hoy ya cumple. F3 rehace `catalog/recipes.json` (34 recetas, `roasts`, la variante media de la 4:6, las 14
   nuevas, todas las instrucciones reescritas, `revision` 2), activa las reglas de redacción y actualiza
   `catalog/README.md`.
3. **La interfaz (F4 y F5).** El filtro "Tueste", las etiquetas de la fila y "Tueste: …" en el detalle (F4), y el mismo
   filtro en el `TargetPicker` (F5).

F0 mide la línea base y guarda un APK con el catálogo actual para la prueba de actualización; F6 es la verificación.

Al recorrer el código y el contenido salieron estas cosas, que el spec no dice y que condicionan el plan:

- **Un ratio de las notas parece un tiempo.** La regla de modelado del §3.10 manda dar el ratio final en las notas, y
  `hoffmann_iced_immersion`, que se queda, dice "el ratio final con hielo es 1:13". Una búsqueda de `m:ss` lo rechaza
  igual que "termina hacia 1:13". El test necesita distinguirlos (D59; P1, aceptada: un ratio va siempre tras la palabra
  "ratio").
- **En español, `SWIRL` se llama "Remover".** `step_type_swirl` vale "Remover" (`values/strings.xml:260`) y el recorrido
  del editor dice "un removido" (`tour_recipe_edit_stage_body`). La regla 4 llama "girar el gotero" al gesto que se
  modela como `SWIRL` y "remover con cuchara" al que es `ACTION`. La etiqueta y el recorrido no cambian (P2, rechazada),
  así que el tipo no basta para saber qué se gira: un `SWIRL` nunca deja la instrucción vacía y dice "Gira el gotero…"
  (D64).
- **`CatalogBrowser` ya combina cinco flujos** (`catalog.state`, `recipes.catalogKeys()`, el idioma, la búsqueda y el
  método) y el `combine` con tipos llega hasta cinco. Los tres filtros pasan a un solo estado (D53).
- **`CatalogBrowser` no se puede probar en la JVM**: recibe `RecipeRepository`, que necesita Room. El §5 pide tests JVM de
  "Quitar filtros". Pasa a recibir el flujo de claves guardadas (D53).
- **Las recetas que salen ya están resueltas en la app, sin código nuevo.** Las copias de "Mis recetas" conservan su
  `catalogKey` y nada las busca en el catálogo (solo `catalogKeys()` para la etiqueta "Guardada", que no aparece porque
  la fila ya no existe). Una referencia activa cuya clave falta se resuelve como `Resolution.Removed`, borra la referencia
  y avisa `target_error_removed` (`ScaleSessionManager.onResolution`, cubierto por `ScaleSessionManagerTargetTest.a
  catalog key that disappears is removed and reports TargetRemoved`). Un detalle abierto pasa a `Missing`
  (`CatalogRecipeDetailScreenTest.aRecipeRemovedFromTheCatalogShowsTheMissingState`). Las preparaciones con `CATALOG`
  conservan nombre y curva congelados, y nada navega del historial al catálogo. `RecipeCatalogRepository.entry(key)` no
  emite hasta leer la copia local (D15), así que la referencia no se pierde por leer tarde. F6 lo comprueba de punta a
  punta con una actualización real (D67).
- **Ningún test depende de las 15 recetas que salen**: `grep` de sus claves en `app/src`, `.github` y `README.md` no
  encuentra nada; solo están en `catalog/recipes.json`. `RecipeCatalogFileTest` exige `four_six` y `v60_classic`, que
  siguen. Los tests de interfaz usan `TestRecipeCatalog`, no el catálogo real.
- **Con `revision` empatada gana la caché** (D12). Si el contenido cambiara en dos fases con la misma `revision`, un
  dispositivo que descargó la versión intermedia de la rama se quedaría con ella. Todo el contenido va en una fase (D61).
- **`recipeJson` de `CatalogTestJson.kt` llama "Bloom" en español al primer paso.** Una receta de prueba añadida al
  catálogo del repo tropezaría con la regla nueva. F2 cambia ese nombre por defecto.
- **`catalog/README.md` está desfasado con la enmienda**: dice que `MOKA` no cabe en el modelo de pasos (entra
  `coava_moka`) y que "las notas" marcan las duraciones inferidas (el §3.10 dice que las marca el anexo). D66.
- **El `TargetPicker` es un `ModalBottomSheet` sin desplazamiento** (`AntDrawer`) y `AntSegmented` tiene un alto mínimo
  de 48 dp (`minimumInteractiveComponentSize`). El filtro añade una fila al cajón (R4).
- **La inactividad termina la preparación con báscula** a los 60 s sin verter (`BrewSettings.DEFAULT_SECONDS`,
  `BrewSession.onTick`). Afecta ya a las recetas de inmersión y afectará a `coava_moka`, cuyo único vertido es el
  llenado de la base. Es comportamiento previo, fuera de la enmienda (R8).
- **El teléfono del usuario no se conecta por adb.** La prueba en el teléfono es manual, con un APK firmado con la misma
  clave que el que tiene (`1.0.0-alpha.3`, versionCode 1000003) (D68).

No hay detekt ni ktlint en el proyecto. Como en los planes anteriores, el control es `:app:lintDebug`, y el estilo es
el del código que rodea a cada cambio (dos espacios, KDoc en español).

## 1. Punto de partida

### 1.1 Se reutiliza

| Pieza | Uso en la enmienda |
|---|---|
| `RoastLevel { LIGHT, MEDIUM, DARK }` (`coffee/CoffeeModels.kt`) | Valores de `roasts` (§3.1). No se crea otro enum. `CoffeeModels.kt` solo importa `java.time`, así que `recipe/catalog` sigue sin `import android`. |
| `RoastLevel.label()` (`ui/coffees/CoffeeLabels.kt`) con `coffee_roast_light`, `coffee_roast_medium` y `coffee_roast_dark` ("Claro", "Medio", "Oscuro" / "Light", "Medium", "Dark") | Opciones del filtro, etiquetas de la fila y "Tueste: …" del detalle. |
| `catalog_filter_all` ("Todos" / "All") y `catalog_clear_filters` | La opción "Todos" del filtro de tueste y "Quitar filtros". |
| `fold` (`coffee/CoffeeSearch.kt`) | Comparar los términos prohibidos sin mayúsculas ni acentos (D58). |
| `RecipeCatalogParser`, `CatalogJson`, `RecipeCatalogRecipeDto`, `toCatalogRecipe`, `CatalogRecipe`, `CatalogRecipeEntry` | Lectura de `roasts` (D50, D51). |
| `CatalogListItem`, `toListItem`, `filterCatalog`, `catalogMethods`, `catalogSearchKey` (`recipe/catalog/CatalogFilter.kt`) | Filtro por tueste (D52). |
| `CatalogBrowser`, `CatalogListUi` (`ui/recipes/catalog/CatalogBrowser.kt`) | Estado de los filtros, compartido por la pestaña Recetas y el selector (D53). |
| `CatalogSearchBar`, `CatalogMethodFilter`, `CatalogNoticeBar`, `CatalogRecipeRow`, `CatalogRecipeList` (`ui/recipes/catalog/CatalogRecipeList.kt`) | Se añade el filtro de tueste y las etiquetas (D54, D55). |
| `AntSegmented` con `itemModifier` (`ui/components/AntSegmented.kt`), `AntTag`/`AntTagColor`, `AntText`, `AntSize` (`ui/theme/AntDimens.kt`) | El control del filtro y las etiquetas. |
| `CatalogRecipeDetailScreen` y `CatalogDetailUi.Ready.recipe` | "Tueste: …" (D56). |
| `TargetPicker` y `CatalogPickerContent` (`ui/scale/TargetPicker.kt`), `PrepareScreen` | El filtro en el selector (F5). |
| `RecipeCatalogCheck.kt` (`catalogProblems`), `RecipeCatalogFileTest`, `CatalogTestJson.kt` (`localized`, `stageJson`, `recipeJson`, `catalogJson`, `parsed`, `testGrinders`), `catalogRecipe` (`CatalogGrindTest.kt`) | El test del repo y sus ayudantes. |
| `RecipeCatalogRepository` con `CatalogSource { … }`, `CatalogCache` y un `CatalogFetcher` falso, como en `RecipeCatalogRepositoryTest` | El test JVM de `CatalogBrowser` (D53). |
| `TestRecipeCatalog` y `FakeCatalogFetcher` (`androidTest/.../recipe/catalog/`), `TestAppDependencies` | Los tests instrumentados (F4, F5). |
| `.github/workflows/catalog.yml` | Sin cambios: ya ejecuta `RecipeCatalogFileTest` en cada `push` y `pull_request` que toca `catalog/**`. |
| `-Ppourline.catalogUrl`, `-Ppourline.versionCode` y `-Ppourline.versionName` (`app/build.gradle.kts`) | El APK de la prueba de actualización (D67) y el del teléfono (D68). |

### 1.2 Falta o cambia

| Pieza | Situación |
|---|---|
| `RecipeCatalogRecipeDto`, `CatalogRecipe`, `CatalogListItem` | Sin `roasts`. |
| `filterCatalog`, `CatalogBrowser`, `CatalogListUi` | Sin filtro por tueste; `CatalogListUi.filtered` solo mira búsqueda y método. |
| `CatalogRecipeRow` | Etiquetas solo "Guardada" y "Activa" (la `FlowRow` solo aparece con una de las dos). |
| `CatalogRecipeDetailScreen` | Sin tueste. |
| `TargetPicker` | Sin `onCatalogRoast`. |
| `catalogProblems` | No mira `roasts`, la proporción, los términos ni los tiempos. |
| `catalog/recipes.json` | 35 recetas de la lista original, `revision` 1, sin `roasts`, la 4:6 a 93 °C, con jerga y tiempos repetidos en 153 instrucciones de las 20 que se quedan. |
| `catalog/README.md` | Sin `roasts`, sin reglas de redacción, `MOKA` como "no cabe", "las notas lo dicen". |
| `CatalogTestJson.recipeJson` | Primer paso llamado "Bloom" en español. |
| `TestRecipeCatalog.json` | Sin `roasts`. |

### 1.3 Discrepancias entre el spec y el código, o dentro del spec

| # | Spec | Problema | Ajuste que propone el plan |
|---|---|---|---|
| 1 | §3.9: ninguna nota contiene tiempos `m:ss`. §3.10 (reglas de modelado): las notas dan el ratio final. | "ratio final con hielo es 1:13" (`hoffmann_iced_immersion`, se queda) tiene la forma `m:ss`. | Un ratio precedido de la palabra "ratio" no cuenta como tiempo, y un ratio con decimales nunca tiene la forma `m:ss` (D59). P1, aceptada; el §3.9 del spec ya lo dice. |
| 2 | §3.10, regla 4: girar el gotero es `SWIRL`; remover con cuchara es `ACTION`. | La etiqueta española de `SWIRL` es "Remover" y el recorrido dice "un removido". | Se quedan como están (P2, rechazada). Un `SWIRL` siempre dice qué se gira, con "Gira el gotero…" (D64). |
| 3 | §3.10, regla 5: si el paso no necesita más que su tipo, la instrucción queda vacía. | Con la etiqueta "Remover", el tipo no dice qué se gira: el gotero, la jarra, la Chemex o la prensa. | Un `SWIRL` nunca queda vacío (D64). |
| 4 | Anexo, tabla 3 y reparto: `peets_pour_over` como "Otro: Gotero cónico", V60 7 y Otro 8. | El spec (decisión del usuario) dice `V60`, V60 8 y Otro 7. | Manda el spec: `V60`, sin `methodOther`, y las notas dicen que vale para cualquier gotero cónico (D62). La tabla 3 y el reparto del anexo ya están corregidos (2026-11-26); la ficha 2.14 sigue con la propuesta original, que la lista de decisiones del anexo anula. |
| 5 | Anexo 2.0: añadir a `hedrick_v60_easy` una nota para oscuros, "opcional". El spec solo dice "se queda". | Ninguno: las notas se reescriben igualmente. | Se añade (D63): es un consejo del propio autor, sin cifras inventadas. |
| 6 | §3.1: un valor desconocido de `roasts` se ignora. | No dice qué pasa con un `roasts` que no es una lista de textos (`"MEDIUM"`, `[1]`). Con un DTO `List<String>`, la receta se descartaría entera. | Se ignora igual, y la receta cuenta como "cualquier tueste"; el test del repo lo rechaza (D50). El §3.1 del spec ya lo dice (Enmienda 1). |
| 7 | §3.4: el modelo `CatalogRecipe` no lista `roasts`. | La enmienda lo necesita para filtrar y mostrar. | `CatalogRecipe.roasts` y `CatalogListItem.roasts`; no pasa a `Recipe` (D51). El §3.4 del spec ya lo lista (Enmienda 1). |
| 8 | §3.10 (reglas de modelado): "Duraciones inferidas: el anexo las marca". | `catalog/README.md` dice que "las notas lo dicen". | Se corrige el README (D66); las notas no tienen que decirlo. |

## 2. Decisiones que completan el spec

Marcadas como "(concreta)" si eligen entre opciones que el spec deja abiertas o delega al plan, y como "(amplía)" si
añaden algo que el spec no dice.

### 2.1 Lectura y filtro

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D50 | `roasts` en el DTO (concreta §3.1; discrepancia 6) | `RecipeCatalogRecipeDto` gana `val roasts: JsonElement? = null`. Nueva `internal fun roastsOf(element: JsonElement?): Set<RoastLevel>` en `CatalogConversion.kt`: si es un `JsonArray`, toma los elementos que son `JsonPrimitive` de texto con un nombre exacto de `RoastLevel` (`RoastLevel.entries.firstOrNull { it.name == value }`) y descarta el resto; los repetidos quedan una vez. Cualquier otra cosa (texto suelto, número, objeto, `null`) da el conjunto vacío. Nunca descarta la receta. | Es "un valor desconocido se ignora" del §3.1, llevado a cualquier forma rara del campo: un error en `roasts` no puede esconder una receta, ni en esta versión ni en las siguientes. El test del repo rechaza todos esos casos (D57), así que en el archivo publicado no ocurren. |
| D51 | Modelo (concreta §3.4; discrepancia 7) | `CatalogRecipe` gana al final `val roasts: Set<RoastLevel> = emptySet()` (vacío = cualquier tueste), igual en los dos idiomas: `toCatalogRecipe(language)` pone `roastsOf(roasts)`. `CatalogListItem` gana al final `val roasts: Set<RoastLevel> = emptySet()` y `toListItem()` lo copia. `toRecipe()` no cambia: el tueste no llega a `Recipe`, ni a la copia guardada ni a la preparación. | El §7 descarta el campo en `Recipe` (evita Room 8 → 9). Los valores por defecto mantienen compilando los constructores con nombre de los tests (`catalogRecipe`, `TargetPickerTest.catalogItem`). El orden de presentación lo da `RoastLevel.entries`, así que basta un `Set`. |
| D52 | Filtro puro (concreta §3.8) | En `CatalogFilter.kt`: `fun CatalogListItem.suitsRoast(roast: RoastLevel?): Boolean = roast == null \|\| roasts.isEmpty() \|\| roast in roasts`. `filterCatalog(items, query, method, roast: RoastLevel? = null)` añade esa condición a las dos que ya tiene. Nuevo `data class CatalogFilters(val query: String = "", val method: BrewMethod? = null, val roast: RoastLevel? = null) { val active: Boolean get() = query.isNotBlank() \|\| method != null \|\| roast != null }`; `CatalogFilters()` es "Todos" en todo. | Es la regla del §3.8: "Medio" muestra las de `MEDIUM` y las de cualquier tueste (igual con "Claro" y "Oscuro"). El parámetro con valor por defecto deja intactas las llamadas de `CatalogFilterTest` y `TargetPickerTest`. |
| D53 | `CatalogBrowser` (concreta §3.8; amplía) | Constructor `CatalogBrowser(catalog: RecipeCatalogRepository, savedKeys: Flow<Set<String>>, language: CatalogLanguageSource, scope: CoroutineScope)`: `RecipesViewModel` y `TargetPickerViewModel` pasan `recipeRepository.catalogKeys()`. Los filtros viven en `private val filters = MutableStateFlow(CatalogFilters())`; `ui` combina cuatro flujos (`catalog.state`, `savedKeys`, `currentLanguage`, `filters`). `setQuery`, `setMethod` y el nuevo `setRoast(value: RoastLevel?)` cambian su campo; `clearFilters()` pone `CatalogFilters()`. `CatalogListUi` gana `val roast: RoastLevel? = null`, y `filtered` pasa a `query.isNotBlank() \|\| method != null \|\| roast != null`. | Un solo estado evita el sexto flujo y hace que "Quitar filtros" limpie los tres a la vez. Método y tueste viven en el mismo objeto, así que tienen la misma persistencia, como pide el §3.8: en Recetas, la de la sesión de la app (`RecipesViewModel`); en el selector, la de la entrada de Scale (`TargetPickerViewModel`). Con el flujo de claves en lugar del repositorio, `CatalogBrowser` (Kotlin puro) se prueba en la JVM. |

### 2.2 Interfaz

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D54 | Control del filtro (concreta §3.8) | `@Composable fun CatalogRoastFilter(selected: RoastLevel?, onSelect: (RoastLevel?) -> Unit, modifier: Modifier = Modifier)` en `CatalogRecipeList.kt`: una `Row` con `AntText(catalog_roast_filter, type = Secondary)` y `AntSegmented<RoastLevel?>(Modifier.weight(1f).testTag("catalog_roast_filter"), size = AntSize.Small)` con las opciones `null` ("Todos", `catalog_filter_all`), `LIGHT`, `MEDIUM` y `DARK` (`RoastLevel.label()`). `itemModifier = { Modifier.testTag("catalog_roast_${it?.name?.lowercase() ?: "all"}") }`. Va bajo `CatalogMethodFilter`, antes del aviso, en `CatalogRecipeList` y en `CatalogPickerContent`. Igual en Básico y en Experto. | El §3.8 lo escribe como "Todos / Claro / Medio / Oscuro", la misma notación que "Mis recetas / Recomendadas", que es un `AntSegmented`. Son cuatro opciones cortas y fijas que se cambian con un toque. Un segundo `AntSelect` mostraría "Todos" dos veces sin decir de qué. La etiqueta "Tueste" da el contexto, y `AntSegmented` reparte el ancho que le queda en partes iguales. |
| D55 | Etiquetas de la fila (concreta §3.8) | En `CatalogRecipeRow`, la `FlowRow` aparece si `saved \|\| active \|\| item.roasts.isNotEmpty()`. Primero, un `AntTag(label, Modifier.testTag("catalog_roast_tag_<light\|medium\|dark>"), color = AntTagColor.Default)` por cada tueste, en el orden de `RoastLevel.entries`; después "Guardada" y "Activa" como hoy. Sin `roasts`, ninguna etiqueta de tueste. | Es el §3.8. El tueste describe la receta, como el método; "Guardada" y "Activa" son del usuario y van detrás. El color neutro no compite con el verde de "Guardada" ni con el azul de "Activa". La misma fila sirve al selector (§3.8: "mismas filas"). |
| D56 | "Tueste: …" en el detalle (concreta §3.8) | Bajo el contexto, `AntText(type = Secondary, Modifier.testTag("catalog_detail_roasts"))` con `catalog_detail_roasts` ("Tueste: %1$s") y las etiquetas en el orden de `RoastLevel.entries`, separadas por ", ", la primera tal cual y las demás en minúscula ("Tueste: Medio, oscuro", "Roast: Medium, dark"); sin `roasts`, `catalog_detail_roasts_any` ("Tueste: cualquiera"). La función `@Composable internal fun roastsSummary(roasts: Set<RoastLevel>): String` vive en `CatalogRecipeList.kt`. | Es el texto literal del §3.8. `lowercase()` sin `Locale` usa `Locale.ROOT`, que basta para "Claro", "Medio", "Oscuro" y sus equivalentes ingleses. |

### 2.3 Test del repo

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D57 | Reglas del contenido (concreta §3.9) | Archivo de test nuevo `test/.../recipe/catalog/CatalogContentRules.kt` con tres funciones que devuelven problemas en texto: `roastValueProblems(text: String)`, que lee la raíz con `CatalogJson` y, por cada receta del arreglo con `roasts`, exige un `JsonArray` de textos con nombres de `RoastLevel` y sin repetir (mensajes "recipe #i (key): roasts has the unknown value \"X\"", "… repeats \"MEDIUM\"" o "… must be a list of roast names"); `roastShareProblems(entries: List<CatalogRecipeEntry>)`, que cuenta como aptas para medio las de `roasts` vacío o con `MEDIUM` y como solo claro las de `roasts == setOf(LIGHT)`, y exige `aptas * 3 >= total * 2` y `soloClaro <= MAX_LIGHT_ONLY` (6) ("only N of M recipes suit a medium roast, at least K are needed", "N recipes are only for a light roast, at most 6"); y `wordingProblems(entries)`, con D58 y D59. `catalogProblems` las llama en la rama `Parsed`. | Es la lista del §3.9. Los valores de `roasts` se miran en el JSON crudo porque la app los ignora al leer (D50). La proporción y la redacción se miran sobre las recetas válidas: una receta descartada ya es un problema por sí misma. Los mensajes nombran la clave, el campo y el idioma, como los de hoy. |
| D58 | Términos prohibidos (concreta §3.10) | Sobre `fold(texto)`, solo en `stages[].name`, `stages[].instruction` y `notes` de cada idioma: en `es` y `en`, `\bdraw[\s-]*downs?\b`, `\brao[\s-]*spin\b` y `\bremoline\w*`; solo en `es`, `\bbloom\w*`. Mensaje: "recipe <key>: stages[i].instruction (es) uses the forbidden term \"drawdown\"". No se miran `name`, `context` ni `grind.text` de la receta. | Es la columna "Prohibido" del §3.10, aplicada "al idioma que corresponda". `remoline\w*` cubre "remolinea", "remolinear" y sus formas. Los campos son los que nombra el §3.9; mirar más sería ampliar el test. |
| D59 | Tiempos `m:ss` y ratios (concreta §3.9; discrepancia 1; P1, aceptada) | En los mismos campos, se quitan primero las menciones de ratio, `(?i)\bratio\b[^.;\d]{0,40}?\b1:\d+(?:[.,]\d+)?`, y después un tiempo es `(?<![\d:.,])\d{1,2}:\d{2}(?![\d:]\|[.,]\d)`. Así, "ratio final con hielo es 1:13", "the final ratio is 1:7.3" y "1:16,7" pasan; "Espera hasta 0:45", "termina hacia 3:30." y "1:13" sin "ratio" delante, no. Mensaje: "recipe <key>: notes (en) has the time \"3:30\"". Convención para el contenido y el README: un ratio entero en las notas va siempre tras la palabra "ratio". | Un ratio entero y un tiempo tienen la misma forma; solo el contexto los separa. "ratio" es la palabra que ya usan las notas en los dos idiomas y la de la app (`scale_ratio`). Los ratios con decimales, como los muestra la app ("1:%1$.1f"), nunca coinciden. |
| D60 | Cuándo se activa cada regla (amplía) | F2 activa `roastValueProblems` y `roastShareProblems` en `catalogProblems`: el catálogo de hoy no tiene `roasts`, así que todas sus recetas son aptas para medio y ninguna es solo claro, y el test del repo sigue en verde. `wordingProblems` se escribe y se prueba en F2, pero se activa en F3, con el contenido reescrito. | Cada fase deja el test del repo en verde. El workflow `Catalog` solo se ejecuta cuando cambia `catalog/**`, es decir, en F3, ya con todas las reglas. |

### 2.4 Contenido

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D61 | Una sola revisión (concreta §3.10) | Todo el contenido cambia en F3 y `revision` pasa de 1 a 2 una sola vez. Si hay que corregir algo del archivo después de subir F3 a `origin`, `revision` sube otra vez (regla del README). | Con empate gana la caché (D12): una copia intermedia con la misma `revision` se quedaría en los dispositivos que la descargaron de la rama. |
| D62 | Qué recetas y con qué datos (concreta §3.10) | Orden del archivo = la "Lista final" del §3.10 (#1 a #34). Salen las 15 claves de la lista "Salen". Las 20 que se quedan conservan sus datos (dosis, temperatura, molienda, fuente, autor, contexto, tipos, inicios, duraciones y agua) salvo `four_six` (`waterTempC` 88.0); ningún paso cambia de tipo (D64). Las 14 nuevas salen del anexo §2.1 a §2.14 (tabla de F3), con lo que el spec cambia: `peets_pour_over` con `method` `V60` y sin `methodOther`; `peets_chemex` con 50 g. Temperaturas en °F: 93.3 y 96.1 °C; las de Stumptown, 96. `hario_switch` sin `waterTempC` y sin una temperatura sugerida en las notas. `roasts` según la columna del §3.10 (ausente para "cualquiera"). | Es la lista final del spec. El §3.10 prohíbe cambiar por cuenta propia los parámetros de un autor, así que solo cambia lo que el spec nombra. |
| D63 | Notas nuevas (concreta §3.10) | `four_six`: el texto del anexo 2.0 (dulzor y acidez en el 40 %, fuerza en el 60 %, 88 °C para medio, 93 °C para claro y 83 °C para oscuro empezando con dos vertidos). `hoffmann_iced_immersion`: "Con tuestes oscuros, 4 min de infusión" (la app usa 5 min). `hedrick_v60_easy`: "Con tuestes más oscuros, el autor recomienda bajar primero la temperatura". `peets_pour_over`: vale para cualquier gotero cónico. Las nuevas con "Notes" en el anexo (`counter_culture_aeropress`, `stumptown_aeropress`, `hario_switch`, `coava_moka`, `origami_the_coffeeshop`) las llevan. `hario_switch` recibe además el "remueve si lo quieres más fuerte" que el anexo ponía en un `WAIT` (reglas 4 y 6). Todo en los dos idiomas. | Son las notas que piden el spec (§3.10, filas 1, 7 y 32) y el anexo. |
| D64 | Cómo se aplican las reglas de redacción (concreta §3.10; discrepancias 2 y 3) | A las 34 recetas, también a las 14 del anexo. (1) **`POUR`:** empieza por "Añade X g (total Y g)" / "Add X g (total Y g)", con X = `waterG` menos el del `POUR` anterior, y sigue con la técnica si la hay ("en círculos", "en el centro"); un prefijo ("AeroPress invertida: añade…") o un final ("… de agua caliente") son válidos. (2) **`WAIT`:** vacía, salvo que haya algo que hacer ("Pon la tapa.") o sea el filtrado final ("Deja que termine de filtrar." / "Let it finish draining."). (3) **`SWIRL`:** nunca vacía; dice qué se gira y cómo, con el vocabulario ("Gira el gotero suavemente", "Gira el gotero con energía", "Gira la jarra suavemente para enfriar"). (4) **Tipos:** la regla 4 tal cual: girar el gotero es `SWIRL` y remover con cuchara, paleta o palillos es `ACTION`. Las 20 que se quedan y las 14 del anexo ya la cumplen, así que ningún paso cambia de tipo. Los giros de otros recipientes conservan el tipo de su receta o del anexo (`SWIRL` en `merikanto_wac_2021` paso 9, `stumptown_chemex` paso 8 y `peets_french_press` paso 2; `ACTION` en `peets_chemex` paso 6 y `peets_pour_over` paso 5); solo cambia su texto ("Gira la jarra suavemente para enfriar", "Gira la Chemex suavemente y sirve"). (5) **Tiempos:** nunca `m:ss`, ni la duración del propio paso ("en 10 s"); un tiempo que la app no muestra (un rango de la fuente, la variante para oscuros) va en las notas con unidades ("4 min", "2 min 30 s"). (6) **Comentarios** ("El 40 % define dulzor y acidez", "Pausa."): a las notas si valen, si no se quitan. (7) **Ratios** en las notas: tras la palabra "ratio" (D59). (8) **"Bloom" en español:** "Preinfusión" en nombres, instrucciones y notas. El vertido que la fuente llama bloom se llama "Preinfusión" / "Bloom", como en las 14 nuevas del anexo (en `hoffmann_ultimate_v60`, "Bloom: 60 g…" pasa a ese nombre y "Añade 60 g (total 60 g)…"); los demás pasos conservan su nombre o su falta de nombre (`four_six` sigue sin nombres). (9) **Inglés:** las mismas reglas, imperativo natural y la columna inglesa del vocabulario. (10) Instrucciones de 200 caracteres como mucho por idioma. | Son las reglas 1 a 6 y el vocabulario del §3.10, en una forma que se puede comprobar paso a paso. El punto 3 evita un "Remover" sin más en un paso de girar (discrepancias 2 y 3). El punto 4 extiende la regla 4 (que habla del gotero) a cualquier recipiente, para que "girar" sea siempre el mismo tipo. |
| D65 | Comprobaciones del contenido con un script desechable (amplía) | Un script de Python (`python3 -I`) en el directorio temporal de la sesión, que no se commitea, compara el `catalog/recipes.json` nuevo con `git show 97060db:catalog/recipes.json` y con el anexo: (a) claves, orden, métodos, autores y `roasts` contra la "Lista final"; (b) en las 20 que se quedan, mismos tipos, inicios, duraciones, agua, dosis, temperatura (salvo `four_six`), molienda y fuente; (c) en las 14 nuevas, pasos, agua, dosis, temperatura, molienda y `sourceUrl` contra su tabla del anexo; (d) en cada `POUR` con instrucción, que los números de "Añade X g (total Y g)" y "Add X g (total Y g)" cuadran con los datos; (e) ningún `SWIRL` con la instrucción vacía; (f) recuentos: 34 recetas, V60 8, AeroPress 5, prensa francesa 4, Kalita Wave 3, Origami 3, Chemex 3, moka 1, Otro 7; 28 aptas para medio (21 cualquiera y 7 con `MEDIUM`) y 6 solo claro; filas por filtro: Todos 34, Claro 32, Medio 28, Oscuro 22. | El validador de la app no ve un número mal copiado ni un paso de otra receta. Lo que el script cubre es lo que el test del repo no debe fijar (D7 del plan anterior: el test no fija la lista de claves). |
| D66 | `catalog/README.md` (concreta §3.9; discrepancia 8) | El ejemplo y la tabla de campos ganan `roasts` (lista de `LIGHT`, `MEDIUM`, `DARK`, sin repetir; ausente = cualquier tueste; qué hace la app con un valor desconocido y que el test lo rechaza). La fila `method` admite `MOKA` (solo el llenado de la base es un vertido, como `coava_moka`); siguen sin caber `ESPRESSO` y `COLD_BREW`. "Duraciones inferidas" dice que las marca el anexo. Sección nueva "Tueste y redacción": la proporción (2/3 aptas para medio, 6 solo claro como mucho), cómo se elige `roasts` (lo que dice la fuente; la variante publicada si existe; nunca se adaptan parámetros por cuenta propia), las reglas 1 a 6, el vocabulario, la convención de los ratios (D59) y qué comprueba el test. | Es el sitio que el §3.9 da a las instrucciones de mantenimiento. |

### 2.5 Verificación

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D67 | Prueba de actualización (amplía) | F0, antes de tocar nada, construye una APK de debug con el catálogo actual (`revision` 1) y la URL de la rama: `./gradlew :app:assembleDebug -Ppourline.catalogUrl=https://raw.githubusercontent.com/c3rberuss/pourline/feat/recipe-catalog/catalog/recipes.json`, copiada a `pourline-rev1-debug.apk` en el directorio temporal de la sesión. F6 la instala en `emulator-5556`, guarda una copia de `rao_v60`, prepara sin báscula `gagne_v60` y deja `hoffmann_aeropress` como referencia activa. Después, si F3 está subida, desliza para actualizar (descarga la `revision` 2 con el código antiguo); en cualquier caso instala encima la debug nueva con `installDebug`. Ver §7.4.A. | Es la pregunta del encargo sobre copias y referencias que salen, comprobada en la app real por los dos caminos: la descarga (y de paso que el lector antiguo ignora `roasts`) y la copia del APK más nueva. |
| D68 | APK para el teléfono (concreta §5) | Release firmada con la clave propia: `./gradlew :app:assembleRelease -Ppourline.versionCode=1000004 -Ppourline.versionName=1.0.0-alpha.4`, copiada a `~/Downloads/pourline-1.0.0-alpha.4-tueste-medio.apk`. URL por defecto (`main`). El agente no la instala: el usuario la instala a mano sobre la `1.0.0-alpha.3` (versionCode 1000003), tras exportar la copia de seguridad. | Misma firma y versionCode mayor: se instala encima sin desinstalar ni perder datos. 1000004 sigue el esquema del workflow de release (alpha 4). |

## 3. Fases

Orden: F0 → F1 → F2 → F3 → F4 → F5 → F6. F2 depende de F1 (lee `roasts`); F3 de F2; F4 de F1 (y se puede adelantar a
F2 o F3); F5 de F4 (`CatalogRoastFilter`); F6 de todas.

| Fase | Contenido | Tamaño | Estado |
|---|---|---|---|
| F0 | Línea base y APK con el catálogo actual | S | Hecha |
| F1 | `roasts` en la lectura, filtro puro y `CatalogBrowser` (JVM) | S | Hecha |
| F2 | Reglas del test del repo (tueste, proporción, redacción) | M | Hecha |
| F3 | Catálogo de 34 recetas, instrucciones reescritas y README | L | Hecha |
| F4 | Filtro "Tueste", etiquetas y detalle en Recetas | M | Hecha |
| F5 | Filtro "Tueste" en el `TargetPicker` | S | Hecha |
| F6 | Verificación final, APK del teléfono y cierre | M | Hecha |

Rutas abreviadas: `main/` es `app/src/main/kotlin/dev/josueamaya/pourline/`, `test/` es
`app/src/test/kotlin/dev/josueamaya/pourline/` y `androidTest/` es
`app/src/androidTest/kotlin/dev/josueamaya/pourline/`.

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app`, nunca con `./gradlew build`
ni con builds limpios.

- **Compilar:** `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`.
- **Tests JVM:** `./gradlew :app:testDebugUnitTest --tests "<clase o paquete>" [...]`, solo los de la fase. Nunca la
  batería completa.
- **Tests instrumentados:** `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<clases separadas por comas>`,
  solo las clases de la fase, nunca dos ejecuciones a la vez. `emulator-5556` es el AVD `SG_API34`; si no está
  arrancado, se lanza en segundo plano (`/Users/josue/Library/Android/sdk/emulator/emulator -avd SG_API34 -port 5556 -no-snapshot-save -no-audio`)
  y se espera a `getprop sys.boot_completed` = 1. `emulator-5554` no se usa. El teléfono del usuario no está
  conectado y no se usa.
- **Sin ARTEMIS.** Tests JVM, tests instrumentados de Compose y adb directo (`uiautomator dump`, `input tap`,
  `screencap`).
- **Lint:** `./gradlew :app:lintDebug` en F0, F4, F5 y F6, sin errores ni avisos nuevos respecto a F0. Si `Typos`
  marca una cadena española nueva, `tools:ignore="Typos"`, como `catalog_search_placeholder`.
- **Archivos temporales** (scripts, APK, capturas, tabla de revisión): en el directorio temporal de la sesión, nunca en
  el repo.
- **Commits:** autorizados por el usuario (2026-11-26): uno por fase al cerrarla en verde, en Conventional Commits y en
  inglés, sin `Co-Authored-By` ni atribución, en `feat/recipe-catalog`. El de F3 espera además al visto bueno de los
  textos (P3). Push autorizado solo de esa rama y nunca forzado. El del plan: `docs(specs): add the implementation plan for SPEC-012 amendment 1 (medium roasts)`.

### F0. Línea base y APK con el catálogo actual

**Objetivo:** las cifras de partida de las clases afectadas y la APK de la prueba de actualización (D67). Sin cambios
de código.

**Pasos:**

1. `adb devices -l`: tiene que estar `emulator-5556`. Si aparece un dispositivo que no se esperaba, se pregunta antes
   de seguir.
2. JVM: `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.*"`.
3. `emulator-5556`, con el prefijo `dev.josueamaya.pourline.`: `ui.recipes.RecipesCatalogScreenTest`,
   `ui.recipes.CatalogRecipeDetailScreenTest`, `ui.recipes.CatalogPrepareFlowTest`, `ui.recipes.RecipesScreenTest`,
   `ui.scale.TargetPickerTest` y `ui.prepare.PrepareScreenTest`.
4. `./gradlew :app:lintDebug`: errores y avisos por id.
5. APK de D67: `./gradlew :app:assembleDebug -Ppourline.catalogUrl=https://raw.githubusercontent.com/c3rberuss/pourline/feat/recipe-catalog/catalog/recipes.json`
   y copia de `app/build/outputs/apk/debug/app-debug.apk` a `pourline-rev1-debug.apk` en el directorio temporal.
   `unzip -p` de esa APK: `assets/catalog/recipes.json` tiene `"revision": 1`.
6. `grep -rn` de las 15 claves que salen en `app/src`, `.github` y `README.md`: vacío.

**Archivos:** este plan (cabecera, "Resultado de F0").

**Tests:** ninguno nuevo.

**Criterio de salida:** línea base anotada y APK guardada. Si algo falla en los pasos 2 o 3, se anota como fallo previo.

Sin commit propio.

### F1. `roasts` en la lectura, filtro puro y `CatalogBrowser`

**Objetivo:** leer `roasts` con tolerancia (D50, D51), filtrar por tueste (D52) y llevar el filtro a `CatalogBrowser`
(D53), todo probado en la JVM. La interfaz todavía no lo muestra.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `main/recipe/catalog/RecipeCatalogDtos.kt` | `RecipeCatalogRecipeDto.roasts: JsonElement? = null` (D50). |
| `main/recipe/catalog/CatalogModels.kt` | `CatalogRecipe.roasts` con su KDoc (D51). |
| `main/recipe/catalog/CatalogConversion.kt` | `roastsOf(element)`; `toCatalogRecipe` rellena `roasts`; KDoc de `toRecipe()`: el tueste no pasa a `Recipe` (D50, D51). |
| `main/recipe/catalog/CatalogFilter.kt` | `CatalogListItem.roasts`, `toListItem()`, `suitsRoast`, `filterCatalog(…, roast)`, `CatalogFilters` (D51, D52). |
| `main/ui/recipes/catalog/CatalogBrowser.kt` | Constructor con `savedKeys`, `filters`, `setRoast`, `clearFilters`, `CatalogListUi.roast` y `filtered` (D53). |
| `main/ui/recipes/RecipesViewModel.kt`, `main/ui/scale/TargetPickerViewModel.kt` | `CatalogBrowser(catalogRepository, recipeRepository.catalogKeys(), language, viewModelScope)`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/catalog/CatalogTestJson.kt` | Ayudante `roastsJson(vararg values: String): JsonArray`. |
| `test/.../recipe/catalog/CatalogGrindTest.kt` | `catalogRecipe(...)` gana `roasts: Set<RoastLevel> = emptySet()`. |
| `test/.../recipe/catalog/RecipeCatalogParserTest.kt` | `` `roasts are read as roast levels` `` (`["MEDIUM","DARK"]`, igual en `es` y `en`). `` `missing or empty roasts mean any roast` ``. `` `unknown roast values are ignored and none left means any roast` `` (`["MEDIUM","ULTRA"]` → `{MEDIUM}`; `["ULTRA"]` → vacío; la receta no se descarta). `` `a malformed roasts field is ignored without discarding the recipe` `` (`"MEDIUM"`, `[1, null]`, `{"a":1}`, `null`). `` `repeated roasts are read once` ``. |
| `test/.../recipe/catalog/CatalogConversionTest.kt` | `` `roasts do not reach the recipe` `` (`toRecipe()` igual con y sin `roasts`). |
| `test/.../recipe/catalog/CatalogFilterTest.kt` | `` `a list item carries its roasts` ``. `` `medium shows medium recipes and those for any roast` `` (fuera las de solo `LIGHT` y solo `DARK`). `` `light and dark also include the recipes for any roast` ``. `` `the roast filter combines with method and search` ``. `` `cleared filters are all at every filter` `` (`CatalogFilters()` sin búsqueda, método ni tueste, y `active` falso; `active` verdadero con cada uno). |
| `test/.../ui/recipes/catalog/CatalogBrowserTest.kt` (nuevo) | Con `runTest`, `RecipeCatalogRepository` de `CatalogSource { json }`, `CatalogCache` en `TemporaryFolder`, un `CatalogFetcher` que da `Failed`, `savedKeys = flowOf(emptySet())`, `CatalogLanguageSource { CatalogLanguage.ES }` y `backgroundScope` (recogiendo `ui` en `backgroundScope` y avanzando con `runCurrent`, como `RecipeCatalogRepositoryTest`). `` `the roast filter starts at all and shows every recipe` ``. `` `medium shows the medium recipes and those for any roast` ``. `` `roast, method and search combine` ``. `` `clear filters resets the roast to all with the method and the search` `` (`roast == null`, `filtered` falso y la lista completa). |

**Verificación:** compilar los tres conjuntos.
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.*" --tests "dev.josueamaya.pourline.ui.recipes.catalog.*"`.
`grep -n "^import android\." app/src/main/kotlin/dev/josueamaya/pourline/recipe/catalog/*.kt app/src/main/kotlin/dev/josueamaya/pourline/ui/recipes/catalog/CatalogBrowser.kt`
no encuentra nada salvo en `ResourcesCatalogLanguage.kt`, como hoy.

**Criterio de salida:** todo en verde; `RecipeCatalogFileTest` sigue pasando con el catálogo de hoy.

Commit sugerido: `feat(catalog): read the roasts of each recipe and filter recommended recipes by roast`.

### F2. Reglas del test del repo

**Objetivo:** las reglas del §3.9 de la enmienda, probadas sobre catálogos sintéticos (D57 a D59), con las de tueste ya
activas en el test del repo (D60).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `test/.../recipe/catalog/CatalogContentRules.kt` (nuevo) | `MAX_LIGHT_ONLY = 6`, `roastValueProblems`, `roastShareProblems`, `wordingProblems`, los patrones de D58 y D59 (D57). |
| `test/.../recipe/catalog/RecipeCatalogCheck.kt` | `catalogProblems` añade `roastValueProblems(text)` y `roastShareProblems(catalog.entries)` en la rama `Parsed`. KDoc con la lista de reglas. |
| `test/.../recipe/catalog/CatalogTestJson.kt` | `recipeJson`: el primer paso pasa a `name = localized("Preinfusión", "Bloom")`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/catalog/CatalogContentRulesTest.kt` (nuevo) | `` `roast values must be known roast names without repeats` `` (`["MEDIUM"]`, ausente y `[]` sin problema; `["MEDIUM","ULTRA"]` nombra `ULTRA`; `["DARK","DARK"]`; `"MEDIUM"` y `[1]` no son una lista de nombres). `` `at least two thirds of the recipes must suit a medium roast` `` (2 de 3 sí; 1 de 3 no; `[LIGHT, MEDIUM]` y `[MEDIUM, DARK]` cuentan como aptas). `` `at most six recipes can be only for a light roast` `` (6 sí, 7 no; `[LIGHT, MEDIUM]` no es solo claro). `` `forbidden terms are found without case, accents or hyphens` `` ("Drawdown", "draw-down", "Rao spin", "rao-spin", "Remolinea", "remolinear", en `es` y en `en`). `` `bloom is forbidden only in spanish` `` (nombre "Bloom" en `es`, problema; en `en`, no; "Preinfusión", no). `` `clock times are found in instructions, stage names and notes` `` ("Espera hasta 0:45", "termina hacia 3:30.", "10:00"). `` `ratios are not clock times` `` ("ratio final con hielo es 1:13", "the final ratio is 1:7.3" y "1:16,7" pasan; "termina hacia 1:13" no). `` `durations with units are allowed` `` ("4 min", "2 min 30 s"). `` `recipe names, contexts and grind texts are not checked` ``. `` `each problem names the recipe, the field and the language` ``. |
| `test/.../recipe/catalog/RecipeCatalogFileTest.kt` | `` `an unknown or repeated roast makes the catalog check fail` `` (el catálogo del repo más una receta con `["MEDIUM","EXTRA_LIGHT"]` y otra con `["DARK","DARK"]`). `` `too few medium roast recipes or too many light only ones make the check fail` `` (el catálogo del repo con siete recetas de solo `LIGHT` añadidas, que superan las 6 y bajan la proporción; y un catálogo de una receta "cualquiera" y dos de solo `LIGHT`). Los casos existentes no cambian. |

**Verificación:** compilar. `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.*"`.
`the repository catalog is valid in both languages` sigue en verde con el catálogo de hoy.

**Criterio de salida:** todo en verde. Una pasada de `wordingProblems` sobre el catálogo de hoy (desde un test temporal o
el script de D65) lista los problemas que F3 tiene que resolver; la lista se anota en "Resultado de F2" como medida del
trabajo.

Commit sugerido: `test(catalog): check roast values, roast balance and step wording in the repository catalog`.

### F3. Catálogo de 34 recetas, instrucciones reescritas y README

**Objetivo:** `catalog/recipes.json` con la "Lista final" del §3.10 y todas las instrucciones, nombres de paso y notas
reescritos con las reglas y el vocabulario, `revision` 2 (D61 a D64), las reglas de redacción activas en el test del
repo y el README al día (D66).

**Pasos:**

1. **Esqueleto** (script de D65): partir de `git show 97060db:catalog/recipes.json`, quitar las 15 que salen, ordenar
   por la "Lista final", `"revision": 2`, `roasts` según el §3.10. Mismo formato que hoy (dos espacios; la app y el test
   no dependen de él).
2. **`four_six`:** `waterTempC` 88.0 y las notas de D63. Pasos, tiempos y agua sin cambios.
3. **Las 14 nuevas**, desde el anexo §2.1 a §2.14: `key`, `name` (es y en del anexo), `author`, `context` (es del anexo;
   en, escrito con el mismo sentido: "Official brew guide", "Official Origami recipe…"), `sourceUrl` (la "Fuente" del
   anexo; en `hario_switch`, la nota de prensa de Hario), `grind.text` y los pasos de su tabla (tipo, inicio, duración y
   agua), con las instrucciones en español del anexo revisadas con D64 y su versión inglesa. Datos de cabecera:

   | Clave | `method` (`methodOther`) | Dosis | `waterTempC` | `roasts` | Molienda (es / en) | Notas (D63) |
   |---|---|---|---|---|---|---|
   | `counter_culture_v60` | `V60` | 30 | 93.3 | — | Media-fina, como sal de mesa / Medium-fine, like table salt | — |
   | `peets_pour_over` | `V60` | 25 | 93.3 | — | Como arena / Like sand | Vale para cualquier gotero cónico |
   | `counter_culture_kalita` | `KALITA_WAVE` | 30 | 93.3 | — | Media, algo más fina que la sal kosher / Medium, slightly finer than kosher salt | — |
   | `ozone_kalita` | `KALITA_WAVE` | 15 | 93 | — | Media de filtro / Medium filter grind | — |
   | `origami_the_coffeeshop` | `ORIGAMI` | 18 | 92 | `LIGHT`, `MEDIUM` | Media (TIMEMORE C3 a unos 16 clics), sin `grind.grinder` | Origami S con filtro cónico; salen unos 270 ml |
   | `counter_culture_chemex` | `CHEMEX` | 45 | 93.3 | — | Media-gruesa, como sal kosher / Medium-coarse, like kosher salt | — |
   | `peets_chemex` | `CHEMEX` | 50 | 93.3 | `LIGHT`, `MEDIUM` | Sin `grind` | — |
   | `counter_culture_french_press` | `FRENCH_PRESS` | 45 | 96.1 | — | Media-gruesa, como sal kosher / Medium-coarse, like kosher salt | — |
   | `peets_french_press` | `FRENCH_PRESS` | 55 | 93.3 | `MEDIUM`, `DARK` | Gruesa, como sal marina gruesa / Coarse, like coarse sea salt | — |
   | `counter_culture_aeropress` | `AEROPRESS` | 18 | 93.3 | — | Media-fina, algo más fina que para goteo / Medium-fine, slightly finer than pour-over | AeroPress invertida con el émbolo ~1 cm dentro; enjuaga el filtro en la tapa |
   | `stumptown_aeropress` | `AEROPRESS` | 17 | 96 | — | Fina, como sal de mesa / Fine, like table salt | AeroPress en posición normal; enjuaga el filtro |
   | `coava_moka` | `MOKA` | 28 | 97.5 | — | Fina, sin llegar a la de espresso / Fine, but not as fine as espresso | El agua de la base se pesa en la báscula; el resto se hace fuera; con otra moka cambia la dosis |
   | `hario_switch` | `OTHER` (Hario Switch) | 20 | — | — | Media / Medium | Válvula cerrada desde el principio; ajusta molienda y tiempo; remueve si lo quieres más fuerte |
   | `ozone_clever` | `OTHER` (Clever) | 15 | 95 | — | Media de filtro / Medium filter grind | — |

   Ajustes ya vistos en las instrucciones del anexo: `hario_switch` paso 2 (`WAIT`) queda vacío y su "remueve…" va a las
   notas; `ozone_clever` paso 3 (`WAIT`) queda en "Pon la tapa."; `coava_moka` paso 4 (`WAIT`) pierde "tarda unos 4
   minutos". Los tipos de paso son los del anexo (D64).
4. **Las 20 que se quedan:** instrucciones, nombres de paso y notas reescritos con D64, en los dos idiomas, sin tocar
   sus datos. Referencias de tono:

   | Receta y paso | Hoy (es) | Con la enmienda (es / en) |
   |---|---|---|
   | `four_six`, `POUR` 1 | Primer vertido (parte del 40 %): 60 g en círculos. | Añade 60 g (total 60 g) en círculos. / Add 60 g (total 60 g) in circles. (sin nombre, como hoy) |
   | `four_six`, `POUR` 2 | Segundo vertido: hasta 120 g. El 40 % define dulzor y acidez. | Añade 60 g (total 120 g). / Add 60 g (total 120 g). (el comentario ya está en las notas) |
   | `four_six`, `WAIT` | Deja que drene. | (vacía) |
   | `four_six`, `ACTION` final | Retira el gotero a las 3:30 aunque quede agua. | Retira el gotero aunque quede agua. / Remove the dripper even if water remains. |
   | `hedrick_v60_easy`, `WAIT` | Espera hasta 0:30. | (vacía) |
   | `hedrick_v60_easy`, `WAIT` final | Drawdown; total 2:30-3:30. | Deja que termine de filtrar. / Let it finish draining. |
   | `hedrick_v60_easy`, notas | … de 2:30 a 3:30; aquí se fija en 3:00. | … de 2 min 30 s a 3 min 30 s; aquí se fija en 3 min. |
   | `hoffmann_ultimate_v60`, `SWIRL` | Remolinea hasta que no quede café seco. | Gira el gotero suavemente hasta que no quede café seco. / Gently swirl the dripper until no dry coffee is left. |
   | `hoffmann_iced_immersion`, `WAIT` | Infusiona hasta las 5:00 (4:00 si es tostado oscuro). | (vacía; la variante para oscuros va a las notas, D63) |
   | `v60_classic`, nombre del `POUR` 1 y `WAIT` | "Bloom"; "Deja que el café florezca." | "Preinfusión" / "Bloom"; (vacía) |

5. **Reglas de redacción activas:** `catalogProblems` añade `wordingProblems(catalog.entries)` (D60).
6. **README** (D66).
7. **Comprobaciones** del script de D65, del test y del APK (Verificación).
8. **Revisión de textos (P3, aceptada):** el script genera `catalog-review.md` en el directorio temporal: por receta,
   nombre, `roasts`, notas y cada paso (tipo, inicio, agua, nombre e instrucción en `es` y `en`). El implementador
   entrega la ruta de la tabla y **se detiene antes del commit de F3** (y del push) hasta el visto bueno del usuario. Las
   correcciones que pida se aplican al JSON, se repiten las comprobaciones y entonces se hace el commit.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `catalog/recipes.json` | El catálogo nuevo (D61 a D64). |
| `catalog/README.md` | D66. |
| `test/.../recipe/catalog/RecipeCatalogCheck.kt` | `wordingProblems` en `catalogProblems` (D60). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../recipe/catalog/RecipeCatalogFileTest.kt` | `the repository catalog is valid in both languages` cubre ya todas las reglas. Nuevos: `` `a forbidden term or a clock time makes the catalog check fail` `` (el catálogo del repo más una receta con una instrucción `es` "Drawdown", otra con un paso llamado "Bloom" en `es`, otra con "Rao spin" en `en` y otra con "Espera hasta 0:45"; `catalogProblems` nombra las cuatro). `` `english bloom and ratios in the notes pass the check` `` (una receta con el nombre "Bloom" en `en` y notas "ratio final con hielo 1:13": ningún problema la nombra). |

**Verificación:**

- Compilar. `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.*"`.
- El script de D65, puntos (a) a (f), sin diferencias.
- `python3 -I -m json.tool catalog/recipes.json > /dev/null` (JSON bien formado).
- Copia en el APK: `./gradlew :app:assembleDebug` y
  `unzip -p app/build/outputs/apk/debug/app-debug.apk assets/catalog/recipes.json | cmp - catalog/recipes.json`.
- Tras el visto bueno y el commit: `git push origin feat/recipe-catalog` y
  `gh run list --workflow catalog.yml --branch feat/recipe-catalog`: el workflow `Catalog` se ejecuta y pasa.

**Criterio de salida:** el test del repo en verde con todas las reglas, el script sin diferencias y el visto bueno del
usuario a la tabla de textos (P3).

Commit sugerido: `feat(catalog): rebalance the catalog toward medium roasts and rewrite its instructions`.

### F4. Filtro "Tueste", etiquetas y detalle en Recetas

**Objetivo:** el filtro en "Recomendadas" (D54), las etiquetas de la fila (D55) y "Tueste: …" en el detalle (D56).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `main/ui/recipes/catalog/CatalogRecipeList.kt` | `CatalogRoastFilter`, `roastsSummary`, etiquetas en `CatalogRecipeRow`, el filtro en `CatalogRecipeList` (`browser::setRoast`). KDoc de la fila y de la lista. |
| `main/ui/recipes/catalog/CatalogRecipeDetailScreen.kt` | "Tueste: …" bajo el contexto (D56). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | Cadenas de §3.1. `step_type_swirl` y `tour_recipe_edit_stage_body` no cambian (P2, rechazada). |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../recipe/catalog/TestRecipeCatalog.kt` | `roasts`: `KEY_V60` `["MEDIUM","DARK"]`, `KEY_AEROPRESS` `["LIGHT"]`, `KEY_GRINDER` `["MEDIUM"]`; `KEY_CLEVER` sin `roasts`. El primer paso de `KEY_V60` pasa a "Preinfusión" / "Bloom" (por coherencia; nada lo comprueba). |
| `androidTest/.../ui/recipes/RecipesCatalogScreenTest.kt` | `theRoastFilterStartsAtAllAndMediumShowsMediumAndAnyRoastRecipes` (`catalog_roast_all` seleccionado; con "Medio", V60, Clever y la del molino; no la AeroPress). `theRoastFilterCombinesWithMethodAndSearch` ("Claro" y AeroPress: solo la AeroPress; "Claro" y "némo": sin resultados). `clearFiltersResetsTheRoastToAll` (sin resultados → "Quitar filtros" → `catalog_roast_all` seleccionado y las cuatro filas). `rowsShowOneTagPerRoastAndNoneForAnyRoast` (V60: "Medio" y "Oscuro"; Clever: ninguna `catalog_roast_tag_*`). `theRoastFilterIsKeptWhenSwitchingSegments` (Mis recetas y vuelta: sigue en "Medio"). |
| `androidTest/.../ui/recipes/CatalogRecipeDetailScreenTest.kt` | `theDetailShowsTheRoasts` (`catalog_detail_roasts` = "Tueste: Medio, oscuro" en el idioma del dispositivo, con `getString`). `aRecipeForAnyRoastSaysAny` (Clever: `catalog_detail_roasts_any`). |

**Verificación:** compilar. Instrumentados en `emulator-5556`: `ui.recipes.RecipesCatalogScreenTest`,
`ui.recipes.CatalogRecipeDetailScreenTest`, `ui.recipes.RecipesScreenTest` y `ui.recipes.CatalogPrepareFlowTest`.
`./gradlew :app:lintDebug`. Capturas
(`adb exec-out screencap -p`) de "Recomendadas" con "Medio" y del detalle de la 4:6, en claro y oscuro, en `es` y `en`,
con el catálogo real del APK.

**Criterio de salida:** todo en verde, lint como F0 y capturas sin recortes (la fila del filtro cabe en el ancho del
emulador, 411 dp).

Commit sugerido: `feat(recipes): filter recommended recipes by roast and show their roasts`.

### F5. Filtro "Tueste" en el `TargetPicker`

**Objetivo:** el mismo filtro en la pestaña "Recomendadas" del selector de Preparar, en Básico y en Experto (§3.8).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `main/ui/scale/TargetPicker.kt` | Parámetro `onCatalogRoast: (RoastLevel?) -> Unit` tras `onCatalogMethod`; `CatalogPickerContent` pinta `CatalogRoastFilter` bajo `CatalogMethodFilter`. KDoc. |
| `main/ui/prepare/PrepareScreen.kt` | `onCatalogRoast = pickerViewModel.catalog::setRoast`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/scale/TargetPickerTest.kt` | `catalogItem(...)` gana `roasts`: `nemo_v60` `MEDIUM`, `DARK`; `test_aeropress` `LIGHT`; `test_clever` sin `roasts`. `show()` guarda también el tueste y pasa `onCatalogRoast`. Nuevos: `theRecommendedTabFiltersByRoast` ("Claro": AeroPress y Clever; "Oscuro": V60 y Clever). `clearFiltersInThePickerResetsTheRoast`. `recommendedRowsShowTheRoastTags`. `theRoastFilterIsShownInBasicAndExpert` (`showBrews` falso y verdadero). |
| `androidTest/.../ui/prepare/PrepareScreenTest.kt` | `thePickerRoastFilterFiltersTheRecommendedRecipes` (con la app real: abrir el selector, "Recomendadas", "Claro": aparece `KEY_AEROPRESS` y desaparece `KEY_V60`). |

**Verificación:** compilar. Instrumentados en `emulator-5556`: `ui.scale.TargetPickerTest`, `ui.prepare.PrepareScreenTest`,
`ui.tour.GeneralTourTest` y `ui.session.ScalelessJourneyTest` (abren el selector). `./gradlew :app:lintDebug`.
Comprobación del alto (R4): con el selector abierto en "Recomendadas", captura en `emulator-5556` tal cual, con
`adb shell settings put system font_scale 1.3` y con `adb shell wm size 720x1280` y `adb shell wm density 320`
(360 × 640 dp); después `font_scale 1.0`, `wm size reset` y `wm density reset`.

**Criterio de salida:** todo en verde y, en las tres capturas, el filtro, la lista y los botones del selector visibles.
Si en 360 × 640 dp algo queda fuera del cajón, se para y se consulta antes de cambiar `ListMaxHeight`.

Commit sugerido: `feat(prepare): filter recommended recipes by roast in the target picker`.

### F6. Verificación final, APK del teléfono y cierre

**Objetivo:** el §7 completo, el APK para la prueba manual del usuario (D68) y el plan al día.

**Pasos:** los de §7 en orden; los resultados van en "Resultado de F6", con la línea base de F0 para separar fallos
previos de fallos nuevos. Estado de la cabecera actualizado. La fila Estado del spec solo cambia si el usuario lo pide.

Commit sugerido: `docs(specs): record the SPEC-012 amendment 1 verification`.

### 3.1 Cadenas nuevas (`values` / `values-en`)

| Clave | Español | Inglés | Fase |
|---|---|---|---|
| `catalog_roast_filter` | Tueste | Roast | F4 |
| `catalog_detail_roasts` | Tueste: %1$s | Roast: %1$s | F4 |
| `catalog_detail_roasts_any` | Tueste: cualquiera | Roast: any | F4 |

Se reutilizan `catalog_filter_all` ("Todos" / "All"), `catalog_clear_filters` y `coffee_roast_light`,
`coffee_roast_medium` y `coffee_roast_dark`. `step_type_swirl` ("Remover" / "Swirl") y
`tour_recipe_edit_stage_body` no cambian (P2, rechazada).

## 4. Trazabilidad con el §5 del spec

Los cuatro criterios de la enmienda tienen fases y tests propios. Los demás no cambian con la enmienda: se cubren por
regresión con las clases que ya los prueban o, si su código no cambia, con el `git diff` vacío del §7.2.

| # | Criterio del §5 | Fases | Cómo se comprueba |
|---|---|---|---|
| E1-a | **Enmienda 1:** el catálogo publicado cumple la proporción (al menos 2/3 aptas para medio, como mucho 6 solo claro) y cada receta nueva o variante tiene fuente en el anexo. | F2, F3, F6 | `roastShareProblems` dentro de `RecipeCatalogFileTest.the repository catalog is valid in both languages`; script de D65 (recuentos y `sourceUrl` de las 14 nuevas y de `four_six` contra el anexo). |
| E1-b | **Enmienda 1:** el test del repo falla con un valor desconocido en `roasts`, con un término prohibido o con un tiempo `m:ss`, y pasa con el catálogo publicado. | F2, F3 | `CatalogContentRulesTest`; `RecipeCatalogFileTest.an unknown or repeated roast makes the catalog check fail`, `a forbidden term or a clock time makes the catalog check fail` y `english bloom and ratios in the notes pass the check`; el workflow tras el push. |
| E1-c | **Enmienda 1:** tests JVM del filtro por tueste: "Medio" incluye las de sin `roasts`; se combina con método y búsqueda; "Quitar filtros" lo restablece a "Todos". | F1 | `CatalogFilterTest` (casos de tueste y `CatalogFilters`); `CatalogBrowserTest`. |
| E1-d | **Enmienda 1:** tests instrumentados del filtro "Tueste" en Recomendadas y en el `TargetPicker`, etiquetas en la fila y "Tueste: …" en el detalle. | F4, F5 | `RecipesCatalogScreenTest`, `CatalogRecipeDetailScreenTest`, `TargetPickerTest`, `PrepareScreenTest.thePickerRoastFilterFiltersTheRecommendedRecipes`. |
| 1 | `catalog/recipes.json` con el contenido del §3.10, pasa su test, y el APK lleva la misma copia. | F3, F6 | `RecipeCatalogFileTest`; script de D65; `unzip -p … \| cmp` en debug y release. |
| 2 | El workflow se ejecuta en un `push` que toca `catalog/**` y falla con una receta inválida. | F3, F6 | Casos de `RecipeCatalogFileTest` (los de hoy y los nuevos); `gh run list` tras el push de F3. |
| 3 | Tests JVM del parser. | F1 | `RecipeCatalogParserTest` (casos de `roasts` y los de hoy). |
| 4 | Tests JVM de la conversión. | F1 | `CatalogConversionTest`. |
| 5, 6 | Tests JVM de la copia y de la política de copias. | F1, F6 (regresión) | `CatalogCopyTest`, `CatalogGrindTest`, `CatalogPolicyTest`, `RecipeCatalogRepositoryTest`, `CatalogCacheTest`, `HttpCatalogFetcherTest` (todos en `recipe.catalog.*`). La `revision` 2 empaquetada frente a una caché 1: §7.4.A. |
| 7, 8 | Migración 7 → 8 y copia de seguridad. | — | Sin cambios: `git diff` vacío en `history/` y `backup/` (§7.2). |
| 9 | Instalación nueva: "Mis recetas" vacía con "Ver recomendadas". | F6 (regresión) | `RecipesCatalogScreenTest.aFreshInstallShowsTheEmptyListWithSeeRecommended`; §7.4.B. |
| 10 | Tests instrumentados del selector, filtro por método, búsqueda, detalle, "Preparar", "Guardar" y pestaña del `TargetPicker`. | F4, F5 (regresión) | Las mismas clases, que se ejecutan enteras en F4, F5 y F6. |
| 11 | Editor con autor y fuente; "Preparar" con la molienda convertida. | — (regresión) | Sin cambios en `ui/recipes/RecipeEdit*` ni en `grinder/`; `CatalogPrepareFlowTest` en F4. |
| 12 | Ajustes sin "Restaurar"; el recorrido señala "Recomendadas". | — | Sin cambios en `ui/settings/` ni en `Tours.kt` (§7.2). |
| 13 | La app solo contacta con `raw.githubusercontent.com` y ML Kit no envía telemetría. | — | Sin cambios en el manifiesto, `app/build.gradle.kts` ni dependencias (§7.2). |
| 14 | Verificación manual en el móvil. | F6 (usuario) | §7.5, con la APK de D68. Lo pendiente del plan anterior (modo avión, deslizar, cambio en `main`) se puede hacer en la misma sesión. |
| 15 | Textos en `es` y `en`; lint sin avisos nuevos; solo los tests del spec. | Todas | §3.1; `lintDebug` en F0, F4, F5 y F6; ejecuciones con `--tests` y con la lista de clases. |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | Al reescribir 259 pasos (153 de las 20 que se quedan y 106 de las 14 nuevas) en dos idiomas, más los nombres y las notas, se cuela un número mal sumado, un cambio de sentido o un paso de otra receta. El validador de la app no lo ve. | Datos intocables en las 20 que se quedan y comparados con el anexo en las 14 nuevas (script de D65, puntos b y c); la suma de "Añade X g (total Y g)" comprobada (punto d); revisión del usuario con la tabla de F3 (P3). |
| R2 | La regla de tiempos da falsos positivos (ratios) o deja pasar un tiempo (un "1:13" tras la palabra "ratio" que sí era un tiempo). | D59 con casos de los dos lados en `CatalogContentRulesTest`; la convención (P1, aceptada) va en el §3.9 del spec y en el README. |
| R3 | La etiqueta "Remover" de `SWIRL`, que se queda (P2, rechazada), no dice qué se gira y puede confundirse con remover con cuchara (`ACTION`). | Un `SWIRL` nunca queda vacío y dice "Gira el gotero…" (o el recipiente que sea) (D64, script de D65 punto e). |
| R4 | El cajón del `TargetPicker` (sin desplazamiento) no cabe en pantallas pequeñas o con letra grande al añadir una fila de 48 dp. | Capturas de F5 con `font_scale` 1.3 y a 360 × 640 dp; si no cabe, se para y se consulta (opciones: bajar `ListMaxHeight` o poner el filtro en la misma fila que el método). |
| R5 | Un dispositivo de prueba con la caché de la rama se queda con una copia intermedia del catálogo. | Una sola subida de `revision` en una sola fase (D61); `pm clear` antes de las pruebas con la URL de la rama. |
| R6 | La versión del teléfono (`1.0.0-alpha.3`) lee el archivo nuevo de `main` mal. | `roasts` es opcional y la app ignora los campos desconocidos (`ignoreUnknownKeys`); `MOKA` ya existía en `BrewMethod`. La APK con el código antiguo de F0 descarga la `revision` 2 en §7.4.A. |
| R7 | Un usuario con la referencia activa o una copia de una receta que sale pierde algo. | La app ya lo resuelve (ver "Al recorrer…"): la copia queda intacta y la referencia avisa con `target_error_removed`, como manda el §3.10. §7.4.A lo comprueba con una actualización real. |
| R8 | En `coava_moka` con báscula, la preparación termina por inactividad (60 s sin verter) o ve el peso caer al retirar la base. | Comportamiento previo, que ya afecta a las recetas de inmersión; la enmienda no toca la sesión. Las notas de la receta dicen que solo el llenado se hace en la báscula. §7.4.B la prepara sin báscula y se anota lo que se vea; si el usuario quiere otro comportamiento, es un cambio aparte. |
| R9 | `R8` (el ofuscador) rompe la lectura de `roasts` (un `JsonElement` en un DTO) en release. | El plugin de serialización ya cubre los DTO del catálogo en release; §7.4.C abre "Recomendadas" con la release y comprueba filtros y etiquetas. |
| R10 | Cambiar el constructor de `CatalogBrowser` rompe a quien lo construye. | Solo lo construyen `RecipesViewModel` y `TargetPickerViewModel`; la compilación de los tres conjuntos lo detecta en F1. |
| R11 | Instalar en el teléfono una APK con otra firma obliga a desinstalar y se pierden los datos. | Release firmada con la clave propia y versionCode 1000004 (D68). El agente no instala nada en el teléfono; el usuario exporta antes la copia de seguridad. |
| R12 | Un error de sintaxis o de formato al editar el JSON a mano. | `json.tool`, el parser y el test del repo en F3; el script de D65 genera el esqueleto en lugar de copiar y pegar. |

## 6. Preguntas para el usuario

Respondidas por el usuario el 2026-11-26.

1. **P1. Ratios en las notas frente a tiempos `m:ss` (discrepancia 1, D59). Aceptada la propuesta.** Las notas deben dar
   el ratio final (§3.10) y el test prohíbe `m:ss` en las notas (§3.9); "1:13" cumple las dos cosas. **Respuesta:** un
   ratio entero se escribe siempre tras la palabra "ratio" ("ratio final con hielo 1:13") y el test no lo cuenta como
   tiempo; un ratio con decimales ("1:16,7") nunca coincide. El §3.9 del spec lo recoge (Enmienda 1).
2. **P2. La etiqueta de `SWIRL` en español (discrepancia 2). Rechazada la propuesta.** Se proponía cambiar
   `step_type_swirl` a "Girar" y "un removido" por "un giro" en el recorrido del editor. **Respuesta:** la etiqueta se
   queda "Remover" y el recorrido no cambia. La regla 4 sigue igual: el gesto de girar el gotero se modela como `SWIRL`
   y su instrucción dice "Gira el gotero…"; remover con cuchara es `ACTION`. Ninguna fase toca esas cadenas (D64, §3.1).
3. **P3. Revisión de los textos antes del commit de F3. Aceptada la propuesta.** **Respuesta:** F3 genera la tabla con
   todas las recetas y sus pasos en los dos idiomas, y el implementador se detiene antes del commit de F3 (y del push,
   que publica el workflow) hasta el visto bueno del usuario.

## 7. Verificación final

### 7.1 Suites (solo las afectadas)

- **JVM:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.*" --tests "dev.josueamaya.pourline.ui.recipes.catalog.*" --tests "dev.josueamaya.pourline.session.ScaleSessionManagerTargetTest"`
  (la última, por la referencia a una receta que sale).
- **Instrumentados** en `emulator-5556` (`dev.josueamaya.pourline.`): `ui.recipes.RecipesCatalogScreenTest`,
  `ui.recipes.CatalogRecipeDetailScreenTest`, `ui.recipes.CatalogPrepareFlowTest`, `ui.recipes.RecipesScreenTest`,
  `ui.scale.TargetPickerTest`, `ui.prepare.PrepareScreenTest`, `ui.tour.GeneralTourTest`,
  `ui.session.ScalelessJourneyTest`.
- **Lint:** `./gradlew :app:lintDebug`, sin errores ni avisos nuevos respecto a F0.
- Se compara con F0: los totales suben solo por los tests nuevos; los fallos previos de F0, si los hay, siguen siendo
  los mismos.

### 7.2 Comprobaciones

1. `git diff 97060db --name-only` lista solo: `catalog/recipes.json`, `catalog/README.md`, los archivos de `main/` de
   F1, F4 y F5 (`RecipeCatalogDtos.kt`, `CatalogModels.kt`, `CatalogConversion.kt`, `CatalogFilter.kt`,
   `CatalogBrowser.kt`, `RecipesViewModel.kt`, `TargetPickerViewModel.kt`, `CatalogRecipeList.kt`,
   `CatalogRecipeDetailScreen.kt`, `TargetPicker.kt`, `PrepareScreen.kt`), los dos `strings.xml`, los tests de las
   fases, este plan y los ajustes del 2026-11-26 en `spec-catalogo-recetas.md` y `anexo-catalogo-recetas.md`.
2. `git diff 97060db -- app/src/main/kotlin/dev/josueamaya/pourline/history app/src/main/kotlin/dev/josueamaya/pourline/backup app/src/main/kotlin/dev/josueamaya/pourline/session app/src/main/kotlin/dev/josueamaya/pourline/settings app/src/main/kotlin/dev/josueamaya/pourline/ui/tour app/src/main/AndroidManifest.xml app/build.gradle.kts gradle .github app/schemas`
   vacío.
3. `grep -n "^import android\." app/src/main/kotlin/dev/josueamaya/pourline/recipe/catalog/*.kt app/src/main/kotlin/dev/josueamaya/pourline/ui/recipes/catalog/CatalogBrowser.kt`:
   solo `ResourcesCatalogLanguage.kt`, como antes.
4. `grep -rn` de las 15 claves que salen en `app/src`, `catalog/`, `.github` y `README.md`: vacío.
5. `unzip -p` de las APK de debug y de release: `assets/catalog/recipes.json` idéntico a `catalog/recipes.json`, con
   `"revision": 2` y `"schemaVersion": 1`.
6. El script de D65, puntos (a) a (f), sin diferencias.
7. Con el push autorizado: `gh run list --workflow catalog.yml --branch feat/recipe-catalog` muestra el workflow del push
   de F3 en `success`.

### 7.3 Rendimiento

La enmienda no fija cifras. El filtro corre en memoria sobre 34 recetas y la lectura de `roasts` no añade trabajo
apreciable al parseo (128 ms la primera vez con 35 recetas, fuera del hilo principal, en F11 del plan anterior). No se
mide de nuevo; §7.4.B comprueba a ojo que la lista se desplaza sin saltos con las etiquetas.

### 7.4 E2E por adb (sin ARTEMIS, `emulator-5556`)

Herramientas: `uiautomator dump` para localizar, `input tap`, `screencap` y `adb -s emulator-5556 logcat -b crash -d`,
que debe quedar vacío en todo el recorrido. Capturas en el directorio temporal de la sesión.

**A. Actualización con recetas que salen (D67).**

1. `adb -s emulator-5556 uninstall dev.josueamaya.pourline` y `adb -s emulator-5556 install <directorio temporal>/pourline-rev1-debug.apk`.
   Onboarding en Básico, sin báscula.
2. Recetas → Recomendadas (35 recetas, `revision` 1): abrir `rao_v60` → "Guardar en mis recetas". Abrir `gagne_v60` →
   "Preparar" → empezar sin báscula → terminar. Abrir `hoffmann_aeropress` → "Preparar" y volver sin empezar: queda
   como referencia activa.
3. Si F3 está subida a `origin`: Recetas → Recomendadas → deslizar para actualizar. Se espera: 34 recetas (el código
   antiguo lee la `revision` 2 e ignora `roasts`), sin `rao_v60`, `gagne_v60` ni `hoffmann_aeropress`; Preparar sin
   objetivo, tras el aviso `target_error_removed`.
4. `ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug` (la debug nueva encima, con la URL de `main`).
5. Se espera, haya pasado o no el paso 3: "Mis recetas" conserva la copia de `rao_v60` con sus pasos, autor y fuente;
   Preparar no tiene objetivo y, si el paso 3 no se hizo, muestra el aviso `target_error_removed` ("El objetivo elegido
   ya no existe"); el historial conserva la preparación de `gagne_v60` con su nombre en la fila "Receta" del detalle
   (`brew_detail_facts`);
   Recomendadas tiene 34 recetas y ninguna con "Guardada".

**B. Recorrido de la enmienda** (debug nueva, `pm clear`, modo avión con `adb shell cmd connectivity airplane-mode enable`).

1. Recetas → "Ver recomendadas": aviso "Usando las recetas incluidas en la app", filtro "Tueste" en "Todos" y etiquetas:
   `four_six` "Medio", `hoffmann_ultimate_v60` "Claro" y "Medio", `v60_classic` sin etiqueta de tueste.
2. "Medio": desaparecen las 6 solo claro (`wang_wbrc_2017` entre ellas) y siguen `v60_classic` y `peets_french_press`.
   "Oscuro": `peets_french_press` sí, `four_six` no. Método AeroPress con "Medio": solo `counter_culture_aeropress` y
   `stumptown_aeropress`. "nemo" con "Medio": "No hay recetas con estos filtros" → "Quitar filtros": "Todos", sin
   búsqueda y la lista completa. Cambiar a "Mis recetas" y volver: el filtro sigue donde estaba.
3. Detalle de `four_six`: 88 °C, "Tueste: Medio", notas con 93 y 83 °C, pasos con "Añade 60 g (total 60 g)…", sin
   "Drawdown" ni tiempos. `peets_french_press`: "Tueste: Medio, oscuro". `v60_classic`: "Tueste: cualquiera" y su
   primer paso "Preinfusión".
4. Preparar → selector → "Recomendadas" → "Claro": `wang_wbrc_2017` visible; elegirla: Preparar la muestra como
   objetivo. En Experto: pestañas Recetas, Recomendadas e Historial, con el mismo filtro.
5. Inglés (`adb shell cmd locale set-app-locales dev.josueamaya.pourline --locales en-US`): "Roast", "All / Light /
   Medium / Dark", "Roast: Medium, dark", instrucciones en inglés con "Bloom" y "Let it finish draining".
6. `coava_moka` → "Preparar" → sin báscula: recorre los pasos hasta el final; se anota lo que se vea (R8).
7. Capturas en claro y oscuro, `es` y `en`, de la lista con "Medio", el detalle de `four_six` y el selector.

**C. Release (R9).** `./gradlew :app:assembleRelease`, desinstalar la debug del emulador, `adb install` de la release,
Recomendadas con 34 recetas, el filtro "Medio" y las etiquetas. Después se vuelve a la debug para lo que quede.

### 7.5 Prueba manual del usuario (teléfono, sin adb)

La hace el usuario con la APK de D68 (`~/Downloads/pourline-1.0.0-alpha.4-tueste-medio.apk`).

1. Antes, exportar la copia de seguridad (SPEC-011).
2. Instalar la APK encima de la `1.0.0-alpha.3`.
3. Recomendadas: 34 recetas con "Usando las recetas incluidas en la app" (el catálogo aún no está en `main`), filtro
   "Tueste", etiquetas y "Tueste: …" en el detalle.
4. Leer tres o cuatro recetas (por ejemplo la 4:6, una AeroPress y una prensa francesa) y juzgar si las instrucciones se
   entienden sin jerga ni tiempos repetidos.
5. Las copias guardadas antes siguen en "Mis recetas"; si la referencia activa era una receta que sale, Preparar avisa.
6. Si algo no convence, se corrige en el JSON con otra subida de `revision` (D61).

## 8. Resultados de la ejecución (2026-11-26)

Commits de `feat/recipe-catalog` (todos empujados, sin force): F1 `e0b15e2`, F2 `129a265`, F3 `16aefc8`, URL a `develop`
`2ba0f09`, F4 `8c90231`, F5 `c0f9b42`, ajuste del selector `c0362be` y `95c72db`. F0 no tiene commit (solo mide), y F6 es
el commit de este apartado.

### Resultado de F0

Línea base de las clases afectadas: instrumentados, 83 tests en las clases del §7.1, sin fallos; `:app:lintDebug` con 0
errores y 20 avisos; APK `pourline-rev1-debug.apk` (`revision` 1, URL de la rama) guardada en el directorio temporal
de la sesión para D67.

### Resultado de F2

Con las reglas nuevas y el catálogo anterior (35 recetas), el test del repositorio listaba 284 problemas en 32 de las 35
recetas: 208 tiempos `m:ss`, 41 "drawdown", 20 "bloom" en español, 8 "rao spin" y 7 "remolinea". F3 los dejó en cero
(el script de D65 no encuentra diferencias en (a) a (f)).

### Resultado de F6

- **JVM** (`recipe.catalog.*`, `ui.recipes.catalog.*`, `session.ScaleSessionManagerTargetTest`, con `--rerun`): 13 clases,
  139 tests, 0 fallos.
- **Instrumentados** en `emulator-5556` (las 8 clases del §7.1): 110 tests, 0 fallos. No se ha ejecutado la batería
  completa, por indicación del usuario.
- **Lint:** 0 errores y 20 avisos, igual que F0 (un aviso nuevo, `ModifierFactoryExtensionFunction`, se corrigió).
- **§7.2:** (1) `git diff 97060db --name-only` solo lista los archivos previstos más `app/build.gradle.kts`; (2) diff vacío
  en `history`, `backup`, `session`, `settings`, `ui/tour`, manifiesto, `gradle`, `.github` y `app/schemas`; en
  `app/build.gradle.kts` solo cambia la URL (desviación 1); (3) el único `import android.` es el de
  `ResourcesCatalogLanguage.kt`; (4) las 15 claves que salen no aparecen en `app/src`, `catalog/`, `.github` ni `README.md`;
  (5) `assets/catalog/recipes.json` de la debug y de la release es idéntico al de `catalog/` (`revision` 2,
  `schemaVersion` 1, 34 recetas); (6) el script de D65, sin diferencias; (7) el workflow `Catalog` termina en `success`
  en los push de F3 y de la URL.
- **§7.4.A (actualización):** la APK de F0 apunta a la rama, que ya tiene la `revision` 2, así que al abrir
  Recomendadas con red descarga las 34 recetas y la prueba no parte de la `revision` 1. Se repitió en modo avión: se
  guardó `rao_v60`, se preparó `gagne_v60` sin báscula (5:09) y se dejó `hoffmann_aeropress` como referencia activa; después
  `installDebug` encima. Resultado: "Mis recetas" conserva la copia de `rao_v60` con sus 9 pasos; Preparar queda sin objetivo;
  el historial conserva la preparación de `gagne_v60` con "Receta: Jonathan Gagné's V60"; Recomendadas no marca ninguna
  como "Guardada". El aviso `target_error_removed` es un snackbar y no quedó a la vista cuando se volcó la pantalla
  (unos 4 s tras abrir), así que solo se comprobó que el objetivo se limpia. El paso 3 (deslizar para actualizar) ya no
  hace falta: la descarga con la APK antigua ocurrió sola y leyó la `revision` 2 sin error (el lector antiguo ignora
  `roasts`).
- **§7.4.B (recorrido):** filtro y etiquetas correctos (`four_six` "Medio", `hoffmann_ultimate_v60` "Claro" y "Medio",
  `v60_classic` sin etiqueta); "Medio" oculta `wang_wbrc_2017`; "Oscuro" oculta `four_six`; "Quitar filtros" restablece;
  detalle de `four_six` (88 °C, "Tueste: Medio", notas con 93 y 83 °C, "Añade 60 g en círculos." en el primer vertido),
  `peets_french_press` ("Tueste: Medio, oscuro") y `v60_classic` ("Tueste: cualquiera", primer paso "Preinfusión");
  selector con "Claro" y `wang_wbrc_2017` elegida como objetivo; modo Experto con las tres pestañas y el filtro; inglés
  ("Roast", "Roast: Medium, dark", "Bloom"); `coava_moka` sin báscula avanza por los pasos. Capturas en claro y oscuro,
  `es` y `en`, en el directorio temporal de la sesión.
- **§7.4.C (release):** firmada con la clave propia, 34 recetas, filtro "Oscuro" sin `four_six`, "Medio" más "wang" sin
  resultados, etiquetas visibles (R9 descartado); `logcat -b crash` vacío en todo el recorrido.
- **D68:** `~/Downloads/pourline-1.0.0-alpha.4-tueste-medio.apk` (`versionCode` 1000004, `versionName` 1.0.0-alpha.4).

### Desviaciones

1. **URL del catálogo (pedida por el usuario):** la app descarga de `develop`, no de `main` (`app/build.gradle.kts`, README
   del catálogo, spec §1, §3.1, §3.3, §3.9, §5 y §7, y D68/§7.4.A). Mientras la rama no esté en `develop`, la app muestra
   "Usando las recetas incluidas".
2. **Textos aprobados por el usuario tras la pausa de F3:** la molienda de `hedrick_one_and_done` ("Ajusta la molienda
   para que el tiempo total sea de 2 a 2 min 30 s") y el primer vertido de cada receta sin "(total X g)" (nota en la regla 3 del
   spec y en D64.1).
3. **Notas con rangos o variantes de fuente (D63):** `wang_wbrc_2017`, `stumptown_kalita`, `stumptown_origami`,
   `origami_kopikalyan`, `hoffmann_french_press`, `van_bunnik_wac_2019`, `hedrick_v60_easy`, `hedrick_one_and_done` e
   `hoffmann_iced_immersion`.
4. **Altura de las listas del selector (aprobada por el usuario en R4):** en 360 x 640 dp el botón "Gestionar recetas" quedaba
   fuera del cajón (ya antes de F5, por el `ListMaxHeight` fijo de 360 dp). Las listas usan ahora `weight(1f, fill = false)`
   con ese tope: ocupan lo que sobra y se desplazan. Comprobado en tamaño normal, con letra 1.3 y en 360 x 640 dp.
   Commits `c0362be` y `95c72db`.
5. **Tests:** `RecipesCatalogScreenTest`, `CatalogPrepareFlowTest` y `TargetPickerTest` desplazan la lista con
   `performScrollToNode` porque la fila del filtro de tueste y la nueva altura dejan filas fuera de pantalla (solo cambia cómo
   se comprueba).

### Limitaciones conocidas

- Con letra 1.3 en inglés, el segmento "Medium" del filtro de tueste se recorta a "Medi…" (decisión del usuario: se
  deja). En español, "Medio" cabe.
- El aviso `target_error_removed` no se pudo capturar a la vista en §7.4.A (snackbar breve).
- Pendiente fuera del plan: la prueba manual en el teléfono (§7.5) y `/code-review` antes de fusionar.

## 9. Seguimiento fuera de este plan

- La fusión con `main` publica la `revision` 2; hasta entonces la app usa la copia del APK (R7 del plan anterior).
- La fila Estado del spec, si el usuario quiere actualizarla (P1, D50 y el modelo con `roasts` ya están en el spec, y el
  reparto de `peets_pour_over`, en el anexo).
- El hallazgo menor de F11 del plan anterior ("Las predefinidas y las tuyas, paso a paso", `tour_prepare_tab_recipes_body`)
  sigue pendiente de que el usuario decida el texto.
- El comportamiento de la sesión con báscula en recetas de inmersión y moka (R8), si el usuario quiere cambiarlo.
- `/code-review` de la rama antes de fusionar.
