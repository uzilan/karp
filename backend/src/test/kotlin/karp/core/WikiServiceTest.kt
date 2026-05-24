package karp.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.nio.file.Path

class WikiServiceTest {

    private fun svc(dir: Path) = WikiService(dir, mock())

    @Test
    fun `write and read wiki page round-trips content`(@TempDir dir: Path) {
        val svc = svc(dir)
        svc.writePage("finance", "# Finance\nSome content.")
        val content = svc.readPage("finance")
        assertEquals("# Finance\nSome content.", content)
    }

    @Test
    fun `list pages returns written pages`(@TempDir dir: Path) {
        val svc = svc(dir)
        svc.writePage("page-a", "content a")
        svc.writePage("page-b", "content b")
        val pages = svc.listPages()
        assertTrue(pages.contains("page-a"))
        assertTrue(pages.contains("page-b"))
    }

    @Test
    fun `readPage returns null for missing page`(@TempDir dir: Path) {
        assertNull(svc(dir).readPage("nonexistent"))
    }

    @Test
    fun `appendToLog adds line`(@TempDir dir: Path) {
        val svc = svc(dir)
        svc.appendToLog("First entry")
        svc.appendToLog("Second entry")
        val log = svc.readPage("log")!!
        assertTrue(log.contains("First entry"))
        assertTrue(log.contains("Second entry"))
    }

    @Test
    fun `allPagesContent returns all pages`(@TempDir dir: Path) {
        val svc = svc(dir)
        svc.writePage("a", "content a")
        svc.writePage("b", "content b")
        val all = svc.allPagesContent()
        assertEquals(2, all.size)
        assertEquals("content a", all["a"])
    }

    @Test
    fun `writePageSource and readPageSource round-trip`(@TempDir dir: Path) {
        val svc = WikiService(dir, mock())
        svc.writePageSource("finance", "report.pdf")
        assertEquals("report.pdf", svc.readPageSource("finance"))
    }

    @Test
    fun `readPageSource returns null when no meta file`(@TempDir dir: Path) {
        val svc = WikiService(dir, mock())
        assertNull(svc.readPageSource("nonexistent"))
    }
}
