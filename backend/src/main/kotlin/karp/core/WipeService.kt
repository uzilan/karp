package karp.core

import io.qdrant.client.QdrantClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class WipeService(
    private val wikiDir: Path,
    private val sourcesDir: Path,
    private val qdrant: QdrantClient,
    private val embedding: EmbeddingService
) {
    private val log = LoggerFactory.getLogger(WipeService::class.java)

    fun wipeAll() {
        wikiDir.toFile().listFiles()?.forEach { it.deleteRecursively() }
        sourcesDir.toFile().listFiles { f -> f.isFile }?.forEach { it.delete() }
        sourcesDir.resolve("errors").toFile().also {
            if (it.exists()) it.listFiles()?.forEach { f -> f.delete() }
        }
        try {
            embedding.resetCollection()
        } catch (e: Exception) {
            log.warn("Could not reset Qdrant collection: ${e.message}")
        }
        log.info("All data wiped")
    }
}
