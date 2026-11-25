package com.kawrexam.student.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** Every answer change is its own row (append-only), so history survives and uploads are idempotent. */
@Entity(tableName = "answers")
data class AnswerEntity(
    @PrimaryKey val clientEventId: String,
    val sessionId: String,
    val questionId: String,
    val optionIds: String, // comma separated option ids
    val text: String?,
    val answeredAt: Long,
    val synced: Boolean,
)

@Entity(tableName = "violations")
data class ViolationEntity(
    @PrimaryKey val clientEventId: String,
    val sessionId: String,
    val type: String,
    val occurredAt: Long,
    val durationMs: Int?,
    val synced: Boolean,
)

/** Last session payload, so an exam can be reopened after a crash or restart without network. */
@Entity(tableName = "session_cache")
data class SessionCache(@PrimaryKey val sessionId: String, val json: String)

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAnswer(a: AnswerEntity)
    @Query("SELECT * FROM answers WHERE sessionId = :sid ORDER BY answeredAt ASC") suspend fun answersFor(sid: String): List<AnswerEntity>
    @Query("SELECT * FROM answers WHERE synced = 0 ORDER BY answeredAt ASC LIMIT 300") suspend fun unsyncedAnswers(): List<AnswerEntity>
    @Query("UPDATE answers SET synced = 1 WHERE clientEventId IN (:ids)") suspend fun markAnswersSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putViolation(v: ViolationEntity)
    @Query("SELECT * FROM violations WHERE synced = 0 ORDER BY occurredAt ASC LIMIT 100") suspend fun unsyncedViolations(): List<ViolationEntity>
    @Query("UPDATE violations SET synced = 1 WHERE clientEventId IN (:ids)") suspend fun markViolationsSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSession(s: SessionCache)
    @Query("SELECT * FROM session_cache WHERE sessionId = :sid") suspend fun getSession(sid: String): SessionCache?
    @Query("DELETE FROM session_cache WHERE sessionId = :sid") suspend fun deleteSession(sid: String)
}

@Database(entities = [AnswerEntity::class, ViolationEntity::class, SessionCache::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
}
