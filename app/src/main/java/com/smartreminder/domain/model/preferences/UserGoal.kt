package com.smartreminder.domain.model.preferences

/**
 * User goals selected during onboarding.
 * Uses stable [storageKey] for persistence instead of [name] to survive enum renames.
 */
enum class UserGoal(val storageKey: String) {
    TASKS("tasks"),
    ROUTINES("routines"),
    PLANNING("planning"),
    STUDY("study"),
    TEAMWORK("teamwork");

    companion object {
        private val keyMap = entries.associateBy { it.storageKey }

        /** Returns null for unknown keys — caller decides whether to skip or fallback. */
        fun fromStorageKey(key: String): UserGoal? = keyMap[key]
    }
}
