package karp.readers

import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class CodeReader : BaseReader {

    override val extensions = listOf(
        ".kt", ".java", ".py", ".ts", ".tsx", ".js", ".jsx",
        ".go", ".rs", ".cpp", ".c", ".cs", ".rb", ".sh", ".sql"
    )

    override fun read(path: Path): ReadResult {
        val text = path.toFile().readText()
        val lang = path.fileName.toString().substringAfterLast('.')
        return ReadResult(
            text = "Language: $lang\n\n$text",
            metadata = mapOf("fileName" to path.fileName.toString(), "language" to lang),
            preview = "$lang file, ${text.lines().size} lines"
        )
    }
}
