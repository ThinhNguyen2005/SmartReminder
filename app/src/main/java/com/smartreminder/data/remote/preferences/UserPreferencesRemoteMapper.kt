package com.smartreminder.data.remote.preferences

import com.smartreminder.domain.model.preferences.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.preferences.UserGoal
import java.time.LocalTime

/**
 * Maps between Supabase [UserPreferencesRemoteDto] and domain [OnboardingPreferencesSnapshot].
 */
object UserPreferencesRemoteMapper {

    private const val MIN_MINUTE = 0
    private const val MAX_MINUTE = 1439

    fun toDomain(dto: UserPreferencesRemoteDto): OnboardingPreferencesSnapshot {
        require(dto.wakeUpMinute in MIN_MINUTE..MAX_MINUTE) {
            "Invalid wake_up_minute from remote: ${dto.wakeUpMinute} (must be 0..1439)"
        }
        require(dto.sleepMinute in MIN_MINUTE..MAX_MINUTE) {
            "Invalid sleep_minute from remote: ${dto.sleepMinute} (must be 0..1439)"
        }

        val wakeUpTime = LocalTime.of(dto.wakeUpMinute / 60, dto.wakeUpMinute % 60)
        val sleepTime = LocalTime.of(dto.sleepMinute / 60, dto.sleepMinute % 60)

        // Ignore unknown storageKeys to ensure forward compatibility
        val goals = dto.selectedGoals.mapNotNull { key ->
            UserGoal.fromStorageKey(key)
        }.toSet()

        return OnboardingPreferencesSnapshot(
            wakeUpTime = wakeUpTime,
            sleepTime = sleepTime,
            goals = goals,
            onboardingCompleted = dto.onboardingCompleted
        )
    }

    fun toDto(userId: String, snapshot: OnboardingPreferencesSnapshot): UserPreferencesRemoteDto {
        val wakeMinute = snapshot.wakeUpTime.hour * 60 + snapshot.wakeUpTime.minute
        val sleepMinute = snapshot.sleepTime.hour * 60 + snapshot.sleepTime.minute

        val goalKeys = snapshot.goals.map { it.storageKey }

        return UserPreferencesRemoteDto(
            userId = userId,
            onboardingCompleted = snapshot.onboardingCompleted,
            wakeUpMinute = wakeMinute,
            sleepMinute = sleepMinute,
            selectedGoals = goalKeys
        )
    }
}
