package karp.config

import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.tomcat.TomcatWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path

@ConfigurationProperties(prefix = "karp")
data class KarpProperties(
    val sourcesDir: String = "${System.getProperty("user.home")}/.karp/sources",
    val wikiDir: String = "${System.getProperty("user.home")}/.karp/wiki",
    val qdrantHost: String = "localhost",
    val qdrantPort: Int = 6334,
    val topK: Int = 5,
)

@Configuration
@EnableConfigurationProperties(KarpProperties::class)
class AppConfig(
    private val props: KarpProperties,
) {
    @Bean
    fun sourcesDir(): Path =
        Path.of(props.sourcesDir).also {
            Files.createDirectories(it)
            Files.createDirectories(it.resolve("errors"))
        }

    @Bean
    fun wikiDir(): Path =
        Path.of(props.wikiDir).also {
            Files.createDirectories(it)
        }

    @Bean
    fun tomcatCustomizer(): WebServerFactoryCustomizer<TomcatWebServerFactory> =
        WebServerFactoryCustomizer { factory ->
            factory.addConnectorCustomizers({ connector ->
                connector.setProperty("socket.soLinger", "0")
            })
        }

    @Bean
    fun qdrantClient(): QdrantClient =
        QdrantClient(
            QdrantGrpcClient.newBuilder(props.qdrantHost, props.qdrantPort, false).build(),
        )
}
