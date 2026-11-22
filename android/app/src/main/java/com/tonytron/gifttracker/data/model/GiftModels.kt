package com.tonytron.gifttracker.data.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

enum class ThemeSeed(val key: String, val displayName: String, val swatchHex: Long) {
    DYNAMIC("dynamic", "Wallpaper Dynamic", 0xFFD0BCFF),
    TERRACOTTA("terracotta", "Festive Terracotta", 0xFFF47C5D),
    FOREST("forest", "Botanical Sage", 0xFF5EBA73),
    OCHRE("ochre", "Sunlit Ochre", 0xFFEBA328),
    OCEAN("ocean", "Pacific Teal", 0xFF3AB6CC);

    companion object {
        fun fromKey(key: String?): ThemeSeed =
            entries.firstOrNull { it.key == key } ?: DYNAMIC
    }
}

enum class ColorMode(val key: String, val label: String) {
    SYSTEM("system", "System"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        fun fromKey(key: String?): ColorMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class NavTab(val label: String, val shortLabel: String) {
    EVENTS("Occasions", "Events"),
    IDEAS("Gift Ideas", "Ideas"),
    PEOPLE("People & Sizes", "People"),
    WISHLIST("My Wishlist", "Me")
}

enum class SyncStatus {
    LOCAL_ONLY,
    SIGNED_OUT,
    CONNECTING,
    SYNCED,
    ERROR
}

@Serializable
data class ClothingSizes(
    val top: String? = null,
    val bottoms: String? = null,
    val intimates: String? = null,
    val shoe: String? = null,
    val ring: String? = null,
    val other: String? = null
) {
    fun nonEmptyEntries(): List<Pair<String, String>> = buildList {
        if (!top.isNullOrBlank()) add("Top" to top)
        if (!bottoms.isNullOrBlank()) add("Bottoms" to bottoms)
        if (!intimates.isNullOrBlank()) add("Intimates" to intimates)
        if (!shoe.isNullOrBlank()) add("Shoe" to shoe)
        if (!ring.isNullOrBlank()) add("Ring" to ring)
        if (!other.isNullOrBlank()) add("Other" to other)
    }

    fun toFirestoreMap(): Map<String, Any> = buildMap {
        top?.takeIf { it.isNotBlank() }?.let { put("top", it) }
        bottoms?.takeIf { it.isNotBlank() }?.let { put("bottoms", it) }
        intimates?.takeIf { it.isNotBlank() }?.let { put("intimates", it) }
        shoe?.takeIf { it.isNotBlank() }?.let { put("shoe", it) }
        ring?.takeIf { it.isNotBlank() }?.let { put("ring", it) }
        other?.takeIf { it.isNotBlank() }?.let { put("other", it) }
    }

    companion object {
        fun fromFirestoreMap(raw: Map<*, *>?): ClothingSizes {
            if (raw == null) return ClothingSizes()
            return ClothingSizes(
                top = raw["top"] as? String,
                bottoms = raw["bottoms"] as? String,
                intimates = raw["intimates"] as? String,
                shoe = raw["shoe"] as? String,
                ring = raw["ring"] as? String,
                other = raw["other"] as? String
            )
        }
    }
}

@Serializable
data class Person(
    val id: String,
    val name: String,
    val budget: Double = 0.0,
    val relationship: String = "Family & Close Circle",
    val sizes: ClothingSizes = ClothingSizes(),
    val notes: String = "",
    val avatarShape: String = "squircle", // squircle | cookie | clover | arch | pill | flower
    val avatarTone: String = "terracotta", // terracotta | sage | amber | teal | rose | ochre
    val birthday: String? = null,
    val order: Int = 0
) {
    fun toFirestoreMap(): Map<String, Any?> = buildMap {
        put("id", id)
        put("name", name)
        put("budget", budget)
        put("relationship", relationship)
        put("sizes", sizes.toFirestoreMap())
        put("notes", notes)
        put("avatarShape", avatarShape)
        put("avatarTone", avatarTone)
        if (!birthday.isNullOrBlank()) put("birthday", birthday)
        put("order", order)
    }

    companion object {
        fun fromFirestoreMap(map: Map<String, Any?>, fallbackId: String): Person = Person(
            id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackId,
            name = (map["name"] as? String) ?: "Unnamed",
            budget = (map["budget"] as? Number)?.toDouble() ?: 0.0,
            relationship = (map["relationship"] as? String) ?: "Family & Close Circle",
            sizes = ClothingSizes.fromFirestoreMap(map["sizes"] as? Map<*, *>),
            notes = (map["notes"] as? String) ?: "",
            avatarShape = (map["avatarShape"] as? String) ?: "squircle",
            avatarTone = (map["avatarTone"] as? String) ?: "terracotta",
            birthday = map["birthday"] as? String,
            order = (map["order"] as? Number)?.toInt() ?: 0
        )
    }
}

@Serializable
data class GiftEvent(
    val id: String,
    val name: String,
    val year: Int = 2026,
    val occasion: String = "Christmas", // Christmas | Birthday | Anniversary | Other
    val emoji: String = "🎄",
    val isActive: Boolean = true,
    val isArchived: Boolean = false,
    val order: Int = 0
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "year" to year,
        "occasion" to occasion,
        "emoji" to emoji,
        "isActive" to isActive,
        "isArchived" to isArchived,
        "order" to order
    )

    companion object {
        fun fromFirestoreMap(map: Map<String, Any?>, fallbackId: String): GiftEvent = GiftEvent(
            id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackId,
            name = (map["name"] as? String) ?: "Occasion",
            year = (map["year"] as? Number)?.toInt() ?: 2026,
            occasion = (map["occasion"] as? String) ?: "Other",
            emoji = (map["emoji"] as? String) ?: "✨",
            isActive = (map["isActive"] as? Boolean) ?: true,
            isArchived = (map["isArchived"] as? Boolean) ?: false,
            order = (map["order"] as? Number)?.toInt() ?: 0
        )
    }
}

@Serializable
data class GiftItem(
    val id: String,
    val title: String,
    val personId: String? = null, // null = "Not sure who" pool
    val eventId: String? = null,  // null = Ideas Backlog / Stockpile
    val cost: Double = 0.0,
    val costLabel: String? = null,
    val store: String = "",
    val url: String = "",
    val tracking: String = "",
    val notes: String = "",
    val bought: Boolean = false,
    val received: Boolean = false,
    val wrapped: Boolean = false,
    val saveForLater: Boolean = false,
    val createdAt: String = Instant.now().toString()
) {
    fun displayPrice(): String {
        if (!costLabel.isNullOrBlank()) return costLabel
        if (cost <= 0.0) return ""
        return if (cost % 1.0 == 0.0) "$${cost.toInt()}" else "$%.2f".format(cost)
    }

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "personId" to personId,
        "eventId" to eventId,
        "cost" to cost,
        "costLabel" to costLabel,
        "store" to store,
        "url" to url,
        "tracking" to tracking,
        "notes" to notes,
        "bought" to bought,
        "received" to received,
        "wrapped" to wrapped,
        "saveForLater" to saveForLater,
        "createdAt" to createdAt
    )

    companion object {
        fun fromFirestoreMap(map: Map<String, Any?>, fallbackId: String): GiftItem = GiftItem(
            id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackId,
            title = (map["title"] as? String) ?: "Gift",
            personId = (map["personId"] as? String)?.takeIf { it.isNotBlank() },
            eventId = (map["eventId"] as? String)?.takeIf { it.isNotBlank() },
            cost = (map["cost"] as? Number)?.toDouble() ?: 0.0,
            costLabel = map["costLabel"] as? String,
            store = (map["store"] as? String) ?: "",
            url = (map["url"] as? String) ?: "",
            tracking = (map["tracking"] as? String) ?: "",
            notes = (map["notes"] as? String) ?: "",
            bought = (map["bought"] as? Boolean) ?: false,
            received = (map["received"] as? Boolean) ?: false,
            wrapped = (map["wrapped"] as? Boolean) ?: false,
            saveForLater = (map["saveForLater"] as? Boolean) ?: false,
            createdAt = (map["createdAt"] as? String) ?: ""
        )
    }
}

@Serializable
data class WishItem(
    val id: String,
    val title: String,
    val url: String = "",
    val notes: String = "",
    val received: Boolean = false,
    val receivedFrom: String = "",
    val receivedWhen: String = "",
    val priority: String = "medium" // high | medium | low
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "url" to url,
        "notes" to notes,
        "received" to received,
        "receivedFrom" to receivedFrom,
        "receivedWhen" to receivedWhen,
        "priority" to priority
    )

    companion object {
        fun fromFirestoreMap(map: Map<String, Any?>, fallbackId: String): WishItem = WishItem(
            id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackId,
            title = (map["title"] as? String) ?: "Wish",
            url = (map["url"] as? String) ?: "",
            notes = (map["notes"] as? String) ?: "",
            received = (map["received"] as? Boolean) ?: false,
            receivedFrom = (map["receivedFrom"] as? String) ?: "",
            receivedWhen = (map["receivedWhen"] as? String) ?: "",
            priority = (map["priority"] as? String) ?: "medium"
        )
    }
}

@Serializable
data class GiftDataset(
    val people: List<Person> = emptyList(),
    val events: List<GiftEvent> = defaultEvents(),
    val gifts: List<GiftItem> = emptyList(),
    val wishes: List<WishItem> = emptyList()
) {
    companion object {
        fun defaultEvents(): List<GiftEvent> = listOf(
            GiftEvent(
                id = "2026-christmas",
                name = "2026 Christmas",
                year = 2026,
                occasion = "Christmas",
                emoji = "🎄",
                isActive = true,
                isArchived = false,
                order = 0
            ),
            GiftEvent(
                id = "2026-birthdays",
                name = "2026 Birthdays",
                year = 2026,
                occasion = "Birthday",
                emoji = "🎂",
                isActive = true,
                isArchived = false,
                order = 1
            )
        )

        val EMPTY = GiftDataset(
            people = emptyList(),
            events = defaultEvents(),
            gifts = emptyList(),
            wishes = emptyList()
        )
    }
}

@Serializable
data class FirebaseRuntimeConfig(
    val apiKey: String,
    val projectId: String,
    val applicationId: String,
    val storageBucket: String? = null,
    val gcmSenderId: String? = null,
    val webClientId: String? = null
)

object IdGenerator {
    val AVATAR_SHAPES = listOf("squircle", "cookie", "clover", "arch", "pill", "flower")
    val AVATAR_TONES = listOf("terracotta", "sage", "amber", "teal", "rose", "ochre")

    fun generate(prefix: String): String {
        val ts = java.lang.Long.toString(System.currentTimeMillis(), 36)
        val rand = UUID.randomUUID().toString().replace("-", "").take(4)
        return "$prefix-$ts-$rand"
    }
}
