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
    fun listSources(): List<SourceFileDto> {
        val mapper = tools.jackson.databind.json.JsonMapper.builder()
            .addModule(tools.jackson.module.kotlin.KotlinModule.Builder().build())
            .build()
        return sourcesDir.toFile()
            .listFiles { f -> f.isFile && f.name != "errors" }
            ?.map { f ->
                val metaFile = sourcesDir.resolve(".meta/${f.name}.json").toFile()
                val tags = if (metaFile.exists()) {
                    try {
                        val node = mapper.readTree(metaFile)
                        (0 until (node["tags"]?.size() ?: 0)).map { node["tags"][it].asText() }
                    } catch (_: Exception) { emptyList() }
                } else emptyList()
                SourceFileDto(name = f.name, extension = f.extension, tags = tags)
            } ?: emptyList()
    }

    @PostMapping("/sources/upload")
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("overwrite", defaultValue = "false") overwrite: Boolean
    ): ResponseEntity<Any> {
        val dest = sourcesDir.resolve(file.originalFilename ?: "upload")
        if (dest.toFile().exists() && !overwrite) {
            return ResponseEntity.status(409).body(mapOf("error" to "File already exists", "fileName" to dest.fileName.toString()))
        }
        return try {
            Files.copy(file.inputStream, dest, StandardCopyOption.REPLACE_EXISTING)
            ingest.ingest(dest)
            ResponseEntity.accepted().body(mapOf("fileName" to dest.fileName.toString()))
        } catch (e: IllegalArgumentException) {
            Files.deleteIfExists(dest)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
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
        val metaFile = sourcesDir.resolve(".meta/$name.json").toFile()
        val meta = if (metaFile.exists()) {
            try {
                val mapper = tools.jackson.databind.json.JsonMapper.builder()
                    .addModule(tools.jackson.module.kotlin.KotlinModule.Builder().build())
                    .build()
                val node = mapper.readTree(metaFile)
                mapOf("tags" to (node["tags"]?.let { arr -> (0 until arr.size()).map { arr[it].asText() } } ?: emptyList<String>()))
            } catch (e: Exception) {
                org.slf4j.LoggerFactory.getLogger(ApiController::class.java).warn("Could not read meta for $name: ${e.message}")
                emptyMap<String, Any>()
            }
        } else emptyMap()
        return try {
            val result = registry.read(file)
            ResponseEntity.ok(mapOf("text" to result.text, "metadata" to result.metadata, "preview" to result.preview) + meta)
        } catch (e: Exception) {
            ResponseEntity.ok(mapOf("text" to file.toFile().readText(), "preview" to name) + meta)
        }
    }

    // Query
    @PostMapping("/query")
    fun query(@RequestBody req: QueryRequest) = query.query(req.question, req.tags)

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
