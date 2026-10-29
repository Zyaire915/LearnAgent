# LearnAgent Windows 一键启动与可移植交付（feature/windows-launcher）

本分支为普通用户提供一个入口：**解压便携 ZIP → 双击 `LearnAgent.exe` → 自动启动数据库与后端并打开浏览器**。

- EXE 由 **jpackage** 生成，**自带 Java 运行时**；
- 数据库是**发布包内置的便携 PostgreSQL 16 + pgvector**，首次运行自动初始化；
- 前端是**已构建的 Vue 产物**，由后端直接托管；
- 所有数据写入 `%LOCALAPPDATA%\LearnAgent`，**密钥外置**，**重复双击不会产生多份进程**。

> 对应任务：`docs/TEAM_BRANCHES.md` 第 6 条 `feature/windows-launcher`。
> 验收结果与测试记录见 [TEST-RESULTS.md](TEST-RESULTS.md)。

---

## 1. 目标电脑需要什么？

**什么都不需要安装。** 不需要 Java、Node.js、Maven、WSL、Docker 或 PostgreSQL，也不需要管理员权限。

| 项目 | 目标电脑（拿到 ZIP 的人） | 构建电脑（打包的人） |
| --- | --- | --- |
| Java 运行时 | 随包内置 `runtime\` | JDK 21（含 `jpackage`） |
| 数据库 | 随包内置 `pgsql\`（PostgreSQL 16 + pgvector） | 仅需联网下载一次二进制包 |
| 前端 | 随包内置构建产物 `app\frontend` | Node.js 20+（`npm ci && npm run build`） |
| 后端 | 随包内置 `app\backend-*.jar` | Maven（项目自带 `mvnw.cmd`） |
| 操作系统 | Windows 10/11 x64 | 同左 |

---

## 2. 使用方法（普通用户）

1. 把 `LearnAgent-1.0.0-windows-x64.zip` 解压到任意**普通目录**（例如 `D:\LearnAgent`，不要放在 `C:\Program Files`，也不要“以管理员身份运行”）；
2. 双击 `LearnAgent.exe`；
3. 首次启动会自动 `initdb` 初始化内置数据库（约 10–30 秒），然后打开浏览器 `http://127.0.0.1:8080/`。

之后每次双击：若后端已在运行，只会在浏览器里新开一个页面，**不会启动第二份进程**。

启动窗口会显示中文进度与结果；出错时会弹出中文提示并给出处理办法，同时写入
`%LOCALAPPDATA%\LearnAgent\logs\launcher-YYYYMMDD.log`。

### 停止与卸载

```powershell
# 只停止后端（内置数据库继续运行，数据保留）
powershell -ExecutionPolicy Bypass -File launcher\Stop-LearnAgent.ps1

# 同时停止内置数据库（数据仍然保留在 pgdata 中）
powershell -ExecutionPolicy Bypass -File launcher\Stop-LearnAgent.ps1 -StopDatabase
```

卸载：直接删除程序目录即可。**学习数据不在程序目录里**，如需彻底清除，再删除
`%LOCALAPPDATA%\LearnAgent`。

---

## 3. 数据与密钥在哪里

```
%LOCALAPPDATA%\LearnAgent\
├─ pgdata\                 内置 PostgreSQL 数据目录（学习记录、任务、测验……）
├─ workspaces\             项目工作台生成的文件
├─ models\onnx\            本地嵌入模型缓存
├─ cache\djl-cpu\          DJL / 分词器缓存
├─ logs\                   launcher-*.log、backend-*.log、postgres.log
├─ tmp\                    临时文件
├─ run\backend.pid         后端进程号
└─ learnagent.env          （可选）AI Key 等配置，由用户自己创建
```

- **界面与学习数据** 全部在 `pgdata` 中；删除/升级程序目录不会丢失数据。
- **API Key 外置**：发布包内只有 `launcher\learnagent.env.example` 模板；
  需要 AI 功能时，复制为 `%LOCALAPPDATA%\LearnAgent\learnagent.env` 并填写。
  密钥不会编译进 EXE，也不会进入仓库或发布包（构建脚本会检查并拒绝打包任何 `.env` / `learnagent.env`）。
- 启动器会把 `DJL_CACHE_DIR`、`ENGINE_CACHE_DIR`、`TOKENIZERS_CACHE`、`LOCAL_EMBEDDING_CACHE`、
  `LEARNAGENT_WORKSPACE_ROOT`、`java.io.tmpdir` 全部指向数据目录，保证**程序目录运行期零写入**。

---

## 4. 发布包结构

```
LearnAgent-1.0.0-windows-x64\
├─ LearnAgent.exe                 jpackage 生成的可双击入口（GUI 程序）
├─ runtime\                       jpackage 内置 JRE（目标电脑无需安装 Java）
├─ app\
│  ├─ learnagent-bootstrap.jar    引导程序（只负责调用 launcher\Start-LearnAgent.ps1）
│  ├─ backend-1.0.0.jar           Spring Boot 后端（含全部依赖）
│  └─ frontend\                   Vue 3 + Vite 构建产物（由后端托管）
├─ launcher\
│  ├─ Start-LearnAgent.ps1        一键启动：检查依赖 → 起数据库 → 起后端 → 开浏览器
│  ├─ Stop-LearnAgent.ps1         停止后端（可选停数据库），不删数据
│  ├─ Launcher.Common.ps1         公共函数（路径、配置、便携 PostgreSQL、互斥量）
│  ├─ launcher-settings.example.json   可选设置模板（端口、是否开浏览器、演示数据）
│  └─ learnagent.env.example      可选 AI 配置模板（密钥外置）
├─ pgsql\                         便携 PostgreSQL 16 + pgvector（bin/lib/share）
├─ docs\WINDOWS_LAUNCHER.md       本文件
└─ README.txt                     给最终用户的三步说明
```

可选设置：把 `launcher\launcher-settings.example.json` 复制为 `launcher\launcher-settings.json` 后修改，
例如后端端口被占用时改 `backendPort`，数据库端口冲突时改 `pgPort`，不需要浏览器时设 `openBrowser: false`。

---

## 5. 启动流程（脚本内部顺序）

1. 解析程序目录、数据目录（默认 `%LOCALAPPDATA%\LearnAgent`）与设置；
2. 获取命名互斥量 `Local\LearnAgentLauncher.SingleInstance`：拿不到就说明另一个启动器正在启动，
   本次只等待并打开页面，**不会重复拉起进程**；
3. 探测 `http://127.0.0.1:8080/api/system/status`：已在运行则只打开浏览器；
4. 读取 `learnagent.env` / `.env`（可选）并重定向缓存目录；
5. 数据库：`pg_ctl status` 判断是否已运行 → 否则先 `initdb`（仅首次）再 `pg_ctl start`，
   只监听 `127.0.0.1:5433`；确保 `learnagent` 数据库与 `vector` 扩展就绪；
6. 定位 JAR / 前端产物 / Java（优先 `runtime\bin\java.exe`），自检前端资源完整性；
7. 用内置 JRE 启动后端（隐藏窗口、日志重定向到数据目录），等待健康检查与页面资源自检；
8. 打开浏览器并输出摘要；退出启动器（后端继续在后台运行，用 Stop 脚本停止）。

失败时会给出中文原因与处理建议（端口占用、数据库未就绪、缺少文件、Java 版本、磁盘空间、管理员权限等）。

---

## 6. 构建发布包（开发者）

构建机只需装好 **JDK 21** 与 **Node.js 20+**：

```powershell
# 完整构建：npm ci && npm run build → mvnw package → 引导 JAR → 内置数据库 → jpackage → ZIP
powershell -ExecutionPolicy Bypass -File .\scripts\build-release.ps1

# 复用已有构建产物，只重打包（改动启动器脚本时最快）
powershell -ExecutionPolicy Bypass -File .\scripts\build-release.ps1 -SkipFrontend -SkipBackend

# 已手动下载 PostgreSQL / pgvector 压缩包时（内网或下载失败的机器）
powershell -ExecutionPolicy Bypass -File .\scripts\build-release.ps1 `
  -PgsqlZipPath D:\downloads\postgresql-16.9-1-windows-x64-binaries.zip `
  -PgVectorZipPath D:\downloads\vector.v0.8.7-pg16.zip

# 精简包：不内置数据库（仅用于开发试用，目标电脑将无法直接运行）
powershell -ExecutionPolicy Bypass -File .\scripts\build-release.ps1 -SkipDatabaseBundling
```

| 参数 | 说明 |
| --- | --- |
| `-Version` | 发布版本号，默认 `1.0.0`（同时写入 EXE 版本资源与目录名） |
| `-JavaHome` | 指定 JDK 21 根目录（默认取 `JAVA_HOME` 或 `PATH` 中的 JDK） |
| `-SkipFrontend` / `-SkipBackend` | 复用 `frontend\dist` / `backend\target\backend-*.jar` |
| `-SkipDatabaseBundling` | 跳过内置 PostgreSQL（精简包） |
| `-PgsqlZipPath` / `-PgsqlZipUrl` | 指定/替换 PostgreSQL 16 Windows x64 binaries |
| `-PgVectorZipPath` / `-PgVectorZipUrl` | 指定/替换 pgvector 的 Windows 构建（pg16） |
| `-NoZip` | 只产出目录，不压缩 ZIP |

产物：`release\LearnAgent-1.0.0-windows-x64\` 与同名 `.zip`；下载的压缩包缓存在 `release\.downloads\`，可复用。
构建脚本会校验发布包完整性（EXE、runtime、后端 JAR、前端 index.html、pgsql、vector.dll 等），
并检查包内**没有** `learnagent.env` / `.env`，避免密钥泄漏。

> 关于“安装器与签名”：按任务要求，本分支先交付**方案 + 最小可运行便携包**；
> 安装器（`jpackage --type exe/msi` + WiX）与代码签名留待后续迭代，接口已预留（`-Version`/`--vendor`）。

---

## 7. 开发模式（在源码仓库里运行启动器）

启动器会自动识别源码仓库布局（存在 `mvnw.cmd`、没有 `pgsql\`）：

- 数据库：优先复用本机 `127.0.0.1:5432`，否则执行 `docker compose up -d postgres`（pgvector/pgvector:pg16）；
- 后端：`backend\target\backend-*.jar`（先执行 `.\mvnw.cmd -pl backend package`）；
- 前端：`frontend\dist`（先执行 `npm run build`）或发布包中的 `app\frontend`；
- Java：`JAVA_HOME` 或 `PATH` 中的 Java 21+。

日常开发仍推荐 `scripts\start-dev.ps1`（Vite 开发服务器 + 热更新）；启动器主要用于验证发布形态。
`-DryRun` 可以只打印启动计划而不启动任何进程，便于排查路径问题。

---

## 8. 常见问题（中文提示对照）

| 现象/提示 | 原因 | 处理 |
| --- | --- | --- |
| “端口 8080 已被其他程序占用” | 8080 被别的程序占用 | 关闭占用程序，或复制 `launcher-settings.example.json` 改 `backendPort` |
| “端口 5433 已被其他程序占用” | 本机另有一个 PostgreSQL 在跑 | 关闭它，或改 `pgPort`（如 5434） |
| “内置数据库初始化失败（initdb 退出码 …）” | 以管理员身份运行、`pgdata` 损坏、磁盘空间不足 | 直接双击（不要管理员运行）；删除 `%LOCALAPPDATA%\LearnAgent\pgdata` 后重试 |
| “发布包不完整：内置数据库缺少 pgvector 扩展” | ZIP 解压不完整 | 重新解压完整 ZIP，不要只复制 exe |
| “前端产物不完整 / 页面资源无法访问” | 解压不完整或旧包 | 重新解压/重新构建 |
| “后端进程提前退出” | 数据库未就绪、端口冲突、Java 版本过低 | 按提示查看 `logs\backend-*.err.log` |
| SmartScreen 提示“未知发布者” | EXE 未签名 | “更多信息 → 仍要运行”（签名属后续迭代） |
| 浏览器显示空白页 | 前端资源缺失（旧包） | 重新解压；启动器会提前自检并给出中文提示 |

日志位置：`%LOCALAPPDATA%\LearnAgent\logs\`（`launcher-*.log` 启动器、`backend-*.log` 后端、`postgres.log` 数据库）。

---

## 9. 验收对照（`docs/TEAM_BRANCHES.md` 第 6 条）

| 要求 | 实现 |
| --- | --- |
| 为普通用户提供一个入口，检查依赖、启动数据库/后端/前端并打开浏览器 | `LearnAgent.exe` → `Start-LearnAgent.ps1`（第 5 节流程） |
| 首版小型 Windows 启动器封装现有 PowerShell 脚本，不把 API Key 编译进 EXE | jpackage 引导 JAR + PowerShell；密钥只从数据目录 `learnagent.env` / 项目 `.env` 读取；构建脚本检查包内无密钥文件 |
| 支持“开发模式”和“演示模式” | 自动识别布局：源码仓库 = 开发模式（复用本机数据库/Docker），发布包 = 演示/便携模式（内置数据库，默认加载 `demo` 演示数据） |
| 端口占用、数据库未启动、Java/Node 缺失时中文提示 | 全部覆盖（见第 8 节），错误弹窗 + 日志 |
| 运行时可下载内容、缓存和数据默认位于用户选择的目录 | 默认 `%LOCALAPPDATA%\LearnAgent`，可用 `launcher-settings.json` 的 `dataRoot` 覆盖 |
| 发布包包含构建后的前端与后端 JAR；数据库二选一并写清前置条件 | `app\frontend` + `app\backend-*.jar`；选择**内置 PostgreSQL 16 + pgvector**，目标电脑零前置条件 |
| 全新 Windows 用户按 README 一键启动 | ZIP 内 `README.txt` 三步 + 本文件 |
| 重复点击不会启动多份进程 | 命名互斥量 + 健康探测双层；第三次点击只开页面 |
| 退出时不删除用户数据 | `Stop-LearnAgent.ps1` 只停进程；`pgdata`/工作区/日志全部保留 |

实测记录（构建命令、发布包体积、启动耗时、重复双击、停止后数据保留等）见 [TEST-RESULTS.md](TEST-RESULTS.md)。

---

## 10. 已知限制

1. **未签名**：SmartScreen 可能提示未知发布者；安装器与代码签名按任务要求留待后续迭代。
2. **首次启动较慢**：`initdb` + Flyway 迁移 + 演示数据约 20–60 秒（仅首次）。
3. **内置数据库仅监听 127.0.0.1**，账号 `learnagent/learnagent`，面向单用户本机应用，不作为多用户服务。
4. **不使用 pgvector 时**：向量检索/RAG 需要额外配置 Embedding Provider 与 API Key；不配置时其余功能完整可用。
5. **Windows 10/11 x64**：未提供 x86 与 ARM64 包。
6. 管理员权限下 `initdb` 会失败（PostgreSQL 的限制），启动器会给出中文提示。
