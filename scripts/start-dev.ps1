$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'start-db.ps1')
Start-Process powershell.exe -ArgumentList '-NoExit','-ExecutionPolicy','Bypass','-File',(Join-Path $PSScriptRoot 'start-backend.ps1')
Start-Sleep -Seconds 2
Start-Process powershell.exe -ArgumentList '-NoExit','-ExecutionPolicy','Bypass','-File',(Join-Path $PSScriptRoot 'start-frontend.ps1')
Write-Host 'Development services launched. Open http://localhost:5173' -ForegroundColor Green
