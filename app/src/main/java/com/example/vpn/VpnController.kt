package com.example.vpn

import com.example.model.ConnectionInfo
import com.example.model.ConnectionState
import com.example.model.ProxyConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnController {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionInfo = MutableStateFlow(ConnectionInfo())
    val connectionInfo: StateFlow<ConnectionInfo> = _connectionInfo.asStateFlow()

    fun updateState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun updateInfo(info: ConnectionInfo) {
        _connectionInfo.value = info
    }

    fun updateTraffic(bytesTx: Long, bytesRx: Long) {
        _connectionInfo.value = _connectionInfo.value.copy(
            bytesTx = bytesTx,
            bytesRx = bytesRx
        )
    }

    fun reset() {
        _connectionState.value = ConnectionState.Disconnected
        _connectionInfo.value = ConnectionInfo()
    }
}
