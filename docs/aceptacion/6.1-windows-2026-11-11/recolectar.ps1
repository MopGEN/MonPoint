<#
    Recolecta el estado ACTUAL del conjunto Windows del recorrido 6.1.
    No reconstruye datos historicos ni ejecuta semillas.
    Requiere Docker, Git y el ADMIN. Nunca guarda claves ni tokens.
    Uso: powershell -ExecutionPolicy Bypass -File .\recolectar.ps1
    Cada intento usa entorno/<instante-UTC>/; no sobrescribe los anteriores.
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$evidencia = $PSScriptRoot
$repo = (Resolve-Path (Join-Path $evidencia '..\..\..')).Path
$infra = Join-Path $repo 'monpoint-ms\infra'
$gateway = 'http://localhost:8080'
$inicio = [DateTime]::UtcNow.ToString('o')
$entorno = Join-Path $evidencia ('entorno\' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))
$archivoManifiesto = Join-Path $evidencia 'SHA256.json'
New-Item -ItemType Directory -Path $entorno | Out-Null
# El manifiesto anterior ya no describe la carpeta al iniciar otro intento.
if (Test-Path $archivoManifiesto) { Remove-Item -LiteralPath $archivoManifiesto }

function Guardar($nombre, [string]$contenido) {
    [IO.File]::WriteAllText((Join-Path $entorno $nombre), $contenido, (New-Object Text.UTF8Encoding($false)))
    Write-Host "  $nombre"
}

function Ejecutar([string]$programa, [string[]]$argumentos) {
    $null = Get-Command -Name $programa -CommandType Application -ErrorAction Stop
    # ErrorActionPreference no detecta los codigos de salida nativos en PS 5.1.
    # Capturar stderr como texto sin convertirlo en un error terminante por si
    # Docker escribe alli logs normales. Restaurar la preferencia en todo caso.
    $anterior = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $salida = @(& $programa @argumentos 2>&1)
        $codigo = $LASTEXITCODE
    } finally { $ErrorActionPreference = $anterior }
    if ($codigo -ne 0) {
        # No imprimir argumentos, salida de login ni credenciales en el error.
        throw "Fallo el comando nativo $programa (salida $codigo). Recoleccion incompleta."
    }
    return ($salida | Out-String).TrimEnd()
}

try {
    Write-Host 'Contenedores, colas y revision...'
    Guardar 'contenedores.json' (Ejecutar 'docker' @('compose', '--project-directory', $infra, 'ps', '--format', 'json'))
    Guardar 'colas-finales.txt' (Ejecutar 'docker' @('exec', 'monpoint-rabbitmq', 'rabbitmqctl', 'list_queues', 'name', 'messages', 'consumers'))
    Guardar 'bindings-finales.txt' (Ejecutar 'docker' @('exec', 'monpoint-rabbitmq', 'rabbitmqctl', 'list_bindings', 'source_name', 'routing_key', 'destination_name'))
    Guardar 'revision.txt' (Ejecutar 'git' @('-C', $repo, 'rev-parse', 'HEAD'))
    Guardar 'estado-git.txt' (Ejecutar 'git' @('-C', $repo, 'status', '--short'))
    # Solo montajes: docker inspect completo expondria variables de entorno.
    $montajes = Ejecutar 'docker' @('inspect', '--format', '{{json .Mounts}}', 'monpoint-mongodb')
    Guardar 'montajes-mongodb.json' $montajes
    # PS 5.1 entrega el arreglo JSON como un unico objeto a la canalizacion.
    # Asignarlo primero permite que foreach recorra cada montaje, no el arreglo.
    $montajesDatos = ConvertFrom-Json -InputObject $montajes
    $volumenes = @()
    foreach ($montaje in $montajesDatos) {
        if ($montaje.Type -eq 'volume') {
            $volumenes += (Ejecutar 'docker' @('volume', 'inspect', '--format', '{{json .}}', $montaje.Name) | ConvertFrom-Json)
        }
    }
    Guardar 'volumenes-mongodb.json' (ConvertTo-Json -InputObject $volumenes -Depth 6)

    Write-Host 'Log de ms-inventario...'
    $eventoId = '1eb998dc-c565-44d9-9cfb-8e993b91eab6'
    $log = Ejecutar 'docker' @('logs', 'monpoint-inventario')
    $lineas = @($log -split '\r?\n' | Where-Object { $_ -match [regex]::Escape($eventoId) })
    Guardar 'inventario-reentrega.txt' ($lineas -join "`r`n")
    if (-not ($lineas -match 'VENTA_CREADA_DESCARTADO.*motivo=ya_procesado')) {
        throw 'No se encontro el descarte esperado en los logs conservados.'
    }

    Write-Host 'Instantaneas de API: se pedira la clave del ADMIN...'
    $cred = Get-Credential -UserName 'admin@monpoint.com' -Message 'ADMIN de MonPoint'
    if ($null -eq $cred) { throw 'Autenticacion cancelada.' }
    $cuerpo = @{ correo = $cred.UserName; password = $cred.GetNetworkCredential().Password } | ConvertTo-Json
    $acceso = Invoke-RestMethod -Method Post -Uri "$gateway/auth/login" -ContentType 'application/json' -Body $cuerpo -TimeoutSec 30
    if ([string]::IsNullOrEmpty($acceso.token)) { throw 'Login sin token.' }
    $h = @{ Authorization = "Bearer $($acceso.token)" }
    Remove-Variable -Name @('cred', 'cuerpo', 'acceso')

    $productos = Invoke-RestMethod -Uri "$gateway/inventario/productos" -Headers $h -TimeoutSec 30
    Guardar 'api-productos.json' (ConvertTo-Json -InputObject @($productos) -Depth 5)
    $notificaciones = Invoke-RestMethod -Uri "$gateway/notificaciones" -Headers $h -TimeoutSec 30
    Guardar 'api-notificaciones.json' (ConvertTo-Json -InputObject @($notificaciones) -Depth 5)
    $agregacion = Invoke-RestMethod -Uri "$gateway/reportes/ventas/agregado?periodo=DIA&desde=2026-11-10&hasta=2026-11-12" -Headers $h -TimeoutSec 30
    Guardar 'api-agregacion.json' (ConvertTo-Json -InputObject $agregacion -Depth 5)

    $ventas = [ordered]@{
        '1' = '6af416432d1858377c263c86'
        '2' = '6af416432d1858377c263c88'
        '3' = '6af416442d1858377c263c8a'
        '4' = '6af41a2b2d1858377c263c8c'
        '5' = '6af41c0e2d1858377c263c8e'
        '6' = '6af41fae2d1858377c263c90'
    }
    $detalle = [ordered]@{}
    foreach ($folio in $ventas.Keys) {
        # Cualquier error HTTP detiene la recoleccion; no se oculta como un dato.
        $venta = Invoke-RestMethod -Uri "$gateway/ventas/$($ventas[$folio])" -Headers $h -TimeoutSec 30
        if ($venta.id -ne $ventas[$folio] -or $venta.folio -ne [int]$folio) {
            throw "Identidad discordante en la venta de folio $folio."
        }
        $detalle[$folio] = $venta
    }
    Guardar 'api-ventas.json' (ConvertTo-Json -InputObject $detalle -Depth 6)
    Guardar 'recoleccion.json' (ConvertTo-Json -InputObject ([ordered]@{
        estado = 'COMPLETADA'; inicioUtc = $inicio; finUtc = [DateTime]::UtcNow.ToString('o')
        zonaLocal = [TimeZoneInfo]::Local.Id; powershell = $PSVersionTable.PSVersion.ToString()
        gateway = $gateway
        alcance = 'Instantaneas posteriores; no certifican por si solas el arranque limpio ni la revision de las imagenes.'
    }))

    Write-Host 'Calculando SHA-256...'
    $manifiesto = [ordered]@{}
    Get-ChildItem -LiteralPath $evidencia -Recurse -File |
        Where-Object { $_.FullName -ne $archivoManifiesto } |
        Sort-Object FullName |
        ForEach-Object {
            $relativo = $_.FullName.Substring($evidencia.Length + 1).Replace('\', '/')
            $manifiesto[$relativo] = [ordered]@{
                sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
                bytes = $_.Length
            }
        }
    [IO.File]::WriteAllText($archivoManifiesto, (ConvertTo-Json -InputObject $manifiesto -Depth 4), (New-Object Text.UTF8Encoding($false)))
    Write-Host "Recoleccion COMPLETADA: $($manifiesto.Count) archivos con hash. Revisar las instantaneas antes de commitear."
} catch {
    if (Test-Path $archivoManifiesto) { Remove-Item -LiteralPath $archivoManifiesto }
    Guardar 'recoleccion.json' (ConvertTo-Json -InputObject ([ordered]@{
        estado = 'INCOMPLETA'; inicioUtc = $inicio; finUtc = [DateTime]::UtcNow.ToString('o')
        alcance = 'Fallo la recoleccion. Los archivos parciales no acreditan un cierre completo.'
    }))
    throw
} finally {
    Remove-Variable -Name @('cred', 'cuerpo', 'acceso', 'h') -ErrorAction SilentlyContinue
}
