package app.cameraroll.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.cameraroll.data.db.PhotoEntity
import app.cameraroll.ui.albums.AlbumDetailScreen
import app.cameraroll.ui.albums.AlbumsScreen
import app.cameraroll.ui.photos.FavoritesScreen
import app.cameraroll.ui.photos.PhotosScreen
import app.cameraroll.ui.photos.TrashScreen
import app.cameraroll.ui.photos.ViewerScreen
import app.cameraroll.ui.photos.computeMemories
import app.cameraroll.ui.search.SearchScreen
import app.cameraroll.ui.settings.SettingsScreen
import app.cameraroll.ui.theme.CameraRollTheme
import java.io.File

object Routes {
    const val PHOTOS = "photos"
    const val SEARCH = "search"
    const val ALBUMS = "albums"
    const val ALBUM_DETAIL = "album/{albumId}"
    const val FAVORITES = "favorites"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{photoId}/{source}"
}

@Composable
fun CameraRollRoot(vm: PhotosViewModel) {
    CameraRollTheme {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val context = LocalContext.current

        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50),
        ) { uris -> vm.import(uris) }

        val share: (List<PhotoEntity>) -> Unit = { list ->
            val uris = list.mapNotNull { p ->
                try {
                    FileProvider.getUriForFile(context, "app.cameraroll.fileprovider", File(p.localPath))
                } catch (_: Exception) {
                    null
                }
            }
            if (uris.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir fotos"))
            }
        }

        val showBottomBar = currentRoute in setOf(
            Routes.PHOTOS, Routes.SEARCH, Routes.ALBUMS, Routes.FAVORITES, Routes.SETTINGS,
        )

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == Routes.PHOTOS,
                            onClick = { nav.navigateTop(Routes.PHOTOS) },
                            icon = { Icon(Icons.Default.Image, contentDescription = null) },
                            label = { Text("Fotos") },
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.SEARCH,
                            onClick = { nav.navigateTop(Routes.SEARCH) },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { Text("Buscar") },
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.ALBUMS,
                            onClick = { nav.navigateTop(Routes.ALBUMS) },
                            icon = { Icon(Icons.Default.PhotoAlbum, contentDescription = null) },
                            label = { Text("Álbumes") },
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.FAVORITES,
                            onClick = { nav.navigateTop(Routes.FAVORITES) },
                            icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                            label = { Text("Favoritos") },
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.SETTINGS,
                            onClick = { nav.navigateTop(Routes.SETTINGS) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Ajustes") },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.PHOTOS,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.PHOTOS) {
                    PhotosScreen(
                        vm = vm,
                        onOpenPhoto = { id, source -> nav.navigate("viewer/$id/$source") },
                        onImport = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                        onShare = share,
                        onSettings = { nav.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(vm) { id, source -> nav.navigate("viewer/$id/$source") }
                }
                composable(Routes.ALBUMS) {
                    AlbumsScreen(vm) { id -> nav.navigate("album/$id") }
                }
                composable(
                    Routes.ALBUM_DETAIL,
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
                ) { entry ->
                    val albumId = entry.arguments?.getLong("albumId") ?: 0L
                    AlbumDetailScreen(
                        vm = vm,
                        albumId = albumId,
                        onBack = { nav.popBackStack() },
                        onOpenPhoto = { id, source -> nav.navigate("viewer/$id/$source") },
                        onAddPhotos = { nav.navigateTop(Routes.PHOTOS) },
                    )
                }
                composable(Routes.FAVORITES) {
                    FavoritesScreen(vm) { id, source -> nav.navigate("viewer/$id/$source") }
                }
                composable(Routes.TRASH) {
                    TrashScreen(vm) { id, source -> nav.navigate("viewer/$id/$source") }
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(vm)
                }
                composable(
                    Routes.VIEWER,
                    arguments = listOf(
                        navArgument("photoId") { type = NavType.LongType },
                        navArgument("source") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val photoId = entry.arguments?.getLong("photoId") ?: 0L
                    val source = entry.arguments?.getString("source") ?: "photos"
                    ViewerHost(vm, photoId, source, onBack = { nav.popBackStack() }, onShare = share)
                }
            }
        }
    }
}

/** Resuelve la lista de fotos del visor según el origen (timeline, favoritos…). */
@Composable
private fun ViewerHost(
    vm: PhotosViewModel,
    photoId: Long,
    source: String,
    onBack: () -> Unit,
    onShare: (List<PhotoEntity>) -> Unit,
) {
    val photos by vm.photos.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val trash by vm.trash.collectAsState()
    val search by vm.searchResults.collectAsState()

    val (list, isTrash) = when {
        source == "photos" -> photos to false
        source == "favorites" -> favorites to false
        source == "trash" -> trash to true
        source == "search" -> search to false
        source.startsWith("album-") -> {
            val albumId = source.removePrefix("album-").toLongOrNull() ?: 0L
            val albumPhotos by vm.albumPhotos(albumId).collectAsState()
            albumPhotos to false
        }
        source.startsWith("memory-") -> {
            val yearsAgo = source.removePrefix("memory-").toIntOrNull() ?: 0
            computeMemories(photos).firstOrNull { it.yearsAgo == yearsAgo }?.photos.orEmpty() to false
        }
        else -> photos to false
    }

    ViewerScreen(
        vm = vm,
        photos = list,
        startPhotoId = photoId,
        isTrash = isTrash,
        onBack = onBack,
        onShare = onShare,
    )
}

private fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(Routes.PHOTOS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
