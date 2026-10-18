$ErrorActionPreference = 'Stop'

$base = 'http://localhost:8080'
function Invoke-SmokeGet([string]$Path, [string]$Label) {
  $result = Invoke-RestMethod -Uri "$base$Path" -TimeoutSec 10
  Write-Host "PASS  $Label" -ForegroundColor Green
  return $result
}

$status = Invoke-SmokeGet '/api/system/status' 'System status'
if (-not $status.databaseConnected) { throw 'Database is not connected.' }
Invoke-SmokeGet '/api/courses' 'Courses API' | Out-Null
Invoke-SmokeGet '/api/tasks' 'Tasks API' | Out-Null
try { Invoke-SmokeGet '/api/weekly-reviews/latest' 'Weekly Review latest' | Out-Null } catch { if ($_.Exception.Response.StatusCode.value__ -eq 204) { Write-Host 'PASS  Weekly Review latest (empty)' -ForegroundColor Green } else { throw } }

if (-not [string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY) -or -not [string]::IsNullOrWhiteSpace($env:ARK_API_KEY)) {
  Invoke-SmokeGet '/api/ai/test' 'Minimal AI call' | Out-Null
} else {
  Write-Host 'SKIP  Minimal AI call (no AI provider key configured)' -ForegroundColor Yellow
}

Write-Host 'LearnAgent smoke test completed.' -ForegroundColor Cyan
