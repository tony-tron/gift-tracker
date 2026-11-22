package com.tonytron.gifttracker.data.repository

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.tonytron.gifttracker.data.local.LocalGiftStorage
import com.tonytron.gifttracker.data.local.UserPreferences
import com.tonytron.gifttracker.data.model.ClothingSizes
import com.tonytron.gifttracker.data.model.ColorMode
import com.tonytron.gifttracker.data.model.FirebaseRuntimeConfig
import com.tonytron.gifttracker.data.model.GiftDataset
import com.tonytron.gifttracker.data.model.GiftEvent
import com.tonytron.gifttracker.data.model.GiftItem
import com.tonytron.gifttracker.data.model.IdGenerator
import com.tonytron.gifttracker.data.model.Person
import com.tonytron.gifttracker.data.model.SyncStatus
import com.tonytron.gifttracker.data.model.ThemeSeed
import com.tonytron.gifttracker.data.model.WishItem
import com.tonytron.gifttracker.data.remote.FirebaseSyncManager
import com.tonytron.gifttracker.widget.GiftTrackerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudAuthState(
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val connectedProjectId: String? = null,
    val isFromGoogleServicesResource: Boolean = false,
    val webClientId: String? = null,
    val userUid: String? = null,
    val userLabel: String? = null,
    val syncError: String? = null
)

class GiftRepository(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val localStorage = LocalGiftStorage(appContext)
    private val firebaseManager = FirebaseSyncManager(appContext)

    private val _dataset = MutableStateFlow(GiftDataset.EMPTY)
    val dataset: StateFlow<GiftDataset> = _dataset.asStateFlow()

    private val _preferences = MutableStateFlow(UserPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private val _cloudState = MutableStateFlow(CloudAuthState())
    val cloudState: StateFlow<CloudAuthState> = _cloudState.asStateFlow()

    private var currentFirebaseApp: FirebaseApp? = null
    private var currentFirebaseUser: FirebaseUser? = null

    init {
        scope.launch {
            val initial = localStorage.readDataset()
            _dataset.value = initial
            GiftTrackerWidget.refreshAll(appContext)
        }

        scope.launch {
            localStorage.preferencesFlow.collectLatest { prefs ->
                val prevRuntime = _preferences.value.runtimeFirebaseConfig
                _preferences.value = prefs
                if (prevRuntime != prefs.runtimeFirebaseConfig || currentFirebaseApp == null) {
                    rebindFirebase(prefs.runtimeFirebaseConfig)
                }
            }
        }
    }

    private fun persistAndRefreshWidget(updated: GiftDataset) {
        _dataset.value = updated
        scope.launch {
            localStorage.writeDataset(updated)
            GiftTrackerWidget.refreshAll(appContext)
        }
    }

    private fun rebindFirebase(runtimeConfig: FirebaseRuntimeConfig?) {
        firebaseManager.clearWorkspaceListeners()
        firebaseManager.clearAuthObserver()
        currentFirebaseUser = null

        val resolved = firebaseManager.resolveConnectedApp(runtimeConfig)
        if (resolved == null) {
            currentFirebaseApp = null
            _cloudState.value = CloudAuthState(syncStatus = SyncStatus.LOCAL_ONLY)
            return
        }

        val (app, info) = resolved
        currentFirebaseApp = app
        _cloudState.value = CloudAuthState(
            syncStatus = SyncStatus.SIGNED_OUT,
            connectedProjectId = info.projectId,
            isFromGoogleServicesResource = info.isFromGoogleServicesResource,
            webClientId = info.webClientId
        )

        firebaseManager.observeAuthState(app) { user ->
            currentFirebaseUser = user
            firebaseManager.clearWorkspaceListeners()

            if (user == null) {
                _cloudState.update {
                    it.copy(
                        syncStatus = SyncStatus.SIGNED_OUT,
                        userUid = null,
                        userLabel = null,
                        syncError = null
                    )
                }
                return@observeAuthState
            }

            _cloudState.update {
                it.copy(
                    syncStatus = SyncStatus.CONNECTING,
                    userUid = user.uid,
                    userLabel = user.email ?: user.displayName ?: if (user.isAnonymous) "Anonymous Sync" else "Cloud User",
                    syncError = null
                )
            }

            scope.launch {
                runCatching {
                    val hasRemote = firebaseManager.checkRemoteHasData(app, user.uid)
                    val localNow = _dataset.value
                    val hasLocal = localNow.people.isNotEmpty() || localNow.gifts.isNotEmpty() || localNow.wishes.isNotEmpty()
                    if (!hasRemote && hasLocal) {
                        firebaseManager.uploadFullDataset(app, user.uid, localNow)
                    }
                }.onFailure { err ->
                    _cloudState.update { it.copy(syncStatus = SyncStatus.ERROR, syncError = err.localizedMessage) }
                }

                firebaseManager.subscribeToWorkspace(
                    app = app,
                    uid = user.uid,
                    onPeople = { items ->
                        persistAndRefreshWidget(_dataset.value.copy(people = items))
                        _cloudState.update { it.copy(syncStatus = SyncStatus.SYNCED, syncError = null) }
                    },
                    onEvents = { items ->
                        val nextEvents = items.ifEmpty { GiftDataset.defaultEvents() }
                        persistAndRefreshWidget(_dataset.value.copy(events = nextEvents))
                        _cloudState.update { it.copy(syncStatus = SyncStatus.SYNCED, syncError = null) }
                    },
                    onGifts = { items ->
                        persistAndRefreshWidget(_dataset.value.copy(gifts = items))
                        _cloudState.update { it.copy(syncStatus = SyncStatus.SYNCED, syncError = null) }
                    },
                    onWishes = { items ->
                        persistAndRefreshWidget(_dataset.value.copy(wishes = items))
                        _cloudState.update { it.copy(syncStatus = SyncStatus.SYNCED, syncError = null) }
                    },
                    onError = { err ->
                        _cloudState.update { it.copy(syncStatus = SyncStatus.ERROR, syncError = err.localizedMessage) }
                    }
                )
            }
        }
    }

    // --- Preferences & Cloud Auth ---

    suspend fun setThemeSeed(seed: ThemeSeed) = localStorage.setThemeSeed(seed)
    suspend fun setColorMode(mode: ColorMode) = localStorage.setColorMode(mode)
    suspend fun setSelectedEventId(id: String) = localStorage.setSelectedEventId(id)

    suspend fun saveRuntimeFirebaseConfig(config: FirebaseRuntimeConfig?) {
        localStorage.setRuntimeFirebaseConfig(config)
        rebindFirebase(config)
    }

    suspend fun signInWithGoogle(activityContext: Context, customWebClientId: String?): Result<Unit> {
        val app = currentFirebaseApp ?: return Result.failure(IllegalStateException("Firebase is not configured yet."))
        val clientId = customWebClientId?.takeIf { it.isNotBlank() }
            ?: _cloudState.value.webClientId
            ?: return Result.failure(
                IllegalStateException("Enter your Firebase Web Client ID (OAuth 2.0 client ID) to sign in with Google, or use Anonymous Sync.")
            )

        // Persist webClientId if using runtime config
        val rt = _preferences.value.runtimeFirebaseConfig
        if (rt != null && rt.webClientId != clientId) {
            localStorage.setRuntimeFirebaseConfig(rt.copy(webClientId = clientId))
        }

        return runCatching {
            firebaseManager.signInWithGoogle(activityContext, app, clientId)
            Unit
        }.onFailure { err ->
            _cloudState.update { it.copy(syncError = err.localizedMessage) }
        }
    }

    suspend fun signInAnonymously(): Result<Unit> {
        val app = currentFirebaseApp ?: return Result.failure(IllegalStateException("Firebase is not configured yet."))
        return runCatching {
            firebaseManager.signInAnonymously(app)
            Unit
        }.onFailure { err ->
            _cloudState.update { it.copy(syncError = err.localizedMessage) }
        }
    }

    fun signOut() {
        currentFirebaseApp?.let { firebaseManager.signOut(it) }
    }

    suspend fun pushLocalToCloud(): Result<Unit> {
        val app = currentFirebaseApp ?: return Result.failure(IllegalStateException("Not connected"))
        val user = currentFirebaseUser ?: return Result.failure(IllegalStateException("Not signed in"))
        return runCatching {
            firebaseManager.uploadFullDataset(app, user.uid, _dataset.value)
        }
    }

    // --- Gift CRUD ---

    fun addGift(draft: GiftItem): GiftItem {
        val item = draft.copy(id = IdGenerator.generate("gift"))
        val updated = _dataset.value.copy(gifts = listOf(item) + _dataset.value.gifts)
        persistAndRefreshWidget(updated)
        syncUpsert("gifts", item.id, item.toFirestoreMap())
        return item
    }

    fun updateGift(item: GiftItem) {
        val updated = _dataset.value.copy(
            gifts = _dataset.value.gifts.map { if (it.id == item.id) item else it }
        )
        persistAndRefreshWidget(updated)
        syncUpsert("gifts", item.id, item.toFirestoreMap())
    }

    fun deleteGift(id: String): GiftItem? {
        val target = _dataset.value.gifts.firstOrNull { it.id == id } ?: return null
        val updated = _dataset.value.copy(gifts = _dataset.value.gifts.filterNot { it.id == id })
        persistAndRefreshWidget(updated)
        syncDelete("gifts", id)
        return target
    }

    fun restoreGift(item: GiftItem) {
        val updated = _dataset.value.copy(gifts = listOf(item) + _dataset.value.gifts)
        persistAndRefreshWidget(updated)
        syncUpsert("gifts", item.id, item.toFirestoreMap())
    }

    fun toggleGiftStatus(id: String, field: String) {
        val target = _dataset.value.gifts.firstOrNull { it.id == id } ?: return
        val patched = when (field) {
            "bought" -> {
                val next = !target.bought
                target.copy(
                    bought = next,
                    received = if (!next) false else target.received,
                    wrapped = if (!next) false else target.wrapped
                )
            }
            "received" -> {
                val next = !target.received
                target.copy(
                    received = next,
                    bought = if (next) true else target.bought,
                    wrapped = if (!next) false else target.wrapped
                )
            }
            "wrapped" -> {
                val next = !target.wrapped
                target.copy(
                    wrapped = next,
                    bought = if (next) true else target.bought,
                    received = if (next) true else target.received
                )
            }
            else -> target
        }
        updateGift(patched)
    }

    fun moveGiftToEvent(giftId: String, eventId: String, personId: String? = null) {
        val target = _dataset.value.gifts.firstOrNull { it.id == giftId } ?: return
        val patched = target.copy(
            eventId = eventId,
            personId = personId ?: target.personId,
            saveForLater = false,
            bought = target.bought || target.saveForLater
        )
        updateGift(patched)
    }

    fun moveGiftToIdeas(giftId: String, saveForLater: Boolean) {
        val target = _dataset.value.gifts.firstOrNull { it.id == giftId } ?: return
        val patched = target.copy(
            eventId = null,
            saveForLater = saveForLater,
            bought = if (saveForLater) true else target.bought,
            wrapped = false
        )
        updateGift(patched)
    }

    // --- People CRUD ---

    fun addPerson(
        name: String,
        budget: Double,
        relationship: String,
        birthday: String?,
        sizes: ClothingSizes,
        notes: String,
        avatarShape: String?,
        avatarTone: String?
    ): Person {
        val idx = _dataset.value.people.size
        val person = Person(
            id = IdGenerator.generate("person"),
            name = name.trim(),
            budget = budget,
            relationship = relationship.ifBlank { "Family & Close Circle" },
            birthday = birthday?.takeIf { it.isNotBlank() },
            sizes = sizes,
            notes = notes,
            avatarShape = avatarShape ?: IdGenerator.AVATAR_SHAPES[idx % IdGenerator.AVATAR_SHAPES.size],
            avatarTone = avatarTone ?: IdGenerator.AVATAR_TONES[idx % IdGenerator.AVATAR_TONES.size],
            order = idx
        )
        val updated = _dataset.value.copy(people = _dataset.value.people + person)
        persistAndRefreshWidget(updated)
        syncUpsert("people", person.id, person.toFirestoreMap())
        return person
    }

    fun updatePerson(person: Person) {
        val updated = _dataset.value.copy(
            people = _dataset.value.people.map { if (it.id == person.id) person else it }
        )
        persistAndRefreshWidget(updated)
        syncUpsert("people", person.id, person.toFirestoreMap())
    }

    fun deletePerson(id: String) {
        val updated = _dataset.value.copy(
            people = _dataset.value.people.filterNot { it.id == id },
            gifts = _dataset.value.gifts.map { if (it.personId == id) it.copy(personId = null) else it }
        )
        persistAndRefreshWidget(updated)
        syncDelete("people", id)
    }

    // --- Event CRUD ---

    fun addEvent(name: String, year: Int, occasion: String, emoji: String): GiftEvent {
        val ev = GiftEvent(
            id = IdGenerator.generate("event-$year"),
            name = name.trim(),
            year = year,
            occasion = occasion,
            emoji = emoji,
            isActive = true,
            isArchived = false,
            order = _dataset.value.events.size
        )
        val updated = _dataset.value.copy(events = listOf(ev) + _dataset.value.events)
        persistAndRefreshWidget(updated)
        syncUpsert("events", ev.id, ev.toFirestoreMap())
        return ev
    }

    // --- Wishes CRUD ---

    fun addWish(title: String, url: String, priority: String = "medium"): WishItem {
        val wish = WishItem(
            id = IdGenerator.generate("wish"),
            title = title.trim(),
            url = url.trim(),
            priority = priority
        )
        val updated = _dataset.value.copy(wishes = listOf(wish) + _dataset.value.wishes)
        persistAndRefreshWidget(updated)
        syncUpsert("wishes", wish.id, wish.toFirestoreMap())
        return wish
    }

    fun updateWish(wish: WishItem) {
        val updated = _dataset.value.copy(
            wishes = _dataset.value.wishes.map { if (it.id == wish.id) wish else it }
        )
        persistAndRefreshWidget(updated)
        syncUpsert("wishes", wish.id, wish.toFirestoreMap())
    }

    fun deleteWish(id: String) {
        val updated = _dataset.value.copy(wishes = _dataset.value.wishes.filterNot { it.id == id })
        persistAndRefreshWidget(updated)
        syncDelete("wishes", id)
    }

    // --- Backup & Reset ---

    fun exportBackupJson(): String = localStorage.exportPrettyJson(_dataset.value)

    suspend fun importBackupJson(raw: String): Boolean {
        val parsed = localStorage.parseBackupJson(raw) ?: return false
        persistAndRefreshWidget(parsed)
        val app = currentFirebaseApp
        val user = currentFirebaseUser
        if (app != null && user != null) {
            runCatching { firebaseManager.uploadFullDataset(app, user.uid, parsed) }
        }
        return true
    }

    suspend fun resetAllData() {
        persistAndRefreshWidget(GiftDataset.EMPTY)
        val app = currentFirebaseApp
        val user = currentFirebaseUser
        if (app != null && user != null) {
            runCatching { firebaseManager.uploadFullDataset(app, user.uid, GiftDataset.EMPTY) }
        }
    }

    private fun syncUpsert(col: String, id: String, map: Map<String, Any?>) {
        val app = currentFirebaseApp ?: return
        val user = currentFirebaseUser ?: return
        scope.launch {
            runCatching { firebaseManager.upsertDoc(app, user.uid, col, id, map) }
        }
    }

    private fun syncDelete(col: String, id: String) {
        val app = currentFirebaseApp ?: return
        val user = currentFirebaseUser ?: return
        scope.launch {
            runCatching { firebaseManager.deleteDoc(app, user.uid, col, id) }
        }
    }
}
