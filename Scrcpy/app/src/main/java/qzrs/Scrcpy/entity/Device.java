package qzrs.Scrcpy.entity;

import java.util.Objects;

public class Device {
  public static final int TYPE_NETWORK = 1;
  public static final int TYPE_LINK = 2;
  
  // 连接模式：0=默认(ADB), 1=中继模式
  public static final int CONNECTION_MODE_DEFAULT = 0;
  public static final int CONNECTION_MODE_RELAY = 1;
  
  public final String uuid;
  public final int type;
  
  // 连接模式
  public int connectionMode = CONNECTION_MODE_DEFAULT;
  
  // P2P/中继配置
  public String stunServer = "stun:stun.l.google.com:19302";
  public String turnServer = "";
  public String turnUsername = "";
  public String turnPassword = "";
  public String relayServer = "";
  public int relayPort = 8000;
  public String relayToken = "";
  
  public String name;
  public String address = "";
  public String startApp = "";
  public int adbPort = 5555;
  public int serverPort = 25166;
  public boolean listenClip=true;
  public boolean isAudio = true;
  public int maxSize = 1600;
  public int maxFps = 60;
  public int maxVideoBit = 4;
  public boolean useH265 = true;
  public boolean connectOnStart = false;
  public boolean customResolutionOnConnect = false;
  public boolean wakeOnConnect = true;
  public boolean lightOffOnConnect = false;
  public boolean showNavBarOnConnect = true;
  public boolean changeToFullOnConnect = true;
  public boolean keepWakeOnRunning = true;
  public boolean changeResolutionOnRunning = false;
  public boolean smallToMiniOnRunning = false;
  public boolean fullToMiniOnRunning = true;
  public boolean miniTimeoutOnRunning = false;
  public boolean lockOnClose = true;
  public boolean lightOnClose = false;
  public boolean reconnectOnClose = false;
  public int customResolutionWidth = 1080;
  public int customResolutionHeight = 2400;
  public int smallX = 200;
  public int smallY = 200;
  public int smallLength = 800;
  public int smallXLan = 200;
  public int smallYLan = 200;
  public int smallLengthLan = 800;
  public int miniY = 200;

  public Device(String uuid, int type) {
    this.uuid = uuid;
    this.type = type;
    this.name = uuid;
  }

  public boolean isNetworkDevice() {
    return type == TYPE_NETWORK;
  }

  public boolean isLinkDevice() {
    return type == TYPE_LINK;
  }

  public boolean isTempDevice() {
    return Objects.equals(name, "----");
  }
  
  /**
   * 获取连接模式枚举
   */
  public int getConnectionMode() {
    return connectionMode;
  }
  
  /**
   * 判断是否使用中继模式
   */
  public boolean isRelayMode() {
    return connectionMode == CONNECTION_MODE_RELAY;
  }
  
  /**
   * 创建连接配置
   */
  public qzrs.Scrcpy.connection.ConnectionConfig createConnectionConfig() {
    qzrs.Scrcpy.connection.ConnectionConfig config = new qzrs.Scrcpy.connection.ConnectionConfig();
    config.setMode(qzrs.Scrcpy.connection.ConnectionMode.fromValue(connectionMode));
    config.setStunServer(stunServer);
    config.setTurnServer(turnServer);
    config.setTurnUsername(turnUsername);
    config.setTurnPassword(turnPassword);
    config.setRelayServer(relayServer);
    config.setRelayPort(relayPort);
    config.setRelayToken(relayToken);
    return config;
  }

  public Device clone(String uuid) {
    Device newDevice = new Device(uuid, type);
    newDevice.name = name;
    newDevice.address = address;
    newDevice.startApp = startApp;
    newDevice.adbPort = adbPort;
    newDevice.serverPort = serverPort;
    newDevice.listenClip = listenClip;
    newDevice.isAudio = isAudio;
    newDevice.maxSize = maxSize;
    newDevice.maxFps = maxFps;
    newDevice.maxVideoBit = maxVideoBit;
    newDevice.useH265 = useH265;
    newDevice.connectOnStart = connectOnStart;
    newDevice.customResolutionOnConnect = customResolutionOnConnect;
    newDevice.wakeOnConnect = wakeOnConnect;
    newDevice.lightOffOnConnect = lightOffOnConnect;
    newDevice.showNavBarOnConnect = showNavBarOnConnect;
    newDevice.changeToFullOnConnect = changeToFullOnConnect;
    newDevice.keepWakeOnRunning = keepWakeOnRunning;
    newDevice.changeResolutionOnRunning = changeResolutionOnRunning;
    newDevice.smallToMiniOnRunning = smallToMiniOnRunning;
    newDevice.fullToMiniOnRunning = fullToMiniOnRunning;
    newDevice.miniTimeoutOnRunning = miniTimeoutOnRunning;
    newDevice.lockOnClose = lockOnClose;
    newDevice.lightOnClose = lightOnClose;
    newDevice.reconnectOnClose = reconnectOnClose;

    newDevice.customResolutionWidth = customResolutionWidth;
    newDevice.customResolutionHeight = customResolutionHeight;
    newDevice.smallX = smallX;
    newDevice.smallY = smallY;
    newDevice.smallLength = smallLength;
    newDevice.smallXLan = smallXLan;
    newDevice.smallYLan = smallYLan;
    newDevice.smallLengthLan = smallLengthLan;
    newDevice.miniY = miniY;
    
    // 连接模式配置
    newDevice.connectionMode = connectionMode;
    newDevice.stunServer = stunServer;
    newDevice.turnServer = turnServer;
    newDevice.turnUsername = turnUsername;
    newDevice.turnPassword = turnPassword;
    newDevice.relayServer = relayServer;
    newDevice.relayPort = relayPort;
    newDevice.relayToken = relayToken;
    
    return newDevice;
  }
}
