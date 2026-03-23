# Scrcpy 连接模式说明

## 概述

Scrcpy 安卓客户端支持三种连接模式：

| 模式 | 值 | 说明 |
|------|-----|------|
| 默认模式 | 0 | 传统的 ADB 网络连接或 USB 直连 |
| P2P 直连 | 1 | 通过 STUN/TURN 实现点对点连接 |
| 中继模式 | 2 | 通过中继服务器转发数据 |

---

## 1. 默认模式（Default）

传统的 Scrcpy 连接方式：

- **网络连接**：通过 IP 地址和端口直连
- **USB/ADB 连接**：通过 ADB 端口转发

### 配置参数
- `address`: 服务器 IP 地址
- `serverPort`: 服务器端口（默认 25166）
- `adbPort`: ADB 端口（默认 5555）

---

## 2. P2P 直连模式（P2P）

通过 ICE 协议实现点对点直连，失败时自动回退到 TURN 中继。

### 工作原理
```
手机 ─────────── 电脑
   ↓ 直接连接 ↓
  不经过服务器
```

### 配置参数
| 参数 | 说明 | 示例 |
|------|------|------|
| `stunServer` | STUN 服务器地址 | `stun:stun.l.google.com:19302` |
| `turnServer` | TURN 服务器地址（可选） | `turn:your-turn-server.com:3478` |
| `turnUsername` | TURN 用户名 | `user` |
| `turnPassword` | TURN 密码 | `password` |

### 使用条件
- 双方都能访问互联网
- 网络不是严格对称 NAT
- 可选：配置 TURN 服务器提高成功率

### 免费 STUN 服务器
- `stun:stun.l.google.com:19302`
- `stun:stun1.l.google.com:19302`
- `stun:stun2.l.google.com:19302`

---

## 3. 中继模式（Relay）

通过中继服务器转发数据，100% 能连接。

### 工作原理
```
手机 ──→ 中继服务器 ──→ 电脑
   所有数据经过服务器
```

### 配置参数
| 参数 | 说明 | 示例 |
|------|------|------|
| `relayServer` | 中继服务器地址 | `relay.example.com` |
| `relayPort` | 中继服务器端口 | `8000` |
| `relayToken` | 认证令牌 | `your-token` |

### 优点
- 100% 连接成功率
- 适合复杂网络环境（对称 NAT、防火墙）
- 容易穿透

### 缺点
- 延迟较高
- 需要中继服务器

---

## 代码使用示例

### 在 Device 中设置连接模式
```java
Device device = new Device(uuid, Device.TYPE_NETWORK);
device.address = "192.168.1.100";
device.serverPort = 25166;

// 设置为 P2P 模式
device.connectionMode = Device.CONNECTION_MODE_P2P;
device.stunServer = "stun:stun.l.google.com:19302";
// 可选：配置 TURN
device.turnServer = "turn:your-turn-server.com:3478";
device.turnUsername = "user";
device.turnPassword = "password";

// 或者设置为中继模式
device.connectionMode = Device.CONNECTION_MODE_RELAY;
device.relayServer = "relay.example.com";
device.relayPort = 8000;
device.relayToken = "your-token";
```

### 使用 ConnectionManager 连接
```java
import qzrs.Scrcpy.connection.*;

ConnectionManager manager = ConnectionManager.getInstance();
ConnectionResult result = manager.connect(device);

if (result.isSuccess()) {
    // 连接成功
    InputStream videoStream = result.getVideoInputStream();
    OutputStream controlStream = result.getMainOutputStream();
} else {
    // 连接失败
    Log.e("Connection failed: " + result.getErrorMessage());
}
```

### 启动服务器监听（Android 设备侧）
```java
ConnectionManager manager = ConnectionManager.getInstance();
ConnectionConfig config = device.createConnectionConfig();

int port = manager.startServer(ConnectionMode.P2P, config, 0);
// 返回实际监听的端口
Log.d("Server started on port: " + port);
```

---

## 中继服务器协议

### 客户端请求格式
```
AUTH <deviceId> <token>
CONNECT <deviceId>
CONNECT <deviceId> VIDEO
PING
```

### 服务器响应格式
```
OK
CONNECTED
ERROR <message>
```

---

## 性能对比

| 模式 | 延迟 | 成功率 | 服务器成本 |
|------|------|--------|------------|
| 默认 | 低 | 取决于网络 | 无 |
| P2P | 低 | 70-80% | 可选（TURN） |
| 中继 | 中等 | 100% | 必须 |

---

## 注意事项

1. **P2P 模式**需要至少一方有公网 IP 或配置 TURN 服务器
2. **中继模式**需要自建或使用第三方中继服务器
3. 首次使用建议先用默认模式，确认基础功能正常
4. 修改连接模式后需要重新连接设备
