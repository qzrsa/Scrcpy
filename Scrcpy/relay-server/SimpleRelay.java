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
                new Thread(() -> handleConnection(socket)).start();
            } catch (SocketTimeoutException e) {
                // 超时继续等待
            } catch (SocketException e) {
                if (running) System.err.println("[Socket] " + e.getMessage());
            }
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
            
            if (cmd.equals("AUTH") || cmd.equals("REGISTER")) {
                handleAuth(parts, socket, writer, reader);
            } else if (cmd.equals("CONNECT")) {
                handleClientConnect(parts, socket, writer, reader);
            } else if (cmd.equals("PING")) {
                writer.println("PONG");
            } else {
                writer.println("ERROR");
                socket.close();
            }
        } catch (SocketTimeoutException e) {
            System.out.println("[超时] 连接超时");
            try { socket.close(); } catch (Exception ignored) {}
        } catch (IOException e) {
            System.out.println("[异常] " + e.getMessage());
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
    
    // 处理被控端认证
    private void handleAuth(String[] parts, Socket socket, PrintWriter out, BufferedReader reader) throws IOException {
        if (parts.length < 2) { 
            out.println("ERROR"); 
            socket.close(); 
            return; 
        }
        
        String uuid = parts[1];
        String token = parts.length > 2 ? parts[2] : "";
        
        System.out.println("[注册] 设备: " + uuid);
        
        // 保存设备信息
        DeviceInfo device = new DeviceInfo(uuid, socket, out, reader);
        devices.put(uuid, device);
        
        // 发送 OK 响应
        out.println("OK");
        out.flush();
        System.out.println("[发送] OK - 等待控制端连接");
        
        // 保持连接，等待控制端连接
        // 注意：不读取任何数据，只是保持连接
        synchronized (device) {
            try {
                device.wait(); // 等待被唤醒
            } catch (InterruptedException e) {
                System.out.println("[设备] 等待被中断");
            }
        }
    }
    
    // 处理控制端连接
    private void handleClientConnect(String[] parts, Socket socket, PrintWriter out, BufferedReader reader) throws IOException {
        if (parts.length < 2) { 
            out.println("ERROR"); 
            socket.close(); 
            return; 
        }
        
        String uuid = parts[1];
        System.out.println("[控制端] 请求连接: " + uuid);
        
        // 查找被控端
        DeviceInfo device = devices.get(uuid);
        if (device == null) {
            out.println("ERROR Device not found");
            socket.close();
            System.out.println("[错误] 设备不存在: " + uuid);
            return;
        }
        
        // 通知被控端有控制端连接
        System.out.println("[通知] 告诉被控端可以连接了");
        device.writer.println("CONNECTED");
        device.writer.flush();
        
        // 告诉控制端连接成功
        out.println("CONNECTED");
        out.flush();
        System.out.println("[发送] CONNECTED 给控制端");
        
        // 转发数据
        pipe(socket, device.socket, "MAIN");
    }
    
    private void pipe(Socket a, Socket b, String type) {
        try {
            InputStream inA = a.getInputStream();
            OutputStream outA = a.getOutputStream();
            InputStream inB = b.getInputStream();
            OutputStream outB = b.getOutputStream();
            
            Thread t1 = new Thread(() -> {
                try { byte[] buf = new byte[BUFFER_SIZE]; int n; while ((n = inA.read(buf)) != -1) { outB.write(buf,0,n); outB.flush(); } } catch (IOException e) {}
            }, "Client->Device");
            Thread t2 = new Thread(() -> {
                try { byte[] buf = new byte[BUFFER_SIZE]; int n; while ((n = inB.read(buf)) != -1) { outA.write(buf,0,n); outA.flush(); } } catch (IOException e) {}
            }, "Device->Client");
            
            t1.start(); t2.start();
            t1.join(); t2.join();
        } catch (Exception e) {
            System.err.println("[转发] 异常: " + e.getMessage());
        } finally {
            try { a.close(); } catch (Exception ignored) {}
            try { b.close(); } catch (Exception ignored) {}
            System.out.println("[转发] 结束");
        }
    }
    
    class DeviceInfo {
        String uuid;
        Socket socket;
        PrintWriter writer;
        BufferedReader reader;
        
        DeviceInfo(String uuid, Socket socket, PrintWriter writer, BufferedReader reader) {
            this.uuid = uuid;
            this.socket = socket;
            this.writer = writer;
            this.reader = reader;
        }
    }
}
