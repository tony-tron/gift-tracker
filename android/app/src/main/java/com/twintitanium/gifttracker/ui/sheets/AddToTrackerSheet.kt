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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twintitanium.gifttracker.data.model.PublishedWishItem
import com.twintitanium.gifttracker.ui.GiftTrackerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddToTrackerSheet(
    wish: PublishedWishItem,
    shareId: String,
    projectKey: String?,
    wishlistOwnerName: String,
    preferredPersonId: String?,
    initialGiverName: String,
    viewModel: GiftTrackerViewModel,
    onDismiss: () -> Unit
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val cleanOwner = wishlistOwnerName.trim().ifBlank { "Wishlist Owner" }
    val matchedPerson = remember(dataset.people, preferredPersonId, cleanOwner) {
        preferredPersonId?.let { pid -> dataset.people.firstOrNull { it.id == pid } }
            ?: dataset.people.firstOrNull { it.name.trim().equals(cleanOwner, ignoreCase = true) }
    }

    var personMode by remember(matchedPerson, dataset.people.size) {
        mutableStateOf(if (matchedPerson != null) "existing" else "create")
    }
    var selectedPersonId by remember(matchedPerson, dataset.people) {
        mutableStateOf(matchedPerson?.id ?: dataset.people.firstOrNull()?.id.orEmpty())
    }
    var selectedEventId by remember(dataset.events) {
        val active = dataset.events.firstOrNull { it.isActive && !it.isArchived }
            ?: dataset.events.firstOrNull { !it.isArchived }
        mutableStateOf(active?.id ?: "ideas")
    }
    var giverName by remember(initialGiverName) { mutableStateOf(initialGiverName) }
    var claimImmediately by remember { mutableStateOf(true) }

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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "TRACK GIFT FROM WISHLIST",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Add to Your Gift Tracker",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Track budget, shipping, and wrapping while syncing purchase status back to the group checklist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(wish.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (wish.notes.isNotBlank()) {
                        Text(
                            wish.notes,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Recipient selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Who is this gift for?", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (dataset.people.isNotEmpty()) {
                        FilterChip(
                            selected = personMode == "existing",
                            onClick = { personMode = "existing" },
                            label = {
                                Text(
                                    if (matchedPerson != null) "Existing: ${matchedPerson.name}"
                                    else "Choose Person (${dataset.people.size})"
                                )
                            }
                        )
                    }
                    FilterChip(
                        selected = personMode == "create",
                        onClick = { personMode = "create" },
                        leadingIcon = { Icon(Icons.Rounded.PersonAdd, null, Modifier.size(15.dp)) },
                        label = { Text("Create \"$cleanOwner\"") }
                    )
                    FilterChip(
                        selected = personMode == "unassigned",
                        onClick = { personMode = "unassigned" },
                        label = { Text("Idea Pool") }
                    )
                }

                if (personMode == "existing" && dataset.people.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dataset.people.forEach { p ->
                            FilterChip(
                                selected = selectedPersonId == p.id,
                                onClick = { selectedPersonId = p.id },
                                label = { Text(p.name) }
                            )
                        }
                    }
                }
            }

            // Occasion selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Occasion / Destination", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dataset.events.filter { !it.isArchived }.forEach { ev ->
                        FilterChip(
                            selected = selectedEventId == ev.id,
                            onClick = { selectedEventId = ev.id },
                            label = { Text("${ev.emoji} ${ev.name}") }
                        )
                    }
                    FilterChip(
                        selected = selectedEventId == "ideas",
                        onClick = { selectedEventId = "ideas" },
                        label = { Text("💡 Gift Ideas") }
                    )
                }
            }

            OutlinedTextField(
                value = giverName,
                onValueChange = { giverName = it },
                label = { Text("Your Name (shown to other givers, hidden from $cleanOwner) *") },
                placeholder = { Text("e.g., Sarah, Brother...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                onClick = { claimImmediately = !claimImmediately },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = claimImmediately,
                        onCheckedChange = { claimImmediately = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            if (claimImmediately) "Claim & reserve immediately" else "Mark as Researching only",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            if (claimImmediately) "Prevents other gift-givers from buying a duplicate."
                            else "Leaves item open for others while you look into it.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = giverName.isNotBlank(),
                    onClick = {
                        viewModel.addSharedWishToTracker(
                            wish = wish,
                            shareId = shareId,
                            projectKey = projectKey,
                            ownerName = cleanOwner,
                            personMode = personMode,
                            selectedPersonId = selectedPersonId,
                            eventId = if (selectedEventId == "ideas") null else selectedEventId,
                            giverName = giverName,
                            claimImmediately = claimImmediately,
                            onDone = onDismiss
                        )
                    }
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save to Tracker")
                }
            }
        }
    }
}
