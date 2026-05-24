package karp.api

import karp.core.*
import karp.readers.ReaderRegistry
import karp.core.WipeService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir

@WebMvcTest(ApiController::class)
class ApiControllerTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var wiki: WikiService
    @MockitoBean lateinit var ingest: IngestService
    @MockitoBean lateinit var query: QueryService
    @MockitoBean lateinit var lint: LintService
    @MockitoBean lateinit var registry: ReaderRegistry
    @MockitoBean(name = "sourcesDir") lateinit var sourcesDir: Path
    @MockitoBean(name = "wikiDir") lateinit var wikiDir: Path
    @MockitoBean lateinit var wipeService: WipeService
    @MockitoBean lateinit var clusterService: ClusterService

    @Test
    fun `GET api-wiki returns list`() {
        whenever(wiki.listPages()).thenReturn(listOf("finance", "index"))

        mvc.get("/api/wiki")
            .andExpect {
                status { isOk() }
                jsonPath("$[0]") { value("finance") }
            }
    }

    @Test
    fun `GET api-wiki-name returns 404 for missing page`() {
        whenever(wiki.readPage("missing")).thenReturn(null)

        mvc.get("/api/wiki/missing")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `GET api-wiki-clusters returns cluster map`() {
        whenever(clusterService.getClusters()).thenReturn(
            mapOf("Authentication" to listOf("auth-login", "auth-token"))
        )

        mvc.get("/api/wiki/clusters")
            .andExpect {
                status { isOk() }
                jsonPath("$.Authentication[0]") { value("auth-login") }
            }
    }

    @Test
    fun `GET sources-name-raw returns file bytes with content type`(@TempDir dir: java.nio.file.Path) {
        val file = dir.resolve("doc.txt")
        file.toFile().writeText("hello")
        whenever(sourcesDir.resolve("doc.txt")).thenReturn(file)

        mvc.get("/api/sources/doc.txt/raw")
            .andExpect {
                status { isOk() }
                content { string("hello") }
            }
    }

    @Test
    fun `GET sources-name-raw returns 404 for missing file`(@TempDir dir: java.nio.file.Path) {
        val missing = dir.resolve("missing.txt")
        whenever(sourcesDir.resolve("missing.txt")).thenReturn(missing)

        mvc.get("/api/sources/missing.txt/raw")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET sources-name-raw rejects path traversal`() {
        mvc.get("/api/sources/..dangerous/raw")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `GET api-wiki-name includes source when meta exists`() {
        whenever(wiki.readPage("finance")).thenReturn("# Finance")
        whenever(wiki.readPageSource("finance")).thenReturn("report.pdf")

        mvc.get("/api/wiki/finance")
            .andExpect {
                status { isOk() }
                jsonPath("$.source") { value("report.pdf") }
            }
    }

    @Test
    fun `GET api-wiki-name omits source when no meta`() {
        whenever(wiki.readPage("index")).thenReturn("# Index")
        whenever(wiki.readPageSource("index")).thenReturn(null)

        mvc.get("/api/wiki/index")
            .andExpect {
                status { isOk() }
                jsonPath("$.source") { doesNotExist() }
            }
    }
}
