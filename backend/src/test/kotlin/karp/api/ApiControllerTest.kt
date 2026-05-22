package karp.api

import karp.core.*
import karp.readers.ReaderRegistry
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.nio.file.Path

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
}
