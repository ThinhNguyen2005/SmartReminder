package com.smartreminder.data.remote.preferences

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Remote DTO representing public.user_preferences table row in Supabase.
 * Uses primitive types for serialization compatibility across PostgREST.
 */
@Serializable
data class UserPreferencesRemoteDto(
    @SerialName("user_id")
    val userId: String,

    @SerialName("onboarding_completed")
    val onboardingCompleted: Boolean,

    @SerialName("wake_up_minute")
    val wakeUpMinute: Int,

    @SerialName("sleep_minute")
    val sleepMinute: Int,

    @SerialName("selected_goals")
    val selectedGoals: List<String> = emptyList()
)
