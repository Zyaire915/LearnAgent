# LearnAgent

LearnAgent is a student-focused AI learning workspace. It combines uploaded course knowledge, learning tasks, Quiz history, weak-point analysis, proactive weekly reviews, and a Spring AI Tool Calling Agent behind a Vue interface.

## Team Development

Development uses one branch per feature and Pull Requests into `main`. Start with [CONTRIBUTING.md](CONTRIBUTING.md), then choose an assignment from [docs/TEAM_BRANCHES.md](docs/TEAM_BRANCHES.md) or the repository Issues. Never commit `.env`, API keys, uploaded course files, browser profiles, runtime caches, or generated workspaces.

## Quick Start

Prerequisites: Java 21, Node.js 20+, and PostgreSQL 16 with PGVector. On the original demo machine the database runs in the `Ubuntu-22.04` WSL distribution; collaborators may instead use `docker compose up -d postgres`.

```powershell
# 1. Start PostgreSQL 16 + pgvector in WSL
powershell -ExecutionPolicy Bypass -File .\scripts\start-db.ps1

# 2. Optional: copy environment template and add your own keys
Copy-Item .env.example .env

# 3. Start backend (loads .env when present)
powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1

# 4. In another terminal, start frontend
powershell -ExecutionPolicy Bypass -File .\scripts\start-frontend.ps1
```

Open `http://localhost:5173`. A convenience launcher is also available:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1
```

## Windows One-Click Launcher (Portable Build)

`feature/windows-launcher` ships a small Windows launcher for non-developers. Unzip the portable
package and double-click `LearnAgent.exe` (built with **jpackage**, so it carries its own JRE). The
launcher starts the bundled **PostgreSQL 16 + pgvector** and the Spring Boot backend (which also
serves the pre-built Vue app), then opens the browser. The target computer needs **no** Java,
Node.js, Maven, WSL, Docker or PostgreSQL.

- **Data location**: everything is written to `%LOCALAPPDATA%\LearnAgent` (learning data, uploads,
  caches, logs and the bundled database under `pgdata\`).
- **Secrets stay external**: API keys only live in `%LOCALAPPDATA%\LearnAgent\learnagent.env` (or the
  repo's `.env`); they are never compiled into the EXE and never packed into the ZIP.
- **Single instance**: a named mutex plus a `/api/system/status` health probe guarantee that
  double-clicking again only opens another browser tab instead of starting a second backend.
- **Nothing is deleted on exit**: `launcher\Stop-LearnAgent.ps1` only stops processes; `pgdata`,
  workspaces and logs are preserved.

Build the portable package on a machine with JDK 21 and Node.js 20+:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-release.ps1
```

See [docs/WINDOWS_LAUNCHER.md](docs/WINDOWS_LAUNCHER.md) for the design, the acceptance mapping and
troubleshooting, and [docs/TEST-RESULTS.md](docs/TEST-RESULTS.md) for the verification record.

## Core Features

- Course cards and PDF document processing states
- PDFBox extraction, Spring AI chunking, embeddings, and course-isolated PGVector RAG
- Task CRUD, completion, restoration, priorities, and filters
- AI-generated single-choice Quiz with backend grading and explanations
- Persistent five-question adaptive test sessions with 80% mastery and 60% per-point floor
- AnswerRecord-based Weak Point and learning statistics
- Agent Skills and Spring AI `@Tool` execution with visible Agent Steps
- Local and Tavily Web sources with distinct evidence UI
- Natural-week review, scheduler, Generate Now, and recommended practice
- Daily 08:00 cited learning digest, in-app inbox, unread badge, and startup catch-up
- Sandboxed project workbench that turns a free-form description into plans, tasks, cited research and starter files
- External Resources library for project research and daily digests, with cleaned summaries and Markdown table repair
- Traceable candidate flashcards, Java-FSRS 0.5.1 review scheduling, and a course knowledge graph
- Keyless degradation: deterministic learning workflows remain usable without AI

## Architecture

```text
User
  -> Vue 3 / Vite
  -> Spring Boot REST API
  -> AgentService / Spring AI ChatClient
  -> Skills (Knowledge, Web Search, Task, Adaptive Test, Memory, Analysis, Planning, Weekly Review, Workspace)
  -> Existing Services
  -> PostgreSQL 16 / PGVector, Doubao or OpenAI, Tavily
```

The model selects Tools, but Java Services perform every database side effect. Sources come from retrieval/provider results, never from model-generated citations.

## Technology

- Java 21, Spring Boot 3.4.5, Spring AI 1.0.9
- Local multilingual ONNX embeddings (384 dimensions, cached under `.models`)
- DJL CPU native cache under `.cache` (no CUDA download)
- PostgreSQL 16.15, pgvector 0.8.7, Flyway
- Java-FSRS 0.5.1 with fixed 90% desired retention
- Apache PDFBox 3
- Vue 3.5, Vite 6, Vue Router, Element Plus
- ECharts 6 / Vue-ECharts 8 for the knowledge map
- Maven dependencies: `.mvn-cache`
- Frontend dependencies: `frontend/node_modules`

## Environment Variables

Copy `.env.example` to `.env`. Real secrets are ignored by Git.

| Variable | Required for | Default/behavior |
|---|---|---|
| `OPENAI_API_KEY` | Chat, embeddings, planning, AI Quiz | AI features unavailable when missing |
| `ARK_API_KEY` | Doubao chat through Volcengine Ark | Used only with the `doubao` profile |
| `DOUBAO_CHAT_MODEL` | Doubao chat model | `doubao-seed-2-0-mini-260428` |
| `LEARNAGENT_CHAT_PROVIDER` | Chat/Agent | `none`; set `openai` |
| `LEARNAGENT_EMBEDDING_PROVIDER` | PDF embeddings | `none`; set `openai` |
| `LEARNAGENT_VECTOR_STORE` | Local RAG | `none`; set `pgvector` |
| `TAVILY_API_KEY` | Web Search | Web Search unavailable when missing |
| `WEEKLY_REVIEW_ENABLED` | Scheduler | `true` |
| `WEEKLY_REVIEW_CRON` | Scheduler time | Sunday 20:00 |
| `DAILY_DIGEST_ENABLED` | Daily proactive digest | `true` |
| `DAILY_DIGEST_CRON` | Daily digest schedule | Every day at 08:00 |
| `MEMORY_REVIEW_ENABLED` | Due-review scheduler and startup catch-up | `true` |
| `MEMORY_REVIEW_CRON` | Due-review notification time | Every day at 08:00 |
| `LEARNAGENT_WORKSPACE_ROOT` | Markdown and Java sandbox | `workspaces` under the repository root |
| `LEARNAGENT_TIME_ZONE` | Natural-week calculation | `Asia/Shanghai` |
| `SPRING_PROFILES_ACTIVE` | Demo seed | blank; set `demo` |

Never commit `.env` or paste real keys into source files.

### Doubao Seed 2.0 Mini profile

LearnAgent can use Doubao through Volcengine Ark's OpenAI-compatible Chat API
while keeping the existing Spring AI 1.0.9 `ChatClient` and `@Tool` code. New Ark
users may receive a model-specific free inference quota. Enable **Safe Trial
Mode** in Ark first; it stops inference near quota exhaustion instead of charging.
Then create an Ark API key and run this locally so the key is entered without
appearing in chat or terminal history:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\configure-doubao.ps1
```

The script stores the ignored `.env` file under the repository root, activates the
`doubao` profile, records the custom `ep-...` Chat endpoint, and uses minimal
reasoning to reduce quota consumption. Embeddings run locally through ONNX and
are stored in PGVector, so document ingestion does not consume Ark quota.

Download the multilingual embedding model once. Both files are stored in the ignored project `.models` directory:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\download-local-embedding.ps1
```

Spring AI 1.0.9 uses Ark's Chat API, so set the custom inference Endpoint ID
after creating a standard pay-per-token endpoint in Safe Trial Mode:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\set-doubao-endpoint.ps1 `
  -EndpointId ep-YOUR_ENDPOINT_ID
```

## Database

PostgreSQL runs directly in Ubuntu WSL; Docker Desktop is not required.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-db.ps1
```

The script is idempotent, waits for `pg_isready`, and verifies Windows `localhost:5432`. Development credentials match `.env.example`; override them outside development.

Flyway creates the application tables. The database already has `vector`, `hstore`, and `uuid-ossp` extensions.

## Backend and Frontend

```powershell
.\mvnw.cmd -pl backend test
.\mvnw.cmd -pl backend spring-boot:run

cd frontend
npm install
npm run dev
npm run build
```

Readiness: `GET http://localhost:8080/api/system/status`. It reports database, chat, embedding, vector store, Web Search, and scheduler state without exposing credentials.

## Demo Profile

Set this in `.env`:

```dotenv
SPRING_PROFILES_ACTIVE=demo
```

The idempotent seed creates:

- Operating Systems, Java Programming, and Computer Networks
- eight tasks across TODO, IN_PROGRESS, and COMPLETED
- a completed Linux file-permissions Quiz
- five AnswerRecords with 40% accuracy, producing a Weak Point
- an initial Weekly Review generated from real statistics

Multiple demo starts do not duplicate seed records. Demo data is never loaded without the `demo` profile.

## Demo PDF

Upload `demo-data/Operating Systems Demo Notes.pdf` to the Operating Systems course. It is a real two-page text PDF containing Linux permissions, chmod, chown, owner/group/others, and the verification code `ORION-4729`.

PDF limitations: text PDFs up to 20MB are supported. Scanned-image OCR and encrypted PDFs are not supported in this version.

## Weekly Review

Weekly Review statistics are calculated by Java from Tasks, Quizzes, AnswerRecords, and Weak Points. AI only writes the narrative summary and recommendation. Without an AI provider, Generate Now saves a valid `PARTIAL` review.

- `POST /api/weekly-reviews/generate`
- `GET /api/weekly-reviews/latest`
- `GET /api/weekly-reviews`

Recommended practice selects the lowest-accuracy Weak Points; the user explicitly starts Quiz generation to avoid unnecessary model costs.

## Adaptive Test and Proactive Inbox

Adaptive tests persist across refreshes. Every round contains five questions grounded in the selected course PDFs and, when available, Tavily results. Java computes total and per-knowledge-point mastery; the model never invents scores. After submission the user can reinforce weak points, test related topics, raise difficulty, or stop. The session automatically caps at three rounds.

The daily scheduler generates three deduplicated external resources at 08:00 and writes a cited note to `workspaces/_inbox/YYYY-MM-DD/daily-digest.md`. Daily digests, weekly reviews, and completed workspace generation create unread in-app notifications. A missed daily or weekly run is filled on the next backend start.

## Project Workbench

The Workbench is restricted to the configured `workspaces` directory. A student describes any learning project in natural language; LearnAgent then creates `PROJECT_PLAN.md`, linked Tasks, cited research notes, a README, and safe starter files that match that description. It rejects absolute paths, traversal, symbolic links, unsupported extensions, and text files over 1 MB. A verified workspace can be opened directly in Windows Explorer from the UI or Agent Tool; callers never supply an arbitrary filesystem path. Existing Maven projects remain compatible and may run the allowlisted `test` or `package` goal, while non-Maven projects never show a Maven-specific action. Every generation/build is persisted as an Agent Job with progress and logs.

## External Resources

The **外部资料** sidebar page groups project `resources/research.md` files and daily digests into traceable collections. It shows the stored source links and cleaned summaries beside a read-only Markdown preview, while keeping an “edit in Workbench” route. Rendering strips common scraped navigation noise and replacement characters, sanitizes HTML, and repairs pipe tables that omit the Markdown header separator.

## Memory Reinforcement

Wrong answers create traceable candidate flashcards, but candidates never enter the review queue until the user edits or approves them. Course PDFs can generate up to ten AI candidates at a time; the model proposes content and relationships while Java validates evidence and stores the graph.

Approved cards use Java-FSRS 0.5.1 locally with a fixed 90% desired retention. Scheduling and grading do not call an AI provider, so `/memory` remains usable without API keys. The daily queue is capped at 20 cards and a review session completes after 15 minutes. `AGAIN`, `HARD`, `GOOD`, and `EASY` produce immutable review logs and deterministic next-review states in PostgreSQL.

- `GET /api/memory-reviews/due`
- `POST /api/memory-reviews/sessions`
- `POST /api/memory-reviews/sessions/{sessionId}/cards/{cardId}`
- `GET /api/memory-reviews/statistics`
- `GET /api/knowledge-graph?courseId=...`

## Smoke Test

With backend running:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

The script checks status, Courses, Tasks, and Weekly Review. It only calls the model when a Doubao or OpenAI key is present.

## Demo and Final AI Validation

- [DEMO.md](DEMO.md): 5–8 minute presentation flow and offline fallback
- [FINAL_AI_SMOKE_TEST.md](FINAL_AI_SMOKE_TEST.md): real-provider validation checklist

## Troubleshooting

- Database unavailable after Windows restart: run `scripts/start-db.ps1`.
- AI provider unavailable: verify the selected profile, its API key, and the three `LEARNAGENT_*` provider values, then restart backend.
- PDF becomes `FAILED`: inspect the displayed error, verify provider configuration, remove the failed record, and upload again.
- Web Search unavailable: configure `TAVILY_API_KEY`; other Skills continue working.
- Run `powershell -ExecutionPolicy Bypass -File .\scripts\configure-tavily.ps1` to validate a Tavily key and save it securely to the ignored root `.env`, then restart the backend.
- Docker Desktop socket errors: LearnAgent no longer requires Docker for PostgreSQL.

## Known Limitations

- Single demo user; no authentication or multi-tenancy
- One formal submission per Quiz
- No OCR for scanned PDFs
- No mobile application or push/email infrastructure
- Real AI/RAG/Web behavior depends on external provider availability and valid keys
