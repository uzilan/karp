package karp.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.qdrant.client.QdrantClient
import io.qdrant.client.grpc.Collections.Distance
import io.qdrant.client.grpc.Collections.VectorParams
import io.qdrant.client.grpc.JsonWithInt
import io.qdrant.client.grpc.Points.*
import jakarta.annotation.PostConstruct
import karp.config.KarpProperties
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.stereotype.Service
import java.util.UUID

private const val COLLECTION = "wiki"
private const val DIMS = 512L

data class SearchResult(val pageName: String, val score: Float)

@Service
class EmbeddingService(
    private val qdrant: QdrantClient,
    private val props: KarpProperties
) {
    private val http = OkHttpClient()
    private val mapper = ObjectMapper().registerKotlinModule()

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
            println("Warning: Could not connect to Qdrant at startup: ${e.message}")
        }
    }

    fun embed(texts: List<String>): List<List<Float>> {
        val body = mapper.writeValueAsString(mapOf(
            "input" to texts,
            "model" to "voyage-3-lite"
        ))
        val request = Request.Builder()
            .url("https://api.voyageai.com/v1/embeddings")
            .header("Authorization", "Bearer ${props.voyageApiKey}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("Voyage AI error: ${response.code}")
            response.body!!.string()
        }
        val json = mapper.readTree(responseBody)
        return json["data"].map { item ->
            item["embedding"].map { it.floatValue() }
        }
    }

    fun upsertPage(pageName: String, content: String, tags: List<String>, category: String) {
        val vector = embed(listOf(content)).first()
        val uuid = UUID.nameUUIDFromBytes(pageName.toByteArray()).toString()

        fun strValue(s: String): JsonWithInt.Value =
            JsonWithInt.Value.newBuilder().setStringValue(s).build()

        val point = PointStruct.newBuilder()
            .setId(PointId.newBuilder().setUuid(uuid))
            .setVectors(
                Vectors.newBuilder().setVector(
                    Vector.newBuilder().addAllData(vector)
                )
            )
            .putPayload("name", strValue(pageName))
            .putPayload("tags", strValue(tags.joinToString(",")))
            .putPayload("category", strValue(category))
            .build()

        qdrant.upsertAsync(COLLECTION, listOf(point)).get()
    }

    fun search(query: String, topK: Int = props.topK, tags: List<String> = emptyList(), category: String? = null): List<SearchResult> {
        val vector = embed(listOf(query)).first()

        val searchBuilder = SearchPoints.newBuilder()
            .setCollectionName(COLLECTION)
            .addAllVector(vector)
            .setLimit(topK.toLong())
            .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true))

        if (tags.isNotEmpty() || category != null) {
            val conditions = mutableListOf<Condition>()
            tags.forEach { tag ->
                conditions.add(
                    Condition.newBuilder().setField(
                        FieldCondition.newBuilder()
                            .setKey("tags")
                            .setMatch(Match.newBuilder().setText(tag))
                    ).build()
                )
            }
            category?.let {
                conditions.add(
                    Condition.newBuilder().setField(
                        FieldCondition.newBuilder()
                            .setKey("category")
                            .setMatch(Match.newBuilder().setText(it))
                    ).build()
                )
            }
            searchBuilder.setFilter(Filter.newBuilder().addAllMust(conditions))
        }

        return qdrant.searchAsync(searchBuilder.build()).get()
            .map { hit ->
                SearchResult(
                    pageName = hit.payloadMap["name"]?.stringValue ?: "",
                    score = hit.score
                )
            }
    }
}
