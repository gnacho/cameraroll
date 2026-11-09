package app.cameraroll.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cloudStore by preferencesDataStore(name = "cloud")

enum class CloudProvider { NEXTCLOUD, OPENCLOUD }

data class CloudConfig(
    val provider: CloudProvider = CloudProvider.NEXTCLOUD,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val basePath: String = "/Fotos",
    val autoUpload: Boolean = true,
    val lastSyncAt: Long = 0L,
    val lastSyncSummary: String = "",
) {
    val isConfigured: Boolean
        get() = url.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /** Endpoint WebDAV: si la URL ya incluye /dav se respeta; si no, se deriva. */
    val davEndpoint: String
        get() {
            val base = url.trim().trimEnd('/')
            return if (base.contains("/dav")) base
            else "$base/remote.php/dav/files/${username.trim()}"
        }
}

object CloudSettings {
    private val KEY_PROVIDER = stringPreferencesKey("provider")
    private val KEY_URL = stringPreferencesKey("url")
    private val KEY_USER = stringPreferencesKey("username")
    private val KEY_PASS = stringPreferencesKey("password")
    private val KEY_PATH = stringPreferencesKey("basePath")
    private val KEY_AUTO = booleanPreferencesKey("autoUpload")
    private val KEY_LAST_SYNC = longPreferencesKey("lastSyncAt")
    private val KEY_LAST_SUMMARY = stringPreferencesKey("lastSyncSummary")

    fun flow(context: Context): Flow<CloudConfig> =
        context.cloudStore.data.map { p ->
            CloudConfig(
                provider = if (p[KEY_PROVIDER] == "OPENCLOUD") CloudProvider.OPENCLOUD else CloudProvider.NEXTCLOUD,
                url = p[KEY_URL] ?: "",
                username = p[KEY_USER] ?: "",
                password = p[KEY_PASS] ?: "",
                basePath = (p[KEY_PATH] ?: "/Fotos").let { if (it.startsWith("/")) it else "/$it" },
                autoUpload = p[KEY_AUTO] ?: true,
                lastSyncAt = p[KEY_LAST_SYNC] ?: 0L,
                lastSyncSummary = p[KEY_LAST_SUMMARY] ?: "",
            )
        }

    suspend fun get(context: Context): CloudConfig =
        flow(context).first()

    suspend fun save(context: Context, config: CloudConfig) {
        context.cloudStore.edit { p ->
            p[KEY_PROVIDER] = config.provider.name
            p[KEY_URL] = config.url
            p[KEY_USER] = config.username
            p[KEY_PASS] = config.password
            p[KEY_PATH] = config.basePath
            p[KEY_AUTO] = config.autoUpload
        }
    }

    suspend fun recordSync(context: Context, summary: String) {
        context.cloudStore.edit { p ->
            p[KEY_LAST_SYNC] = System.currentTimeMillis()
            p[KEY_LAST_SUMMARY] = summary
        }
    }

    suspend fun clear(context: Context) {
        context.cloudStore.edit { it.clear() }
    }
}
