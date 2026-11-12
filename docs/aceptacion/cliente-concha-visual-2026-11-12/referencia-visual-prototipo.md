# Referencia para alinear el cliente con el prototipo

Extraído del prototipo renderizado (`docs/prototipo/index.html`), leyendo las
reglas CSS y la geometría del DOM, no de capturas. 72 variables, tema claro y
oscuro.

No es una reescritura: `base.css` ya nació del prototipo. Lo que sigue son los
valores que derivaron, los tokens que faltan y los cambios de estructura.

---

## 1. Valores que derivaron

Cinco variables actuales no coinciden con el prototipo. Cambiarlas no rompe
nada porque conservan su nombre.

| Variable actual | Hoy | Prototipo | Nombre allí |
|---|---|---|---|
| `--mp-fondo` | `#f4f6f9` | **`#F2F5F8`** | `--sup-0` |
| `--mp-borde` | `#d8dee8` | **`#C8D1DC`** | `--borde` |
| `--mp-texto` | `#0a1422` | **`#16202B`** | `--texto-1` |
| `--mp-texto-suave` | `#5a6677` | **`#4E5A6B`** | `--texto-2` |
| `--mp-verde-oscuro` | `#17805a` | **`#17795A`** | `--verde-700` |

`--mp-marino` (`#12233f`) y `--mp-verde` (`#1f9d6e`) ya son correctos.

El rojo y el ámbar actuales (`#c0392b`, `#b7791f`) no existen en el prototipo:
su sistema usa tríos de estado, descritos abajo.

---

## 2. Tokens que faltan

### Escala de grises

```
--g50  #F7F9FB    --g500 #7A8798
--g100 #EDF1F6    --g600 #5C6878
--g200 #DFE5EC    --g700 #43505F
--g300 #C8D1DC    --g800 #2B3643
--g400 #A3AFBE    --g900 #18212B
```

### Azul y verde completos

```
--azul      #12233F    --verde      #1F9D6E
--azul-700  #1B3559    --verde-700  #17795A
--azul-500  #2A4A75    --verde-100  #E1F3EB
--azul-100  #DCE4EF
```

### Superficies

```
--sup-0 #F2F5F8   fondo de página
--sup-1 #FFFFFF   tarjetas
--sup-2 #EDF1F6   realces
--sup-3 #E2E8F0   realces fuertes
```

### Bordes y texto

```
--borde        #C8D1DC    --texto-1 #16202B
--borde-suave  #DFE5EC    --texto-2 #4E5A6B
--borde-fuerte #94A2B4    --texto-3 #6C7889
```

### Estados, en tríos de texto, fondo y borde

Esto es lo que más nos falta: hoy tenemos un color plano por aviso y el
prototipo define los tres, que es lo que da esas insignias y avisos con fondo
tenue y borde del mismo tono.

```
--exito #177955   --exito-bg #E1F3EB   --exito-borde #9CD6BE
--adv   #8A5200   --adv-bg   #FBF0DC   --adv-borde   #E2BE7C
--err   #B3261E   --err-bg   #FCE9E7   --err-borde   #E8A19B
--proc  #1B4F8F   --proc-bg  #E3ECF8   --proc-borde  #9CBCE4
```

`--proc` es el estado "en proceso", que corresponde a nuestra venta PENDIENTE.
Hoy no tenemos color para eso.

### Barra lateral

```
--barra        #12233F
--barra-texto  #E7EDF5
--barra-activo #1F3A63
```

### Radios

```
--r-ctl  8px    botones, campos, insignias
--r-card 12px   tarjetas y diálogos
```

Hoy usamos `--mp-radio: 8px` para todo. Las tarjetas deben ir a 12px.

---

## 3. Tema oscuro

Ya tenemos el mecanismo correcto (`[data-tema='oscuro']` más la consulta
`prefers-color-scheme`). Solo cambian los valores:

```
--sup-0 #0C131C   --borde        #33424F   --texto-1 #E9EEF4
--sup-1 #141E29   --borde-suave  #263340   --texto-2 #AFBCCA
--sup-2 #1B2733   --borde-fuerte #5A6B7C   --texto-3 #8A98A8
--sup-3 #23313F
--verde #2FBE88   --verde-700 #249B6E   --verde-100 #10362A
--exito #4ED09B   --exito-bg #0F3328    --exito-borde #1E6B4F
--adv   #E9B45C   --adv-bg   #35270D    --adv-borde   #7A5C1E
--err   #F3867D   --err-bg   #3A1714    --err-borde   #7E2D27
--proc  #7FB2EE   --proc-bg  #0F2740    --proc-borde  #2A527F
--azul-100 #1B2A3D
--barra #0A111A   --barra-texto #DCE5EF
```

Falta lo visible: **el conmutador de tema en la barra superior**. El prototipo
lo tiene entre la campana y el chip de usuario.

---

## 4. Tipografía

El prototipo usa **Inter**; nosotros `system-ui`. Es de las diferencias que más
se notan, y trae una decisión con consecuencia para la entrega:

- **Enlazar Google Fonts** significa que la demostración del 19 depende de que
  haya internet. En un salón, eso es un riesgo evitable.
- **Autoalojar Inter** dentro del cliente nginx: un par de archivos woff2 y un
  `@font-face`. Pesa unos 40 kB y funciona sin red.
- **Quedarnos en `system-ui`** y aceptar la diferencia.

Recomiendo autoalojar. Es la única opción que se ve como el prototipo y no
depende de la red el día de la demostración.

Medidas tomadas del DOM: campos de **48 px** de alto con radio 8, botón
primario de peso **700**.

---

## 5. Estructura de la concha

Esto es lo que cambia la silueta de la aplicación, más que los colores.

**Hoy:** barra superior oscura a todo lo ancho, debajo barra lateral blanca y
contenido.

**Prototipo:** barra lateral **azul marino a toda altura**, de unos 232 px, con
el logotipo arriba y "Colapsar barra" abajo. La barra superior vive **dentro**
del área de contenido, a la derecha de la lateral.

La barra superior del prototipo lleva, de izquierda a derecha:

1. Eyebrow con el nombre del negocio y, debajo, el título de la página.
2. **Chip de ruta** en monoespaciada: `/caja`, `/productos`.
3. A la derecha: campana con badge numérico, conmutador de tema, y chip de
   usuario con iniciales, nombre y rol, con menú desplegable.

El elemento activo de la barra lateral usa `--barra-activo` como fondo, no solo
un cambio de color de texto.

---

## 6. Lo que NO hay que copiar

- La píldora flotante **"PROTOTIPO · Escenarios"** abajo a la izquierda y su
  panel: son andamiaje del prototipo.
- Las credenciales de demostración de la pantalla de inicio.
- **Proveedores**: no existe microservicio detrás. Es una sección del prototipo
  fuera de la arquitectura de seis servicios.
- **Historial de ventas** y **Ventas en proceso**: ms-ventas solo expone
  `POST /ventas` y `GET /ventas/{id}`. Sin endpoint de listado no hay pantalla.

---

## 7. Orden sugerido

1. Reconciliar los cinco valores derivados y añadir los tokens que faltan.
2. Inter autoalojada.
3. La concha: lateral a toda altura con logotipo, barra superior dentro del
   contenido, chip de ruta, conmutador de tema y chip de usuario.
4. Tarjetas a `--r-card` y avisos e insignias con los tríos de estado.
5. Después, pantalla por pantalla. La Caja es la más lejana: rejilla de
   tarjetas de producto, buscador con F2, stepper de tres pasos y Cobrar con
   F12.

Los pasos 1 a 4 mueven las seis pantallas a la vez. El 5 es trabajo por
pantalla y más largo.
