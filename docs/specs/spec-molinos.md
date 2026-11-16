# SPEC-006: Molinos y conversión de molienda

| Campo | Valor |
|---|---|
| Estado | Aprobado. Plan aprobado: [`plan-molinos.md`](plan-molinos.md) (sus decisiones D-n se citan en el texto) |
| Fecha | 2026-11-16 |
| Deriva de | La molienda en texto libre (`grind: String?`) de [SPEC-002 Historial](spec-historial.md) y [SPEC-003 Curva objetivo](spec-curva-objetivo.md) |
| Módulos afectados | `:app` (nuevo paquete `dev.dotscale.grinder`, nuevo `ui/grinders`, `history`, `recipe`, `settings`, `ui/recipes`, `ui/history`, `ui/prepare`, `ui/session`, `ui/onboarding`, `ui/settings`, navegación) |
| Specs relacionados | [SPEC-002 Historial](spec-historial.md), [SPEC-003 Curva objetivo](spec-curva-objetivo.md), [SPEC-005 Navegación y modos](spec-navegacion-modos.md) |

## 1. Resumen y Contexto

- **Problema:**
  - La molienda de recetas y preparaciones es un texto libre (`RecipeEntity.grind`,
    `BrewEntity.grind`), con sugerencias como "Media, 21 clics…".
  - La app no sabe con qué molino se definió el ajuste. Si una receta dice "30 clics" en un Timemore
    Chestnut S3 y el usuario prepara con otro molino, tiene que adivinar el ajuste equivalente.
  - No hay forma de registrar los molinos propios ni sus rangos por método.
- **Solución:**
  - **Molinos:**
    - Un catálogo predefinido de solo lectura, con datos de Honest Coffee Guide.
    - Molinos propios, creados desde cero o duplicando uno predefinido.
    - Cada molino declara unidades (incluidas las compuestas), escala, dirección, paso mínimo,
      rango por método y anclas de grosor.
  - **Ajuste estructurado:** recetas y preparaciones guardan molino y ajuste en vez de texto.
  - **Conversión:** al preparar una receta, el usuario elige uno de sus molinos y ve el ajuste
    equivalente. La conversión usa la **posición dentro del rango del método**: el mismo porcentaje
    del rango en el molino de origen y en el de destino.
  - **Convertidor suelto**, para convertir un ajuste sin pasar por una receta.
  - **Métodos nuevos:** Prensa francesa, Espresso, Cold brew y Moka.
- **Éxito:**
  - El usuario administra sus molinos (alta, edición, duplicado, borrado, predeterminado) y define
    los rangos de molienda por método.
  - Con una receta definida en un molino, al elegir otro de "mis molinos" la app muestra el ajuste
    equivalente en el formato del molino destino, incluidos los formatos compuestos.
  - El convertidor suelto da el equivalente de un ajuste en los molinos del usuario.
- **Alcance (In):**
  - Catálogo predefinido (§3.6) sembrado desde un archivo versionado, con fuente visible.
  - Ajustes → Molinos: lista de "mis molinos", alta, edición, duplicado, borrado y molino
    predeterminado.
  - Editor de molino con dos pestañas, Descripción y Rangos.
  - Formatos compuestos (por ejemplo, rotaciones · números · clics).
  - Selector de molino y ajuste en el editor de recetas y en "Editar preparación".
  - Tarjeta de molienda en Preparar y en el paso 2 (Dosis) de los pasos previos de la sesión, con
    conversión al molino elegido. En modo Experto, además, una regla de grueso a fino.
  - La preparación guarda el molino y el ajuste usados.
  - Convertidor suelto.
  - Paso opcional "¿Qué molino usas?" en el onboarding.
  - `BrewMethod` gana `FRENCH_PRESS`, `ESPRESSO`, `COLD_BREW` y `MOKA`.
  - Migración Room de v4 a v5.
- **Alcance (Out):**
  - Conservar o interpretar el texto libre `grind` actual: no hay datos reales, así que se elimina.
  - Recetas integradas nuevas para los métodos añadidos.
  - Conversión por micras, como unidad común entre molinos.
  - Datos colaborativos, sincronización o descarga del catálogo desde internet.
  - Ajustar el ajuste a mano dentro de la sesión. Se corrige después en "Editar preparación".

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como usuario, quiero registrar los molinos que tengo, eligiéndolos del catálogo o
  creándolos, para que la app sepa con qué muelo.
- **US-2:** Como usuario, quiero definir para cada molino propio su escala y el rango de molienda de
  cada método, para que las conversiones reflejen mi molino.
- **US-3:** Como usuario, quiero guardar en una receta el molino y el ajuste exactos (por ejemplo,
  "Chestnut S3 · 30 clics"), en vez de un texto libre.
- **US-4:** Como usuario, quiero que al preparar una receta se muestre su ajuste y pueda elegir otro
  de mis molinos para ver el ajuste equivalente.
- **US-5:** Como usuario, quiero que la preparación recuerde con qué molino y qué ajuste la hice.
- **US-6:** Como usuario, quiero un convertidor para saber el equivalente de un ajuste en mis otros
  molinos sin abrir una receta.
- **US-7:** Como usuario nuevo, quiero indicar mi molino en el onboarding, o saltarme ese paso.
- **US-8:** Como usuario de un molino con formato compuesto (por ejemplo, 1Zpresso JX-Pro), quiero
  ver e introducir el ajuste en su formato, por ejemplo "1 · 2 · 3".
- **US-9:** Como usuario, quiero personalizar un molino del catálogo sin perder los datos originales,
  duplicándolo.
- **US-10:** Como usuario de prensa francesa, espresso, cold brew o moka, quiero elegir esos métodos
  en recetas, preparaciones y filtros del historial.

## 3. Especificación Técnica

### 3.1 Modelo de molino

- **Unidades.** Enum `GrindUnit { CLICKS, POINTS, NOTCHES, NUMBERS, ROTATIONS, STEPS }`, que en la
  interfaz son Clics, Puntos, Muescas, Números, Rotaciones y Pasos.
- **Jerarquía de unidades.** Cada molino tiene una lista ordenada de unidades, de la más gruesa a la
  más fina:
  - La posición 0 es la **unidad primaria**.
  - Cada unidad secundaria declara `perParent`, el número de unidades suyas que forman una de la
    anterior. Por ejemplo, en un molino de prueba: rotaciones; números con `perParent = 4`; clics
    con `perParent = 10`. La jerarquía real de cada predefinido sale del formato de la fuente (por
    ejemplo, `rotation.number.tick` en el JX-Pro) y se confirma en la revisión del catálogo (§6).
  - La última unidad es la **unidad base**.
  - Un molino con una sola unidad (por ejemplo, solo clics) es el caso simple.
- **Valor de un ajuste.** Es un único `Double` en unidades base (`grindValue`). Para mostrarlo se
  descompone según la jerarquía; para introducirlo se compone.
- **Escala.** `scaleMin` y `scaleMax` en unidades base, que son los límites físicos del molino.
- **Paso mínimo.** `step` en unidades base, con 1 por defecto. Admite fracciones (por ejemplo, 0,5)
  para molinos con medios pasos.
  - Los molinos **sin pasos** (stepless, por ejemplo Niche Zero o DF64) se modelan igual: la marca
    del dial como unidad y un paso fraccionario (por ejemplo, 0,1). No hay soporte especial para
    vueltas continuas ni grados del dial.
  - Da igual que el molino sea manual o eléctrico.
- **Dirección.** Enum `GrindDirection { HIGHER_IS_COARSER, HIGHER_IS_FINER }`, con
  `HIGHER_IS_COARSER` por defecto.
- **Rangos por método.** Para cada `BrewMethod`, menos `OTHER`, un rango opcional `[min, max]` en
  unidades base, dentro de la escala.
- **Anclas de grosor.** Enum `GrindLevel { EXTRA_FINE, FINE, MEDIUM_FINE, MEDIUM, MEDIUM_COARSE,
  COARSE, EXTRA_COARSE }`, que son las 7 bandas de Honest Coffee Guide. Cada ancla es opcional y es
  un valor en unidades base.
  - Deben ser monótonas en el sentido de la dirección: más grueso significa un valor mayor en
    `HIGHER_IS_COARSER`.
- **Origen:**
  - Un molino **predefinido** tiene `catalogKey` (clave estable, por ejemplo
    `timemore-chestnut-c3s`), `sourceUrl` y es de solo lectura.
  - Un molino **propio** tiene `catalogKey = null` y es editable. Si es un duplicado, guarda
    `duplicatedFromKey`.
- **"Mis molinos".** Son los molinos con `owned = true`. Los propios siempre lo son; un predefinido
  lo es cuando el usuario lo marca como "Lo tengo".
- **Retirado.** Un predefinido que desaparece del catálogo y está en uso queda con `retired = true`.

### 3.2 Formato compuesto

- **Funciones puras**, sin Android, en `dev.dotscale.grinder`:
  - `GrindFormat.decompose(value, units): List<Int|Double>`.
  - `GrindFormat.compose(parts, units): Double`.
  - `GrindFormat.format(value, grinder, texts): String`. `texts` (`GrindTexts`) aporta los nombres
    de unidad con plurales por idioma, para que el dominio no dependa de Android (plan D12).
- **En pantalla:**
  - Forma corta con las partes separadas por " · ", por ejemplo "1 · 2 · 3".
  - Etiquetas de unidad bajo cada parte, como en los steppers de las imágenes de referencia.
  - Molino de una sola unidad: el número con su unidad ("30 clics").
  - La descripción para accesibilidad usa la forma larga ("1 rotación, 2 números, 3 clics").
- **Introducción.** Un stepper +/− por unidad. Al pasar de `perParent - 1` en una unidad secundaria,
  se acarrea a la unidad superior, y al bajar de 0 se toma de ella. El valor resultante se limita a
  la escala.
- **Ida y vuelta:** `compose(decompose(v)) == v` para cualquier `v` de la escala que sea múltiplo del
  paso.

### 3.3 Conversión

- **Función pura** `GrindConverter.convert(value, method, from: Grinder, to: Grinder):
  Conversion`. Su resultado es una de estas tres:
  - `Exact(value)`;
  - `Approximate(value, reasons)`;
  - `NoEquivalent`.

  Las razones de `reasons` pueden ser `ANCHORS`, `ROUNDED` y `AT_LIMIT`.
- **Coordenada de grosor.** Toda posición se mide en el sentido de fino a grueso. En
  `HIGHER_IS_FINER`, los extremos del rango y de las anclas se invierten antes de calcular, de modo
  que la posición 0 siempre es el extremo fino.
- **Regla principal (modelo A, por rango del método).** Se aplica si los dos molinos tienen rango
  para el `method`:
  1. `p = (v − fino_origen) / (grueso_origen − fino_origen)`, donde los extremos son los del rango
     del método.
  2. `v' = fino_destino + p × (grueso_destino − fino_destino)`.
  3. Si `p` cae fuera de [0, 1], se **extrapola** sin marcarlo: un ajuste fuera del rango
     recomendado es válido.
- **Respaldo por anclas.** Se usa si falta el rango del método en alguno de los dos molinos, o si el
  método es `OTHER` o nulo:
  1. Se toman las anclas definidas **en los dos** molinos (al menos 2 en común).
  2. Se interpola linealmente por tramos: el valor de origen se pasa a la coordenada de nivel
     (`EXTRA_FINE = 0` … `EXTRA_COARSE = 6`), y ese nivel se pasa al valor de destino.
  3. Fuera del tramo cubierto se extrapola con el tramo más cercano.
  4. El resultado lleva la razón `ANCHORS`, y la interfaz lo muestra como **"aproximado"**.
- **Sin equivalencia.** Si no aplica ni la regla principal ni el respaldo, el resultado es
  `NoEquivalent`.
- **Límite físico.** Si `v'` sale de `[scaleMin, scaleMax]` del destino, se limita al extremo y se
  añade `AT_LIMIT`. La interfaz muestra "al límite del molino". **Es el único caso que se avisa como
  fuera de rango.**
- **Redondeo.** `v'` se redondea al múltiplo de `step` más cercano, contado desde `scaleMin`. Si el
  redondeo cambia el valor, se añade `ROUNDED` y el valor se muestra con el prefijo "≈".
- **Mismo molino.** Si el origen y el destino son el mismo molino, el resultado es `Exact(value)`
  sin cálculo.
- **Receta sin ajuste.** Con un molino elegido, si la receta no tiene molino ni ajuste y ese molino
  tiene rango para el método, se propone el **punto medio del rango**, redondeado al paso, con la
  etiqueta "sugerido". Si el molino no tiene rango para el método, la tarjeta muestra "La receta no
  indica molienda", sin sugerencia.

### 3.4 Modelo de datos (Room, `HistoryDatabase` de v4 a v5)

- **Tablas nuevas:**

  | Tabla | Columnas | Claves e índices |
  |---|---|---|
  | `grinders` | `id` Long, `catalogKey` String?, `brand` String, `model` String, `direction` GrindDirection, `scaleMin` Double, `scaleMax` Double, `step` Double, `owned` Boolean, `retired` Boolean, `sourceUrl` String?, `duplicatedFromKey` String?, `updatedAtEpochMs` Long | PK `id`; índice único sobre `catalogKey` (varias filas pueden tenerlo `null`) |
  | `grinder_units` | `grinderId` Long, `position` Int, `unit` GrindUnit, `perParent` Int? | PK (`grinderId`, `position`); FK a `grinders` con `CASCADE` |
  | `grinder_method_ranges` | `grinderId` Long, `method` BrewMethod, `min` Double, `max` Double | PK (`grinderId`, `method`); FK con `CASCADE` |
  | `grinder_anchors` | `grinderId` Long, `level` GrindLevel, `value` Double | PK (`grinderId`, `level`); FK con `CASCADE` |

- **`recipes` y `brews`:**
  - Se elimina `grind`.
  - Se añaden `grinderId` (Long?, FK a `grinders` con `SET_NULL`, indexado), `grindValue` (Double?)
    y `grindLabel` (String?).
  - `grindLabel` es la **copia de texto** del molino y el ajuste formateado (por ejemplo,
    "Chestnut S3 · 30 clics"). Se regenera cada vez que se guarda la receta o la preparación.
- **Mostrar el ajuste guardado:**
  - Si `grinderId` existe, se muestra con los datos vivos del molino.
  - Si es `null` y hay `grindLabel`, se muestra `grindLabel` con la marca "molino eliminado".
  - Una receta en ese estado **no se puede convertir** hasta que se le asigne un molino al editarla.
- **Migración 4 → 5:**
  - Se crean las tablas nuevas.
  - `recipes` y `brews` se reconstruyen sin `grind` y con las columnas nuevas a `NULL`. El texto
    anterior se descarta.
  - Se conservan todos los demás datos y las FK existentes (`coffeeId`, etapas, etc.).
  - El catálogo se siembra después de la migración, no dentro de ella (§3.6).
- **`BrewMethod`:**
  - Pasa a `{ V60, KALITA_WAVE, CHEMEX, ORIGAMI, AEROPRESS, FRENCH_PRESS, ESPRESSO, COLD_BREW,
    MOKA, OTHER }`.
  - Se guarda por nombre (`HistoryConverters`), así que no requiere migración de datos.
  - Los nombres visibles se añaden en `HistoryFormatters`: "Prensa francesa", "Espresso", "Cold
    brew" y "Moka".
- **Recetas integradas.** `BuiltInRecipes` y el `INSERT` de `BuiltInRecipeSeeder` dejan de llevar
  `grind`, y las recetas integradas no tienen molino.
- **Preferencias.** Se añade `default_grinder_id` (Long?) al `SharedPreferences "settings"`, con una
  clase `GrinderSettings` expuesta como `StateFlow`, igual que `ExperienceSettings`. También se
  guarda `grinder_catalog_version` (Int) para el sembrado.

### 3.5 Contratos del paquete `dev.dotscale.grinder`

- `Grinder` es el modelo de dominio: molino con unidades, rangos y anclas.
- `GrindFormat` y `GrindConverter` son Kotlin puro (§3.2 y §3.3).
- `GrinderValidator` valida un molino:
  - `scaleMin < scaleMax`, `step > 0`;
  - la escala es múltiplo del paso (plan D15);
  - un molino compuesto no admite escala negativa (plan D15);
  - la unidad primaria no lleva `perParent` y cada secundaria tiene `perParent ≥ 2`;
  - unidades sin repetir;
  - rangos con `min < max` y dentro de la escala;
  - anclas dentro de la escala y estrictamente monótonas según la dirección;
  - marca y modelo no vacíos.
- **Escrituras con molino.** Toda escritura que vincule un molino a una receta o preparación
  comprueba que el molino exista, como con el café en SPEC-004 (plan D18).
- `GrinderDao` y `GrinderRepository` exponen, como `Flow`:
  - los molinos propios;
  - el catálogo completo;
  - un molino por id;
  - alta, edición, duplicado, borrado y marcado como "Lo tengo";
  - el **número de recetas que usan** un molino, para el aviso de borrado.
- `GrinderCatalogSeeder` lee el archivo del catálogo y sincroniza los predefinidos (§3.6).

### 3.6 Catálogo predefinido

- **Archivo.** Un archivo de datos versionado en los assets de `:app` (por ejemplo,
  `assets/grinders/catalog.json`), con un `version: Int` y una lista de molinos. Cada molino lleva:
  - `key`, `brand` y `model`;
  - las unidades;
  - la escala, el paso y la dirección;
  - el rango en micras;
  - los rangos por método;
  - las anclas;
  - `sourceUrl`.
- **Molinos incluidos:** Timemore Chestnut C2, C3/C3S y S3; Comandante C40; 1Zpresso JX-Pro,
  K-Ultra y Q2; Kingrinder K6; Baratza Encore; Fellow Ode Gen 2; Femobook A2.
- **Fuente.** Las páginas por molino de Honest Coffee Guide (por ejemplo,
  `https://honestcoffeeguide.com/timemore-c2-grind-settings/`). Dan el rango total en micras y los
  rangos recomendados en clics para V60, Pour over, AeroPress, French press, Espresso y otros
  métodos.
- **Correspondencia de métodos (fuente → `BrewMethod`):**

  | `BrewMethod` | Rango de la fuente |
  |---|---|
  | `V60` | V60 |
  | `KALITA_WAVE`, `CHEMEX`, `ORIGAMI` | Pour over |
  | `AEROPRESS` | AeroPress |
  | `FRENCH_PRESS` | French press |
  | `ESPRESSO` | Espresso |
  | `COLD_BREW` | Cold brew |
  | `MOKA` | Moka pot |
  | `OTHER` | Sin rango (usa las anclas) |

- **Anclas de los predefinidos.** Se calculan al preparar el archivo, no en la app, suponiendo que
  el molino es lineal entre su rango en micras y su escala. Para cada ancla se toma el punto medio
  de su banda de la fuente:

  | Ancla | Banda de la fuente | Punto medio |
  |---|---|---|
  | `EXTRA_FINE` | 0–200 µm | 100 µm |
  | `FINE` | 200–400 µm | 300 µm |
  | `MEDIUM_FINE` | 400–600 µm | 500 µm |
  | `MEDIUM` | 600–800 µm | 700 µm |
  | `MEDIUM_COARSE` | 800–1000 µm | 900 µm |
  | `COARSE` | 1000–1200 µm | 1100 µm |
  | `EXTRA_COARSE` | 1200–1400 µm | 1300 µm |

  Un ancla que cae fuera del rango en micras del molino se omite.
- **Puerta de revisión.**
  - Al terminar la investigación, el implementador **se detiene** y presenta una tabla por molino:
    unidades, escala, paso, dirección, rangos por método, anclas, URL de fuente y datos ausentes.
  - **No continúa hasta que el usuario la apruebe o la corrija.**
  - Los datos que la fuente no tenga se marcan como ausentes; no se inventan.
  - La tabla vive solo en el JSON del catálogo y en el informe del implementador, sin documento
    aparte.
- **Sembrado y actualización:**
  - Al arrancar, si `version` del archivo es mayor que `grinder_catalog_version`, se hace un upsert
    de los predefinidos por `catalogKey`. Se reemplazan sus unidades, rangos y anclas, y se conserva
    `owned`.
  - Los molinos propios, incluidos los duplicados, no se tocan.
  - Un predefinido que ya no está en el archivo se borra si nadie lo usa (sin recetas, sin
    preparaciones, `owned = false` y no predeterminado). Si se usa, se marca `retired = true`.
  - Después se guarda la nueva `grinder_catalog_version`.
- **Fuente visible.** En el detalle de un molino predefinido y en el resultado de cualquier
  conversión en la que intervenga uno, se muestra "Fuente: Honest Coffee Guide · valores
  orientativos", con enlace a `sourceUrl`.

### 3.7 Interfaz

- **Ajustes → Molinos** (nueva entrada en `SettingsScreen`):
  - Lista de "mis molinos": marca y modelo, unidad primaria y una etiqueta "Predeterminado" en el
    que lo es.
  - "Añadir molino" abre el catálogo, con búsqueda por marca o modelo. Cada fila tiene "Lo tengo"
    y, al final, "Crear molino propio".
  - **Detalle de un molino:**
    - Escala, unidades, regla con los rangos por método y anclas, fuente si es predefinido.
    - Acciones: "Usar como predeterminado", "Duplicar para personalizar", "Editar" (solo propios),
      "Quitar de mis molinos" (predefinidos) y "Borrar" (propios).
- **Editor de molino** (solo molinos propios), con dos pestañas, como en las imágenes de
  referencia:
  - **Descripción:** fabricante, modelo, unidad primaria (seis opciones), "Agregar unidad" (unidad
    secundaria con su `perParent`), paso mínimo y dirección.
  - **Rangos:**
    - Escala total (mínimo y máximo).
    - Por cada método, un rango opcional con steppers "de" y "a".
    - Las siete anclas opcionales.
    - Una regla de vista previa con la escala, el rango del método seleccionado y las anclas.
  - "Guardar" valida con `GrinderValidator` y muestra los errores junto al campo.
- **Regla de molienda** (componente reutilizable):
  - Escala horizontal, **siempre de grueso (izquierda) a fino (derecha)** sea cual sea la
    dirección del molino, con marcas numeradas.
  - Rango del método resaltado con su nombre, marcador del ajuste actual y extremos "Grueso" y
    "Fino".
- **Recetas** (`RecipeEditScreen`) y **"Editar preparación"** (`BrewEditScreen`):
  - El campo de texto "Molienda" se sustituye por un selector de molino (mis molinos más "Sin
    molino") y la introducción compuesta del ajuste.
  - En una receta nueva, el molino preseleccionado es el predeterminado.
  - Se admite **molino sin ajuste** (plan D16).
- **Detalle de receta y de preparación.** Muestran "Molino · ajuste" (o la copia con "molino
  eliminado"). No existe una pantalla de detalle de receta aparte: el "detalle de receta" es
  `RecipeEditScreen` (plan D8).
- **Tarjeta de molienda en Preparar y en el paso 2 (Dosis) de `PrepFlow`:**
  - **Básico:**
    - Molino de la receta y su ajuste.
    - Selector "Moler con" con mis molinos.
    - El ajuste del molino elegido en grande (convertido, "sugerido", "aproximado", "≈" o "al
      límite del molino" según el caso).
  - **Experto:** lo mismo, más la regla de molienda del molino elegido.
  - **Molino preseleccionado** en "Moler con":
    1. el de la receta, si es de mis molinos;
    2. si no, el predeterminado;
    3. si no, el primero de mis molinos.
  - **Sin molinos propios:** se muestra el ajuste original sin selector y el enlace "Añade tu molino
    para ver el equivalente", que lleva a Ajustes → Molinos.
  - **Sin equivalencia:** "Sin equivalencia" y un enlace para completar los rangos (molino propio) o
    duplicarlo (predefinido).
  - **Sin receta o receta sin método:** se aplica la regla de anclas o "Sin equivalencia" (§3.3).
  - **Origen de la tarjeta:** la receta elegida o, si el objetivo de la sesión es una preparación
    guardada, esa preparación (plan D24).
- **Al terminar la preparación.** Se guardan en la preparación el molino elegido en "Moler con" y el
  ajuste mostrado (convertido o sugerido), con su `grindLabel`. Si no hay ajuste que mostrar (sin
  receta, o receta sin molienda con un molino sin rango), se guarda **el molino sin ajuste** y se
  completa en "Editar preparación" (plan D16). Si no hay molino elegido, los campos quedan a `null`.
- **Convertidor suelto:**
  - Está en Ajustes → Molinos → "Convertidor".
  - **Atajo "Ver en mis otros molinos"** en la tarjeta de molienda y en el detalle de la receta.
    Abre el convertidor ya relleno con el molino, el método y el ajuste de origen. No aparece si la
    receta no tiene molino ni ajuste.
  - Entradas: molino de origen (cualquiera del catálogo o propio), método y ajuste, con la
    introducción compuesta.
  - Salida: una fila por cada molino de "mis molinos" distinto del origen, con el ajuste equivalente
    y su marca (aproximado, ≈, al límite del molino o sin equivalencia). Solo se listan mis molinos,
    sin opción de ver todo el catálogo.
  - La fuente se muestra si interviene un predefinido.
- **Onboarding** (`OnboardingScreen`):
  - Después de elegir Básico o Experto, aparece el paso opcional **"¿Qué molino usas?"**, con la
    lista del catálogo y búsqueda.
  - Elegir un molino lo marca como "Lo tengo" y como predeterminado. "Omitir" sigue sin elegir.
  - `onboarding_done` se marca al terminar o al omitir ese paso.
  - Las instalaciones con `onboarding_done = true` no lo ven.
  - "Saltar" en el primer paso (el modo) termina el onboarding en Básico sin preguntar por el
    molino (plan D6).
- **Métodos nuevos.** Aparecen en los selectores de método de recetas y preparaciones y en el filtro
  de método del historial. Las tarjetas para compartir no muestran el método, así que no cambian.

### 3.8 Flujo de ejecución (camino principal)

1. En el onboarding, el usuario elige el modo y, en "¿Qué molino usas?", el Timemore Chestnut S3,
   que queda en mis molinos y como predeterminado.
2. En Ajustes → Molinos, añade "Lo tengo" al Comandante C40.
3. Crea una receta V60 con "Chestnut S3 · 30 clics".
4. En Preparar elige esa receta. La tarjeta de molienda muestra "Chestnut S3 · 30 clics" y, en
   "Moler con", el Chestnut S3.
5. Cambia "Moler con" a Comandante C40. `GrindConverter` calcula la posición de 30 clics en el rango
   V60 del S3 y la aplica al rango V60 del C40. La tarjeta muestra el ajuste del C40 y la fuente.
6. Empieza la sesión. En el paso 2 (Dosis) ve la misma tarjeta, con la regla si está en Experto.
7. Al terminar, la preparación se guarda con `grinderId` del C40, el ajuste convertido y
   `grindLabel`.

## 4. Casos Límite y Manejo de Errores

- **Borrar un molino propio usado en recetas.** Aviso "Se usa en N recetas". Al confirmar:
  - antes de borrar, se regenera `grindLabel` en las recetas y preparaciones que lo usan, para que
    la copia lleve el nombre actual (plan D17);
  - las recetas y preparaciones quedan con `grinderId = null` y conservan `grindValue` y
    `grindLabel`, que se muestran con "molino eliminado";
  - si era el predeterminado, `default_grinder_id` pasa a `null`.
- **Quitar un predefinido de mis molinos.** Solo cambia `owned`. Las recetas y preparaciones que lo
  usan siguen mostrándolo con datos vivos.
- **Editar las unidades o la escala de un molino propio.** Los `grindValue` guardados no se
  modifican. Si alguno queda fuera de la nueva escala, se muestra con un aviso, sin corregirlo.
- **Molino destino sin rango para el método y sin anclas suficientes** (menos de 2 en común):
  "Sin equivalencia", con el enlace para completar o duplicar.
- **Receta con método `OTHER` o sin método.** Solo puede usar el respaldo de anclas.
- **Resultado fuera de la escala del destino.** Se limita al extremo, con "al límite del molino".
- **Ajuste fuera del rango del método.** Se extrapola sin aviso.
- **Paso fraccionario.** El redondeo y el acarreo de la introducción compuesta respetan `step`. El
  valor nunca queda fuera de los múltiplos de `step` contados desde `scaleMin`.
- **Archivo del catálogo ilegible o inválido.** No se toca la base de datos ni
  `grinder_catalog_version`; se registra el error en logcat y la app sigue con el catálogo que ya
  tenía. El test del §5 impide publicar un archivo inválido.
- **Predefinido retirado del catálogo.** Se conserva con `retired = true` si está en uso. Ya no
  aparece en "Añadir molino", pero sigue visible en mis molinos y en los detalles.
- **Sin molinos propios.** La tarjeta de molienda muestra el ajuste original sin selector y el
  enlace "Añade tu molino para ver el equivalente". El convertidor muestra un estado vacío con el
  mismo enlace.
- **Molino predeterminado borrado o quitado de mis molinos.** `default_grinder_id` pasa a `null`.
- **Rendimiento.** La conversión es aritmética local, sin E/S. La tarjeta de molienda no debe
  empeorar el `SessionPerformanceTest` (umbral p95 de 32 ms).
- **Seguridad y privacidad.** Todo es local. No hay red ni permisos nuevos, y el enlace a la fuente
  se abre en el navegador del sistema.

## 5. Criterios de Aceptación (Definition of Done)

### Datos y migración
- [ ] `HistoryDatabase` está en la versión 5. `HistoryMigrationTest` cubre la migración 4 → 5: crea
      las tablas nuevas, quita `grind` de `recipes` y `brews`, añade `grinderId`, `grindValue` y
      `grindLabel`, y conserva el resto de columnas, filas y FK.
- [ ] `BrewMethod` incluye `FRENCH_PRESS`, `ESPRESSO`, `COLD_BREW` y `MOKA`, con nombre visible,
      selectores de recetas y preparaciones, y filtro del historial.
- [ ] Las recetas integradas ya no tienen `grind` y se siembran sin molino.

### Catálogo
- [ ] La tabla de datos de los molinos del §3.6 está revisada y aprobada por el usuario antes de
      sembrarla.
- [ ] Un test unitario valida el archivo del catálogo:
  - cada molino pasa `GrinderValidator`;
  - las claves son únicas;
  - cada molino tiene `sourceUrl`;
  - los rangos caben en la escala;
  - las anclas son monótonas.
- [ ] `GrinderCatalogSeeder`:
  - siembra en el primer arranque;
  - con una `version` mayor, actualiza por `catalogKey` sin tocar los propios ni `owned`;
  - marca `retired` los retirados en uso;
  - borra los retirados sin uso.

  Todo cubierto por tests.

### Conversión y formato (tests unitarios JVM)
- [ ] Conversión lineal dentro del rango, con el mismo porcentaje de posición en los dos molinos.
- [ ] Extrapolación fuera del rango del método, sin `AT_LIMIT` mientras quepa en la escala.
- [ ] Límite físico: el valor se queda en el extremo y lleva `AT_LIMIT`.
- [ ] Respaldo por anclas, con la razón `ANCHORS`, cuando falta el rango o el método es `OTHER`.
- [ ] `NoEquivalent` sin rango y con menos de 2 anclas en común.
- [ ] Redondeo al paso (entero y 0,5), con `ROUNDED` solo cuando cambia el valor.
- [ ] Molino `HIGHER_IS_FINER`: la conversión entre un molino invertido y uno normal mantiene la
      posición de fino a grueso.
- [ ] Mismo molino como origen y destino: `Exact` con el mismo valor.
- [ ] Punto medio "sugerido" para una receta sin ajuste.
- [ ] Formato compuesto: "1 · 2 · 3" en un molino de prueba con rotaciones, números con
      `perParent = 4` y clics con `perParent = 10` da 63 clics y vuelve a "1 · 2 · 3". El acarreo del stepper cruza la
      unidad superior en los dos sentidos.
- [ ] `GrinderValidator` rechaza cada regla del §3.5 con un caso de prueba.

### Interfaz (tests instrumentados de Compose en el AVD `SG_API34`)
- [ ] Crear un molino propio con unidad compuesta y rangos, guardarlo y verlo en mis molinos.
- [ ] Duplicar un predefinido: la copia es editable y el original sigue de solo lectura.
- [ ] Onboarding: elegir un molino lo deja como predeterminado; "Omitir" termina sin molino.
- [ ] Tarjeta de molienda en Preparar y en el paso 2 de la sesión:
  - en Básico, sin regla;
  - en Experto, con regla;
  - cambiar "Moler con" muestra el ajuste convertido;
  - sin molinos propios, el enlace "Añade tu molino".
- [ ] La preparación guardada muestra el molino y el ajuste usados.
- [ ] Convertidor suelto: con origen, método y ajuste, lista el equivalente en cada uno de mis
      molinos.
- [ ] El atajo "Ver en mis otros molinos", desde la tarjeta de molienda y desde el detalle de la
      receta, abre el convertidor relleno con el molino, el método y el ajuste de origen.
- [ ] Borrar un molino usado en recetas muestra "Se usa en N recetas". Tras confirmar, la receta
      muestra la copia con "molino eliminado".
- [ ] La fuente "Honest Coffee Guide · valores orientativos" aparece en el detalle de un predefinido
      y en una conversión en la que interviene uno.
- [ ] `SessionPerformanceTest` no empeora respecto a `develop`.

### Cierre
- [ ] `./gradlew :app:testDebugUnitTest` y `:app:connectedDebugAndroidTest` pasan, salvo los fallos
      previos conocidos (`CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap`, por el
      idioma del emulador).
- [ ] El lint y el formato del proyecto pasan en los archivos tocados.
- [ ] Hay una revisión manual con capturas por adb (`screencap`) de:
  - mis molinos;
  - el editor (las dos pestañas);
  - la tarjeta en Básico y en Experto;
  - el convertidor;
  - el paso del onboarding.

## 6. Preguntas abiertas

- **Cobertura de la fuente.** Puede que Honest Coffee Guide no tenga todos los molinos de la lista
  (por ejemplo, el Femobook A2 o el Chestnut S3) o todos los métodos (por ejemplo, la Moka o el Cold
  brew en algunos molinos). Lo que falte se resuelve en la revisión de la tabla del §3.6: se
  completa con otra fuente citada, se deja sin rango o se quita el molino.
- **Unidades compuestas de los predefinidos.** Hay que confirmar con la fuente o con el fabricante
  la jerarquía real de cada molino (por ejemplo, cuántos números por rotación y cuántos clics por
  número en los 1Zpresso, o los pasos intermedios del Fellow Ode Gen 2).
- **Linealidad de las anclas.** Las anclas de los predefinidos suponen que cada molino es lineal
  entre su mínimo y su máximo en micras. Si la revisión muestra desvíos claros, se pueden ajustar a
  mano en la tabla.

## 7. Alternativas descartadas

- **Conversión por micras (unidad común).** Es más precisa en teoría, pero requiere micras por
  clic fiables para cada molino, y la fuente no las da. El modelo de posición dentro del rango del
  método solo depende de rangos que la fuente sí publica y que el usuario puede editar.
- **Molinos predefinidos editables con "Restaurar valores de fábrica".** Se descarta porque una
  actualización del catálogo chocaría con los cambios del usuario. Se usa solo lectura con
  "Duplicar para personalizar".
- **Migración destructiva de la base de datos.** Se descarta para mantener probada la cadena de
  migraciones v1 → v5.
- **Conservar el texto libre `grind` como "nota de molienda".** No hay datos reales que conservar.
