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

        // wait for worker to finish before @TempDir cleanup to avoid DirectoryNotEmptyException
        val deadline = System.currentTimeMillis() + 2000
        while (svc.getStatus("test.json").let { it == IngestStatus.PENDING || it == IngestStatus.PROCESSING }) {
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(10)
        }
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
