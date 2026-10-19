package com.dk80.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk80.tool.ble.BleManager
import com.dk80.tool.ble.BleState
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

        // 步骤说明
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StepRow(1, "选择固件 bin 文件")
                StepRow(2, "App 切换到 OTA 服务并按 MTU 分片写入 RX 特征")
                StepRow(3, "设备端 ota_service 校验并刷写，完成后自动重启")
            }
        }

        // 文件选择
        OutlinedButton(
            onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("选择固件文件")
        }
        if (msg.isNotEmpty()) {
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(4.dp))

        // 开始按钮
        Button(
            onClick = {
                scope.launch {
                    val uri = selectedUri ?: return@launch
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) { msg = "读取文件失败"; return@launch }
                    ble.startOta()
                    ble.sendOtaData(bytes) { ok ->
                        msg = if (ok) "OTA 数据发送完成，等待设备刷写..." else "OTA 失败，请重试"
                    }
                }
            },
            enabled = ready && selectedUri != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.Upload, null)
            Spacer(Modifier.width(8.dp))
            Text("开始 OTA 升级", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // 进度
        if (progress > 0f || selectedUri != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("升级进度", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "${"%.1f".format(progress * 100)}%",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!ready) {
            Text(
                "请先在「遥控」页连接设备",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StepRow(step: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text("$step", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
