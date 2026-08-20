package com.smartreminder.domain.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

data class GoogleTokenResult(
    val idToken: String,
    val rawNonce: String
)

object GoogleAuthHelper {

    /**
     * Gọi Google Credential Manager để hiển thị giao diện One-Tap / BottomSheet
     * và nhận Google ID Token cùng rawNonce để Supabase xác thực.
     */
    suspend fun getGoogleIdToken(
        context: Context,
        serverClientId: String
    ): Result<GoogleTokenResult> {
        return try {
            val credentialManager = CredentialManager.create(context)

            // Tạo raw nonce để tăng cường bảo mật
            val rawNonce = UUID.randomUUID().toString()
            val bytes = rawNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                Result.success(GoogleTokenResult(idToken = idToken, rawNonce = rawNonce))
            } else {
                Result.failure(Exception("Loại credential không được hỗ trợ: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Người dùng đã huỷ đăng nhập Google"))
        } catch (e: GetCredentialException) {
            Result.failure(Exception("Lỗi xác thực Google: ${e.localizedMessage ?: e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
