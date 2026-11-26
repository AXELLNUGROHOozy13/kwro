package com.kawrexam.student.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kawrexam.student.App
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

fun isoOf(ms: Long): String = Instant.ofEpochMilli(ms).toString()
fun msOf(iso: String): Long = Instant.parse(iso).toEpochMilli()

data class AnswerState(val optionIds: Set<String> = emptySet(), val text: String? = null)

class Repo(private val api: Api, private val dao: AppDao, private val store: TokenStore, private val json: Json) {

    // ── Auth ──
    suspend fun login(code: String, password: String): AuthResp {
        val r = api.login(LoginReq(code, password))
        store.saveTokens(r.accessToken, r.refreshToken)
        r.student?.let { saveStudent(it) }
        return r
    }
    suspend fun hasLogin(): Boolean = store.accessToken() != null
    suspend fun student(): StudentInfo? =
        store.studentJson()?.let { runCatching { json.decodeFromString(StudentInfo.serializer(), it) }.getOrNull() }
    suspend fun saveStudent(s: StudentInfo) = store.saveStudentJson(json.encodeToString(StudentInfo.serializer(), s))
    suspend fun changePassword(current: String, new: String) {
        val r = api.changePassword(ChangePwReq(current, new))
        if (!r.isSuccessful) throw HttpException(r)
    }
    suspend fun logout() = store.clear()
    suspend fun isSuspended(): Boolean = store.suspended()
    suspend fun setSuspended(v: Boolean) = store.setSuspended(v)

    // ── Exams ──
    suspend fun exams(): ExamListResp = api.exams()
    suspend fun history(): HistoryResp = api.history()
    suspend fun activeSessionId(): String? = store.activeSession()
    suspend fun setActiveSession(id: String?) = store.setActiveSession(id)

    suspend fun start(examId: String): SessionDto = api.start(examId).also { cache(it) }

    /** Falls back to the cached copy when offline, so a restarted phone can keep going. */
    suspend fun session(id: String): SessionDto =
        try { api.session(id).also { cache(it) } } catch (e: IOException) { cached(id) ?: throw e }

    private suspend fun cache(s: SessionDto) = dao.putSession(SessionCache(s.sessionId, json.encodeToString(SessionDto.serializer(), s)))
    private suspend fun cached(id: String): SessionDto? =
        dao.getSession(id)?.let { runCatching { json.decodeFromString(SessionDto.serializer(), it.json) }.getOrNull() }

    /** Server answers merged with anything newer that is still only on this phone. */
    suspend fun mergedAnswers(s: SessionDto): Map<String, AnswerState> {
        val map = HashMap<String, AnswerState>()
        s.answers.forEach { map[it.questionId] = AnswerState(it.optionIds.orEmpty().toSet(), it.text) }
        dao.answersFor(s.sessionId).forEach { a ->
            map[a.questionId] = AnswerState(if (a.optionIds.isEmpty()) emptySet() else a.optionIds.split(",").toSet(), a.text)
        }
        return map
    }

    suspend fun saveAnswer(sessionId: String, questionId: String, optionIds: Set<String>, text: String?) {
        dao.putAnswer(
            AnswerEntity(UUID.randomUUID().toString(), sessionId, questionId, optionIds.sorted().joinToString(","), text, System.currentTimeMillis(), false),
        )
    }

    suspend fun recordViolation(sessionId: String, type: String, occurredAt: Long, durationMs: Int?) {
        dao.putViolation(ViolationEntity(UUID.randomUUID().toString(), sessionId, type, occurredAt, durationMs, false))
    }

    // ── Sync (autosave queue) ──
    /** Uploads queued answers and violations. Throws on network trouble so callers can retry. */
    suspend fun syncAll(): ViolationsResp? {
        var last: ViolationsResp? = null
        for ((sid, rows) in dao.unsyncedAnswers().groupBy { it.sessionId }) {
            for (chunk in rows.chunked(50)) {
                val body = AnswersReq(chunk.map {
                    AnswerEvent(it.clientEventId, it.questionId, if (it.optionIds.isEmpty()) null else it.optionIds.split(","), it.text, isoOf(it.answeredAt))
                })
                try {
                    api.answers(sid, body)
                } catch (e: HttpException) {
                    if (e.code() !in listOf(400, 403, 404, 409)) throw e // the session is closed: retrying cannot help
                }
                dao.markAnswersSynced(chunk.map { it.clientEventId })
            }
        }
        for ((sid, rows) in dao.unsyncedViolations().groupBy { it.sessionId }) {
            val body = ViolationsReq(rows.map { ViolationEvent(it.clientEventId, it.type, isoOf(it.occurredAt), it.durationMs) })
            try {
                last = api.violations(sid, body)
            } catch (e: HttpException) {
                if (e.code() !in listOf(400, 403, 404, 409)) throw e
            }
            dao.markViolationsSynced(rows.map { it.clientEventId })
        }
        return last
    }

    suspend fun heartbeat(sessionId: String): HeartbeatResp = api.heartbeat(sessionId)
    suspend fun submit(sessionId: String, pin: String? = null): ResultDto = api.submit(sessionId, PinReq(pin))
    suspend fun resetViolations(sessionId: String, pin: String): ResetResp { syncAll(); return api.resetViolations(sessionId, PinReq(pin)) }
    suspend fun forget(sessionId: String) { dao.deleteSession(sessionId); store.setActiveSession(null) }
}

/** Background retry for the autosave queue, runs once a connection is available. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as App).container.repo.syncAll()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

fun scheduleSync(ctx: Context) {
    val req = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(ctx).enqueueUniqueWork("kawrexam-sync", ExistingWorkPolicy.KEEP, req)
}
