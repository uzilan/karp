# Karp Wiki

LLM-maintained personal knowledge base. Drop files in, Claude builds a structured wiki.

## Prerequisites

- Docker Desktop
- Claude Code CLI installed and logged in (`claude` in your PATH)

## Getting Started

1. Clone this repo
2. Start the Claude proxy (keeps running in background):
   ```bash
   node claude-proxy.js
   ```
3. Run Docker:
   ```bash
   docker compose up -d
   ```
4. Open: http://localhost:7777

## How It Works

The app shells out to your local `claude` CLI for LLM operations (ingest, query, lint). The proxy at port 8765 bridges Docker to your local Claude session — no API key required.

## Adding Files

- Drag files into the web UI, or
- Drop files into `./data/sources/` (auto-detected)

Supported: `.xlsx`, `.xls`, `.docx`, `.pdf`, `.json`, `.yaml`, `.yml`, `.md`, `.txt`, and most code files.

## Claude Desktop (MCP)

Add to `~/.claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "karp": {
      "url": "http://localhost:7777/mcp/sse"
    }
  }
}
```

Restart Claude Desktop. You can now ask Claude about your wiki directly.

## Transferring to Another Machine

1. `zip -r karp-data.zip data/`
2. Copy to new machine alongside `docker-compose.yml`
3. Start the proxy and run `docker compose up -d`

All sources, wiki pages, and vector index are intact.
