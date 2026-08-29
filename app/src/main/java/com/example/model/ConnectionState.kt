package com.example.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val stage: String = "Connecting...") : ConnectionState
    data object Connected : ConnectionState
    data object Disconnecting : ConnectionState
    data class Error(val message: String, val timestamp: Long = System.currentTimeMillis()) : ConnectionState

    val isConnected: Boolean
        get() = this is Connected

    val isConnecting: Boolean
        get() = this is Connecting

    val isDisconnected: Boolean
        get() = this is Disconnected

    val isDisconnecting: Boolean
        get() = this is Disconnecting

    val isBusy: Boolean
        get() = this is Connecting || this is Disconnecting
}
