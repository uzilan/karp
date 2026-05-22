package karp.readers

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class PdfReader : BaseReader {

    override val extensions = listOf(".pdf")

    override fun read(path: Path): ReadResult {
        val text = Loader.loadPDF(path.toFile()).use { doc -> PDFTextStripper().getText(doc) }
        val pageCount = Loader.loadPDF(path.toFile()).use { it.numberOfPages }
        return ReadResult(
            text = text,
            metadata = mapOf("fileName" to path.fileName.toString(), "pages" to pageCount),
            preview = "PDF, $pageCount page(s). First 200 chars: ${text.take(200)}"
        )
    }
}
