# Source File Links Design

**Date:** 2026-05-24

## Goal

Show the filename in the CenterPanel header as a clickable link that opens the original uploaded file in a new browser tab. Works for both source file views and wiki page views (linking back to the source file that generated the wiki page).

## Backend Changes

### 1. `GET /api/sources/{name}/raw` — new endpoint in `ApiController.kt`

Serves the raw file bytes from `sourcesDir` with the correct `Content-Type` header (detected via `Files.probeContentType`). Clicking the link from the browser will open or render the file natively (PDF, image, etc.).

Security: reject any `name` containing `/` or `..` to prevent path traversal. Return 404 if file not found.

### 2. Wiki page source tracking — `IngestService.kt`

After each `wiki.writePage(pageName, content)` call during ingest, write a metadata file to `wikiDir/.meta/{pageName}.json`:

```json
{"source": "original-filename.pdf"}
```

Same pattern as the existing `sourcesDir/.meta/{fileName}.json` used for tags.

### 3. `GET /api/wiki/{name}` — updated in `ApiController.kt`

Read `wikiDir/.meta/{name}.json` if present and include `"source"` in the response body alongside `name` and `content`. Omit the field when no meta file exists (e.g. manually created wiki pages).

## Frontend Changes

### `CenterPanel.tsx`

Replace `<span>{title}</span>` with conditional link rendering:

- **Source selection:** `<a href="/api/sources/{encodedName}/raw" target="_blank">{title}</a>`
- **Wiki selection with source:** `<a href="/api/sources/{encodedSource}/raw" target="_blank">{title}</a>` (only when API returns a `source` field)
- **Wiki selection without source:** `<span>{title}</span>` (unchanged — no original file to link to)

Style: inherit existing faint text color, no underline by default, underline on hover.

### `types/index.ts`

Add `source?: string` to the `WikiPage` type.

## Files Touched

| File | Change |
|------|--------|
| `backend/src/main/kotlin/karp/api/ApiController.kt` | Add `/sources/{name}/raw` endpoint; update `/wiki/{name}` to include source |
| `backend/src/main/kotlin/karp/core/IngestService.kt` | Write `.meta/{pageName}.json` after each wiki page write |
| `frontend/src/components/CenterPanel.tsx` | Conditional link for title |
| `frontend/src/types/index.ts` | Add `source?: string` to `WikiPage` |

## Out of Scope

- Retroactively linking existing wiki pages to their sources (no meta files exist for those yet)
- Download behavior (open-in-new-tab is the chosen UX)
- Wiki pages created via MCP tools or manually — no source link shown