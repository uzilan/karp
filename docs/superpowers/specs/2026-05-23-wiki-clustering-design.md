# Wiki Clustering — Virtual Folders Design

**Date:** 2026-05-23  
**Status:** Approved

## Summary

Semantic clustering of wiki pages into AI-generated virtual folders displayed in the left panel. Clustering runs async in the background after each page write; results are cached in memory on the backend and persisted in localStorage on the frontend.

## Architecture

```
page write → WikiService.writePage() / deletePage()
                 └→ ClusterService.triggerAsync()   (fire-and-forget)
                         └→ EmbeddingService.scrollAll()
                         └→ k-means on vectors
                         └→ LlmService.nameCluster(pageNames) per cluster
                         └→ AtomicReference<Map<String,List<String>>> cache update

GET /wiki/clusters → return cache instantly (empty map if not yet computed)

Frontend:
  on load  → read localStorage → render → fetch /wiki/clusters async → merge → re-render
  on write → refreshKey++ → 1s delay → re-fetch clusters (silent, no spinner)
```

## Backend Components

### ClusterService (new)

- `triggerAsync()` — fires `CompletableFuture.runAsync {}`, skips if job already running
- Calls `EmbeddingService.scrollAll()` to get all page vectors
- Runs k-means with K = `max(2, sqrt(n/2).roundToInt())`
- Calls `LlmService.nameCluster(pageNames)` per cluster for a short label
- Stores result in `AtomicReference<Map<String, List<String>>>`
- `getClusters()` — returns current cached value (never null, minimum empty map)

### EmbeddingService — new method

```kotlin
fun scrollAll(): List<Pair<String, List<Float>>>
```

Uses Qdrant scroll API to retrieve all points with name payload and vectors.

### LlmService — new method

```kotlin
fun nameCluster(pageNames: List<String>): String
```

Calls Claude with page names, returns a short label (e.g. "Authentication", "API Design").  
Fallback: `"Cluster ${i+1}"` if LLM call fails.

### ApiController — new endpoint

```
GET /wiki/clusters → Map<String, List<String>>   (clusterName → pageNames)
```

Returns cached result instantly. Never throws — returns empty map on error.

### Trigger points

`ClusterService.triggerAsync()` called from:
- `WikiService.writePage()`
- `WikiService.deletePage()`

## Frontend

### State

`LeftPanel` adds:
```ts
const [wikiClusters, setWikiClusters] = useState<Record<string, string[]>>(
  () => JSON.parse(localStorage.getItem('karp-wiki-clusters') ?? '{}')
)
const [clusterCollapsed, setClusterCollapsed] = useState<Record<string, boolean>>(
  () => JSON.parse(localStorage.getItem('karp-wiki-clusters-collapsed') ?? '{}')
)
```

### Fetch logic

```ts
useEffect(() => {
  api.wiki.clusters().then(clusters => {
    localStorage.setItem('karp-wiki-clusters', JSON.stringify(clusters))
    setWikiClusters(clusters)
  }).catch(() => {})
}, [refreshKey])
```

No spinner. Stale localStorage renders immediately; fresh data silently replaces it.

### Rendering

Flat `wikiPages` list replaced by clustered folders rendered inline in `LeftPanel`.  
Each cluster = collapsible section using existing `▾/▸` pattern.  
Pages not present in any cluster → `"Other"` folder.  
Collapse state persisted in `karp-wiki-clusters-collapsed` localStorage key.

### API client

New method: `api.wiki.clusters() → Promise<Record<string, string[]>>`

## Edge Cases

| Condition | Behavior |
|-----------|----------|
| < 3 pages | Skip clustering, return `{ "All Pages": [...] }` |
| Qdrant unavailable | Log warning, cache stays stale |
| LLM naming fails | Fall back to `"Cluster ${i+1}"` |
| Job already running | Skip new trigger (debounce) |
| fetch fails on frontend | Use localStorage as-is, no error shown |
| Empty cache on GET | Return `{}` |

## Testing

- `ClusterServiceTest` — k-means grouping with mock vectors
- `EmbeddingServiceTest` — `scrollAll()` with mock Qdrant client
- `ApiControllerTest` — `GET /wiki/clusters` endpoint
- Frontend: no new tests (consistent with existing project pattern)
