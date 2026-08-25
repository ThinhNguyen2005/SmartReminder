package com.smartreminder.domain.auth

import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * Trợ lý kiểm tra tính hợp lệ của các dữ liệu xác thực (Auth Validation).
 * Thuần Kotlin Logic, dễ dàng kiểm thử tự động với Unit Test.
 */
object AuthValidationHelper {

    private val EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    /**
     * Kiểm tra định dạng email người dùng nhập vào.
     */
    fun isValidEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return EMAIL_PATTERN.matcher(email.trim()).matches()
    }

    /**
     * Kiểm tra URL của dự án Supabase (bắt buộc giao thức HTTPS và đúng định dạng supabase.co).
     */
    fun isValidSupabaseUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        return trimmed.startsWith("https://") && trimmed.endsWith(".supabase.co") && trimmed.length > 20
    }

    /**
     * Kiểm tra tính hợp lệ của Google Web Client ID.
     * Trả về false nếu là chuỗi rỗng hoặc chuỗi placeholder mặc định.
     */
    fun isValidWebClientId(clientId: String?): Boolean {
        if (clientId.isNullOrBlank()) return false
        val trimmed = clientId.trim()
        if (trimmed == "YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com") return false
        return trimmed.endsWith(".apps.googleusercontent.com") && trimmed.length > 30
    }

    /**
     * Băm chuỗi rawNonce bằng thuật toán SHA-256 thành chuỗi hex 64 ký tự.
     */
    fun hashNonce(rawNonce: String): String {
        val bytes = rawNonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
