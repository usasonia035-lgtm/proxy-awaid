package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.AppSettings
import com.example.model.ProxyConfig
import com.example.model.ProxyType
import com.example.storage.ProxyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Owais Proxy Server", appName)
    }

    @Test
    fun `test ProxyRepository encrypted save and load active config`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProxyRepository(context)

        val testConfig = ProxyConfig(
            id = "test-proxy-001",
            name = "Test SOCKS5 Server",
            type = ProxyType.SOCKS5,
            host = "proxy.owais.net",
            port = 1080,
            username = "owais_user",
            password = "SecretPassword@123"
        )

        repository.saveActiveConfig(testConfig)
        val loaded = repository.loadActiveConfig()

        assertEquals("test-proxy-001", loaded.id)
        assertEquals("Test SOCKS5 Server", loaded.name)
        assertEquals(ProxyType.SOCKS5, loaded.type)
        assertEquals("proxy.owais.net", loaded.host)
        assertEquals(1080, loaded.port)
        assertEquals("owais_user", loaded.username)
        // Verify decrypted password matches original plain password
        assertEquals("SecretPassword@123", loaded.password)
    }

    @Test
    fun `test ProxyRepository saved list operations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProxyRepository(context)

        val config1 = ProxyConfig(
            id = "cfg-1",
            name = "HTTP Proxy",
            type = ProxyType.HTTP,
            host = "1.2.3.4",
            port = 8080,
            username = "user1",
            password = "pass1"
        )

        val config2 = ProxyConfig(
            id = "cfg-2",
            name = "HTTPS Proxy",
            type = ProxyType.HTTPS,
            host = "5.6.7.8",
            port = 443,
            username = "user2",
            password = "pass2"
        )

        repository.saveToSavedList(config1)
        repository.saveToSavedList(config2)

        val savedList = repository.savedConfigs.value
        assertTrue(savedList.any { it.id == "cfg-1" })
        assertTrue(savedList.any { it.id == "cfg-2" })

        repository.deleteFromSavedList("cfg-1")
        val updatedList = repository.savedConfigs.value
        assertTrue(updatedList.none { it.id == "cfg-1" })
        assertTrue(updatedList.any { it.id == "cfg-2" })
    }

    @Test
    fun `test AppSettings persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProxyRepository(context)

        val settings = AppSettings(
            autoConnectOnLaunch = true,
            vibrationEnabled = false,
            notificationEnabled = true,
            bypassLocalSubnets = false
        )

        repository.updateSettings(settings)
        val loaded = repository.appSettings.value

        assertEquals(true, loaded.autoConnectOnLaunch)
        assertEquals(false, loaded.vibrationEnabled)
        assertEquals(true, loaded.notificationEnabled)
        assertEquals(false, loaded.bypassLocalSubnets)
    }
}
