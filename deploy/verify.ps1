$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $projectRoot 'compose.yaml'

$requiredServices = @(
    'mysql',
    'redis',
    'rabbitmq',
    'pgvector',
    'minio',
    'minio-init',
    'web-admin',
    'web-app'
)

$services = @(docker compose --project-directory $projectRoot -f $composeFile config --services)
if ($LASTEXITCODE -ne 0) {
    throw 'docker compose config failed'
}

foreach ($name in $requiredServices) {
    if ($services -notcontains $name) {
        throw "missing compose service: $name"
    }
}

foreach ($port in 8080, 8081) {
    $health = $null
    $deadline = (Get-Date).AddSeconds(120)
    do {
        try {
            $health = Invoke-RestMethod "http://localhost:$port/actuator/health"
            if ($health.status -eq 'UP') {
                break
            }
        } catch {
            if ((Get-Date) -ge $deadline) {
                throw "application on port $port did not become UP: $($_.Exception.Message)"
            }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    if ($null -eq $health -or $health.status -ne 'UP') {
        throw "application on port $port did not become UP within 120 seconds"
    }
}

Write-Host 'Compose backend services and application health checks passed.'
