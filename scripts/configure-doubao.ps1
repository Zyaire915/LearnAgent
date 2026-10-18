[CmdletBinding()]
param(
  [switch]$SafeModeConfirmed
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$exampleFile = Join-Path $projectRoot '.env.example'
$envFile = Join-Path $projectRoot '.env'
$temporaryDirectory = Join-Path $projectRoot '.tmp'
New-Item -ItemType Directory -Force -Path $temporaryDirectory | Out-Null

Write-Host 'Configure LearnAgent for Doubao Seed 2.0 Mini through Volcengine Ark.' -ForegroundColor Cyan
if (-not $SafeModeConfirmed) {
  Write-Host 'Before continuing, enable Ark Safe Trial Mode so free quota exhaustion stops requests instead of charging.' -ForegroundColor Yellow
  $safeMode = Read-Host 'Type SAFE to confirm Safe Trial Mode is enabled'
  if ($safeMode -cne 'SAFE') { throw 'Configuration stopped. Enable Safe Trial Mode first.' }
}

Write-Host "The key is written only to $envFile and is never printed."
$secureKey = Read-Host 'Paste your ARK API key' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
  $apiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
if ([string]::IsNullOrWhiteSpace($apiKey)) { throw 'ARK API key cannot be empty.' }

$lines = if (Test-Path -LiteralPath $envFile) {
  [Collections.Generic.List[string]](Get-Content -LiteralPath $envFile)
} else {
  [Collections.Generic.List[string]](Get-Content -LiteralPath $exampleFile)
}

function Set-DotEnvValue([string]$Name, [string]$Value) {
  for ($index = 0; $index -lt $lines.Count; $index++) {
    if ($lines[$index] -match "^$([Regex]::Escape($Name))=") {
      $lines[$index] = "$Name=$Value"
      return
    }
  }
  $lines.Add("$Name=$Value")
}

$profileLine = $lines | Where-Object { $_ -match '^SPRING_PROFILES_ACTIVE=' } | Select-Object -First 1
$existingProfiles = if ($profileLine) { $profileLine -replace '^SPRING_PROFILES_ACTIVE=', '' } else { '' }
$profiles = @($existingProfiles.Split(',', [StringSplitOptions]::RemoveEmptyEntries) |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -notin @('gemini', 'doubao') })
$profiles = @('doubao') + $profiles

$currentEndpointLine = $lines | Where-Object { $_ -match '^DOUBAO_CHAT_MODEL=ep-' } | Select-Object -First 1
$currentEndpoint = if ($currentEndpointLine) { $currentEndpointLine -replace '^DOUBAO_CHAT_MODEL=', '' } else { '' }
$endpointId = Read-Host 'Paste the custom inference Endpoint ID (ep-...), or press Enter to keep the existing value'
if ([string]::IsNullOrWhiteSpace($endpointId)) { $endpointId = $currentEndpoint }
if ($endpointId -notmatch '^ep-[A-Za-z0-9-]+$') {
  throw 'A valid custom inference Endpoint ID is required for the Ark Chat API.'
}

Set-DotEnvValue 'SPRING_PROFILES_ACTIVE' ($profiles -join ',')
Set-DotEnvValue 'ARK_API_KEY' $apiKey.Trim()
Set-DotEnvValue 'DOUBAO_CHAT_MODEL' $endpointId
Set-DotEnvValue 'DOUBAO_TEMPERATURE' '0.2'
Set-DotEnvValue 'DOUBAO_REASONING_EFFORT' 'minimal'
Set-DotEnvValue 'DOUBAO_MAX_COMPLETION_TOKENS' '1024'
Set-DotEnvValue 'LEARNAGENT_CHAT_PROVIDER' 'openai'
# Embeddings run locally through ONNX and are stored in PGVector.
Set-DotEnvValue 'LEARNAGENT_EMBEDDING_PROVIDER' 'transformers'
Set-DotEnvValue 'LEARNAGENT_VECTOR_STORE' 'pgvector'
$portableRoot = $projectRoot.Replace('\\', '/')
Set-DotEnvValue 'LOCAL_EMBEDDING_CACHE' "$portableRoot/.models/onnx"
Set-DotEnvValue 'DJL_CACHE_DIR' "$portableRoot/.cache/djl-cpu"
Set-DotEnvValue 'ENGINE_CACHE_DIR' "$portableRoot/.cache/djl-cpu/engine"
Set-DotEnvValue 'TOKENIZERS_CACHE' "$portableRoot/.cache/djl-cpu/tokenizers"
Set-DotEnvValue 'PYTORCH_FLAVOR' 'cpu'

$temporaryFile = Join-Path $temporaryDirectory ('.env.' + [Guid]::NewGuid().ToString('N') + '.tmp')
try {
  [IO.File]::WriteAllLines($temporaryFile, $lines, [Text.UTF8Encoding]::new($false))
  Move-Item -LiteralPath $temporaryFile -Destination $envFile -Force
} finally {
  if (Test-Path -LiteralPath $temporaryFile) { Remove-Item -LiteralPath $temporaryFile -Force }
  $apiKey = $null
}

if ($IsWindows -or $env:OS -eq 'Windows_NT') {
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
  $acl = New-Object Security.AccessControl.FileSecurity
  $acl.SetOwner([Security.Principal.NTAccount]::new($identity))
  $acl.SetAccessRuleProtection($true, $false)
  $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
      $identity, [Security.AccessControl.FileSystemRights]::FullControl,
      [Security.AccessControl.AccessControlType]::Allow))
  Set-Acl -LiteralPath $envFile -AclObject $acl
}

Write-Host "Doubao chat-only configuration saved to $envFile." -ForegroundColor Green
Write-Host 'Do not paste the key into chat, source files, screenshots, or logs.' -ForegroundColor Yellow
