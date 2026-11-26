# SPEC-012: Catálogo de recetas recomendadas

| Campo | Valor |
|---|---|
| Estado | Aprobado el 2026-11-25 (ajustado el mismo día con las respuestas del [plan](plan-catalogo-recetas.md)); implementado en `feat/recipe-catalog` (F0 a F11), pendiente de la prueba manual y de la fusión. **Enmienda 1** (tueste medio e instrucciones): aprobada el 2026-11-26; plan [plan-tueste-medio.md](plan-tueste-medio.md) aprobado el mismo día, en implementación |
| Fecha | 2026-11-25. Enmienda 1: 2026-11-26 |
| Rama | `feat/recipe-catalog` |
| Deriva de | Uso real: la app no trae recetas de referencia para los distintos métodos |
| Módulos afectados | `:app`: nuevo paquete `recipe/catalog`, `recipe` (`RecipeEntity`, `Recipe`, `RecipeRepository`, `BuiltInRecipes`, `BuiltInRecipeSeeder`), `history` (`HistoryDatabase`, `Migrations`, `BrewEntities`, `HistoryModels`), `settings` (`TargetSettings`), `session` (`ScaleSessionManager`), `backup`, `ui/recipes`, `ui/scale` (`TargetPicker`), `ui/settings`, `ui/history` (`BrewFactsCard`), `ui/tour`, `Navigation.kt`, `NavigationKeys.kt`, `AndroidManifest.xml`, `build.gradle.kts`, `res/values*`. Raíz del repo: `catalog/recipes.json`, `.github/workflows/`, `README.md` |
| Specs relacionados | [SPEC-003](spec-curva-objetivo.md), [SPEC-005](spec-navegacion-modos.md), [SPEC-006](spec-molinos.md), [SPEC-008](spec-recorrido-guiado.md), [SPEC-009](spec-compartir-receta.md), [SPEC-010](spec-paso-accion.md), [SPEC-011](spec-exportar-datos.md) |
| Enmienda 1 | 2026-11-26, en la misma rama. Reequilibra el catálogo hacia el tueste medio (campo opcional `roasts`, filtro "Tueste", al menos 2/3 aptas para medio, máximo 6 solo para claro) y reescribe las instrucciones sin jerga ni tiempos repetidos, con un vocabulario y un test que lo hace cumplir. Cambia §3.1, §3.8, §3.9, §3.10, §5 y §7. Motivo: el tueste claro es difícil de conseguir y varias instrucciones no se entendían |

## 1. Resumen y Contexto

- **Problema:**
  - La app solo trae dos recetas de fábrica (`BuiltInRecipes`: V60 clásica y 4:6), las dos de V60.
  - Quien empieza no tiene recetas de referencia para los demás métodos, ni las recetas conocidas de
    baristas profesionales y campeonatos.
  - Añadir o corregir una receta de fábrica obliga hoy a publicar una versión nueva de la app.
- **Solución:**
  - Un **catálogo de recetas recomendadas** (hasta 35, §3.10) de baristas reconocidos y campeonatos
    (World Brewers Cup, World AeroPress Championship, etc.), con autor, contexto y enlace a la fuente.
  - Se administra desde el propio repo: un único archivo `catalog/recipes.json` que la app descarga
    de la rama `main` y que, además, va empaquetado en el APK como copia inicial.
  - Textos en español e inglés.
  - Desde el catálogo se puede **preparar directamente** o **guardar una copia** en "Mis recetas"
    para editarla.
  - Las dos recetas de fábrica pasan al catálogo y dejan de sembrarse.
- **Alcance (In):**
  - Selector "Mis recetas / Recomendadas" en la pestaña Recetas.
  - Listado de recomendadas con filtro por método y búsqueda por nombre o autor.
  - Detalle de solo lectura, "Preparar" sin guardar y "Guardar en mis recetas".
  - Pestaña "Recomendadas" en el selector de receta de Preparar (`TargetPicker`), también en Básico.
  - Descarga, caché y copia empaquetada del catálogo; permiso `INTERNET`.
  - Campos nuevos de receta (autor, fuente, clave de origen) y referencia de tipo catálogo en el
    historial, con su migración de Room y su reflejo en la copia de seguridad.
  - Retirada de las recetas de fábrica, de "Restaurar recetas predefinidas" y reanclaje del paso del
    recorrido guiado.
  - Test del catálogo del repo y workflow de GitHub Actions que lo ejecuta.
  - Contenido inicial del catálogo (§3.10).
- **Alcance (Out):**
  - Filtro y búsqueda en "Mis recetas" (siguen como hoy).
  - Actualizar las copias ya guardadas cuando cambia el catálogo: la copia es independiente.
  - Descarga en segundo plano, notificaciones de recetas nuevas o interruptor para desactivar la red.
  - Recetas enviadas por usuarios, valoraciones, imágenes o vídeos en el catálogo.
  - Que autor, fuente o clave de origen viajen en el QR de SPEC-009 (el formato `v=1` no cambia).
  - Cualquier otro uso de la red: sin telemetría ni envío de datos del usuario.

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como usuario, quiero ver un listado de recetas recomendadas de baristas y campeonatos para
  tener un punto de partida fiable con cada método.
- **US-2:** Como usuario, quiero filtrar las recomendadas por método y buscarlas por nombre o autor
  para encontrar rápido la que me interesa.
- **US-3:** Como usuario, quiero ver el detalle de una receta recomendada (pasos, dosis, agua,
  temperatura, molienda, autor y fuente) antes de prepararla.
- **US-4:** Como usuario, quiero preparar una receta recomendada directamente, sin llenar "Mis
  recetas" de recetas que solo pruebo una vez.
- **US-5:** Como usuario, quiero guardar una copia de una receta recomendada en "Mis recetas" para
  ajustarla a mi café y mi molino.
- **US-6:** Como usuario, quiero elegir una receta recomendada desde Preparar, igual que elijo una
  propia.
- **US-7:** Como usuario sin conexión, quiero seguir viendo el catálogo (la última versión descargada
  o la que trae la app).
- **US-8:** Como mantenedor, quiero añadir o corregir recetas editando un archivo del repo, sin
  publicar una versión nueva de la app, y que CI impida publicar una receta rota.
- **US-9:** Como usuario que ya tenía la V60 clásica y la 4:6, quiero conservarlas y conservar mi
  historial al actualizar.

## 3. Especificación Técnica

### 3.1 Archivo del catálogo (contrato entre el repo y la app)

- **Ubicación en el repo:** `catalog/recipes.json`, en la raíz. Es la única fuente: la copia del APK
  se genera desde este archivo en el build (el plan decide cómo: `sourceSets` o tarea de copia a
  `assets/catalog/recipes.json`), nunca se mantiene a mano una segunda copia.
- **URL remota:** `https://raw.githubusercontent.com/c3rberuss/pourline/main/catalog/recipes.json`,
  como constante de build (`BuildConfig` o equivalente), no como cadena suelta en el código.
- **Codificación:** UTF-8, JSON.
- **Estructura (`schemaVersion = 1`):**

  ```json
  {
    "schemaVersion": 1,
    "revision": 1,
    "recipes": [
      {
        "key": "four_six",
        "name": { "es": "4:6", "en": "4:6" },
        "author": "Tetsu Kasuya",
        "context": { "es": "Campeón del World Brewers Cup 2016", "en": "2016 World Brewers Cup Champion" },
        "method": "V60",
        "methodOther": null,
        "doseG": 20.0,
        "waterTempC": 92.0,
        "grind": {
          "text": { "es": "Gruesa", "en": "Coarse" },
          "grinder": { "catalogKey": "comandante-c40", "value": 30 }
        },
        "notes": { "es": "...", "en": "..." },
        "sourceUrl": "https://...",
        "stages": [
          {
            "type": "POUR",
            "startS": 0,
            "durationS": 10,
            "waterG": 60.0,
            "name": { "es": "Bloom", "en": "Bloom" },
            "instruction": { "es": "...", "en": "..." }
          }
        ]
      }
    ]
  }
  ```

  Los valores del ejemplo ilustran el formato; los datos reales salen de §3.10.

- **Campos:**

  | Campo | Tipo | Obligatorio | Contenido |
  |---|---|---|---|
  | `schemaVersion` | entero | sí | versión del formato; este spec define `1` |
  | `revision` | entero | sí | se incrementa en cada cambio del archivo; decide qué copia es más nueva (§3.3) |
  | `recipes[].key` | string `[a-z0-9_]+` | sí | clave estable y única; nunca se reutiliza para otra receta |
  | `name` | texto localizado | sí | nombre de la receta |
  | `author` | string | no | autor o barista, sin traducir. Las recetas sin autor (la antigua V60 clásica) lo omiten |
  | `context` | texto localizado | no | campeonato y año, o publicación |
  | `method` | string | sí | nombre de `BrewMethod` (`V60`, `KALITA_WAVE`, `CHEMEX`, `ORIGAMI`, `AEROPRESS`, `FRENCH_PRESS`, `ESPRESSO`, `COLD_BREW`, `MOKA`, `OTHER`) |
  | `methodOther` | texto localizado | solo con `OTHER` | p. ej. "April", "Clever" |
  | `doseG` | número | sí | gramos |
  | `waterTempC` | número | no | °C |
  | `grind.text` | texto localizado | no | descripción de la molienda ("media-fina") |
  | `grind.grinder` | objeto | no | `catalogKey` de `assets/grinders/catalog.json` y `value` en unidades base de ese molino |
  | `notes` | texto localizado | no | instrucciones generales, no notas de cata (igual que `Recipe.notes`) |
  | `sourceUrl` | string `https://` | no (sí para recetas con `author`) | fuente original |
  | `stages[].type` | string | sí | `POUR`, `SWIRL`, `WAIT` o `ACTION` |
  | `stages[].startS`, `durationS` | entero | sí | segundos desde t0 y duración |
  | `stages[].waterG` | número | solo en `POUR` | agua **acumulada** al terminar el paso; en el resto se deriva con `withDerivedWater()` |
  | `stages[].name`, `instruction` | texto localizado | no | nombre del paso e instrucción (≤ 200 caracteres por idioma) |
  | `roasts` (enmienda 1) | lista de string | no | tuestes para los que está pensada la receta: valores de `RoastLevel` (`LIGHT`, `MEDIUM`, `DARK`), sin repetir. Ausente o vacía = cualquier tueste |

  - **Texto localizado:** objeto con las claves `es` y `en`, las dos obligatorias y no vacías.
  - Los campos desconocidos se ignoran (permite añadir campos opcionales sin subir `schemaVersion`).
- **Validación de cada receta** (la aplica la app al leer y el test del repo, §3.9):
  - Convertida a `Recipe` (§3.4) en cada idioma, pasa `RecipeValidator` sin errores.
  - `method` y `type` son valores conocidos; `methodOther` está si y solo si `method == OTHER`.
  - `grind.grinder.catalogKey`, si está, existe en el catálogo de molinos y `value` está en su escala.
  - `sourceUrl`, si está, empieza por `https://`.
  - `key` es única en el archivo.
  - **Enmienda 1:** un valor desconocido en `roasts` se ignora al leer (si no queda ninguno, la
    receta cuenta como "cualquier tueste"). Un `roasts` que no es una lista de textos (un texto
    suelto, un número, un objeto) también se ignora entero, y la receta cuenta como "cualquier
    tueste": nunca se descarta por este campo. El test del repo rechaza los dos casos, así que en
    el archivo publicado no ocurren. `roasts` es opcional, así que no sube `schemaVersion`: las versiones
    anteriores de la app lo ignoran.
- **Versionado:**
  - Un cambio que una versión anterior de la app no puede representar sube `schemaVersion`. Por
    ejemplo: un `BrewMethod` o `StageType` nuevo como valor obligatorio, o un cambio de significado
    de un campo.
  - Dentro de la misma `schemaVersion`, una receta con un valor desconocido se descarta sola (§4).

### 3.2 Idioma

- Se muestra el idioma que la app usa para sus propios textos: un recurso nuevo `catalog_language`
  vale `es` en `values` y `en` en `values-en`. Así un sistema en francés ve el español, igual que el
  resto de la app (la carpeta por defecto es la española).
- La copia guardada (§3.6) y la preparación directa (§3.7) toman los textos en ese idioma en el
  momento de guardar o de empezar; después no se vuelven a traducir.

### 3.3 Obtención, caché y copia empaquetada (`recipe/catalog`)

- **Componentes** (nombres orientativos; el plan los fija):
  - `RecipeCatalogParser`: JVM puro. Lee el JSON con `kotlinx-serialization-json`, aplica §3.1 y
    devuelve las recetas válidas y el número de descartadas, o un error de formato o de versión.
  - `CatalogFetcher`: interfaz; la implementación usa `HttpURLConnection`, sin dependencias nuevas.
    GET anónimo a la URL remota, tiempo de espera de unos 10 s, sin cabeceras con datos del usuario.
    Puede usar `If-None-Match`/`ETag` para no descargar de nuevo un archivo igual.
  - `CatalogCache`: guarda el último catálogo válido en `filesDir/catalog/recipes.json` y la hora de
    la última descarga correcta.
  - `RecipeCatalogRepository`: expone un `Flow` con el estado (recetas, origen, última
    actualización, error del último intento) y la operación `refresh(force)`.
- **Qué copia se usa:** entre la caché y la copia del APK, la de `revision` mayor (con empate, la
  caché). Así, al actualizar la app con un catálogo más nuevo dentro, se usa ese aunque la caché
  sea anterior.
- **Cuándo se descarga:**
  - Al mostrar "Recomendadas" (pestaña Recetas o `TargetPicker`), si la última descarga correcta
    tiene más de 24 h o no hay ninguna.
  - Al deslizar hacia abajo para actualizar (`force = true`), sin mirar las 24 h.
  - Nunca en segundo plano ni con la vista cerrada. Dos peticiones simultáneas se unen en una.
- **Escritura atómica:** la descarga se valida entera antes de sustituir la caché (archivo temporal y
  renombrado). Un catálogo inválido nunca reemplaza a uno válido.
- **Tiempo de publicación:** `raw.githubusercontent.com` cachea unos minutos; un cambio en `main`
  puede tardar ese tiempo en llegar. No se intenta evitar.

### 3.4 Modelo y conversión a `Recipe`

- **`CatalogRecipe`** (dominio, ya en el idioma elegido): `key`, `name`, `author?`, `context?`,
  `method`, `methodOther?`, `doseG`, `waterTempC?`, `grindText?`, `grinderRef?` (`catalogKey`,
  `value`), `notes?`, `sourceUrl?`, `stages: List<RecipeStage>`. **Enmienda 1:** también
  `roasts: Set<RoastLevel>` (vacío = cualquier tueste), igual en los dos idiomas. Sirve para filtrar y
  mostrar el tueste (§3.8) y no pasa a `Recipe` (§7).
- **A `Recipe`:**

  | Catálogo | `Recipe` |
  |---|---|
  | `name`, `method`, `methodOther`, `waterTempC`, `doseG`, `notes` | mismos campos |
  | `stages` | `RecipeStage(name, startS × 1000, waterG o derivada, durationS × 1000, type, instruction)`, ordenados por `startMs` y con `withDerivedWater()` |
  | `author`, `sourceUrl`, `key` | campos nuevos `author`, `sourceUrl`, `catalogKey` (§3.5) |
  | `grind` | §3.6 |
  | — | `id = 0`, `builtInKey = null`, `localizedNames = false` |

- Los totales que muestra la UI (agua total, ratio, tiempo total) son los de `Recipe`
  (`totalWaterG`, `ratio`, `totalDurationMs`), no campos del JSON.

### 3.5 Datos persistentes (Room, `HistoryDatabase` 7 → 8)

- **`recipes`:** columnas nuevas, todas `TEXT` nulas:
  - `author`, `sourceUrl`: se muestran y se editan en el editor de la receta propia (campos
    opcionales "Autor" y "Fuente"; la fuente, si no está vacía, debe ser una URL `http(s)://`) y el
    detalle ofrece "Ver fuente". Cualquier receta propia puede tenerlos, no solo las copias.
    `RecipeValidator` añade los límites que fije el plan (longitud razonable, URL válida).
  - `catalogKey`: clave de la receta del catálogo de la que salió la copia. Sin índice único: puede
    haber varias copias.
  - `Recipe` gana `author`, `sourceUrl` y `catalogKey`.
- **`brews`:** columna nueva `referenceKey TEXT` para la referencia de tipo catálogo.
  - `ReferenceType` gana `CATALOG`. Con `CATALOG`, `referenceId` es `null`, `referenceKey` lleva la
    clave del catálogo y `referenceName` el nombre mostrado al empezar, como ya hace con las demás.
- **Recetas de fábrica existentes** (en la misma migración):
  - Las filas con `builtInKey` no nulo pasan a ser recetas propias: `name` y los `name` e
    `instruction` de sus pasos se escriben con el texto traducido al idioma actual (lo que hoy se
    traduce al vuelo), `localizedNames = 0`, `builtInKey = NULL` y `catalogKey` = la clave antigua
    (`v60_classic`, `four_six`), que es la misma en el catálogo (§3.10).
  - Se aplica a todas, editadas o no. No se borra nada; `id` no cambia, así que el historial y la
    referencia activa siguen apuntando a ellas.
  - La columna `builtInKey` se queda (quitarla exige reconstruir la tabla) y deja de usarse.
  - Los textos traducidos que necesita la migración (hoy `R.string.recipe_builtin_*` y
    `R.array.recipe_builtin_*`) se conservan mientras exista la migración.
- **Referencia activa (`TargetSettings`):** `ReferenceKey` gana `Catalog(key: String)`, que se
  guarda como tipo `CATALOG` (en mayúsculas, como `RECIPE` y `BREW`) con la clave en texto.
- **Copia de seguridad (SPEC-011):** `RecipeDto` gana `author`, `sourceUrl`, `catalogKey`; el DTO de
  preparación gana `referenceKey`. Todos opcionales con valor por defecto `null`, de modo que las
  copias anteriores se importan igual. Si el lector actual rechaza claves desconocidas, el plan sube
  `FORMAT_VERSION` y lo justifica.
- **Se retiran:** `BuiltInRecipes`, `BuiltInRecipeSeeder` (ya no se siembra en instalaciones nuevas),
  `RecipeRepository.restoreBuiltIns`, la opción "Restaurar recetas predefinidas" de Ajustes y la
  etiqueta "Predefinida" de `RecipeRow`.

### 3.6 Guardar una copia ("Guardar en mis recetas")

- Construye la `Recipe` de §3.4 en el idioma actual.
- **Molienda:**
  - Con `grind.grinder`: se resuelve con la misma lógica que la vista previa de importación de
    SPEC-009 §3.6 (`SharedGrindResolution`): mismo molino si el usuario lo tiene, conversión a sus
    molinos con equivalencia (selector "Usar con <molino> · <ajuste>" / "Guardar como texto", primer
    molino convertible preseleccionado, "≈" en conversiones redondeadas), o texto.
  - El selector se muestra en el detalle (§3.8) solo cuando hay más de una opción.
  - El texto, cuando no se enlaza a un molino, es `grind.text`; si además hay `grind.grinder`, se le
    añade " · <marca> <modelo> <ajuste>".
  - Sin `grind`: sin molienda.
  - Una molienda solo de texto (sin molino ni ajuste) se muestra sin la etiqueta "molino eliminado"
    en el editor, en Preparar y en el historial. Vale también para las recetas importadas "como
    texto" de SPEC-009.
- **Nombre repetido:** si ya existe una receta con el mismo nombre (sin mayúsculas ni espacios
  extremos), se añade " (2)", " (3)"… como en SPEC-009.
- **Copia existente:** si ya hay una receta con ese `catalogKey`, se avisa "Ya tienes una copia" y se
  permite guardar otra.
- `RecipeRepository.save(recipe)` y mensaje "Receta guardada en Mis recetas". La copia no se
  actualiza si el catálogo cambia.

### 3.7 Preparar sin guardar

- "Preparar" fija `ReferenceKey.Catalog(key)` como referencia activa y abre Preparar, igual que hoy
  con una receta propia.
- `ScaleSessionManager` resuelve `Catalog` con `RecipeCatalogRepository`: construye la `Recipe` de
  §3.4 y la trata como una receta (`TargetCurve.fromRecipe`, dosis nominal, prellenado de método y
  temperatura). `UsedTarget` se guarda con `type = CATALOG`, la clave y el nombre.
- Si la clave ya no existe en el catálogo actual: `Resolution.Removed`, con el mismo aviso que hoy
  cuando se borró la receta.
- La molienda de la referencia (`sourceGrind`) se resuelve como en §3.6, pero de forma automática y sin
  selector: mismo molino si el usuario lo tiene; si no, el primer molino convertible en el orden de la
  pantalla Molinos (con "≈" si se redondea); si no hay ninguno, el texto de §3.6. Con una molienda
  solo de texto, la tarjeta de molienda muestra ese texto y la sugerencia del método para el molino
  elegido, como con una receta sin molienda.
- `BrewFactsCard` y el detalle de la preparación muestran el nombre de la receta también con
  `CATALOG`.

### 3.8 Interfaz

- **Pestaña Recetas:** `AntSegmented` "Mis recetas / Recomendadas" arriba. Se recuerda la última
  selección durante la sesión de la app.
- **"Mis recetas":** como hoy, sin la etiqueta "Predefinida". Su estado vacío (el de toda instalación
  nueva, porque ya no se siembra nada) añade un enlace "Ver recomendadas" que cambia el selector.
- **"Recomendadas":**
  - Campo de búsqueda: coincide con nombre o autor, sin distinguir mayúsculas ni acentos.
  - Filtro por método: "Todos" más los métodos presentes en el catálogo.
  - **Enmienda 1:** filtro por tueste: "Todos / Claro / Medio / Oscuro", que arranca en "Todos"
    (con la misma persistencia que el filtro por método). "Medio" muestra las recetas con `MEDIUM`
    en `roasts` y las de cualquier tueste; igual con "Claro" y "Oscuro".
  - Filtros (método y tueste) y búsqueda se combinan; "Quitar filtros" los quita todos.
  - Fila: nombre, autor, método, "dosis · agua · ratio", tiempo total y la etiqueta "Guardada" si
    existe una receta propia con su `catalogKey`. **Enmienda 1:** además, una etiqueta por tueste de
    `roasts` ("Claro", "Medio", "Oscuro"); sin etiqueta de tueste si es para cualquiera.
  - Orden: el del archivo.
  - Deslizar hacia abajo actualiza (§3.3).
  - Aviso discreto con el error del último intento (§4) y "Última actualización: <fecha>".
  - Sin resultados: "No hay recetas con estos filtros" y "Quitar filtros".
- **Detalle** (`CatalogRecipeDetail(key: String) : NavKey`), de solo lectura:
  - Nombre, autor, contexto, método, dosis, agua total, ratio, temperatura, molienda, tiempo total,
    notas y lista de pasos con su tipo, inicio, agua e instrucción.
  - **Enmienda 1:** "Tueste: Medio, oscuro" o "Tueste: cualquiera".
  - "Ver fuente" abre `sourceUrl` en el navegador (`ACTION_VIEW`).
  - En Experto, vista previa de la curva objetivo como en el editor; en Básico se oculta (SPEC-005).
  - Botones "Preparar" (principal) y "Guardar en mis recetas".
- **Receta propia con autor o fuente:** el detalle o editor muestra el autor y "Ver fuente".
- **Selector de Preparar (`TargetPicker`):** pestaña nueva "Recomendadas" con la misma búsqueda,
  filtros (método y, desde la enmienda 1, tueste) y filas; elegir una fija `ReferenceKey.Catalog`. Aparece en Básico y en Experto (en Básico
  quedan "Recetas" y "Recomendadas").
- **Ajustes:** se quita "Restaurar recetas predefinidas".
- **Recorrido guiado (SPEC-008):** el paso `recipes.builtin` se sustituye por `recipes.catalog` (id
  nuevo, para que lo vea una vez quien ya hizo el recorrido de Recetas), anclado al segmento
  "Recomendadas" (ancla `recipes_catalog_segment`), con textos nuevos: título "Recomendadas",
  cuerpo "Recetas de baristas y campeonatos. Prepáralas tal cual o guarda una copia para ajustarla."
  (y su versión en inglés).
- Todos los textos nuevos, en `values` y `values-en`.

### 3.9 Plataforma, red y CI

- **Manifiesto:** se quita `tools:node="remove"` del permiso `INTERNET` y se reescribe el comentario:
  la red solo se usa para descargar el catálogo público de recetas; los datos del usuario no salen
  del teléfono.
- **Telemetría de ML Kit:** hoy se evita quitando `INTERNET`. Con el permiso de vuelta, el
  reconocimiento de texto de ML Kit puede enviar telemetría. Debe quedar desactivada (por ejemplo,
  quitando del manifiesto fusionado los componentes de `com.google.android.datatransport`); F0 del
  plan lo investiga y lo verifica (§5).
- **README:** sección de privacidad o nota que explique el uso de la red y cómo se administra el
  catálogo (`catalog/recipes.json` en `main`).
- **Test del catálogo del repo:** test JVM que lee `catalog/recipes.json` de la raíz y comprueba:
  esquema, `revision` y `schemaVersion` presentes, claves únicas, ambos idiomas en todos los textos
  localizados, cada receta válida en ambos idiomas (§3.1) y `catalogKey` de molino existente.
  **Enmienda 1**, además:
  - `roasts` solo con valores de `RoastLevel` y sin repetir.
  - Proporción de §3.10: al menos 2/3 de las recetas aptas para tueste medio y como máximo 6 solo
    para claro.
  - Ninguna instrucción, nombre de paso ni nota contiene términos de la lista prohibida (§3.10, sin
    distinguir mayúsculas ni acentos, en los dos idiomas donde aplique) ni tiempos con formato
    `m:ss` (por ejemplo "0:45", "3:30").
  - Excepción de los ratios (Enmienda 1): un ratio entero va siempre tras la palabra "ratio"
    ("ratio final con hielo 1:13", "the final ratio is 1:13") y no cuenta como tiempo; un ratio con
    decimales ("1:16,7") nunca tiene la forma `m:ss`. Un "1:13" sin "ratio" delante sí cuenta como
    tiempo.
- **Workflow** nuevo en `.github/workflows/` que ejecuta solo ese test en cada `push` y
  `pull_request` que toque `catalog/**`.
- **Mantenimiento:** el README (o un comentario junto al archivo, en el `README` de `catalog/`)
  explica cómo añadir una receta y que hay que incrementar `revision`.

### 3.10 Contenido inicial

**Enmienda 1 (2026-11-26): tueste medio e instrucciones claras.** Prevalece sobre lo que sigue en
esta sección.

- **Motivo:** muchas recetas (sobre todo las de campeonato) están pensadas para tueste claro, que es
  difícil de conseguir; y varias instrucciones usan jerga ("Drawdown", "Rao spin", "Remolinea") o
  repiten tiempos que la app ya muestra.
- **Proporción:** unas 35 recetas; al menos 2/3 aptas para tueste medio (`MEDIUM` en `roasts` o sin
  `roasts`) y como máximo 6 solo para claro, reservadas a las de campeonato más icónicas.
- **Variantes publicadas:** si la fuente de una receta publica su variante para tueste medio (por
  ejemplo, la 4:6 de Kasuya a 88 °C), se usa esa variante y su `roasts` lo refleja. Nunca se
  cambian por cuenta propia los parámetros de un autor: sin variante publicada, la receta se queda
  como es (con su tueste) o se sustituye.
- **Sustituciones:** las recetas solo para claro que no se quedan se reemplazan por recetas con
  fuente verificada pensadas para tueste medio o cualquier tueste (guías de fabricantes, tostadores
  y baristas reconocidos), con las mismas exigencias de rigor que la investigación original (sin
  confianza baja).
- **`roasts` de cada receta:** el tueste que indica su fuente; si la fuente no lo dice, se infiere y
  el anexo lo marca; una guía genérica de fabricante sin indicación es "cualquiera" (sin `roasts`).
- **Reglas de redacción** de instrucciones, nombres de paso y notas, en los dos idiomas, para todas
  las recetas (también las que se quedan):
  1. Cada instrucción dice qué hacer en ese paso, en lenguaje llano y sin jerga.
  2. Sin tiempos ni horas que la app ya muestra ("Espera hasta 0:30", "termina hacia 1:08").
  3. En los vertidos, lo que se añade y el total: "Añade 60 g (total 120 g)". (Cambio del 2026-11-26:
     el primer vertido solo dice lo que se añade, "Añade 60 g", sin el total redundante.)
  4. "Girar el gotero" siempre es `SWIRL`; "remover con cuchara" siempre es `ACTION`.
  5. Si el paso no necesita más que su tipo, la instrucción queda vacía.
  6. Sin comentarios que no sean una acción ("Pausa.", "El 40 % define dulzor y acidez"); esa
     información, si vale, va a las notas.
- **Vocabulario:**

  | Prohibido | Español | Inglés |
  |---|---|---|
  | drawdown | "Deja que termine de filtrar" | "Let it finish draining" |
  | rao spin | "Gira el gotero con energía" | "Swirl the dripper firmly" |
  | remolinea / remolinear | "Gira el gotero suavemente" | "Gently swirl the dripper" |
  | bloom (solo en español) | "Preinfusión" | "Bloom" (se mantiene) |

  La lista prohibida del test (§3.9) es la columna "Prohibido" aplicada al idioma que corresponda
  ("bloom" solo se prohíbe en español).
- **Lista final** (investigación del 2026-11-26, decisiones del usuario del mismo día). Sustituye a
  la tabla "Recetas" de más abajo, que queda como historia. Datos, citas de tueste y fuentes de las
  recetas nuevas: sección "Enmienda 1" del [anexo](anexo-catalogo-recetas.md).

  | # | Clave | Método | Autor | `roasts` | Confianza | Estado |
  |---|---|---|---|---|---|---|
  | 1 | `four_six` | V60 | Tetsu Kasuya | MEDIUM | alta | variante media: 88 °C ("Around 88°C for medium roast coffee"); notas con 93 °C para claro y 83 °C para oscuro |
  | 2 | `v60_classic` | V60 | — | cualquiera | — | se queda |
  | 3 | `hoffmann_ultimate_v60` | V60 | James Hoffmann | LIGHT, MEDIUM | alta | se queda |
  | 4 | `hedrick_v60_easy` | V60 | Lance Hedrick | cualquiera | alta | se queda |
  | 5 | `hedrick_one_and_done` | V60 | Lance Hedrick | cualquiera | media | se queda |
  | 6 | `counter_culture_v60` | V60 | Counter Culture Coffee | cualquiera | media | nueva |
  | 7 | `peets_pour_over` | V60 | Peet's Coffee | cualquiera | media | nueva; la guía dice "pour over cone" sin modelo: las notas aclaran que vale para cualquier gotero cónico |
  | 8 | `wang_wbrc_2017` | V60 | Chad Wang | LIGHT | alta | se queda (campeonato) |
  | 9 | `stumptown_kalita` | Kalita Wave | Stumptown Coffee Roasters | cualquiera | alta | se queda |
  | 10 | `counter_culture_kalita` | Kalita Wave | Counter Culture Coffee | cualquiera | media | nueva |
  | 11 | `ozone_kalita` | Kalita Wave | Ozone Coffee | cualquiera | alta | nueva |
  | 12 | `stumptown_origami` | Origami | Stumptown Coffee Roasters | cualquiera | alta | se queda |
  | 13 | `origami_kopikalyan` | Origami | Origami / KOPIKALYAN TOKYO | LIGHT, MEDIUM | media | se queda |
  | 14 | `origami_the_coffeeshop` | Origami | Origami / THE COFFEESHOP | LIGHT, MEDIUM | alta | nueva |
  | 15 | `stumptown_chemex` | Chemex | Stumptown Coffee Roasters | cualquiera | alta | se queda |
  | 16 | `counter_culture_chemex` | Chemex | Counter Culture Coffee | cualquiera | media | nueva |
  | 17 | `peets_chemex` | Chemex | Peet's Coffee | LIGHT, MEDIUM | media | nueva; 50 g (la fuente dice 55 g, pero su bloom y su ratio dan 50 g) |
  | 18 | `hoffmann_french_press` | Prensa francesa | James Hoffmann | cualquiera | media | se queda |
  | 19 | `stumptown_french_press` | Prensa francesa | Stumptown Coffee Roasters | cualquiera | alta | se queda |
  | 20 | `counter_culture_french_press` | Prensa francesa | Counter Culture Coffee | cualquiera | alta | nueva |
  | 21 | `peets_french_press` | Prensa francesa | Peet's Coffee | MEDIUM, DARK | alta | nueva |
  | 22 | `counter_culture_aeropress` | AeroPress | Counter Culture Coffee | cualquiera | alta | nueva |
  | 23 | `stumptown_aeropress` | AeroPress | Stumptown Coffee Roasters | cualquiera | alta | nueva |
  | 24 | `merikanto_wac_2021` | AeroPress | Tuomas Merikanto | LIGHT | alta | se queda (campeonato) |
  | 25 | `van_bunnik_wac_2019` | AeroPress | Wendelien van Bunnik | LIGHT | alta | se queda (campeonato) |
  | 26 | `nemo_pop_wac_2025` | AeroPress | Némo Pop | LIGHT | media | se queda (campeonato) |
  | 27 | `coava_moka` | Moka | Coava Coffee Roasters | cualquiera | media | nueva; solo el llenado de la base se pesa, el resto son pasos `ACTION` |
  | 28 | `hario_switch` | Otro: Hario Switch | Hario | cualquiera | media | nueva; sin temperatura en la fuente |
  | 29 | `hoffmann_clever` | Otro: Clever | James Hoffmann | cualquiera | media | se queda |
  | 30 | `five_senses_clever` | Otro: Clever | Five Senses Coffee | LIGHT, MEDIUM | media | se queda |
  | 31 | `ozone_clever` | Otro: Clever | Ozone Coffee | cualquiera | alta | nueva |
  | 32 | `hoffmann_iced_immersion` | Otro: Clever o Hario Switch | James Hoffmann | cualquiera | media | se queda; nota nueva: 4 min de infusión para tuestes oscuros |
  | 33 | `peng_wbrc_2025` | Otro: Solo Dripper | George Jinyang Peng | LIGHT | alta | se queda (campeonato) |
  | 34 | `fukahori_wbrc_2018` | Otro: GINA | Emi Fukahori | LIGHT | alta | se queda (campeonato) |

  - **34 recetas** (no 35: llegar a 35 exigía bajar la confianza o pasar de 6 solo para claro). **28
    aptas para medio** (82 %: 21 para cualquier tueste y 7 con `MEDIUM`). **6 solo para claro**, todas
    de campeonato (campeones, métodos distintos).
  - Reparto: V60 8, AeroPress 5 (2 aptas para medio), prensa francesa 4, Kalita Wave 3, Origami 3,
    Chemex 3, moka 1, Otro 7.
  - **Salen (15):** `hoffmann_one_cup_v60`, `rao_v60`, `gagne_v60`, `wallgren_wbrc_2016`,
    `goldsworthy_wbrc_2016`, `du_jianing_wbrc_2019`, `medina_wbrc_2023`, `hoffmann_aeropress`,
    `stanica_wac_2024`, `wipvasutt_wac_2023`, `ibarra_wac_2018`, `wendelboe_aeropress`,
    `fukahori_switch`, `boen_wbrc_2021`, `april_brewer`. Sus claves no se reutilizan. Las copias
    guardadas con esos `catalogKey` siguen intactas en "Mis recetas"; una referencia activa a una de
    ellas se resuelve como `Removed` (§3.7, §4).
  - **Molino del catálogo:** al salir `fukahori_switch`, solo `merikanto_wac_2021` lleva
    `grind.grinder`. Ninguna receta nueva da un ajuste de un molino del catálogo.
  - **Temperaturas en °F** de las fuentes nuevas: 200 °F = 93,3 °C y 205 °F = 96,1 °C; las de
    Stumptown, que dicen "about 205°F", se quedan en 96 °C.
  - **Fuentes contradictorias:** se siguen los pasos cuando la cabecera de la guía da otro tiempo
    total (Counter Culture, Stumptown); un vertido de más de 120 s se parte en dos (Peet's Chemex,
    "okay to pause").
- **Claves:** las recetas que salen del catálogo pierden su clave para siempre (no se reutiliza);
  `four_six` y `v60_classic` se mantienen, aunque cambien a su variante media.
- `revision` sube al publicar el cambio.

- **Origen:** investigación del 2026-11-25. Los datos completos de cada receta (pasos, tiempos, agua,
  molienda, fuentes consultadas y qué es inferido) están en el anexo
  [anexo-catalogo-recetas.md](anexo-catalogo-recetas.md). Este spec fija qué recetas entran y cómo se
  modelan; el anexo, los números.
- **Total:** hasta 35 recetas: las 31 de confianza alta o media de la investigación, 3 reservas que
  sustituyen a las de confianza baja y la antigua V60 clásica. Las reservas que no se puedan completar
  se quedan fuera (ver "Reservas"), así que el total final está entre 32 y 35.
- **Reglas de modelado** (además de §3.1):
  - **Dilución después de prensar** (AeroPress de campeonato): paso `ACTION` con los gramos y la
    temperatura en la instrucción ("Añade 60 g de agua a 96 °C"). La curva termina en el agua de
    infusión, así que el ratio que muestra la app es el de infusión; las notas dan el ratio final.
  - **Hielo** (café helado): no se modela; las notas indican los gramos de hielo y el ratio final.
  - **Varias temperaturas:** `waterTempC` es la principal o se omite; las demás van en la instrucción
    del paso que las usa.
  - **Café añadido después del agua** o en dos tandas: se explica en la instrucción del paso.
  - **Duraciones inferidas:** las fuentes casi nunca dan la duración de cada vertido; se fijan para
    que encajen los inicios publicados y el anexo las marca como inferidas.
  - **Clave estable:** la 4:6 de Kasuya usa `four_six` (no `kasuya_4_6`) para que las copias
    migradas (§3.5) la reconozcan; la V60 clásica conserva `v60_classic`, sin autor, con los datos de
    `BuiltInRecipes` actuales.
- **Recetas (lista original del 2026-11-25, sustituida por la enmienda 1):**

  | # | Clave | Método | Autor | Contexto | Fuente principal | Confianza |
  |---|---|---|---|---|---|---|
  | 1 | `four_six` | V60 | Tetsu Kasuya | World Brewers Cup 2016, campeón | https://en.philocoffea.com/en-th/blogs/blog/coffee-brewing-method | alta |
  | 2 | `v60_classic` | V60 | — | Antigua receta de fábrica | — | — |
  | 3 | `hoffmann_ultimate_v60` | V60 | James Hoffmann | The Ultimate V60 Technique | https://www.hario-usa.com/blogs/recipes-and-more-from-friends/james-hoffmann-uitimate-v60-technique | alta |
  | 4 | `hoffmann_one_cup_v60` | V60 | James Hoffmann | V60 de una taza | https://www.hario-usa.com/blogs/recipes-and-more-from-friends/james-hoffmann-1-cup-v60-technique | alta |
  | 5 | `rao_v60` | V60 | Scott Rao | Entrevista Hario | https://www.hario.co.uk/blogs/hario-ambassadors/hario-v60-recipe-interview-with-hario-ambassador-scott-rao | media |
  | 6 | `hedrick_v60_easy` | V60 | Lance Hedrick | Vídeo 2021 | https://www.youtube.com/watch?v=Ji4lZTtRAoo | alta |
  | 7 | `hedrick_one_and_done` | V60 | Lance Hedrick | One and Done | https://www.youtube.com/watch?v=PNFVCmxBjQQ | media |
  | 8 | `gagne_v60` | V60 | Jonathan Gagné | Coffee ad Astra | https://coffeeadastra.com/2019/06/09/a-v60-pour-over-video/ | media |
  | 9 | `wang_wbrc_2017` | V60 | Chad Wang | World Brewers Cup 2017, campeón | https://sprudge.com/6-recipes-from-the-2017-world-brewers-cup-123165.html | alta |
  | 10 | `stumptown_kalita` | Kalita Wave | Stumptown Coffee Roasters | Guía de preparación | https://www.stumptowncoffee.com/brew-guides/kalita-wave | alta |
  | 11 | `wallgren_wbrc_2016` | Kalita Wave | Mikaela Wallgren | World Brewers Cup 2016, finalista | https://baristamagazine.com/brewing-experiments-mikaela-wallgrens-2016-world-brewers-cup-championship-recipe | alta |
  | 12 | `goldsworthy_wbrc_2016` | Kalita Wave | Todd Goldsworthy | World Brewers Cup 2016, finalista | https://www.baristamagazine.com/brewing-experiments-todd-goldsworthys-2016-world-brewers-cup-championship-recipe/ | media |
  | 13 | `du_jianing_wbrc_2019` | Origami | Du Jianing | World Brewers Cup 2019, campeón | https://theroasterspack.com/blogs/news/origami-dripper-our-extensive-guide-with-recipes | media |
  | 14 | `medina_wbrc_2023` | Origami | Carlos Medina | World Brewers Cup 2023, campeón | https://www.slowpoursupply.co/blogs/brew-recipes/recipe-recap-carlos-medina-s-representing-chile-world-brewers-cup-champion-recipe | media |
  | 15 | `stumptown_origami` | Origami | Stumptown Coffee Roasters | Guía de preparación | https://www.stumptowncoffee.com/pages/brew-guide-origami | alta |
  | 16 | `origami_kopikalyan` | Origami | Origami / KOPIKALYAN TOKYO | Receta oficial de Origami (reserva) | https://origami-kai.com/en/blogs/special-feature/origami-with-kopikalyan-tokyo-vol-3 | media |
  | 17 | `stumptown_chemex` | Chemex | Stumptown Coffee Roasters | Guía de preparación | https://www.stumptowncoffee.com/pages/brew-guide-chemex | alta |
  | 18 | `hoffmann_french_press` | Prensa francesa | James Hoffmann | The Ultimate French Press Technique | https://www.youtube.com/watch?v=st571DYYTR8 | media |
  | 19 | `stumptown_french_press` | Prensa francesa | Stumptown Coffee Roasters | Guía de preparación | https://www.stumptowncoffee.com/pages/brew-guide-french-press | alta |
  | 20 | `hoffmann_aeropress` | AeroPress | James Hoffmann | The Ultimate AeroPress Technique | https://www.youtube.com/watch?v=j6VlT_jUVPc | alta |
  | 21 | `merikanto_wac_2021` | AeroPress | Tuomas Merikanto | World AeroPress Championship 2021, campeón | https://aeropress.com/pages/wac-recipes | alta |
  | 22 | `van_bunnik_wac_2019` | AeroPress | Wendelien van Bunnik | World AeroPress Championship 2019, campeona | https://aeropress.com/pages/wac-recipes | alta |
  | 23 | `stanica_wac_2024` | AeroPress | George Stanica | World AeroPress Championship 2024, campeón | https://worldaeropresschampionship.com/pages/1st-george-stanica-romania-2024 | media |
  | 24 | `wipvasutt_wac_2023` | AeroPress | Ekameth "Tay" Wipvasutt | World AeroPress Championship 2023, campeón | https://worldaeropresschampionship.com/pages/1st-tay-wipvasutt-thailand-2023 | media |
  | 25 | `nemo_pop_wac_2025` | AeroPress | Némo Pop | World AeroPress Championship 2025, campeón | https://aeropress.com/blogs/w-a-c-aeropress-recipes/1st-nemo-pop-australia-2025 | media |
  | 26 | `ibarra_wac_2018` | AeroPress | Carolina Ibarra Garay | World AeroPress Championship 2018, campeona (reserva) | https://aeropress.com/pages/wac-recipes | por verificar |
  | 27 | `wendelboe_aeropress` | AeroPress | Tim Wendelboe | Guía de preparación | https://timwendelboe.no/pages/how-to-brew-with-an-aeropress | media |
  | 28 | `peng_wbrc_2025` | Otro: Solo Dripper | George Jinyang Peng | World Brewers Cup 2025, campeón | https://sprudge.com/how-to-brew-coffee-like-the-2025-world-brewers-cup-champion-338576.html | alta |
  | 29 | `fukahori_wbrc_2018` | Otro: GINA | Emi Fukahori | World Brewers Cup 2018, campeona | https://goat-story.com/blogs/news/emi-fukahoris-winning-recipe | alta |
  | 30 | `fukahori_switch` | Otro: Hario Switch | Emi Fukahori | Receta MAME | https://www.youtube.com/watch?v=3euEkTBxtEk | alta |
  | 31 | `boen_wbrc_2021` | Otro: Hario Switch | Ole Kristian Bøen | World Brewers Cup 2021 | https://www.hario-europe.com/blogs/hario-community/ole-kristian-boens-switch-recipe | alta |
  | 32 | `hoffmann_clever` | Otro: Clever | James Hoffmann | Vídeo Clever Dripper | https://www.youtube.com/watch?v=RpOdennxP24 | media |
  | 33 | `five_senses_clever` | Otro: Clever | Five Senses Coffee | Guía de preparación (reserva) | https://fivesenses.com.au/blog/clever-coffee-dripper-brew-guide | por verificar |
  | 34 | `april_brewer` | Otro: April | Patrik Rolf / April Coffee Roasters | Receta de la tienda | https://www.aprilcoffeeroasters.com/coffee-inf-recipes/el-tesoro | media |
  | 35 | `hoffmann_iced_immersion` | Otro: Clever o Hario Switch | James Hoffmann | Café helado por inmersión | https://www.youtube.com/watch?v=8uGGeV8A-BM | media |

  Reparto con las 35: V60 9, Kalita Wave 3, Origami 4, Chemex 1, prensa francesa 2, AeroPress 8,
  Otro 8.
- **Molino del catálogo** (`grind.grinder`): solo `merikanto_wac_2021` (`comandante-c40`, 27 clics,
  equivalente con el que se desarrolló la receta) y `fukahori_switch` (`comandante-c40`, 25 clics).
  El resto lleva solo `grind.text`.
- **Reservas** (16, 26, 33): la investigación solo tiene sus datos resumidos; antes de añadirlas al
  JSON hay que completar sus pasos desde la fuente y marcar lo inferido. Si alguna no se puede
  completar con fuente fiable, se queda fuera (no se repone).
- **Descartadas:**
  - Por confianza baja: Matt Winton "Five Pour", Chemex de Hoffmann y Switch "del diablo" de Kasuya.
  - Porque no caben en el modelo: espresso, cold brew, moka y similares (detalle en el anexo).
  - Por fuentes contradictorias o incompletas: Sherry Hsu 2022, Martin Wölfl 2024, Ryan Wibawa 2024 y
    otras (detalle en el anexo).

### 3.11 Flujo de ejecución

1. El usuario abre Recetas → "Recomendadas". `RecipeCatalogRepository` emite la mejor copia local
   (caché o APK, §3.3) y, si toca, descarga en paralelo.
2. Descarga correcta y válida → se guarda en caché y la lista se actualiza. Fallo → se mantiene la
   lista y se muestra el aviso.
3. Filtro y búsqueda se aplican en memoria.
4. Toca una receta → detalle.
5. "Preparar" → `ReferenceKey.Catalog` → Preparar → sesión → la preparación se guarda con
   `ReferenceType.CATALOG`.
6. "Guardar en mis recetas" → resolución de molienda → `RecipeRepository.save` → etiqueta "Guardada".

## 4. Casos Límite y Manejo de Errores

- Sin red, error HTTP, tiempo agotado → se mantiene la copia actual; aviso "No se pudo actualizar ·
  Última actualización: <fecha>" (o "Usando las recetas incluidas en la app" si nunca se descargó).
- JSON corrupto o sin los campos obligatorios de la raíz → igual que sin red; la caché no se toca.
- `schemaVersion` mayor que el soportado → se ignora la descarga; aviso "Actualiza la app para ver
  las recetas nuevas".
- Receta inválida (valor desconocido, no pasa `RecipeValidator`, falta un idioma) → se descarta esa
  receta en silencio; el resto se muestra.
- Catálogo válido pero sin recetas válidas → no reemplaza la caché (se trata como corrupto).
- Caché ilegible en disco → se usa la copia del APK y se borra la caché.
- Primer arranque sin red → copia del APK, sin error visible más allá del aviso.
- Clave de la referencia activa que desaparece del catálogo → `Resolution.Removed` (aviso actual).
- Preparación con `CATALOG` cuya receta ya no existe → el historial conserva nombre y curva congelados.
- Rotación o salida de la pantalla durante la descarga → la descarga sigue en el ámbito del
  repositorio y la siguiente emisión la recoge; no se duplica.
- Dos recetas con el mismo nombre en "Mis recetas" → sufijo " (2)".
- `sourceUrl` sin navegador que lo abra → mensaje "No hay ninguna app para abrir el enlace".
- Sesión en curso → abrir el catálogo o actualizarlo no afecta a la sesión; cambiar la referencia
  sigue las reglas actuales de Preparar.
- Molino del catálogo de molinos ausente en una versión antigua → la receta se descarta (§3.1).

## 5. Criterios de Aceptación (Definition of Done)

- [ ] `catalog/recipes.json` existe en la raíz con el contenido de §3.10 y pasa su test; el APK
      contiene la misma copia generada en el build.
- [ ] El workflow de catálogo se ejecuta en un `push` que toca `catalog/**` y falla con una receta
      inválida (comprobado con un caso de prueba del parser, no rompiendo `main`).
- [ ] Tests JVM del parser: `schemaVersion` mayor, método y tipo de paso desconocidos, falta un idioma,
      receta que no pasa `RecipeValidator`, claves duplicadas, campos desconocidos ignorados.
- [ ] Tests JVM de la conversión a `Recipe`: tiempos, agua derivada, idioma, `author`, `sourceUrl`,
      `catalogKey`.
- [ ] Tests JVM de la copia: molienda con molino propio, convertible y texto; sufijo " (2)"; aviso de
      copia existente.
- [ ] Tests JVM de la política de copias: 24 h, `force`, `revision` mayor gana, catálogo inválido no
      sustituye a la caché, caché ilegible.
- [ ] Test de migración 7 → 8: columnas nuevas; las recetas de fábrica existentes quedan como propias
      con nombre y pasos traducidos, `catalogKey` y el mismo `id`; las preparaciones que las usaban
      siguen enlazadas.
- [ ] La copia de seguridad exporta e importa `author`, `sourceUrl`, `catalogKey` y `referenceKey`, e
      importa una copia anterior sin esos campos.
- [ ] Instalación nueva: "Mis recetas" vacía con "Ver recomendadas"; no se siembra ninguna receta.
- [ ] Tests instrumentados: selector "Mis recetas / Recomendadas", filtro por método, búsqueda por
      nombre y por autor (sin acentos), detalle, "Preparar" sin guardar (la preparación queda con
      `CATALOG`), "Guardar en mis recetas" con etiqueta "Guardada", pestaña "Recomendadas" en el
      `TargetPicker` en Básico y Experto.
- [ ] El editor de recetas permite editar autor y fuente (validada como URL) y la receta guarda los
      cambios; "Preparar" sin guardar usa la molienda convertida al primer molino compatible.
- [ ] Ajustes ya no muestra "Restaurar recetas predefinidas"; el recorrido guiado señala
      "Recomendadas".
- [ ] La app solo contacta con `raw.githubusercontent.com` y ML Kit no envía telemetría tras añadir
      `INTERNET` (F0 investiga el mecanismo). En el emulador (con root), los contadores de `iptables`
      del UID de la app solo registran conexiones a GitHub tras usar el OCR y descargar el catálogo. En
      el móvil (sin root, release firmada instalada con `adb install -r`), no hay componentes ni
      trabajos de `datatransport` y el UID no transmite bytes al usar el OCR sin abrir "Recomendadas".
- [ ] Verificación manual en el móvil: primer arranque en modo avión muestra la copia del APK;
      deslizar hacia abajo descarga; un cambio publicado en `main` aparece tras actualizar.
- [ ] Textos en español e inglés; detekt/lint sin avisos nuevos; solo se ejecutan los tests del spec.
- [ ] **Enmienda 1:** el catálogo publicado cumple la proporción de §3.10 (al menos 2/3 aptas para
      tueste medio, como máximo 6 solo para claro) y cada receta nueva o variante tiene fuente en el
      anexo.
- [ ] **Enmienda 1:** el test del repo falla con un valor desconocido en `roasts`, con un término
      prohibido o con un tiempo `m:ss` en una instrucción (comprobado con casos del parser o del test,
      no rompiendo `main`), y pasa con el catálogo publicado.
- [ ] **Enmienda 1:** tests JVM del filtro por tueste: "Medio" incluye las recetas sin `roasts`; se
      combina con método y búsqueda; "Quitar filtros" lo restablece a "Todos".
- [ ] **Enmienda 1:** tests instrumentados: filtro "Tueste" en Recomendadas y en el `TargetPicker`,
      etiquetas de tueste en la fila y "Tueste: …" en el detalle.

## 6. Preguntas abiertas

1. **Recetas de confianza media (§3.10):** entran; sus tiempos intermedios suelen ser inferidos. Si
   el usuario las prueba y alguna no convence, se corrige en el JSON sin publicar versión.
2. **Mecanismo para desactivar la telemetría de ML Kit:** lo determina F0.

## 7. Alternativas descartadas

- **Enmienda 1, adaptar las recetas de autor a tueste medio por cuenta propia** (bajar temperatura,
  moler más grueso): falsearía la receta que se atribuye al autor.
- **Enmienda 1, campo de tueste en `Recipe`:** obligaría a migrar Room 8 → 9; el tueste solo sirve
  para filtrar el catálogo.
- **Enmienda 1, filtro de tueste que arranca en "Medio":** ocultaría por defecto las de campeonato.
- **Enmienda 1, solo filtro sin reequilibrar el contenido:** la mayoría seguirían siendo de claro.
- **Catálogo solo en el APK, sin red:** mantiene la app sin `INTERNET`, pero no permite administrar el
  catálogo sin publicar versión.
- **Híbrido con descarga opcional en Ajustes:** más complejo para el usuario; se descartó en favor de
  la descarga directa con copia empaquetada.
- **Sembrar las recomendadas en "Mis recetas":** mezcla recetas de referencia con las propias y obliga
  a sincronizarlas.
- **"Preparar" guarda antes una copia:** llena "Mis recetas" de recetas que solo se prueban una vez.
- **Autor y fuente al final de las notas:** se pierde el dato estructurado.
- **Rama dedicada para el catálogo:** `main` ya es la rama publicada y evita mantener otra.
- **Borrar las recetas de fábrica existentes:** rompe la expectativa de quien ya las usa; se convierten
  en propias.
