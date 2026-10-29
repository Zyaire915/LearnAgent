# =====================================================================
# LearnAgent 启动器公共函数库（feature/windows-launcher）
#
# 被 Start-LearnAgent.ps1 与 Stop-LearnAgent.ps1 复用。
# 兼容 Windows PowerShell 5.1（目标电脑自带）与 PowerShell 7+。
#
# 设计要点：
#   * 发布包（含 pgsql\）与源码仓库两种布局都能自动识别，用户无需选择模式；
#   * 缓存、模型、日志、工作区与数据库数据统一放在数据目录
#     （默认 %LOCALAPPDATA%\LearnAgent）；
#   * 数据库使用发布包内置的便携 PostgreSQL 16 + pgvector，目标电脑无需安装
#     PostgreSQL，也无需 WSL / Docker；
#   * 调用外部程序统一走 Invoke-NativeProcess：Windows PowerShell 5.1 的
#     「& 命令 参数」会按 ANSI 代码页转换参数，中文/空格路径会损坏；
#     通过 ProcessStartInfo 传参才能保留 Unicode（中文目录、中文用户名都能用）；
#   * 本文件不包含任何 API Key，密钥只从数据目录 learnagent.env 或项目 .env 读取。
# =====================================================================

$script:LearnAgentLogFile = $null
$script:LastNativeExitCode = 0
$script:LastNativeOutput = ''
$script:LastNativeError = ''

function Join-LauncherPath {
  <#
    .SYNOPSIS
    安全拼接数据目录子路径（数据目录可能位于任意盘符，含中文与空格）。
  #>
  param(
    [Parameter(Mandatory = $true)][string]$Base,
    [Parameter(Mandatory = $true)][string]$Child
  )
  return ($Base.TrimEnd('\', '/') + '\' + $Child.TrimStart('\', '/'))
}

function Initialize-LauncherLog {
  <#
    .SYNOPSIS
    把启动器输出同时记录到 <数据目录>\logs\launcher-<日期>.log，便于事后排查。
  #>
  param([Parameter(Mandatory = $true)][string]$DataRoot)
  try {
    $logDirectory = Join-LauncherPath -Base $DataRoot -Child 'logs'
    New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
    $script:LearnAgentLogFile = Join-LauncherPath -Base $logDirectory -Child ("launcher-{0}.log" -f (Get-Date -Format 'yyyyMMdd'))
    Add-Content -LiteralPath $script:LearnAgentLogFile -Encoding UTF8 -Value ("===== {0} 启动器会话开始（PID {1}）=====" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $PID)
  } catch {
    $script:LearnAgentLogFile = $null
  }
}

function Write-LauncherLog {
  param(
    [Parameter(Mandatory = $true)][string]$Message,
    [ValidateSet('INFO', 'WARN', 'ERROR')][string]$Level = 'INFO'
  )
  if (-not $script:LearnAgentLogFile) { return }
  try {
    Add-Content -LiteralPath $script:LearnAgentLogFile -Encoding UTF8 -Value ("[{0}] [{1}] {2}" -f (Get-Date -Format 'HH:mm:ss'), $Level, $Message)
  } catch { }
}

function Write-LauncherStep {
  <#
    .SYNOPSIS
    统一的「步骤」输出：控制台可见 + 写入启动器日志。
  #>
  param(
    [Parameter(Mandatory = $true)][string]$Message,
    [ConsoleColor]$Color = [ConsoleColor]::Cyan
  )
  Write-Host ("==> {0}" -f $Message) -ForegroundColor $Color
  Write-LauncherLog -Message $Message
}

function Write-LauncherInfo {
  param([Parameter(Mandatory = $true)][string]$Message, [ConsoleColor]$Color = [ConsoleColor]::Gray)
  Write-Host ("    {0}" -f $Message) -ForegroundColor $Color
  Write-LauncherLog -Message $Message
}

function Get-LauncherContext {
  <#
    .SYNOPSIS
    解析项目目录、数据目录、启动器设置与端口。发布包与源码仓库两种布局都适用。
  #>
  param(
    [Parameter(Mandatory = $true)][string]$ScriptDirectory,
    [string]$RequestedProjectRoot = '',
    [string]$RequestedDataRoot = '',
    [string]$RequestedSettingsFile = '',
    [int]$RequestedBackendPort = 0
  )

  $context = @{
    ProjectRoot  = ''
    DataRoot     = ''
    Mode         = 'portable'
    Settings     = @{}
    SettingsFile = ''
    BackendPort  = 8080
    PgPort       = 5433
  }

  $context.ProjectRoot = Resolve-ProjectRootPath -ScriptDirectory $ScriptDirectory -RequestedRoot $RequestedProjectRoot

  if ($RequestedSettingsFile) {
    $context.SettingsFile = $RequestedSettingsFile
  } else {
    $context.SettingsFile = Join-Path $ScriptDirectory 'launcher-settings.json'
  }
  $context.Settings = Read-LauncherSettings -SettingsFile $context.SettingsFile

  $context.DataRoot = Resolve-DataRootPath -RequestedRoot $RequestedDataRoot -Settings $context.Settings -AppRoot $context.ProjectRoot
  $context.Mode = Resolve-LauncherMode -ProjectRoot $context.ProjectRoot

  if ($RequestedBackendPort -gt 0) {
    $context.BackendPort = $RequestedBackendPort
  } elseif ($context.Settings.ContainsKey('backendPort') -and [int]$context.Settings['backendPort'] -gt 0) {
    $context.BackendPort = [int]$context.Settings['backendPort']
  }
  if ($context.Settings.ContainsKey('pgPort') -and [int]$context.Settings['pgPort'] -gt 0) {
    $context.PgPort = [int]$context.Settings['pgPort']
  }
  return $context
}

function Resolve-LauncherMode {
  <#
    .SYNOPSIS
    发布包（含 pgsql\bin\postgres.exe 或 app\）= 便携/演示模式；
    源码仓库（含 mvnw.cmd）= 开发模式。用户无需手动选择。
  #>
  param([Parameter(Mandatory = $true)][string]$ProjectRoot)
  if (Test-Path -LiteralPath (Join-Path $ProjectRoot 'pgsql\bin\postgres.exe')) { return 'portable' }
  if (Test-Path -LiteralPath (Join-Path $ProjectRoot 'app')) { return 'portable' }
  if (Test-Path -LiteralPath (Join-Path $ProjectRoot 'mvnw.cmd')) { return 'source' }
  return 'unknown'
}

function Resolve-ProjectRootPath {
  param(
    [Parameter(Mandatory = $true)][string]$ScriptDirectory,
    [string]$RequestedRoot = ''
  )
  if ($RequestedRoot) { return (Resolve-AbsolutePath -Path $RequestedRoot) }

  # 发布包布局：<发布目录>\launcher\Start-LearnAgent.ps1
  $releaseCandidate = Split-Path -Parent $ScriptDirectory
  # 源码仓库布局：<仓库>\scripts\launcher\Start-LearnAgent.ps1
  $repoCandidate = Split-Path -Parent (Split-Path -Parent $ScriptDirectory)

  if (Test-Path -LiteralPath (Join-Path $releaseCandidate 'runtime\bin\java.exe')) { return $releaseCandidate }
  if (Test-Path -LiteralPath (Join-Path $releaseCandidate 'app')) { return $releaseCandidate }
  if (Test-Path -LiteralPath (Join-Path $releaseCandidate 'pgsql')) { return $releaseCandidate }
  if (Test-Path -LiteralPath (Join-Path $repoCandidate 'mvnw.cmd')) { return $repoCandidate }
  return $releaseCandidate
}

function Resolve-AbsolutePath {
  param([Parameter(Mandatory = $true)][string]$Path)
  if ([System.IO.Path]::IsPathRooted($Path)) {
    return [System.IO.Path]::GetFullPath($Path)
  }
  return [System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $Path))
}

function Read-LauncherSettings {
  <#
    .SYNOPSIS
    读取 launcher\launcher-settings.json（可选）。缺失或损坏时使用默认值，绝不阻断启动。
  #>
  param([string]$SettingsFile)
  $result = @{}
  if (-not $SettingsFile -or -not (Test-Path -LiteralPath $SettingsFile)) { return $result }
  try {
    $json = Get-Content -LiteralPath $SettingsFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($json.dataRoot) { $result['dataRoot'] = [string]$json.dataRoot }
    if ($json.backendPort) { $result['backendPort'] = [int]$json.backendPort }
    if ($json.pgPort) { $result['pgPort'] = [int]$json.pgPort }
    if ($null -ne $json.openBrowser) { $result['openBrowser'] = [bool]$json.openBrowser }
    if ($null -ne $json.popupOnError) { $result['popupOnError'] = [bool]$json.popupOnError }
    if ($null -ne $json.demoMode) { $result['demoMode'] = [bool]$json.demoMode }
  } catch {
    Write-Host ("警告：无法解析启动器设置文件 {0}（{1}），已忽略并使用默认设置。" -f $SettingsFile, $_.Exception.Message) -ForegroundColor Yellow
  }
  return $result
}

function Resolve-DataRootPath {
  <#
    .SYNOPSIS
    数据目录优先级：参数 > 设置文件 > %LOCALAPPDATA%\LearnAgent（验收要求：所有数据写入 LOCALAPPDATA）。
  #>
  param(
    [string]$RequestedRoot = '',
    [hashtable]$Settings = @{},
    [string]$AppRoot = ''
  )
  $root = $RequestedRoot
  if (-not $root -and $Settings -and $Settings['dataRoot']) { $root = [string]$Settings['dataRoot'] }
  if (-not $root) {
    if ($env:LOCALAPPDATA) {
      $root = Join-Path $env:LOCALAPPDATA 'LearnAgent'
    } elseif ($env:USERPROFILE) {
      $root = Join-Path $env:USERPROFILE 'AppData\Local\LearnAgent'
    } else {
      $root = Join-Path $AppRoot 'data'
    }
  }
  return $root.TrimEnd('\', '/')
}

function Test-PortOpen {
  param(
    [string]$ComputerName = '127.0.0.1',
    [Parameter(Mandatory = $true)][int]$Port,
    [int]$TimeoutMs = 1200
  )
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $async = $client.BeginConnect($ComputerName, $Port, $null, $null)
    $signalled = $async.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
    if ($signalled -and $client.Connected) { return $true }
    return $false
  } catch {
    return $false
  } finally {
    try { $client.Close() } catch { }
  }
}

function Get-LearnAgentStatus {
  <#
    .SYNOPSIS
    读取 /api/system/status。返回状态对象；不是 LearnAgent 或不可达时返回 $null。
  #>
  param([int]$Port = 8080)
  try {
    $response = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/api/system/status" -f $Port) -TimeoutSec 4
    if ($response -and $response.PSObject.Properties['service'] -and $response.service -eq 'LearnAgent') { return $response }
  } catch { }
  return $null
}

function Test-LearnAgentService {
  param([int]$Port = 8080)
  return ($null -ne (Get-LearnAgentStatus -Port $Port))
}

function Import-LauncherEnvFile {
  <#
    .SYNOPSIS
    读取 KEY=VALUE 配置并写入当前进程环境变量。
    默认只填充尚未设置的变量；-OverrideExisting 时覆盖（数据目录 learnagent.env 优先于项目 .env）。
  #>
  param(
    [Parameter(Mandatory = $true)][string]$EnvFile,
    [switch]$OverrideExisting
  )
  if (-not (Test-Path -LiteralPath $EnvFile)) { return 0 }
  $applied = 0
  foreach ($line in (Get-Content -LiteralPath $EnvFile -Encoding UTF8)) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    $parts = $trimmed.Split('=', 2)
    if ($parts.Count -ne 2) { continue }
    $name = $parts[0].Trim()
    $value = $parts[1].Trim()
    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
      $value = $value.Substring(1, $value.Length - 2)
    }
    if (-not $name -or $name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { continue }
    $existing = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ($OverrideExisting -or [string]::IsNullOrEmpty($existing)) {
      [Environment]::SetEnvironmentVariable($name, $value, 'Process')
      $applied++
    }
  }
  return $applied
}

function Set-LauncherDataEnvironment {
  <#
    .SYNOPSIS
    把缓存、模型、工作区、临时文件等一切可写位置重定向到数据目录，
    保证程序目录（发布包解压位置）保持只读，满足「所有数据写入 LOCALAPPDATA」。
  #>
  param([Parameter(Mandatory = $true)][string]$DataRoot)
  $values = @{
    'DJL_CACHE_DIR'             = (Join-LauncherPath -Base $DataRoot -Child 'cache\djl-cpu')
    'ENGINE_CACHE_DIR'          = (Join-LauncherPath -Base $DataRoot -Child 'cache\djl-cpu\engine')
    'TOKENIZERS_CACHE'          = (Join-LauncherPath -Base $DataRoot -Child 'cache\djl-cpu\tokenizers')
    'LOCAL_EMBEDDING_CACHE'     = (Join-LauncherPath -Base $DataRoot -Child 'models\onnx')
    'LEARNAGENT_WORKSPACE_ROOT' = (Join-LauncherPath -Base $DataRoot -Child 'workspaces')
  }
  foreach ($key in $values.Keys) {
    [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
  }
  return $values
}

function New-LauncherDataDirectories {
  param([Parameter(Mandatory = $true)][string]$DataRoot)
  $directories = @(
    'cache\djl-cpu\engine',
    'cache\djl-cpu\tokenizers',
    'models\onnx',
    'workspaces',
    'logs',
    'tmp',
    'run'
  )
  foreach ($dir in $directories) {
    New-Item -ItemType Directory -Force -Path (Join-LauncherPath -Base $DataRoot -Child $dir) | Out-Null
  }
}

# =====================================================================
# 外部程序调用（Unicode 参数安全）
# =====================================================================

function ConvertTo-NativeArgument {
  <#
    .SYNOPSIS
    按 Windows 命令行规则为一个参数加引号（保留中文路径与空格）。
  #>
  param([string]$Value)
  if ($null -eq $Value) { return '""' }
  if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') { return $Value }
  $builder = New-Object System.Text.StringBuilder
  [void]$builder.Append('"')
  $backslashes = 0
  foreach ($ch in $Value.ToCharArray()) {
    if ($ch -eq '\') { $backslashes++; continue }
    if ($ch -eq '"') {
      [void]$builder.Append(('\' * (2 * $backslashes + 1)))
      [void]$builder.Append('"')
      $backslashes = 0
      continue
    }
    if ($backslashes -gt 0) {
      [void]$builder.Append(('\' * $backslashes))
      $backslashes = 0
    }
    [void]$builder.Append($ch)
  }
  if ($backslashes -gt 0) { [void]$builder.Append(('\' * (2 * $backslashes))) }
  [void]$builder.Append('"')
  return $builder.ToString()
}

function Invoke-NativeProcess {
  <#
    .SYNOPSIS
    以 Unicode 安全的方式运行外部程序（pg_ctl / initdb / psql / java / javac / jpackage ...），
    捕获并回显输出；退出码写入 $script:LastNativeExitCode，输出写入 $script:LastNativeOutput / Error。
  #>
  param(
    [Parameter(Mandatory = $true)][string]$FilePath,
    [string[]]$Arguments = @(),
    [string]$WorkingDirectory = '',
    [switch]$Quiet
  )
  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = $FilePath
  $psi.Arguments = (($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' ')
  $psi.UseShellExecute = $false
  $psi.CreateNoWindow = $true
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError = $true
  # Windows 上 Java / PostgreSQL 工具按系统 ANSI 代码页输出（中文系统为 936），
  # 用 .NET 默认编码解码才能正确显示中文错误信息。
  $psi.StandardOutputEncoding = [System.Text.Encoding]::Default
  $psi.StandardErrorEncoding = [System.Text.Encoding]::Default
  if ($WorkingDirectory) { $psi.WorkingDirectory = $WorkingDirectory }

  $process = [System.Diagnostics.Process]::Start($psi)
  $stdoutTask = $process.StandardOutput.ReadToEndAsync()
  $stderrTask = $process.StandardError.ReadToEndAsync()
  $process.WaitForExit()
  $stdout = $stdoutTask.Result
  $stderr = $stderrTask.Result

  $script:LastNativeExitCode = $process.ExitCode
  $script:LastNativeOutput = $stdout
  $script:LastNativeError = $stderr
  if (-not $Quiet) {
    $text = (($stdout + "`n" + $stderr)).Trim()
    if ($text) {
      foreach ($line in [regex]::Split($text, "\r?\n")) {
        if ($line.Trim()) { Write-Host ("    {0}" -f $line) }
      }
    }
  }
}

function Get-NativeExitCode { return $script:LastNativeExitCode }
function Get-NativeOutput { return $script:LastNativeOutput }
function Get-NativeError { return $script:LastNativeError }

function Get-JavaRuntimeVersion {
  param([Parameter(Mandatory = $true)][string]$JavaExecutable)
  # java -version 的输出走 stderr
  Invoke-NativeProcess -FilePath $JavaExecutable -Arguments @('-version') -Quiet
  $text = (Get-NativeOutput) + (Get-NativeError)
  if ($text -match 'version "(\d+)(?:\.(\d+))?') {
    $major = [int]$Matches[1]
    if ($major -eq 1 -and $Matches[2]) { $major = [int]$Matches[2] }
    return $major
  }
  return $null
}

function Resolve-JavaExecutable {
  <#
    .SYNOPSIS
    定位可用的 Java 21+：-JavaPath > 发布包内置 runtime\ > JAVA_HOME > PATH。
  #>
  param(
    [string]$RequestedJava = '',
    [string]$ProjectRoot = '',
    [int]$RequiredMajor = 21
  )
  $candidates = @()
  if ($RequestedJava) { $candidates += $RequestedJava }
  if ($ProjectRoot) {
    $candidates += (Join-Path $ProjectRoot 'runtime\bin\java.exe')
  }
  if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
  $candidates += 'java'

  foreach ($candidate in $candidates) {
    $resolved = $null
    if ($candidate -eq 'java') {
      $command = Get-Command java -ErrorAction SilentlyContinue
      if ($command) { $resolved = $command.Source }
    } elseif (Test-Path -LiteralPath $candidate) {
      $resolved = $candidate
    }
    if (-not $resolved) { continue }
    $major = Get-JavaRuntimeVersion -JavaExecutable $resolved
    if ($major -and $major -ge $RequiredMajor) {
      return @{ Path = $resolved; Version = $major }
    }
  }
  return $null
}

function Merge-SpringProfiles {
  param(
    [string]$ExistingProfiles = '',
    [Parameter(Mandatory = $true)][string]$RequiredProfile
  )
  $list = @()
  if ($ExistingProfiles) {
    $list = @($ExistingProfiles.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
  }
  if ($list -notcontains $RequiredProfile) {
    $list = @($RequiredProfile) + $list
  }
  return ($list -join ',')
}

# =====================================================================
# 单实例互斥：避免「重复双击启动多份进程」
# =====================================================================

function Enter-LauncherMutex {
  <#
    .SYNOPSIS
    获取本用户会话内唯一的启动器互斥量。返回 @{ Acquired; Mutex }。
    .DESCRIPTION
    使用命名互斥量而不是锁文件：进程退出（含崩溃）时操作系统自动释放，
    不会留下「假锁」导致用户永远无法启动。
  #>
  param([int]$TimeoutSeconds = 0)
  $mutex = New-Object System.Threading.Mutex($false, 'Local\LearnAgentLauncher.SingleInstance')
  $acquired = $false
  try {
    $acquired = $mutex.WaitOne([TimeSpan]::FromSeconds($TimeoutSeconds))
  } catch [System.Threading.AbandonedMutexException] {
    # 上一个启动器异常退出，互斥量已归本进程所有
    $acquired = $true
  }
  return @{ Acquired = $acquired; Mutex = $mutex }
}

function Exit-LauncherMutex {
  param([hashtable]$Handle)
  if (-not $Handle -or -not $Handle.Mutex) { return }
  if ($Handle.Acquired) {
    try { $Handle.Mutex.ReleaseMutex() } catch { }
  }
  try { $Handle.Mutex.Dispose() } catch { }
}

# =====================================================================
# 进程与端口
# =====================================================================

function Stop-ProcessListeningOnPort {
  <#
    .SYNOPSIS
    结束监听指定端口的进程（含子进程树）。仅用于停止 LearnAgent 自己拉起的后端。
  #>
  param([Parameter(Mandatory = $true)][int]$Port)
  $killed = @()
  $connections = $null
  try {
    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  } catch { $connections = $null }
  if ($connections) {
    $ownerPids = @($connections | ForEach-Object { $_.OwningProcess } | Select-Object -Unique)
    foreach ($ownerPid in $ownerPids) {
      if ($ownerPid -and $ownerPid -ne $PID) {
        try {
          Invoke-NativeProcess -FilePath (Join-Path $env:SystemRoot 'System32\taskkill.exe') -Arguments @('/PID', "$ownerPid", '/T', '/F') -Quiet
          $killed += $ownerPid
        } catch { }
      }
    }
  }
  return $killed
}

function Stop-ProcessFromPidFile {
  param([Parameter(Mandatory = $true)][string]$PidFile)
  $result = @{ Killed = $false; Pid = 0 }
  if (-not (Test-Path -LiteralPath $PidFile)) { return $result }
  $raw = Get-Content -LiteralPath $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1
  $ownerPid = 0
  if ($raw -match '^\s*(\d+)') { $ownerPid = [int]$Matches[1] }
  if ($ownerPid -le 0 -or $ownerPid -eq $PID) { return $result }
  $result.Pid = $ownerPid
  try { Get-Process -Id $ownerPid -ErrorAction Stop | Out-Null } catch { return $result }
  try {
    Invoke-NativeProcess -FilePath (Join-Path $env:SystemRoot 'System32\taskkill.exe') -Arguments @('/PID', "$ownerPid", '/T', '/F') -Quiet
    $result.Killed = $true
  } catch { }
  return $result
}

function Open-LauncherUrl {
  param([Parameter(Mandatory = $true)][string]$Url)
  try {
    Start-Process -FilePath $Url | Out-Null
    return $true
  } catch {
    try {
      $psi = New-Object System.Diagnostics.ProcessStartInfo
      $psi.FileName = $Url
      $psi.UseShellExecute = $true
      [void][System.Diagnostics.Process]::Start($psi)
      return $true
    } catch {
      return $false
    }
  }
}

function Show-LauncherError {
  param(
    [string]$Title = 'LearnAgent 启动失败',
    [Parameter(Mandatory = $true)][string]$Message,
    [switch]$Popup
  )
  Write-Host ''
  Write-Host ("==== {0} ====" -f $Title) -ForegroundColor Red
  Write-Host $Message -ForegroundColor Red
  Write-Host ''
  Write-LauncherLog -Level 'ERROR' -Message ("{0}: {1}" -f $Title, $Message)
  if ($Popup) {
    try {
      Add-Type -AssemblyName System.Windows.Forms -ErrorAction SilentlyContinue
      [System.Windows.Forms.MessageBox]::Show($Message, $Title, [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
    } catch { }
  }
}

function Stop-Launcher {
  <#
    .SYNOPSIS
    抛出启动器专用致命错误（由顶层 catch 统一展示中文信息）。
  #>
  param([Parameter(Mandatory = $true)][string]$Message)
  throw ("LAUNCHER_FATAL: {0}" -f $Message)
}

# =====================================================================
# 内置便携 PostgreSQL 16（目标电脑无需安装 PostgreSQL / Docker / WSL）
# =====================================================================

function Get-PortablePostgreSqlBin {
  param([Parameter(Mandatory = $true)][string]$ProjectRoot)
  $candidate = Join-Path $ProjectRoot 'pgsql\bin'
  if (Test-Path -LiteralPath (Join-Path $candidate 'postgres.exe')) { return $candidate }
  return $null
}

function Test-PortablePostgreSqlRunning {
  param(
    [Parameter(Mandatory = $true)][string]$PgBin,
    [Parameter(Mandatory = $true)][string]$PgData
  )
  $pgCtl = Join-Path $PgBin 'pg_ctl.exe'
  Invoke-NativeProcess -FilePath $pgCtl -Arguments @('-D', $PgData, 'status') -Quiet
  return ((Get-NativeExitCode) -eq 0)
}

function Initialize-PortablePostgreSql {
  <#
    .SYNOPSIS
    首次运行时初始化便携数据库数据目录（initdb，仅创建本机单用户实例）。
  #>
  param(
    [Parameter(Mandatory = $true)][string]$PgBin,
    [Parameter(Mandatory = $true)][string]$PgData,
    [Parameter(Mandatory = $true)][string]$SuperUser,
    [Parameter(Mandatory = $true)][string]$Password,
    [Parameter(Mandatory = $true)][string]$TempDirectory
  )
  $initDb = Join-Path $PgBin 'initdb.exe'
  $pwFile = Join-Path $TempDirectory 'pg-pwfile.txt'
  Set-Content -LiteralPath $pwFile -Value $Password -Encoding ASCII
  Write-LauncherStep ("首次运行：正在初始化内置数据库（约 10-30 秒，只需一次）：{0}" -f $PgData)
  Invoke-NativeProcess -FilePath $initDb -Arguments @('-D', $PgData, '-U', $SuperUser, '-A', 'scram-sha-256', "--pwfile=$pwFile", '-E', 'UTF8', '--locale=C')
  $initDbExit = Get-NativeExitCode
  Remove-Item -LiteralPath $pwFile -Force -ErrorAction SilentlyContinue
  if ($initDbExit -ne 0) {
    Stop-Launcher ("内置数据库初始化失败（initdb 退出码 {0}）。`n`n常见原因与处理办法：`n  1) 数据目录不完整：先运行 launcher\Stop-LearnAgent.ps1 -StopDatabase，再删除`n     {1}`n     后重试（会重新初始化，已有的学习数据请先备份）；`n  2) 以管理员身份运行：请直接双击 LearnAgent.exe，不要选择「以管理员身份运行」；`n  3) 磁盘空间不足或路径含特殊字符：把发布包换到普通目录（如 D:\LearnAgent）后重试。`n`n启动器日志：{2}" -f $initDbExit, $PgData, $script:LearnAgentLogFile)
  }
  Write-LauncherInfo '内置数据库数据目录初始化完成。' -Color Green
}

function Start-PortablePostgreSql {
  <#
    .SYNOPSIS
    启动内置便携 PostgreSQL（仅监听 127.0.0.1），并确保应用数据库与 pgvector 扩展存在。
  #>
  param(
    [Parameter(Mandatory = $true)][string]$PgBin,
    [Parameter(Mandatory = $true)][string]$PgData,
    [Parameter(Mandatory = $true)][string]$SuperUser,
    [Parameter(Mandatory = $true)][string]$Password,
    [Parameter(Mandatory = $true)][int]$Port,
    [Parameter(Mandatory = $true)][string]$LogDirectory,
    [string]$DatabaseName = 'learnagent'
  )
  $pgCtl = Join-Path $PgBin 'pg_ctl.exe'
  $pgLog = Join-LauncherPath -Base $LogDirectory -Child 'postgres.log'
  $portInUse = Test-PortOpen -Port $Port

  if (Test-PortablePostgreSqlRunning -PgBin $PgBin -PgData $PgData) {
    Write-LauncherInfo ("内置数据库已在运行（127.0.0.1:{0}），直接复用。" -f $Port) -Color Green
  } elseif ($portInUse) {
    Stop-Launcher ("端口 {0} 已被其他程序占用，无法启动内置数据库。`n`n处理办法：关闭占用该端口的程序（可能是另一个 PostgreSQL 实例），`n或把 launcher\launcher-settings.json 中的 pgPort 改成其他端口（如 5434）后重试。" -f $Port)
  } else {
    Write-LauncherStep ("启动内置数据库 PostgreSQL（127.0.0.1:{0}）..." -f $Port)
    # 注意：不能用管道捕获 pg_ctl 的输出——postgres 会继承管道句柄，读取端会一直等不到 EOF。
    # 这里把 pg_ctl 的输出重定向到日志文件，并以端口是否可连接判断启动成功。
    $pgCtlOut = Join-LauncherPath -Base $LogDirectory -Child 'pg_ctl-start.out.log'
    $pgCtlErr = Join-LauncherPath -Base $LogDirectory -Child 'pg_ctl-start.err.log'
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
      $startArgs = '-D "{0}" -l "{1}" -o "-p {2} -h 127.0.0.1" start' -f $PgData, $pgLog, $Port
      $startProc = Start-Process -FilePath $pgCtl -ArgumentList $startArgs -RedirectStandardOutput $pgCtlOut -RedirectStandardError $pgCtlErr -PassThru -WindowStyle Hidden
    } catch {
      Write-LauncherInfo ("pg_ctl 启动异常：{0}" -f $_.Exception.Message) -Color Yellow
    } finally {
      $ErrorActionPreference = $prevEap
    }
    $ready = $false
    for ($i = 0; $i -lt 120; $i++) {
      if (Test-PortOpen -Port $Port) { $ready = $true; break }
      Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
      $tail = ''
      try { $tail = ((Get-Content -LiteralPath $pgCtlErr -Tail 10 -ErrorAction SilentlyContinue) -join "`n") } catch { }
      Stop-Launcher ("内置数据库启动失败（127.0.0.1:{0} 无响应）。`n`npg_ctl 输出：`n{1}`n`n数据库日志：{2}`n常见原因：端口被占用（可在 launcher-settings.json 中修改 pgPort），或 pgdata 目录损坏。" -f $Port, $tail, $pgLog)
    }    Write-LauncherInfo ("内置数据库已就绪：127.0.0.1:{0}" -f $Port) -Color Green
  }

  $psql = Join-Path $PgBin 'psql.exe'
  $env:PGPASSWORD = $Password
  try {
    Invoke-NativeProcess -FilePath $psql -Arguments @('-h', '127.0.0.1', '-p', "$Port", '-U', $SuperUser, '-d', 'postgres', '-tAc', ("SELECT 1 FROM pg_database WHERE datname='{0}'" -f $DatabaseName)) -Quiet
    $exists = (Get-NativeOutput).Trim()
    if ($exists -ne '1') {
      Write-LauncherInfo ("创建应用数据库：{0}" -f $DatabaseName)
      Invoke-NativeProcess -FilePath $psql -Arguments @('-h', '127.0.0.1', '-p', "$Port", '-U', $SuperUser, '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-c', ("CREATE DATABASE {0}" -f $DatabaseName))
      if ((Get-NativeExitCode) -ne 0) {
        Stop-Launcher ("创建应用数据库 {0} 失败（psql 退出码 {1}）。请查看数据库日志：{2}" -f $DatabaseName, (Get-NativeExitCode), $pgLog)
      }
    }
    # pgvector 是 V1 迁移（向量列 / RAG）的前提，发布包必须内置；缺失时尽早给出明确提示。
    Invoke-NativeProcess -FilePath $psql -Arguments @('-h', '127.0.0.1', '-p', "$Port", '-U', $SuperUser, '-d', $DatabaseName, '-tAc', "SELECT 1 FROM pg_available_extensions WHERE name='vector'") -Quiet
    $vector = (Get-NativeOutput).Trim()
    if ($vector -ne '1') {
      Stop-Launcher ("发布包不完整：内置数据库缺少 pgvector 扩展（应为 pgsql\lib\vector.dll 与 pgsql\share\extension\vector.control）。`n`n请重新解压完整的发布包（LearnAgent-*-windows-x64.zip），不要只复制 LearnAgent.exe。`n详细说明见 docs\WINDOWS_LAUNCHER.md。")
    }
    Invoke-NativeProcess -FilePath $psql -Arguments @('-h', '127.0.0.1', '-p', "$Port", '-U', $SuperUser, '-d', $DatabaseName, '-v', 'ON_ERROR_STOP=1', '-c', 'CREATE EXTENSION IF NOT EXISTS vector')
    if ((Get-NativeExitCode) -ne 0) {
      Stop-Launcher ("启用 pgvector 扩展失败（psql 退出码 {0}）。`n如果内置数据库版本与 pgvector 不匹配，请重新解压完整发布包后重试。`n数据库日志：{1}" -f (Get-NativeExitCode), $pgLog)
    }
    Write-LauncherInfo 'pgvector 扩展已就绪（本地向量检索可用）。' -Color Green
  } finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
  }
  return $Port
}

function Stop-PortablePostgreSql {
  <#
    .SYNOPSIS
    停止内置便携 PostgreSQL（只停进程，pgdata 数据目录完整保留）。
  #>
  param(
    [Parameter(Mandatory = $true)][string]$PgBin,
    [Parameter(Mandatory = $true)][string]$PgData,
    [Parameter(Mandatory = $true)][int]$Port,
    [switch]$DryRun
  )
  $pgCtl = Join-Path $PgBin 'pg_ctl.exe'
  if ($DryRun) {
    Write-Host ("（演练）将执行 pg_ctl -D `"{0}`" stop -m fast（仅停止数据库进程，数据全部保留）。" -f $PgData)
    return $true
  }
  if (-not (Test-PortablePostgreSqlRunning -PgBin $PgBin -PgData $PgData)) {
    Write-Host '内置数据库本就未在运行。'
    return $false
  }
  Invoke-NativeProcess -FilePath $pgCtl -Arguments @('-D', $PgData, '-m', 'fast', '-w', '-t', '60', 'stop')
  if ((Get-NativeExitCode) -eq 0) {
    Write-Host '内置数据库已停止；学习数据全部保留（数据目录与 pgdata 均未删除）。' -ForegroundColor Green
    return $true
  }
  $killed = Stop-ProcessListeningOnPort -Port $Port
  if ($killed.Count -gt 0) {
    Write-Host ("内置数据库已停止（按端口清理，PID：{0}）；数据全部保留。" -f ($killed -join ', ')) -ForegroundColor Green
    return $true
  }
  Write-Host '内置数据库停止失败或本就未运行（可忽略）。' -ForegroundColor Yellow
  return $false
}
