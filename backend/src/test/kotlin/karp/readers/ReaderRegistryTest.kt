package karp.readers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path

class ReaderRegistryTest {

    private val registry = ReaderRegistry(listOf(
        object : BaseReader {
            override val extensions = listOf(".foo")
            override fun read(path: Path) = ReadResult("text", emptyMap(), "preview")
        }
    ))

    @Test
    fun `finds reader for known extension`() {
        val reader = registry.findReader(Path.of("file.foo"))
        assertNotNull(reader)
    }

    @Test
    fun `returns null for unknown extension`() {
        val reader = registry.findReader(Path.of("file.unknown"))
        assertNull(reader)
    }

    @Test
    fun `extension matching is case-insensitive`() {
        val reader = registry.findReader(Path.of("file.FOO"))
        assertNotNull(reader)
    }

    @Test
    fun `read throws for unknown extension`() {
        assertThrows(IllegalArgumentException::class.java) {
            registry.read(Path.of("file.unknown"))
        }
    }
}
