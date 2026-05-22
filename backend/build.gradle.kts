plugins {
    id("org.springframework.boot") version "4.1.0-RC1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.spring") version "2.3.20"
}

group = "karp"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

extra["springAiVersion"] = "2.0.0-M6"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Jackson 3 — group ID changed from com.fasterxml.jackson to tools.jackson
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")

    // Spring AI MCP Server (renamed in 2.x)
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    // Claude API
    implementation("com.anthropic:anthropic-java:0.8.0")

    // Qdrant
    implementation("io.qdrant:client:1.9.1")
    implementation("com.google.protobuf:protobuf-java:3.24.0")

    // File readers
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.pdfbox:pdfbox:3.0.2")
    implementation("io.swagger.parser.v3:swagger-parser:2.1.21")

    // Local embeddings via DJL
    implementation("ai.djl:api:0.31.0")
    implementation("ai.djl.huggingface:tokenizers:0.31.0")
    runtimeOnly("ai.djl.pytorch:pytorch-engine:0.31.0")
    runtimeOnly("ai.djl.pytorch:pytorch-jni:2.5.1-0.31.0")
    runtimeOnly("ai.djl.pytorch:pytorch-native-cpu:2.5.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
