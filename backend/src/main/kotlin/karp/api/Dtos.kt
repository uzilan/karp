package karp.api

data class QueryRequest(
    val question: String,
    val tags: List<String> = emptyList(),
    val category: String? = null
)

data class FileBackRequest(val pageName: String, val content: String)

data class IngestConfirmRequest(
    val fileName: String,
    val tags: List<String>,
    val category: String
)

data class SourceFileDto(
    val name: String,
    val extension: String,
    val category: String,
    val tags: List<String>
)
