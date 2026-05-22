package karp.api

import karp.core.*
import karp.readers.ReaderRegistry
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.nio.file.Path

@WebMvcTest(ApiController::class)
class ApiControllerTest {

    @Autowired lateinit var mvc: MockMvc
    @MockBean lateinit var wiki: WikiService
    @MockBean lateinit var ingest: IngestService
    @MockBean lateinit var query: QueryService
    @MockBean lateinit var lint: LintService
    @MockBean lateinit var registry: ReaderRegistry
    @MockBean(name = "sourcesDir") lateinit var sourcesDir: Path
    @MockBean(name = "wikiDir") lateinit var wikiDir: Path

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
