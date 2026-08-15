package com.kingzcheung.xime.relationship.network

import com.kingzcheung.xime.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class ReplyCandidateDto(
    val candidateId: String,
    val style: String,
    val text: String,
    val usedMemories: List<String> = emptyList(),
)

@Serializable
data class ReplyGenerateResponseDto(
    val requestId: String,
    val provider: String,
    val modelVersion: String,
    val candidates: List<ReplyCandidateDto>,
)

data class SmartReplyResult(
    val response: ReplyGenerateResponseDto,
    val degraded: Boolean,
)

@Serializable
data class MemoryCandidateDto(
    val candidateId: String,
    val type: String,
    val category: String,
    val content: String,
    val sourceExcerpt: String,
    val confidence: Double,
    val requiresConfirmation: Boolean,
)

@Serializable
private data class MemoryExtractRequestDto(
    val currentMessage: String,
    val anonymousPersonId: String,
    val recentContext: List<String> = emptyList(),
)

@Serializable
private data class MemoryExtractResponseDto(
    val requestId: String,
    val provider: String,
    val modelVersion: String,
    val candidates: List<MemoryCandidateDto>,
)

data class MemoryExtractResult(
    val candidates: List<MemoryCandidateDto>,
    val degraded: Boolean,
)

@Serializable
private data class ReplyGenerateRequestDto(
    val currentMessage: String,
    val anonymousPersonId: String,
    val relationshipStage: String,
    val goal: String,
    val relevantMemories: List<String>,
    val userStyle: UserStyleDto = UserStyleDto(),
)

@Serializable
private data class UserStyleDto(
    val replyLength: String = "short",
    val humor: String = "light",
    val emoji: String = "rare",
)

@Serializable
private data class ApiEnvelope<T>(
    val code: String,
    val data: T? = null,
    val msg: String = "",
)

@Serializable
private data class FeedbackRequestDto(
    val requestId: String,
    val candidateId: String,
    val action: String,
    val editRatio: Double? = null,
    val recipientOutcome: String? = null,
)

class RelationshipAiClient private constructor() {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun generateReplies(
        message: String,
        anonymousPersonId: String,
        relationshipStage: String,
        memories: List<String>,
    ): SmartReplyResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = ReplyGenerateRequestDto(
                currentMessage = message.take(2_000),
                anonymousPersonId = anonymousPersonId,
                relationshipStage = relationshipStage.take(32),
                goal = "support",
                relevantMemories = memories.take(10).map { it.take(300) },
            )
            val request = Request.Builder()
                .url("${BuildConfig.RELATIONSHIP_API_BASE_URL.trimEnd('/')}/api/v1/relationship-ai/replies/generate")
                .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("服务返回 ${response.code}")
                val envelope = json.decodeFromString<ApiEnvelope<ReplyGenerateResponseDto>>(body)
                if (envelope.code != "00000" || envelope.data == null) {
                    error(envelope.msg.ifBlank { "服务暂不可用" })
                }
                SmartReplyResult(envelope.data, degraded = false)
            }
        }.getOrElse {
            SmartReplyResult(localFallback(memories), degraded = true)
        }
    }

    suspend fun extractMemories(
        message: String,
        anonymousPersonId: String,
    ): MemoryExtractResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = MemoryExtractRequestDto(
                currentMessage = message.take(2_000),
                anonymousPersonId = anonymousPersonId,
            )
            val request = Request.Builder()
                .url("${BuildConfig.RELATIONSHIP_API_BASE_URL.trimEnd('/')}/api/v1/relationship-ai/memory/extract")
                .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("服务返回 ${response.code}")
                val envelope = json.decodeFromString<ApiEnvelope<MemoryExtractResponseDto>>(body)
                if (envelope.code != "00000" || envelope.data == null) error(envelope.msg)
                MemoryExtractResult(envelope.data.candidates, degraded = false)
            }
        }.getOrElse {
            MemoryExtractResult(
                candidates = listOf(
                    MemoryCandidateDto(
                        candidateId = "local-memory",
                        type = "FACT",
                        category = "current_concern",
                        content = message.trim().take(120),
                        sourceExcerpt = message.trim().take(80),
                        confidence = 0.5,
                        requiresConfirmation = true,
                    ),
                ).filter { it.content.isNotBlank() },
                degraded = true,
            )
        }
    }

    suspend fun sendFeedback(
        requestId: String,
        candidateId: String,
        action: String,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = FeedbackRequestDto(requestId, candidateId, action)
            val request = Request.Builder()
                .url("${BuildConfig.RELATIONSHIP_API_BASE_URL.trimEnd('/')}/api/v1/relationship-ai/feedback")
                .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun localFallback(memories: List<String>): ReplyGenerateResponseDto {
        val used = memories.take(2)
        return ReplyGenerateResponseDto(
            requestId = "local-fallback",
            provider = "local",
            modelVersion = "fallback-v1",
            candidates = listOf(
                ReplyCandidateDto("local-natural", "natural", "听起来你今天真的挺累的，先缓一缓也没关系。", used),
                ReplyCandidateDto("local-humorous", "humorous", "今天先切到省电模式，别和自己硬碰硬啦。", used),
                ReplyCandidateDto("local-measured", "measured", "如果你想说，我会听；暂时不想说也不用勉强。", used),
            ),
        )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val instance: RelationshipAiClient by lazy { RelationshipAiClient() }
    }
}
