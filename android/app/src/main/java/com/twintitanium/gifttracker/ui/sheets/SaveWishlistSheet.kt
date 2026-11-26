package com.twintitanium.gifttracker.ui.sheets

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twintitanium.gifttracker.data.model.SavedWishlist
import com.twintitanium.gifttracker.data.remote.FirebaseSyncManager
import com.twintitanium.gifttracker.ui.GiftTrackerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveWishlistSheet(
    editingWishlist: SavedWishlist?,
    defaultPersonId: String?,
    defaultUrl: String,
    viewModel: GiftTrackerViewModel,
    onDismiss: () -> Unit
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val initialPerson = remember(dataset.people, editingWishlist, defaultPersonId) {
        val targetId = editingWishlist?.personId ?: defaultPersonId
        dataset.people.firstOrNull { it.id == targetId }
    }

    var url by remember(editingWishlist, defaultUrl) {
        mutableStateOf(editingWishlist?.url ?: defaultUrl)
    }
    var title by remember(editingWishlist) {
        mutableStateOf(editingWishlist?.title.orEmpty())
    }
    var ownerName by remember(editingWishlist, initialPerson) {
        mutableStateOf(editingWishlist?.ownerName ?: initialPerson?.name.orEmpty())
    }
    var personMode by remember(editingWishlist, defaultPersonId, dataset.people.size) {
        val initialMode = when {
            editingWishlist != null -> if (editingWishlist.personId != null) "existing" else "unlinked"
            defaultPersonId != null -> "existing"
            dataset.people.isNotEmpty() -> "existing"
            else -> "create"
        }
        mutableStateOf(initialMode)
    }
    var selectedPersonId by remember(editingWishlist, defaultPersonId, dataset.people) {
        mutableStateOf(
            editingWishlist?.personId
                ?: defaultPersonId
                ?: dataset.people.firstOrNull()?.id.orEmpty()
        )
    }
    var notes by remember(editingWishlist) {
        mutableStateOf(editingWishlist?.notes.orEmpty())
    }
    var fetchingMeta by remember { mutableStateOf(false) }

    val parsedUrl = remember(url) { FirebaseSyncManager.parseWishlistUrl(url) }

    suspend fun runAutoFill(targetUrl: String) {
        if (targetUrl.isBlank()) return
        fetchingMeta = true
        val parsed = FirebaseSyncManager.parseWishlistUrl(targetUrl)
        if (parsed.type == "live") {
            val remoteDoc = viewModel.fetchPublicWishlistMetadata(targetUrl)
            if (remoteDoc != null) {
                if (title.isBlank() && remoteDoc.title.isNotBlank()) title = remoteDoc.title
                if (ownerName.isBlank() && remoteDoc.ownerName.isNotBlank()) {
                    ownerName = remoteDoc.ownerName
                    val matched = dataset.people.firstOrNull {
                        it.name.trim().equals(remoteDoc.ownerName.trim(), ignoreCase = true)
                    }
                    if (matched != null && defaultPersonId == null) {
                        personMode = "existing"
                        selectedPersonId = matched.id
                    }
                }
            }
        } else {
            val scraped = viewModel.scrapeUrl(parsed.normalizedUrl)
            if (title.isBlank() && scraped.title.isNotBlank()) {
                title = scraped.title
            }
        }
        fetchingMeta = false
    }

    LaunchedEffect(defaultUrl) {
        if (editingWishlist == null && defaultUrl.isNotBlank()) {
            runAutoFill(defaultUrl)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "SHARED WITH ME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (editingWishlist != null) "Edit Saved Wishlist" else "Save Someone's Wishlist",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                if (editingWishlist != null) {
                    IconButton(
                        onClick = {
                            viewModel.deleteSavedWishlistWithUndo(editingWishlist.id)
                            onDismiss()
                        }
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Remove saved wishlist",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                "Paste a Gift Tracker /w/... link for live synced claims, or any external registry URL (Amazon, Etsy, Target, Google Docs).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // URL Input + Type Badge + Auto-fill
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (url.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (parsedUrl.type == "live") MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (parsedUrl.type == "live") "● Live Synced Checklist" else "External Registry Link",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (parsedUrl.type == "live") MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Wishlist Link *") },
                            placeholder = { Text("https://.../w/abc123 or amazon.com/...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalButton(
                            enabled = url.isNotBlank() && !fetchingMeta,
                            onClick = { scope.launch { runAutoFill(url) } }
                        ) {
                            if (fetchingMeta) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("Auto-fill", fontSize = 12.sp)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("List Title") },
                    placeholder = { Text("e.g., Holiday Wishlist") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )

                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Whose List?") },
                    placeholder = { Text("e.g., Mom, Jordan") },
                    singleLine = true,
                    modifier = Modifier.weight(0.95f)
                )
            }

            // Link to Person in People & Sizes
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Link to Person in People & Sizes", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (dataset.people.isNotEmpty()) {
                        FilterChip(
                            selected = personMode == "existing",
                            onClick = { personMode = "existing" },
                            label = { Text("Existing (${dataset.people.size})") }
                        )
                    }
                    FilterChip(
                        selected = personMode == "create",
                        onClick = { personMode = "create" },
                        leadingIcon = { Icon(Icons.Rounded.PersonAdd, null, Modifier.size(15.dp)) },
                        label = {
                            Text(
                                if (ownerName.isNotBlank()) "Create \"${ownerName.trim()}\""
                                else "Create Person"
                            )
                        }
                    )
                    FilterChip(
                        selected = personMode == "unlinked",
                        onClick = { personMode = "unlinked" },
                        label = { Text("Don't Link Yet") }
                    )
                }

                if (personMode == "existing" && dataset.people.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dataset.people.forEach { p ->
                            FilterChip(
                                selected = selectedPersonId == p.id,
                                onClick = {
                                    selectedPersonId = p.id
                                    if (ownerName.isBlank()) ownerName = p.name
                                },
                                label = { Text(p.name) }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Private Notes (only visible to you)") },
                placeholder = { Text("Optional reminder about this list...") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (editingWishlist != null && editingWishlist.url.isNotBlank()) {
                    TextButton(
                        onClick = { runCatching { uriHandler.openUri(editingWishlist.url) } }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open URL")
                    }
                    Spacer(Modifier.weight(1f))
                }

                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = url.isNotBlank(),
                    onClick = {
                        viewModel.saveIncomingWishlist(
                            editing = editingWishlist,
                            url = url,
                            title = title,
                            ownerName = ownerName,
                            personMode = personMode,
                            selectedPersonId = selectedPersonId,
                            notes = notes
                        )
                    }
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (editingWishlist != null) "Save Changes" else "Save Wishlist")
                }
            }
        }
    }
}
