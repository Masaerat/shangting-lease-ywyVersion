$ErrorActionPreference = 'Stop'

$requiredServices = @(
    'mysql',
    'redis',
    'rabbitmq',
    'pgvector',
    'minio',
    'minio-init',
    'web-admin',
    'web-app',
    'rent-house-h5'
)

$services = @(docker compose config --services)
if ($LASTEXITCODE -ne 0) {
    throw 'docker compose config failed'
}

foreach ($name in $requiredServices) {
    if ($services -notcontains $name) {
        throw "missing compose service: $name"
    }
}

foreach ($port in 8080, 8081) {
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

$h5 = Invoke-WebRequest -UseBasicParsing 'http://localhost:8082/'
if ($h5.StatusCode -ne 200 -or $h5.Content -notmatch '<div id="app"></div>') {
    throw 'H5 application on port 8082 is not ready'
}

Write-Host 'Compose services and application health checks passed.'
