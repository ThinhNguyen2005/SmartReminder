package com.smartreminder.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseConfigurationTest {

    @Test(expected = MissingSupabaseConfigurationException::class)
    fun `missing collaboration config fails clearly`() {
        SupabaseConfiguration(url = "", anonKey = "").requireComplete()
    }

    @Test
    fun `configured collaboration target is preserved exactly`() {
        val config = SupabaseConfiguration(
            url = "https://g2-dev.example.supabase.co",
            anonKey = "dev-anon-key"
        ).requireComplete()

        assertEquals("https://g2-dev.example.supabase.co", config.url)
        assertEquals("dev-anon-key", config.anonKey)
    }
}
