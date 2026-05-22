package karp.core

import karp.readers.ReadResult
import karp.readers.ReaderRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

enum class IngestStatus { PENDING, PROCESSING, COMPLETE, ERROR }

data class IngestPreview(
    val fileName: String,
    val suggestedCategory: String,
    val suggestedTags: List<String>,
    val preview: String
)

private data class IngestJob(
    val fileName: String,
    val sourcePath: Path,
    val tags: List<String>,
    val category: String,
    val readResult: ReadResult
)

@Service
class IngestService(
    private val sourcesDir: Path,
    private val registry: ReaderRegistry,
    private val wiki: WikiService,
    private val llm: LlmService,
    private val embedding: EmbeddingService
) {
    private val log = LoggerFactory.getLogger(IngestService::class.java)
    private val statusMap = ConcurrentHashMap<String, IngestStatus>()
    private val queue = LinkedBlockingQueue<IngestJob>()

    init {
        Thread(::processQueue, "ingest-worker").apply { isDaemon = true }.start()
    }

    fun preview(file: Path): IngestPreview {
        val dest = sourcesDir.resolve(file.fileName)
        Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING)
        return previewUploaded(dest)
    }

    fun previewUploaded(dest: Path): IngestPreview {
        val result = registry.read(dest)
        val suggestion = llm.suggestTagsAndCategory(result)
        result.suggestedCategory = suggestion.category
        result.suggestedTags = suggestion.tags
        return IngestPreview(dest.fileName.toString(), suggestion.category, suggestion.tags, result.preview)
    }

    fun confirm(fileName: String, tags: List<String>, category: String) {
        val path = sourcesDir.resolve(fileName)
        val result = registry.read(path)
        statusMap[fileName] = IngestStatus.PENDING
        queue.put(IngestJob(fileName, path, tags, category, result))
    }

    fun getStatus(fileName: String): IngestStatus? = statusMap[fileName]

    private fun processQueue() {
        while (true) {
            val job = queue.take()
            statusMap[job.fileName] = IngestStatus.PROCESSING
            try {
                val updates = llm.updateWikiPages(job.readResult, job.tags, job.category)
                updates.forEach { update ->
                    wiki.writePage(update.name, update.content)
                    embedding.upsertPage(update.name, update.content, job.tags, job.category)
                }
                wiki.appendToLog("INGEST ${job.fileName} category=${job.category} tags=${job.tags}")
                statusMap[job.fileName] = IngestStatus.COMPLETE
                log.info("Ingested: ${job.fileName}")
            } catch (e: Exception) {
                log.error("Ingest failed: ${job.fileName}", e)
                val errorDir = sourcesDir.resolve("errors")
                Files.createDirectories(errorDir)
                try {
                    Files.move(job.sourcePath, errorDir.resolve(job.fileName), StandardCopyOption.REPLACE_EXISTING)
                } catch (moveEx: Exception) {
                    log.warn("Could not move failed file to errors dir: ${moveEx.message}")
                }
                statusMap[job.fileName] = IngestStatus.ERROR
            }
        }
    }
}
