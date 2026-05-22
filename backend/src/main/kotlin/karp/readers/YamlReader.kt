package karp.readers

import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
@Order(3)
class YamlReader : BaseReader {

    private val yamlMapper = YAMLMapper.builder().build()
    private val jsonMapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build()

    override val extensions = listOf(".yaml", ".yml")

    override fun read(path: Path): ReadResult {
        val node = yamlMapper.readTree(path.toFile())
        val asJson = jsonMapper.writeValueAsString(node)
        val topLevelKeys = if (node.isObject) node.propertyNames().toList() else emptyList()

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
