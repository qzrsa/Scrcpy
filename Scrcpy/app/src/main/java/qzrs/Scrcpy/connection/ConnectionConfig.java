package qzrs.Scrcpy.connection;

import java.util.Objects;

/**
 * 连接配置，包含各种连接模式的参数
 */
public class ConnectionConfig {
    
    // 连接模式
    private ConnectionMode mode = ConnectionMode.DEFAULT;
    
    // P2P 配置
    private String stunServer = "stun:stun.l.google.com:19302";
    private String turnServer = "";
    private String turnUsername = "";
    private String turnPassword = "";
    private int p2pTimeout = 15000;  // P2P 连接超时（毫秒）
    
    // 中转模式配置
    private String relayServer = "";
    private int relayPort = 8000;
    private String relayToken = "";
    
    // ICE 服务器列表（STUN + 可选的 TURN）
    private String iceServers;

    public ConnectionMode getMode() {
        return mode;
    }

    public void setMode(ConnectionMode mode) {
        this.mode = mode;
    }

    public String getStunServer() {
        return stunServer;
    }

    public void setStunServer(String stunServer) {
        this.stunServer = stunServer;
    }

    public String getTurnServer() {
        return turnServer;
    }

    public void setTurnServer(String turnServer) {
        this.turnServer = turnServer;
    }

    public String getTurnUsername() {
        return turnUsername;
    }

    public void setTurnUsername(String turnUsername) {
        this.turnUsername = turnUsername;
    }

    public String getTurnPassword() {
        return turnPassword;
    }

    public void setTurnPassword(String turnPassword) {
        this.turnPassword = turnPassword;
    }

    public int getP2pTimeout() {
        return p2pTimeout;
    }

    public void setP2pTimeout(int p2pTimeout) {
        this.p2pTimeout = p2pTimeout;
    }

    public String getRelayServer() {
        return relayServer;
    }

    public void setRelayServer(String relayServer) {
        this.relayServer = relayServer;
    }

    public int getRelayPort() {
        return relayPort;
    }

    public void setRelayPort(int relayPort) {
        this.relayPort = relayPort;
    }

    public String getRelayToken() {
        return relayToken;
    }

    public void setRelayToken(String relayToken) {
        this.relayToken = relayToken;
    }

    /**
     * 生成 ICE 服务器配置字符串
     * 格式用于 WebRTC 或类似库
     */
    public String getIceServers() {
        if (iceServers != null) {
            return iceServers;
        }
        
        StringBuilder sb = new StringBuilder();
        
        // STUN 服务器
        sb.append(stunServer);
        
        // TURN 服务器（如果有）
        if (!Objects.isNull(turnServer) && !turnServer.isEmpty()) {
            sb.append("|turn:");
            sb.append(turnServer);
            if (!turnUsername.isEmpty() && !turnPassword.isEmpty()) {
                sb.append("|").append(turnUsername);
                sb.append("|").append(turnPassword);
            }
        }
        
        return sb.toString();
    }

    public void setIceServers(String iceServers) {
        this.iceServers = iceServers;
    }

    /**
     * 默认的公共 STUN 服务器
     */
    public static String[] DEFAULT_STUN_SERVERS = {
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302", 
        "stun:stun2.l.google.com:19302"
    };
}
