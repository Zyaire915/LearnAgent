# LearnAgent 协作开发说明

感谢参与 LearnAgent。当前采用 `main + 功能分支 + Pull Request` 的协作方式。

## 开始开发

1. 克隆仓库后复制 `.env.example` 为 `.env`，只在本机填写数据库密码与 API Key。
2. 不得提交 `.env`、课程 PDF、浏览器配置、数据库文件、生成工作区或任何访问令牌。
3. 从指定功能分支继续开发，不要直接向 `main` 提交。
4. 后端使用 Java 21；前端使用 Node.js 20 或更高版本。
5. 每个数据库变更只能使用已经分配的 Flyway 版本号，避免合并时冲突。

## 提交前检查

```powershell
.\mvnw.cmd -pl backend test
Set-Location frontend
npm ci
npm run build
```

提交 Pull Request 时，请写明：实现内容、数据库变化、手工验证路径、测试结果和界面截图。一个 PR 只处理一个功能主题；发现无关问题请另开 Issue。

详细分支任务见 [docs/TEAM_BRANCHES.md](docs/TEAM_BRANCHES.md)。
