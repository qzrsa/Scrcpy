package qzrs.Scrcpy.connection;

import android.util.Log;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import qzrs.Scrcpy.client.tools.AdbTools;
import qzrs.Scrcpy.entity.Device;

/**
 * 统一连接管理器
 * 
 * 负责根据连接模式选择合适的连接器：
 * - DEFAULT: 原有 ADB 连接方式
 * - P2P: P2P 直连/TURN 中继
 * - RELAY: 中继服务器模式
 */
public class ConnectionManager {
    
    private static final String TAG = "ConnectionManager";
    
    // 单例
    private static ConnectionManager instance;
    
    // 当前活动的连接器
    private P2pClientConnector p2pClientConnector;
    private P2pServerConnector p2pServerConnector;
    private RelayClientConnector relayClientConnector;
    private RelayServerConnector relayServerConnector;
    
    // 连接结果缓存
    private final Map<String, ConnectionResult> activeConnections = new HashMap<>();
    
    private ConnectionManager() {}
    
    public static synchronized ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }
    
    /**
     * 建立连接（客户端调用）
     * @param device 目标设备
     * @return 连接结果
     */
    public ConnectionResult connect(Device device) {
        return connect(device, null);
    }
    
    /**
     * 建立连接（带配置）
     * @param device 目标设备
     * @param config 连接配置（可选）
     * @return 连接结果
     */
    public ConnectionResult connect(Device device, ConnectionConfig config) {
        if (config == null) {
            config = createDefaultConfig(device);
        }
        
        ConnectionMode mode = config.getMode();
        Log.d(TAG, "Connecting in mode: " + mode);
        
        switch (mode) {
            case P2P:
                return connectP2P(device, config);
            case RELAY:
                return connectRelay(device, config);
            case DEFAULT:
            default:
                return connectDefault(device);
        }
    }
    
    /**
     * 默认连接（原有方式）
     */
    private ConnectionResult connectDefault(Device device) {
        // 使用原有的 ClientStream 连接逻辑
        // 这里返回失败，提示使用原有方式
        return ConnectionResult.fail("Use default ADB connection");
    }
    
    /**
     * P2P 连接
     */
    private ConnectionResult connectP2P(Device device, ConnectionConfig config) {
        try {
            // 关闭旧连接器
            if (p2pClientConnector != null) {
                p2pClientConnector.close();
            }
            
            p2pClientConnector = new P2pClientConnector(config);
            
            // 尝试 P2P 连接
            // 注意：这里需要服务器地址，实际使用时从设备信息获取
            String serverAddress = device.address;
            int serverPort = device.serverPort;
            
            ConnectionResult result = p2pClientConnector.connect(serverAddress, serverPort);
            
            if (result.isSuccess()) {
                activeConnections.put(device.uuid, result);
            }
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "P2P connection failed", e);
            return ConnectionResult.fail("P2P error: " + e.getMessage());
        }
    }
    
    /**
     * 中继连接
     */
    private ConnectionResult connectRelay(Device device, ConnectionConfig config) {
        try {
            // 关闭旧连接器
            if (relayClientConnector != null) {
                relayClientConnector.disconnect();
            }
            
            relayClientConnector = new RelayClientConnector(config);
            
            ConnectionResult result = relayClientConnector.connect(device.uuid);
            
            if (result.isSuccess()) {
                activeConnections.put(device.uuid, result);
            }
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Relay connection failed", e);
            return ConnectionResult.fail("Relay error: " + e.getMessage());
        }
    }
    
    /**
     * 启动服务器监听（服务端调用）
     */
    public int startServer(ConnectionMode mode, ConnectionConfig config, int port) throws IOException {
        switch (mode) {
            case P2P:
                if (p2pServerConnector != null) {
                    p2pServerConnector.stopServer();
                }
                p2pServerConnector = new P2pServerConnector(config);
                return p2pServerConnector.startServer(port);
                
            case RELAY:
                if (relayServerConnector != null) {
                    relayServerConnector.stopServer();
                }
                relayServerConnector = new RelayServerConnector(config);
                return relayServerConnector.startServer(port);
                
            default:
                throw new IllegalArgumentException("Default mode cannot start server");
        }
    }
    
    /**
     * 获取活动连接
     */
    public ConnectionResult getConnection(String deviceUuid) {
        return activeConnections.get(deviceUuid);
    }
    
    /**
     * 断开连接
     */
    public void disconnect(String deviceUuid) {
        ConnectionResult result = activeConnections.remove(deviceUuid);
        if (result != null) {
            result.closeAll();
        }
    }
    
    /**
     * 断开所有连接
     */
    public void disconnectAll() {
        for (ConnectionResult result : activeConnections.values()) {
            result.closeAll();
        }
        activeConnections.clear();
        
        if (p2pClientConnector != null) {
            p2pClientConnector.close();
        }
        if (relayClientConnector != null) {
            relayClientConnector.disconnect();
        }
    }
    
    /**
     * 从设备创建默认配置
     */
    private ConnectionConfig createDefaultConfig(Device device) {
        ConnectionConfig config = new ConnectionConfig();
        
        // 根据设备类型设置默认模式
        if (device.isNetworkDevice()) {
            config.setMode(ConnectionMode.DEFAULT);
        } else {
            config.setMode(ConnectionMode.DEFAULT);
        }
        
        // 设置默认 STUN 服务器
        config.setStunServer("stun:stun.l.google.com:19302");
        
        return config;
    }
    
    /**
     * 根据设备 ID 设置连接配置
     */
    public void setConnectionConfig(String deviceUuid, ConnectionConfig config) {
        // 可以持久化到本地存储
        // 这里简单保存在内存中
    }
    
    /**
     * 获取设备的连接配置
     */
    public ConnectionConfig getConnectionConfig(String deviceUuid) {
        // 从存储中读取
        return createDefaultConfig(null);
    }
}
