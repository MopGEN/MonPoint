<#
    Pruebas del recolector sin Docker, red ni credenciales reales.
    Uso: powershell -NoProfile -File .\docs\aceptacion\pruebas\recolectar.test.ps1
    Tambien admite pwsh. Ejecuta una copia temporal del script, nunca la carpeta
    de evidencia real. Simula arrays JSON no enumerados, como en PowerShell 5.1.
#>
param(
    [string]$Recolector = (Join-Path $PSScriptRoot '..\6.1-windows-2026-11-11\recolectar.ps1')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
$origen = $Recolector
$temporal = Join-Path ([IO.Path]::GetTempPath()) ('monpoint-recolector-' + [guid]::NewGuid())
$global:PruebaRecolector = [pscustomobject]@{
    caso = ''; ventasLeidas = 0; volumenesLeidos = 0
    mockToken = 'TOKEN_SOLO_PARA_PRUEBA_NO_GUARDAR'
    mockClave = 'CLAVE_SOLO_PARA_PRUEBA_NO_GUARDAR'
}

function Exigir([bool]$condicion, [string]$mensaje) {
    if (-not $condicion) { throw $mensaje }
}

function Get-Command {
    [CmdletBinding()]
    param([string]$Name, $CommandType)
    if ($Name -in @('docker', 'git')) { return [pscustomobject]@{ Name = $Name } }
    Microsoft.PowerShell.Core\Get-Command -Name $Name -CommandType $CommandType
}

function ConvertFrom-Json {
    [CmdletBinding()]
    param([Parameter(ValueFromPipeline = $true)]$InputObject)
    process {
        if ($PSVersionTable.PSVersion.Major -ge 7) {
            $dato = Microsoft.PowerShell.Utility\ConvertFrom-Json -InputObject $InputObject -NoEnumerate
        } else {
            $dato = Microsoft.PowerShell.Utility\ConvertFrom-Json -InputObject $InputObject
        }
        if ($null -eq $dato) { return }
        Write-Output -NoEnumerate $dato
    }
}

function docker {
    $global:LASTEXITCODE = 0
    if ($global:PruebaRecolector.caso -eq 'docker-falla') { $global:LASTEXITCODE = 23; return 'error simulado' }
    $a = @($args)
    switch ($a[0]) {
        'compose' { return '{"Name":"monpoint-mongodb","Health":"healthy"}' }
        'exec' { return 'estado simulado del broker' }
        'inspect' {
            if ($global:PruebaRecolector.caso -eq 'cero-montajes') { return '[]' }
            return '[{"Type":"bind","Source":"/config","Destination":"/init"},{"Type":"volume","Name":"mongodb-prueba","Destination":"/data/db"},{"Type":"volume","Name":"configdb-prueba","Destination":"/data/configdb"}]'
        }
        'volume' {
            Exigir ($a.Count -eq 5) 'Argumentos incorrectos para volume inspect'
            Exigir ($a[4] -in @('mongodb-prueba', 'configdb-prueba')) 'Se inspecciono un nombre incorrecto'
            $global:PruebaRecolector.volumenesLeidos++
            return ('{"Name":"' + $a[4] + '","CreatedAt":"2026-11-11T07:00:00Z"}')
        }
        'logs' {
            if ($global:PruebaRecolector.caso -eq 'sin-descarte') { return 'log sin el evento' }
            return 'evento=VENTA_CREADA_DESCARTADO eventoId=1eb998dc-c565-44d9-9cfb-8e993b91eab6 motivo=ya_procesado'
        }
        default { throw 'Operacion Docker inesperada' }
    }
}

function git {
    $global:LASTEXITCODE = 0
    return 'revision-simulada'
}

function Get-Credential {
    param($UserName, $Message)
    return New-Object Management.Automation.PSCredential($UserName, (ConvertTo-SecureString $global:PruebaRecolector.mockClave -AsPlainText -Force))
}

function Invoke-RestMethod {
    param($Method, $Uri, $ContentType, $Body, $TimeoutSec, $Headers)
    if ($Uri -like '*/auth/login') { return [pscustomobject]@{ token = $global:PruebaRecolector.mockToken } }
    Exigir ($Headers.Authorization -eq ('Bearer ' + $global:PruebaRecolector.mockToken)) 'Cabecera de autenticacion incorrecta'
    if ($Uri -like '*/inventario/productos') {
        Write-Output -NoEnumerate @([pscustomobject]@{ nombre = 'Tinta'; existencia = 5 }, [pscustomobject]@{ nombre = 'Papel'; existencia = 7 })
        return
    }
    if ($Uri -like '*/notificaciones') {
        Write-Output -NoEnumerate @([pscustomobject]@{ tipo = 'STOCK_BAJO'; leida = $false })
        return
    }
    if ($Uri -like '*/reportes/*') { return [pscustomobject]@{ total = 672.25 } }
    if ($Uri -like '*/ventas/*') {
        $global:PruebaRecolector.ventasLeidas++
        if ($global:PruebaRecolector.caso -eq 'venta-falla') { throw 'HTTP 401 simulado' }
        $id = ($Uri -split '/')[-1]
        $folio = @('6af416432d1858377c263c86', '6af416432d1858377c263c88', '6af416442d1858377c263c8a', '6af41a2b2d1858377c263c8c', '6af41c0e2d1858377c263c8e', '6af41fae2d1858377c263c90').IndexOf($id) + 1
        if ($global:PruebaRecolector.caso -eq 'folio-discordante') { $folio = 999 }
        return [pscustomobject]@{ id = $id; folio = $folio }
    }
    throw 'Peticion inesperada'
}

try {
    foreach ($caso in @('completa', 'cero-montajes', 'docker-falla', 'venta-falla', 'sin-descarte', 'folio-discordante')) {
        $global:PruebaRecolector.caso = $caso
        $raiz = Join-Path $temporal $caso
        $evidencia = Join-Path $raiz 'docs\aceptacion\recorrido'
        $null = New-Item -ItemType Directory -Path $evidencia -Force
        $null = New-Item -ItemType Directory -Path (Join-Path $raiz 'monpoint-ms\infra') -Force
        $copia = Join-Path $evidencia 'recolectar.ps1'
        Copy-Item -LiteralPath $origen -Destination $copia
        # Debe invalidar el manifiesto anterior aunque este intento falle.
        [IO.File]::WriteAllText((Join-Path $evidencia 'SHA256.json'), '{}')
        $global:PruebaRecolector.ventasLeidas = 0
        $global:PruebaRecolector.volumenesLeidos = 0
        $fallo = $false
        try { & $copia } catch {
            $fallo = $true
            if ($global:PruebaRecolector.caso -in @('completa', 'cero-montajes')) { throw }
        }
        $registro = @(Get-ChildItem -LiteralPath (Join-Path $evidencia 'entorno') -Recurse -Filter recoleccion.json)
        Exigir ($registro.Count -eq 1) 'Falta el estado del intento'
        $estado = Microsoft.PowerShell.Utility\ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($registro[0].FullName))
        $manifiesto = Join-Path $evidencia 'SHA256.json'
        if ($global:PruebaRecolector.caso -in @('completa', 'cero-montajes')) {
            Exigir (-not $fallo) "Fallo inesperado en $caso"
            Exigir ($estado.estado -eq 'COMPLETADA') 'Estado de exito incorrecto'
            Exigir ($global:PruebaRecolector.ventasLeidas -eq 6) 'No se consultaron las seis ventas'
            $esperados = if ($global:PruebaRecolector.caso -eq 'completa') { 2 } else { 0 }
            Exigir ($global:PruebaRecolector.volumenesLeidos -eq $esperados) 'Montajes mal enumerados'
            $carpeta = $registro[0].DirectoryName
            Exigir (([IO.File]::ReadAllText((Join-Path $carpeta 'api-productos.json'))).TrimStart().StartsWith('[')) 'Productos sin arreglo JSON'
            Exigir (([IO.File]::ReadAllText((Join-Path $carpeta 'api-notificaciones.json'))).TrimStart().StartsWith('[')) 'Notificaciones sin arreglo JSON'
            $hashes = Microsoft.PowerShell.Utility\ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($manifiesto))
            $archivos = @(Get-ChildItem -LiteralPath $evidencia -Recurse -File | Where-Object { $_.Name -ne 'SHA256.json' })
            Exigir (@($hashes.PSObject.Properties).Count -eq $archivos.Count) 'Manifiesto incompleto'
            foreach ($entrada in $hashes.PSObject.Properties) {
                $archivo = Join-Path $evidencia $entrada.Name
                Exigir ((Get-FileHash -LiteralPath $archivo -Algorithm SHA256).Hash.ToLowerInvariant() -eq $entrada.Value.sha256) 'Hash incorrecto'
                Exigir ((Get-Item -LiteralPath $archivo).Length -eq $entrada.Value.bytes) 'Tamano incorrecto'
            }
        } else {
            Exigir $fallo "No se propago el fallo de $caso"
            Exigir ($estado.estado -eq 'INCOMPLETA') 'Fallo registrado como exito'
            Exigir (-not (Test-Path -LiteralPath $manifiesto)) 'Manifiesto conservado tras fallo'
        }
        foreach ($archivo in Get-ChildItem -LiteralPath $evidencia -Recurse -File) {
            if ($archivo.Name -eq 'recolectar.ps1') { continue }
            $texto = [IO.File]::ReadAllText($archivo.FullName)
            Exigir (-not $texto.Contains($global:PruebaRecolector.mockToken)) 'Se guardo el token'
            Exigir (-not $texto.Contains($global:PruebaRecolector.mockClave)) 'Se guardo la clave'
        }
        Write-Host "OK: $caso"
    }
    Write-Host '6 pruebas completadas sin Docker, red ni credenciales reales.'
} finally {
    Remove-Variable -Name PruebaRecolector -Scope Global -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $temporal) { Remove-Item -LiteralPath $temporal -Recurse -Force }
}
