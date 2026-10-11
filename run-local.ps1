# run-local.ps1 — loads .env and runs JobWatch once (run-now mode), then exits.
# Contains NO secrets; reads them from the git-ignored .env file.
#
# Usage:
#   .\run-local.ps1            # single one-shot run (fetch -> filter -> email), then exit
#   .\run-local.ps1 -Service   # long-lived scheduled mode (cron)

param(
    [switch]$Service,
    [switch]$Dashboard
)

$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error "Missing .env file at $envFile"
    exit 1
}

# Load KEY=VALUE pairs from .env into the process environment.
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $idx = $line.IndexOf("=")
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        [Environment]::SetEnvironmentVariable($key, $val, "Process")
    }
}

if ($Service) {
    Write-Host "Starting JobWatch in scheduled (service) mode..."
    & "$PSScriptRoot\mvnw.cmd" spring-boot:run
} elseif ($Dashboard) {
    Write-Host "Starting JobWatch dashboard at http://127.0.0.1:8080 ..."
    & "$PSScriptRoot\mvnw.cmd" spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=dashboard"
} else {
    Write-Host "Starting JobWatch in one-shot (run-now) mode..."
    & "$PSScriptRoot\mvnw.cmd" spring-boot:run "-Dspring-boot.run.arguments=--jobwatch.run-now=true"
}

