package com.dk80.tool.data

/**
 * DK80 设备实时状态（对应固件 CHAR1 的 16 字节二进制快照，小端）。
 *
 * 字节布局（见 dk80_gatt_service.c dk80_build_status）：
 *  [0..1] gas_raw     [2..3] pm2_5    [4..5] pm10(固件保留，App 不展示)
 *  [6..7] temp_x10    [8..9] humi_x10
 *  [10] fan_speed     [11] uv         [12] ozone_gen
 *  [13] bit0=气体传感器在线    [14..15] reserved
 *
 * gas_raw：O3=ppb；NH3=0.01ppm（×100 的整数）
 */
data class DeviceStatus(
    val gasRaw: Int = 0,
    val pm25: Int = 0,
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val fanSpeed: Int = 0,
    val uvOn: Boolean = false,
    val ozoneGenOn: Boolean = false,
    val gasOnline: Boolean = false
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
                gasOnline = b.size >= 14 && (b[13].toInt() and 0x01) != 0
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
