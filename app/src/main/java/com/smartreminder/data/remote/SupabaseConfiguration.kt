package com.smartreminder.data.remote

import com.smartreminder.BuildConfig

class MissingSupabaseConfigurationException(message: String) : IllegalStateException(message)

data class SupabaseConfiguration(
    val url: String,
    val anonKey: String
) {
    fun requireComplete(): SupabaseConfiguration {
        if (url.isBlank() || anonKey.isBlank()) {
            throw MissingSupabaseConfigurationException(
                "Missing G2 Supabase configuration. Set SUPABASE_URL and SUPABASE_ANON_KEY " +
                    "through local.properties, Gradle properties, or environment variables."
            )
        }
        return this
    }

    companion object {
        fun fromBuildConfig(): SupabaseConfiguration = SupabaseConfiguration(
            url = BuildConfig.COLLABORATION_SUPABASE_URL,
            anonKey = BuildConfig.COLLABORATION_SUPABASE_ANON_KEY
        )
    }
}
