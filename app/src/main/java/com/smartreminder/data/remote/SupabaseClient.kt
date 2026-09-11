package com.smartreminder.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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

    /** Returns null only when the G2 Supabase configuration is absent. */
    fun clientOrNull(): SupabaseClient? = try {
        client
    } catch (_: MissingSupabaseConfigurationException) {
        null
    }

    fun currentUserIdOrNull(): String? = clientOrNull()
        ?.auth
        ?.currentUserOrNull()
        ?.id

    /** Auth-session identity stream kept as a typed, account-safe boundary for repositories. */
    fun observeCurrentUserId(): Flow<String?> = clientOrNull()
        ?.auth
        ?.sessionStatus
        ?.map { status ->
            (status as? SessionStatus.Authenticated)?.session?.user?.id
        }
        ?.distinctUntilChanged()
        ?: flowOf(null)

    suspend fun signOut() {
        client.auth.signOut()
    }
}
