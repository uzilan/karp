package karp.config

import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path

@ConfigurationProperties(prefix = "karp")
data class KarpProperties(
    val sourcesDir: String = "/app/sources",
    val wikiDir: String = "/app/wiki",
    val anthropicApiKey: String = "",
    val voyageApiKey: String = "",
    val qdrantHost: String = "localhost",
    val qdrantPort: Int = 6334,
    val topK: Int = 5
)

@Configuration
@EnableConfigurationProperties(KarpProperties::class)
class AppConfig(private val props: KarpProperties) {

    @Bean
    fun sourcesDir(): Path = Path.of(props.sourcesDir).also {
        Files.createDirectories(it)
        Files.createDirectories(it.resolve("errors"))
    }

    @Bean
    fun wikiDir(): Path = Path.of(props.wikiDir).also {
        Files.createDirectories(it)
    }

    @Bean
    fun qdrantClient(): QdrantClient = QdrantClient(
        QdrantGrpcClient.newBuilder(props.qdrantHost, props.qdrantPort, false).build()
    )
}
