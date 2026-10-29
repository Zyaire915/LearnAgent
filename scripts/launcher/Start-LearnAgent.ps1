# =====================================================================
# LearnAgent Windows 一键启动器（feature/windows-launcher）
#
# 双击 LearnAgent.exe（jpackage 打包）或运行本脚本后：
#   1. 检查依赖与端口，给出中文提示；
#   2. 启动内置便携 PostgreSQL 16 + pgvector（首次运行自动 initdb，数据在 LOCALAPPDATA）；
#   3. 用内置 JRE 运行后端 JAR，并由后端直接托管已构建的 Vue 前端（无需 Node.js）；
#   4. 自动打开浏览器 http://127.0.0.1:8080/；
#   5. 重复双击只会再打开一个页面，不会启动第二份后端/数据库进程。
#
# 数据目录：%LOCALAPPDATA%\LearnAgent（缓存、模型、工作区、日志、pgdata 全在其中）。
# 密钥：只从 <数据目录>\learnagent.env 或源码仓库 .env 读取，本脚本与 EXE 都不含 Key。
#
# 用法示例：
#   powershell -ExecutionPolicy Bypass -File launcher\Start-LearnAgent.ps1
#   powershell -ExecutionPolicy Bypass -File launcher\Start-LearnAgent.ps1 -DryRun   # 只打印计划
# =====================================================================
[CmdletBinding()]
param(
  [string]$DataRoot = '',
  [string]$SettingsFile = '',
  [string]$ProjectRoot = '',
  [string]$JavaPath = '',
  [string]$JarPath = '',
  [string]$FrontendDist = '',
  [int]$BackendPort = 0,
  [switch]$NoBrowser,
  [switch]$PopupOnError,
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Launcher.Common.ps1')
try { $Host.UI.RawUI.WindowTitle = 'LearnAgent 启动器' } catch { }

$mutexHandle = $null
$startedByLauncher = $false
$usePopup = $false
$openBrowser = $true
$demoMode = $true

function Wait-LearnAgentReady {
  param([int]$Port, [int]$TimeoutSeconds)
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $status = Get-LearnAgentStatus -Port $Port
    if ($status) { return $status }
    Start-Sleep -Milliseconds 1000
  }
  return $null
}

try {
  # ------------------------------------------------------------------
  # 1. 目录、设置与日志
  # ------------------------------------------------------------------
  $context = Get-LauncherContext -ScriptDirectory $PSScriptRoot -RequestedProjectRoot $ProjectRoot `
    -RequestedDataRoot $DataRoot -RequestedSettingsFile $SettingsFile -RequestedBackendPort $BackendPort
  $projectRoot = $context.ProjectRoot
  $dataRoot = $context.DataRoot
  $backendPort = $context.BackendPort
  $pgPort = $context.PgPort
  $mode = $context.Mode

  if ($mode -eq 'unknown') {
    Stop-Launcher ("无法识别 LearnAgent 的程序目录：{0}`n`n请确认发布包已完整解压（应包含 LearnAgent.exe、app、launcher、pgsql、runtime 等目录），`n或在源码仓库中运行 scripts\launcher\Start-LearnAgent.ps1。" -f $projectRoot)
  }

  if (-not $DryRun) {
    New-LauncherDataDirectories -DataRoot $dataRoot
    Initialize-LauncherLog -DataRoot $dataRoot
  }

  $openBrowser = $true
  if ($NoBrowser) { $openBrowser = $false }
  elseif ($context.Settings.ContainsKey('openBrowser')) { $openBrowser = [bool]$context.Settings['openBrowser'] }
  $usePopup = $PopupOnError.IsPresent
  if (-not $PopupOnError -and $context.Settings.ContainsKey('popupOnError')) { $usePopup = [bool]$context.Settings['popupOnError'] }
  $demoMode = $true
  if ($context.Settings.ContainsKey('demoMode')) { $demoMode = [bool]$context.Settings['demoMode'] }

  $entryUrl = "http://127.0.0.1:{0}/" -f $backendPort
  # 打开浏览器时带一个时间戳参数：浏览器不会复用已打开的旧标签页（旧页面可能已缓存旧前端）
  $browserUrl = ("http://127.0.0.1:{0}/?launch={1}" -f $backendPort, (Get-Date -Format 'yyyyMMddHHmmss'))
  $backendLogDir = Join-LauncherPath -Base $dataRoot -Child 'logs'

  Write-Host ''
  Write-Host '==============================================' -ForegroundColor Cyan
  Write-Host '        LearnAgent  Windows  一键启动' -ForegroundColor Cyan
  Write-Host '==============================================' -ForegroundColor Cyan
  Write-Host ("  程序目录：{0}" -f $projectRoot)
  Write-Host ("  数据目录：{0}" -f $dataRoot)
  Write-Host ("  运行模式：{0}" -f $(if ($mode -eq 'portable') { '便携模式（内置 Java 运行时 + 内置数据库）' } else { '开发模式（复用本机 Java / 数据库）' }))
  if ($DryRun) { Write-Host '  [演练模式] 仅打印启动计划，不实际启动任何进程。' -ForegroundColor Yellow }
  Write-Host '==============================================' -ForegroundColor Cyan
  Write-LauncherLog -Message ("程序目录={0} 数据目录={1} 模式={2} 后端端口={3} 数据库端口={4}" -f $projectRoot, $dataRoot, $mode, $backendPort, $pgPort)

  # ------------------------------------------------------------------
  # 2. 单实例保护：重复双击不会启动第二份进程
  # ------------------------------------------------------------------
  if (-not $DryRun) {
    $mutexHandle = Enter-LauncherMutex -TimeoutSeconds 0
    if (-not $mutexHandle.Acquired) {
      Write-LauncherStep '检测到另一个 LearnAgent 启动器正在启动，本次不会重复启动进程。'
      Write-LauncherInfo '正在等待它完成启动（首次运行需要初始化数据库，可能要几十秒）...'
      $status = Wait-LearnAgentReady -Port $backendPort -TimeoutSeconds 300
      if ($status) {
        Write-LauncherInfo ("LearnAgent 已就绪，直接打开页面：{0}" -f $entryUrl) -Color Green
        if ($openBrowser) { [void](Open-LauncherUrl -Url $browserUrl) }
        exit 0
      }
      Stop-Launcher ("另一个启动器尚未就绪。请稍候再双击，或查看日志：{0}" -f $backendLogDir)
    }
  }

  # ------------------------------------------------------------------
  # 3. 已在运行：只打开页面
  # ------------------------------------------------------------------
  if (-not $DryRun) {
    $existing = Get-LearnAgentStatus -Port $backendPort
    if ($existing) {
      Write-LauncherStep ("LearnAgent 已在运行（数据库连接：{0}），不会重复启动进程。" -f $existing.databaseConnected) -Color Green
      if ($openBrowser) { [void](Open-LauncherUrl -Url $browserUrl) }
      Write-Host ("  访问地址：{0}" -f $entryUrl)
      exit 0
    }
    if (Test-PortOpen -Port $backendPort) {
      Stop-Launcher ("端口 {0} 已被其他程序占用，且不是 LearnAgent。`n`n处理办法：关闭占用该端口的程序后重试；`n或把 launcher\launcher-settings.json 中的 backendPort 改成其他端口（如 8081）。" -f $backendPort)
    }
  }

  # ------------------------------------------------------------------
  # 4. 配置：数据目录 learnagent.env 优先于项目 .env；缓存目录统一重定向
  # ------------------------------------------------------------------
  $applied = 0
  $applied += (Import-LauncherEnvFile -EnvFile (Join-Path $projectRoot '.env'))
  $applied += (Import-LauncherEnvFile -EnvFile (Join-LauncherPath -Base $dataRoot -Child 'learnagent.env') -OverrideExisting)
  [void](Set-LauncherDataEnvironment -DataRoot $dataRoot)
  if ($applied -gt 0) { Write-LauncherInfo ("已读取 {0} 项 AI/数据库配置（来自 learnagent.env 或 .env）。" -f $applied) }

  # ------------------------------------------------------------------
  # 5. 数据库
  # ------------------------------------------------------------------
  $pgBin = Get-PortablePostgreSqlBin -ProjectRoot $projectRoot
  if ($pgBin) {
    $pgDataDir = Join-LauncherPath -Base $dataRoot -Child 'pgdata'
    if ($DryRun) {
      Write-LauncherInfo ("（演练）数据库：内置便携 PostgreSQL，监听 127.0.0.1:{0}，数据目录 {1}" -f $pgPort, $pgDataDir)
    } else {
      # PostgreSQL 的 initdb / 服务进程在 Windows 上不支持含中文等非 ASCII 字符的路径，
      # 提前给出明确提示，避免出现难以理解的编码错误。
      if ($pgDataDir -match '[^\x00-\x7F]' -or $projectRoot -match '[^\x00-\x7F]') {
        Stop-Launcher ("当前路径包含中文或其它非 ASCII 字符，内置 PostgreSQL 无法初始化：`n  程序目录：{0}`n  数据目录：{1}`n`n处理办法（任选其一）：`n  1) 把发布包解压到纯英文路径（例如 D:\LearnAgent）后重新双击 LearnAgent.exe；`n  2) 如果 Windows 用户名是中文，请在 launcher\launcher-settings.json 中把 dataRoot`n     指定为纯英文目录（例如 D:\LearnAgent\data），再重新启动。`n`n说明：这是 PostgreSQL 在 Windows 上的已知限制（路径按 ANSI 代码页传递）。" -f $projectRoot, $dataRoot)
      }      if (-not (Test-Path -LiteralPath (Join-Path $pgDataDir 'PG_VERSION'))) {
        Initialize-PortablePostgreSql -PgBin $pgBin -PgData $pgDataDir -SuperUser 'learnagent' -Password 'learnagent' `
          -TempDirectory (Join-LauncherPath -Base $dataRoot -Child 'tmp')
      }
      [void](Start-PortablePostgreSql -PgBin $pgBin -PgData $pgDataDir -SuperUser 'learnagent' -Password 'learnagent' `
        -Port $pgPort -LogDirectory $backendLogDir)
      [Environment]::SetEnvironmentVariable('DB_URL', ("jdbc:postgresql://127.0.0.1:{0}/learnagent" -f $pgPort), 'Process')
      [Environment]::SetEnvironmentVariable('DB_USERNAME', 'learnagent', 'Process')
      [Environment]::SetEnvironmentVariable('DB_PASSWORD', 'learnagent', 'Process')
    }
  } else {
    # 源码仓库布局：复用本机 5432，或使用 docker compose 启动 pgvector/pgvector:pg16
    if ($DryRun) {
      Write-LauncherInfo '（演练）数据库：源码仓库布局，将复用 127.0.0.1:5432，或执行 docker compose up -d postgres。'
    } elseif (Test-PortOpen -Port 5432) {
      Write-LauncherInfo '已检测到本机 5432 端口有 PostgreSQL 在运行，直接复用（数据库连接取 learnagent.env / .env 配置）。' -Color Green
    } else {
      $composeFile = Join-Path $projectRoot 'docker-compose.yml'
      if (-not (Test-Path -LiteralPath $composeFile)) {
        Stop-Launcher ("未找到数据库：本机 5432 端口没有 PostgreSQL，也没有找到 {0}。`n`n请先启动开发数据库：powershell -ExecutionPolicy Bypass -File scripts\start-db.ps1`n或直接使用发布包（内置数据库，无需安装 PostgreSQL）。" -f $composeFile)
      }
      $docker = Get-Command docker -ErrorAction SilentlyContinue
      $podman = Get-Command podman -ErrorAction SilentlyContinue
      if (-not $docker -and -not $podman) {
        Stop-Launcher ("开发模式需要 PostgreSQL 16。本机 5432 端口没有数据库，也未检测到 Docker/Podman。`n`n处理办法（任选其一）：`n  1) 运行 scripts\start-db.ps1（WSL 中的 PostgreSQL 16 + pgvector）；`n  2) 安装并启动 Docker Desktop 后重新运行；`n  3) 使用发布包（内置数据库，目标电脑无需安装任何数据库）。")
      }
      $engine = $(if ($docker) { 'docker' } else { 'podman' })
      Write-LauncherStep ("使用 {0} 启动 PostgreSQL 16 + pgvector ..." -f $engine)
      Invoke-NativeCommand -FilePath $engine -Arguments @('compose', '-f', $composeFile, 'up', '-d', 'postgres')
      $composeExit = Get-NativeExitCode
      if ($composeExit -ne 0) {
        Stop-Launcher ("{0} compose 启动数据库失败（退出码 {1}）。`n请确认 {0} 已启动后重试；也可运行 scripts\start-db.ps1。" -f $engine, $composeExit)
      }
      $dbReady = $false
      for ($i = 0; $i -lt 120; $i++) {
        if (Test-PortOpen -Port 5432) { $dbReady = $true; break }
        Start-Sleep -Seconds 1
      }
      if (-not $dbReady) { Stop-Launcher '数据库未能在 120 秒内就绪（127.0.0.1:5432）。请检查容器状态后重试。' }
      Write-LauncherInfo '数据库已就绪：PostgreSQL 16 + pgvector（127.0.0.1:5432）。' -Color Green
    }
  }

  # ------------------------------------------------------------------
  # 6. 后端 JAR 与已构建前端
  # ------------------------------------------------------------------
  $jar = $JarPath
  if (-not $jar) {
    $patterns = @(
      (Join-Path $projectRoot 'app\backend-*.jar'),
      (Join-Path $projectRoot 'backend\target\backend-*.jar')
    )
    foreach ($pattern in $patterns) {
      $found = Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '\.original$' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
      if ($found) { $jar = $found.FullName; break }
    }
  }
  if (-not $jar -or -not (Test-Path -LiteralPath $jar)) {
    Stop-Launcher ("未找到后端 JAR（app\backend-*.jar）。`n发布包可能不完整，请重新解压；源码仓库请先运行 scripts\build-release.ps1，`n或对后端执行 .\mvnw.cmd -pl backend package。")
  }

  $dist = $FrontendDist
  if (-not $dist) {
    foreach ($candidate in @((Join-Path $projectRoot 'app\frontend'), (Join-Path $projectRoot 'frontend\dist'))) {
      if (Test-Path -LiteralPath (Join-Path $candidate 'index.html')) { $dist = $candidate; break }
    }
  }
  if (-not $dist -or -not (Test-Path -LiteralPath (Join-Path $dist 'index.html'))) {
    Stop-Launcher ("未找到已构建的前端页面（index.html）。`n发布包可能不完整，请重新解压；源码仓库请先运行 scripts\build-release.ps1 构建前端。")
  }

  # 前端产物自检：index.html 引用的构建资源必须存在，否则页面会整片空白
  $assetRefs = @()
  try {
    $indexHtml = Get-Content -LiteralPath (Join-Path $dist 'index.html') -Raw
    foreach ($match in [regex]::Matches($indexHtml, '(?:src|href)="(/[^"]+)"')) { $assetRefs += $match.Groups[1].Value }
  } catch { }
  $missingLocal = @()
  foreach ($ref in $assetRefs) {
    if ($ref -notlike '/assets/*') { continue }
    $relative = $ref.TrimStart('/').Split('?')[0]
    if (-not (Test-Path -LiteralPath (Join-Path $dist $relative))) { $missingLocal += $ref }
  }
  if ($missingLocal.Count -gt 0) {
    Stop-Launcher ("前端产物不完整：index.html 引用以下文件但 {0} 中不存在：`n  {1}`n请重新解压发布包，或重新运行 scripts\build-release.ps1。" -f $dist, ($missingLocal -join "`n  "))
  }
  if ($assetRefs.Count -gt 0) { Write-LauncherInfo ("前端产物自检通过（{0} 个构建资源均存在）。" -f $assetRefs.Count) }

  # ------------------------------------------------------------------
  # 7. Java 与运行参数
  # ------------------------------------------------------------------
  $javaInfo = Resolve-JavaExecutable -RequestedJava $JavaPath -ProjectRoot $projectRoot
  if (-not $javaInfo) {
    Stop-Launcher ("未找到可用的 Java 21+ 运行时。`n`n发布包自带 runtime 目录，请确认解压完整；源码仓库请安装 JDK 21 并设置 JAVA_HOME。")
  }
  Write-LauncherInfo ("Java {0}：{1}" -f $javaInfo.Version, $javaInfo.Path)

  [Environment]::SetEnvironmentVariable('SERVER_ADDRESS', '127.0.0.1', 'Process')
  [Environment]::SetEnvironmentVariable('SERVER_PORT', [string]$backendPort, 'Process')
  if ($demoMode) {
    $existingProfiles = [Environment]::GetEnvironmentVariable('SPRING_PROFILES_ACTIVE', 'Process')
    [Environment]::SetEnvironmentVariable('SPRING_PROFILES_ACTIVE', (Merge-SpringProfiles -ExistingProfiles $existingProfiles -RequiredProfile 'demo'), 'Process')
  }
  $distUri = ([System.Uri](([System.IO.Path]::GetFullPath($dist)).TrimEnd('\') + '\')).AbsoluteUri
  [Environment]::SetEnvironmentVariable('SPRING_WEB_RESOURCES_STATIC_LOCATIONS', $distUri, 'Process')
  [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', ("-Djava.io.tmpdir={0}" -f (Join-LauncherPath -Base $dataRoot -Child 'tmp')), 'Process')
  Write-LauncherLog -Message ("SPRING_WEB_RESOURCES_STATIC_LOCATIONS={0}" -f $distUri)

  if ($DryRun) {
    Write-LauncherInfo ("（演练）后端：{0} -jar {1}" -f $javaInfo.Path, $jar)
    Write-LauncherInfo ("（演练）前端静态目录：{0}" -f $distUri)
    Write-LauncherInfo ("（演练）浏览器地址：{0}" -f $entryUrl)
    Write-Host ''
    Write-Host '演练结束：未启动任何进程。' -ForegroundColor Yellow
    exit 0
  }

  # ------------------------------------------------------------------
  # 8. 启动后端并等待就绪
  # ------------------------------------------------------------------
  Write-LauncherStep '启动 LearnAgent 后端（首次启动需要初始化数据库，约 20-60 秒）...'
  $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
  $outLog = Join-Path $backendLogDir ("backend-{0}.out.log" -f $stamp)
  $errLog = Join-Path $backendLogDir ("backend-{0}.err.log" -f $stamp)
  $startParams = @{
    FilePath               = $javaInfo.Path
    ArgumentList           = @('-XX:+ExitOnOutOfMemoryError', '-Dfile.encoding=UTF-8', '-jar', ('"{0}"' -f $jar))
    WorkingDirectory       = $projectRoot
    RedirectStandardOutput = $outLog
    RedirectStandardError  = $errLog
    PassThru               = $true
    WindowStyle            = 'Hidden'
  }
  $proc = Start-Process @startParams
  $startedByLauncher = $true
  Set-Content -LiteralPath (Join-LauncherPath -Base $dataRoot -Child 'run\backend.pid') -Value $proc.Id -Encoding UTF8
  Write-LauncherInfo ("后端进程 PID {0}；日志：{1}" -f $proc.Id, $backendLogDir)

  $status = $null
  for ($i = 0; $i -lt 150; $i++) {
    Start-Sleep -Seconds 2
    if ($proc.HasExited) {
      $tail = ''
      try { $tail = (Get-Content -LiteralPath $errLog -Tail 15 -ErrorAction SilentlyContinue) -join "`n" } catch { }
      Stop-Launcher ("后端进程提前退出（退出码 {0}）。`n`n错误日志（最后几行）：`n{1}`n`n完整日志：{2}" -f $proc.ExitCode, $tail, $errLog)
    }
    $status = Get-LearnAgentStatus -Port $backendPort
    if ($status) { break }
    if ($i -gt 0 -and $i % 10 -eq 0) { Write-LauncherInfo ("  仍在启动中...（{0} 秒）" -f ($i * 2)) }
  }
  if (-not $status) {
    Stop-Launcher ("后端未能在 300 秒内就绪（{0} 无响应）。`n`n请查看错误日志：{1}" -f $entryUrl, $errLog)
  }

  if (-not $status.databaseConnected) {
    Stop-Launcher ("后端已启动，但数据库连接失败（databaseConnected=false）。`n`n请查看日志：{0}`n若使用内置数据库，可运行 launcher\Stop-LearnAgent.ps1 -StopDatabase 后重试。" -f $backendLogDir)
  }

  # HTTP 级页面自检：避免「浏览器打开但页面空白」
  $badRefs = @()
  foreach ($ref in $assetRefs) {
    if ($ref -notlike '/assets/*') { continue }
    try {
      $resp = Invoke-WebRequest -Uri ("http://127.0.0.1:{0}{1}" -f $backendPort, $ref) -Method Head -TimeoutSec 5 -UseBasicParsing
      if ($resp.StatusCode -ne 200) { $badRefs += ("{0}（HTTP {1}）" -f $ref, [int]$resp.StatusCode) }
    } catch {
      $code = $null
      if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
      $badRefs += ("{0}（{1}）" -f $ref, $(if ($code) { "HTTP $code" } else { '无响应' }))
    }
  }
  if ($badRefs.Count -gt 0) {
    Stop-Launcher ("页面资源无法通过后端访问，浏览器会显示空白页：`n  {0}`n请重新解压发布包或重新构建后再试。" -f ($badRefs -join "`n  "))
  }

  if (-not $status.llmConfigured) {
    Write-LauncherInfo '提示：尚未配置 AI Key，AI 对话/联网搜索不可用；课程、任务、测验、复习、工作台等功能正常。' -Color Yellow
    Write-LauncherInfo ("      需要 AI 时，把 launcher\learnagent.env.example 复制为 {0} 并填写 Key 后重启。" -f (Join-LauncherPath -Base $dataRoot -Child 'learnagent.env')) -Color Yellow
  }

  # ------------------------------------------------------------------
  # 9. 打开浏览器与摘要
  # ------------------------------------------------------------------
  if ($openBrowser) {
    if (Open-LauncherUrl -Url $browserUrl) { Write-LauncherStep ("已在默认浏览器打开：{0}" -f $entryUrl) -Color Green }
    else { Write-LauncherInfo ("无法自动打开浏览器，请手动访问：{0}" -f $entryUrl) -Color Yellow }
  }

  Write-Host ''
  Write-Host 'LearnAgent 启动完成。' -ForegroundColor Green
  Write-Host ("  访问地址：{0}" -f $entryUrl)
  Write-Host ("  数据目录：{0}" -f $dataRoot)
  Write-Host '            （学习记录、课件、缓存、日志与内置数据库都在这里，删除程序不会丢失）'
  Write-Host ("  停止方式：运行 {0}\Stop-LearnAgent.ps1（不会删除任何学习数据）" -f $PSScriptRoot)
  Write-Host ''
} catch {
  $message = $_.Exception.Message
  if ($message -like 'LAUNCHER_FATAL: *') {
    $message = $message.Substring('LAUNCHER_FATAL: '.Length)
  } else {
    $message = "发生未预期的错误：{0}`n{1}" -f $message, $_.InvocationInfo.PositionMessage
  }
  Show-LauncherError -Title 'LearnAgent 启动失败' -Message $message -Popup:$usePopup
  exit 1
} finally {
  Exit-LauncherMutex -Handle $mutexHandle
}

# 由 LearnAgent.exe 拉起时，窗口在脚本结束后会自动关闭；留几秒让用户看清摘要。
if ($env:LEARNAGENT_LAUNCHER_EXE -and -not $DryRun) { Start-Sleep -Seconds 6 }
