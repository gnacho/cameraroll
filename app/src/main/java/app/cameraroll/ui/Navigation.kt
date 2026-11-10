package app.cameraroll.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import app.cameraroll.R
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
import kotlinx.coroutines.launch

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

private data class NavDestination(val route: String, val label: Int, val icon: ImageVector)

private val navDestinations = listOf(
    NavDestination(Routes.PHOTOS, R.string.nav_photos, Icons.Default.Image),
    NavDestination(Routes.SEARCH, R.string.nav_search, Icons.Default.Search),
    NavDestination(Routes.ALBUMS, R.string.nav_albums, Icons.Default.PhotoAlbum),
    NavDestination(Routes.FAVORITES, R.string.nav_favorites, Icons.Default.Favorite),
    NavDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
)

private val topLevelRoutes = setOf(
    Routes.PHOTOS, Routes.SEARCH, Routes.ALBUMS, Routes.FAVORITES, Routes.SETTINGS,
)

@Composable
fun CameraRollRoot(vm: PhotosViewModel) {
    CameraRollTheme {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val showMessage: (String) -> Unit = { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
        val expanded = LocalConfiguration.current.screenWidthDp >= 600

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
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_photos_title)))
            }
        }

        val showNav = currentRoute in topLevelRoutes

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showNav && !expanded) {
                    AppNavigationBar(currentRoute = currentRoute, onNavigate = nav::navigateTop)
                }
            },
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if (showNav && expanded) {
                    AppNavigationRail(currentRoute = currentRoute, onNavigate = nav::navigateTop)
                }
                NavHost(
                    navController = nav,
                    startDestination = Routes.PHOTOS,
                    modifier = Modifier.weight(1f),
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
                        SettingsScreen(vm, onShowMessage = showMessage)
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
                        ViewerHost(
                            vm = vm,
                            photoId = photoId,
                            source = source,
                            onBack = { nav.popBackStack() },
                            onShare = share,
                            onShowMessage = showMessage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavigationBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar {
        navDestinations.forEach { d ->
            NavigationBarItem(
                selected = currentRoute == d.route,
                onClick = { onNavigate(d.route) },
                icon = { Icon(d.icon, contentDescription = null) },
                label = { Text(stringResource(d.label)) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        navDestinations.forEach { d ->
            NavigationRailItem(
                selected = currentRoute == d.route,
                onClick = { onNavigate(d.route) },
                icon = { Icon(d.icon, contentDescription = null) },
                label = { Text(stringResource(d.label)) },
            )
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
    onShowMessage: (String) -> Unit,
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
            computeMemories(photos, LocalContext.current.resources).firstOrNull { it.yearsAgo == yearsAgo }?.photos.orEmpty() to false
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
        onShowMessage = onShowMessage,
    )
}

private fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(Routes.PHOTOS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
