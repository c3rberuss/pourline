---
name: receta-catalogo
description: Busca recetas nuevas para un método de preparación (V60, Origami, AeroPress, Chemex…) y las añade al catálogo de recetas recomendadas (catalog/recipes.json) con fuente verificada. Úsala cuando el usuario pida "más recetas de <método>", "añade recetas al catálogo", "busca recetas de <método>" o escriba /receta-catalogo <método> [cantidad].
---

# Recetas nuevas para el catálogo

Argumentos: `<método> [cantidad]`. El método es un `method` del catálogo (`V60`, `KALITA_WAVE`, `CHEMEX`, `ORIGAMI`,
`AEROPRESS`, `FRENCH_PRESS`, `MOKA`, `OTHER`) o un gotero concreto (V60 NEO, Clever, Switch…). Cantidad por defecto: 3-4
por método. Si piden varios métodos, se hace todo junto (una investigación por método, en paralelo).

Reglas del catálogo: `catalog/README.md` (formato, validación, tueste y redacción). Datos y fuentes de las recetas ya
investigadas: `docs/specs/anexo-catalogo-recetas.md`. Ayudante: `scripts/catalog_recipe.py` (ver su docstring).

## 1. Preparar

1. Si estás en `develop` o `main`, crea una rama `feat/catalog-<método>-recipes`. `develop` sirve el catálogo en vivo
   a las apps: nada llega a `develop` sin que el usuario lo pida.
2. Para cada método, ejecuta `python3 scripts/catalog_recipe.py summary <METHOD>`. Da las recetas que ya están, el
   cupo de tueste y lo que ya se investigó y descartó. Todo eso va a la lista de exclusión del encargo.
3. Gotero sin `method` propio (V60 NEO, Clever, Switch…): usa el `method` de los filtros que usa si encaja (la V60 NEO
   es `V60`, con "V60 NEO" en el nombre y las notas) o `OTHER` con `methodOther`. Un `method` nuevo obliga a subir
   `schemaVersion`: no lo hagas sin preguntar. Si nunca se ha decidido cómo modelar ese gotero, pregunta al usuario.

## 2. Investigar

Un agente `general-purpose` por método, todos en un mismo mensaje, con este encargo (rellena los `<…>`):

> Investiga en la web recetas de café para **<método>**. Son para el catálogo de una app de café con báscula. Solo
> investigas: no edites archivos.
>
> Objetivo: <cantidad + 2> candidatas, todas con una fuente que TÚ hayas leído (WebFetch/WebSearch); no basta con el
> resumen del buscador. Buenas fuentes: el fabricante, tostadores con guía de preparación, baristas conocidos y sus
> embajadores; busca también en el idioma del fabricante (p. ej. japonés para Hario u Origami).
>
> Excluir (ya están en el catálogo o ya se descartaron): <lista del summary>.
>
> Tueste: <si el cupo de solo claro está lleno: "la receta debe servir para tueste MEDIO. Vale si la fuente dice
> medio (o claro y medio, o cualquier tueste) o si es una guía genérica de tostador o fabricante sin indicación.
> Rechaza las que la fuente presenta solo para claro y las de campeonato sin indicación (se asumen claras). Si la
> fuente publica una variante para tueste medio, usa esa variante."; si quedan huecos, admite también solo claro
> para recetas de campeonato icónicas y dilo>.
>
> Datos: dosis en gramos (5-60), agua en gramos (ml = g; descarta las que miden por marcas o cucharadas sin gramos),
> temperatura, molienda (texto; molino y ajuste si se da) y el programa: inicio de cada paso y agua acumulada tras
> cada vertido, más el tiempo total. Si no se da la duración de un paso, dilo (se infiere luego). Rechaza recetas de
> más de 20 pasos, con tiempos solo por señales visuales o con datos contradictorios.
>
> Por receta: nombre (es/en), autor, contexto, URL leída (https), cita literal sobre el tueste, dosis, agua, ratio,
> temperatura, molienda, tabla de pasos (POUR / SWIRL girar el gotero o la AeroPress / WAIT / ACTION remover con
> cuchara, tapar, voltear, presionar o retirar el gotero) con inicio, duración ([inferido] si no viene) y agua
> acumulada, tiempo total, confianza (alta/media/baja) y qué es inferido. Al final, las descartadas y por qué (una
> línea cada una). No inventes números.

## 3. Elegir con el usuario

Enseña una tabla de candidatas (método, autor, dosis/agua, °C, tueste, confianza, fuente) con tu recomendación: las de
confianza alta y de autores distintos de los que ya hay. Espera a que el usuario elija antes de escribir nada.

## 4. Escribir las recetas

1. Escríbelas en formato compacto en un archivo del scratchpad (formato en el docstring del script; una receta por
   bloque, separadas con `---`). Reglas de redacción del README:
   - Instrucciones en lenguaje llano, sin tiempos que la app ya muestra; los que no muestra van en las notas con
     unidades ("2 min 30 s"), nunca `m:ss`. Un ratio entero va tras la palabra "ratio".
   - Vertidos: deja que el script genere "Añade X g (total Y g)"; la técnica va con `+ cola_es | cola_en`.
   - Girar el gotero es `SWIRL` y dice qué se gira; remover con cuchara es `ACTION`.
   - Sin jerga: nada de drawdown, rao spin, remolinear ni "bloom" en español (usa la marca `bloom` del paso).
   - Las notas explican lo que la app no muestra: filtro, variantes de tueste, rangos de la fuente y qué tiempo es
     una estimación.
   - Claves nuevas en `snake_case`, del tipo `<autor>_<método>`.
   - Rangos de temperatura: `temp` es el centro del rango y el rango va en las notas.
2. `python3 scripts/catalog_recipe.py build <archivo>` valida sin escribir. Corrige hasta que todas digan OK.
3. `python3 scripts/catalog_recipe.py build <archivo> --write` las añade al final e incrementa `revision`.
4. Ejecuta solo el test del catálogo:
   `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.RecipeCatalogFileTest"` y
   comprueba en `app/build/test-results/testDebugUnitTest/TEST-*RecipeCatalogFileTest.xml` que no hay fallos.

## 5. Documentar en el anexo

Añade al final de `docs/specs/anexo-catalogo-recetas.md` una sección `## Ampliación N (AAAA-MM-DD): <métodos>` (N =
la siguiente) con:

- Un párrafo: cuántas recetas, criterio de tueste, fecha en que se leyeron las fuentes y la regla de duraciones
  inferidas. Si se decidió cómo modelar un gotero, explícalo aquí.
- Tabla: clave, método, autor, dosis, agua, °C, total, `roasts`, confianza y fuente.
- Las citas de tueste de las recetas que llevan `roasts`.
- "Qué es inferido, por receta": una viñeta por receta.
- Un párrafo que empiece por `Descartadas en esta ampliación:` con cada descartada y su motivo. El `summary` lo
  busca para que la próxima vez no se vuelva a investigar.

## 6. Cerrar

Resume al usuario las recetas añadidas (tabla corta) y lo que tiene que saber (decisiones de modelado, recetas con
datos inferidos importantes). No hagas commit, fusión ni push sin que lo pida. Al fusionar a `develop` y subirlo, las
apps reciben el catálogo nuevo: con el pull-to-refresh en Recetas > Recomendadas, o solas en un día como mucho.
