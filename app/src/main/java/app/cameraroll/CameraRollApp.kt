package app.cameraroll

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import app.cameraroll.data.CloudSettings
import app.cameraroll.data.PhotoRepository
import app.cameraroll.sync.SyncWorker
import java.io.PrintWriter
import java.io.StringWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CameraRollApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository: PhotoRepository by lazy { PhotoRepository(this) }

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        appScope.launch {
            runCatching { repository.purgeExpiredTrash() }
            val config = runCatching { CloudSettings.get(this@CameraRollApp) }.getOrNull()
            if (config != null && config.isConfigured && config.autoUpload) {
                runCatching { SyncWorker.schedulePeriodic(this@CameraRollApp) }
            }
        }
    }

    /** Captura cualquier excepción no controlada y la deja legible en Ajustes. */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val report = buildString {
                    appendLine("CameraRoll - informe de fallo")
                    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MODEL}")
                    appendLine("Hilo: ${thread.name}")
                    appendLine()
                    append(sw.toString())
                }
                getSharedPreferences("crash", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last", report)
                    .apply()
            } catch (_: Exception) {
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    companion object {
        fun lastCrash(context: Context): String? =
            context.getSharedPreferences("crash", Context.MODE_PRIVATE).getString("last", null)

        fun clearCrash(context: Context) {
            context.getSharedPreferences("crash", Context.MODE_PRIVATE)
                .edit().remove("last").apply()
        }

        fun copyCrash(context: Context) {
            val text = lastCrash(context) ?: return
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crash", text))
            Toast.makeText(context, "Informe copiado al portapapeles", Toast.LENGTH_SHORT).show()
        }
    }
}
