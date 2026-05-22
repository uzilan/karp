package karp.readers

import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class ReaderRegistry(private val readers: List<BaseReader>) {

    fun findReader(path: Path): BaseReader? {
        val ext = ".${path.fileName.toString().substringAfterLast('.').lowercase()}"
        return readers.firstOrNull { ext in it.extensions.map { e -> e.lowercase() } }
    }

    fun read(path: Path): ReadResult {
        val ext = ".${path.fileName.toString().substringAfterLast('.').lowercase()}"
        val candidates = readers.filter { ext in it.extensions.map { e -> e.lowercase() } }
        for (reader in candidates) {
            try {
                return reader.read(path)
            } catch (_: UnsupportedOperationException) {
                continue
            }
        }
        throw IllegalArgumentException("No reader found for: ${path.fileName}")
    }
}
