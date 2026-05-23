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

        val svc = IngestService(sourcesDir, registry, wiki, llm, embedding)
        svc.ingest(file)

        val status = svc.getStatus("test.json")
        assertEquals(IngestStatus.PENDING, status)
    }
}
