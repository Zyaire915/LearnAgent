$ErrorActionPreference = 'Stop'

$distro = 'Ubuntu-22.04'
Write-Host 'Starting PostgreSQL 16 in Ubuntu-22.04 (data stored in D:\WSL\Ubuntu-22.04)...'

$ErrorActionPreference = 'SilentlyContinue'
& wsl.exe -d $distro -- sudo pg_ctlcluster 16 main start *> $null
$ErrorActionPreference = 'Stop'

$ready = $false
$ErrorActionPreference = 'SilentlyContinue'
for ($attempt = 1; $attempt -le 20; $attempt++) {
  & wsl.exe -d $distro -- pg_isready -h 127.0.0.1 -p 5432 -U learnagent -d learnagent *> $null
  if ($LASTEXITCODE -eq 0) {
    $ready = $true
    break
  }
  Start-Sleep -Milliseconds 500
}
$ErrorActionPreference = 'Stop'

if (-not $ready) {
  throw 'PostgreSQL did not become ready within 10 seconds.'
}

$tcp = Test-NetConnection -ComputerName localhost -Port 5432 -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
  throw 'PostgreSQL is ready inside WSL, but Windows localhost:5432 is unavailable.'
}

Write-Host 'Database Ready: PostgreSQL 16 + pgvector on localhost:5432' -ForegroundColor Green
