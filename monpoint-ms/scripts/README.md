# Datos de prueba por API — actividad 3.1

## Fase 1 implementada: usuarios, clientes y productos

`semilla.mjs` se ejecuta con el conjunto levantado y entra únicamente por el
Gateway. No escribe en MongoDB ni publica eventos artificiales. Las altas de
productos y clientes usan sus servicios, que publican los eventos de catálogo.
Esta fase **no crea ventas ni acredita la recepción de esos eventos** en ventas.

Requiere Node.js **22.9 o posterior**, sin `npm install`, y auth, clientes e
inventario disponibles a través del Gateway. Antes de la primera alta, el guion:

1. Valida la configuración, autentica al administrador y consulta su perfil.
2. Lee los tres listados y comprueba todas las coincidencias de la semilla.
3. Si el vendedor ya existe, verifica también sus credenciales.
4. Crea únicamente lo que falta; comprueba el login del vendedor recién creado.

El ADMIN inicial lo crea `DataInitializer` al arrancar ms-auth con `usuarios`
vacía. El guion lo verifica y reutiliza; no intenta dar de alta un administrador
sin autenticarse. El resultado queda asociado a los ids que devuelve la API.

| Datos | Identidad estable | Valores iniciales |
|---|---|---|
| ADMIN | `admin@monpoint.com` | Cuenta inicial existente |
| VENDEDOR | `vendedor.semilla@monpoint.test` | `Semilla 3.1 - Vendedor` |
| Clientes | `Semilla 3.1 - Ana`, `Semilla 3.1 - Luis` | Contactos ficticios en `example.test` |
| Productos | `Semilla 3.1 - Tinta`, `Semilla 3.1 - Papel`, `Semilla 3.1 - Sello` | Precios 41.15, 75.50 y 120.00; existencia 10; umbral 5 |

No usa UUID ni fechas en los nombres. No crea otro producto por cada ejecución.

## Ejecución en Windows

Desde PowerShell, con `infra/.env` ya configurado:

```powershell
Set-Location C:\Users\osber\MonPoint\monpoint-ms
docker compose --project-directory infra ps
node --version
node --env-file=infra/.env scripts/semilla.mjs
if ($LASTEXITCODE -ne 0) { throw 'Semilla interrumpida. Revisar el error y el resumen antes de repetir.' }
```

En Linux/macOS, desde `monpoint-ms`, el mismo comando de Node funciona:

```bash
node --env-file=infra/.env scripts/semilla.mjs
```

`--env-file` carga el archivo sin imprimirlo ni ejecutarlo como código. Las
variables exportadas en la terminal tienen prioridad, tanto para Node como para
Compose. No imprimir tokens, contraseñas ni el contenido de `.env` para depurar.
Para estas variables usar valores literales; Node no expande referencias como
`${OTRA_VARIABLE}` dentro del archivo.

| Variable | Por omisión | Uso |
|---|---|---|
| `SEMILLA_GATEWAY_URL` | `http://localhost:8080` | Origen del Gateway; no una ruta ni una URL con credenciales |
| `ADMIN_DEFAULT_PASSWORD` | `Admin123!` | Login del ADMIN; misma variable que DataInitializer y Compose |
| `SEMILLA_VENDEDOR_PASSWORD` | `Vendedor123!` | Alta y verificación del VENDEDOR de prueba |

Las claves por omisión son de desarrollo. Definir las elegidas en `infra/.env`
antes de inicializar una base nueva. **Cambiar ADMIN_DEFAULT_PASSWORD después
no cambia la contraseña de una cuenta existente.** Tampoco el guion cambia la
del vendedor existente: si no coincide, falla antes de crear catálogos.
`ADMIN_DEFAULT_PASSWORD` ausente o vacía usa `Admin123!` en Compose,
DataInitializer y este guion. No se recortan espacios de las contraseñas.
`SEMILLA_VENDEDOR_PASSWORD` vacía se rechaza por la política del vendedor.
Cada petición tiene un plazo de 10 segundos.

Compose ahora pasa `ADMIN_DEFAULT_PASSWORD` al contenedor ms-auth. Para aplicar
el cambio de configuración a un contenedor anterior se usa `docker compose up
-d ms-auth` desde `infra`; esto tampoco rota la contraseña guardada.

## Repetición, fallos y alcance de la idempotencia

Al terminar se imprime JSON con `COMPLETADA`, los ids, las acciones `CREADO` o
`REUTILIZADO` y `ventasCreadas: 0`. Una segunda ejecución serial debe mostrar
todo reutilizado con los mismos ids. **No se repone stock consumido**: el valor
10 solo se manda en un alta nueva. No hay PUT, PATCH ni DELETE en el guion.

Una coincidencia con campos de catálogo distintos o más de un registro con el
mismo nombre produce error antes de empezar las altas. No se corrigen datos
ajenos ni se sobrescribe una edición hecha a mano.
Los precios se comparan por su valor decimal: `41.15`, `"41.1500"` y
`"4.115e1"` coinciden. Las cadenas no se convierten a doble ni se redondean;
`"41.150000000000000001"` sigue siendo un conflicto.

Un fallo imprime `INTERRUMPIDA`, la operación y el resumen de las respuestas
confirmadas, y termina con código 1. La ejecución no es una transacción global:
pueden quedar altas anteriores al fallo. Un POST sin respuesta, con JSON
incompleto o error 5xx **puede haber creado el recurso**; no se reintenta
automáticamente. El resumen no afirma que ese recurso esté ausente. Esperar
a que se resuelva la petición en el servidor, revisar el listado y después
volver a ejecutar: el guion redescubre lo creado mediante la API, sin un archivo
local de estado. La salida JSON sirve como evidencia, no como fuente de identidad.

Condiciones para repetir sin duplicados:

- Ejecutar **una sola instancia** del guion y conservar los nombres de semilla.
  Productos y clientes no tienen un índice único por nombre: una carrera entre
  dos ejecuciones no puede resolverse con buscar-antes-de-crear.
- Conservar activos los productos y usuarios de prueba. Sus listados solo
  muestran activos. Un producto dado de baja quedaría invisible y podría
  crearse de nuevo con otro id. Un usuario inactivo conserva el correo único y
  el alta responderá 409; el guion se detiene, no lo reactiva.
- No borrar ni renombrar los registros esperando que el guion reconozca su
  identidad anterior. Tampoco recupera réplicas borradas o publicaciones perdidas
  al reutilizar un recurso: no vuelve a publicar sus eventos.

Una base nueva tampoco garantiza por sí sola la recepción de todos los eventos:
los publicadores actuales no tienen outbox. La disponibilidad de las réplicas
deberá verificarse al registrar las ventas de la fase 2.

## Fase 2: tres ventas confirmadas (`--ventas`)

Desde `monpoint-ms`, después de la fase 1:

```powershell
node --env-file=infra/.env scripts/semilla.mjs --ventas
if ($LASTEXITCODE -ne 0) { throw 'Fase 2 interrumpida. Conservar el resumen y verificar las ventas antes de repetir cualquier operación.' }
```

Esta opción ejecuta **solo la fase 2**. Descubre los ids por los nombres estables
de la fase 1 y no crea ni repone usuarios, productos o clientes. Las tres ventas
son secuenciales: no envía la siguiente hasta confirmar la anterior.

| Venta | Cliente | Producto y cantidad | Total previsto con el catálogo de la semilla |
|---|---|---|---|
| 1 | Ana | Tinta × 5 | 205.75 |
| 2 | Luis | Papel × 2 | 151.00 |
| 3 | Sin cliente | Sello × 1 | 120.00 |

El total guardado en el resumen procede del servidor. En una base nueva las
existencias quedan en 5, 8 y 9. La primera reserva alcanza el umbral 5 y el
publicador de inventario emite `StockBajo`.

### Preparación y comprobaciones antes de vender

Levantar los **seis servicios**, con ms-reportes y ms-notificaciones sanos y
sus colas declaradas **antes de ejecutar la fase 1**. Un exchange durable no
conserva eventos sin cola: arrancar reportes después de crear el catálogo no
reconstruye sus proyecciones. La fase 2 tampoco las repara.

El guion autentica al ADMIN y comprueba su perfil, consulta notificaciones y
una agregación vacía del día 2000-01-01 como comprobación de disponibilidad HTTP.
Después lee productos y clientes y valida todo el lote antes del primer POST:
identidades no ambiguas, datos de catálogo coincidentes, recursos presentes y
activos, y **existencia inicial 10 en los tres productos**. Si una existencia ya
cambió, se detiene. No repone stock ni interpreta esa diferencia como una venta
conocida. Esta fase está pensada para datos nuevos y una sola ejecución controlada.

Los GET de comprobación pueden esperar ante red, timeout y 5xx. Cada comprobación
tiene un plazo acotado. Un 401, 403, 404 u otro rechazo no transitorio detiene el
guion. Que ambas APIs respondan **no demuestra** que sus colas estén enlazadas
ni garantiza que vayan a recibir cada evento: esa preparación se comprueba en
Compose/RabbitMQ. La semilla usa únicamente las APIs del Gateway, sin credenciales
de MongoDB ni RabbitMQ.

### Espera de réplicas y resolución de la saga

Se intenta la venta real con `POST /ventas`. **Solo** se repite un HTTP 400 cuyo
`detail` sea exactamente uno de estos dos rechazos de `VentaService`:

- `El producto no está disponible o está inactivo: <productoId>`, con uno de los
  ids de la solicitud actual.
- `el cliente no está disponible; si acaba de darlo de alta, inténtelo de nuevo en unos segundos`,
  únicamente cuando esa solicitud incluye cliente.

Este reconocimiento está acoplado a la redacción exacta de `ProblemDetail.detail`:
hasta disponer de un código de error estable, cambiar esos textos exige actualizar
el guion y sus pruebas; de lo contrario, un rechazo por réplica pendiente pasa a
tratarse como un HTTP 400 no reintentable.

Ambas validaciones ocurren antes de asignar el folio y de insertar la venta.
Otro 400, un 5xx, un fallo de red, un timeout o un 202 incompleto **no** provocan
otro POST. Un producto inactivo y una réplica que aún no llegó comparten el primer
mensaje; el plazo impide reintentar indefinidamente.

Un 202 completo solo deja la venta **PENDIENTE**. Se conserva su id y folio y se
consulta `GET /ventas/{id}` hasta CONFIRMADA o CANCELADA. Los fallos transitorios
del GET permiten consultar otra vez, siempre sin reenviar el POST. CANCELADA
interrumpe el lote, conserva su identidad y pide consultar el motivo por API.
Una respuesta de identidad discordante o estado desconocido también lo interrumpe.

`SEMILLA_VENTAS_PLAZO_MS` vale **30000** por omisión (entero de 1 a 300000 ms).
Cada venta tiene **un único plazo**, compartido entre reintentos por réplica y
sondeo de la saga: recibir el 202 no reinicia el reloj. Cada petición se limita
además a 10 segundos o al tiempo restante, lo que sea menor. Las consultas y los
reintentos se programan tras terminar la petición anterior, con pausas de 500 ms;
no hay peticiones solapadas del guion.

### Resumen parcial y repetición

Éxito significa **tres ventas CONFIRMADAS**, no tres respuestas 202. La salida
JSON incluye `fase: 2`, `estado`, `ventasPrevistas`, `ventasConfirmadas` (ids,
folios, clientes, líneas y totales del servidor), `ventaEnCurso` y
`rechazosReplica` (cantidad de los dos rechazos conocidos que se observaron).

Ante un fallo, termina con código 1 y conserva esa misma estructura dentro de
`resumen`, además de la última operación. `ventaEnCurso` delimita la frontera:

| Estado conocido | Qué hacer |
|---|---|
| `NO_ENVIADA` o `RECHAZADA_HTTP` | No hay aceptación conocida para esa solicitud. Revisar la causa; las anteriores pueden estar confirmadas. |
| `PENDIENTE` | Consultar el id guardado; el plazo del guion no cancela la saga. |
| `CANCELADA` | Consultar el motivo; no se continúa automáticamente con otra venta. |
| `INCIERTA` | El POST pudo haberse persistido. No repetirlo ni volver a ejecutar el lote. Conservar cualquier id/folio recuperable. |
| `CONFIRMADA` en `ventaEnCurso` | El servidor informó confirmación, pero devolvió datos incompletos o discordantes; verificar esa venta. |

**Las ventas no son idempotentes.** No hay clave de idempotencia, referencia
externa ni listado de ventas en la API. El guion no puede recuperar un POST sin
identidad ni reconocer con certeza una ejecución anterior. La comprobación de
existencia inicial detecta una repetición normal, pero no protege de ejecuciones
concurrentes, reposiciones manuales ni una saga que todavía no descontó. No usa
un archivo local como falsa garantía de recuperación. Después de una interrupción,
conservar el resumen, consultar los ids conocidos y resolver manualmente los
resultados inciertos; para otra demostración usar datos nuevos en un entorno
separado, sin borrar evidencia ni reponer stock para forzar la ejecución.

`COMPLETADA` acredita las confirmaciones de ventas. **No acredita por sí sola**
la proyección de reportes ni la alerta: los publicadores sin outbox aún pueden
perder eventos entre commit y envío. La prueba real de esta fase comprueba
ambas recepciones por separado; el recorrido de siete escenarios de 6.1 y los
controles conservan sus propios criterios.

## Verificación

```bash
node --test scripts/semilla.test.mjs scripts/semilla-ventas.test.mjs
```

Las pruebas HTTP locales cubren ambas fases: primera carga y repetición del
catálogo, credenciales, conflictos, los dos rechazos reintentables, 202 pendiente,
cancelación, POST incierto sin repetición, conservación de folios, errores del
GET y el plazo compartido entre réplicas y saga. Son distintas de la comprobación contra el conjunto
real; sus resultados se documentan por separado en `docs/aceptacion/` de la raíz.

[Prueba real de la fase 2 sobre volúmenes nuevos](../../docs/aceptacion/3.1-fase2-2026-11-11/RESULTADOS.md):
tres confirmadas, agregación 476.75 y aviso de tinta con existencia 5. No declara
cerrados el recorrido 6.1 ni los controles.
