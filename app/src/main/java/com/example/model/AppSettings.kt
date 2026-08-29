package com.example.model

data class AppSettings(
    val autoConnectOnLaunch: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val notificationEnabled: Boolean = true,
    val bypassLocalSubnets: Boolean = true
)
