package qzrs.Scrcpy.connection;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * P2P 连接器 - 服务端侧（Android 设备作为被连接方）
 * 
 * 在 Android 设备上监听连接请求，等待客户端连接
 */
public class P2pServerConnector {
    
    private static final String TAG = "P2pServer";
    
    private ConnectionConfig config;
    private ServerSocket mainServerSocket;
    private ServerSocket videoServerSocket;
    private ServerSocket audioServerSocket;
    
    private volatile boolean isRunning = false;
    private Thread acceptThread;
    
    // 回调接口
    private P2pConnectionListener listener;
    
    public interface P2pConnectionListener {
        void onClientConnected(ConnectionResult result);
        void onConnectionError(String error);
    }
    
    public P2pServerConnector(ConnectionConfig config) {
        this.config = config != null ? config : new ConnectionConfig();
    }
    
    /**
     * 启动 P2P 服务器监听
     * @param port 监听端口（0 表示自动分配）
     * @return 实际使用的端口
     */
    public int startServer(int port) throws IOException {
        if (isRunning) {
            throw new IllegalStateException("Server already running");
        }
        
        isRunning = true;
        
        // 尝试使用指定端口，失败则自动分配
        int actualPort = port;
        boolean portBound = false;
        
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                mainServerSocket = new ServerSocket(actualPort);
                mainServerSocket.setReuseAddress(true);
                portBound = true;
                break;
            } catch (IOException e) {
                if (port > 0) {
                    // 如果指定端口失败，尝试其他端口
                    actualPort = 0;
                }
                actualPort = 0; // 让系统分配端口
            }
        }
        
        if (!portBound) {
            throw new IOException("Failed to bind to any port");
        }
        
        final int listeningPort = actualPort;
        
        // 启动接受连接的线程
        acceptThread = new Thread(() -> {
            Log.d(TAG, "P2P server started on port " + listeningPort);
            
            while (isRunning) {
                try {
                    Socket clientSocket = mainServerSocket.accept();
                    clientSocket.setSoTimeout(config.getP2pTimeout());
                    
                    Log.d(TAG, "Client connected from: " + clientSocket.getRemoteSocketAddress());
                    
                    // 处理客户端连接
                    handleClientConnection(clientSocket, listeningPort);
                    
                } catch (IOException e) {
                    if (isRunning) {
                        Log.e(TAG, "Error accepting connection", e);
                    }
                    break;
                }
            }
            
            Log.d(TAG, "P2P server stopped");
        });
        
        acceptThread.start();
        
        return listeningPort;
    }
    
    /**
     * 处理客户端连接
     */
    private void handleClientConnection(Socket clientSocket, int listenPort) {
        try {
            // 创建视频和音频连接
            ConnectionResult result = ConnectionResult.success(ConnectionMode.P2P);
            result.setMainSocket(clientSocket);
            result.setMainInputStream(clientSocket.getInputStream());
            result.setMainOutputStream(clientSocket.getOutputStream());
            result.setRemoteAddress(clientSocket.getInetAddress().getHostAddress());
            result.setRemotePort(clientSocket.getPort());
            
            // 通知监听器
            if (listener != null) {
                listener.onClientConnected(result);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling client connection", e);
            try {
                clientSocket.close();
            } catch (Exception ignored) {}
            
            if (listener != null) {
                listener.onConnectionError(e.getMessage());
            }
        }
    }
    
    /**
     * 设置连接监听器
     */
    public void setConnectionListener(P2pConnectionListener listener) {
        this.listener = listener;
    }
    
    /**
     * 停止服务器
     */
    public void stopServer() {
        isRunning = false;
        
        try {
            if (mainServerSocket != null) {
                mainServerSocket.close();
            }
            if (videoServerSocket != null) {
                videoServerSocket.close();
            }
            if (audioServerSocket != null) {
                audioServerSocket.close();
            }
            if (acceptThread != null) {
                acceptThread.interrupt();
            }
        } catch (Exception ignored) {}
        
        Log.d(TAG, "P2P server stopped");
    }
    
    /**
     * 检查服务器是否在运行
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 获取服务器监听的端口
     */
    public int getListeningPort() {
        if (mainServerSocket != null) {
            return mainServerSocket.getLocalPort();
        }
        return 0;
    }
}
