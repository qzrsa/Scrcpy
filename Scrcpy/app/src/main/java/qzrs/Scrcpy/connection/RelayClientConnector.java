package qzrs.Scrcpy.connection;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import qzrs.Scrcpy.helper.LogHelper;

/**
 * 中转模式连接器 - 客户端侧
 * 
 * 通过中继服务器建立连接：
 * 客户端 -> 中继服务器 -> 目标设备
 */
public class RelayClientConnector {
    
    private static final String TAG = "RelayClient";
    
    private ConnectionConfig config;
    private Socket mainSocket;
    private Socket videoSocket;
    
    // 连接状态
    private volatile boolean isConnected = false;
    private AtomicBoolean isConnecting = new AtomicBoolean(false);
    
    // 心跳
    private Thread heartbeatThread;
    private volatile boolean heartbeatRunning = false;
    
    public RelayClientConnector(ConnectionConfig config) {
        this.config = config != null ? config : new ConnectionConfig();
    }
    
    /**
     * 连接到中继服务器
     * @param deviceId 目标设备 ID
     * @return 连接结果
     */
    public ConnectionResult connect(String deviceId) {
        if (isConnecting.compareAndSet(false, true)) {
            isConnected = false;
        } else {
            return ConnectionResult.fail("Already connecting or connected");
        }
        
        ConnectionResult result = ConnectionResult.fail("Unknown error");
        
        try {
            String relayServer = config.getRelayServer();
            int relayPort = config.getRelayPort();
            String token = config.getRelayToken();
            
            if (relayServer == null || relayServer.isEmpty()) {
                LogHelper.e("RelayClient", "中继服务器未配置");
                return ConnectionResult.fail("No relay server configured");
            }
            
            LogHelper.i("RelayClient", "正在连接中继服务器: " + relayServer + ":" + relayPort);
            
            // 步骤 1：建立到中继服务器的连接
            LogHelper.i("RelayClient", "步骤1: 建立TCP连接...");
            mainSocket = new Socket();
            mainSocket.connect(new InetSocketAddress(relayServer, relayPort), 10000);
            mainSocket.setSoTimeout(30000);
            
            DataOutputStream out = new DataOutputStream(mainSocket.getOutputStream());
            DataInputStream in = new DataInputStream(mainSocket.getInputStream());
            
            // 步骤 2：认证
            LogHelper.i("RelayClient", "步骤2: 发送认证请求...");
            String authRequest = buildAuthRequest(deviceId, token);
            LogHelper.d("RelayClient", "认证请求: " + authRequest.trim());
            out.write(authRequest.getBytes(StandardCharsets.UTF_8));
            out.flush();
            
            // 读取认证响应
            String authResponse = readLine(in);
            LogHelper.d("RelayClient", "认证响应: " + authResponse);
            if (!authResponse.startsWith("OK")) {
                LogHelper.e("RelayClient", "认证失败: " + authResponse);
                return ConnectionResult.fail("Authentication failed: " + authResponse);
            }
            
            LogHelper.i("RelayClient", "认证成功");
            
            // 步骤 3：请求连接到目标设备
            LogHelper.i("RelayClient", "步骤3: 请求连接设备...");
            String connectRequest = "CONNECT " + deviceId + "\n";
            LogHelper.d("RelayClient", "连接请求: " + connectRequest.trim());
            out.write(connectRequest.getBytes(StandardCharsets.UTF_8));
            out.flush();
            
            // 读取连接响应
            String connectResponse = readLine(in);
            LogHelper.d("RelayClient", "连接响应: " + connectResponse);
            if (!connectResponse.startsWith("CONNECTED")) {
                LogHelper.e("RelayClient", "连接设备失败: " + connectResponse);
                return ConnectionResult.fail("Failed to connect to device: " + connectResponse);
            }
            
            LogHelper.i("RelayClient", "设备连接成功");
            
            // 步骤 4：建立视频连接（同一连接复用或新建）
            LogHelper.i("RelayClient", "步骤4: 建立视频通道...");
            videoSocket.connect(new InetSocketAddress(relayServer, relayPort), 10000);
            
            DataOutputStream videoOut = new DataOutputStream(videoSocket.getOutputStream());
            DataInputStream videoIn = new DataInputStream(videoSocket.getInputStream());
            
            // 视频通道认证
            LogHelper.i("RelayClient", "视频通道: 发送认证...");
            String videoAuthRequest = buildAuthRequest(deviceId, token);
            videoOut.write(videoAuthRequest.getBytes(StandardCharsets.UTF_8));
            videoOut.flush();
            
            String videoAuthResponse = readLine(videoIn);
            LogHelper.d("RelayClient", "视频认证响应: " + videoAuthResponse);
            if (!videoAuthResponse.startsWith("OK")) {
                LogHelper.e("RelayClient", "视频通道认证失败");
                return ConnectionResult.fail("Video channel auth failed");
            }
            
            // 请求视频通道
            String videoConnectRequest = "CONNECT " + deviceId + " VIDEO\n";
            videoOut.write(videoConnectRequest.getBytes(StandardCharsets.UTF_8));
            videoOut.flush();
            
            String videoConnectResponse = readLine(videoIn);
            LogHelper.d("RelayClient", "视频连接响应: " + videoConnectResponse);
            if (!videoConnectResponse.startsWith("CONNECTED")) {
                LogHelper.e("RelayClient", "视频通道连接失败");
                return ConnectionResult.fail("Failed to establish video channel");
            }
            
            // 构建结果
            LogHelper.i("RelayClient", "中继连接全部建立成功！");
            result = ConnectionResult.success(ConnectionMode.RELAY);
            result.setMainSocket(mainSocket);
            result.setMainInputStream(in);
            result.setMainOutputStream(out);
            result.setVideoSocket(videoSocket);
            result.setVideoInputStream(videoIn);
            result.setVideoOutputStream(videoOut);
            result.setRemoteAddress(relayServer);
            result.setRemotePort(relayPort);
            result.setDirect(false); // 经过了中继
            
            isConnected = true;
            
            // 启动心跳
            startHeartbeat(out);
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Relay connection error", e);
            result.setErrorMessage(e.getMessage());
            return result;
        } finally {
            isConnecting.set(false);
        }
    }
    
    /**
     * 构建认证请求
     */
    private String buildAuthRequest(String deviceId, String token) {
        // 简单的认证协议
        return "AUTH " + deviceId + " " + token + "\n";
    }
    
    /**
     * 读取一行数据
     */
    private String readLine(DataInputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            sb.append((char) c);
        }
        return sb.toString();
    }
    
    /**
     * 启动心跳线程
     */
    private void startHeartbeat(DataOutputStream out) {
        heartbeatRunning = true;
        heartbeatThread = new Thread(() -> {
            while (heartbeatRunning && isConnected) {
                try {
                    Thread.sleep(10000); // 10 秒心跳
                    out.write("PING\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (Exception e) {
                    Log.e(TAG, "Heartbeat error", e);
                    break;
                }
            }
        });
        heartbeatThread.start();
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        heartbeatRunning = false;
        isConnected = false;
        
        try {
            if (heartbeatThread != null) heartbeatThread.interrupt();
        } catch (Exception ignored) {}
        
        closeSockets();
    }
    
    /**
     * 关闭所有 Socket
     */
    private void closeSockets() {
        try { if (mainSocket != null) mainSocket.close(); } catch (Exception ignored) {}
        try { if (videoSocket != null) videoSocket.close(); } catch (Exception ignored) {}
    }
    
    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return isConnected;
    }
}
