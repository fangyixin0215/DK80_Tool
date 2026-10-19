package com.dk80.tool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk80.tool.ble.BleManager
import com.dk80.tool.ble.BleState
import com.dk80.tool.ui.theme.Bad
import com.dk80.tool.util.LogExporter
import kotlinx.coroutines.launch

// 日志终端配色
private val TermBg = Color(0xFF0B1020)
private val TermDefault = Color(0xFFCBD5E1)
private val TermInfo = Color(0xFF38BDF8)
private val TermOk = Color(0xFF4ADE80)
private val TermWarn = Color(0xFFFBBF24)
private val TermErr = Color(0xFFF87171)
private val TermTime = Color(0xFF64748B)

@Composable
fun LogScreen(ble: BleManager) {
    val logs by ble.logs.collectAsState()
    val state by ble.state.collectAsState()
    val ready = state == BleState.Ready
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 日志开关状态在 BleManager 持有：切到其他页再回来不会丢（仍显示"停止"）
    val logOn by ble.logStreamOn.collectAsState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 控制栏
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { ble.setLogStream(!logOn) },
                enabled = ready,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (logOn) Bad else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(if (logOn) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (logOn) "停止" else "开启日志")
            }
            OutlinedButton(
                onClick = { ble.clearLogs() },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("清空")
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val uri = LogExporter.export(ctx, logs) ?: return@launch
                        LogExporter.share(ctx, uri)
                    }
                },
                enabled = logs.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导出")
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${logs.size} 行",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
            )
        }

        Spacer(Modifier.height(10.dp))

        // 终端窗口
        Card(
            Modifier.fillMaxSize(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = TermBg)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            if (ready) "点击「开启日志」开始接收设备日志..." else "请先连接设备",
                            color = TermTime, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                        )
                    }
                }
                items(logs) { line ->
                    LogLine(line)
                }
            }
        }
    }
}

@Composable
private fun LogLine(line: String) {
    val text = line.ifEmpty { " " }
    // 按等级着色
    val color = when {
        text.contains("ERROR", ignoreCase = true) || text.contains("ERR", ignoreCase = true) -> TermErr
        text.contains("WARN", ignoreCase = true) -> TermWarn
        text.contains("INFO", ignoreCase = true) -> TermInfo
        text.contains("OK", ignoreCase = true) || text.contains("ACK", ignoreCase = true) || text.contains("SUCCESS", ignoreCase = true) -> TermOk
        else -> TermDefault
    }
    Text(
        text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.5.sp,
        lineHeight = 15.sp
    )
}
