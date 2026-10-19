package com.dk80.tool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WindPower
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk80.tool.ble.BleManager
import com.dk80.tool.ble.BleState
import com.dk80.tool.ui.theme.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun DashboardScreen(ble: BleManager) {
    val status by ble.status.collectAsState()
    val info by ble.info.collectAsState()
    val state by ble.state.collectAsState()
    val ready = state == BleState.Ready

    LaunchedEffect(ready) {
        if (ready) ble.requestInfo()
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // PM2.5 主卡片：大数字 + 空气质量等级
        Pm25HeroCard(pm25 = status.pm25)

        // 传感器网格
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SensorCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Air,
                label = if (info.gas == "O3") "臭氧 O₃" else "氨气 NH₃",
                value = "${status.ozonePpb}",
                unit = "ppb"
            )
            SensorCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Air,
                label = "PM10",
                value = "${status.pm10}",
                unit = "µg/m³"
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SensorCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.DeviceThermostat,
                label = "温度",
                value = "%.1f".format(status.temperature),
                unit = "℃"
            )
            SensorCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.WaterDrop,
                label = "湿度",
                value = "%.1f".format(status.humidity),
                unit = "%"
            )
        }

        // 运行状态
        SectionCard(title = "运行状态") {
            StatusRow("风速档位", "${status.fanSpeed} 档", icon = Icons.Outlined.WindPower)
            Divider()
            StatusRow("UV 杀菌", if (status.uvOn) "开启" else "关闭", accent = status.uvOn)
            Divider()
            StatusRow("臭氧发生器", if (status.ozoneGenOn) "开启" else "关闭", accent = status.ozoneGenOn)
        }

        // 设备身份
        SectionCard(title = "设备身份") {
            InfoRow("IMEI", info.imei)
            Divider()
            InfoRow("ICCID", info.iccid)
            Divider()
            InfoRow("IMSI", info.imsi)
            Divider()
            InfoRow("MAC", info.mac)
            Divider()
            InfoRow("固件版本", info.fw)
            Divider()
            InfoRow("硬件版本", info.hw)
            Divider()
            InfoRow("编译时间", info.build)
            Divider()
            InfoRow("气体类型", info.gas.ifEmpty { "—" })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { ble.requestInfo() }, enabled = ready,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("刷新信息")
            }
            OutlinedButton(
                onClick = { ble.requestStatus() }, enabled = ready,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) { Text("刷新状态") }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---- PM2.5 主卡片 ----
@Composable
private fun Pm25HeroCard(pm25: Int) {
    val (level, color, bgBrush) = when {
        pm25 < 35 -> Triple("优", Good, Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF10B981))))
        pm25 < 75 -> Triple("良", Warn, Brush.horizontalGradient(listOf(Color(0xFFD97706), Color(0xFFF59E0B))))
        else -> Triple("差", Bad, Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFEF4444))))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(bgBrush)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PM2.5", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$pm25", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text("µg/m³", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(level, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---- 传感器小卡片 ----
@Composable
private fun SensorCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    unit: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(3.dp))
                Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

// ---- 分组卡片 ----
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, icon: ImageVector? = null, accent: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) Good else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value.ifEmpty { "—" },
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
