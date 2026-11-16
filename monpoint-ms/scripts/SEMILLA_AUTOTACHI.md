# Semilla nominal y catálogo Auto Tachi

La semilla tiene dos pasos: `ms-auth` añade las cuentas al arrancar y
`semilla-autotachi.mjs` importa el catálogo por el Gateway. No hay panel de
credenciales en el cliente ni contraseñas por omisión en el inicializador.

| Nombre | Correo de acceso | Rol inicial | Variable de contraseña |
|---|---|---|---|
| Víctor Ortiz | `victor.ortiz@autotachi.mx` | ADMIN | `AUTOTACHI_VICTOR_PASSWORD` |
| Alejandro Bermúdez | `alejandro.bermudez@autotachi.mx` | ADMIN | `AUTOTACHI_ALEJANDRO_PASSWORD` |
| Hugo Trejo | `hugo.trejo@autotachi.mx` | ADMIN | `AUTOTACHI_HUGO_PASSWORD` |
| Vendedor | `vendedor@autotachi.mx` | VENDEDOR | `AUTOTACHI_VENDEDOR_PASSWORD` |

Nombres y dominio proceden del prototipo. Por acuerdo del equipo, los tres
nominales son ADMIN, aunque el prototipo daba VENDEDOR a Alejandro y Hugo.
Los correos son identificadores de login: no se envía correo ni se acredita
propiedad de esos buzones. La cuarta cuenta es compartida; sus ventas no
permiten distinguir qué persona la utilizó.

## Preparación en Windows

Conservar el `.env` actual y sus demás valores. Añadir estas cinco variables
**al archivo privado** `monpoint-ms/infra/.env` usando la plantilla
[`.env.example`](../infra/.env.example):

- `SEMILLA_NOMINAL_HABILITADA=true`
- Las cuatro `AUTOTACHI_*_PASSWORD` de la tabla, cada una con su clave elegida.

Sustituir los marcadores `REEMPLAZAR`; son deliberadamente inválidos. La
política la aplica el mismo `PasswordValidator` de registro: mínimo 8 caracteres,
máximo 72 bytes UTF-8, mayúscula, minúscula, dígito y uno de `@$!%*?&`.
No se recortan espacios. Para claves con `$` usar comillas simples en `.env`
para evitar interpolación de Compose; Node también reconoce esas comillas.
Las variables ya exportadas en PowerShell prevalecen sobre el archivo tanto
en Compose como en Node. No imprimir `.env`, tokens ni claves al diagnosticar.

Desde PowerShell:

```powershell
Set-Location C:\Users\osber\MonPoint\monpoint-ms
# Conserva los volúmenes; reconstruye ms-auth con las nuevas variables.
docker compose --project-directory infra up -d --build ms-auth
if ($LASTEXITCODE -ne 0) { throw 'No se pudo actualizar ms-auth.' }
docker compose --project-directory infra ps
```

Levantar el resto de servicios si están apagados, y esperar a que estén sanos
**antes de importar**. Reportes y ventas deben tener sus consumidores y colas
listos antes de las altas: reutilizar un producto no vuelve a publicar su evento.
No se necesita reconstruir el cliente por este cambio.

Con Node.js 22.9 o posterior, sin `npm install`:

```powershell
node --env-file=infra/.env scripts/semilla-autotachi.mjs
if ($LASTEXITCODE -ne 0) { throw 'Importacion interrumpida. Conservar el resumen y revisar antes de repetir.' }
```

Por omisión el Gateway es `http://localhost:8080`; `SEMILLA_GATEWAY_URL` admite
otro origen HTTP(S), sin credenciales, ruta, query ni fragmento.
En Linux/macOS se usan los mismos comandos desde `monpoint-ms`.

## Qué cambia y qué conserva

`DataInitializer` no crea ya `admin@monpoint.com` ni usa
`ADMIN_DEFAULT_PASSWORD`. La bandera nominal vale `false` cuando no se configura:
una instalación existente puede arrancar sin claves nuevas y conserva sus cuentas;
una base vacía se detiene con instrucciones de configuración. Al habilitarla,
se buscan los cuatro correos **incluyendo inactivos**, se validan todas las
claves de cuentas ausentes antes de guardar la primera y se crean solo las que
faltan. Las claves de cuentas ya existentes no se exigen para ese arranque.

No se cambian nombres, roles, contraseñas ni bajas existentes. Cambiar una clave
en `.env` no la rota en MongoDB. Si se desactiva o cambia el rol de una cuenta,
reiniciar no revierte esa decisión. Un fallo de persistencia puede dejar una
parte creada; el siguiente arranque completa los correos ausentes. El índice
único de correo resuelve altas concurrentes; no hay transacción de todo el lote.

El importador comprueba login y perfil de las cuatro cuentas, incluidos sus
roles y nombres, antes de escribir productos. Si alguna fue editada o desactivada,
se detiene: no la corrige. Para volver a importar se necesita resolver esa
diferencia conscientemente; no es un mecanismo de recuperación de cuentas.

[`catalogo-autotachi.json`](catalogo-autotachi.json) conserva las 24 entradas
originales de `semillaProductos()` del prototipo entregado en `index.html`.
Se importan **23 activos**, con nombre, precio, existencia y umbral originales;
`ACC-023` tiene existencia 0. Se excluye `ACC-024`, inactivo en origen.
Las referencias `REF-…`, `LIQ-…`, `HER-…`, `ACC-…` son trazabilidad del archivo:
no se envían como SKU ni como descripción, porque el contrato no tiene código.
La descripción se envía como `null` y los precios de centavos se convierten a
número JSON (todos los de este catálogo son importes enteros en pesos).

Antes del primer POST se comprueba todo el catálogo existente por nombre exacto.
Un precio, descripción o umbral diferente o nombres duplicados producen conflicto.
La existencia se excluye de esa comparación: al repetir no se repone stock
consumido ni se deshacen ajustes. Solo se envía POST para productos ausentes;
no hay PUT, PATCH, DELETE, registro de usuarios por API ni creación de ventas.

Las cuentas y productos genéricos anteriores **permanecen**: no se ejecuta
`down -v`, no se borran ventas y no se reinician folios. Una base nueva tiene
cuatro cuentas y 23 productos; una base histórica puede tener más. Los
expedientes de 3.1, 6.1 y F12 describen sus ejecuciones anteriores y conservan
validez histórica; no describen el estado posterior a esta importación. No se
reescriben sus números ni manifiestos.

## Repetición y límites

Ejecutar una sola instancia del importador. No hay índice único por nombre de
producto: dos ejecuciones concurrentes pueden duplicarlo. El listado muestra
solo activos; si se da de baja o renombra un producto importado, ya no puede
redescubrirse por ese nombre y otra ejecución podría crearlo de nuevo.
El importador tampoco repara eventos perdidos ni réplicas borradas.

Cada petición tiene 10 segundos de plazo, sin reintentos. Un timeout, 5xx o JSON
incompleto tras un POST puede significar alta ya persistida. La salida marca
`INTERRUMPIDA` y conserva solo respuestas verificadas; no afirma que lo demás
esté ausente. Esperar a que el servidor termine, revisar el listado y resolver
antes de repetir. Un 201 con datos discordantes también requiere esa revisión.

`COMPLETADA` acredita los cuatro accesos y las respuestas de catálogo verificadas.
No acredita ventas, tickets, proyecciones de reportes ni alertas. Crear un
producto con stock bajo no emite por sí mismo `StockBajo`; esa emisión sucede
al reservar existencias. No se fabrican notificaciones para imitar el prototipo.

## Pruebas

```bash
node --test scripts/semilla-autotachi.test.mjs scripts/semilla.test.mjs scripts/semilla-ventas.test.mjs
```

Los dobles HTTP verifican repetición, conflictos antes de escribir, claves,
identidades, cuerpos enviados y fallos parciales sin reenvío. Para la suite Java
completa se requiere MongoDB aislado, las cuatro claves en el entorno y
`SEMILLA_NOMINAL_HABILITADA=true`; desde `ms-auth`: `./mvnw test`.
No ejecutar la suite integral contra la base de demostración.
