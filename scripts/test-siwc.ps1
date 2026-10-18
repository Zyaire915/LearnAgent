[CmdletBinding()]
param(
  [switch]$SkipBuild,
  [switch]$AuthorizeOnly
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$moduleRoot = Join-Path $projectRoot 'siwc-smoke-test'
$mavenCache = Join-Path $projectRoot '.mvn-cache'
$runtimeDirectory = Join-Path $projectRoot '.runtime\siwc'
$temporaryDirectory = Join-Path $projectRoot '.tmp'
$wrapper = Join-Path $projectRoot 'mvnw.cmd'

New-Item -ItemType Directory -Force -Path $mavenCache, $runtimeDirectory, $temporaryDirectory | Out-Null
$env:MAVEN_USER_HOME = $mavenCache
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$temporaryDirectory"

if (-not $SkipBuild) {
  Write-Host 'Building the isolated SIWC smoke test...' -ForegroundColor Cyan
  & $wrapper "-Dmaven.repo.local=$mavenCache" -f (Join-Path $moduleRoot 'pom.xml') clean package
  if ($LASTEXITCODE -ne 0) { throw "SIWC smoke-test build failed with exit code $LASTEXITCODE." }
}

$jar = Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'target') -Filter 'siwc-smoke-test-*.jar' |
  Where-Object { -not $_.Name.EndsWith('.jar.original') } |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1
if ($null -eq $jar) { throw 'No packaged SIWC smoke-test JAR was found. Run without -SkipBuild first.' }

Write-Host 'Starting Sign in with ChatGPT verification...' -ForegroundColor Cyan
if ($AuthorizeOnly) {
  & java "-Dsiwc.data-dir=$runtimeDirectory" -jar $jar.FullName --authorize-only
} else {
  & java "-Dsiwc.data-dir=$runtimeDirectory" -jar $jar.FullName
}
exit $LASTEXITCODE
