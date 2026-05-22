package karp.api

import karp.core.*
import karp.readers.ReaderRegistry
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@RestController
@RequestMapping("/api")
class ApiController(
    private val wiki: WikiService,
    private val ingest: IngestService,
    private val query: QueryService,
    private val lint: LintService,
    private val registry: ReaderRegistry,
    private val sourcesDir: Path,
    private val wipeService: WipeService
) {
    // Wiki
    @GetMapping("/wiki")
    fun listWiki() = wiki.listPages()

    @GetMapping("/wiki/{name}")
    fun getWikiPage(@PathVariable name: String): ResponseEntity<Map<String, String>> {
        val content = wiki.readPage(name) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("name" to name, "content" to content))
    }

    @PutMapping("/wiki/{name}")
    fun updateWikiPage(@PathVariable name: String, @RequestBody body: Map<String, String>): ResponseEntity<Void> {
        val content = body["content"] ?: return ResponseEntity.badRequest().build()
        wiki.writePage(name, content)
        return ResponseEntity.ok().build()
    }

    // Sources
    @GetMapping("/sources")
    fun listSources(): List<SourceFileDto> =
        sourcesDir.toFile()
            .listFiles { f -> f.isFile && f.name != "errors" }
            ?.map { f ->
                SourceFileDto(
                    name = f.name,
                    extension = f.extension,
                    category = "",
                    tags = emptyList()
                )
            } ?: emptyList()

    @PostMapping("/sources/upload")
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("overwrite", defaultValue = "false") overwrite: Boolean
    ): ResponseEntity<Any> {
        val dest = sourcesDir.resolve(file.originalFilename ?: "upload")
        if (dest.toFile().exists() && !overwrite) {
            return ResponseEntity.status(409).body(mapOf("error" to "File already exists", "fileName" to dest.fileName.toString()))
        }
        Files.copy(file.inputStream, dest, StandardCopyOption.REPLACE_EXISTING)
        return try {
            val preview = ingest.previewUploaded(dest)
            ResponseEntity.ok(preview)
        } catch (e: IllegalArgumentException) {
            Files.deleteIfExists(dest)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/sources/confirm")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun confirm(@RequestBody req: IngestConfirmRequest) {
        ingest.confirm(req.fileName, req.tags, req.category)
    }

    @GetMapping("/sources/{name}/status")
    fun status(@PathVariable name: String): ResponseEntity<Map<String, String>> {
        val s = ingest.getStatus(name) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("status" to s.name))
    }

    @GetMapping("/sources/{name}/data")
    fun sourceData(@PathVariable name: String): ResponseEntity<Any> {
        val file = sourcesDir.resolve(name)
        if (!file.toFile().exists()) return ResponseEntity.notFound().build()
        return try {
            val result = registry.read(file)
            ResponseEntity.ok(mapOf("text" to result.text, "metadata" to result.metadata, "preview" to result.preview))
        } catch (e: Exception) {
            ResponseEntity.ok(mapOf("text" to file.toFile().readText(), "preview" to name))
        }
    }

    // Query
    @PostMapping("/query")
    fun query(@RequestBody req: QueryRequest) = query.query(req.question, req.tags, req.category)

    @PostMapping("/query/file-back")
    fun fileBack(@RequestBody req: FileBackRequest): ResponseEntity<Void> {
        query.fileBack(req.pageName, req.content)
        return ResponseEntity.ok().build()
    }

    // Lint
    @PostMapping("/lint")
    fun lint() = lint.lint()

    // Wipe
    @PostMapping("/wipe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun wipe() = wipeService.wipeAll()
}
