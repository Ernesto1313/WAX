package com.example.wax.core.di

import com.example.wax.data.remote.SpotifyApiService
import com.example.wax.data.remote.SpotifyTokenService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module that provides all network-layer dependencies for the Wax app.
 *
 * ## Why two Retrofit instances?
 *
 * Spotify exposes two completely separate base URLs that serve different purposes:
 *
 * | Name            | Base URL                          | Purpose                                   |
 * |-----------------|-----------------------------------|-------------------------------------------|
 * | `spotify_api`   | `https://api.spotify.com/v1/`     | Playback control, track search, user info |
 * | `spotify_auth`  | `https://accounts.spotify.com/api/` | OAuth 2.0 token exchange and refresh    |
 *
 * Retrofit requires a single, fixed base URL per instance. Because the two domains
 * differ, a single Retrofit object cannot serve both. Attempting to hard-code the
 * full URL in `@GET`/`@POST` annotations would bypass Retrofit's URL resolution
 * logic entirely and is considered an anti-pattern. The clean solution is two
 * separate Retrofit instances, each pre-configured for its domain.
 *
 * ## Why `@Named`?
 *
 * Both instances have the same Java type — [Retrofit]. Without a qualifier,
 * Hilt/Dagger cannot distinguish which instance to inject when a parameter of
 * type [Retrofit] is requested. `@Named("spotify_api")` and `@Named("spotify_auth")`
 * act as string-based qualifiers that let Dagger route each binding to the correct
 * consumer (i.e., [provideSpotifyApiService] vs [provideSpotifyTokenService]).
 *
 * ## Scope
 *
 * All bindings are `@Singleton`, meaning each dependency is created once and
 * reused for the entire process lifetime. This is appropriate for network clients
 * because OkHttp's connection pool and thread pool are expensive to create and
 * are designed to be shared.
 *
 * Installed in [SingletonComponent] so the bindings live as long as the Application.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Creates and provides the shared [OkHttpClient] used by both Retrofit instances.
     *
     * A single client is shared to reuse OkHttp's internal connection pool and
     * thread pool, which are heavyweight resources. Both the API and auth Retrofit
     * instances receive the same client via constructor injection.
     *
     * ## Interceptor
     *
     * An [HttpLoggingInterceptor] set to [HttpLoggingInterceptor.Level.BODY] is
     * attached as an application-level interceptor. At `BODY` level it logs:
     * - The full request URL, method, and headers.
     * - The full request body (e.g., form-encoded token exchange parameters).
     * - The response status code, headers, and full response body.
     *
     * This is invaluable during development for diagnosing OAuth errors and
     * malformed API responses. In a production build this should be gated behind
     * a `BuildConfig.DEBUG` check or replaced with a `NONE`-level interceptor to
     * avoid leaking access tokens to logcat.
     *
     * @return A fully configured [OkHttpClient] singleton with body-level HTTP logging.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            // Log every request and response body — useful for debugging OAuth
            // token exchanges and Spotify API responses during development
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    /**
     * Creates and provides the [Retrofit] instance for Spotify's **Web API**.
     *
     * This instance is bound under the qualifier `"spotify_api"` and targets
     * `https://api.spotify.com/v1/`, which is the base URL for all data and
     * playback endpoints (e.g., `/me`, `/me/player`, `/search`).
     *
     * ## Converter factory
     *
     * [GsonConverterFactory] is registered so that Retrofit automatically
     * deserializes JSON response bodies into Kotlin/Java data classes using
     * Gson reflection. All DTOs in `data/remote/dto` are designed around
     * Gson's field-name conventions.
     *
     * The `@Named("spotify_api")` qualifier is required here because Dagger
     * would otherwise be unable to differentiate this [Retrofit] binding from
     * the one provided by [provideSpotifyAuthRetrofit], which shares the same type.
     *
     * @param okHttpClient The shared [OkHttpClient] provided by [provideOkHttpClient],
     *   injected automatically by Hilt.
     * @return A `@Singleton` [Retrofit] instance targeting `https://api.spotify.com/v1/`,
     *   qualified as `"spotify_api"`.
     */
    @Provides
    @Singleton
    @Named("spotify_api")
    fun provideSpotifyApiRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            // Base URL for all Spotify Web API endpoints (playback, search, user data)
            .baseUrl("https://api.spotify.com/v1/")
            .client(okHttpClient)
            // Deserialize JSON responses into DTOs automatically via Gson reflection
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Creates and provides the [Retrofit] instance for Spotify's **Accounts (auth) API**.
     *
     * This instance is bound under the qualifier `"spotify_auth"` and targets
     * `https://accounts.spotify.com/api/`, which hosts the OAuth 2.0 token endpoint
     * (`/token`). It is used exclusively by [SpotifyTokenService] to:
     * - Exchange a PKCE authorization code for an access token and refresh token.
     * - Exchange a refresh token for a new access token when the current one expires.
     *
     * Keeping auth traffic on a dedicated instance means the base URL never needs
     * to change for the API instance, and the two concerns (authentication vs. data
     * access) remain cleanly separated in the DI graph.
     *
     * The `@Named("spotify_auth")` qualifier distinguishes this binding from the
     * `"spotify_api"` Retrofit instance that shares the same [Retrofit] type.
     *
     * @param okHttpClient The shared [OkHttpClient] provided by [provideOkHttpClient],
     *   injected automatically by Hilt.
     * @return A `@Singleton` [Retrofit] instance targeting `https://accounts.spotify.com/api/`,
     *   qualified as `"spotify_auth"`.
     */
    @Provides
    @Singleton
    @Named("spotify_auth")
    fun provideSpotifyAuthRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            // Base URL for Spotify's OAuth 2.0 token endpoint (code exchange & refresh)
            .baseUrl("https://accounts.spotify.com/api/")
            .client(okHttpClient)
            // Deserialize the token JSON response body into TokenDto via Gson
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Creates and provides the [SpotifyApiService] Retrofit service interface.
     *
     * Retrofit's [Retrofit.create] generates a dynamic proxy that implements the
     * [SpotifyApiService] interface at runtime, translating each annotated method
     * into an HTTP call against `https://api.spotify.com/v1/`.
     *
     * The `@Named("spotify_api")` qualifier on the [retrofit] parameter tells
     * Dagger to inject the API-domain Retrofit instance rather than the auth one.
     *
     * @param retrofit The `"spotify_api"`-qualified [Retrofit] instance provided
     *   by [provideSpotifyApiRetrofit], injected automatically by Hilt.
     * @return A `@Singleton` implementation of [SpotifyApiService] backed by the
     *   Spotify Web API Retrofit instance.
     */
    @Provides
    @Singleton
    fun provideSpotifyApiService(@Named("spotify_api") retrofit: Retrofit): SpotifyApiService {
        // Retrofit generates a dynamic proxy class that implements SpotifyApiService;
        // every interface method becomes an HTTP call to api.spotify.com/v1/
        return retrofit.create(SpotifyApiService::class.java)
    }

    /**
     * Creates and provides the [SpotifyTokenService] Retrofit service interface.
     *
     * [SpotifyTokenService] handles the two token-endpoint calls required by the
     * PKCE OAuth flow:
     * 1. Initial code-for-token exchange after the user grants access.
     * 2. Refresh-token exchange to silently obtain a new access token.
     *
     * The `@Named("spotify_auth")` qualifier on the [retrofit] parameter ensures
     * Dagger injects the accounts-domain Retrofit instance, not the API one.
     *
     * @param retrofit The `"spotify_auth"`-qualified [Retrofit] instance provided
     *   by [provideSpotifyAuthRetrofit], injected automatically by Hilt.
     * @return A `@Singleton` implementation of [SpotifyTokenService] backed by the
     *   Spotify Accounts Retrofit instance.
     */
    @Provides
    @Singleton
    fun provideSpotifyTokenService(@Named("spotify_auth") retrofit: Retrofit): SpotifyTokenService {
        // Retrofit generates a dynamic proxy; every method becomes an HTTP call
        // to accounts.spotify.com/api/ (i.e., the /token endpoint)
        return retrofit.create(SpotifyTokenService::class.java)
    }
}
