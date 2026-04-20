package com.example.wax.core.di

import com.example.wax.core.auth.SpotifyAuthManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt [EntryPoint] that exposes [SpotifyAuthManager] to code that is not
 * part of Hilt's standard injection graph — most notably Android [android.app.Service]
 * subclasses, [android.content.BroadcastReceiver]s, and [android.content.ContentProvider]s
 * that are not annotated with `@AndroidEntryPoint`.
 *
 * ## What is a Hilt EntryPoint?
 *
 * Hilt normally injects dependencies through constructor injection (for plain classes)
 * or field injection (for framework types annotated with `@AndroidEntryPoint`). However,
 * some Android framework components cannot be annotated with `@AndroidEntryPoint`, or
 * there are situations where you need a Hilt-managed object from code that Hilt does
 * not control the instantiation of. In those cases, an `@EntryPoint` interface acts as
 * a **manually-pulled injection bridge**: you declare which bindings you need as
 * interface methods, and Hilt generates the wiring at compile time.
 *
 * At runtime you retrieve the entry point via:
 * ```kotlin
 * val entryPoint = EntryPointAccessors.fromApplication(
 *     context.applicationContext,
 *     AuthEntryPoint::class.java
 * )
 * val authManager = entryPoint.spotifyAuthManager()
 * ```
 *
 * ## Why is this needed for [SpotifyAuthManager]?
 *
 * [SpotifyAuthManager] is a `@Singleton` managed by Hilt. Any `@AndroidEntryPoint`-
 * annotated Activity or ViewModel can receive it via constructor injection automatically.
 * But if a component such as a foreground [android.app.Service] (e.g., a media playback
 * service) or a deep-link handler that is not an Activity needs to initiate or complete
 * the Spotify OAuth flow, it cannot use constructor injection directly. Declaring
 * [AuthEntryPoint] lets that component reach into the [SingletonComponent] and pull
 * the already-constructed [SpotifyAuthManager] singleton without going outside the DI graph.
 *
 * ## Why [SingletonComponent]?
 *
 * [SpotifyAuthManager] is scoped to [SingletonComponent] (application lifetime).
 * The entry point must be installed in the same or a parent component to access the
 * binding — [SingletonComponent] is the top-level component and is accessible from
 * `context.applicationContext`, making it the natural choice here.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthEntryPoint {

    /**
     * Returns the [SpotifyAuthManager] singleton from the Hilt [SingletonComponent].
     *
     * This method is the single access point exposed by this entry point. Hilt
     * generates an implementation that returns the same [SpotifyAuthManager] instance
     * that would be injected anywhere else in the app — no new object is created.
     *
     * Typical usage from a non-injected context (e.g., a Service):
     * ```kotlin
     * val authManager = EntryPointAccessors
     *     .fromApplication(applicationContext, AuthEntryPoint::class.java)
     *     .spotifyAuthManager()
     * val intent = authManager.getAuthIntent()
     * startActivity(intent)
     * ```
     *
     * @return The application-scoped [SpotifyAuthManager] singleton.
     */
    fun spotifyAuthManager(): SpotifyAuthManager
}
