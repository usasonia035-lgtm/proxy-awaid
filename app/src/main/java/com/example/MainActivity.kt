package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.storage.ProxyRepository
import com.example.ui.HomeScreen
import com.example.ui.ProxyViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.HapticFeedbackManager

class MainActivity : ComponentActivity() {

    private lateinit var repository: ProxyRepository
    private lateinit var hapticManager: HapticFeedbackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = ProxyRepository(applicationContext)
        hapticManager = HapticFeedbackManager(applicationContext)

        // Request POST_NOTIFICATIONS on Android 13+ (Tiramisu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            MyApplicationTheme(darkTheme = true) {
                val proxyViewModel: ProxyViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ProxyViewModel(repository, hapticManager) as T
                        }
                    }
                )

                // VPN permission launcher
                val vpnLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        proxyViewModel.onVpnPermissionGranted(this@MainActivity)
                    }
                }

                HomeScreen(
                    viewModel = proxyViewModel,
                    onRequestVpnPermission = { intent ->
                        vpnLauncher.launch(intent)
                    }
                )
            }
        }
    }
}
