package com.example.wax.domain.model

enum class TurntableSkin(val key: String) {
    DARK("dark"),
    VINTAGE_WOOD("vintage_wood"),
    MINIMALIST("minimalist");

    companion object {
        fun fromKey(key: String): TurntableSkin =
            entries.firstOrNull { it.key == key } ?: DARK
    }
}
