# Wiki Clustering — Virtual Folders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Group wiki pages into AI-named semantic clusters shown as collapsible virtual folders in the left panel, computed async in the background after each page write.

**Architecture:** After each `writePage`/`deletePage`, `ClusterService.triggerAsync()` fires a background job that scrolls all vectors from Qdrant, runs k-means, names each cluster via LLM, and caches the result in an `AtomicReference`. The frontend fetches `/api/wiki/clusters` on load and after writes (silent, no spinner), persists results in localStorage, and renders folders instead of a flat page list.

**Tech Stack:** Kotlin/Spring Boot, Qdrant gRPC client 1.9.1, mockito-kotlin 5.3.1, React/TypeScript, localStorage

---

## File Map

| Action | File |
|--------|------|
| Create | `backend/src/main/kotlin/karp/core/ClusterService.kt` |
| Modify | `backend/src/main/kotlin/karp/core/EmbeddingService.kt` |
| Modify | `backend/src/main/kotlin/karp/core/LlmService.kt` |
| Modify | `backend/src/main/kotlin/karp/core/WikiService.kt` |
| Modify | `backend/src/main/kotlin/karp/api/ApiController.kt` |
| Create | `backend/src/test/kotlin/karp/core/ClusterServiceTest.kt` |
| Modify | `backend/src/test/kotlin/karp/api/ApiControllerTest.kt` |
| Modify | `frontend/src/api/client.ts` |
| Modify | `frontend/src/components/LeftPanel.tsx` |

---

## Task 1: Add `scrollAll()` to EmbeddingService

Fetches all points (name + vector) from Qdrant using the scroll API.

**Files:**
- Modify: `backend/src/main/kotlin/karp/core/EmbeddingService.kt`
- Test: `backend/src/test/kotlin/karp/core/LocalEmbeddingTest.kt` (manual verification — no mock needed here, tested indirectly via ClusterServiceTest)

- [ ] **Step 1: Add `scrollAll()` method to `EmbeddingService`**

Add after the `search()` method:

```kotlin
fun scrollAll(): List<Pair<String, List<Float>>> {
    val request = ScrollPoints.newBuilder()
        .setCollectionName(COLLECTION)
        .setLimit(1000)
        .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true))
        .setWithVectors(WithVectorsSelector.newBuilder().setEnable(true))
        .build()
    return try {
        qdrant.scrollAsync(request).get().resultList.mapNotNull { point ->
            val name = point.payloadMap["name"]?.stringValue ?: return@mapNotNull null
            val vector = point.vectors.vector.dataList
            name to vector
        }
    } catch (e: Exception) {
        log.warn("scrollAll failed: ${e.message}")
        emptyList()
    }
}
```

`WithVectorsSelector` is already covered by the existing `import io.qdrant.client.grpc.Points.*`.

- [ ] **Step 2: Compile to verify no errors**

```bash
cd backend && ./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/karp/core/EmbeddingService.kt
git commit -m "feat: add scrollAll() to EmbeddingService for cluster vector retrieval"
```

---

## Task 2: Add `nameCluster()` to LlmService

Names a cluster given its page names via an LLM prompt.

**Files:**
- Modify: `backend/src/main/kotlin/karp/core/LlmService.kt`

- [ ] **Step 1: Add `nameCluster()` method to `LlmService`**

Add after `lintWiki()`:

```kotlin
fun nameCluster(pageNames: List<String>): String {
    val prompt = "These wiki pages belong to the same topic cluster: ${pageNames.joinToString(", ")}. " +
        "Respond with a short 1-3 word label for this cluster (e.g. 'Authentication', 'API Design', 'Data Model'). " +
        "Respond with the label only, no explanation, no punctuation."
    return ask("You are a wiki organizer. Respond with a short cluster label only.", prompt)
        .trim().take(50)
}
```

- [ ] **Step 2: Compile**

```bash
cd backend && ./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/karp/core/LlmService.kt
git commit -m "feat: add nameCluster() to LlmService"
```

---

## Task 3: Create ClusterService

Background clustering: scrolls vectors, runs k-means, names clusters, caches result.

**Files:**
- Create: `backend/src/main/kotlin/karp/core/ClusterService.kt`
- Create: `backend/src/test/kotlin/karp/core/ClusterServiceTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/kotlin/karp/core/ClusterServiceTest.kt`:

```kotlin
package karp.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ClusterServiceTest {

    private val embedding: EmbeddingService = mock()
    private val llm: LlmService = mock()
    private val svc = ClusterService(embedding, llm)

    @Test
    fun `getClusters returns empty map initially`() {
        assertEquals(emptyMap<String, List<String>>(), svc.getClusters())
    }

    @Test
    fun `triggerAsync with fewer than 3 pages returns All Pages cluster`() {
        whenever(embedding.scrollAll()).thenReturn(
            listOf("page-a" to listOf(1f, 0f), "page-b" to listOf(0f, 1f))
        )

        svc.triggerAsync()
        Thread.sleep(500)

        val clusters = svc.getClusters()
        assertEquals(1, clusters.size)
        assertEquals("All Pages", clusters.keys.first())
        assertTrue(clusters["All Pages"]!!.containsAll(listOf("page-a", "page-b")))
    }

    @Test
    fun `triggerAsync with empty pages returns empty map`() {
        whenever(embedding.scrollAll()).thenReturn(emptyList())

        svc.triggerAsync()
        Thread.sleep(500)

        assertEquals(emptyMap<String, List<String>>(), svc.getClusters())
    }

    @Test
    fun `triggerAsync clusters pages and names them`() {
        val pages = listOf(
            "auth-login" to listOf(1f, 0f, 0f),
            "auth-token" to listOf(0.9f, 0.1f, 0f),
            "auth-session" to listOf(0.85f, 0.1f, 0.05f),
            "api-rest" to listOf(0f, 1f, 0f),
            "api-graphql" to listOf(0f, 0.9f, 0.1f),
            "api-grpc" to listOf(0.05f, 0.85f, 0.1f),
        )
        whenever(embedding.scrollAll()).thenReturn(pages)
        whenever(llm.nameCluster(any())).thenReturn("Auth").thenReturn("API")

        svc.triggerAsync()
        Thread.sleep(500)

        val clusters = svc.getClusters()
        assertEquals(2, clusters.size)
        val allPages = clusters.values.flatten()
        assertTrue(allPages.containsAll(pages.map { it.first }))
    }

    @Test
    fun `triggerAsync falls back to Cluster N when LLM fails`() {
        val pages = (1..6).map { "page-$it" to listOf(if (it <= 3) 1f else 0f, if (it > 3) 1f else 0f) }
        whenever(embedding.scrollAll()).thenReturn(pages)
        whenever(llm.nameCluster(any())).thenThrow(RuntimeException("LLM unavailable"))

        svc.triggerAsync()
        Thread.sleep(500)

        val clusters = svc.getClusters()
        assertTrue(clusters.keys.all { it.startsWith("Cluster ") })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && ./gradlew test --tests "karp.core.ClusterServiceTest" 2>&1 | tail -5
```

Expected: compilation failure — `ClusterService` not found.

- [ ] **Step 3: Create `ClusterService`**

Create `backend/src/main/kotlin/karp/core/ClusterService.kt`:

```kotlin
package karp.core

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Service
class ClusterService(
    private val embedding: EmbeddingService,
    private val llm: LlmService,
) {
    private val log = LoggerFactory.getLogger(ClusterService::class.java)
    private val cache = AtomicReference<Map<String, List<String>>>(emptyMap())
    private val running = AtomicBoolean(false)

    fun getClusters(): Map<String, List<String>> = cache.get()

    fun triggerAsync() {
        if (!running.compareAndSet(false, true)) return
        CompletableFuture.runAsync {
            try {
                val all = embedding.scrollAll()
                if (all.isEmpty()) return@runAsync
                if (all.size < 3) {
                    cache.set(mapOf("All Pages" to all.map { it.first }))
                    return@runAsync
                }
                val k = max(2, sqrt(all.size / 2.0).roundToInt())
                val assignments = kmeans(all.map { it.second }, k)
                val grouped = (0 until k).associateWith { c ->
                    all.indices.filter { assignments[it] == c }.map { all[it].first }
                }.filter { it.value.isNotEmpty() }
                val named = grouped.entries.mapIndexed { i, (_, pages) ->
                    val name = try { llm.nameCluster(pages) } catch (e: Exception) { "Cluster ${i + 1}" }
                    name to pages
                }.toMap()
                cache.set(named)
            } catch (e: Exception) {
                log.warn("Clustering failed: ${e.message}")
            } finally {
                running.set(false)
            }
        }
    }

    private fun kmeans(points: List<List<Float>>, k: Int, maxIter: Int = 20): IntArray {
        val n = points.size
        val dims = points[0].size
        var centroids = points.shuffled().take(k).map { it.toMutableList() }
        val assignments = IntArray(n)

        repeat(maxIter) {
            for (i in 0 until n) {
                assignments[i] = centroids.indices.minByOrNull { c ->
                    cosineDistance(points[i], centroids[c])
                } ?: 0
            }
            for (c in 0 until k) {
                val members = (0 until n).filter { assignments[it] == c }
                if (members.isEmpty()) continue
                centroids[c] = MutableList(dims) { d -> members.map { points[it][d] }.average().toFloat() }
            }
        }
        return assignments
    }

    private fun cosineDistance(a: List<Float>, b: List<Float>): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return 1f - dot / (sqrt(na) * sqrt(nb) + 1e-8f)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./gradlew test --tests "karp.core.ClusterServiceTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` with all 5 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/karp/core/ClusterService.kt \
        backend/src/test/kotlin/karp/core/ClusterServiceTest.kt
git commit -m "feat: add ClusterService with async k-means clustering"
```

---

## Task 4: Wire ClusterService into WikiService

Trigger async clustering after each page write or delete.

**Files:**
- Modify: `backend/src/main/kotlin/karp/core/WikiService.kt`

**Note on circular dependency:** `LlmService` → `WikiService` → `ClusterService` → `LlmService` forms a cycle. Break it with `@Lazy` on `clusterService` so Spring injects a proxy and resolves late.

- [ ] **Step 1: Update `WikiService` constructor and trigger methods**

Replace the class declaration and `writePage`/`deletePage` methods:

```kotlin
package karp.core

import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*

@Service
class WikiService(
    private val wikiDir: Path,
    @Lazy private val clusterService: ClusterService,
) {
    fun listPages(): List<String> =
        wikiDir
            .toFile()
            .listFiles { f -> f.extension == "md" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

    fun readPage(name: String): String? {
        val file = wikiDir.resolve("$name.md")
        return if (file.exists()) file.readText() else null
    }

    fun writePage(name: String, content: String) {
        wikiDir.resolve("$name.md").writeText(content)
        clusterService.triggerAsync()
    }

    fun deletePage(name: String) {
        wikiDir.resolve("$name.md").deleteIfExists()
        clusterService.triggerAsync()
    }

    fun appendToLog(entry: String) {
        val log = wikiDir.resolve("log.md")
        val timestamp = Instant.now().toString()
        val line = "[$timestamp] $entry\n"
        if (log.exists()) {
            log.appendText(line)
        } else {
            log.writeText(line)
        }
    }

    fun allPagesContent(): Map<String, String> = listPages().associateWith { readPage(it) ?: "" }
}
```

- [ ] **Step 2: Run existing WikiService tests**

```bash
cd backend && ./gradlew test --tests "karp.core.WikiServiceTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`. All existing tests pass (they use `@TempDir` and don't inject `ClusterService`, so they'll fail to construct. Fix: update tests to pass a mock or no-op `ClusterService`.)

If tests fail with `No suitable constructor found` or similar, the `@TempDir`-based `WikiService(dir)` constructor no longer matches. Update `WikiServiceTest` to use a mock:

```kotlin
package karp.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.nio.file.Path

class WikiServiceTest {

    private fun svc(dir: Path) = WikiService(dir, mock())

    @Test
    fun `write and read wiki page round-trips content`(@TempDir dir: Path) {
        val svc = svc(dir)
        svc.writePage("finance", "# Finance\nSome content.")
        val content = svc.readPage("finance")
        assertEquals("# Finance\nSome content.", content)
    }

    @Test
    fun `list pages returns written pages`(@TempDir dir: Path) {
        val svc = svc(dir)
        svc.writePage("page-a", "content a")
        svc.writePage("page-b", "content b")
        val pages = svc.listPages()
        assertTrue(pages.contains("page-a"))
        assertTrue(pages.contains("page-b"))
    }

    @Test
    fun `readPage returns null for missing page`(@TempDir dir: Path) {
        assertNull(svc(dir).readPage("nonexistent"))
    }

    @Test
    fun `appendToLog adds line`(@TempDir dir: Path) {
        val svc = svc(dir)
        svc.appendToLog("First entry")
        svc.appendToLog("Second entry")
        val log = svc.readPage("log")!!
        assertTrue(log.contains("First entry"))
        assertTrue(log.contains("Second entry"))
    }

    @Test
    fun `allPagesContent returns all pages`(@TempDir dir: Path) {
        val svc = svc(dir)
        svc.writePage("a", "content a")
        svc.writePage("b", "content b")
        val all = svc.allPagesContent()
        assertEquals(2, all.size)
        assertEquals("content a", all["a"])
    }
}
```

- [ ] **Step 3: Run WikiService tests again**

```bash
cd backend && ./gradlew test --tests "karp.core.WikiServiceTest" 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/karp/core/WikiService.kt \
        backend/src/test/kotlin/karp/core/WikiServiceTest.kt
git commit -m "feat: trigger async clustering after wiki page write/delete"
```

---

## Task 5: Add GET /wiki/clusters endpoint

Expose cluster cache via a new REST endpoint.

**Files:**
- Modify: `backend/src/main/kotlin/karp/api/ApiController.kt`
- Modify: `backend/src/test/kotlin/karp/api/ApiControllerTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `ApiControllerTest`:

```kotlin
@MockitoBean lateinit var clusterService: ClusterService

@Test
fun `GET api-wiki-clusters returns cluster map`() {
    whenever(clusterService.getClusters()).thenReturn(
        mapOf("Authentication" to listOf("auth-login", "auth-token"))
    )

    mvc.get("/api/wiki/clusters")
        .andExpect {
            status { isOk() }
            jsonPath("$.Authentication[0]") { value("auth-login") }
        }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && ./gradlew test --tests "karp.api.ApiControllerTest.GET api-wiki-clusters returns cluster map" 2>&1 | tail -5
```

Expected: FAIL — endpoint not found (404).

- [ ] **Step 3: Add `ClusterService` to `ApiController` and the new endpoint**

In `ApiController.kt`:

1. Add `private val clusterService: ClusterService` to the constructor parameters.
2. Add the endpoint inside the Wiki section:

```kotlin
@GetMapping("/wiki/clusters")
fun getWikiClusters(): Map<String, List<String>> = clusterService.getClusters()
```

- [ ] **Step 4: Run all controller tests**

```bash
cd backend && ./gradlew test --tests "karp.api.ApiControllerTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Run full test suite**

```bash
cd backend && ./gradlew test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/karp/api/ApiController.kt \
        backend/src/test/kotlin/karp/api/ApiControllerTest.kt
git commit -m "feat: add GET /api/wiki/clusters endpoint"
```

---

## Task 6: Add `api.wiki.clusters()` to frontend client

**Files:**
- Modify: `frontend/src/api/client.ts`

- [ ] **Step 1: Add `clusters` to `api.wiki`**

In `client.ts`, update the `wiki` object:

```ts
wiki: {
  list: () => get<string[]>('/wiki'),
  get: (name: string) => get<WikiPage>(`/wiki/${name}`),
  update: (name: string, content: string) => put(`/wiki/${name}`, { content }),
  clusters: () => get<Record<string, string[]>>('/wiki/clusters'),
},
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/client.ts
git commit -m "feat: add api.wiki.clusters() to frontend client"
```

---

## Task 7: Update LeftPanel to render clustered virtual folders

Replace the flat wiki page list with collapsible cluster folders. Falls back to flat list when no clusters available yet (empty map from localStorage or fresh load).

**Files:**
- Modify: `frontend/src/components/LeftPanel.tsx`

- [ ] **Step 1: Replace LeftPanel wiki section**

Replace the full content of `LeftPanel.tsx` with:

```tsx
import { useState, useEffect } from 'react'
import { api } from '../api/client'
import type { Selection } from '../App'
import type { SourceFile } from '../types'
import SourceTree from './SourceTree'

interface Props {
  refreshKey: number
  sources: SourceFile[]
  selectedTags: string[]
  selection: Selection
  onSelect: (s: Selection) => void
  onFileDrop: (file: File) => void
}

const CLUSTERS_KEY = 'karp-wiki-clusters'
const CLUSTERS_COLLAPSED_KEY = 'karp-wiki-clusters-collapsed'

function loadClusters(): Record<string, string[]> {
  try { const s = localStorage.getItem(CLUSTERS_KEY); if (s) return JSON.parse(s) } catch {}
  return {}
}

function loadCollapsed(): Record<string, boolean> {
  try { const s = localStorage.getItem(CLUSTERS_COLLAPSED_KEY); if (s) return JSON.parse(s) } catch {}
  return {}
}

export default function LeftPanel({ refreshKey, sources, selectedTags, selection, onSelect, onFileDrop }: Props) {
  const [wikiPages, setWikiPages] = useState<string[]>([])
  const [wikiClusters, setWikiClusters] = useState<Record<string, string[]>>(loadClusters)
  const [clusterCollapsed, setClusterCollapsed] = useState<Record<string, boolean>>(loadCollapsed)
  const [dragging, setDragging] = useState(false)
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null)
  const [addFolderKey, setAddFolderKey] = useState(0)
  const [wikiOpen, setWikiOpen] = useState(true)
  const [sourcesOpen, setSourcesOpen] = useState(true)

  useEffect(() => {
    api.wiki.list().then(setWikiPages).catch(() => {})
    api.wiki.clusters().then(clusters => {
      localStorage.setItem(CLUSTERS_KEY, JSON.stringify(clusters))
      setWikiClusters(clusters)
    }).catch(() => {})
  }, [refreshKey])

  const toggleCluster = (name: string) => {
    setClusterCollapsed(prev => {
      const next = { ...prev, [name]: !prev[name] }
      localStorage.setItem(CLUSTERS_COLLAPSED_KEY, JSON.stringify(next))
      return next
    })
  }

  const filteredSources = selectedTags.length === 0
    ? sources
    : sources.filter(s => selectedTags.some(t => (s.tags ?? []).includes(t)))

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) onFileDrop(file)
  }

  const panelStyle: React.CSSProperties = {
    width: 220, borderRight: '1px solid #ddd', overflow: 'hidden',
    display: 'flex', flexDirection: 'column', fontSize: 13, background: '#fafafa',
    height: '100%'
  }
  const sectionStyle: React.CSSProperties = {
    padding: '10px 12px 4px', fontWeight: 700, color: '#555',
    fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.05em',
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    cursor: 'pointer', userSelect: 'none'
  }
  const itemStyle = (active: boolean, indent = 0): React.CSSProperties => ({
    padding: `4px 16px 4px ${16 + indent * 14}px`, cursor: 'pointer',
    background: active ? '#e8f0fe' : 'transparent',
    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
    color: active ? '#1a73e8' : '#333'
  })
  const clusterHeaderStyle: React.CSSProperties = {
    padding: '4px 8px', cursor: 'pointer', userSelect: 'none',
    display: 'flex', alignItems: 'center', gap: 4, fontSize: 12,
    color: '#555', fontWeight: 600,
  }

  const hasClusters = Object.keys(wikiClusters).length > 0

  return (
    <div style={panelStyle}>
      <div style={{ display: 'flex', flexDirection: 'column', ...(wikiOpen ? { flex: 1, minHeight: 0 } : { flexShrink: 0 }) }}>
        <div onClick={() => setWikiOpen(o => !o)} style={sectionStyle}>
          <span>Wiki Pages</span>
          <span style={{ fontSize: 10 }}>{wikiOpen ? '▾' : '▸'}</span>
        </div>
        {wikiOpen && (
          <div style={{ overflow: 'auto', flex: 1, minHeight: 0 }}>
            {!hasClusters && wikiPages.length === 0 && (
              <div style={{ padding: '4px 16px', color: '#999', fontSize: 12 }}>No pages yet</div>
            )}
            {!hasClusters && wikiPages.map(name => (
              <div key={name}
                style={itemStyle(selection?.type === 'wiki' && selection.name === name)}
                onClick={() => onSelect({ type: 'wiki', name })}>
                📄 {name}
              </div>
            ))}
            {hasClusters && Object.entries(wikiClusters).map(([clusterName, pages]) => (
              <div key={clusterName}>
                <div onClick={() => toggleCluster(clusterName)} style={clusterHeaderStyle}>
                  <span style={{ fontSize: 9, color: '#999', minWidth: 10 }}>
                    {clusterCollapsed[clusterName] ? '▶' : '▼'}
                  </span>
                  <span>📁 {clusterName}</span>
                </div>
                {!clusterCollapsed[clusterName] && pages.map(name => (
                  <div key={name}
                    style={itemStyle(selection?.type === 'wiki' && selection.name === name, 1)}
                    onClick={() => onSelect({ type: 'wiki', name })}>
                    📄 {name}
                  </div>
                ))}
              </div>
            ))}
          </div>
        )}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', ...(sourcesOpen ? { flex: 1, minHeight: 0 } : { flexShrink: 0 }) }}>
        <div onClick={() => setSourcesOpen(o => !o)} style={sectionStyle}>
          <span>Sources</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span
              title="Add folder"
              onClick={e => { e.stopPropagation(); setAddFolderKey(k => k + 1) }}
              style={{ cursor: 'pointer', fontSize: 14, color: '#888', lineHeight: 1, paddingRight: 2 }}
            >＋</span>
            <span style={{ fontSize: 10 }}>{sourcesOpen ? '▾' : '▸'}</span>
          </span>
        </div>
        {sourcesOpen && (
          <div style={{ overflow: 'auto', flex: 1, minHeight: 0 }}>
            <SourceTree
              serverSources={filteredSources.map(s => s.name)}
              refreshKey={refreshKey}
              selection={selection}
              onSelect={onSelect}
              selectedFolderId={selectedFolderId}
              onSelectFolder={setSelectedFolderId}
              addRootFolderKey={addFolderKey}
            />
          </div>
        )}
      </div>

      <div
        onDragOver={e => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        style={{
          marginTop: 'auto', padding: 16, textAlign: 'center', fontSize: 12, color: '#888',
          borderTop: '1px dashed #ccc',
          background: dragging ? '#f0f4ff' : 'transparent',
          cursor: 'pointer', transition: 'background 0.15s'
        }}
        onClick={() => {
          const input = document.createElement('input')
          input.type = 'file'
          input.onchange = (e) => {
            const file = (e.target as HTMLInputElement).files?.[0]
            if (file) onFileDrop(file)
          }
          input.click()
        }}>
        {dragging ? '📂 Drop to upload' : '+ Drop files here'}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Type-check frontend**

```bash
cd frontend && npx tsc --noEmit 2>&1 | tail -10
```

Expected: no errors.

- [ ] **Step 3: Start dev server and verify manually**

```bash
cd frontend && npm run dev
```

Open the app. Wiki Pages section should show folders if clusters exist in localStorage (or flat list if empty). Ingest a document and wait ~5-10s, then reload — pages should appear in cluster folders. Folders collapse/expand. Collapse state survives page reload.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/LeftPanel.tsx
git commit -m "feat: render wiki pages as semantic cluster folders in left panel"
```

---

## Task 8: Final integration verification

- [ ] **Step 1: Run full backend test suite**

```bash
cd backend && ./gradlew test 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`, 0 failures.

- [ ] **Step 2: Start backend and frontend, ingest a document, verify clusters appear**

1. Start backend: `cd backend && ./gradlew bootRun`
2. Start frontend: `cd frontend && npm run dev`
3. Drop a document onto the upload zone
4. Wait for ingest to complete (status polling)
5. Wait ~5-10s for background clustering to finish
6. Reload the page — wiki pages should appear grouped in named cluster folders
7. Collapse a folder, reload — collapsed state persists

- [ ] **Step 3: Final commit if any fixes needed**

```bash
git add -p
git commit -m "fix: <description of any fixes found during integration>"
```
