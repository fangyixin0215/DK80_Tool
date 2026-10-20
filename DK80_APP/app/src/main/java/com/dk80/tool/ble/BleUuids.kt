package com.dk80.tool.ble

import java.util.UUID

/**
 * DK80 GATT 协议 UUID（与固件 dk80_gatt_service.c 一一对应）。
 *
 * DK80 调试服务使用 16 位 UUID(0xFFD0/0xFFD1/0xFFD2/0xFFD3)，映射到标准
 * Bluetooth 128 位基址 0000xxxx-0000-1000-8000-00805F9B34FB。
 *
 * OTA 服务 UUID 定义在 FreqchipOta.kt（可移植 OTA 传输层，含移植清单）。
 */
object BleUuids {
    // DK80 调试服务（16-bit UUID 基址）
    val DK80_SERVICE: UUID = UUID.fromString("0000FFD0-0000-1000-8000-00805F9B34FB")
    val CHAR_STATUS: UUID  = UUID.fromString("0000FFD1-0000-1000-8000-00805F9B34FB")
    val CHAR_CMD: UUID     = UUID.fromString("0000FFD2-0000-1000-8000-00805F9B34FB")
    val CHAR_DEBUG: UUID   = UUID.fromString("0000FFD3-0000-1000-8000-00805F9B34FB")

    val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}
