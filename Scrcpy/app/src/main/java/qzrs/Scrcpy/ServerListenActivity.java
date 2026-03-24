package qzrs.Scrcpy;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.os.Bundle;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import qzrs.Scrcpy.databinding.ActivityServerListenBinding;
import qzrs.Scrcpy.entity.AppData;
import qzrs.Scrcpy.helper.LogHelper;
import qzrs.Scrcpy.helper.PublicTools;
import qzrs.Scrcpy.helper.ViewTools;
import qzrs.Scrcpy.server.Server;

/**
 * 无 ADB 直连模式 - 被控端启动 Server 监听
 * 用户点击"启动"后，Server 开始监听端口，等待控制端连接
 */
public class ServerListenActivity extends Activity {
    
    private ActivityServerListenBinding binding;
    private ExecutorService executorService;
    private boolean isListening = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewTools.setStatusAndNavBar(this);
        ViewTools.setLocale(this);
        binding = ActivityServerListenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        executorService = Executors.newSingleThreadExecutor();
        showLocalIp();
        setButtonListener();
    }
    
    private void showLocalIp() {
        try {
            android.util.Pair<ArrayList<String>, ArrayList<String>> ipPair = PublicTools.getLocalIp();
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
        } catch (Exception e) {
            LogHelper.e("ServerListen", "获取IP失败", e);
            binding.ipAddress.setText("获取失败");
        }
    }
    
    private void setButtonListener() {
        binding.backButton.setOnClickListener(v -> {
            stopListening();
            finish();
        });
        
        binding.startButton.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });
        
        binding.copyButton.setOnClickListener(v -> {
            try {
                String ip = binding.ipAddress.getText().toString().trim();
                String port = "25166";
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
            binding.statusText.setText("正在启动 Server...");
            
            // 在后台线程启动 Server
            executorService.execute(() -> {
                try {
                    // 调用 Server.main() 启动 server
                    // Server 会自动监听 serverPort (默认 25166)
                    String[] args = {
                        "serverPort=25166",
                        "listenerClip=1",
                        "isAudio=1",
                        "maxSize=1600",
                        "maxFps=60",
                        "maxVideoBit=4",
                        "keepAwake=1",
                        "supportH265=1",
                        "supportOpus=1"
                    };
                    
                    runOnUiThread(() -> {
                        binding.statusText.setText("Server 已启动\n等待控制端连接...");
                        binding.startButtonText.setText("停止");
                        binding.startButton.setEnabled(true);
                        
                        // 复制到剪贴板
                        try {
                            String ip = binding.ipAddress.getText().toString().split("\n")[0].trim();
                            String connectionString = ip + ":25166";
                            AppData.clipBoard.setPrimaryClip(
                                ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, connectionString)
                            );
                            Toast.makeText(this, "已启动，地址已复制", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            LogHelper.e("ServerListen", "复制失败", e);
                        }
                    });
                    
                    LogHelper.i("ServerListen", "Server 启动成功，等待连接...");
                    
                    // 调用 Server.main() 启动（会阻塞直到连接）
                    Server.main(args);
                    
                } catch (Exception e) {
                    LogHelper.e("ServerListen", "Server 启动失败", e);
                    runOnUiThread(() -> {
                        binding.statusText.setText("启动失败: " + e.getMessage());
                        binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
                        binding.startButtonText.setText("启动");
                        binding.startButton.setEnabled(true);
                        isListening = false;
                    });
                }
            });
            
        } catch (Exception e) {
            isListening = false;
            binding.startButton.setEnabled(true);
            binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
            binding.statusText.setText("启动失败: " + e.getMessage());
            LogHelper.e("ServerListen", "启动失败", e);
        }
    }
    
    private void stopListening() {
        isListening = false;
        
        // 关闭线程池
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        
        binding.statusText.setText("已停止");
        binding.startButtonText.setText("启动");
        binding.statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
        
        LogHelper.i("ServerListen", "监听已停止");
    }
    
    @Override
    protected void onDestroy() {
        stopListening();
        super.onDestroy();
    }
}
