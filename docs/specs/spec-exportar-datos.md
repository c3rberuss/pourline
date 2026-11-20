# SPEC-011: Copia de seguridad (exportar e importar todos los datos)

| Campo | Valor |
|---|---|
| Estado | Implementado el 2026-11-20 en `feat/data-backup` (F0 a F10 del [plan](plan-exportar-datos.md)); pendiente la prueba manual del usuario en su teléfono y `/code-review` |
| Fecha | 2026-11-19 |
| Rama | `feat/data-backup` |
| Deriva de | Uso real: al cambiar de móvil se pierden todos los datos de la app |
| Módulos afectados | `:app`: nuevo paquete `backup`, `history` (`HistoryDatabase`), `settings`, `coffee/photo`, `grinder` (`GrinderCatalogSeeder`), `session` (`ScaleUiState`, `GrindSelection`), `ui/settings`, `Navigation.kt`, `res/xml`, `AndroidManifest.xml` |
| Specs relacionados | [SPEC-002](spec-historial.md), [SPEC-003](spec-curva-objetivo.md), [SPEC-004](spec-catalogo-cafes.md), [SPEC-006](spec-molinos.md), [SPEC-009](spec-compartir-receta.md) |

## 1. Resumen y Contexto

- **Problema:**
  - Al cambiar de móvil (o reinstalar la app) el usuario pierde los cafés, los molinos, las calibraciones, las
    recetas, el historial de preparaciones y sus ajustes. No hay forma de hacer una copia de seguridad ni de pasar
    los datos a otro dispositivo.
  - La copia automática de Android está activa por omisión: `AndroidManifest.xml` solo declara
    `android:allowBackup="true"` y no tiene `android:dataExtractionRules` ni `android:fullBackupContent`. Los archivos
    `res/xml/backup_rules.xml` y `res/xml/data_extraction_rules.xml` existen, pero son las plantillas vacías del
    asistente y nada los usa. Sin reglas, Android copia todo lo que la app guarda salvo las cachés: no hay control sobre
    qué se copia.
- **Datos de la app hoy:**
  - Base de datos Room `history.db` (`HistoryDatabase`, versión 7) con 11 tablas: `brews`, `brew_curves`,
    `brew_targets`, `recipes`, `recipe_stages`, `coffees`, `grinders`, `grinder_units`, `grinder_method_ranges`,
    `grinder_anchors` y `grind_calibrations`.
  - Fotos de las etiquetas en `filesDir/coffee_photos` (`CoffeePhotoStore`): `<uuid>.jpg` y `<uuid>_thumb.jpg`. Las
    columnas `photo1` y `photo2` de `coffees` guardan solo el nombre principal.
  - Un único archivo de `SharedPreferences`, `settings` (`BrewSettings.PREFS_NAME`), compartido por `BrewSettings`,
    `ConnectionSettings`, `ExperienceSettings`, `GrinderSettings`, `TargetSettings`, `ThemeSettings` y
    `TourSettings`.
- **Solución:**
  - Exportar todos los datos y los ajustes de uso a un único archivo ZIP que el usuario guarda donde quiera (carpeta
    local o Google Drive) con el selector de archivos de Android.
  - Importar ese archivo en cualquier dispositivo, reemplazando todos los datos actuales, de forma atómica y tras
    una confirmación con el resumen de la copia.
  - Configurar reglas explícitas para la copia automática de Android.
- **Éxito:** todos los datos de la app se pueden exportar desde un dispositivo e importar en cualquier otro, y la app
  queda con exactamente los mismos cafés (con sus fotos), molinos, calibraciones, recetas, historial y ajustes de
  uso.
- **Alcance (In):**
  - Exportar e importar manualmente desde Ajustes, en los modos Básico y Experto.
  - Selector de archivos (Storage Access Framework) para guardar y para abrir la copia.
  - Las 11 tablas, todas las fotos de los cafés y los ajustes de uso (§3.2).
  - Reglas explícitas de la copia automática y la transferencia entre dispositivos de Android.
- **Alcance (Out):**
  - Copias automáticas o programadas.
  - Cifrado o contraseña del archivo.
  - Exportar a CSV u otros formatos de análisis.
  - Fusionar una copia con los datos existentes: importar siempre reemplaza.
  - Abrir la copia desde otra app ("Abrir con", intent `VIEW`) o desde la hoja de compartir.
  - Ofrecer la restauración en el onboarding del primer arranque.

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como usuario, quiero exportar todos mis datos a un archivo y guardarlo donde quiera (por ejemplo, Google
  Drive), para tener una copia de seguridad.
- **US-2:** Como usuario que estrena móvil, quiero importar esa copia y encontrar mis cafés, molinos, calibraciones,
  recetas, historial y ajustes tal como los tenía.
- **US-3:** Como usuario, quiero ver qué contiene la copia (fecha y recuentos) y confirmar antes de que reemplace mis
  datos actuales, para no perder nada por error.
- **US-4:** Como usuario, quiero que una copia dañada o incompatible no toque mis datos y me diga claramente qué
  pasó.
- **US-5:** Como usuario, quiero que la copia automática de Android restaure mis datos de forma consistente cuando
  configuro un móvil nuevo con mi cuenta.

### Flujo de exportación

1. Ajustes > tarjeta "Copia de seguridad" > "Exportar datos".
2. Se abre el selector de archivos (`ActivityResultContracts.CreateDocument("application/zip")`) con el nombre
   propuesto `pourline-backup-AAAA-MM-DD-HHmm.zip` (hora local, por ejemplo `pourline-backup-2026-11-19-1845.zip`).
3. Si el usuario cancela el selector, no pasa nada.
4. La app escribe el ZIP en el URI elegido con un indicador de progreso que bloquea la tarjeta (no cancelable).
5. Al terminar: mensaje de éxito con los recuentos exportados; si falla, mensaje de error y se intenta borrar el
   documento a medio escribir (`DocumentsContract.deleteDocument`, si el proveedor lo permite).

### Flujo de importación

1. Ajustes > "Copia de seguridad" > "Importar datos". Si hay una preparación en curso, el botón está deshabilitado
   con una explicación (§4).
2. Se abre el selector (`ActivityResultContracts.OpenDocument`, tipos `application/zip`,
   `application/x-zip-compressed` y `application/octet-stream`: algunos proveedores informan el segundo para los ZIP
   creados en Windows y otros no informan el tipo).
3. La app lee y valida el archivo completo sin modificar nada (§3.4). Si no es válido, muestra el error y termina.
4. Diálogo de confirmación con la fecha de la copia, la versión de la app que la creó y los recuentos ("12 cafés,
   3 molinos, 8 recetas, 140 preparaciones, 15 fotos"), y el aviso de que todos los datos actuales se reemplazarán.
   Botones "Reemplazar" (destructivo) y "Cancelar".
5. Con "Reemplazar", la importación se aplica de forma atómica (§3.5) con un indicador de progreso no cancelable.
6. Al terminar: mensaje de éxito y, si hubo defectos tolerados, el aviso correspondiente ("2 fotos no se pudieron
   restaurar"). La app muestra los datos y ajustes importados sin necesidad de reiniciarse.

## 3. Especificación Técnica

### 3.1 Formato del archivo (versión 1)

ZIP con estas entradas, y solo estas:

| Entrada | Contenido |
|---|---|
| `manifest.json` | Metadatos y recuentos. Es la primera entrada del ZIP |
| `data.json` | Las 11 tablas |
| `settings.json` | Ajustes de uso (§3.2) |
| `photos/<nombre>.jpg` | Fotos de los cafés: principales y miniaturas (`<uuid>.jpg`, `<uuid>_thumb.jpg`) |

`manifest.json`:

```json
{
  "app": "pourline",
  "formatVersion": 1,
  "schemaVersion": 7,
  "appVersionName": "1.0",
  "appVersionCode": 1,
  "createdAt": "2026-11-19T18:45:12-06:00",
  "counts": {
    "coffees": 12, "grinders": 3, "grindCalibrations": 4, "recipes": 8,
    "brews": 140, "photos": 15
  }
}
```

- `app` identifica el archivo como copia de Pourline. `formatVersion` es la versión de este formato, independiente
  de la versión del esquema de Room. `schemaVersion` es informativo (diagnóstico).
- `counts` resume la copia: `coffees`, todas las filas (también los archivados); `grinders`, solo los molinos propios
  (`owned = true`), no los predefinidos del catálogo que también están en la tabla; `grindCalibrations`, `recipes`
  (incluidas las predefinidas) y `brews`, todas las filas; `photos`, las fotos principales distintas incluidas en
  `photos/`, sin miniaturas. Son informativos: al importar, el diálogo de confirmación usa recuentos recalculados con
  los datos validados y las fotos presentes.
- `data.json` contiene un objeto por tabla con una lista de filas. Las filas se serializan con DTOs propios
  `@Serializable` del paquete `backup` (kotlinx.serialization, ya en el proyecto), **no** con las entidades de Room,
  para que un cambio de esquema no altere el formato sin querer.
- Se conservan los IDs primarios de todas las filas, de modo que las claves foráneas entre tablas y los IDs
  guardados en las preferencias (`last_coffee_id`, `default_grinder_id`, `target_ref_id`) siguen siendo válidos.
- Los `ByteArray` (por ejemplo, `BrewCurveEntity.data` y la curva de `brew_targets`) se codifican en Base64.
- Los enums se guardan por nombre, igual que en los `TypeConverters` actuales.

### 3.2 Ajustes incluidos

Del archivo `settings`, se exportan e importan:

| Clave | Dueño |
|---|---|
| `theme_mode` | `ThemeSettings` |
| `experience_mode`, `onboarding_done` | `ExperienceSettings` |
| `use_scale`, `last_scale_address`, `last_scale_name` | `ConnectionSettings` |
| `auto_finish_seconds`, `countdown_seconds`, `last_coffee_id`, `default_dose_g`, `default_ratio` | `BrewSettings` |
| `target_ref_type`, `target_ref_id`, `stage_vibration`, `stage_sound` | `TargetSettings` |
| `default_grinder_id`, `grinder_catalog_version` | `GrinderSettings` |

- **No** se incluye `tour_seen` (`TourSettings`): el estado del recorrido guiado es del dispositivo.
- `onboarding_done` se escribe como `true` al importar: quien importa desde Ajustes ya completó el onboarding.
- En `settings.json` solo van las claves presentes en el origen. Al importar, una clave ausente se elimina en el
  destino, para que el valor por defecto se comporte igual que en el origen.
- `grinder_catalog_version` viaja con los molinos para que `GrinderCatalogSeeder.sync()` sepa si el catálogo
  importado está desactualizado. Tras importar se ejecuta `sync()` (§3.5).

### 3.3 Contratos

Paquete nuevo `dev.josueamaya.pourline.backup`. Firmas orientativas; el plan fija los detalles:

```kotlin
/** Resumen de una copia leída y validada, para el diálogo de confirmación. */
data class BackupSummary(
  val createdAt: OffsetDateTime,
  val appVersionName: String,
  val counts: BackupCounts,
)

sealed interface ExportResult {
  data class Success(val counts: BackupCounts) : ExportResult
  data class Failed(val reason: BackupError) : ExportResult
}

/** Copia validada y lista para aplicarse; no ha modificado nada. */
class PreparedImport internal constructor(/* datos ya leídos y fotos en staging */) {
  val summary: BackupSummary
}

sealed interface PrepareResult {
  data class Ready(val import: PreparedImport) : PrepareResult
  data class Invalid(val reason: BackupError) : PrepareResult
}

sealed interface ImportResult {
  data class Success(val counts: BackupCounts, val missingPhotos: Int) : ImportResult
  data class Failed(val reason: BackupError) : ImportResult   // los datos no cambiaron
}

enum class BackupError {
  NOT_A_BACKUP,          // no es ZIP, falta manifest.json o app != "pourline"
  NEWER_FORMAT,          // formatVersion mayor que el soportado
  CORRUPT,               // JSON inválido, campos obligatorios ausentes, claves repetidas, entradas inesperadas o rutas inseguras
  BROKEN_REFERENCES,     // una fila referencia otra que no está en la copia
  TOO_LARGE,             // supera los límites de tamaño de §3.4 (al importar o al exportar)
  IO,                    // fallo de lectura o escritura (proveedor, espacio)
}

class BackupManager(/* HistoryDatabase, CoffeePhotoStore/dir, ajustes, GrinderCatalogSeeder, ContentResolver */) {
  suspend fun export(target: Uri, onProgress: (Float) -> Unit): ExportResult
  suspend fun prepareImport(source: Uri, onProgress: (Float) -> Unit): PrepareResult
  suspend fun apply(prepared: PreparedImport, onProgress: (Float) -> Unit): ImportResult
  fun discard(prepared: PreparedImport)   // borra el staging si el usuario cancela
}
```

- `BackupManager` se crea en `PourlineApp` junto al resto de dependencias.
- La lógica de formato (DTOs, validación, ida y vuelta) debe poder probarse con tests unitarios sin Android.
- Todo el trabajo corre en `Dispatchers.IO`.

### 3.4 Validación (antes de tocar nada)

`prepareImport` lee el ZIP entero y rechaza la copia si:

- No es un ZIP legible, no tiene `manifest.json` o `app` no es `"pourline"` → `NOT_A_BACKUP`.
- `formatVersion` es mayor que el que soporta la app → `NEWER_FORMAT`. Una versión igual o menor se acepta: las
  versiones futuras del formato deben seguir leyendo las anteriores.
- `data.json` o `settings.json` no se pueden deserializar, faltan campos obligatorios, hay entradas fuera de la lista
  de §3.1 o una ruta intenta salir del directorio de destino (`..`, rutas absolutas: "zip slip") → `CORRUPT`.
- Una clave foránea apunta a una fila ausente de la copia (por ejemplo, un `brew` con un `coffeeId` que no existe,
  o una etapa de una receta inexistente) → `BROKEN_REFERENCES`. Las columnas de referencia que admiten `null` se
  validan solo si tienen valor.
- Una clave primaria o única se repite (por ejemplo, dos cafés con el mismo id o dos molinos con la misma
  `catalogKey`) → `CORRUPT`. Se detecta aquí para no llegar a la transacción, que fallaría y se informaría como `IO`.
- Se supera algún límite de tamaño → `TOO_LARGE`. Los bytes se cuentan al descomprimir, sin fiarse de los tamaños
  que declara el ZIP. Límites: `data.json` hasta 64 MiB; cada foto hasta 16 MiB; `manifest.json` y `settings.json`
  hasta 256 KiB cada uno; como mucho 10 000 entradas y 1 GiB descomprimido en total. Como referencia, el caso grande
  de §4 ocupa unos 55 MB, de los que unos 13 MB son `data.json`.

Los mismos límites se comprueban al exportar: si los datos los superan, la exportación falla con `TOO_LARGE` y se
intenta borrar el documento a medio escribir. Así la app nunca escribe una copia que ella misma rechazaría al
importarla.

Se toleran, sin rechazar la copia:

- Una foto referenciada por `photo1` o `photo2` que no está en `photos/`: el café queda sin esa foto, y se cuenta en
  `missingPhotos`. Si falta solo la miniatura, se regenera a partir de la foto principal.
- Fotos en `photos/` que ningún café referencia: se ignoran.

Las fotos se descomprimen a un directorio de staging privado (dentro de `filesDir`, en el mismo volumen que
`coffee_photos` para permitir un renombrado atómico). Si la validación falla o el usuario cancela, el staging se
borra.

### 3.5 Aplicación atómica

Orden de `apply`:

1. Transacción única de Room (`withTransaction`): borrar todas las tablas e insertar todas las filas de la copia con
   sus IDs. Si falla, se revierte y se borra el staging: los datos no cambian.
2. Sustituir `coffee_photos` por el directorio de staging (renombrar el actual a un nombre temporal, renombrar el
   staging a `coffee_photos` y borrar el temporal).
3. Escribir los ajustes de §3.2 a través de las clases de `settings` (no directamente en `SharedPreferences`), para
   que sus `StateFlow` en memoria reflejen los valores nuevos sin reiniciar la app.
4. Ejecutar `GrinderCatalogSeeder.sync()` por si la copia trae un catálogo de molinos anterior al de la app.

Requisitos:

- La interfaz (listas de cafés, recetas, historial, molinos) refleja los datos importados sin reiniciar la app.
  Los repositorios ya exponen `Flow` de Room. Lo que Room no invalida se descarta al terminar: los deshacer
  pendientes de preparaciones y recetas, la elección de "Moler con" y la caché de imágenes. Además, las pestañas
  Cafés, Recetas, Historial y Herramientas vuelven a su pantalla raíz, para que un editor abierto no guarde un
  borrador sobre los datos importados (con ids que ahora son de otras filas).
- Si la app muere durante la importación, el siguiente arranque deja un estado consistente: la base ya importada es
  la fuente de verdad. Antes de la transacción se escribe un marcador (`filesDir/backup_import/import.json`, con
  escritura atómica) con los ajustes que se van a restaurar y una huella de los datos de la copia: recuentos por
  tabla, ids máximos de las tablas autoincrementales y nombres de foto referenciados. Tras la transacción, el
  marcador pasa a "base confirmada". Al arrancar, antes del barrido de fotos huérfanas y de la sincronización del
  catálogo:
  - Marcador en "base confirmada", o en "aplicando" con la huella de la base igual a la de la copia: se terminan los
    pasos 2 a 4. El cambio de fotos se puede repetir sin daño desde cualquier estado intermedio.
  - Marcador en "aplicando" con otra huella: la transacción no llegó a confirmarse; se borran el staging y el
    marcador.
  - Sin marcador: se borra el directorio de trabajo huérfano, si lo hay.
- Una vez confirmada la transacción, la importación no se informa como fallida aunque falle un paso posterior: los
  datos ya cambiaron, y el siguiente arranque termina los pasos pendientes.
- Durante `apply` la interfaz no permite navegar fuera de Ajustes ni iniciar una preparación.

### 3.6 Copia automática de Android

- El manifiesto pasa a referenciar `android:dataExtractionRules="@xml/data_extraction_rules"` (API 31 o superior) y
  `android:fullBackupContent="@xml/backup_rules"` (API 26-30); hoy no referencia ninguno de los dos (§1).
  `allowBackup` sigue en `true`.
- Qué incluye cada regla:

| Regla | `database`: `history.db`, `history.db-wal`, `history.db-shm` | `sharedpref`: `settings.xml` | `file`: `coffee_photos/` |
|---|---|---|---|
| `data_extraction_rules`, `cloud-backup` (API 31+) | Sí | Sí | No |
| `data_extraction_rules`, `device-transfer` (API 31+) | Sí | Sí | Sí |
| `backup_rules` (API 26-30, nube y dispositivo) | Sí | Sí | No |

- Las fotos no van en la copia en la nube: Android admite 25 MB por app y, si se superan, no copia nada, ni la base
  ni los ajustes. Unas 50 fotos ya los superan. `backup_rules` no distingue entre la nube y la transferencia entre
  dispositivos, así que tampoco las lleva. La exportación manual siempre lleva las fotos.
- Consecuencia aceptada: tras una restauración desde la nube (y, en API 26-30, también tras una transferencia), los
  cafés quedan sin sus fotos; la interfaz muestra el marcador sin foto cuando falta el archivo. Para conservarlas, la
  transferencia directa entre móviles con API 31 o superior, o la exportación manual.
- Todo lo demás queda excluido (cachés, staging de importación, archivos temporales de compartir y bases de los
  tests de rendimiento).
- No se puede excluir una clave dentro de `settings.xml`, así que la copia de Android incluye `tour_seen`. Es una
  diferencia aceptada respecto a la exportación manual.

### 3.7 Interfaz

- Tarjeta nueva "Copia de seguridad" en `SettingsScreen`, visible en los modos Básico y Experto (sin
  `ExpertFeature`), con un texto breve que explica qué incluye y los botones "Exportar datos" e "Importar datos".
- Textos en `values` (español) y `values-en` (inglés), como el resto de la app.
- Etiquetas de test (`testTag`) para los botones, el diálogo de confirmación y el indicador de progreso.
- Mensajes de error por cada `BackupError`, en lenguaje sencillo, por ejemplo:
  - `NOT_A_BACKUP`: "Este archivo no es una copia de Pourline."
  - `NEWER_FORMAT`: "Esta copia se creó con una versión más nueva de Pourline. Actualiza la app para importarla."
  - `CORRUPT` y `BROKEN_REFERENCES`: "La copia está dañada. Tus datos no se han modificado."
  - `TOO_LARGE` al importar: "La copia es demasiado grande para importarla. Tus datos no se han modificado."
  - `TOO_LARGE` al exportar: "Tus datos superan el tamaño máximo de una copia."
  - `IO`: "No se pudo leer o escribir el archivo."

### 3.8 Evolución futura

Toda migración nueva de `HistoryDatabase` (versión 8 en adelante) debe actualizar los DTOs de `backup` y, si el
cambio afecta al formato, subir `formatVersion` y mantener la lectura de las versiones anteriores, con su test.

El lector rechaza los campos desconocidos (`ignoreUnknownKeys = false`) para no descartar datos en silencio. Por eso
cualquier campo nuevo en los DTOs, aunque sea opcional, obliga a subir `formatVersion`: así una versión anterior de la
app informa `NEWER_FORMAT` (pide actualizar) en lugar de `CORRUPT`.

## 4. Casos Límite y Manejo de Errores

- Archivo que no es ZIP, ZIP sin `manifest.json` o ZIP de otra app → `NOT_A_BACKUP`; no se cambia nada.
- Copia creada por una versión más nueva de la app (`formatVersion` mayor) → `NEWER_FORMAT`; no se cambia nada.
- JSON corrupto, entrada inesperada, ruta insegura o referencias rotas → `CORRUPT` o `BROKEN_REFERENCES`; no se
  cambia nada.
- ZIP desproporcionado (bomba ZIP) o que supera los límites de §3.4 → `TOO_LARGE`; no se cambia nada.
- Clave primaria o única repetida en la copia → `CORRUPT`; no se cambia nada.
- Los datos superan los límites de §3.4 al exportar → `TOO_LARGE`; se intenta borrar el documento a medio escribir.
- Falta una foto en la copia → el café queda sin foto, la importación sigue y el mensaje final lo indica.
- Falla la transacción de Room al importar → se revierte, se borra el staging y se informa `IO`; los datos no
  cambian.
- El proceso muere a mitad de la importación → al siguiente arranque, estado consistente según §3.5.
- Hay una preparación en curso → "Importar datos" está deshabilitado con el texto "Termina la preparación en curso
  para importar". Exportar sí está permitido. Cuenta como preparación en curso una sesión abierta en
  `ScaleSessionManager` (`ScaleUiState.hasSession`: guiada, sin báscula, con cronómetro o en cuenta atrás;
  `BrewService` solo corre con una sesión abierta) o la última preparación aún guardándose después de pulsar "Listo"
  (`savedBrew == SavedBrew.Saving`), porque se guardaría con ids que, tras importar, serían de otro café o molino.
  La condición se vuelve a comprobar al pulsar "Reemplazar".
- El usuario cancela el selector o el diálogo de confirmación → no pasa nada; el staging se borra.
- Sin espacio o error del proveedor (por ejemplo, Drive sin conexión) al exportar → `IO`; se intenta borrar el
  documento a medio escribir.
- App sin datos (instalación nueva) → exportar está permitido y produce una copia válida con recuentos a cero y los
  ajustes presentes.
- Importar en una instalación nueva (sin datos) → mismo flujo y misma confirmación.
- Recetas predefinidas: viajan como cualquier receta. Tras importar quedan exactamente las de la copia (también si
  el usuario había borrado o editado alguna). "Restaurar recetas" sigue funcionando igual.
- Báscula recordada: se importa su nombre y su dirección. Si en el móvil nuevo no conecta, el usuario la busca como
  siempre (ver §6).
- Rendimiento: exportar e importar el caso grande (1000 preparaciones con curva, 100 cafés con foto y 20 recetas)
  tarda menos de 15 s cada uno en el emulador (`emulator-5556`), que se acepta como referencia, sin bloquear el hilo
  principal (ver §5).
- Privacidad: el archivo contiene datos no sensibles y no se cifra. La app no lo envía a ningún sitio; solo se
  escribe donde el usuario elige. No hace falta ningún permiso de almacenamiento.

## 5. Criterios de Aceptación (Definition of Done)

- [ ] Ajustes muestra la tarjeta "Copia de seguridad" en los modos Básico y Experto, con "Exportar datos" e
      "Importar datos".
- [ ] Exportar abre el selector con el nombre `pourline-backup-AAAA-MM-DD-HHmm.zip` y escribe un ZIP con
      `manifest.json`, `data.json`, `settings.json` y `photos/`, conforme a §3.1 y §3.2.
- [ ] Test unitario de ida y vuelta: un conjunto de datos con filas en las 11 tablas (incluidas curvas, objetivos,
      pasos de todos los tipos, cafés con dos fotos, molinos con unidades, rangos y anclas, y calibraciones) se
      serializa y se deserializa sin diferencias.
- [ ] Tests unitarios de validación: rechazan un archivo no ZIP, un ZIP sin manifiesto, `app` distinto,
      `formatVersion` mayor, JSON corrupto, entrada inesperada, ruta con `..` y referencia rota, cada uno con su
      `BackupError`; y toleran una foto faltante, contándola en `missingPhotos`.
- [ ] Test instrumentado: con datos en todas las tablas, fotos y ajustes, exportar, borrar todo e importar deja la
      base, las fotos y los ajustes de §3.2 idénticos a los originales (IDs incluidos), y `tour_seen` no cambia.
- [ ] Test instrumentado de atomicidad: si la aplicación falla durante la transacción, la base, las fotos y los
      ajustes quedan como antes de importar.
- [ ] Test instrumentado: tras importar, los `StateFlow` de las clases de `settings` emiten los valores importados
      sin recrearlas.
- [ ] Test de rendimiento instrumentado: el caso grande de §4 exporta e importa en menos de 15 s cada uno en el
      emulador (`emulator-5556`), que se acepta como referencia.
- [ ] "Importar datos" está deshabilitado, con su explicación, durante una preparación en curso (§4).
- [ ] El diálogo de confirmación muestra la fecha, la versión de la app y los recuentos de la copia antes de
      reemplazar.
- [ ] El manifiesto referencia `data_extraction_rules` y `backup_rules`, con el contenido de §3.6 (fotos solo en
      `device-transfer`); verificado con `adb shell bmgr backupnow dev.josueamaya.pourline` y una restauración
      (`adb shell bmgr restore`) o con la inspección de la copia local: la copia en la nube trae la base y los ajustes
      sin las fotos, y la transferencia entre dispositivos (`is_device_transfer=true` en el transporte local) también
      las fotos.
- [ ] Verificación manual en el móvil del usuario: exportar con datos reales, desinstalar la app, reinstalarla e
      importar; los cafés (con fotos), molinos, calibraciones, recetas, historial y ajustes de uso coinciden.
- [ ] Textos en español e inglés; sin avisos nuevos de lint ni de detekt en el módulo `:app`.

## 6. Preguntas abiertas

- **Báscula en otro móvil:** se supone que `last_scale_address` es la dirección de la báscula y no la del móvil, así
  que la reconexión debería funcionar en el dispositivo nuevo. Está sin verificar: se comprobará en la verificación
  manual. Si no funciona, se excluirán `last_scale_address` y `last_scale_name` de la copia.

## 7. Alternativas descartadas

- **Copiar el archivo `history.db` tal cual:** más rápido de implementar, pero ata la copia a la versión del
  esquema (una copia de una versión más nueva no se podría abrir) y un `.db` dañado solo se detectaría después de
  reemplazar los datos.
- **Fusionar en lugar de reemplazar:** exige reglas de duplicados y de reasignación de IDs entre cafés, molinos,
  recetas y preparaciones. Para cambiar de móvil o restaurar, reemplazar es más predecible.
- **Hoja de compartir para exportar:** el selector de archivos ya permite guardar en Drive o en una carpeta local, y
  se usa igual para importar.
- **Desactivar la copia automática de Android:** se pierde la restauración automática al configurar un móvil nuevo.
