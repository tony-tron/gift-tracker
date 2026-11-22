package com.tonytron.gifttracker.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tonytron.gifttracker.data.model.ClothingSizes
import com.tonytron.gifttracker.data.model.IdGenerator
import com.tonytron.gifttracker.data.model.Person
import com.tonytron.gifttracker.ui.GiftTrackerViewModel
import com.tonytron.gifttracker.ui.components.ExpressiveAvatar
import com.tonytron.gifttracker.ui.components.avatarToneColors

@Composable
fun PersonEditorSheet(
    editingPerson: Person?,
    viewModel: GiftTrackerViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(editingPerson) { mutableStateOf(editingPerson?.name.orEmpty()) }
    var budgetStr by remember(editingPerson) {
        mutableStateOf(
            editingPerson?.budget?.takeIf { it > 0 }?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
            }.orEmpty()
        )
    }
    var relationship by remember(editingPerson) {
        mutableStateOf(editingPerson?.relationship ?: "Family & Close Circle")
    }
    var birthday by remember(editingPerson) { mutableStateOf(editingPerson?.birthday.orEmpty()) }
    var avatarShape by remember(editingPerson) {
        mutableStateOf(editingPerson?.avatarShape ?: "squircle")
    }
    var avatarTone by remember(editingPerson) {
        mutableStateOf(editingPerson?.avatarTone ?: "terracotta")
    }

    var topSize by remember(editingPerson) { mutableStateOf(editingPerson?.sizes?.top.orEmpty()) }
    var bottomsSize by remember(editingPerson) { mutableStateOf(editingPerson?.sizes?.bottoms.orEmpty()) }
    var intimatesSize by remember(editingPerson) { mutableStateOf(editingPerson?.sizes?.intimates.orEmpty()) }
    var shoeSize by remember(editingPerson) { mutableStateOf(editingPerson?.sizes?.shoe.orEmpty()) }
    var ringSize by remember(editingPerson) { mutableStateOf(editingPerson?.sizes?.ring.orEmpty()) }
    var otherSize by remember(editingPerson) { mutableStateOf(editingPerson?.sizes?.other.orEmpty()) }
    var notes by remember(editingPerson) { mutableStateOf(editingPerson?.notes.orEmpty()) }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpressiveAvatar(
                        name = name.ifBlank { "?" },
                        shapeName = avatarShape,
                        tone = avatarTone,
                        size = 48.dp
                    )
                    Text(
                        text = if (editingPerson != null) "Edit Recipient" else "New Recipient",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                if (editingPerson != null) {
                    IconButton(onClick = { viewModel.deletePerson(editingPerson) }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete person",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                placeholder = { Text("e.g., Mom, Alex, Sam...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = budgetStr,
                    onValueChange = { budgetStr = it },
                    label = { Text("Target Budget ($)") },
                    placeholder = { Text("50") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = birthday,
                    onValueChange = { birthday = it },
                    label = { Text("Birthday (optional)") },
                    placeholder = { Text("Oct 14") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = relationship,
                onValueChange = { relationship = it },
                label = { Text("Group / Relationship") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // MaterialShapes Avatar Picker
            Text("Material 3 Expressive Avatar Shape & Tone", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IdGenerator.AVATAR_SHAPES.forEach { shape ->
                    FilterChip(
                        selected = avatarShape == shape,
                        onClick = { avatarShape = shape },
                        label = { Text(shape.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IdGenerator.AVATAR_TONES.forEach { tone ->
                    val (bg, fg) = avatarToneColors(tone)
                    val selected = avatarTone == tone
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .then(
                                if (selected) Modifier.border(2.5.dp, fg, CircleShape)
                                else Modifier
                            )
                            .clickable { avatarTone = tone }
                    )
                }
            }

            // Clothing & Ring Sizes
            Text("Clothing & Ring Sizes", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = topSize,
                    onValueChange = { topSize = it },
                    label = { Text("Top / Shirt") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = bottomsSize,
                    onValueChange = { bottomsSize = it },
                    label = { Text("Bottoms") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = shoeSize,
                    onValueChange = { shoeSize = it },
                    label = { Text("Shoe") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = ringSize,
                    onValueChange = { ringSize = it },
                    label = { Text("Ring") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = intimatesSize,
                    onValueChange = { intimatesSize = it },
                    label = { Text("Intimates") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = otherSize,
                    onValueChange = { otherSize = it },
                    label = { Text("Other") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Interests, Favorite Brands & Notes") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val budget = budgetStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                        val sizes = ClothingSizes(
                            top = topSize.trim().ifBlank { null },
                            bottoms = bottomsSize.trim().ifBlank { null },
                            intimates = intimatesSize.trim().ifBlank { null },
                            shoe = shoeSize.trim().ifBlank { null },
                            ring = ringSize.trim().ifBlank { null },
                            other = otherSize.trim().ifBlank { null }
                        )
                        viewModel.savePerson(
                            existing = editingPerson,
                            name = name,
                            budget = budget,
                            relationship = relationship,
                            birthday = birthday,
                            sizes = sizes,
                            notes = notes,
                            avatarShape = avatarShape,
                            avatarTone = avatarTone
                        )
                    }
                ) {
                    Text(if (editingPerson != null) "Save" else "Add Person")
                }
            }
        }
    }
}
