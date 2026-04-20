package com.example.wax.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object representing the JSON body returned by Spotify's
 * `/token` endpoint for both the initial authorization-code exchange and
 * subsequent refresh-token exchanges.
 *
 * ## Fields explained
 *
 * ### `access_token` vs `refresh_token`
 *
 * These are two fundamentally different credentials with different lifetimes
 * and purposes:
 *
 * | Field           | Lifetime       | Purpose                                               |
 * |-----------------|----------------|-------------------------------------------------------|
 * | `access_token`  | ~1 hour        | Sent in the `Authorization: Bearer` header of every  |
 * |                 |                | Spotify Web API request to prove the app is acting   |
 * |                 |                | on behalf of the authenticated user.                 |
 * | `refresh_token` | Long-lived     | Used **once** to obtain a new access token when the  |
 * |                 |                | current one expires, without re-prompting the user.  |
 * |                 |                | Spotify may rotate it; always persist the latest one.|
 *
 * ### `expires_in` is in **seconds**, not milliseconds
 *
 * Spotify returns the token lifetime as an integer number of **seconds**
 * (typically `3600` = 1 hour). Callers that compute an absolute expiry
 * timestamp must multiply by `1_000` to convert to milliseconds before
 * adding to `System.currentTimeMillis()`. Forgetting this conversion would
 * cause the token to appear expired ~16 minutes after the Unix epoch (1970),
 * triggering constant unnecessary refreshes.
 *
 * @property accessToken The OAuth 2.0 bearer token used to authenticate requests
 *   to `https://api.spotify.com/v1/`. Maps from JSON key `access_token`.
 * @property tokenType The type of token issued. Always `"Bearer"` for Spotify.
 *   Maps from JSON key `token_type`.
 * @property expiresIn The number of **seconds** from the time of issuance until the
 *   access token expires. Typically `3600` (1 hour). Maps from JSON key `expires_in`.
 * @property refreshToken A long-lived token that can be exchanged for a new access
 *   token via `SpotifyTokenService.refreshAccessToken`. Present on the initial code
 *   exchange and optionally on refresh responses (Spotify may rotate it). `null` when
 *   the token endpoint does not include a new refresh token in the response.
 *   Maps from JSON key `refresh_token`.
 */
data class TokenDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    // Number of seconds until the access token expires — NOT milliseconds
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("refresh_token") val refreshToken: String?
)
