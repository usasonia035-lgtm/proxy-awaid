package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppSettings
import com.example.ui.theme.OpsAccentCyan
import com.example.ui.theme.OpsCardBorderDark
import com.example.ui.theme.OpsPrimary
import com.example.ui.theme.OpsSecondary
import com.example.ui.theme.OpsSurfaceDark
import com.example.ui.theme.OpsSurfaceVariantDark
import com.example.ui.theme.OpsTextMuted
import com.example.ui.theme.OpsTextPrimary
import com.example.ui.theme.OpsTextSecondary

@Composable
fun SettingsDialog(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onClearSavedProxy: () -> Unit,
    onShowAbout: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpsSurfaceDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(OpsPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = OpsAccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "App Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OpsTextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Auto-connect
                SettingsToggleRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "Auto-connect on launch",
                    subtitle = "Automatically connect to saved proxy when app opens (OFF by default)",
                    checked = settings.autoConnectOnLaunch,
                    onCheckedChange = {
                        onSettingsChanged(settings.copy(autoConnectOnLaunch = it))
                    },
                    testTag = "setting_toggle_auto_connect"
                )

                HorizontalDivider(color = OpsCardBorderDark, thickness = 0.8.dp)

                // Vibration feedback
                SettingsToggleRow(
                    icon = Icons.Default.Vibration,
                    title = "Haptic Vibration",
                    subtitle = "Vibrate on connection start, success, and disconnect",
                    checked = settings.vibrationEnabled,
                    onCheckedChange = {
                        onSettingsChanged(settings.copy(vibrationEnabled = it))
                    },
                    testTag = "setting_toggle_vibration"
                )

                HorizontalDivider(color = OpsCardBorderDark, thickness = 0.8.dp)

                // Persistent notification
                SettingsToggleRow(
                    icon = Icons.Default.Notifications,
                    title = "Connection Notification",
                    subtitle = "Show persistent foreground notification with IP and Stop button",
                    checked = settings.notificationEnabled,
                    onCheckedChange = {
                        onSettingsChanged(settings.copy(notificationEnabled = it))
                    },
                    testTag = "setting_toggle_notification"
                )

                HorizontalDivider(color = OpsCardBorderDark, thickness = 0.8.dp)

                // Clear Saved Proxy
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onClearSavedProxy() }
                        .testTag("setting_clear_saved_proxy"),
                    shape = RoundedCornerShape(12.dp),
                    color = OpsSurfaceVariantDark.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, OpsCardBorderDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Clear Saved Proxy",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Remove current saved proxy inputs",
                                style = MaterialTheme.typography.bodySmall,
                                color = OpsTextMuted
                            )
                        }
                    }
                }

                // About Action
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onShowAbout() }
                        .testTag("setting_about_button"),
                    shape = RoundedCornerShape(12.dp),
                    color = OpsSurfaceVariantDark.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, OpsCardBorderDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = OpsAccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "About Owais Proxy Server",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = OpsTextPrimary
                            )
                            Text(
                                text = "Version 1.0.0 • Architecture & Protocol details",
                                style = MaterialTheme.typography.bodySmall,
                                color = OpsTextMuted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("settings_close_button")
            ) {
                Text("Done", color = OpsAccentCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OpsSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OpsTextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsTextMuted,
                    lineHeight = 14.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = OpsTextPrimary,
                checkedTrackColor = OpsPrimary,
                uncheckedThumbColor = OpsTextMuted,
                uncheckedTrackColor = OpsSurfaceVariantDark
            )
        )
    }
}
