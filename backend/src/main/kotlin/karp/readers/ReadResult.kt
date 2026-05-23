package karp.readers

data class ReadResult(
    val text: String,
    val metadata: Map<String, Any>,
    val preview: String,
    var suggestedTags: List<String> = emptyList(),
    var suggestedCategory: String = "",
)
