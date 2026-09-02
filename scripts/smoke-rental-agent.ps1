param(
    [string]$BaseUrl = 'http://localhost:8081'
)

& "$PSScriptRoot\smoke-ai-agent.ps1" -BaseUrl $BaseUrl
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
