package com.aistudio.pocketpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aistudio.pocketpad.model.ConnectionState
import com.aistudio.pocketpad.ui.theme.ForzaCyan
import com.aistudio.pocketpad.ui.theme.ForzaGreen
import com.aistudio.pocketpad.ui.theme.ForzaMagenta
import com.aistudio.pocketpad.ui.theme.ForzaYellow
import com.aistudio.pocketpad.ui.theme.SurfaceDark
import com.aistudio.pocketpad.ui.theme.TextMuted
import com.aistudio.pocketpad.ui.theme.TextWhite

@Composable
fun ConnectionDialog(
    initialIp: String,
    initialPort: Int,
    connectionState: ConnectionState,
    onConnect: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onScanQrClick: () -> Unit
) {
    var ipText by remember { mutableStateOf(initialIp) }
    var portText by remember { mutableStateOf(initialPort.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, ForzaCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .testTag("connection_dialog"),
            color = SurfaceDark
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🌐",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = "Connect to PocketPad Server",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = TextWhite
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status info
                val isConnected = connectionState == ConnectionState.CONNECTED_WIFI || connectionState == ConnectionState.CONNECTED_USB
                val isConnecting = connectionState == ConnectionState.CONNECTING

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isConnected) ForzaGreen.copy(alpha = 0.15f)
                            else if (isConnecting) ForzaYellow.copy(alpha = 0.15f)
                            else Color(0xFF131A29)
                        )
                        .border(
                            1.dp,
                            if (isConnected) ForzaGreen else if (isConnecting) ForzaYellow else Color(0xFF243248),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED_USB -> "⚡ Connected via USB Cable (0.2ms Latency)"
                            ConnectionState.CONNECTED_WIFI -> "📶 Connected via 5GHz Wi-Fi (Voice QoS)"
                            ConnectionState.CONNECTING -> "⏳ Connecting to $ipText:$portText..."
                            ConnectionState.DISCONNECTED -> "● Server Offline. Enter your PC's IP address below:"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) ForzaGreen else if (isConnecting) ForzaYellow else TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // IP Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ipText,
                        onValueChange = { ipText = it },
                        label = { Text("PC Server IP Address", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_server_ip"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForzaCyan,
                            unfocusedBorderColor = Color(0xFF2C3E5A),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { onScanQrClick() },
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E5A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📷 QR", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Port Field
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("WebSocket Port (Default: 8765 / 8766)", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_server_port"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForzaCyan,
                        unfocusedBorderColor = Color(0xFF2C3E5A),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick presets
                Text(text = "Quick IP Presets:", fontSize = 9.sp, color = TextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickIpPill(label = "Emulator (10.0.2.2)") { ipText = "10.0.2.2" }
                    QuickIpPill(label = "USB (127.0.0.1)") { ipText = "127.0.0.1" }
                    QuickIpPill(label = "Localhost") { ipText = "localhost" }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isConnected || isConnecting) {
                        Button(
                            onClick = onDisconnect,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_disconnect"),
                            colors = ButtonDefaults.buttonColors(containerColor = ForzaMagenta),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Disconnect", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = {
                                val port = portText.toIntOrNull() ?: 8765
                                onConnect(ipText.trim(), port)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_connect"),
                            colors = ButtonDefaults.buttonColors(containerColor = ForzaCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Connect ➔", fontWeight = FontWeight.Black, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickIpPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF172033))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(text = label, fontSize = 9.sp, color = ForzaCyan, fontWeight = FontWeight.Bold)
    }
}
