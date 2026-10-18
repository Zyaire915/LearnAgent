$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'start-db.ps1')

$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) {
  foreach ($line in Get-Content -LiteralPath $envFile) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    $parts = $trimmed.Split('=', 2)
    if ($parts.Count -eq 2) { [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process') }
  }
}

$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$projectRoot\.tmp -XX:InitialCodeCacheSize=64m -XX:ReservedCodeCacheSize=256m -Xss512k"
$env:DJL_CACHE_DIR = if ($env:DJL_CACHE_DIR) { $env:DJL_CACHE_DIR } else { "$projectRoot\.cache\djl-cpu" }
$env:ENGINE_CACHE_DIR = if ($env:ENGINE_CACHE_DIR) { $env:ENGINE_CACHE_DIR } else { "$projectRoot\.cache\djl-cpu\engine" }
$env:TOKENIZERS_CACHE = if ($env:TOKENIZERS_CACHE) { $env:TOKENIZERS_CACHE } else { "$projectRoot\.cache\djl-cpu\tokenizers" }
$env:PYTORCH_FLAVOR = 'cpu'
New-Item -ItemType Directory -Force -Path $env:DJL_CACHE_DIR, $env:ENGINE_CACHE_DIR, $env:TOKENIZERS_CACHE | Out-Null
Set-Location -LiteralPath $projectRoot
Write-Host 'Starting LearnAgent backend on http://localhost:8080 ...'
& (Join-Path $projectRoot 'mvnw.cmd') -pl backend spring-boot:run
exit $LASTEXITCODE
