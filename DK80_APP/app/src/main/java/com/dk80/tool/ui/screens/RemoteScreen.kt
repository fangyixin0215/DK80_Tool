package com.dk80.tool.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk80.tool.ble.BleManager
import com.dk80.tool.ble.BleState
import com.dk80.tool.ui.theme.Accent
import com.dk80.tool.ui.theme.AccentDark
import com.dk80.tool.ui.theme.Primary
import com.dk80.tool.ui.theme.PrimaryDark

// 红外键值（与固件 dk80_mode_ir_cmd 一一对应）
private data class KeyItem(val label: String, val code: Int, val accent: Boolean = false)

private val keys = listOf(
    KeyItem("电源", 0x06, true),
    KeyItem("童锁", 0x05),
    KeyItem("风+", 0x0D),
    KeyItem("风-", 0x0F),
    KeyItem("定时", 0x0A),
    KeyItem("循环", 0x0E),
    KeyItem("预约", 0x0C),
    KeyItem("时+", 0x00),
    KeyItem("时-", 0x04),
    KeyItem("分+", 0x01),
    KeyItem("分-", 0x03),
    KeyItem("时间/确认", 0x02),
    KeyItem("复位/取消", 0x0B),
)

@Composable
fun RemoteScreen(ble: BleManager) {
    val state by ble.state.collectAsState()
    val enabled = state == BleState.Ready

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "蓝牙遥控器",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            if (enabled) "按键实时下发到设备" else "请先连接设备",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 18.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(keys) { k ->
                RemoteKey(k, enabled) { ble.sendKey(k.code) }
            }
        }
    }
}

@Composable
private fun RemoteKey(k: KeyItem, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "keyScale")

    val baseColors = if (k.accent) Accent to AccentDark else Primary to PrimaryDark

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(listOf(baseColors.first, baseColors.second))
            )
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material.ripple.rememberRipple(color = Color.White.copy(alpha = 0.3f)),
                enabled = enabled
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            k.label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
