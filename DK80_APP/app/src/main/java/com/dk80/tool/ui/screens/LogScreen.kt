package com.dk80.tool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk80.tool.ble.BleManager
import com.dk80.tool.ble.BleState
import com.dk80.tool.ui.theme.Primary
import com.dk80.tool.util.LogExporter
import kotlinx.coroutines.launch

@Composable
fun LogScreen(ble: BleManager) {
    val logs by ble.logs.collectAsState()
    val state by ble.state.collectAsState()
    val ready = state == BleState.Ready
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var logOn by remember { mutableStateOf(false) }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                logOn = !logOn
                ble.setLogStream(logOn)
            }, enabled = ready) {
                Text(if (logOn) "停止日志流" else "开启日志流")
            }
            OutlinedButton(onClick = { ble.clearLogs() }) { Text("清空") }
            OutlinedButton(onClick = {
                scope.launch {
                    val uri = LogExporter.export(ctx, logs) ?: return@launch
                    LogExporter.share(ctx, uri)
                }
            }, enabled = logs.isNotEmpty()) { Text("导出分享") }
        }

        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { line ->
                    Text(
                        line,
                        color = Color(0xFFD1FAE5),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
