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
        var centroids = points.shuffled().take(k).map { it.toMutableList() }.toMutableList()
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
