package com.example.wax.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.example.wax.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val AUTH_URL = "https://accounts.spotify.com/authorize"
        private const val SCOPES = "user-read-private user-read-email " +
                "user-read-playback-state user-modify-playback-state user-read-currently-playing"
        private const val PREFS_NAME = "spotify_auth"
        private const val KEY_CODE_VERIFIER = "code_verifier"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun getAuthIntent(): Intent {
        val codeVerifier = generateCodeVerifier()
        prefs.edit().putString(KEY_CODE_VERIFIER, codeVerifier).apply()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .build()

        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun extractCodeFromUri(uri: Uri?): String? = uri?.getQueryParameter("code")

    fun getCodeVerifier(): String = prefs.getString(KEY_CODE_VERIFIER, "") ?: ""
}
