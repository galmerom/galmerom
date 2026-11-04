package com.galmerom.outfittracker.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.galmerom.outfittracker.domain.repository.AuthRepository
import com.galmerom.outfittracker.domain.repository.SignedInUser
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import java.security.SecureRandom
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val appContext: Context,
    private val webClientId: String?,
) : AuthRepository {
    override fun observeUser(): Flow<SignedInUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toSignedInUser())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(activityContext: Context): Result<Unit> {
        val clientId = webClientId ?: return Result.failure(IllegalStateException("missing web client"))
        return try {
            val option = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                .setNonce(newNonce())
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Result.failure(IllegalStateException("unexpected credential"))
            } else {
                val googleId = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleId.idToken, null)
                auth.signInWithCredential(firebaseCredential).await()
                Result.success(Unit)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: GetCredentialCancellationException) {
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        runCatching {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}

private fun FirebaseUser.toSignedInUser() = SignedInUser(
    uid = uid,
    displayName = displayName,
    email = email,
)

private fun newNonce(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
