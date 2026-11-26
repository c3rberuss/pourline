# Anexo de SPEC-012: datos de las recetas del catálogo

Anexo de [SPEC-012](spec-catalogo-recetas.md) §3.10. Investigación del 2026-11-25 con los datos de cada
receta: pasos, tiempos, agua, molienda, fuentes consultadas y qué es inferido.

## Decisiones del spec que prevalecen sobre este anexo

Donde este anexo y el spec difieran, manda el spec. En concreto:

- **Recetas descartadas por confianza baja:** la 7 (Matt Winton "Five Pour"), la 17 (Chemex de
  Hoffmann) y la 31 (Switch "del diablo" de Kasuya). Se conservan abajo solo como referencia y están
  marcadas como DESCARTADA.
- **Clave de la 4:6:** `four_six` (la investigación proponía `kasuya_4_6`), para que las copias migradas la reconozcan.
- **V60 clásica (`v60_classic`):** entra sin autor, con los datos actuales de `BuiltInRecipes`; no
  figura en este anexo.
- **Dilución después de prensar** (recetas 22, 23, 24 y 25, y la reserva de Carolina Ibarra): las
  tablas de abajo la modelan como `POUR` finales con el agua acumulada, pero el spec decide un paso
  `ACTION` con los gramos y la temperatura en la instrucción. La curva termina en el agua de infusión
  y las notas dan el ratio final. Hay que convertir esos pasos al crear el JSON.
- **Hielo** (receta 34): no se modela; las notas indican los gramos de hielo y el ratio final.
- **Reservas que entran:** Origami oficial (KOPIKALYAN TOKYO), Carolina Ibarra (World AeroPress
  Championship 2018) y Clever de Five Senses Coffee, con las claves `origami_kopikalyan`,
  `ibarra_wac_2018` y `five_senses_clever`. Aquí solo hay sus datos resumidos: antes de añadirlas hay
  que completar sus pasos desde la fuente; si no se puede con fuente fiable, se quedan fuera.
- **Duda 6 (4:6 y la herramienta):** la receta entra en el catálogo; la herramienta 4:6 no cambia.

## Cómo leer este documento

- **Agua acumulada**: la del modelo `RecipeStage.targetWaterG`, agua total en la cafetera al terminar el paso. En SWIRL, WAIT y ACTION se repite la del POUR anterior (`withDerivedWater`), que en las tablas aparece entre paréntesis.
- **[fuente]** = lo dice una fuente consultada. **[inferido]** = lo deduje yo y explico de qué. Las duraciones de los vertidos casi nunca se publican: cuando faltan, las fijo para que encajen los inicios publicados y lo marco en las notas.
- **Validación**: cada receta se comprobó con un script que reproduce `RecipeValidator` (dosis 5-60 g con 1 decimal, temperatura ≤ 100 °C con 1 decimal, 1-20 pasos, al menos un POUR, agua acumulada creciente y ≤ 1000 g, POUR/SWIRL/ACTION 1-120 s, WAIT 1-600 s, sin solapes, último paso antes de 600 s, instrucción ≤ 200 caracteres). Todas pasan.
- **Temperatura vacía (—)**: la receta usa varias temperaturas. Propongo `waterTempC = null` y poner la temperatura en la instrucción de cada paso, en lugar de elegir una que falsee la receta.
- **Verificado por mí**: además de la investigación inicial, volví a leer yo mismo las fuentes de las recetas marcadas así en sus notas.

## Dudas generales que afectan a varias recetas

1. **Dilución (bypass) tras presionar la AeroPress.** Varias recetas de campeonato hacen un concentrado y luego lo diluyen. Lo modelo como POUR finales con agua acumulada = agua de infusión + agua de dilución. Pero si la báscula está bajo la jarra, tras presionar marcará el concentrado (unos 60-80 g), no el agua vertida, porque el poso retiene agua. La sesión en vivo podría mostrar objetivos que no cuadran con la lectura. Hay que decidir cómo presentarlo; por ejemplo, avisar en notes de que el objetivo es agua añadida y no peso de la bebida.
2. **Varias temperaturas** (Peng, Fukahori GINA, Kasuya Switch, Stanica, Wipvasutt, Némo Pop): la app solo tiene una por receta.
3. **Clever con el agua primero** (Hoffmann Clever) y **café añadido a mitad** (Wipvasutt): la lectura de la báscula sube con el café y no coincide con el agua acumulada.
4. **Pasos de AeroPress fuera de la báscula** (voltear, presionar sobre la jarra): la sesión debe tolerar saltos de peso en pasos ACTION.
5. **Molinos**: solo dos recetas dan un ajuste de un molino del catálogo: Merikanto (`comandante-c40`, 27) y Fukahori Switch (`comandante-c40`, 25). Los demás ajustes de las fuentes son de molinos fuera del catálogo (EK43, ZP6, Trailmaster, FM, EG-1, Lido 3) o son conversiones de terceros, que no anoto como dato.
6. **4:6**: la app ya tiene la herramienta 4:6 (`four_six`). Decidir si la receta del catálogo la duplica o se enlaza a la herramienta.

## Recetas

### 1. Método 4:6 de Tetsu Kasuya

- **Clave**: `four_six`
- **Nombre (es)**: Método 4:6 de Tetsu Kasuya
- **Nombre (en)**: Tetsu Kasuya's 4:6 Method
- **Autor**: Tetsu Kasuya (Philocoffea)
- **Contexto**: Campeón del World Brewers Cup 2016 (Dublín), final. Versión publicada por su empresa Philocoffea ("How to Make Coffee Using the 4:6 Brewing Method", 7-mar-2022).
- **Método**: `V60`
- **Dosis**: 20 g · **Agua total**: 300 g · **Ratio**: 1:15
- **Temperatura**: 93 °C
- **Molienda**: Media-gruesa a gruesa ("medium-coarse to coarse grind"). Sin molino ni ajuste en la fuente. Sin catalogKey.
- **Duración total**: 3:35 (11 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 60 | Primer vertido (parte del 40 %): 60 g en círculos. |
| 2 | WAIT | 10 | 35 | (60) | Deja que drene. |
| 3 | POUR | 45 | 10 | 120 | Segundo vertido: hasta 120 g. El 40 % define dulzor y acidez. |
| 4 | WAIT | 55 | 35 | (120) | Deja que drene. |
| 5 | POUR | 90 | 10 | 180 | Tercer vertido (empieza el 60 %): hasta 180 g. |
| 6 | WAIT | 100 | 35 | (180) | Deja que drene. |
| 7 | POUR | 135 | 10 | 240 | Cuarto vertido: hasta 240 g. |
| 8 | WAIT | 145 | 20 | (240) | Deja que drene. |
| 9 | POUR | 165 | 10 | 300 | Quinto vertido: hasta 300 g. |
| 10 | WAIT | 175 | 35 | (300) | Deja que drene. |
| 11 | ACTION | 210 | 5 | (300) | Retira el gotero a las 3:30 aunque quede agua. |

**Fuentes**:

- https://en.philocoffea.com/en-th/blogs/blog/coffee-brewing-method
- https://hario-europe.com/blogs/hario-community/v60-ambassadors-tetsu-kasuya (variante)
- https://sprudge.com/world-brewers-cup-102370.html (receta de la final 2016)

**Notas**:

- Inicios y cantidades [fuente]: "0:00 Pour 60 g / 0:45 / 1:30 / 2:15 / 2:45 / 3:30 Remove the coffee dripper". Verificado por mí.
- Duración de cada vertido (10 s) [inferida]: la fuente solo da el inicio.
- Temperatura 93 °C para tueste claro; la fuente da 88 °C (medio) y 83 °C (oscuro). Se podría anotar en notes.
- Variante oficial de Hario Europe/UK (entrevista a Kasuya): 50/70/60/60/60 g a 0:00, 0:45, 1:30, 2:10, 2:40, 92 °C, retirar a 3:30. Elijo la de Philocoffea por ser de su propia empresa y la más difundida.
- Solapa con la herramienta 4:6 de la app (builtInKey four_six). Decidir si se incluye como receta del catálogo o se enlaza a la herramienta.

### 2. V60 definitivo de James Hoffmann

- **Clave**: `hoffmann_ultimate_v60`
- **Nombre (es)**: V60 definitivo de James Hoffmann
- **Nombre (en)**: James Hoffmann's Ultimate V60
- **Autor**: James Hoffmann
- **Contexto**: Vídeo "The Ultimate V60 Technique" (YouTube, 5-ago-2019); receta escrita en las webs de Hario USA y Hario UK.
- **Método**: `V60`
- **Dosis**: 30 g · **Agua total**: 500 g · **Ratio**: 1:16,7
- **Temperatura**: 97 °C
- **Molienda**: Media-fina ("medium fine"). Sin molino ni ajuste del autor. Sin catalogKey.
- **Duración total**: 3:30 (9 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 60 | Bloom: 60 g (el doble de la dosis). |
| 2 | SWIRL | 10 | 5 | (60) | Remolinea hasta que no quede café seco. |
| 3 | WAIT | 15 | 30 | (60) | Bloom hasta 0:45. |
| 4 | POUR | 45 | 30 | 300 | Vierte hasta 300 g en 30 s. |
| 5 | POUR | 75 | 30 | 500 | Vierte hasta 500 g en 30 s, algo más despacio. |
| 6 | ACTION | 105 | 5 | (500) | Remueve con una cuchara: 1 vez en sentido horario y 1 en antihorario. |
| 7 | WAIT | 110 | 10 | (500) | Deja que drene un poco. |
| 8 | SWIRL | 120 | 5 | (500) | Remolinea suavemente el V60 para aplanar la cama. |
| 9 | WAIT | 125 | 85 | (500) | Drawdown; debería terminar hacia las 3:30. |

**Fuentes**:

- https://www.hario-usa.com/blogs/recipes-and-more-from-friends/james-hoffmann-uitimate-v60-technique
- https://www.hario.co.uk/blogs/hario-ambassadors/hario-v60-recipe-interview-with-hario-ambassador-james-hoffmann
- https://www.youtube.com/watch?v=AI4ynXzkSQo

**Notas**:

- Cantidades, inicios (0:45, 1:15, 1:45) y fin (3:30) [fuente, Hario USA].
- Temperatura: Hario UK dice 97 °C; Hario USA dice "the hotter, the better". Elijo 97 °C por ser un número explícito.
- Duraciones del bloom, el remolino, el remover y la espera de drenado [inferidas]; la fuente no las cronometra.
- Fin del drawdown: un resumen de terceros dice 3:00, pero escalado a 18 g. Para 30 g me quedo con 3:30 (Hario USA).

### 3. V60 de una taza de James Hoffmann

- **Clave**: `hoffmann_one_cup_v60`
- **Nombre (es)**: V60 de una taza de James Hoffmann
- **Nombre (en)**: James Hoffmann's Better 1-Cup V60
- **Autor**: James Hoffmann
- **Contexto**: Vídeo "A Better 1 Cup V60 Technique" (YouTube, 22-nov-2022); receta en la descripción del vídeo y en Hario USA.
- **Método**: `V60`
- **Dosis**: 15 g · **Agua total**: 250 g · **Ratio**: 1:16,7
- **Temperatura**: 100 °C
- **Molienda**: Media-fina ("medium-fine"). Desarrollada en V60 01 de plástico. Sin catalogKey.
- **Duración total**: 3:00 (12 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 50 | Bloom: 50 g. |
| 2 | SWIRL | 10 | 5 | (50) | Remolinea suavemente. |
| 3 | WAIT | 15 | 30 | (50) | Bloom hasta 0:45. |
| 4 | POUR | 45 | 15 | 100 | Vierte hasta 100 g (40 %). |
| 5 | WAIT | 60 | 10 | (100) | Pausa. |
| 6 | POUR | 70 | 10 | 150 | Vierte hasta 150 g (60 %). |
| 7 | WAIT | 80 | 10 | (150) | Pausa. |
| 8 | POUR | 90 | 10 | 200 | Vierte hasta 200 g (80 %). |
| 9 | WAIT | 100 | 10 | (200) | Pausa. |
| 10 | POUR | 110 | 10 | 250 | Vierte hasta 250 g (100 %). |
| 11 | SWIRL | 120 | 5 | (250) | Remolinea suavemente. |
| 12 | WAIT | 125 | 55 | (250) | Drawdown; debería terminar hacia las 3:00. |

**Fuentes**:

- https://www.hario-usa.com/blogs/recipes-and-more-from-friends/james-hoffmann-1-cup-v60-technique
- https://www.youtube.com/watch?v=1oB1oDrDkHM

**Notas**:

- Todos los tramos de tiempo vienen en la fuente (0:00-0:10 bloom, 0:10-0:15 swirl, 0:45-1:00 vertido... 2:00-2:05 swirl). Verificado por mí.
- Temperatura: "freshly boiled (for lighter roasts)" → 100 °C [inferida], justo en el límite de la app.
- El vídeo "Part 2" da los clics de Comandante, pero no pude leer la cifra; una web de terceros dice ~20 clics. No lo anoto como ajuste.

### 4. V60 de Scott Rao

- **Clave**: `rao_v60`
- **Nombre (es)**: V60 de Scott Rao
- **Nombre (en)**: Scott Rao's V60
- **Autor**: Scott Rao
- **Contexto**: Entrevista de embajador de Hario UK (10-may-2022).
- **Método**: `V60`
- **Dosis**: 20 g · **Agua total**: 330 g · **Ratio**: 1:16,5
- **Temperatura**: 97 °C
- **Molienda**: No especificada; ajústala para un tiempo total de 4:00-4:30. V60 de plástico. Sin catalogKey.
- **Duración total**: 4:15 (9 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 60 | Haz un hueco en la cama y vierte 60 g. |
| 2 | SWIRL | 10 | 3 | (60) | Gira el gotero con energía (Rao spin). |
| 3 | WAIT | 13 | 40 | (60) | Espera 40 s. |
| 4 | POUR | 53 | 30 | 200 | Vierte hasta 200 g con chorro bajo y constante. |
| 5 | SWIRL | 83 | 1 | (200) | Giro muy suave (menos de 1 s). |
| 6 | WAIT | 84 | 40 | (200) | Espera a que haya drenado un 70 %. |
| 7 | POUR | 124 | 25 | 330 | Vierte hasta 330 g. |
| 8 | SWIRL | 149 | 1 | (330) | Giro muy suave. |
| 9 | WAIT | 150 | 105 | (330) | Drawdown; total 4:00-4:30. |

**Fuentes**:

- https://www.hario.co.uk/blogs/hario-ambassadors/hario-v60-recipe-interview-with-hario-ambassador-scott-rao

**Notas**:

- Dosis, agua, 97 °C, bloom de 60 g con spin, espera de 40 s, vertido a 200 g, spin <1 s, 70 % drenado, vertido a 330 g y total 4:00-4:30 [fuente]. Verificado por mí.
- La página dice "97°C / 97°F"; lo segundo es una errata.
- Duraciones de los vertidos y el momento del 70 % drenado [inferidos]: la receta va por señales visuales, no por reloj.
- El spin de menos de 1 s se redondea a 1 s (mínimo de la app).

### 5. V60 fácil y eficaz de Lance Hedrick

- **Clave**: `hedrick_v60_easy`
- **Nombre (es)**: V60 fácil y eficaz de Lance Hedrick
- **Nombre (en)**: Lance Hedrick's Easy and Effective V60
- **Autor**: Lance Hedrick
- **Contexto**: Vídeo "HARIO V60 BREWGUIDE: Easy and Effective V60 Recipe!" (YouTube, 7-may-2021); receta en la descripción.
- **Método**: `V60`
- **Dosis**: 20 g · **Agua total**: 340 g · **Ratio**: 1:17
- **Temperatura**: 100 °C
- **Molienda**: 720 µm ("20g ground at 720 microns"). Sin molino concreto. Sin catalogKey.
- **Duración total**: 3:00 (12 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 8 | 50 | Vierte 50 g (algo más del doble de la dosis). |
| 2 | SWIRL | 8 | 4 | (50) | Gira el gotero. |
| 3 | WAIT | 12 | 18 | (50) | Espera hasta 0:30. |
| 4 | POUR | 30 | 8 | 100 | Repite: vierte hasta 100 g. |
| 5 | SWIRL | 38 | 4 | (100) | Gira el gotero. |
| 6 | WAIT | 42 | 18 | (100) | Espera hasta 1:00. |
| 7 | POUR | 60 | 15 | 220 | Vierte con energía justo detrás del centro hasta 220 g. |
| 8 | SWIRL | 75 | 4 | (220) | Gira el gotero. |
| 9 | WAIT | 79 | 11 | (220) | Espera hasta 1:30. |
| 10 | POUR | 90 | 15 | 340 | Vierte con energía hasta 340 g. |
| 11 | SWIRL | 105 | 4 | (340) | Gira el gotero. |
| 12 | WAIT | 109 | 71 | (340) | Drawdown; total 2:30-3:30. |

**Fuentes**:

- https://www.youtube.com/watch?v=Ji4lZTtRAoo

**Notas**:

- Descripción verificada por mí: "20g ground at 720 microns / 340g boiling water / Pour every 30 sec / 50g, spin / 50g, spin / 120g, spin / 120g, spin", "1:17 ratio", "TBT- 2:30-3:30".
- Temperatura: "boiling water" → 100 °C [inferida].
- Duraciones de vertidos y spins [inferidas]; los inicios cada 30 s son de la fuente. Fin del drawdown fijado en 3:00 (punto medio del rango).

### 6. V60 "One and Done" de Lance Hedrick

- **Clave**: `hedrick_one_and_done`
- **Nombre (es)**: V60 "One and Done" de Lance Hedrick
- **Nombre (en)**: Lance Hedrick's One and Done V60
- **Autor**: Lance Hedrick
- **Contexto**: Vídeo "The Last V60 Recipe You'll Ever Need" (YouTube, 26-abr-2026).
- **Método**: `V60`
- **Dosis**: 15 g · **Agua total**: 225 g · **Ratio**: 1:15
- **Temperatura**: 93 °C
- **Molienda**: Ajusta para que el drawdown total quede en 2:00-2:30. Sin ajuste del autor. Sin catalogKey.
- **Duración total**: 2:15 (6 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 6 | 45 | Espiral hasta 45 g. No agites después. |
| 2 | WAIT | 6 | 24 | (45) | Espera hasta 0:30. |
| 3 | POUR | 30 | 6 | 90 | Espiral hasta 90 g. |
| 4 | WAIT | 36 | 24 | (90) | Espera hasta 1:00. |
| 5 | POUR | 60 | 13 | 225 | Vierte con energía el resto, hasta 225 g. |
| 6 | WAIT | 73 | 62 | (225) | Drawdown; total 2:00-2:30. |

**Fuentes**:

- https://www.youtube.com/watch?v=PNFVCmxBjQQ (la descripción no trae la receta)
- https://gota.cafe/en/recipes/v60-02/hedrick-one-and-done (transcripción)
- https://pourist.app/en/recipes/lance-hedrick-one-and-done (transcripción)

**Notas**:

- Solo hay dos transcripciones de terceros, que coinciden en 15 g / 225 g / 1:15 y en la estructura 45-90-225 a 0:00, 0:30 y 1:00.
- Temperatura: Gota dice 93 °C y Pourist 90-96 °C; elijo 93 °C.
- Gotero: Pourist dice V60 01; Gota lo clasifica en V60 02. Sin resolver.
- El "~31 clics de C40" de Pourist es una conversión de terceros; no lo anoto.

### 7. V60 de cinco vertidos de Matt Winton (DESCARTADA)

- **Clave**: `winton_five_pour`
- **Nombre (es)**: V60 de cinco vertidos de Matt Winton
- **Nombre (en)**: Matt Winton's Five-Pour V60
- **Autor**: Matt Winton
- **Contexto**: Campeón del World Brewers Cup 2021 (Milán). Vídeo de Hario "V60 Five-Pour Recipe - Matt Winton" (YouTube, 23-sep-2022). No es la receta exacta de competición (no se publicó).
- **Método**: `V60`
- **Dosis**: 20 g · **Agua total**: 300 g · **Ratio**: 1:15
- **Temperatura**: 93 °C
- **Molienda**: Gruesa; unos 12 clics en un molino manual Hario según una transcripción de terceros. V60 metálico 02. Sin catalogKey.
- **Duración total**: 3:40 (12 pasos)
- **Confianza**: **baja**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 60 | Vierte 60 g con energía en el centro y luego por los lados. |
| 2 | WAIT | 10 | 20 | (60) | Espera hasta 0:30. |
| 3 | POUR | 30 | 10 | 120 | Vierte hasta 120 g. |
| 4 | WAIT | 40 | 30 | (120) | Espera hasta 1:10. |
| 5 | POUR | 70 | 10 | 180 | Vierte hasta 180 g. |
| 6 | WAIT | 80 | 20 | (180) | Espera hasta 1:40. |
| 7 | POUR | 100 | 10 | 240 | Vierte hasta 240 g. |
| 8 | WAIT | 110 | 25 | (240) | Espera hasta 2:15. |
| 9 | POUR | 135 | 10 | 300 | Vierte hasta 300 g. |
| 10 | WAIT | 145 | 65 | (300) | Drawdown. |
| 11 | ACTION | 210 | 5 | (300) | Retira el gotero a las 3:30. |
| 12 | SWIRL | 215 | 5 | (300) | Remolinea la jarra. |

**Fuentes**:

- https://www.youtube.com/watch?v=YIC-2nFQ7vM (vídeo primario, sin receta escrita)
- https://www.homegrounds.co/coffee-brew-timer/ (transcripción)
- https://honestcoffeeguide.com/brew-recipes/matt-winton-v60-five-pour/ (transcripción)
- https://en.wikipedia.org/wiki/World_Brewers_Cup (datos de la final 2021)

**Notas**:

- 20 g / 300 g y 5 vertidos de 60 g coinciden en todas las fuentes, pero ninguna escrita es del autor.
- Según Homegrounds, solo 0:30 y 3:30 los dice Winton; 1:10, 1:40 y 2:15 se "leyeron" de su demostración [inferidos por terceros].
- Temperatura: Honest Coffee Guide dice 93 °C en el bloom y 88 °C después (sin verificar); Wikipedia da 93/88 °C para la competición. Uso 93 °C como valor único; alternativa: campo vacío y temperaturas en las instrucciones.
- Recomendación: validar con el vídeo antes de publicarla, o no incluirla.

### 8. V60 de Jonathan Gagné

- **Clave**: `gagne_v60`
- **Nombre (es)**: V60 de Jonathan Gagné
- **Nombre (en)**: Jonathan Gagné's V60
- **Autor**: Jonathan Gagné (Coffee ad Astra)
- **Contexto**: Entrada "A V60 Pour Over Video" (coffeeadastra.com, 9-jun-2019). Autor de "The Physics of Filter Coffee".
- **Método**: `V60`
- **Dosis**: 22 g · **Agua total**: 374 g · **Ratio**: 1:17
- **Temperatura**: 100 °C
- **Molienda**: Fina para filtro; en la fuente, Weber EG-1 con muelas SSP a 6,8 (700 rpm). Molino no presente en el catálogo. Sin catalogKey.
- **Duración total**: 4:03 (9 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 77 | Bloom: 77 g de agua hirviendo. |
| 2 | SWIRL | 10 | 5 | (77) | Rao spin a fondo. |
| 3 | WAIT | 15 | 30 | (77) | Bloom. |
| 4 | POUR | 45 | 23 | 200 | Vierte hasta 200 g; termina hacia 1:08. |
| 5 | SWIRL | 68 | 5 | (200) | Rao spin suave. |
| 6 | WAIT | 73 | 32 | (200) | Espera hasta 1:45. |
| 7 | POUR | 105 | 35 | 374 | Vierte hasta 374 g; termina hacia 2:20. |
| 8 | SWIRL | 140 | 5 | (374) | Rao spin suave. |
| 9 | WAIT | 145 | 98 | (374) | Drawdown; total ~4:03. |

**Fuentes**:

- https://coffeeadastra.com/2019/06/09/a-v60-pour-over-video/
- https://coffeeadastra.com/2018/11/30/brewing-better-coffee/ (flujo de trabajo escrito, 22 g / 352 g)

**Notas**:

- Verificado por mí: 22 g, 374 g, bloom de 77 g, fin del primer vertido 1:08, inicio del segundo 1:45, fin 2:20, total 4:03, "boiling water", spin tras cada vertido.
- Duración del bloom [inferida]: la entrada no la da; un comentario suyo sugiere que suele ser de 0:45.
- Spin tras el segundo vertido [inferido] a partir de su flujo de 2018 ("do another Rao spin").
- Versión alternativa (2018, la que enlaza su menú "V60 Recipe"): 22 g / 352 g (1:16), bloom de 66 g, ~200 g, resto por señales visuales; tiempos casi todos por señales, peor para la app.

### 9. V60 de Chad Wang (World Brewers Cup 2017)

- **Clave**: `wang_wbrc_2017`
- **Nombre (es)**: V60 de Chad Wang (World Brewers Cup 2017)
- **Nombre (en)**: Chad Wang's 2017 World Brewers Cup V60
- **Autor**: Chad Wang (Taiwán, Jascaffe)
- **Contexto**: Campeón del World Brewers Cup 2017 (Budapest), final.
- **Método**: `V60`
- **Dosis**: 15 g · **Agua total**: 250 g · **Ratio**: 1:16,7
- **Temperatura**: 92 °C
- **Molienda**: Muy fina ("very finely"); tamizar a ~400 µm si se puede (sugerencia de la recreación). V60 de cerámica. Sin catalogKey.
- **Duración total**: 2:00 (4 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 30 | Vierte 30 g en espiral. |
| 2 | WAIT | 10 | 20 | (30) | Preinfusión hasta 0:30. |
| 3 | POUR | 30 | 45 | 250 | Vierte en el centro, sin círculos, hasta 250 g en 40-45 s. |
| 4 | WAIT | 75 | 45 | (250) | Drawdown; termina hacia 2:00. |

**Fuentes**:

- https://sprudge.com/6-recipes-from-the-2017-world-brewers-cup-123165.html
- https://www.baristamagazine.com/world-brewers-2017-recipe/

**Notas**:

- Sprudge: "15g coffee to 250mL of 92°C water", bloom de 30 s, vertido al centro sin círculos, 2:00 total, ~220 ml de bebida.
- Barista Magazine (recreación): 93 °C, bloom de 30 g, "CENTER POUR up to 250g water in 40-45s", fin 2:00-2:15. Elijo 92 °C (Sprudge cubrió la final).
- Los 30 g del bloom solo aparecen en Barista Magazine.
- Reserva: su receta de 2016 (3.er puesto, 15 g / 250 g, cuatro pulsos), con versiones contradictorias entre Sprudge y Barista Magazine.

### 10. Kalita Wave de Stumptown

- **Clave**: `stumptown_kalita`
- **Nombre (es)**: Kalita Wave de Stumptown
- **Nombre (en)**: Stumptown Kalita Wave
- **Autor**: Stumptown Coffee Roasters
- **Contexto**: Guía de preparación oficial de Stumptown.
- **Método**: `KALITA_WAVE`
- **Dosis**: 21 g · **Agua total**: 345 g · **Ratio**: 1:16,4
- **Temperatura**: 96 °C
- **Molienda**: Media-fina, como sal marina ("medium-fine around the texture of sea salt"). Sin catalogKey.
- **Duración total**: 2:50 (6 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 60 | Bloom: 60 g en los primeros 10 s. |
| 2 | ACTION | 10 | 5 | (60) | Remueve suave para que no quede café seco. |
| 3 | WAIT | 15 | 30 | (60) | Espera hasta 0:45. |
| 4 | POUR | 45 | 15 | 200 | Espiral lenta hasta 200 g; termina a 1:00. |
| 5 | POUR | 60 | 60 | 345 | Pulsos de 25-50 g hasta 345 g a las 2:00. |
| 6 | WAIT | 120 | 50 | (345) | Drawdown; termina entre 2:45 y 3:00. |

**Fuentes**:

- https://www.stumptowncoffee.com/brew-guides/kalita-wave

**Notas**:

- Verificado por mí: 21 g, "about 205°F", 60 g en 10 s con remover suave, 0:45 a 200 g por 1:00, pulsos hasta 345 g a las 2:00, fin 2:45-3:00.
- 205 °F = 96,1 °C; uso 96 °C porque la fuente dice "about".
- La fuente no fija cuántos pulsos; lo dejo como un solo POUR de 60 s con la instrucción de pulsos. Alternativa: 3-4 POUR cortos [inferidos].
- Duración del remover (5 s) [inferida].

### 11. Kalita Wave de Mikaela Wallgren (World Brewers Cup 2016)

- **Clave**: `wallgren_wbrc_2016`
- **Nombre (es)**: Kalita Wave de Mikaela Wallgren (World Brewers Cup 2016)
- **Nombre (en)**: Mikaela Wallgren's 2016 World Brewers Cup Kalita Wave
- **Autor**: Mikaela Wallgren (Finlandia, Coffee Collective)
- **Contexto**: Finalista del World Brewers Cup 2016 (Dublín); puesto final no consta.
- **Método**: `KALITA_WAVE`
- **Dosis**: 15 g · **Agua total**: 250 g · **Ratio**: 1:16,7
- **Temperatura**: 96 °C
- **Molienda**: Mahlkönig EK43 a 6,3, tamizando los finos (se muelen 17-18 g para quedarse con 15 g). Kalita Wave 155. Sin catalogKey.
- **Duración total**: 2:35 (4 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 30 | Vierte 30 g en espiral. |
| 2 | WAIT | 10 | 20 | (30) | Bloom hasta 0:30. |
| 3 | POUR | 30 | 75 | 250 | Espiral lenta y continua hasta 250 g a la 1:45. |
| 4 | WAIT | 105 | 50 | (250) | Drawdown; retira el gotero a las 2:35. |

**Fuentes**:

- https://baristamagazine.com/brewing-experiments-mikaela-wallgrens-2016-world-brewers-cup-championship-recipe
- https://sprudge.com/world-brewers-cup-102370.html

**Notas**:

- Barista Magazine y Sprudge coinciden: 15 g, 96 °C, bloom 0:30, fin del vertido 1:45, total 2:35.
- El agua total (250 g) solo la da Barista Magazine.
- El tamizado no se puede representar; va en notas.

### 12. Kalita Wave de Todd Goldsworthy (World Brewers Cup 2016)

- **Clave**: `goldsworthy_wbrc_2016`
- **Nombre (es)**: Kalita Wave de Todd Goldsworthy (World Brewers Cup 2016)
- **Nombre (en)**: Todd Goldsworthy's 2016 World Brewers Cup Kalita Wave
- **Autor**: Todd Goldsworthy (EE. UU.)
- **Contexto**: 5.º puesto en el World Brewers Cup 2016 (Dublín), final.
- **Método**: `KALITA_WAVE`
- **Dosis**: 27 g · **Agua total**: 350 g · **Ratio**: 1:13
- **Temperatura**: 95 °C
- **Molienda**: Media-gruesa. Kalita Wave 185 de cristal. (El "~20 en Baratza Encore" es del autor de la recreación, no de Todd.) Sin catalogKey.
- **Duración total**: 3:00 (8 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 5 | 40 | Bloom: 40 g en espiral. |
| 2 | WAIT | 5 | 40 | (40) | Bloom hasta 0:45. |
| 3 | POUR | 45 | 12 | 150 | Espiral hasta 150 g. |
| 4 | WAIT | 57 | 18 | (150) | Espera hasta 1:15. |
| 5 | POUR | 75 | 12 | 250 | Espiral hasta 250 g. |
| 6 | WAIT | 87 | 33 | (250) | Espera hasta 2:00. |
| 7 | POUR | 120 | 12 | 350 | Espiral hasta 350 g. |
| 8 | WAIT | 132 | 48 | (350) | Drawdown; termina hacia 3:00. |

**Fuentes**:

- https://www.baristamagazine.com/brewing-experiments-todd-goldsworthys-2016-world-brewers-cup-championship-recipe/
- https://sprudge.com/world-brewers-cup-102370.html

**Notas**:

- Dosis, agua y 95 °C coinciden en ambas fuentes.
- Tiempos: Barista Magazine (recreación) da el programa completo que uso; Sprudge da 150 ml a 1:15, 250 ml a 2:00 y total 2:45, es decir, objetivos alcanzados antes. Contradicción sin resolver; elijo Barista Magazine por ser la única completa.

### 13. Origami de Du Jianing (World Brewers Cup 2019)

- **Clave**: `du_jianing_wbrc_2019`
- **Nombre (es)**: Origami de Du Jianing (World Brewers Cup 2019)
- **Nombre (en)**: Du Jianing's 2019 World Brewers Cup Origami
- **Autor**: Du Jianing (China)
- **Contexto**: Campeona del World Brewers Cup 2019 (Boston). Ronda no indicada (probablemente la final).
- **Método**: `ORIGAMI`
- **Dosis**: 16 g · **Agua total**: 240 g · **Ratio**: 1:15
- **Temperatura**: 94 °C
- **Molienda**: Media, molida dos veces (primero más gruesa y luego al tamaño final). Filtro de fondo plano (wave). Sin catalogKey.
- **Duración total**: 1:45 (6 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 60 | Vierte 60 g a ~6 g/s. |
| 2 | WAIT | 10 | 8 | (60) | Espera 8 s. |
| 3 | POUR | 18 | 20 | 140 | Vierte 80 g a ~4 g/s, hasta 140 g. |
| 4 | WAIT | 38 | 18 | (140) | Espera hasta 0:56. |
| 5 | POUR | 56 | 14 | 240 | Vierte el resto hasta 240 g a ~5 g/s; termina hacia 1:10. |
| 6 | WAIT | 70 | 35 | (240) | Drawdown hasta 1:40-1:50. |

**Fuentes**:

- https://theroasterspack.com/blogs/news/origami-dripper-our-extensive-guide-with-recipes
- https://sprudge.com/du-jianing-of-china-is-the-2019-world-brewers-cup-champion-142739.html
- https://www.youtube.com/watch?v=HNDnngFhZMU (vídeo de la competición)

**Notas**:

- Programa de The Roasters Pack (blog de terceros que enlaza el vídeo de la competición). Sprudge confirma el Origami, el café molido dos veces y los caudales "six grams per second to four and then up to five".
- Incoherencia de la fuente: 100 g a 5 g/s son 20 s (terminaría a 1:16), pero dice "approximately 1m10s". Uso 14 s para respetar el 1:10.
- Fin del drawdown fijado en 1:45 (punto medio de 1:40-1:50).
- El "26 clics de Comandante" es una sugerencia del blog, no de la competidora; no lo anoto.

### 14. Origami de Carlos Medina (World Brewers Cup 2023)

- **Clave**: `medina_wbrc_2023`
- **Nombre (es)**: Origami de Carlos Medina (World Brewers Cup 2023)
- **Nombre (en)**: Carlos Medina's 2023 World Brewers Cup Origami
- **Autor**: Carlos Medina (Chile)
- **Contexto**: Campeón del World Brewers Cup 2023 (Atenas). Ronda no indicada.
- **Método**: `ORIGAMI`
- **Dosis**: 15,5 g · **Agua total**: 250 g · **Ratio**: 1:16,1
- **Temperatura**: 91 °C
- **Molienda**: No indicada en la fuente leída. Origami Air S con filtro cónico. Sin catalogKey.
- **Duración total**: 2:40 (10 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 50 | Vierte 50 g en espiral hacia fuera y hacia dentro. |
| 2 | WAIT | 10 | 20 | (50) | Espera hasta 0:30. |
| 3 | POUR | 30 | 10 | 100 | Vierte hasta 100 g. |
| 4 | WAIT | 40 | 20 | (100) | Espera hasta 1:00. |
| 5 | POUR | 60 | 10 | 150 | Vierte hasta 150 g. |
| 6 | WAIT | 70 | 20 | (150) | Espera hasta 1:30. |
| 7 | POUR | 90 | 10 | 200 | Vierte hasta 200 g. |
| 8 | WAIT | 100 | 20 | (200) | Espera hasta 2:00. |
| 9 | POUR | 120 | 10 | 250 | Vierte hasta 250 g. |
| 10 | WAIT | 130 | 30 | (250) | Drawdown; total 2:40. |

**Fuentes**:

- https://www.slowpoursupply.co/blogs/brew-recipes/recipe-recap-carlos-medina-s-representing-chile-world-brewers-cup-champion-recipe
- https://en.wikipedia.org/wiki/World_Brewers_Cup

**Notas**:

- Slow Pour Supply: 15,5 g, 250 g, 1:16, 91 °C, 5 vertidos de 50 g, "silent time: 30 seconds between each pour", total 2'40".
- Interpreto los vertidos como inicios cada 30 s (también lo lee así Wikipedia); con 30 s de silencio tras cada vertido no saldría 2:40.
- Duración de cada vertido (10 s) [inferida].
- Wikipedia cita un 1Zpresso ZP6 Special a 5,2 (no está en el catálogo y no lo verifiqué).

### 15. Origami de Stumptown

- **Clave**: `stumptown_origami`
- **Nombre (es)**: Origami de Stumptown
- **Nombre (en)**: Stumptown Origami
- **Autor**: Stumptown Coffee Roasters
- **Contexto**: Guía de preparación oficial de Stumptown.
- **Método**: `ORIGAMI`
- **Dosis**: 21 g · **Agua total**: 340 g · **Ratio**: 1:16,2
- **Temperatura**: 96 °C
- **Molienda**: Media-fina, como sal marina. Sin catalogKey.
- **Duración total**: 3:05 (9 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 50 | Bloom: 50 g. |
| 2 | ACTION | 10 | 5 | (50) | Remueve para que no quede café seco. |
| 3 | WAIT | 15 | 30 | (50) | Espera hasta 0:45. |
| 4 | POUR | 45 | 15 | 200 | Espiral hasta 200 g; termina a 1:00. |
| 5 | WAIT | 60 | 30 | (200) | Espera hasta 1:30. |
| 6 | POUR | 90 | 10 | 280 | Vierte hasta 280 g. |
| 7 | WAIT | 100 | 20 | (280) | Espera hasta 2:00. |
| 8 | POUR | 120 | 10 | 340 | Vierte hasta 340 g mojando el café pegado al filtro. |
| 9 | WAIT | 130 | 55 | (340) | Drawdown; termina entre 3:00 y 3:15. |

**Fuentes**:

- https://www.stumptowncoffee.com/pages/brew-guide-origami

**Notas**:

- Verificado por mí: 21 g, ~205 °F, 50 g + remover, 0:45 a 200 g, 1:30 a 280 g, 2:00 a 340 g, fin 3:00-3:15.
- Duraciones de los vertidos de 1:30 y 2:00 y del remover [inferidas].

### 16. Chemex de Stumptown

- **Clave**: `stumptown_chemex`
- **Nombre (es)**: Chemex de Stumptown
- **Nombre (en)**: Stumptown Chemex
- **Autor**: Stumptown Coffee Roasters
- **Contexto**: Guía de preparación oficial de Stumptown (Chemex de 8 tazas).
- **Método**: `CHEMEX`
- **Dosis**: 42 g · **Agua total**: 700 g · **Ratio**: 1:16,7
- **Temperatura**: 96 °C
- **Molienda**: Media-gruesa, como sal kosher. Sin catalogKey.
- **Duración total**: 4:15 (9 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 15 | 150 | Bloom: 150 g. |
| 2 | ACTION | 15 | 5 | (150) | Remueve para que no quede café seco. |
| 3 | WAIT | 20 | 25 | (150) | Espera hasta 0:45. |
| 4 | POUR | 45 | 30 | 450 | Vierte en zigzag y luego en espiral hasta 450 g. |
| 5 | WAIT | 75 | 30 | (450) | Espera hasta 1:45. |
| 6 | POUR | 105 | 30 | 700 | Vierte hasta 700 g, casi hasta el borde. |
| 7 | WAIT | 135 | 105 | (700) | Drawdown hasta ~4:00. |
| 8 | ACTION | 240 | 10 | (700) | Levanta y retira el filtro. |
| 9 | SWIRL | 250 | 5 | (700) | Remolinea la Chemex y sirve. |

**Fuentes**:

- https://www.stumptowncoffee.com/pages/brew-guide-chemex

**Notas**:

- Verificado por mí: 42 g, ~205 °F, 150 g + remover, 0:45 a 450 g, 1:45 a 700 g, 4:00 retirar filtro, remolinear.
- Duraciones de los vertidos [inferidas].

### 17. Chemex de James Hoffmann (DESCARTADA)

- **Clave**: `hoffmann_chemex`
- **Nombre (es)**: Chemex de James Hoffmann
- **Nombre (en)**: James Hoffmann's Chemex
- **Autor**: James Hoffmann
- **Contexto**: Vídeo "The Chemex" (YouTube, 23-ene-2020), mitad reseña y mitad técnica.
- **Método**: `CHEMEX`
- **Dosis**: 30 g · **Agua total**: 500 g · **Ratio**: 1:16,7
- **Temperatura**: 100 °C
- **Molienda**: Media. Sin catalogKey.
- **Duración total**: 4:10 (8 pasos)
- **Confianza**: **baja**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 80 | Bloom: 80 g (60-90 g). |
| 2 | WAIT | 10 | 35 | (80) | Bloom hasta 0:45. |
| 3 | POUR | 45 | 30 | 300 | Vierte en círculos hasta 300 g en 30 s. |
| 4 | POUR | 75 | 30 | 500 | Vierte en círculos hasta 500 g en 30 s. |
| 5 | ACTION | 105 | 5 | (500) | Remueve 1 vez en sentido horario y 1 en antihorario. |
| 6 | WAIT | 110 | 10 | (500) | Espera 10 s. |
| 7 | SWIRL | 120 | 3 | (500) | Agita suavemente la Chemex 3 s. |
| 8 | WAIT | 123 | 127 | (500) | Drawdown; termina hacia 4:10. |

**Fuentes**:

- https://www.youtube.com/watch?v=ikt-X5x7yoc (la descripción no trae la receta)
- https://honestcoffeeguide.com/brew-recipes/james-hoffmann-chemex-recipe/ (transcripción)

**Notas**:

- Única fuente escrita: transcripción de Honest Coffee Guide ("adapted from his V60 recipe"). No pude leer el vídeo.
- Recomendación: verificar con el vídeo antes de publicarla, o sustituirla por la de Stumptown.

### 18. Prensa francesa de James Hoffmann

- **Clave**: `hoffmann_french_press`
- **Nombre (es)**: Prensa francesa de James Hoffmann
- **Nombre (en)**: James Hoffmann's Ultimate French Press
- **Autor**: James Hoffmann
- **Contexto**: Vídeo "The Ultimate French Press Technique" (YouTube, 2-oct-2016); consejos repetidos en Imbibe (may/jun 2025).
- **Método**: `FRENCH_PRESS`
- **Dosis**: 30 g · **Agua total**: 500 g · **Ratio**: 1:16,7
- **Temperatura**: 100 °C
- **Molienda**: Media-gruesa. Sin catalogKey.
- **Duración total**: 9:40 (6 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 500 | Vierte los 500 g de agua recién hervida. |
| 2 | WAIT | 10 | 230 | (500) | Espera hasta las 4:00 sin tocar. |
| 3 | ACTION | 240 | 10 | (500) | Remueve la costra para que se hunda el café. |
| 4 | ACTION | 250 | 20 | (500) | Retira la espuma y los restos con dos cucharas. |
| 5 | WAIT | 270 | 300 | (500) | Espera al menos 5 min más (hasta 8 si quieres más claridad). |
| 6 | ACTION | 570 | 10 | (500) | Baja el émbolo solo hasta la superficie y sirve despacio. |

**Fuentes**:

- https://www.youtube.com/watch?v=st571DYYTR8 (la descripción no trae la receta)
- https://imbibemagazine.com/?p=91280
- https://honestcoffeeguide.com/brew-recipes/james-hoffmann-french-press/ (transcripción)

**Notas**:

- Imbibe (fuente fiable): "60 to 70 grams of coffee per liter", 4 min, remover y retirar la espuma, otros 5 min, "do not fully plunge".
- 30 g / 500 g y los tiempos exactos (10 s, 230 s) vienen de la transcripción de Honest Coffee Guide; coherentes con 60 g/L.
- Duraciones de remover y retirar espuma [inferidas].
- Límite de la app: con la espera mínima de 5 min el último paso empieza a 570 s; con 8 min empezaría hacia 750 s y no cabría. Por eso se fija la mínima.

### 19. Prensa francesa de Stumptown

- **Clave**: `stumptown_french_press`
- **Nombre (es)**: Prensa francesa de Stumptown
- **Nombre (en)**: Stumptown French Press
- **Autor**: Stumptown Coffee Roasters
- **Contexto**: Guía de preparación oficial de Stumptown (prensa de 8 tazas).
- **Método**: `FRENCH_PRESS`
- **Dosis**: 56 g · **Agua total**: 850 g · **Ratio**: 1:15,2
- **Temperatura**: 96 °C
- **Molienda**: Gruesa, como pan rallado. Sin catalogKey.
- **Duración total**: 4:25 (8 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 20 | 450 | Llena hasta la mitad con 450 g. |
| 2 | ACTION | 20 | 3 | (450) | Pon la tapa sin presionar. |
| 3 | WAIT | 23 | 37 | (450) | Espera hasta 1:00. |
| 4 | ACTION | 60 | 10 | (450) | Quita la tapa y remueve. |
| 5 | POUR | 70 | 20 | 850 | Añade agua hasta 850 g y vuelve a poner la tapa. |
| 6 | WAIT | 90 | 150 | (850) | Espera hasta 4:00. |
| 7 | ACTION | 240 | 15 | (850) | Presiona el émbolo. |
| 8 | ACTION | 255 | 10 | (850) | Pasa a una jarra el café que no se sirva enseguida. |

**Fuentes**:

- https://www.stumptowncoffee.com/pages/brew-guide-french-press

**Notas**:

- Fuente: 56 g, ~205 °F, 450 g, tapa sin presionar, a 1:00 quitar tapa y remover, añadir hasta 850 g, presionar a 4:00, decantar.
- Duraciones de los vertidos y acciones [inferidas]. Está cerca de los límites (dosis 56 ≤ 60, agua 850 ≤ 1000).

### 20. AeroPress definitiva de James Hoffmann

- **Clave**: `hoffmann_aeropress`
- **Nombre (es)**: AeroPress definitiva de James Hoffmann
- **Nombre (en)**: James Hoffmann's Ultimate AeroPress
- **Autor**: James Hoffmann
- **Contexto**: Vídeo "The Ultimate AeroPress Technique" (YouTube, 21-abr-2021); resumen escrito en la descripción.
- **Método**: `AEROPRESS`
- **Dosis**: 11 g · **Agua total**: 200 g · **Ratio**: 1:18,2
- **Temperatura**: 100 °C
- **Molienda**: Media tirando a fina ("finer end of medium") para tueste claro; más gruesa y más dosis si es más oscuro. Posición normal, filtro sin enjuagar. Sin catalogKey.
- **Duración total**: 3:20 (6 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 200 | Vierte 200 g mojando todo el café. |
| 2 | ACTION | 10 | 5 | (200) | Pon el émbolo ~1 cm dentro para hacer vacío. |
| 3 | WAIT | 15 | 120 | (200) | Espera 2 min. |
| 4 | SWIRL | 135 | 5 | (200) | Remolinea suavemente sujetando cámara y émbolo. |
| 5 | WAIT | 140 | 30 | (200) | Espera 30 s. |
| 6 | ACTION | 170 | 30 | (200) | Presiona suavemente hasta el final. |

**Fuentes**:

- https://www.youtube.com/watch?v=j6VlT_jUVPc

**Notas**:

- Descripción verificada por mí: "11g coffee", "200g water (brought to a boil and used immediately)", "Don't rinse or preheat", "Wait 2 minutes", "gently swirl", "Wait 30 seconds", "Press gently all the way".
- Hoffmann arranca el reloj al terminar de verter; aquí t=0 es el inicio del vertido, así que la espera de 2 min empieza tras el émbolo.
- Duración del vertido y del prensado (30 s) [inferidas].
- Webs que la describen como invertida o con 17 g contradicen la descripción del autor; descartadas.

### 21. AeroPress de Tuomas Merikanto (Campeonato Mundial de AeroPress 2021)

- **Clave**: `merikanto_wac_2021`
- **Nombre (es)**: AeroPress de Tuomas Merikanto (Campeonato Mundial de AeroPress 2021)
- **Nombre (en)**: Tuomas Merikanto's 2021 World AeroPress Championship
- **Autor**: Tuomas Merikanto (Finlandia)
- **Contexto**: 1.er puesto del World AeroPress Championship 2021 (edición online).
- **Método**: `AEROPRESS`
- **Dosis**: 18 g · **Agua total**: 200 g · **Ratio**: 1:11,1
- **Temperatura**: 80 °C
- **Molienda**: En competición, Macap Labo 70D a 7,0. Equivalente usado al desarrollar la receta: Comandante a 27 clics. Invertida, 2 filtros de papel enjuagados. **catalogKey** `comandante-c40`, valor **27**.
- **Duración total**: 2:30 (10 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 50 | AeroPress invertida: vierte 50 g. |
| 2 | ACTION | 10 | 5 | (50) | Remueve muy suave 3 veces adelante y atrás. |
| 3 | POUR | 15 | 15 | 200 | Vierte hasta 200 g. |
| 4 | WAIT | 30 | 20 | (200) | Infusiona. |
| 5 | ACTION | 50 | 10 | (200) | Remueve suave 3 veces más. |
| 6 | ACTION | 60 | 10 | (200) | Saca el aire y pon la tapa con 2 filtros. |
| 7 | WAIT | 70 | 25 | (200) | Infusiona hasta 1:35. |
| 8 | ACTION | 95 | 5 | (200) | Pon la jarra encima. |
| 9 | ACTION | 100 | 20 | (200) | Da la vuelta y presiona enseguida hasta 2:00. |
| 10 | SWIRL | 120 | 30 | (200) | Remolinea para enfriar hasta 2:30. |

**Fuentes**:

- https://aeropress.com/blogs/blog/wac
- https://aeropress.com/pages/wac-recipes
- https://worldaeropresschampionship.com/pages/recipes

**Notas**:

- Verificado por mí: 18 g, 200 g de Third Wave Water a 80 °C, 0:00-0:10 50 g, 0:10-0:15 remover, 0:15-0:30 a 200 g, remover a 0:50, tapa a 1:00, jarra a 1:35, voltear a 1:40, presionar hasta 2:00, remolinear 2:00-2:30. La fuente menciona el Comandante a 27 clics.
- Duraciones de los pasos de 0:50 y 1:00 [inferidas].
- Sin bypass: es la receta de campeonato que mejor encaja en la app.

### 22. AeroPress de Wendelien van Bunnik (Campeonato Mundial de AeroPress 2019)

- **Clave**: `van_bunnik_wac_2019`
- **Nombre (es)**: AeroPress de Wendelien van Bunnik (Campeonato Mundial de AeroPress 2019)
- **Nombre (en)**: Wendelien van Bunnik's 2019 World AeroPress Championship
- **Autor**: Wendelien van Bunnik (Países Bajos)
- **Contexto**: 1.er puesto del World AeroPress Championship 2019 (Londres).
- **Método**: `AEROPRESS`
- **Dosis**: 30 g · **Agua total**: 220 g · **Ratio**: 1:7,3
- **Temperatura**: 92 °C
- **Molienda**: 7 en una escala de 1 a 10 (Mahlkönig EK43S según el sitio del WAC). Invertida, filtro Aesir enjuagado. Sin catalogKey.
- **Duración total**: 1:40 (8 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 100 | AeroPress invertida: vierte 100 g en 10 s. |
| 2 | ACTION | 10 | 10 | (100) | Remueve con energía 20 veces. |
| 3 | ACTION | 20 | 10 | (100) | Pon la tapa con filtro y saca el aire. |
| 4 | WAIT | 30 | 10 | (100) | Espera. |
| 5 | ACTION | 40 | 20 | (100) | Da la vuelta y presiona (~60 g de concentrado). |
| 6 | POUR | 60 | 10 | 200 | Diluye con 100 g de agua. |
| 7 | POUR | 70 | 10 | 220 | Prueba y añade al gusto; ella usó 120 g en total. |
| 8 | ACTION | 80 | 20 | (220) | Enfría a ~60 °C removiendo y decantando. |

**Fuentes**:

- https://aeropress.com/pages/wac-recipes
- https://worldaeropresschampionship.com/pages/recipes

**Notas**:

- Fuente oficial: 30 g, Spa Blauw a 92 °C, 100 g en 10 s, remover 20 veces, tapa, voltear y presionar, tiempo de infusión total 1:00, diluir 100 g y "I ended up with 120g dilution".
- Momento de la tapa y duración del prensado [inferidos] para cuadrar con el total de 1:00.
- Dilución tras presionar: ver la duda general sobre bypass y báscula.

### 23. AeroPress de George Stanica (Campeonato Mundial de AeroPress 2024)

- **Clave**: `stanica_wac_2024`
- **Nombre (es)**: AeroPress de George Stanica (Campeonato Mundial de AeroPress 2024)
- **Nombre (en)**: George Stanica's 2024 World AeroPress Championship
- **Autor**: George Stanica (Rumanía)
- **Contexto**: 1.er puesto del World AeroPress Championship 2024 (Lisboa).
- **Método**: `AEROPRESS`
- **Dosis**: 18 g · **Agua total**: 180 g · **Ratio**: 1:10
- **Temperatura**: 96 °C
- **Molienda**: Comandante C40 MK4 con Red Clix a 58 clics (870 µm). Red Clix tiene el doble de resolución: ~29 clics estándar [inferido]. Invertida, filtro Aesir enjuagado. Sin catalogKey.
- **Duración total**: 2:35 (10 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 6 | 50 | AeroPress invertida: vierte ~50 g. |
| 2 | WAIT | 6 | 30 | (50) | Bloom ~30 s. |
| 3 | POUR | 36 | 6 | 100 | Vierte otros 50 g (100 g). |
| 4 | ACTION | 42 | 10 | (100) | Remueve N-S-E-O durante 10 s. |
| 5 | WAIT | 52 | 28 | (100) | Espera hasta 1:20. |
| 6 | ACTION | 80 | 10 | (100) | Pon la tapa con el filtro y saca el aire. |
| 7 | ACTION | 90 | 5 | (100) | Da la vuelta sobre la jarra. |
| 8 | ACTION | 95 | 35 | (100) | Presiona suave 30-40 s (~77 g de concentrado). |
| 9 | POUR | 130 | 15 | 155 | Añade agua tibia hasta 130-135 g de bebida. |
| 10 | POUR | 145 | 10 | 180 | Añade 20-30 g de agua a temperatura ambiente. |

**Fuentes**:

- https://worldaeropresschampionship.com/pages/1st-george-stanica-romania-2024
- https://worldaeropresschampionship.com/pages/recipes

**Notas**:

- Fuente oficial: 18 g, 96 °C, ~50 g en 5-6 s, bloom ~30 s, otros 50 g, remover NSEW 10 s, esperar a 1:20, presionar desde 1:35 durante 30-40 s (~76-79 g), diluir con agua caliente a 130-135 g de bebida y 20-30 g a temperatura ambiente.
- Agua acumulada de la dilución [inferida]: 100 + (132,5 − 77,5) = 155 g y +25 = 180 g. La fuente da pesos de bebida, no agua añadida.
- Tres temperaturas (96 °C, tibia y ambiente): la app solo guarda una; uso 96 °C y lo explico en las instrucciones.
- Red Clix no se mapea al catálogo (que usa clics estándar del C40 MK4).
- Barista Magazine lo llama "George Popescu"; las fuentes oficiales dicen Stanica.

### 24. AeroPress de Tay Wipvasutt (Campeonato Mundial de AeroPress 2023)

- **Clave**: `wipvasutt_wac_2023`
- **Nombre (es)**: AeroPress de Tay Wipvasutt (Campeonato Mundial de AeroPress 2023)
- **Nombre (en)**: Tay Wipvasutt's 2023 World AeroPress Championship
- **Autor**: Ekameth "Tay" Wipvasutt (Tailandia)
- **Contexto**: 1.er puesto del World AeroPress Championship 2023 (Melbourne).
- **Método**: `AEROPRESS`
- **Dosis**: 18 g · **Agua total**: 180 g · **Ratio**: 1:10
- **Temperatura**: 89 °C
- **Molienda**: 1Zpresso ZP6 a 65 clics (no está en el catálogo). Invertida, 1 filtro de papel enjuagado. Sin catalogKey.
- **Duración total**: 2:25 (12 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 100 | AeroPress invertida con 16 g de café: vierte 100 g. |
| 2 | WAIT | 10 | 20 | (100) | Espera hasta 0:30. |
| 3 | ACTION | 30 | 5 | (100) | Remueve 5 s con un palillo. |
| 4 | WAIT | 35 | 10 | (100) | Espera hasta 0:45. |
| 5 | ACTION | 45 | 5 | (100) | Añade los 2 g de café restantes. |
| 6 | WAIT | 50 | 5 | (100) | Espera hasta 0:55. |
| 7 | ACTION | 55 | 5 | (100) | Remueve 5 s. |
| 8 | ACTION | 60 | 10 | (100) | Saca el aire y cierra la tapa. |
| 9 | WAIT | 70 | 25 | (100) | Espera hasta 1:35. |
| 10 | ACTION | 95 | 30 | (100) | Da la vuelta y presiona ~30 s (~75 g). |
| 11 | POUR | 125 | 10 | 140 | Añade agua a temperatura ambiente hasta 115 g de bebida. |
| 12 | POUR | 135 | 10 | 180 | Añade agua caliente hasta 155 g de bebida. |

**Fuentes**:

- https://worldaeropresschampionship.com/pages/1st-tay-wipvasutt-thailand-2023
- https://worldaeropresschampionship.com/pages/recipes

**Notas**:

- Fuente oficial: 18 g (16 + 2), 89 °C, 100 g, remover a 0:30, añadir 2 g a 0:45, remover a 0:55, voltear y presionar desde 1:35 ~30 s (~75 g), bypass ambiente hasta 115 g y caliente hasta 155 g de bebida.
- Dosis dividida: la dosis de la receta es 18 g, pero al principio solo van 16 g. Agua acumulada de la dilución [inferida] desde pesos de bebida.
- aeropress.com publica una versión "non-inverted" adaptada (no es la de competición); descartada.
- Un resumen de búsqueda le atribuía "Comandante 32 clics": es del 2.º clasificado (Carlo Graf Bülow). Descartado.

### 25. AeroPress de Némo Pop (Campeonato Mundial de AeroPress 2025)

- **Clave**: `nemo_pop_wac_2025`
- **Nombre (es)**: AeroPress de Némo Pop (Campeonato Mundial de AeroPress 2025)
- **Nombre (en)**: Némo Pop's 2025 World AeroPress Championship
- **Autor**: Némo Pop (Australia)
- **Contexto**: 1.er puesto del World AeroPress Championship 2025 (Seúl).
- **Método**: `AEROPRESS`
- **Dosis**: 18 g · **Agua total**: 170 g · **Ratio**: 1:9,4
- **Temperatura**: 84 °C
- **Molienda**: Comandante Trailmaster X25 con muelas Tigershark a 31 clics, tamizado a 200 µm (no está en el catálogo). Posición normal, Flow Control Cap con 2 filtros. Sin catalogKey.
- **Duración total**: 1:20 (6 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 100 | Vierte 100 g mojando todo el café. |
| 2 | WAIT | 10 | 15 | (100) | Espera hasta 0:25. |
| 3 | ACTION | 25 | 5 | (100) | Remueve N-S-N-S y O-E-O-E. |
| 4 | WAIT | 30 | 20 | (100) | Espera hasta 0:50. |
| 5 | ACTION | 50 | 20 | (100) | Presiona suavemente ~20 s. |
| 6 | POUR | 70 | 10 | 170 | Añade 70 g de bypass (a 50 °C en el original). |

**Fuentes**:

- https://worldaeropresschampionship.com/pages/recipes
- https://aeropress.com/blogs/w-a-c-aeropress-recipes/1st-nemo-pop-australia-2025

**Notas**:

- Fuentes oficiales: 18 g, 170 g (100 g de infusión a 84 °C + 70 g de bypass a 50 °C), remover a 0:25, presionar desde 0:50 ~20 s.
- En el original el bypass se vierte en la jarra ANTES de preparar; aquí se reordena al final para que el agua acumulada suba. Es una adaptación.
- Duración del vertido inicial y del remover [inferidas].

### 26. AeroPress de Tim Wendelboe

- **Clave**: `wendelboe_aeropress`
- **Nombre (es)**: AeroPress de Tim Wendelboe
- **Nombre (en)**: Tim Wendelboe's AeroPress
- **Autor**: Tim Wendelboe
- **Contexto**: Guía "How to brew with an AeroPress" de su tostador (timwendelboe.no). Campeón mundial de barismo 2004.
- **Método**: `AEROPRESS`
- **Dosis**: 14 g · **Agua total**: 200 g · **Ratio**: 1:14,3
- **Temperatura**: 100 °C
- **Molienda**: Fina de filtro ("fine filter ground"), tueste claro. Posición normal, filtro de papel enjuagado. Sin catalogKey.
- **Duración total**: 1:48 (6 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 200 | Vierte 200 g de agua hirviendo. |
| 2 | ACTION | 10 | 5 | (200) | Remueve 3 veces. |
| 3 | ACTION | 15 | 3 | (200) | Pon el émbolo para que no gotee. |
| 4 | WAIT | 18 | 60 | (200) | Infusiona 60 s. |
| 5 | ACTION | 78 | 5 | (200) | Quita el émbolo y remueve 3 veces de atrás adelante. |
| 6 | ACTION | 83 | 25 | (200) | Pon el émbolo y presiona sin apretar de más. |

**Fuentes**:

- https://timwendelboe.no/pages/how-to-brew-with-an-aeropress

**Notas**:

- Fuente: 14 g, 200 g hirviendo, remover 3 veces, émbolo, 60 s, remover 3 veces, presionar.
- Todas las duraciones salvo los 60 s [inferidas]; 'hirviendo' = 100 °C [inferido].

### 27. Solo Dripper de George Jinyang Peng (World Brewers Cup 2025)

- **Clave**: `peng_wbrc_2025`
- **Nombre (es)**: Solo Dripper de George Jinyang Peng (World Brewers Cup 2025)
- **Nombre (en)**: George Jinyang Peng's 2025 World Brewers Cup Solo Dripper
- **Autor**: George Jinyang Peng (China)
- **Contexto**: Campeón del World Brewers Cup 2025. Ronda no indicada.
- **Método**: `OTHER` + methodOther = "Solo Dripper"
- **Dosis**: 15 g · **Agua total**: 210 g · **Ratio**: 1:14
- **Temperatura**: vacía (varias temperaturas, ver los pasos)
- **Molienda**: Media; 11 clics en un molino FM (no está en el catálogo). Agua de 40 ppm. Sin catalogKey.
- **Duración total**: 1:45 (6 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 6 | 30 | A 96 °C: 30 g en tres círculos antihorarios. |
| 2 | WAIT | 6 | 24 | (30) | Espera hasta 0:30. |
| 3 | POUR | 30 | 10 | 120 | A 96 °C: vierte 90 g en 10 s (120 g). |
| 4 | WAIT | 40 | 30 | (120) | Espera hasta 1:10. |
| 5 | POUR | 70 | 15 | 210 | A 80 °C: vierte 90 g a través de un Melodrip (210 g). |
| 6 | WAIT | 85 | 20 | (210) | Drawdown; termina a la 1:45. |

**Fuentes**:

- https://sprudge.com/how-to-brew-coffee-like-the-2025-world-brewers-cup-champion-338576.html
- https://en.wikipedia.org/wiki/World_Brewers_Cup

**Notas**:

- Verificado por mí en Sprudge: 15 g, 210 ml (120 a 205 °F y 90 a 176 °F), 30 ml en 6 s, 90 ml a 0:30 en 10 s, 90 ml a 1:10 con Melodrip, fin 1:45. 205 °F = 96,1 °C; 176 °F = 80 °C.
- Dos temperaturas: propongo dejar waterTempC vacío y poner la temperatura en cada instrucción (alternativa: 96 °C con nota).
- Duración del último vertido [inferida].
- Precalentar gotero y filtro con 80 ml de agua (no cuenta en el agua de la receta).
- Honest Coffee Guide da otros tiempos (total 2:15) sin citar fuentes; descartado.

### 28. GINA de Emi Fukahori (World Brewers Cup 2018)

- **Clave**: `fukahori_wbrc_2018`
- **Nombre (es)**: GINA de Emi Fukahori (World Brewers Cup 2018)
- **Nombre (en)**: Emi Fukahori's 2018 World Brewers Cup GINA
- **Autor**: Emi Fukahori (Suiza, MAME)
- **Contexto**: Campeona del World Brewers Cup 2018 (Belo Horizonte). Ronda no indicada.
- **Método**: `OTHER` + methodOther = "GINA (Goat Story)"
- **Dosis**: 17 g · **Agua total**: 220 g · **Ratio**: 1:12,9
- **Temperatura**: vacía (varias temperaturas, ver los pasos)
- **Molienda**: No especificada. Agua de 100 ppm. Sin catalogKey.
- **Duración total**: 2:55 (9 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | ACTION | 0 | 3 | (0) | Cierra la válvula. |
| 2 | POUR | 3 | 10 | 50 | A 80 °C: vierte 50 g. |
| 3 | WAIT | 13 | 32 | (50) | Válvula cerrada hasta 0:45. |
| 4 | ACTION | 45 | 2 | (50) | Abre la válvula. |
| 5 | POUR | 47 | 58 | 150 | A 95 °C: vierte 100 g en el centro hasta 1:45. |
| 6 | ACTION | 105 | 2 | (150) | Cierra la válvula. |
| 7 | POUR | 107 | 43 | 220 | A 80 °C: vierte 70 g hasta 2:30. |
| 8 | ACTION | 150 | 2 | (220) | Abre la válvula. |
| 9 | WAIT | 152 | 23 | (220) | Deja caer a la jarra hasta 2:55. |

**Fuentes**:

- https://goat-story.com/blogs/news/emi-fukahoris-winning-recipe

**Notas**:

- Verificado por mí en el blog del fabricante: 17 g, 220 g a 100 ppm, 50 g a 80 °C con válvula cerrada 45 s, 100 g a 95 °C hasta 1:45, cerrar, 70 g a 80 °C hasta 2:30, abrir hasta 2:55.
- Tres temperaturas: waterTempC vacío y temperatura en cada instrucción.
- Duración de las acciones de válvula y del primer vertido [inferidas].
- Se puede preparar en una Hario Switch (también tiene válvula), pero eso es una adaptación.

### 29. Hario Switch de Emi Fukahori

- **Clave**: `fukahori_switch`
- **Nombre (es)**: Hario Switch de Emi Fukahori
- **Nombre (en)**: Emi Fukahori's Hario Switch
- **Autor**: Emi Fukahori (MAME)
- **Contexto**: Vídeo de European Coffee Trip "Easy Hario Switch Recipe from World Brewers Cup Champion" (YouTube, 30-mar-2024); receta de las cafeterías MAME.
- **Método**: `OTHER` + methodOther = "Hario Switch"
- **Dosis**: 14 g · **Agua total**: 200 g · **Ratio**: 1:14,3
- **Temperatura**: 93 °C
- **Molienda**: Comandante, 25 clics. **catalogKey** `comandante-c40`, valor **25**.
- **Duración total**: 2:20 (6 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | ACTION | 0 | 2 | (0) | Cierra la válvula. |
| 2 | POUR | 2 | 10 | 50 | Bloom: 50 g con la válvula cerrada. |
| 3 | WAIT | 12 | 18 | (50) | Espera hasta 0:30. |
| 4 | ACTION | 30 | 2 | (50) | Abre la válvula. |
| 5 | POUR | 32 | 38 | 200 | Vierte 150 g en un chorro continuo al centro; termina a 1:10. |
| 6 | WAIT | 70 | 70 | (200) | Drawdown; total 2:20. |

**Fuentes**:

- https://www.youtube.com/watch?v=3euEkTBxtEk

**Notas**:

- Descripción verificada por mí: "14 grams of coffee / 200 grams of water (93C) / 25 clicks on the Comandante grinder", bloom de 50 g con válvula cerrada, abrir a los 30 s, 150 g en vertido continuo, "1:10 end of pouring", "2:20 total brew time".
- Duraciones de las acciones y del bloom [inferidas].

### 30. Hario Switch de Ole Kristian Bøen (World Brewers Cup 2021)

- **Clave**: `boen_wbrc_2021`
- **Nombre (es)**: Hario Switch de Ole Kristian Bøen (World Brewers Cup 2021)
- **Nombre (en)**: Ole Kristian Bøen's 2021 World Brewers Cup Hario Switch
- **Autor**: Ole Kristian Bøen (Noruega)
- **Contexto**: Receta que usó en el World Brewers Cup 2021 (Milán); campeón de Noruega 2020. Ronda y puesto no indicados.
- **Método**: `OTHER` + methodOther = "Hario Switch"
- **Dosis**: 16,5 g · **Agua total**: 240 g · **Ratio**: 1:14,5
- **Temperatura**: 96 °C
- **Molienda**: Media-fina. Hario Switch 02. Sin catalogKey.
- **Duración total**: 3:05 (11 pasos)
- **Confianza**: **alta**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | ACTION | 0 | 2 | (0) | Cierra la válvula. |
| 2 | POUR | 2 | 10 | 50 | Bloom: 50 g con la válvula cerrada. |
| 3 | WAIT | 12 | 28 | (50) | Bloom hasta 0:40. |
| 4 | ACTION | 40 | 2 | (50) | Abre la válvula. |
| 5 | POUR | 42 | 20 | 150 | Vierte enseguida 100 g en el centro. |
| 6 | WAIT | 62 | 28 | (150) | Espera hasta 1:30. |
| 7 | ACTION | 90 | 2 | (150) | Cierra la válvula. |
| 8 | POUR | 92 | 15 | 240 | Vierte 90 g en círculos. |
| 9 | WAIT | 107 | 23 | (240) | Inmersión hasta 2:10. |
| 10 | ACTION | 130 | 2 | (240) | Abre la válvula. |
| 11 | WAIT | 132 | 53 | (240) | Deja drenar; total 3:00-3:15. |

**Fuentes**:

- https://www.hario-europe.com/blogs/hario-community/ole-kristian-boens-switch-recipe

**Notas**:

- Verificado por mí: 16,5 g, 240 ml a 96 °C, bloom de 50 g cerrada 40 s, abrir y 100 g al centro, cerrar a 1:30 y 90 g en círculos, abrir a 2:10, total 3:00-3:15. La página dice que es "the recipe that he used in Italy at the World Brewers Cup 2021".
- Duraciones de los vertidos [inferidas].

### 31. Receta híbrida ("del diablo") de Tetsu Kasuya para Hario Switch (DESCARTADA)

- **Clave**: `kasuya_switch_hybrid`
- **Nombre (es)**: Receta híbrida ("del diablo") de Tetsu Kasuya para Hario Switch
- **Nombre (en)**: Tetsu Kasuya's Hario Switch Hybrid ("Devil's Recipe")
- **Autor**: Tetsu Kasuya
- **Contexto**: Vídeo de su canal (YouTube, 9-feb-2023), en japonés. Sin versión escrita oficial; datos de transcripciones de terceros.
- **Método**: `OTHER` + methodOther = "Hario Switch"
- **Dosis**: 20 g · **Agua total**: 280 g · **Ratio**: 1:14
- **Temperatura**: vacía (varias temperaturas, ver los pasos)
- **Molienda**: Media-fina, algo más fina que para pour-over. (~20 clics de Comandante según una transcripción; no es dato del autor.) Sin catalogKey.
- **Duración total**: 3:00 (9 pasos)
- **Confianza**: **baja**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 10 | 60 | A 93 °C, válvula ABIERTA: vierte 60 g. |
| 2 | WAIT | 10 | 20 | (60) | Espera hasta 0:30. |
| 3 | POUR | 30 | 10 | 120 | A 93 °C: vierte hasta 120 g. |
| 4 | WAIT | 40 | 35 | (120) | Deja drenar; enfría el agua a ~70 °C añadiendo agua fría. |
| 5 | ACTION | 75 | 3 | (120) | Cierra la válvula. |
| 6 | POUR | 78 | 15 | 280 | A ~70 °C: vierte 160 g (280 g). |
| 7 | WAIT | 93 | 12 | (280) | Inmersión. |
| 8 | ACTION | 105 | 3 | (280) | Abre la válvula. |
| 9 | WAIT | 108 | 72 | (280) | Drawdown hasta ~3:00. |

**Fuentes**:

- https://www.youtube.com/watch?v=gC8K40kZ_6E (vídeo primario, sin receta escrita)
- https://honestcoffeeguide.com/brew-recipes/tetsu-kasuya-hybrid-method/
- https://outpostcoffee.com/blogs/brew-at-home/tetsu-kasuyas-hario-switch-recipe-for-perfect-coffee
- https://www.homegrounds.co/coffee-brew-timer/

**Notas**:

- Las tres transcripciones coinciden en 20 g / 280 g, 60 + 60 g con la válvula abierta y 160 g a 70-75 °C con la válvula cerrada, total ~3:00.
- Discrepan en la inmersión: 10 s (HCG), 30 s (Outpost) o de 1:20 a 1:45 (Homegrounds). Programa de consenso [inferido].
- Dos temperaturas: waterTempC vacío.
- Recomendación: verificar con el vídeo o titularla "adaptada de".

### 32. Clever Dripper de James Hoffmann

- **Clave**: `hoffmann_clever`
- **Nombre (es)**: Clever Dripper de James Hoffmann
- **Nombre (en)**: James Hoffmann's Ultimate Clever Dripper
- **Autor**: James Hoffmann
- **Contexto**: Vídeo "The Ultimate Clever Dripper Technique" (YouTube, 3-dic-2020).
- **Método**: `OTHER` + methodOther = "Clever"
- **Dosis**: 18 g · **Agua total**: 300 g · **Ratio**: 1:16,7
- **Temperatura**: vacía (varias temperaturas, ver los pasos)
- **Molienda**: Media-fina. Sin catalogKey.
- **Duración total**: 3:35 (7 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 15 | 300 | Vierte primero los 300 g de agua en el Clever vacío. |
| 2 | ACTION | 15 | 10 | (300) | Añade el café y remueve suave. |
| 3 | WAIT | 25 | 95 | (300) | Infusiona hasta 2:00. |
| 4 | ACTION | 120 | 5 | (300) | Rompe la costra removiendo o agitando. |
| 5 | WAIT | 125 | 25 | (300) | Espera hasta 2:30. |
| 6 | ACTION | 150 | 5 | (300) | Colócalo sobre la taza o jarra para drenar. |
| 7 | WAIT | 155 | 60 | (300) | Drawdown de ~1 min. |

**Fuentes**:

- https://www.youtube.com/watch?v=RpOdennxP24 (la descripción no trae la receta)
- https://gist.github.com/fluency03/eb48aa3da3b7df0fd7ff160d38049857 (transcripción)
- https://honestcoffeeguide.com/brew-recipes/james-hoffmann-clever-dripper/ (transcripción)

**Notas**:

- Dos transcripciones coinciden en 18 g / 300 g, agua primero, 2:00 romper costra, 2:30 drenar, ~1 min de drawdown.
- Temperatura no especificada de forma fiable (HCG dice 90 °C sin respaldo); dejo waterTempC vacío.
- Honest Coffee Guide se contradice (cabecera 300 g, paso 250 g); elijo 300 g.
- Al añadir el café después del agua, la báscula sube 18 g por encima del objetivo; avisar en la instrucción o en notas.
- Alternativas con fuente oficial: Five Senses Coffee (24 g / 400 g, ~94 °C, Baratza Encore 18-22) y Ozone (15 g / 250 g, 95 °C).

### 33. April Brewer de April Coffee

- **Clave**: `april_brewer`
- **Nombre (es)**: April Brewer de April Coffee
- **Nombre (en)**: April Coffee's April Brewer
- **Autor**: Patrik Rolf / April Coffee Roasters
- **Contexto**: Receta base de filtro de April (Copenhague) para sus cafés; Patrik Rolf fue 2.º en el World Brewers Cup 2019 con el April Brewer.
- **Método**: `OTHER` + methodOther = "April"
- **Dosis**: 12 g · **Agua total**: 200 g · **Ratio**: 1:16,7
- **Temperatura**: 92 °C
- **Molienda**: 11,5 en EK43 (en la fuente; no está en el catálogo). Ajusta para 2:30-3:00. Sin catalogKey.
- **Duración total**: 2:45 (6 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 5 | 30 | Vierte 30 g en círculos para mojar todo el café. |
| 2 | POUR | 5 | 10 | 100 | Vierte 70 g en el centro (100 g). |
| 3 | WAIT | 15 | 15 | (100) | Espera hasta 0:30. |
| 4 | POUR | 30 | 5 | 130 | Vierte 30 g en círculos. |
| 5 | POUR | 35 | 10 | 200 | Vierte 70 g en el centro (200 g). |
| 6 | WAIT | 45 | 120 | (200) | Drawdown; total 2:30-3:00. |

**Fuentes**:

- https://www.aprilcoffeeroasters.com/coffee-inf-recipes/el-tesoro
- https://www.aprilcoffeeroasters.com/pages/coffee-inf-recipes

**Notas**:

- Verificado por mí en los datos de la página: "Dose: 12g / Water: 200g at 92°C / Grind size: 11.5 / Brew time: 2:30-3:00", "30g Circle Pour - 70g Center Pour (0:00)" y "(0:30)".
- Contradicción de la fuente: el texto dice que el segundo vertido empieza a los 35 s y la lista dice 0:30. Uso 0:30.
- Es la receta de un café concreto (El Tesoro); otras fichas cambian dosis y molienda (p. ej. Volcan Azul: "Base recipe with April Brewer", 13 g, 94 °C, 60 g en círculos + 40 g al centro).
- Las fichas no siempre nombran el April Brewer; lo infiero de que April sirve así en su tienda.

### 34. Café helado por inmersión de James Hoffmann

- **Clave**: `hoffmann_iced_immersion`
- **Nombre (es)**: Café helado por inmersión de James Hoffmann
- **Nombre (en)**: James Hoffmann's Immersion Iced Coffee
- **Autor**: James Hoffmann
- **Contexto**: Vídeo "Immersion Iced Coffee: A Better & Easier Technique" (YouTube, 15-sep-2023); receta en la descripción.
- **Método**: `OTHER` + methodOther = "Clever o Hario Switch"
- **Dosis**: 37,5 g · **Agua total**: 330 g · **Ratio**: 1:8,8
- **Temperatura**: 100 °C
- **Molienda**: Más fina que para pour-over, sin llegar a espresso. Sin catalogKey.
- **Duración total**: 6:10 (6 pasos)
- **Confianza**: **media**

| # | Tipo | Inicio s | Duración s | Agua acum. g | Instrucción |
|---|------|---------:|-----------:|-------------:|-------------|
| 1 | POUR | 0 | 15 | 330 | Vierte 330 g de agua recién hervida sobre el café. |
| 2 | ACTION | 15 | 10 | (330) | Mezcla hasta que no quede café seco. |
| 3 | WAIT | 25 | 275 | (330) | Infusiona hasta las 5:00 (4:00 si es tostado oscuro). |
| 4 | ACTION | 300 | 5 | (330) | Filtra sobre 170 g de hielo (abre la Switch o pon el Clever sobre la jarra). |
| 5 | WAIT | 305 | 60 | (330) | Deja drenar. |
| 6 | ACTION | 365 | 5 | (330) | Remueve hasta fundir el hielo que quede. |

**Fuentes**:

- https://www.youtube.com/watch?v=8uGGeV8A-BM

**Notas**:

- Descripción verificada por mí: "37.5g of coffee, finer than pour over setting", "330g of freshly boiled ... water", "Steep for at least 5 minutes (or 4 minutes for darker roasts)", "170g of ice", "75g/L, or about 1:13".
- El hielo no se modela: la app mostrará ratio 1:8,8 (agua caliente) en lugar de 1:13 (con hielo). Anotar el hielo en notes.
- Gotero: la fuente solo dice "immersion brewer"; Clever o Switch [inferido].
- Duraciones salvo los 5 min [inferidas]; el drawdown no se especifica.

## Tabla resumen

| # | Clave | Método | Autor | Dosis g | Agua g | Ratio | °C | Total | Confianza |
|---|-------|--------|-------|--------:|-------:|------:|---:|------:|-----------|
| 1 | `four_six` | V60 | Tetsu Kasuya | 20 | 300 | 1:15 | 93 | 3:35 | alta |
| 2 | `hoffmann_ultimate_v60` | V60 | James Hoffmann | 30 | 500 | 1:16,7 | 97 | 3:30 | alta |
| 3 | `hoffmann_one_cup_v60` | V60 | James Hoffmann | 15 | 250 | 1:16,7 | 100 | 3:00 | alta |
| 4 | `rao_v60` | V60 | Scott Rao | 20 | 330 | 1:16,5 | 97 | 4:15 | media |
| 5 | `hedrick_v60_easy` | V60 | Lance Hedrick | 20 | 340 | 1:17 | 100 | 3:00 | alta |
| 6 | `hedrick_one_and_done` | V60 | Lance Hedrick | 15 | 225 | 1:15 | 93 | 2:15 | media |
| 7 | `winton_five_pour` | V60 | Matt Winton | 20 | 300 | 1:15 | 93 | 3:40 | baja |
| 8 | `gagne_v60` | V60 | Jonathan Gagné | 22 | 374 | 1:17 | 100 | 4:03 | media |
| 9 | `wang_wbrc_2017` | V60 | Chad Wang | 15 | 250 | 1:16,7 | 92 | 2:00 | alta |
| 10 | `stumptown_kalita` | Kalita Wave | Stumptown Coffee Roasters | 21 | 345 | 1:16,4 | 96 | 2:50 | alta |
| 11 | `wallgren_wbrc_2016` | Kalita Wave | Mikaela Wallgren | 15 | 250 | 1:16,7 | 96 | 2:35 | alta |
| 12 | `goldsworthy_wbrc_2016` | Kalita Wave | Todd Goldsworthy | 27 | 350 | 1:13 | 95 | 3:00 | media |
| 13 | `du_jianing_wbrc_2019` | Origami | Du Jianing | 16 | 240 | 1:15 | 94 | 1:45 | media |
| 14 | `medina_wbrc_2023` | Origami | Carlos Medina | 15,5 | 250 | 1:16,1 | 91 | 2:40 | media |
| 15 | `stumptown_origami` | Origami | Stumptown Coffee Roasters | 21 | 340 | 1:16,2 | 96 | 3:05 | alta |
| 16 | `stumptown_chemex` | Chemex | Stumptown Coffee Roasters | 42 | 700 | 1:16,7 | 96 | 4:15 | alta |
| 17 | `hoffmann_chemex` | Chemex | James Hoffmann | 30 | 500 | 1:16,7 | 100 | 4:10 | baja |
| 18 | `hoffmann_french_press` | Prensa francesa | James Hoffmann | 30 | 500 | 1:16,7 | 100 | 9:40 | media |
| 19 | `stumptown_french_press` | Prensa francesa | Stumptown Coffee Roasters | 56 | 850 | 1:15,2 | 96 | 4:25 | alta |
| 20 | `hoffmann_aeropress` | AeroPress | James Hoffmann | 11 | 200 | 1:18,2 | 100 | 3:20 | alta |
| 21 | `merikanto_wac_2021` | AeroPress | Tuomas Merikanto | 18 | 200 | 1:11,1 | 80 | 2:30 | alta |
| 22 | `van_bunnik_wac_2019` | AeroPress | Wendelien van Bunnik | 30 | 220 | 1:7,3 | 92 | 1:40 | alta |
| 23 | `stanica_wac_2024` | AeroPress | George Stanica | 18 | 180 | 1:10 | 96 | 2:35 | media |
| 24 | `wipvasutt_wac_2023` | AeroPress | Ekameth "Tay" Wipvasutt | 18 | 180 | 1:10 | 89 | 2:25 | media |
| 25 | `nemo_pop_wac_2025` | AeroPress | Némo Pop | 18 | 170 | 1:9,4 | 84 | 1:20 | media |
| 26 | `wendelboe_aeropress` | AeroPress | Tim Wendelboe | 14 | 200 | 1:14,3 | 100 | 1:48 | media |
| 27 | `peng_wbrc_2025` | Otro (Solo Dripper) | George Jinyang Peng | 15 | 210 | 1:14 | — | 1:45 | alta |
| 28 | `fukahori_wbrc_2018` | Otro (GINA (Goat Story)) | Emi Fukahori | 17 | 220 | 1:12,9 | — | 2:55 | alta |
| 29 | `fukahori_switch` | Otro (Hario Switch) | Emi Fukahori | 14 | 200 | 1:14,3 | 93 | 2:20 | alta |
| 30 | `boen_wbrc_2021` | Otro (Hario Switch) | Ole Kristian Bøen | 16,5 | 240 | 1:14,5 | 96 | 3:05 | alta |
| 31 | `kasuya_switch_hybrid` | Otro (Hario Switch) | Tetsu Kasuya | 20 | 280 | 1:14 | — | 3:00 | baja |
| 32 | `hoffmann_clever` | Otro (Clever) | James Hoffmann | 18 | 300 | 1:16,7 | — | 3:35 | media |
| 33 | `april_brewer` | Otro (April) | Patrik Rolf / April Coffee Roasters | 12 | 200 | 1:16,7 | 92 | 2:45 | media |
| 34 | `hoffmann_iced_immersion` | Otro (Clever o Hario Switch) | James Hoffmann | 37,5 | 330 | 1:8,8 | 100 | 6:10 | media |

**Reparto por método**: V60 9, AeroPress 7, Kalita Wave 3, Origami 3, Otro (Hario Switch) 3, Chemex 2, Prensa francesa 2, Otro (Solo Dripper) 1, Otro (GINA (Goat Story)) 1, Otro (Clever) 1, Otro (April) 1, Otro (Clever o Hario Switch) 1. Total 34.

**Confianza**: alta 17, media 14, baja 3.

## Descartadas y reservas

### No caben en el modelo

| Receta | Motivo |
|--------|--------|
| Espresso (cualquiera) | Se mide en rendimiento de bebida y presión, en 25-35 s, sin vertidos manuales. Un POUR con "agua acumulada" falsearía la receta. |
| Cold brew (cualquiera) | Infusiones de 12-24 h: superan el WAIT máximo (600 s) y el inicio máximo del último paso (600 s). Como mucho se podría modelar el vertido inicial con la espera en notas, pero el temporizador no tendría sentido. |
| James Hoffmann, moka ("The Ultimate Moka Pot Technique", 2022) | Ni dosis ni agua se pesan (se llena la base "hasta la válvula" y el cesto "hasta arriba"), y no hay calendario de vertidos. Fuente: https://www.youtube.com/watch?v=BfDLoIvb0w4 (sin números en la descripción). |
| James Hoffmann, café helado V60 ("Better than cold brew", 2018) | Solo hay una nota personal de terceros (65 g/L, 40 % hielo) sin calendario de vertidos. La versión por inmersión de 2023 sí está incluida (`hoffmann_iced_immersion`). |
| Lance Hedrick, Hario Switch "Gong Fu" (2025) | 19 pasos y dos temperaturas, y la única fuente es una transcripción de terceros (gota.cafe). |

### Fuente insuficiente o contradictoria

| Receta | Motivo |
|--------|--------|
| Sherry Hsu, campeona del World Brewers Cup 2022 | Crónica no oficial (vocus.cc) y Wikipedia se contradicen en el gotero (fondo plano u Orea V3) y en la temperatura del 1.er vertido (75 o 70 °C). Además, falta el tiempo final. 14 g / 200 g, 4 × 50 g cada 30 s. |
| Martin Wölfl, campeón del World Brewers Cup 2024 (Orea V4) | Su tostador confirma 17 g / 270 ml, pero el programa de vertidos solo está en Wikipedia; European Coffee Trip respondió 403. La molienda se contradice: 630 µm en Wikipedia y 490 µm en un resumen. |
| Ryan Wibawa, 3.º en el World Brewers Cup 2024 (Hario Switch) | Reinicia el temporizador a mitad, usa dos temperaturas y no da tiempo final. Fuente: https://hario-europe.com/blogs/hario-community/interview-with-ryan-wibawa-switch-recipe |
| Matt Winton, receta de competición 2021 | No se publicó el programa (Barista Hustle tras muro de pago). Se incluye su "Five Pour" divulgativa con confianza baja. |
| Odd-Steinar Tøllefsen (2015), Stefanos Domatiotis (2014) | Solo datos parciales (dosis, agua y total) sin programa de vertidos, o ninguna fuente fiable. |
| Patrik Rolf, 2.º en el World Brewers Cup 2019 | La página de April sobre su receta de competición da 404. Se incluye la receta de tienda de April. |
| Finalistas de 2017 (Sam Corra, Stathis Koremtas, Constantin Hoppenz, Michael Manhart, Petra Strelecka) y Benny Wong (2016) | Sprudge solo da dosis, agua y temperatura, sin programa de vertidos. |
| Scott Rao, Kalita Wave y Clever | Sus vídeos y entradas describen el método sin números fijos. |
| Lance Hedrick, "ULTIMATE POUROVER RECIPE (any method)" y "2 and Through" | La primera es una plantilla con rangos. La segunda solo tiene una transcripción de terceros, que además indica un V60 01 para 30 g / 450 g, algo dudoso. |
| Lance Hedrick, "1-2-1" | Solo hay una transcripción de terceros, que cita un vídeo que no resolvió. Confianza baja-media; se puede recuperar si se verifica. |
| Kasuya, Switch "evolucionada" (2025) y vídeo de Switch de 2022 | No hay números escritos fiables. |
| Receta oficial de AeroPress ("Signature Brew") | Da el agua por la marca "4" de la cámara, no en gramos. Habría que inventar el peso. |
| Blue Bottle (Chemex, prensa francesa) | La web bloqueó la lectura (403); sin verificar. |

### Reservas válidas (no incluidas para no saturar el catálogo)

| Receta | Datos | Fuente |
|--------|-------|--------|
| Kasuya 4:6, variante de Hario | 20 g / 300 g, 92 °C, 50/70/60/60/60 a 0:00, 0:45, 1:30, 2:10, 2:40; retirar a 3:30 | https://hario-europe.com/blogs/hario-community/v60-ambassadors-tetsu-kasuya |
| Chad Wang, 3.º en el World Brewers Cup 2016 (V60) | 15 g / 250 g, 92 °C, 4 pulsos (50/120/180/250 a 0:00, 0:30, 0:52, 1:15) | https://baristamagazine.com/chad-wangs-world-brewers-cup-recipe |
| Carolina Ibarra Garay, campeona del World AeroPress Championship 2018 | 34,9 g, 160 g a 85 °C + 40 g a temperatura ambiente, invertida, 1:30 | https://aeropress.com/pages/wac-recipes |
| Paulina Miczka, campeona del World AeroPress Championship 2017 | 35 g, 84 °C, invertida. La cabecera dice 370 g, pero los pasos suman 310-350 g (incoherencia de la fuente). | https://aeropress.com/pages/wac-recipes |
| Lukas Zahradnik, campeón del World AeroPress Championship 2015 | 20 g / 230 g, 79 °C, invertida. Las duraciones no cuadran con el total de 1:35. | https://aeropress.com/pages/wac-recipes |
| Sophan Nugraha, 2.º en el World AeroPress Championship 2024 (atribución por orden del podio) | 18 g, 60 ml a temperatura ambiente para el bloom y luego 95 °C hasta 190 ml; dilución de 10 ml | https://worldaeropresschampionship.com/pages/recipes |
| Five Senses Coffee, Clever | 24 g / 400 g, ~94 °C, Baratza Encore 18-22 (`baratza-encore`), romper costra a 1:00, drenar a 4:00 | https://fivesenses.com.au/blog/clever-coffee-dripper-brew-guide |
| Ozone Coffee, Clever | 15 g / 250 g, 95 °C, remover 15 s, drenar a 2:15, fin 3:00-3:30 | https://ozonecoffee.co.uk/pages/brew-guide-clever-dripper |
| Origami oficial (con KOPIKALYAN TOKYO) | 18 g / 300 g, 92 °C, Comandante 24-25 clics, 40/60/60/60/80 cada 30 s; sin tiempo final | https://origami-kai.com/en/blogs/special-feature/origami-with-kopikalyan-tokyo-vol-3 |
| Jonathan Gagné, flujo de 2018 | 22 g / 352 g, bloom de 66 g; tiempos por señales visuales | https://coffeeadastra.com/2018/11/30/brewing-better-coffee/ |

### Sitios que no se pudieron leer

European Coffee Trip (403; el vídeo de Fukahori sí se leyó por su descripción de YouTube), Bean Scene (403), Barista Hustle (402, muro de pago), Blue Bottle (403) y la página de competición de April (404). Tampoco se pudieron leer las transcripciones de YouTube; solo las descripciones de los vídeos.
