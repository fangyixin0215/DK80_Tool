package com.dk80.tool.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk80.tool.ble.BleState
import com.dk80.tool.ui.theme.Bad
import com.dk80.tool.ui.theme.Good
import com.dk80.tool.ui.theme.Info

@Composable
fun ConnectionBar(
    state: BleState,
    deviceName: String,
    onScan: () -> Unit,
    onDisconnect: () -> Unit
) {
    val connected = state == BleState.Connected || state == BleState.Ready
    val scanning = state == BleState.Scanning || state == BleState.Connecting

    // 状态配色
    val (barColor, statusText, statusColor) = when (state) {
        BleState.Scanning -> Triple(Info, "正在扫描...", Color.White)
        BleState.Connecting -> Triple(Info, "连接中...", Color.White)
        BleState.Connected -> Triple(Good, "已连接，握手中...", Color.White)
        BleState.Ready -> Triple(Good, "已就绪", Color.White)
        BleState.Failed -> Triple(Bad, "连接失败", Color.White)
        else -> Triple(Color(0xFF94A3B8), "未连接", Color.White)
    }

    // 扫描/连接中呼吸动画
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(barColor, barColor.copy(alpha = 0.82f))
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Icon(
                imageVector = when {
                    connected -> Icons.Filled.Bluetooth
                    scanning -> Icons.Filled.BluetoothSearching
                    else -> Icons.Filled.BluetoothDisabled
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
            if (scanning) {
                Box(
                    Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = pulse))
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (deviceName.isNotEmpty()) deviceName else "DK80 设备",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp
            )
            Text(
                text = statusText,
                color = statusColor.copy(alpha = 0.9f), fontSize = 12.sp
            )
        }
        if (connected) {
            TextButton(onClick = onDisconnect) {
                Text("断开", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = onScan,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text("扫描连接", color = barColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}
