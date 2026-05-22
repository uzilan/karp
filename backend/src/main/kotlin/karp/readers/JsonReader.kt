package karp.readers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
@Order(2)
class JsonReader : BaseReader {

    private val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    override val extensions = listOf(".json")

    override fun read(path: Path): ReadResult {
        val raw = path.toFile().readText()
        val node = mapper.readTree(raw)
        val pretty = mapper.writeValueAsString(node)
        val topLevelKeys = if (node.isObject) node.fieldNames().asSequence().toList() else emptyList()

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
