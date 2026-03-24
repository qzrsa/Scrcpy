package qzrs.Scrcpy;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.os.Bundle;
import android.util.Pair;
import android.widget.Toast;

import java.net.ServerSocket;
import java.util.ArrayList;

import qzrs.Scrcpy.databinding.ActivityListenBinding;
import qzrs.Scrcpy.entity.AppData;
import qzrs.Scrcpy.helper.LogHelper;
import qzrs.Scrcpy.helper.PublicTools;
import qzrs.Scrcpy.helper.ViewTools;

/**
 * P2P 等待连接模式 - 简化版
 * 直接在 Activity 中监听，不使用服务
 */
public class ListenActivity extends Activity {
    
    private ActivityListenBinding binding;
    private static final int DEFAULT_PORT = 25166;
    
    private boolean isListening = false;
    private ServerSocket serverSocket;
    private Thread listenThread;
    private int listeningPort = DEFAULT_PORT;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewTools.setStatusAndNavBar(this);
        ViewTools.setLocale(this);
        binding = ActivityListenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        showLocalIp();
        setButtonListener();
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
        binding.backButton.setOnClickListener(v -> {
            stopListening();
            finish();
        });
        
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
            }
        });
    }
    
    private void startListening() {
        if (isListening) return;
        
        try {
            isListening = true;
            binding.startButton.setEnabled(false);
            binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_ok);
            binding.statusText.setText("正在启动...");
            
            // 创建 ServerSocket
            serverSocket = new ServerSocket(listeningPort);
            serverSocket.setReuseAddress(true);
            
            // 更新 UI
            String ip = binding.ipAddress.getText().toString().split("\n")[0].trim();
            binding.statusText.setText("等待控制端连接...\n\n" +
                "连接地址: " + ip + ":" + listeningPort + "\n\n" +
                "在控制端输入以上地址");
            binding.startButtonText.setText("停止");
            binding.startButton.setEnabled(true);
            
            // 复制到剪贴板
            AppData.clipBoard.setPrimaryClip(
                ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, ip + ":" + listeningPort)
            );
            
            Toast.makeText(this, "已启动，地址已复制", Toast.LENGTH_SHORT).show();
            
            LogHelper.i("ListenActivity", "监听开始: " + ip + ":" + listeningPort);
            
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
        isListening = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (Exception e) {
            LogHelper.e("ListenActivity", "关闭失败", e);
        }
        
        binding.statusText.setText("已停止监听");
        binding.startButtonText.setText("启动");
        binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
        
        LogHelper.i("ListenActivity", "监听停止");
    }
    
    @Override
    protected void onDestroy() {
        stopListening();
        super.onDestroy();
    }
}
