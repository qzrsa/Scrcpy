import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class SimpleRelay {
    private static final int PORT = 5555;
    private static final int BUFFER_SIZE = 65536;
    
    private final Map<String, DeviceInfo> devices = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private boolean running = true;
    
    public static void main(String[] args) {
        System.out.println("=== Scrcpy 中继服务器启动 ===");
        System.out.println("端口: " + PORT);
        
        try {
            new SimpleRelay().start();
        } catch (IOException e) {
            System.err.println("启动失败: " + e.getMessage());
            System.exit(1);
        }
    }
    
    public void start() throws IOException {
        serverSocket = new ServerSocket(PORT);
        System.out.println("中继服务器已启动");
        
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(60000); // 1分钟超时
                new Thread(() -> handleConnection(socket)).start();
            } catch (SocketException e) {}
        }
    }
    
    private void handleConnection(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            
            String line = reader.readLine();
            if (line == null) { socket.close(); return; }
            
            String[] parts = line.trim().split(" ", 3);
            String cmd = parts[0].toUpperCase();
            
            System.out.println("[收到] " + cmd + " " + (parts.length > 1 ? parts[1] : ""));
            System.out.println("[原始] " + line);
            
            if (cmd.equals("AUTH") || cmd.equals("REGISTER")) {
                handleAuth(parts, socket, writer);
            } else if (cmd.equals("CONNECT")) {
                handleConnect(parts, socket, writer);
            } else if (cmd.equals("PING")) {
                writer.println("PONG");
            } else {
                writer.println("ERROR");
                socket.close();
            }
        } catch (SocketTimeoutException e) {
            System.out.println("[超时] 连接超时，关闭");
            try { socket.close(); } catch (Exception ignored) {}
        } catch (IOException e) {
            System.out.println("[异常] " + e.getMessage());
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
    
    private void handleAuth(String[] parts, Socket socket, PrintWriter out) throws IOException {
        if (parts.length < 2) { 
            out.println("ERROR"); 
            socket.close(); 
            return; 
        }
        
        String uuid = parts[1];
        System.out.println("[认证] 设备: " + uuid);
        
        devices.put(uuid, new DeviceInfo(uuid, socket, out));
        
        // 发送 OK 响应
        out.println("OK");
        out.flush();
        
        System.out.println("[发送] OK");
        
        // 保持连接，等待控制端
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            while (running && !socket.isClosed()) {
                String line = reader.readLine();
                if (line == null) break;
                System.out.println("[设备消息] " + line);
                if (line.startsWith("PING")) {
                    out.println("PONG");
                    out.flush();
                }
            }
        } catch (SocketException e) {
            System.out.println("[设备断开] " + e.getMessage());
        } finally {
            devices.remove(uuid);
            System.out.println("[断开] 设备: " + uuid);
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
    
    private void handleConnect(String[] parts, Socket socket, PrintWriter out) throws IOException {
        if (parts.length < 2) { 
            out.println("ERROR"); 
            socket.close(); 
            return; 
        }
        
        String uuid = parts[1];
        String type = parts.length > 2 ? parts[2] : "MAIN";
        System.out.println("[连接] " + uuid + " (" + type + ")");
        
        DeviceInfo device = devices.get(uuid);
        if (device == null) {
            out.println("ERROR Device not found");
            socket.close();
            return;
        }
        
        // 通知设备有控制端连接
        device.writer.println("CONNECTED");
        device.writer.flush();
        System.out.println("[通知设备] CONNECTED");
        
        // 告诉控制端连接成功
        out.println("CONNECTED");
        out.flush();
        System.out.println("[发送] CONNECTED");
        
        // 转发数据
        pipe(socket, device.socket, type);
    }
    
    private void pipe(Socket a, Socket b, String type) {
        try {
            InputStream inA = a.getInputStream();
            OutputStream outA = a.getOutputStream();
            InputStream inB = b.getInputStream();
            OutputStream outB = b.getOutputStream();
            
            Thread t1 = new Thread(() -> {
                try { byte[] buf = new byte[BUFFER_SIZE]; int n; while ((n = inA.read(buf)) != -1) { outB.write(buf,0,n); outB.flush(); } } catch (IOException e) {}
            });
            Thread t2 = new Thread(() -> {
                try { byte[] buf = new byte[BUFFER_SIZE]; int n; while ((n = inB.read(buf)) != -1) { outA.write(buf,0,n); outA.flush(); } } catch (IOException e) {}
            });
            t1.start(); t2.start();
            t1.join(); t2.join();
        } catch (Exception e) {
            System.err.println("[转发] " + type + " 异常: " + e.getMessage());
        } finally {
            try { a.close(); } catch (Exception ignored) {}
            try { b.close(); } catch (Exception ignored) {}
            System.out.println("[转发] " + type + " 结束");
        }
    }
    
    class DeviceInfo {
        String uuid;
        Socket socket;
        PrintWriter writer;
        DeviceInfo(String uuid, Socket socket, PrintWriter writer) {
            this.uuid = uuid;
            this.socket = socket;
            this.writer = writer;
        }
    }
}
