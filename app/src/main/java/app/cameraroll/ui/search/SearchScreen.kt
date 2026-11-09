package app.cameraroll.ui.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cameraroll.R
import app.cameraroll.ui.PhotosViewModel
import app.cameraroll.ui.components.EmptyState
import app.cameraroll.ui.components.PhotoTimelineGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: PhotosViewModel, onOpenPhoto: (Long, String) -> Unit) {
    val results by vm.searchResults.collectAsState()
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    androidx.compose.runtime.LaunchedEffect(query, kind) {
        vm.setSearch(query, kind)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(Modifier.padding(padding)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                FilterChip(
                    selected = kind == null,
                    onClick = { kind = null },
                    label = { Text(stringResource(R.string.filter_all)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = kind == "image",
                    onClick = { kind = "image" },
                    label = { Text(stringResource(R.string.filter_photos)) },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = kind == "video",
                    onClick = { kind = "video" },
                    label = { Text(stringResource(R.string.filter_videos)) },
                    leadingIcon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                )
            }
            if (results.isEmpty()) {
                EmptyState(
                    icon = {
                        Icon(
                            Icons.Default.Search, contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    title = if (query.isBlank()) stringResource(R.string.search_empty_title) else stringResource(R.string.search_no_results_title),
                    body = if (query.isBlank()) stringResource(R.string.search_empty_body) else stringResource(R.string.search_no_results_body, query),
                )
            } else {
                PhotoTimelineGrid(
                    photos = results,
                    selected = selected,
                    selectionMode = selected.isNotEmpty(),
                    onPhotoClick = { p ->
                        if (selected.isNotEmpty()) {
                            selected = if (selected.contains(p.id)) selected - p.id else selected + p.id
                        } else onOpenPhoto(p.id, "search")
                    },
                    onPhotoLongClick = { p ->
                        selected = if (selected.contains(p.id)) selected - p.id else selected + p.id
                    },
                )
            }
        }
    }
}
