# =====================================================================
# LearnAgent Windows 便携发布包构建脚本（feature/windows-launcher）
#
# 产物：release\LearnAgent-<版本>-windows-x64\ 与同名 ZIP：
#   LearnAgent.exe       jpackage 生成的可双击启动器（自带 JRE）
#   runtime\             jpackage 内置运行时（目标电脑无需安装 Java）
#   app\                 引导 JAR + 后端 JAR + 已构建的 Vue 前端（无需 Node.js）
#   launcher\            一键启动/停止脚本、配置模板
#   pgsql\               内置便携 PostgreSQL 16 + pgvector（无需安装数据库/Docker/WSL）
#   docs\                使用与验收说明
#
# 目标电脑前置条件：**无**。解压后双击 LearnAgent.exe 即可。
# 构建机前置条件：JDK 21（含 jpackage）、Node.js 20+，以及一次联网下载
#   PostgreSQL 二进制（约 300 MB）与 pgvector（约 170 KB）；都可用参数指向本地文件。
#
# 与验收要求（docs/TEAM_BRANCHES.md #6）的对应：
#   * Vue 构建产物与后端整合 —— 第 1/2 步，前端 dist 由后端以静态资源托管；
#   * jpackage 生成 LearnAgent.exe / 便携 ZIP —— 第 6/7 步；
#   * 目标电脑不要求 Java/Node/Maven/WSL/Docker/PostgreSQL —— runtime、JAR、dist、pgsql 全部随包；
#   * 所有数据写入 LOCALAPPDATA —— 启动器默认数据目录 %LOCALAPPDATA%\LearnAgent；
#   * 密钥外置 —— 只提供 learnagent.env.example 模板，发布包内不含任何 Key；
#   * 重复启动不产生多进程 —— 命名互斥量 + 健康探测（Start-LearnAgent.ps1）；
#   * 安装器与签名 —— 按任务要求先交付便携包，安装器/签名留待后续迭代。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1
#   ... -SkipFrontend -SkipBackend      # 复用已有 frontend\dist 与 backend\target\*.jar，只重打包
#   ... -PgsqlZipPath D:\downloads\postgresql-16.9-1-windows-x64-binaries.zip
#   ... -SkipDatabaseBundling           # 精简包（不含内置数据库，仅用于开发/试用）
# =====================================================================
[CmdletBinding()]
param(
  [string]$Version = '1.0.0',
  [string]$OutputDirectory = 'release',
  [string]$JavaHome = '',
  [switch]$SkipFrontend,
  [switch]$SkipBackend,
  [switch]$SkipDatabaseBundling,
  [string]$PgsqlZipPath = '',
  [string]$PgsqlZipUrl = 'https://get.enterprisedb.com/postgresql/postgresql-16.9-1-windows-x64-binaries.zip',
  [string]$PgVectorZipPath = '',
  [string]$PgVectorZipUrl = 'https://github.com/andreiramani/pgvector_pgsql_windows/releases/download/0.8.7/vector.v0.8.7-pg16.zip',
  [switch]$NoZip
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# 复用启动器的 Unicode 安全进程调用：Windows PowerShell 5.1 的 “& 命令 参数”
# 会按 ANSI 代码页转换参数，中文/空格路径会被损坏（本仓库路径与解压目录都可能含中文）。
. (Join-Path $repoRoot 'scripts\launcher\Launcher.Common.ps1')

function Assert-ExitCode {
  param([int]$Code, [string]$Step, [string]$Hint = '')
  if ($Code -ne 0) {
    throw ("{0} 失败（退出码 {1}）。{2}" -f $Step, $Code, $Hint)
  }
}

function Invoke-NativeCommand {
  <#
    .SYNOPSIS
    调用外部程序（npm / mvnw / javac / jar / jpackage），避免 Windows PowerShell 5.1
    把原生命令的 stderr 当成终止错误。退出码见 Get-NativeExitCode。
  #>
  param(
    [Parameter(Mandatory = $true)][string]$FilePath,
    [string[]]$Arguments = @()
  )
  $prev = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    & $FilePath @Arguments
  } finally {
    $ErrorActionPreference = $prev
  }
  $script:LastNativeExitCode = $LASTEXITCODE
}

function Get-NativeExitCode {
  return $script:LastNativeExitCode
}

function Resolve-JdkTool {
  param([Parameter(Mandatory = $true)][string]$ToolName)
  $jdkHome = $JavaHome
  if (-not $jdkHome) { $jdkHome = $env:JAVA_HOME }
  if ($jdkHome) {
    # 注意：双引号里的 "$ToolName.exe" 会被 PowerShell 解析成属性访问，必须显式拼接
    $candidate = Join-Path $jdkHome ('bin\' + $ToolName)
    if (Test-Path -LiteralPath $candidate) { return $candidate }
    Write-Host ("  警告：{0} 下没有 {1}，改用 PATH 中的版本。" -f $jdkHome, $ToolName) -ForegroundColor Yellow
  }
  $command = Get-Command $ToolName -ErrorAction SilentlyContinue
  if ($command) { return $command.Source }
  throw ("未找到 {0}。请安装 JDK 21 并设置 JAVA_HOME，或用 -JavaHome 指定 JDK 根目录（jpackage/javac/jar 都随 JDK 提供）。" -f $ToolName)
}

function Expand-ZipToDirectory {
  <#
    .SYNOPSIS
    用 .NET ZipFile 解压（比 Expand-Archive 快，且避免 PowerShell 5.1 的
    Archive 模块在超大压缩包上抛 Remove-Item 异常）。
  #>
  param(
    [Parameter(Mandatory = $true)][string]$ZipPath,
    [Parameter(Mandatory = $true)][string]$Destination
  )
  if (Test-Path -LiteralPath $Destination) { Remove-Item -LiteralPath $Destination -Recurse -Force }
  New-Item -ItemType Directory -Force -Path $Destination | Out-Null
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $Destination)
}

function Expand-PostgresBinaries {
  <#
    .SYNOPSIS
    只解压运行所需的 pgsql\bin、lib、share（跳过 include/doc/symbols/pgAdmin/StackBuilder）。
    .DESCRIPTION
    EDB 的 zip 里有超过 260 字符的 pgAdmin 路径，Windows PowerShell 5.1 的
    Expand-Archive 与 ZipFile.ExtractToDirectory 都会因长路径失败；按条目选择性
    解压既绕开长路径，也把包体缩小约 500 MB。
  #>
  param(
    [Parameter(Mandatory = $true)][string]$ZipPath,
    [Parameter(Mandatory = $true)][string]$Destination
  )
  if (Test-Path -LiteralPath $Destination) { Remove-Item -LiteralPath $Destination -Recurse -Force }
  New-Item -ItemType Directory -Force -Path $Destination | Out-Null
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $archive = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
  $extracted = 0
  try {
    foreach ($entry in $archive.Entries) {
      $name = $entry.FullName.Replace('/', '\')
      if (-not $name.StartsWith('pgsql\')) { continue }
      $relative = $name.Substring(6)
      if (-not $relative) { continue }
      $top = ($relative.Split('\'))[0]
      if ($top -in @('include', 'doc', 'symbols', 'pgAdmin 4', 'StackBuilder')) { continue }
      if ($name.EndsWith('\')) { continue }
      $target = Join-Path $Destination $name
      $parent = Split-Path -Parent $target
      if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
      [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
      $extracted++
    }
  } finally {
    $archive.Dispose()
  }
  Write-Host ("    已解压 {0} 个文件（bin/lib/share）。" -f $extracted)
}

function Get-Download {
  <#
    .SYNOPSIS
    下载并缓存构建用压缩包；已存在则直接复用（避免重复下载 300 MB）。
  #>
  param(
    [string]$LocalPath,
    [string]$Url,
    [string]$CacheFile,
    [string]$Label
  )
  if ($LocalPath) {
    if (-not (Test-Path -LiteralPath $LocalPath)) { throw ("未找到 -{0} 指定的文件：{1}" -f $Label, $LocalPath) }
    return (Resolve-Path -LiteralPath $LocalPath).Path
  }
  if (Test-Path -LiteralPath $CacheFile) {
    Write-Host ("    复用已下载的 {0}：{1}" -f $Label, $CacheFile)
    return $CacheFile
  }
  New-Item -ItemType Directory -Force -Path (Split-Path -Parent $CacheFile) | Out-Null
  Write-Host ("    下载 {0}：{1}" -f $Label, $Url)
  try {
    Invoke-WebRequest -Uri $Url -OutFile $CacheFile -UseBasicParsing
  } catch {
    Remove-Item -LiteralPath $CacheFile -Force -ErrorAction SilentlyContinue
    throw ("下载 {0} 失败：{1}`n请手动下载后使用对应参数指定本地文件（例如 -{2} D:\downloads\xxx.zip）。" -f $Label, $_.Exception.Message, $Label)
  }
  return $CacheFile
}

Write-Host ''
Write-Host '==============================================' -ForegroundColor Cyan
Write-Host '       LearnAgent Windows 发布包构建' -ForegroundColor Cyan
Write-Host ("  版本：{0}" -f $Version)
Write-Host ("  仓库：{0}" -f $repoRoot)
Write-Host ("  JAVA_HOME：{0}" -f $(if ($JavaHome) { $JavaHome } elseif ($env:JAVA_HOME) { $env:JAVA_HOME } else { '(未设置，使用 PATH 中的 JDK)' }))
Write-Host '==============================================' -ForegroundColor Cyan

$jpackage = Resolve-JdkTool 'jpackage.exe'
$javac = Resolve-JdkTool 'javac.exe'
$jarTool = Resolve-JdkTool 'jar.exe'
$javaTool = Resolve-JdkTool 'java.exe'

# jpackage 对非 ASCII 的 JDK 路径有缺陷（会以 NullPointerException 失败），提前给出明确提示
if ($jpackage -match '[^\x00-\x7F]') {
  throw ("jpackage 不支持包含中文或其它非 ASCII 字符的 JDK 路径：`n  {0}`n`n请把 JDK 21 安装/解压到纯英文路径（例如 C:\Java\jdk-21），`n再用 -JavaHome 指定，或设置 JAVA_HOME 后重新运行。`n（仓库目录本身可以包含中文，只有 JDK 路径必须是纯英文。）" -f $jpackage)
}
# 离线/CI 构建：允许用环境变量指定已下载的压缩包（避免命令行传递中文路径时被 ANSI 转换）
if (-not $PgsqlZipPath -and $env:LEARNAGENT_PGSQL_ZIP) { $PgsqlZipPath = $env:LEARNAGENT_PGSQL_ZIP }
if (-not $PgVectorZipPath -and $env:LEARNAGENT_PGVECTOR_ZIP) { $PgVectorZipPath = $env:LEARNAGENT_PGVECTOR_ZIP }
# mvnw.cmd 需要 JAVA_HOME：未设置时从 java.exe 自动检测
if (-not $env:JAVA_HOME) {
  Invoke-NativeProcess -FilePath $javaTool -Arguments @('-XshowSettings:properties', '-version') -Quiet
  $javaProps = ((Get-NativeOutput) + (Get-NativeError))
  if ($javaProps -match 'java\.home\s*=\s*(\S.+)') {
    $detected = $Matches[1].Trim()
    if (Test-Path -LiteralPath (Join-Path $detected 'bin\java.exe')) {
      $env:JAVA_HOME = $detected
      Write-Host ("  已自动检测 JAVA_HOME = {0}" -f $env:JAVA_HOME) -ForegroundColor DarkGray
    }
  }
}

Invoke-NativeProcess -FilePath $javaTool -Arguments @('-version') -Quiet
$javaVersionText = ((Get-NativeOutput) + (Get-NativeError)).Trim()
Invoke-NativeProcess -FilePath $jpackage -Arguments @('--version') -Quiet
$jpackageVersion = ((Get-NativeOutput) + (Get-NativeError)).Trim()
Write-Host ("  JDK：{0}（jpackage {1}）" -f ($javaVersionText -split "`r?`n" | Select-Object -First 1), $jpackageVersion) -ForegroundColor DarkGray

$releaseName = "LearnAgent-$Version-windows-x64"
$stageRoot = Join-Path $repoRoot $OutputDirectory
$buildRoot = Join-Path $stageRoot '.build'
$cacheRoot = Join-Path $stageRoot '.downloads'
$appInput = Join-Path $buildRoot 'app'
$appContent = Join-Path $buildRoot 'appcontent'
$stage = Join-Path $stageRoot $releaseName

foreach ($dir in @($buildRoot, $appInput, $appContent)) {
  if (Test-Path -LiteralPath $dir) { Remove-Item -LiteralPath $dir -Recurse -Force }
  New-Item -ItemType Directory -Path $dir -Force | Out-Null
}

# 运行中的后端会锁住 backend JAR 与 pgsql 文件，先停止
$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
  Write-Host '检测到 8080 端口仍有 LearnAgent 后端在运行（会锁住 JAR），正在停止...' -ForegroundColor Yellow
  $stopScript = @(
    (Join-Path $stage 'launcher\Stop-LearnAgent.ps1'),
    (Join-Path $repoRoot 'scripts\launcher\Stop-LearnAgent.ps1')
  ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
  if ($stopScript) {
    Invoke-NativeCommand -FilePath 'powershell.exe' -Arguments @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $stopScript, '-StopDatabase')
  }
  Start-Sleep -Seconds 3
  if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    throw '后端仍在运行并占用 8080 端口，无法重新打包。请先运行 scripts\launcher\Stop-LearnAgent.ps1 -StopDatabase。'
  }
}

# ---------------------------------------------------------------------
# 1/7 前端：Vue 3 + Vite 构建产物（进入 app\frontend，由后端托管）
# ---------------------------------------------------------------------
$frontendRoot = Join-Path $repoRoot 'frontend'
if ($SkipFrontend) {
  $distSource = Join-Path $frontendRoot 'dist'
  if (-not (Test-Path -LiteralPath (Join-Path $distSource 'index.html'))) {
    throw '-SkipFrontend 需要已存在的 frontend\dist（含 index.html）；请先完整构建一次。'
  }
  Write-Host '[1/7] 前端：复用已有 frontend\dist。'
} else {
  Write-Host '[1/7] 前端：npm ci && npm run build ...'
  Push-Location $frontendRoot
  try {
    Invoke-NativeCommand -FilePath 'npm.cmd' -Arguments @('ci')
    Assert-ExitCode -Code (Get-NativeExitCode) -Step 'npm ci' -Hint '请确认构建机已安装 Node.js 20+（目标电脑不需要）。'
    Invoke-NativeCommand -FilePath 'npm.cmd' -Arguments @('run', 'build')
    Assert-ExitCode -Code (Get-NativeExitCode) -Step 'npm run build' -Hint '查看上方 Vite 报错输出。'
  } finally {
    Pop-Location
  }
  $distSource = Join-Path $frontendRoot 'dist'
}
if (-not (Test-Path -LiteralPath (Join-Path $distSource 'index.html'))) { throw '前端构建产物缺少 index.html。' }
Copy-Item -LiteralPath $distSource -Destination (Join-Path $appInput 'frontend') -Recurse

# ---------------------------------------------------------------------
# 2/7 后端：Spring Boot 可执行 JAR（进入 app\backend-<版本>.jar）
# ---------------------------------------------------------------------
if ($SkipBackend) {
  $existing = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'backend\target') -Filter 'backend-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
  if (-not $existing) { throw '-SkipBackend 需要已存在的 backend\target\backend-*.jar；请先完整构建一次。' }
  Copy-Item -LiteralPath $existing.FullName -Destination (Join-Path $appInput $existing.Name)
  Write-Host ('[2/7] 后端：复用已有 JAR（{0}）。' -f $existing.Name)
} else {
  Write-Host '[2/7] 后端：mvnw -pl backend -am package -DskipTests ...'
  Invoke-NativeCommand -FilePath (Join-Path $repoRoot 'mvnw.cmd') -Arguments @('-pl', 'backend', '-am', 'package', '-DskipTests')
  Assert-ExitCode -Code (Get-NativeExitCode) -Step 'Maven 构建' -Hint '请确认已安装 JDK 21 并设置 JAVA_HOME（目标电脑不需要）。'
  $jar = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'backend\target') -Filter 'backend-*.jar' |
    Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
  if (-not $jar) { throw '未找到 Maven 生成的 backend-*.jar。' }
  Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $appInput $jar.Name)
}

# ---------------------------------------------------------------------
# 3/7 引导 JAR：LauncherMain（jpackage 的 main-jar，负责调用 PowerShell 启动器）
# ---------------------------------------------------------------------
Write-Host '[3/7] 引导 JAR：javac + jar ...'
$bootstrapSource = Join-Path $repoRoot 'scripts\launcher\src-bootstrap\com\learnagent\launcher\LauncherMain.java'
$classesDir = Join-Path $buildRoot 'classes'
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
Invoke-NativeProcess -FilePath $javac -Arguments @('-encoding', 'UTF-8', '--release', '21', '-d', $classesDir, $bootstrapSource)
Assert-ExitCode -Code (Get-NativeExitCode) -Step 'javac 编译引导源码' -Hint '查看上方 javac 报错输出。'
Invoke-NativeProcess -FilePath $jarTool -Arguments @('--create', '--file', (Join-Path $appInput 'learnagent-bootstrap.jar'), '--main-class', 'com.learnagent.launcher.LauncherMain', '-C', $classesDir, '.')
Assert-ExitCode -Code (Get-NativeExitCode) -Step 'jar 打包引导程序'

# ---------------------------------------------------------------------
# 4/7 内置便携 PostgreSQL 16 + pgvector（目标电脑无需安装数据库）
# ---------------------------------------------------------------------
if ($SkipDatabaseBundling) {
  Write-Host '[4/7] 内置数据库：按 -SkipDatabaseBundling 跳过（目标电脑将无法直接使用，仅用于开发试用）。' -ForegroundColor Yellow
} else {
  Write-Host '[4/7] 内置数据库：便携 PostgreSQL 16 + pgvector ...'
  $pgsqlZip = Get-Download -LocalPath $PgsqlZipPath -Url $PgsqlZipUrl -CacheFile (Join-Path $cacheRoot 'postgresql-16-windows-x64-binaries.zip') -Label 'PgsqlZipPath'
  $pgVectorZip = Get-Download -LocalPath $PgVectorZipPath -Url $PgVectorZipUrl -CacheFile (Join-Path $cacheRoot 'vector-pg16.zip') -Label 'PgVectorZipPath'

  $extractRoot = Join-Path $buildRoot 'pgsql-extract'
  Write-Host '    解压 PostgreSQL（约 1-2 分钟）...'
  Expand-PostgresBinaries -ZipPath $pgsqlZip -Destination $extractRoot
  $pgsqlDir = Join-Path $extractRoot 'pgsql'
  if (-not (Test-Path -LiteralPath (Join-Path $pgsqlDir 'bin\postgres.exe'))) {
    throw '解压后未找到 pgsql\bin\postgres.exe；请确认下载的是 EDB 官方 Windows x64 binaries 包。'
  }
  Invoke-NativeProcess -FilePath (Join-Path $pgsqlDir 'bin\postgres.exe') -Arguments @('--version') -Quiet
  $pgVersion = ((Get-NativeOutput) + (Get-NativeError)).Trim()
  if ($pgVersion -notmatch ' 16\.') {
    throw ("内置数据库版本不是 PostgreSQL 16：{0}`npgvector 的 Windows 构建按大版本匹配，请使用 16.x 的 binaries 包。" -f $pgVersion.Trim())
  }

  # pgvector：vector.dll → pgsql\lib，vector.control/vector--*.sql → pgsql\share\extension
  $vectorExtract = Join-Path $buildRoot 'pgvector-extract'
  Expand-ZipToDirectory -ZipPath $pgVectorZip -Destination $vectorExtract
  $vectorDll = Get-ChildItem -LiteralPath $vectorExtract -Recurse -Filter 'vector.dll' | Select-Object -First 1
  $vectorControl = Get-ChildItem -LiteralPath $vectorExtract -Recurse -Filter 'vector.control' | Select-Object -First 1
  if (-not $vectorDll -or -not $vectorControl) {
    throw 'pgvector 压缩包结构异常（缺少 vector.dll 或 vector.control）；请确认下载的是 pg16 版本。'
  }
  $pgLib = Join-Path $pgsqlDir 'lib'
  $pgShare = Join-Path $pgsqlDir 'share\extension'
  New-Item -ItemType Directory -Force -Path $pgShare | Out-Null
  Copy-Item -LiteralPath $vectorDll.FullName -Destination $pgLib -Force
  Copy-Item -Path (Join-Path $vectorControl.DirectoryName 'vector*') -Destination $pgShare -Force
  $vectorVersion = (Get-Content -LiteralPath $vectorControl.FullName | Where-Object { $_ -match '^default_version' }) -join ''
  Write-Host ("    已内置 {0} + pgvector（{1}）。" -f $pgVersion.Trim(), $vectorVersion.Trim())

  # 精简运行不需要的构建/文档文件（保留 bin、lib、share）
  foreach ($trim in @('include', 'doc', 'symbols', 'pgAdmin 4', 'StackBuilder')) {
    $path = Join-Path $pgsqlDir $trim
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
  }
  Move-Item -LiteralPath $pgsqlDir -Destination (Join-Path $appContent 'pgsql')
}

# ---------------------------------------------------------------------
# 5/7 附加内容：启动/停止脚本、配置模板、文档（进入发布包根目录）
# ---------------------------------------------------------------------
Write-Host '[5/7] 附加内容：launcher 脚本与文档 ...'
$launcherOut = Join-Path $appContent 'launcher'
New-Item -ItemType Directory -Path $launcherOut -Force | Out-Null
foreach ($name in @('Start-LearnAgent.ps1', 'Stop-LearnAgent.ps1', 'Launcher.Common.ps1', 'launcher-settings.example.json', 'learnagent.env.example')) {
  Copy-Item -LiteralPath (Join-Path $repoRoot (Join-Path 'scripts\launcher' $name)) -Destination $launcherOut
}
$docsOut = Join-Path $appContent 'docs'
New-Item -ItemType Directory -Path $docsOut -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repoRoot 'docs\WINDOWS_LAUNCHER.md') -Destination $docsOut

@'
==============================================
  LearnAgent 便携版（Windows 10/11 x64）
==============================================

【三步开始使用】
  1. 把整个文件夹（或 ZIP 解压后的文件夹）放到任意位置，例如 D:\LearnAgent；
  2. 双击 LearnAgent.exe；
  3. 首次启动会自动初始化内置数据库（约 10-30 秒）并打开浏览器；
     之后每次双击只会打开页面，不会重复启动进程。

【本包不需要安装任何软件】
  Java、Node.js、Maven、WSL、Docker、PostgreSQL 全部随包内置或无需使用。
  目标电脑只要能双击运行程序即可（无需管理员权限）。

【数据保存在哪里】
  学习记录、上传的课件、任务与测验、日志、内置数据库全部写入：
      %LOCALAPPDATA%\LearnAgent
  删除程序目录不会丢失学习数据；需要彻底清除时再删除上述目录。

【AI 密钥（可选，外置配置）】
  本包不含任何 API Key。需要 AI 对话/联网搜索时：
     把 launcher\learnagent.env.example 复制为
     %LOCALAPPDATA%\LearnAgent\learnagent.env，填入 Key 后重启 LearnAgent.exe。
  不配置 Key 时，课程、任务、测验、复习、工作台等功能均正常使用。

【停止 / 卸载】
  停止：可以关闭浏览器后运行 launcher\Stop-LearnAgent.ps1
        （加 -StopDatabase 可同时停止内置数据库；数据不会删除）。
  卸载：直接删除本文件夹；如需彻底清除数据，再删除 %LOCALAPPDATA%\LearnAgent。

【遇到问题】
  启动器日志：%LOCALAPPDATA%\LearnAgent\logs\launcher-*.log
  数据库日志：%LOCALAPPDATA%\LearnAgent\logs\postgres.log
  详细说明：docs\WINDOWS_LAUNCHER.md（含常见问题与验收清单）

【已知限制】
  * LearnAgent.exe 未做代码签名，Windows SmartScreen 可能提示「未知发布者」，
    选择「更多信息 → 仍要运行」即可（后续迭代再做安装器与签名）；
  * 请勿「以管理员身份运行」：内置数据库的 initdb 不允许管理员权限。
'@ | Set-Content -LiteralPath (Join-Path $appContent 'README.txt') -Encoding UTF8

# ---------------------------------------------------------------------
# 6/7 jpackage 生成 LearnAgent.exe（app-image，自带 runtime\）
# ---------------------------------------------------------------------
Write-Host '[6/7] 生成自带 Java 运行时（jlink）并用 jpackage 打包 LearnAgent.exe ...'
$jdkRoot = Split-Path -Parent (Split-Path -Parent $jpackage)
$jlink = Join-Path $jdkRoot 'bin\jlink.exe'
$jmods = Join-Path $jdkRoot 'jmods'
if (-not (Test-Path -LiteralPath $jlink) -or -not (Test-Path -LiteralPath $jmods)) {
  throw ("未找到 jlink 或 jmods：{0}`n请使用完整的 JDK（不是 JRE）进行构建。" -f $jdkRoot)
}
# jpackage 生成的运行时默认去掉 java.exe（--strip-native-commands），而启动器需要一个
# 独立的 java.exe 来启动后端 JAR，因此先用 jlink 生成包含 java.exe 的运行时，再交给 jpackage。
$runtimeImage = Join-Path $buildRoot 'runtime-image'
if (Test-Path -LiteralPath $runtimeImage) { Remove-Item -LiteralPath $runtimeImage -Recurse -Force }
Invoke-NativeProcess -FilePath $jlink -Arguments @(
  '--module-path', $jmods,
  '--add-modules', 'ALL-MODULE-PATH',
  '--output', $runtimeImage,
  '--strip-debug',
  '--no-man-pages',
  '--no-header-files',
  '--compress=zip-6'
)
if ((Get-NativeExitCode) -ne 0) {
  Write-Host '    当前 JDK 不支持 --compress=zip-6，改为不压缩重建运行时...' -ForegroundColor Yellow
  if (Test-Path -LiteralPath $runtimeImage) { Remove-Item -LiteralPath $runtimeImage -Recurse -Force }
  Invoke-NativeProcess -FilePath $jlink -Arguments @(
    '--module-path', $jmods,
    '--add-modules', 'ALL-MODULE-PATH',
    '--output', $runtimeImage,
    '--strip-debug',
    '--no-man-pages',
    '--no-header-files'
  )
  Assert-ExitCode -Code (Get-NativeExitCode) -Step 'jlink 生成运行时' -Hint '请确认使用 JDK 21 且 jmods 完整。'
}
if (-not (Test-Path -LiteralPath (Join-Path $runtimeImage 'bin\java.exe'))) {
  throw 'jlink 生成的运行时缺少 bin\java.exe，无法启动后端。'
}
Write-Host ("    运行时已生成：{0:N0} MB" -f ((Get-ChildItem -LiteralPath $runtimeImage -Recurse -File | Measure-Object Length -Sum).Sum / 1MB))

$imageTemp = Join-Path $buildRoot 'image'
# jpackage 要求 --dest 指定的目录必须已存在
New-Item -ItemType Directory -Force -Path $imageTemp | Out-Null
Invoke-NativeProcess -FilePath $jpackage -Arguments @(
  '--type', 'app-image',
  '--name', 'LearnAgent',
  '--app-version', $Version,
  '--vendor', 'LearnAgent',
  '--description', 'LearnAgent 主动学习 Agent（便携版）',
  '--input', $appInput,
  '--dest', $imageTemp,
  '--main-jar', 'learnagent-bootstrap.jar',
  '--runtime-image', $runtimeImage,
  '--java-options', '-Dfile.encoding=UTF-8'
)
Assert-ExitCode -Code (Get-NativeExitCode) -Step 'jpackage 打包' -Hint '请确认 jpackage 来自 JDK 21+。'

if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
Move-Item -LiteralPath (Join-Path $imageTemp 'LearnAgent') -Destination $stage
Copy-Item -Path (Join-Path $appContent '*') -Destination $stage -Recurse -Force

# 发布包完整性检查
$required = @(
  'LearnAgent.exe',
  'runtime\bin\java.exe',
  'runtime\bin\javaw.exe',
  'app\learnagent-bootstrap.jar',
  'app\frontend\index.html',
  'launcher\Start-LearnAgent.ps1',
  'launcher\Stop-LearnAgent.ps1',
  'docs\WINDOWS_LAUNCHER.md',
  'README.txt'
)
if (-not $SkipDatabaseBundling) {
  $required += 'pgsql\bin\postgres.exe'
  $required += 'pgsql\bin\initdb.exe'
  $required += 'pgsql\lib\vector.dll'
  $required += 'pgsql\share\extension\vector.control'
}
$missing = @()
foreach ($relative in $required) {
  if (-not (Test-Path -LiteralPath (Join-Path $stage $relative))) { $missing += $relative }
}
$backendJar = Get-ChildItem -LiteralPath (Join-Path $stage 'app') -Filter 'backend-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $backendJar) { $missing += 'app\backend-*.jar' }
if ($missing.Count -gt 0) {
  throw ("发布包缺少必要文件：`n  {0}" -f ($missing -join "`n  "))
}

# 密钥外置检查：发布包内不得出现 learnagent.env / .env
$leaked = Get-ChildItem -LiteralPath $stage -Recurse -File -Force -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -eq 'learnagent.env' -or $_.Name -eq '.env' }
if ($leaked) {
  throw ("发布包内出现密钥配置文件，已中止：`n  {0}" -f (($leaked | ForEach-Object { $_.FullName }) -join "`n  "))
}

# ---------------------------------------------------------------------
# 7/7 便携 ZIP
# ---------------------------------------------------------------------
$zipPath = Join-Path $stageRoot ($releaseName + '.zip')
if (-not $NoZip) {
  Write-Host '正在压缩便携 ZIP（约 400-500 MB，需要几分钟）...'
  if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [System.IO.Compression.ZipFile]::CreateFromDirectory($stage, $zipPath, [System.IO.Compression.CompressionLevel]::Optimal, $true)
}

$stageSize = (Get-ChildItem -LiteralPath $stage -Recurse -File | Measure-Object Length -Sum).Sum
Write-Host ''
Write-Host '发布包构建完成。' -ForegroundColor Green
Write-Host ("  目录：{0}（{1:N0} MB）" -f $stage, ($stageSize / 1MB))
if (-not $NoZip) { Write-Host ("  ZIP ：{0}（{1:N0} MB）" -f $zipPath, ((Get-Item -LiteralPath $zipPath).Length / 1MB)) }
Write-Host ''
Write-Host '发布内容：'
Get-ChildItem -LiteralPath $stage | ForEach-Object { Write-Host ("  - {0}" -f $_.Name) }
Write-Host ''
Write-Host '验收提示：把 ZIP 复制到一台全新 Windows 电脑，解压后双击 LearnAgent.exe 即可运行；'
Write-Host '数据默认写入 %LOCALAPPDATA%\LearnAgent。验收清单见 docs\WINDOWS_LAUNCHER.md。'
