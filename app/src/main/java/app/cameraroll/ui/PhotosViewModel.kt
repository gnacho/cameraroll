package app.cameraroll.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.cameraroll.CameraRollApp
import app.cameraroll.data.CloudConfig
import app.cameraroll.data.CloudSettings
import app.cameraroll.data.PhotoRepository
import app.cameraroll.data.db.AlbumEntity
import app.cameraroll.data.db.PhotoEntity
import app.cameraroll.sync.SyncEngine
import app.cameraroll.sync.SyncResult
import app.cameraroll.sync.SyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryStats(
    val total: Int = 0,
    val bytes: Long = 0,
    val synced: Int = 0,
    val trash: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PhotosViewModel(private val app: CameraRollApp) : ViewModel() {

    private val repo: PhotoRepository get() = app.repository

    val photos: StateFlow<List<PhotoEntity>> =
        repo.db.photoDao().timeline()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<PhotoEntity>> =
        repo.db.photoDao().favorites()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trash: StateFlow<List<PhotoEntity>> =
        repo.db.photoDao().trash()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums = repo.db.albumDao().albumsWithCover()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cloud: StateFlow<CloudConfig> = CloudSettings.flow(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CloudConfig())

    val stats: StateFlow<LibraryStats> = kotlinx.coroutines.flow.combine(
        repo.db.photoDao().countActive(),
        repo.db.photoDao().bytesActive(),
        repo.db.photoDao().countSynced(),
        repo.db.photoDao().countTrash(),
    ) { total, bytes, synced, trashCount ->
        LibraryStats(total, bytes, synced, trashCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryStats())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _searchKind = MutableStateFlow<String?>(null)

    val searchResults: StateFlow<List<PhotoEntity>> =
        kotlinx.coroutines.flow.combine(_searchQuery, _searchKind) { q, k -> q to k }
            .flatMapLatest { (q, k) -> repo.db.photoDao().search(q, k) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val importing = MutableStateFlow(false)
    val syncing = MutableStateFlow(false)
    val syncResult = MutableStateFlow<SyncResult?>(null)
    val cloudTestResult = MutableStateFlow<Result<Unit>?>(null)

    fun setSearch(q: String, kind: String?) {
        _searchQuery.value = q
        _searchKind.value = kind
    }

    fun import(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            try {
                repo.importUris(uris)
                val config = cloud.value
                if (config.isConfigured && config.autoUpload) {
                    SyncWorker.syncNow(app)
                }
            } finally {
                importing.value = false
            }
        }
    }

    fun setFavorite(ids: List<Long>, fav: Boolean) =
        viewModelScope.launch { repo.setFavorite(ids, fav) }

    fun trash(ids: List<Long>) = viewModelScope.launch { repo.trash(ids) }
    fun restore(ids: List<Long>) = viewModelScope.launch { repo.restore(ids) }

    fun deleteForever(ids: List<Long>) = viewModelScope.launch {
        val deleted = repo.deleteForever(ids)
        for (p in deleted) SyncEngine(app).deleteRemoteIfSynced(p.ncPath)
    }

    fun emptyTrash() = viewModelScope.launch {
        val ids = trash.value.map { it.id }
        if (ids.isEmpty()) return@launch
        val deleted = repo.deleteForever(ids)
        for (p in deleted) SyncEngine(app).deleteRemoteIfSynced(p.ncPath)
    }

    fun albumPhotos(albumId: Long) =
        repo.db.photoDao().albumPhotos(albumId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun album(id: Long) = repo.db.albumDao().byId(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun createAlbum(name: String, photoIds: List<Long> = emptyList()) =
        viewModelScope.launch {
            val id = repo.createAlbum(name)
            if (photoIds.isNotEmpty()) repo.addToAlbum(id, photoIds)
        }

    fun renameAlbum(id: Long, name: String) = viewModelScope.launch { repo.renameAlbum(id, name) }

    fun deleteAlbum(album: AlbumEntity) = viewModelScope.launch { repo.deleteAlbum(album) }

    fun addToAlbum(albumId: Long, photoIds: List<Long>) =
        viewModelScope.launch { repo.addToAlbum(albumId, photoIds) }

    fun removeFromAlbum(albumId: Long, photoIds: List<Long>) =
        viewModelScope.launch { repo.removeFromAlbum(albumId, photoIds) }

    fun saveCloud(config: CloudConfig) = viewModelScope.launch {
        CloudSettings.save(app, config)
        if (config.isConfigured && config.autoUpload) SyncWorker.schedulePeriodic(app)
    }

    fun clearCloud() = viewModelScope.launch {
        CloudSettings.clear(app)
        SyncWorker.cancelPeriodic(app)
    }

    fun testCloud(config: CloudConfig) = viewModelScope.launch {
        cloudTestResult.value = null
        cloudTestResult.value = SyncEngine(app).testConnection(config)
    }

    fun syncNow() = viewModelScope.launch {
        syncing.value = true
        try {
            syncResult.value = SyncEngine(app).sync()
        } finally {
            syncing.value = false
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CameraRollApp
                return PhotosViewModel(app) as T
            }
        }
    }
}
