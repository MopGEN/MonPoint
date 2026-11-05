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
del vendedor existente: si no coincide, falla antes de crear catálogos. Una
variable definida como cadena vacía se rechaza, no se sustituye por la clave
por omisión. Cada petición tiene un plazo de 10 segundos.

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

## Fase 2 pendiente: ventas

Se implementará después de desplegar **ms-reportes y ms-notificaciones**, con
sus colas y bindings declarados **antes** de vender. Un exchange durable no
guarda eventos sin una cola enlazada. Ventas anteriores no poblarán por sí solas
las futuras proyecciones de reportes ni los avisos de stock bajo. La fase 1
terminada no equivale a acreditar toda la 3.1 ni a marcar CONTROL 1.

La espera de réplica y el alta de venta serán una sola operación: intentar la
venta prevista por `POST /ventas`. Reintentar, con plazo total acotado, solo el
400 conocido de producto no disponible/inactivo mientras se propaga el catálogo;
si se incluye cliente, también el 400 específico de cliente no disponible.
Ambas comprobaciones preceden a la asignación de folio en `VentaService`.
No reintentar indiscriminadamente
otros 400, red, timeout ni 5xx. Al recibir 202 se conserva el id y se consulta
`GET /ventas/{id}`; no se vuelve a emitir el POST.

Con umbral 5 y existencia inicial 10, una reserva de cinco unidades alcanza el
umbral y emite StockBajo. Antes de vender se revisará el stock real: repetir
la fase 1 no lo restaura. Las ventas usarán los ids descubiertos en la fase 1,
no ids escritos de memoria. Si usan cliente, también depende de su réplica.

**Separar las fases no vuelve idempotentes las ventas.** La API actual carece
de clave de idempotencia, referencia externa y listado consultable de ventas.
Un POST cuyo resultado se pierde no se puede repetir con garantía. La futura
fase 2 debe documentarse como ejecución controlada sobre datos nuevos o resolver
antes esa limitación de API; un archivo local no garantiza recuperación si se
pierde. Este guion no ofrece todavía una opción de fase 2.

## Verificación

```bash
node --test scripts/semilla.test.mjs
```

Las pruebas HTTP locales cubren primera carga, repetición, credenciales, conflicto
de datos, listados inaccesibles, recuperación tras respuesta perdida y timeout
sin repetición automática. Son distintas de la comprobación contra el conjunto
real; sus resultados se documentan por separado en `docs/aceptacion/` de la raíz.
