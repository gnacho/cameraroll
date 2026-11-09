package app.cameraroll.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import app.cameraroll.data.db.AlbumEntity
import app.cameraroll.data.db.AlbumPhotoCrossRef
import app.cameraroll.data.db.AppDatabase
import app.cameraroll.data.db.PhotoEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val THUMB_SIZE = 512
private const val TRASH_TTL_MS = 30L * 24 * 60 * 60 * 1000

class PhotoRepository(private val context: Context) {

    val db: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "cameraroll.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val photosDir: File get() = File(context.filesDir, "photos").apply { mkdirs() }
    val thumbsDir: File get() = File(context.filesDir, "thumbs").apply { mkdirs() }

    // ── importación ──────────────────────────────────────────────────────────

    suspend fun importUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var imported = 0
        for (uri in uris) {
            try {
                if (importOne(uri)) imported++
            } catch (_: Exception) {
                // archivo ilegible → se omite
            }
        }
        imported
    }

    private suspend fun importOne(uri: Uri): Boolean {
        val resolver = context.contentResolver
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
            ?: guessMime(uri.toString())
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) return false

        val name = runCatching { queryDisplayName(uri) }.getOrNull()
            ?: "foto-${System.currentTimeMillis()}"
        val safeName = name.replace(Regex("[/\\\\]"), "_")
        val stamp = "${System.currentTimeMillis()}-${(1000..9999).random()}"
        val dest = File(photosDir, "$stamp-$safeName")

        try {
            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
        } catch (_: Exception) {
            runCatching { dest.delete() }
            return false
        }

        val isVideo = mime.startsWith("video/")
        var width = 0
        var height = 0
        var duration: Int? = null
        var takenAt = runCatching { dest.lastModified() }.getOrDefault(System.currentTimeMillis())
        var thumbPath: String? = null

        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(dest.absolutePath)
                val frame = runCatching { retriever.getFrameAtTime(1_000_000) }.getOrNull()
                if (frame != null) thumbPath = saveThumb(frame, stamp)
                width = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                }.getOrNull() ?: 0
                height = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                }.getOrNull() ?: 0
                val durMs = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                }.getOrNull() ?: 0L
                duration = (durMs / 1000L).toInt()
            } catch (_: Exception) {
            } finally {
                runCatching { retriever.release() }
            }
        } else {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(dest.absolutePath, opts)
                width = opts.outWidth.coerceAtLeast(0)
                height = opts.outHeight.coerceAtLeast(0)
            }
            runCatching {
                val exif = ExifInterface(dest)
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                raw?.let { parseExifDate(it) }?.let { takenAt = it }
            }
            decodeThumb(dest.absolutePath)?.let { thumbPath = saveThumb(it, stamp) }
        }

        return try {
            db.photoDao().insert(
                PhotoEntity(
                    filename = name,
                    mime = mime,
                    isVideo = isVideo,
                    size = runCatching { dest.length() }.getOrDefault(0L),
                    width = width,
                    height = height,
                    durationSec = duration,
                    takenAt = takenAt,
                    localPath = dest.absolutePath,
                    thumbPath = thumbPath,
                ),
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun decodeThumb(path: String): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (opts.outWidth / (sample * 2) >= THUMB_SIZE) sample *= 2
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, decode)
    }.getOrNull()

    fun saveThumb(bitmap: Bitmap, stamp: String): String? = runCatching {
        val f = File(thumbsDir, "$stamp.webp")
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP, 82, it) }
        f.absolutePath
    }.getOrNull()

    suspend fun ensureThumb(photo: PhotoEntity): PhotoEntity = withContext(Dispatchers.IO) {
        if (photo.thumbPath != null || photo.isVideo) return@withContext photo
        decodeThumb(photo.localPath)?.let { bmp ->
            saveThumb(bmp, "t${photo.id}")?.let { path ->
                db.photoDao().setThumb(photo.id, path)
                return@withContext photo.copy(thumbPath = path)
            }
        }
        photo
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "application/octet-stream"
        }
    }

    private fun parseExifDate(raw: String): Long? = try {
        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)?.time
    } catch (_: Exception) {
        null
    }

    // ── acciones ─────────────────────────────────────────────────────────────

    suspend fun setFavorite(ids: List<Long>, fav: Boolean) = db.photoDao().setFavorite(ids, fav)
    suspend fun trash(ids: List<Long>) =
        db.photoDao().setTrashed(ids, System.currentTimeMillis())
    suspend fun restore(ids: List<Long>) = db.photoDao().setTrashed(ids, null)

    /** Borrado definitivo: archivos locales + fila. La nube la gestiona SyncEngine. */
    suspend fun deleteForever(ids: List<Long>): List<PhotoEntity> = withContext(Dispatchers.IO) {
        val dao = db.photoDao()
        val photos = ids.mapNotNull { dao.byId(it) }
        for (p in photos) {
            runCatching { File(p.localPath).delete() }
            p.thumbPath?.let { runCatching { File(it).delete() } }
        }
        dao.delete(photos)
        photos
    }

    suspend fun purgeExpiredTrash() {
        val cutoff = System.currentTimeMillis() - TRASH_TTL_MS
        val expired = runCatching { db.photoDao().expiredTrash(cutoff) }.getOrDefault(emptyList())
        if (expired.isNotEmpty()) deleteForever(expired.map { it.id })
    }

    // ── álbumes ──────────────────────────────────────────────────────────────

    suspend fun createAlbum(name: String): Long = db.albumDao().insert(AlbumEntity(name = name))
    suspend fun renameAlbum(id: Long, name: String) = db.albumDao().rename(id, name)
    suspend fun deleteAlbum(album: AlbumEntity) = db.albumDao().delete(album)
    suspend fun addToAlbum(albumId: Long, photoIds: List<Long>) =
        db.albumDao().addPhotos(photoIds.map { AlbumPhotoCrossRef(albumId, it) })
    suspend fun removeFromAlbum(albumId: Long, photoIds: List<Long>) =
        db.albumDao().removePhotos(albumId, photoIds)
}
