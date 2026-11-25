package com.kawrexam.student.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "kawrexam")

/** Tokens, the signed-in student and the exam in progress. Wiped on logout. */
class TokenStore(private val ctx: Context) {
    private val kAccess = stringPreferencesKey("access")
    private val kRefresh = stringPreferencesKey("refresh")
    private val kStudent = stringPreferencesKey("student")
    private val kSession = stringPreferencesKey("active_session")

    private suspend fun get(key: Preferences.Key<String>): String? = ctx.dataStore.data.first()[key]
    private suspend fun put(key: Preferences.Key<String>, value: String?) {
        ctx.dataStore.edit { if (value == null) it.remove(key) else it[key] = value }
    }

    suspend fun accessToken() = get(kAccess)
    suspend fun refreshToken() = get(kRefresh)
    suspend fun saveTokens(access: String, refresh: String) {
        ctx.dataStore.edit { it[kAccess] = access; it[kRefresh] = refresh }
    }
    suspend fun studentJson() = get(kStudent)
    suspend fun saveStudentJson(json: String) = put(kStudent, json)
    suspend fun activeSession() = get(kSession)
    suspend fun setActiveSession(id: String?) = put(kSession, id)
    suspend fun clear() { ctx.dataStore.edit { it.clear() } }
}
