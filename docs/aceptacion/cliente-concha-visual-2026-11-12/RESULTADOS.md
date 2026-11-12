# Tokens y concha visual: revisión del 12 de noviembre

Base `2204da5`, main con Usuarios y ticket fusionados. Implementación sobre la
referencia visual entregada por el usuario, sin cambiar contratos de servicios
ni incorporar las pantallas excluidas. Pruebas en Linux con Chromium; no es una
ejecución manual de Víctor/Hugo ni evidencia del empaquetado en Windows.

## Cambio que se puede revisar

- Lateral marino a toda altura: 232 px expandida y 64 px colapsada, marca arriba
  y control de colapso abajo. Conserva enlaces derivados del router y filtro por
  la misma función de rol que usa la guarda.
- Cabecera dentro del contenido: nombre del negocio, título único de página,
  chip de ruta, campana ADMIN, conmutador claro/oscuro y chip de usuario con
  iniciales, nombre, rol y menú para salir. Los desplegables cierran al salir el
  foco, pulsar fuera, navegar o pulsar Escape; Escape devuelve el foco al botón.
- Cinco colores reconciliados, escalas y superficies añadidas, estados en
  tríos de texto/fondo/borde, proceso azul, controles de 8 px y tarjetas y
  diálogos de 12 px. Campos de altura mínima 48 px. Caja conserva sus columnas,
  carrito y lógica de cobro; sus resultados usan los tríos semánticos.
- Inter variable autoalojada: 48.256 + 85.068 = 133.324 bytes. No son los 40 kB
  estimados en la referencia. Se incluyen los dos WOFF2 originales y licencia
  OFL; el recorrido verifica respuestas locales 200 para el subconjunto usado.
- Logo PNG extraído sin modificar del HTML del prototipo, coincidente
  visualmente con la imagen adjunta. Se conserva completo, con su fondo blanco,
  en lugar de recrear el símbolo con otro dibujo.

La cabecera muestra el negocio registrado por `GET /reportes/configuracion`,
permitido a ambos roles. Ante 404 usa «Auto Tachi»; durante carga o error usa
«MonPoint». En el conjunto real aparece el nombre de pruebas ya registrado
`Revision Reportes 1794411680561`: no se cambió la empresa para embellecer la
captura. Guardar desde Reportes invalida la lectura de la cabecera.

El badge cuenta la lista sin leer de la última consulta. Se consulta al montar
la campana, al abrirla y tras marcar desde Notificaciones. Error o carga omiten
la cifra, sin afirmar que haya cero avisos. No hay sondeo, push ni actualización
en tiempo real. VENDEDOR no monta la campana ni consulta sus endpoints.

El tema sigue al sistema hasta elegirlo y solo esa elección se guarda como
`monpoint.tema`. El token sigue en memoria: F5 conduce a login, conservando el
tema. El colapso no se persiste. El interruptor funciona incluso con acceso a
localStorage bloqueado.

## Verificación y alcance de cada resultado

| Comprobación | Resultado conservado |
|---|---|
| `npm test` | 95 aprobadas, cero fallos u omitidas: [salida](pruebas/unitarias.txt) |
| `npm run build` | Correcta: [salida](pruebas/build.txt); sin cambios de dependencias ni lockfile |
| Concha con API simulada sobre build/preview | [Diez casos aprobados](pruebas/concha.json): geometría, seis rutas en ambos temas, menú, contador, empresa, móvil, roles, fallo/reintento y preferencias |
| Siete recorridos existentes con API simulada sobre Vite | Caja, Productos, Clientes, Reportes, Notificaciones, Usuarios y ticket: salidas en `pruebas/` |
| Concha con Gateway y servicios vivos sobre Vite | [Aprobada](real/resultado.json): empresa y contador contrastados con API, seis rutas ADMIN, diálogo oscuro, tres rutas VENDEDOR, F5 y tema |

El recorrido real no intercepta peticiones. Sus GET registrados respondieron
200. No creó productos, ventas ni alertas y no marcó avisos existentes. Creó
una cuenta de vendedor y la desactivó al terminar (204). El intento anterior
también creó su propia cuenta y la desactivó (204). Son escrituras de prueba
en ms-auth, no cambios al contrato ni a los datos comerciales.

Se conservan dos fallos de los propios recorridos antes de corregirlos:

- [Concha inicial](pruebas/concha-intento-01.json): un selector singular de
  «Cargando» encontró dos recursos en Reportes. Se cambió por esperar la
  ausencia de todas las cargas; no se alteró la aplicación por esa aserción.
- [Real inicial](pruebas/real-intento-01.json): tras F5 la guarda llevó a
  `/login?regresarA=/clientes`, pero la prueba esperaba una URL sin query.
  Se comprueba ahora el pathname `/login`. La red y la aplicación no fallaron.

## Capturas para comparar

Las capturas simuladas usan datos controlados; no acreditan contenido del
conjunto vivo. La serie real vive por separado en `real/`.

| Vista | Clara | Oscura |
|---|---|---|
| Inicio | [Login](capturas/01-login-claro.png) | [Login](capturas/05-login-oscuro.png) |
| Caja | [Caja](capturas/02-caja-claro.png) | [Caja](capturas/oscuro-caja.png) |
| Productos | [Productos](capturas/claro-productos.png) | [Productos](capturas/oscuro-productos.png) |
| Clientes | [Clientes](capturas/claro-clientes.png) | [Clientes](capturas/oscuro-clientes.png) |
| Reportes | [Reportes](capturas/claro-reportes.png) | [Reportes](capturas/oscuro-reportes.png) |
| Notificaciones | [Notificaciones](capturas/claro-notificaciones.png) | [Notificaciones](capturas/oscuro-notificaciones.png) |
| Usuarios | [Usuarios](capturas/claro-usuarios.png) | [Usuarios](capturas/oscuro-usuarios.png) |

También se conservan [lateral colapsada](capturas/03-colapsada.png),
[campana](capturas/04-campana-oscuro.png),
[Reportes móvil](capturas/movil-reportes.png),
[Caja móvil](capturas/movil-caja.png),
[Productos móvil](capturas/movil-productos.png) y
[VENDEDOR](capturas/06-vendedor.png).
El móvil se comprobó a 390 × 844 px, sin desbordamiento de documento y con menú
de usuario contenido en el viewport. La tabla puede desplazarse internamente.

No se afirma igualdad de píxeles con el prototipo ni cobertura exhaustiva de
accesibilidad o de navegadores. Se revisaron visualmente login, tablas claras y
oscuras, panel de avisos, diálogo y composición móvil. La geometría y una
selección de valores CSS leídos del DOM están en `pruebas/concha.json`.

## Límites acordados

La rejilla de productos de Caja, F2/F12 y stepper requieren otra intervención.
No se portaron escenarios/credenciales de demostración, Proveedores, Historial
de ventas ni Ventas en proceso. Configuración continúa dentro de Reportes; no
se creó una pantalla ni enlace de Guía de estilo. El PDF de historial existente
permanece en Reportes, sin implicar un listado nuevo de ms-ventas.

Estos recorridos no repiten todos los CRUD contra backend ni verifican el
instalador/imagen nginx en Windows. Las regresiones de cobro, permisos, errores
por campo, formularios y descarga se ejecutaron con sus dobles existentes.
No se tocaron Asana ni el expediente 6.1 pendiente de restauración de capturas.

## Procedencia e integridad

[Referencia recibida](referencia-visual-prototipo.md), conservada sin editar.
[fuentes.json](fuentes.json) identifica la base, los hashes de los tres archivos
de entrada y los archivos del cliente cambiados. El archivo de referencia
procede del usuario; sus afirmaciones de contrato no sustituyen al código real.
`SHA256.json` cubre este expediente salvo el propio manifiesto. Los PNG no se
reescalaron ni editaron; `.gitattributes` conserva sus bytes y los de los JSON
al clonar con autocrlf. El manifiesto debe verificarse contra los blobs de Git,
no deducirse de que la copia de trabajo se vea igual.
