package com.example.secretary

/**
 * Keeps Retrofit and manual OkHttp calls on the same valid API base URL format.
 */
object ApiUrlNormalizer {
    fun normalize(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) return BuildConfig.BASE_URL.ensureTrailingSlash()
        return trimmed.ensureTrailingSlash()
    }

    fun resolve(baseUrl: String, endpoint: String): String {
        return normalize(baseUrl) + endpoint.trimStart('/')
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
}
