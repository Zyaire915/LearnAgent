[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
$exampleFile = Join-Path $projectRoot '.env.example'
$temporaryDirectory = Join-Path $projectRoot '.tmp'
$endpoint = 'https://api.tavily.com/search'
New-Item -ItemType Directory -Force -Path $temporaryDirectory | Out-Null

Write-Host 'Configure Tavily Web Search for LearnAgent.' -ForegroundColor Cyan
Write-Host "The key is validated once, saved only to $envFile, and never printed."
$secureKey = Read-Host 'Paste your Tavily API key (tvly-...)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
  $apiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}

if ([string]::IsNullOrWhiteSpace($apiKey)) { throw 'Tavily API key cannot be empty.' }
$apiKey = $apiKey.Trim()
if (-not $apiKey.StartsWith('tvly-')) {
  throw 'The Tavily API key should start with tvly-.'
}

Write-Host 'Validating the key with one basic search request...' -ForegroundColor DarkCyan
$requestBody = @{
  query = 'Java 21 official documentation'
  search_depth = 'basic'
  max_results = 1
  topic = 'general'
  include_answer = $false
  include_raw_content = $false
  include_published_date = $true
} | ConvertTo-Json

try {
  $validation = Invoke-RestMethod -Method Post -Uri $endpoint `
    -Headers @{ Authorization = "Bearer $apiKey" } `
    -ContentType 'application/json' -Body $requestBody -TimeoutSec 30
} catch {
  $apiKey = $null
  throw "Tavily key validation failed: $($_.Exception.Message)"
}

if (-not $validation.results -or @($validation.results).Count -eq 0) {
  $apiKey = $null
  throw 'Tavily accepted the request but returned no validation result.'
}

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

Set-DotEnvValue 'TAVILY_API_KEY' $apiKey
Set-DotEnvValue 'TAVILY_ENDPOINT' $endpoint

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

$firstResult = @($validation.results)[0]
Write-Host 'Tavily Web Search configured and validated successfully.' -ForegroundColor Green
Write-Host "Validation result: $($firstResult.title)"
Write-Host 'Restart the LearnAgent backend so it loads the new key.' -ForegroundColor Yellow
Write-Host 'Do not paste the key into chat, source files, screenshots, or logs.' -ForegroundColor Yellow
