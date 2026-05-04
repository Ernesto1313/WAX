package com.example.wax.domain.model

/**
 * Enum representing the available visual themes for the vinyl turntable on the main screen.
 *
 * Each skin controls the color palette of the [com.example.wax.presentation.common.VinylCanvas]
 * composable — body gradient, groove ring tint, directional highlight, rim vignette, label disc,
 * and spindle dot — without altering any layout or animation logic.
 *
 * Skins are persisted to DataStore as their [key] string via
 * [com.example.wax.core.preferences.UserPreferencesRepository.setSelectedSkin] so the user's
 * choice survives app restarts. [fromKey] is the single entry point for deserialization and
 * provides a safe fallback to [DARK] when an unrecognized key is encountered.
 *
 * @property key The stable string identifier written to / read from DataStore. Must not be
 *               changed between app versions as it is stored on device.
 */
enum class TurntableSkin(val key: String) {
    /** Classic near-black vinyl with subtle white groove rings. The default skin. */
    DARK("dark"),

    /** Warm amber-brown disc evoking 1960s–70s coloured pressings and mahogany plinths. */
    VINTAGE_WOOD("vintage_wood"),

    /** Clean light-grey disc referencing the stripped-back aesthetic of modern hi-fi. */
    MINIMALIST("minimalist");

    companion object {
        /**
         * Looks up a [TurntableSkin] by its DataStore [key] string.
         *
         * Returns [DARK] when no entry matches the given [key], which handles:
         * - A fresh install where no preference has been written yet (DataStore returns a default
         *   empty string that won't match any key).
         * - A downgrade scenario where a skin added in a later version is no longer present.
         *
         * @param key The string key read from DataStore.
         * @return The matching [TurntableSkin], or [DARK] if the key is unrecognized.
         */
        fun fromKey(key: String): TurntableSkin =
            entries.firstOrNull { it.key == key } ?: DARK
    }
}
