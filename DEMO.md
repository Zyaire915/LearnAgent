# LearnAgent Final Demo

## Before presenting

1. Run `scripts/start-db.ps1`, `scripts/start-backend.ps1`, and `scripts/start-frontend.ps1`.
2. Confirm `GET /api/system/status` reports database connected and Chat, Embedding, PGVector, and Web Search ready.
3. Confirm the Operating Systems and Java PDFs are `READY`.
4. Open `http://localhost:5173` and keep the notification badge visible.

## Demo A — Java assignment Agent

1. Open **AI 对话**, select **计算机网络**, and ask: `帮我创建一个 TCP 拥塞控制可视化学习项目，包含原理讲解、实验步骤和演示页面。`
2. Show the three choices: Spring Boot course management, CLI library management, and JavaFX student management.
3. Choose Spring Boot course management and confirm creation once.
4. Open **项目工作台** from the Agent response.
5. Show:
   - `<repository>\workspaces\<project>`;
   - editable `PROJECT_PLAN.md` and rendered preview;
   - `resources/research.md` with real URLs;
   - Course Entity, Repository, Service, Controller, DTOs, and test;
   - linked tasks in **学习任务**.
6. Click **运行 Maven 测试** and show `BUILD SUCCESS` plus 2 passing tests.

## Demo B — Adaptive exam preparation

1. Open **AI 对话**, select **操作系统**, and ask: `请结合我上传的资料和必要的互联网内容测试我的 Linux 文件权限知识。`
2. Enter the generated adaptive session. Point out five questions and PDF page / web URL evidence.
3. Submit deliberately weak answers.
4. Show Java-calculated total accuracy, per-point scores, weak points, explanations, and the three next actions.
5. Choose **强化薄弱点** and show the second five-question round generated around the detected weaknesses.
6. Explain that mastery requires total accuracy ≥80% and every tested point ≥60%, with a three-round cap and an explicit stop action.

## Proactive learning proof

1. Click the notification bell and show Daily Digest, Workspace, and Weekly Review notifications.
2. Open the daily digest in the Markdown Workbench; show three cited external resources.
3. Open **学习周报** and show task completion, overdue tasks, quiz accuracy, per-course progress, mastery map, external resources, reflection, next-week plan, and action buttons.
4. Explain that daily content is scheduled for 08:00 and weekly review for Sunday 20:00 in `Asia/Shanghai`; missed runs are filled at next startup.

## Safety proof

- All generated artifacts and caches are on D.
- Workspace APIs reject absolute paths, traversal, symbolic links, unsupported file types, and oversized text.
- Code execution is restricted to Maven `test` and `package` in the selected workspace.
- External notes store summaries and citations rather than silently mirroring arbitrary copyrighted pages.
