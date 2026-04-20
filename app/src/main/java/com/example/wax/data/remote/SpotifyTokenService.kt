package com.example.wax.data.remote

import com.example.wax.data.remote.dto.TokenDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Retrofit service interface for Spotify's **Accounts API** token endpoint
 * (`https://accounts.spotify.com/api/token`).
 *
 * Both methods POST to the same `/token` path but carry a different `grant_type`
 * field, which tells Spotify which OAuth 2.0 flow to execute:
 *
 * | Method                | `grant_type`         | Purpose                                      |
 * |-----------------------|----------------------|----------------------------------------------|
 * | [getAccessToken]      | `authorization_code` | Trade a one-time auth code for token pair    |
 * | [refreshAccessToken]  | `refresh_token`      | Silently obtain a new access token           |
 *
 * Both requests are sent as `application/x-www-form-urlencoded` bodies (not JSON),
 * which is mandated by the OAuth 2.0 specification (RFC 6749 §4.1.3). The
 * `@FormUrlEncoded` + `@Field` annotations instruct Retrofit to encode the parameters
 * accordingly rather than serializing a JSON body.
 *
 * No `Authorization` header is sent here because this app uses PKCE, which is
 * designed for public clients that cannot store a client secret. Identity is
 * proved instead via the `client_id` + `code_verifier` pair.
 */
interface SpotifyTokenService {

    /**
     * Exchanges a PKCE authorization code for an access token and refresh token.
     *
     * This is the **first** token request in the OAuth 2.0 PKCE flow and must be
     * called exactly once after the user grants access on the Spotify authorization
     * page and the app receives the one-time `code` via the redirect URI.
     *
     * Spotify validates the request by:
     * 1. Looking up the authorization code and confirming it has not expired or
     *    been used before.
     * 2. Re-hashing [codeVerifier] with SHA-256 and comparing it to the
     *    `code_challenge` that was submitted when the authorization flow started.
     *    A mismatch means the requester is not the party that initiated the flow.
     * 3. Confirming [redirectUri] exactly matches the one registered in the
     *    Spotify Developer Dashboard and sent during authorization.
     *
     * On success Spotify returns a [TokenDto] containing both an access token
     * (valid for ~1 hour) and a refresh token (long-lived). The authorization code
     * is invalidated immediately and cannot be reused.
     *
     * Why `grant_type = "authorization_code"`?
     * The `grant_type` field tells Spotify's token endpoint which grant flow is
     * being executed. `authorization_code` is the standard OAuth 2.0 code grant,
     * distinguished from `refresh_token` (silent renewal) and `client_credentials`
     * (server-to-server, not used here).
     *
     * @param grantType Always `"authorization_code"` for this flow; defaults to that value.
     * @param code The one-time authorization code extracted from the redirect URI
     *   after the user approved the permission request.
     * @param redirectUri The redirect URI the authorization code was delivered to.
     *   Must be an exact, byte-for-byte match with the value sent during authorization
     *   and with the URI registered in the Spotify Developer Dashboard.
     * @param clientId The application's Spotify client ID, used to identify the app
     *   since no client secret is involved in PKCE.
     * @param codeVerifier The original PKCE code verifier generated before the
     *   authorization request was sent. Spotify re-derives the challenge from this
     *   value and checks it against the stored challenge to prove authenticity.
     * @return A [TokenDto] containing the access token, token type, expiry in seconds,
     *   and a refresh token for future silent renewals.
     */
    @FormUrlEncoded
    @POST("token")
    suspend fun getAccessToken(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String
    ): TokenDto

    /**
     * Exchanges a refresh token for a new access token without user interaction.
     *
     * This is the **silent renewal** path, called whenever the current access token
     * has expired (or is about to expire, per the 60-second buffer in
     * `TokenManager`). It does not require the user to re-authorize the app.
     *
     * Spotify may optionally issue a new refresh token in the response. If
     * [TokenDto.refreshToken] is non-null, the caller must replace the stored
     * refresh token with the new one; using the old token again will result in a
     * 400 error.
     *
     * Why `grant_type = "refresh_token"`?
     * The `grant_type` value switches Spotify's token endpoint into refresh mode.
     * Unlike the `authorization_code` grant, no `code` or `code_verifier` is needed —
     * the refresh token itself is the credential. The `client_id` is still required
     * for PKCE public clients because there is no client secret to identify the app.
     *
     * @param grantType Always `"refresh_token"` for this flow; defaults to that value.
     * @param refreshToken The long-lived refresh token previously obtained from
     *   [getAccessToken] and stored by `TokenManager`.
     * @param clientId The application's Spotify client ID.
     * @return A [TokenDto] containing the new access token and its expiry in seconds.
     *   [TokenDto.refreshToken] may be non-null if Spotify rotated the refresh token.
     */
    @FormUrlEncoded
    @POST("token")
    suspend fun refreshAccessToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String
    ): TokenDto
}
