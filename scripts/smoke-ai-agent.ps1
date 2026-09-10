param(
    [string]$BaseUrl = 'http://localhost:8081'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-AgentJson {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token
    )

    $headers = @{}
    if ($Token) {
        $headers['access-token'] = $Token
    }
    $request = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
        ContentType = 'application/json'
    }
    if ($null -ne $Body) {
        $request.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }
    $response = Invoke-RestMethod @request
    if ($response.code -ne 200) {
        throw "$Path failed: code=$($response.code), message=$($response.message)"
    }
    return $response.data
}

$login = Invoke-AgentJson -Method 'POST' -Path '/app/login' -Body @{
    phone = '13800000000'
    code = '888888'
} -Token ''
$token = [string]$login
if (-not $token) {
    throw 'Demo login did not return an access token.'
}

$chatResponse = Invoke-WebRequest -UseBasicParsing -Method 'POST' `
    -Uri "$BaseUrl/app/ai/chat" `
    -Headers @{ 'access-token' = $token; Accept = 'text/event-stream' } `
    -ContentType 'application/json' `
    -Body (@{ conversationId = 'smoke-agent'; message = '预算2500元，并说明押金怎么退' } |
        ConvertTo-Json -Compress)
$events = @($chatResponse.Content -split "`r?`n" |
    Where-Object { $_ -like 'data:*' } |
    ForEach-Object { $_.Substring(5).Trim() | ConvertFrom-Json })
$meta = $events | Where-Object { $_.type -eq 'meta' } | Select-Object -First 1
$recommendations = $events | Where-Object { $_.type -eq 'recommendations' } | Select-Object -First 1
$citations = $events | Where-Object { $_.type -eq 'citations' } | Select-Object -First 1
$done = $events | Where-Object { $_.type -eq 'done' } | Select-Object -First 1
foreach ($requiredType in @('meta', 'message', 'recommendations', 'citations', 'done')) {
    if (-not ($events | Where-Object { $_.type -eq $requiredType } | Select-Object -First 1)) {
        throw "Chat SSE is missing required event: $requiredType"
    }
}
if ($meta.payload.mode -notin @('MODEL', 'FALLBACK')) {
    throw "Unexpected chat mode: $($meta.payload.mode)."
}
if (-not $meta.payload.provider -or -not $meta.payload.traceId) {
    throw 'Chat meta did not include provider and traceId.'
}
if ($done.payload.traceId -ne $meta.payload.traceId) {
    throw 'The done event traceId does not match the meta event.'
}
if ($done.payload.suggestedAction -ne 'SELECT_ROOM') {
    throw "Expected SELECT_ROOM action, got $($done.payload.suggestedAction)."
}
if (@($recommendations.payload).Count -eq 0 -or @($citations.payload).Count -eq 0) {
    throw 'Chat did not return both room recommendations and citations.'
}

$roomId = [long]$recommendations.payload[0].roomId
$before = @(Invoke-AgentJson -Method 'GET' -Path '/app/appointment/listItem' -Body $null -Token $token)
$appointmentTime = (Get-Date).Date.AddDays(1).AddHours(14).ToString('yyyy-MM-ddTHH:mm:ss')
$draft = Invoke-AgentJson -Method 'POST' -Path '/app/ai/appointments/draft' -Body @{
    roomId = $roomId
    name = '演示用户'
    phone = '13800000000'
    appointmentTime = $appointmentTime
    additionalInfo = 'AI 找房 smoke 验收'
} -Token $token
$afterDraft = @(Invoke-AgentJson -Method 'GET' -Path '/app/appointment/listItem' -Body $null -Token $token)
if ($afterDraft.Count -ne $before.Count) {
    throw 'Creating a draft unexpectedly persisted an appointment.'
}

$first = Invoke-AgentJson -Method 'POST' -Path '/app/ai/appointments/confirm' -Body @{
    confirmationToken = $draft.confirmationToken
} -Token $token
$replay = Invoke-AgentJson -Method 'POST' -Path '/app/ai/appointments/confirm' -Body @{
    confirmationToken = $draft.confirmationToken
} -Token $token
if ($first.appointmentId -ne $replay.appointmentId -or -not $replay.idempotentReplay) {
    throw 'Confirmation replay was not idempotent.'
}

$appointments = @(Invoke-AgentJson -Method 'GET' -Path '/app/appointment/listItem' -Body $null -Token $token)
if ($appointments.id -notcontains $first.appointmentId) {
    throw "Appointment $($first.appointmentId) was not visible in the appointment list."
}

[pscustomobject]@{
    mode = $meta.payload.mode
    provider = $meta.payload.provider
    traceId = $meta.payload.traceId
    roomId = $roomId
    appointmentId = $first.appointmentId
    idempotentReplay = $replay.idempotentReplay
    result = 'PASS'
} | Format-List
