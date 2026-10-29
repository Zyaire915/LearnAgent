# feature/windows-launcher 测试记录

对应任务：`docs/TEAM_BRANCHES.md` 第 6 条「Windows 一键启动与可移植交付」。
本文记录本分支在本机（Windows 11 x64，中文用户名，JDK 21.0.12.1，Node.js 24）上的实际验证结果。

## 1. 构建

```powershell
# 构建机：JDK 21（路径必须为纯英文，jpackage 不支持中文 JDK 路径）+ Node.js 20+
powershell -ExecutionPolicy Bypass -File .\scripts\build-release.ps1
```

产物：

| 项目 | 结果 |
| --- | --- |
| 目录 | `release\LearnAgent-1.0.0-windows-x64\`（394 MB） |
| 便携 ZIP | `release\LearnAgent-1.0.0-windows-x64.zip`（283 MB） |
| 内置运行时 | jlink 生成，88 MB，含 `runtime\bin\java.exe` |
| 内置数据库 | PostgreSQL 16.9 + pgvector 0.8.7（`pgsql\`，121 MB） |
| 后端 | `app\backend-1.0.0.jar`（Spring Boot 3.4.5，Java 21） |
| 前端 | `app\frontend`（Vue 3 + Vite 构建产物，由后端托管） |

## 2. 端到端验证

把发布包放在纯英文路径 `C:\LearnAgent-test`，数据目录使用纯英文路径，执行 `launcher\Start-LearnAgent.ps1`：

| 验证项 | 结果 |
| --- | --- |
| 首次 `initdb` 初始化内置数据库 | 通过（约 10 秒） |
| `pg_ctl` 启动并监听 `127.0.0.1:5433` | 通过（1 秒内就绪） |
| 创建 `learnagent` 数据库 + `CREATE EXTENSION vector` | 通过 |
| 后端启动 | 通过（约 11–20 秒） |
| `GET /api/system/status` | `databaseConnected=true`，`javaVersion=21.0.12.1` |
| `GET /` | 200，返回 Vue 页面 |
| 首页数据（演示数据） | 待办 5 / 已完成 3 / 正确率 40% / 薄弱点 1，课程 3、任务 8 |
| 直接刷新子页面 `GET /dashboard` | 200，回退到 `index.html`（History 路由可用） |
| 重复启动（服务已运行时再次双击） | 识别到已运行，仅打开浏览器，Java 进程数仍为 1 |
| `Stop-LearnAgent.ps1 -StopDatabase` | 后端与数据库进程均停止 |
| 停止后数据保留 | `pgdata` 完整保留；再次启动后课程/任务数据仍在 |
| 发布包内是否含密钥 | 不含 `.env` / `learnagent.env`（构建脚本会检查并拒绝打包） |

## 3. 修复过的问题（本分支内已验证）

1. `pg_ctl start` 用管道读取输出会因 postgres 继承管道句柄而永久阻塞 → 改为重定向到日志文件 + 轮询端口；
2. jpackage 默认运行时不包含 `java.exe`（`--strip-native-commands`）→ 改用 jlink 生成含 `java.exe` 的运行时，再 `jpackage --runtime-image`；
3. 前端把接口地址写死为 `http://localhost:8080`，在 `127.0.0.1` 页面下属于跨域被浏览器拦截 → 改为同源相对地址（开发模式由 Vite 代理 `/api`）；
4. History 路由刷新子页面返回 500 → 增加 `SpaErrorController`，非 `/api` 的 404 页面回退到 `index.html`；
5. 浏览器复用旧标签页导致看到旧页面 → 启动器打开带时间戳的地址 `/?launch=<时间戳>`，并关闭静态资源缓存。

## 4. 已知限制

1. 程序目录与数据目录需为纯英文路径：PostgreSQL 在 Windows 上无法初始化中文路径（启动器会给出中文提示）；
2. 发布包未签名，SmartScreen 可能提示「未知发布者」，选择「更多信息 → 仍要运行」；
3. 请勿以管理员身份运行：`initdb` 拒绝管理员权限；
4. 内置数据库仅监听 `127.0.0.1`，账号 `learnagent/learnagent`，面向单用户本机应用。
