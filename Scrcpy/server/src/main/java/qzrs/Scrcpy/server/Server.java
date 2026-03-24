/*
 * 本项目大量借鉴学习了开源投屏软件：Scrcpy，在此对该项目表示感谢
 */
package qzrs.Scrcpy.server;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.os.IInterface;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import qzrs.Scrcpy.server.entity.Device;
import qzrs.Scrcpy.server.entity.Options;
import qzrs.Scrcpy.server.helper.AudioEncode;
import qzrs.Scrcpy.server.helper.ControlPacket;
import qzrs.Scrcpy.server.helper.VideoEncode;
import qzrs.Scrcpy.server.wrappers.ClipboardManager;
import qzrs.Scrcpy.server.wrappers.DisplayManager;
import qzrs.Scrcpy.server.wrappers.InputManager;
import qzrs.Scrcpy.server.wrappers.SurfaceControl;
import qzrs.Scrcpy.server.wrappers.WindowManager;

// 此部分代码摘抄借鉴了著名投屏软件Scrcpy的开源代码(https://github.com/Genymobile/scrcpy/tree/master/server)
public final class Server {
  private static Socket mainSocket;
  private static Socket videoSocket;
  private static OutputStream mainOutputStream;
  private static OutputStream videoOutputStream;
  public static DataInputStream mainInputStream;

  private static final Object object = new Object();

  private static final int timeoutDelay = 1000 * 20;

  public static void main(String... args) {
    try {
      Thread timeOutThread = new Thread(() -> {
        try {
          Thread.sleep(timeoutDelay);
          release();
        } catch (InterruptedException ignored) {
        }
      });
      timeOutThread.start();
      // 解析参数
      Options.parse(args);
      // 初始化
      setManagers();
      Device.init();
      // 连接
      connectClient();
      // 初始化子服务
      boolean canAudio = AudioEncode.init();
      VideoEncode.init();
      // 启动
      ArrayList<Thread> threads = new ArrayList<>();
      threads.add(new Thread(Server::executeVideoOut));
      if (canAudio) {
        threads.add(new Thread(Server::executeAudioIn));
        threads.add(new Thread(Server::executeAudioOut));
      }
      threads.add(new Thread(Server::executeControlIn));
      for (Thread thread : threads) thread.setPriority(Thread.MAX_PRIORITY);
      for (Thread thread : threads) thread.start();
      // 程序运行
      timeOutThread.interrupt();
      synchronized (object) {
        object.wait();
      }
      // 终止子服务
      for (Thread thread : threads) thread.interrupt();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      // 释放资源
      release();
    }
  }

  private static Method GET_SERVICE_METHOD;

  @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
  private static void setManagers() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    GET_SERVICE_METHOD = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
    // 1
    WindowManager.init(getService("window", "android.view.IWindowManager"));
    // 2
    DisplayManager.init(Class.forName("android.hardware.display.DisplayManagerGlobal").getDeclaredMethod("getInstance").invoke(null));
    // 3
    Class<?> inputManagerClass;
    try {
      inputManagerClass = Class.forName("android.hardware.input.InputManagerGlobal");
    } catch (ClassNotFoundException e) {
      inputManagerClass = android.hardware.input.InputManager.class;
    }
    InputManager.init(inputManagerClass.getDeclaredMethod("getInstance").invoke(null));
    // 4
    ClipboardManager.init(getService("clipboard", "android.content.IClipboard"));
    // 5
    SurfaceControl.init();
  }

  private static IInterface getService(String service, String type) {
    try {
      IBinder binder = (IBinder) GET_SERVICE_METHOD.invoke(null, service);
      Method asInterfaceMethod = Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class);
      return (IInterface) asInterfaceMethod.invoke(null, binder);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static void connectClient() throws IOException {
    // 检查是否启用中继模式
    if (Options.isRelayMode()) {
      // 中继模式：通过中继服务器连接
      connectViaRelay();
    } else {
      // 普通模式：直接监听端口
      try (ServerSocket serverSocket = new ServerSocket(Options.serverPort)) {
        mainSocket = serverSocket.accept();
        videoSocket = serverSocket.accept();
        mainOutputStream = mainSocket.getOutputStream();
        videoOutputStream = videoSocket.getOutputStream();
        mainInputStream = new DataInputStream(mainSocket.getInputStream());
        // 关闭TCP的Nagle算法，避免小包缓冲
        mainSocket.setTcpNoDelay(true);
      }
    }
  }
  
  /**
   * 通过中继服务器连接
   */
  private static void connectViaRelay() throws IOException {
    System.out.println("[Relay] 连接到中继服务器: " + Options.relayServer + ":" + Options.relayPort);
    System.out.println("[Relay] 设备UUID: " + Options.deviceUUID);
    
    try {
      // 连接到中继服务器
      java.net.Socket relaySocket = new java.net.Socket();
      relaySocket.connect(new java.net.InetSocketAddress(Options.relayServer, Options.relayPort), 10000);
      
      java.io.OutputStream out = relaySocket.getOutputStream();
      java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(relaySocket.getInputStream()));
      
      // 发送注册请求
      String registerRequest = "AUTH " + Options.deviceUUID + " " + Options.relayToken + "\n";
      out.write(registerRequest.getBytes());
      out.flush();
      
      // 等待注册响应
      String response = in.readLine();
      System.out.println("[Relay] 注册响应: " + response);
      
      if (response != null && response.startsWith("OK")) {
        // 注册成功，等待连接
        System.out.println("[Relay] 注册成功，等待控制端连接...");
        
        // 读取主连接和视频连接
        String mainResponse = in.readLine();
        String videoResponse = in.readLine();
        
        if (mainResponse != null && mainResponse.startsWith("CONNECTED")) {
          mainSocket = relaySocket;
          mainOutputStream = out;
          mainInputStream = new DataInputStream(relaySocket.getInputStream());
          mainSocket.setTcpNoDelay(true);
        }
        
        if (videoResponse != null && videoResponse.startsWith("CONNECT VIDEO")) {
          // 创建独立的视频连接
          java.net.Socket videoRelaySocket = new java.net.Socket();
          videoRelaySocket.connect(new java.net.InetSocketAddress(Options.relayServer, Options.relayPort), 10000);
          
          String videoConnectRequest = "CONNECT " + Options.deviceUUID + " VIDEO\n";
          videoRelaySocket.getOutputStream().write(videoConnectRequest.getBytes());
          videoRelaySocket.getOutputStream().flush();
          
          videoSocket = videoRelaySocket;
          videoOutputStream = videoRelaySocket.getOutputStream();
          videoSocket.setTcpNoDelay(true);
        }
        
        System.out.println("[Relay] 控制端已连接");
      } else {
        throw new IOException("中继注册失败: " + response);
      }
    } catch (Exception e) {
      System.out.println("[Relay] 中继连接失败: " + e.getMessage());
      throw new IOException("中继连接失败: " + e.getMessage());
    }
  }

  private static void executeVideoOut() {
    try {
      int frame = 0;
      while (!Thread.interrupted()) {
        if (VideoEncode.isHasChangeConfig) {
          VideoEncode.isHasChangeConfig = false;
          VideoEncode.stopEncode();
          VideoEncode.startEncode();
        }
        VideoEncode.encodeOut();
        frame++;
        if (frame > 120) {
          if (System.currentTimeMillis() - lastKeepAliveTime > timeoutDelay) throw new IOException("连接断开");
          frame = 0;
        }
      }
    } catch (Exception e) {
      errorClose(e);
    }
  }

  private static void executeAudioIn() {
    while (!Thread.interrupted()) AudioEncode.encodeIn();
  }

  private static void executeAudioOut() {
    try {
      while (!Thread.interrupted()) AudioEncode.encodeOut();
    } catch (Exception e) {
      errorClose(e);
    }
  }

  private static long lastKeepAliveTime = System.currentTimeMillis();

  private static void executeControlIn() {
    try {
      while (!Thread.interrupted()) {
        switch (Server.mainInputStream.readByte()) {
          case 1:
            ControlPacket.handleTouchEvent();
            break;
          case 2:
            ControlPacket.handleKeyEvent();
            break;
          case 3:
            ControlPacket.handleClipboardEvent();
            break;
          case 4:
            lastKeepAliveTime = System.currentTimeMillis();
            // 收到心跳包，原样返回，用于客户端计算RTT往返延迟
            mainOutputStream.write(new byte[]{4});
            // 强制flush，立刻发送，避免TCP缓冲
            mainOutputStream.flush();
            break;
          case 5:
            Device.changeResolution(mainInputStream.readFloat());
            break;
          case 6:
            Device.rotateDevice();
            break;
          case 7:
            Device.changeScreenPowerMode(mainInputStream.readByte());
            break;
          case 8:
            Device.changePower(mainInputStream.readInt());
            break;
          case 9:
            Device.changeResolution(mainInputStream.readInt(), mainInputStream.readInt());
            break;
        }
      }
    } catch (Exception e) {
      errorClose(e);
    }
  }

  public synchronized static void writeMain(ByteBuffer byteBuffer) throws IOException {
    mainOutputStream.write(byteBuffer.array());
  }

  public static void writeVideo(ByteBuffer byteBuffer) throws IOException {
    videoOutputStream.write(byteBuffer.array());
  }

  public static void errorClose(Exception e) {
    e.printStackTrace();
    synchronized (object) {
      object.notify();
    }
  }

  // 释放资源
  private static void release() {
    for (int i = 0; i < 4; i++) {
      try {
        switch (i) {
          case 0:
            mainInputStream.close();
            mainSocket.close();
            videoSocket.close();
            break;
          case 1:
            VideoEncode.release();
            AudioEncode.release();
            break;
          case 2:
            Device.fallbackResolution();
            Device.fallbackScreenLightTimeout();
          case 3:
            Runtime.getRuntime().exit(0);
            break;
        }
      } catch (Exception ignored) {
      }
    }
  }

}
