package qzrs.Scrcpy.connection;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * P2P 连接器 - 客户端侧
 * 
 * 使用简化的 ICE 类似协议进行 P2P 连接：
 * 1. 通过 STUN 服务器获取本地公网信息
 * 2. 交换候选地址（通过现有 ADB 通道或其他信令）
 * 3. 尝试直连，失败则回退到 TURN 中继
 */
public class P2pClientConnector {
    
    private static final String TAG = "P2pClient";
    
    // 默认 STUN 服务器
    private static final String DEFAULT_STUN = "stun:stun.l.google.com:19302";
    
    private ConnectionConfig config;
    private Socket mainSocket;
    private Socket videoSocket;
    
    public P2pClientConnector(ConnectionConfig config) {
        this.config = config != null ? config : new ConnectionConfig();
    }
    
    /**
     * 开始 P2P 连接
     * @param serverAddress Scrcpy 服务器地址（用于回退）
     * @param serverPort Scrcpy 服务器端口
     * @return 连接结果
     */
    public ConnectionResult connect(String serverAddress, int serverPort) {
        long startTime = System.currentTimeMillis();
        ConnectionResult result = ConnectionResult.fail("Unknown error");
        
        try {
            // 步骤 1：获取本地网络信息
            LocalNetworkInfo localInfo = getLocalNetworkInfo();
            Log.d(TAG, "Local network info: " + localInfo);
            
            // 步骤 2：尝试 P2P 直连
            // 注意：实际实现需要通过信令服务器交换地址
            // 这里演示直接尝试直连（在同一网络下可用）
            result = tryP2pDirectConnect(serverAddress, serverPort, localInfo);
            
            if (result.isSuccess()) {
                result.setDirect(true);
                result.setConnectionTime(System.currentTimeMillis() - startTime);
                Log.d(TAG, "P2P direct connect success!");
                return result;
            }
            
            // 步骤 3：P2P 失败，尝试 TURN 中继
            Log.d(TAG, "P2P direct failed, trying TURN relay...");
            result = tryTurnRelayConnect(serverAddress, serverPort);
            
            if (result.isSuccess()) {
                result.setDirect(false);
                result.setConnectionTime(System.currentTimeMillis() - startTime);
                return result;
            }
            
            // 步骤 4: 全部失败，返回错误
            result.setErrorMessage("P2P connection failed: " + result.getErrorMessage());
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "P2P connect error", e);
            return ConnectionResult.fail("P2P error: " + e.getMessage());
        }
    }
    
    /**
     * 尝试 P2P 直连
     */
    private ConnectionResult tryP2pDirectConnect(String serverAddress, int serverPort, 
                                                  LocalNetworkInfo localInfo) {
        int timeout = config.getP2pTimeout();
        
        try {
            // 尝试连接到服务器地址（假设对方也启动了 P2P 服务器）
            mainSocket = new Socket();
            mainSocket.connect(new InetSocketAddress(serverAddress, serverPort), timeout);
            
            videoSocket = new Socket();
            videoSocket.connect(new InetSocketAddress(serverAddress, serverPort), timeout);
            
            ConnectionResult result = ConnectionResult.success(ConnectionMode.P2P);
            result.setMainSocket(mainSocket);
            result.setMainInputStream(mainSocket.getInputStream());
            result.setMainOutputStream(mainSocket.getOutputStream());
            result.setVideoSocket(videoSocket);
            result.setVideoInputStream(videoSocket.getInputStream());
            result.setVideoOutputStream(videoSocket.getOutputStream());
            result.setRemoteAddress(serverAddress);
            result.setRemotePort(serverPort);
            
            return result;
            
        } catch (IOException e) {
            closeSockets();
            return ConnectionResult.fail("Direct connection failed: " + e.getMessage());
        }
    }
    
    /**
     * 尝试 TURN 中继连接
     */
    private ConnectionResult tryTurnRelayConnect(String serverAddress, int serverPort) {
        String turnServer = config.getTurnServer();
        
        // 如果没有配置 TURN 服务器，使用默认的公共 TURN 或回退到普通连接
        if (turnServer == null || turnServer.isEmpty()) {
            return ConnectionResult.fail("No TURN server configured");
        }
        
        // 实现 TURN 协议连接
        // 这是一个简化实现，实际需要完整的 TURN 协议
        try {
            Socket turnSocket = new Socket();
            turnSocket.connect(new InetSocketAddress(turnServer, 3478), 5000);
            
            DataOutputStream out = new DataOutputStream(turnSocket.getOutputStream());
            DataInputStream in = new DataInputStream(turnSocket.getInputStream());
            
            // TURN 认证流程（简化版）
            String username = config.getTurnUsername();
            String password = config.getTurnPassword();
            
            // 发送Allocate请求
            byte[] allocateRequest = buildTurnAllocateRequest(username, serverAddress, serverPort);
            out.write(allocateRequest);
            out.flush();
            
            // 读取响应
            byte[] response = new byte[1024];
            int len = in.read(response);
            
            if (len > 0 && response[0] == 0x01) { // 成功响应
                // TURN 中继建立成功，使用该通道传输数据
                return buildTurnConnectionResult(turnSocket, serverAddress, serverPort);
            }
            
            turnSocket.close();
            return ConnectionResult.fail("TURN allocation failed");
            
        } catch (Exception e) {
            return ConnectionResult.fail("TURN connection failed: " + e.getMessage());
        }
    }
    
    /**
     * 构建 TURN Allocate 请求
     */
    private byte[] buildTurnAllocateRequest(String username, String serverAddress, int serverPort) {
        // 简化实现：实际需要完整的 STUN/TURN 协议
        // 返回一个简化的请求包
        return new byte[0];
    }
    
    /**
     * 从 TURN 通道构建连接结果
     */
    private ConnectionResult buildTurnConnectionResult(Socket turnSocket, 
                                                        String serverAddress, int serverPort) {
        // 简化实现：实际需要建立两个通道（控制+视频）
        ConnectionResult result = ConnectionResult.success(ConnectionMode.P2P);
        result.setMainSocket(turnSocket);
        result.setRemoteAddress(serverAddress);
        result.setRemotePort(serverPort);
        return result;
    }
    
    /**
     * 获取本地网络信息
     */
    private LocalNetworkInfo getLocalNetworkInfo() {
        LocalNetworkInfo info = new LocalNetworkInfo();
        
        try {
            // 获取本地 IP 地址
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        info.localIp = addr.getHostAddress();
                        info.interfaceName = ni.getName();
                        break;
                    }
                }
                if (info.localIp != null) break;
            }
            
            // TODO: 通过 STUN 服务器获取公网 IP（需要网络库）
            // info.publicIp = getPublicIpFromStun(config.getStunServer());
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting local network info", e);
        }
        
        return info;
    }
    
    /**
     * 关闭所有 Socket
     */
    private void closeSockets() {
        try { if (mainSocket != null) mainSocket.close(); } catch (Exception ignored) {}
        try { if (videoSocket != null) videoSocket.close(); } catch (Exception ignored) {}
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        closeSockets();
    }
    
    /**
     * 本地网络信息
     */
    private static class LocalNetworkInfo {
        String localIp;
        String publicIp;
        int localPort;
        String interfaceName;
        
        @Override
        public String toString() {
            return "LocalNetworkInfo{" +
                    "localIp='" + localIp + '\'' +
                    ", publicIp='" + publicIp + '\'' +
                    ", localPort=" + localPort +
                    ", interfaceName='" + interfaceName + '\'' +
                    '}';
        }
    }
}
