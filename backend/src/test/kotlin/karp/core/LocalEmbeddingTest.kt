package karp.core

import io.qdrant.client.QdrantClient
import karp.config.KarpProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.math.sqrt

class LocalEmbeddingTest {

    private fun mockProps(): KarpProperties {
        val p = mock<KarpProperties>()
        whenever(p.topK).thenReturn(5)
        return p
    }

    @Test
    fun `embed returns vectors of correct dimension`() {
        val svc = EmbeddingService(mock<QdrantClient>(), mockProps())
        val result = svc.embed(listOf("Hello world", "Test sentence"))
        assertEquals(2, result.size)
        assertEquals(384, result[0].size)
    }

    @Test
    fun `similar texts score higher than unrelated texts`() {
        val svc = EmbeddingService(mock<QdrantClient>(), mockProps())
        val vecs = svc.embed(listOf("cat sat on mat", "kitten resting on rug", "stock market crash 2024"))
        val sim01 = cosine(vecs[0], vecs[1])
        val sim02 = cosine(vecs[0], vecs[2])
        assertTrue(sim01 > sim02, "cat/kitten similarity ($sim01) should exceed cat/stocks ($sim02)")
    }

    private fun cosine(a: List<Float>, b: List<Float>): Double {
        val dot = a.zip(b).sumOf { (x, y) -> x.toDouble() * y }
        val na = sqrt(a.sumOf { it.toDouble() * it })
        val nb = sqrt(b.sumOf { it.toDouble() * it })
        return dot / (na * nb)
    }
}
