package com.tonytron.gifttracker.ui.sheets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonytron.gifttracker.data.model.ColorMode
import com.tonytron.gifttracker.data.model.SyncStatus
import com.tonytron.gifttracker.data.model.ThemeSeed
import com.tonytron.gifttracker.data.remote.FirebaseSyncManager
import com.tonytron.gifttracker.ui.GiftTrackerViewModel
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    viewModel: GiftTrackerViewModel,
    onDismiss: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val cloudState by viewModel.cloudState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var snippetInput by remember { mutableStateOf("") }
    var webClientIdInput by remember(cloudState.webClientId) {
        mutableStateOf(cloudState.webClientId.orEmpty())
    }
    var configError by remember { mutableStateOf<String?>(null) }
    var showConfigEditor by remember { mutableStateOf(false) }
    var confirmResetOpen by remember { mutableStateOf(false) }

    // Android Storage Access Framework — Export JSON Backup to Drive or Downloads
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = viewModel.exportJsonBackup()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                viewModel.showMessage("Exported JSON backup!")
            }.onFailure {
                viewModel.showMessage("Could not write backup file")
            }
        }
    }

    // Android Storage Access Framework — Import JSON Backup from Drive or local storage
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (!raw.isNullOrBlank()) {
                    viewModel.importJsonBackup(raw)
                }
            }.onFailure {
                viewModel.showMessage("Could not read JSON file")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Settings, null, tint = MaterialTheme.colorScheme.primary)
                Text("Settings", style = MaterialTheme.typography.titleLarge)
            }

            // 1. Appearance (Wallpaper Dynamic Color + 4 Seeds + System/Dark/Light)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    }

                    // Connected ColorMode Toggle Group
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                    ) {
                        val modes = listOf(ColorMode.SYSTEM, ColorMode.DARK, ColorMode.LIGHT)
                        modes.forEachIndexed { idx, mode ->
                            val shapes = when (idx) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            FilledTonalToggleButton(
                                checked = prefs.colorMode == mode,
                                onCheckedChange = { viewModel.setColorMode(mode) },
                                modifier = Modifier.weight(1f),
                                shapes = shapes
                            ) {
                                Text(mode.label, fontSize = 12.sp)
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeSeed.entries.forEach { seed ->
                            val active = prefs.themeSeed == seed
                            Surface(
                                onClick = { viewModel.setThemeSeed(seed) },
                                shape = RoundedCornerShape(if (active) 99.dp else 14.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(seed.swatchHex)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (active) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        seed.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Cloud Sync (Firebase Firestore + Credential Manager Auth)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Cloud Sync", style = MaterialTheme.typography.titleMedium)
                        }

                        val statusLabel = when (cloudState.syncStatus) {
                            SyncStatus.SYNCED -> "● Synced"
                            SyncStatus.CONNECTING -> "Syncing..."
                            SyncStatus.SIGNED_OUT -> "Signed Out"
                            SyncStatus.ERROR -> "Sync Error"
                            SyncStatus.LOCAL_ONLY -> "Local Only"
                        }
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (cloudState.syncStatus == SyncStatus.SYNCED)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = statusLabel,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (cloudState.connectedProjectId != null) {
                        Text(
                            text = buildString {
                                append("Project: ${cloudState.connectedProjectId}")
                                if (cloudState.userLabel != null) {
                                    append(" • ${cloudState.userLabel}")
                                }
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (cloudState.userUid == null) {
                            if (!cloudState.isFromGoogleServicesResource && cloudState.webClientId.isNullOrBlank()) {
                                OutlinedTextField(
                                    value = webClientIdInput,
                                    onValueChange = { webClientIdInput = it },
                                    label = { Text("Google Web Client ID (for Google Sign-In)") },
                                    placeholder = { Text("123456-abc.apps.googleusercontent.com") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.signInGoogle(context, webClientIdInput) }
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Login, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sign in with Google")
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.signInAnonymous() }
                                ) {
                                    Text("Anonymous Sync")
                                }
                            }
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(onClick = { viewModel.pushLocalToCloud() }) {
                                    Icon(Icons.Rounded.CloudUpload, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Push Local to Cloud")
                                }
                                FilledTonalButton(onClick = { viewModel.signOutFirebase() }) {
                                    Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sign Out")
                                }
                            }
                        }

                        if (!cloudState.isFromGoogleServicesResource) {
                            TextButton(onClick = { showConfigEditor = !showConfigEditor }) {
                                Text(if (showConfigEditor) "Hide config editor" else "Change Firebase config")
                            }
                        }
                    } else {
                        Text(
                            "Data is stored locally on this device (and updates your Home Screen Widget). To sync live with the Web App, paste your Firebase config or google-services.json below.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (cloudState.connectedProjectId == null || showConfigEditor) {
                        OutlinedTextField(
                            value = snippetInput,
                            onValueChange = { snippetInput = it },
                            label = { Text("Paste firebaseConfig or google-services.json") },
                            placeholder = { Text("const firebaseConfig = { apiKey: \"...\", projectId: \"gift-tracker-3018d\" };") },
                            minLines = 3,
                            maxLines = 6,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = webClientIdInput,
                            onValueChange = { webClientIdInput = it },
                            label = { Text("Optional: Google OAuth Web Client ID") },
                            placeholder = { Text("...apps.googleusercontent.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (configError != null) {
                            Text(configError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (cloudState.connectedProjectId != null) {
                                TextButton(onClick = { viewModel.connectFirebaseConfig(null) }) {
                                    Text("Disconnect")
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Button(
                                onClick = {
                                    val parsed = FirebaseSyncManager.parseFirebaseSnippet(
                                        input = snippetInput,
                                        webClientIdOverride = webClientIdInput,
                                        targetPackageName = context.packageName
                                    )
                                    if (parsed == null) {
                                        configError = "Could not find apiKey and projectId. Paste firebaseConfig or google-services.json."
                                    } else {
                                        configError = null
                                        viewModel.connectFirebaseConfig(parsed)
                                        snippetInput = ""
                                        showConfigEditor = false
                                    }
                                }
                            ) {
                                Text("Connect Firebase")
                            }
                        }
                    }

                    cloudState.syncError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            // 3. Backup & Data (Storage Access Framework Export/Import + Reset)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Backup & Data", style = MaterialTheme.typography.titleMedium)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                exportLauncher.launch("gift-tracker-backup-${LocalDate.now()}.json")
                            }
                        ) {
                            Icon(Icons.Rounded.FileDownload, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export JSON")
                        }

                        FilledTonalButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            }
                        ) {
                            Icon(Icons.Rounded.FileUpload, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import JSON")
                        }

                        FilledTonalButton(onClick = { confirmResetOpen = true }) {
                            Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset Data")
                        }
                    }
                }
            }
        }
    }

    if (confirmResetOpen) {
        AlertDialog(
            onDismissRequest = { confirmResetOpen = false },
            title = { Text("Reset all Gift Tracker data?") },
            text = { Text("This clears all gifts, recipients, and wishlist items to a fresh empty slate.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmResetOpen = false
                        viewModel.resetData()
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetOpen = false }) { Text("Cancel") }
            }
        )
    }
}
