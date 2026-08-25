package com.smartreminder.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {

    @Test
    fun `given valid email addresses, when validating email, then returns true`() {
        assertTrue(AuthValidationHelper.isValidEmail("user@example.com"))
        assertTrue(AuthValidationHelper.isValidEmail("thinh.nguyen@smartreminder.vn"))
        assertTrue(AuthValidationHelper.isValidEmail("test+cue@domain.co.uk"))
    }

    @Test
    fun `given invalid email addresses, when validating email, then returns false`() {
        assertFalse(AuthValidationHelper.isValidEmail(null))
        assertFalse(AuthValidationHelper.isValidEmail(""))
        assertFalse(AuthValidationHelper.isValidEmail("   "))
        assertFalse(AuthValidationHelper.isValidEmail("plainaddress"))
        assertFalse(AuthValidationHelper.isValidEmail("@missingusername.com"))
        assertFalse(AuthValidationHelper.isValidEmail("username@.com"))
    }

    @Test
    fun `given valid supabase url, when validating url, then returns true`() {
        val validUrl = "https://ygmzcumcmkyfjnzrrrah.supabase.co"
        assertTrue(AuthValidationHelper.isValidSupabaseUrl(validUrl))
    }

    @Test
    fun `given invalid or insecure supabase url, when validating url, then returns false`() {
        assertFalse(AuthValidationHelper.isValidSupabaseUrl(null))
        assertFalse(AuthValidationHelper.isValidSupabaseUrl(""))
        assertFalse(AuthValidationHelper.isValidSupabaseUrl("http://insecure-supabase.co"))
        assertFalse(AuthValidationHelper.isValidSupabaseUrl("https://wrongdomain.com"))
    }

    @Test
    fun `given valid google web client id, when validating client id, then returns true`() {
        val validClientId = "29777417746-3gmlaloi5ohmsqb04pq6u8ucbbata53g.apps.googleusercontent.com"
        assertTrue(AuthValidationHelper.isValidWebClientId(validClientId))
    }

    @Test
    fun `given placeholder or empty client id, when validating client id, then returns false`() {
        assertFalse(AuthValidationHelper.isValidWebClientId(null))
        assertFalse(AuthValidationHelper.isValidWebClientId(""))
        assertFalse(AuthValidationHelper.isValidWebClientId("YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com"))
        assertFalse(AuthValidationHelper.isValidWebClientId("invalid-short-id"))
    }

    @Test
    fun `given raw nonce, when hashing with sha-256, then returns 64 character hex string`() {
        val rawNonce = "sample-raw-nonce-12345"
        val hashed = AuthValidationHelper.hashNonce(rawNonce)

        assertEquals(64, hashed.length)
        assertTrue(hashed.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
