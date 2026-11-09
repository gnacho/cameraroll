package app.cameraroll.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "photos",
    indices = [Index("takenAt"), Index("trashedAt"), Index("isFavorite")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,
    val mime: String,
    val isVideo: Boolean = false,
    val size: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationSec: Int? = null,
    val takenAt: Long,
    /** ruta absoluta del archivo local */
    val localPath: String,
    /** ruta absoluta de la miniatura */
    val thumbPath: String? = null,
    val isFavorite: Boolean = false,
    val trashedAt: Long? = null,
    /** ruta en Nextcloud/OpenCloud cuando está sincronizada */
    val ncPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "album_photos",
    primaryKeys = ["albumId", "photoId"],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("photoId")],
)
data class AlbumPhotoCrossRef(
    val albumId: Long,
    val photoId: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

data class AlbumWithCover(
    @Embedded val album: AlbumEntity,
    val photoCount: Int,
    val coverThumbPath: String?,
    val coverPath: String?,
)

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE trashedAt IS NULL ORDER BY takenAt DESC, id DESC")
    fun timeline(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE trashedAt IS NULL AND isFavorite = 1 ORDER BY takenAt DESC")
    fun favorites(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    fun trash(): Flow<List<PhotoEntity>>

    @Query(
        "SELECT * FROM photos WHERE trashedAt IS NULL AND filename LIKE '%' || :q || '%' " +
            "AND (:kind IS NULL OR (isVideo = (:kind = 'video'))) ORDER BY takenAt DESC",
    )
    fun search(q: String, kind: String?): Flow<List<PhotoEntity>>

    @Query(
        "SELECT p.* FROM photos p INNER JOIN album_photos ap ON ap.photoId = p.id " +
            "WHERE ap.albumId = :albumId AND p.trashedAt IS NULL ORDER BY p.takenAt DESC",
    )
    fun albumPhotos(albumId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun byId(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE trashedAt IS NULL AND ncPath IS NULL LIMIT :limit")
    suspend fun pendingCloudUpload(limit: Int): List<PhotoEntity>

    @Query("SELECT ncPath FROM photos WHERE ncPath IS NOT NULL")
    suspend fun syncedPaths(): List<String>

    @Query("SELECT * FROM photos WHERE trashedAt IS NOT NULL AND trashedAt < :cutoff")
    suspend fun expiredTrash(cutoff: Long): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity): Long

    @Update
    suspend fun update(photo: PhotoEntity)

    @Query("UPDATE photos SET isFavorite = :fav WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, fav: Boolean)

    @Query("UPDATE photos SET trashedAt = :ts WHERE id IN (:ids)")
    suspend fun setTrashed(ids: List<Long>, ts: Long?)

    @Query("UPDATE photos SET ncPath = :ncPath WHERE id = :id")
    suspend fun setNcPath(id: Long, ncPath: String)

    @Query("UPDATE photos SET thumbPath = :thumb WHERE id = :id")
    suspend fun setThumb(id: Long, thumb: String)

    @Delete
    suspend fun delete(photos: List<PhotoEntity>)

    @Query("SELECT COUNT(*) FROM photos WHERE trashedAt IS NULL")
    fun countActive(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size),0) FROM photos WHERE trashedAt IS NULL")
    fun bytesActive(): Flow<Long>

    @Query("SELECT COUNT(*) FROM photos WHERE trashedAt IS NULL AND ncPath IS NOT NULL")
    fun countSynced(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE trashedAt IS NOT NULL")
    fun countTrash(): Flow<Int>
}

@Dao
interface AlbumDao {
    @Transaction
    @Query(
        "SELECT a.*, " +
            "(SELECT COUNT(*) FROM album_photos ap WHERE ap.albumId = a.id) AS photoCount, " +
            "(SELECT p.thumbPath FROM album_photos ap2 JOIN photos p ON p.id = ap2.photoId " +
            "  WHERE ap2.albumId = a.id AND p.trashedAt IS NULL ORDER BY p.takenAt DESC LIMIT 1) AS coverThumbPath, " +
            "(SELECT p.localPath FROM album_photos ap3 JOIN photos p ON p.id = ap3.photoId " +
            "  WHERE ap3.albumId = a.id AND p.trashedAt IS NULL ORDER BY p.takenAt DESC LIMIT 1) AS coverPath " +
            "FROM albums a ORDER BY a.createdAt DESC",
    )
    fun albumsWithCover(): Flow<List<AlbumWithCover>>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun byId(id: Long): Flow<AlbumEntity?>

    @Insert
    suspend fun insert(album: AlbumEntity): Long

    @Query("UPDATE albums SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Delete
    suspend fun delete(album: AlbumEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPhotos(refs: List<AlbumPhotoCrossRef>)

    @Query("DELETE FROM album_photos WHERE albumId = :albumId AND photoId IN (:photoIds)")
    suspend fun removePhotos(albumId: Long, photoIds: List<Long>)
}

@Database(
    entities = [PhotoEntity::class, AlbumEntity::class, AlbumPhotoCrossRef::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun albumDao(): AlbumDao
}
