package com.dk80.tool.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.dk80.tool.data.DeviceInfo
import com.dk80.tool.data.DeviceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/** BLE 连接状态 */
enum class BleState { Disconnected, Scanning, Connecting, Connected, Ready, Failed }

/**
 * DK80 BLE 通信核心：扫描/连接/握手/MTU/收发/命令分发。
 *
 * 连接成功握手流程：
 *  connect -> requestMtu(247) -> discoverServices
 *  -> setCharacteristicNotification(CHAR1) + write CCC
 *  -> setCharacteristicNotification(CHAR3) + write CCC
 *  -> write("HS:REQ") -> 收到 "HS:ACK ..." -> state=Ready
 */
@SuppressLint("MissingPermission")
class BleManager private constructor(context: Context) {

    companion object {
        @Volatile private var instance: BleManager? = null
        fun get(context: Context): BleManager =
            instance ?: synchronized(this) {
                instance ?: BleManager(context.applicationContext).also { instance = it }
            }
    }

    private val appCtx = context.applicationContext
    private val btManager = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btAdapter: BluetoothAdapter? = btManager.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private var gatt: BluetoothGatt? = null
    private var charStatus: BluetoothGattCharacteristic? = null
    private var charCmd: BluetoothGattCharacteristic? = null
    private var charDebug: BluetoothGattCharacteristic? = null
    private var otaRx: BluetoothGattCharacteristic? = null
    private var otaNoti: BluetoothGattCharacteristic? = null

    private var mtu = 23

    /* 用户主动断开标志：为 false 时扫描超时/异常断开都会自动重扫重连 */
    @Volatile private var userClosed = false

    private val _state = MutableStateFlow(BleState.Disconnected)
    val state: StateFlow<BleState> = _state.asStateFlow()

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()

    private val _info = MutableStateFlow(DeviceInfo())
    val info: StateFlow<DeviceInfo> = _info.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _handshake = MutableStateFlow("")
    val handshake: StateFlow<String> = _handshake.asStateFlow()

    private val _otaProgress = MutableStateFlow(0f)
    val otaProgress: StateFlow<Float> = _otaProgress.asStateFlow()

    /* 蓝牙信号强度（手机读设备的 RSSI，dBm；Int.MIN_VALUE=未知/未连接） */
    private val _rssi = MutableStateFlow(Int.MIN_VALUE)
    val rssi: StateFlow<Int> = _rssi.asStateFlow()

    /* Ready 期间每 3s 轮询一次 RSSI；状态离开 Ready 后自动停（run 里自检） */
    private val rssiPoller = object : Runnable {
        override fun run() {
            if (_state.value != BleState.Ready) return
            gatt?.readRemoteRssi()
            mainHandler.postDelayed(this, 3000)
        }
    }

    // 待解析的 INFO 缓冲（INFO? 返回多行，到 INFO:END 结束）
    private val infoBuilder = StringBuilder()

    /* OTA 服务缺失时 refresh+重发现 只自动重试一次 */
    @Volatile private var otaDiscRetry = false

    // 串行写队列：保证 GATT write 有序（Android BLE 单次只允许一个未完成的写操作）
    private data class WriteJob(val char: BluetoothGattCharacteristic, val data: ByteArray, val ack: Boolean)
    private val writeQueue = ConcurrentLinkedQueue<WriteJob>()
    @Volatile private var writeInProgress = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            if (_state.value != BleState.Scanning) return
            // 软件过滤：广播包中含 DK80 服务 UUID，或设备名为 DK80
            val record = result.scanRecord
            val hasService = record?.serviceUuids
                ?.any { it.uuid == BleUuids.DK80_SERVICE } == true
            val nameMatch = dev.name?.equals("DK80", ignoreCase = true) == true
            if (!hasService && !nameMatch) return
            stopScan()
            _state.value = BleState.Connecting
            _deviceName.value = dev.name ?: "DK80"
            gatt = dev.connectGatt(appCtx, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = BleState.Connected
                /* Android 按 MAC 缓存 GATT 服务表：固件新增服务（如 OTA）后
                 * 不刷新会一直拿到旧缓存，getService(OTA) 返回 null。
                 * refresh() 是隐藏 API，反射调用。 */
                refreshGattCache(g)
                g.requestMtu(247)
            } else {
                closeGatt()
                if (!userClosed) {
                    /* 异常断开/连接失败：自动回到扫描，持续重连（设备重启窗口也能自己恢复） */
                    _state.value = BleState.Scanning
                    mainHandler.postDelayed({ beginScan() }, 1200)
                } else {
                    _state.value = BleState.Disconnected
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            this@BleManager.mtu = mtu
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(BleUuids.DK80_SERVICE)
            if (svc == null) { _state.value = BleState.Failed; return }
            charStatus = svc.getCharacteristic(BleUuids.CHAR_STATUS)
            charCmd = svc.getCharacteristic(BleUuids.CHAR_CMD)
            charDebug = svc.getCharacteristic(BleUuids.CHAR_DEBUG)

            val otaSvc = g.getService(FreqchipOta.SVC)
            if (otaSvc != null) {
                otaRx = otaSvc.getCharacteristic(FreqchipOta.CHAR_RX)
                otaNoti = otaSvc.getCharacteristic(FreqchipOta.CHAR_NOTI)
                appendLog("OTA service discovered, rx=${otaRx != null} noti=${otaNoti != null}")
            } else if (!otaDiscRetry) {
                /* 服务缓存可能是旧库（Android 按 MAC 缓存 GATT DB）：refresh 后二次发现 */
                otaDiscRetry = true
                val ok = refreshGattCache(g)
                appendLog("OTA service missing, gatt refresh=$ok, rediscover...")
                mainHandler.postDelayed({ g.discoverServices() }, 300)
            } else {
                /* refresh+二次发现仍没有：枚举全部服务记日志，并按 UUID 尾部模糊匹配兜底 */
                val all = g.services.orEmpty().map { it.uuid.toString().lowercase() }
                appendLog("services(${all.size}): ${all.joinToString()}")
                val fuzzy = g.services.orEmpty().firstOrNull {
                    it.uuid.toString().lowercase().replace("-", "").endsWith("fe00")
                }
                if (fuzzy != null) {
                    otaRx = fuzzy.getCharacteristic(FreqchipOta.CHAR_RX)
                    otaNoti = fuzzy.getCharacteristic(FreqchipOta.CHAR_NOTI)
                    appendLog("OTA service fuzzy-matched: ${fuzzy.uuid}, rx=${otaRx != null}")
                } else {
                    appendLog("OTA service STILL missing after refresh+rediscover")
                }
            }

            // 依次开启 CHAR1 / CHAR3 / OTA 通知（OTA 应答只走 notify，CCC 不开设备不回包）。
            // 必须串行：Android 同一连接上描述符写并发第二条会直接失败丢弃，
            // otaNoti 的 CCC 与 charStatus 并发写过一次，导致 OTA 应答永远发不出
            enableNotify(charStatus) { enableNotify(charDebug) { enableNotify(otaNoti) {
                appendLog("notify enabled (status/debug/ota)")
                sendHandshake()
            } } }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val value = c.value ?: return
            when (c.uuid) {
                BleUuids.CHAR_STATUS -> _status.value = DeviceStatus.fromBytes(value)
                BleUuids.CHAR_DEBUG -> handleDebugText(String(value))
                FreqchipOta.CHAR_NOTI -> otaEngine?.onNotify(value)
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            otaEngine?.onWriteDone()
            writeInProgress = false
            pumpWriteQueue()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS)
                appendLog("desc write FAILED: char=${d.characteristic?.uuid} status=$status")
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) _rssi.value = rssi
        }
    }

    // -------------------- 扫描 / 连接 / 断开 --------------------

    fun startScan() {
        if (_state.value == BleState.Scanning) return
        userClosed = false
        beginScan()
    }

    private fun beginScan() {
        val scanner = btAdapter?.bluetoothLeScanner ?: return
        _state.value = BleState.Scanning
        // 不使用硬件 setServiceUuid 过滤（部分 Android OEM 解析不可靠），
        // 改在 onScanResult 中按 DK80 服务 UUID / 设备名做软件过滤。
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, scanCallback)
        // 10 秒扫不到先停一拍再继续扫，直到用户主动断开
        mainHandler.postDelayed({
            if (userClosed || _state.value != BleState.Scanning) return@postDelayed
            scanner.stopScan(scanCallback)
            if (!userClosed) mainHandler.postDelayed({ beginScan() }, 600)
        }, 10_000)
    }

    fun stopScan() {
        btAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_state.value == BleState.Scanning) _state.value = BleState.Disconnected
    }

    fun disconnect() {
        userClosed = true
        mainHandler.removeCallbacksAndMessages(null)
        btAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        gatt?.disconnect()
        _state.value = BleState.Disconnected
    }

    private fun closeGatt() {
        gatt?.close()
        gatt = null
        charStatus = null; charCmd = null; charDebug = null
        otaRx = null; otaNoti = null
        otaDiscRetry = false
        writeQueue.clear(); writeInProgress = false
        mainHandler.removeCallbacks(rssiPoller)
        _rssi.value = Int.MIN_VALUE
    }

    // -------------------- 通知开启（链式） --------------------

    private fun enableNotify(c: BluetoothGattCharacteristic?, done: () -> Unit) {
        if (c == null) { done(); return }
        val g = gatt ?: run { done(); return }
        g.setCharacteristicNotification(c, true)
        val desc = c.getDescriptor(BleUuids.CCC) ?: run {
            appendLog("notify enable: CCC descriptor missing, char=${c.uuid}")
            done(); return
        }
        writeDescriptor(desc, done)
    }

    private fun writeDescriptor(desc: BluetoothGattDescriptor, done: () -> Unit) {
        val g = gatt ?: run { done(); return }
        val rc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION") desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION") if (g.writeDescriptor(desc)) BluetoothGatt.GATT_SUCCESS else 1
        }
        if (rc != BluetoothGatt.GATT_SUCCESS) appendLog("writeDescriptor rejected, rc=$rc")
        /* 串行节奏靠 120ms 间隔 + 回调链保证（描述符写并发第二条会被 Android 丢弃） */
        mainHandler.postDelayed(done, 120)
    }

    // -------------------- 握手 / 命令 --------------------

    private fun sendHandshake() {
        writeCommand("HS:REQ\n")
    }

    /** 发送文本命令到 CHAR2（自动追加换行、自动按 MTU-3 分片） */
    fun writeCommand(text: String) {
        val c = charCmd ?: return
        enqueueWrite(c, text.toByteArray(Charsets.UTF_8), true)
    }

    /** 模拟按键（红外码值 0..15，与固件 dk80_mode_ir_cmd 一致） */
    fun sendKey(key: Int) { writeCommand("KEY:$key\n") }

    fun requestInfo() { writeCommand("INFO?\n") }

    /* 日志流开关：状态保持在 BleManager（而非页面 remember），
     * 切页/重建页面不丢；固件侧断连后标志仍有效，重连握手成功自动补发 LOG:1 恢复 */
    private val _logStreamOn = MutableStateFlow(false)
    val logStreamOn: StateFlow<Boolean> = _logStreamOn.asStateFlow()

    fun setLogStream(on: Boolean) {
        _logStreamOn.value = on
        writeCommand("LOG:${if (on) 1 else 0}\n")
    }

    fun requestStatus() { writeCommand("STA?\n") }

    /* 切换气体检测类型：false=NH3 氨气, true=O3 臭氧（固件 GAS: 命令，落 flash，重启保持）。
     * 本地乐观更新 info.gas，下次 INFO? 刷新兜底 */
    fun setGasType(isO3: Boolean) {
        writeCommand(if (isO3) "GAS:1\n" else "GAS:0\n")
        _info.value = _info.value.copy(gas = if (isO3) "O3" else "NH3")
    }

    fun startOta() { writeCommand("OTA:START\n") }

    // -------------------- 串行写队列 + MTU 分片 --------------------

    private fun enqueueWrite(c: BluetoothGattCharacteristic, data: ByteArray, ack: Boolean) {
        val max = (mtu - 3).coerceAtLeast(20)
        if (data.size <= max) {
            writeQueue.add(WriteJob(c, data, ack))
        } else {
            var off = 0
            while (off < data.size) {
                val end = (off + max).coerceAtMost(data.size)
                writeQueue.add(WriteJob(c, data.copyOfRange(off, end), ack && off + max >= data.size))
                off = end
            }
        }
        pumpWriteQueue()
    }

    private fun pumpWriteQueue() {
        if (writeInProgress) return
        val job = writeQueue.poll() ?: return
        writeInProgress = true
        val c = job.char
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val wt = if (job.ack) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            gatt?.writeCharacteristic(c, job.data, wt)
        } else {
            @Suppress("DEPRECATION") c.writeType = if (job.ack) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION") c.value = job.data
            @Suppress("DEPRECATION") gatt?.writeCharacteristic(c)
        }
        if (!job.ack) {
            /* NO_RESPONSE 写入不会有 onCharacteristicWrite 回调，定时复位队列锁，
             * 否则队列在第一个 no-response 包之后就停摆 */
            mainHandler.postDelayed({ writeInProgress = false; pumpWriteQueue() }, 20)
        }
    }

    /** 刷新 Android 端 GATT 服务缓存（隐藏 API，反射调用；部分 ROM 会拦截，返回是否成功） */
    private fun refreshGattCache(g: BluetoothGatt): Boolean {
        return try {
            g.javaClass.getMethod("refresh").invoke(g) as Boolean
        } catch (_: Exception) { false }
    }

    // -------------------- CHAR3 文本解析 --------------------

    private fun handleDebugText(text: String) {
        appendLog(text)
        when {
            text.startsWith("HS:ACK") -> {
                _handshake.value = text.trim()
                _state.value = BleState.Ready
                /* 日志流此前开着：断连期间固件标志仍有效，补发 LOG:1 恢复推送 */
                if (_logStreamOn.value) writeCommand("LOG:1\n")
                mainHandler.post(rssiPoller)
            }
            text.startsWith("KEY:ACK") -> { /* 按键应答，可按需处理 */ }
            text.startsWith("LOG:ACK") -> { /* 日志开关应答 */ }
            text.startsWith("OTA:READY") -> { /* OTA 就绪，App 走 OTA 服务 */ }
            text.startsWith("INFO:END") -> {
                _info.value = parseInfo(infoBuilder.toString())
                infoBuilder.clear()
            }
            text.startsWith("IMEI=") || text.startsWith("ICCID=") || text.startsWith("IMSI=") ||
            text.startsWith("MAC=") || text.startsWith("FW=") || text.startsWith("HW=") ||
            text.startsWith("BUILD=") || text.startsWith("GAS=") -> {
                infoBuilder.append(text)
            }
        }
    }

    private fun parseInfo(s: String): DeviceInfo {
        val map = HashMap<String, String>()
        s.lines().forEach { line ->
            val idx = line.indexOf('=')
            if (idx > 0) map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
        return DeviceInfo(
            imei = map["IMEI"] ?: "", iccid = map["ICCID"] ?: "", imsi = map["IMSI"] ?: "",
            mac = map["MAC"] ?: "", fw = map["FW"] ?: "", hw = map["HW"] ?: "",
            build = map["BUILD"] ?: "", gas = map["GAS"] ?: ""
        )
    }

    private fun appendLog(line: String) {
        val current = _logs.value.toMutableList()
        current.add(line.trimEnd())
        if (current.size > 2000) current.removeAt(0)
        _logs.value = current
    }

    fun clearLogs() { _logs.value = emptyList() }

    // -------------------- OTA（委托给可移植的 FreqchipOta 传输层，见其文件头移植清单） --------------------

    @Volatile private var otaEngine: FreqchipOta? = null

    /* OTA 当前阶段描述（擦除/写入进度），OTA 页展示 */
    private val _otaPhase = MutableStateFlow("")
    val otaPhase: StateFlow<String> = _otaPhase.asStateFlow()

    /**
     * 执行完整 OTA 流程：0x01 查基地址 -> 0x03 按 4KB 循环擦除 -> 0x05 分包写 -> 0x09 重启。
     * 返回 true 表示升级指令已完整下发（设备随后自行复位重启）。
     */
    suspend fun sendOtaData(file: ByteArray): Boolean {
        val rx = otaRx; val nt = otaNoti; val g = gatt
        if (rx == null || nt == null || g == null) {
            _otaPhase.value = "未发现 OTA 服务（手机 GATT 缓存未刷新）：请关闭再打开手机蓝牙，或重启手机后重试"
            return false
        }
        val engine = FreqchipOta(g, rx, nt, mtu)
        otaEngine = engine
        return try {
            when (val r = engine.perform(file, onPhase = { _otaPhase.value = it }, onProgress = { _otaProgress.value = it })) {
                is FreqchipOta.Result.Done -> true
                is FreqchipOta.Result.Fail -> { _otaPhase.value = r.reason; false }
            }
        } finally {
            otaEngine = null
        }
    }
}
