# Karp: LLM-Maintained Personal Knowledge Wiki

**Date:** 2026-05-22  
**Status:** Approved  

## Overview

Personal knowledge base inspired by Karpathy's LLM Wiki pattern. Ingests files of any type, LLM incrementally builds and maintains a structured markdown wiki. Knowledge compounds — each ingested source updates existing pages, resolves contradictions, adds cross-refs. Supports browsing both wiki pages and original source files via a local web UI.

**Stack:** Kotlin + Spring Boot backend, React + Vite + TypeScript frontend, Qdrant vector DB.

---

## Architecture

```
karp/
├── sources/                    # Raw files (immutable)
│   └── errors/                 # Quarantined failed ingests
├── wiki/                       # LLM-generated markdown pages
│   ├── index.md                # Master catalog
│   ├── log.md                  # Append-only chronological log
│   └── *.md                    # Topic pages
├── .index/                     # Qdrant local vector storage
├── backend/                    # Spring Boot application
│   └── src/main/kotlin/karp/
│       ├── readers/            # File type plugins
│       │   ├── BaseReader.kt   # Reader interface
│       │   ├── ExcelReader.kt
│       │   ├── WordReader.kt
│       │   ├── PdfReader.kt
│       │   ├── JsonReader.kt
│       │   ├── OpenApiReader.kt
│       │   └── ...             # Custom types: add new Reader here
│       ├── core/
│       │   ├── IngestService.kt    # Ingest pipeline + queue
│       │   ├── QueryService.kt     # Query + chat logic
│       │   ├── LintService.kt      # Wiki health checks
│       │   └── WikiService.kt      # Read/write wiki pages
│       ├── watcher/
│       │   └── FileWatcherService.kt  # java.nio.file.WatchService
│       └── api/
│           └── ApiController.kt    # REST endpoints for frontend
├── frontend/                   # React + Vite + TypeScript
│   ├── src/
│   │   ├── components/
│   │   └── ...
│   └── dist/                   # Built assets served by Spring Boot
├── schema.md                   # LLM behavior rules (system prompt in a file)
└── build.gradle.kts            # Project build
```

**Data flow:**
- `sources/` → reader plugin → `ReadResult` → LLM → updates `wiki/*.md` + Qdrant
- Query → vector search Qdrant → relevant wiki pages → LLM → answer → optionally filed back to wiki

---

## Reader Plugin System

Each file type is one class implementing `BaseReader`:

```kotlin
interface BaseReader {
    val extensions: List<String>
    fun read(path: Path): ReadResult
}

data class ReadResult(
    val text: String,               // Normalized text for LLM
    val metadata: Map<String, Any>, // title, author, sheet names, etc.
    val preview: String,            // Short human-readable summary for UI
    var suggestedTags: List<String> = emptyList(),    // LLM fills post-read
    var suggestedCategory: String = ""                // LLM fills post-read
)
```

Readers registered as Spring `@Component` beans — auto-discovered at startup. Zero core changes to add a new type.

**Built-in readers (v1):**

| File type | Library | Notes |
|---|---|---|
| `.xlsx/.xls` | Apache POI | Each sheet → text table; chunked if >1000 rows |
| `.docx` | Apache POI (XWPF) | Paragraphs + headings |
| `.pdf` | Apache PDFBox | Text extraction |
| `.json` | Jackson | Pretty-printed + schema inferred |
| `.yaml` | SnakeYAML | Same as JSON |
| `openapi.json/yaml` | swagger-parser | Endpoints + schemas extracted |
| `.md/.txt` | stdlib | Passthrough |
| `.kt/.ts/etc` | stdlib | Raw code |

---

## Core Operations

### Ingest

Triggered by React UI file upload or `FileWatcherService` (`java.nio.file.WatchService`) detecting new file in `sources/`.

Both paths funnel through a sequential ingest queue (no race conditions).

1. Reader plugin converts file → `ReadResult`
2. LLM assigns `suggestedCategory` + `suggestedTags` from content
3. UI shows confirmation modal — user edits/confirms tags before proceeding
4. LLM receives: normalized text + existing relevant wiki pages
5. LLM updates 10–15 wiki pages (creates/edits topic pages, updates `index.md`)
6. Appends entry to `log.md`
7. Re-embeds updated pages → Qdrant (with tag/category metadata)

### Query / Chat

1. User question → vector search Qdrant (optionally filtered by tag/category) → top-k relevant wiki pages
2. LLM receives: question + retrieved pages (default top-5, configurable in `schema.md`)
3. LLM answers
4. User can click "File back to wiki" → answer proposed as new/updated wiki page

### Lint

Manual trigger from UI or CLI.

1. LLM reviews all wiki pages for: contradictions, stale claims, orphan pages, missing cross-refs
2. Returns report in UI — user decides what to fix

### Schema Doc (`schema.md`)

Governs LLM behavior across all operations. Controls: page titling conventions, when to create vs update existing pages, cross-ref style, log format, tag taxonomy guidelines. Edit this file to tune LLM behavior without touching code.

---

## Categorization & Tagging

**On ingest:** LLM suggests one `category` and a list of `tags` based on file content. Shown in confirmation modal before pipeline proceeds. User can edit freely.

**Storage:** Tags + category stored in:
- `ReadResult` metadata
- Qdrant document metadata (enables tag-filtered semantic search)
- Wiki page frontmatter

**Chat integration:** Tag-filtered queries supported — "tell me about #budget files" narrows vector search to matching docs.

---

## Web UI

**Stack:** Spring Boot serves React build (`/frontend/dist`) as static assets. REST API at `/api/*`.

**Layout:**

```
┌─────────────────────────────────────────────────────────────┐
│  [Wiki] [Sources] [Lint]                    [⚙ Settings]   │
├──────────────┬──────────────────────────┬───────────────────┤
│              │                          │                   │
│  Wiki Pages  │   Content Viewer         │   Chat            │
│  ──────────  │   ─────────────          │   ───────────     │
│  index.md    │   (renders markdown,     │   Ask anything    │
│  topic-a.md  │    Excel table,          │   about your      │
│  topic-b.md  │    JSON tree,            │   knowledge base  │
│  ...         │    Word doc,             │                   │
│              │    OpenAPI explorer,     │   [File answer    │
│  Sources     │    etc.)                 │    back to wiki]  │
│  ──────────  │                          │                   │
│  📁 Finance  │                          │                   │
│    file.xlsx │                          │                   │
│  📁 Tech     │                          │                   │
│    spec.json │                          │                   │
│              │                          │                   │
│  Tags        │                          │                   │
│  #budget (3) │                          │                   │
│  #api (2)    │                          │                   │
│              │                          │                   │
│  [+ drop]    │                          │                   │
└──────────────┴──────────────────────────┴───────────────────┘
```

**Left panel:** Wiki page tree + source files grouped by category + tag filter list. Drop zone at bottom for new files.

**Center viewer (type-aware):**
- Markdown → rendered formatted
- Excel → sortable HTML table
- JSON → collapsible tree
- OpenAPI → endpoint explorer (method, path, params, response schema)
- Word → formatted text
- Code → syntax highlighted

**Right panel:** Persistent chat. "File back to wiki" button on LLM answers. Tag filter chips available.

**Ingest confirmation modal:** Appears after file drop. Shows LLM-suggested category + tags. User edits/confirms before ingest proceeds.

---

## Error Handling

| Scenario | Behavior |
|---|---|
| Unsupported file type | UI error: "No reader found." File not copied to `sources/`. |
| Reader fails (corrupt file) | File quarantined to `sources/errors/`, logged, UI shows error. |
| LLM API failure mid-ingest | Wiki pages not written (rolled back), file stays unprocessed, retry available. |
| Duplicate filename | UI warns: "Already exists. Re-ingest to update?" Replaces source + re-runs pipeline. |
| Large files (e.g. 10k-row Excel) | Reader chunks content → multiple LLM calls → results merged before wiki update. |
| File watcher + UI upload race | Both funnel through sequential ingest queue. No conflicts. |

Wiki page edits are atomic per page. `log.md` is append-only and never rewritten.

---

## CLI

Spring Boot app exposes a CLI mode via Spring Shell:

```bash
./gradlew bootRun                    # Start web UI (default: localhost:8080)
./gradlew bootRun --args='--ingest path/to/file.xlsx'
./gradlew bootRun --args='--query "your question"'
./gradlew bootRun --args='--lint'
```

---

## Packaging & Distribution

**Goal:** User receives a zip/repo, runs two commands, wiki is running. No Java, no manual setup.

**Prerequisites for recipient:** Docker + Docker Compose (only requirement).

**Directory structure shipped:**
```
karp/
├── docker-compose.yml
├── .env.example            # Copy to .env, set ANTHROPIC_API_KEY
├── README.md               # Getting started in <5 steps
└── (source code / pre-built image)
```

**docker-compose.yml:**
```yaml
services:
  app:
    image: karp:latest          # or build: .
    ports:
      - "8080:8080"
    env_file: .env
    volumes:
      - ./data/sources:/app/sources     # raw files — persisted on host
      - ./data/wiki:/app/wiki           # wiki pages — persisted on host
    depends_on:
      - qdrant

  qdrant:
    image: qdrant/qdrant
    volumes:
      - ./data/qdrant:/qdrant/storage   # vector index — persisted on host
```

All data lives in `./data/` on the host — survives container restarts and removals.

**Getting started (README):**
1. Install Docker Desktop
2. Copy `.env.example` → `.env`, set `ANTHROPIC_API_KEY=sk-...`
3. `docker compose up -d`
4. Open `http://localhost:8080`
5. Drop files in the UI to start building your wiki

**Transferring to another machine:** zip `./data/` → copy to new machine → `docker compose up -d`. All sources, wiki pages, and vector index intact.

---

## MCP Server

Karp exposes an MCP server so AI assistants (Claude Desktop, other MCP clients) can read, search, and interact with the wiki directly — without opening the web UI.

**Implementation:** Spring AI MCP Server starter (``spring-ai-mcp-server-spring-boot-starter``). Tools defined as `@Tool`-annotated Spring beans, auto-registered at startup.

**Exposed tools:**

| Tool | Description |
|---|---|
| `list_wiki_pages()` | List all wiki page names |
| `read_wiki_page(name)` | Return full markdown content of a wiki page |
| `list_sources()` | List all source files with category + tags |
| `search(query, tags?, category?)` | Semantic search, returns top-5 relevant wiki pages |
| `lint()` | Run wiki health check, return report |
| `ingest_file(path)` | Ingest a file by absolute path (for local MCP clients) |

**Exposed resources:**

| URI pattern | Description |
|---|---|
| `wiki://{page-name}` | Read a wiki page as a resource |
| `source://{file-name}` | Read source file preview as a resource |

**Transport:** SSE (Server-Sent Events) at `/mcp/sse` — standard MCP over HTTP.

**Claude Desktop config** (user adds once):
```json
{
  "mcpServers": {
    "karp": {
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```

The MCP server shares all backend services (WikiService, QueryService, IngestService, LintService) — no duplicate logic.

---

## Dependencies

**Backend (build.gradle.kts):**
```
com.anthropic:anthropic-java                          # Claude API (official Java SDK)
org.springframework.boot                              # Web server + DI
org.springframework.ai:spring-ai-mcp-server-spring-boot-starter  # MCP server
io.qdrant:client                                      # Qdrant vector DB client
org.apache.poi:poi-ooxml                              # Excel + Word reader
org.apache.pdfbox:pdfbox                              # PDF reader
com.fasterxml.jackson.module:kotlin                   # JSON reader
org.yaml:snakeyaml                                    # YAML reader
io.swagger.parser.v3:swagger-parser                   # OpenAPI reader
```

**Frontend (package.json):**
```
react + react-dom
vite
typescript
react-markdown          # Markdown rendering
@uiw/react-json-view    # JSON collapsible tree
```
