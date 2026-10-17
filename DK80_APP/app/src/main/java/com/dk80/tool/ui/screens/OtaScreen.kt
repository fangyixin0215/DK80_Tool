package com.dk80.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dk80.tool.ble.BleManager
import com.dk80.tool.ble.BleState
import com.dk80.tool.ui.theme.Primary
import kotlinx.coroutines.launch

@Composable
fun OtaScreen(ble: BleManager) {
    val state by ble.state.collectAsState()
    val progress by ble.otaProgress.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ready = state == BleState.Ready
    var msg by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
        msg = uri?.let { "已选择固件: ${it.lastPathSegment}" } ?: "未选择文件"
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("蓝牙 OTA 升级", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Text(
            "流程：1) 选择固件 bin 文件；2) 点击开始，App 通过 OTA 服务（RX 特征）按 MTU 分片写入；3) 设备端 ota_service 负责校验与刷写。",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedButton(onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) }) {
            Text("选择固件文件")
        }
        Text(msg)

        Button(
            onClick = {
                scope.launch {
                    val uri = selectedUri ?: return@launch
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) { msg = "读取文件失败"; return@launch }
                    ble.startOta()
                    ble.sendOtaData(bytes) { ok ->
                        msg = if (ok) "OTA 数据发送完成" else "OTA 失败"
                    }
                }
            },
            enabled = ready && selectedUri != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("开始 OTA") }

        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(10.dp),
        )
        Text("进度: ${"%.1f".format(progress * 100)}%")
    }
}
