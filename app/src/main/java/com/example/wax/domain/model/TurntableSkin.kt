package com.example.wax.domain.model

enum class TurntableSkin(val key: String) {
    DARK("dark");

    companion object {
        fun fromKey(key: String): TurntableSkin = DARK
    }
}
