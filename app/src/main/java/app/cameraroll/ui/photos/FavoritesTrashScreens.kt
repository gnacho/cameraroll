package app.cameraroll.ui.photos

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cameraroll.ui.PhotosViewModel
import app.cameraroll.ui.components.EmptyState
import app.cameraroll.ui.components.PhotoTimelineGrid
import app.cameraroll.ui.components.SelectionTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: PhotosViewModel, onOpenPhoto: (Long, String) -> Unit) {
    val photos by vm.favorites.collectAsState()
    var selected by remember { mutableStateOf(setOf<Long>()) }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionTopBar(count = selected.size, onClear = { selected = emptySet() }) {
                    IconButton(onClick = {
                        vm.setFavorite(selected.toList(), false); selected = emptySet()
                    }) { Icon(Icons.Default.FavoriteBorder, contentDescription = "Quitar favorito") }
                    IconButton(onClick = {
                        vm.trash(selected.toList()); selected = emptySet()
                    }) { Icon(Icons.Default.Delete, contentDescription = "Papelera") }
                }
            } else {
                TopAppBar(title = { Text("Favoritos") })
            }
        },
    ) { padding ->
        if (photos.isEmpty()) {
            EmptyState(
                icon = {
                    Icon(
                        Icons.Default.Favorite, contentDescription = null,
                        modifier = Modifier.padding(0.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                title = "Sin favoritos",
                body = "Marca fotos con el corazón para verlas aquí.",
            )
        } else {
            PhotoTimelineGrid(
                photos = photos,
                selected = selected,
                selectionMode = selected.isNotEmpty(),
                onPhotoClick = { p ->
                    if (selected.isNotEmpty()) {
                        selected = if (selected.contains(p.id)) selected - p.id else selected + p.id
                    } else onOpenPhoto(p.id, "favorites")
                },
                onPhotoLongClick = { p ->
                    selected = if (selected.contains(p.id)) selected - p.id else selected + p.id
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: PhotosViewModel, onOpenPhoto: (Long, String) -> Unit) {
    val photos by vm.trash.collectAsState()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmEmpty by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionTopBar(count = selected.size, onClear = { selected = emptySet() }) {
                    IconButton(onClick = {
                        vm.restore(selected.toList()); selected = emptySet()
                    }) { Icon(Icons.Default.Restore, contentDescription = "Restaurar") }
                    IconButton(onClick = {
                        vm.deleteForever(selected.toList()); selected = emptySet()
                    }) { Icon(Icons.Default.DeleteForever, contentDescription = "Eliminar") }
                }
            } else {
                TopAppBar(
                    title = { Text("Papelera") },
                    actions = {
                        if (photos.isNotEmpty()) {
                            IconButton(onClick = { confirmEmpty = true }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Vaciar papelera")
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (photos.isEmpty()) {
            EmptyState(
                icon = {
                    Icon(
                        Icons.Default.Delete, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                title = "Papelera vacía",
                body = "Los elementos borrados se eliminan definitivamente a los 30 días.",
            )
        } else {
            PhotoTimelineGrid(
                photos = photos,
                selected = selected,
                selectionMode = selected.isNotEmpty(),
                onPhotoClick = { p ->
                    if (selected.isNotEmpty()) {
                        selected = if (selected.contains(p.id)) selected - p.id else selected + p.id
                    } else onOpenPhoto(p.id, "trash")
                },
                onPhotoLongClick = { p ->
                    selected = if (selected.contains(p.id)) selected - p.id else selected + p.id
                },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (confirmEmpty) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("¿Vaciar la papelera?") },
            text = { Text("Se eliminarán definitivamente ${photos.size} elementos.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmEmpty = false
                    vm.emptyTrash()
                }) { Text("Vaciar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmEmpty = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
