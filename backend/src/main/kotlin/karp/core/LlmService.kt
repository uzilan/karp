package karp.core

import karp.readers.ReadResult
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

data class IngestResult(
    val tags: List<String>,
    val pages: List<WikiPageUpdate>,
)

data class WikiPageUpdate(
    val name: String,
    val content: String,
)

data class QueryAnswer(
    val answer: String,
    val sourcedFrom: List<String>,
)

data class LintIssue(
    val type: String,
    val page: String,
    val description: String,
)

@Service
class LlmService(
    private val wikiService: WikiService,
) {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .build()

    private val schema: String by lazy {
        Path.of("schema.md").let { if (it.toFile().exists()) it.toFile().readText() else "" }
    }

    private val httpClient = HttpClient.newHttpClient()

    private fun ask(
        systemPrompt: String,
        userMessage: String,
    ): String {
        val proxyUrl = System.getenv("CLAUDE_PROXY_URL")
        return if (proxyUrl != null) askViaProxy(proxyUrl, systemPrompt, userMessage) else askViaCli(systemPrompt, userMessage)
    }

    private fun askViaProxy(proxyUrl: String, systemPrompt: String, userMessage: String): String {
        val body = mapper.writeValueAsString(mapOf("systemPrompt" to systemPrompt, "userMessage" to userMessage))
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$proxyUrl/ask"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw RuntimeException("Proxy error ${response.statusCode()}: ${response.body()}")
        val output = response.body().trim()
        if (output.isBlank()) throw RuntimeException("Empty response from proxy")
        return output
    }

    private fun askViaCli(systemPrompt: String, userMessage: String): String {
        val process =
            ProcessBuilder(
                "claude",
                "-p",
                userMessage,
                "--system-prompt",
                systemPrompt,
                "--dangerously-skip-permissions",
            ).redirectInput(ProcessBuilder.Redirect.from(java.io.File("/dev/null")))
                .start()
        val output = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0 || output.isBlank()) {
            throw RuntimeException("claude exited $exitCode. stderr: $stderr")
        }
        return output.trim()
    }

    fun ingestDocument(result: ReadResult): IngestResult {
        val existingPages = wikiService.allPagesContent()
        val existingJson = mapper.writeValueAsString(existingPages.mapValues { (_, v) -> v.take(500) })

        val prompt =
            """
            A new document has been ingested:
            - File: ${result.metadata["fileName"]}
            - Preview: ${result.preview}

            Full content:
            ${result.text.take(4000)}

            Existing wiki pages (truncated):
            $existingJson

            $schema

            Respond with JSON only, no markdown:
            {
              "tags": ["tag1", "tag2", "tag3"],
              "pages": [{"name": "page-slug", "content": "# Title\n...markdown..."}]
            }

            tags: 3-8 short lowercase keywords describing this document.
            pages: wiki pages to create or update. Update index.md to reference this source. Update or create 3-10 topic pages.
            """.trimIndent()

        val json = ask(
            "You are a wiki maintainer and knowledge base librarian. Respond with JSON only.",
            prompt,
        )
        val cleaned = json.trim().removePrefix("```json").removeSuffix("```").trim()
        return mapper.readValue(cleaned)
    }

    fun answerQuery(
        question: String,
        relevantPages: Map<String, String>,
    ): QueryAnswer {
        val pagesText =
            relevantPages.entries.joinToString("\n\n---\n\n") { (name, content) ->
                "### Wiki page: $name\n$content"
            }
        val prompt =
            """
            Answer this question using the wiki pages provided:

            Question: $question

            Wiki pages:
            $pagesText
            """.trimIndent()

        val answer = ask("You are a knowledgeable assistant with access to a personal wiki.", prompt)
        return QueryAnswer(answer = answer, sourcedFrom = relevantPages.keys.toList())
    }

    fun lintWiki(): List<LintIssue> {
        val pages = wikiService.allPagesContent()
        if (pages.isEmpty()) return emptyList()

        val pagesJson = mapper.writeValueAsString(pages.mapValues { (_, v) -> v.take(800) })

        val prompt =
            """
            Review these wiki pages for quality issues.
            Return JSON array of issues found. Each issue: {"type": "contradiction|stale|orphan|missing-ref", "page": "page-name", "description": "what's wrong"}
            If no issues, return empty array: []

            Wiki pages:
            $pagesJson
            """.trimIndent()

        val json = ask("You are a wiki quality reviewer. Respond with JSON array only.", prompt)
        val cleaned = json.trim().removePrefix("```json").removeSuffix("```").trim()
        return mapper.readValue(cleaned)
    }

    fun nameCluster(pageNames: List<String>): String {
        val prompt = "These wiki pages belong to the same topic cluster: ${pageNames.joinToString(", ")}. " +
            "Respond with a short 1-3 word label for this cluster (e.g. 'Authentication', 'API Design', 'Data Model'). " +
            "Respond with the label only, no explanation, no punctuation."
        return ask("You are a wiki organizer. Respond with a short cluster label only.", prompt)
            .trim().take(50)
    }
}
