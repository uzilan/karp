package karp.core

import karp.readers.ReaderRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

enum class IngestStatus { PENDING, PROCESSING, COMPLETE, ERROR }

private data class IngestJob(
    val fileName: String,
    val sourcePath: Path,
    val readResult: karp.readers.ReadResult,
)

@Service
class IngestService(
    private val sourcesDir: Path,
    private val registry: ReaderRegistry,
    private val wiki: WikiService,
    private val llm: LlmService,
    private val embedding: EmbeddingService,
) {
    private val log = LoggerFactory.getLogger(IngestService::class.java)
    private val statusMap = ConcurrentHashMap<String, IngestStatus>()
    private val queue = LinkedBlockingQueue<IngestJob>()

    init {
        Thread(::processQueue, "ingest-worker").apply { isDaemon = true }.start()
    }

    fun ingest(dest: Path) {
        val fileName = dest.fileName.toString()
        val result = registry.read(dest)
        statusMap[fileName] = IngestStatus.PENDING
        queue.put(IngestJob(fileName, dest, result))
    }

    fun getStatus(fileName: String): IngestStatus? = statusMap[fileName]

    private fun processQueue() {
        while (true) {
            val job = queue.take()
            statusMap[job.fileName] = IngestStatus.PROCESSING
            try {
                val ingestResult = llm.ingestDocument(job.readResult)
                ingestResult.pages.forEach { update ->
                    wiki.writePage(update.name, update.content)
                    embedding.upsertPage(update.name, update.content, ingestResult.tags)
                }
                val metaDir = sourcesDir.resolve(".meta")
                Files.createDirectories(metaDir)
                val tagsJson = ingestResult.tags.joinToString(",", "[", "]") { "\"$it\"" }
                metaDir.resolve("${job.fileName}.json").toFile().writeText("""{"tags":$tagsJson}""")
                wiki.appendToLog("INGEST ${job.fileName} tags=${ingestResult.tags}")
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
