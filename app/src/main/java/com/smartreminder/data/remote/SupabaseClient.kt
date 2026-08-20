package com.smartreminder.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Quản lý Supabase Client cho toàn ứng dụng.
 * Bạn có thể lấy SUPABASE_URL và SUPABASE_ANON_KEY từ Dashboard:
 * https://supabase.com/dashboard/project/<your-project-id>/settings/api
 */
object SupabaseManager {
    // 1. Project URL từ Supabase Dashboard của bạn (Chính xác: 3 chữ 'r' - zrrrah)
    val SUPABASE_URL = "https://ygmzcumcmkyfjnzrrrah.supabase.co"

    // 2. anon public Key từ Supabase Dashboard của bạn
    val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlnbXpjdW1jbWt5ZmpuenJycmFoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcxMDU0NjksImV4cCI6MjEwMjY4MTQ2OX0.LWeNYqFp7zN6XJvmXsfcSUQbppegcreOYHkUl86JoLM"

    // 3. Web Client ID (Google OAuth 2.0 Web Application) từ Google Cloud Console (Dùng mã Web Client ID ở Ảnh 3 của bạn)
    val GOOGLE_WEB_CLIENT_ID = "29777417746-3gmlaloi5ohmsqb04pq6u8ucbbata53g.apps.googleusercontent.com"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
