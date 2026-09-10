package com.smartreminder.data.remote.profile

import com.smartreminder.data.remote.SupabaseManager
import com.smartreminder.domain.model.user.UserProfile
import com.smartreminder.domain.repository.UserProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SupabaseUserProfileRepository(
    private val supabase: SupabaseClient = SupabaseManager.client
) : UserProfileRepository {

    override suspend fun getCurrentProfile(): UserProfile {
        val user = try {
            supabase.auth.currentUserOrNull()
        } catch (_: Exception) {
            null
        }

        val metadata = user?.userMetadata
        val name = metadata?.get("full_name")?.jsonPrimitive?.contentOrNull
            ?: metadata?.get("name")?.jsonPrimitive?.contentOrNull
            ?: user?.email?.substringBefore("@")
        val email = user?.email
        val avatarUrl = metadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
            ?: metadata?.get("picture")?.jsonPrimitive?.contentOrNull

        return UserProfile(
            displayName = name,
            email = email,
            avatarUrl = avatarUrl
        )
    }

    override suspend fun updateProfile(displayName: String?, avatarUrl: String?) {
        val user = supabase.auth.currentUserOrNull() ?: return
        val currentMeta = user.userMetadata ?: emptyMap()

        supabase.auth.updateUser {
            data = buildJsonObject {
                currentMeta.forEach { (key, value) ->
                    put(key, value)
                }
                displayName?.let { put("full_name", it) }
                avatarUrl?.let { put("avatar_url", it) }
            }
        }
    }
}
