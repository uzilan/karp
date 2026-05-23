package karp.readers

import io.swagger.v3.parser.OpenAPIV3Parser
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
@Order(1)
class OpenApiReader : BaseReader {
    override val extensions = listOf(".json", ".yaml", ".yml")

    private val openApiFilenames =
        setOf(
            "openapi",
            "swagger",
            "api-spec",
            "api_spec",
        )

    override fun read(path: Path): ReadResult {
        val name =
            path.fileName
                .toString()
                .substringBeforeLast('.')
                .lowercase()
        if (name !in openApiFilenames) {
            throw UnsupportedOperationException("Not an OpenAPI file: $name")
        }

        val result =
            OpenAPIV3Parser().read(path.toString())
                ?: throw IllegalArgumentException("Failed to parse OpenAPI spec: $path")

        val sb = StringBuilder()
        sb.appendLine("# ${result.info?.title ?: "API"} — ${result.info?.version ?: ""}")
        sb.appendLine(result.info?.description ?: "")
        sb.appendLine()

        result.paths?.forEach { (pathStr, item) ->
            item.readOperationsMap().forEach { (method, op) ->
                sb.appendLine("## $method $pathStr")
                sb.appendLine(op.summary ?: "")
                op.parameters?.forEach { p ->
                    sb.appendLine("- param: ${p.name} (${p.`in`}) required=${p.required}")
                }
                sb.appendLine()
            }
        }

        val endpointCount = result.paths?.values?.sumOf { it.readOperationsMap().size } ?: 0

        return ReadResult(
            text = sb.toString(),
            metadata =
                mapOf(
                    "fileName" to path.fileName.toString(),
                    "title" to (result.info?.title ?: ""),
                    "endpointCount" to endpointCount,
                ),
            preview = "OpenAPI spec: ${result.info?.title}, $endpointCount endpoints",
        )
    }
}
