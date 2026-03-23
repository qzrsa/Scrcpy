package qzrs.Scrcpy.connection;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 中转模式连接器 - 服务端侧（Android 设备）
 * 
 * 在 Android 设备上运行，作为中继服务器的被连接端
 * 等待中继服务器推送数据
 */
public class RelayServerConnector {
    
    private static final String TAG = "RelayServer";
    
    private ConnectionConfig config;
    private ServerSocket serverSocket;
    
    private volatile boolean isRunning = false;
    private Thread acceptThread;
    private ExecutorService executor;
    
    // 当前连接
    private Socket currentClientSocket;
    private ConnectionResult currentResult;
    
    // 回调
    private RelayConnectionListener listener;
    
    public interface RelayConnectionListener {
        void onClientConnected(ConnectionResult result);
        void onConnectionError(String error);
        void onClientDisconnected();
    }
    
    public RelayServerConnector(ConnectionConfig config) {
        this.config = config != null ? config : new ConnectionConfig();
        this.executor = Executors.newCachedThreadPool();
    }
    
    /**
     * 启动中继服务端监听
     * @param port 监听端口（0 表示自动分配）
     * @return 实际使用的端口
     */
    public int startServer(int port) throws IOException {
        if (isRunning) {
            throw new IllegalStateException("Server already running");
        }
        
        isRunning = true;
        
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            serverSocket.setSoTimeout(0); // 无限等待
        } catch (IOException e) {
            // 尝试自动分配端口
            serverSocket = new ServerSocket(0);
        }
        
        final int listeningPort = serverSocket.getLocalPort();
        
        Log.d(TAG, "Relay server started on port " + listeningPort);
        
        acceptThread = new Thread(() -> {
            while (isRunning) {
                try {
                    final Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(30000);
                    
                    Log.d(TAG, "Relay client connected: " + 
                          clientSocket.getRemoteSocketAddress());
                    
                    executor.execute(() -> handleClient(clientSocket));
                    
                } catch (IOException e) {
                    if (isRunning) {
                        Log.e(TAG, "Error accepting connection", e);
                    }
                    break;
                }
            }
        });
        
        acceptThread.start();
        
        return listeningPort;
    }
    
    /**
     * 处理中继客户端连接
     */
    private void handleClient(Socket clientSocket) {
        try {
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            
            // 读取认证请求
            String authLine = readLine(in);
            if (!authLine.startsWith("AUTH ")) {
                out.write("ERROR Invalid auth\n".getBytes(StandardCharsets.UTF_8));
                clientSocket.close();
                return;
            }
            
            // 验证通过，发送 OK
            out.write("OK\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            
            // 读取连接请求
            String connectLine = readLine(in);
            if (!connectLine.startsWith("CONNECT ")) {
                out.write("ERROR Invalid connect\n".getBytes(StandardCharsets.UTF_8));
                clientSocket.close();
                return;
            }
            
            // 发送连接成功
            out.write("CONNECTED\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            
            Log.d(TAG, "Relay connection established");
            
            // 保存当前连接
            currentClientSocket = clientSocket;
            currentResult = ConnectionResult.success(ConnectionMode.RELAY);
            currentResult.setMainSocket(clientSocket);
            currentResult.setMainInputStream(in);
            currentResult.setMainOutputStream(out);
            currentResult.setRemoteAddress(clientSocket.getInetAddress().getHostAddress());
            currentResult.setRemotePort(clientSocket.getPort());
            
            // 通知监听器
            if (listener != null) {
                listener.onClientConnected(currentResult);
            }
            
            // 等待连接关闭
            // 读取数据（实际数据通过 Scrcpy 协议处理）
            byte[] buffer = new byte[65536];
            while (isRunning && !clientSocket.isClosed()) {
                try {
                    int len = in.read(buffer);
                    if (len == -1) break;
                    // 数据由 Scrcpy 协议处理
                } catch (IOException e) {
                    break;
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling relay client", e);
            if (listener != null) {
                listener.onConnectionError(e.getMessage());
            }
        } finally {
            try {
                clientSocket.close();
            } catch (Exception ignored) {}
            
            if (currentClientSocket == clientSocket) {
                currentClientSocket = null;
                currentResult = null;
                if (listener != null) {
                    listener.onClientDisconnected();
                }
            }
        }
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
     * 设置连接监听器
     */
    public void setConnectionListener(RelayConnectionListener listener) {
        this.listener = listener;
    }
    
    /**
     * 停止服务器
     */
    public void stopServer() {
        isRunning = false;
        
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            if (currentClientSocket != null) {
                currentClientSocket.close();
            }
            if (acceptThread != null) {
                acceptThread.interrupt();
            }
            if (executor != null) {
                executor.shutdown();
            }
        } catch (Exception ignored) {}
        
        Log.d(TAG, "Relay server stopped");
    }
    
    /**
     * 检查是否在运行
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 获取监听端口
     */
    public int getListeningPort() {
        if (serverSocket != null) {
            return serverSocket.getLocalPort();
        }
        return 0;
    }
}
