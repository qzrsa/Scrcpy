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
            } catch (SocketException e) {}
        }
    }
    
    private void handleConnection(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream();
            String line = reader.readLine();
            if (line == null) { socket.close(); return; }
            
            String[] parts = line.trim().split(" ", 3);
            String cmd = parts[0].toUpperCase();
            
            System.out.println("[收到] " + cmd + " " + (parts.length > 1 ? parts[1] : ""));
            
            if (cmd.equals("AUTH") || cmd.equals("REGISTER")) {
                handleAuth(parts, socket, out);
            } else if (cmd.equals("CONNECT")) {
                handleConnect(parts, socket, out);
            } else if (cmd.equals("PING")) {
                out.write("PONG\n".getBytes());
                out.flush();
            } else {
                out.write("ERROR\n".getBytes());
                out.flush();
                socket.close();
            }
        } catch (IOException e) {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
    
    private void handleAuth(String[] parts, Socket socket, OutputStream out) throws IOException {
        if (parts.length < 2) { out.write("ERROR\n".getBytes()); out.flush(); socket.close(); return; }
        
        String uuid = parts[1];
        System.out.println("[认证] 设备: " + uuid);
        
        devices.put(uuid, new DeviceInfo(uuid, socket));
        out.write("OK\n".getBytes());
        out.flush();
        
        // 保持连接
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            while (running && !socket.isClosed()) {
                String line = reader.readLine();
                if (line == null) break;
                if (line.startsWith("PING")) out.write("PONG\n".getBytes());
                out.flush();
            }
        } finally {
            devices.remove(uuid);
            System.out.println("[断开] 设备: " + uuid);
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
    
    private void handleConnect(String[] parts, Socket socket, OutputStream out) throws IOException {
        if (parts.length < 2) { out.write("ERROR\n".getBytes()); out.flush(); socket.close(); return; }
        
        String uuid = parts[1];
        String type = parts.length > 2 ? parts[2] : "MAIN";
        System.out.println("[连接] " + uuid + " (" + type + ")");
        
        DeviceInfo device = devices.get(uuid);
        if (device == null) {
            out.write("ERROR Device not found\n".getBytes());
            out.flush();
            socket.close();
            return;
        }
        
        // 通知设备
        device.out.write("CONNECTED\n".getBytes());
        device.out.flush();
        
        // 告诉客户端连接成功
        out.write("CONNECTED\n".getBytes());
        out.flush();
        
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
            try { a.close(); b.close(); } catch (Exception ignored) {}
            System.out.println("[转发] " + type + " 结束");
        }
    }
    
    class DeviceInfo {
        String uuid;
        Socket socket;
        OutputStream out;
        DeviceInfo(String uuid, Socket socket) {
            this.uuid = uuid;
            this.socket = socket;
            try { this.out = socket.getOutputStream(); } catch (IOException e) {}
        }
    }
}
