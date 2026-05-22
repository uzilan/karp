package karp.core

import io.qdrant.client.QdrantClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.nio.file.Files
import java.nio.file.Path

class WipeServiceTest {

    @Test
    fun `wipeAll deletes wiki and source files`(@TempDir wikiDir: Path, @TempDir sourcesDir: Path) {
        Files.writeString(wikiDir.resolve("page.md"), "content")
        Files.writeString(sourcesDir.resolve("file.json"), "{}")
        Files.createDirectories(sourcesDir.resolve("errors"))
        val qdrant = mock<QdrantClient>()
        val embedding = mock<EmbeddingService>()

        val svc = WipeService(wikiDir, sourcesDir, qdrant, embedding)
        svc.wipeAll()

        assertEquals(0, wikiDir.toFile().listFiles()?.size ?: 0)
        assertEquals(0, sourcesDir.toFile().listFiles { f -> f.isFile }?.size ?: 0)
    }

    @Test
    fun `wipeAll resets Qdrant collection`(@TempDir wikiDir: Path, @TempDir sourcesDir: Path) {
        val qdrant = mock<QdrantClient>()
        val embedding = mock<EmbeddingService>()

        val svc = WipeService(wikiDir, sourcesDir, qdrant, embedding)
        svc.wipeAll()

        verify(embedding).resetCollection()
    }
}
