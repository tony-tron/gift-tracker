package com.tonytron.gifttracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
    var loggingWishId by rememberSaveable { mutableStateOf<String?>(null) }
    var fromInput by rememberSaveable { mutableStateOf("") }
    var whenInput by rememberSaveable { mutableStateOf("Christmas 2026") }

    val wantedWishes = remember(dataset.wishes) { dataset.wishes.filter { !it.received } }
    val receivedWishes = remember(dataset.wishes) { dataset.wishes.filter { it.received } }
    val displayed = if (filter == "wanted") wantedWishes else receivedWishes

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                            "Keep ideas for yourself handy and share a clean list via Android Share Sheet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        enabled = wantedWishes.isNotEmpty(),
                        onClick = {
                            val lines = wantedWishes.mapIndexed { idx, w ->
                                "${idx + 1}. ${w.title}${if (w.url.isNotBlank()) " (${w.url})" else ""}"
                            }
                            val formatted = "🎁 My Gift Wishlist:\n\n${lines.joinToString("\n")}"
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "My Gift Wishlist")
                                putExtra(Intent.EXTRA_TEXT, formatted)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Wishlist"))
                        }
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
                        Button(
                            enabled = newTitle.isNotBlank(),
                            onClick = {
                                viewModel.addWish(newTitle, newUrl)
                                newTitle = ""
                                newUrl = ""
                            }
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
            }
        }

        // Filter Chips
        item {
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
            items(displayed, key = { it.id }) { wish ->
                val isLogging = loggingWishId == wish.id
                ElevatedCard(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

                                IconButton(onClick = { viewModel.deleteWish(wish.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete wish")
                                }
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
