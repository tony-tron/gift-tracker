package com.twintitanium.gifttracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twintitanium.gifttracker.data.model.NavTab
import com.twintitanium.gifttracker.data.model.PublicSharedWishlistDoc
import com.twintitanium.gifttracker.data.model.PublishedWishItem
import com.twintitanium.gifttracker.data.model.SavedWishlist
import com.twintitanium.gifttracker.data.model.SharedWishClaim
import com.twintitanium.gifttracker.ui.GiftTrackerViewModel
import com.twintitanium.gifttracker.ui.components.ExpressiveAvatar
import com.twintitanium.gifttracker.ui.components.onSecondaryClick
import com.twintitanium.gifttracker.ui.components.pointerHandCursor
import com.twintitanium.gifttracker.ui.sheets.AddToTrackerSheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedWishlistDetailPane(
    savedWishlist: SavedWishlist,
    viewModel: GiftTrackerViewModel,
    compactInline: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val cloudState by viewModel.cloudState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    val linkedPerson = remember(dataset.people, savedWishlist.personId) {
        dataset.people.firstOrNull { it.id == savedWishlist.personId }
    }

    val shareId = if (savedWishlist.type == "live") savedWishlist.shareId.orEmpty() else ""
    val (viewerApp, viewerUid) = remember(shareId, savedWishlist.projectKey, cloudState.connectedProjectId, cloudState.userUid) {
        if (shareId.isBlank()) null to null
        else viewModel.repository.resolveViewerAppAndUid(savedWishlist.projectKey)
    }

    var loading by remember(savedWishlist.id, viewerApp) {
        mutableStateOf(viewerApp != null && shareId.isNotBlank())
    }
    var liveDoc by remember(savedWishlist.id) { mutableStateOf<PublicSharedWishlistDoc?>(null) }
    var claims by remember(savedWishlist.id) { mutableStateOf<Map<String, SharedWishClaim>>(emptyMap()) }
    var errorMsg by remember(savedWishlist.id) { mutableStateOf<String?>(null) }

    val effectiveGiverName = remember(prefs.giverDisplayName, prefs.ownerDisplayName, cloudState.userLabel) {
        prefs.giverDisplayName
            ?: prefs.ownerDisplayName
            ?: cloudState.userLabel?.substringBefore("@")?.substringBefore(" ")
            ?: ""
    }

    var editingGiverName by remember { mutableStateOf(false) }
    var giverNameDraft by remember(effectiveGiverName) { mutableStateOf(effectiveGiverName) }

    var pendingPromptAction by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // wishId to claimImmediately
    var pendingNameInput by remember { mutableStateOf("") }

    var noteTargetClaim by remember { mutableStateOf<SharedWishClaim?>(null) }
    var noteDraft by remember { mutableStateOf("") }

    var trackerSheetWish by remember { mutableStateOf<PublishedWishItem?>(null) }
    var filter by rememberSaveable(savedWishlist.id) { mutableStateOf("all") } // all | available | mine

    DisposableEffect(viewerApp, shareId) {
        if (viewerApp == null || shareId.isBlank()) {
            onDispose { }
        } else {
            loading = true
            val reg = viewModel.repository.firebaseManager.subscribeToPublicWishlist(
                viewerApp = viewerApp,
                shareId = shareId,
                onDoc = { doc ->
                    liveDoc = doc
                    errorMsg = null
                    loading = false
                },
                onError = { err ->
                    errorMsg = err.localizedMessage
                    loading = false
                }
            )
            onDispose { runCatching { reg.remove() } }
        }
    }

    val isOwnerPreview = remember(liveDoc, viewerUid) {
        liveDoc != null && !viewerUid.isNullOrBlank() && viewerUid == liveDoc?.ownerUid
    }

    DisposableEffect(viewerApp, shareId, liveDoc?.ownerUid, isOwnerPreview) {
        if (viewerApp == null || shareId.isBlank() || liveDoc == null || isOwnerPreview) {
            onDispose { }
        } else {
            val reg = viewModel.repository.firebaseManager.subscribeToPublicWishlistClaims(
                viewerApp = viewerApp,
                shareId = shareId,
                onClaims = { claimsMap -> claims = claimsMap }
            )
            onDispose { runCatching { reg.remove() } }
        }
    }

    fun isClaimMine(claim: SharedWishClaim?): Boolean {
        if (claim == null) return false
        val tok = prefs.claimerToken
        if (tok.isNotBlank() && claim.claimerToken == tok) return true
        if (tok.isNotBlank() && tok in claim.researcherTokens) return true
        val g = effectiveGiverName.trim().lowercase()
        if (g.isNotBlank() && claim.claimedBy.trim().lowercase() == g) return true
        if (g.isNotBlank() && claim.researchers.any { it.trim().lowercase() == g }) return true
        return false
    }

    val sortedItems = remember(liveDoc) {
        liveDoc?.items?.sortedBy { it.order }.orEmpty()
    }

    val availableCount = remember(sortedItems, claims) {
        sortedItems.count { item ->
            val c = claims[item.id]
            c == null || c.status == "researching"
        }
    }
    val mineCount = remember(sortedItems, claims, prefs.claimerToken, effectiveGiverName) {
        sortedItems.count { item -> isClaimMine(claims[item.id]) }
    }

    val displayedItems = remember(sortedItems, claims, filter, isOwnerPreview, prefs.claimerToken, effectiveGiverName) {
        if (isOwnerPreview || filter == "all") sortedItems
        else if (filter == "available") sortedItems.filter {
            val c = claims[it.id]
            c == null || c.status == "researching"
        } else {
            sortedItems.filter { isClaimMine(claims[it.id]) }
        }
    }

    val ownerDisplay = liveDoc?.ownerName?.takeIf { it.isNotBlank() }
        ?: savedWishlist.ownerName.takeIf { it.isNotBlank() }
        ?: linkedPerson?.name
        ?: "the wishlist owner"

    @Composable
    fun InnerContent() {
        Column(
            modifier = if (compactInline) Modifier.padding(top = 12.dp) else Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 2-Pane Header
            if (!compactInline) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        ExpressiveAvatar(
                            name = linkedPerson?.name ?: ownerDisplay,
                            shapeName = linkedPerson?.avatarShape ?: "clover",
                            tone = linkedPerson?.avatarTone ?: "teal",
                            size = 46.dp
                        )
                        Column {
                            Text(
                                text = liveDoc?.title ?: savedWishlist.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "From $ownerDisplay",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (linkedPerson != null) {
                                    Text("•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${linkedPerson.name} Profile →",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .pointerHandCursor()
                                            .clickable {
                                                viewModel.selectPerson(linkedPerson.id)
                                                viewModel.setActiveTab(NavTab.PEOPLE)
                                            }
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (savedWishlist.url.isNotBlank()) {
                            IconButton(onClick = { runCatching { uriHandler.openUri(savedWishlist.url) } }) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open link in browser")
                            }
                        }
                        IconButton(onClick = { viewModel.openSaveWishlistSheet(editing = savedWishlist) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit saved wishlist")
                        }
                        IconButton(onClick = { viewModel.deleteSavedWishlistWithUndo(savedWishlist.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove saved wishlist")
                        }
                    }
                }

                if (linkedPerson != null) {
                    AssistChip(
                        onClick = {
                            viewModel.selectPerson(linkedPerson.id)
                            viewModel.setActiveTab(NavTab.PEOPLE)
                        },
                        label = { Text("Linked to ${linkedPerson.name} (Sizes & Budget) →", fontSize = 12.sp) }
                    )
                }
            }

            if (savedWishlist.notes.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your private note: ${savedWishlist.notes}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (savedWishlist.type == "external") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "External wishlist or registry hosted outside Gift Tracker. Open it in your browser to browse items, then log any gift you pick into Occasions so your budgets stay accurate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            savedWishlist.url,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { runCatching { uriHandler.openUri(savedWishlist.url) } }) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open External Registry")
                            }
                            FilledTonalButton(
                                onClick = {
                                    viewModel.openNewGiftComposer(
                                        personId = savedWishlist.personId,
                                        initialUrl = savedWishlist.url
                                    )
                                }
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Log Gift from List")
                            }
                        }
                    }
                }
            } else if (viewerApp == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Connect Firebase Cloud Sync in Settings (or save the full link with ?k=...) to view live claims natively, or open in your browser.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { runCatching { uriHandler.openUri(savedWishlist.url) } }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open in Browser")
                        }
                    }
                }
            } else if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Loading live checklist...", fontSize = 13.sp)
                    }
                }
            } else if (errorMsg != null || liveDoc == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg ?: "This shared wishlist was not found or may have been revoked by its owner.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // Privacy Banner + Giver Identity
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (isOwnerPreview) "Owner Preview: claims stay hidden from you."
                                    else "Claims & group notes are strictly hidden from $ownerDisplay.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!isOwnerPreview && !editingGiverName) {
                                AssistChip(
                                    onClick = {
                                        giverNameDraft = effectiveGiverName
                                        editingGiverName = true
                                    },
                                    label = {
                                        Text(
                                            if (effectiveGiverName.isNotBlank()) "As: $effectiveGiverName"
                                            else "Set Your Name",
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                            }
                        }

                        if (editingGiverName) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = giverNameDraft,
                                    onValueChange = { giverNameDraft = it },
                                    label = { Text("Your Name (for other gift-givers)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        viewModel.setGiverDisplayName(giverNameDraft)
                                        editingGiverName = false
                                    }
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }

                // Filter Chips
                if (!isOwnerPreview && sortedItems.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = filter == "all",
                            onClick = { filter = "all" },
                            label = { Text("All (${sortedItems.size})", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = filter == "available",
                            onClick = { filter = "available" },
                            label = { Text("Available ($availableCount)", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = filter == "mine",
                            onClick = { filter = "mine" },
                            label = { Text("Claimed by Me ($mineCount)", fontSize = 12.sp) }
                        )
                    }
                }

                // Items List
                if (displayedItems.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (sortedItems.isEmpty()) "$ownerDisplay hasn't added active wishes yet."
                            else "No items match this filter.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                } else {
                    displayedItems.forEach { item ->
                        val claim = claims[item.id]
                        val mine = isClaimMine(claim)
                        val isResearching = claim?.status == "researching"
                        val isClaimedByOther = claim != null && !isResearching && !mine
                        val trackedGift = dataset.gifts.firstOrNull {
                            it.sourceWishlistShareId == shareId && it.sourceWishId == item.id
                        }
                        var showItemMenu by remember { mutableStateOf(false) }

                        Box {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSecondaryClick(enabled = !isOwnerPreview) { showItemMenu = true }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    textDecoration = if (isClaimedByOther) TextDecoration.LineThrough else null,
                                                    color = if (isClaimedByOther) MaterialTheme.colorScheme.onSurfaceVariant
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (item.priority == "high") {
                                                    Surface(
                                                        shape = RoundedCornerShape(99.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text(
                                                            "Top Pick",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            if (item.notes.isNotBlank()) {
                                                Text(
                                                    item.notes,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (!isOwnerPreview && isResearching && claim != null) {
                                                Text(
                                                    "👀 Researching: ${claim.claimedBy}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }

                                            if (!isOwnerPreview && claim != null && claim.giverNote.isNotBlank()) {
                                                Text(
                                                    "💬 Group note: \"${claim.giverNote}\"",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    // Item Actions Row
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (item.url.isNotBlank()) {
                                            AssistChip(
                                                onClick = { runCatching { uriHandler.openUri(item.url) } },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(14.dp)) },
                                                label = { Text("Link", fontSize = 11.sp) }
                                            )
                                        }

                                        if (!isOwnerPreview) {
                                            when {
                                                isClaimedByOther && claim != null -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                                    ) {
                                                        Text(
                                                            text = if (claim.purchased) "🛍️ Bought by ${claim.claimedBy}"
                                                            else "Claimed by ${claim.claimedBy}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                                mine && claim != null && !isResearching -> {
                                                    FilledTonalButton(
                                                        onClick = {
                                                            viewModel.togglePurchasedOnPublicWish(
                                                                shareId = shareId,
                                                                projectKey = savedWishlist.projectKey,
                                                                existingClaim = claim
                                                            )
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(
                                                            if (claim.purchased) Icons.Rounded.ShoppingBag else Icons.Rounded.Check,
                                                            null,
                                                            Modifier.size(14.dp)
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                            if (claim.purchased) "Purchased by You" else "Claimed by You",
                                                            fontSize = 11.sp
                                                        )
                                                    }

                                                    TextButton(
                                                        onClick = {
                                                            noteTargetClaim = claim
                                                            noteDraft = claim.giverNote
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Group Note", fontSize = 11.sp)
                                                    }

                                                    TextButton(
                                                        onClick = {
                                                            viewModel.releasePublicWishClaim(
                                                                shareId = shareId,
                                                                projectKey = savedWishlist.projectKey,
                                                                wishId = item.id
                                                            )
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.Refresh, null, Modifier.size(13.dp))
                                                        Spacer(Modifier.width(3.dp))
                                                        Text("Release", fontSize = 11.sp)
                                                    }
                                                }
                                                else -> {
                                                    if (mine && isResearching) {
                                                        FilledTonalButton(
                                                            onClick = {
                                                                viewModel.releasePublicWishClaim(
                                                                    shareId = shareId,
                                                                    projectKey = savedWishlist.projectKey,
                                                                    wishId = item.id
                                                                )
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                        ) {
                                                            Icon(Icons.Rounded.Visibility, null, Modifier.size(14.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("Stop Researching", fontSize = 11.sp)
                                                        }
                                                    } else {
                                                        FilledTonalButton(
                                                            onClick = {
                                                                if (effectiveGiverName.isBlank()) {
                                                                    pendingNameInput = ""
                                                                    pendingPromptAction = item.id to false
                                                                } else {
                                                                    viewModel.claimOrResearchPublicWish(
                                                                        shareId = shareId,
                                                                        projectKey = savedWishlist.projectKey,
                                                                        wishId = item.id,
                                                                        giverName = effectiveGiverName,
                                                                        claimImmediately = false,
                                                                        ownerDisplay = ownerDisplay
                                                                    )
                                                                }
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                        ) {
                                                            Icon(Icons.Rounded.Visibility, null, Modifier.size(14.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("Research", fontSize = 11.sp)
                                                        }
                                                    }

                                                    Button(
                                                        onClick = {
                                                            if (effectiveGiverName.isBlank()) {
                                                                pendingNameInput = ""
                                                                pendingPromptAction = item.id to true
                                                            } else {
                                                                viewModel.claimOrResearchPublicWish(
                                                                    shareId = shareId,
                                                                    projectKey = savedWishlist.projectKey,
                                                                    wishId = item.id,
                                                                    giverName = effectiveGiverName,
                                                                    claimImmediately = true,
                                                                    ownerDisplay = ownerDisplay
                                                                )
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.Check, null, Modifier.size(14.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Claim", fontSize = 11.sp)
                                                    }
                                                }
                                            }

                                            if (!isClaimedByOther) {
                                                if (trackedGift != null) {
                                                    FilledTonalButton(
                                                        onClick = {
                                                            viewModel.setActiveTab(
                                                                if (trackedGift.eventId != null) NavTab.EVENTS else NavTab.IDEAS
                                                            )
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("✓ In Tracker →", fontSize = 11.sp)
                                                    }
                                                } else {
                                                    FilledTonalButton(
                                                        onClick = { trackerSheetWish = item },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.Add, null, Modifier.size(14.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Track Gift", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showItemMenu,
                                onDismissRequest = { showItemMenu = false }
                            ) {
                                if (!isClaimedByOther) {
                                    DropdownMenuItem(
                                        text = { Text(if (trackedGift != null) "View in Tracker" else "Add to Gift Tracker") },
                                        onClick = {
                                            showItemMenu = false
                                            if (trackedGift != null) {
                                                viewModel.setActiveTab(
                                                    if (trackedGift.eventId != null) NavTab.EVENTS else NavTab.IDEAS
                                                )
                                            } else {
                                                trackerSheetWish = item
                                            }
                                        }
                                    )
                                }
                                if (item.url.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("Open Product Link") },
                                        onClick = {
                                            showItemMenu = false
                                            runCatching { uriHandler.openUri(item.url) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (compactInline) {
        Column(modifier = modifier.fillMaxWidth()) {
            InnerContent()
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            InnerContent()
        }
    }

    // Prompt for Giver Name on first Claim/Research tap
    pendingPromptAction?.let { (wishId, claimImm) ->
        AlertDialog(
            onDismissRequest = { pendingPromptAction = null },
            title = { Text("What's your name?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Shown to other gift-givers so nobody buys duplicates — strictly hidden from $ownerDisplay.",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = pendingNameInput,
                        onValueChange = { pendingNameInput = it },
                        placeholder = { Text("e.g., Sarah, Brother...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = pendingNameInput.isNotBlank(),
                    onClick = {
                        val chosen = pendingNameInput.trim()
                        pendingPromptAction = null
                        viewModel.claimOrResearchPublicWish(
                            shareId = shareId,
                            projectKey = savedWishlist.projectKey,
                            wishId = wishId,
                            giverName = chosen,
                            claimImmediately = claimImm,
                            ownerDisplay = ownerDisplay
                        )
                    }
                ) {
                    Text(if (claimImm) "Claim Item" else "Mark Researching")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPromptAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Secret Giver Note Dialog
    noteTargetClaim?.let { claimTarget ->
        AlertDialog(
            onDismissRequest = { noteTargetClaim = null },
            title = { Text("Secret Group Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Leave a coordination note for other gift-givers (strictly hidden from $ownerDisplay).",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        placeholder = { Text("e.g., Splitting this with Sam!") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveGiverNoteOnPublicWish(
                            shareId = shareId,
                            projectKey = savedWishlist.projectKey,
                            existingClaim = claimTarget,
                            note = noteDraft
                        ) {
                            noteTargetClaim = null
                        }
                    }
                ) {
                    Text("Save Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteTargetClaim = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add to Tracker Sheet
    trackerSheetWish?.let { wishItem ->
        if (shareId.isNotBlank()) {
            AddToTrackerSheet(
                wish = wishItem,
                shareId = shareId,
                projectKey = savedWishlist.projectKey,
                wishlistOwnerName = ownerDisplay,
                preferredPersonId = savedWishlist.personId,
                initialGiverName = effectiveGiverName,
                viewModel = viewModel,
                onDismiss = { trackerSheetWish = null }
            )
        }
    }
}
