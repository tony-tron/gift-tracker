package com.twintitanium.gifttracker.ui.screens

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twintitanium.gifttracker.data.model.GiftEvent
import com.twintitanium.gifttracker.data.model.GiftItem
import com.twintitanium.gifttracker.data.model.Person
import com.twintitanium.gifttracker.ui.GiftTrackerViewModel
import com.twintitanium.gifttracker.ui.components.ExpressiveAvatar
import com.twintitanium.gifttracker.ui.components.ExpressiveBudgetBar

enum class PeopleSortOption(val label: String) {
    DEFAULT("Default"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    BUDGET_DESC("Budget (High)"),
    SPENT_DESC("Spent (High)"),
    IDEAS_DESC("Most Ideas")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleScreen(
    viewModel: GiftTrackerViewModel,
    isTwoPane: Boolean,
    contentPadding: PaddingValues
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOption by rememberSaveable { mutableStateOf(PeopleSortOption.DEFAULT) }
    var reorderMode by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var mobileDetailOpen by rememberSaveable { mutableStateOf(false) }

    val filteredPeople = remember(dataset.people, dataset.gifts, searchQuery, sortOption) {
        val base = if (searchQuery.isBlank()) dataset.people
        else {
            val q = searchQuery.lowercase()
            dataset.people.filter { p ->
                p.name.lowercase().contains(q) ||
                    p.notes.lowercase().contains(q) ||
                    p.sizes.nonEmptyEntries().any { (_, v) -> v.lowercase().contains(q) }
            }
        }

        when (sortOption) {
            PeopleSortOption.NAME_ASC -> base.sortedBy { it.name.lowercase() }
            PeopleSortOption.NAME_DESC -> base.sortedByDescending { it.name.lowercase() }
            PeopleSortOption.BUDGET_DESC -> base.sortedByDescending { it.budget }
            PeopleSortOption.SPENT_DESC -> base.sortedByDescending { p ->
                dataset.gifts.filter { it.personId == p.id && it.eventId == "2026-christmas" }.sumOf { it.cost }
            }
            PeopleSortOption.IDEAS_DESC -> base.sortedByDescending { p ->
                dataset.gifts.count { it.personId == p.id && it.eventId == null }
            }
            PeopleSortOption.DEFAULT -> base.sortedBy { it.order }
        }
    }

    val activePerson = remember(dataset.people, uiState.selectedPersonId) {
        dataset.people.firstOrNull { it.id == uiState.selectedPersonId } ?: dataset.people.firstOrNull()
    }

    val eventMap = remember(dataset.events) { dataset.events.associateBy { it.id } }

    var backProgress by remember { mutableStateOf(0f) }

    PredictiveBackHandler(enabled = !isTwoPane && mobileDetailOpen) { progress ->
        try {
            progress.collect { backEvent ->
                backProgress = backEvent.progress
            }
            mobileDetailOpen = false
        } finally {
            backProgress = 0f
        }
    }

    @Composable
    fun DirectoryPane(modifier: Modifier = Modifier) {
        LazyColumn(
            modifier = modifier,
            contentPadding = if (isTwoPane) PaddingValues(0.dp) else contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search people or sizes...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { viewModel.openPersonEditor(null) }) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "Add Person", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilterChip(
                            selected = sortOption != PeopleSortOption.DEFAULT,
                            onClick = { showSortMenu = true },
                            leadingIcon = { Icon(Icons.Rounded.SwapVert, null, Modifier.size(16.dp)) },
                            label = { Text("Sort: ${sortOption.label}", fontSize = 12.sp) }
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            PeopleSortOption.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    onClick = {
                                        sortOption = opt
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    if (sortOption == PeopleSortOption.DEFAULT && searchQuery.isBlank() && dataset.people.size > 1) {
                        FilledTonalButton(
                            onClick = { reorderMode = !reorderMode },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(if (reorderMode) "Done" else "Reorder", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (filteredPeople.isEmpty()) {
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
                            Text("No recipients yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Add family & friends to track clothing sizes, budgets, notes, and gift history.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { viewModel.openPersonEditor(null) }) {
                                Text("Add First Person")
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(filteredPeople, key = { _, it -> it.id }) { index, person ->
                    val isSelected = isTwoPane && activePerson?.id == person.id
                    val spent2026 = dataset.gifts
                        .filter { it.personId == person.id && it.eventId == "2026-christmas" }
                        .sumOf { it.cost }
                    val ideasCount = dataset.gifts.count { it.personId == person.id && it.eventId == null }

                    Surface(
                        onClick = {
                            viewModel.selectPerson(person.id)
                            if (!isTwoPane) mobileDetailOpen = true
                        },
                        shape = RoundedCornerShape(22.dp),
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
                                    name = person.name,
                                    shapeName = person.avatarShape,
                                    tone = person.avatarTone,
                                    size = 44.dp
                                )
                                Column {
                                    Text(person.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    val sub = buildList {
                                        add(person.relationship)
                                        if (ideasCount > 0) add("$ideasCount ideas")
                                    }.joinToString(" • ")
                                    Text(
                                        sub,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (reorderMode && sortOption == PeopleSortOption.DEFAULT && searchQuery.isBlank()) {
                                    IconButton(
                                        enabled = index > 0,
                                        onClick = {
                                            val list = dataset.people.sortedBy { it.order }.toMutableList()
                                            val fromIdx = list.indexOfFirst { it.id == person.id }
                                            if (fromIdx > 0) {
                                                val item = list.removeAt(fromIdx)
                                                list.add(fromIdx - 1, item)
                                                viewModel.reorderPeople(list.map { it.id })
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move Up")
                                    }
                                    IconButton(
                                        enabled = index < filteredPeople.size - 1,
                                        onClick = {
                                            val list = dataset.people.sortedBy { it.order }.toMutableList()
                                            val fromIdx = list.indexOfFirst { it.id == person.id }
                                            if (fromIdx in 0 until list.size - 1) {
                                                val item = list.removeAt(fromIdx)
                                                list.add(fromIdx + 1, item)
                                                viewModel.reorderPeople(list.map { it.id })
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move Down")
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    if (person.budget > 0) {
                                        Text(
                                            "$${spent2026.toInt()} / $${person.budget.toInt()}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "Budget",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            "Profile →",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
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

    @Composable
    fun DetailPane(person: Person, modifier: Modifier = Modifier) {
        val personGifts = remember(dataset.gifts, person.id) {
            dataset.gifts.filter { it.personId == person.id }
        }
        val activeGifts = personGifts.filter { it.eventId != null && eventMap[it.eventId]?.isArchived != true }
        val ideas = personGifts.filter { it.eventId == null }
        val pastGifts = personGifts.filter { it.eventId != null && eventMap[it.eventId]?.isArchived == true }
        val xmasSpent = activeGifts.filter { it.eventId == "2026-christmas" }.sumOf { it.cost }

        LazyColumn(
            modifier = modifier,
            contentPadding = if (isTwoPane) PaddingValues(0.dp) else contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!isTwoPane) {
                item {
                    FilledTonalButton(onClick = { mobileDetailOpen = false }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("All People")
                    }
                }
            }

            // Recipient Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                ExpressiveAvatar(
                                    name = person.name,
                                    shapeName = person.avatarShape,
                                    tone = person.avatarTone,
                                    size = 56.dp
                                )
                                Column {
                                    Text(person.name, style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        buildList {
                                            add(person.relationship)
                                            if (!person.birthday.isNullOrBlank()) add("🎂 ${person.birthday}")
                                        }.joinToString(" • "),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { viewModel.openPersonEditor(person) }) {
                                    Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit")
                                }
                                Button(onClick = { viewModel.openNewGiftComposer(personId = person.id, eventId = null) }) {
                                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Idea")
                                }
                            }
                        }

                        if (person.budget > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("2026 Christmas Budget", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "$${xmasSpent.toInt()} / $${person.budget.toInt()}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                ExpressiveBudgetBar(
                                    progress = (xmasSpent / person.budget).toFloat(),
                                    isOverBudget = xmasSpent > person.budget
                                )
                            }
                        }
                    }
                }
            }

            // Clothing & Ring Sizes Cheat Sheet
            item {
                val sizes = person.sizes.nonEmptyEntries()
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Checkroom, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Clothing & Ring Sizes", style = MaterialTheme.typography.titleMedium)
                        }

                        if (sizes.isEmpty()) {
                            Text(
                                "No sizes saved yet. Tap Edit above to add shirt, shoe, or ring sizes.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                sizes.forEach { (label, value) ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            Text(
                                                "$label: ",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                                            )
                                            Text(
                                                value,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Notes
            if (person.notes.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Interests & Notes", style = MaterialTheme.typography.titleMedium)
                            Text(person.notes, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Planned Gifts & Ideas
            item {
                PersonGiftSection(
                    title = "Active Planned Gifts (${activeGifts.size})",
                    gifts = activeGifts,
                    eventMap = eventMap,
                    onGiftClick = { viewModel.openEditGiftComposer(it) }
                )
            }

            item {
                PersonGiftSection(
                    title = "Ideas Backlog (${ideas.size})",
                    gifts = ideas,
                    eventMap = eventMap,
                    onGiftClick = { viewModel.openEditGiftComposer(it) }
                )
            }

            if (pastGifts.isNotEmpty()) {
                item {
                    PersonGiftSection(
                        title = "Past Gift History (${pastGifts.size})",
                        gifts = pastGifts,
                        eventMap = eventMap,
                        onGiftClick = { viewModel.openEditGiftComposer(it) },
                        isHistory = true
                    )
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
            DirectoryPane(modifier = Modifier.weight(0.95f))
            if (activePerson != null) {
                DetailPane(person = activePerson, modifier = Modifier.weight(1.35f))
            }
        }
    } else {
        if (mobileDetailOpen && activePerson != null) {
            DetailPane(
                person = activePerson,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f - (0.08f * backProgress)
                        scaleX = scale
                        scaleY = scale
                        translationX = backProgress * 48f
                        alpha = 1f - (0.15f * backProgress)
                    }
            )
        } else {
            DirectoryPane(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PersonGiftSection(
    title: String,
    gifts: List<GiftItem>,
    eventMap: Map<String, GiftEvent>,
    onGiftClick: (GiftItem) -> Unit,
    isHistory: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isHistory) {
                    Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }

            if (gifts.isEmpty()) {
                Text("None yet", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                gifts.forEach { g ->
                    Surface(
                        onClick = { onGiftClick(g) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(g.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                val evLabel = g.eventId?.let { eventMap[it]?.name } ?: "Idea"
                                Text(
                                    buildList {
                                        add(evLabel)
                                        if (g.store.isNotBlank()) add(g.store)
                                    }.joinToString(" • "),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val p = g.displayPrice()
                            if (p.isNotBlank()) {
                                Text(p, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
