package com.smartreminder.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseManager {
    private val configuration: SupabaseConfiguration
        get() = SupabaseConfiguration.fromBuildConfig()

    val SUPABASE_URL: String
        get() = configuration.url

    val SUPABASE_ANON_KEY: String
        get() = configuration.anonKey

    // Google OAuth configuration remains separate from the G2 Supabase target.
    val GOOGLE_WEB_CLIENT_ID = "29777417746-3gmlaloi5ohmsqb04pq6u8ucbbata53g.apps.googleusercontent.com"

    val client: SupabaseClient by lazy {
        val configured = configuration.requireComplete()
        createSupabaseClient(
            supabaseUrl = configured.url,
            supabaseKey = configured.anonKey
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
