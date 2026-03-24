package qzrs.Scrcpy;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    
    private ImageButton backButton;
    private ImageButton copyButton;
    private Button startButton;
    private TextView ipAddress;
    private TextView statusText;
    private View statusIndicator;
    
    private ExecutorService executorService;
    private boolean isListening = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewTools.setStatusAndNavBar(this);
        ViewTools.setLocale(this);
        setContentView(R.layout.activity_server_listen);
        
        initViews();
        executorService = Executors.newSingleThreadExecutor();
        showLocalIp();
        setButtonListener();
    }
    
    private void initViews() {
        backButton = findViewById(R.id.back_button);
        copyButton = findViewById(R.id.copy_button);
        startButton = findViewById(R.id.start_button);
        ipAddress = findViewById(R.id.ip_address);
        statusText = findViewById(R.id.status_text);
        statusIndicator = findViewById(R.id.status_indicator);
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
            
            ipAddress.setText(sb.toString().trim());
        } catch (Exception e) {
            LogHelper.e("ServerListen", "获取IP失败", e);
            ipAddress.setText("获取失败");
        }
    }
    
    private void setButtonListener() {
        backButton.setOnClickListener(v -> {
            stopListening();
            finish();
        });
        
        startButton.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });
        
        copyButton.setOnClickListener(v -> {
            try {
                String ip = ipAddress.getText().toString().trim();
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
            startButton.setEnabled(false);
            statusIndicator.setBackgroundResource(R.drawable.background_circle_ok);
            statusText.setText("正在启动 Server...");
            
            // 在后台线程启动 Server
            executorService.execute(() -> {
                try {
                    // 调用 Server.main() 启动 server
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
                        statusText.setText("Server 已启动\n等待控制端连接...");
                        startButton.setText("停止");
                        startButton.setEnabled(true);
                        
                        // 复制到剪贴板
                        try {
                            String ip = ipAddress.getText().toString().split("\n")[0].trim();
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
                        statusText.setText("启动失败: " + e.getMessage());
                        statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
                        startButton.setText("启动");
                        startButton.setEnabled(true);
                        isListening = false;
                    });
                }
            });
            
        } catch (Exception e) {
            isListening = false;
            startButton.setEnabled(true);
            statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
            statusText.setText("启动失败: " + e.getMessage());
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
        
        statusText.setText("已停止");
        startButton.setText("启动");
        statusIndicator.setBackgroundResource(R.drawable.background_circle_warn);
        
        LogHelper.i("ServerListen", "监听已停止");
    }
    
    @Override
    protected void onDestroy() {
        stopListening();
        super.onDestroy();
    }
}
