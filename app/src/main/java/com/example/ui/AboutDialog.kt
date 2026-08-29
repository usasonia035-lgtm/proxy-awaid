package com.example.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OpsAccentCyan
import com.example.ui.theme.OpsCardBorderDark
import com.example.ui.theme.OpsPrimary
import com.example.ui.theme.OpsSurfaceDark
import com.example.ui.theme.OpsSurfaceVariantDark
import com.example.ui.theme.OpsTextMuted
import com.example.ui.theme.OpsTextPrimary
import com.example.ui.theme.OpsTextSecondary

@Composable
fun AboutDialog(
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(OpsPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = OpsAccentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Owais Proxy Server",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OpsTextPrimary
                    )
                    Text(
                        text = "Secure proxy connection utility",
                        style = MaterialTheme.typography.bodySmall,
                        color = OpsAccentCyan
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OpsSurfaceVariantDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, OpsCardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Application Version", style = MaterialTheme.typography.bodySmall, color = OpsTextSecondary)
                            Text("1.0.0 (Build 1)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = OpsTextPrimary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Architecture", style = MaterialTheme.typography.bodySmall, color = OpsTextSecondary)
                            Text("VpnService + Native Proxy", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = OpsTextPrimary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Min Android SDK", style = MaterialTheme.typography.bodySmall, color = OpsTextSecondary)
                            Text("API 29 (Android 10+)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = OpsTextPrimary)
                        }
                    }
                }

                Text(
                    text = "Supported Protocols",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = OpsTextPrimary
                )

                ProtocolItem(
                    protocol = "SOCKS5 (RFC 1928 / RFC 1929)",
                    desc = "Full TCP proxy tunneling with optional RFC 1929 username/password authentication."
                )

                ProtocolItem(
                    protocol = "HTTP CONNECT Proxy",
                    desc = "Standard HTTP CONNECT proxy tunneling with Basic proxy authentication."
                )

                ProtocolItem(
                    protocol = "HTTPS Proxy (TLS-wrapped)",
                    desc = "Encrypted TLS transport tunnel to the proxy server with Basic authentication."
                )

                HorizontalDivider(color = OpsCardBorderDark, thickness = 0.8.dp)

                Text(
                    text = "Security & Network Disclosure",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = OpsTextPrimary
                )

                Text(
                    text = "Owais Proxy Server creates an on-device VPN tunnel using Android VpnService to forward device TCP and DNS traffic through your chosen proxy server. Encryption is determined by the proxy protocol and end-to-end TLS (HTTPS/TLS) of target destinations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsTextSecondary,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("about_dialog_close")
            ) {
                Text("Close", color = OpsAccentCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ProtocolItem(protocol: String, desc: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = protocol,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = OpsAccentCyan
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = OpsTextMuted,
            lineHeight = 14.sp
        )
    }
}
