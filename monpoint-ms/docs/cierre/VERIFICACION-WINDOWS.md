# Antes de entregar: operaciones pendientes en Windows

Anfitrión acordado: el equipo Windows de entrega. Partir de la raíz del clon.
Estos pasos son un procedimiento,
**no una constancia de ejecución**. No se rotaron claves ni se cambiaron los datos
de empresa de ese equipo desde la nube. No publicar `.env`, tokens, cuerpos de
login ni exportaciones de Postman con valores privados.

## 1. Preparar y verificar acceso nominal

Aplicar primero los cambios revisados de limpieza. Conservar los volúmenes.
Desde `monpoint-ms`, comprobar `docker compose --project-directory infra config
--quiet` y la salud con `docker compose --project-directory infra ps`.
Verificar que no haya una segunda instancia de auth o Gateway desde el IDE.

Iniciar sesión con una cuenta nominal ADMIN y comprobar el perfil en Usuarios.
El ADMIN genérico debe estar desactivado: comprobarlo con el listado de cuentas
activas y un intento de login. La desactivación indicada en la conversación no
se presenta aquí como una consulta independiente a la base de Windows.

## 2. Rotar JWT_SECRET en ese anfitrión

La clave es propia de cada entorno; no distribuir la de la demostración a los
otros equipos. Antes de rotar, conservar **solo en memoria o en la copia privada
de Postman** un token anterior que aún permita consultar inventario. Comprobar
que su `exp` no vencerá durante la prueba; de lo contrario un 401 posterior no
permitiría atribuir el rechazo a la rotación.

Usar un editor privado para cambiar solo `JWT_SECRET` de `infra/.env` por una
clave nueva aleatoria de al menos 32 bytes. No reutilizar el marcador de
`.env.example`, no compartir la clave y no cifrar el archivo como parte de este
procedimiento: el cifrado quedó pendiente de una decisión separada.

Si se prefiere generar y guardar la clave sin imprimirla, este bloque de
PowerShell actualiza únicamente una línea JWT_SECRET existente. Leerlo antes de
ejecutarlo. Rechaza un override exportado en la consola, que prevalecería sobre
el archivo. Ejecutar desde `monpoint-ms`:

```powershell
& {
    $ErrorActionPreference = 'Stop'
    if (Test-Path Env:JWT_SECRET) { throw 'JWT_SECRET exportado: resolver ese override antes de rotar.' }
    $rutaEntorno = (Resolve-Path -LiteralPath 'infra/.env').Path
    $textoEntorno = [IO.File]::ReadAllText($rutaEntorno)
    $patronClave = '(?m)^[\t ]*JWT_SECRET[\t ]*=[^\r\n]*'
    if ([regex]::Matches($textoEntorno, $patronClave).Count -ne 1) {
        throw 'Se requiere exactamente una linea JWT_SECRET; revisar duplicados y codificacion en privado (p. ej., UTF-16 sin BOM).'
    }
    $bytesClave = New-Object byte[] 32
    $generadorClave = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generadorClave.GetBytes($bytesClave) } finally { $generadorClave.Dispose() }
    $claveNueva = [Convert]::ToBase64String($bytesClave)
    $textoNuevo = [regex]::Replace($textoEntorno, $patronClave, ('JWT_SECRET=' + $claveNueva))
    $rutaTemporal = Join-Path ([IO.Path]::GetDirectoryName($rutaEntorno)) ('.env.jwt-' + [guid]::NewGuid().ToString('N'))
    try {
        [IO.File]::WriteAllText($rutaTemporal, $textoNuevo, (New-Object Text.UTF8Encoding($false)))
        if ([IO.File]::ReadAllText($rutaTemporal) -cne $textoNuevo) { throw 'No se pudo verificar el archivo temporal.' }
        [IO.File]::Replace($rutaTemporal, $rutaEntorno, $null)
    } finally {
        if (Test-Path -LiteralPath $rutaTemporal) { Remove-Item -LiteralPath $rutaTemporal }
    }
    Write-Host 'JWT_SECRET actualizado en el archivo privado; falta recrear ambos validadores.'
}
docker compose --project-directory infra config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose invalido; no continuar.' }
docker compose --project-directory infra up -d --no-deps --force-recreate ms-auth api-gateway
if ($LASTEXITCODE -ne 0) { throw 'No se pudo completar la recreacion; revisar ambos servicios.' }
docker compose --project-directory infra ps
```

La clave queda en el `.env` privado y temporalmente en la memoria de PowerShell;
esto evita imprimirla, **no** es un sistema de custodia cifrada ni garantiza
borrado seguro de memoria. El bloque escribe y comprueba un temporal junto al
original antes de reemplazarlo, para no truncar el `.env` si falla la escritura.
Conserva el contenido de las otras líneas, pero usa UTF-8 sin BOM. Requiere un
sistema de archivos Windows que admita File.Replace. No ejecutar `down -v`.

ReadAllText detecta un BOM y, si no existe, interpreta UTF-8. Un `.env` en
UTF-16 sin BOM puede contener caracteres NUL que impidan reconocer la línea;
en ese caso se detiene antes de modificar el original. Revisar la codificación
con un editor privado y guardar como UTF-8 antes de reintentar, sin imprimir el
archivo ni convertirlo a ciegas. Una coincidencia ausente no demuestra por sí
sola que falte la variable.

Esperar a que ambos servicios estén saludables y registrados. El Gateway puede
conservar temporalmente destinos de Eureka anteriores; un 503 no prueba rechazo
de firma. Repetir la **consulta de lectura**, no un cobro, cuando el enrutamiento
se haya recuperado. Comprobar:

| Control | Resultado requerido |
|---|---|
| Token anterior todavía no vencido, GET /inventario/productos por Gateway | 401 |
| Login nominal nuevo y la misma consulta | 200 |
| Login del genérico desactivado | 401 |
| Todas las instancias que validan JWT | Mismo nuevo secreto; sin instancia del IDE con el anterior |

Guardar fecha, revisión, estados HTTP y salud; nunca los JWT ni el secreto.
La rotación solo queda acreditada al aportar esos resultados. La falta de
revocación individual sigue siendo una limitación aunque esta operación pase.

## 3. Alinear empresa y hacerla reproducible

Entrar como ADMIN a **Reportes**, editar datos de empresa y guardar exactamente
«Auto Tachi Refacciones y Accesorios». Conservar RFC, dirección y teléfono
existentes salvo corrección deliberada. La pantalla usa PUT por Gateway; no
editar MongoDB directamente.

Configurar en el `.env` privado `SEMILLA_EMPRESA_HABILITADA=true` y las cuatro
variables de empresa según [la guía de semilla](../../scripts/SEMILLA_AUTOTACHI.md#empresa-reproducible-opcional).
Copiar esos datos, sin claves, a la configuración de prueba de cada compañero.
El importador crea ante 404, reutiliza si coincide y se detiene ante diferencias;
no deshace una edición de empresa ni repone el stock de productos existentes.

Comprobar cabecera y un PDF nuevo. El login mantiene una marca pública estática
con ese texto; el respaldo corto «Auto Tachi» solo se usa sin configuración.
No afirmar que el login siga cambios de la base. Conservar la respuesta pública
de configuración y la captura/PDF de esta ejecución si se incorporan a evidencia.

## 4. Resolver el directorio local no versionado

`docs/aceptacion/windows-20261104-131653/` no existe en el árbol revisado de la
nube ni en `main` de la base de este documento. No se inspeccionó su contenido.
No añadirlo automáticamente ni descartarlo por su nombre.

Decisión para el cierre: mantenerlo fuera del paquete público hasta revisarlo.
En Windows, inventariar archivos y hashes; comparar con los expedientes ya
versionados del 4 y 11 de noviembre. Revisar posibles credenciales antes de
publicar. Si es duplicado, archivarlo en una carpeta privada **fuera del clon**,
comprobar la copia por SHA-256 y retirar la copia local solo después. Si aporta
evidencia única, incorporar lo pertinente con procedencia y límites en una
revisión aparte. Este documento no acredita ninguna de esas acciones.

## 5. Segundo anfitrión antes del jueves 19

Víctor o Hugo debe ejecutar personalmente desde un clon limpio. Este documento
no envía una solicitud ni sustituye su ejecución:

1. Configurar su `.env` desde `.env.example`, con claves **propias**, cuatro
   contraseñas nominales válidas, CORS y la empresa habilitada y completa.
2. Ejecutar `docker compose --project-directory infra up -d --build`. No usar
   `down -v` sobre un entorno con datos; un proyecto/volúmenes nuevos deben ser
   identificables en la evidencia del clon limpio.
3. Esperar consumidores, importar con `node --env-file=infra/.env scripts/semilla-autotachi.mjs`.
   Debe registrar cuatro accesos, 23 productos activos y empresa creada.
4. Repetir: empresa reutilizada, cero productos nuevos, sin reponer stock.
5. Abrir cliente nginx en 3000, probar login, enlace profundo y un cobro de
   prueba con su ticket; verificar salud, descuento y permisos.
6. Conservar revisión, versiones, salida de build y estados/resultados sin
   secretos. Las capturas de otra máquina o los tests simulados no acreditan
   que un compañero haya reproducido el despliegue.

Este segundo anfitrión es la alternativa de demostración frente a falta de
espacio o problemas de WSL en el equipo principal. Comprobar espacio libre y
estado de Docker Desktop en ambos antes del traslado. La reproducibilidad por
otro integrante queda **pendiente** hasta recibir el acta, no se deduce de que
el Compose funcione en la nube.
