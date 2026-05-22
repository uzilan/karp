package karp.watcher

import karp.core.IngestService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.nio.file.*
import jakarta.annotation.PostConstruct

@Service
class FileWatcherService(
    private val sourcesDir: Path,
    private val ingestService: IngestService
) {
    private val log = LoggerFactory.getLogger(FileWatcherService::class.java)

    @PostConstruct
    @Async
    fun watch() {
        val watcher = FileSystems.getDefault().newWatchService()
        sourcesDir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE)
        log.info("Watching sources dir: $sourcesDir")

        while (true) {
            val key = try {
                watcher.take()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            key.pollEvents().forEach { event ->
                if (event.kind() != StandardWatchEventKinds.OVERFLOW) {
                    @Suppress("UNCHECKED_CAST")
                    val filename = (event as WatchEvent<Path>).context()
                    val fullPath = sourcesDir.resolve(filename)
                    if (fullPath.toFile().isFile) {
                        log.info("New file detected: $filename")
                        try {
                            ingestService.previewUploaded(fullPath)
                            log.info("Preview ready for watcher-detected file: $filename — confirm via UI")
                        } catch (e: Exception) {
                            log.warn("Could not preview $filename: ${e.message}")
                        }
                    }
                }
            }
            key.reset()
        }
    }
}
