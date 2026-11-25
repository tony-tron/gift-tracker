package com.twintitanium.gifttracker.ui.screens

import android.content.ClipDescription
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twintitanium.gifttracker.data.model.NavTab
import com.twintitanium.gifttracker.data.model.SyncStatus
import com.twintitanium.gifttracker.ui.GiftTrackerViewModel
import com.twintitanium.gifttracker.ui.sheets.GiftComposerSheet
import com.twintitanium.gifttracker.ui.sheets.MoveToEventSheet
import com.twintitanium.gifttracker.ui.sheets.PersonEditorSheet
import com.twintitanium.gifttracker.ui.sheets.SettingsSheet

private data class NavDestination(
    val tab: NavTab,
    val icon: ImageVector,
    val count: Int
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GiftTrackerRoot(
    viewModel: GiftTrackerViewModel,
    widthSizeClass: WindowWidthSizeClass
) {
    val dataset by viewModel.dataset.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val cloudState by viewModel.cloudState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val isCompact = widthSizeClass == WindowWidthSizeClass.Compact
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded

    var searchFocusTick by remember { mutableIntStateOf(0) }
    var escapeTick by remember { mutableIntStateOf(0) }
    var isExternalDragOver by remember { mutableStateOf(false) }

    val externalDropTarget = remember(context, viewModel) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isExternalDragOver = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isExternalDragOver = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isExternalDragOver = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isExternalDragOver = false
                val clipData = event.toAndroidDragEvent().clipData ?: return false
                if (clipData.itemCount == 0) return false
                val text = clipData.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
                if (text.isBlank()) return false
                viewModel.handleIncomingShare(text, null)
                return true
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                event.onAction?.invoke()
            }
        }
    }

    val eventGiftsCount = remember(dataset.gifts, dataset.events, prefs.selectedEventId) {
        if (prefs.selectedEventId == "all:active" || prefs.selectedEventId == "year:2026") {
            val activeIds = dataset.events.filter { !it.isArchived }.map { it.id }.toSet()
            dataset.gifts.count { it.eventId != null && it.eventId in activeIds }
        } else {
            dataset.gifts.count { it.eventId == prefs.selectedEventId }
        }
    }
    val ideasCount = remember(dataset.gifts) { dataset.gifts.count { it.eventId == null } }
    val wantedWishesCount = remember(dataset.wishes) { dataset.wishes.count { !it.received } }

    val navDestinations = listOf(
        NavDestination(NavTab.EVENTS, Icons.Rounded.Celebration, eventGiftsCount),
        NavDestination(NavTab.IDEAS, Icons.Rounded.Lightbulb, ideasCount),
        NavDestination(NavTab.PEOPLE, Icons.Rounded.Group, dataset.people.size),
        NavDestination(NavTab.WISHLIST, Icons.Rounded.Star, wantedWishesCount)
    )

    val heading = when (uiState.activeTab) {
        NavTab.EVENTS -> {
            if (prefs.selectedEventId == "all:active" || prefs.selectedEventId == "year:2026") "All Active Occasions"
            else dataset.events.firstOrNull { it.id == prefs.selectedEventId }?.name ?: "Occasions"
        }
        NavTab.IDEAS -> "Gift Ideas & Stash"
        NavTab.PEOPLE -> "People & Sizes"
        NavTab.WISHLIST -> "My Wishlist"
    }

    val isSynced = cloudState.syncStatus == SyncStatus.SYNCED

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    keyEvent.isCtrlPressed && keyEvent.key == Key.N -> {
                        viewModel.openNewGiftComposer()
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.F -> {
                        searchFocusTick++
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.One -> {
                        viewModel.setActiveTab(NavTab.EVENTS)
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.Two -> {
                        viewModel.setActiveTab(NavTab.IDEAS)
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.Three -> {
                        viewModel.setActiveTab(NavTab.PEOPLE)
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.Four -> {
                        viewModel.setActiveTab(NavTab.WISHLIST)
                        true
                    }
                    keyEvent.key == Key.Escape -> {
                        when {
                            uiState.composerPreset != null -> { viewModel.closeGiftComposer(); true }
                            uiState.movingGift != null -> { viewModel.closeMoveGiftSheet(); true }
                            uiState.isPersonSheetOpen -> { viewModel.closePersonEditor(); true }
                            uiState.isSettingsOpen -> { viewModel.closeSettings(); true }
                            else -> { escapeTick++; false }
                        }
                    }
                    else -> false
                }
            }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                },
                target = externalDropTarget
            )
            .then(
                if (isExternalDragOver) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Medium & Expanded (Foldable / Tablet / Googlebook): Adaptive Navigation Rail
            if (!isCompact) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    header = {
                        FloatingActionButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                viewModel.openNewGiftComposer()
                            },
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add Gift or Idea")
                        }
                    }
                ) {
                    navDestinations.forEach { dest ->
                        NavigationRailItem(
                            selected = uiState.activeTab == dest.tab,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                viewModel.setActiveTab(dest.tab)
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (dest.count > 0) {
                                            Badge { Text(dest.count.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(dest.icon, contentDescription = dest.tab.label)
                                }
                            },
                            label = { Text(dest.tab.shortLabel, fontSize = 11.sp) }
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    NavigationRailItem(
                        selected = false,
                        onClick = { viewModel.openSettings() },
                        icon = {
                            Box {
                                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                                if (isSynced) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4ADE80))
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                        },
                        label = { Text("Settings", fontSize = 11.sp) }
                    )
                }
            }

            // Main Workspace
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(bottom = if (isCompact) 84.dp else 16.dp)
                    )
                },
                topBar = {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = heading,
                                style = MaterialTheme.typography.headlineMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            FilledTonalIconButton(
                                onClick = { viewModel.openSettings() }
                            ) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = "Settings"
                                    )
                                    if (isSynced) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4ADE80))
                                                .align(Alignment.TopEnd)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                var tabBackProgress by remember { mutableStateOf(0f) }

                PredictiveBackHandler(enabled = uiState.activeTab != NavTab.EVENTS) { progress ->
                    try {
                        progress.collect { backEvent ->
                            tabBackProgress = backEvent.progress
                        }
                        viewModel.setActiveTab(NavTab.EVENTS)
                    } finally {
                        tabBackProgress = 0f
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    val bottomClearance = if (isCompact) 112.dp else 24.dp
                    val contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding() + bottomClearance,
                        start = 16.dp,
                        end = 16.dp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (tabBackProgress > 0f) {
                                    val scale = 1f - (0.06f * tabBackProgress)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 1f - (0.12f * tabBackProgress)
                                }
                            }
                    ) {
                        when (uiState.activeTab) {
                            NavTab.EVENTS -> EventsScreen(
                                viewModel = viewModel,
                                isTwoPane = isExpanded || widthSizeClass == WindowWidthSizeClass.Medium,
                                contentPadding = contentPadding,
                                searchFocusTick = searchFocusTick
                            )
                            NavTab.IDEAS -> IdeasScreen(
                                viewModel = viewModel,
                                isWide = !isCompact,
                                contentPadding = contentPadding,
                                searchFocusTick = searchFocusTick
                            )
                            NavTab.PEOPLE -> PeopleScreen(
                                viewModel = viewModel,
                                isTwoPane = !isCompact,
                                contentPadding = contentPadding,
                                searchFocusTick = searchFocusTick,
                                escapeTick = escapeTick
                            )
                            NavTab.WISHLIST -> WishlistScreen(
                                viewModel = viewModel,
                                isWide = !isCompact,
                                contentPadding = contentPadding,
                                escapeTick = escapeTick
                            )
                        }

                        if (isExternalDragOver) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = innerPadding.calculateTopPadding() + 12.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 6.dp
                            ) {
                                Text(
                                    text = "Drop link or text to add gift",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Compact Phone: Material 3 Expressive HorizontalFloatingToolbar + Vibrant FAB
                    if (isCompact) {
                        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        HorizontalFloatingToolbar(
                            expanded = true,
                            floatingActionButton = {
                                FloatingToolbarDefaults.VibrantFloatingActionButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        viewModel.openNewGiftComposer()
                                    }
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add Gift or Idea")
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = navBarBottom + 12.dp)
                        ) {
                            navDestinations.forEach { dest ->
                                val selected = uiState.activeTab == dest.tab
                                if (selected) {
                                    Surface(
                                        onClick = { viewModel.setActiveTab(dest.tab) },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = dest.icon,
                                                contentDescription = dest.tab.label,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            AnimatedVisibility(visible = true) {
                                                Row {
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = dest.tab.shortLabel,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            viewModel.setActiveTab(dest.tab)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = dest.icon,
                                            contentDescription = dest.tab.label,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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

    // Modal Bottom Sheets
    uiState.composerPreset?.let { preset ->
        GiftComposerSheet(
            preset = preset,
            viewModel = viewModel,
            onDismiss = { viewModel.closeGiftComposer() }
        )
    }

    uiState.movingGift?.let { gift ->
        MoveToEventSheet(
            gift = gift,
            viewModel = viewModel,
            onDismiss = { viewModel.closeMoveGiftSheet() }
        )
    }

    if (uiState.isPersonSheetOpen) {
        PersonEditorSheet(
            editingPerson = uiState.editingPerson,
            viewModel = viewModel,
            onDismiss = { viewModel.closePersonEditor() }
        )
    }

    if (uiState.isSettingsOpen) {
        SettingsSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.closeSettings() }
        )
    }
}
