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
    val rssi by ble.rssi.collectAsState()
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

        // 传感器卡片：气体 / 温度 / 湿度（PM10 硬件无此数据，不展示）
        val isO3 = info.gas == "O3"
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SensorCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Air,
                label = if (isO3) "臭氧 O₃" else "氨气 NH₃",
                // 实测标定：两气体模组原始值均为 0.01ppm 单位
                value = if (status.gasInvalid) "—" else "%.2f".format(status.gasRaw / 100f),
                unit = "ppm"
            )
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
            StatusRow(
                if (isO3) "O₃ 传感器" else "NH₃ 传感器",
                if (status.gasOnline) "已连接" else "未连接",
                icon = Icons.Outlined.Air,
                valueColor = if (status.gasOnline) Good else Bad
            )
            Divider()
            /* 工作模式 + 档位 + 已运行/剩余（固件 CHAR1 byte16..22） */
            val modeName = when (status.mode) { 1 -> "定时"; 2 -> "循环"; 3 -> "预约"; else -> "待机" }
            val timingMins = intArrayOf(30, 60, 120, 180, 240)
            val modeDetail = when {
                status.mode == 1 && status.modeGroup in 1..5 -> "${timingMins[status.modeGroup - 1]}分钟档"
                status.mode == 3 && status.modeGroup in 1..6 -> "第${status.modeGroup}组"
                status.modeGroup > 0 -> "${status.modeGroup}档"
                else -> ""
            }
            StatusRow(
                "工作模式",
                if (modeDetail.isEmpty()) modeName else "$modeName · $modeDetail",
                accent = status.working
            )
            Divider()
            StatusRow("已运行", if (status.working) formatHMS(status.runSec) else "—")
            Divider()
            StatusRow(
                "剩余时间",
                when {
                    !status.working -> "—"
                    status.remainSec > 0 -> formatHMS(status.remainSec)
                    else -> "—"
                },
                accent = status.working
            )
            Divider()
            StatusRow("风速档位", "${status.fanSpeed} 档", icon = Icons.Outlined.WindPower)
            Divider()
            StatusRow("等离子杀菌", if (status.uvOn) "开启" else "关闭", accent = status.uvOn)
            Divider()
            GasTypeRow(current = info.gas, enabled = ready) { isO3 -> ble.setGasType(isO3) }
        }

        // 4G 信号（CSQ rssi -> dBm = -113 + 2*rssi）与蓝牙信号（RSSI）分档，
        // 档位口径对齐安卓手机信号格习惯；颜色：满格绿→中黄→差红
        val csqDbm = if (status.csq in 0..31) -113 + 2 * status.csq else null
        val csqText = when {
            csqDbm == null -> "—"
            else -> "$csqDbm dBm (CSQ ${status.csq})"
        }
        val csqLevel = when {
            csqDbm == null -> 0
            csqDbm >= -73  -> 4
            csqDbm >= -85  -> 3
            csqDbm >= -95  -> 2
            csqDbm >= -105 -> 1
            else           -> 0
        }
        val rssiText = if (rssi != Int.MIN_VALUE) "$rssi dBm" else "—"
        val rssiLevel = when {
            rssi == Int.MIN_VALUE -> 0
            rssi >= -55           -> 4
            rssi >= -65           -> 3
            rssi >= -75           -> 2
            rssi >= -85           -> 1
            else                  -> 0
        }
        // 信号格颜色：4 格绿 / 3 格绿 / 2 格黄 / 1 格红 / 0 格灰
        @Composable
        fun signalColor(level: Int): Color = when (level) {
            4 -> Color(0xFF10B981)   // 绿
            3 -> Color(0xFF10B981)   // 绿
            2 -> Color(0xFFF59E0B)   // 黄
            1 -> Color(0xFFEF4444)   // 红
            else -> MaterialTheme.colorScheme.outlineVariant
        }

        // 设备身份
        SectionCard(title = "设备身份") {
            InfoRow("IMEI", info.imei)
            Divider()
            InfoRow("ICCID", info.iccid)
            Divider()
            InfoRow("IMSI", info.imsi)
            Divider()
            InfoRow(
                "SIM 卡",
                if (status.simReady) "已插入" else "未插入",
                valueColor = if (status.simReady) Good else Bad
            )
            Divider()
            InfoRow("MAC", info.mac)
            Divider()
            SignalRow("4G 信号", csqLevel, csqText, signalColor(csqLevel))
            Divider()
            SignalRow("蓝牙信号", rssiLevel, rssiText, signalColor(rssiLevel))
            Divider()
            InfoRow("固件版本", info.fw)
            Divider()
            InfoRow("硬件版本", info.hw)
            Divider()
            InfoRow("编译时间", info.build)
        }

        Button(
            onClick = { ble.requestInfo(); ble.requestStatus() }, enabled = ready,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("刷新")
        }
        Spacer(Modifier.height(4.dp))
    }
}

/* 秒 -> "H小时M分" / "M分S秒"，供已运行/剩余时间展示 */
private fun formatHMS(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "${h}小时${m}分" else "${m}分${s}秒"
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
            /* 数值与单位竖排：一行三列后卡片变窄，"174.47" 这种 6 位数值 24sp 会换行断字 */
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun StatusRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    accent: Boolean = false,
    valueColor: Color? = null
) {
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
            color = valueColor ?: if (accent) Good else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value.ifEmpty { "—" },
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

/* 信号行：信号格图标 + dBm 文本；barColor 为信号格主色（绿/黄/红） */
@Composable
private fun SignalRow(label: String, level: Int, text: String, barColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
        SignalBars(level, barColor)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/* 手机信号格：4 根递增高条，level 0..4 高亮前 level 根；
 * 高亮条用传入的 barColor（绿/黄/红，随信号强度变化），未高亮条用灰色 */
@Composable
private fun SignalBars(level: Int, barColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0..3) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((6 + i * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (i < level) barColor
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

/* 气体检测类型切换：NH3 / O3 二选一，发送 GAS: 命令（固件落 flash 并即时刷新屏显） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GasTypeRow(current: String, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    val isO3 = current.equals("O3", ignoreCase = true)
    val hasValue = current.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("气体检测类型", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            /* 深色模式下默认 FilterChip 选中色（secondaryContainer）发灰暗淡，
             * 显式指定：选中=绿底白字，未选中=暗底描边，两种主题下一致 */
            val chipColors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = Good,
                selectedLabelColor = Color.White
            )
            FilterChip(
                selected = hasValue && !isO3,
                onClick = { onSelect(false) },
                enabled = enabled,
                label = { Text("NH₃ 氨气", fontSize = 12.sp) },
                colors = chipColors
            )
            FilterChip(
                selected = isO3,
                onClick = { onSelect(true) },
                enabled = enabled,
                label = { Text("O₃ 臭氧", fontSize = 12.sp) },
                colors = chipColors
            )
        }
    }
}
