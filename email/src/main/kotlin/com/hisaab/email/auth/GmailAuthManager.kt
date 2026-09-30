package com.hisaab.email.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.hisaab.email.BuildConfig
import com.hisaab.email.api.AccessTokenProvider
import com.hisaab.email.sync.GmailSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ConnectResult {
    data class Connected(val email: String?) : ConnectResult
    /** Launch this with an IntentSenderRequest, then pass the result Intent to [GmailAuthManager.completeConsent]. */
    data class NeedsConsent(val intent: PendingIntent, val email: String?) : ConnectResult
    data class Failed(val message: String) : ConnectResult
}

/**
 * Sign-in with Credential Manager (who the user is), then AuthorizationClient for the
 * gmail.readonly scope (what the app may read). Access tokens are short-lived; after the first
 * consent, AuthorizationClient hands out new ones silently, so no refresh token is ever stored.
 */
@Singleton
class GmailAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokens: TokenStore,
    private val settings: GmailSettingsStore,
    private val http: HttpClient,
) : AccessTokenProvider {
    private val refreshLock = Mutex()
    private val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(GMAIL_READONLY))).build()

    suspend fun connect(activity: Activity): ConnectResult {
        val email = signIn(activity)
        return try {
            val result = Identity.getAuthorizationClient(activity).authorize(request).await()
            if (result.hasResolution()) ConnectResult.NeedsConsent(result.pendingIntent!!, email)
            else finish(result, email)
        } catch (e: Exception) {
            ConnectResult.Failed(e.message ?: "Authorization failed")
        }
    }

    suspend fun completeConsent(activity: Activity, data: Intent?, email: String?): ConnectResult = try {
        finish(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data), email)
    } catch (e: Exception) {
        ConnectResult.Failed(e.message ?: "Consent was not granted")
    }

    private suspend fun finish(result: AuthorizationResult, email: String?): ConnectResult {
        val token = result.accessToken ?: return ConnectResult.Failed("No access token returned")
        if (GMAIL_READONLY !in result.grantedScopes) return ConnectResult.Failed("Gmail read access was not granted")
        tokens.save(token, System.currentTimeMillis() + TOKEN_LIFETIME_MS)
        settings.connected(email)
        return ConnectResult.Connected(email)
    }

    /** Optional step: needs the Web client id in local.properties. Without it the account picker in authorize() is used. */
    private suspend fun signIn(activity: Activity): String? {
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() } ?: return null
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build()
        return try {
            val response = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).id
            } else {
                null
            }
        } catch (_: GetCredentialException) {
            null
        }
    }

    override suspend fun token(forceRefresh: Boolean): String? = refreshLock.withLock {
        val stored = tokens.read()
        if (!forceRefresh && stored != null && stored.expiresAt > System.currentTimeMillis() + EXPIRY_MARGIN_MS) return stored.value
        // Silent re-authorization; only works after the user has consented once.
        val result = try {
            Identity.getAuthorizationClient(context).authorize(request).await()
        } catch (_: Exception) {
            return null
        }
        if (result.hasResolution() || result.accessToken == null) {
            settings.setNeedsReauth(true)
            return null
        }
        tokens.save(result.accessToken!!, System.currentTimeMillis() + TOKEN_LIFETIME_MS)
        result.accessToken
    }

    /** Revokes Google's grant, deletes the token and its Keystore key, and forgets the account. */
    suspend fun signOut() {
        tokens.read()?.let { t -> runCatching { http.post("https://oauth2.googleapis.com/revoke") { parameter("token", t.value) } } }
        tokens.wipe()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        settings.clearAccount()
    }

    companion object {
        const val GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly"
        private const val TOKEN_LIFETIME_MS = 55 * 60 * 1000L
        private const val EXPIRY_MARGIN_MS = 2 * 60 * 1000L
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
