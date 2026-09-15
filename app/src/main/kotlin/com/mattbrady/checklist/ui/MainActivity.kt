package com.mattbrady.checklist.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mattbrady.checklist.BuildConfig
import com.mattbrady.checklist.ChecklistApp
import com.mattbrady.checklist.data.Prefs
import com.mattbrady.checklist.data.SyncResult
import com.mattbrady.checklist.data.local.NoteEntity
import com.mattbrady.checklist.update.UpdateChecker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ChecklistScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChecklistScreen() {
    val context = LocalContext.current
    val repo = remember { ChecklistApp.repository(context) }
    val scope = rememberCoroutineScope()

    val baseUrl by Prefs.baseUrl(context).collectAsState(initial = null)
    val token by Prefs.token(context).collectAsState(initial = null)
    val notes by repo.observeNotes().collectAsState(initial = emptyList())

    var showSettings by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }

    val configured = !baseUrl.isNullOrBlank() && !token.isNullOrBlank()

    LaunchedEffect(Unit) {
        updateInfo = UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
    }

    fun runSync() {
        scope.launch {
            syncing = true
            syncMessage = null
            syncMessage = when (val result = repo.syncNow()) {
                is SyncResult.Success -> "Synced"
                is SyncResult.NotConfigured -> "Add your Worker URL and token first"
                is SyncResult.Failed -> "Sync failed: ${result.message}"
            }
            syncing = false
        }
    }

    LaunchedEffect(configured) {
        if (configured) runSync()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Shows the actual installed build number, so it's always
                // possible to check at a glance whether an install/update
                // really took effect, instead of guessing.
                title = { Text("Checklist (build ${BuildConfig.VERSION_CODE})") },
                actions = {
                    IconButton(onClick = { runSync() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            if (configured) {
                FloatingActionButton(
                    onClick = { context.startActivity(Intent(context, CaptureActivity::class.java)) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add note")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            updateInfo?.let { info ->
                UpdateBanner(
                    info = info,
                    onUpdateClick = { downloadAndInstallUpdate(context, info.downloadUrl) },
                )
            }
            if (syncing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (!configured || showSettings) {
                var settingsSaving by remember { mutableStateOf(false) }
                var settingsError by remember { mutableStateOf<String?>(null) }
                SettingsForm(
                    initialUrl = baseUrl ?: "",
                    initialToken = token ?: "",
                    canDismiss = configured,
                    saving = settingsSaving,
                    errorMessage = settingsError,
                    onSave = { url, tok ->
                        scope.launch {
                            settingsSaving = true
                            settingsError = null
                            try {
                                Prefs.setBaseUrl(context, url.trim())
                                Prefs.setToken(context, tok.trim())
                                showSettings = false
                                runSync()
                            } catch (e: Exception) {
                                settingsError = "Couldn't save: ${e.message ?: e.javaClass.simpleName}"
                            } finally {
                                settingsSaving = false
                            }
                        }
                    },
                    onDismiss = { showSettings = false },
                )
            } else {
                syncMessage?.let {
                    Text(it, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                }
                NotesList(
                    notes = notes,
                    onToggle = { note, done -> scope.launch { repo.toggleDone(note.localId, done) } },
                    onDelete = { note -> scope.launch { repo.deleteNote(note.localId) } },
                )
            }
        }
    }
}

@Composable
private fun SettingsForm(
    initialUrl: String,
    initialToken: String,
    canDismiss: Boolean,
    saving: Boolean,
    errorMessage: String?,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var tok by remember { mutableStateOf(initialToken) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Connect to your Worker", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "From SETUP.md: the Worker URL you deployed, and the API_TOKEN you set.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Worker URL") },
            placeholder = { Text("https://checklist-api.<you>.workers.dev") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tok,
            onValueChange = { tok = it },
            label = { Text("API token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (canDismiss) {
                TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
            }
            Button(
                enabled = !saving,
                onClick = {
                    val trimmedUrl = url.trim()
                    val trimmedTok = tok.trim()
                    if (trimmedUrl.isNotEmpty() && trimmedTok.isNotEmpty()) {
                        onSave(trimmedUrl, trimmedTok)
                    }
                },
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun NotesList(
    notes: List<NoteEntity>,
    onToggle: (NoteEntity, Boolean) -> Unit,
    onDelete: (NoteEntity) -> Unit,
) {
    if (notes.isEmpty()) {
        Text(
            "Nothing yet. Tap + and type: Category Subcategory your note.",
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    val grouped = notes
        .groupBy { it.category.ifBlank { "Uncategorised" } }
        .toSortedMap(compareBy { it.lowercase() })

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        for ((category, categoryNotes) in grouped) {
            item {
                Text(
                    category,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            val bySubcategory = categoryNotes
                .groupBy { it.subcategory.ifBlank { "General" } }
                .toSortedMap(compareBy { it.lowercase() })

            for ((subcategory, subNotes) in bySubcategory) {
                item {
                    Text(
                        subcategory,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                items(subNotes.sortedByDescending { it.createdAt }) { note ->
                    NoteRow(note = note, onToggle = onToggle, onDelete = onDelete)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun NoteRow(
    note: NoteEntity,
    onToggle: (NoteEntity, Boolean) -> Unit,
    onDelete: (NoteEntity) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Checkbox(checked = note.done, onCheckedChange = { onToggle(note, it) })
        Text(
            text = note.body.ifBlank { "(no text)" },
            style = if (note.done) {
                MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            modifier = Modifier.padding(top = 12.dp).weight(1f),
        )
        IconButton(onClick = { onDelete(note) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun UpdateBanner(info: UpdateChecker.UpdateInfo, onUpdateClick: () -> Unit) {
    // Stacked (not side-by-side) so the button can never get squeezed off
    // screen by a long label, whatever width the phone has.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp),
    ) {
        Text(
            text = "Update available (${info.versionLabel})",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onUpdateClick, modifier = Modifier.fillMaxWidth()) {
            Text("Update")
        }
    }
}

/**
 * Downloads the new APK and, once it's done, opens Android's own install
 * screen for it (you still have to tap "Install" there yourself - Android
 * doesn't allow apps to install updates silently unless they came from the
 * Play Store). If "install unknown apps" hasn't been allowed for this app
 * yet, this sends you to the one settings screen to turn it on, then you
 * just tap Update again.
 */
private fun downloadAndInstallUpdate(context: Context, downloadUrl: String) {
    if (!context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ),
        )
        return
    }

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(Uri.parse(downloadUrl))
        .setTitle("Checklist update")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, "checklist-update.apk")
    val downloadId = downloadManager.enqueue(request)

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (finishedId != downloadId) return
            receiverContext.unregisterReceiver(this)

            val apkUri = downloadManager.getUriForDownloadedFile(finishedId) ?: return
            receiverContext.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
        }
    }

    ContextCompat.registerReceiver(
        context,
        receiver,
        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
}
