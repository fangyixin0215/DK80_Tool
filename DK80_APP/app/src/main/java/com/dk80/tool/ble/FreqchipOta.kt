package com.dk80.tool.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * 富芮坤 Freqchip FR801xH 全系 BLE OTA 通用传输层（单文件，可整体拷贝到其他项目）。
 *
 * 协议对应 SDK 的 components/ble/profiles/ble_ota（FR801xH OTA 参考文档）：
 *
 * 命令帧（写 CHAR_RX）：opcode(1) + len(2, 小端) + 载荷
 *   0x01 查询存储基地址（无载荷），应答 = base_addr(4, 小端)
 *   0x03 页擦除，载荷 = addr(4, 小端)，按 4KB 循环擦到固件末尾
 *   0x05 写数据，载荷 = addr(4, 小端) + len(2, 小端) + 数据
 *        单包 = 9(帧头) + 数据，整包必须落在单个 ATT 包里 -> 数据长 = MTU-3-9
 *   0x09 重启（无载荷），设备收到即复位，无应答
 * 应答帧（CHAR_NOTI notify）：result(1) + org_opcode(1) + len(2) + 载荷
 *
 * == 设备端前提（新项目移植检查清单）==
 * 1. proj_main.c 初始化时注册 OTA 服务：ota_gatt_add_service()，
 *    并实现 app_otas_get_storage_address() 返回 B 区起始地址
 * 2. 工程包含 ble_ota profile 与 jump_table 中的 OTA 钩子（直接参考官方例程）
 * 3. OTA_CRC_CHECK 关闭时 0x09 重启帧无附加载荷（本实现即按无载荷发送）
 * 4. 设备只有收到 CCC 使能后才会回 notify 应答（ota_link_ntf_enable 门槛）
 *
 * == APP 端接线（参考 BleManager 中的用法）==
 * 1. 连接后 requestMtu(247) -> discoverServices
 * 2. 按 [SVC]/[CHAR_RX]/[CHAR_NOTI] 找服务和特征
 * 3. 【踩坑关键】CCC 使能写必须串行：同一连接上并发第二条描述符写会被 Android
 *    直接丢弃且无任何回调，表现为设备永远打不出 ntf_enable、OTA 无应答超时
 * 4. BluetoothGattCallback 中：
 *      onCharacteristicChanged -> [onNotify]
 *      onCharacteristicWrite   -> [onWriteDone]
 * 5. 调 [perform]，Done 表示指令全部下发，设备随后自行复位重启
 */
class FreqchipOta(
    private val gatt: BluetoothGatt,
    private val rxChar: BluetoothGattCharacteristic,
    private val notifyChar: BluetoothGattCharacteristic,
    private val mtu: Int = 247,
) {
    companion object {
        /* 判别字节在 UUID 最后两位（固件 att 表字节序 {01,FF,...,F0,02} ->
         * 字符串 ...000000FF01，写成 ...0001FF00 是错的，特征会找不到） */
        val SVC: UUID = UUID.fromString("02F00000-0000-0000-0000-00000000FE00")
        val CHAR_TX: UUID = UUID.fromString("02F00000-0000-0000-0000-00000000FF00")
        val CHAR_RX: UUID = UUID.fromString("02F00000-0000-0000-0000-00000000FF01")
        val CHAR_NOTI: UUID = UUID.fromString("02F00000-0000-0000-0000-00000000FF02")
        val CHAR_VERSION: UUID = UUID.fromString("02F00000-0000-0000-0000-00000000FF03")
        private val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        const val CMD_GET_BASE = 0x01
        const val CMD_ERASE = 0x03
        const val CMD_WRITE = 0x05
        const val CMD_REBOOT = 0x09
        const val FLASH_PAGE = 0x1000

        /* 固件 0x58 处允许的 jump_table checkword（DK80 当前 55 44 33 22，
         * 早期 OTA 文档示例 51 52 52 51）；其他项目替换或传 null 跳过校验 */
        private val DEFAULT_CHECK_WORDS = listOf(
            byteArrayOf(0x55, 0x44, 0x33, 0x22),
            byteArrayOf(0x51, 0x52, 0x52, 0x51),
        )
    }

    sealed interface Result {
        data object Done : Result
        data class Fail(val reason: String) : Result
    }

    /* org_opcode -> 等待应答的 continuation（设备应答带回原 opcode，按此匹配） */
    private val waiters = ConcurrentHashMap<Int, kotlinx.coroutines.CancellableContinuation<ByteArray>>()

    /* 写完成信号：onWriteDone 由外层 onCharacteristicWrite 回调 */
    @Volatile private var writeDone: CompletableDeferred<Unit>? = null

    /** 在 onCharacteristicChanged 中调用；返回 true 表示该 notify 是 OTA 应答并已消费 */
    fun onNotify(value: ByteArray): Boolean {
        if (value.size < 2) return false
        waiters.remove(value[1].toInt() and 0xff)?.resume(value)
        return true
    }

    /** 在 onCharacteristicWrite 中调用 */
    fun onWriteDone() {
        writeDone?.complete(Unit)
    }

    /**
     * 串行使能 OTA 应答通知（写 CCC，等 onDescriptorWrite 回调）。
     * 调用前后都不能有其他 in-flight 的 GATT 写，否则整条被 Android 丢弃。
     */
    suspend fun enableNotify(timeoutMs: Long = 3000): Boolean {
        gatt.setCharacteristicNotification(notifyChar, true)
        val desc = notifyChar.getDescriptor(CCC) ?: return false
        val rc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION") desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            if (gatt.writeDescriptor(desc)) BluetoothGatt.GATT_SUCCESS else 1
        }
        return rc == BluetoothGatt.GATT_SUCCESS
    }

    /**
     * 执行完整 OTA：0x01 查基地址 -> 0x03 按 4KB 循环擦除 -> 0x05 分包写 -> 0x09 重启。
     *
     * @param checkWords 固件 0x58 处允许的 jump_table checkword 列表（防选错文件），
     *                   传 null 跳过校验。DK80 当前 55 44 33 22，早期文档示例 51 52 52 51
     */
    suspend fun perform(
        file: ByteArray,
        checkWords: List<ByteArray>? = DEFAULT_CHECK_WORDS,
        onPhase: (String) -> Unit = {},
        onProgress: (Float) -> Unit = {},
    ): Result {
        if (file.isEmpty()) return Result.Fail("固件文件为空")
        if (checkWords != null) {
            if (file.size < 0x5c) return Result.Fail("固件文件无效（文件太小），请确认固件 bin")
            val magic = file.copyOfRange(0x58, 0x5c)
            if (checkWords.none { it.contentEquals(magic) })
                return Result.Fail("固件文件无效（0x58 魔数不匹配），请确认固件 bin")
        }

        val chunk = ((mtu - 3) - 9).coerceAtLeast(20)   // 单包数据长 = MTU-3-9
        val pages = (file.size + FLASH_PAGE - 1) / FLASH_PAGE
        onProgress(0f)

        // 步骤1：查询存储基地址；应答 result(1)+org(1)+len(2)+base_addr(4)
        val rspBase = transact(CMD_GET_BASE, byteArrayOf(), 5000)
            ?: return Result.Fail("OTA 失败：设备无应答（查询基地址超时）")
        if (rspBase[0].toInt() != 0 || rspBase.size < 8) return Result.Fail("OTA 失败：查询基地址被拒绝")
        val base = le32at(rspBase, 4)

        // 步骤2：按 4KB 循环擦除（擦到固件末尾覆盖的所有页）
        for (i in 0 until pages) {
            onPhase("擦除 flash... (${i + 1}/$pages)")
            val rsp = transact(CMD_ERASE, le32(base + i * FLASH_PAGE), 10000)
                ?: return Result.Fail("OTA 失败：擦除超时（第 ${i + 1} 页）")
            if (rsp[0].toInt() != 0) return Result.Fail("OTA 失败：擦除被拒绝（地址可能越界）")
        }

        // 步骤3：分包写入，等每包应答后再发下一包
        var off = 0
        while (off < file.size) {
            val n = minOf(chunk, file.size - off)
            val payload = ByteArray(6 + n)
            System.arraycopy(le32(base + off), 0, payload, 0, 4)
            System.arraycopy(le16(n), 0, payload, 4, 2)
            System.arraycopy(file, off, payload, 6, n)
            val rsp = transact(CMD_WRITE, payload, 5000)
                ?: return Result.Fail("OTA 失败：写入超时（偏移 0x${Integer.toHexString(base + off)}）")
            if (rsp[0].toInt() != 0) return Result.Fail("OTA 失败：写入被拒绝")
            off += n
            onProgress(off.toFloat() / file.size)
            onPhase("写入固件... ${off * 100 / file.size}%")
        }

        // 步骤4：重启（设备收到即复位，无应答，不等）
        onPhase("写入完成，设备重启中...")
        writePkt(byteArrayOf(CMD_REBOOT.toByte(), 0, 0))
        return Result.Done
    }

    /** 发一帧命令并等 notify 应答；超时返回 null */
    private suspend fun transact(opcode: Int, payload: ByteArray, timeoutMs: Long): ByteArray? {
        val pkt = ByteArray(3 + payload.size)
        pkt[0] = opcode.toByte()
        pkt[1] = (payload.size and 0xff).toByte()
        pkt[2] = ((payload.size shr 8) and 0xff).toByte()
        System.arraycopy(payload, 0, pkt, 3, payload.size)
        if (!writePkt(pkt)) return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                waiters[opcode] = cont
            }
        }.also { waiters.remove(opcode) }
    }

    /** 带 ACK 的 characteristic 写：WRITE_TYPE_DEFAULT + 等 onCharacteristicWrite */
    private suspend fun writePkt(pkt: ByteArray): Boolean {
        rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val done = CompletableDeferred<Unit>()
        writeDone = done
        val rc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(rxChar, pkt, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION") rxChar.value = pkt
            @Suppress("DEPRECATION")
            if (gatt.writeCharacteristic(rxChar)) BluetoothGatt.GATT_SUCCESS else 1
        }
        if (rc != BluetoothGatt.GATT_SUCCESS) { writeDone = null; return false }
        return withTimeoutOrNull(5000) { done.await() } != null
    }

    private fun le16(v: Int) = byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())

    private fun le32(v: Int) = byteArrayOf(
        (v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(),
        ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte()
    )

    private fun le32at(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8) or
        ((b[off + 2].toInt() and 0xff) shl 16) or ((b[off + 3].toInt() and 0xff) shl 24)
}
