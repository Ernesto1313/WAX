package com.example.wax.domain.model

enum class LockScreenTheme(val key: String, val displayName: String) {
    FLOATING_VINYL("floating_vinyl", "Floating Vinyl"),
    SLEEVE("sleeve",                "Sleeve"),
    WAVEFORM("waveform",            "Waveform"),
    POLAROID("polaroid",            "Polaroid"),
    NEON("neon",                    "Neon");

    companion object {
        fun fromKey(key: String): LockScreenTheme =
            entries.firstOrNull { it.key == key } ?: FLOATING_VINYL
    }
}
