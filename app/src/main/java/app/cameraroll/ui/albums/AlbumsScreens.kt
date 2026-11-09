package app.cameraroll.ui.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.cameraroll.ui.PhotosViewModel
import app.cameraroll.ui.components.EmptyState
import app.cameraroll.ui.components.PhotoTimelineGrid
import app.cameraroll.ui.components.SelectionTopBar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    vm: PhotosViewModel,
    onOpenAlbum: (Long) -> Unit,
) {
    val albums by vm.albums.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Álbumes") },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Crear álbum")
                    }
                },
            )
        },
    ) { padding ->
        if (albums.isEmpty()) {
            EmptyState(
                icon = {
                    Icon(
                        Icons.Default.PhotoAlbum, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                title = "Sin álbumes",
                body = "Crea álbumes para organizar tus fotos.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(albums, key = { it.album.id }) { a ->
                    Column(
                        Modifier.clickable { onOpenAlbum(a.album.id) },
                    ) {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            AsyncImage(
                                model = a.coverThumbPath?.let { File(it) } ?: a.coverPath?.let { File(it) },
                                contentDescription = a.album.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            )
                        }
                        Text(
                            a.album.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "${a.photoCount} elementos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Álbum nuevo") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Nombre del álbum") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = name.trim()
                        if (n.isNotEmpty()) {
                            vm.createAlbum(n)
                            name = ""
                            creating = false
                        }
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    vm: PhotosViewModel,
    albumId: Long,
    onBack: () -> Unit,
    onOpenPhoto: (Long, String) -> Unit,
    onAddPhotos: () -> Unit,
) {
    val album by vm.album(albumId).collectAsState()
    val photos by vm.albumPhotos(albumId).collectAsState()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionTopBar(count = selected.size, onClear = { selected = emptySet() }) {
                    IconButton(onClick = {
                        vm.removeFromAlbum(albumId, selected.toList()); selected = emptySet()
                    }) { Icon(Icons.Default.Close, contentDescription = "Quitar del álbum") }
                    IconButton(onClick = {
                        vm.trash(selected.toList()); selected = emptySet()
                    }) { Icon(Icons.Default.Delete, contentDescription = "Papelera") }
                }
            } else {
                TopAppBar(
                    title = { Text(album?.name ?: "Álbum") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            newName = album?.name ?: ""
                            renaming = true
                        }) { Icon(Icons.Default.Edit, contentDescription = "Renombrar") }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar álbum")
                        }
                        IconButton(onClick = onAddPhotos) {
                            Icon(Icons.Default.Add, contentDescription = "Añadir fotos")
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
                        Icons.Default.PhotoAlbum, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                title = "Álbum vacío",
                body = "Toca + para añadir fotos de tu biblioteca.",
            )
        } else {
            PhotoTimelineGrid(
                photos = photos,
                selected = selected,
                selectionMode = selected.isNotEmpty(),
                onPhotoClick = { p ->
                    if (selected.isNotEmpty()) {
                        selected = if (selected.contains(p.id)) selected - p.id else selected + p.id
                    } else onOpenPhoto(p.id, "album-$albumId")
                },
                onPhotoLongClick = { p ->
                    selected = if (selected.contains(p.id)) selected - p.id else selected + p.id
                },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renombrar álbum") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newName.trim()
                    if (n.isNotEmpty()) vm.renameAlbum(albumId, n)
                    renaming = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar «${album?.name}»?") },
            text = { Text("Las fotos seguirán en tu biblioteca.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    album?.let { vm.deleteAlbum(it) }
                    onBack()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
