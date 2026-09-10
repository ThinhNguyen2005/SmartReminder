package com.smartreminder.domain.repository

import com.smartreminder.domain.model.user.UserProfile

/**
 * Domain repository for accessing and updating the user profile.
 */
interface UserProfileRepository {

    /**
     * Retrieves the current user's profile, or default guest values if not authenticated.
     */
    suspend fun getCurrentProfile(): UserProfile

    /**
     * Updates user profile metadata (e.g. display name and avatar URL).
     *
     * @param displayName new display name, or null to leave unchanged
     * @param avatarUrl new avatar URL or image link, or null to leave unchanged
     */
    suspend fun updateProfile(displayName: String?, avatarUrl: String?)
}
