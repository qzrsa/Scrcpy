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
import qzrs.Scrcpy.entity.AppData;
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
            try {
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
            } catch (Exception e) {
                LogHelper.e("ListenActivity", "创建通知频道失败", e);
            }
        }
    }
    
    private void showLocalIp() {
        try {
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
        } catch (Exception e) {
            LogHelper.e("ListenActivity", "获取IP失败", e);
            binding.ipAddress.setText("获取失败");
        }
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
            try {
                String ip = binding.ipAddress.getText().toString().trim();
                String port = binding.port.getText().toString().trim();
                String firstIp = ip.split("\n")[0].trim();
                String connectionString = firstIp + ":" + port;
                
                AppData.clipBoard.setPrimaryClip(
                    ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, connectionString)
                );
                Toast.makeText(this, getString(R.string.toast_copy), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
                LogHelper.e("ListenActivity", "复制失败", e);
            }
        });
    }
    
    private void startListening() {
        if (isListening) return;
        
        try {
            isListening = true;
            binding.startButton.setEnabled(false);
            binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_ok);
            binding.statusText.setText("正在启动服务...");
            
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
                try {
                    if (!isListening) return;
                    
                    binding.startButton.setEnabled(true);
                    binding.startButtonText.setText("停止");
                    
                    String ip = binding.ipAddress.getText().toString().split("\n")[0].trim();
                    binding.statusText.setText("等待控制端连接...\n\n" +
                        "连接地址: " + ip + ":" + listeningPort + "\n\n" +
                        "在控制端输入以上地址即可连接");
                    
                    Toast.makeText(this, "服务已启动", Toast.LENGTH_SHORT).show();
                    
                    // 自动复制到剪贴板
                    AppData.clipBoard.setPrimaryClip(
                        ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, ip + ":" + listeningPort)
                    );
                } catch (Exception e) {
                    LogHelper.e("ListenActivity", "更新UI失败", e);
                }
            }, 1000);
            
            LogHelper.i("ListenActivity", "P2P 监听服务已启动，端口: " + listeningPort);
            
        } catch (Exception e) {
            isListening = false;
            binding.startButton.setEnabled(true);
            binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
            binding.statusText.setText("启动失败: " + e.getMessage());
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            LogHelper.e("ListenActivity", "启动失败", e);
        }
    }
    
    private void stopListening() {
        try {
            isListening = false;
            
            // 停止服务
            Intent serviceIntent = new Intent(this, P2PListenService.class);
            stopService(serviceIntent);
            
            binding.statusText.setText("已停止监听");
            binding.startButtonText.setText("启动");
            binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
            binding.startButton.setEnabled(true);
            
            LogHelper.i("ListenActivity", "P2P 监听服务已停止");
        } catch (Exception e) {
            LogHelper.e("ListenActivity", "停止失败", e);
        }
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
