package com.twintitanium.gifttracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.twintitanium.gifttracker.data.model.SharedWishlistLink
import com.twintitanium.gifttracker.data.model.WishItem
import com.twintitanium.gifttracker.data.remote.FirebaseSyncManager
import com.twintitanium.gifttracker.ui.GiftTrackerViewModel

@Composable
fun WishlistScreen(
    viewModel: GiftTrackerViewModel,
    contentPadding: PaddingValues
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    var filter by rememberSaveable { mutableStateOf("wanted") } // wanted | received
    var newTitle by rememberSaveable { mutableStateOf("") }
    var newUrl by rememberSaveable { mutableStateOf("") }
    var newNotes by rememberSaveable { mutableStateOf("") }
    var showQuickAddDetails by rememberSaveable { mutableStateOf(false) }

    // Edit wish state
    var editingWishId by rememberSaveable { mutableStateOf<String?>(null) }
    var editTitle by rememberSaveable { mutableStateOf("") }
    var editUrl by rememberSaveable { mutableStateOf("") }
    var editNotes by rememberSaveable { mutableStateOf("") }

    // Logging received state
    var loggingWishId by rememberSaveable { mutableStateOf<String?>(null) }
    var fromInput by rememberSaveable { mutableStateOf("") }
    var whenInput by rememberSaveable { mutableStateOf("Christmas 2026") }

    // Share Selection Sheet state
    var showShareDialog by rememberSaveable { mutableStateOf(false) }

    // Reorder mode state
    var reorderMode by rememberSaveable { mutableStateOf(false) }

    val sortedWishes = remember(dataset.wishes) { dataset.wishes.sortedBy { it.order } }
    val wantedWishes = remember(sortedWishes) { sortedWishes.filter { !it.received } }
    val receivedWishes = remember(sortedWishes) { sortedWishes.filter { it.received } }
    val displayed = if (filter == "wanted") wantedWishes else receivedWishes

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Shareable Wishlist Card
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "PERSONAL WISHLIST (\"ME\" TAB)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${wantedWishes.size} Wishes • ${receivedWishes.size} Received",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Share separate live group checklists so friends & family don't duplicate gifts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        enabled = wantedWishes.isNotEmpty() || dataset.sharedLinks.isNotEmpty(),
                        onClick = { showShareDialog = true }
                    ) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (dataset.sharedLinks.isNotEmpty())
                                "Share (${dataset.sharedLinks.size})"
                            else "Share"
                        )
                    }
                }
            }
        }

        // Quick Add Wish Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text("Add something to your wishlist...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newUrl,
                            onValueChange = { newUrl = it },
                            placeholder = { Text("Optional product link...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalButton(onClick = { showQuickAddDetails = !showQuickAddDetails }) {
                            Text(if (showQuickAddDetails) "Less" else "Notes", fontSize = 12.sp)
                        }

                        Button(
                            enabled = newTitle.isNotBlank(),
                            onClick = {
                                viewModel.addWish(newTitle, newUrl, newNotes)
                                newTitle = ""
                                newUrl = ""
                                newNotes = ""
                                showQuickAddDetails = false
                            }
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    if (showQuickAddDetails) {
                        OutlinedTextField(
                            value = newNotes,
                            onValueChange = { newNotes = it },
                            placeholder = { Text("Subnotes & specifics (size, color, brand, details)...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Filter Chips + Reorder
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter == "wanted",
                        onClick = { filter = "wanted" },
                        label = { Text("⭐ Wishes (${wantedWishes.size})") }
                    )
                    FilterChip(
                        selected = filter == "received",
                        onClick = { filter = "received" },
                        label = { Text("🎁 Received Log (${receivedWishes.size})") }
                    )
                }

                if (displayed.size > 1) {
                    FilledTonalButton(
                        onClick = { reorderMode = !reorderMode },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(if (reorderMode) "Done" else "Reorder", fontSize = 12.sp)
                    }
                }
            }
        }

        if (displayed.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Text(
                        text = if (filter == "wanted") "Your wishlist is empty! Add an idea above."
                        else "No received gifts logged yet.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(displayed, key = { _, it -> it.id }) { index, wish ->
                val isLogging = loggingWishId == wish.id
                val isEditing = editingWishId == wish.id

                ElevatedCard(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isEditing) {
                            // Edit Form
                            Text(
                                "EDIT WISH IDEA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                label = { Text("Wish Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editUrl,
                                onValueChange = { editUrl = it },
                                label = { Text("Link (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = { Text("Subnotes & details (size, color, details)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { editingWishId = null }) {
                                    Text("Cancel")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    enabled = editTitle.isNotBlank(),
                                    onClick = {
                                        viewModel.updateWish(
                                            wish.copy(
                                                title = editTitle.trim(),
                                                url = editUrl.trim(),
                                                notes = editNotes.trim()
                                            )
                                        )
                                        editingWishId = null
                                    }
                                ) {
                                    Text("Save Changes")
                                }
                            }
                        } else {
                            // Normal Wish View
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = wish.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        textDecoration = if (wish.received) TextDecoration.LineThrough else null
                                    )
                                    if (wish.received && (wish.receivedFrom.isNotBlank() || wish.receivedWhen.isNotBlank())) {
                                        Text(
                                            text = "✓ From ${wish.receivedFrom.ifBlank { "Family" }} (${wish.receivedWhen})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (reorderMode && displayed.size > 1) {
                                        IconButton(
                                            enabled = index > 0,
                                            onClick = {
                                                val mutable = displayed.toMutableList()
                                                if (index > 0) {
                                                    val item = mutable.removeAt(index)
                                                    mutable.add(index - 1, item)
                                                    val others = if (filter == "wanted") receivedWishes else wantedWishes
                                                    val merged = if (filter == "wanted") mutable + others else others + mutable
                                                    viewModel.reorderWishes(merged.map { it.id })
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move Up")
                                        }
                                        IconButton(
                                            enabled = index < displayed.size - 1,
                                            onClick = {
                                                val mutable = displayed.toMutableList()
                                                if (index < mutable.size - 1) {
                                                    val item = mutable.removeAt(index)
                                                    mutable.add(index + 1, item)
                                                    val others = if (filter == "wanted") receivedWishes else wantedWishes
                                                    val merged = if (filter == "wanted") mutable + others else others + mutable
                                                    viewModel.reorderWishes(merged.map { it.id })
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move Down")
                                        }
                                    }

                                    if (!wish.received) {
                                        FilledTonalButton(
                                            onClick = {
                                                loggingWishId = if (isLogging) null else wish.id
                                                fromInput = ""
                                                whenInput = "Christmas 2026"
                                            }
                                        ) {
                                            Icon(Icons.Rounded.Check, null, Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Received", fontSize = 12.sp)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                viewModel.updateWish(
                                                    wish.copy(received = false, receivedFrom = "", receivedWhen = "")
                                                )
                                            }
                                        ) {
                                            Text("Undo")
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            editingWishId = wish.id
                                            editTitle = wish.title
                                            editUrl = wish.url
                                            editNotes = wish.notes
                                            loggingWishId = null
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit wish idea")
                                    }

                                    IconButton(onClick = { viewModel.deleteWish(wish.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete wish")
                                    }
                                }
                            }

                            // Subnotes display
                            if (wish.notes.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = wish.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            if (wish.url.isNotBlank()) {
                                AssistChip(
                                    onClick = { runCatching { uriHandler.openUri(wish.url) } },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(15.dp)) },
                                    label = { Text("Open Link", fontSize = 12.sp) }
                                )
                            }

                            if (isLogging) {
                                Column(
                                    modifier = Modifier.padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = fromInput,
                                            onValueChange = { fromInput = it },
                                            label = { Text("From who? (e.g., Alex, Dad)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = whenInput,
                                            onValueChange = { whenInput = it },
                                            label = { Text("Occasion") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { loggingWishId = null }) { Text("Cancel") }
                                        Spacer(Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                viewModel.updateWish(
                                                    wish.copy(
                                                        received = true,
                                                        receivedFrom = fromInput.trim(),
                                                        receivedWhen = whenInput.trim()
                                                    )
                                                )
                                                loggingWishId = null
                                            }
                                        ) {
                                            Text("Save to Received Log")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Share & Manage Group Links Sheet
    if (showShareDialog) {
        ShareWishlistSheet(
            viewModel = viewModel,
            wishes = wantedWishes,
            allWishes = sortedWishes,
            sharedLinks = dataset.sharedLinks,
            onDismiss = { showShareDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareWishlistSheet(
    viewModel: GiftTrackerViewModel,
    wishes: List<WishItem>,
    allWishes: List<WishItem>,
    sharedLinks: List<SharedWishlistLink>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val cloudState by viewModel.cloudState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var activeTab by remember {
        mutableStateOf(if (wishes.isEmpty() && sharedLinks.isNotEmpty()) "manage" else "create")
    }

    // Create / Edit link state
    var editingLink by remember { mutableStateOf<SharedWishlistLink?>(null) }
    var groupTitle by rememberSaveable { mutableStateOf("Family & Friends Wishlist") }
    var ownerName by remember(prefs.ownerDisplayName, cloudState.userLabel) {
        mutableStateOf(
            prefs.ownerDisplayName
                ?: cloudState.userLabel?.substringBefore("@")?.substringBefore(" ")
                ?: ""
        )
    }
    val defaultInferredWeb = remember(prefs.webAppUrl, cloudState.exportableConfig?.projectId) {
        prefs.webAppUrl?.takeIf { it.isNotBlank() }
            ?: cloudState.exportableConfig?.projectId?.takeIf { it.isNotBlank() }?.let { "https://$it.web.app" }
            ?: ""
    }
    var webAppUrlInput by remember(defaultInferredWeb) {
        mutableStateOf(defaultInferredWeb)
    }
    var selectedIds by remember { mutableStateOf(wishes.map { it.id }.toSet()) }
    var includeAllActive by rememberSaveable { mutableStateOf(false) }
    var includeNotes by rememberSaveable { mutableStateOf(true) }
    var includeUrls by rememberSaveable { mutableStateOf(true) }

    // Manage tab QR & revoke confirmation state
    var qrLinkId by remember { mutableStateOf<String?>(null) }
    var confirmRevokeLink by remember { mutableStateOf<SharedWishlistLink?>(null) }

    val allSelected = selectedIds.size == wishes.size && wishes.isNotEmpty()
    val isSignedInCloud = cloudState.userUid != null

    val formattedShareText = remember(selectedIds, includeAllActive, includeNotes, includeUrls, wishes, groupTitle) {
        val selected = wishes.filter { includeAllActive || it.id in selectedIds }
        if (selected.isEmpty()) ""
        else {
            val lines = selected.mapIndexed { idx, w ->
                buildString {
                    append("${idx + 1}. ${w.title}")
                    if (includeUrls && w.url.isNotBlank()) append(" (${w.url})")
                    if (includeNotes && w.notes.isNotBlank()) append("\n   Note: ${w.notes}")
                }
            }
            val heading = groupTitle.trim().ifBlank { "My Gift Wishlist" }
            "🎁 $heading (${selected.size} items):\n\n${lines.joinToString("\n\n")}"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        PredictiveBackHandler(enabled = editingLink != null || qrLinkId != null) { progress ->
            progress.collect { }
            when {
                editingLink != null -> {
                    editingLink = null
                    groupTitle = "Family & Friends Wishlist"
                    selectedIds = wishes.map { it.id }.toSet()
                    includeAllActive = false
                    activeTab = "manage"
                }
                qrLinkId != null -> {
                    qrLinkId = null
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "COLLABORATIVE WISHLIST SHARING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (editingLink != null) "Edit Group Wishlist Link" else "Share Personal Wishlist",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Share separate live checklists with different groups — they coordinate claims together while surprises stay hidden from you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Connected ButtonGroup: New Share Link vs Active Links
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                FilledTonalToggleButton(
                    checked = activeTab == "create",
                    onCheckedChange = { activeTab = "create" },
                    modifier = Modifier.weight(1f),
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (editingLink != null) "Editing Link" else "New Link / Text", fontSize = 12.sp)
                }
                FilledTonalToggleButton(
                    checked = activeTab == "manage",
                    onCheckedChange = {
                        editingLink = null
                        activeTab = "manage"
                    },
                    modifier = Modifier.weight(1f),
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
                ) {
                    Icon(Icons.Rounded.Link, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Active Links (${sharedLinks.size})", fontSize = 12.sp)
                }
            }

            if (activeTab == "create") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = groupTitle,
                        onValueChange = { groupTitle = it },
                        label = { Text("Group Label (e.g., Family, Friends)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Your Name") },
                        singleLine = true,
                        modifier = Modifier.weight(0.9f)
                    )
                }

                if (isSignedInCloud && prefs.webAppUrl.isNullOrBlank()) {
                    OutlinedTextField(
                        value = webAppUrlInput,
                        onValueChange = { webAppUrlInput = it },
                        label = { Text("Hosted Web App URL (saved for all links)") },
                        placeholder = { Text("https://your-gift-tracker.hosted.app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Select All + Checkboxes
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.Center
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (allSelected) {
                                selectedIds = emptySet()
                                includeAllActive = false
                            } else {
                                selectedIds = wishes.map { it.id }.toSet()
                            }
                        }
                    ) {
                        Text(
                            if (allSelected) "Deselect All" else "Select All (${selectedIds.size}/${wishes.size})",
                            fontSize = 12.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeAllActive,
                            onCheckedChange = {
                                includeAllActive = it
                                if (it) selectedIds = wishes.map { w -> w.id }.toSet()
                            }
                        )
                        Text("Auto-add new", fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Checkbox(checked = includeNotes, onCheckedChange = { includeNotes = it })
                        Text("Notes", fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Checkbox(checked = includeUrls, onCheckedChange = { includeUrls = it })
                        Text("Links", fontSize = 11.sp)
                    }
                }

                // Wish Selection List
                if (wishes.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No active wishes right now. Check \"Auto-add new\" to create a live link ready for future wishes!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(wishes, key = { it.id }) { wish ->
                            val isChecked = includeAllActive || wish.id in selectedIds
                            Surface(
                                onClick = {
                                    if (isChecked) {
                                        selectedIds = selectedIds - wish.id
                                        includeAllActive = false
                                    } else {
                                        selectedIds = selectedIds + wish.id
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = {
                                            if (isChecked) {
                                                selectedIds = selectedIds - wish.id
                                                includeAllActive = false
                                            } else {
                                                selectedIds = selectedIds + wish.id
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(wish.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        if (wish.notes.isNotBlank()) {
                                            Text(
                                                wish.notes,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isSignedInCloud) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "💡 Sign in to Cloud Sync in Settings to publish live collaborative group links. You can still copy or share a plain-text list below.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                val canPublish = includeAllActive || selectedIds.isNotEmpty()

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editingLink != null) {
                        TextButton(
                            onClick = {
                                editingLink = null
                                groupTitle = "Family & Friends Wishlist"
                                selectedIds = wishes.map { it.id }.toSet()
                                includeAllActive = false
                            }
                        ) {
                            Text("Cancel Edit")
                        }
                        Spacer(Modifier.width(6.dp))
                    } else {
                        TextButton(
                            enabled = formattedShareText.isNotBlank(),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Gift Wishlist", formattedShareText))
                                viewModel.showMessage("Copied plain-text wishlist!")
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Text")
                        }

                        if (!isSignedInCloud) {
                            Spacer(Modifier.width(6.dp))
                            Button(
                                enabled = formattedShareText.isNotBlank(),
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, groupTitle.ifBlank { "My Gift Wishlist" })
                                        putExtra(Intent.EXTRA_TEXT, formattedShareText)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Wishlist"))
                                    onDismiss()
                                }
                            ) {
                                Icon(Icons.Rounded.Share, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share Text")
                            }
                        }
                    }

                    if (isSignedInCloud) {
                        Spacer(Modifier.width(6.dp))
                        Button(
                            enabled = canPublish,
                            onClick = {
                                if (webAppUrlInput.isNotBlank()) {
                                    viewModel.setWebAppUrl(webAppUrlInput)
                                }
                                val chosenIds = if (includeAllActive) wishes.map { it.id } else selectedIds.toList()
                                val currentEditing = editingLink
                                if (currentEditing != null) {
                                    viewModel.updateSharedWishlistLink(
                                        currentEditing.copy(
                                            title = groupTitle.trim().ifBlank { "Gift Wishlist" },
                                            ownerName = ownerName.trim(),
                                            wishIds = chosenIds,
                                            includeAllActive = includeAllActive,
                                            includeNotes = includeNotes,
                                            includeUrls = includeUrls
                                        )
                                    ) {
                                        editingLink = null
                                        activeTab = "manage"
                                    }
                                } else {
                                    viewModel.createSharedWishlistLink(
                                        title = groupTitle,
                                        ownerName = ownerName,
                                        wishIds = chosenIds,
                                        includeAllActive = includeAllActive,
                                        includeNotes = includeNotes,
                                        includeUrls = includeUrls
                                    ) { created ->
                                        val effectiveWeb = webAppUrlInput.trim().ifBlank { prefs.webAppUrl.orEmpty() }
                                        if (effectiveWeb.isNotBlank()) {
                                            val url = FirebaseSyncManager.buildSharedWishlistUrl(
                                                created.id,
                                                cloudState.exportableConfig,
                                                effectiveWeb
                                            )
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText(created.title, url))
                                        }
                                        qrLinkId = created.id
                                        activeTab = "manage"
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (editingLink != null) "Save Changes" else "Create Live Group Link")
                        }
                    }
                }
            } else {
                // Manage Active Links Tab
                if (prefs.webAppUrl.isNullOrBlank()) {
                    OutlinedTextField(
                        value = webAppUrlInput,
                        onValueChange = { webAppUrlInput = it },
                        label = { Text("Hosted Web App URL (required for shareable web URL)") },
                        placeholder = { Text("https://gift-tracker-3018d.web.app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (sharedLinks.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "No active group wishlist links yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { activeTab = "create" }) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Create First Group Link")
                            }
                        }
                    }
                } else {
                    sharedLinks.forEach { link ->
                        val activeCount = remember(link, allWishes) {
                            link.computeActiveWishes(allWishes).size
                        }
                        val effectiveWeb = webAppUrlInput.trim().ifBlank { prefs.webAppUrl.orEmpty() }
                        val shareUrl = remember(link.id, cloudState.exportableConfig, effectiveWeb) {
                            if (effectiveWeb.isBlank()) ""
                            else FirebaseSyncManager.buildSharedWishlistUrl(
                                link.id,
                                cloudState.exportableConfig,
                                effectiveWeb
                            )
                        }
                        val isShowingQr = qrLinkId == link.id

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            link.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            buildString {
                                                append("$activeCount active wish${if (activeCount == 1) "" else "es"}")
                                                if (link.includeAllActive) append(" • Auto-adds new")
                                                if (link.ownerName.isNotBlank()) append(" • ${link.ownerName}")
                                            },
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingLink = link
                                                groupTitle = link.title
                                                ownerName = link.ownerName.ifBlank { ownerName }
                                                selectedIds = if (link.includeAllActive) {
                                                    wishes.map { it.id }.toSet()
                                                } else {
                                                    link.wishIds.toSet()
                                                }
                                                includeAllActive = link.includeAllActive
                                                includeNotes = link.includeNotes
                                                includeUrls = link.includeUrls
                                                activeTab = "create"
                                            }
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Edit group link")
                                        }
                                        IconButton(onClick = { confirmRevokeLink = link }) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Revoke link")
                                        }
                                    }
                                }

                                if (isShowingQr && shareUrl.isNotBlank()) {
                                    val qrBitmap = remember(shareUrl) { generateShareQrBitmap(shareUrl) }
                                    if (qrBitmap != null) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = Color.White,
                                                shadowElevation = 2.dp
                                            ) {
                                                Image(
                                                    bitmap = qrBitmap,
                                                    contentDescription = "QR Code for ${link.title}",
                                                    modifier = Modifier
                                                        .padding(10.dp)
                                                        .size(168.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        enabled = shareUrl.isNotBlank(),
                                        onClick = {
                                            if (webAppUrlInput.isNotBlank()) viewModel.setWebAppUrl(webAppUrlInput)
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, link.title)
                                                val prefix = if (link.ownerName.isNotBlank()) "${link.ownerName}'s" else "My"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "🎁 $prefix live wishlist (${link.title}) — claim items here so nobody duplicates gifts:\n$shareUrl"
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Group Wishlist"))
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Share, null, Modifier.size(15.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Share")
                                    }

                                    FilledTonalButton(
                                        enabled = shareUrl.isNotBlank(),
                                        onClick = {
                                            if (webAppUrlInput.isNotBlank()) viewModel.setWebAppUrl(webAppUrlInput)
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText(link.title, shareUrl))
                                            viewModel.showMessage("Copied link for \"${link.title}\"!")
                                        }
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(15.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Copy")
                                    }

                                    FilledTonalButton(
                                        enabled = shareUrl.isNotBlank(),
                                        onClick = {
                                            if (webAppUrlInput.isNotBlank()) viewModel.setWebAppUrl(webAppUrlInput)
                                            qrLinkId = if (isShowingQr) null else link.id
                                        }
                                    ) {
                                        Icon(Icons.Rounded.QrCode2, null, Modifier.size(15.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(if (isShowingQr) "Hide QR" else "QR")
                                    }

                                    if (shareUrl.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                if (webAppUrlInput.isNotBlank()) viewModel.setWebAppUrl(webAppUrlInput)
                                                runCatching { uriHandler.openUri(shareUrl) }
                                            }
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Preview")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmRevokeLink?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmRevokeLink = null },
            title = { Text("Revoke \"${target.title}\"?") },
            text = { Text("People with this link will no longer be able to view or claim items on this group checklist.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSharedWishlistLink(target)
                        confirmRevokeLink = null
                    }
                ) {
                    Text("Revoke Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevokeLink = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun generateShareQrBitmap(content: String, sizePx: Int = 480): ImageBitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx)
    val dark = android.graphics.Color.rgb(20, 18, 24)
    val light = android.graphics.Color.WHITE
    for (y in 0 until sizePx) {
        val offset = y * sizePx
        for (x in 0 until sizePx) {
            pixels[offset + x] = if (matrix.get(x, y)) dark else light
        }
    }
    Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()
