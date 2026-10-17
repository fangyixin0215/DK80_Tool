package com.dk80.tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk80.tool.ble.BleState
import com.dk80.tool.ui.theme.Primary

@Composable
fun ConnectionBar(
    state: BleState,
    deviceName: String,
    onScan: () -> Unit,
    onDisconnect: () -> Unit
) {
    val connected = state == BleState.Connected || state == BleState.Ready
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (connected) Primary else Color(0xFF9CA3AF))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (connected) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
            contentDescription = null, tint = Color.White
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (deviceName.isNotEmpty()) deviceName else "DK80 设备",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
            )
            Text(
                text = when (state) {
                    BleState.Scanning -> "正在扫描..."
                    BleState.Connecting -> "连接中..."
                    BleState.Connected -> "已连接，握手中..."
                    BleState.Ready -> "已就绪"
                    BleState.Failed -> "连接失败"
                    else -> "未连接"
                },
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
            )
        }
        if (connected) {
            TextButton(onClick = onDisconnect) {
                Text("断开", color = Color.White)
            }
        } else {
            Button(onClick = onScan, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                Text("扫描连接", color = Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
