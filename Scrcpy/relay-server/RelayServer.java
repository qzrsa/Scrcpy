package qzrs.Scrcpy.relay;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 中继服务器
 * 用于在控制端和被控端之间转发流量
 * 
 * 协议:
 * - 被控端注册: REGISTER <uuid> <token>
 * - 控制端连接: CONNECT <uuid> MAIN/VIDEO
 * - 心跳: PING/PONG
 */
public class RelayServer {
    private static final int DEFAULT_PORT = 3478;
    private static final int BUFFER_SIZE = 65536;
    private static final long IDLE_TIMEOUT = 60000; // 60秒无活动断开
    
    private final int port;
    private final Map<String, DeviceConnection> devices = new ConcurrentHashMap<>();
    private final Map<String, ClientConnection> clients = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private boolean running = true;
    
    public RelayServer(int port) {
        this.port = port;
    }
    
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        System.out.println("=== Scrcpy 中继服务器启动 ===");
        System.out.println("端口: " + port);
        System.out.println("==============================");
        
        try {
            new RelayServer(port).start();
        } catch (IOException e) {
            System.err.println("启动失败: " + e.getMessage());;
            System.exit(1);
        }
    }
    
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("中继服务器已启动，监听端口 " + port);
        
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                clientSocket.setSoTimeout(30000);
                
                // 处理新连接
                new Thread(() -> handleConnection(clientSocket)).start();
            } catch (SocketException e) {
                if (running) {
                    System.err.println("Socket异常: " + e.getMessage());
                }
            }
        }
    }
    
    private void handleConnection(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream();
            
            String firstLine = reader.readLine();
            if (firstLine == null) {
                socket.close();
                return;
            }
            
            String[] parts = firstLine.split(" ", 3);
            String command = parts[0].toUpperCase();
            
            switch (command) {
                case "REGISTER":
                    handleRegister(parts, socket, reader, out);
                    break;
                case "CONNECT":
                    handleClientConnect(parts, socket, reader, out);
                    break;
                case "PING":
                    out.write("PONG\n".getBytes());
                    out.flush();
                    break;
                default:
                    out.write("ERROR Unknown command\n".getBytes());
                    out.flush();
                    socket.close();
            }
        } catch (IOException e) {
            System.err.println("处理连接异常: " + e.getMessage());
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
    
    /**
     * 处理被控端注册
     * 格式: REGISTER <uuid> <token>
     */
    private void handleRegister(String[] parts, Socket socket, BufferedReader reader, OutputStream out) throws IOException {
        if (parts.length < 2) {
            out.write("ERROR Missing UUID\n".getBytes());
            out.flush();
            socket.close();
            return;
        }
        
        String uuid = parts[1];
        String token = parts.length > 2 ? parts[2] : "";
        
        System.out.println("[注册] 设备 " + uuid + " 请求注册");
        
        // 验证token（这里简单处理，可以添加更复杂的验证）
        DeviceConnection device = new DeviceConnection(uuid, token, socket, reader, out);
        devices.put(uuid, device);
        
        out.write("OK REGISTERED\n".getBytes());
        out.flush();
        
        System.out.println("[注册] 设备 " + uuid + " 注册成功");
        
        // 保持连接，等待被控制
        try {
            // 等待连接请求或心跳
            while (running && !socket.isClosed()) {
                String line = reader.readLine();
                if (line == null) break;
                
                if (line.startsWith("PING")) {
                    out.write("PONG\n".getBytes());
                    out.flush();
                } else if (line.startsWith("ALIVE")) {
                    // 设备定期发送ALIVE保持活跃
                    device.updateLastActive();
                }
            }
        } catch (SocketException e) {
            // 连接断开
        } finally {
            devices.remove(uuid);
            System.out.println("[注册] 设备 " + uuid + " 断开连接");
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
    
    /**
     * 处理控制端连接请求
     * 格式: CONNECT <uuid> [MAIN|VIDEO]
     */
    private void handleClientConnect(String[] parts, Socket socket, BufferedReader reader, OutputStream out) throws IOException {
        if (parts.length < 2) {
            out.write("ERROR Missing UUID\n".getBytes());
            out.flush();
            socket.close();
            return;
        }
        
        String uuid = parts[1];
        String type = parts.length > 2 ? parts[2] : "MAIN";
        
        System.out.println("[连接] 控制端请求连接 " + uuid + " (" + type + ")");
        
        DeviceConnection device = devices.get(uuid);
        if (device == null) {
            out.write("ERROR Device not found\n".getBytes());
            out.flush();
            socket.close();
            System.out.println("[连接] 设备 " + uuid + " 不存在");
            return;
        }
        
        if (type.equals("MAIN")) {
            // 主连接
            out.write("OK CONNECTED\n".getBytes());
            out.flush();
            
            // 通知设备有控制端连接
            device.notifyConnected();
            
            // 转发数据
            pipeData(socket, device.getSocket(), "MAIN");
        } else if (type.equals("VIDEO")) {
            // 视频连接
            out.write("OK CONNECTED\n".getBytes());
            out.flush();
            
            // 转发数据
            pipeData(socket, device.getSocket(), "VIDEO");
        } else {
            out.write("ERROR Unknown type\n".getBytes());
            out.flush();
            socket.close();
        }
    }
    
    /**
     * 转发数据
     */
    private void pipeData(Socket clientSocket, Socket deviceSocket, String type) {
        try {
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();
            InputStream deviceIn = deviceSocket.getInputStream();
            OutputStream deviceOut = deviceSocket.getOutputStream();
            
            // 创建两个转发线程
            Thread clientToDevice = new Thread(() -> {
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int n;
                    while ((n = clientIn.read(buffer)) != -1) {
                        deviceOut.write(buffer, 0, n);
                        deviceOut.flush();
                    }
                } catch (IOException e) {
                    // 连接断开
                }
            }, "Client->Device-" + type);
            
            Thread deviceToClient = new Thread(() -> {
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int n;
                    while ((n = deviceIn.read(buffer)) != -1) {
                        clientOut.write(buffer, 0, n);
                        clientOut.flush();
                    }
                } catch (IOException e) {
                    // 连接断开
                }
            }, "Device->Client-" + type);
            
            clientToDevice.start();
            deviceToClient.start();
            
            clientToDevice.join();
            deviceToClient.join();
            
        } catch (Exception e) {
            System.err.println("[转发] " + type + " 异常: " + e.getMessage());
        } finally {
            try { clientSocket.close(); } catch (Exception ignored) {}
            System.out.println("[转发] " + type + " 结束");
        }
    }
    
    /**
     * 设备连接
     */
    private static class DeviceConnection {
        final String uuid;
        final String token;
        final Socket socket;
        final BufferedReader reader;
        final OutputStream out;
        long lastActive;
        
        DeviceConnection(String uuid, String token, Socket socket, BufferedReader reader, OutputStream out) {
            this.uuid = uuid;
            this.token = token;
            this.socket = socket;
            this.reader = reader;
            this.out = out;
            this.lastActive = System.currentTimeMillis();
        }
        
        Socket getSocket() { return socket; }
        
        void updateLastActive() { this.lastActive = System.currentTimeMillis(); }
        
        void notifyConnected() throws IOException {
            out.write("CONNECTED\n".getBytes());
            out.flush();
        }
    }
    
    /**
     * 客户端连接
     */
    private static class ClientConnection {
        final String uuid;
        final String type;
        final Socket socket;
        
        ClientConnection(String uuid, String type, Socket socket) {
            this.uuid = uuid;
            this.type = type;
            this.socket = socket;
        }
    }
}
