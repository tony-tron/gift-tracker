package com.twintitanium.gifttracker.data.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.twintitanium.gifttracker.data.model.FirebaseRuntimeConfig
import com.twintitanium.gifttracker.data.model.GiftDataset
import com.twintitanium.gifttracker.data.model.GiftEvent
import com.twintitanium.gifttracker.data.model.GiftItem
import com.twintitanium.gifttracker.data.model.Person
import com.twintitanium.gifttracker.data.model.PublicSharedWishlistDoc
import com.twintitanium.gifttracker.data.model.SavedWishlist
import com.twintitanium.gifttracker.data.model.SharedWishClaim
import com.twintitanium.gifttracker.data.model.SharedWishlistLink
import com.twintitanium.gifttracker.data.model.WishItem
import java.time.Instant
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class FirebaseSyncManager(private val appContext: Context) {

    private var activeApp: FirebaseApp? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var observedAuth: FirebaseAuth? = null
    private val snapshotRegistrations = mutableListOf<ListenerRegistration>()

    data class ConnectedInfo(
        val projectId: String,
        val isFromGoogleServicesResource: Boolean,
        val webClientId: String?,
        val exportableConfig: FirebaseRuntimeConfig
    )

    fun resolveConnectedApp(runtimeConfig: FirebaseRuntimeConfig?): Pair<FirebaseApp, ConnectedInfo>? {
        if (runtimeConfig != null) {
            val appName = "runtime-${runtimeConfig.projectId}-${runtimeConfig.apiKey.takeLast(6)}"
            val existing = FirebaseApp.getApps(appContext).firstOrNull { it.name == appName }
            val app = existing ?: runCatching {
                val options = FirebaseOptions.Builder()
                    .setApiKey(runtimeConfig.apiKey)
                    .setProjectId(runtimeConfig.projectId)
                    .setApplicationId(runtimeConfig.applicationId)
                    .apply {
                        runtimeConfig.storageBucket?.takeIf { it.isNotBlank() }?.let { setStorageBucket(it) }
                        runtimeConfig.gcmSenderId?.takeIf { it.isNotBlank() }?.let { setGcmSenderId(it) }
                    }
                    .build()
                FirebaseApp.initializeApp(appContext, options, appName)
            }.getOrNull() ?: return null

            val resolvedWebClient = runtimeConfig.webClientId ?: getResourceString("default_web_client_id")
            activeApp = app
            return app to ConnectedInfo(
                projectId = runtimeConfig.projectId,
                isFromGoogleServicesResource = false,
                webClientId = resolvedWebClient,
                exportableConfig = runtimeConfig.copy(webClientId = resolvedWebClient)
            )
        }

        // Try default FirebaseApp initialized via google-services.json if present
        val defaultApp = runCatching {
            if (FirebaseApp.getApps(appContext).any { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
                FirebaseApp.getInstance()
            } else {
                FirebaseApp.initializeApp(appContext)
            }
        }.getOrNull() ?: return null

        val pid = defaultApp.options.projectId ?: return null
        val apiKey = defaultApp.options.apiKey.takeIf { it.isNotBlank() } ?: return null
        val appId = defaultApp.options.applicationId.ifBlank { "1:000000000000:android:gifttracker" }
        val resolvedWebClient = getResourceString("default_web_client_id")
        activeApp = defaultApp
        return defaultApp to ConnectedInfo(
            projectId = pid,
            isFromGoogleServicesResource = true,
            webClientId = resolvedWebClient,
            exportableConfig = FirebaseRuntimeConfig(
                apiKey = apiKey,
                projectId = pid,
                applicationId = appId,
                storageBucket = defaultApp.options.storageBucket,
                gcmSenderId = defaultApp.options.gcmSenderId,
                webClientId = resolvedWebClient
            )
        )
    }

    fun observeAuthState(
        app: FirebaseApp,
        onUserChanged: (FirebaseUser?) -> Unit
    ) {
        clearAuthObserver()
        val auth = FirebaseAuth.getInstance(app)
        val listener = FirebaseAuth.AuthStateListener { fbAuth ->
            onUserChanged(fbAuth.currentUser)
        }
        observedAuth = auth
        authListener = listener
        auth.addAuthStateListener(listener)
    }

    fun clearAuthObserver() {
        authListener?.let { observedAuth?.removeAuthStateListener(it) }
        authListener = null
        observedAuth = null
    }

    fun clearWorkspaceListeners() {
        snapshotRegistrations.forEach { runCatching { it.remove() } }
        snapshotRegistrations.clear()
    }

    /**
     * Sign in with Google via Android 14+ / 16 Credential Manager API.
     */
    suspend fun signInWithGoogle(
        activityContext: Context,
        app: FirebaseApp,
        webClientId: String
    ): FirebaseUser {
        val auth = FirebaseAuth.getInstance(app)
        val credentialManager = CredentialManager.create(activityContext)
        val trimmedClientId = webClientId.trim()

        val signInOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(trimmedClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        val result = try {
            credentialManager.getCredential(
                request = request,
                context = activityContext
            )
        } catch (primaryErr: androidx.credentials.exceptions.GetCredentialException) {
            val isReauthFailure = primaryErr is androidx.credentials.exceptions.GetCredentialCancellationException &&
                primaryErr.errorMessage?.toString()?.contains("reauth", ignoreCase = true) == true
            val isNoCredential = primaryErr is androidx.credentials.exceptions.NoCredentialException

            if (isReauthFailure || isNoCredential) {
                val activity = activityContext.findActivity()
                if (activity != null) {
                    return try {
                        val provider = OAuthProvider.newBuilder(GoogleAuthProvider.PROVIDER_ID, auth)
                            .setScopes(listOf("email", "profile"))
                            .addCustomParameter("prompt", "select_account")
                            .build()
                        val authResult = auth.startActivityForSignInWithProvider(activity, provider).await()
                        authResult.user ?: error("Google browser sign-in returned null FirebaseUser")
                    } catch (browserErr: Exception) {
                        throw buildSignInDiagnosticException(
                            activityContext = activityContext,
                            app = app,
                            trimmedClientId = trimmedClientId,
                            primaryErr = primaryErr,
                            fallbackErr = browserErr
                        )
                    }
                }
            }
            throw buildSignInDiagnosticException(
                activityContext = activityContext,
                app = app,
                trimmedClientId = trimmedClientId,
                primaryErr = primaryErr,
                fallbackErr = null
            )
        }

        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCred = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = auth.signInWithCredential(firebaseCred).await()
            return authResult.user ?: error("Google Sign-In returned null FirebaseUser")
        } else {
            error("Unexpected credential type returned by Credential Manager: ${credential::class.java.simpleName}")
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private fun buildSignInDiagnosticException(
        activityContext: Context,
        app: FirebaseApp,
        trimmedClientId: String,
        primaryErr: androidx.credentials.exceptions.GetCredentialException,
        fallbackErr: Exception?
    ): IllegalStateException {
        val sha1 = getAppSigningSha1(activityContext)
        val shaHint = if (sha1 != null) " (Installed APK SHA-1: $sha1)" else ""
        val details = buildString {
            appendLine("Primary Type: ${primaryErr.type}")
            appendLine("Primary ErrorMessage: ${primaryErr.errorMessage}")
            appendLine("Primary Cause: ${primaryErr.cause?.let { "${it::class.java.name}: ${it.message}" } ?: "none"}")
            if (fallbackErr != null) {
                appendLine("Browser Fallback Exception: ${fallbackErr::class.java.name}")
                appendLine("Browser Fallback Message: ${fallbackErr.localizedMessage ?: fallbackErr.message}")
                appendLine("Browser Fallback Cause: ${fallbackErr.cause?.let { "${it::class.java.name}: ${it.message}" } ?: "none"}")
            }
            appendLine("Package: ${activityContext.packageName}")
            appendLine("Installed SHA-1: ${sha1 ?: "unknown"}")
            appendLine("Firebase AppId: ${app.options.applicationId}")
            append("WebClientId: $trimmedClientId")
        }
        val summary = if (fallbackErr != null) {
            "Google Sign-In failed in both Credential Manager and browser fallback: ${fallbackErr.localizedMessage ?: fallbackErr.message}"
        } else if (primaryErr is androidx.credentials.exceptions.GetCredentialCancellationException) {
            "Sign-in canceled or rejected by Google Play Services. If you selected an account, verify your Firebase SHA-1 & OAuth consent screen$shaHint."
        } else {
            "${primaryErr.type}: ${primaryErr.errorMessage ?: primaryErr.localizedMessage}"
        }
        return IllegalStateException("$summary\n\n[Details]\n$details", fallbackErr ?: primaryErr)
    }

    suspend fun signInAnonymously(app: FirebaseApp): FirebaseUser {
        val res = FirebaseAuth.getInstance(app).signInAnonymously().await()
        return res.user ?: error("Anonymous Sign-In returned null FirebaseUser")
    }

    fun signOut(app: FirebaseApp) {
        clearWorkspaceListeners()
        FirebaseAuth.getInstance(app).signOut()
    }

    suspend fun checkRemoteHasData(app: FirebaseApp, uid: String): Boolean {
        val db = FirebaseFirestore.getInstance(app)
        val snap = db.collection("workspaces").document(uid).collection("people").limit(1).get().await()
        val giftsSnap = db.collection("workspaces").document(uid).collection("gifts").limit(1).get().await()
        return !snap.isEmpty || !giftsSnap.isEmpty
    }

    suspend fun uploadFullDataset(app: FirebaseApp, uid: String, dataset: GiftDataset) {
        val db = FirebaseFirestore.getInstance(app)
        val ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any?>>>()
        val root = db.collection("workspaces").document(uid)

        dataset.people.forEach { p ->
            ops += root.collection("people").document(p.id) to p.toFirestoreMap()
        }
        dataset.events.forEach { e ->
            ops += root.collection("events").document(e.id) to e.toFirestoreMap()
        }
        dataset.gifts.forEach { g ->
            ops += root.collection("gifts").document(g.id) to g.toFirestoreMap()
        }
        dataset.wishes.forEach { w ->
            ops += root.collection("wishes").document(w.id) to w.toFirestoreMap()
        }
        dataset.sharedLinks.forEach { s ->
            ops += root.collection("shared_links").document(s.id) to s.toFirestoreMap()
        }
        dataset.savedWishlists.forEach { sw ->
            ops += root.collection("saved_wishlists").document(sw.id) to sw.toFirestoreMap()
        }

        ops.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, map) -> batch.set(ref, map) }
            batch.commit().await()
        }
    }

    suspend fun upsertDoc(
        app: FirebaseApp,
        uid: String,
        collectionName: String,
        docId: String,
        data: Map<String, Any?>
    ) {
        val db = FirebaseFirestore.getInstance(app)
        db.collection("workspaces")
            .document(uid)
            .collection(collectionName)
            .document(docId)
            .set(data)
            .await()
    }

    suspend fun deleteDoc(
        app: FirebaseApp,
        uid: String,
        collectionName: String,
        docId: String
    ) {
        val db = FirebaseFirestore.getInstance(app)
        db.collection("workspaces")
            .document(uid)
            .collection(collectionName)
            .document(docId)
            .delete()
            .await()
    }

    suspend fun publishSharedWishlistDoc(
        app: FirebaseApp,
        ownerUid: String,
        link: SharedWishlistLink,
        allWishes: List<WishItem>
    ) {
        val db = FirebaseFirestore.getInstance(app)
        val payload = mapOf(
            "id" to link.id,
            "ownerUid" to ownerUid,
            "title" to link.title.trim().ifBlank { "Gift Wishlist" },
            "ownerName" to link.ownerName.trim(),
            "items" to link.computePublishedItemMaps(allWishes),
            "createdAt" to link.createdAt,
            "updatedAt" to Instant.now().toString()
        )
        db.collection("shared_wishlists")
            .document(link.id)
            .set(payload)
            .await()
    }

    suspend fun syncAllSharedWishlistsForOwner(
        app: FirebaseApp,
        ownerUid: String,
        links: List<SharedWishlistLink>,
        allWishes: List<WishItem>
    ) {
        links.forEach { link ->
            runCatching { publishSharedWishlistDoc(app, ownerUid, link, allWishes) }
        }
    }

    suspend fun deletePublicSharedWishlistDoc(
        app: FirebaseApp,
        shareId: String,
        knownWishIds: List<String> = emptyList()
    ) {
        val db = FirebaseFirestore.getInstance(app)
        val shareRef = db.collection("shared_wishlists").document(shareId)
        knownWishIds.forEach { wid ->
            runCatching { shareRef.collection("claims").document(wid).delete().await() }
        }
        shareRef.delete().await()
    }

    suspend fun clearPublicWishClaimsForOwner(
        app: FirebaseApp,
        links: List<SharedWishlistLink>,
        wishId: String
    ) {
        val db = FirebaseFirestore.getInstance(app)
        links.forEach { link ->
            runCatching {
                db.collection("shared_wishlists")
                    .document(link.id)
                    .collection("claims")
                    .document(wishId)
                    .delete()
                    .await()
            }
        }
    }

    fun resolvePublicViewerApp(projectKey: String?, primaryApp: FirebaseApp?): FirebaseApp? {
        val cleanKey = projectKey?.trim()?.takeIf { it.isNotBlank() }
        if (cleanKey != null) {
            val keyCfg = decodePublicProjectKey(cleanKey)
                ?: decodePairingToken(cleanKey)?.config
            if (keyCfg != null) {
                if (primaryApp != null && primaryApp.options.projectId == keyCfg.projectId) {
                    return primaryApp
                }
                val appName = "public-viewer-${keyCfg.projectId}-${keyCfg.apiKey.takeLast(6)}"
                val existing = FirebaseApp.getApps(appContext).firstOrNull { it.name == appName }
                if (existing != null) return existing
                return runCatching {
                    val opts = FirebaseOptions.Builder()
                        .setApiKey(keyCfg.apiKey)
                        .setProjectId(keyCfg.projectId)
                        .setApplicationId(
                            keyCfg.applicationId.ifBlank { "1:000000000000:web:publicviewer" }
                        )
                        .build()
                    FirebaseApp.initializeApp(appContext, opts, appName)
                }.getOrNull() ?: primaryApp
            }
        }
        return primaryApp
    }

    suspend fun fetchPublicWishlistOnce(
        viewerApp: FirebaseApp,
        shareId: String
    ): PublicSharedWishlistDoc? = runCatching {
        val snap = FirebaseFirestore.getInstance(viewerApp)
            .collection("shared_wishlists")
            .document(shareId)
            .get()
            .await()
        val data = snap.data ?: return null
        PublicSharedWishlistDoc.fromFirestoreMap(data, snap.id)
    }.getOrNull()

    fun subscribeToPublicWishlist(
        viewerApp: FirebaseApp,
        shareId: String,
        onDoc: (PublicSharedWishlistDoc?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val db = FirebaseFirestore.getInstance(viewerApp)
        return db.collection("shared_wishlists")
            .document(shareId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    onDoc(null)
                    return@addSnapshotListener
                }
                val data = snap.data
                if (data == null) {
                    onDoc(null)
                } else {
                    onDoc(PublicSharedWishlistDoc.fromFirestoreMap(data, snap.id))
                }
            }
    }

    fun subscribeToPublicWishlistClaims(
        viewerApp: FirebaseApp,
        shareId: String,
        onClaims: (Map<String, SharedWishClaim>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        val db = FirebaseFirestore.getInstance(viewerApp)
        return db.collection("shared_wishlists")
            .document(shareId)
            .collection("claims")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val map = buildMap<String, SharedWishClaim> {
                        snap.documents.forEach { d ->
                            val raw = d.data
                            if (raw != null) {
                                val parsed = SharedWishClaim.fromFirestoreMap(raw, d.id)
                                put(d.id, parsed)
                            }
                        }
                    }
                    onClaims(map)
                }
            }
    }

    suspend fun claimPublicWishItem(
        viewerApp: FirebaseApp,
        shareId: String,
        claim: SharedWishClaim
    ): Pair<Boolean, String?> {
        val db = FirebaseFirestore.getInstance(viewerApp)
        val claimRef = db.collection("shared_wishlists")
            .document(shareId)
            .collection("claims")
            .document(claim.wishId)

        return db.runTransaction { tx ->
            val snap = tx.get(claimRef)
            if (snap.exists()) {
                val existing = snap.data.orEmpty()
                val existingStatus = existing["status"] as? String
                val existingToken = existing["claimerToken"] as? String
                if (existingStatus != "researching" &&
                    !existingToken.isNullOrBlank() &&
                    existingToken != claim.claimerToken
                ) {
                    val other = (existing["claimedBy"] as? String) ?: "Someone else"
                    return@runTransaction false to other
                }
            }
            val now = Instant.now().toString()
            val cleanName = claim.claimedBy.trim().take(80)
            val cleanStatus = claim.status.ifBlank { if (claim.purchased) "purchased" else "reserved" }
            val payload = mapOf(
                "wishId" to claim.wishId,
                "claimedBy" to cleanName,
                "claimerToken" to claim.claimerToken,
                "purchased" to claim.purchased,
                "status" to cleanStatus,
                "researchers" to (claim.researchers.ifEmpty { listOf(cleanName) }),
                "researcherTokens" to (claim.researcherTokens.ifEmpty { listOf(claim.claimerToken) }),
                "giverNote" to claim.giverNote.trim(),
                "claimedAt" to claim.claimedAt.ifBlank { now },
                "updatedAt" to now
            )
            tx.set(claimRef, payload)
            true to null
        }.await()
    }

    suspend fun setWishResearching(
        viewerApp: FirebaseApp,
        shareId: String,
        wishId: String,
        researcherName: String,
        researcherToken: String
    ): Pair<Boolean, String?> {
        val db = FirebaseFirestore.getInstance(viewerApp)
        val claimRef = db.collection("shared_wishlists")
            .document(shareId)
            .collection("claims")
            .document(wishId)

        return db.runTransaction { tx ->
            val snap = tx.get(claimRef)
            val now = Instant.now().toString()
            val cleanName = researcherName.trim().take(40)
            if (snap.exists()) {
                val existing = snap.data.orEmpty()
                val status = existing["status"] as? String
                val purchased = (existing["purchased"] as? Boolean) ?: false
                val existingToken = existing["claimerToken"] as? String
                if (status == "reserved" || status == "purchased" || purchased) {
                    if (existingToken != researcherToken) {
                        val other = (existing["claimedBy"] as? String) ?: "Someone else"
                        return@runTransaction false to other
                    }
                }
                val existingResearchers = (existing["researchers"] as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: listOfNotNull((existing["claimedBy"] as? String) ?: cleanName)
                val existingTokens = (existing["researcherTokens"] as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: listOfNotNull(existingToken ?: researcherToken)

                val nextResearchers = if (cleanName in existingResearchers) existingResearchers else existingResearchers + cleanName
                val nextTokens = if (researcherToken in existingTokens) existingTokens else existingTokens + researcherToken
                val joinedNames = nextResearchers.joinToString(", ").take(80).ifBlank { cleanName }

                val payload = mapOf(
                    "wishId" to wishId,
                    "claimedBy" to joinedNames,
                    "claimerToken" to researcherToken,
                    "purchased" to false,
                    "status" to "researching",
                    "researchers" to nextResearchers,
                    "researcherTokens" to nextTokens,
                    "giverNote" to ((existing["giverNote"] as? String) ?: "").trim(),
                    "claimedAt" to ((existing["claimedAt"] as? String)?.ifBlank { now } ?: now),
                    "updatedAt" to now
                )
                tx.set(claimRef, payload)
                return@runTransaction true to null
            }

            val payload = mapOf(
                "wishId" to wishId,
                "claimedBy" to cleanName.take(80),
                "claimerToken" to researcherToken,
                "purchased" to false,
                "status" to "researching",
                "researchers" to listOf(cleanName),
                "researcherTokens" to listOf(researcherToken),
                "giverNote" to "",
                "claimedAt" to now,
                "updatedAt" to now
            )
            tx.set(claimRef, payload)
            true to null
        }.await()
    }

    suspend fun updatePublicWishClaim(
        viewerApp: FirebaseApp,
        shareId: String,
        wishId: String,
        existing: SharedWishClaim,
        purchased: Boolean? = null,
        giverNote: String? = null,
        claimedBy: String? = null,
        status: String? = null
    ) {
        val db = FirebaseFirestore.getInstance(viewerApp)
        val claimRef = db.collection("shared_wishlists")
            .document(shareId)
            .collection("claims")
            .document(wishId)

        val nextPurchased = purchased ?: existing.purchased
        val nextStatus = status ?: if (nextPurchased) "purchased" else existing.status.ifBlank { "reserved" }
        val payload = mapOf(
            "wishId" to wishId,
            "claimedBy" to (claimedBy ?: existing.claimedBy).trim().take(80),
            "claimerToken" to existing.claimerToken,
            "purchased" to nextPurchased,
            "status" to nextStatus,
            "researchers" to existing.researchers.ifEmpty { listOf(existing.claimedBy) },
            "researcherTokens" to existing.researcherTokens.ifEmpty { listOf(existing.claimerToken) },
            "giverNote" to (giverNote?.trim() ?: existing.giverNote),
            "claimedAt" to existing.claimedAt,
            "updatedAt" to Instant.now().toString()
        )
        claimRef.set(payload).await()
    }

    suspend fun unclaimOrStopResearchingPublicWishItem(
        viewerApp: FirebaseApp,
        shareId: String,
        wishId: String,
        claimerToken: String
    ) {
        val db = FirebaseFirestore.getInstance(viewerApp)
        val claimRef = db.collection("shared_wishlists")
            .document(shareId)
            .collection("claims")
            .document(wishId)

        db.runTransaction { tx ->
            val snap = tx.get(claimRef)
            if (!snap.exists()) return@runTransaction
            val existing = snap.data.orEmpty()
            val status = existing["status"] as? String
            if (status == "researching") {
                val researchers = (existing["researchers"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val tokens = (existing["researcherTokens"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val remainingPairs = researchers.zip(tokens).filter { (_, tok) -> tok != claimerToken }
                if (remainingPairs.isEmpty()) {
                    tx.delete(claimRef)
                } else {
                    val remNames = remainingPairs.map { it.first }
                    val remTokens = remainingPairs.map { it.second }
                    val updated = existing.toMutableMap().apply {
                        put("claimedBy", remNames.joinToString(", ").take(80))
                        put("claimerToken", remTokens.firstOrNull() ?: "unknown")
                        put("researchers", remNames)
                        put("researcherTokens", remTokens)
                        put("updatedAt", Instant.now().toString())
                    }
                    tx.set(claimRef, updated)
                }
                return@runTransaction
            }
            tx.delete(claimRef)
        }.await()
    }

    suspend fun syncGiftBoughtStatusToWishlist(
        viewerApp: FirebaseApp,
        shareId: String,
        wishId: String,
        bought: Boolean,
        giverName: String,
        claimerToken: String
    ): Pair<Boolean, String?> = runCatching {
        val db = FirebaseFirestore.getInstance(viewerApp)
        val claimRef = db.collection("shared_wishlists")
            .document(shareId)
            .collection("claims")
            .document(wishId)
        val cleanName = giverName.ifBlank { "Gift Giver" }.trim().take(80)

        db.runTransaction { tx ->
            val snap = tx.get(claimRef)
            val existing = snap.data.orEmpty()
            val existingStatus = existing["status"] as? String
            val existingToken = existing["claimerToken"] as? String
            val now = Instant.now().toString()

            if (bought) {
                if (snap.exists() &&
                    existingStatus != "researching" &&
                    !existingToken.isNullOrBlank() &&
                    existingToken != claimerToken
                ) {
                    val other = (existing["claimedBy"] as? String) ?: "someone else"
                    return@runTransaction false to "Already claimed by $other on the wishlist"
                }
                val researchers = (existing["researchers"] as? List<*>)?.mapNotNull { it as? String } ?: listOf(cleanName)
                val tokens = (existing["researcherTokens"] as? List<*>)?.mapNotNull { it as? String } ?: listOf(claimerToken)
                tx.set(
                    claimRef,
                    mapOf(
                        "wishId" to wishId,
                        "claimedBy" to cleanName,
                        "claimerToken" to claimerToken,
                        "purchased" to true,
                        "status" to "purchased",
                        "researchers" to researchers,
                        "researcherTokens" to tokens,
                        "giverNote" to ((existing["giverNote"] as? String) ?: "").trim(),
                        "claimedAt" to ((existing["claimedAt"] as? String)?.ifBlank { now } ?: now),
                        "updatedAt" to now
                    )
                )
                true to "Marked as Purchased on the wishlist!"
            } else {
                if (snap.exists() &&
                    existingStatus != "researching" &&
                    !existingToken.isNullOrBlank() &&
                    existingToken != claimerToken
                ) {
                    return@runTransaction false to null
                }
                tx.set(
                    claimRef,
                    mapOf(
                        "wishId" to wishId,
                        "claimedBy" to cleanName,
                        "claimerToken" to claimerToken,
                        "purchased" to false,
                        "status" to "researching",
                        "researchers" to listOf(cleanName),
                        "researcherTokens" to listOf(claimerToken),
                        "giverNote" to ((existing["giverNote"] as? String) ?: "").trim(),
                        "claimedAt" to ((existing["claimedAt"] as? String)?.ifBlank { now } ?: now),
                        "updatedAt" to now
                    )
                )
                true to "Reverted to Researching on the wishlist"
            }
        }.await()
    }.getOrElse { false to it.localizedMessage }

    fun subscribeToWorkspace(
        app: FirebaseApp,
        uid: String,
        onPeople: (List<Person>) -> Unit,
        onEvents: (List<GiftEvent>) -> Unit,
        onGifts: (List<GiftItem>) -> Unit,
        onWishes: (List<WishItem>) -> Unit,
        onSharedLinks: (List<SharedWishlistLink>) -> Unit,
        onSavedWishlists: (List<SavedWishlist>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        clearWorkspaceListeners()
        val db = FirebaseFirestore.getInstance(app)
        val root = db.collection("workspaces").document(uid)

        snapshotRegistrations += root.collection("people").addSnapshotListener { snap, err ->
            if (err != null) {
                onError(err)
                return@addSnapshotListener
            }
            if (snap != null) {
                val items = snap.documents
                    .mapNotNull { d -> d.data?.let { Person.fromFirestoreMap(it, d.id) } }
                    .sortedBy { it.order }
                onPeople(items)
            }
        }

        snapshotRegistrations += root.collection("events").addSnapshotListener { snap, err ->
            if (err != null) {
                onError(err)
                return@addSnapshotListener
            }
            if (snap != null) {
                val items = snap.documents
                    .mapNotNull { d -> d.data?.let { GiftEvent.fromFirestoreMap(it, d.id) } }
                    .sortedBy { it.order }
                onEvents(items)
            }
        }

        snapshotRegistrations += root.collection("gifts").addSnapshotListener { snap, err ->
            if (err != null) {
                onError(err)
                return@addSnapshotListener
            }
            if (snap != null) {
                val items = snap.documents
                    .mapNotNull { d -> d.data?.let { GiftItem.fromFirestoreMap(it, d.id) } }
                    .sortedByDescending { it.createdAt }
                onGifts(items)
            }
        }

        snapshotRegistrations += root.collection("wishes").addSnapshotListener { snap, err ->
            if (err != null) {
                onError(err)
                return@addSnapshotListener
            }
            if (snap != null) {
                val items = snap.documents
                    .mapNotNull { d -> d.data?.let { WishItem.fromFirestoreMap(it, d.id) } }
                    .sortedBy { it.order }
                onWishes(items)
            }
        }

        snapshotRegistrations += root.collection("shared_links").addSnapshotListener { snap, err ->
            if (err != null) {
                onError(err)
                return@addSnapshotListener
            }
            if (snap != null) {
                val items = snap.documents
                    .mapNotNull { d -> d.data?.let { SharedWishlistLink.fromFirestoreMap(it, d.id) } }
                    .sortedByDescending { it.createdAt }
                onSharedLinks(items)
            }
        }

        snapshotRegistrations += root.collection("saved_wishlists").addSnapshotListener { snap, err ->
            if (err != null) {
                onError(err)
                return@addSnapshotListener
            }
            if (snap != null) {
                val items = snap.documents
                    .mapNotNull { d -> d.data?.let { SavedWishlist.fromFirestoreMap(it, d.id) } }
                    .sortedByDescending { it.updatedAt.ifBlank { it.createdAt } }
                onSavedWishlists(items)
            }
        }
    }

    private fun getResourceString(name: String): String? {
        val id = appContext.resources.getIdentifier(name, "string", appContext.packageName)
        return if (id != 0) appContext.getString(id).takeIf { it.isNotBlank() } else null
    }

    companion object {
        data class ParsedWishlistUrl(
            val normalizedUrl: String,
            val type: String, // "live" | "external"
            val shareId: String? = null,
            val projectKey: String? = null
        )

        fun parseWishlistUrl(rawInput: String): ParsedWishlistUrl {
            var trimmed = rawInput.trim()
            if (trimmed.isEmpty()) return ParsedWishlistUrl("", "external")
            if (!Regex("^[a-zA-Z][a-zA-Z0-9+\\-.]*://").containsMatchIn(trimmed)) {
                trimmed = "https://$trimmed"
            }
            return runCatching {
                val uri = android.net.Uri.parse(trimmed)
                val path = uri.path.orEmpty()
                val match = Regex("/w/([A-Za-z0-9_-]+)").find(path)
                if (match != null) {
                    val shareId = android.net.Uri.decode(match.groupValues[1])
                    val key = (uri.getQueryParameter("k") ?: uri.getQueryParameter("pair"))
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                    ParsedWishlistUrl(
                        normalizedUrl = uri.toString(),
                        type = "live",
                        shareId = shareId,
                        projectKey = key
                    )
                } else {
                    ParsedWishlistUrl(normalizedUrl = uri.toString(), type = "external")
                }
            }.getOrDefault(ParsedWishlistUrl(normalizedUrl = trimmed, type = "external"))
        }

        fun decodePublicProjectKey(rawKey: String): FirebaseRuntimeConfig? = runCatching {
            val normalized = rawKey.trim().replace('-', '+').replace('_', '/')
            if (normalized.isEmpty()) return null
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            val decodedBytes = android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
            val obj = Json.parseToJsonElement(String(decodedBytes, Charsets.UTF_8)).jsonObject
            val projectId = obj["p"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val apiKey = obj["a"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() } ?: return null
            FirebaseRuntimeConfig(
                apiKey = apiKey,
                projectId = projectId,
                applicationId = "1:000000000000:web:publicviewer"
            )
        }.getOrNull()
        /**
         * Returns the colon-separated uppercase SHA-1 fingerprint of the installed APK's signing certificate.
         */
        fun getAppSigningSha1(context: Context): String? = runCatching {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signer = pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()
                ?: pkgInfo.signingInfo?.signingCertificateHistory?.firstOrNull()
                ?: return null
            val digest = java.security.MessageDigest.getInstance("SHA-1").digest(signer.toByteArray())
            digest.joinToString(":") { "%02X".format(it) }
        }.getOrNull()

        data class DecodedPairingResult(
            val config: FirebaseRuntimeConfig,
            val webUrl: String? = null
        )

        fun buildSharedWishlistUrl(
            shareId: String,
            config: FirebaseRuntimeConfig?,
            webUrl: String
        ): String {
            val base = webUrl.trim().trimEnd('/')
            if (config == null) {
                return "$base/w/${android.net.Uri.encode(shareId)}"
            }
            val keyPayload = buildJsonObject {
                put("p", config.projectId.trim())
                put("a", config.apiKey.trim())
            }.toString()
            val encodedKey = android.util.Base64.encodeToString(
                keyPayload.toByteArray(Charsets.UTF_8),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
            return "$base/w/${android.net.Uri.encode(shareId)}?k=${android.net.Uri.encode(encodedKey)}"
        }

        fun encodePairingToken(config: FirebaseRuntimeConfig, webUrl: String? = null): String {
            val payload = buildJsonObject {
                put("v", 1)
                put("apiKey", config.apiKey.trim())
                put("projectId", config.projectId.trim())
                put("authDomain", "${config.projectId.trim()}.firebaseapp.com")
                config.applicationId.takeIf { it.isNotBlank() }?.let { put("appId", it.trim()) }
                config.storageBucket?.takeIf { it.isNotBlank() }?.let { put("storageBucket", it.trim()) }
                config.gcmSenderId?.takeIf { it.isNotBlank() }?.let { put("messagingSenderId", it.trim()) }
                config.webClientId?.takeIf { it.isNotBlank() }?.let { put("webClientId", it.trim()) }
                webUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }?.let { put("webUrl", it) }
            }
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            return android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
        }

        fun buildPairingLink(config: FirebaseRuntimeConfig, webUrl: String? = null): String {
            val cleanWeb = webUrl?.trim()?.trimEnd('/')?.takeIf {
                it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
            }
            val token = encodePairingToken(config, cleanWeb)
            val encoded = android.net.Uri.encode(token)
            return if (cleanWeb != null) {
                "$cleanWeb/?pair=$encoded"
            } else {
                "gifttracker://pair?cfg=$encoded"
            }
        }

        fun decodePairingToken(rawInput: String): DecodedPairingResult? = runCatching {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) return null

            var candidate = trimmed
            var inferredWebUrl: String? = null

            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("gifttracker://", ignoreCase = true)
            ) {
                val uri = android.net.Uri.parse(trimmed)
                val param = uri.getQueryParameter("pair") ?: uri.getQueryParameter("cfg") ?: return null
                candidate = param
                if (uri.scheme == "http" || uri.scheme == "https") {
                    val portPart = if (uri.port != -1) ":${uri.port}" else ""
                    if (!uri.host.isNullOrBlank()) {
                        inferredWebUrl = "${uri.scheme}://${uri.host}$portPart"
                    }
                }
            }

            val normalized = candidate.replace('-', '+').replace('_', '/')
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            val decodedBytes = android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
            val obj = Json.parseToJsonElement(String(decodedBytes, Charsets.UTF_8)).jsonObject

            fun str(key: String): String? =
                obj[key]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }

            val apiKey = str("apiKey") ?: return null
            val projectId = str("projectId") ?: return null
            val appId = str("appId") ?: str("applicationId") ?: "1:000000000000:web:gifttracker"

            DecodedPairingResult(
                config = FirebaseRuntimeConfig(
                    apiKey = apiKey,
                    projectId = projectId,
                    applicationId = appId,
                    storageBucket = str("storageBucket") ?: "$projectId.firebasestorage.app",
                    gcmSenderId = str("messagingSenderId") ?: str("gcmSenderId"),
                    webClientId = str("webClientId")
                ),
                webUrl = str("webUrl") ?: inferredWebUrl
            )
        }.getOrNull()

        /**
         * Parses ANY of:
         * 1. Pairing link (`https://.../?pair=...` or `gifttracker://pair?cfg=...`) or raw token,
         * 2. Android `google-services.json` content pasted directly, OR
         * 3. Web JS/JSON firebaseConfig snippet (`apiKey`, `projectId`, `appId`, optional `webClientId`)!
         */
        fun parseFirebaseSnippet(
            input: String,
            webClientIdOverride: String? = null,
            targetPackageName: String? = null
        ): FirebaseRuntimeConfig? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null

            // 1. Check if pairing link or token
            decodePairingToken(trimmed)?.let { res ->
                val overrideClient = webClientIdOverride?.takeIf { it.isNotBlank() }
                return if (overrideClient != null) res.config.copy(webClientId = overrideClient) else res.config
            }

            // 2. Check if google-services.json format
            runCatching {
                val root = Json.parseToJsonElement(trimmed).jsonObject
                if ("project_info" in root && "client" in root) {
                    val pInfo = root["project_info"]!!.jsonObject
                    val projectId = pInfo["project_id"]?.jsonPrimitive?.content ?: return@runCatching
                    val bucket = pInfo["storage_bucket"]?.jsonPrimitive?.content
                    val sender = pInfo["project_number"]?.jsonPrimitive?.content
                    val clients = root["client"]!!.jsonArray.mapNotNull { it.jsonObject }
                    val client = if (!targetPackageName.isNullOrBlank()) {
                        clients.firstOrNull {
                            it["client_info"]?.jsonObject?.get("android_client_info")?.jsonObject
                                ?.get("package_name")?.jsonPrimitive?.content == targetPackageName
                        } ?: clients.firstOrNull()
                    } else {
                        clients.firstOrNull()
                    } ?: return@runCatching

                    val appId = client["client_info"]?.jsonObject?.get("mobilesdk_app_id")?.jsonPrimitive?.content
                        ?: return@runCatching
                    val apiKey = client["api_key"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("current_key")?.jsonPrimitive?.content ?: return@runCatching

                    val oauthWebClient = client["oauth_client"]?.jsonArray
                        ?.mapNotNull { it.jsonObject }
                        ?.firstOrNull { it["client_type"]?.jsonPrimitive?.content == "3" }
                        ?.get("client_id")?.jsonPrimitive?.content
                        ?: clients.flatMap { it["oauth_client"]?.jsonArray?.mapNotNull { oc -> oc.jsonObject } ?: emptyList() }
                            .firstOrNull { it["client_type"]?.jsonPrimitive?.content == "3" }
                            ?.get("client_id")?.jsonPrimitive?.content

                    return FirebaseRuntimeConfig(
                        apiKey = apiKey,
                        projectId = projectId,
                        applicationId = appId,
                        storageBucket = bucket,
                        gcmSenderId = sender,
                        webClientId = webClientIdOverride?.takeIf { it.isNotBlank() } ?: oauthWebClient
                    )
                }
            }

            // 3. Otherwise parse Web firebaseConfig JSON or JS object snippet
            fun extract(key: String): String? {
                val re = Regex("""["']?$key["']?\s*:\s*["'`]([^"'`]+)["'`]""")
                return re.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
            }

            val apiKey = extract("apiKey") ?: return null
            val projectId = extract("projectId") ?: return null
            val appId = extract("appId")
                ?: extract("applicationId")
                ?: "1:000000000000:web:gifttracker"

            return FirebaseRuntimeConfig(
                apiKey = apiKey,
                projectId = projectId,
                applicationId = appId,
                storageBucket = extract("storageBucket") ?: "$projectId.firebasestorage.app",
                gcmSenderId = extract("messagingSenderId"),
                webClientId = webClientIdOverride?.takeIf { it.isNotBlank() } ?: extract("webClientId")
            )
        }
    }
}
