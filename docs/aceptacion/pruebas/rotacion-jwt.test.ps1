<#
    Prueba el primer bloque PowerShell del procedimiento, sin copiar su logica.
    Solo usa archivos ficticios en un directorio temporal. Docker es una funcion
    simulada y nunca se invoca el ejecutable. No lee el .env de la instalacion.
    Uso desde la raiz: pwsh -NoProfile -File docs/aceptacion/pruebas/rotacion-jwt.test.ps1
    Tambien se puede ejecutar con Windows PowerShell para comprobar ese runtime.
#>
param(
    [string]$Documento = (Join-Path $PSScriptRoot '../../../monpoint-ms/docs/cierre/VERIFICACION-WINDOWS.md')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
function Exigir([bool]$condicion, [string]$mensaje) {
    if (-not $condicion) { throw $mensaje }
}
$texto = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $Documento).Path)
$coincidencia = [regex]::Match($texto, '(?s)```powershell\r?\n(.*?)\r?\n```')
Exigir $coincidencia.Success 'No se encontro el bloque PowerShell del procedimiento.'
$codigo = $coincidencia.Groups[1].Value
$tokens = $null; $errores = $null
$ast = [Management.Automation.Language.Parser]::ParseInput($codigo, [ref]$tokens, [ref]$errores)
Exigir ($errores.Count -eq 0) 'El bloque tiene errores de sintaxis.'
Exigir ($ast.EndBlock.Statements.Count -eq 1) 'La rotacion y Docker deben estar en una sola instruccion & { ... }, sin comandos sueltos.'
Exigir ($codigo -match '\[IO\.File\]::Replace\(\$rutaTemporal, \$rutaEntorno, \$rutaRespaldo\)') 'Se requiere una ruta real de respaldo en File.Replace.'

$temporal = Join-Path ([IO.Path]::GetTempPath()) ('monpoint-rotacion-prueba-' + [guid]::NewGuid().ToString('N'))
$ubicacion = Get-Location
$global:PruebaRotacion = $null
# El cambio solo afecta este proceso de pruebas, no la consola que lo lanza.
$env:JWT_SECRET = $null
function docker {
    $argumentos = @($args)
    Exigir ($argumentos.Count -ge 4) 'Invocacion Docker inesperada.'
    Exigir ($argumentos[0] -eq 'compose') 'Solo se admite Docker Compose simulado.'
    $operacion = $argumentos[3]
    Exigir ($operacion -in @('config', 'up', 'ps')) 'Operacion Docker inesperada.'
    [void]$global:PruebaRotacion.llamadas.Add($operacion)
    $global:LASTEXITCODE = 0
    if (($global:PruebaRotacion.caso -eq 'config-falla' -and $operacion -eq 'config') -or
        ($global:PruebaRotacion.caso -eq 'up-falla' -and $operacion -eq 'up')) {
        $global:LASTEXITCODE = 23
    }
}

try {
    [void][IO.Directory]::CreateDirectory($temporal)
    foreach ($caso in @('correcta', 'replace-falla', 'duplicada', 'override', 'config-falla', 'up-falla')) {
        $carpeta = Join-Path $temporal $caso
        $infra = Join-Path $carpeta 'infra'
        [void][IO.Directory]::CreateDirectory($infra)
        $ruta = Join-Path $infra '.env'
        $lineas = @('OTRA_CLAVE=valor con espacios y $literal',
            'JWT_SECRET=valor-anterior-exclusivo-de-prueba', ('MARCA=Pe' + [char]0xF1 + 'a'))
        if ($caso -eq 'duplicada') { $lineas += 'JWT_SECRET=duplicado-exclusivo-de-prueba' }
        $anterior = [string]::Join("`r`n", $lineas) + "`r`n"
        [IO.File]::WriteAllText($ruta, $anterior, (New-Object Text.UTF8Encoding($false)))
        $bytesAnteriores = [IO.File]::ReadAllBytes($ruta)
        $global:PruebaRotacion = [pscustomobject]@{ caso = $caso; llamadas = (New-Object 'Collections.Generic.List[string]') }
        $env:JWT_SECRET = $null
        if ($caso -eq 'override') { $env:JWT_SECRET = 'override-exclusivo-de-prueba' }
        $ejecutable = $codigo
        if ($caso -eq 'replace-falla') {
            # Inyectar una excepcion en el punto del reemplazo comprueba que
            # el finally limpia y que no se llega a recrear ningun servicio.
            $ejecutable = $codigo.Replace('[IO.File]::Replace($rutaTemporal, $rutaEntorno, $rutaRespaldo)',
                "throw 'Fallo de reemplazo inyectado para la prueba.'")
            Exigir ($ejecutable -ne $codigo) 'No se pudo inyectar el fallo.'
        }
        Set-Location -LiteralPath $carpeta
        $tokenPrevio = 'referencia-ficticia-en-memoria'
        $fallo = $false
        try { $salida = @(& ([scriptblock]::Create($ejecutable)) *>&1) }
        catch {
            $fallo = $true
            if ($caso -eq 'correcta') { throw }
        }
        $env:JWT_SECRET = $null
        $actual = [IO.File]::ReadAllText($ruta)
        $sinEscritura = $caso -in @('replace-falla', 'duplicada', 'override')
        Exigir ($tokenPrevio -eq 'referencia-ficticia-en-memoria') 'El bloque altero el token previo del llamador.'
        Exigir ($fallo -eq ($caso -ne 'correcta')) 'Estado de error inesperado.'
        if ($sinEscritura) {
            Exigir ([Convert]::ToBase64String([IO.File]::ReadAllBytes($ruta)) -ceq [Convert]::ToBase64String($bytesAnteriores)) 'Un fallo previo altero el archivo original.'
            Exigir ($global:PruebaRotacion.llamadas.Count -eq 0) 'Se llamo a Docker despues de fallar antes del reemplazo.'
        } else {
            $clave = [regex]::Match($actual, '(?m)^JWT_SECRET=([^\r\n]+)').Groups[1].Value
            Exigir ([Convert]::FromBase64String($clave).Length -eq 32) 'La nueva clave no contiene 32 bytes aleatorios.'
            $esperado = $anterior.Replace('JWT_SECRET=valor-anterior-exclusivo-de-prueba', ('JWT_SECRET=' + $clave))
            Exigir ($actual -ceq $esperado) 'Se alteraron otras lineas o saltos de linea.'
            $secuencia = [string]::Join(',', $global:PruebaRotacion.llamadas.ToArray())
            $prevista = switch ($caso) { 'correcta' { 'config,up,ps' }; 'config-falla' { 'config' }; 'up-falla' { 'config,up' } }
            Exigir ($secuencia -eq $prevista) 'La secuencia Docker continuo despues de un fallo.'
        }
        $restos = @(Get-ChildItem -LiteralPath $infra -Force | Where-Object { $_.Name -ne '.env' })
        Exigir ($restos.Count -eq 0) 'Quedaron temporales o respaldos en un caso con limpieza esperada.'
        Write-Output ('PASS ' + $caso)
    }
    Write-Output ('6 casos correctos; Docker simulado; PowerShell ' + $PSVersionTable.PSVersion.ToString())
} finally {
    $env:JWT_SECRET = $null
    Set-Location -LiteralPath $ubicacion.Path
    if (Test-Path -LiteralPath $temporal) { Remove-Item -LiteralPath $temporal -Recurse -Force }
}
