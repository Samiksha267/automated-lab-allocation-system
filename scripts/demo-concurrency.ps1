<#
.SYNOPSIS
Phase 29 demo script — proves FCFS concurrency-safe booking with two genuinely parallel requests
for the identical lab/date/time, against the real running Dockerized backend.

Demo-only. Reads the CR demo password from an environment variable (never hard-coded); falls back
to this project's own documented, non-secret local demo default (see .env.example / docs/13) only
if the variable isn't set, exactly matching how docker-compose.yml itself defaults it.

.PARAMETER BaseUrl
Backend base URL. Defaults to the local Docker Compose mapping.

.PARAMETER SubjectId
Subject to book (defaults to 1 = BDA).

.PARAMETER BatchId
Batch to book for (defaults to 1 = A1).

.PARAMETER LabId
Lab to race for. If omitted, the script searches for a valid candidate first.

.PARAMETER Date / StartTime / EndTime
The slot both requests will race for. Defaults to this doc's documented free demo slot.
Pick a fresh, never-before-used date/time if re-running this script more than once, since the
first successful run leaves a real booking behind (see docs/17-DEMO-SCENARIOS.md "Demo Reset").
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [long]$SubjectId = 1,
    [long]$BatchId = 1,
    [Nullable[long]]$LabId = $null,
    [string]$Date = "2030-06-24",
    [string]$StartTime = "15:00:00",
    [string]$EndTime = "17:00:00"
)

$ErrorActionPreference = "Stop"
$crPassword = if ($env:DEMO_CR_PASSWORD) { $env:DEMO_CR_PASSWORD } else { "CrDemo123!" }

function Get-Token($email, $password) {
    $body = @{ email = $email; password = $password } | ConvertTo-Json
    $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body $body
    return $response.accessToken
}

Write-Host "Logging in as the demo CR..."
$token = Get-Token "cr@example.edu" $crPassword

if (-not $LabId) {
    Write-Host "No -LabId given; searching for a valid candidate at $Date $StartTime-$EndTime..."
    $searchBody = @{ subjectId = $SubjectId; targetType = "BATCH"; batchId = $BatchId; allocationDate = $Date; startTime = $StartTime; endTime = $EndTime } | ConvertTo-Json
    $searchResult = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/allocations/extra/search" -ContentType "application/json" -Headers @{ Authorization = "Bearer $token" } -Body $searchBody
    if (-not $searchResult.rankedValidLabs -or $searchResult.rankedValidLabs.Count -eq 0) {
        Write-Error "No valid lab found for this slot - pick a different date/time (faculty availability and existing bookings both matter). See docs/17-DEMO-SCENARIOS.md."
        exit 1
    }
    $LabId = $searchResult.rankedValidLabs[0].labId
    Write-Host "Using lab '$($searchResult.rankedValidLabs[0].labCode)' (id=$LabId)."
}

$bookingBody = @{ subjectId = $SubjectId; targetType = "BATCH"; batchId = $BatchId; allocationDate = $Date; startTime = $StartTime; endTime = $EndTime; labId = $LabId } | ConvertTo-Json

Write-Host "`nFiring two genuinely parallel booking requests for the identical lab/date/time..."

$jobA = Start-Job -ScriptBlock {
    param($url, $tok, $body)
    try {
        $r = Invoke-WebRequest -Method Post -Uri "$url/api/allocations/extra" -ContentType "application/json" -Headers @{ Authorization = "Bearer $tok" } -Body $body -UseBasicParsing
        [pscustomobject]@{ Label = "Request A"; Status = $r.StatusCode; Body = $r.Content }
    } catch {
        [pscustomobject]@{ Label = "Request A"; Status = $_.Exception.Response.StatusCode.value__; Body = $_.ErrorDetails.Message }
    }
} -ArgumentList $BaseUrl, $token, $bookingBody

$jobB = Start-Job -ScriptBlock {
    param($url, $tok, $body)
    try {
        $r = Invoke-WebRequest -Method Post -Uri "$url/api/allocations/extra" -ContentType "application/json" -Headers @{ Authorization = "Bearer $tok" } -Body $body -UseBasicParsing
        [pscustomobject]@{ Label = "Request B"; Status = $r.StatusCode; Body = $r.Content }
    } catch {
        [pscustomobject]@{ Label = "Request B"; Status = $_.Exception.Response.StatusCode.value__; Body = $_.ErrorDetails.Message }
    }
} -ArgumentList $BaseUrl, $token, $bookingBody

$results = Receive-Job -Job $jobA, $jobB -Wait
Remove-Job -Job $jobA, $jobB

foreach ($r in $results) {
    Write-Host "`n--- $($r.Label): HTTP $($r.Status) ---"
    Write-Host $r.Body
}

$successCount = @($results | Where-Object { $_.Status -eq 200 }).Count
$conflictCount = @($results | Where-Object { $_.Status -eq 409 }).Count
Write-Host "`nSummary: $successCount success(es), $conflictCount conflict(s) - expected exactly 1 and 1, in either order."
Write-Host "Verify the database directly with:"
$sql = "SELECT COUNT(*) FROM allocation WHERE allocation_date=" + [char]39 + $Date + [char]39 + " AND start_time=" + [char]39 + $StartTime + [char]39 + " AND status IN (" + [char]39 + "APPROVED" + [char]39 + "," + [char]39 + "PUBLISHED" + [char]39 + ");"
$dq = [char]34
Write-Host ("  docker exec lab_allocation-postgres-1 psql -U lab_user -d lab_allocation -c " + $dq + $sql + $dq)
