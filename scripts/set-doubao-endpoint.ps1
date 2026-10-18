[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$EndpointId
)

$ErrorActionPreference = 'Stop'

if ($EndpointId -notmatch '^ep-[A-Za-z0-9-]+$') {
  throw 'EndpointId must start with ep- and contain only letters, digits, and hyphens.'
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
  throw "$envFile does not exist. Run configure-doubao.ps1 first."
}

$lines = [Collections.Generic.List[string]](Get-Content -LiteralPath $envFile)
$updated = $false
for ($index = 0; $index -lt $lines.Count; $index++) {
  if ($lines[$index] -match '^DOUBAO_CHAT_MODEL=') {
    $lines[$index] = "DOUBAO_CHAT_MODEL=$EndpointId"
    $updated = $true
    break
  }
}
if (-not $updated) { $lines.Add("DOUBAO_CHAT_MODEL=$EndpointId") }

# Write in place so the restricted ACL created by configure-doubao.ps1 remains.
[IO.File]::WriteAllLines($envFile, $lines, [Text.UTF8Encoding]::new($false))
Write-Host "Doubao Endpoint ID saved: $EndpointId" -ForegroundColor Green
