package qzrs.Scrcpy.connection;

/**
 * 连接模式枚举
 */
public enum ConnectionMode {
    /**
     * 默认模式：ADB 网络连接或本地转发
     */
    DEFAULT(0, "默认模式"),
    
    /**
     * P2P 外网直连：通过 ICE/STUN/TURN 建立点对点连接
     */
    P2P(1, "P2P 直连"),
    
    /**
     * 中转模式：通过中继服务器转发数据
     */
    RELAY(2, "中转模式");

    private final int value;
    private final String displayName;

    ConnectionMode(int value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public int getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ConnectionMode fromValue(int value) {
        for (ConnectionMode mode : values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
