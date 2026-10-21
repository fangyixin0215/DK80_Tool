package com.dk80.tool.data

/**
 * DK80 设备实时状态（对应固件 CHAR1 的 24 字节二进制快照，小端）。
 *
 * 字节布局（见 dk80_gatt_service.c dk80_build_status）：
 *  [0..1] gas_raw     [2..3] pm2_5    [4..5] pm10(固件保留，App 不展示)
 *  [6..7] temp_x10    [8..9] humi_x10
 *  [10] fan_speed     [11] uv         [12] ozone_gen
 *  [13] bit0=气体传感器在线    [14] 4G CSQ rssi 0~31（0xFF=未知）
 *  [15] bit0=SIM 卡已插入
 *  [16] 模式 0待机/1定时/2循环/3预约   [17] 档位/组号
 *  [18] 工作中(0/1)   [19..20] 已运行秒(u16)   [21..22] 剩余秒(u16)
 *
 * gas_raw：两气体模组原始值均为 0.01ppm 单位（×100 的整数）
 * csq：AT+CSQ rssi，dBm = -113 + 2*rssi；-1=未知/不可测
 * 固件版本 <24B 快照时 mode 等字段保持默认 0（旧固件兼容）
 */
data class DeviceStatus(
    val gasRaw: Int = 0,
    val pm25: Int = 0,
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val fanSpeed: Int = 0,
    val uvOn: Boolean = false,
    val ozoneGenOn: Boolean = false,
    val gasOnline: Boolean = false,
    val csq: Int = -1,
    val simReady: Boolean = false,
    val mode: Int = 0,
    val modeGroup: Int = 0,
    val working: Boolean = false,
    val runSec: Int = 0,
    val remainSec: Int = 0
) {
    val gasInvalid: Boolean get() = gasRaw == 0xFFFF

    companion object {
        fun fromBytes(b: ByteArray): DeviceStatus {
            if (b.size < 13) return DeviceStatus()
            val u16 = { i: Int -> (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) }
            val s16 = { i: Int ->
                val v = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
                if (v > 0x7FFF) v - 0x10000 else v
            }
            return DeviceStatus(
                gasRaw = u16(0),
                pm25 = u16(2),
                temperature = s16(6) / 10f,
                humidity = u16(8) / 10f,
                fanSpeed = b[10].toInt() and 0xFF,
                uvOn = (b[11].toInt() and 0xFF) != 0,
                ozoneGenOn = (b[12].toInt() and 0xFF) != 0,
                gasOnline = b.size >= 14 && (b[13].toInt() and 0x01) != 0,
                csq = if (b.size >= 15) {
                    val v = b[14].toInt() and 0xFF
                    if (v == 0xFF) -1 else v
                } else -1,
                simReady = b.size >= 16 && (b[15].toInt() and 0x01) != 0,
                mode = if (b.size >= 23) b[16].toInt() and 0xFF else 0,
                modeGroup = if (b.size >= 23) b[17].toInt() and 0xFF else 0,
                working = b.size >= 23 && (b[18].toInt() and 0xFF) != 0,
                runSec = if (b.size >= 23) u16(19) else 0,
                remainSec = if (b.size >= 23) u16(21) else 0
            )
        }
    }
}

/** INFO? 文本行解析出的设备身份信息 */
data class DeviceInfo(
    val imei: String = "",
    val iccid: String = "",
    val imsi: String = "",
    val mac: String = "",
    val fw: String = "",
    val hw: String = "",
    val build: String = "",
    val gas: String = ""
)
