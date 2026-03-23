package qzrs.Scrcpy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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
        LogHelper.i("P2PListenService", "服务创建");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null) {
                listeningPort = intent.getIntExtra("port", 25166);
            }
            
            startForeground(NOTIFICATION_ID, createNotification());
            startListening();
            
            return START_STICKY;
        } catch (Exception e) {
            LogHelper.e("P2PListenService", "启动服务失败", e);
            stopSelf();
            return START_NOT_STICKY;
        }
    }
    
    private Notification createNotification() {
        try {
            Intent notificationIntent = new Intent(this, ListenActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            );
            
            return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("P2P 投屏服务运行中")
                .setContentText("监听端口: " + listeningPort)
                .setSmallIcon(R.drawable.wifi)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        } catch (Exception e) {
            LogHelper.e("P2PListenService", "创建通知失败", e);
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
                
                LogHelper.i("P2PListenService", "开始监听端口: " + listeningPort);
                
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        LogHelper.i("P2PListenService", "收到连接: " + clientSocket.getRemoteSocketAddress());
                        
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
            
            // 关闭连接（真正的实现需要启动 scrcpy server）
            clientSocket.close();
            
            LogHelper.i("P2PListenService", "P2P 直连功能开发中");
            
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
        
        if (executorService != null) {
            executorService.shutdown();
        }
        LogHelper.i("P2PListenService", "服务销毁");
        
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
