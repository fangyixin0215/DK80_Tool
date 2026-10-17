# DK80 Tool — Android 调试 App

面向 DK80（FR2018A-U）净化器量产调试的 BLE 工具 App。

## 功能

1. **蓝牙遥控器** — 通过 BLE 复用红外码值表（0x00~0x0F），实现板载全部按键（电源/童锁/风+/风-/定时/循环/预约/时±/分±/时间确认/复位）。
2. **工况与设备身份** — 连接后自动展示：传感器（PM2.5/PM10/NH₃或O₃/温湿度）、风速、UV、臭氧发生器；IMEI、ICCID、IMSI、MAC、固件版本、硬件版本、编译时间、气体类型。
3. **日志查看与一键导出** — 实时日志流（`LOG:1`），可清空、一键导出为 txt 并通过系统分享（微信/邮件/保存到文件）。
4. **蓝牙 OTA** — 选择固件 bin，通过 SDK `ble_ota` 服务的 RX 特征按 MTU-3 分片写入，带进度条。
5. **握手协议** — 连接后 App 发 `HS:REQ`，设备回 `HS:ACK FW=.. HW=.. BUILD=.. SN=<mac> GAS=..`，校验通过进入 Ready。

## 构建

用 Android Studio 打开本目录，等待 Gradle 同步后直接 Run。  
或命令行：
```
./gradlew :app:assembleDebug
```
产物：`app/build/outputs/apk/debug/app-debug.apk`

## 蓝牙协议（与固件 dk80_gatt_service.c 对齐）

服务 UUID `0000FFD0-0000-1000-8000-00805F9B34FB`：

| 特征 | UUID | 性质 | 说明 |
|------|------|------|------|
| CHAR1 | FFD1 | Read/Notify | 16B 二进制状态快照（小端） |
| CHAR2 | FFD2 | Write | 文本命令，`\n` 结尾 |
| CHAR3 | FFD3 | Notify | 调试响应 + 日志流（变长文本行） |

命令（CHAR2）：
```
HS:REQ          握手
KEY:<0-15>      模拟按键（红外码值）
INFO?           返回 IMEI/ICCID/IMSI/MAC/FW/HW/BUILD/GAS
LOG:<0|1>       开关日志流
STA?            立即推一次状态
OTA:START       进入 OTA（切到 ble_ota 服务）
```

OTA 服务 UUID `02F00000-0000-0000-0000-00000000FE00`，RX 特征 `...FF00`，NOTI `...FF02`。

## 宏控制（量产开关）

固件侧三个宏可一键裁剪调试能力（见对应头文件）：
- `DK80_BLE_DEBUG_EN` — DK80 GATT 调试服务（命令/信息/日志/OTA 握手）
- `DK80_BLE_LOG_EN` — co_log 复制到 BLE 环形缓冲
- OTA 服务本身由 `ota_gatt_add_service()` 调用决定

量产版可将 `DK80_BLE_LOG_EN` 置 0、仅保留遥控+状态上报；或全部关闭仅留基本服务。

## 注意

- OTA 分片写入为通用实现，若固件 `ota_service` 要求特定包头/校验，请在 `BleManager.sendOtaData` 中按协议封装。
- 首次启动需授予蓝牙/位置权限（Android 12+ 为 BLUETOOTH_SCAN / BLUETOOTH_CONNECT）。
