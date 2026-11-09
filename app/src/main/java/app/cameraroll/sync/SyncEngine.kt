package app.cameraroll.sync

import android.content.Context
import app.cameraroll.R
import app.cameraroll.data.CloudConfig
import app.cameraroll.data.CloudSettings
import app.cameraroll.data.PhotoRepository
import app.cameraroll.data.db.PhotoEntity
import app.cameraroll.webdav.DavClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncResult(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val errors: List<String> = emptyList(),
) {
    fun summary(context: Context): String =
        context.getString(R.string.sync_short_summary, pushed, pulled) +
            if (errors.isNotEmpty()) context.getString(R.string.sync_errors, errors.size) else ""
}

/**
 * Sincronización bidireccional con Nextcloud/OpenCloud:
 *  - PUSH: fotos locales aún no presentes en la nube → PUT a basePath.
 *  - PULL: ficheros imagen/vídeo de basePath (recursivo) no presentes en la
 *    biblioteca → se descargan e importan.
 */
class SyncEngine(private val context: Context) {

    private val repo = PhotoRepository(context)

    private val davDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val config = CloudSettings.get(context)
        if (!config.isConfigured) return@withContext SyncResult()

        val dav = DavClient(config, context)
        dav.mkdirs(config.basePath)
        val dao = repo.db.photoDao()

        var pushed = 0
        var pulled = 0
        val errors = mutableListOf<String>()

        // PUSH
        for (photo in dao.pendingCloudUpload(200)) {
            try {
                val remote = uniqueRemote(dav, config, remoteName(config, photo))
                dav.upload(remote, File(photo.localPath), photo.mime)
                dao.setNcPath(photo.id, "${config.basePath.trimEnd('/')}/$remote")
                pushed++
            } catch (e: Exception) {
                errors.add("↑ ${photo.filename}: ${e.message}")
            }
        }

        // PULL
        try {
            val known = dao.syncedPaths().toSet()
            val entries = dav.listDeep(config.basePath).filter { e ->
                !e.isDir && isMedia(e.contentType, e.href) &&
                    "${config.basePath.trimEnd('/')}/${e.href.trim('/')}" !in known
            }
            for (entry in entries.take(200)) {
                try {
                    pulled += importRemote(config, dav, entry.href)
                } catch (e: Exception) {
                    errors.add("↓ ${entry.href}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("Listado remoto: ${e.message}")
        }

        val result = SyncResult(pushed, pulled, errors)
        CloudSettings.recordSync(context, result.summary(context))
        result
    }

    private fun isMedia(contentType: String, path: String): Boolean {
        if (contentType.startsWith("image/") || contentType.startsWith("video/")) return true
        val ext = path.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in setOf(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif",
            "mp4", "mov", "webm", "mkv", "m4v", "3gp",
        )
    }

    private fun remoteName(config: CloudConfig, photo: PhotoEntity): String {
        // organizar por año/mes como el backup de móvil clásico
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = photo.takenAt }
        val year = cal.get(java.util.Calendar.YEAR)
        val month = String.format(Locale.US, "%02d", cal.get(java.util.Calendar.MONTH) + 1)
        return "$year/$month/${photo.filename.replace(Regex("[/\\\\]"), "_")}"
    }

    private suspend fun uniqueRemote(
        dav: DavClient,
        config: CloudConfig,
        relPath: String,
    ): String {
        val dir = relPath.substringBeforeLast('/', "")
        if (dir.isNotEmpty()) dav.mkdirs("${config.basePath.trimEnd('/')}/$dir")
        return relPath
    }

    private suspend fun importRemote(
        config: CloudConfig,
        dav: DavClient,
        relPath: String,
    ): Int {
        val base = relPath.substringAfterLast('/')
        val stamp = "${System.currentTimeMillis()}-${(1000..9999).random()}"
        val dest = File(repo.photosDir, "$stamp-${base.replace(Regex("[/\\\\]"), "_")}")
        dav.download(relPath, dest)

        val mime = destMime(dest.name)
        val isVideo = mime.startsWith("video/")
        val takenAt = System.currentTimeMillis()
        val entity = PhotoEntity(
            filename = base,
            mime = mime,
            isVideo = isVideo,
            size = dest.length(),
            takenAt = takenAt,
            localPath = dest.absolutePath,
            ncPath = "${config.basePath.trimEnd('/')}/${relPath.trim('/')}",
        )
        val id = repo.db.photoDao().insert(entity)
        if (!isVideo) {
            repo.decodeThumb(dest.absolutePath)?.let { bmp ->
                repo.saveThumb(bmp, "t$id")?.let { repo.db.photoDao().setThumb(id, it) }
            }
        }
        return 1
    }

    private fun destMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heif"
            "avif" -> "image/avif"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "3gp" -> "video/3gpp"
            else -> "application/octet-stream"
        }
    }

    /** Borra en la nube si la foto estaba sincronizada (mejor esfuerzo). */
    suspend fun deleteRemoteIfSynced(ncPath: String?) = withContext(Dispatchers.IO) {
        if (ncPath == null) return@withContext
        val config = CloudSettings.get(context)
        if (!config.isConfigured) return@withContext
        try {
            DavClient(config, context).delete(ncPath)
        } catch (_: Exception) {
        }
    }

    suspend fun testConnection(config: CloudConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dav = DavClient(config, context)
            dav.ping()
            dav.mkdirs(config.basePath)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
