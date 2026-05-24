# Source File Links Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the filename in CenterPanel header as a clickable link that opens the original uploaded file in a new browser tab — for both source file views and wiki page views.

**Architecture:** New `/api/sources/{name}/raw` endpoint serves raw file bytes. `WikiService` gains two methods to write/read per-page source metadata stored in `wikiDir/.meta/{name}.json`. `CenterPanel` renders a link when source info is available.

**Tech Stack:** Kotlin/Spring Boot 4, JUnit 5, Mockito-Kotlin, React/TypeScript

---

## File Map

| File | Change |
|------|--------|
| `backend/src/main/kotlin/karp/core/WikiService.kt` | Add `writePageSource` and `readPageSource` methods |
| `backend/src/main/kotlin/karp/core/IngestService.kt` | Call `wiki.writePageSource` after each `wiki.writePage` |
| `backend/src/main/kotlin/karp/api/ApiController.kt` | Add `GET /sources/{name}/raw`; update `GET /wiki/{name}` to include source |
| `backend/src/test/kotlin/karp/core/WikiServiceTest.kt` | Tests for two new methods |
| `backend/src/test/kotlin/karp/core/IngestServiceTest.kt` | Test that ingest writes source meta |
| `backend/src/test/kotlin/karp/api/ApiControllerTest.kt` | Tests for raw endpoint and updated wiki endpoint |
| `frontend/src/types/index.ts` | Add `source?: string` to `WikiPage` |
| `frontend/src/components/CenterPanel.tsx` | Conditional link for title |

---

### Task 1: WikiService — add `writePageSource` and `readPageSource`

**Files:**
- Modify: `backend/src/main/kotlin/karp/core/WikiService.kt`
- Modify: `backend/src/test/kotlin/karp/core/WikiServiceTest.kt`

- [ ] **Step 1: Write failing tests**

Open `backend/src/test/kotlin/karp/core/WikiServiceTest.kt` and add:

```kotlin
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.nio.file.Path

// (existing test stays — add below it)

@Test
fun `writePageSource and readPageSource round-trip`(@TempDir dir: Path) {
    val svc = WikiService(dir, mock())
    svc.writePageSource("finance", "report.pdf")
    assertEquals("report.pdf", svc.readPageSource("finance"))
}

@Test
fun `readPageSource returns null when no meta file`(@TempDir dir: Path) {
    val svc = WikiService(dir, mock())
    assertNull(svc.readPageSource("nonexistent"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && ./gradlew test --tests "karp.core.WikiServiceTest" 2>&1 | tail -15
```

Expected: FAIL — `writePageSource` and `readPageSource` not defined.

- [ ] **Step 3: Implement `writePageSource` and `readPageSource` in WikiService**

Open `backend/src/main/kotlin/karp/core/WikiService.kt` and add these two methods (after `appendToLog`):

```kotlin
fun writePageSource(name: String, source: String) {
    val metaDir = wikiDir.resolve(".meta")
    java.nio.file.Files.createDirectories(metaDir)
    metaDir.resolve("$name.json").toFile().writeText("""{"source":"$source"}""")
}

fun readPageSource(name: String): String? {
    val metaFile = wikiDir.resolve(".meta/$name.json").toFile()
    if (!metaFile.exists()) return null
    return try {
        val text = metaFile.readText()
        // parse {"source":"..."} — simple extraction avoids an extra Jackson dependency here
        val match = Regex(""""source"\s*:\s*"([^"]+)"""").find(text)
        match?.groupValues?.get(1)
    } catch (_: Exception) { null }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./gradlew test --tests "karp.core.WikiServiceTest" 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL, all WikiServiceTest tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/karp/core/WikiService.kt \
        backend/src/test/kotlin/karp/core/WikiServiceTest.kt
git commit -m "feat: add writePageSource/readPageSource to WikiService"
```

---

### Task 2: IngestService — write source metadata per wiki page

**Files:**
- Modify: `backend/src/main/kotlin/karp/core/IngestService.kt`
- Modify: `backend/src/test/kotlin/karp/core/IngestServiceTest.kt`

- [ ] **Step 1: Write failing test**

Open `backend/src/test/kotlin/karp/core/IngestServiceTest.kt`. The full file after edits:

```kotlin
package karp.core

import karp.readers.ReadResult
import karp.readers.ReaderRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.*
import java.nio.file.Path

class IngestServiceTest {

    private val registry = mock<ReaderRegistry>()
    private val wiki = mock<WikiService>()
    private val llm = mock<LlmService>()
    private val embedding = mock<EmbeddingService>()

    @Test
    fun `ingest enqueues job and status becomes PENDING`(@TempDir sourcesDir: Path) {
        val file = sourcesDir.resolve("test.json")
        file.toFile().writeText("{}")

        val readResult = ReadResult("text", emptyMap(), "preview")
        whenever(registry.read(any())).thenReturn(readResult)
        whenever(llm.ingestDocument(any())).thenReturn(IngestResult(emptyList(), emptyList()))

        val svc = IngestService(sourcesDir, registry, wiki, llm, embedding)
        svc.ingest(file)

        val status = svc.getStatus("test.json")
        assertEquals(IngestStatus.PENDING, status)
    }

    @Test
    fun `ingest writes source metadata for each wiki page`(@TempDir sourcesDir: Path) {
        val file = sourcesDir.resolve("report.pdf")
        file.toFile().writeText("content")

        val readResult = ReadResult("text", emptyMap(), "preview")
        val pages = listOf(WikiPageUpdate("finance", "# Finance"), WikiPageUpdate("index", "# Index"))
        whenever(registry.read(any())).thenReturn(readResult)
        whenever(llm.ingestDocument(any())).thenReturn(IngestResult(listOf("finance"), pages))

        val svc = IngestService(sourcesDir, registry, wiki, llm, embedding)
        svc.ingest(file)

        // allow background worker to finish
        Thread.sleep(500)

        verify(wiki).writePageSource("finance", "report.pdf")
        verify(wiki).writePageSource("index", "report.pdf")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && ./gradlew test --tests "karp.core.IngestServiceTest.ingest writes source metadata for each wiki page" 2>&1 | tail -15
```

Expected: FAIL — `writePageSource` not called.

- [ ] **Step 3: Update IngestService to call `writePageSource`**

Open `backend/src/main/kotlin/karp/core/IngestService.kt`. In `processQueue`, after `wiki.writePage(update.name, update.content)`, add:

```kotlin
ingestResult.pages.forEach { update ->
    wiki.writePage(update.name, update.content)
    wiki.writePageSource(update.name, job.fileName)   // <-- add this line
    embedding.upsertPage(update.name, update.content, ingestResult.tags)
}
```

- [ ] **Step 4: Run all tests to verify they pass**

```bash
cd backend && ./gradlew test --tests "karp.core.IngestServiceTest" 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL, all IngestServiceTest tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/karp/core/IngestService.kt \
        backend/src/test/kotlin/karp/core/IngestServiceTest.kt
git commit -m "feat: write wiki page source metadata during ingest"
```

---

### Task 3: ApiController — add `/sources/{name}/raw` and update `/wiki/{name}`

**Files:**
- Modify: `backend/src/main/kotlin/karp/api/ApiController.kt`
- Modify: `backend/src/test/kotlin/karp/api/ApiControllerTest.kt`

- [ ] **Step 1: Write failing tests**

Open `backend/src/test/kotlin/karp/api/ApiControllerTest.kt`. Add these tests after the existing ones:

```kotlin
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.MediaType
import java.nio.file.Path as JPath

@Test
fun `GET sources-name-raw returns file bytes with content type`(@TempDir dir: JPath) {
    val file = dir.resolve("doc.txt")
    file.toFile().writeText("hello")
    whenever(sourcesDir.resolve("doc.txt")).thenReturn(file)

    mvc.get("/api/sources/doc.txt/raw")
        .andExpect {
            status { isOk() }
            content { string("hello") }
        }
}

@Test
fun `GET sources-name-raw returns 404 for missing file`(@TempDir dir: JPath) {
    val missing = dir.resolve("missing.txt")
    whenever(sourcesDir.resolve("missing.txt")).thenReturn(missing)

    mvc.get("/api/sources/missing.txt/raw")
        .andExpect { status { isNotFound() } }
}

@Test
fun `GET sources-name-raw rejects path traversal`() {
    mvc.get("/api/sources/..dangerous/raw")
        .andExpect { status { isBadRequest() } }
}

@Test
fun `GET api-wiki-name includes source when meta exists`() {
    whenever(wiki.readPage("finance")).thenReturn("# Finance")
    whenever(wiki.readPageSource("finance")).thenReturn("report.pdf")

    mvc.get("/api/wiki/finance")
        .andExpect {
            status { isOk() }
            jsonPath("$.source") { value("report.pdf") }
        }
}

@Test
fun `GET api-wiki-name omits source when no meta`() {
    whenever(wiki.readPage("index")).thenReturn("# Index")
    whenever(wiki.readPageSource("index")).thenReturn(null)

    mvc.get("/api/wiki/index")
        .andExpect {
            status { isOk() }
            jsonPath("$.source") { doesNotExist() }
        }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && ./gradlew test --tests "karp.api.ApiControllerTest" 2>&1 | tail -20
```

Expected: FAIL — endpoints not yet updated.

- [ ] **Step 3: Add `GET /sources/{name}/raw` to ApiController**

Open `backend/src/main/kotlin/karp/api/ApiController.kt`. Add after the `sourceData` endpoint:

```kotlin
@GetMapping("/sources/{name}/raw")
fun rawSource(@PathVariable name: String): ResponseEntity<ByteArray> {
    if (name.contains("..")) return ResponseEntity.badRequest().build()
    val file = sourcesDir.resolve(name)
    if (!file.toFile().exists()) return ResponseEntity.notFound().build()
    val contentType = Files.probeContentType(file) ?: "application/octet-stream"
    return ResponseEntity.ok()
        .header("Content-Type", contentType)
        .body(file.toFile().readBytes())
}
```

- [ ] **Step 4: Update `GET /wiki/{name}` to include source**

In the same file, replace the existing `getWikiPage` method:

```kotlin
@GetMapping("/wiki/{name}")
fun getWikiPage(@PathVariable name: String): ResponseEntity<Map<String, String?>> {
    val content = wiki.readPage(name) ?: return ResponseEntity.notFound().build()
    val source = wiki.readPageSource(name)
    val response = mutableMapOf<String, String?>("name" to name, "content" to content)
    if (source != null) response["source"] = source
    return ResponseEntity.ok(response)
}
```

- [ ] **Step 5: Run all tests to verify they pass**

```bash
cd backend && ./gradlew test 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/karp/api/ApiController.kt \
        backend/src/test/kotlin/karp/api/ApiControllerTest.kt
git commit -m "feat: add raw file endpoint and source field to wiki page API"
```

---

### Task 4: Frontend — type update and CenterPanel link

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/components/CenterPanel.tsx`

- [ ] **Step 1: Add `source` to `WikiPage` type**

Open `frontend/src/types/index.ts`. Replace the `WikiPage` interface:

```typescript
export interface WikiPage {
  name: string
  content: string
  source?: string
}
```

- [ ] **Step 2: Add `sourceFile` state to CenterPanel and populate it**

Open `frontend/src/components/CenterPanel.tsx`.

Add `sourceFile` to the state declarations (alongside the existing `title` and `tags`):

```typescript
const [sourceFile, setSourceFile] = useState<string | null>(null)
```

In the `useEffect` load function, update both branches to set `sourceFile`:

```typescript
if (selection.type === 'wiki') {
  const page = await api.wiki.get(selection.name)
  setContent(page.content)
  setTitle(selection.name)
  setTags([])
  setSourceFile(page.source ?? null)
} else {
  const data = await api.sources.getData(selection.name)
  setContent(data.text)
  setTitle(selection.name)
  setTags(data.tags ?? [])
  setSourceFile(selection.name)
}
```

Also reset `sourceFile` in the early return branch:

```typescript
if (!selection) { setContent(null); setTitle(''); setTags([]); setSourceFile(null); return }
```

- [ ] **Step 3: Replace title `<span>` with conditional link**

In `CenterPanel.tsx`, locate the header div (around line 77) and replace `<span>{title}</span>` with:

```tsx
{sourceFile ? (
  <a
    href={`/api/sources/${encodeURIComponent(sourceFile)}/raw`}
    target="_blank"
    rel="noreferrer"
    style={{ color: 'inherit', textDecoration: 'none' }}
    onMouseEnter={e => { (e.currentTarget as HTMLAnchorElement).style.textDecoration = 'underline' }}
    onMouseLeave={e => { (e.currentTarget as HTMLAnchorElement).style.textDecoration = 'none' }}
  >
    {title}
  </a>
) : (
  <span>{title}</span>
)}
```

- [ ] **Step 4: Verify TypeScript compiles**

```bash
cd frontend && npm run build 2>&1 | tail -20
```

Expected: no TypeScript errors, build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/index.ts \
        frontend/src/components/CenterPanel.tsx
git commit -m "feat: show filename as link to original file in CenterPanel"
```

---

### Task 5: Manual smoke test

- [ ] **Step 1: Start the app in IntelliJ (backend) and run `npm run dev` (frontend)**

- [ ] **Step 2: Upload a PDF or image file via the UI**

- [ ] **Step 3: Select the source file in the left panel — verify the filename at top is a clickable link that opens the raw file in a new tab**

- [ ] **Step 4: Wait for ingest to complete, then select a generated wiki page — verify the title links to the original source file**

- [ ] **Step 5: Select a manually created wiki page (not from ingest) — verify the title is plain text, no link**