package net.android.apllicationusetime.data.supabase

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Supabase Auth 管理器 —— 支持注册 / 登录 / 注销 / Token 持久化
 */
object AuthManager {

    private const val TAG = "AuthManager"
    private const val AUTH_BASE = "${SupabaseConfig.SUPABASE_URL}/auth/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    // ================== 持久化 Key ==================

    private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_session")

    private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
    private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
    private val KEY_USER_USERNAME = stringPreferencesKey("user_username")

    // ================== 数据类 ==================

    data class AuthResult(
        val success: Boolean,
        val error: String? = null,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val email: String? = null,
        val username: String? = null
    )

    // ================== 会话状态 Flow ==================

    /** 是否已登录 */
    fun isLoggedInFlow(context: Context): Flow<Boolean> {
        return context.authDataStore.data.map { prefs ->
            !prefs[KEY_ACCESS_TOKEN].isNullOrEmpty()
        }
    }

    /** 获取当前用户邮箱 */
    fun getUserEmailFlow(context: Context): Flow<String?> {
        return context.authDataStore.data.map { prefs ->
            prefs[KEY_USER_EMAIL]
        }
    }

    /** 获取当前用户名 */
    fun getUsernameFlow(context: Context): Flow<String?> {
        return context.authDataStore.data.map { prefs ->
            prefs[KEY_USER_USERNAME]
        }
    }

    /** 获取当前 access token（同步，用于 API 调用） */
    suspend fun getAccessToken(context: Context): String? {
        return context.authDataStore.data.first()[KEY_ACCESS_TOKEN]
    }

    // ================== 注册 ==================

    /**
     * 使用 邮箱 + 密码 + 用户名 注册
     * Supabase Auth 的 signup 接口默认只支持 email 和 password
     * 我们注册成功后，将用户名存入 user_metadata
     */
    suspend fun signUp(context: Context, email: String, password: String, username: String): AuthResult {
        return try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("data", JSONObject().apply {
                    put("username", username)
                })
            }

            val request = Request.Builder()
                .url("$AUTH_BASE/signup")
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val accessToken = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token", "")
                val userEmail = json.optJSONObject("user")?.optString("email", email)
                val meta = json.optJSONObject("user")?.optJSONObject("user_metadata")
                val userName = meta?.optString("username", username) ?: username

                if (accessToken.isNotEmpty()) {
                    saveSession(context, accessToken, refreshToken, userEmail, userName)
                    AuthResult(success = true, accessToken = accessToken, refreshToken = refreshToken,
                        email = userEmail, username = userName)
                } else {
                    // 可能开启了邮箱确认
                    val msg = json.optString("msg", "注册成功，请检查邮箱确认")
                    AuthResult(success = true, error = msg)
                }
            } else {
                val errMsg = if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    json.optString("error_description", json.optString("error", "注册失败"))
                } else "注册失败: ${response.code}"
                AuthResult(success = false, error = errMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignUp error", e)
            AuthResult(success = false, error = "网络错误: ${e.message}")
        }
    }

    // ================== 登录 ==================

    /**
     * 使用 邮箱 + 密码 登录
     */
    suspend fun signIn(context: Context, email: String, password: String): AuthResult {
        return try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            val request = Request.Builder()
                .url("$AUTH_BASE/token?grant_type=password")
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val accessToken = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token", "")
                val userEmail = json.optJSONObject("user")?.optString("email", email) ?: email
                val meta = json.optJSONObject("user")?.optJSONObject("user_metadata")
                val userName = meta?.optString("username", "")

                saveSession(context, accessToken, refreshToken, userEmail, userName)
                AuthResult(success = true, accessToken = accessToken, refreshToken = refreshToken,
                    email = userEmail, username = userName)
            } else {
                val errMsg = if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    json.optString("error_description", json.optString("error", "登录失败"))
                } else "登录失败: ${response.code}"
                AuthResult(success = false, error = errMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignIn error", e)
            AuthResult(success = false, error = "网络错误: ${e.message}")
        }
    }

    // ================== 注销 ==================

    suspend fun signOut(context: Context) {
        context.authDataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_USER_USERNAME)
        }
    }

    // ================== 内部方法 ==================

    private suspend fun saveSession(
        context: Context,
        accessToken: String,
        refreshToken: String,
        email: String?,
        username: String?
    ) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            if (email != null) prefs[KEY_USER_EMAIL] = email
            if (username != null) prefs[KEY_USER_USERNAME] = username
        }
    }
}