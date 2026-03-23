package qzrs.Scrcpy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * P2P 监听服务
 * 在后台运行，监听控制端的连接
 */
public class P2PListenService extends Service {
    
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
        try {
            LogHelper.i("P2PListenService", "服务创建");
        } catch (Exception ignored) {}
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null) {
                listeningPort = intent.getIntExtra("port", 25166);
            }
            
            // 先创建通知
            Notification notification = createNotification();
            if (notification != null) {
                startForeground(NOTIFICATION_ID, notification);
            }
            
            startListening();
            
            return START_STICKY;
        } catch (Exception e) {
            try { LogHelper.e("P2PListenService", "启动服务失败", e); } catch (Exception ignored) {}
            stopSelf();
            return START_NOT_STICKY;
        }
    }
    
    private Notification createNotification() {
        try {
            // 创建通知渠道
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "P2P 监听服务",
                    NotificationManager.IMPORTANCE_LOW
                );
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
            
            Intent notificationIntent = new Intent(this, ListenActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            );
            
            Notification notification;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                notification = new Notification.Builder(this)
                    .setContentTitle("P2P 投屏服务")
                    .setContentText("监听端口: " + listeningPort)
                    .setSmallIcon(R.drawable.wifi)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .build();
            } else {
                notification = new Notification();
                notification.icon = R.drawable.wifi;
                notification.tickerText = "P2P 投屏服务运行中";
            }
            
            return notification;
        } catch (Exception e) {
            try { LogHelper.e("P2PListenService", "创建通知失败", e); } catch (Exception ignored) {}
            return null;
        }
    }
    
    private void startListening() {
        if (isRunning) return;
        
        isRunning = true;
        
        executorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(listeningPort);
                serverSocket.setReuseAddress(true);
                
                try { LogHelper.i("P2PListenService", "开始监听端口: " + listeningPort); } catch (Exception ignored) {}
                
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        try { LogHelper.i("P2PListenService", "收到连接: " + clientSocket.getRemoteSocketAddress()); } catch (Exception ignored) {}
                        handleClientConnection(clientSocket);
                    } catch (IOException e) {
                        if (isRunning) {
                            try { LogHelper.e("P2PListenService", "接受连接失败", e); } catch (Exception ignored) {}
                        }
                    }
                }
                
            } catch (IOException e) {
                try { LogHelper.e("P2PListenService", "监听失败", e); } catch (Exception ignored) {}
            }
        });
    }
    
    private void handleClientConnection(Socket clientSocket) {
        try {
            try { LogHelper.i("P2PListenService", "处理客户端连接"); } catch (Exception ignored) {}
            clientSocket.close();
            try { LogHelper.i("P2PListenService", "P2P 功能开发中"); } catch (Exception ignored) {}
        } catch (Exception e) {
            try { LogHelper.e("P2PListenService", "处理连接失败", e); } catch (Exception ignored) {}
        }
    }
    
    @Override
    public void onDestroy() {
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        
        if (executorService != null) {
            executorService.shutdown();
        }
        try { LogHelper.i("P2PListenService", "服务销毁"); } catch (Exception ignored) {}
        
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
