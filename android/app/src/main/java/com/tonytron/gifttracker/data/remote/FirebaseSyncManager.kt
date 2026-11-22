package com.tonytron.gifttracker.data.remote

import android.content.Context
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tonytron.gifttracker.data.model.FirebaseRuntimeConfig
import com.tonytron.gifttracker.data.model.GiftDataset
import com.tonytron.gifttracker.data.model.GiftEvent
import com.tonytron.gifttracker.data.model.GiftItem
import com.tonytron.gifttracker.data.model.Person
import com.tonytron.gifttracker.data.model.WishItem
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FirebaseSyncManager(private val appContext: Context) {

    private var activeApp: FirebaseApp? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var observedAuth: FirebaseAuth? = null
    private val snapshotRegistrations = mutableListOf<ListenerRegistration>()

    data class ConnectedInfo(
        val projectId: String,
        val isFromGoogleServicesResource: Boolean,
        val webClientId: String?
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

            activeApp = app
            return app to ConnectedInfo(
                projectId = runtimeConfig.projectId,
                isFromGoogleServicesResource = false,
                webClientId = runtimeConfig.webClientId ?: getResourceString("default_web_client_id")
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
        activeApp = defaultApp
        return defaultApp to ConnectedInfo(
            projectId = pid,
            isFromGoogleServicesResource = true,
            webClientId = getResourceString("default_web_client_id")
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
        val credentialManager = CredentialManager.create(activityContext)
        val trimmedClientId = webClientId.trim()

        // 1. First attempt: GetSignInWithGoogleOption (standard for explicit "Sign in with Google" button clicks)
        val result = runCatching {
            val signInOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(trimmedClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            credentialManager.getCredential(
                request = request,
                context = activityContext
            )
        }.recoverCatching { err ->
            // 2. Fallback attempt: GetGoogleIdOption (alternative flow with filterByAuthorizedAccounts = false)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(trimmedClientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            credentialManager.getCredential(
                request = request,
                context = activityContext
            )
        }.getOrThrow()

        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCred = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = FirebaseAuth.getInstance(app).signInWithCredential(firebaseCred).await()
            return authResult.user ?: error("Google Sign-In returned null FirebaseUser")
        } else {
            error("Unexpected credential type returned by Credential Manager: ${credential::class.java.simpleName}")
        }
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

    fun subscribeToWorkspace(
        app: FirebaseApp,
        uid: String,
        onPeople: (List<Person>) -> Unit,
        onEvents: (List<GiftEvent>) -> Unit,
        onGifts: (List<GiftItem>) -> Unit,
        onWishes: (List<WishItem>) -> Unit,
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
                onWishes(items)
            }
        }
    }

    private fun getResourceString(name: String): String? {
        val id = appContext.resources.getIdentifier(name, "string", appContext.packageName)
        return if (id != 0) appContext.getString(id).takeIf { it.isNotBlank() } else null
    }

    companion object {
        /**
         * Parses EITHER:
         * 1. Web JS/JSON firebaseConfig snippet (`apiKey`, `projectId`, `appId`, optional `webClientId`), OR
         * 2. Android `google-services.json` content pasted directly!
         */
        fun parseFirebaseSnippet(
            input: String,
            webClientIdOverride: String? = null,
            targetPackageName: String? = null
        ): FirebaseRuntimeConfig? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null

            // Check if google-services.json format
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

            // Otherwise parse Web firebaseConfig JSON or JS object snippet
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
