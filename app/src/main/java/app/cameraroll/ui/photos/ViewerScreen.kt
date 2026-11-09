package app.cameraroll.ui.photos

import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import app.cameraroll.data.db.PhotoEntity
import app.cameraroll.ui.PhotosViewModel
import app.cameraroll.ui.components.formatDuration
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    vm: PhotosViewModel,
    photos: List<PhotoEntity>,
    startPhotoId: Long,
    isTrash: Boolean,
    onBack: () -> Unit,
    onShare: (List<PhotoEntity>) -> Unit,
) {
    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
        }
        return
    }

    val startIndex = photos.indexOfFirst { it.id == startPhotoId }.coerceIn(0, photos.size - 1)
    val pagerState = rememberPagerState(initialPage = startIndex) { photos.size }
    var showInfo by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val current: PhotoEntity = photos.getOrNull(pagerState.currentPage) ?: photos.last()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
        ) { page ->
            val photo = photos[page]
            val isCurrent = page == pagerState.currentPage
            if (photo.isVideo) {
                VideoPage(photo)
            } else {
                ZoomablePhoto(
                    photo = photo,
                    active = isCurrent,
                    onZoomChange = { zoomed = it },
                    onTap = { controlsVisible = !controlsVisible },
                )
            }
        }

        if (controlsVisible) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(
                            current.filename,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Text(
                            SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es"))
                                .format(Date(current.takenAt)),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (!isTrash) {
                        IconButton(onClick = { vm.setFavorite(listOf(current.id), !current.isFavorite) }) {
                            Icon(
                                if (current.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (current.isFavorite) Color(0xFFFF5252) else Color.White,
                            )
                        }
                        IconButton(onClick = { onShare(listOf(current)) }) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir", tint = Color.White)
                        }
                        IconButton(onClick = {
                            vm.trash(listOf(current.id))
                            Toast.makeText(context, "Movida a la papelera", Toast.LENGTH_SHORT).show()
                            if (photos.size <= 1) onBack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Papelera", tint = Color.White)
                        }
                    } else {
                        IconButton(onClick = {
                            vm.restore(listOf(current.id))
                            Toast.makeText(context, "Restaurada", Toast.LENGTH_SHORT).show()
                            onBack()
                        }) {
                            Icon(Icons.Default.Restore, contentDescription = "Restaurar", tint = Color.White)
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Eliminar", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Información", tint = Color.White)
                    }
                }
            }
        }

        if (showInfo) {
            ModalBottomSheet(onDismissRequest = { showInfo = false }) {
                Column(
                    Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text("Información", style = MaterialTheme.typography.titleMedium)
                    InfoRow("Nombre", current.filename)
                    InfoRow(
                        "Fecha",
                        SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale("es"))
                            .format(Date(current.takenAt)),
                    )
                    InfoRow("Tamaño", formatBytes(current.size))
                    if (current.width > 0) InfoRow("Dimensiones", "${current.width} × ${current.height}")
                    current.durationSec?.let { InfoRow("Duración", formatDuration(it)) }
                    InfoRow("Tipo", current.mime)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Copia en tu nube", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (current.ncPath != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                Text("Sincronizada")
                            }
                        } else {
                            Text("No")
                        }
                    }
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("¿Eliminar definitivamente?") },
                text = { Text("Se borrará para siempre de la biblioteca y de tu nube.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        vm.deleteForever(listOf(current.id))
                        onBack()
                    }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
                },
            )
        }
    }
}

/** Foto con zoom de pinza (hasta 5×), paneo cuando hay zoom y doble toque. */
@Composable
private fun ZoomablePhoto(
    photo: PhotoEntity,
    active: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
            onZoomChange(false)
        }
    }

    val state = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        offset = if (newScale > 1f) offset + panChange else Offset.Zero
        onZoomChange(newScale > 1.02f)
    }

    Box(
        Modifier
            .fillMaxSize()
            .transformable(state)
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChange(false)
                        } else {
                            scale = 2.5f
                            onZoomChange(true)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(photo.localPath),
            contentDescription = photo.filename,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}

/** Vídeo con controles del sistema y orientación bloqueada para evitar recreación. */
@Composable
private fun VideoPage(photo: PhotoEntity) {
    val activity = LocalContext.current as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(photo.id) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose {
            activity?.requestedOrientation =
                previous ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                val controller = MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoPath(photo.localPath)
                setOnPreparedListener { start() }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val i = (kotlin.math.ln(bytes.toDouble()) / kotlin.math.ln(1024.0)).toInt()
        .coerceAtMost(units.size - 1)
    val v = bytes / Math.pow(1024.0, i.toDouble())
    return "%.1f %s".format(v, units[i])
}
