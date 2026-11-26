package com.twintitanium.gifttracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.twintitanium.gifttracker.data.model.SavedWishlist
import com.twintitanium.gifttracker.data.model.SharedWishlistLink
import com.twintitanium.gifttracker.data.model.WishlistHubTab
import com.twintitanium.gifttracker.data.model.WishItem
import com.twintitanium.gifttracker.data.remote.FirebaseSyncManager
import com.twintitanium.gifttracker.ui.GiftTrackerViewModel
import com.twintitanium.gifttracker.ui.components.ExpressiveAvatar
import com.twintitanium.gifttracker.ui.components.dragHandle
import com.twintitanium.gifttracker.ui.components.draggableItemModifier
import com.twintitanium.gifttracker.ui.components.longPressDraggable
import com.twintitanium.gifttracker.ui.components.onSecondaryClick
import com.twintitanium.gifttracker.ui.components.pointerHandCursor
import com.twintitanium.gifttracker.ui.components.rememberDragDropState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WishlistScreen(
    viewModel: GiftTrackerViewModel,
    contentPadding: PaddingValues,
    isWide: Boolean = false,
    escapeTick: Int = 0
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val cloudState by viewModel.cloudState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val subTab = uiState.wishlistSubTab

    // --- My Wishlist State ---
    var filter by rememberSaveable { mutableStateOf("wanted") } // wanted | received
    var newTitle by rememberSaveable { mutableStateOf("") }
    var newUrl by rememberSaveable { mutableStateOf("") }
    var newNotes by rememberSaveable { mutableStateOf("") }
    var showQuickAddDetails by rememberSaveable { mutableStateOf(false) }

    var editingWishId by rememberSaveable { mutableStateOf<String?>(null) }
    var editTitle by rememberSaveable { mutableStateOf("") }
    var editUrl by rememberSaveable { mutableStateOf("") }
    var editNotes by rememberSaveable { mutableStateOf("") }

    var loggingWishId by rememberSaveable { mutableStateOf<String?>(null) }
    var fromInput by rememberSaveable { mutableStateOf("") }
    var whenInput by rememberSaveable { mutableStateOf("Christmas 2026") }

    var reorderMode by rememberSaveable { mutableStateOf(false) }

    // --- Outbound Share Sheet / Manage State ---
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    var shareSheetInitialEditingLink by remember { mutableStateOf<SharedWishlistLink?>(null) }
    var shareSheetInitialTab by remember { mutableStateOf<String?>(null) }
    var outboundQrLinkId by remember { mutableStateOf<String?>(null) }
    var confirmRevokeOutbound by remember { mutableStateOf<SharedWishlistLink?>(null) }

    // --- Shared With Me (Mobile Inline Accordion) ---
    var mobileExpandedSavedId by rememberSaveable(uiState.selectedSavedWishlistId) {
        mutableStateOf(uiState.selectedSavedWishlistId)
    }

    LaunchedEffect(escapeTick) {
        if (escapeTick > 0) {
            when {
                editingWishId != null -> editingWishId = null
                loggingWishId != null -> loggingWishId = null
                reorderMode -> reorderMode = false
                !isWide && mobileExpandedSavedId != null -> mobileExpandedSavedId = null
                outboundQrLinkId != null -> outboundQrLinkId = null
            }
        }
    }

    PredictiveBackHandler(enabled = !isWide && subTab == WishlistHubTab.INCOMING && mobileExpandedSavedId != null) { progress ->
        progress.collect { }
        mobileExpandedSavedId = null
    }

    val sortedWishes = remember(dataset.wishes) { dataset.wishes.sortedBy { it.order } }
    val wantedWishes = remember(sortedWishes) { sortedWishes.filter { !it.received } }
    val receivedWishes = remember(sortedWishes) { sortedWishes.filter { it.received } }
    val displayed = if (filter == "wanted") wantedWishes else receivedWishes

    val peopleMap = remember(dataset.people) { dataset.people.associateBy { it.id } }
    val activeSavedWishlist = remember(dataset.savedWishlists, uiState.selectedSavedWishlistId) {
        dataset.savedWishlists.firstOrNull { it.id == uiState.selectedSavedWishlistId }
            ?: dataset.savedWishlists.firstOrNull()
    }

    @Composable
    fun HubSwitcherRow() {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                FilledTonalToggleButton(
                    checked = subTab == WishlistHubTab.MINE,
                    onCheckedChange = { viewModel.setWishlistSubTab(WishlistHubTab.MINE) },
                    modifier = Modifier.weight(1f),
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "My Wishlist (${wantedWishes.size})",
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalToggleButton(
                    checked = subTab == WishlistHubTab.INCOMING,
                    onCheckedChange = { viewModel.setWishlistSubTab(WishlistHubTab.INCOMING) },
                    modifier = Modifier.weight(1f),
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Inbox, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Shared (${dataset.savedWishlists.size})",
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalToggleButton(
                    checked = subTab == WishlistHubTab.OUTBOUND,
                    onCheckedChange = { viewModel.setWishlistSubTab(WishlistHubTab.OUTBOUND) },
                    modifier = Modifier.weight(1f),
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Link, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "My Links (${dataset.sharedLinks.size})",
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // =========================================================================
    // 2-PANE SPLIT FOR FOLDABLE & DESKTOP IN "SHARED WITH ME"
    // =========================================================================
    if (subTab == WishlistHubTab.INCOMING && isWide && dataset.savedWishlists.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // LEFT PANE: Hub Switcher + Saved Wishlists Directory
            LazyColumn(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { HubSwitcherRow() }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Shared With Me (${dataset.savedWishlists.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(onClick = { viewModel.openSaveWishlistSheet() }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Wishlist")
                        }
                    }
                }

                items(dataset.savedWishlists, key = { it.id }) { sw ->
                    val linkedPerson = sw.personId?.let { peopleMap[it] }
                    val isSelected = activeSavedWishlist?.id == sw.id
                    val ownerLabel = sw.ownerName.ifBlank { linkedPerson?.name ?: "Shared" }
                    var showMenu by remember { mutableStateOf(false) }

                    Box {
                        Surface(
                            onClick = { viewModel.selectSavedWishlist(sw.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerHandCursor()
                                .onSecondaryClick { showMenu = true },
                            shape = RoundedCornerShape(if (isSelected) 24.dp else 18.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    ExpressiveAvatar(
                                        name = linkedPerson?.name ?: ownerLabel,
                                        shapeName = linkedPerson?.avatarShape ?: "squircle",
                                        tone = linkedPerson?.avatarTone ?: "teal",
                                        size = 42.dp
                                    )
                                    Column {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "$ownerLabel — ${sw.title}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            buildString {
                                                append(if (sw.type == "live") "● Live Synced" else "External Registry")
                                                if (linkedPerson != null) append(" • ${linkedPerson.name}")
                                            },
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Saved Wishlist") },
                                onClick = {
                                    showMenu = false
                                    viewModel.openSaveWishlistSheet(editing = sw)
                                }
                            )
                            if (sw.url.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Open URL in Browser") },
                                    onClick = {
                                        showMenu = false
                                        runCatching { uriHandler.openUri(sw.url) }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Remove") },
                                onClick = {
                                    showMenu = false
                                    viewModel.deleteSavedWishlistWithUndo(sw.id)
                                }
                            )
                        }
                    }
                }
            }

            // RIGHT PANE: Active Saved Wishlist Live Checklist / External Card
            if (activeSavedWishlist != null) {
                Column(
                    modifier = Modifier
                        .weight(1.38f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    SavedWishlistDetailPane(
                        savedWishlist = activeSavedWishlist,
                        viewModel = viewModel,
                        compactInline = false
                    )
                }
            }
        }
        return
    }

    // =========================================================================
    // SINGLE-PANE / CENTERED WIDE WORKSPACE (MY WISHLIST, OUTBOUND, COMPACT INCOMING)
    // =========================================================================
    val canReorder = subTab == WishlistHubTab.MINE && reorderMode && displayed.size > 1 && editingWishId == null
    var localWishesOrder by remember(displayed) { mutableStateOf(displayed) }
    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(
        lazyListState = listState,
        headerCount = 4,
        itemCount = localWishesOrder.size,
        onMove = { fromIndex, toIndex ->
            localWishesOrder = localWishesOrder.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        },
        onDragEnd = {
            val newIds = localWishesOrder.map { it.id }
            if (newIds != displayed.map { it.id }) {
                val others = if (filter == "wanted") receivedWishes else wantedWishes
                val merged = if (filter == "wanted") localWishesOrder + others else others + localWishesOrder
                viewModel.reorderWishes(merged.map { it.id })
            }
        }
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxHeight()
                .then(if (isWide) Modifier.widthIn(max = 880.dp) else Modifier)
                .fillMaxWidth()
                .imePadding(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header 0: 3-Way Segmented Switcher
            item { HubSwitcherRow() }

            when (subTab) {
                // -------------------------------------------------------------
                // SUB-VIEW 1: MY WISHLIST (DEFAULT)
                // -------------------------------------------------------------
                WishlistHubTab.MINE -> {
                    // Header 1: Hero Shareable Wishlist Card
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
                                        "PERSONAL WISHLIST (EDITABLE BY YOU)",
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
                                    onClick = {
                                        shareSheetInitialEditingLink = null
                                        shareSheetInitialTab = null
                                        showShareDialog = true
                                    }
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

                    // Header 2: Quick Add Wish Card
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

                    // Header 3: Filter Chips + Reorder
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

                    if (localWishesOrder.isEmpty()) {
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
                        itemsIndexed(localWishesOrder, key = { _, it -> it.id }) { index, wish ->
                            val isLogging = loggingWishId == wish.id
                            val isEditing = editingWishId == wish.id
                            var showContextMenu by remember { mutableStateOf(false) }

                            Box {
                                ElevatedCard(
                                    modifier = Modifier
                                        .then(draggableItemModifier(dragDropState, wish.id))
                                        .longPressDraggable(dragDropState, wish.id, enabled = canReorder && !isEditing)
                                        .onSecondaryClick(enabled = !canReorder && !isEditing) { showContextMenu = true },
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = if (canReorder) 10.dp else 16.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (isEditing) {
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
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (canReorder) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DragHandle,
                                                        contentDescription = "Drag to reorder",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier
                                                            .dragHandle(dragDropState, wish.id, enabled = true)
                                                            .padding(end = 10.dp, top = 6.dp, bottom = 6.dp)
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = wish.title,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        textDecoration = if (wish.received) TextDecoration.LineThrough else null,
                                                        maxLines = if (canReorder) 1 else Int.MAX_VALUE,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (!canReorder && wish.received && (wish.receivedFrom.isNotBlank() || wish.receivedWhen.isNotBlank())) {
                                                        Text(
                                                            text = "✓ From ${wish.receivedFrom.ifBlank { "Family" }} (${wish.receivedWhen})",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (canReorder) {
                                                        IconButton(
                                                            enabled = index > 0,
                                                            onClick = {
                                                                val mutable = localWishesOrder.toMutableList()
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
                                                            enabled = index < localWishesOrder.size - 1,
                                                            onClick = {
                                                                val mutable = localWishesOrder.toMutableList()
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
                                                    } else {
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
                                            }

                                            if (!canReorder && wish.notes.isNotBlank()) {
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

                                            if (!canReorder && wish.url.isNotBlank()) {
                                                AssistChip(
                                                    onClick = { runCatching { uriHandler.openUri(wish.url) } },
                                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(15.dp)) },
                                                    label = { Text("Open Link", fontSize = 12.sp) }
                                                )
                                            }

                                            if (!canReorder && isLogging) {
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

                                DropdownMenu(
                                    expanded = showContextMenu,
                                    onDismissRequest = { showContextMenu = false }
                                ) {
                                    if (!wish.received) {
                                        DropdownMenuItem(
                                            text = { Text("Mark Received") },
                                            onClick = {
                                                showContextMenu = false
                                                loggingWishId = wish.id
                                                fromInput = ""
                                                whenInput = "Christmas 2026"
                                            }
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text("Undo Received") },
                                            onClick = {
                                                showContextMenu = false
                                                viewModel.updateWish(
                                                    wish.copy(received = false, receivedFrom = "", receivedWhen = "")
                                                )
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Edit Wish") },
                                        onClick = {
                                            showContextMenu = false
                                            editingWishId = wish.id
                                            editTitle = wish.title
                                            editUrl = wish.url
                                            editNotes = wish.notes
                                            loggingWishId = null
                                        }
                                    )
                                    if (wish.url.isNotBlank()) {
                                        DropdownMenuItem(
                                            text = { Text("Open Link") },
                                            onClick = {
                                                showContextMenu = false
                                                runCatching { uriHandler.openUri(wish.url) }
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Delete Wish") },
                                        onClick = {
                                            showContextMenu = false
                                            viewModel.deleteWish(wish.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // SUB-VIEW 2: SHARED WITH ME (COMPACT PHONE OR EMPTY STATE)
                // -------------------------------------------------------------
                WishlistHubTab.INCOMING -> {
                    item {
                        Card(
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
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
                                        "${dataset.savedWishlists.size} Saved Wishlist${if (dataset.savedWishlists.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        "Read-only lists from family & friends — claim items secretly and add gifts to Occasions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Button(onClick = { viewModel.openSaveWishlistSheet() }) {
                                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save")
                                }
                            }
                        }
                    }

                    if (dataset.savedWishlists.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("No Shared Wishlists Yet", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Save someone's Gift Tracker /w/... link for live synced claims, or bookmark external Amazon, Etsy, and Target registries.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(onClick = { viewModel.openSaveWishlistSheet() }) {
                                        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save Someone's Wishlist")
                                    }
                                }
                            }
                        }
                    } else {
                        items(dataset.savedWishlists, key = { it.id }) { sw ->
                            val linkedPerson = sw.personId?.let { peopleMap[it] }
                            val isExpanded = mobileExpandedSavedId == sw.id
                            val ownerLabel = sw.ownerName.ifBlank { linkedPerson?.name ?: "Shared" }

                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            ExpressiveAvatar(
                                                name = linkedPerson?.name ?: ownerLabel,
                                                shapeName = linkedPerson?.avatarShape ?: "squircle",
                                                tone = linkedPerson?.avatarTone ?: "teal",
                                                size = 40.dp
                                            )
                                            Column {
                                                Text(
                                                    "$ownerLabel — ${sw.title}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.5.sp
                                                )
                                                Text(
                                                    buildString {
                                                        append(if (sw.type == "live") "● Live Synced" else "External Registry")
                                                        if (linkedPerson != null) append(" • ${linkedPerson.name}")
                                                    },
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FilledTonalButton(
                                                onClick = {
                                                    viewModel.selectSavedWishlist(sw.id)
                                                    mobileExpandedSavedId = if (isExpanded) null else sw.id
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    if (isExpanded) "Hide"
                                                    else if (sw.type == "live") "Browse"
                                                    else "Details",
                                                    fontSize = 12.sp
                                                )
                                            }
                                            IconButton(onClick = { viewModel.openSaveWishlistSheet(editing = sw) }) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                                            }
                                        }
                                    }

                                    if (isExpanded) {
                                        SavedWishlistDetailPane(
                                            savedWishlist = sw,
                                            viewModel = viewModel,
                                            compactInline = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // SUB-VIEW 3: LINKS I SHARED (OUTBOUND GROUP LINKS)
                // -------------------------------------------------------------
                WishlistHubTab.OUTBOUND -> {
                    item {
                        Card(
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "LINKS I SHARED",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${dataset.sharedLinks.size} Outbound Link${if (dataset.sharedLinks.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Lock,
                                            null,
                                            Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Claim statuses stay strictly hidden from you.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Button(
                                    onClick = {
                                        shareSheetInitialEditingLink = null
                                        shareSheetInitialTab = "create"
                                        showShareDialog = true
                                    }
                                ) {
                                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("New Link")
                                }
                            }
                        }
                    }

                    if (dataset.sharedLinks.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("No Outbound Links Yet", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Create separate live checklist links for Family, Friends, or Secret Santa from your wishlist.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = {
                                            shareSheetInitialEditingLink = null
                                            shareSheetInitialTab = "create"
                                            showShareDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Create First Share Link")
                                    }
                                }
                            }
                        }
                    } else {
                        val effectiveWeb = prefs.webAppUrl?.takeIf { it.isNotBlank() }
                            ?: cloudState.exportableConfig?.projectId?.takeIf { it.isNotBlank() }?.let { "https://$it.web.app" }
                            ?: ""

                        items(dataset.sharedLinks, key = { it.id }) { link ->
                            val activeCount = remember(link, sortedWishes) {
                                link.computeActiveWishes(sortedWishes).size
                            }
                            val shareUrl = remember(link.id, cloudState.exportableConfig, effectiveWeb) {
                                if (effectiveWeb.isBlank()) ""
                                else FirebaseSyncManager.buildSharedWishlistUrl(
                                    link.id,
                                    cloudState.exportableConfig,
                                    effectiveWeb
                                )
                            }
                            val isShowingQr = outboundQrLinkId == link.id

                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                                    shareSheetInitialEditingLink = link
                                                    shareSheetInitialTab = "create"
                                                    showShareDialog = true
                                                }
                                            ) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Edit group link")
                                            }
                                            IconButton(onClick = { confirmRevokeOutbound = link }) {
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
                                                outboundQrLinkId = if (isShowingQr) null else link.id
                                            }
                                        ) {
                                            Icon(Icons.Rounded.QrCode2, null, Modifier.size(15.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text(if (isShowingQr) "Hide QR" else "QR")
                                        }

                                        if (shareUrl.isNotBlank()) {
                                            TextButton(
                                                onClick = { runCatching { uriHandler.openUri(shareUrl) } }
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
    }

    // Share & Manage Group Links Sheet
    if (showShareDialog) {
        ShareWishlistSheet(
            viewModel = viewModel,
            wishes = wantedWishes,
            allWishes = sortedWishes,
            sharedLinks = dataset.sharedLinks,
            initialTab = shareSheetInitialTab,
            initialEditingLink = shareSheetInitialEditingLink,
            onDismiss = {
                showShareDialog = false
                shareSheetInitialEditingLink = null
                shareSheetInitialTab = null
            }
        )
    }

    confirmRevokeOutbound?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmRevokeOutbound = null },
            title = { Text("Revoke \"${target.title}\"?") },
            text = { Text("People with this link will no longer be able to view or claim items on this group checklist.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSharedWishlistLink(target)
                        confirmRevokeOutbound = null
                    }
                ) {
                    Text("Revoke Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevokeOutbound = null }) {
                    Text("Cancel")
                }
            }
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
    initialTab: String? = null,
    initialEditingLink: SharedWishlistLink? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val cloudState by viewModel.cloudState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var activeTab by remember(initialTab, initialEditingLink) {
        val start = when {
            initialEditingLink != null -> "create"
            initialTab != null -> initialTab
            wishes.isEmpty() && sharedLinks.isNotEmpty() -> "manage"
            else -> "create"
        }
        mutableStateOf(start)
    }

    var editingLink by remember(initialEditingLink) { mutableStateOf(initialEditingLink) }
    var groupTitle by rememberSaveable(initialEditingLink?.id) {
        mutableStateOf(initialEditingLink?.title ?: "Family & Friends Wishlist")
    }
    var ownerName by remember(prefs.ownerDisplayName, cloudState.userLabel, initialEditingLink) {
        mutableStateOf(
            initialEditingLink?.ownerName?.takeIf { it.isNotBlank() }
                ?: prefs.ownerDisplayName
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
    var selectedIds by remember(initialEditingLink, wishes) {
        val initSet = if (initialEditingLink != null) {
            if (initialEditingLink.includeAllActive) wishes.map { it.id }.toSet()
            else initialEditingLink.wishIds.toSet()
        } else {
            wishes.map { it.id }.toSet()
        }
        mutableStateOf(initSet)
    }
    var includeAllActive by rememberSaveable(initialEditingLink?.id) {
        mutableStateOf(initialEditingLink?.includeAllActive ?: false)
    }
    var includeNotes by rememberSaveable(initialEditingLink?.id) {
        mutableStateOf(initialEditingLink?.includeNotes ?: true)
    }
    var includeUrls by rememberSaveable(initialEditingLink?.id) {
        mutableStateOf(initialEditingLink?.includeUrls ?: true)
    }

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
