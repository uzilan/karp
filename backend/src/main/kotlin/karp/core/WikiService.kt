package karp.core

import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*

@Service
class WikiService(
    private val wikiDir: Path,
) {
    fun listPages(): List<String> =
        wikiDir
            .toFile()
            .listFiles { f -> f.extension == "md" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

    fun readPage(name: String): String? {
        val file = wikiDir.resolve("$name.md")
        return if (file.exists()) file.readText() else null
    }

    fun writePage(
        name: String,
        content: String,
    ) {
        wikiDir.resolve("$name.md").writeText(content)
    }

    fun deletePage(name: String) {
        wikiDir.resolve("$name.md").deleteIfExists()
    }

    fun appendToLog(entry: String) {
        val log = wikiDir.resolve("log.md")
        val timestamp = Instant.now().toString()
        val line = "[$timestamp] $entry\n"
        if (log.exists()) {
            log.appendText(line)
        } else {
            log.writeText(line)
        }
    }

    fun allPagesContent(): Map<String, String> = listPages().associateWith { readPage(it) ?: "" }
}
