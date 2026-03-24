package qzrs.Scrcpy.client.tools;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

import qzrs.Scrcpy.BuildConfig;
import qzrs.Scrcpy.R;
import qzrs.Scrcpy.adb.Adb;
import qzrs.Scrcpy.buffer.BufferStream;
import qzrs.Scrcpy.client.decode.DecodecTools;
import qzrs.Scrcpy.connection.ConnectionConfig;
import qzrs.Scrcpy.connection.ConnectionResult;
import qzrs.Scrcpy.connection.P2pClientConnector;
import qzrs.Scrcpy.connection.RelayClientConnector;
import qzrs.Scrcpy.entity.AppData;
import qzrs.Scrcpy.entity.Device;
import qzrs.Scrcpy.entity.MyInterface;
import qzrs.Scrcpy.helper.PublicTools;

public class ClientStream {
  private boolean isClose = false;
  private boolean connectDirect = false;
  private Adb adb;
  private Device device;  // 保存设备引用，用于 P2P 检测
  private Socket mainSocket;
  private Socket videoSocket;
  private OutputStream mainOutputStream;
  private DataInputStream mainDataInputStream;
  private DataInputStream videoDataInputStream;
  private BufferStream mainBufferStream;
  private BufferStream videoBufferStream;
  private BufferStream shell;
  private Thread connectThread = null;
  private static final String serverName = "/data/local/tmp/scrcpy_server_" + BuildConfig.VERSION_CODE + ".jar";
  private static final boolean supportH265 = DecodecTools.isSupportH265();
  private static final boolean supportOpus = DecodecTools.isSupportOpus();

  private static final int timeoutDelay = 1000 * 15;

  // 统计信息覆盖层
  private final StatsOverlay statsOverlay = new StatsOverlay();

  // 心跳包发送时间戳，用于计算RTT
  public long pingSendTime = 0;

  public StatsOverlay getStatsOverlay() {
    return statsOverlay;
  }

  public ClientStream(Device device, MyInterface.MyFunctionBoolean handle) {
    Thread timeOutThread = new Thread(() -> {
      try {
        Thread.sleep(timeoutDelay);
        PublicTools.logToast("stream", AppData.applicationContext.getString(R.string.toast_timeout), true);
        handle.run(false);
        if (connectThread != null) connectThread.interrupt();
      } catch (InterruptedException ignored) {
      }
    });
    connectThread = new Thread(() -> {
      try {
        // 保存设备引用
        ClientStream.this.device = device;
        
        adb = AdbTools.connectADB(device);
        startServer(device);
        connectServer(device);
        
        // 连接成功后，更新悬浮窗显示当前模式
        updateConnectionModeForOverlay(device);
        
        // 启动后台 P2P 检测线程（仅中继模式时）
        if (device.connectionMode == Device.CONNECTION_MODE_RELAY) {
          startP2PCheckThread();
        }
        
        handle.run(true);
      } catch (Exception e) {
        PublicTools.logToast("stream", e.toString(), true);
        handle.run(false);
      } finally {
        timeOutThread.interrupt();
      }
    });
    connectThread.start();
    timeOutThread.start();
  }

  private void startServer(Device device) throws Exception {
    if (BuildConfig.ENABLE_DEBUG_FEATURE || !adb.runAdbCmd("ls /data/local/tmp/scrcpy_*").contains(serverName)) {
      adb.runAdbCmd("rm /data/local/tmp/scrcpy_* ");
      adb.pushFile(AppData.applicationContext.getResources().openRawResource(R.raw.scrcpy_server), serverName, null);
    }
    shell = adb.getShell();
    shell.write(ByteBuffer.wrap(("app_process -Djava.class.path=" + serverName + " / qzrs.Scrcpy.server.Server"
      + " serverPort=" + device.serverPort
      + " listenClip=" + (device.listenClip ? 1 : 0)
      + " isAudio=" + (device.isAudio ? 1 : 0)
      + " maxSize=" + device.maxSize
      + " maxFps=" + device.maxFps
      + " maxVideoBit=" + device.maxVideoBit
      + " keepAwake=" + (device.keepWakeOnRunning ? 1 : 0)
      + " supportH265=" + ((device.useH265 && supportH265) ? 1 : 0)
      + " supportOpus=" + (supportOpus ? 1 : 0)
      + " startApp=" + device.startApp + " \n").getBytes()));
  }

  private void connectServer(Device device) throws Exception {
    Thread.sleep(50);
    int reTry = 40;
    int reTryTime = timeoutDelay / reTry;
    
    // 根据连接模式选择连接方式
    if (device.connectionMode == Device.CONNECTION_MODE_RELAY) {
      // 中继模式（会自动尝试直连，失败后回退到中继）
      connectRelay(device, reTry, reTryTime);
    } else {
      // 默认模式 (ADB 连接)
      connectDefault(device, reTry, reTryTime);
    }
  }
  
  /**
   * 默认模式连接 (原有逻辑)
   */
  private void connectDefault(Device device, int reTry, int reTryTime) throws Exception {
    if (!device.isLinkDevice()) {
      long startTime = System.currentTimeMillis();
      boolean mainConn = false;
      InetSocketAddress inetSocketAddress = new InetSocketAddress(PublicTools.getIp(device.address), device.serverPort);
      for (int i = 0; i < reTry; i++) {
        try {
          if (!mainConn) {
            mainSocket = new Socket();
            mainSocket.connect(inetSocketAddress, timeoutDelay / 2);
            mainConn = true;
          }
          videoSocket = new Socket();
          videoSocket.connect(inetSocketAddress, timeoutDelay / 2);
          mainOutputStream = mainSocket.getOutputStream();
          mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
          videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
          connectDirect = true;
          return;
        } catch (Exception ignored) {
          if (mainSocket != null) mainSocket.close();
          if (videoSocket != null) videoSocket.close();
          if (System.currentTimeMillis() - startTime >= timeoutDelay / 2 - 1000) i = reTry;
          else Thread.sleep(reTryTime);
        }
      }
    }
    for (int i = 0; i < reTry; i++) {
      try {
        if (mainBufferStream == null) mainBufferStream = adb.tcpForward(device.serverPort);
        if (videoBufferStream == null) videoBufferStream = adb.tcpForward(device.serverPort);
        return;
      } catch (Exception ignored) {
        Thread.sleep(reTryTime);
      }
    }
    throw new Exception(AppData.applicationContext.getString(R.string.toast_connect_server));
  }
  
  /**
   * P2P 直连模式
   * 使用 STUN/TURN 尝试建立 P2P 连接，失败时回退到默认模式
   */
  private void connectP2P(Device device, int reTry, int reTryTime) throws Exception {
    // P2P 连接逻辑 - 先尝试直连，失败则回退
    try {
      ConnectionConfig config = device.createConnectionConfig();
      P2pClientConnector p2pConnector = new P2pClientConnector(config);
      ConnectionResult result = p2pConnector.connect(device.address, device.serverPort);
      
      if (result.isSuccess()) {
        mainSocket = result.getMainSocket();
        videoSocket = result.getVideoSocket();
        mainOutputStream = result.getMainOutputStream();
        mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
        videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
        connectDirect = result.isDirect();
        p2pConnector.close();
        return;
      } else {
        // P2P 失败，回退到默认模式
        android.util.Log.w("ClientStream", "P2P connection failed, falling back to default: " + result.getErrorMessage());
        connectDefault(device, reTry, reTryTime);
      }
    } catch (Exception e) {
      // P2P 异常，回退到默认模式
      android.util.Log.w("ClientStream", "P2P exception, falling back to default: " + e.getMessage());
      connectDefault(device, reTry, reTryTime);
    }
  }
  
  /**
   * 中继模式
   * 通过中继服务器建立连接
   */
  private void connectRelay(Device device, int reTry, int reTryTime) throws Exception {
    try {
      ConnectionConfig config = device.createConnectionConfig();
      RelayClientConnector relayConnector = new RelayClientConnector(config);
      ConnectionResult result = relayConnector.connect(device.uuid);
      
      if (result.isSuccess()) {
        mainSocket = result.getMainSocket();
        videoSocket = result.getVideoSocket();
        mainOutputStream = result.getMainOutputStream();
        mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
        videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
        connectDirect = false; // 经过中继
        return;
      } else {
        // 中继失败，回退到默认模式
        android.util.Log.w("ClientStream", "Relay connection failed, falling back to default: " + result.getErrorMessage());
        connectDefault(device, reTry, reTryTime);
      }
    } catch (Exception e) {
      // 中继异常，回退到默认模式
      android.util.Log.w("ClientStream", "Relay exception, falling back to default: " + e.getMessage());
      connectDefault(device, reTry, reTryTime);
    }
  }

  /**
   * 更新悬浮窗显示当前连接模式
   */
  private void updateConnectionModeForOverlay(Device device) {
    int mode;
    if (connectDirect) {
      // connectDirect = true 表示直连模式（直接 TCP 连接）
      mode = Device.CONNECTION_MODE_DIRECT;
    } else {
      // 经过中继或其他方式
      mode = device.connectionMode;
    }
    statsOverlay.setConnectionMode(mode);
  }
  
  /**
   * 启动后台 P2P 检测线程
   * 每隔几秒尝试 P2P 直连，成功后切换
   */
  private void startP2PCheckThread() {
    Thread p2pCheckThread = new Thread(() -> {
      while (!isClose) {
        try {
          // 每 5 秒检测一次
          Thread.sleep(5000);
          
          // 尝试 P2P 直连
          if (tryP2PDirectConnect()) {
            android.util.Log.i("ClientStream", "P2P 直连成功，切换到直连模式");
            statsOverlay.setConnectionMode(Device.CONNECTION_MODE_DIRECT);
            // 可以选择断开中继连接，但为了简单，先保持
            break;
          }
        } catch (InterruptedException e) {
          break;
        } catch (Exception e) {
          android.util.Log.d("ClientStream", "P2P 检测异常: " + e.getMessage());
        }
      }
    });
    p2pCheckThread.setName("P2P-Check-Thread");
    p2pCheckThread.start();
  }
  
  /**
   * 尝试 P2P 直连
   * @return 是否成功
   */
  private boolean tryP2PDirectConnect() {
    try {
      // 使用 P2PClientConnector 尝试直连
      ConnectionConfig config = device.createConnectionConfig();
      P2pClientConnector p2pConnector = new P2pClientConnector(config);
      
      // 获取被控端的 IP 地址和端口
      String serverAddress = device.address;
      int serverPort = device.serverPort;
      
      ConnectionResult result = p2pConnector.connect(serverAddress, serverPort);
      
      if (result.isSuccess()) {
        // 保存原来的 socket
        Socket originalMainSocket = mainSocket;
        Socket originalVideoSocket = videoSocket;
        
        // 切换到直连
        mainSocket = result.getMainSocket();
        videoSocket = result.getVideoSocket();
        mainOutputStream = mainSocket.getOutputStream();
        mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
        videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
        
        // 关闭原来的 socket
        try { if (originalMainSocket != null) originalMainSocket.close(); } catch (Exception ignored) {}
        try { if (originalVideoSocket != null) originalVideoSocket.close(); } catch (Exception ignored) {}
        
        connectDirect = true;
        return true;
      }
    } catch (Exception e) {
      android.util.Log.d("ClientStream", "P2P 直连失败: " + e.getMessage());
    }
    return false;
  }

  public String runShell(String cmd) throws Exception {
    return adb.runAdbCmd(cmd);
  }

  public byte readByteFromMain() throws IOException, InterruptedException {
    if (connectDirect) return mainDataInputStream.readByte();
    else return mainBufferStream.readByte();
  }

  public byte readByteFromVideo() throws IOException, InterruptedException {
    if (connectDirect) return videoDataInputStream.readByte();
    else return videoBufferStream.readByte();
  }

  public int readIntFromMain() throws IOException, InterruptedException {
    if (connectDirect) return mainDataInputStream.readInt();
    else return mainBufferStream.readInt();
  }

  public int readIntFromVideo() throws IOException, InterruptedException {
    if (connectDirect) return videoDataInputStream.readInt();
    else return videoBufferStream.readInt();
  }

  public ByteBuffer readByteArrayFromMain(int size) throws IOException, InterruptedException {
    if (connectDirect) {
      byte[] buffer = new byte[size];
      mainDataInputStream.readFully(buffer);
      return ByteBuffer.wrap(buffer);
    } else return mainBufferStream.readByteArray(size);
  }

  public ByteBuffer readByteArrayFromVideo(int size) throws IOException, InterruptedException {
    if (connectDirect) {
      byte[] buffer = new byte[size];
      videoDataInputStream.readFully(buffer);
      return ByteBuffer.wrap(buffer);
    }
    return videoBufferStream.readByteArray(size);
  }

  public ByteBuffer readFrameFromMain() throws Exception {
    if (!connectDirect) mainBufferStream.flush();
    return readByteArrayFromMain(readIntFromMain());
  }

  public ByteBuffer readFrameFromVideo() throws Exception {
    if (!connectDirect) videoBufferStream.flush();
    int size = readIntFromVideo();
    return readByteArrayFromVideo(size);
  }

  public void writeToMain(ByteBuffer byteBuffer) throws Exception {
    if (connectDirect) mainOutputStream.write(byteBuffer.array());
    else mainBufferStream.write(byteBuffer);
  }

  /**
   * 发送 keepAlive 并测量 RTT 延迟，结果上报给 StatsOverlay
   */
  public void writeToMainWithLatency(ByteBuffer byteBuffer) throws Exception {
    pingSendTime = System.currentTimeMillis();
    writeToMain(byteBuffer);
  }

  public void close() {
    if (isClose) return;
    isClose = true;
    if (shell != null) PublicTools.logToast("server", new String(shell.readByteArrayBeforeClose().array()), false);
    if (connectDirect) {
      try {
        mainOutputStream.close();
        videoDataInputStream.close();
        mainDataInputStream.close();
        mainSocket.close();
        videoSocket.close();
      } catch (Exception ignored) {
      }
    } else {
      mainBufferStream.close();
      videoBufferStream.close();
    }
  }
}
