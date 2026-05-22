package karp.readers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class JsonReaderTest {

    private val reader = JsonReader()

    @Test
    fun `reads json and extracts top-level keys`(@TempDir dir: Path) {
        val file = dir.resolve("data.json")
        file.toFile().writeText("""{"name": "test", "value": 42, "active": true}""")

        val result = reader.read(file)

        assertTrue(result.text.contains("name"))
        assertTrue(result.text.contains("value"))
        assertTrue(result.metadata["topLevelKeys"].toString().contains("name"))
    }

    @Test
    fun `openapi reader declines non-openapi json file`(@TempDir dir: Path) {
        val file = dir.resolve("data.json")
        file.toFile().writeText("""{"name": "test"}""")
        val openApiReader = OpenApiReader()
        assertThrows(UnsupportedOperationException::class.java) {
            openApiReader.read(file)
        }
    }
}
