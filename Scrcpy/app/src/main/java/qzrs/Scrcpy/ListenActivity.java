package qzrs.Scrcpy;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.LayoutInflater;
import android.widget.Toast;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import qzrs.Scrcpy.client.view.FullActivity;
import qzrs.Scrcpy.databinding.ActivityListenBinding;
import qzrs.Scrcpy.databinding.ItemLoadingBinding;
import qzrs.Scrcpy.entity.AppData;
import qzrs.Scrcpy.entity.Device;
import qzrs.Scrcpy.helper.LogHelper;
import qzrs.Scrcpy.helper.PublicTools;
import qzrs.Scrcpy.helper.ViewTools;

/**
 * P2P 等待连接模式
 * 
 * 被控端启动此模式后，会在指定端口监听来自控制端的连接
 * 控制端直接通过 IP:端口 连接，无需 ADB
 */
public class ListenActivity extends Activity {
    
    private ActivityListenBinding binding;
    
    private static final int DEFAULT_PORT = 25166;
    private static final String SERVER_FILENAME = "scrcpy_server.jar";
    
    private ServerSocket serverSocket;
    private boolean isListening = false;
    private int listeningPort = DEFAULT_PORT;
    private ExecutorService executorService;
    private Handler mainHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewTools.setStatusAndNavBar(this);
        ViewTools.setLocale(this);
        binding = ActivityListenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // 显示本机 IP
        showLocalIp();
        
        // 设置按钮监听
        setButtonListener();
        
        // 自动开始监听
        startListening();
    }
    
    /**
     * 显示本机 IP 地址
     */
    private void showLocalIp() {
        Pair<ArrayList<String>, ArrayList<String>> ipPair = PublicTools.getLocalIp();
        StringBuilder sb = new StringBuilder();
        
        // 优先显示 IPv4
        if (!ipPair.first.isEmpty()) {
            for (String ip : ipPair.first) {
                if (!sb.toString().contains(ip)) {
                    sb.append(ip).append("\n");
                }
            }
        }
        
        // 添加 IPv6（如果有）
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
    
    /**
     * 设置按钮监听
     */
    private void setButtonListener() {
        binding.backButton.setOnClickListener(v -> stopListeningAndFinish());
        
        binding.startButton.setOnClickListener(v -> {
            // 读取端口
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
        
        // 复制 IP:端口
        binding.copyButton.setOnClickListener(v -> {
            String ip = binding.ipAddress.getText().toString().trim();
            String port = binding.port.getText().toString().trim();
            // 取第一个 IP
            String firstIp = ip.split("\n")[0].trim();
            String connectionString = firstIp + ":" + port;
            
            AppData.clipBoard.setPrimaryClip(
                ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, connectionString)
            );
            Toast.makeText(this, getString(R.string.toast_copy), Toast.LENGTH_SHORT).show();
        });
    }
    
    /**
     * 开始监听连接
     */
    private void startListening() {
        if (isListening) return;
        
        isListening = true;
        binding.statusText.setText("等待控制端连接...");
        binding.startButton.setText("停止");
        binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_ok);
        
        // 更新日志
        LogHelper.i("ListenActivity", "开始监听端口: " + listeningPort);
        
        executorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(listeningPort);
                serverSocket.setReuseAddress(true);
                
                mainHandler.post(() -> {
                    binding.statusText.setText("等待控制端连接...\nIP: " + binding.ipAddress.getText().toString().split("\n")[0] + ":" + listeningPort);
                });
                
                LogHelper.i("ListenActivity", "ServerSocket 已启动，监听端口: " + listeningPort);
                
                // 等待客户端连接
                Socket clientSocket = serverSocket.accept();
                
                LogHelper.i("ListenActivity", "收到连接 from: " + clientSocket.getRemoteSocketAddress());
                
                mainHandler.post(() -> {
                    binding.statusText.setText("控制端已连接！");
                    binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_ok);
                });
                
                // 处理客户端连接
                handleClientConnection(clientSocket);
                
            } catch (IOException e) {
                if (isListening) {
                    LogHelper.e("ListenActivity", "监听失败: " + e.getMessage());
                    mainHandler.post(() -> {
                        binding.statusText.setText("监听失败: " + e.getMessage());
                        binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
                        binding.startButton.setText("启动");
                    });
                }
            }
        });
    }
    
    /**
     * 处理客户端连接
     */
    private void handleClientConnection(Socket clientSocket) {
        try {
            mainHandler.post(() -> {
                // 显示连接成功
                ItemLoadingBinding loading = ViewTools.createLoading(this);
                loading.second.show();
                binding.statusText.setText("连接成功！正在启动服务...");
            });
            
            // 这里可以添加启动 scrcpy server 的逻辑
            // 由于没有 ADB，我们需要另一种方式启动服务
            
            // 暂时先显示成功，实际启动需要更复杂的实现
            mainHandler.post(() -> {
                Toast.makeText(this, "连接成功！P2P 模式即将推出...", Toast.LENGTH_LONG).show();
                binding.statusText.setText("连接成功！\n请等待后续功能...");
            });
            
            LogHelper.i("ListenActivity", "客户端连接处理完成");
            
        } catch (Exception e) {
            LogHelper.e("ListenActivity", "处理连接失败", e);
            mainHandler.post(() -> {
                binding.statusText.setText("连接处理失败: " + e.getMessage());
                binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
            });
        }
    }
    
    /**
     * 停止监听
     */
    private void stopListening() {
        isListening = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException e) {
            LogHelper.e("ListenActivity", "关闭 ServerSocket 失败", e);
        }
        
        binding.statusText.setText("已停止监听");
        binding.startButton.setText("启动");
        binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
        
        LogHelper.i("ListenActivity", "停止监听");
    }
    
    /**
     * 停止并退出
     */
    private void stopListeningAndFinish() {
        stopListening();
        finish();
    }
    
    @Override
    protected void onDestroy() {
        stopListening();
        executorService.shutdown();
        super.onDestroy();
    }
}
