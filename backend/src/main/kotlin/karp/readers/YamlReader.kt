package karp.readers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
@Order(2)
class YamlReader : BaseReader {

    private val yamlMapper = ObjectMapper(YAMLFactory())
    private val jsonMapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    override val extensions = listOf(".yaml", ".yml")

    override fun read(path: Path): ReadResult {
        val node = yamlMapper.readTree(path.toFile())
        val asJson = jsonMapper.writeValueAsString(node)
        val topLevelKeys = if (node.isObject) node.fieldNames().asSequence().toList() else emptyList()

        return ReadResult(
            text = asJson,
            metadata = mapOf(
                "fileName" to path.fileName.toString(),
                "topLevelKeys" to topLevelKeys
            ),
            preview = "YAML file. Top-level keys: ${topLevelKeys.joinToString(", ").take(200)}"
        )
    }
}
