package app.cameraroll.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.cameraroll.CameraRollApp
import app.cameraroll.R
import app.cameraroll.data.CloudProvider
import app.cameraroll.ui.PhotosViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: PhotosViewModel) {
    val cloud by vm.cloud.collectAsState()
    val stats by vm.stats.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val syncResult by vm.syncResult.collectAsState()
    val testResult by vm.cloudTestResult.collectAsState()
    val context = LocalContext.current

    var editing by remember { mutableStateOf(false) }

    // formulario
    var provider by remember(cloud.provider) { mutableStateOf(cloud.provider) }
    var url by remember(cloud.url) { mutableStateOf(cloud.url) }
    var username by remember(cloud.username) { mutableStateOf(cloud.username) }
    var password by remember(cloud.password) { mutableStateOf(cloud.password) }
    var basePath by remember(cloud.basePath) { mutableStateOf(cloud.basePath) }
    var autoUpload by remember(cloud.autoUpload) { mutableStateOf(cloud.autoUpload) }

    val showForm = editing || !cloud.isConfigured

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ── Tu nube ──────────────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cloud_section_title), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        stringResource(R.string.cloud_section_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )

                    if (showForm) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = provider == CloudProvider.NEXTCLOUD,
                                onClick = { provider = CloudProvider.NEXTCLOUD },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text(stringResource(R.string.provider_nextcloud)) }
                            SegmentedButton(
                                selected = provider == CloudProvider.OPENCLOUD,
                                onClick = { provider = CloudProvider.OPENCLOUD },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text(stringResource(R.string.provider_opencloud)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = url, onValueChange = { url = it },
                            label = { Text(stringResource(R.string.field_server_url)) },
                            placeholder = { Text(stringResource(R.string.field_server_url_placeholder)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = username, onValueChange = { username = it },
                            label = { Text(stringResource(R.string.field_username)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            label = { Text(stringResource(R.string.field_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = basePath, onValueChange = { basePath = it },
                            label = { Text(stringResource(R.string.field_remote_folder)) },
                            placeholder = { Text(stringResource(R.string.field_remote_folder_placeholder)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = autoUpload, onCheckedChange = { autoUpload = it })
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.auto_upload_label))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Button(
                                onClick = {
                                    vm.saveCloud(
                                        cloud.copy(
                                            provider = provider,
                                            url = url.trim(),
                                            username = username.trim(),
                                            password = password,
                                            basePath = basePath.trim().ifBlank { "/Fotos" },
                                            autoUpload = autoUpload,
                                        ),
                                    )
                                    editing = false
                                    Toast.makeText(context, context.getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
                                },
                                enabled = url.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                            ) { Text(stringResource(R.string.action_save)) }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    vm.testCloud(
                                        cloud.copy(
                                            provider = provider, url = url.trim(),
                                            username = username.trim(), password = password,
                                            basePath = basePath.trim().ifBlank { "/Fotos" },
                                        ),
                                    )
                                },
                            ) { Text(stringResource(R.string.action_test)) }
                            if (cloud.isConfigured) {
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { vm.clearCloud() }) {
                                    Text(stringResource(R.string.action_unlink), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        testResult?.let { r ->
                            Text(
                                if (r.isSuccess) stringResource(R.string.test_ok) else stringResource(R.string.test_fail, r.exceptionOrNull()?.message.orEmpty()),
                                color = if (r.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    } else {
                        SettingsRow(stringResource(R.string.row_provider), if (cloud.provider == CloudProvider.OPENCLOUD) stringResource(R.string.provider_opencloud) else stringResource(R.string.provider_nextcloud))
                        SettingsRow(stringResource(R.string.row_server), cloud.url)
                        SettingsRow(stringResource(R.string.field_username), cloud.username)
                        SettingsRow(stringResource(R.string.row_folder), cloud.basePath)
                        SettingsRow(
                            stringResource(R.string.row_last_sync),
                            if (cloud.lastSyncAt > 0)
                                SimpleDateFormat(stringResource(R.string.date_viewer), Locale.getDefault()).format(Date(cloud.lastSyncAt))
                            else stringResource(R.string.last_sync_never),
                        )
                        if (cloud.lastSyncSummary.isNotBlank()) {
                            SettingsRow(stringResource(R.string.row_result), cloud.lastSyncSummary)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Button(onClick = { vm.syncNow() }, enabled = !syncing) {
                                if (syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.width(16.dp).height(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.action_sync_now))
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { editing = true }) { Text(stringResource(R.string.action_edit)) }
                        }
                        syncResult?.let { r ->
                            Text(
                                stringResource(R.string.sync_summary, r.pushed, r.pulled) +
                                    if (r.errors.isNotEmpty()) stringResource(R.string.sync_errors, r.errors.size) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Almacenamiento ───────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.storage_section_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.storage_stats, stats.total, formatBytes(stats.bytes)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.storage_cloud_stats, stats.synced, stats.trash),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Informe de fallos ────────────────────────────────────────────
            CrashReportCard()

            // ── Acerca de ────────────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.about_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val i = (kotlin.math.ln(bytes.toDouble()) / kotlin.math.ln(1024.0)).toInt().coerceAtMost(units.size - 1)
    return "%.1f %s".format(bytes / Math.pow(1024.0, i.toDouble()), units[i])
}


@Composable
private fun CrashReportCard() {
    val context = LocalContext.current
    val crash = remember { CameraRollApp.lastCrash(context) } ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.crash_section_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.crash_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                crash.take(1200),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { CameraRollApp.copyCrash(context) }) {
                    Text(stringResource(R.string.crash_copy))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { CameraRollApp.clearCrash(context) }) {
                    Text(stringResource(R.string.crash_dismiss))
                }
            }
        }
    }
}
