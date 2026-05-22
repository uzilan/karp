package karp.core

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import karp.config.KarpProperties
import karp.readers.ReadResult
import org.springframework.stereotype.Service
import java.nio.file.Path

data class TagSuggestion(val category: String, val tags: List<String>)
data class WikiPageUpdate(val name: String, val content: String)
data class QueryAnswer(val answer: String, val sourcedFrom: List<String>)
data class LintIssue(val type: String, val page: String, val description: String)

@Service
class LlmService(
    private val props: KarpProperties,
    private val wikiService: WikiService
) {
    private val client = AnthropicOkHttpClient.builder()
        .apiKey(props.anthropicApiKey)
        .build()

    private val mapper = ObjectMapper().registerKotlinModule()

    private val schema: String by lazy {
        Path.of("schema.md").let { if (it.toFile().exists()) it.toFile().readText() else "" }
    }

    private fun ask(systemPrompt: String, userMessage: String): String {
        val response = client.messages().create(
            MessageCreateParams.builder()
                .model(Model.of("claude-sonnet-4-6"))
                .maxTokens(8192)
                .system(systemPrompt)
                .addUserMessage(userMessage)
                .build()
        )
        return response.content()
            .first { it.isText() }
            .asText()
            .text()
    }

    fun suggestTagsAndCategory(result: ReadResult): TagSuggestion {
        val prompt = """
            Analyze this content and respond with JSON only, no markdown:
            {"category": "single category name", "tags": ["tag1", "tag2", "tag3"]}

            Content preview: ${result.preview}

            First 500 chars of content:
            ${result.text.take(500)}
        """.trimIndent()

        val json = ask("You are a knowledge base librarian. Respond with JSON only.", prompt)
        val cleaned = json.trim().removePrefix("```json").removeSuffix("```").trim()
        return mapper.readValue(cleaned)
    }

    fun updateWikiPages(result: ReadResult, tags: List<String>, category: String): List<WikiPageUpdate> {
        val existingPages = wikiService.allPagesContent()
        val existingJson = mapper.writeValueAsString(existingPages.mapValues { (_, v) -> v.take(500) })

        val prompt = """
            A new document has been ingested:
            - File: ${result.metadata["fileName"]}
            - Category: $category
            - Tags: ${tags.joinToString(", ")}
            - Preview: ${result.preview}

            Full content:
            ${result.text.take(4000)}

            Existing wiki pages (truncated):
            $existingJson

            $schema

            Return JSON array of wiki pages to create or update. Each page must have "name" (slug, no spaces) and "content" (full markdown).
            Update index.md to reference this source. Update or create 3-10 topic pages.
            Respond with JSON array only:
            [{"name": "page-name", "content": "# Title\n...markdown..."}]
        """.trimIndent()

        val json = ask(
            "You are a wiki maintainer. Build a structured, interlinked knowledge base. Respond with JSON array only.",
            prompt
        )
        val cleaned = json.trim().removePrefix("```json").removeSuffix("```").trim()
        return mapper.readValue(cleaned)
    }

    fun answerQuery(question: String, relevantPages: Map<String, String>): QueryAnswer {
        val pagesText = relevantPages.entries.joinToString("\n\n---\n\n") { (name, content) ->
            "### Wiki page: $name\n$content"
        }
        val prompt = """
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

        val prompt = """
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
}
