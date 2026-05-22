package karp.readers

import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
@Order(2)
class JsonReader : BaseReader {

    private val mapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build()

    override val extensions = listOf(".json")

    override fun read(path: Path): ReadResult {
        val raw = path.toFile().readText()
        val node = mapper.readTree(raw)
        val pretty = mapper.writeValueAsString(node)
        val topLevelKeys = if (node.isObject) node.propertyNames().toList() else emptyList()

        return ReadResult(
            text = pretty,
            metadata = mapOf(
                "fileName" to path.fileName.toString(),
                "topLevelKeys" to topLevelKeys
            ),
            preview = "JSON file. Top-level keys: ${topLevelKeys.joinToString(", ").take(200)}"
        )
    }
}
