package com.dk80.tool.ble

import java.util.UUID

/**
 * DK80 GATT 协议 UUID（与固件 dk80_gatt_service.c / ota_service.h 一一对应）。
 *
 * DK80 调试服务使用 16 位 UUID(0xFFD0/0xFFD1/0xFFD2/0xFFD3)，映射到标准
 * Bluetooth 128 位基址 0000xxxx-0000-1000-8000-00805F9B34FB。
 *
 * OTA 服务使用自定义 128 位 UUID，由固件 little-endian 字节数组反转得到。
 */
object BleUuids {
    // DK80 调试服务（16-bit UUID 基址）
    val DK80_SERVICE: UUID = UUID.fromString("0000FFD0-0000-1000-8000-00805F9B34FB")
    val CHAR_STATUS: UUID  = UUID.fromString("0000FFD1-0000-1000-8000-00805F9B34FB")
    val CHAR_CMD: UUID     = UUID.fromString("0000FFD2-0000-1000-8000-00805F9B34FB")
    val CHAR_DEBUG: UUID   = UUID.fromString("0000FFD3-0000-1000-8000-00805F9B34FB")

    // OTA 服务（自定义 128-bit UUID）
    val OTA_SERVICE: UUID      = UUID.fromString("02F00000-0000-0000-0000-00000000FE00")
    val OTA_CHAR_TX: UUID      = UUID.fromString("02F00000-0000-0000-0000-00000000FF00")
    val OTA_CHAR_RX: UUID      = UUID.fromString("02F00000-0000-0000-0000-00000001FF00")
    val OTA_CHAR_NOTI: UUID    = UUID.fromString("02F00000-0000-0000-0000-00000002FF00")
    val OTA_CHAR_VERSION: UUID = UUID.fromString("02F00000-0000-0000-0000-00000003FF00")

    val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}
