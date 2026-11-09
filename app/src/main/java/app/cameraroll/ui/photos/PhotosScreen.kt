package app.cameraroll.ui.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.cameraroll.data.db.PhotoEntity
import app.cameraroll.ui.PhotosViewModel
import app.cameraroll.ui.components.EmptyState
import app.cameraroll.ui.components.PhotoTimelineGrid
import app.cameraroll.ui.components.SelectionTopBar
import java.io.File
import java.util.Calendar

data class Memory(val yearsAgo: Int, val label: String, val photos: List<PhotoEntity>)

fun computeMemories(photos: List<PhotoEntity>): List<Memory> {
    val now = Calendar.getInstance()
    val memories = mutableListOf<Memory>()
    for (yearsAgo in 1..8) {
        val matches = photos.filter { p ->
            val cal = Calendar.getInstance().apply { timeInMillis = p.takenAt }
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) - yearsAgo &&
                cal.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                kotlin.math.abs(cal.get(Calendar.DAY_OF_MONTH) - now.get(Calendar.DAY_OF_MONTH)) <= 4
        }
        if (matches.size >= 2) {
            memories.add(
                Memory(
                    yearsAgo,
                    if (yearsAgo == 1) "Hace 1 año" else "Hace $yearsAgo años",
                    matches.take(12),
                ),
            )
        }
        if (memories.size >= 4) break
    }
    return memories
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    vm: PhotosViewModel,
    onOpenPhoto: (photoId: Long, source: String) -> Unit,
    onImport: () -> Unit,
    onShare: (List<PhotoEntity>) -> Unit,
    onSettings: () -> Unit,
) {
    val photos by vm.photos.collectAsState()
    val cloud by vm.cloud.collectAsState()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    val memories = remember(photos) { computeMemories(photos) }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionTopBar(
                    count = selected.size,
                    onClear = { selected = emptySet() },
                ) {
                    IconButton(onClick = {
                        vm.setFavorite(selected.toList(), true); selected = emptySet()
                    }) { Icon(Icons.Default.Favorite, contentDescription = "Favorito") }
                    IconButton(onClick = { showAlbumPicker = true }) {
                        Icon(Icons.Default.Collections, contentDescription = "Añadir a álbum")
                    }
                    IconButton(onClick = {
                        onShare(photos.filter { selected.contains(it.id) }); selected = emptySet()
                    }) { Icon(Icons.Default.Share, contentDescription = "Compartir") }
                    IconButton(onClick = {
                        vm.trash(selected.toList()); selected = emptySet()
                    }) { Icon(Icons.Default.Delete, contentDescription = "Papelera") }
                }
            } else {
                TopAppBar(
                    title = { Text("CameraRoll", fontWeight = FontWeight.Medium) },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(
                                if (cloud.isConfigured) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = "Estado de la nube",
                                tint = if (cloud.isConfigured) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onImport) {
                Icon(Icons.Default.Add, contentDescription = "Añadir fotos")
            }
        },
    ) { padding ->
        if (photos.isEmpty()) {
            EmptyState(
                icon = {
                    Icon(
                        Icons.Default.Collections,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                title = "Tu biblioteca está vacía",
                body = "Pulsa + para importar fotos y vídeos de tu dispositivo.",
            )
        } else {
            PhotoTimelineGrid(
                photos = photos,
                selected = selected,
                selectionMode = selected.isNotEmpty(),
                onPhotoClick = { p ->
                    if (selected.isNotEmpty()) {
                        selected = if (selected.contains(p.id)) selected - p.id else selected + p.id
                    } else onOpenPhoto(p.id, "photos")
                },
                onPhotoLongClick = { p ->
                    selected = if (selected.contains(p.id)) selected - p.id else selected + p.id
                },
                modifier = Modifier.padding(padding),
                header = {
                    if (memories.isNotEmpty()) {
                        MemoriesRow(
                            memories = memories,
                            onOpen = { m -> onOpenPhoto(m.photos.first().id, "memory-${m.yearsAgo}") },
                        )
                    }
                },
            )
        }
    }

    if (showAlbumPicker) {
        AlbumPickerDialog(
            vm = vm,
            photoIds = selected.toList(),
            onDismiss = { showAlbumPicker = false; selected = emptySet() },
        )
    }
}

@Composable
fun MemoriesRow(memories: List<Memory>, onOpen: (Memory) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(memories, key = { it.yearsAgo }) { memory ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onOpen(memory) },
            ) {
                AsyncImage(
                    model = File(memory.photos.first().thumbPath ?: memory.photos.first().localPath),
                    contentDescription = memory.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                )
                Text(
                    memory.label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
