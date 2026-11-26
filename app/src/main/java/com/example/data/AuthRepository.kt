package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.crypto.E2eeCryptoManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository(
    private val db: FirebaseFirestore,
    private val cryptoManager: E2eeCryptoManager
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        E2eeCryptoManager(context.applicationContext)
    )

    private val auth = Firebase.auth

    suspend fun syncSignedInProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val displayName = user.displayName?.take(60)?.ifBlank { "Pengguna BORDER CHAT" }
            ?: "Pengguna BORDER CHAT"
        val photoUrl = user.photoUrl?.toString()?.take(500)
        val email = user.email?.take(120)
        val publicKey = cryptoManager.getPublicKeyBase64(uid)

        // 1. Sync owner-only private PII record (/users/{uid})
        val privateRef = db.collection("users").document(uid)
        try {
            val snap = privateRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "photoUrl" to photoUrl,
                    "readReceiptsEnabled" to true,
                    "groupReadReceiptsEnabled" to true,
                    "chatReadReceiptOverrides" to emptyMap<String, String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                privateRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "displayName" to displayName,
                    "email" to email,
                    "photoUrl" to photoUrl,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                privateRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, privateRef.path)
            return Result.failure(e)
        }

        // 2. Sync discoverable non-PII public profile & ECDH public key (/publicProfiles/{uid})
        val publicRef = db.collection("publicProfiles").document(uid)
        return try {
            val snap = publicRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "photoUrl" to photoUrl,
                    "statusText" to "Terenkripsi End-to-End • Siap Chat",
                    "publicKey" to publicKey,
                    "online" to true,
                    "discoverable" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                publicRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "displayName" to displayName,
                    "photoUrl" to photoUrl,
                    "publicKey" to publicKey,
                    "online" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                publicRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, publicRef.path)
            Result.failure(e)
        }
    }

    suspend fun setOnlineStatus(uid: String, online: Boolean) {
        val publicRef = db.collection("publicProfiles").document(uid)
        try {
            publicRef.update(
                mapOf(
                    "online" to online,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, publicRef.path)
        }
    }

    companion object {
        fun attemptAutoSignIn(
            context: Context,
            credentialManager: CredentialManager,
            onAuthSuccess: () -> Unit,
            onUnauthenticated: () -> Unit,
            scope: CoroutineScope
        ) {
            if (Firebase.auth.currentUser != null) {
                onAuthSuccess()
                return
            }
            val clientId = try {
                context.getString(R.string.default_web_client_id)
            } catch (_: Exception) {
                onUnauthenticated()
                return
            }
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            scope.launch {
                try {
                    val cred = credentialManager.getCredential(context, request).credential
                    if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                        onAuthSuccess()
                    } else {
                        onUnauthenticated()
                    }
                } catch (_: Exception) {
                    onUnauthenticated()
                }
            }
        }

        fun onGoogleSignInClicked(
            context: Context,
            credentialManager: CredentialManager,
            onAuthSuccess: () -> Unit,
            onAuthError: (String) -> Unit,
            scope: CoroutineScope,
            onAuthCancelled: () -> Unit = {}
        ) {
            val clientId = try {
                context.getString(R.string.default_web_client_id)
            } catch (_: Exception) {
                onAuthError("Konfigurasi Google Sign-In belum tersedia.")
                return
            }
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            scope.launch {
                try {
                    val activity = context as? Activity
                        ?: throw IllegalStateException("Activity context required for sign-in")
                    val cred = credentialManager.getCredential(activity, request).credential
                    if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                        onAuthSuccess()
                    } else {
                        onAuthError("Tipe kredensial tidak dikenali.")
                    }
                } catch (e: GetCredentialCancellationException) {
                    Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                    onAuthCancelled()
                } catch (e: Exception) {
                    Log.e("Auth", "Google Sign-In failed", e)
                    onAuthError(e.localizedMessage ?: "Gagal masuk dengan Google")
                }
            }
        }

        fun signOut(
            credentialManager: CredentialManager,
            authRepository: AuthRepository?,
            scope: CoroutineScope,
            onSignOutComplete: () -> Unit = {}
        ) {
            val uid = Firebase.auth.currentUser?.uid
            scope.launch {
                withContext(NonCancellable) {
                    try {
                        if (uid != null) {
                            authRepository?.setOnlineStatus(uid, false)
                        }
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    } catch (e: Exception) {
                        Log.w("Auth", "Failed to clear credential state: ${e.message}")
                    } finally {
                        Firebase.auth.signOut()
                        onSignOutComplete()
                    }
                }
            }
        }
    }
}
