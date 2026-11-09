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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.cameraroll.R
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
                title = { Text(stringResource(R.string.albums_title)) },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_create_album))
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
                title = stringResource(R.string.albums_empty_title),
                body = stringResource(R.string.albums_empty_body),
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
                            pluralStringResource(R.plurals.album_item_count, a.photoCount, a.photoCount),
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
            title = { Text(stringResource(R.string.new_album_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.new_album_name_placeholder)) },
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
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.action_cancel)) } },
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
                    }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove_from_album)) }
                    IconButton(onClick = {
                        vm.trash(selected.toList()); selected = emptySet()
                    }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_trash)) }
                }
            } else {
                TopAppBar(
                    title = { Text(album?.name ?: stringResource(R.string.album_default_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            newName = album?.name ?: ""
                            renaming = true
                        }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_rename)) }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete_album))
                        }
                        IconButton(onClick = onAddPhotos) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_photos))
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
                title = stringResource(R.string.album_empty_title),
                body = stringResource(R.string.album_empty_body),
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
            title = { Text(stringResource(R.string.rename_album_title)) },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newName.trim()
                    if (n.isNotEmpty()) vm.renameAlbum(albumId, n)
                    renaming = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_album_title, album?.name.orEmpty())) },
            text = { Text(stringResource(R.string.delete_album_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    album?.let { vm.deleteAlbum(it) }
                    onBack()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
