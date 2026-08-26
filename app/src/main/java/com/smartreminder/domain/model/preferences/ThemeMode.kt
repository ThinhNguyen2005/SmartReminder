package com.smartreminder.domain.model.preferences

/**
 * Theme preference with stable [storageKey] for persistence.
 */
enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        private val keyMap = entries.associateBy { it.storageKey }

        fun fromStorageKey(key: String): ThemeMode = keyMap[key] ?: SYSTEM
    }
}
