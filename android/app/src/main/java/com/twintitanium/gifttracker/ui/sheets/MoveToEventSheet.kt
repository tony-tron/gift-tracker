package com.twintitanium.gifttracker.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twintitanium.gifttracker.data.model.GiftItem
import com.twintitanium.gifttracker.ui.GiftTrackerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveToEventSheet(
    gift: GiftItem,
    viewModel: GiftTrackerViewModel,
    onDismiss: () -> Unit
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val activeEvents = remember(dataset.events) { dataset.events.filter { !it.isArchived } }
    var selectedEventId by remember(activeEvents) {
        mutableStateOf(activeEvents.firstOrNull()?.id ?: "2026-christmas")
    }
    var selectedPersonId by remember(gift) { mutableStateOf(gift.personId) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Move Idea to Occasion", style = MaterialTheme.typography.titleLarge)
            Text(
                gift.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text("Select Occasion", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activeEvents.forEach { ev ->
                    val active = selectedEventId == ev.id
                    Surface(
                        onClick = { selectedEventId = ev.id },
                        shape = RoundedCornerShape(18.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${ev.emoji} ${ev.name}",
                                fontWeight = FontWeight.Bold,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(ev.year.toString())
                        }
                    }
                }
            }

            if (dataset.people.isNotEmpty()) {
                Text("Recipient", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedPersonId == null,
                        onClick = { selectedPersonId = null },
                        label = { Text("Unassigned") }
                    )
                    dataset.people.forEach { p ->
                        FilterChip(
                            selected = selectedPersonId == p.id,
                            onClick = { selectedPersonId = p.id },
                            label = { Text(p.name) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.moveGiftToEvent(gift.id, selectedEventId, selectedPersonId)
                    }
                ) {
                    Text("Move Gift")
                }
            }
        }
    }
}
