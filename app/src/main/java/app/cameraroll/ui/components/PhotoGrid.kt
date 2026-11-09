package app.cameraroll.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.cameraroll.data.db.PhotoEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayGroup(val key: String, val label: String, val photos: List<PhotoEntity>)

fun groupByDay(photos: List<PhotoEntity>): List<DayGroup> {
    val now = Calendar.getInstance()
    val today = now.get(Calendar.DAY_OF_YEAR) to now.get(Calendar.YEAR)
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.DAY_OF_YEAR) to now.get(Calendar.YEAR)

    val dayFmt = SimpleDateFormat("d 'de' MMMM", Locale("es"))
    val yearFmt = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es"))

    return photos.groupBy { p ->
        Calendar.getInstance().apply { timeInMillis = p.takenAt }
            .let { it.get(Calendar.DAY_OF_YEAR) to it.get(Calendar.YEAR) }
    }.toSortedMap(compareByDescending { (d, y) -> y * 1000 + d })
        .map { (day, items) ->
            val label = when (day) {
                today -> "Hoy"
                yesterday -> "Ayer"
                else -> {
                    val date = Date(items.first().takenAt)
                    if (day.second == Calendar.getInstance().get(Calendar.YEAR)) dayFmt.format(date)
                    else yearFmt.format(date)
                }
            }
            DayGroup("${day.second}-${day.first}", label, items)
        }
}

fun formatDuration(sec: Int?): String {
    if (sec == null || sec <= 0) return ""
    return "%d:%02d".format(sec / 60, sec % 60)
}

sealed interface GridItem {
    data class Header(val label: String) : GridItem
    data class Cell(val photo: PhotoEntity) : GridItem
}

@Composable
fun PhotoTimelineGrid(
    photos: List<PhotoEntity>,
    selected: Set<Long>,
    selectionMode: Boolean,
    onPhotoClick: (PhotoEntity) -> Unit,
    onPhotoLongClick: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val groups = remember(photos) { groupByDay(photos) }
    val gridItems = remember(groups) {
        buildList {
            for (g in groups) {
                add(GridItem.Header(g.label))
                for (p in g.photos) add(GridItem.Cell(p))
            }
        }
    }
    val state = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        state = state,
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        items(
            items = gridItems,
            key = { item ->
                when (item) {
                    is GridItem.Header -> "h-${item.label}"
                    is GridItem.Cell -> "p-${item.photo.id}"
                }
            },
            span = { item ->
                when (item) {
                    is GridItem.Header -> GridItemSpan(maxLineSpan)
                    is GridItem.Cell -> GridItemSpan(1)
                }
            },
        ) { item ->
            when (item) {
                is GridItem.Header -> Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 2.dp),
                )
                is GridItem.Cell -> PhotoCell(
                    photo = item.photo,
                    selected = selected.contains(item.photo.id),
                    selectionMode = selectionMode,
                    onClick = { onPhotoClick(item.photo) },
                    onLongClick = { onPhotoLongClick(item.photo) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCell(
    photo: PhotoEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = File(photo.thumbPath ?: photo.localPath),
            contentDescription = photo.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (photo.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = formatDuration(photo.durationSec),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (selectionMode || selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.3f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: @Composable () -> Unit, title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun SelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            androidx.compose.material3.IconButton(onClick = onClear) {
                Icon(Icons.Default.Check, contentDescription = "Cancelar selección")
            }
            Text(
                "$count seleccionados",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                actions()
            }
        }
    }
}
