package karp.core

import org.springframework.stereotype.Service

@Service
class LintService(private val llm: LlmService) {
    fun lint(): List<LintIssue> = llm.lintWiki()
}
