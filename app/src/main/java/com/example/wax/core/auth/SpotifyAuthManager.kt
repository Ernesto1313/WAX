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

/**
 * Manages the Spotify OAuth 2.0 authorization flow using PKCE
 * (Proof Key for Code Exchange, RFC 7636).
 *
 * PKCE allows a public client — one that cannot safely store a client secret,
 * such as an Android app — to obtain tokens without ever sending a client secret
 * over the network. Instead:
 *  1. The app generates a cryptographically random [code verifier].
 *  2. It derives a [code challenge] by hashing the verifier with SHA-256.
 *  3. The challenge is sent to Spotify's authorization endpoint; the verifier is stored locally.
 *  4. After the user grants access, Spotify returns an authorization code.
 *  5. The app sends the authorization code **and** the original verifier to the token endpoint.
 *     Spotify re-hashes the verifier and checks it against the challenge — proving the
 *     requester is the same party that started the flow — then issues the access token.
 *
 * Because the client secret is never involved, intercepting the authorization code
 * alone is not enough to obtain a token, making the flow safe for mobile apps.
 *
 * This class is provided as a [Singleton] by Hilt so that the same PKCE state
 * (code verifier) is accessible from any part of the app that needs it.
 *
 * @param context Application context used to access [android.content.SharedPreferences]
 *   for persisting the PKCE code verifier across Activity boundaries.
 */
@Singleton
class SpotifyAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /**
         * Spotify's OAuth 2.0 authorization endpoint where the user is redirected
         * to log in and grant the requested permissions.
         */
        private const val AUTH_URL = "https://accounts.spotify.com/authorize"

        /**
         * Space-separated list of Spotify permission scopes requested from the user:
         * - `user-read-private`            – read the user's subscription level and country.
         * - `user-read-email`              – read the user's email address.
         * - `user-read-playback-state`     – read the currently active device and playback state.
         * - `user-modify-playback-state`   – control playback (play, pause, skip, seek, volume).
         * - `user-read-currently-playing`  – read the track currently playing on any device.
         */
        private const val SCOPES = "user-read-private user-read-email " +
                "user-read-playback-state user-modify-playback-state user-read-currently-playing"

        /**
         * Name of the [android.content.SharedPreferences] file used to persist
         * PKCE state between the moment the auth browser opens and the moment
         * the redirect URI is received by the app.
         */
        private const val PREFS_NAME = "spotify_auth"

        /**
         * SharedPreferences key under which the PKCE code verifier is stored.
         * The verifier must survive any Activity recreation that may occur while
         * the user is in the Spotify authorization browser.
         */
        private const val KEY_CODE_VERIFIER = "code_verifier"
    }

    /**
     * SharedPreferences instance scoped to [PREFS_NAME], used exclusively to
     * persist the PKCE code verifier across the authorization redirect.
     */
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Generates a cryptographically secure PKCE code verifier.
     *
     * A code verifier is a high-entropy random string of 43–128 characters
     * (per RFC 7636 §4.1). Here 32 random bytes are produced by [SecureRandom]
     * and then Base64url-encoded (URL-safe alphabet, no padding, no line wraps)
     * to yield a 43-character ASCII string suitable for use as a query parameter.
     *
     * [SecureRandom] is used — not [java.util.Random] — because a predictable
     * verifier would allow an attacker to forge the PKCE challenge.
     *
     * @return A Base64url-encoded, 43-character random string to be used as the
     *   PKCE code verifier.
     */
    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        // Fill the byte array with cryptographically strong random values
        SecureRandom().nextBytes(bytes)
        // Base64url-encode without padding or line breaks so the result is
        // safe to embed directly in a URL query parameter
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Derives the PKCE code challenge from the given code verifier.
     *
     * Per RFC 7636 §4.2, the S256 method computes:
     *   `BASE64URL(SHA256(ASCII(code_verifier)))`
     *
     * The challenge is safe to send in the clear to the authorization server because
     * SHA-256 is a one-way function — an eavesdropper cannot reverse it to obtain
     * the verifier.
     *
     * @param verifier The PKCE code verifier produced by [generateCodeVerifier].
     * @return A Base64url-encoded SHA-256 digest of [verifier], used as the
     *   `code_challenge` query parameter in the authorization request.
     */
    private fun generateCodeChallenge(verifier: String): String {
        // Encode the verifier as US-ASCII bytes as required by the RFC
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        // Compute the SHA-256 digest of the verifier bytes
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        // Base64url-encode the digest without padding or line breaks
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Builds and returns an [Intent] that opens Spotify's authorization page in
     * the device browser, initiating the PKCE OAuth 2.0 flow.
     *
     * The method:
     *  1. Generates a fresh [code verifier][generateCodeVerifier] for this session.
     *  2. Persists the verifier to SharedPreferences so it survives Activity teardown.
     *  3. Derives the corresponding [code challenge][generateCodeChallenge].
     *  4. Constructs the authorization URL with all required query parameters.
     *  5. Returns an [Intent.ACTION_VIEW] intent pointing at that URL.
     *
     * Query parameters appended to [AUTH_URL]:
     * - `client_id`             – Identifies this application to Spotify (from BuildConfig).
     * - `response_type=code`    – Requests an authorization code (not an implicit token).
     * - `redirect_uri`          – The URI Spotify will redirect to after the user consents;
     *                             must match one of the URIs registered in the Spotify Dashboard.
     * - `scope`                 – Space-separated list of permissions being requested ([SCOPES]).
     * - `code_challenge_method` – Always "S256" (SHA-256); "plain" is not accepted by Spotify.
     * - `code_challenge`        – The Base64url-SHA-256 hash of the code verifier.
     *
     * @return An [Intent] with action [Intent.ACTION_VIEW] that launches the Spotify
     *   authorization page in the system browser.
     */
    fun getAuthIntent(): Intent {
        val codeVerifier = generateCodeVerifier()

        // Persist the verifier before launching the browser; if the Activity is
        // recreated while the user is in the browser, this value must still be
        // retrievable when the redirect URI callback arrives
        prefs.edit().putString(KEY_CODE_VERIFIER, codeVerifier).apply()

        val codeChallenge = generateCodeChallenge(codeVerifier)

        // Build the full authorization URI with all PKCE and OAuth parameters
        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            // Tell Spotify we're using the S256 (SHA-256) challenge method
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .build()

        return Intent(Intent.ACTION_VIEW, uri)
    }

    /**
     * Extracts the Spotify authorization code from the redirect URI received
     * after the user grants access on the Spotify authorization page.
     *
     * Spotify appends `?code=<authorization_code>` (on success) or
     * `?error=access_denied` (on failure) to the redirect URI. This method
     * reads only the `code` parameter; callers should treat a `null` return
     * value as an authorization failure or user cancellation.
     *
     * @param uri The full redirect [Uri] delivered to the app via an
     *   [android.app.Activity] intent or deep-link handler.
     * @return The one-time authorization code string if present, or `null` if
     *   [uri] is `null` or does not contain the `code` parameter.
     */
    fun extractCodeFromUri(uri: Uri?): String? = uri?.getQueryParameter("code")

    /**
     * Returns the PKCE code verifier that was generated and stored during the
     * most recent call to [getAuthIntent].
     *
     * The verifier is required by the token exchange request: it is sent alongside
     * the authorization code to Spotify's token endpoint so that Spotify can
     * verify the requester is the same party that initiated the authorization flow.
     * After a successful token exchange the verifier is no longer needed, but it
     * is left in SharedPreferences and overwritten on the next [getAuthIntent] call.
     *
     * @return The stored PKCE code verifier used to complete the OAuth flow,
     *   or an empty string if no authorization flow has been started yet.
     */
    fun getCodeVerifier(): String = prefs.getString(KEY_CODE_VERIFIER, "") ?: ""
}
