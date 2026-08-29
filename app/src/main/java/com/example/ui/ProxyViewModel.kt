package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppSettings
import com.example.model.ConnectionInfo
import com.example.model.ConnectionState
import com.example.model.ProxyConfig
import com.example.model.ProxyType
import com.example.proxy.ProbeResult
import com.example.proxy.ProxyProber
import com.example.storage.ProxyRepository
import com.example.util.HapticFeedbackManager
import com.example.vpn.OwaisVpnService
import com.example.vpn.VpnController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ProxyUiState(
    val selectedType: ProxyType = ProxyType.SOCKS5,
    val hostInput: String = "",
    val portInput: String = "1080",
    val usernameInput: String = "",
    val passwordInput: String = "",
    val isPasswordVisible: Boolean = false,
    val validationError: String? = null,
    val activeSavedConfigId: String? = null,
    val isTesting: Boolean = false,
    val testResult: ProbeResult? = null,
    val connectedDurationSeconds: Long = 0L,
    val saveMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val validityDays: Int = 30,
    val expiresAt: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L)
)

class ProxyViewModel(
    private val repository: ProxyRepository,
    private val hapticManager: HapticFeedbackManager
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = VpnController.connectionState
    val connectionInfo: StateFlow<ConnectionInfo> = VpnController.connectionInfo
    val savedConfigs: StateFlow<List<ProxyConfig>> = repository.savedConfigs
    val appSettings: StateFlow<AppSettings> = repository.appSettings

    private val _uiState = MutableStateFlow(ProxyUiState())
    val uiState: StateFlow<ProxyUiState> = _uiState.asStateFlow()

    private val proxyProber = ProxyProber()
    private var durationTimerJob: Job? = null
    private var lastState: ConnectionState = ConnectionState.Disconnected

    init {
        // Load initial active config
        val active = repository.loadActiveConfig()
        if (active.host.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                selectedType = active.type,
                hostInput = active.host,
                portInput = active.port.toString(),
                usernameInput = active.username,
                passwordInput = active.password,
                activeSavedConfigId = active.id,
                createdAt = active.createdAt,
                validityDays = active.validityDays,
                expiresAt = active.expiresAt
            )
        }

        // Monitor state changes to trigger haptics and duration timer
        viewModelScope.launch {
            connectionState.collect { state ->
                handleStateTransitions(state)
            }
        }
    }

    private fun handleStateTransitions(newState: ConnectionState) {
        val settings = appSettings.value
        when (newState) {
            is ConnectionState.Connecting -> {
                if (lastState !is ConnectionState.Connecting) {
                    hapticManager.vibrateStart(settings.vibrationEnabled)
                }
                stopDurationTimer()
            }
            is ConnectionState.Connected -> {
                if (lastState !is ConnectionState.Connected) {
                    hapticManager.vibrateSuccess(settings.vibrationEnabled)
                    startDurationTimer()
                }
            }
            is ConnectionState.Error -> {
                hapticManager.vibrateError(settings.vibrationEnabled)
                stopDurationTimer()
            }
            is ConnectionState.Disconnected -> {
                if (lastState is ConnectionState.Connected || lastState is ConnectionState.Disconnecting) {
                    hapticManager.vibrateDisconnect(settings.vibrationEnabled)
                }
                stopDurationTimer()
            }
            is ConnectionState.Disconnecting -> {
                stopDurationTimer()
            }
        }
        lastState = newState
    }

    private fun startDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                _uiState.value = _uiState.value.copy(connectedDurationSeconds = elapsed)
                delay(1000L)
            }
        }
    }

    private fun stopDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = null
        _uiState.value = _uiState.value.copy(connectedDurationSeconds = 0L)
    }

    fun onTypeChanged(type: ProxyType) {
        val currentPort = _uiState.value.portInput.toIntOrNull()
        val defaultPortForPrevious = _uiState.value.selectedType.defaultPort
        val newPort = if (currentPort == null || currentPort == defaultPortForPrevious) {
            type.defaultPort.toString()
        } else {
            _uiState.value.portInput
        }
        _uiState.value = _uiState.value.copy(
            selectedType = type,
            portInput = newPort,
            validationError = null,
            testResult = null
        )
    }

    fun onHostChanged(host: String) {
        _uiState.value = _uiState.value.copy(hostInput = host, validationError = null, testResult = null)
    }

    fun onPortChanged(port: String) {
        val filtered = port.filter { it.isDigit() }.take(5)
        _uiState.value = _uiState.value.copy(portInput = filtered, validationError = null, testResult = null)
    }

    fun onUsernameChanged(user: String) {
        _uiState.value = _uiState.value.copy(usernameInput = user, validationError = null, testResult = null)
    }

    fun onPasswordChanged(pass: String) {
        _uiState.value = _uiState.value.copy(passwordInput = pass, validationError = null, testResult = null)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(isPasswordVisible = !_uiState.value.isPasswordVisible)
    }

    fun dismissValidationError() {
        _uiState.value = _uiState.value.copy(validationError = null)
    }

    fun dismissSaveMessage() {
        _uiState.value = _uiState.value.copy(saveMessage = null)
    }

    fun dismissTestResult() {
        _uiState.value = _uiState.value.copy(testResult = null)
    }

    fun onValidityDaysChanged(days: Int) {
        val created = _uiState.value.createdAt
        _uiState.value = _uiState.value.copy(
            validityDays = days,
            expiresAt = created + (days.toLong() * 24L * 60 * 60 * 1000L)
        )
    }

    fun getCurrentConfig(): ProxyConfig {
        val state = _uiState.value
        val port = state.portInput.toIntOrNull() ?: state.selectedType.defaultPort
        return ProxyConfig(
            id = state.activeSavedConfigId ?: java.util.UUID.randomUUID().toString(),
            name = "${state.selectedType.displayName} ${state.hostInput.trim()}:$port",
            type = state.selectedType,
            host = state.hostInput.trim(),
            port = port,
            username = state.usernameInput,
            password = state.passwordInput,
            createdAt = state.createdAt,
            validityDays = state.validityDays,
            expiresAt = state.expiresAt
        )
    }

    fun saveCurrentProxy(): Boolean {
        val config = getCurrentConfig()
        val (isValid, errorMsg) = config.isValid()
        if (!isValid) {
            _uiState.value = _uiState.value.copy(validationError = errorMsg)
            return false
        }

        repository.saveActiveConfig(config)
        repository.saveToSavedList(config)
        _uiState.value = _uiState.value.copy(
            activeSavedConfigId = config.id,
            saveMessage = "Proxy configuration saved successfully",
            validationError = null
        )
        return true
    }

    fun loadSavedConfig(config: ProxyConfig) {
        _uiState.value = _uiState.value.copy(
            selectedType = config.type,
            hostInput = config.host,
            portInput = config.port.toString(),
            usernameInput = config.username,
            passwordInput = config.password,
            activeSavedConfigId = config.id,
            createdAt = config.createdAt,
            validityDays = config.validityDays,
            expiresAt = config.expiresAt,
            validationError = null,
            testResult = null
        )
        repository.saveActiveConfig(config)
    }

    fun deleteSavedConfig(id: String) {
        repository.deleteFromSavedList(id)
        if (_uiState.value.activeSavedConfigId == id) {
            _uiState.value = _uiState.value.copy(activeSavedConfigId = null)
        }
    }

    fun clearConfiguration() {
        repository.clearActiveConfig()
        val now = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            selectedType = ProxyType.SOCKS5,
            hostInput = "",
            portInput = "1080",
            usernameInput = "",
            passwordInput = "",
            activeSavedConfigId = null,
            createdAt = now,
            validityDays = 30,
            expiresAt = now + (30L * 24 * 60 * 60 * 1000L),
            validationError = null,
            testResult = null,
            saveMessage = "Configuration cleared"
        )
    }

    fun testProxyConnection() {
        val config = getCurrentConfig()
        val (isValid, errorMsg) = config.isValid()
        if (!isValid) {
            _uiState.value = _uiState.value.copy(validationError = errorMsg)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, testResult = null, validationError = null)
            val result = proxyProber.probeProxy(config, timeoutMs = 8000)
            _uiState.value = _uiState.value.copy(isTesting = false, testResult = result)
        }
    }

    fun toggleConnection(
        context: Context,
        onRequireVpnPermission: (Intent) -> Unit
    ) {
        val currentState = connectionState.value

        if (currentState.isConnected || currentState.isConnecting) {
            // STOP connection
            OwaisVpnService.stopService(context)
            return
        }

        // START connection
        val config = getCurrentConfig()
        val (isValid, errorMsg) = config.isValid()
        if (!isValid) {
            _uiState.value = _uiState.value.copy(validationError = errorMsg)
            return
        }

        // Persist as active proxy
        repository.saveActiveConfig(config)

        // Check VPN Permission
        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            onRequireVpnPermission(vpnIntent)
        } else {
            startVpnTunnel(context, config)
        }
    }

    fun onVpnPermissionGranted(context: Context) {
        val config = getCurrentConfig()
        startVpnTunnel(context, config)
    }

    private fun startVpnTunnel(context: Context, config: ProxyConfig) {
        OwaisVpnService.startService(context, config)
    }

    fun updateSettings(settings: AppSettings) {
        repository.updateSettings(settings)
    }
}
