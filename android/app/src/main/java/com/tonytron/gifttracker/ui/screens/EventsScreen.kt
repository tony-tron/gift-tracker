package com.tonytron.gifttracker.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tonytron.gifttracker.data.model.GiftItem
import com.tonytron.gifttracker.data.model.NavTab
import com.tonytron.gifttracker.data.model.Person
import com.tonytron.gifttracker.ui.GiftTrackerViewModel
import com.tonytron.gifttracker.ui.components.ExpressiveAvatar
import com.tonytron.gifttracker.ui.components.ExpressiveBudgetBar
import com.tonytron.gifttracker.ui.components.GiftStatusConnectedGroup

@Composable
fun EventsScreen(
    viewModel: GiftTrackerViewModel,
    isTwoPane: Boolean,
    contentPadding: PaddingValues
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    var personFilter by rememberSaveable { mutableStateOf("all") }
    var statusFilter by rememberSaveable { mutableStateOf("all") } // all | to-buy | to-wrap
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var creatingEvent by rememberSaveable { mutableStateOf(false) }
    var newYear by rememberSaveable { mutableIntStateOf(2026) }
    var newOccasion by rememberSaveable { mutableStateOf("Christmas") }
    var newCustomTitle by rememberSaveable { mutableStateOf("") }

    val personMap = remember(dataset.people) { dataset.people.associateBy { it.id } }
    val activeEvents = remember(dataset.events) { dataset.events.filter { !it.isArchived } }
    val archivedEvents = remember(dataset.events) { dataset.events.filter { it.isArchived } }

    val selectedEventId = prefs.selectedEventId
    val isYear2026Mode = selectedEventId == "year:2026"
    val currentEvent = dataset.events.firstOrNull { it.id == selectedEventId } ?: dataset.events.firstOrNull()

    val eventGifts = remember(dataset.gifts, dataset.events, selectedEventId) {
        if (isYear2026Mode) {
            val ids2026 = dataset.events.filter { it.year == 2026 }.map { it.id }.toSet()
            dataset.gifts.filter { it.eventId != null && it.eventId in ids2026 }
        } else {
            dataset.gifts.filter { it.eventId == selectedEventId }
        }
    }

    val filteredGifts = remember(eventGifts, personFilter, statusFilter, searchQuery, personMap) {
        eventGifts.filter { g ->
            if (personFilter == "unassigned" && g.personId != null) return@filter false
            if (personFilter != "all" && personFilter != "unassigned" && g.personId != personFilter) return@filter false
            if (statusFilter == "to-buy" && g.bought) return@filter false
            if (statusFilter == "to-wrap" && g.wrapped) return@filter false
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.lowercase()
                val pName = g.personId?.let { personMap[it]?.name?.lowercase() }.orEmpty()
                g.title.lowercase().contains(q) || g.store.lowercase().contains(q) || pName.contains(q)
            } else {
                true
            }
        }
    }

    val totalBudget = remember(dataset.people) { dataset.people.sumOf { it.budget } }
    val spent = remember(eventGifts) { eventGifts.sumOf { it.cost } }
    val boughtCount = remember(eventGifts) { eventGifts.count { it.bought } }
    val arrivedCount = remember(eventGifts) { eventGifts.count { it.received } }
    val wrappedCount = remember(eventGifts) { eventGifts.count { it.wrapped } }

    val recipientBreakdown = remember(dataset.people, eventGifts, dataset.gifts) {
        val spentByPerson = mutableMapOf<String, Double>()
        eventGifts.forEach { g ->
            if (g.personId != null) {
                spentByPerson[g.personId] = (spentByPerson[g.personId] ?: 0.0) + g.cost
            }
        }
        dataset.people
            .filter { it.budget > 0.0 || (spentByPerson[it.id] ?: 0.0) > 0.0 }
            .map { p ->
                val pSpent = spentByPerson[p.id] ?: 0.0
                val pIdeas = dataset.gifts.count { it.eventId == null && it.personId == p.id }
                Triple(p, pSpent, pIdeas)
            }
    }

    @Composable
    fun OccasionHeaderAndHero() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Occasion Switcher Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeEvents.forEach { ev ->
                    FilterChip(
                        selected = selectedEventId == ev.id,
                        onClick = {
                            viewModel.selectEvent(ev.id)
                            personFilter = "all"
                        },
                        label = { Text("${ev.emoji} ${ev.name}") }
                    )
                }

                FilterChip(
                    selected = isYear2026Mode,
                    onClick = {
                        viewModel.selectEvent("year:2026")
                        personFilter = "all"
                    },
                    label = { Text("🗓️ All 2026") }
                )

                if (archivedEvents.isNotEmpty()) {
                    FilterChip(
                        selected = showArchived || (currentEvent?.isArchived == true && !isYear2026Mode),
                        onClick = { showArchived = !showArchived },
                        leadingIcon = { Icon(Icons.Rounded.Archive, null, Modifier.size(16.dp)) },
                        label = { Text("Past Years") }
                    )
                }

                FilterChip(
                    selected = creatingEvent,
                    onClick = { creatingEvent = !creatingEvent },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) },
                    label = { Text("New Occasion") }
                )
            }

            if (showArchived && archivedEvents.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    archivedEvents.forEach { ev ->
                        FilterChip(
                            selected = selectedEventId == ev.id,
                            onClick = { viewModel.selectEvent(ev.id) },
                            label = { Text("${ev.emoji} ${ev.name}") }
                        )
                    }
                }
            }

            if (creatingEvent) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Create New Occasion", fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Christmas" to "🎄",
                                "Birthday" to "🎂",
                                "Anniversary" to "💝",
                                "Other" to "✨"
                            ).forEach { (occ, em) ->
                                FilterChip(
                                    selected = newOccasion == occ,
                                    onClick = { newOccasion = occ },
                                    label = { Text("$em $occ", fontSize = 12.sp) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = newCustomTitle,
                            onValueChange = { newCustomTitle = it },
                            label = { Text("Occasion Name (optional)") },
                            placeholder = { Text("$newYear $newOccasion") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { creatingEvent = false }) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val emojiMap = mapOf(
                                        "Christmas" to "🎄",
                                        "Birthday" to "🎂",
                                        "Anniversary" to "💝",
                                        "Other" to "✨"
                                    )
                                    val title = newCustomTitle.trim().ifBlank {
                                        "$newYear ${if (newOccasion == "Birthday") "Birthdays" else newOccasion}"
                                    }
                                    viewModel.addEvent(
                                        name = title,
                                        year = newYear,
                                        occasion = newOccasion,
                                        emoji = emojiMap[newOccasion] ?: "✨"
                                    )
                                    newCustomTitle = ""
                                    creatingEvent = false
                                }
                            ) {
                                Text("Create")
                            }
                        }
                    }
                }
            }

            // Hero Expressive Wavy Budget Card
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (totalBudget > 0) "$${"%.0f".format(spent)} / $${"%.0f".format(totalBudget)}"
                                else "$${"%.0f".format(spent)} Planned",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${eventGifts.size} gifts • $boughtCount bought • $arrivedCount arrived • $wrappedCount wrapped",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                val target = if (isYear2026Mode) "2026-christmas" else selectedEventId
                                viewModel.openNewGiftComposer(eventId = target)
                            }
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Gift")
                        }
                    }

                    val pct = if (totalBudget > 0) {
                        (spent / totalBudget).toFloat()
                    } else if (eventGifts.isNotEmpty()) {
                        boughtCount.toFloat() / eventGifts.size
                    } else 0f

                    ExpressiveBudgetBar(
                        progress = pct,
                        isOverBudget = totalBudget > 0 && spent > totalBudget
                    )
                }
            }

            // Search & Status Filter Chips
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search gifts, stores, or recipients...") },
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
                    selected = statusFilter == "all",
                    onClick = { statusFilter = "all" },
                    label = { Text("All (${eventGifts.size})") }
                )
                FilterChip(
                    selected = statusFilter == "to-buy",
                    onClick = { statusFilter = "to-buy" },
                    label = { Text("To Buy (${eventGifts.count { !it.bought }})") }
                )
                FilterChip(
                    selected = statusFilter == "to-wrap",
                    onClick = { statusFilter = "to-wrap" },
                    label = { Text("To Wrap (${eventGifts.count { !it.wrapped }})") }
                )
                dataset.people.forEach { person ->
                    FilterChip(
                        selected = personFilter == person.id,
                        onClick = { personFilter = if (personFilter == person.id) "all" else person.id },
                        label = { Text(person.name) }
                    )
                }
            }
        }
    }

    @Composable
    fun SupportingBudgetPane() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Budgets by Recipient", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { viewModel.openPersonEditor(null) }) {
                            Text("+ Person")
                        }
                    }

                    if (recipientBreakdown.isEmpty()) {
                        Text(
                            "Add recipients with target budgets in the People tab to track per-person spending.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recipientBreakdown.forEach { (person, pSpent, pIdeas) ->
                            Surface(
                                onClick = {
                                    viewModel.selectPerson(person.id)
                                    viewModel.setActiveTab(NavTab.PEOPLE)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            ExpressiveAvatar(
                                                name = person.name,
                                                shapeName = person.avatarShape,
                                                tone = person.avatarTone,
                                                size = 34.dp
                                            )
                                            Column {
                                                Text(person.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                if (pIdeas > 0) {
                                                    Text(
                                                        "$pIdeas ideas in stash",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = if (person.budget > 0) "$${pSpent.toInt()} / $${person.budget.toInt()}"
                                            else "$${pSpent.toInt()}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = if (person.budget > 0 && pSpent > person.budget)
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (person.budget > 0) {
                                        ExpressiveBudgetBar(
                                            progress = (pSpent / person.budget).toFloat(),
                                            isOverBudget = pSpent > person.budget
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isTwoPane) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1.5f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { OccasionHeaderAndHero() }
                if (filteredGifts.isEmpty()) {
                    item { EmptyEventGiftsCard { viewModel.openNewGiftComposer() } }
                } else {
                    items(filteredGifts, key = { it.id }) { gift ->
                        EventGiftCard(
                            gift = gift,
                            person = gift.personId?.let { personMap[it] },
                            onToggleStatus = { field -> viewModel.toggleGiftStatus(gift.id, field) },
                            onEdit = { viewModel.openEditGiftComposer(gift) },
                            onSaveForLater = { viewModel.moveGiftToIdeas(gift.id, saveForLater = true) },
                            onMoveToIdeas = { viewModel.moveGiftToIdeas(gift.id, saveForLater = false) },
                            onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { SupportingBudgetPane() }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { OccasionHeaderAndHero() }

            if (filteredGifts.isEmpty()) {
                item { EmptyEventGiftsCard { viewModel.openNewGiftComposer() } }
            } else {
                items(filteredGifts, key = { it.id }) { gift ->
                    EventGiftCard(
                        gift = gift,
                        person = gift.personId?.let { personMap[it] },
                        onToggleStatus = { field -> viewModel.toggleGiftStatus(gift.id, field) },
                        onEdit = { viewModel.openEditGiftComposer(gift) },
                        onSaveForLater = { viewModel.moveGiftToIdeas(gift.id, saveForLater = true) },
                        onMoveToIdeas = { viewModel.moveGiftToIdeas(gift.id, saveForLater = false) },
                        onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } }
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                SupportingBudgetPane()
            }
        }
    }
}

@Composable
private fun EmptyEventGiftsCard(onAddClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("No gifts in this checklist yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add a gift directly, share a product link from Chrome/Amazon, or move an item from your Gift Ideas tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAddClick) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add First Gift")
            }
        }
    }
}

@Composable
private fun EventGiftCard(
    gift: GiftItem,
    person: Person?,
    onToggleStatus: (String) -> Unit,
    onEdit: () -> Unit,
    onSaveForLater: () -> Unit,
    onMoveToIdeas: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ExpressiveAvatar(
                        name = person?.name ?: "?",
                        shapeName = person?.avatarShape ?: "squircle",
                        tone = person?.avatarTone ?: "terracotta",
                        size = 42.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = gift.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = buildList {
                            add(person?.name ?: "Unassigned")
                            if (gift.store.isNotBlank()) add(gift.store)
                            if (gift.tracking.isNotBlank()) add("📦 ${gift.tracking}")
                        }.joinToString(" • ")
                        Text(
                            text = meta,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val price = gift.displayPrice()
                    if (price.isNotBlank()) {
                        Text(
                            text = price,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit gift", modifier = Modifier.size(19.dp))
                    }
                }
            }

            if (gift.notes.isNotBlank()) {
                Text(
                    text = gift.notes,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Material 3 Expressive Connected Toggle Button Group
            GiftStatusConnectedGroup(
                bought = gift.bought,
                received = gift.received,
                wrapped = gift.wrapped,
                onToggleBought = { onToggleStatus("bought") },
                onToggleReceived = { onToggleStatus("received") },
                onToggleWrapped = { onToggleStatus("wrapped") }
            )

            // Secondary quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (gift.url.isNotBlank()) {
                    AssistChip(
                        onClick = { onOpenUrl(gift.url) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(15.dp)) },
                        label = { Text(gift.store.ifBlank { "Open Link" }, fontSize = 12.sp) }
                    )
                }
                AssistChip(
                    onClick = onMoveToIdeas,
                    leadingIcon = { Icon(Icons.Rounded.Lightbulb, null, Modifier.size(15.dp)) },
                    label = { Text("Back to Ideas", fontSize = 12.sp) }
                )
                if (gift.bought) {
                    AssistChip(
                        onClick = onSaveForLater,
                        leadingIcon = { Icon(Icons.Rounded.BookmarkBorder, null, Modifier.size(15.dp)) },
                        label = { Text("Stash", fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}
