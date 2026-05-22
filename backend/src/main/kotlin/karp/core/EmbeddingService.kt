package karp.core

import ai.djl.Application
import ai.djl.inference.Predictor
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import io.qdrant.client.QdrantClient
import io.qdrant.client.grpc.Collections.Distance
import io.qdrant.client.grpc.Collections.VectorParams
import io.qdrant.client.grpc.JsonWithInt
import io.qdrant.client.grpc.Points.*
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import karp.config.KarpProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

private const val COLLECTION = "wiki"
private const val DIMS = 384L

data class SearchResult(val pageName: String, val score: Float)

@Service
class EmbeddingService(
    private val qdrant: QdrantClient,
    private val props: KarpProperties
) {
    private val log = LoggerFactory.getLogger(EmbeddingService::class.java)

    private val model: ZooModel<String, FloatArray>
    private val predictor: Predictor<String, FloatArray>

    init {
        val criteria = Criteria.builder()
            .optApplication(Application.NLP.TEXT_EMBEDDING)
            .setTypes(String::class.java, FloatArray::class.java)
            .optEngine("PyTorch")
            .optModelUrls("djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2")
            .build()
        model = criteria.loadModel()
        predictor = model.newPredictor()
    }

    @PostConstruct
    fun ensureCollection() {
        try {
            val exists = qdrant.collectionExistsAsync(COLLECTION).get()
            if (!exists) {
                qdrant.createCollectionAsync(
                    COLLECTION,
                    VectorParams.newBuilder()
                        .setDistance(Distance.Cosine)
                        .setSize(DIMS)
                        .build()
                ).get()
            }
        } catch (e: Exception) {
            log.warn("Could not connect to Qdrant at startup: ${e.message}")
        }
    }

    @PreDestroy
    fun destroy() {
        predictor.close()
        model.close()
    }

    @Synchronized
    fun embed(texts: List<String>): List<List<Float>> =
        texts.map { predictor.predict(it).toList() }

    fun upsertPage(pageName: String, content: String, tags: List<String>, category: String) {
        val vector = embed(listOf(content)).first()
        val uuid = UUID.nameUUIDFromBytes(pageName.toByteArray()).toString()

        fun strValue(s: String): JsonWithInt.Value =
            JsonWithInt.Value.newBuilder().setStringValue(s).build()

        val point = PointStruct.newBuilder()
            .setId(PointId.newBuilder().setUuid(uuid))
            .setVectors(Vectors.newBuilder().setVector(Vector.newBuilder().addAllData(vector)))
            .putPayload("name", strValue(pageName))
            .putPayload("tags", strValue(tags.joinToString(",")))
            .putPayload("category", strValue(category))
            .build()

        qdrant.upsertAsync(COLLECTION, listOf(point)).get()
    }

    fun search(
        query: String,
        topK: Int = props.topK,
        tags: List<String> = emptyList(),
        category: String? = null
    ): List<SearchResult> {
        val vector = embed(listOf(query)).first()
        val fetchLimit = if (tags.isEmpty() && category == null) topK else topK * 5

        val searchRequest = SearchPoints.newBuilder()
            .setCollectionName(COLLECTION)
            .addAllVector(vector)
            .setLimit(fetchLimit.toLong())
            .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true))
            .build()

        return qdrant.searchAsync(searchRequest).get()
            .filter { hit ->
                val storedTags = hit.payloadMap["tags"]?.stringValue?.split(",") ?: emptyList()
                val storedCategory = hit.payloadMap["category"]?.stringValue ?: ""
                (tags.isEmpty() || tags.any { it in storedTags }) &&
                (category == null || storedCategory == category)
            }
            .map { hit -> SearchResult(pageName = hit.payloadMap["name"]?.stringValue ?: "", score = hit.score) }
            .take(topK)
    }
}
