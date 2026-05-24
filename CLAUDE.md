# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture

Karp is a two-service app: a Spring Boot backend (Kotlin, Java 25) and a React/Vite frontend. In production they're bundled — the frontend is compiled into the backend jar and served as static files.

**Ingest pipeline:**
1. File dropped in `data/sources/` (watched by `FileWatcherService`) or uploaded via UI
2. `IngestService` reads file via `ReaderRegistry` → dispatches to a background `LinkedBlockingQueue` worker
3. Worker calls `LlmService` → runs `claude -p` (or HTTP proxy in Docker) → gets back JSON with tags + wiki page updates
4. Pages written to disk (`WikiService`), vectors upserted to Qdrant (`EmbeddingService` using local DJL/MiniLM-L6-v2)
5. `ClusterService.triggerAsync()` re-clusters wiki pages via k-means on Qdrant vectors + LLM cluster naming

**LLM routing:** `LlmService.ask()` checks `CLAUDE_PROXY_URL` env. If set, POSTs to HTTP proxy (`claude-proxy.js`). Otherwise shells out to local `claude` CLI. The proxy is required when running in Docker since OAuth tokens live in the macOS Keychain.

**Storage layout:**
- `data/sources/` — original uploaded files; `.meta/<name>.json` holds tags
- `data/wiki/` — markdown files, one per wiki page; `.meta/<name>.json` holds source filename
- `data/qdrant/` — Qdrant vector index
- Failed ingests move to `data/sources/errors/`

**Key config (`application.yml` / env vars):**
- `KARP_SOURCES_DIR`, `KARP_WIKI_DIR` — override default paths (Docker sets these to `/app/sources`, `/app/wiki`)
- `QDRANT_HOST`, `QDRANT_PORT` — Qdrant connection (defaults: `localhost:6334`)
- `CLAUDE_PROXY_URL` — if set, LLM calls go to this HTTP proxy instead of local CLI

## Commands

### Backend
```bash
cd backend
./gradlew compileKotlin          # type-check
./gradlew test                   # run all tests
./gradlew test --tests "karp.core.IngestServiceTest"  # single test class
./gradlew bootRun                # run locally (needs Qdrant + claude CLI in PATH)
```

Local run without Qdrant/claude (MCP disabled):
```bash
cd backend && ANTHROPIC_API_KEY=dummy ./gradlew bootRun --args='--spring.ai.mcp.server.enabled=false'
```

### Frontend
```bash
cd frontend
npm install
npm run dev      # dev server on :5173, proxies /api to :7777
npm run build    # type-check + bundle
```

### Docker (production)
```bash
node claude-proxy.js &           # start LLM proxy on host (port 8765)
docker compose build
docker compose up -d
```

## Key design decisions

- **`LlmService` uses `ProcessBuilder("claude", ...)`** — not the Anthropic SDK. The LLM is invoked as a subprocess. JSON is parsed from stdout; stderr is captured separately. The process gets `/dev/null` as stdin.
- **Jackson 3** is used (`tools.jackson.*` group ID, not `com.fasterxml.jackson`). Import from `tools.jackson.*`.
- **Spring Boot 4 / Spring AI 2.x** — MCP server is `spring-ai-starter-mcp-server-webmvc`.
- **Embeddings are local** — no external embedding API. DJL downloads `sentence-transformers/all-MiniLM-L6-v2` on first run (384-dim vectors).
- **Clustering is in-memory** — `ClusterService` implements k-means from scratch over Qdrant-stored vectors. Cluster names come from LLM. Results cached in `AtomicReference`.
- **Wiki pages are plain `.md` files** — no database. Source attribution stored in `wiki/.meta/<page>.json`.
