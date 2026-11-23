package com.tonytron.gifttracker.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonytron.gifttracker.data.model.ClothingSizes
import com.tonytron.gifttracker.data.model.ColorMode
import com.tonytron.gifttracker.data.model.FirebaseRuntimeConfig
import com.tonytron.gifttracker.data.model.GiftItem
import com.tonytron.gifttracker.data.model.NavTab
import com.tonytron.gifttracker.data.model.Person
import com.tonytron.gifttracker.data.model.ThemeSeed
import com.tonytron.gifttracker.data.model.WishItem
import com.tonytron.gifttracker.data.remote.LinkScraper
import com.tonytron.gifttracker.data.remote.ScrapedLinkResult
import com.tonytron.gifttracker.data.repository.GiftRepository
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
    val selectedPersonId: String? = null,
    val composerPreset: GiftComposerPreset? = null,
    val movingGift: GiftItem? = null,
    val editingPerson: Person? = null,
    val isPersonSheetOpen: Boolean = false,
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

    fun selectEvent(eventId: String) {
        viewModelScope.launch { repository.setSelectedEventId(eventId) }
    }

    fun selectPerson(personId: String?) {
        _uiState.update { it.copy(selectedPersonId = personId) }
    }

    fun openNewGiftComposer(personId: String? = null, eventId: String? = null) {
        val resolvedEvent = eventId ?: if (_uiState.value.activeTab == NavTab.EVENTS) {
            val sel = preferences.value.selectedEventId
            if (sel == "year:2026") "2026-christmas" else sel
        } else {
            null
        }
        _uiState.update {
            it.copy(
                composerPreset = GiftComposerPreset(
                    initialPersonId = personId,
                    initialEventId = resolvedEvent
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
        val targetEvent = if (sel == "year:2026") "2026-christmas" else sel
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
        _uiState.update { it.copy(activeTab = NavTab.WISHLIST, composerPreset = null) }
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
        repository.toggleGiftStatus(id, field)
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

    // Settings
    fun openSettings() = _uiState.update { it.copy(isSettingsOpen = true) }
    fun closeSettings() = _uiState.update { it.copy(isSettingsOpen = false) }

    fun setThemeSeed(seed: ThemeSeed) {
        viewModelScope.launch { repository.setThemeSeed(seed) }
    }

    fun setColorMode(mode: ColorMode) {
        viewModelScope.launch { repository.setColorMode(mode) }
    }

    fun connectFirebaseConfig(config: FirebaseRuntimeConfig?) {
        viewModelScope.launch {
            repository.saveRuntimeFirebaseConfig(config)
            showMessage(if (config != null) "Connected to Firebase (${config.projectId})" else "Cleared runtime Firebase config")
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
