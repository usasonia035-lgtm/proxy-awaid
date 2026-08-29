package com.example.model

enum class ProxyType(val displayName: String, val defaultPort: Int) {
    SOCKS5("SOCKS5", 1080),
    HTTP("HTTP", 8080),
    HTTPS("HTTPS", 443);

    companion object {
        fun fromString(value: String): ProxyType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SOCKS5
        }
    }
}
