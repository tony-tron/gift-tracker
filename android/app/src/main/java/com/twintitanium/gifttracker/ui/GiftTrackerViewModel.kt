package com.twintitanium.gifttracker.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twintitanium.gifttracker.data.model.ClothingSizes
import com.twintitanium.gifttracker.data.model.ColorMode
import com.twintitanium.gifttracker.data.model.FirebaseRuntimeConfig
import com.twintitanium.gifttracker.data.model.GiftItem
import com.twintitanium.gifttracker.data.model.NavTab
import com.twintitanium.gifttracker.data.model.Person
import com.twintitanium.gifttracker.data.model.PublicSharedWishlistDoc
import com.twintitanium.gifttracker.data.model.PublishedWishItem
import com.twintitanium.gifttracker.data.model.SavedWishlist
import com.twintitanium.gifttracker.data.model.SharedWishClaim
import com.twintitanium.gifttracker.data.model.SharedWishlistLink
import com.twintitanium.gifttracker.data.model.ThemeSeed
import com.twintitanium.gifttracker.data.model.WishlistHubTab
import com.twintitanium.gifttracker.data.model.WishItem
import com.twintitanium.gifttracker.data.remote.FirebaseSyncManager
import com.twintitanium.gifttracker.data.remote.LinkScraper
import com.twintitanium.gifttracker.data.remote.ScrapedLinkResult
import com.twintitanium.gifttracker.data.repository.GiftRepository
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GiftComposerPreset(
    val editingGift: GiftItem? = null,
    val initialTitle: String = "",
    val initialUrl: String = "",
    val initialStore: String = "",
    val initialCost: Double? = null,
    val initialPersonId: String? = null,
    val initialEventId: String? = null
)

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

data class AppUiState(
    val activeTab: NavTab = NavTab.EVENTS,
    val wishlistSubTab: WishlistHubTab = WishlistHubTab.MINE,
    val selectedSavedWishlistId: String? = null,
    val selectedPersonId: String? = null,
    val composerPreset: GiftComposerPreset? = null,
    val movingGift: GiftItem? = null,
    val editingPerson: Person? = null,
    val isPersonSheetOpen: Boolean = false,
    val isSaveWishlistSheetOpen: Boolean = false,
    val editingSavedWishlist: SavedWishlist? = null,
    val saveWishlistDefaultPersonId: String? = null,
    val saveWishlistDefaultUrl: String = "",
    val isSettingsOpen: Boolean = false
)

class GiftTrackerViewModel(application: Application) : AndroidViewModel(application) {

    val repository = GiftRepository(application.applicationContext)

    val dataset = repository.dataset
    val preferences = repository.preferences
    val cloudState = repository.cloudState

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<SnackbarEvent> = _snackbarEvents.asSharedFlow()

    fun showMessage(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        _snackbarEvents.tryEmit(SnackbarEvent(message, actionLabel, onAction))
    }

    fun setActiveTab(tab: NavTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setWishlistSubTab(sub: WishlistHubTab) {
        _uiState.update { it.copy(wishlistSubTab = sub) }
    }

    fun selectSavedWishlist(id: String?) {
        _uiState.update { it.copy(selectedSavedWishlistId = id) }
    }

    fun openSavedWishlistInHub(id: String) {
        _uiState.update {
            it.copy(
                activeTab = NavTab.WISHLIST,
                wishlistSubTab = WishlistHubTab.INCOMING,
                selectedSavedWishlistId = id
            )
        }
    }

    fun openSaveWishlistSheet(
        editing: SavedWishlist? = null,
        defaultPersonId: String? = null,
        defaultUrl: String = ""
    ) {
        _uiState.update {
            it.copy(
                isSaveWishlistSheetOpen = true,
                editingSavedWishlist = editing,
                saveWishlistDefaultPersonId = defaultPersonId,
                saveWishlistDefaultUrl = defaultUrl
            )
        }
    }

    fun closeSaveWishlistSheet() {
        _uiState.update {
            it.copy(
                isSaveWishlistSheetOpen = false,
                editingSavedWishlist = null,
                saveWishlistDefaultPersonId = null,
                saveWishlistDefaultUrl = ""
            )
        }
    }

    fun selectEvent(eventId: String) {
        viewModelScope.launch { repository.setSelectedEventId(eventId) }
    }

    fun selectPerson(personId: String?) {
        _uiState.update { it.copy(selectedPersonId = personId) }
    }

    fun openNewGiftComposer(
        personId: String? = null,
        eventId: String? = null,
        initialUrl: String = ""
    ) {
        val resolvedEvent = eventId ?: if (_uiState.value.activeTab == NavTab.EVENTS) {
            val sel = preferences.value.selectedEventId
            if (sel == "all:active" || sel == "year:2026") {
                repository.dataset.value.events.firstOrNull { !it.isArchived }?.id
            } else sel
        } else {
            null
        }
        _uiState.update {
            it.copy(
                composerPreset = GiftComposerPreset(
                    initialPersonId = personId,
                    initialEventId = resolvedEvent,
                    initialUrl = initialUrl
                )
            )
        }
    }

    fun openEditGiftComposer(gift: GiftItem) {
        _uiState.update { it.copy(composerPreset = GiftComposerPreset(editingGift = gift)) }
    }

    fun closeGiftComposer() {
        _uiState.update { it.copy(composerPreset = null) }
    }

    fun handleIncomingShare(sharedText: String?, sharedSubject: String?) {
        val (url, fallbackTitle) = LinkScraper.parseSharedText(sharedText, sharedSubject)
        if (url.isNotBlank() && FirebaseSyncManager.parseWishlistUrl(url).type == "live") {
            _uiState.update {
                it.copy(
                    activeTab = NavTab.WISHLIST,
                    wishlistSubTab = WishlistHubTab.INCOMING,
                    isSaveWishlistSheetOpen = true,
                    editingSavedWishlist = null,
                    saveWishlistDefaultPersonId = null,
                    saveWishlistDefaultUrl = url
                )
            }
            return
        }
        val store = if (url.isNotBlank()) LinkScraper.inferStoreFromUrl(url) else ""
        _uiState.update {
            it.copy(
                activeTab = NavTab.IDEAS,
                composerPreset = GiftComposerPreset(
                    initialTitle = fallbackTitle,
                    initialUrl = url,
                    initialStore = store,
                    initialEventId = null
                )
            )
        }
    }

    fun openQuickAddFromWidget() {
        val sel = preferences.value.selectedEventId
        val targetEvent = if (sel == "all:active" || sel == "year:2026") {
            repository.dataset.value.events.firstOrNull { !it.isArchived }?.id
        } else sel
        _uiState.update {
            it.copy(
                activeTab = NavTab.EVENTS,
                composerPreset = GiftComposerPreset(initialEventId = targetEvent)
            )
        }
    }

    suspend fun scrapeUrl(url: String): ScrapedLinkResult = LinkScraper.scrape(url)

    fun saveGift(draft: GiftItem, isEditing: Boolean) {
        if (isEditing) {
            repository.updateGift(draft)
            showMessage("Updated \"${draft.title}\"")
        } else {
            repository.addGift(draft)
            showMessage(
                if (draft.eventId != null) "Added \"${draft.title}\" to occasion"
                else "Saved \"${draft.title}\" to Gift Ideas"
            )
        }
        closeGiftComposer()
    }

    fun saveSharedWish(title: String, url: String, notes: String) {
        repository.addWish(title = title, url = url, notes = notes)
        _uiState.update {
            it.copy(
                activeTab = NavTab.WISHLIST,
                wishlistSubTab = WishlistHubTab.MINE,
                composerPreset = null
            )
        }
        showMessage("Added \"${title.trim()}\" to My Wishlist")
    }

    fun deleteGiftWithUndo(id: String) {
        val removed = repository.deleteGift(id) ?: return
        closeGiftComposer()
        showMessage("Removed \"${removed.title}\"", actionLabel = "Undo") {
            repository.restoreGift(removed)
        }
    }

    fun toggleGiftStatus(id: String, field: String) {
        repository.toggleGiftStatus(id, field) { msg -> showMessage(msg) }
    }

    fun openMoveGiftSheet(gift: GiftItem) {
        _uiState.update { it.copy(movingGift = gift) }
    }

    fun closeMoveGiftSheet() {
        _uiState.update { it.copy(movingGift = null) }
    }

    fun moveGiftToEvent(giftId: String, eventId: String, personId: String? = null) {
        repository.moveGiftToEvent(giftId, eventId, personId)
        val evName = dataset.value.events.firstOrNull { it.id == eventId }?.name ?: "Occasion"
        closeMoveGiftSheet()
        showMessage("Moved to $evName")
    }

    fun moveGiftToIdeas(giftId: String, saveForLater: Boolean) {
        repository.moveGiftToIdeas(giftId, saveForLater)
        showMessage(if (saveForLater) "Saved in Stockpile for later" else "Moved back to Ideas")
    }

    // People Sheet
    fun openPersonEditor(person: Person? = null) {
        _uiState.update { it.copy(editingPerson = person, isPersonSheetOpen = true) }
    }

    fun closePersonEditor() {
        _uiState.update { it.copy(editingPerson = null, isPersonSheetOpen = false) }
    }

    fun savePerson(
        existing: Person?,
        name: String,
        budget: Double,
        relationship: String,
        birthday: String?,
        sizes: ClothingSizes,
        notes: String,
        avatarShape: String,
        avatarTone: String
    ) {
        if (existing != null) {
            repository.updatePerson(
                existing.copy(
                    name = name.trim(),
                    budget = budget,
                    relationship = relationship,
                    birthday = birthday?.takeIf { it.isNotBlank() },
                    sizes = sizes,
                    notes = notes,
                    avatarShape = avatarShape,
                    avatarTone = avatarTone
                )
            )
            showMessage("Updated ${name.trim()}")
        } else {
            val created = repository.addPerson(
                name = name,
                budget = budget,
                relationship = relationship,
                birthday = birthday,
                sizes = sizes,
                notes = notes,
                avatarShape = avatarShape,
                avatarTone = avatarTone
            )
            _uiState.update { it.copy(selectedPersonId = created.id) }
            showMessage("Added ${created.name}")
        }
        closePersonEditor()
    }

    fun deletePerson(person: Person) {
        repository.deletePerson(person.id)
        if (_uiState.value.selectedPersonId == person.id) {
            _uiState.update { it.copy(selectedPersonId = null) }
        }
        closePersonEditor()
        showMessage("Removed ${person.name}")
    }

    fun reorderPeople(orderedIds: List<String>) {
        repository.reorderPeople(orderedIds)
        showMessage("Updated recipient order")
    }

    // Occasions & Wishes
    fun addEvent(name: String, year: Int, occasion: String, emoji: String) {
        val created = repository.addEvent(name, year, occasion, emoji)
        selectEvent(created.id)
        showMessage("Created ${created.emoji} ${created.name}")
    }

    fun setEventArchived(eventId: String, archived: Boolean) {
        val updated = repository.setEventArchived(eventId, archived) ?: return
        val actionLabel = if (archived) "Archived" else "Restored"
        showMessage("$actionLabel ${updated.emoji} ${updated.name}")
    }

    fun addWish(title: String, url: String, notes: String = "") {
        repository.addWish(title = title, url = url, notes = notes)
        showMessage("Added to Wishlist")
    }

    fun updateWish(wish: WishItem) = repository.updateWish(wish)

    fun reorderWishes(orderedIds: List<String>) {
        repository.reorderWishes(orderedIds)
        showMessage("Updated wishlist order")
    }
    fun deleteWish(id: String) {
        repository.deleteWish(id)
        showMessage("Removed from Wishlist")
    }

    fun setOwnerDisplayName(name: String?) {
        viewModelScope.launch { repository.setOwnerDisplayName(name) }
    }

    fun createSharedWishlistLink(
        title: String,
        ownerName: String,
        wishIds: List<String>,
        includeAllActive: Boolean,
        includeNotes: Boolean,
        includeUrls: Boolean,
        onCreated: (SharedWishlistLink) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (ownerName.isNotBlank()) {
                repository.setOwnerDisplayName(ownerName)
            }
            repository.createSharedWishlistLink(
                title = title,
                ownerName = ownerName,
                wishIds = wishIds,
                includeAllActive = includeAllActive,
                includeNotes = includeNotes,
                includeUrls = includeUrls
            ).onSuccess { link ->
                showMessage("Created live link \"${link.title}\"!")
                onCreated(link)
            }.onFailure { err ->
                showMessage(err.localizedMessage ?: "Failed to create live wishlist link")
            }
        }
    }

    fun updateSharedWishlistLink(link: SharedWishlistLink, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            if (link.ownerName.isNotBlank()) {
                repository.setOwnerDisplayName(link.ownerName)
            }
            repository.updateSharedWishlistLink(link)
                .onSuccess {
                    showMessage("Updated \"${link.title}\"")
                    onSaved()
                }
                .onFailure { err ->
                    showMessage(err.localizedMessage ?: "Failed to update shared link")
                }
        }
    }

    fun deleteSharedWishlistLink(link: SharedWishlistLink) {
        viewModelScope.launch {
            repository.deleteSharedWishlistLink(link.id)
                .onSuccess { showMessage("Revoked link \"${link.title}\"") }
                .onFailure { err -> showMessage(err.localizedMessage ?: "Failed to revoke link") }
        }
    }

    // Incoming Saved Wishlists (Shared With Me) & Live Claims
    fun setGiverDisplayName(name: String?) {
        viewModelScope.launch { repository.setGiverDisplayName(name) }
    }

    suspend fun fetchPublicWishlistMetadata(rawUrl: String): PublicSharedWishlistDoc? {
        val parsed = FirebaseSyncManager.parseWishlistUrl(rawUrl)
        if (parsed.type != "live" || parsed.shareId.isNullOrBlank()) return null
        val (viewerApp, _) = repository.resolveViewerAppAndUid(parsed.projectKey)
        if (viewerApp == null) return null
        return repository.firebaseManager.fetchPublicWishlistOnce(viewerApp, parsed.shareId)
    }

    fun saveIncomingWishlist(
        editing: SavedWishlist?,
        url: String,
        title: String,
        ownerName: String,
        personMode: String, // "existing" | "create" | "unlinked"
        selectedPersonId: String?,
        notes: String,
        navigateToHub: Boolean = false,
        onFinished: () -> Unit = {}
    ) {
        viewModelScope.launch {
            var resolvedPersonId: String? = null
            val cleanOwner = ownerName.trim()
            if (personMode == "existing" && !selectedPersonId.isNullOrBlank()) {
                resolvedPersonId = selectedPersonId
            } else if (personMode == "create") {
                val createName = cleanOwner.ifBlank {
                    title.substringBefore("'").trim().ifBlank { "Friend" }
                }
                val createdPerson = repository.addPerson(
                    name = createName,
                    budget = 0.0,
                    relationship = "Family & Close Circle",
                    birthday = null,
                    sizes = ClothingSizes(),
                    notes = "",
                    avatarShape = null,
                    avatarTone = null
                )
                resolvedPersonId = createdPerson.id
            }

            if (editing != null) {
                val resolvedTitle = title.trim().ifBlank { editing.title }
                val linkedName = dataset.value.people.firstOrNull { it.id == resolvedPersonId }?.name.orEmpty()
                repository.updateSavedWishlist(
                    editing.copy(
                        url = url.trim(),
                        title = resolvedTitle,
                        ownerName = cleanOwner.ifBlank { linkedName.ifBlank { editing.ownerName } },
                        personId = resolvedPersonId,
                        notes = notes.trim()
                    )
                )
                showMessage("Updated \"$resolvedTitle\"")
            } else {
                val outcome = repository.saveIncomingWishlist(
                    rawUrl = url.trim(),
                    title = title.trim(),
                    ownerName = cleanOwner,
                    personId = resolvedPersonId,
                    personExplicit = personMode != "auto",
                    notes = notes.trim()
                )
                when {
                    outcome.wasOwnLink -> {
                        _uiState.update {
                            it.copy(
                                activeTab = NavTab.WISHLIST,
                                wishlistSubTab = WishlistHubTab.OUTBOUND,
                                composerPreset = null
                            )
                        }
                        showMessage("That's one of your own shared links!")
                    }
                    outcome.saved != null -> {
                        _uiState.update { state ->
                            state.copy(
                                selectedSavedWishlistId = outcome.saved.id,
                                activeTab = if (navigateToHub) NavTab.WISHLIST else state.activeTab,
                                wishlistSubTab = if (navigateToHub) WishlistHubTab.INCOMING else state.wishlistSubTab,
                                composerPreset = if (navigateToHub) null else state.composerPreset
                            )
                        }
                        showMessage(
                            if (outcome.updatedExisting) "Updated \"${outcome.saved.title}\" in Shared With Me"
                            else "Saved \"${outcome.saved.title}\" to Shared With Me!"
                        )
                    }
                }
            }
            closeSaveWishlistSheet()
            onFinished()
        }
    }

    fun deleteSavedWishlistWithUndo(id: String) {
        val removed = repository.deleteSavedWishlist(id) ?: return
        if (_uiState.value.selectedSavedWishlistId == id) {
            val nextId = dataset.value.savedWishlists.firstOrNull()?.id
            _uiState.update { it.copy(selectedSavedWishlistId = nextId) }
        }
        showMessage("Removed \"${removed.title}\"", actionLabel = "Undo") {
            repository.restoreSavedWishlist(removed)
            _uiState.update { it.copy(selectedSavedWishlistId = removed.id) }
        }
    }

    fun claimOrResearchPublicWish(
        shareId: String,
        projectKey: String?,
        wishId: String,
        giverName: String,
        claimImmediately: Boolean,
        ownerDisplay: String
    ) {
        viewModelScope.launch {
            val cleanName = giverName.trim()
            if (cleanName.isBlank()) return@launch
            repository.setGiverDisplayName(cleanName)
            val (viewerApp, _) = repository.resolveViewerAppAndUid(projectKey)
            if (viewerApp == null) {
                showMessage("Connect Firebase Cloud Sync to update claims")
                return@launch
            }
            val token = repository.localStorage.ensureClaimerToken()
            runCatching {
                if (claimImmediately) {
                    val (ok, other) = repository.firebaseManager.claimPublicWishItem(
                        viewerApp = viewerApp,
                        shareId = shareId,
                        claim = SharedWishClaim(
                            wishId = wishId,
                            claimedBy = cleanName,
                            claimerToken = token,
                            purchased = false,
                            status = "reserved",
                            researchers = listOf(cleanName),
                            researcherTokens = listOf(token),
                            claimedAt = Instant.now().toString()
                        )
                    )
                    if (!ok) showMessage("Heads up — ${other ?: "someone else"} just claimed this item!")
                    else showMessage("Claimed! Strictly hidden from $ownerDisplay.")
                } else {
                    val (ok, other) = repository.firebaseManager.setWishResearching(
                        viewerApp = viewerApp,
                        shareId = shareId,
                        wishId = wishId,
                        researcherName = cleanName,
                        researcherToken = token
                    )
                    if (!ok) showMessage("Already claimed by ${other ?: "someone else"}.")
                    else showMessage("Marked as Researching (hidden from $ownerDisplay).")
                }
            }.onFailure { err ->
                showMessage(err.localizedMessage ?: "Failed to update claim")
            }
        }
    }

    fun togglePurchasedOnPublicWish(
        shareId: String,
        projectKey: String?,
        existingClaim: SharedWishClaim
    ) {
        viewModelScope.launch {
            val (viewerApp, _) = repository.resolveViewerAppAndUid(projectKey)
            if (viewerApp == null) return@launch
            val nextPurchased = !existingClaim.purchased
            runCatching {
                repository.firebaseManager.updatePublicWishClaim(
                    viewerApp = viewerApp,
                    shareId = shareId,
                    wishId = existingClaim.wishId,
                    existing = existingClaim,
                    purchased = nextPurchased,
                    status = if (nextPurchased) "purchased" else "reserved"
                )
                showMessage(if (nextPurchased) "Marked as Purchased!" else "Marked as Reserved")
            }.onFailure { err ->
                showMessage(err.localizedMessage ?: "Failed to update claim")
            }
        }
    }

    fun saveGiverNoteOnPublicWish(
        shareId: String,
        projectKey: String?,
        existingClaim: SharedWishClaim,
        note: String,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val (viewerApp, _) = repository.resolveViewerAppAndUid(projectKey)
            if (viewerApp == null) return@launch
            runCatching {
                repository.firebaseManager.updatePublicWishClaim(
                    viewerApp = viewerApp,
                    shareId = shareId,
                    wishId = existingClaim.wishId,
                    existing = existingClaim,
                    giverNote = note
                )
                showMessage("Saved secret note for other gift-givers!")
                onSaved()
            }.onFailure { err ->
                showMessage(err.localizedMessage ?: "Failed to save note")
            }
        }
    }

    fun releasePublicWishClaim(
        shareId: String,
        projectKey: String?,
        wishId: String
    ) {
        viewModelScope.launch {
            val (viewerApp, _) = repository.resolveViewerAppAndUid(projectKey)
            if (viewerApp == null) return@launch
            val token = repository.localStorage.ensureClaimerToken()
            runCatching {
                repository.firebaseManager.unclaimOrStopResearchingPublicWishItem(
                    viewerApp = viewerApp,
                    shareId = shareId,
                    wishId = wishId,
                    claimerToken = token
                )
                showMessage("Released claim on wishlist item")
            }.onFailure { err ->
                showMessage(err.localizedMessage ?: "Failed to release claim")
            }
        }
    }

    fun addSharedWishToTracker(
        wish: PublishedWishItem,
        shareId: String,
        projectKey: String?,
        ownerName: String,
        personMode: String, // "existing" | "create" | "unassigned"
        selectedPersonId: String?,
        eventId: String?, // null = Ideas
        giverName: String,
        claimImmediately: Boolean,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val cleanGiver = giverName.trim()
            if (cleanGiver.isBlank()) return@launch
            repository.setGiverDisplayName(cleanGiver)
            val token = repository.localStorage.ensureClaimerToken()

            val finalPersonId = when (personMode) {
                "create" -> {
                    val created = repository.addPerson(
                        name = ownerName.trim().ifBlank { "Friend" },
                        budget = 0.0,
                        relationship = "Friend or Family",
                        birthday = null,
                        sizes = ClothingSizes(),
                        notes = "Added from shared wishlist",
                        avatarShape = null,
                        avatarTone = null
                    )
                    created.id
                }
                "existing" -> selectedPersonId?.takeIf { it.isNotBlank() }
                else -> null
            }

            val (viewerApp, _) = repository.resolveViewerAppAndUid(projectKey)
            val syncStatus = if (claimImmediately) "reserved" else "researching"
            if (viewerApp != null) {
                runCatching {
                    if (claimImmediately) {
                        val (ok, other) = repository.firebaseManager.claimPublicWishItem(
                            viewerApp = viewerApp,
                            shareId = shareId,
                            claim = SharedWishClaim(
                                wishId = wish.id,
                                claimedBy = cleanGiver,
                                claimerToken = token,
                                purchased = false,
                                status = "reserved",
                                researchers = listOf(cleanGiver),
                                researcherTokens = listOf(token),
                                claimedAt = Instant.now().toString()
                            )
                        )
                        if (!ok) {
                            showMessage("Note: ${other ?: "someone else"} just claimed this, saved to your tracker.")
                        }
                    } else {
                        repository.firebaseManager.setWishResearching(
                            viewerApp = viewerApp,
                            shareId = shareId,
                            wishId = wish.id,
                            researcherName = cleanGiver,
                            researcherToken = token
                        )
                    }
                }
            }

            repository.addGift(
                GiftItem(
                    id = "draft",
                    title = wish.title,
                    personId = finalPersonId,
                    eventId = eventId,
                    cost = 0.0,
                    store = if (wish.url.isNotBlank()) LinkScraper.inferStoreFromUrl(wish.url) else "",
                    url = wish.url,
                    notes = wish.notes,
                    bought = false,
                    received = false,
                    wrapped = false,
                    saveForLater = false,
                    sourceWishlistShareId = shareId,
                    sourceWishId = wish.id,
                    sourceWishlistKey = projectKey,
                    sourceSyncStatus = syncStatus
                )
            )
            showMessage("Added \"${wish.title}\" to your Gift Tracker!")
            onDone()
        }
    }

    // Settings
    fun openSettings() = _uiState.update { it.copy(isSettingsOpen = true) }
    fun closeSettings() = _uiState.update { it.copy(isSettingsOpen = false) }

    fun setThemeSeed(seed: ThemeSeed) {
        viewModelScope.launch { repository.setThemeSeed(seed) }
    }

    fun setColorMode(mode: ColorMode) {
        viewModelScope.launch { repository.setColorMode(mode) }
    }

    fun setWebAppUrl(url: String?) {
        viewModelScope.launch { repository.setWebAppUrl(url) }
    }

    fun connectFirebaseConfig(config: FirebaseRuntimeConfig?) {
        viewModelScope.launch {
            repository.saveRuntimeFirebaseConfig(config)
            showMessage(if (config != null) "Connected to Firebase (${config.projectId})" else "Cleared runtime Firebase config")
        }
    }

    fun handleIncomingPairingUri(uri: android.net.Uri) {
        val decoded = com.twintitanium.gifttracker.data.remote.FirebaseSyncManager.decodePairingToken(uri.toString())
            ?: uri.getQueryParameter("cfg")?.let { com.twintitanium.gifttracker.data.remote.FirebaseSyncManager.decodePairingToken(it) }
            ?: uri.getQueryParameter("pair")?.let { com.twintitanium.gifttracker.data.remote.FirebaseSyncManager.decodePairingToken(it) }

        if (decoded == null) {
            showMessage("Invalid or expired Firebase pairing link")
            return
        }

        viewModelScope.launch {
            if (!decoded.webUrl.isNullOrBlank()) {
                repository.setWebAppUrl(decoded.webUrl)
            }
            val currentCloud = cloudState.value
            val alreadyBuiltInSameProject =
                currentCloud.isFromGoogleServicesResource &&
                    currentCloud.connectedProjectId == decoded.config.projectId

            if (!alreadyBuiltInSameProject) {
                repository.saveRuntimeFirebaseConfig(decoded.config)
            }
            openSettings()
            if (currentCloud.userUid != null && currentCloud.connectedProjectId == decoded.config.projectId) {
                showMessage("Already synced with ${decoded.config.projectId}!")
            } else {
                showMessage("Connected to ${decoded.config.projectId} — tap Sign in with Google!")
            }
        }
    }

    fun signInGoogle(activityContext: Context, webClientId: String?) {
        viewModelScope.launch {
            val res = repository.signInWithGoogle(activityContext, webClientId)
            res.onSuccess { showMessage("Signed in & syncing with Cloud Firestore!") }
                .onFailure { showMessage(it.localizedMessage ?: "Google Sign-In failed") }
        }
    }

    fun signInAnonymous() {
        viewModelScope.launch {
            val res = repository.signInAnonymously()
            res.onSuccess { showMessage("Anonymous Cloud Sync active!") }
                .onFailure { showMessage(it.localizedMessage ?: "Anonymous Sign-In failed") }
        }
    }

    fun signOutFirebase() {
        repository.signOut()
        showMessage("Signed out of Cloud Sync")
    }

    fun pushLocalToCloud() {
        viewModelScope.launch {
            repository.pushLocalToCloud()
                .onSuccess { showMessage("All local data pushed to Cloud Firestore!") }
                .onFailure { showMessage(it.localizedMessage ?: "Push failed") }
        }
    }

    fun exportJsonBackup(): String = repository.exportBackupJson()

    fun importJsonBackup(raw: String) {
        viewModelScope.launch {
            val ok = repository.importBackupJson(raw)
            showMessage(if (ok) "Backup imported successfully!" else "Invalid backup JSON file")
        }
    }

    fun resetData() {
        viewModelScope.launch {
            repository.resetAllData()
            closeSettings()
            showMessage("Cleared data to a fresh clean slate")
        }
    }
}
