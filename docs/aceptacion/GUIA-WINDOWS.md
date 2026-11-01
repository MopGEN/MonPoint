# Recorrido de aceptación en Windows: caja y nginx

Esta guía prueba el cliente empaquetado de la **5.6** y repite los tres desenlaces
de caja contra los servicios reales en tu equipo. Reserva unos 20–30 minutos,
además de la primera descarga/compilación de imágenes.

Usa Windows PowerShell 5.1 o PowerShell 7, Git, Docker Desktop con **contenedores
Linux** y Chrome o Edge. No necesitas instalar Node para este recorrido: Docker
compila y nginx sirve el cliente. No ejecutes `npm run dev`.

Mantén abierta la misma terminal durante toda la prueba: conservará el producto
y las credenciales temporales usados por los comandos. Usa datos de desarrollo;
el recorrido crea ventas reales en tu base local y pausa inventario brevemente.

## 1. Abrir el proyecto y elegir la revisión

Abre Docker Desktop y espera a que el motor esté listo. En PowerShell:

```powershell
$ErrorActionPreference = 'Stop'
$repo = 'C:\Users\osber\MonPoint'
Set-Location $repo
git status --short
git fetch origin
if ($LASTEXITCODE -ne 0) { throw 'No se pudo actualizar la referencia remota.' }
```

Si `git status` muestra cambios tuyos, guárdalos antes de cambiar de rama. No los
borres ni fuerces el cambio. Escoge **una** de las siguientes alternativas:

**Si la 5.6 todavía no está fusionada**, prueba la rama subida para revisión:

```powershell
git switch infra/5.6-cliente-nginx
if ($LASTEXITCODE -ne 0) { throw 'No se pudo cambiar a la rama de prueba.' }
git pull --ff-only origin infra/5.6-cliente-nginx
if ($LASTEXITCODE -ne 0) { throw 'No se pudo actualizar la rama; revisar antes de continuar.' }
```

Git crea el seguimiento de la rama remota si aún no existe localmente. Si informa
que no encuentra la rama, comprueba `git branch -r`: debe figurar
`origin/infra/5.6-cliente-nginx`.

**Si la 5.6 ya está fusionada**, prueba `main`:

```powershell
git switch main
if ($LASTEXITCODE -ne 0) { throw 'No se pudo cambiar a main.' }
git pull --ff-only origin main
if ($LASTEXITCODE -ne 0) { throw 'No se pudo actualizar main; revisar antes de continuar.' }
```

Comprueba y registra la revisión:

```powershell
git branch --show-current
git log -1 --oneline
Test-Path .\monpoint-ms\cliente-web\Dockerfile
$carpetaEvidencia = Join-Path $repo ('docs\aceptacion\windows-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $carpetaEvidencia | Out-Null
git rev-parse HEAD | Set-Content (Join-Path $carpetaEvidencia 'revision.txt')
```

`Test-Path` debe devolver `True`. La carpeta de evidencia no se sube automáticamente.

## 2. Revisar la configuración local

```powershell
Set-Location (Join-Path $repo 'monpoint-ms\infra')
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

Conserva las claves y contraseñas que ya funcionan en tu entorno. Comprueba estas
**dos líneas**, sin duplicarlas:

```ini
CORS_ORIGENES=http://localhost:5173,http://localhost:3000
VITE_GATEWAY_URL=http://localhost:8080
```

Si tu lista CORS tiene otros orígenes, consérvalos y añade
`http://localhost:3000`. Usa siempre `localhost` durante este recorrido:
`http://127.0.0.1:3000` es otro origen.

Si acabas de crear `.env`, completa también las variables descritas en
`infra/.env.example`, incluidas las claves de MongoDB, RabbitMQ, JWT y ventas.
Si ya tienes datos, no cambies las contraseñas de MongoDB esperando que los
usuarios existentes se actualicen: los guiones de inicialización solo corren
con un volumen vacío.

## 3. Levantar y comprobar el conjunto

Desde `monpoint-ms\infra`:

```powershell
docker version
docker compose config --services
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { throw 'El arranque fallo; revisar la salida antes de continuar.' }
docker compose ps --format "{{.Name}}  {{.Status}}"
```

Con la rama 5.6 actual deben aparecer **nueve contenedores en healthy**:

- monpoint-mongodb, monpoint-rabbitmq, monpoint-eureka y monpoint-gateway.
- monpoint-auth, monpoint-inventario, monpoint-clientes y monpoint-ventas.
- monpoint-cliente.

`starting` es transitorio; vuelve a ejecutar `docker compose ps` después de unos
segundos. Abre `http://localhost:8761`: deben registrarse API-GATEWAY, MS-AUTH,
MS-INVENTARIO, MS-CLIENTES y MS-VENTAS con estado UP. Eureka puede tardar más que
los healthchecks.

```powershell
curl.exe -f http://localhost:3000/healthz
docker compose exec cliente-web nginx -t
```

Esperado: `ok` y configuración nginx válida. Esto comprueba el servidor web;
el siguiente paso comprueba su comunicación real con la API.

No uses `docker compose down -v`: borraría los datos y no es parte de la prueba.

## 4. Entrar desde el navegador y probar una recarga

1. Abre **http://localhost:3000/caja**. Debe aparecer el inicio de sesión, sin 404
   de nginx. La dirección puede cambiar a `/login?regresarA=/caja`.
2. Pulsa F12. Abre **Network/Red**, selecciona Fetch/XHR y activa **Preserve log /
   Conservar registro**. Abre también Console/Consola para detectar errores CORS.
3. Inicia sesión con tu administrador local. En una base inicializada con los
   valores por defecto es `admin@monpoint.com` / `Admin123!`; si cambiaste la
   contraseña, usa la tuya. El semillero no restablece un usuario ya existente.
4. Debe abrirse Caja y cargarse productos y clientes. En Red, `/auth/login`,
   `/inventario/productos` y `/clientes` deben responder 200 desde `:8080`.
5. **Ahora, con el carrito vacío y antes de crear ventas**, pulsa F5 en `/caja`.
   Debe volver al login, sin 404 de nginx. Inicia sesión nuevamente.

Perder la sesión al recargar es esperado: el token vive en memoria. **No recargues
la página durante los casos siguientes**, especialmente mientras haya una venta
pendiente; la bandeja también vive en memoria.

Comprueba que los archivos JavaScript se cargan desde `/assets/`, sin
`/@vite/client` ni `/src/`. Estás probando nginx, no el servidor de desarrollo.

## 5. Crear un producto exclusivo para el recorrido

La caja actual consulta el catálogo pero no permite dar de alta productos.
Créalo una vez mediante el Gateway desde la **misma PowerShell**. Este login es
independiente del login del navegador; no sustituye la comprobación CORS anterior.

```powershell
$gateway = 'http://localhost:8080'
$credencial = Get-Credential -UserName 'admin@monpoint.com' -Message 'Administrador de MonPoint local'
$cuerpoAcceso = @{
    correo = $credencial.UserName
    password = $credencial.GetNetworkCredential().Password
} | ConvertTo-Json
$acceso = Invoke-RestMethod -Method Post -Uri "$gateway/auth/login" -ContentType 'application/json; charset=utf-8' -Body $cuerpoAcceso
$cabeceras = @{ Authorization = "Bearer $($acceso.token)" }
Remove-Variable credencial, cuerpoAcceso, acceso

$nombrePrueba = 'Aceptacion Windows ' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$cuerpoProducto = @{
    nombre = $nombrePrueba
    descripcion = 'Producto exclusivo del recorrido local de caja'
    precio = 41.15
    existencia = 10
    umbralStock = 0
} | ConvertTo-Json
$producto = Invoke-RestMethod -Method Post -Uri "$gateway/inventario/productos" -Headers $cabeceras -ContentType 'application/json; charset=utf-8' -Body $cuerpoProducto
$producto | Select-Object id, nombre, precio, existencia, umbralStock
$producto | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 (Join-Path $carpetaEvidencia 'producto-inicial.json')
```

Esperado: un `id`, precio **41.15** y existencia **10**. Conserva esta terminal;
los comandos siguientes reutilizan `$producto` y `$cabeceras`. No imprimas ni
captures el token o las cabeceras de autorización.

Espera unos segundos a que el producto se replique en ventas. En el navegador,
pulsa **Actualizar catálogo**, busca el nombre que acabas de crear y deja la
opción **Venta sin cliente**. Si repites el recorrido otro día, crea otro producto:
no reutilices uno al que ya se le descontaron unidades.

## 6. Desenlace confirmado

1. Pulsa **Agregar** junto al producto de prueba.
2. Escribe cantidad **3**. El estimado debe ser **$123.45**.
3. Limpia el registro de Red para identificar las peticiones de esta operación.
4. Pulsa **Cobrar**. Debe pasar por **Venta en proceso**, con folio, y terminar en
   **Venta confirmada**. El estado intermedio puede durar alrededor de un segundo.
5. Comprueba el total final **$123.45** y el carrito vacío.
6. Guarda una captura como `01-confirmada.png` en la carpeta de evidencia.

En Red, abre **Response/Respuesta** de `POST /ventas`: debe haber devuelto 202
con `id` y `folio`. Después, los `GET /ventas/{id}` deben mostrar CONFIRMADA.
Un 202 por sí solo no significa que la venta esté confirmada.

Guarda el detalle y comprueba inventario desde PowerShell:

```powershell
$idConfirmada = Read-Host 'Pega el id de la venta confirmada (no el folio)'
Invoke-RestMethod -Uri "$gateway/ventas/$idConfirmada" -Headers $cabeceras | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 (Join-Path $carpetaEvidencia '01-confirmada.json')
Invoke-RestMethod -Uri "$gateway/inventario/productos/$($producto.id)" -Headers $cabeceras | Select-Object id, nombre, existencia
```

Esperado: existencia **7**. La cifra del catálogo puede estar desactualizada hasta
pulsar Actualizar catálogo; no la confundas con la consulta actual al servidor.

## 7. Desenlace cancelado

1. Pulsa **Nueva venta** y agrega el mismo producto.
2. Escribe cantidad **20**, mayor que las siete unidades disponibles.
3. Limpia Red y pulsa **Cobrar**.
4. Espera **Venta cancelada**: motivo de existencia insuficiente en español,
   carrito conservado con cantidad 20 editable y línea del producto resaltada.
5. Guarda `02-cancelada.png`.

En la respuesta final de `GET /ventas/{id}`, comprueba `estado: CANCELADA` y que
`productoIdRechazado` coincida con `$producto.id` de la terminal.

```powershell
$idCancelada = Read-Host 'Pega el id de la venta cancelada'
Invoke-RestMethod -Uri "$gateway/ventas/$idCancelada" -Headers $cabeceras | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 (Join-Path $carpetaEvidencia '02-cancelada.json')
Invoke-RestMethod -Uri "$gateway/inventario/productos/$($producto.id)" -Headers $cabeceras | Select-Object id, nombre, existencia
```

Esperado: existencia **7**, sin descuento por la venta cancelada.

## 8. Sin desenlace a los diez segundos

Primero detén **solo inventario**, antes de cobrar:

```powershell
Set-Location (Join-Path $repo 'monpoint-ms\infra')
docker compose stop ms-inventario
docker compose ps -a ms-inventario
```

Debe aparecer detenido. Mantén Gateway, ventas, MongoDB y RabbitMQ encendidos.

1. En el carrito conservado, cambia la cantidad de **20 a 2**.
2. No actualices el catálogo: inventario está detenido y el producto ya está cargado.
3. Limpia Red y pulsa **Cobrar una sola vez**.
4. El POST debe devolver **202 con id y folio**. Anótalos.
5. Pasados unos **10 segundos desde la aceptación**, debe aparecer
   **Seguimiento de operaciones**, con ese folio y resultado sin confirmar.
6. La caja queda disponible y el carrito vacío. Puedes agregar un producto para
   comprobarlo, pero no hagas otro cobro durante este recorrido.
7. Guarda `03-sin-respuesta.png` antes de recuperar inventario.

Mientras inventario sigue detenido, guarda la venta pendiente:

```powershell
$idPendiente = Read-Host 'Pega el id de la venta pendiente'
Invoke-RestMethod -Uri "$gateway/ventas/$idPendiente" -Headers $cabeceras | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 (Join-Path $carpetaEvidencia '03-pendiente.json')
```

Esperado: **PENDIENTE**, no CANCELADA. No pulses Cobrar de nuevo para repetir esa
operación. Un fallo de comunicación no demuestra que el cobro haya fallado.

## 9. Restaurar inventario y recuperar el mismo folio

Ejecuta esto incluso si algún paso del caso anterior falló:

```powershell
docker compose start ms-inventario
docker compose ps ms-inventario
```

Espera a `healthy` y a que MS-INVENTARIO vuelva a UP en Eureka. La cola pendiente
se procesará; puede haber un breve desfase entre recuperar el servicio y volver
a enrutar hacia él desde el Gateway.

1. En la **misma pestaña**, sin recargar, pulsa **Consultar de nuevo** en la
   operación pendiente.
2. Si aún está pendiente, espera unos segundos y consulta otra vez. No la cobres de nuevo.
3. Debe aparecer **Venta confirmada · Total cobrado: $82.30** bajo el **mismo folio**.
4. Guarda `04-recuperada.png`.

```powershell
Invoke-RestMethod -Uri "$gateway/ventas/$idPendiente" -Headers $cabeceras | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 (Join-Path $carpetaEvidencia '04-recuperada.json')
$productoFinal = Invoke-RestMethod -Uri "$gateway/inventario/productos/$($producto.id)" -Headers $cabeceras
$productoFinal | Select-Object id, nombre, existencia
$productoFinal | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 (Join-Path $carpetaEvidencia 'producto-final.json')
docker compose ps --format "{{.Name}}  {{.Status}}" | Tee-Object -FilePath (Join-Path $carpetaEvidencia 'contenedores-final.txt')
```

Esperado: existencia **5** y los nueve contenedores saludables. Si la consulta a
inventario devuelve 503 durante su recuperación, espera a su registro y repite
**solo la consulta GET**, nunca el POST de venta.

## 10. Revisar el resultado y conservar evidencia

| Comprobación | Esperado | Resultado local |
| --- | --- | --- |
| nginx y CORS | Login/catálogo reales desde :3000; sin error CORS | Por completar |
| Subruta | /caja carga y al recargar solicita login, sin 404 | Por completar |
| Confirmada | 3 × 41.15 = 123.45; stock 10 → 7 | Por completar |
| Cancelada | Carrito y motivo visibles; producto señalado; stock 7 | Por completar |
| Sin respuesta | Folio conservado tras unos 10 s; backend PENDIENTE | Por completar |
| Recuperación | Mismo folio; 2 × 41.15 = 82.30; stock 7 → 5 | Por completar |
| Restauración | Inventario y los otros ocho contenedores healthy | Por completar |

Anota los tres folios locales, fecha, revisión Git y resultado de cada fila en
un `resultado.md` dentro de la carpeta de evidencia. Los folios no tienen que
ser 25, 26 y 27: esos pertenecen a la ejecución en nube.

En Red filtra por `/ventas`: para cada acción debe haber un único **POST**; los
OPTIONS de CORS no son cobros. Los GET de sondeo no deben solaparse en la cascada
Waterfall: el siguiente empieza después de terminar el anterior. El registro
puede incluir además tus consultas manuales si las haces desde el navegador.

Para probar también la guarda de doble clic, puedes hacer doble clic rápido
**en lugar del clic del paso 6**, manteniendo el mismo producto y cantidades.
Debe seguir existiendo un único POST. No añadas otro cobro al final para probarlo,
porque cambiaría las existencias esperadas de este guion.

Captura la pantalla o el cuerpo de respuesta, no las cabeceras Authorization ni
la respuesta de login. No adjuntes un HAR completo con credenciales.

```powershell
Remove-Variable cabeceras -ErrorAction SilentlyContinue
Write-Host "Evidencia guardada en: $carpetaEvidencia"
```

Conserva los datos de prueba. Si terminas de usar el conjunto, puedes ejecutar
`docker compose stop`; para continuarlo, `docker compose up -d`. No borres los
volúmenes para limpiar esta prueba.

## Si algo falla

| Síntoma | Comprobación y siguiente paso |
| --- | --- |
| Docker no conecta con el motor | Abrir Docker Desktop y comprobar `docker version`; usar contenedores Linux |
| No existe cliente-web en Compose | Falta la rama/fusión 5.6; revisar el paso 1 |
| Puerto 3000 u 8080 ocupado | Identificar el proceso/contenedor que lo usa y resolver la colisión; cambiar solo el puerto rompería la correspondencia con CORS/URL compilada |
| Error CORS en el navegador | Abrir exactamente localhost:3000; revisar CORS_ORIGENES y ejecutar `docker compose up -d api-gateway`. `restart` no incorpora variables nuevas |
| El cliente llama a otra URL | Corregir VITE_GATEWAY_URL en infra/.env y ejecutar `docker compose up -d --build cliente-web`; nginx no lee esa variable en ejecución |
| Login 401 | Verificar la contraseña del usuario existente; no reinicializar la base para restablecerla |
| Login o catálogo 503 tras arrancar | Esperar registro UP en Eureka; si persiste, revisar logs de Gateway y del servicio afectado |
| Producto visible pero primer cobro rechazado por réplica aún ausente | Esperar propagación del producto; comprobar el 4xx y su motivo antes de corregir/reintentar. No repetir una operación con resultado incierto |
| No se resalta el producto rechazado | Revisar productoIdRechazado en GET y que la imagen ms-ventas incluya #12; reconstruirla si es antigua |
| Se confirmó el caso de espera inmediatamente | Confirmar que ms-inventario estaba detenido antes de ese POST. Esa venta ya descontó stock: para repetir el guion completo usar un producto nuevo |
| Se cerró la pestaña o se perdió la bandeja | No volver a cobrar. Consultar GET /ventas/{id} con el id anotado y verificar existencias |

Para diagnosticar, desde infra:

```powershell
docker compose logs --tail 80 cliente-web api-gateway ms-inventario ms-ventas
```

Este recorrido cubre los tres desenlaces con **POST respondido**. No acredita el
POST colgado sin id/folio, que necesita una prueba separada con fallo de red
controlado. Tampoco cierra por sí solo 6.1: faltan los otros criterios de su
alcance vigente, como reportes/ticket y el arranque desde cero.
