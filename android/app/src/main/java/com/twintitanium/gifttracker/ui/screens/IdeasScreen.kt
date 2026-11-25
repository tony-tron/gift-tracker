package com.twintitanium.gifttracker.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twintitanium.gifttracker.ui.GiftTrackerViewModel
import com.twintitanium.gifttracker.ui.components.ExpressiveAvatar
import com.twintitanium.gifttracker.ui.components.MoveIdeaSplitButton

@Composable
fun IdeasScreen(
    viewModel: GiftTrackerViewModel,
    isWide: Boolean,
    contentPadding: PaddingValues
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    var modeFilter by rememberSaveable { mutableStateOf("all") } // all | ideas | stockpile | pool
    var personFilter by rememberSaveable { mutableStateOf("all") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val personMap = remember(dataset.people) { dataset.people.associateBy { it.id } }
    val primaryActiveEvent = remember(dataset.events) {
        dataset.events.firstOrNull { it.id == "2026-christmas" }
            ?: dataset.events.firstOrNull { !it.isArchived }
            ?: dataset.events.firstOrNull()
    }

    val backlogItems = remember(dataset.gifts) { dataset.gifts.filter { it.eventId == null } }

    val filteredIdeas = remember(backlogItems, modeFilter, personFilter, searchQuery, personMap) {
        backlogItems.filter { g ->
            if (modeFilter == "ideas" && g.saveForLater) return@filter false
            if (modeFilter == "stockpile" && !g.saveForLater) return@filter false
            if (modeFilter == "pool" && g.personId != null) return@filter false
            if (personFilter != "all" && g.personId != personFilter) return@filter false
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.lowercase()
                val pName = g.personId?.let { personMap[it]?.name?.lowercase() }.orEmpty()
                g.title.lowercase().contains(q) || g.store.lowercase().contains(q) || pName.contains(q)
            } else true
        }
    }

    val cols = if (isWide) GridCells.Adaptive(minSize = 320.dp) else GridCells.Fixed(1)

    LazyVerticalGrid(
        columns = cols,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                "YEAR-ROUND BRAINSTORMING & STASH",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${backlogItems.size} Gift Ideas Ready",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "Share links straight from Chrome/Amazon or use the Split Button to assign to an occasion.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                viewModel.openNewGiftComposer(
                                    personId = personFilter.takeIf { it != "all" },
                                    eventId = null
                                )
                            }
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Idea")
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search ideas, stores, or recipients...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = modeFilter == "all",
                        onClick = { modeFilter = "all" },
                        label = { Text("All (${backlogItems.size})") }
                    )
                    FilterChip(
                        selected = modeFilter == "ideas",
                        onClick = { modeFilter = "ideas" },
                        label = { Text("💡 Unbought (${backlogItems.count { !it.saveForLater }})") }
                    )
                    FilterChip(
                        selected = modeFilter == "stockpile",
                        onClick = { modeFilter = "stockpile" },
                        label = { Text("📦 Bought & Saved (${backlogItems.count { it.saveForLater }})") }
                    )
                    FilterChip(
                        selected = modeFilter == "pool",
                        onClick = {
                            modeFilter = "pool"
                            personFilter = "all"
                        },
                        label = { Text("🎯 Not Sure Who (${backlogItems.count { it.personId == null }})") }
                    )
                }

                if (dataset.people.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = personFilter == "all",
                            onClick = { personFilter = "all" },
                            label = { Text("Everyone") }
                        )
                        dataset.people.forEach { p ->
                            val c = backlogItems.count { it.personId == p.id }
                            if (c > 0) {
                                FilterChip(
                                    selected = personFilter == p.id,
                                    onClick = {
                                        personFilter = if (personFilter == p.id) "all" else p.id
                                        if (modeFilter == "pool") modeFilter = "all"
                                    },
                                    label = { Text("${p.name} ($c)") }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (filteredIdeas.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No gift ideas match your filter", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Jot down an idea or use Android's Share Sheet from Chrome, Amazon, or Etsy!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredIdeas, key = { it.id }) { idea ->
                val person = idea.personId?.let { personMap[it] }
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                                    name = person?.name ?: "?",
                                    shapeName = person?.avatarShape ?: "clover",
                                    tone = person?.avatarTone ?: "amber",
                                    size = 38.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        idea.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildList {
                                            add(person?.name ?: "Not sure who yet")
                                            if (idea.store.isNotBlank()) add(idea.store)
                                            if (idea.saveForLater) add("📦 Bought & Saved")
                                        }.joinToString(" • "),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val price = idea.displayPrice()
                                if (price.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(99.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = price,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.openEditGiftComposer(idea) }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit idea", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (idea.notes.isNotBlank()) {
                            Text(
                                idea.notes,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (idea.url.isNotBlank()) {
                                AssistChip(
                                    onClick = { runCatching { uriHandler.openUri(idea.url) } },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(15.dp)) },
                                    label = { Text(idea.store.ifBlank { "Link" }, fontSize = 12.sp) }
                                )
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }

                            val targetEv = primaryActiveEvent
                            if (targetEv != null) {
                                MoveIdeaSplitButton(
                                    primaryLabel = "${targetEv.emoji} ${targetEv.name}",
                                    onPrimaryClick = {
                                        viewModel.moveGiftToEvent(idea.id, targetEv.id, idea.personId)
                                    },
                                    onMoreOptionsClick = {
                                        viewModel.openMoveGiftSheet(idea)
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
