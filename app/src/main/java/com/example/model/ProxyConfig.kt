package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ProxyConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: ProxyType = ProxyType.SOCKS5,
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val validityDays: Int = 30,
    val expiresAt: Long = createdAt + (validityDays.toLong() * 24L * 60 * 60 * 1000L)
) {
    val requiresAuth: Boolean
        get() = username.isNotBlank() || password.isNotBlank()

    val formattedAddress: String
        get() = if (host.isNotBlank()) "$host:$port" else ""

    val daysRemaining: Long
        get() {
            val diff = expiresAt - System.currentTimeMillis()
            return if (diff > 0) (diff / (24L * 60 * 60 * 1000L)) else 0L
        }

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAt

    val isExpiringSoon: Boolean
        get() = !isExpired && daysRemaining <= 3L

    val formattedCreatedDate: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            return sdf.format(Date(createdAt))
        }

    val formattedExpiryDate: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            return sdf.format(Date(expiresAt))
        }

    val expiryStatusText: String
        get() = when {
            isExpired -> "Expired on $formattedExpiryDate"
            daysRemaining == 0L -> "Expires today!"
            daysRemaining == 1L -> "Expires tomorrow (1 day left)"
            isExpiringSoon -> "Expires in $daysRemaining days ($formattedExpiryDate)"
            else -> "Expires on $formattedExpiryDate ($daysRemaining days left)"
        }

    fun isValid(): Pair<Boolean, String?> {
        val trimmedHost = host.trim()
        if (trimmedHost.isBlank()) {
            return false to "Proxy IP / Host cannot be empty"
        }
        if (port !in 1..65535) {
            return false to "Port must be between 1 and 65535"
        }
        if (username.isBlank() && password.isNotBlank()) {
            return false to "Username is required when password is provided"
        }
        return true to null
    }
}

