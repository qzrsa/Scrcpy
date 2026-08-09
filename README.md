# Scrcpy — 安卓设备远程控制

> 一款运行在安卓设备上的 scrcpy 风格投屏控制工具。用你的安卓手机/平板，远程控制同一局域网或通过 USB 连接的另一台安卓设备。

## 效果预览

<p align="center">
  <img src="https://raw.githubusercontent.com/wiki/qzrsa/Scrcpy/images/demo.gif" alt="demo" width="60%"/>
</p>

## 主要功能

| 功能 | 说明 |
|---|---|
| **实时投屏** | 高帧率、低延迟的视频流传输（H.264 / H.265） |
| **音频传输** | 支持 AAC / Opus 音频编码（Android 12+） |
| **远程控制** | 触控输入、按键事件、剪贴板同步 |
| **分辨率调节** | 支持动态切换被控端分辨率 |
| **屏幕旋转 / 电源** | 远程旋转屏幕、开关屏幕 |
| **多设备管理** | USB / WiFi 两种连接方式，保存多个设备 |
| **悬浮窗权限** | 后台保活，不影响被控端前台使用 |

## 系统要求

| 组件 | 要求 |
|---|---|
| **控制端**（本 APP） | Android 5.0+（API 21） |
| **被控端**（server） | Android 7.0+（API 24），建议 Android 12+ 以启用音频 |
| **连接方式** | 同一局域网，或 USB 调试模式 |
| **权限** | USB 调试、悬浮窗权限（必须授予） |

## 快速开始

### 方式一：WiFi 连接

1. 在被控端安装 [scrcpy-server.jar](Scrcpy/app/src/main/res/raw/scrcpy_server.jar)
2. 确保控制端和被控端在同一局域网
3. 在被控端启动 ADB Server：`adb pair <IP:PORT>`
4. 打开本 APP，添加设备（输入被控端 IP 和端口）
5. 点击连接，开始投屏

### 方式二：USB 连接

1. 在被控端开启 USB 调试
2. 用 USB 连接两台设备
3. 在被控端授权 USB 调试
4. 打开本 APP，USB 设备会自动识别

## 编译构建

### 环境要求

- JDK 17+
- Android SDK（compileSdk 36）
- Android NDK（如需 native 编译）
- Gradle 8.x

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/qzrsa/Scrcpy.git
cd Scrcpy/Scrcpy

# 构建 server（会自动复制到 app/res/raw/）
./gradlew :server:copyRelease

# 构建完整 APP
./gradlew :app:assembleRelease
```

### 签名配置（可选）

在 `Scrcpy/` 目录创建 `keystore.properties`：

```properties
MY_KEYSTORE_FILE=release.jks
MY_KEY_ALIAS=your_alias
MY_KEY_PASSWORD=your_key_password
MY_KEYSTORE_PASSWORD=your_store_password
```

## 技术架构

```
控制端 APP（Android）
├── client/          — 设备管理、连接控制
├── adb/             — ADB 通信封装
├── helper/          — UI 适配器、事件处理
└── entity/          — 数据模型

被控端 Server（Android）
├── Server.java      — 主程序、线程调度、心跳机制
├── helper/
│   ├── VideoEncode  — 视频编码（H.264/H.265 + Surface）
│   ├── AudioEncode  — 音频编码（AAC/Opus）
│   └── ControlPacket — 控制指令序列化
└── wrappers/
    ├── SurfaceControl — 虚拟显示器管理
    ├── InputManager   — 输入事件注入
    └── WindowManager  — 窗口管理
```

## 参数说明

通过 ADB 传递给 server 的可选参数：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `serverPort` | 25166 | TCP 监听端口 |
| `maxSize` | 1600 | 最大分辨率（长边 px） |
| `maxVideoBit` | 4 | 视频码率（Mbps） |
| `maxFps` | 60 | 最大帧率 |
| `isAudio` | 1 | 是否传输音频（1=是） |
| `supportH265` | 1 | 是否启用 H.265 编码 |
| `supportOpus` | 1 | 是否启用 Opus 音频 |
| `keepAwake` | 1 | 是否保持屏幕常亮 |
| `listenerClip` | 1 | 是否监听剪贴板同步 |
| `timeoutDelay` | 20000 | 心跳超时（毫秒） |

## License

本项目基于 [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy) 的设计思路实现，遵循 Apache 2.0 License。

---

**必须授予悬浮窗权限！** 不授予悬浮窗权限应用无法正常运行。
