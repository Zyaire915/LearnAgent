# =====================================================================
# LearnAgent 停止器（feature/windows-launcher）
#
# 停止启动器拉起的后端进程（可选同时停止内置数据库）。
# 重要：本脚本不会删除任何用户数据。
#   * 学习记录、课件、工作区、缓存、日志保留在数据目录（默认 %LOCALAPPDATA%\LearnAgent）；
#   * 内置数据库的数据保留在 <数据目录>\pgdata；
#   * 开发模式数据库数据保留在 WSL 或 Docker/Podman 数据卷中。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File launcher\Stop-LearnAgent.ps1
#   powershell -ExecutionPolicy Bypass -File launcher\Stop-LearnAgent.ps1 -StopDatabase
# =====================================================================
[CmdletBinding()]
param(
  [string]$DataRoot = '',
  [string]$SettingsFile = '',
  [string]$ProjectRoot = '',
  [int]$BackendPort = 0,
  [switch]$StopDatabase,
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Launcher.Common.ps1')

$context = Get-LauncherContext -ScriptDirectory $PSScriptRoot -RequestedProjectRoot $ProjectRoot `
  -RequestedDataRoot $DataRoot -RequestedSettingsFile $SettingsFile -RequestedBackendPort $BackendPort
$projectRoot = $context.ProjectRoot
$dataRoot = $context.DataRoot
$backendPort = $context.BackendPort
$pgPort = $context.PgPort
$mode = $context.Mode

if (-not $DryRun) { Initialize-LauncherLog -DataRoot $dataRoot }

Write-Host ''
Write-Host '==============================================' -ForegroundColor Cyan
Write-Host '           LearnAgent 停止器' -ForegroundColor Cyan
Write-Host '==============================================' -ForegroundColor Cyan
Write-Host ("  程序目录：{0}" -f $projectRoot)
Write-Host ("  数据目录：{0}（停止后全部保留）" -f $dataRoot)
if ($DryRun) { Write-Host '  [演练模式] 仅打印停止计划，不执行任何操作。' -ForegroundColor Yellow }

$stoppedAnything = $false

# 1. 后端：先按端口结束监听进程，再按 PID 文件兜底
if ($DryRun) {
  Write-Host ("（演练）将停止监听 {0} 端口的 LearnAgent 后端（若在运行）。" -f $backendPort)
} else {
  $killed = Stop-ProcessListeningOnPort -Port $backendPort
  if ($killed.Count -gt 0) {
    Write-LauncherStep ("已停止后端进程（PID：{0}）。" -f ($killed -join ', ')) -Color Green
    $stoppedAnything = $true
  } else {
    $result = Stop-ProcessFromPidFile -PidFile (Join-LauncherPath -Base $dataRoot -Child 'run\backend.pid')
    if ($result.Killed) {
      Write-LauncherStep ("已停止后端进程（PID {0}）。" -f $result.Pid) -Color Green
      $stoppedAnything = $true
    } else {
      Write-LauncherInfo '后端未在运行。'
    }
  }
}

# 2. 清理 PID 记录（互斥量由操作系统在进程退出时自动释放，无需人工清理）
if (-not $DryRun) {
  foreach ($pidFile in @('backend.pid')) {
    $path = Join-LauncherPath -Base $dataRoot -Child ('run\' + $pidFile)
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue }
  }
}

# 3. 数据库（可选）
if ($StopDatabase) {
  $pgBin = Get-PortablePostgreSqlBin -ProjectRoot $projectRoot
  $pgDataDir = Join-LauncherPath -Base $dataRoot -Child 'pgdata'
  if ($pgBin -and (Test-Path -LiteralPath (Join-LauncherPath -Base $pgDataDir -Child 'PG_VERSION'))) {
    if (Stop-PortablePostgreSql -PgBin $pgBin -PgData $pgDataDir -Port $pgPort -DryRun:$DryRun) { $stoppedAnything = $true }
  } elseif ($pgBin) {
    Write-LauncherInfo '内置数据库尚未初始化（首次启动 LearnAgent.exe 后才会创建），无需停止。'
  } else {
    $composeFile = Join-Path $projectRoot 'docker-compose.yml'
    if (-not (Test-Path -LiteralPath $composeFile)) {
      Write-LauncherInfo ("未找到 {0}，跳过数据库容器停止。" -f $composeFile)
    } else {
      $docker = Get-Command docker -ErrorAction SilentlyContinue
      $podman = Get-Command podman -ErrorAction SilentlyContinue
      if ($docker -or $podman) {
        $engine = $(if ($docker) { 'docker' } else { 'podman' })
        if ($DryRun) {
          Write-Host ("（演练）将执行 {0} compose -f `"{1}`" stop postgres（仅停止容器，数据卷保留）。" -f $engine, $composeFile)
        } else {
          & $engine compose -f $composeFile stop postgres
          if ($LASTEXITCODE -eq 0) {
            Write-LauncherInfo '数据库容器已停止；数据仍保存在数据卷中，不会丢失。' -Color Green
            $stoppedAnything = $true
          } else {
            Write-LauncherInfo '数据库容器停止失败（可能本就未启动，可忽略）。' -Color Yellow
          }
        }
      } else {
        Write-LauncherInfo '未检测到 Docker/Podman；开发模式数据库可能运行在 WSL 中，默认保持运行（数据不受影响）。'
      }
    }
  }
} else {
  Write-LauncherInfo '数据库保持运行（如需同时停止，请加 -StopDatabase 参数）。'
}

Write-Host ''
if ($stoppedAnything) { Write-Host 'LearnAgent 已停止。' -ForegroundColor Green }
else { Write-Host '没有发现需要停止的 LearnAgent 进程。' }
Write-Host '用户数据已全部保留：'
Write-Host ("  - 数据目录：{0}" -f $dataRoot)
Write-Host '    （学习记录、课件、工作区、缓存、日志；内置数据库数据在其 pgdata 子目录）'
Write-Host '如需彻底删除数据，请先备份后手动删除上述目录。'
