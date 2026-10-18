$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $projectRoot 'frontend'
Set-Location -LiteralPath $frontend

if (-not (Test-Path -LiteralPath (Join-Path $frontend 'node_modules'))) {
  Write-Host "Installing frontend dependencies into $frontend\node_modules ..."
  npm install --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host 'Starting LearnAgent frontend on http://localhost:5173 ...'
npm run dev
exit $LASTEXITCODE
