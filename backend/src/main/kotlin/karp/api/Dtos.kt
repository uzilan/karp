package karp.api

data class QueryRequest(
    val question: String,
    val tags: List<String> = emptyList(),
)

data class FileBackRequest(val pageName: String, val content: String)

data class SourceFileDto(
    val name: String,
    val extension: String,
    val tags: List<String>
)
