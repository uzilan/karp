# Karp Wiki

LLM-maintained personal knowledge base. Drop files in, Claude builds a structured wiki.

## Prerequisites

- Docker Desktop
- Anthropic API key (console.anthropic.com/settings/keys)

## Getting Started

1. Clone this repo
2. Copy `.env.example` to `.env` and fill in your API key
3. Run: `docker compose up -d`
4. Open: http://localhost:8080

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
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```

Restart Claude Desktop. You can now ask Claude about your wiki directly.

## Transferring to Another Machine

1. `zip -r karp-data.zip data/`
2. Copy to new machine alongside `docker-compose.yml` and `.env`
3. `docker compose up -d`

All sources, wiki pages, and vector index are intact.
