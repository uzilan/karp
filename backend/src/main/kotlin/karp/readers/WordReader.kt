package karp.readers

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class WordReader : BaseReader {

    override val extensions = listOf(".docx")

    override fun read(path: Path): ReadResult {
        val sb = StringBuilder()
        XWPFDocument(path.toFile().inputStream()).use { doc ->
            doc.paragraphs.forEach { para ->
                if (para.text.isNotBlank()) sb.appendLine(para.text)
            }
        }
        val text = sb.toString()
        val wordCount = text.split("\\s+".toRegex()).size
        return ReadResult(
            text = text,
            metadata = mapOf("fileName" to path.fileName.toString(), "wordCount" to wordCount),
            preview = "Word document, ~$wordCount words. First 200 chars: ${text.take(200)}"
        )
    }
}
