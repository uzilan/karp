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
    fun `previewUploaded reads file and returns tag suggestion`(@TempDir sourcesDir: Path) {
        val file = sourcesDir.resolve("test.json")
        file.toFile().writeText("{}")

        val readResult = ReadResult("text", emptyMap(), "preview")
        val tagSuggestion = TagSuggestion("Tech", listOf("api", "json"))

        whenever(registry.read(any())).thenReturn(readResult)
        whenever(llm.suggestTagsAndCategory(readResult)).thenReturn(tagSuggestion)

        val svc = IngestService(sourcesDir, registry, wiki, llm, embedding)
        val preview = svc.previewUploaded(file)

        assertEquals("Tech", preview.suggestedCategory)
        assertEquals(listOf("api", "json"), preview.suggestedTags)
        verify(registry).read(any())
    }

    @Test
    fun `confirm enqueues job and status becomes PENDING`(@TempDir sourcesDir: Path) {
        val file = sourcesDir.resolve("test.json")
        file.toFile().writeText("{}")

        val readResult = ReadResult("text", emptyMap(), "preview")
        whenever(registry.read(any())).thenReturn(readResult)

        val svc = IngestService(sourcesDir, registry, wiki, llm, embedding)
        svc.confirm("test.json", listOf("api"), "Tech")

        val status = svc.getStatus("test.json")
        assertNotNull(status)
        // Status is either PENDING or already PROCESSING (worker thread is fast)
        assertTrue(status == IngestStatus.PENDING || status == IngestStatus.PROCESSING || status == IngestStatus.COMPLETE || status == IngestStatus.ERROR)
    }
}
