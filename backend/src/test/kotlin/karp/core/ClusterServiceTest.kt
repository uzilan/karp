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
