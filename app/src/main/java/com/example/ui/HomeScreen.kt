package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionInfo
import com.example.model.ConnectionState
import com.example.model.ProxyType
import com.example.ui.theme.OpsAccentCyan
import com.example.ui.theme.OpsBackgroundDark
import com.example.ui.theme.OpsCardBorderDark
import com.example.ui.theme.OpsError
import com.example.ui.theme.OpsErrorGlow
import com.example.ui.theme.OpsPrimary
import com.example.ui.theme.OpsPrimaryGlow
import com.example.ui.theme.OpsSecondary
import com.example.ui.theme.OpsSuccess
import com.example.ui.theme.OpsSuccessGlow
import com.example.ui.theme.OpsSurfaceDark
import com.example.ui.theme.OpsSurfaceVariantDark
import com.example.ui.theme.OpsTextMuted
import com.example.ui.theme.OpsTextPrimary
import com.example.ui.theme.OpsTextSecondary
import com.example.ui.theme.OpsWarning
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ProxyViewModel,
    onRequestVpnPermission: (Intent) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionInfo by viewModel.connectionInfo.collectAsState()
    val savedConfigs by viewModel.savedConfigs.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showSavedSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var pendingVpnIntent by remember { mutableStateOf<Intent?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show save message toast/snackbar
    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let { msg ->
            scope.launch {
                snackbarHostState.showSnackbar(msg)
                viewModel.dismissSaveMessage()
            }
        }
    }

    Scaffold(
        containerColor = OpsBackgroundDark,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            OpsBackgroundDark,
                            OpsSurfaceDark.copy(alpha = 0.95f),
                            OpsBackgroundDark
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Header Bar
                TopHeader(
                    savedCount = savedConfigs.size,
                    onOpenSaved = { showSavedSheet = true },
                    onOpenSettings = { showSettingsDialog = true }
                )

                // Connection Status Card
                ConnectionStatusCard(
                    connectionState = connectionState,
                    info = connectionInfo,
                    durationSeconds = uiState.connectedDurationSeconds,
                    onCopyIp = { ip ->
                        copyToClipboard(context, "Proxy Public IP", ip)
                        scope.launch { snackbarHostState.showSnackbar("Copied IP: $ip") }
                    }
                )

                // Expiry Warning Banner (Show notification if proxy expires in 3 days or less)
                val currentConfig = viewModel.getCurrentConfig()
                AnimatedVisibility(
                    visible = currentConfig.host.isNotBlank() && (currentConfig.isExpiringSoon || currentConfig.isExpired),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val isExp = currentConfig.isExpired
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isExp) OpsError.copy(alpha = 0.15f) else Color(0xFFFFB300).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isExp) OpsError.copy(alpha = 0.5f) else Color(0xFFFFB300).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isExp) Icons.Default.ErrorOutline else Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (isExp) OpsErrorGlow else Color(0xFFFFB300),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isExp) "Proxy Expired" else "Proxy Expiring Soon!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExp) OpsErrorGlow else Color(0xFFFFB300)
                                )
                                Text(
                                    text = if (isExp) "This proxy expired on ${currentConfig.formattedExpiryDate}. Please renew or replace it."
                                    else "Attention: This proxy will expire in ${currentConfig.daysRemaining} days (${currentConfig.formattedExpiryDate}).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OpsTextPrimary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                // Error Banners
                AnimatedVisibility(
                    visible = connectionState is ConnectionState.Error,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val errorMsg = (connectionState as? ConnectionState.Error)?.message ?: "Connection error"
                    ErrorBanner(
                        title = "Connection Failed",
                        message = errorMsg,
                        onDismiss = null
                    )
                }

                AnimatedVisibility(
                    visible = uiState.validationError != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    uiState.validationError?.let { err ->
                        ErrorBanner(
                            title = "Configuration Warning",
                            message = err,
                            onDismiss = { viewModel.dismissValidationError() }
                        )
                    }
                }

                // Test Probe Result Banner
                AnimatedVisibility(
                    visible = uiState.testResult != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    uiState.testResult?.let { result ->
                        TestResultBanner(
                            result = result,
                            onDismiss = { viewModel.dismissTestResult() }
                        )
                    }
                }

                // Main Start / Stop Button (Prominent)
                MainActionButton(
                    connectionState = connectionState,
                    onClick = {
                        viewModel.toggleConnection(context) { vpnIntent ->
                            pendingVpnIntent = vpnIntent
                        }
                    }
                )

                // Proxy Configuration Card
                ProxyConfigurationCard(
                    uiState = uiState,
                    isEnabled = connectionState.isDisconnected || connectionState is ConnectionState.Error,
                    onTypeSelected = { viewModel.onTypeChanged(it) },
                    onHostChanged = { viewModel.onHostChanged(it) },
                    onPortChanged = { viewModel.onPortChanged(it) },
                    onUsernameChanged = { viewModel.onUsernameChanged(it) },
                    onPasswordChanged = { viewModel.onPasswordChanged(it) },
                    onTogglePasswordVisibility = { viewModel.togglePasswordVisibility() },
                    onValidityDaysChanged = { viewModel.onValidityDaysChanged(it) },
                    onSaveProxy = { viewModel.saveCurrentProxy() },
                    onTestProxy = { viewModel.testProxyConnection() },
                    onClearProxy = { viewModel.clearConfiguration() }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Dialogs & Sheets
    if (showSavedSheet) {
        SavedProxiesSheet(
            savedList = savedConfigs,
            activeConfigId = uiState.activeSavedConfigId,
            onSelect = { config -> viewModel.loadSavedConfig(config) },
            onDelete = { id -> viewModel.deleteSavedConfig(id) },
            onDismiss = { showSavedSheet = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = appSettings,
            onSettingsChanged = { viewModel.updateSettings(it) },
            onClearSavedProxy = {
                viewModel.clearConfiguration()
                showSettingsDialog = false
            },
            onShowAbout = {
                showSettingsDialog = false
                showAboutDialog = true
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    pendingVpnIntent?.let { vpnIntent ->
        VpnPermissionDialog(
            onConfirm = {
                onRequestVpnPermission(vpnIntent)
                pendingVpnIntent = null
            },
            onDismiss = {
                pendingVpnIntent = null
            }
        )
    }
}

@Composable
private fun TopHeader(
    savedCount: Int,
    onOpenSaved: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Monogram Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(OpsPrimary, OpsAccentCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OPS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF070B16),
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Owais Proxy Server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OpsTextPrimary
                )
                Text(
                    text = "Native VPN & Proxy Tunnel",
                    style = MaterialTheme.typography.labelSmall,
                    color = OpsSecondary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Saved Proxies Button
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenSaved() }
                    .testTag("open_saved_proxies_button"),
                shape = RoundedCornerShape(10.dp),
                color = OpsSurfaceVariantDark,
                border = BorderStroke(1.dp, OpsCardBorderDark)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Saved Proxies",
                        tint = OpsAccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    if (savedCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$savedCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = OpsTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Settings Button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(OpsSurfaceVariantDark)
                    .testTag("open_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = OpsTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    info: ConnectionInfo,
    durationSeconds: Long,
    onCopyIp: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val (statusLabel, statusColor, statusIcon) = when (connectionState) {
        is ConnectionState.Connecting -> Triple(
            connectionState.stage,
            OpsWarning,
            Icons.Default.Refresh
        )
        is ConnectionState.Connected -> Triple(
            "Connected ✓",
            OpsSuccess,
            Icons.Default.CheckCircle
        )
        is ConnectionState.Disconnecting -> Triple(
            "Disconnecting...",
            OpsWarning,
            Icons.Default.Refresh
        )
        is ConnectionState.Error -> Triple(
            "Connection Failed",
            OpsError,
            Icons.Default.Error
        )
        is ConnectionState.Disconnected -> Triple(
            "Disconnected",
            OpsTextMuted,
            Icons.Default.Language
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connection_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = OpsSurfaceDark),
        border = BorderStroke(
            1.2.dp,
            if (connectionState.isConnected) OpsSuccess.copy(alpha = 0.6f) else OpsCardBorderDark
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Status Badge & Proxy Type Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (connectionState.isConnecting) statusColor.copy(alpha = pulseAlpha)
                                else statusColor
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = OpsPrimary.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, OpsPrimary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = info.proxyType.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OpsAccentCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = OpsCardBorderDark, thickness = 0.8.dp)

            // Country & Geolocation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Country",
                        style = MaterialTheme.typography.labelSmall,
                        color = OpsTextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = info.countryDisplay,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OpsTextPrimary
                    )
                }

                if (connectionState.isConnected && info.pingLatencyMs > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = OpsSurfaceVariantDark
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = OpsAccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${info.pingLatencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OpsTextPrimary
                            )
                        }
                    }
                }
            }

            // Public IP Row with Copy Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = OpsSurfaceVariantDark,
                border = BorderStroke(1.dp, OpsCardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Public IP",
                            style = MaterialTheme.typography.labelSmall,
                            color = OpsTextMuted
                        )
                        Text(
                            text = info.publicIp,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (connectionState.isConnected) OpsAccentCyan else OpsTextPrimary
                        )
                    }

                    if (connectionState.isConnected && info.publicIp.isNotBlank() && info.publicIp != "Detecting...") {
                        IconButton(
                            onClick = { onCopyIp(info.publicIp) },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("copy_ip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy IP",
                                tint = OpsAccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Server & Duration Metrics (When Connected)
            if (connectionState.isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricItem(
                        icon = Icons.Default.Timer,
                        label = "Connected Time",
                        value = formatDuration(durationSeconds)
                    )
                    MetricItem(
                        icon = Icons.Default.Dns,
                        label = "Server",
                        value = if (info.host.isNotBlank()) "${info.host}:${info.port}" else "Connected"
                    )
                    MetricItem(
                        icon = Icons.Default.NetworkCheck,
                        label = "Traffic",
                        value = formatBytes(info.bytesTx + info.bytesRx)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OpsTextMuted,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OpsTextMuted,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = OpsTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MainActionButton(
    connectionState: ConnectionState,
    onClick: () -> Unit
) {
    val isConnected = connectionState.isConnected
    val isConnecting = connectionState.isConnecting

    val buttonColor by animateColorAsState(
        targetValue = when {
            isConnected -> OpsError
            isConnecting -> OpsWarning
            else -> OpsPrimary
        },
        label = "buttonColor"
    )

    Button(
        onClick = onClick,
        enabled = !connectionState.isDisconnecting,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .testTag("main_connection_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            disabledContainerColor = buttonColor.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "CONNECTING...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            } else if (isConnected) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "STOP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "START",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun ProxyConfigurationCard(
    uiState: ProxyUiState,
    isEnabled: Boolean,
    onTypeSelected: (ProxyType) -> Unit,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onValidityDaysChanged: (Int) -> Unit,
    onSaveProxy: () -> Unit,
    onTestProxy: () -> Unit,
    onClearProxy: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("proxy_configuration_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = OpsSurfaceDark),
        border = BorderStroke(1.dp, OpsCardBorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Proxy Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OpsTextPrimary
            )

            // Proxy Type Selector
            Column {
                Text(
                    text = "Proxy Type",
                    style = MaterialTheme.typography.labelSmall,
                    color = OpsTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProxyType.entries.forEach { type ->
                        val selected = uiState.selectedType == type
                        FilterChip(
                            selected = selected,
                            onClick = { if (isEnabled) onTypeSelected(type) },
                            label = {
                                Text(
                                    text = type.displayName,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OpsPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = OpsSurfaceVariantDark,
                                labelColor = OpsTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = isEnabled,
                                selected = selected,
                                borderColor = OpsCardBorderDark,
                                selectedBorderColor = OpsAccentCyan
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("proxy_type_${type.name.lowercase(Locale.ROOT)}")
                        )
                    }
                }
            }

            // Proxy Host / IP Field
            OutlinedTextField(
                value = uiState.hostInput,
                onValueChange = onHostChanged,
                enabled = isEnabled,
                label = { Text("Proxy IP / Host") },
                placeholder = { Text("e.g. 198.51.100.24 or proxy.net") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = OpsSecondary
                    )
                },
                trailingIcon = {
                    if (uiState.hostInput.isNotBlank() && isEnabled) {
                        IconButton(onClick = { onHostChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Host",
                                tint = OpsTextMuted
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proxy_host_input")
            )

            // Proxy Port Field with quick preset chips
            Column {
                OutlinedTextField(
                    value = uiState.portInput,
                    onValueChange = onPortChanged,
                    enabled = isEnabled,
                    label = { Text("Proxy Port") },
                    placeholder = { Text("1080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = textFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_port_input")
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val portPresets = listOf(1080, 8080, 443, 3128, 80)
                    portPresets.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (uiState.portInput == preset.toString()) OpsPrimary.copy(alpha = 0.25f) else OpsSurfaceVariantDark,
                            border = BorderStroke(
                                0.8.dp,
                                if (uiState.portInput == preset.toString()) OpsAccentCyan else OpsCardBorderDark
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = isEnabled) { onPortChanged(preset.toString()) }
                        ) {
                            Text(
                                text = ":$preset",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.portInput == preset.toString()) OpsAccentCyan else OpsTextMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Username Field
            OutlinedTextField(
                value = uiState.usernameInput,
                onValueChange = onUsernameChanged,
                enabled = isEnabled,
                label = { Text("Username") },
                placeholder = { Text("Optional if proxy has no auth") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = OpsSecondary
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proxy_username_input")
            )

            // Secure Masked Password Field
            OutlinedTextField(
                value = uiState.passwordInput,
                onValueChange = onPasswordChanged,
                enabled = isEnabled,
                label = { Text("Password") },
                placeholder = { Text("Optional secure password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = OpsSecondary
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (uiState.isPasswordVisible) "Hide password" else "Show password",
                            tint = OpsTextMuted
                        )
                    }
                },
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proxy_password_input")
            )

            // Validity Duration Selector
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Proxy Validity / Expiry",
                        style = MaterialTheme.typography.labelSmall,
                        color = OpsTextSecondary
                    )
                    Text(
                        text = "${uiState.validityDays} Days Validity",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OpsAccentCyan
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val validityOptions = listOf(7, 15, 30, 60, 90)
                    validityOptions.forEach { days ->
                        val selected = uiState.validityDays == days
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (selected) OpsPrimary.copy(alpha = 0.25f) else OpsSurfaceVariantDark,
                            border = BorderStroke(
                                0.8.dp,
                                if (selected) OpsAccentCyan else OpsCardBorderDark
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = isEnabled) { onValidityDaysChanged(days) }
                        ) {
                            Text(
                                text = "${days}d",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) OpsAccentCyan else OpsTextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Button Action Row (Save, Test, Clear)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SAVE PROXY Button
                Button(
                    onClick = onSaveProxy,
                    enabled = isEnabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OpsPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_proxy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SAVE PROXY", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }

                // TEST PROXY Probe Button
                OutlinedButton(
                    onClick = onTestProxy,
                    enabled = isEnabled && !uiState.isTesting,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, OpsAccentCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OpsAccentCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_proxy_button")
                ) {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(
                            color = OpsAccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TESTING...", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TEST PROXY", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Clear Button
            TextButton(
                onClick = onClearProxy,
                enabled = isEnabled,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("clear_proxy_button")
            ) {
                Text(
                    text = "Clear Configuration",
                    color = OpsTextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    title: String,
    message: String,
    onDismiss: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = OpsError.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, OpsError.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = OpsErrorGlow,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OpsErrorGlow
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsTextPrimary,
                    lineHeight = 15.sp
                )
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = OpsTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TestResultBanner(
    result: com.example.proxy.ProbeResult,
    onDismiss: () -> Unit
) {
    val isSuccess = result.isSuccess
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSuccess) OpsSuccess.copy(alpha = 0.15f) else OpsError.copy(alpha = 0.15f),
        border = BorderStroke(
            1.dp,
            if (isSuccess) OpsSuccess.copy(alpha = 0.5f) else OpsError.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isSuccess) OpsSuccessGlow else OpsErrorGlow,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSuccess) "Proxy Reachable (${result.latencyMs}ms)" else "Probe Failed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSuccess) OpsSuccessGlow else OpsErrorGlow
                )
                Text(
                    text = if (isSuccess) "Proxy responded to handshake successfully. IP: ${result.resolvedIp ?: "OK"}"
                    else (result.errorMessage ?: "Proxy unreachable"),
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsTextPrimary,
                    lineHeight = 15.sp
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = OpsTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OpsAccentCyan,
    unfocusedBorderColor = OpsCardBorderDark,
    focusedLabelColor = OpsAccentCyan,
    unfocusedLabelColor = OpsTextSecondary,
    focusedTextColor = OpsTextPrimary,
    unfocusedTextColor = OpsTextPrimary,
    focusedContainerColor = OpsSurfaceVariantDark,
    unfocusedContainerColor = OpsSurfaceVariantDark,
    cursorColor = OpsAccentCyan
)

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.ROOT, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", mins, secs)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
