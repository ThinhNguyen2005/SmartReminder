package com.smartreminder.domain.repository

import com.smartreminder.domain.model.OnboardingPreferencesSnapshot

/**
 * Domain contract for remote cloud persistence of onboarding preferences.
 * Explicitly decoupled from any remote SDK or transport technology (Supabase/PostgREST).
 */
interface UserPreferencesCloudRepository {

    /**
     * Retrieves remote onboarding preferences snapshot for [userId].
     *
     * @return [OnboardingPreferencesSnapshot] if a cloud record exists, null if no row found.
     * @throws Exception on network, transport, or mapping failure.
     */
    suspend fun getForUser(
        userId: String
    ): OnboardingPreferencesSnapshot?

    /**
     * Creates or updates the remote onboarding preferences record for [userId].
     * Idempotent operation.
     *
     * @throws Exception on network, transport, or persistence failure.
     */
    suspend fun upsertForUser(
        userId: String,
        snapshot: OnboardingPreferencesSnapshot
    )
}
