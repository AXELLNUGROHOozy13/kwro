package com.kawrexam.student.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response as HttpResponse
import okhttp3.Route
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.io.IOException

@Serializable data class LoginReq(val loginCode: String, val password: String)
@Serializable data class RefreshReq(val refreshToken: String)
@Serializable data class ChangePwReq(val currentPassword: String, val newPassword: String)
@Serializable data class StudentInfo(val id: String, val name: String, val className: String? = null, val mustChangePassword: Boolean = false)
@Serializable data class AuthResp(val accessToken: String, val refreshToken: String, val expiresIn: Int = 900, val student: StudentInfo? = null)

@Serializable
data class ExamItem(
    val id: String, val title: String, val description: String? = null, val durationMinutes: Int,
    val startsAt: String? = null, val endsAt: String? = null, val status: String,
    val sessionId: String? = null, val sessionStatus: String? = null,
    val canStart: Boolean = false, val reason: String? = null, val suspended: Boolean = false,
)
@Serializable data class ExamListResp(val data: List<ExamItem>, val serverTime: String)

@Serializable data class OptionDto(val id: String, val text: String)
@Serializable data class QuestionDto(val id: String, val type: String, val text: String, val imageUrl: String? = null, val options: List<OptionDto> = emptyList())
@Serializable data class AnswerDto(val questionId: String, val optionIds: List<String>? = null, val text: String? = null)

@Serializable
data class SessionDto(
    val sessionId: String, val examId: String, val title: String, val status: String,
    val deadlineAt: String, val serverTime: String, val offlineMode: Boolean = false,
    val warningCount: Int = 0, val penaltyUntil: String? = null,
    val questions: List<QuestionDto>, val answers: List<AnswerDto> = emptyList(),
)

@Serializable data class AnswerEvent(val clientEventId: String, val questionId: String, val optionIds: List<String>? = null, val text: String? = null, val answeredAt: String)
@Serializable data class AnswersReq(val answers: List<AnswerEvent>)
@Serializable data class ViolationEvent(val clientEventId: String, val type: String, val occurredAt: String, val durationMs: Int? = null)
@Serializable data class ViolationsReq(val events: List<ViolationEvent>)
@Serializable data class ViolationsResp(val warningCount: Int, val penaltyUntil: String? = null, val penaltyMinutes: Int = 0, val serverTime: String, val suspended: Boolean = false)
@Serializable data class PinReq(val pin: String? = null)
@Serializable data class ResetResp(val warningCount: Int = 0, val penaltyUntil: String? = null)
@Serializable data class HeartbeatResp(val status: String, val suspended: Boolean = false, val serverTime: String, val deadlineAt: String, val warningCount: Int = 0, val penaltyUntil: String? = null)
@Serializable
data class ResultDto(
    val visible: Boolean, val message: String? = null, val finalScore: Double? = null, val passed: Boolean? = null,
    val kkm: Double? = null, val correct: Int? = null, val incorrect: Int? = null, val unanswered: Int? = null,
)

@Serializable
data class HistoryItem(
    val sessionId: String, val title: String, val durationMinutes: Int, val finishedAt: String? = null, val forced: Boolean = false,
    val visible: Boolean = false, val finalScore: Double? = null, val passed: Boolean? = null, val kkm: Double? = null,
    val correct: Int? = null, val incorrect: Int? = null, val unanswered: Int? = null,
)
@Serializable data class HistoryResp(val data: List<HistoryItem>)

interface Api {
    @GET("api/v1/student/history") suspend fun history(): HistoryResp
    @POST("api/v1/auth/student/login") suspend fun login(@Body b: LoginReq): AuthResp
    @POST("api/v1/auth/student/refresh") suspend fun refresh(@Body b: RefreshReq): AuthResp
    @POST("api/v1/student/change-password") suspend fun changePassword(@Body b: ChangePwReq): Response<Unit>
    @GET("api/v1/student/exams") suspend fun exams(): ExamListResp
    @POST("api/v1/student/exams/{id}/start") suspend fun start(@Path("id") id: String): SessionDto
    @GET("api/v1/student/sessions/{id}") suspend fun session(@Path("id") id: String): SessionDto
    @PUT("api/v1/student/sessions/{id}/answers") suspend fun answers(@Path("id") id: String, @Body b: AnswersReq): Response<Unit>
    @POST("api/v1/student/sessions/{id}/violations") suspend fun violations(@Path("id") id: String, @Body b: ViolationsReq): ViolationsResp
    @POST("api/v1/student/sessions/{id}/heartbeat") suspend fun heartbeat(@Path("id") id: String): HeartbeatResp
    @POST("api/v1/student/sessions/{id}/submit") suspend fun submit(@Path("id") id: String, @Body b: PinReq): ResultDto
    @POST("api/v1/student/sessions/{id}/reset-violations") suspend fun resetViolations(@Path("id") id: String, @Body b: PinReq): ResetResp
}

/**
 * Refreshes the access token on a 401. Refresh tokens rotate and a reused one revokes the session,
 * so refreshing is serialized and a request that lost the race simply retries with the newer token.
 */
class TokenAuthenticator(private val store: TokenStore, private val refreshApi: () -> Api) : Authenticator {
    override fun authenticate(route: Route?, response: HttpResponse): Request? {
        if (response.request.url.encodedPath.contains("/auth/")) return null
        if (response.priorResponse != null) return null // already retried once
        return synchronized(this) {
            val used = response.request.header("Authorization")?.removePrefix("Bearer ")
            val current = runBlocking { store.accessToken() }
            if (current != null && current != used) return retry(response, current)
            val refresh = runBlocking { store.refreshToken() } ?: return null
            val fresh = try {
                runBlocking { refreshApi().refresh(RefreshReq(refresh)) }
            } catch (e: Exception) {
                return null
            }
            runBlocking { store.saveTokens(fresh.accessToken, fresh.refreshToken) }
            retry(response, fresh.accessToken)
        }
    }

    private fun retry(r: HttpResponse, token: String): Request =
        r.request.newBuilder().header("Authorization", "Bearer $token").build()
}

/** Turns network and server errors into messages a student can act on. */
fun friendly(e: Throwable): String = when (e) {
    is HttpException -> {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val msg = body?.let {
            runCatching { Json.parseToJsonElement(it).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()
        }
        msg ?: "Server menolak permintaan (${e.code()})."
    }
    is IOException -> "Tidak ada koneksi internet."
    else -> e.message ?: "Terjadi kesalahan."
}
