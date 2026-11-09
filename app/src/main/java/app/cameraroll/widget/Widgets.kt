package app.cameraroll.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.cameraroll.R
import app.cameraroll.data.PhotoRepository
import app.cameraroll.data.db.PhotoEntity
import app.cameraroll.ui.MainActivity
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private fun openAppIntent(context: Context) =
    actionStartActivity(
        android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        },
    )

/** Widget "Recuerdos": tiras de fotos de este mismo día en años anteriores. */
class MemoriesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val photos = loadMemories(context)
        provideContent {
            GlanceTheme {
                MemoriesContent(photos)
            }
        }
    }

    private suspend fun loadMemories(context: Context): List<PhotoEntity> =
        withContext(Dispatchers.IO) {
            try {
                val repo = PhotoRepository(context)
                val all = repo.db.photoDao().timeline().first()
                val now = Calendar.getInstance()
                val matches = all.filter { p ->
                    val cal = Calendar.getInstance().apply { timeInMillis = p.takenAt }
                    cal.get(Calendar.YEAR) != now.get(Calendar.YEAR) &&
                        cal.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                        kotlin.math.abs(cal.get(Calendar.DAY_OF_MONTH) - now.get(Calendar.DAY_OF_MONTH)) <= 4
                }
                matches.ifEmpty { all }.take(6)
            } catch (_: Exception) {
                emptyList()
            }
        }
}

@Composable
private fun MemoriesContent(photos: List<PhotoEntity>) {
    val context = androidx.glance.LocalContext.current
    val openApp = openAppIntent(androidx.glance.LocalContext.current)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
            .clickable(openApp),
    ) {
        Text(
            text = context.getString(R.string.widget_memories_title),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))
        if (photos.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    context.getString(R.string.widget_memories_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        } else {
            Row(modifier = GlanceModifier.fillMaxSize()) {
                photos.forEach { photo ->
                    WidgetThumb(photo, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(6.dp))
                }
            }
        }
    }
}

@Composable
private fun WidgetThumb(path: String?, modifier: GlanceModifier) {
    val bitmap = path?.let { BitmapFactory.decodeFile(it) }
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxSize()
                .cornerRadius(12.dp),
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .cornerRadius(12.dp)
                .background(ColorProvider(Color(0xFFCCE8E1))),
        ) {}
    }
}

@Composable
private fun WidgetThumb(photo: PhotoEntity, modifier: GlanceModifier) {
    WidgetThumb(photo.thumbPath ?: photo.localPath, modifier)
}

class MemoriesWidgetReceiver : GlanceAppWidgetReceiver() {    override val glanceAppWidget: GlanceAppWidget = MemoriesWidget()
}

/** Widget "Foto": la foto favorita más reciente (o la última de la biblioteca). */
class PhotoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val photo = loadFeatured(context)
        provideContent {
            GlanceTheme {
                val openApp = openAppIntent(androidx.glance.LocalContext.current)
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(openApp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    WidgetThumb(photo?.thumbPath ?: photo?.localPath, GlanceModifier.fillMaxSize())
                    if (photo != null) {
                        Text(
                            text = photo.filename,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 12.sp,
                            ),
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ColorProvider(Color(0x66000000)))
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadFeatured(context: Context): PhotoEntity? =
        withContext(Dispatchers.IO) {
            try {
                val repo = PhotoRepository(context)
                val favs = repo.db.photoDao().favorites().first()
                favs.firstOrNull() ?: repo.db.photoDao().timeline().first().firstOrNull()
            } catch (_: Exception) {
                null
            }
        }
}

class PhotoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PhotoWidget()
}
