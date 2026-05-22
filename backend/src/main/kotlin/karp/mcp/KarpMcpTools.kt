package karp.mcp

import karp.core.IngestService
import karp.core.LintIssue
import karp.core.LintService
import karp.core.QueryAnswer
import karp.core.QueryService
import karp.core.WikiService
import karp.core.WipeService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class KarpMcpTools(
    private val wiki: WikiService,
    private val query: QueryService,
    private val ingest: IngestService,
    private val lint: LintService,
    private val sourcesDir: Path,
    private val wipeService: WipeService
) {
    @Tool(description = "List all wiki page names in the knowledge base")
    fun listWikiPages(): List<String> = wiki.listPages()

    @Tool(description = "Read the full markdown content of a wiki page by name")
    fun readWikiPage(name: String): String =
        wiki.readPage(name) ?: "Page not found: $name"

    @Tool(description = "List all source files with their names")
    fun listSources(): List<String> =
        sourcesDir.toFile().listFiles { f -> f.isFile }?.map { it.name } ?: emptyList()

    @Tool(description = "Semantic search across the wiki. Optional: filter by tags (comma-separated) or category")
    fun search(query: String, tags: String = "", category: String = ""): QueryAnswer {
        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val cat = category.ifBlank { null }
        return this.query.query(query, tagList, cat)
    }

    @Tool(description = "Run a wiki health check and return a list of issues found")
    fun lint(): List<LintIssue> = lint.lint()

    @Tool(description = "Wipe all data — deletes all wiki pages, source files, and clears the vector index. Pass confirm=\"yes\" to execute.")
    fun wipeAllData(confirm: String): String {
        if (confirm != "yes") return "Aborted. Pass confirm=\"yes\" to wipe all data."
        wipeService.wipeAll()
        return "All data wiped successfully."
    }

    @Tool(description = "Ingest a local file into the wiki by absolute path")
    fun ingestFile(absolutePath: String): String {
        val path = Path.of(absolutePath)
        return try {
            val preview = ingest.preview(path)
            ingest.confirm(preview.fileName, preview.suggestedTags, preview.suggestedCategory)
            "Queued for ingest: ${preview.fileName} (category=${preview.suggestedCategory}, tags=${preview.suggestedTags})"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

@Configuration
class KarpMcpToolsConfig {
    @Bean
    fun karpToolCallbackProvider(tools: KarpMcpTools): MethodToolCallbackProvider =
        MethodToolCallbackProvider.builder().toolObjects(tools).build()
}
