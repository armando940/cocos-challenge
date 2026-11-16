<# 
.SYNOPSIS
    Carga variables de entorno desde .env al proceso actual de PowerShell
.DESCRIPTION
    Lee el archivo .env y exporta cada KEY=VALUE como variable de entorno de proceso ($env:KEY).
    ADVERTENCIA: NO funciona con ./gradlew bootRun porque Gradle lanza la app en un JVM separado.
    Usá la forma inline documentada en README.md en su lugar.
.EXAMPLE
    # Para uso general (scripts, docker, etc.):
    .\load-env.ps1
    # Luego corrés tu comando en la MISMA sesión
#>

param()

$envPath = Join-Path $PSScriptRoot ".env"

if (-not (Test-Path $envPath)) {
    Write-Error "No se encontró .env en $PSScriptRoot"
    exit 1
}

Get-Content $envPath | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line -match '^([^=]+)=(.*)$') {
        $key = $matches[1].Trim()
        $value = $matches[2].Trim()
        Set-Item -Path "Env:$key" -Value $value
        Write-Host "Set `$env:$key" -ForegroundColor Gray
    }
}

Write-Host "Variables cargadas en esta sesión de PowerShell." -ForegroundColor Green
Write-Host "Para ./gradlew bootRun usá la forma inline del README." -ForegroundColor Yellow