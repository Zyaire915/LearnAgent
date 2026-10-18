[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw "$envFile does not exist." }

$lines = [Collections.Generic.List[string]](Get-Content -LiteralPath $envFile)
function Set-DotEnvValue([string]$Name, [string]$Value) {
  for ($index = 0; $index -lt $lines.Count; $index++) {
    if ($lines[$index] -match "^$([Regex]::Escape($Name))=") { $lines[$index] = "$Name=$Value"; return }
  }
  $lines.Add("$Name=$Value")
}
Set-DotEnvValue 'LEARNAGENT_EMBEDDING_PROVIDER' 'transformers'
Set-DotEnvValue 'LEARNAGENT_VECTOR_STORE' 'pgvector'
$portableRoot = $projectRoot.Replace('\\', '/')
Set-DotEnvValue 'LOCAL_EMBEDDING_CACHE' "$portableRoot/.models/onnx"
Set-DotEnvValue 'DJL_CACHE_DIR' "$portableRoot/.cache/djl-cpu"
Set-DotEnvValue 'ENGINE_CACHE_DIR' "$portableRoot/.cache/djl-cpu/engine"
Set-DotEnvValue 'TOKENIZERS_CACHE' "$portableRoot/.cache/djl-cpu/tokenizers"
Set-DotEnvValue 'PYTORCH_FLAVOR' 'cpu'
[IO.File]::WriteAllLines($envFile, $lines, [Text.UTF8Encoding]::new($false))
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot '.models\onnx') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot '.cache\djl-cpu\engine'), (Join-Path $projectRoot '.cache\djl-cpu\tokenizers') | Out-Null
Write-Host 'Local ONNX embeddings and PGVector are enabled.' -ForegroundColor Green
