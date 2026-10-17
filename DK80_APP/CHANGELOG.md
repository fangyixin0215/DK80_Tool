# DK80 Tool 修改记录

> 本文件用于追踪 DK80 Tool Android App 及配套固件 BLE 调试通道的版本变更。
> 每次功能更新请在顶部追加新版本条目，格式保持一致，便于回溯。

---

## [1.0.0] - 2026-10-17

### 新增（固件 BLE 调试通道）

- 新增 **DK80 自定义 GATT 服务**（UUID `0xFFD0`），含三个特征：
  - CHAR1 (`0xFFD1`)：16 字节二进制状态快照（Read / Notify）
  - CHAR2 (`0xFFD2`)：文本命令通道（Write）
  - CHAR3 (`0xFFD3`)：调试响应 + 日志流（Notify，变长文本行）
- 新增 **握手协议**：`HS:REQ` → `HS:ACK FW=.. HW=.. BUILD=.. SN=<MAC> GAS=..`
- 新增 **按键遥控**：`KEY:<0-15>` 复用红外码值表（`dk80_mode_ir_cmd`）
- 新增 **设备信息查询**：`INFO?` 返回 IMEI / ICCID / IMSI / MAC / FW / HW / BUILD / GAS
- 新增 **日志流**：`LOG:<0|1>` 开关，`co_log` 经环形缓冲按行 Notify 到 App
- 新增 **状态查询**：`STA?` 立即推送一次 CHAR1 状态
- 新增 **OTA 握手**：`OTA:START` → `OTA:READY`，App 切到 SDK `ble_ota` 服务
- 新增 `dk80_ble_log.c/h`：挂钩 `fputc_imp`，不影响 UART0，2KB 环形缓冲
- `dk80_module_net.c`：联网建立时缓存 IMEI / ICCID / IMSI 并暴露 getter
- `dk80_board.h`：定义 `DK80_FW_VER_STRING` / `DK80_HW_VER_STRING` / `DK80_GAS_TYPE_DEFAULT`
- Keil 工程加入 `dk80_ble_log.c` / `ota_service.c` / `ota.c`
- **量产宏控制**：`DK80_BLE_DEBUG_EN`、`DK80_BLE_LOG_EN`

### 新增（Android App）

- 工程骨架：Kotlin + Jetpack Compose + Material3，minSdk 26 / targetSdk 34
- BLE 核心 `BleManager`：扫描过滤 DK80 服务 UUID → 连接 → MTU 247 → 开 CHAR1/CHAR3 通知 → 握手 → Ready；串行写队列 + MTU-3 分片
- **遥控页**：圆形按键网格，键值与红外码一一对应（电源/童锁/风±/定时/循环/预约/时±/分±/时间/复位）
- **工况页**：实时传感器（PM2.5 / PM10 / NH₃或O₃ / 温湿度）+ 风扇/UV/臭氧，设备身份信息卡
- **日志页**：实时日志流开关、清空、一键导出 txt 并系统分享
- **OTA 页**：选择固件 bin → 按 MTU 分片写入 ble_ota RX 特征，带进度条
- 顶部连接状态栏（状态/设备名/扫描/断开）
- Android 12+ 蓝牙权限处理、FileProvider 导出

### 编译验证

- 固件 Keil 编译：**0 Error**（2 个预存 scatter 警告与本次无关）

### 已知待办

- OTA 分片写入为通用实现，若固件 `ota_service` 要求特定包头/校验，需在 `BleManager.sendOtaData` 中按协议封装
- 未配置 App 图标（暂用系统默认图标）
- 未配置签名（release 构建需自行配置 keystore）

---

## 版本号约定

- 主版本号：协议不兼容变更（如 UUID / 命令字变更）
- 次版本号：新增功能（向下兼容）
- 修订号：Bug 修复

---

## 维护说明

每次提交前请更新本文件，新增条目格式：

```
## [x.y.z] - YYYY-MM-DD

### 新增
### 修改
### 修复
### 编译验证
### 已知待办
```
