package karp.readers

import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class MarkdownReader : BaseReader {

    override val extensions = listOf(".md", ".txt", ".rst")

    override fun read(path: Path): ReadResult {
        val text = path.toFile().readText()
        return ReadResult(
            text = text,
            metadata = mapOf("fileName" to path.fileName.toString()),
            preview = text.lines().take(5).joinToString("\n")
        )
    }
}
