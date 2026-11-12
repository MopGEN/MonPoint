# Caja visual: revisión e integración del 12 de noviembre

Base `1cd0bc7`, fusión #29 de la concha. Parche recibido
`0001-5.5-Rejilla-de-catalogo-indicador-de-pasos-y-atajos-.patch`, commit
declarado `e6b4c65`, importado con git am como `eb72d6b`, conservando la autoría
de Alejandro Bermúdez. Los cambios de revisión se añaden después.
Pruebas en Linux/Chromium, sin acreditar ejecución manual ni atajos en Windows.

## Resultado y decisiones de alcance

La Caja usa tarjetas con precio e insignia de existencia, filtro por nombre y
descripción sin acentos, F2 para enfocar la búsqueda, Enter para agregar y
Ctrl+Enter/Meta+Enter/F12 para cobrar. El indicador ordenado refleja Carrito,
En proceso y Resultado sobre la máquina de cobro existente.

Se leyó `ProductoResponse` completo: no tiene código/SKU, sí `umbralStock`.
La tarjeta no inventa un identificador. La existencia cero se distingue del
aviso `existencia <= umbralStock`, sin impedir intentar una venta: el servidor
decide la reserva. `ReservaStockService` usa el mismo umbral tras reservar, lo
que no convierte una insignia del catálogo en prueba de una alerta emitida.
No se añadieron campos de producto, endpoints ni cambios de persistencia.

Se ajustó únicamente el relleno de la ruta Caja y su reparto interior. Productos
y las demás pantallas conservan el relleno de la concha. IBM Plex Mono regular
y medium se extrajeron de los recursos del HTML recibido y se autoalojan con
su licencia OFL: 14.708 + 14.888 = 29.596 bytes. Inter ya estaba autoalojada y
continúa cargándose; no se deduce su ausencia de index.html. Todos los importes
de Caja usan Inter con cifras tabulares. Chips e identificadores usan la pila
IBM Plex Mono.

Tras la revisión, ambas licencias viven en `public/fuentes/`: Vite las copia
sin modificar a `dist/fuentes/`, con rutas estables y sin depender de ejecutar
JavaScript. Se retiraron los imports y la inyección de enlaces de main.js.
La [comprobación estática](pruebas/licencias-estaticas.json) contrasta los bytes
y su entrega HTTP con JavaScript deshabilitado. No se ejecutó el empaquetado nginx.

## Defectos corregidos durante la revisión

1. **Ctrl+Enter agregaba y cobraba a la vez.** El manejador Enter del buscador
   también aceptaba modificadores; primero agregaba el resultado y luego el
   manejador global enviaba el cobro. La prueba original solo contaba POST y su
   doble devolvía un total fijo, por lo que no detectaba el contenido incorrecto.
   [Reproducción anterior al arreglo](pruebas/atajos-antes.json): se había
   elegido `p2`, pero el cuerpo incluía también `p1`. Ahora Enter simple y los
   atajos de cobro son excluyentes; [la regresión](pruebas/atajos.json) exige el
   cuerpo exacto, incluido el caso de carrito vacío.
2. **Atajos sin contexto suficiente.** Se excluyen composición de texto,
   repetición de tecla, modificadores ajenos, menús/diálogos abiertos y rutas
   distintas de Caja. Enter solo agrega cuando el catálogo está visible y su
   carga terminó correctamente; no agrega datos ocultos durante carga/error.
   La escucha global se retira al desmontar.
3. **F12 se describía como universalmente no cancelable.** Chromium recibió en
   este recorrido un keydown confiable y cancelable y observó defaultPrevented
   después del manejador. Está registrado en `pruebas/atajos.json`. Esto prueba
   la ruta de la aplicación por Playwright, no el comportamiento de una tecla
   física, DevTools o Windows. Se retiró la afirmación absoluta y se muestra
   Ctrl+Enter/⌘+Enter como alternativa. No se promete que todos los navegadores
   entreguen F12 a la página.
4. **Aislamiento y contrato del doble visual.** Faltaba atender la nueva lectura
   de configuración de cabecera, lo que podía llegar al Gateway real. Ahora se
   usa el fixture de concha. El POST simulado devuelve 202, como `/ventas`, en
   lugar de 201. Las pruebas de teclado y geometría atienden todas sus rutas de
   API y no usan las respuestas del conjunto vivo como parte del doble.
5. **Separadores del indicador.** Las flechas pasaron del contenido generado por
   CSS a elementos explícitos con aria-hidden, sin dar por hecho que un lector
   de pantalla ignora los pseudoelementos. El encabezado accesible del catálogo
   dice «Catálogo de productos» para distinguirlo del h1 de la ruta Productos.

## Medidas verificadas

Se volvió a renderizar el HTML original a 1440 × 900 y se midió el botón que
contiene REF-001 y sus ancestros. [Extracción](pruebas/prototipo-medidas.json).
El resultado se contrasta con [el DOM del cliente](pruebas/geometria.json):

| Medida | Prototipo | Cliente revisado |
|---|---:|---:|
| Lateral | 232 px | 232 px |
| Interior a la derecha | 1208 px | 1208 px |
| Catálogo | 816 px | 816 px |
| Panel de cobro | 392 px | 392 px |
| Cuatro tarjetas por fila | 185 px cada una | 185 px cada una |
| Relleno de tarjeta / separación | 14 / 12 px | 14 / 12 px |
| Precio | Inter 20 px, peso 600 | Inter 20 px, peso 600 |

No se afirma igualdad completa de píxeles: el prototipo tiene línea de SKU y
datos distintos; el cliente no tiene esa línea. También difieren un píxel en
la altura de cabecera y algunos textos/controles del carrito. No se alteró la
marca ni el icono del conmutador de tema en esta intervención.

Se comprobaron 1440, 1280, 1100, 960, 820, 720, 390 y 320 px, sin desbordar el
documento ni truncar las tarjetas. Al faltar 814 px de contenido disponible se
pasa a una columna; depende del ancho interior, también con la lateral
colapsada. En móvil hay dos tarjetas por fila. El panel de cobro vuelve al flujo
normal. Las capturas permiten revisar el resultado, no acreditan cobertura de
todos los anchos ni de todos los navegadores.

## Verificación ejecutada

| Comprobación | Resultado |
|---|---|
| npm test | [95 aprobadas, cero fallos u omitidas](pruebas/unitarias.txt) |
| npm run build | [Correcta](pruebas/build.txt), sin dependencias ni lockfile nuevos |
| Once recorridos simulados sobre build/preview | Caja, Caja visual, atajos, geometría, ticket, concha, Productos, Clientes, Reportes, Notificaciones y Usuarios: salidas en `pruebas/` |
| Regresión del cuerpo del cobro | [Aprobada](pruebas/atajos.json): no hay línea adicional ni cobro de carrito vacío |
| Geometría, temas y fuentes | [Aprobada](pruebas/geometria.json): recursos locales 200, sin solicitudes externas en ese recorrido |
| Usuarios/ticket contra servicios reales y Vite | [Aprobado](real/resultado.json), sin interceptar las peticiones |

El recorrido real se adaptó a la tarjeta y al nuevo nombre del buscador. Cobra
mediante Ctrl+Enter conservando el foco en el filtro, justo donde estaba el
defecto. Observó estas dos ventas y descargó sus PDF:

| Rol al cobrar | Folio | Estado por GET | Cantidad | Total |
|---|---:|---|---:|---:|
| VENDEDOR | 61 | CONFIRMADA | 1 | 41.15 |
| ADMIN | 62 | CONFIRMADA | 1 | 41.15 |

[Ticket 61](real/ticket-folio-61.pdf) y [ticket 62](real/ticket-folio-62.pdf).
El script extrajo su texto con pdftotext y contrastó folio, producto y total;
registró que pedir el ticket no repitió el POST. No hubo cobros reales fuera del
conjunto de prueba. La ejecución creó un producto y dos ventas, que conserva;
creó dos cuentas de prueba y las desactivó al terminar, ambas con 204. También
volvió a comprobar los permisos y edición de Usuarios de su recorrido existente.

## Capturas

- [Claro con carrito](capturas/01-claro-carrito.png) y
  [oscuro con carrito](capturas/02-oscuro-carrito.png).
- [Venta confirmada](capturas/02-confirmada.png) y
  [cancelada](capturas/03-cancelada.png), con desenlaces simulados.
- [Ancho 820](capturas/ancho-820.png), [móvil 390](capturas/ancho-390.png) y
  [móvil 320](capturas/ancho-320.png).
- [Ticket desde Caja VENDEDOR](real/ticket-VENDEDOR.png) y
  [desde Caja ADMIN](real/ticket-ADMIN.png), con servicios reales.

Los datos de catálogo/ventas de las capturas en `capturas/` son dobles, no
instantáneas del backend. Los de `real/` pertenecen al conjunto local de prueba.
No se editaron ni reescalaron los PNG.

## Límites e integridad

No se verificaron teclado físico ni empaquetado Windows/nginx. Meta+Enter está
implementado, pero no se probó sobre macOS. Las pruebas simuladas verifican los
manejadores y el render; no sustituyen los contratos del servidor. La operación
incierta sigue siendo incierta y no se reenvía automáticamente. No se añadieron
historial paginado, Proveedores ni otras pantallas. No se tocaron Asana, las
tareas 6.2/6.3/6.4 ni las capturas pendientes del expediente 6.1.

`fuentes.json` identifica el parche recibido, el HTML del prototipo, las fuentes
extraídas y los archivos del cliente cambiados. `SHA256.json` cubre este
expediente salvo el propio manifiesto; debe contrastarse con los bytes de Git.
Las reglas -text conservan esos bytes al clonar con autocrlf. La referencia de
tokens ya está conservada en el expediente de la concha, no se duplica aquí.
