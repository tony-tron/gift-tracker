package com.tonytron.gifttracker.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tonytron.gifttracker.data.model.ColorMode
import com.tonytron.gifttracker.data.model.FirebaseRuntimeConfig
import com.tonytron.gifttracker.data.model.GiftDataset
import com.tonytron.gifttracker.data.model.ThemeSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private val Context.appDataStore by preferencesDataStore(name = "gift_tracker_prefs_v1")

data class UserPreferences(
    val themeSeed: ThemeSeed = ThemeSeed.DYNAMIC,
    val colorMode: ColorMode = ColorMode.SYSTEM,
    val selectedEventId: String = "2026-christmas",
    val runtimeFirebaseConfig: FirebaseRuntimeConfig? = null
)

class LocalGiftStorage(private val context: Context) {

    private val fileMutex = Mutex()
    private val datasetFile: File
        get() = File(context.filesDir, DATASET_FILENAME)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = false
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = true
    }

    val preferencesFlow: Flow<UserPreferences> = context.appDataStore.data.map { prefs ->
        val fbRaw = prefs[KEY_FIREBASE_CONFIG]
        val fbCfg = if (!fbRaw.isNullOrBlank()) {
            runCatching { json.decodeFromString<FirebaseRuntimeConfig>(fbRaw) }.getOrNull()
        } else {
            null
        }
        UserPreferences(
            themeSeed = ThemeSeed.fromKey(prefs[KEY_THEME_SEED]),
            colorMode = ColorMode.fromKey(prefs[KEY_COLOR_MODE]),
            selectedEventId = prefs[KEY_SELECTED_EVENT] ?: "2026-christmas",
            runtimeFirebaseConfig = fbCfg
        )
    }

    suspend fun readDataset(): GiftDataset = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            readDatasetBlocking(context)
        }
    }

    suspend fun writeDataset(dataset: GiftDataset) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
                val tmp = File(context.filesDir, "$DATASET_FILENAME.tmp")
                tmp.writeText(json.encodeToString(GiftDataset.serializer(), dataset))
                tmp.renameTo(datasetFile)
            }
        }
    }

    fun exportPrettyJson(dataset: GiftDataset): String =
        prettyJson.encodeToString(GiftDataset.serializer(), dataset)

    fun parseBackupJson(raw: String): GiftDataset? = runCatching {
        val parsed = json.decodeFromString(GiftDataset.serializer(), raw)
        parsed.copy(
            events = parsed.events.ifEmpty { GiftDataset.defaultEvents() }
        )
    }.getOrNull()

    suspend fun setThemeSeed(seed: ThemeSeed) {
        context.appDataStore.edit { it[KEY_THEME_SEED] = seed.key }
    }

    suspend fun setColorMode(mode: ColorMode) {
        context.appDataStore.edit { it[KEY_COLOR_MODE] = mode.key }
    }

    suspend fun setSelectedEventId(eventId: String) {
        context.appDataStore.edit { it[KEY_SELECTED_EVENT] = eventId }
    }

    suspend fun setRuntimeFirebaseConfig(config: FirebaseRuntimeConfig?) {
        context.appDataStore.edit { prefs ->
            if (config == null) {
                prefs.remove(KEY_FIREBASE_CONFIG)
            } else {
                prefs[KEY_FIREBASE_CONFIG] = json.encodeToString(FirebaseRuntimeConfig.serializer(), config)
            }
        }
    }

    companion object {
        const val DATASET_FILENAME = "gift_tracker_dataset_v1.json"

        private val KEY_THEME_SEED = stringPreferencesKey("theme_seed")
        private val KEY_COLOR_MODE = stringPreferencesKey("color_mode")
        private val KEY_SELECTED_EVENT = stringPreferencesKey("selected_event_id")
        private val KEY_FIREBASE_CONFIG = stringPreferencesKey("firebase_runtime_config")

        /**
         * Fast helper also used by the Glance AppWidget process/coroutine to render the latest snapshot.
         */
        fun readDatasetBlocking(context: Context): GiftDataset {
            val file = File(context.filesDir, DATASET_FILENAME)
            if (!file.exists()) return GiftDataset.EMPTY
            return runCatching {
                val readerJson = Json { ignoreUnknownKeys = true }
                val decoded = readerJson.decodeFromString(GiftDataset.serializer(), file.readText())
                decoded.copy(
                    people = decoded.people.sortedBy { it.order },
                    events = decoded.events.ifEmpty { GiftDataset.defaultEvents() }
                )
            }.getOrDefault(GiftDataset.EMPTY)
        }

        fun readSelectedEventBlocking(context: Context): String = "2026-christmas"
    }
}
