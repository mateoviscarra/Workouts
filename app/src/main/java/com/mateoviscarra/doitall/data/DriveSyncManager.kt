package com.mateoviscarra.doitall.data

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.mateoviscarra.doitall.calendar.CALENDAR_SCOPES
import com.mateoviscarra.doitall.calendar.DRIVE_FILE_SCOPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "DriveSyncManager"
private const val PREFS_NAME = "drive_sync_prefs"
private const val KEY_AUTO_SYNC = "auto_sync_enabled"
private const val KEY_FOLDER_ID = "drive_folder_id"
private const val KEY_DOC_FILE_ID = "drive_doc_file_id"
private const val KEY_JSON_FILE_ID = "drive_json_file_id"
private const val KEY_LAST_SYNC_TIME = "last_sync_time_ms"
private const val KEY_LAST_SYNC_STATUS = "last_sync_status"
private const val KEY_LAST_SYNC_ERROR = "last_sync_error"

private const val FOLDER_NAME = "DoItAll Fitness"
private const val DOC_FILE_NAME = "DoItAll — Live Coach Context & Data"
private const val JSON_FILE_NAME = "workouts_backup_latest.json"
private const val GOOGLE_FOLDER_MIME = "application/vnd.google-apps.folder"
private const val GOOGLE_DOC_MIME = "application/vnd.google-apps.document"
private const val DEBOUNCE_DELAY_MS = 12_000L

data class DriveSyncState(
    val isSyncing: Boolean = false,
    val autoSyncEnabled: Boolean = true,
    val lastSyncTimeMs: Long = 0L,
    val lastSyncStatus: String? = null,
    val lastSyncError: String? = null
)

class RecoverableDriveAuthException(
    val recoveryIntent: Intent?,
    message: String
) : Exception(message)

object DriveSyncManager {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var debounceJob: Job? = null

    @Volatile
    private var initialized = false

    private val _state = MutableStateFlow(DriveSyncState())

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun stateFlow(context: Context): StateFlow<DriveSyncState> {
        ensureInitialized(context)
        return _state.asStateFlow()
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        val p = prefs(context)
        _state.value = DriveSyncState(
            isSyncing = false,
            autoSyncEnabled = p.getBoolean(KEY_AUTO_SYNC, true),
            lastSyncTimeMs = p.getLong(KEY_LAST_SYNC_TIME, 0L),
            lastSyncStatus = p.getString(KEY_LAST_SYNC_STATUS, null),
            lastSyncError = p.getString(KEY_LAST_SYNC_ERROR, null)
        )
        initialized = true
    }

    fun getSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context.applicationContext)

    fun hasDriveScope(context: Context): Boolean {
        val account = getSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DRIVE_FILE_SCOPE))
    }

    fun getDriveSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CALENDAR_SCOPES), Scope(DRIVE_FILE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        ensureInitialized(context)
        return _state.value.autoSyncEnabled
    }

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        ensureInitialized(context)
        prefs(context).edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        _state.value = _state.value.copy(autoSyncEnabled = enabled)
    }

    fun scheduleAutoSync(context: Context, reason: String = "Update") {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        if (!isAutoSyncEnabled(appContext) || !hasDriveScope(appContext)) {
            return
        }
        debounceJob?.cancel()
        debounceJob = syncScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            Log.d(TAG, "Running debounced Drive auto-sync (reason=$reason)")
            syncNow(appContext)
        }
    }

    suspend fun syncNow(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        debounceJob?.cancel()

        syncMutex.withLock {
            _state.value = _state.value.copy(isSyncing = true, lastSyncError = null)
            try {
                val account = getSignedInAccount(appContext)
                    ?: throw IllegalStateException("Google account is not signed in.")
                if (!GoogleSignIn.hasPermissions(account, Scope(DRIVE_FILE_SCOPE))) {
                    throw IllegalStateException("Google Drive permission not granted yet. Tap Connect / Grant Access in Settings.")
                }
                val email = account.email
                    ?: throw IllegalStateException("Signed-in Google account has no email.")
                val androidAccount = account.account ?: Account(email, "com.google")

                val backupManager = DataBackupManager(appContext)
                val backupJson = backupManager.createUnifiedBackupJson()
                val docMarkdown = backupManager.createNotebookLmDocMarkdown()

                var token = fetchOAuthToken(appContext, androidAccount)

                suspend fun performUpload(authToken: String): String {
                    val p = prefs(appContext)
                    val folderId = ensureFolder(authToken, p)
                    p.edit().putString(KEY_FOLDER_ID, folderId).apply()

                    val docId = upsertFileInPlace(
                        token = authToken,
                        folderId = folderId,
                        cachedFileId = p.getString(KEY_DOC_FILE_ID, null),
                        fileName = DOC_FILE_NAME,
                        targetDriveMimeType = GOOGLE_DOC_MIME,
                        uploadContentType = "text/plain; charset=UTF-8",
                        contentBytes = docMarkdown.toByteArray(Charsets.UTF_8)
                    )
                    p.edit().putString(KEY_DOC_FILE_ID, docId).apply()

                    val jsonId = upsertFileInPlace(
                        token = authToken,
                        folderId = folderId,
                        cachedFileId = p.getString(KEY_JSON_FILE_ID, null),
                        fileName = JSON_FILE_NAME,
                        targetDriveMimeType = "application/json",
                        uploadContentType = "application/json; charset=UTF-8",
                        contentBytes = backupJson.toByteArray(Charsets.UTF_8)
                    )
                    p.edit().putString(KEY_JSON_FILE_ID, jsonId).apply()

                    return "Synced Google Doc & JSON to \"$FOLDER_NAME\""
                }

                val statusMsg = try {
                    performUpload(token)
                } catch (e: HttpStatusException) {
                    if (e.code == 401) {
                        clearOAuthToken(appContext, token)
                        token = fetchOAuthToken(appContext, androidAccount)
                        performUpload(token)
                    } else {
                        throw e
                    }
                }

                val now = System.currentTimeMillis()
                prefs(appContext).edit()
                    .putLong(KEY_LAST_SYNC_TIME, now)
                    .putString(KEY_LAST_SYNC_STATUS, statusMsg)
                    .remove(KEY_LAST_SYNC_ERROR)
                    .apply()

                _state.value = _state.value.copy(
                    isSyncing = false,
                    lastSyncTimeMs = now,
                    lastSyncStatus = statusMsg,
                    lastSyncError = null
                )
                Result.success(statusMsg)
            } catch (e: Exception) {
                Log.e(TAG, "Drive sync failed", e)
                val msg = e.localizedMessage ?: "Google Drive sync failed."
                prefs(appContext).edit()
                    .putString(KEY_LAST_SYNC_ERROR, msg)
                    .apply()
                _state.value = _state.value.copy(
                    isSyncing = false,
                    lastSyncError = msg
                )
                Result.failure(e)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun fetchOAuthToken(context: Context, androidAccount: Account): String {
        val scopeString = "oauth2:$DRIVE_FILE_SCOPE"
        return try {
            GoogleAuthUtil.getToken(context, androidAccount, scopeString)
        } catch (e: UserRecoverableAuthException) {
            throw RecoverableDriveAuthException(
                recoveryIntent = e.intent,
                message = "Additional Google Drive consent required."
            )
        } catch (e: Exception) {
            // Fallback to AccountManager
            val am = AccountManager.get(context)
            val bundle = am.getAuthToken(androidAccount, scopeString, false, null, null).result
            val recoveryIntent = bundle?.getParcelable<Intent>(AccountManager.KEY_INTENT)
            if (recoveryIntent != null) {
                throw RecoverableDriveAuthException(
                    recoveryIntent = recoveryIntent,
                    message = "Google Drive consent required."
                )
            }
            bundle?.getString(AccountManager.KEY_AUTHTOKEN)
                ?: throw IllegalStateException("Unable to obtain Google Drive token: ${e.localizedMessage}", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun clearOAuthToken(context: Context, token: String) {
        runCatching { GoogleAuthUtil.clearToken(context, token) }
        runCatching { AccountManager.get(context).invalidateAuthToken("com.google", token) }
    }

    private fun ensureFolder(token: String, prefs: SharedPreferences): String {
        val cachedId = prefs.getString(KEY_FOLDER_ID, null)
        if (!cachedId.isNullOrBlank() && verifyFileExists(token, cachedId)) {
            return cachedId
        }

        val escapedName = FOLDER_NAME.replace("'", "\\'")
        val query = "mimeType='$GOOGLE_FOLDER_MIME' and name='$escapedName' and trashed=false"
        val existingId = searchFirstFileId(token, query)
        if (existingId != null) return existingId

        val metadata = JSONObject().apply {
            put("name", FOLDER_NAME)
            put("mimeType", GOOGLE_FOLDER_MIME)
        }
        val response = executeRequest(
            urlStr = "https://www.googleapis.com/drive/v3/files?fields=id",
            method = "POST",
            token = token,
            contentType = "application/json; charset=UTF-8",
            bodyBytes = metadata.toString().toByteArray(Charsets.UTF_8)
        )
        return JSONObject(response).getString("id")
    }

    private fun upsertFileInPlace(
        token: String,
        folderId: String,
        cachedFileId: String?,
        fileName: String,
        targetDriveMimeType: String,
        uploadContentType: String,
        contentBytes: ByteArray
    ): String {
        var targetId: String? = null
        if (!cachedFileId.isNullOrBlank() && verifyFileExists(token, cachedFileId)) {
            targetId = cachedFileId
        }

        if (targetId == null) {
            val escapedName = fileName.replace("'", "\\'")
            val query = "name='$escapedName' and '$folderId' in parents and trashed=false"
            targetId = searchFirstFileId(token, query)
        }

        return if (targetId != null) {
            // Update existing file content in-place to preserve the File ID for NotebookLM
            executeRequest(
                urlStr = "https://www.googleapis.com/upload/drive/v3/files/$targetId?uploadType=media&fields=id",
                method = "PATCH",
                token = token,
                contentType = uploadContentType,
                bodyBytes = contentBytes
            )
            targetId
        } else {
            val metadata = JSONObject().apply {
                put("name", fileName)
                put("mimeType", targetDriveMimeType)
                put("parents", JSONArray().put(folderId))
            }
            val boundary = "DoItAllDriveBoundary${System.currentTimeMillis()}"
            val out = ByteArrayOutputStream()
            out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
            out.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
            out.write(metadata.toString().toByteArray(Charsets.UTF_8))
            out.write("\r\n--$boundary\r\n".toByteArray(Charsets.UTF_8))
            out.write("Content-Type: $uploadContentType\r\n\r\n".toByteArray(Charsets.UTF_8))
            out.write(contentBytes)
            out.write("\r\n--$boundary--".toByteArray(Charsets.UTF_8))

            val response = executeRequest(
                urlStr = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
                method = "POST",
                token = token,
                contentType = "multipart/related; boundary=$boundary",
                bodyBytes = out.toByteArray()
            )
            JSONObject(response).getString("id")
        }
    }

    private fun verifyFileExists(token: String, fileId: String): Boolean {
        return try {
            val res = executeRequest(
                urlStr = "https://www.googleapis.com/drive/v3/files/$fileId?fields=id,trashed",
                method = "GET",
                token = token
            )
            val obj = JSONObject(res)
            !obj.optBoolean("trashed", false) && obj.optString("id").isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun searchFirstFileId(token: String, query: String): String? {
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val res = executeRequest(
            urlStr = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id,name)&spaces=drive&pageSize=1",
            method = "GET",
            token = token
        )
        val files = JSONObject(res).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        return files.getJSONObject(0).getString("id")
    }

    private class HttpStatusException(val code: Int, message: String) : Exception("HTTP $code: $message")

    private fun executeRequest(
        urlStr: String,
        method: String,
        token: String,
        contentType: String? = null,
        bodyBytes: ByteArray? = null
    ): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            if (method == "PATCH") {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                requestMethod = method
            }
            setRequestProperty("Authorization", "Bearer $token")
            if (contentType != null) {
                setRequestProperty("Content-Type", contentType)
            }
            if (bodyBytes != null) {
                doOutput = true
                setFixedLengthStreamingMode(bodyBytes.size)
            }
        }

        try {
            if (bodyBytes != null) {
                conn.outputStream.use { it.write(bodyBytes) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val parsedMsg = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                }.getOrNull()
                throw HttpStatusException(
                    code,
                    parsedMsg ?: body.ifBlank { conn.responseMessage ?: "Request failed" }
                )
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
}
