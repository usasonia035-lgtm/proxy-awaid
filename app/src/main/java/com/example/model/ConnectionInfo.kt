package com.example.model

data class ConnectionInfo(
    val publicIp: String = "Detecting...",
    val countryCode: String = "",
    val countryName: String = "Detecting location...",
    val countryFlag: String = "🌐",
    val proxyType: ProxyType = ProxyType.SOCKS5,
    val host: String = "",
    val port: Int = 0,
    val connectedSinceMillis: Long = 0L,
    val bytesTx: Long = 0L,
    val bytesRx: Long = 0L,
    val pingLatencyMs: Long = 0L
) {
    val countryDisplay: String
        get() = when {
            countryCode.isBlank() && countryName.contains("Detecting", ignoreCase = true) -> "Detecting location..."
            countryCode.isBlank() -> "Location unavailable"
            else -> "$countryFlag $countryName"
        }
}
