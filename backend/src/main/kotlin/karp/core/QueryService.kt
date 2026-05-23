package karp.core

import karp.config.KarpProperties
import org.springframework.stereotype.Service

@Service
class QueryService(
    private val embedding: EmbeddingService,
    private val wiki: WikiService,
    private val llm: LlmService,
    private val props: KarpProperties
) {
    fun query(question: String, tags: List<String> = emptyList()): QueryAnswer {
        val results = embedding.search(question, props.topK, tags)
        val pages = results
            .mapNotNull { r -> wiki.readPage(r.pageName)?.let { r.pageName to it } }
            .toMap()
        return llm.answerQuery(question, pages)
    }

    fun fileBack(pageName: String, content: String) {
        wiki.writePage(pageName, content)
        embedding.upsertPage(pageName, content, emptyList())
        wiki.appendToLog("FILE_BACK page=$pageName")
    }
}
