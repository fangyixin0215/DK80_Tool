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
import java.util.UUID
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

    // 待解析的 INFO 缓冲（INFO? 返回多行，到 INFO:END 结束）
    private val infoBuilder = StringBuilder()

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
                g.requestMtu(247)
            } else {
                _state.value = BleState.Disconnected
                closeGatt()
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

            val otaSvc = g.getService(BleUuids.OTA_SERVICE)
            if (otaSvc != null) {
                otaRx = otaSvc.getCharacteristic(BleUuids.OTA_CHAR_RX)
                otaNoti = otaSvc.getCharacteristic(BleUuids.OTA_CHAR_NOTI)
            }

            // 依次开启 CHAR1 / CHAR3 通知
            enableNotify(charStatus) { enableNotify(charDebug) { sendHandshake() } }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val value = c.value ?: return
            when (c.uuid) {
                BleUuids.CHAR_STATUS -> _status.value = DeviceStatus.fromBytes(value)
                BleUuids.CHAR_DEBUG -> handleDebugText(String(value))
                BleUuids.OTA_CHAR_NOTI -> handleOtaNotify(value)
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeInProgress = false
            pumpWriteQueue()
        }
    }

    // -------------------- 扫描 / 连接 / 断开 --------------------

    fun startScan() {
        val scanner = btAdapter?.bluetoothLeScanner ?: return
        _state.value = BleState.Scanning
        // 不使用硬件 setServiceUuid 过滤（部分 Android OEM 解析不可靠），
        // 改在 onScanResult 中按 DK80 服务 UUID / 设备名做软件过滤。
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, scanCallback)
        mainHandler.postDelayed({ if (_state.value == BleState.Scanning) stopScan() }, 10_000)
    }

    fun stopScan() {
        btAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_state.value == BleState.Scanning) _state.value = BleState.Disconnected
    }

    fun disconnect() {
        stopScan()
        gatt?.disconnect()
    }

    private fun closeGatt() {
        gatt?.close()
        gatt = null
        charStatus = null; charCmd = null; charDebug = null
        otaRx = null; otaNoti = null
        writeQueue.clear(); writeInProgress = false
    }

    // -------------------- 通知开启（链式） --------------------

    private fun enableNotify(c: BluetoothGattCharacteristic?, done: () -> Unit) {
        if (c == null) { done(); return }
        gatt?.setCharacteristicNotification(c, true)
        val desc = c.getDescriptor(BleUuids.CCC) ?: run { done(); return }
        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        writeDescriptor(desc, done)
    }

    private fun writeDescriptor(desc: BluetoothGattDescriptor, done: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt?.writeDescriptor(desc)
            // TIRAMISU+ 通过 onDescriptorWrite 回调，这里简化：延迟继续
            mainHandler.postDelayed(done, 120)
        } else {
            @Suppress("DEPRECATION") desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION") gatt?.writeDescriptor(desc)
            mainHandler.postDelayed(done, 120)
        }
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

    fun setLogStream(on: Boolean) { writeCommand("LOG:${if (on) 1 else 0}\n") }

    fun requestStatus() { writeCommand("STA?\n") }

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
    }

    // -------------------- CHAR3 文本解析 --------------------

    private fun handleDebugText(text: String) {
        appendLog(text)
        when {
            text.startsWith("HS:ACK") -> {
                _handshake.value = text.trim()
                _state.value = BleState.Ready
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

    // -------------------- OTA（基本实现：按 MTU-3 分片写 OTA_RX） --------------------

    fun sendOtaData(file: ByteArray, onDone: (Boolean) -> Unit) {
        if (otaRx == null) { onDone(false); return }
        val max = (mtu - 3).coerceAtLeast(20)
        _otaProgress.value = 0f
        var off = 0
        val sendNext = object : Runnable {
            override fun run() {
                if (off >= file.size) { onDone(true); return }
                val end = (off + max).coerceAtMost(file.size)
                enqueueWrite(otaRx!!, file.copyOfRange(off, end), false)
                off = end
                _otaProgress.value = off.toFloat() / file.size
                mainHandler.postDelayed(this, 5)
            }
        }
        sendNext.run()
    }

    private fun handleOtaNotify(value: ByteArray) {
        // OTA 进度/状态通知（按固件 ota_service 协议解析，此处预留）
        appendLog("OTA NOTI: ${value.joinToString(" ") { "%02X".format(it) }}")
    }
}
