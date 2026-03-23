package qzrs.Scrcpy;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Pair;
import android.widget.Toast;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import qzrs.Scrcpy.databinding.ActivityListenBinding;
import qzrs.Scrcpy.databinding.ItemLoadingBinding;
import qzrs.Scrcpy.databinding.ItemTextBinding;
import qzrs.Scrcpy.entity.AppData;
import qzrs.Scrcpy.entity.Device;
import qzrs.Scrcpy.helper.LogHelper;
import qzrs.Scrcpy.helper.PublicTools;
import qzrs.Scrcpy.helper.ViewTools;

/**
 * P2P 等待连接模式
 * 
 * 被控端启动此模式后，会启动后台服务监听连接
 * 控制端直接通过 IP:端口 连接
 */
public class ListenActivity extends Activity {
    
    private ActivityListenBinding binding;
    private static final int DEFAULT_PORT = 25166;
    
    private boolean isListening = false;
    private int listeningPort = DEFAULT_PORT;
    private Handler mainHandler;
    
    // 前台服务相关
    private static final String CHANNEL_ID = "p2p_listen_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewTools.setStatusAndNavBar(this);
        ViewTools.setLocale(this);
        binding = ActivityListenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        createNotificationChannel();
        
        showLocalIp();
        setButtonListener();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "P2P 监听服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于 P2P 直连投屏的后台服务");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private void showLocalIp() {
        Pair<ArrayList<String>, ArrayList<String>> ipPair = PublicTools.getLocalIp();
        StringBuilder sb = new StringBuilder();
        
        if (!ipPair.first.isEmpty()) {
            for (String ip : ipPair.first) {
                if (!sb.toString().contains(ip)) {
                    sb.append(ip).append("\n");
                }
            }
        }
        
        if (!ipPair.second.isEmpty()) {
            for (String ip : ipPair.second) {
                if (!sb.toString().contains(ip) && sb.toString().length() < 200) {
                    sb.append(ip).append("\n");
                }
            }
        }
        
        binding.ipAddress.setText(sb.toString().trim());
        binding.port.setText(String.valueOf(DEFAULT_PORT));
    }
    
    private void setButtonListener() {
        binding.backButton.setOnClickListener(v -> stopListeningAndFinish());
        
        binding.startButton.setOnClickListener(v -> {
            String portStr = binding.port.getText().toString().trim();
            try {
                listeningPort = Integer.parseInt(portStr);
                if (listeningPort <= 0 || listeningPort > 65535) {
                    Toast.makeText(this, "端口号无效", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                listeningPort = DEFAULT_PORT;
            }
            
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });
        
        binding.copyButton.setOnClickListener(v -> {
            String ip = binding.ipAddress.getText().toString().trim();
            String port = binding.port.getText().toString().trim();
            String firstIp = ip.split("\n")[0].trim();
            String connectionString = firstIp + ":" + port;
            
            AppData.clipBoard.setPrimaryClip(
                ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, connectionString)
            );
            Toast.makeText(this, getString(R.string.toast_copy), Toast.LENGTH_SHORT).show();
        });
    }
    
    private void startListening() {
        if (isListening) return;
        
        isListening = true;
        binding.startButton.setEnabled(false);
        binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_ok);
        
        // 启动前台服务
        Intent serviceIntent = new Intent(this, P2PListenService.class);
        serviceIntent.putExtra("port", listeningPort);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // 更新 UI
        mainHandler.postDelayed(() -> {
            binding.startButton.setEnabled(true);
            String ip = binding.ipAddress.getText().toString().split("\n")[0].trim();
            binding.statusText.setText("等待控制端连接...\n\n" +
                "连接地址: " + ip + ":" + listeningPort + "\n\n" +
                "在控制端输入以上地址即可连接");
            
            Toast.makeText(this, "服务已启动，IP 地址已复制", Toast.LENGTH_LONG).show();
            
            // 自动复制到剪贴板
            AppData.clipBoard.setPrimaryClip(
                ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, ip + ":" + listeningPort)
            );
        }, 500);
        
        LogHelper.i("ListenActivity", "P2P 监听服务已启动，端口: " + listeningPort);
    }
    
    private void stopListening() {
        isListening = false;
        
        // 停止服务
        Intent serviceIntent = new Intent(this, P2PListenService.class);
        stopService(serviceIntent);
        
        binding.statusText.setText("已停止监听");
        binding.startButtonText.setText("启动");
        binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
        binding.startButton.setEnabled(true);
        
        LogHelper.i("ListenActivity", "P2P 监听服务已停止");
    }
    
    private void stopListeningAndFinish() {
        if (isListening) {
            stopListening();
        }
        finish();
    }
    
    @Override
    protected void onDestroy() {
        if (isListening) {
            stopListening();
        }
        super.onDestroy();
    }
}

/**
 * P2P 监听服务
 * 在后台运行，监听控制端的连接
 */
class P2PListenService extends Service {
    
    private static final String CHANNEL_ID = "p2p_listen_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private ExecutorService executorService;
    private int listeningPort = 25166;
    
    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newCachedThreadPool();
        LogHelper.i("P2PListenService", "服务创建");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            listeningPort = intent.getIntExtra("port", 25166);
        }
        
        startForeground(NOTIFICATION_ID, createNotification());
        
        startListening();
        
        return START_STICKY;
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, ListenActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("P2P 投屏服务运行中")
            .setContentText("监听端口: " + listeningPort)
            .setSmallIcon(R.drawable.wifi)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
        
        return notification;
    }
    
    private void startListening() {
        if (isRunning) return;
        
        isRunning = true;
        
        executorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(listeningPort);
                serverSocket.setReuseAddress(true);
                
                LogHelper.i("P2PListenService", "开始监听端口: " + listeningPort);
                
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        LogHelper.i("P2PListenService", "收到连接: " + clientSocket.getRemoteSocketAddress());
                        
                        // 处理客户端连接
                        handleClientConnection(clientSocket);
                        
                    } catch (IOException e) {
                        if (isRunning) {
                            LogHelper.e("P2PListenService", "接受连接失败", e);
                        }
                    }
                }
                
            } catch (IOException e) {
                LogHelper.e("P2PListenService", "监听失败", e);
            }
        });
    }
    
    private void handleClientConnection(Socket clientSocket) {
        try {
            LogHelper.i("P2PListenService", "处理客户端连接...");
            
            // 这里需要启动 scrcpy server 并转发连接
            // 目前先简单地关闭连接并提示
            clientSocket.close();
            
            LogHelper.i("P2PListenService", "P2P 直连功能开发中，连接已关闭");
            
        } catch (Exception e) {
            LogHelper.e("P2PListenService", "处理连接失败", e);
        }
    }
    
    @Override
    public void onDestroy() {
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LogHelper.e("P2PListenService", "关闭 ServerSocket 失败", e);
        }
        
        executorService.shutdown();
        LogHelper.i("P2PListenService", "服务销毁");
        
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
