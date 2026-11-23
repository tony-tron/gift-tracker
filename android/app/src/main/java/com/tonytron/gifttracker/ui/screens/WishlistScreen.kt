package com.tonytron.gifttracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonytron.gifttracker.data.model.WishItem
import com.tonytron.gifttracker.ui.GiftTrackerViewModel

@Composable
fun WishlistScreen(
    viewModel: GiftTrackerViewModel,
    contentPadding: PaddingValues
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val context = LocalContext.current
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

    // Share Selection Dialog state
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
                            "Keep ideas for yourself handy and share a selected or full list via Android Share Sheet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        enabled = wantedWishes.isNotEmpty(),
                        onClick = { showShareDialog = true }
                    ) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
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

    // Share Selection Dialog
    if (showShareDialog) {
        ShareWishlistDialog(
            wishes = wantedWishes,
            onDismiss = { showShareDialog = false }
        )
    }
}

@Composable
private fun ShareWishlistDialog(
    wishes: List<WishItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedIds by remember { mutableStateOf(wishes.map { it.id }.toSet()) }
    var includeNotes by rememberSaveable { mutableStateOf(true) }
    var includeUrls by rememberSaveable { mutableStateOf(true) }

    val allSelected = selectedIds.size == wishes.size && wishes.isNotEmpty()

    val formattedShareText = remember(selectedIds, includeNotes, includeUrls, wishes) {
        val selected = wishes.filter { it.id in selectedIds }
        if (selected.isEmpty()) ""
        else {
            val lines = selected.mapIndexed { idx, w ->
                buildString {
                    append("${idx + 1}. ${w.title}")
                    if (includeUrls && w.url.isNotBlank()) append(" (${w.url})")
                    if (includeNotes && w.notes.isNotBlank()) append("\n   Note: ${w.notes}")
                }
            }
            "🎁 My Gift Wishlist (${selected.size} items):\n\n${lines.joinToString("\n\n")}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Share Wishlist", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Select which wishes to include in your shared list:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Toolbar row: Select All + Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            selectedIds = if (allSelected) emptySet() else wishes.map { it.id }.toSet()
                        }
                    ) {
                        Text(if (allSelected) "Deselect All" else "Select All (${selectedIds.size}/${wishes.size})", fontSize = 12.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeNotes, onCheckedChange = { includeNotes = it })
                        Text("Notes", fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Checkbox(checked = includeUrls, onCheckedChange = { includeUrls = it })
                        Text("Links", fontSize = 12.sp)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(wishes, key = { it.id }) { wish ->
                        val isChecked = wish.id in selectedIds
                        Surface(
                            onClick = {
                                selectedIds = if (isChecked) selectedIds - wish.id else selectedIds + wish.id
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
                                        selectedIds = if (isChecked) selectedIds - wish.id else selectedIds + wish.id
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
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Gift Wishlist", formattedShareText))
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }

                Button(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "My Gift Wishlist")
                            putExtra(Intent.EXTRA_TEXT, formattedShareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Wishlist"))
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share (${selectedIds.size})")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
