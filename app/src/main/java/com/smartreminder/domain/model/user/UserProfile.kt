package com.smartreminder.domain.model.user

/**
 * Immutable domain model representing user profile information.
 */
data class UserProfile(
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null
)
