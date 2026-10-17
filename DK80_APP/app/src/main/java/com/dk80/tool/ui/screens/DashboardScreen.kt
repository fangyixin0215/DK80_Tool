package com.dk80.tool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk80.tool.ble.BleManager
import com.dk80.tool.ble.BleState
import com.dk80.tool.ui.theme.Good
import com.dk80.tool.ui.theme.Primary
import com.dk80.tool.ui.theme.TextSub
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("实时工况")
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RowItem("PM2.5", "${status.pm25} µg/m³")
                RowItem("PM10", "${status.pm10} µg/m³")
                RowItem(if (info.gas == "O3") "臭氧 O₃" else "氨气 NH₃", "${status.ozonePpb} ppb")
                RowItem("温度", "%.1f ℃".format(status.temperature))
                RowItem("湿度", "%.1f %%".format(status.humidity))
                RowItem("风速", "${status.fanSpeed} 档")
                RowItem("UV", if (status.uvOn) "开启" else "关闭")
                RowItem("臭氧发生器", if (status.ozoneGenOn) "开启" else "关闭")
            }
        }

        SectionTitle("设备身份")
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RowItem("IMEI", info.imei)
                RowItem("ICCID", info.iccid)
                RowItem("IMSI", info.imsi)
                RowItem("MAC", info.mac)
                RowItem("固件版本", info.fw)
                RowItem("硬件版本", info.hw)
                RowItem("编译时间", info.build)
                RowItem("气体类型", info.gas.ifEmpty { "—" })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { ble.requestInfo() }, enabled = ready, modifier = Modifier.weight(1f)) {
                Text("刷新信息")
            }
            Button(onClick = { ble.requestStatus() }, enabled = ready, modifier = Modifier.weight(1f)) {
                Text("刷新状态")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = Primary, fontSize = 16.sp)
}

@Composable
private fun RowItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSub)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
