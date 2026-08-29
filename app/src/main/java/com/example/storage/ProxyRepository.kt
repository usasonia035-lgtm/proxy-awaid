package com.example.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.model.AppSettings
import com.example.model.ProxyConfig
import com.example.model.ProxyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ProxyRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentConfig = MutableStateFlow(loadActiveConfig())
    val currentConfig: StateFlow<ProxyConfig> = _currentConfig.asStateFlow()

    private val _savedConfigs = MutableStateFlow(loadSavedConfigs())
    val savedConfigs: StateFlow<List<ProxyConfig>> = _savedConfigs.asStateFlow()

    private val _appSettings = MutableStateFlow(loadSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    // AES encryption key derived from internal seed for sensitive fields
    private val keySpec = SecretKeySpec(
        "OPS_SecureKey_2026_ProxyApp01".toByteArray(StandardCharsets.UTF_8).copyOf(16),
        "AES"
    )
    private val ivSpec = IvParameterSpec(
        "OPS_InitVector01".toByteArray(StandardCharsets.UTF_8).copyOf(16)
    )

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (_: Exception) {
            value
        }
    }

    private fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        return try {
            val decoded = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            String(cipher.doFinal(decoded), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            value
        }
    }

    fun saveActiveConfig(config: ProxyConfig) {
        _currentConfig.value = config
        prefs.edit().apply {
            putString(KEY_ACTIVE_ID, config.id)
            putString(KEY_ACTIVE_NAME, config.name)
            putString(KEY_ACTIVE_TYPE, config.type.name)
            putString(KEY_ACTIVE_HOST, config.host)
            putInt(KEY_ACTIVE_PORT, config.port)
            putString(KEY_ACTIVE_USER, config.username)
            putString(KEY_ACTIVE_PASS, encrypt(config.password))
            putLong(KEY_ACTIVE_CREATED_AT, config.createdAt)
            putInt(KEY_ACTIVE_VALIDITY_DAYS, config.validityDays)
            putLong(KEY_ACTIVE_EXPIRES_AT, config.expiresAt)
            apply()
        }
    }

    fun loadActiveConfig(): ProxyConfig {
        val host = prefs.getString(KEY_ACTIVE_HOST, "") ?: ""
        if (host.isBlank()) {
            return ProxyConfig(
                type = ProxyType.SOCKS5,
                host = "",
                port = 1080,
                username = "",
                password = ""
            )
        }
        val typeStr = prefs.getString(KEY_ACTIVE_TYPE, ProxyType.SOCKS5.name) ?: ProxyType.SOCKS5.name
        val port = prefs.getInt(KEY_ACTIVE_PORT, 1080)
        val user = prefs.getString(KEY_ACTIVE_USER, "") ?: ""
        val encPass = prefs.getString(KEY_ACTIVE_PASS, "") ?: ""
        val id = prefs.getString(KEY_ACTIVE_ID, "") ?: ""
        val name = prefs.getString(KEY_ACTIVE_NAME, "") ?: ""
        val createdAt = prefs.getLong(KEY_ACTIVE_CREATED_AT, System.currentTimeMillis())
        val validityDays = prefs.getInt(KEY_ACTIVE_VALIDITY_DAYS, 30)
        val expiresAt = prefs.getLong(KEY_ACTIVE_EXPIRES_AT, createdAt + (validityDays.toLong() * 24L * 60 * 60 * 1000L))

        return ProxyConfig(
            id = if (id.isNotBlank()) id else java.util.UUID.randomUUID().toString(),
            name = name,
            type = ProxyType.fromString(typeStr),
            host = host,
            port = port,
            username = user,
            password = decrypt(encPass),
            createdAt = createdAt,
            validityDays = validityDays,
            expiresAt = expiresAt
        )
    }

    fun clearActiveConfig() {
        val emptyConfig = ProxyConfig()
        _currentConfig.value = emptyConfig
        prefs.edit().apply {
            remove(KEY_ACTIVE_ID)
            remove(KEY_ACTIVE_NAME)
            remove(KEY_ACTIVE_TYPE)
            remove(KEY_ACTIVE_HOST)
            remove(KEY_ACTIVE_PORT)
            remove(KEY_ACTIVE_USER)
            remove(KEY_ACTIVE_PASS)
            remove(KEY_ACTIVE_CREATED_AT)
            remove(KEY_ACTIVE_VALIDITY_DAYS)
            remove(KEY_ACTIVE_EXPIRES_AT)
            apply()
        }
    }

    fun saveToSavedList(config: ProxyConfig) {
        val currentList = _savedConfigs.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            currentList[index] = config
        } else {
            currentList.add(0, config)
        }
        _savedConfigs.value = currentList
        persistSavedConfigs(currentList)
    }

    fun deleteFromSavedList(configId: String) {
        val updated = _savedConfigs.value.filterNot { it.id == configId }
        _savedConfigs.value = updated
        persistSavedConfigs(updated)
    }

    fun clearAllSavedConfigs() {
        _savedConfigs.value = emptyList()
        prefs.edit().remove(KEY_SAVED_LIST).apply()
    }

    private fun persistSavedConfigs(list: List<ProxyConfig>) {
        val jsonArray = JSONArray()
        for (cfg in list) {
            val obj = JSONObject().apply {
                put("id", cfg.id)
                put("name", cfg.name)
                put("type", cfg.type.name)
                put("host", cfg.host)
                put("port", cfg.port)
                put("user", cfg.username)
                put("pass", encrypt(cfg.password))
                put("createdAt", cfg.createdAt)
                put("validityDays", cfg.validityDays)
                put("expiresAt", cfg.expiresAt)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_LIST, jsonArray.toString()).apply()
    }

    private fun loadSavedConfigs(): List<ProxyConfig> {
        val raw = prefs.getString(KEY_SAVED_LIST, null) ?: return emptyList()
        val list = mutableListOf<ProxyConfig>()
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val validityDays = obj.optInt("validityDays", 30)
                val defaultExpiresAt = createdAt + (validityDays.toLong() * 24L * 60 * 60 * 1000L)
                val expiresAt = obj.optLong("expiresAt", defaultExpiresAt)
                list.add(
                    ProxyConfig(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", ""),
                        type = ProxyType.fromString(obj.optString("type", "SOCKS5")),
                        host = obj.optString("host", ""),
                        port = obj.optInt("port", 1080),
                        username = obj.optString("user", ""),
                        password = decrypt(obj.optString("pass", "")),
                        createdAt = createdAt,
                        validityDays = validityDays,
                        expiresAt = expiresAt
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun updateSettings(settings: AppSettings) {
        _appSettings.value = settings
        prefs.edit().apply {
            putBoolean(KEY_AUTO_CONNECT, settings.autoConnectOnLaunch)
            putBoolean(KEY_VIBRATION, settings.vibrationEnabled)
            putBoolean(KEY_NOTIFICATION, settings.notificationEnabled)
            putBoolean(KEY_BYPASS_LOCAL, settings.bypassLocalSubnets)
            apply()
        }
    }

    private fun loadSettings(): AppSettings {
        return AppSettings(
            autoConnectOnLaunch = prefs.getBoolean(KEY_AUTO_CONNECT, false),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, true),
            notificationEnabled = prefs.getBoolean(KEY_NOTIFICATION, true),
            bypassLocalSubnets = prefs.getBoolean(KEY_BYPASS_LOCAL, true)
        )
    }

    companion object {
        private const val PREFS_NAME = "owais_proxy_storage"
        private const val KEY_ACTIVE_ID = "active_id"
        private const val KEY_ACTIVE_NAME = "active_name"
        private const val KEY_ACTIVE_TYPE = "active_type"
        private const val KEY_ACTIVE_HOST = "active_host"
        private const val KEY_ACTIVE_PORT = "active_port"
        private const val KEY_ACTIVE_USER = "active_user"
        private const val KEY_ACTIVE_PASS = "active_pass"
        private const val KEY_ACTIVE_CREATED_AT = "active_created_at"
        private const val KEY_ACTIVE_VALIDITY_DAYS = "active_validity_days"
        private const val KEY_ACTIVE_EXPIRES_AT = "active_expires_at"
        private const val KEY_SAVED_LIST = "saved_proxies_json"
        private const val KEY_AUTO_CONNECT = "setting_auto_connect"
        private const val KEY_VIBRATION = "setting_vibration"
        private const val KEY_NOTIFICATION = "setting_notification"
        private const val KEY_BYPASS_LOCAL = "setting_bypass_local"
    }
}
