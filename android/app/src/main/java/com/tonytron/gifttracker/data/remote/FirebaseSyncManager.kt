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
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            val sha1 = getAppSigningSha1(activityContext)
            val shaHint = if (sha1 != null) " (Installed APK SHA-1: $sha1)" else ""
            throw IllegalStateException(
                "Sign-in canceled or rejected by Google Play Services. If you selected an account, verify your Firebase SHA-1 & OAuth consent screen$shaHint.",
                e
            )
        } catch (e: androidx.credentials.exceptions.GetCredentialException) {
            throw IllegalStateException("${e.type}: ${e.errorMessage ?: e.localizedMessage}", e)
        }

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
                    .sortedBy { it.order }
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
