package karp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class KarpApplication

fun main(args: Array<String>) {
    runApplication<KarpApplication>(*args)
}
