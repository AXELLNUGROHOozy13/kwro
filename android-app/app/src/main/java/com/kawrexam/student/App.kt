package com.kawrexam.student

import android.app.Application
import android.content.Context
import androidx.room.Room
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kawrexam.student.data.Api
import com.kawrexam.student.data.AppDb
import com.kawrexam.student.data.Repo
import com.kawrexam.student.data.TokenAuthenticator
import com.kawrexam.student.data.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class App : Application() {
    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        container = Container(this)
    }
}

/** Hand-wired dependencies (small app, so no DI framework). */
class Container(ctx: Context) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    val store = TokenStore(ctx)
    private val db = Room.databaseBuilder(ctx, AppDb::class.java, "kawrexam.db").build()

    // Separate client without the authenticator, used only to refresh tokens.
    private val refreshApi: Api = build(withAuth = false)
    val api: Api = build(withAuth = true)
    val repo = Repo(api, db.dao(), store, json)

    private fun build(withAuth: Boolean): Api {
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
        if (withAuth) {
            http.addInterceptor { chain ->
                val token = runBlocking { store.accessToken() }
                val req = if (token != null) chain.request().newBuilder().header("Authorization", "Bearer $token").build() else chain.request()
                chain.proceed(req)
            }
            http.authenticator(TokenAuthenticator(store) { refreshApi })
        }
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
            .client(http.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(Api::class.java)
    }
}
