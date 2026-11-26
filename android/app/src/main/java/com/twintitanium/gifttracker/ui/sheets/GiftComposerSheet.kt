package com.twintitanium.gifttracker.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twintitanium.gifttracker.data.model.GiftItem
import com.twintitanium.gifttracker.ui.GiftComposerPreset
import com.twintitanium.gifttracker.ui.GiftTrackerViewModel
import com.twintitanium.gifttracker.ui.components.GiftStatusConnectedGroup
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GiftComposerSheet(
    preset: GiftComposerPreset,
    viewModel: GiftTrackerViewModel,
    onDismiss: () -> Unit
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val editing = preset.editingGift
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var title by remember(preset) { mutableStateOf(editing?.title ?: preset.initialTitle) }
    var url by remember(preset) { mutableStateOf(editing?.url ?: preset.initialUrl) }
    var store by remember(preset) { mutableStateOf(editing?.store ?: preset.initialStore) }
    var costStr by remember(preset) {
        val init = when {
            editing != null && editing.cost > 0 -> {
                if (editing.cost % 1.0 == 0.0) editing.cost.toInt().toString() else editing.cost.toString()
            }
            editing?.costLabel != null -> editing.costLabel
            preset.initialCost != null && preset.initialCost > 0 -> preset.initialCost.toString()
            else -> ""
        }
        mutableStateOf(init)
    }
    var personId by remember(preset) { mutableStateOf(editing?.personId ?: preset.initialPersonId) }
    var destination by remember(preset) {
        val dest = when {
            editing != null -> editing.eventId ?: if (editing.saveForLater) "stockpile" else "ideas"
            preset.initialEventId != null -> preset.initialEventId
            else -> "ideas"
        }
        mutableStateOf(dest)
    }
    var tracking by remember(preset) { mutableStateOf(editing?.tracking.orEmpty()) }
    var notes by remember(preset) { mutableStateOf(editing?.notes.orEmpty()) }
    var bought by remember(preset) { mutableStateOf(editing?.bought ?: (preset.initialEventId != null)) }
    var received by remember(preset) { mutableStateOf(editing?.received ?: false) }
    var wrapped by remember(preset) { mutableStateOf(editing?.wrapped ?: false) }
    var scraping by remember { mutableStateOf(false) }

    suspend fun runAutoFill(targetUrl: String, showFailureFeedback: Boolean = false) {
        if (targetUrl.isBlank()) return
        scraping = true
        val res = viewModel.scrapeUrl(targetUrl)
        if (res.title.isNotBlank() && (title.isBlank() || title.trim() == targetUrl.trim())) title = res.title
        if (res.store.isNotBlank() && store.isBlank()) store = res.store
        if (res.price != null && costStr.isBlank()) costStr = res.price.toString()
        if (res.url.isNotBlank()) url = res.url
        if (showFailureFeedback && res.title.isBlank() && res.store.isBlank() && res.price == null) {
            viewModel.showMessage("Couldn't auto-fill details from that link")
        }
        scraping = false
    }

    // Auto-scrape when launched from Android Share Sheet with a URL
    LaunchedEffect(preset.initialUrl) {
        if (editing == null && preset.initialUrl.isNotBlank()) {
            runAutoFill(preset.initialUrl)
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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editing != null) "Edit Gift Details" else "Add Gift or Idea",
                    style = MaterialTheme.typography.titleLarge
                )
                if (editing != null) {
                    IconButton(onClick = { viewModel.deleteGiftWithUndo(editing.id) }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete gift",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // URL + Auto-fill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Product Link (optional)") },
                    placeholder = { Text("https://amazon.com/...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    enabled = url.isNotBlank() && !scraping,
                    onClick = { scope.launch { runAutoFill(url, showFailureFeedback = true) } }
                ) {
                    if (scraping) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Auto-fill", fontSize = 12.sp)
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Gift Name *") },
                placeholder = { Text("e.g., Water Bottle Bag, Board Game...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Destination Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Destination", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = destination == "ideas",
                        onClick = { destination = "ideas" },
                        label = { Text("💡 Gift Ideas") }
                    )
                    if (editing == null) {
                        FilterChip(
                            selected = destination == "wishlist",
                            onClick = { destination = "wishlist" },
                            label = { Text("⭐ My Wishlist") }
                        )
                        FilterChip(
                            selected = destination == "shared-wishlist",
                            onClick = { destination = "shared-wishlist" },
                            label = { Text("📥 Shared With Me") }
                        )
                    }
                    FilterChip(
                        selected = destination == "stockpile",
                        onClick = { destination = "stockpile" },
                        label = { Text("📦 Bought & Saved") }
                    )
                    dataset.events.filter { !it.isArchived }.forEach { ev ->
                        FilterChip(
                            selected = destination == ev.id,
                            onClick = { destination = ev.id },
                            label = { Text("${ev.emoji} ${ev.name}") }
                        )
                    }
                }
            }

            if (destination != "wishlist") {
                // Recipient Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (destination == "shared-wishlist") "Link to Person" else "Recipient",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = personId == null,
                            onClick = { personId = null },
                            label = {
                                Text(
                                    if (destination == "shared-wishlist") "Unlinked / Auto"
                                    else "🎯 Not sure who"
                                )
                            }
                        )
                        dataset.people.forEach { p ->
                            FilterChip(
                                selected = personId == p.id,
                                onClick = { personId = p.id },
                                label = { Text(p.name) }
                            )
                        }
                    }
                }

                if (destination != "shared-wishlist") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it },
                        label = { Text("Price / Range") },
                        placeholder = { Text("e.g., 35 or 30-50") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = store,
                        onValueChange = { store = it },
                        label = { Text("Store") },
                        placeholder = { Text("Amazon, Etsy...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (destination != "ideas") {
                    GiftStatusConnectedGroup(
                        bought = if (destination == "stockpile") true else bought,
                        received = received,
                        wrapped = wrapped,
                        onToggleBought = { bought = !bought },
                        onToggleReceived = {
                            received = !received
                            if (received) bought = true
                        },
                        onToggleWrapped = {
                            wrapped = !wrapped
                            if (wrapped) {
                                bought = true
                                received = true
                            }
                        }
                    )
                }

                    OutlinedTextField(
                        value = tracking,
                        onValueChange = { tracking = it },
                        label = { Text("Order / Tracking Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (color, size, gift idea details)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = if (destination == "shared-wishlist") url.isNotBlank() else title.isNotBlank(),
                    onClick = {
                        if (editing == null && destination == "shared-wishlist") {
                            viewModel.saveIncomingWishlist(
                                editing = null,
                                url = url.trim(),
                                title = title.trim(),
                                ownerName = "",
                                personMode = if (personId != null) "existing" else "auto",
                                selectedPersonId = personId,
                                notes = notes.trim(),
                                navigateToHub = true
                            )
                        } else if (editing == null && destination == "wishlist") {
                            viewModel.saveSharedWish(
                                title = title.trim(),
                                url = url.trim(),
                                notes = notes.trim()
                            )
                        } else {
                            val (parsedCost, parsedLabel) = parseCost(costStr)
                            val isIdea = destination == "ideas" || destination == "stockpile"
                            val saveForLater = destination == "stockpile"
                            val eventId = if (isIdea) null else destination

                            val item = GiftItem(
                                id = editing?.id ?: "",
                                title = title.trim(),
                                personId = personId,
                                eventId = eventId,
                                cost = parsedCost,
                                costLabel = parsedLabel,
                                store = store.trim(),
                                url = url.trim(),
                                tracking = tracking.trim(),
                                notes = notes.trim(),
                                bought = if (saveForLater) true else bought,
                                received = received,
                                wrapped = wrapped,
                                saveForLater = saveForLater,
                                createdAt = editing?.createdAt ?: java.time.Instant.now().toString()
                            )
                            viewModel.saveGift(item, isEditing = editing != null)
                        }
                    }
                ) {
                    Text(
                        when {
                            editing != null -> "Save Changes"
                            destination == "shared-wishlist" -> "Save Shared Wishlist"
                            destination == "wishlist" -> "Add to Wishlist"
                            else -> "Save Gift"
                        }
                    )
                }
            }
        }
    }
}

private fun parseCost(raw: String): Pair<Double, String?> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return 0.0 to null
    val rangeMatch = Regex("""^\$?(\d+(?:\.\d+)?)\s*[-–]\s*\$?(\d+(?:\.\d+)?)$""").find(trimmed)
    if (rangeMatch != null) {
        val low = rangeMatch.groupValues[1].toDoubleOrNull() ?: 0.0
        val high = rangeMatch.groupValues[2]
        return low to "$${rangeMatch.groupValues[1]}–$$high"
    }
    val num = trimmed.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
    return num to null
}
