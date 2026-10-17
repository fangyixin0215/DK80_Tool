package com.dk80.tool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk80.tool.ble.BleManager
import com.dk80.tool.ui.theme.Accent
import com.dk80.tool.ui.theme.Primary

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
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "蓝牙遥控器",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(keys) { k ->
                Button(
                    onClick = { ble.sendKey(k.code) },
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(CircleShape),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (k.accent) Accent else Primary,
                        contentColor = Color.White
                    )
                ) {
                    Text(k.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
