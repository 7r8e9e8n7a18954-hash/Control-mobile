package com.universalaiagent.data.provider

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object EndpointValidator {
    fun validate(baseUrl: String, allowInsecureHttp: Boolean): String? {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) return "Base URL is required"
        val url = trimmed.toHttpUrlOrNull() ?: return "Enter a valid HTTPS base URL"
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return "Base URLs must not contain credentials"
        }
        if (url.scheme == "http") {
            if (!allowInsecureHttp) return "HTTP is blocked. Use HTTPS or explicitly allow local HTTP."
            if (!isLoopback(url.host)) {
                return "Insecure HTTP is only allowed for localhost or the Android emulator host"
            }
        }
        if (url.scheme != "https" && url.scheme != "http") return "Only HTTP(S) endpoints are supported"
        if (url.host.isBlank()) return "Base URL has no host"
        return null
    }

    fun normalized(baseUrl: String): String = baseUrl.trim().trimEnd('/')

    fun isLoopback(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "localhost" ||
            normalized == "127.0.0.1" ||
            normalized == "::1" ||
            normalized == "10.0.2.2"
    }
}
