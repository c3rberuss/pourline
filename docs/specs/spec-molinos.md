# SPEC-006: Molinos y conversión de molienda

| Campo | Valor |
|---|---|
| Estado | Implementado (2026-11-16, `feat/grinders`). Plan: [`plan-molinos.md`](plan-molinos.md) (sus decisiones D1 a D39 están en el §3.9 de este spec). **Enmienda 1** (conversión por calibración y curva de grosor): Aprobado, pendiente de plan (2026-11-17) |
| Fecha | 2026-11-16. Enmienda 1: 2026-11-17 |
| Enmienda 1 | Sustituye la conversión por posición dentro del rango del método (§3.3) por una equivalencia independiente del método: pares de calibración entre mis molinos y una curva de grosor por molino. Cambia §1, §2, §3.3 a §3.8, D5, D14, D25, D26 y D39, §4, §5, §6 y §7. Motivo: entre el Chestnut S3 y el Femobook A2, "Sin método", Espresso y Cold brew daban "Sin equivalencia" (el A2 no tiene anclas), y el mismo ajuste del S3 (20 clics) daba 14, 43, 55, 60 o 62 en el A2 según el método |
| Deriva de | La molienda en texto libre (`grind: String?`) de [SPEC-002 Historial](spec-historial.md) y [SPEC-003 Curva objetivo](spec-curva-objetivo.md) |
| Módulos afectados | `:app` (nuevo paquete `dev.josueamaya.pourline.grinder`, nuevo `ui/grinders`, `history`, `recipe`, `settings`, `ui/recipes`, `ui/history`, `ui/prepare`, `ui/session`, `ui/onboarding`, `ui/settings`, navegación) |
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
    equivalente. La conversión **no depende del método** (enmienda 1): usa los **pares de
    calibración** que el usuario haya comprobado entre sus molinos ("S3 25 = A2 60") y, si no los
    hay, la **curva de grosor** de cada molino (sus anclas o, sin ellas, un ajuste lineal de sus
    rangos).
  - **Convertidor suelto**, para convertir un ajuste sin pasar por una receta.
  - **Métodos nuevos:** Prensa francesa, Espresso, Cold brew y Moka.
- **Éxito:**
  - El usuario administra sus molinos (alta, edición, duplicado, borrado, predeterminado) y define
    los rangos de molienda por método.
  - Con una receta definida en un molino, al elegir otro de "mis molinos" la app muestra el ajuste
    equivalente en el formato del molino destino, incluidos los formatos compuestos.
  - El convertidor suelto da el equivalente de un ajuste en los molinos del usuario.
  - Un mismo ajuste de origen da **un único** equivalente en cada molino, sea cual sea el método
    (enmienda 1).
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
  - Enmienda 1: pares de calibración entre mis molinos (convertidor y detalle del molino), curva de
    grosor por molino con anclas implícitas para los molinos sin anclas, y migración Room de v6 a
    v7.
- **Alcance (Out):**
  - Conservar o interpretar el texto libre `grind` actual: no hay datos reales, así que se elimina.
  - Recetas integradas nuevas para los métodos añadidos.
  - Conversión por micras, como unidad común entre molinos.
  - Datos colaborativos, sincronización o descarga del catálogo desde internet.
  - Ajustar el ajuste a mano dentro de la sesión. Se corrige después en "Editar preparación".
  - Enmienda 1: encadenar calibraciones a través de un tercer molino; mostrar las anclas implícitas
    en la regla de molienda o en el editor; pares de calibración con molinos que no son míos.

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
- **US-11 (enmienda 1):** Como usuario con dos molinos, quiero guardar las equivalencias que he
  comprobado moliendo (por ejemplo, "Chestnut S3 25 = Femobook A2 60"), para que la conversión entre
  ellos use mis datos y no los de terceros.
- **US-12 (enmienda 1):** Como usuario, quiero que el mismo ajuste dé siempre el mismo equivalente,
  sin depender del método, y que haya equivalencia aunque uno de mis molinos no tenga anclas.

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

- **Funciones puras**, sin Android, en `dev.josueamaya.pourline.grinder`:
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

### 3.3 Conversión (reescrita por la enmienda 1)

- **Función pura** `GrindConverter.convert(value, from: Grinder, to: Grinder, pairs:
  List<CalibrationPoint>): Conversion`. **Ya no recibe el método.**
  - `pairs` son los pares de calibración entre `from` y `to`, orientados de origen a destino:
    `CalibrationPoint(fromValue, toValue)` (§3.3.1).
  - Su resultado es una de estas tres:
    - `Exact(value)`;
    - `Approximate(value, reasons)`;
    - `NoEquivalent`.
  - Las razones de `reasons` pueden ser `CALIBRATED`, `ANCHORS`, `ROUNDED` y `AT_LIMIT`.
- **Se elimina la regla por rango del método** (el antiguo "modelo A"). Daba equivalentes distintos
  para un mismo ajuste según el método, porque los rangos de cada método no son coherentes entre
  molinos (§7). El método solo sirve ya para el ajuste **sugerido** y para la banda de la regla de
  molienda.
- **Orden de las reglas.** Se aplica la primera que corresponda:
  1. **Mismo molino** (mismo `id` distinto de 0): `Exact(value)` sin cálculo.
  2. **Calibración con 2 o más pares.** Se interpola linealmente por tramos sobre los pares,
     ordenados por `fromValue`, y fuera de ellos se extrapola con el tramo extremo.
     - Si `value` coincide con el `fromValue` de un par (con `EPS`), el resultado es
       `Exact(toValue)`.
     - En cualquier otro caso lleva `CALIBRATED`.
  3. **Calibración con 1 par** `(x0, y0)`. Hace falta la curva de grosor de los dos molinos
     (§3.3.2). Se calcula el valor por curvas (regla 4) y se desplaza para que pase por el par:
     `v' = c(v) + (y0 − c(x0))`, donde `c` es la conversión por curvas. Lleva `CALIBRATED`, o es
     `Exact(y0)` si `value` coincide con `x0`. Si falta alguna curva: `NoEquivalent`, con la pista
     "Añade otra equivalencia" (`GrindFix.ADD_CALIBRATION`).
  4. **Curva de grosor.** Si los dos molinos tienen curva (§3.3.2): el valor de origen pasa a nivel
     con la curva del origen, y ese nivel pasa a valor con la curva del destino. Lleva `ANCHORS`, y
     la interfaz lo muestra como **"aproximado"**. No hace falta que los dos molinos compartan
     anclas.
  5. **Sin equivalencia.** Si no aplica ninguna: `NoEquivalent`.
- **Etiquetas.** `CALIBRATED` se muestra como **"según tu calibración"**. Tiene prioridad sobre
  "aproximado": un resultado no lleva `CALIBRATED` y `ANCHORS` a la vez.
- **Límite físico.** Si `v'` sale de `[scaleMin, scaleMax]` del destino, se limita al extremo y se
  añade `AT_LIMIT`. La interfaz muestra "al límite del molino". **Es el único caso que se avisa como
  fuera de rango.**
- **Redondeo.** `v'` se redondea al múltiplo de `step` más cercano, contado desde `scaleMin`. Si el
  redondeo cambia el valor, se añade `ROUNDED` y el valor se muestra con el prefijo "≈".
- **Receta sin ajuste.** Con un molino elegido, si la receta no tiene molino ni ajuste y ese molino
  tiene rango para el método, se propone el **punto medio del rango**, redondeado al paso, con la
  etiqueta "sugerido". Si el molino no tiene rango para el método, la tarjeta muestra "La receta no
  indica molienda", sin sugerencia. (Sin cambios en la enmienda 1: `GrindConverter.suggested(method,
  grinder)` se mantiene.)

#### 3.3.1 Pares de calibración (enmienda 1)

- **Qué son.** Un par afirma que dos ajustes de dos molinos distintos muelen igual, por ejemplo
  "Chestnut S3 25 = Femobook A2 60". Los registra el usuario después de comprobarlo moliendo.
- **Alcance.** Un par solo afecta a la conversión entre sus dos molinos, en los dos sentidos. No se
  encadenan: los pares S3↔A2 y A2↔C2 no dan S3↔C2.
- **Al crear o editar un par:**
  - Los dos molinos son de "Mis molinos" y son distintos.
  - Cada valor está dentro de la escala de su molino y es múltiplo de su paso (lo garantiza
    `GrindInput`).
  - **Valor repetido.** Si ya hay un par entre esos dos molinos con el mismo valor en uno de los
    lados, ese par se **actualiza** con el valor nuevo y se avisa con "Equivalencia actualizada".
  - **Monotonía.** Los pares de dos molinos, en la coordenada de grosor de cada uno (de fino a
    grueso, invertida en `HIGHER_IS_FINER`), tienen que ser estrictamente crecientes. Un par que lo
    rompa (por ejemplo, "S3 25 = A2 55" con "S3 20 = A2 60" ya guardado) **no se guarda**. Se
    muestra "Contradice la equivalencia S3 20 = A2 60", con la opción "Reemplazar", que borra los
    pares en conflicto y guarda el nuevo.
  - Si el valor repetido actualiza un par y el resultado contradice a otro, se trata como un
    conflicto.
- **Después de crear un par:**
  - Si uno de los molinos deja de estar en "Mis molinos" o se retira del catálogo, el par se
    conserva y sigue aplicando cuando ese molino interviene en una conversión.
  - Borrar un molino borra sus pares.
  - Duplicar un predefinido no copia sus pares.
  - Una actualización del catálogo no toca los pares.
  - Si una edición de un molino propio deja un valor de un par fuera de la escala, el par se
    conserva y se muestra con "Fuera de la escala" (D30), sin corregirlo. Los pares guardados no se
    revalidan si cambia la dirección del molino.

#### 3.3.2 Curva de grosor (enmienda 1)

- **Qué es.** Una función monótona de cada molino entre su ajuste (unidades base) y el nivel de
  grosor `L` (coordenada continua, `EXTRA_FINE = 0` … `EXTRA_COARSE = 6`), que se puede invertir y
  extrapolar. Función pura `GrindCurve.of(grinder): GrindCurve?`, con `level(value)` y
  `value(level)`.
- **Curva explícita.** Si el molino tiene **2 o más anclas**, se interpola por tramos sobre los
  puntos `(ancla, ordinal del nivel)` y fuera de ellos se extrapola con el tramo extremo. Se usan
  todas las anclas del molino.
- **Curva implícita.** Si tiene menos de 2 anclas, se deriva de sus rangos por método:
  1. Cada rango aporta el punto `(nivel del método, punto medio del rango)`. El nivel de cada método
     es la constante `METHOD_LEVELS` (§3.6). `OTHER` no tiene rango.
  2. Los puntos repetidos cuentan una vez (por ejemplo, Kalita Wave, Chemex y Origami con el mismo
     rango).
  3. Se ajusta una recta por mínimos cuadrados, `valor = a + b × L`.
  4. No hay curva si quedan menos de 2 niveles distintos o si la pendiente contradice la dirección
     del molino (`b ≤ 0` en `HIGHER_IS_COARSER`, `b ≥ 0` en `HIGHER_IS_FINER`).
  - Un ancla suelta (solo una) no se usa.
  - La curva implícita se calcula en la app, así que también vale para los molinos propios que solo
    tienen rangos. No se guarda en la base de datos ni en el catálogo.
- **Sin curva.** Un molino sin 2 anclas ni rangos suficientes no tiene curva.

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
- **Enmienda 1: tabla `grind_calibrations` (`HistoryDatabase` de v6 a v7).**

  | Tabla | Columnas | Claves e índices |
  |---|---|---|
  | `grind_calibrations` | `id` Long, `grinderAId` Long, `valueA` Double, `grinderBId` Long, `valueB` Double, `updatedAtEpochMs` Long | PK `id` autoincremental; FK de `grinderAId` y `grinderBId` a `grinders` con `CASCADE`; índices sobre `grinderAId` y `grinderBId`; únicos sobre (`grinderAId`, `grinderBId`, `valueA`) y (`grinderAId`, `grinderBId`, `valueB`) |

  - Orden canónico: `grinderAId < grinderBId`. El repositorio orienta los pares de cada conversión
    (`CalibrationPoint(fromValue, toValue)`).
  - `MIGRATION_6_7` solo crea la tabla y sus índices. No toca datos. Se exporta `7.json`.
  - Si SPEC-008 (en curso en `feat/guided-tour`) sube antes la versión de la base de datos, esta
    migración pasa a la siguiente libre. El plan lo comprueba al empezar.

### 3.5 Contratos del paquete `dev.josueamaya.pourline.grinder`

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
- **Enmienda 1:**
  - `GrindCurve` (puro): curva explícita o implícita y `METHOD_LEVELS` (§3.3.2 y §3.6).
  - `CalibrationPoint(fromValue: Double, toValue: Double)` y `GrindCalibration(id, grinderAId,
    valueA, grinderBId, valueB)` en el dominio.
  - `CalibrationValidator` (puro): comprueba un par nuevo frente a los existentes y devuelve
    `Ok`, `Update(existing)` o `Conflict(conflicts: List<GrindCalibration>)` (§3.3.1). La escala y
    el paso los garantiza `GrindInput`, pero el validador también los comprueba.
  - `GrindCalibrationDao` y `GrinderRepository` exponen:
    - como `Flow`, los pares de un molino y los pares entre dos molinos (orientados);
    - `saveCalibration(...)`, que devuelve `Saved`, `Updated` o `Conflict`;
    - `replaceCalibration(...)`, que borra los pares en conflicto y guarda el nuevo en una
      transacción;
    - `deleteCalibration(id)`.
  - `GrindFix` gana `ADD_CALIBRATION`.

### 3.6 Catálogo predefinido

- **Archivo.** Un archivo de datos versionado en los assets de `:app` (por ejemplo,
  `assets/grinders/catalog.json`), con un `version: Int` y una lista de molinos. Cada molino lleva:
  - `key`, `brand` y `model`;
  - las unidades;
  - la escala, el paso y la dirección;
  - el rango en micras;
  - los rangos por método;
  - las anclas;
  - `sourceUrl` y `sourceName` (el nombre de la fuente que se muestra en la atribución).
- **Molinos incluidos:** Timemore Chestnut C2, C3/C3S y S3; Comandante C40; 1Zpresso JX-Pro,
  K-Ultra y Q2; Kingrinder K6; Baratza Encore; Fellow Ode Gen 2; Femobook A2.
- **Fuente.** Las páginas por molino de Honest Coffee Guide (por ejemplo,
  `https://honestcoffeeguide.com/timemore-c2-grind-settings/`). Dan el rango total en micras y los
  rangos recomendados en clics para V60, Pour over, AeroPress, French press, Espresso y otros
  métodos. El Femobook A2 no tiene página allí: sus datos salen de otras fuentes, citadas en su
  `sourceName` y su `notes` (D39 del §3.9).
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
- **Nivel de cada método (`METHOD_LEVELS`, enmienda 1).** La curva implícita (§3.3.2) necesita un
  nivel de grosor por método. Se calcula con los datos del catálogo al preparar la enmienda, no en
  la app, y se fija como constante en `GrindCurve`:
  1. Para cada predefinido con `micronRange` y rango para el método, se pasan los extremos del rango
     a micras, suponiendo el molino lineal entre su escala y su rango en micras (invertido en
     `HIGHER_IS_FINER`), y se toma el punto medio.
  2. Se promedia ese punto medio sobre todos esos predefinidos.
  3. Se pasa a nivel con `L = (µ − 100) / 200` (la banda `EXTRA_FINE` está centrada en 100 µm y cada
     banda mide 200 µm) y se redondea a 2 decimales.
  - Un test JVM recalcula `METHOD_LEVELS` desde `catalog.json` y falla si no coincide con la
    constante. Así, un cambio del catálogo obliga a revisarla.
- **Puerta de revisión del Femobook A2 (enmienda 1).**
  - Antes de tocar la interfaz, el implementador **se detiene** y presenta:
    - `METHOD_LEVELS`;
    - la curva implícita del A2 (recta y anclas equivalentes en los 7 niveles);
    - una tabla de conversión S3 → A2 para 0, 10, 20 … 90 clics del S3, calculada con el rango
      de moka del A2 y sin él.
  - Los datos del A2 se contradicen (moka 10–18 frente a V60 50–80), así que el resultado será
    aproximado en cualquier caso. Una estimación previa daba S3 20 → A2 31 con el rango de moka y
    → 45 sin él.
  - **No continúa hasta que el usuario decida** si se mantiene, se corrige o se quita el rango de
    moka del A2. Si cambia, se sube la `version` del catálogo y se anota en `notes`.
  - La calibración del usuario se impone en cualquier caso.
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
  conversión en la que intervenga uno, se muestra "Fuente: <sourceName> · valores
  orientativos" con el nombre de la fuente de cada predefinido que interviene (D38 del §3.9), con
  enlace a su `sourceUrl`.

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
    - **Sección "Equivalencias" (enmienda 1).** Aparece si el molino está en mis molinos o tiene
      pares:
      - Los pares con mis otros molinos, agrupados por molino y ordenados por el valor de este
        molino, por ejemplo "Femobook A2 · S3 20 = A2 60". Cada par se puede editar y borrar.
      - "Añadir equivalencia" (solo si el molino está en mis molinos): elegir otro de mis molinos e
        introducir los dos ajustes con `GrindInput`.
      - Los mensajes "Equivalencia actualizada" y "Contradice la equivalencia …" con "Reemplazar"
        del §3.3.1.
      - Sin pares: el texto "Guarda los ajustes que muelen igual en tus molinos para afinar la
        conversión".
      - Borrar un par usa la misma confirmación o el mismo "Deshacer" que los demás borrados de la
        app.
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
    - El ajuste del molino elegido en grande (convertido, "sugerido", "según tu calibración",
      "aproximado", "≈" o "al límite del molino" según el caso).
  - **Experto:** lo mismo, más la regla de molienda del molino elegido.
  - **Molino preseleccionado** en "Moler con":
    1. el de la receta, si es de mis molinos;
    2. si no, el predeterminado;
    3. si no, el primero de mis molinos.
  - **Sin molinos propios:** se muestra el ajuste original sin selector y el enlace "Añade tu molino
    para ver el equivalente", que lleva a Ajustes → Molinos.
  - **Sin equivalencia:** "Sin equivalencia" y un enlace para completar los rangos (molino propio) o
    duplicarlo (predefinido). Con un solo par de calibración y sin curva, la pista "Añade otra
    equivalencia" lleva a la sección "Equivalencias" del molino elegido (enmienda 1).
  - **El método de la receta no afecta a la conversión** (enmienda 1). Solo decide el ajuste
    sugerido.
  - **Origen de la tarjeta:** la receta elegida o, si el objetivo de la sesión es una preparación
    guardada, esa preparación (plan D24).
- **Al terminar la preparación.** Se guardan en la preparación el molino elegido en "Moler con" y el
  ajuste mostrado (convertido o sugerido), con su `grindLabel`. Si no hay ajuste que mostrar (sin
  receta, o receta sin molienda con un molino sin rango), se guarda **el molino sin ajuste** y se
  completa en "Editar preparación" (plan D16). Si no hay molino elegido, los campos quedan a `null`.
- **Convertidor suelto:**
  - Está en Ajustes → Molinos → "Convertidor".
  - **Atajo "Ver en mis otros molinos"** en la tarjeta de molienda y en el detalle de la receta.
    Abre el convertidor ya relleno con el molino y el ajuste de origen. No aparece si la receta no
    tiene molino ni ajuste.
  - Entradas: molino de origen (cualquiera del catálogo o propio) y ajuste, con la introducción
    compuesta. **El selector de método se quita** (enmienda 1).
  - Salida: una fila por cada molino de "mis molinos" distinto del origen, con el ajuste equivalente
    y su marca (según tu calibración, aproximado, ≈, al límite del molino o sin equivalencia). Solo
    se listan mis molinos, sin opción de ver todo el catálogo.
  - **"Corregir" (enmienda 1).** Cada fila lleva "Corregir" si el origen está en mis molinos (en
    una fila sin equivalencia, "Añadir equivalencia"):
    - Abre la introducción compuesta del molino de la fila, con el valor mostrado o, sin
      equivalencia, el sugerido de D13.
    - "Guardar equivalencia" guarda el par (ajuste de origen, valor introducido) con las reglas del
      §3.3.1, y la fila se recalcula.
    - Si el origen no está en mis molinos, no hay "Corregir".
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
5. Cambia "Moler con" a Comandante C40. Sin pares de calibración, `GrindConverter` pasa los 30 clics
   del S3 a nivel de grosor con la curva del S3 y ese nivel a clics con la curva del C40 (enmienda
   1). La tarjeta muestra el ajuste del C40 como "aproximado" y la fuente.
   - Si el usuario comprueba que el C40 muele igual con otro valor, lo corrige en el convertidor
     ("Corregir"). Con 2 o más pares, la tarjeta pasa a mostrar "según tu calibración".
6. Empieza la sesión. En el paso 2 (Dosis) ve la misma tarjeta, con la regla si está en Experto.
7. Al terminar, la preparación se guarda con `grinderId` del C40, el ajuste convertido y
   `grindLabel`.

### 3.9 Decisiones de implementación (D1 a D39)

Las decisiones que el plan ([`plan-molinos.md`](plan-molinos.md)) tomó donde este spec dejaba huecos o lo ampliaba.
D8, D12, D15, D16, D17, D18, D23, D37 y D38 amplían o concretan el spec; D9 queda ampliada por D38 (la fuente se muestra
por molino); las demás son de implementación. D39 recoge los datos del Femobook A2 que aportó el usuario. Las
referencias a fases (F0 a F11) y a apartados "§" son las del plan.

**Enmienda 1.** D5, D14, D25, D26 y D39 están actualizadas: la parte que cambia va marcada con "(enmienda 1)". El plan de la enmienda añadirá sus propias decisiones a partir de D40.

#### Diseño visual

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Componentes nuevos | `AntStepper` (vertical: [+] arriba, valor, [−] abajo y la etiqueta de la unidad debajo; botones de 40 dp con área táctil de 48 dp), `AntTabs` (pestañas de línea, como `Tabs` de antd, con indicador inferior de 2 dp en `primary.base`) e iconos `Minus`, `Grinder` (molino manual) y `Convert` (dos flechas opuestas). Bocetos en F0.3. | Las imágenes de referencia del spec no están en el repo (P4). Tres steppers verticales caben de sobra a 360 dp (3 × 48 dp) y dejan la etiqueta de la unidad bajo cada parte, como pide el §3.2. |
| D2 | Introducción compuesta (`GrindInput`) | Un `AntStepper` por unidad, en el orden de la jerarquía, con " · " entre columnas. Con valor `null` cada parte muestra "—" y el primer toque parte del valor inicial (D13). La semántica del conjunto es la forma larga ("1 rotación, 2 números, 3 clics"); cada botón se lee "Aumentar rotaciones"/"Disminuir clics". | §3.2. TalkBack lee el ajuste completo y no tres números sueltos. |
| D3 | Regla de molienda (`GrindRuler`) | Alto 64 dp. Pista horizontal con marcas (D11 y F1); **grueso a la izquierda, fino a la derecha** siempre. Rango del método como banda `primary.bg` con su nombre encima; ajuste actual como marcador triangular `primary.base` con el valor; anclas como puntos de 6 dp con su abreviatura (F0.3); "Grueso" y "Fino" en los extremos (`Secondary`). Semántica: "Regla de molienda, de grueso a fino. V60 de 13 a 22 clics. Ajuste: 30 clics". | §3.7. Una sola regla para el detalle, el editor y la tarjeta de Experto. |
| D4 | Tarjeta de molienda (`GrindCard`) | `AntCard` "Molienda": línea de la receta, selector "Moler con", ajuste del molino elegido con `AntTitle(level = 2)` y las marcas como `AntTag` ("≈" va delante del número; "aproximado", "sugerido" y "al límite del molino" como etiquetas), la regla solo en Experto, el enlace "Ver en mis otros molinos" y la línea de la fuente. Los estados y sus textos están en D25 y F0.3. En la sesión va en el paso 2, debajo de la barra de estabilidad. | §3.7. En el paso 2 la sesión ya tiene desplazamiento vertical (`PrepPanel`). |
| D5 | Navegación | Claves nuevas: `Grinders`, `GrinderCatalog`, `GrinderDetail(id)`, `GrinderEdit(id: Long? = null, fromId: Long? = null, openRanges: Boolean = false)` y `GrindConverter(sourceId: Long? = null, value: Double? = null)` (enmienda 1: sin `method`). Ajustes → Molinos se apila en la pila de Preparar (como Ajustes). Los atajos se apilan en la pila de la pestaña actual. `showsBottomBar` pasa a recibir la pila: **sin barra si hay una `Session` en la pila** o si la cima es un editor (`CoffeeEdit`, `RecipeEdit`, `BrewEdit`, `GrinderEdit`) o el convertidor. | Desde el paso 2 de la sesión se puede abrir Molinos o el convertidor; con la regla de la cima sola, la barra aparecería con la sesión abierta debajo. El convertidor es una herramienta a pantalla completa con su flecha atrás. |
| D6 | Onboarding | Dos pasos dentro de la misma puerta de `MainNavigation` (no son claves de navegación, D11 de SPEC-005). El paso 1 no cambia; elegir Básico o Experto pasa al paso 2 sin guardar nada. "Saltar" del paso 1 termina el onboarding en Básico **sin** mostrar el paso 2. En el paso 2, "Atrás" vuelve al 1; elegir un molino o "Omitir" guardan el modo y `onboarding_done` en una escritura (`completeOnboarding`). El paso se guarda con `rememberSaveable`. | §3.7: el paso del molino aparece "después de elegir Básico o Experto". "Saltar" ya significa "no me preguntes" (P5). Si el proceso muere en el paso 2, se repite el onboarding entero: no queda un modo a medias. |
| D7 | Catálogo y detalle | En "Añadir molino", tocar una fila abre su detalle y el botón "Lo tengo" lo añade sin salir de la lista (pasa a "En mis molinos", deshabilitado). Acciones del detalle: predefinido de mis molinos → "Usar como predeterminado", "Duplicar para personalizar" y "Quitar de mis molinos"; predefinido que no tengo → "Lo tengo" y "Duplicar para personalizar"; propio → "Usar como predeterminado", "Editar", "Duplicar para personalizar" y "Borrar". "Usar como predeterminado" solo existe para mis molinos y no aparece en el que ya lo es. | El predeterminado tiene que ser de mis molinos: el §4 lo anula al quitarlo de ellos. Ver el detalle antes de añadirlo ayuda a elegir entre variantes (C3 y C3S). |
| D8 | "Detalle de receta" **(concreta)** | Es `RecipeEditScreen`, que es lo que abre una receta: el campo de molienda muestra "Molino · ajuste" (o la copia con "molino eliminado") y debajo el atajo "Ver en mis otros molinos". No se crea una pantalla de detalle de receta. | No existe otra pantalla de receta (P6). |
| D9 | Fuente visible | Una línea `Secondary` "Fuente: Honest Coffee Guide · valores orientativos" seguida de un enlace (`AntButton` `Link`) por cada predefinido que interviene, con su modelo, que abre `sourceUrl` en el navegador del sistema (`LocalUriHandler`). Aparece en el detalle de un predefinido, en la tarjeta si el molino de la receta o el elegido son predefinidos y en el convertidor si lo es el origen o alguna fila. | §3.6. Con dos predefinidos hay dos páginas de origen distintas. |

#### Implementación

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D10 | Paquetes | `dev.josueamaya.pourline.grinder`: dominio, Room, repositorio, sembrador y textos. **Puros** (sin `import android`, se comprueba con `grep` en F1 y F11): `GrindModels.kt`, `GrindMath.kt`, `GrindFormat.kt`, `GrindTexts.kt`, `GrindStepper.kt`, `GrindConverter.kt`, `GrinderValidator.kt`, `GrindSetting.kt`, `GrinderCatalogFile.kt`, `GrindRulerModel.kt`, `GrindCardState.kt` y `ConverterRows.kt`. `dev.josueamaya.pourline.ui.grinders`: pantallas y componentes. `GrindSelection` en `dev.josueamaya.pourline.session`. `GrinderSettings` en `dev.josueamaya.pourline.settings`. | Como en `history` y `coffee` (D15 de SPEC-004). `ReferenceKey` vive en un archivo con `import android`, así que los archivos puros no la usan. |
| D11 | Aritmética del valor | `GrindMath.snap(v, g) = g.scaleMin + round((v − g.scaleMin) / g.step) × g.step`, redondeado a 6 decimales para quitar el ruido de coma flotante (0,1 × 3). Igualdad con `EPS = 1e-9`. Decimales que se muestran: los del paso (1 → 0; 0,5 y 0,1 → 1; 0,25 → 2; máximo 3). `decompose` devuelve `List<Double>`: todas las partes son enteras salvo la última, que puede llevar el decimal del paso. Los multiplicadores son el producto de los `perParent` siguientes (`Long`). | §3.2 y §4 ("el valor nunca queda fuera de los múltiplos de `step`"). Kotlin no tiene el `Int|Double` del spec. |
| D12 | Formato **(amplía la firma)** | `GrindFormat.format(value, grinder, texts: GrindTexts)`: una unidad → "30 clics" (plural del idioma); varias → "1 · 2 · 3". `describe(value, grinder, texts)` → "1 rotación, 2 números, 3 clics". `label(grinder, value, texts)` → `"<modelo> · <format>"` ("Chestnut S3 · 30 clics") o solo `"<modelo>"` sin valor. `interface GrindTexts { fun quantity(unit: GrindUnit, value: Double): String; fun unitName(unit: GrindUnit): String; fun number(value: Double, decimals: Int): String }`; `ResourcesGrindTexts(context)` usa `plurals` (`grind_unit_clicks`…, con `other` para los no enteros). | Los nombres de las unidades y los plurales dependen del idioma y el paquete es puro. El spec firma `format(value, grinder)`: el parámetro extra es la única forma de no importar Android. |
| D13 | Acarreo (`GrindStepper`) | `GrindStepper.step(value: Double?, unitIndex: Int, delta: Int, grinder: Grinder, start: Double): Double`. En una unidad superior suma `delta × multiplicador`; en la base, `delta × step`. Como el valor es absoluto, el acarreo y el préstamo salen solos. Después, `snap` y límite a la escala. Con `value == null` devuelve `start` sin sumar: el primer toque fija el valor inicial, que es `GrindConverter.suggested(method, grinder)` o, sin rango, el centro de la escala ajustado al paso. | §3.2. Un primer toque que ya sumara partiría de un valor que el usuario no ha visto. |
| D14 | Conversión **(enmienda 1)** | `GrindConverter.convert(value, from, to, pairs: List<CalibrationPoint>): Conversion` con `sealed interface Conversion { data class Exact(val value: Double); data class Approximate(val value: Double, val reasons: Set<ConversionReason>); data object NoEquivalent }` y `enum class ConversionReason { CALIBRATED, ANCHORS, ROUNDED, AT_LIMIT }`. "Mismo molino" = mismo `id` distinto de 0. Reglas en el orden del §3.3: mismo molino, calibración con 2 o más pares, calibración con 1 par (desplaza la conversión por curvas), curva de grosor (`GrindCurve`, §3.3.2: coordenada de nivel `ordinal` 0–6, tramos lineales sobre las anclas de **cada** molino, o recta por mínimos cuadrados de sus rangos, con extrapolación por el tramo extremo) y `NoEquivalent`. Se elimina la regla por rango del método. Orden de los pasos: valor calculado → `snap` (`ROUNDED` si cambia) → si sale de `[scaleMin, scaleMax]`, el extremo y `AT_LIMIT`. `GrindConverter.suggested(method, grinder): Double?` = `snap` del punto medio del rango, o `null`. | §3.3. Redondear antes de limitar garantiza que el extremo sea siempre un valor válido (con D15). |
| D15 | Validador **(amplía)** | `GrinderValidator.validate(grinder): List<GrinderError>` con las reglas del §3.5 y tres más: (a) `(scaleMax − scaleMin)` es múltiplo de `step`; (b) con más de una unidad, `scaleMin ≥ 0`; (c) la unidad primaria no lleva `perParent` y las secundarias sí. Las anclas tienen que ser **estrictamente** monótonas. No hay rango para `OTHER`. | (a) sin ella, limitar a `scaleMax` daría un valor fuera de los múltiplos del paso, que prohíbe el §4. (b) la descomposición de un negativo en partes ("−1 · 3 · 9") no se puede leer. Anclas iguales harían la interpolación no invertible (P2). |
| D16 | Ajuste guardado **(amplía)** | `data class GrindSetting(val grinderId: Long?, val value: Double?, val label: String?)` en `Recipe.grind` y `BrewDetails.grind` (`null` = sin molienda). Estados: con molino y ajuste; **con molino y sin ajuste** ("Chestnut S3"); molino eliminado (`grinderId = null`, `label` presente). Los formularios usan `sealed interface GrindChoice { None; Live(grinderId: Long, value: Double?); Orphan(setting: GrindSetting) }`: `Orphan` solo existe si ya lo era al cargar y se conserva tal cual hasta que se elige un molino o "Sin molino". | El spec no dice qué guarda una preparación sin receta con un molino en "Moler con" (§3.7: "se guardan el molino elegido y el ajuste mostrado", pero no hay ajuste que mostrar). Guardar el molino sin ajuste conserva lo que sí se sabe y se completa en "Editar preparación" (P1). |
| D17 | `grindLabel` **(amplía)** | Se calcula con `GrindFormat.label` y el molino vivo al guardar en `RecipeEditViewModel`, `BrewEditViewModel` y al congelar el ajuste de la sesión (D23). Un `Orphan` conserva su copia. Al **borrar** un molino propio, `GrinderRepository.delete` regenera primero la copia de cada receta y preparación que lo usan con el nombre y el formato actuales, y borra después; la clave foránea pone `grinderId = NULL`. El idioma de la copia es el del momento del guardado, como `referenceName`. | La copia solo se ve cuando el molino ya no existe: si se renombró después de guardar la receta, "molino eliminado" mostraría un nombre que el usuario ya no reconoce. |
| D18 | Claves foráneas al escribir **(amplía)** | `RecipeDao.save` y `restore`, y `BrewDao.insertBrew`, `restore` y `applyDetails`, comprueban `SELECT EXISTS(SELECT 1 FROM grinders WHERE id = :id)`. Si el molino ya no existe, escriben `grinderId = NULL` y conservan `grindValue` y `grindLabel`. | Igual que el café (D21 de SPEC-004): se puede borrar el molino mientras se edita una receta, o "Deshacer" el borrado de una receta cuyo molino ya no existe, y la FK haría fallar la escritura. |
| D19 | Migración 4 → 5 | `val MIGRATION_4_5` (constante). (1) `CREATE TABLE` de las cuatro tablas de molinos y el índice único `index_grinders_catalogKey`, copiados de `5.json`. (2) Por cada una de `recipes` y `brews`: lee `seq` de `sqlite_sequence`; `CREATE TABLE <t>_new` con el SQL de `5.json`; `INSERT INTO <t>_new (<columnas sin grind>) SELECT <mismas> FROM <t>`; `DROP TABLE <t>`; `ALTER TABLE <t>_new RENAME TO <t>`; recrea sus índices (`index_recipes_builtInKey`, `index_recipes_grinderId`; `index_brews_startedAtEpochMs`, `index_brews_coffeeId`, `index_brews_grinderId`); y repone `seq`. (3) `PRAGMA foreign_key_check` de las dos tablas: si devuelve filas, lanza `IllegalStateException`. Antes de nada comprueba `PRAGMA foreign_keys`: si vale 1, lanza `IllegalStateException` sin tocar nada (la transacción se deshace). El catálogo no se siembra aquí. | Es el procedimiento de SQLite para cambiar una tabla (crear, copiar, borrar, renombrar) y el que usan las automigraciones de Room, que ejecutan las migraciones con las claves foráneas desactivadas (las activa `onOpen`, después). Con ellas activas, el `DROP` borraría etapas, curvas y objetivos: la comprobación convierte esa pérdida en un error recuperable (R1). Reponer `seq` evita reutilizar ids de recetas borradas, que `ReferenceKey` y `referenceId` guardan sin clave foránea. |
| D20 | Entidades y conversores | `GrinderEntity` (`grinders`), `GrinderUnitEntity` (`grinder_units`), `GrinderMethodRangeEntity` (`grinder_method_ranges`, `method` con el conversor de `BrewMethod` de `HistoryConverters`), `GrinderAnchorEntity` (`grinder_anchors`) y `GrinderWithDetails` (`@Embedded` + tres `@Relation`, consultas con `@Transaction`). `GrinderConverters`: `GrindDirection`, `GrindUnit` y `GrindLevel` por nombre; un nombre desconocido se lee como `HIGHER_IS_COARSER`, `CLICKS` y la fila de ancla se ignora al mapear al dominio. | Mismo criterio que `HistoryConverters`: volver a una versión anterior no rompe la lectura. |
| D21 | Repositorio y predeterminado | `GrinderRepository(db, defaults: DefaultGrinderStore, texts: GrindTexts)` con escrituras en `Dispatchers.IO + NonCancellable`. `setOwned(id, false)` y `delete(id)` ponen `default_grinder_id = null` si era ese. `save(grinder)` valida con `GrinderValidator` y rechaza los predefinidos (`catalogKey != null`). Una receta nueva preselecciona el predeterminado con valor `null` (D13): el ajuste lo fija el primer toque. | §4. Proponer un valor que nadie ha elegido lo guardaría sin que el usuario lo vea. |
| D22 | Duplicar | "Duplicar para personalizar" abre `GrinderEdit(fromId = id)` con una copia en memoria: `id = 0`, `catalogKey = null`, `owned = true`, `retired = false`, `sourceUrl = null`, modelo "<modelo> (copia)" (`grinder_copy_name`) y `duplicatedFromKey = original.catalogKey ?: original.duplicatedFromKey`. Se inserta al pulsar "Guardar". | Si el usuario sale sin guardar no queda una copia huérfana en mis molinos. Con el mismo nombre, dos filas de mis molinos serían indistinguibles. |
| D23 | Selección de "Moler con" y la sesión **(concreta)** | `GrindSelection` (objeto de la app, en `session/`): combina `manager.ui.map { it.target }` (con `distinctUntilChanged` sobre la fuente, D24), `SessionGrinders` (mis molinos y un molino por id), `DefaultGrinderStore` y la elección del usuario, y expone `state: StateFlow<GrindCardState>` con `SharingStarted.Eagerly` en `Dispatchers.Default`. La elección se guarda junto con la `ReferenceKey` de la receta: al cambiar de receta vuelve a la preselección del §3.7; sobrevive a cambiar de pestaña, a girar y al paso entre Preparar y la sesión, pero no a la muerte del proceso. El gestor recibe `grind: SessionGrind = NoSessionGrind` (`fun interface SessionGrind { fun current(): GrindSetting? }`) y congela `grind.current()` al pasar a `Active`, en el mismo punto que el café; `save` lo escribe en `details.grind`. En `PourlineApp`, el gestor recibe `SessionGrind { grindSelection.current() }` para romper la dependencia circular. | Fuera del gestor, su camino caliente (10 Hz) no cambia: el rendimiento de la sesión no depende de la tarjeta (§4). Congelar en `Active` es coherente con el objetivo y el café, y la tarjeta ya no se ve después del paso 2. |
| D24 | Origen de la tarjeta | La fuente es la referencia activa, sea una receta o una preparación (objetivo "desde el historial" de Experto): su método (`prefill.method`) y su ajuste (`ActiveTarget.sourceGrind`, campo nuevo). `prefill` deja de llevar la molienda: la preparación guarda la de "Moler con", no la de la referencia. | El spec habla de "receta", pero `TargetPicker` también admite preparaciones como objetivo. Copiar la molienda de la referencia contradiría el §3.7. |
| D25 | Estados de la tarjeta | Función pura `grindCardState(source: GrindSource?, sourceGrinder: Grinder?, owned: List<Grinder>, chosenId: Long?, defaultId: Long?): GrindCardState`. `GrindOutcome`: `Value(value, exact, reasons, suggested)`; `NoSetting` ("La receta no indica molienda", sin rango para sugerir); `NoEquivalent(fix: GrindFix)` con `COMPLETE_RANGES` (propio), `DUPLICATE` (predefinido) o, con un solo par de calibración y sin curva, `ADD_CALIBRATION` (enmienda 1); `Value` puede llevar la razón `CALIBRATED` ("según tu calibración"). Los pares de calibración entran como `Flow` del repositorio, fuera del camino caliente de la sesión; `Orphan` (la receta tiene "molino eliminado": "Asigna un molino a la receta para convertir"); `NoReference` (sin receta: solo "Moler con"). Sin molinos propios: la línea de la receta y el enlace "Añade tu molino para ver el equivalente", sin selector. `GrindCardState.snapshot(texts): GrindSetting?` es lo que se guarda: molino elegido, valor mostrado (convertido o sugerido, o `null`) y su copia. | §3.3 y §3.7. Una receta con molino eliminado no se puede convertir (§3.4): necesita un texto propio, no "Sin equivalencia", que invitaría a completar rangos de otro molino. |
| D26 | Convertidor **(enmienda 1)** | Origen: `GrinderPicker` (`AntDrawer` con buscador) con dos secciones, "Mis molinos" y "Catálogo" (sin retirados). **Sin selector de método** (enmienda 1). Ajuste: `GrindInput` del origen. Salida: función pura `converterRows(source, value, owned, pairs): List<ConverterRow>`, donde `pairs` son los pares del origen con cada molino; cada fila indica si admite "Corregir" (el origen está en mis molinos); una fila por cada molino de mis molinos distinto del origen, en el orden de mis molinos. Sin mis molinos: `AntEmpty` con el enlace a Ajustes → Molinos. Cambiar el origen deja el valor en `null`. | §3.7. Un valor de otro molino no tiene sentido en el nuevo origen. |
| D27 | Nombres de los métodos | `ESPRESSO` como nombre fijo ("Espresso"); `FRENCH_PRESS`, `COLD_BREW` y `MOKA` con cadenas: "Prensa francesa"/"French press", "Cold brew"/"Cold brew" y "Moka"/"Moka pot". `RecipeRow` pasa a usar `label()`. | El §3.4 da los nombres en español; "Moka" y "Prensa francesa" cambian en inglés. |
| D28 | Búsqueda y orden | Búsqueda con `fold()` sobre marca, modelo y "marca modelo". Mis molinos y el catálogo, por marca y modelo con `Collator` (fuerza `SECONDARY`, como `RecipeRepository.list`). El predeterminado lleva la etiqueta, no sube al principio. | Orden estable que no salta al cambiar el predeterminado. |
| D29 | Regla en Experto | `ExpertFeature.GRIND_RULER`: la regla de la tarjeta de molienda. | Mismo mecanismo que el resto de reglas de visibilidad. |
| D30 | Valores fuera de escala | Si un `grindValue` vivo queda fuera de la escala actual de su molino (§4), se muestra igual con un `AntTag` `Warning` "Fuera de la escala"; ni el editor ni la conversión lo corrigen solos (`GrindInput` lo limita al primer toque). | §4. |
| D31 | Retirados | No salen en "Añadir molino" ni en el origen del convertidor; en mis molinos y en el detalle llevan `AntTag` "Retirado del catálogo". Si un archivo posterior vuelve a traer su clave, se actualiza y `retired` pasa a `false`. | §4. El spec no dice qué pasa si vuelve. |
| D32 | Rendimiento | La línea base de `SessionPerformanceTest` se toma en F0 sobre `develop`, tres pasadas por caso. En F7 la prueba añade al montaje dos molinos propios y una receta con molino, para que `GrindSelection` y la tarjeta trabajen. En F11, tres pasadas más. "No empeora" = mediana del p95 de cada caso ≤ la de la línea base + 1 ms, y mismas condiciones de umbral. | El caso Experto oscila cerca de 32 ms en este emulador (spec-005): una sola pasada no basta para comparar. |

#### Catálogo y puerta

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D33 | Formato del archivo y dependencia | `app/src/main/assets/grinders/catalog.json`, leído con `kotlinx-serialization-json` (`implementation(libs.kotlinx.serialization.json)`, ya en la caché). Esquema: `{ "version": Int, "grinders": [ { "key", "brand", "model", "units": [ { "unit": "ROTATIONS" }, { "unit": "NUMBERS", "perParent": 10 }, … ], "scale": { "min", "max" }, "step", "direction", "micronRange": { "min", "max" } \| null, "ranges": { "V60": { "min", "max", "source": "13 – 22" }, … }, "anchors": { "FINE": 9.5, … }, "anchorsManual": false, "sourceUrl", "missing": [String], "notes": String? } ] }`. Valores en unidades base; `source` guarda el texto de la fuente ("2.2.0 – 3.8.1") para revisarlo. `GrinderCatalogFile.parse(json): Result<CatalogData>` rechaza el archivo **entero** si una entrada es inválida (`GrinderValidator`, clave repetida, método desconocido o `OTHER`, `sourceUrl` vacía). | Un solo lector, en `main` y probado en la JVM, que usa el test del archivo y el sembrador. Con `org.json` (simulado en la JVM) el test validaría un lector distinto del de la app. |
| D34 | Sembrado | `GrinderCatalogSeeder(dao, source: CatalogSource, versions: CatalogVersionStore, defaults: DefaultGrinderStore)`, `suspend fun sync(): SyncResult` (`UpToDate`, `Synced(version, added, updated, retired, deleted)`, `Failed(reason)`). Todo en una transacción de `GrinderDao.syncCatalog`; la versión se escribe en las preferencias **después** del commit. Un predefinido "en uso" es el que tiene recetas o preparaciones, `owned = true` o es el predeterminado. Se llama desde `PourlineApp.onCreate` en `applicationScope` con `Dispatchers.IO` (en F10, no antes). Los errores van a `Log.w`. | §3.6 y §4. Si el proceso muere entre el commit y la preferencia, el siguiente arranque repite la sincronización, que es idempotente. |
| D35 | Borrador y puerta | El borrador de F2 se escribe ya en la ruta final, pero **nada lo lee en la app** hasta F10 (el sembrador no se invoca al arrancar). `GrinderCatalogFileTest` lo valida desde F2. F10 solo empieza con la tabla aprobada. | La tabla vive solo en el JSON y en el informe (§3.6). Así el borrador se valida con el mismo test que el final y no se siembra nada antes de tiempo. |
| D36 | Catálogo de prueba | `androidTest/.../grinder/TestGrinderCatalog.kt` y `test/.../grinder/GrinderFixtures.kt` con marcas ficticias ("Acme Uno" de clics, "Acme Dos" de clics con medio paso, "Acme Tres" compuesto de rotaciones, números y clics y "Acme Inverso" `HIGHER_IS_FINER`), con rangos y anclas inventados a propósito. `TestAppDependencies.seedGrinders(json = TestGrinderCatalog.JSON)` lo siembra con el sembrador real. | F5 a F9 avanzan sin los datos reales, y nadie confunde un dato de prueba con uno de la fuente. |
| D37 | Escala, jerarquía y anclas de los predefinidos **(concreta)** | La fuente no da el total de clics ni la jerarquía. Regla: la **escala** es la del gráfico de la fuente, cuyo máximo corresponde al máximo en micras (el C2: 0–30 clics para 0–950 µm); si el fabricante publica otra, se anota en `notes` y en la tabla de la puerta para que decida el usuario (P3). La **jerarquía** sale del formato de la tabla de la fuente y de los máximos que aparecen en sus rangos, y se confirma con el manual del fabricante, citado en `notes`. Un dial que empieza en 1 (Fellow Ode) se modela con `scaleMin` > 0 y la descomposición absoluta (D11), y los pasos intermedios del Ode como unidad base `STEPS` con `perParent = 3`, sin fracciones de un tercio. Las **anclas** se calculan con `scaleMin + (µ − µmin) / (µmax − µmin) × (scaleMax − scaleMin)` sobre los puntos medios del §3.6, redondeadas a 1 decimal, y se omiten fuera del rango en micras; con `anchorsManual: true` se pueden ajustar a mano (§6 del spec). Nada se inventa: lo que falta va a `missing`. | Las anclas del §3.6 suponen un molino lineal entre su rango en micras y su escala: usar otra escala que la del gráfico de la fuente rompería esa correspondencia. |
| D38 | Nombre de la fuente por molino **(amplía D9)** | El catálogo y el modelo llevan `sourceName` (obligatorio en el archivo; `grinders.sourceName TEXT`, nulo en los propios y en los duplicados). La atribución es "Fuente: <nombres> · valores orientativos", con los `sourceName` distintos de los predefinidos que intervienen, y los enlaces por modelo siguen abriendo cada `sourceUrl`. La BD sigue en v5: la v5 solo existe en esta rama (ni `develop` ni `main` tienen `5.json`), así que se amplió `5.json` y `MIGRATION_4_5` en lugar de crear una v6. Una build de depuración con la v5 anterior necesita `pm clear`. | Con el Femobook A2 hay predefinidos cuya fuente no es Honest Coffee Guide. |
| D39 | Femobook A2 | Datos aprobados por el usuario: Rotaciones › Clics (40 por rotación), escala 0–120, paso 1, `HIGHER_IS_COARSER`; V60, Kalita Wave, Chemex y Origami 50–80, AeroPress 40–50, Moka 10–18 y Prensa francesa 80–120; sin rango para Espresso ni Cold brew; sin anclas (los 18 µm por clic del fabricante no cuadran con los rangos). `sourceName` "Femobook, The Way to Coffee y Earl Coffee" y `sourceUrl` de The Way to Coffee; los respaldos (FAQ de Femobook y Earl Coffee) van en `notes`. **Enmienda 1:** el A2 sigue sin anclas en el catálogo, pero tiene curva implícita (§3.3.2), así que convierte con cualquier molino que tenga curva, sea cual sea el método. Su rango de moka se revisa en la puerta del §3.6. | No hay página del A2 en Honest Coffee Guide. |

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
- **Molino sin curva de grosor y sin 2 pares de calibración** (enmienda 1): "Sin equivalencia",
  con el enlace para completar o duplicar. Con un solo par: la pista "Añade otra equivalencia".
- **Receta con método `OTHER`, sin método o con un método sin rango en algún molino** (enmienda 1).
  No cambia nada: la conversión no depende del método.
- **Par de calibración contradictorio** (enmienda 1). No se guarda. Se muestra la equivalencia con
  la que choca y la opción "Reemplazar".
- **Par con un valor ya registrado** (enmienda 1). Se actualiza el par existente: "Equivalencia
  actualizada".
- **Curva implícita con datos incoherentes** (enmienda 1). Si la recta de los rangos tiene la
  pendiente al revés de la dirección del molino, no hay curva. Si sale con la pendiente correcta
  pero con rangos que se contradicen (el A2), el resultado es "aproximado", y la calibración del
  usuario lo corrige.
- **Molino con pares que deja de ser mío, se retira o cambia de escala** (enmienda 1). Los pares se
  conservan y siguen aplicando. Un valor fuera de la nueva escala se muestra con "Fuera de la
  escala".
- **Resultado fuera de la escala del destino.** Se limita al extremo, con "al límite del molino".
- **Ajuste fuera de los pares o de las anclas.** Se extrapola con el tramo extremo, sin aviso
  (enmienda 1).
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
- **Rendimiento.** La conversión es aritmética local, sin E/S. Los pares de calibración llegan como
  `Flow` y no se leen en cada lectura de la báscula (enmienda 1). La tarjeta de molienda no debe
  empeorar el `SessionPerformanceTest` (umbral p95 de 32 ms).
- **Seguridad y privacidad.** Todo es local. No hay red ni permisos nuevos, y el enlace a la fuente
  se abre en el navegador del sistema.

## 5. Criterios de Aceptación (Definition of Done)

### Datos y migración
- [x] `HistoryDatabase` está en la versión 5. `HistoryMigrationTest` cubre la migración 4 → 5: crea
      las tablas nuevas, quita `grind` de `recipes` y `brews`, añade `grinderId`, `grindValue` y
      `grindLabel`, y conserva el resto de columnas, filas y FK.
- [x] `BrewMethod` incluye `FRENCH_PRESS`, `ESPRESSO`, `COLD_BREW` y `MOKA`, con nombre visible,
      selectores de recetas y preparaciones, y filtro del historial.
- [x] Las recetas integradas ya no tienen `grind` y se siembran sin molino.

### Catálogo
- [x] La tabla de datos de los molinos del §3.6 está revisada y aprobada por el usuario antes de
      sembrarla.
- [x] Un test unitario valida el archivo del catálogo:
  - cada molino pasa `GrinderValidator`;
  - las claves son únicas;
  - cada molino tiene `sourceUrl`;
  - los rangos caben en la escala;
  - las anclas son monótonas.
- [x] `GrinderCatalogSeeder`:
  - siembra en el primer arranque;
  - con una `version` mayor, actualiza por `catalogKey` sin tocar los propios ni `owned`;
  - marca `retired` los retirados en uso;
  - borra los retirados sin uso.

  Todo cubierto por tests.

### Conversión y formato (tests unitarios JVM)
- [x] Límite físico: el valor se queda en el extremo y lleva `AT_LIMIT`.
- Sustituidos por la enmienda 1 (ya no aplican): conversión por posición dentro del rango del
  método, extrapolación fuera de ese rango, respaldo por anclas comunes y `NoEquivalent` por menos
  de 2 anclas en común.
- [x] Redondeo al paso (entero y 0,5), con `ROUNDED` solo cuando cambia el valor.
- [ ] Molino `HIGHER_IS_FINER`: la conversión entre un molino invertido y uno normal mantiene la
      posición de fino a grueso, por curvas y por calibración (rehecho en la enmienda 1).
- [x] Mismo molino como origen y destino: `Exact` con el mismo valor.
- [x] Punto medio "sugerido" para una receta sin ajuste.
- [x] Formato compuesto: "1 · 2 · 3" en un molino de prueba con rotaciones, números con
      `perParent = 4` y clics con `perParent = 10` da 63 clics y vuelve a "1 · 2 · 3". El acarreo del stepper cruza la
      unidad superior en los dos sentidos.
- [x] `GrinderValidator` rechaza cada regla del §3.5 con un caso de prueba.

### Interfaz (tests instrumentados de Compose en el AVD `SG_API34`)
- [x] Crear un molino propio con unidad compuesta y rangos, guardarlo y verlo en mis molinos.
- [x] Duplicar un predefinido: la copia es editable y el original sigue de solo lectura.
- [x] Onboarding: elegir un molino lo deja como predeterminado; "Omitir" termina sin molino.
- [x] Tarjeta de molienda en Preparar y en el paso 2 de la sesión:
  - en Básico, sin regla;
  - en Experto, con regla;
  - cambiar "Moler con" muestra el ajuste convertido;
  - sin molinos propios, el enlace "Añade tu molino".
- [x] La preparación guardada muestra el molino y el ajuste usados.
- [ ] Convertidor suelto: con origen y ajuste, sin selector de método, lista el equivalente en cada
      uno de mis molinos (rehecho en la enmienda 1).
- [ ] El atajo "Ver en mis otros molinos", desde la tarjeta de molienda y desde el detalle de la
      receta, abre el convertidor relleno con el molino y el ajuste de origen (rehecho en la
      enmienda 1).
- [x] Borrar un molino usado en recetas muestra "Se usa en N recetas". Tras confirmar, la receta
      muestra la copia con "molino eliminado".
- [x] La fuente "Fuente: <nombre> · valores orientativos" (el `sourceName` de cada predefinido) aparece en el detalle de un predefinido
      y en una conversión en la que interviene uno.
- [x] `SessionPerformanceTest` no empeora respecto a `develop`.

### Cierre
- [x] `./gradlew :app:testDebugUnitTest` y `:app:connectedDebugAndroidTest` pasan, salvo los fallos
      previos conocidos (`CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap`, por el
      idioma del emulador, y la oscilación del caso Experto de `SessionPerformanceTest`; resultados en el
      plan).
- [x] El lint y el formato del proyecto pasan en los archivos tocados.
- [x] Hay una revisión manual con capturas por adb (`screencap`) de:
  - mis molinos;
  - el editor (las dos pestañas);
  - la tarjeta en Básico y en Experto;
  - el convertidor;
  - el paso del onboarding.

### Enmienda 1: conversión por calibración y curva de grosor
- [ ] Puerta del §3.6 superada: el usuario aprobó `METHOD_LEVELS`, la curva implícita del A2 y la
      decisión sobre su rango de moka antes de tocar la interfaz.
- [ ] `HistoryDatabase` en v7 (o la siguiente libre, §3.4). `HistoryMigrationTest` cubre la
      migración 6 → 7: crea `grind_calibrations` con sus FK, índices y únicos, y conserva el resto de
      datos.
- [ ] Tests JVM de `GrindCurve`:
  - curva explícita por tramos con todas las anclas del molino y extrapolación;
  - curva implícita por mínimos cuadrados, con los puntos repetidos contados una vez;
  - sin curva con menos de 2 niveles distintos o con la pendiente al revés de la dirección;
  - `level` y `value` son inversas.
- [ ] Un test JVM recalcula `METHOD_LEVELS` desde `catalog.json` y coincide con la constante.
- [ ] Tests JVM de `GrindConverter`:
  - el mismo ajuste de origen da el mismo resultado con cualquier método, también `OTHER` y sin
    método (la firma ya no recibe el método);
  - Chestnut S3 ↔ Femobook A2 convierte en los dos sentidos con las curvas y lleva `ANCHORS`;
  - con 2 o más pares: interpolación entre pares, `Exact` en un par, extrapolación fuera de ellos,
    `CALIBRATED` y nunca `ANCHORS`;
  - con 1 par: el resultado pasa por el par y se desplaza con las curvas; sin curva,
    `NoEquivalent` con `ADD_CALIBRATION`;
  - `ROUNDED` y `AT_LIMIT` se siguen aplicando después de la calibración.
- [ ] Tests JVM de `CalibrationValidator`: par válido, valor repetido (`Update`), par
      contradictorio (`Conflict`), molinos iguales, valor fuera de escala o fuera del paso, y
      molinos con direcciones opuestas.
- [ ] Tests del repositorio: guardar, actualizar, reemplazar en una transacción y borrar en cascada
      al borrar un molino; duplicar un predefinido no copia pares; la sincronización del catálogo no
      los toca.
- [ ] Tests instrumentados de Compose en `SG_API34`:
  - en el convertidor, "Corregir" guarda un par y la fila pasa a mostrar el valor corregido; con un
    segundo par, otra fila intermedia muestra "según tu calibración";
  - sin "Corregir" cuando el origen no está en mis molinos;
  - en el detalle, la sección "Equivalencias" lista, añade, edita y borra pares, y muestra el
    conflicto con "Reemplazar";
  - la tarjeta de molienda muestra "según tu calibración" con 2 pares.
- [ ] `SessionPerformanceTest` no empeora respecto a `develop`.
- [ ] Verificación manual en el móvil del usuario por adb: con 2 pares reales Chestnut S3 ↔
      Femobook A2 introducidos por el usuario, el convertidor da un único valor "según tu
      calibración" en los dos sentidos, sin selector de método. Capturas con `screencap` del
      convertidor y de la sección "Equivalencias".
- [ ] `./gradlew :app:testDebugUnitTest` y `:app:connectedDebugAndroidTest` pasan (salvo los fallos
      previos conocidos) y el lint y el formato pasan en los archivos tocados.

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
- **Rango de moka del Femobook A2 (enmienda 1).** 10–18 clics choca con el resto de sus rangos y
  desvía su curva implícita. Se decide en la puerta del §3.6: mantenerlo, corregirlo con otra
  fuente o quitarlo.

## 7. Alternativas descartadas

- **Conversión por micras (unidad común).** Es más precisa en teoría, pero requiere micras por
  clic fiables para cada molino, y la fuente no las da. La calibración y la curva de grosor
  (enmienda 1) solo dependen de pares que comprueba el usuario y de anclas y rangos que la fuente sí
  publica y que el usuario puede editar.
- **Conversión por posición dentro del rango del método (enmienda 1).** Era la regla principal
  original. Se descarta porque los rangos de cada método no son coherentes entre molinos: el mismo
  ajuste del S3 (20 clics) daba 14, 43, 55, 60 o 62 en el A2 según el método, y "Sin equivalencia"
  sin método o con un método sin rango. Tampoco se conserva como último recurso, para que nunca
  dependa del método.
- **Encadenar calibraciones (enmienda 1).** Pasar de S3 a C2 a través de los pares S3↔A2 y A2↔C2
  acumula errores y hace la conversión menos predecible. Solo cuentan los pares directos.
- **Borrar automáticamente los pares en conflicto (enmienda 1).** Se prefiere rechazar el par y
  ofrecer "Reemplazar", para que el usuario vea qué equivalencia pierde.
- **Anclas a mano solo para el A2 (enmienda 1).** No resolvía los molinos propios que solo tienen
  rangos. La curva implícita cubre los dos casos.
- **Molinos predefinidos editables con "Restaurar valores de fábrica".** Se descarta porque una
  actualización del catálogo chocaría con los cambios del usuario. Se usa solo lectura con
  "Duplicar para personalizar".
- **Migración destructiva de la base de datos.** Se descarta para mantener probada la cadena de
  migraciones v1 → v5.
- **Conservar el texto libre `grind` como "nota de molienda".** No hay datos reales que conservar.
