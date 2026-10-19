package com.dk80.tool.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk80.tool.ble.BleManager
import com.dk80.tool.ble.BleState

/* 按键渐变配色：按功能分组，一眼区分 */
private val C_PWR   = Color(0xFFF97316) to Color(0xFFDC2626)   // 电源：橙红
private val C_FAN   = Color(0xFF3B82F6) to Color(0xFF1D4ED8)   // 风速：蓝
private val C_LOOP  = Color(0xFF0EA5E9) to Color(0xFF0369A1)   // 循环：青
private val C_TIMER = Color(0xFF8B5CF6) to Color(0xFF6D28D9)   // 定时/预约：紫
private val C_LOCK  = Color(0xFFF59E0B) to Color(0xFFB45309)   // 童锁：琥珀
private val C_ADJ_H = Color(0xFF64748B) to Color(0xFF334155)   // 时调整：灰蓝
private val C_ADJ_M = Color(0xFF94A3B8) to Color(0xFF475569)   // 分调整：浅灰
private val C_OK    = Color(0xFF10B981) to Color(0xFF047857)   // 确认：绿
private val C_NO    = Color(0xFFEF4444) to Color(0xFFB91C1C)   // 取消：红

@Composable
fun RemoteScreen(ble: BleManager) {
    val state by ble.state.collectAsState()
    val enabled = state == BleState.Ready

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "蓝牙遥控器",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            if (enabled) "按键实时下发到设备" else "请先连接设备",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        /* 电源：大圆形主按钮 */
        PowerKey(enabled) { ble.sendKey(0x06) }

        SectionLabel("风速调节")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WideKey("风 -", Icons.Outlined.Air, C_FAN, enabled, Modifier.weight(1f)) { ble.sendKey(0x0F) }
            WideKey("风 +", Icons.Outlined.Air, C_FAN, enabled, Modifier.weight(1f)) { ble.sendKey(0x0D) }
        }

        SectionLabel("模式 · 定时")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TileKey("童锁", Icons.Outlined.Lock, C_LOCK, enabled, Modifier.weight(1f)) { ble.sendKey(0x05) }
            TileKey("循环", Icons.Outlined.Autorenew, C_LOOP, enabled, Modifier.weight(1f)) { ble.sendKey(0x0E) }
            TileKey("定时", Icons.Outlined.Schedule, C_TIMER, enabled, Modifier.weight(1f)) { ble.sendKey(0x0A) }
            TileKey("预约", Icons.Outlined.EditCalendar, C_TIMER, enabled, Modifier.weight(1f)) { ble.sendKey(0x0C) }
        }

        SectionLabel("时间校正")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TileKey("时+", Icons.Outlined.KeyboardArrowUp, C_ADJ_H, enabled, Modifier.weight(1f)) { ble.sendKey(0x00) }
            TileKey("时-", Icons.Outlined.KeyboardArrowDown, C_ADJ_H, enabled, Modifier.weight(1f)) { ble.sendKey(0x04) }
            TileKey("分+", Icons.Outlined.KeyboardArrowUp, C_ADJ_M, enabled, Modifier.weight(1f)) { ble.sendKey(0x01) }
            TileKey("分-", Icons.Outlined.KeyboardArrowDown, C_ADJ_M, enabled, Modifier.weight(1f)) { ble.sendKey(0x03) }
        }

        SectionLabel("操作")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WideKey("复位 / 取消", Icons.Outlined.RestartAlt, C_NO, enabled, Modifier.weight(1f)) { ble.sendKey(0x0B) }
            WideKey("时间 / 确认", Icons.Outlined.CheckCircle, C_OK, enabled, Modifier.weight(1f)) { ble.sendKey(0x02) }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun PowerKey(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "pwrScale")

    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .size(92.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(C_PWR.first, C_PWR.second)))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material.ripple.rememberRipple(color = Color.White.copy(alpha = 0.3f)),
                enabled = enabled
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.PowerSettingsNew,
            contentDescription = "电源",
            tint = Color.White,
            modifier = Modifier.size(38.dp)
        )
    }
}

@Composable
private fun WideKey(
    label: String,
    icon: ImageVector,
    colors: Pair<Color, Color>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "keyScale")

    Row(
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(colors.first, colors.second)))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material.ripple.rememberRipple(color = Color.White.copy(alpha = 0.3f)),
                enabled = enabled
            ) { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TileKey(
    label: String,
    icon: ImageVector,
    colors: Pair<Color, Color>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "tileScale")

    Column(
        modifier = modifier
            .height(74.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(colors.first, colors.second)))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material.ripple.rememberRipple(color = Color.White.copy(alpha = 0.3f)),
                enabled = enabled
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
