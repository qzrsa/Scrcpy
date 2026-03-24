package qzrs.Scrcpy.client.tools;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import qzrs.Scrcpy.entity.AppData;
import qzrs.Scrcpy.entity.Device;

/**
 * 悬浮统计信息覆盖层：显示帧率、下载速度、连接模式、延迟
 */
public class StatsOverlay {
  private final TextView textView;
  private final WindowManager.LayoutParams params;
  private boolean isAdded = false;
  
  // 当前连接模式
  private int connectionMode = Device.CONNECTION_MODE_DEFAULT;
  private String connectionModeStr = "ADB";

  // 统计数据
  private int frameCount = 0;
  private long byteCount = 0;
  private int fps = 0;
  private float speedKbps = 0f;
  private long latencyMs = -1;

  private long lastUpdateTime = System.currentTimeMillis();
  
  // 是否可见（全屏时显示，小窗时隐藏）
  private boolean isVisible = false;

  public StatsOverlay() {
    textView = new TextView(AppData.applicationContext);
    textView.setTextColor(Color.WHITE);
    textView.setTextSize(11f);
    textView.setShadowLayer(3f, 1f, 1f, Color.BLACK);
    textView.setPadding(12, 6, 12, 6);
    textView.setBackgroundColor(0x88000000);
    
    // 点击后变透明
    textView.setOnClickListener(v -> {
      textView.setVisibility(View.INVISIBLE);
    });

    params = new WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        : WindowManager.LayoutParams.TYPE_PHONE,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    );
    params.gravity = Gravity.TOP | Gravity.START;
    params.x = 16;
    params.y = 80;
  }
  
  /**
   * 设置连接模式
   * @param mode 连接模式：0=ADB, 1=中继模式, 2=直连
   */
  public void setConnectionMode(int mode) {
    this.connectionMode = mode;
    switch (mode) {
      case Device.CONNECTION_MODE_DIRECT:
        this.connectionModeStr = "直连";
        break;
      case Device.CONNECTION_MODE_RELAY:
        this.connectionModeStr = "中继";
        break;
      default:
        this.connectionModeStr = "ADB";
        break;
    }
    updateText();
  }

  public void show() {
    if (isVisible) return;
    isVisible = true;
    
    AppData.uiHandler.post(() -> {
      if (!isAdded) {
        AppData.windowManager.addView(textView, params);
        isAdded = true;
      }
      textView.setVisibility(View.VISIBLE);
    });
  }

  public void hide() {
    if (!isVisible) return;
    isVisible = false;
    
    AppData.uiHandler.post(() -> {
      if (isAdded) {
        AppData.windowManager.removeView(textView);
        isAdded = false;
      }
    });
  }
  
  /**
   * 全屏时显示
   */
  public void showForFullScreen() {
    show();
  }
  
  /**
   * 小窗时隐藏
   */
  public void hideForSmallWindow() {
    hide();
  }

  /** 每解码一帧视频时调用，bytes 为本帧数据字节数 */
  public void onVideoFrame(int bytes) {
    frameCount++;
    byteCount += bytes;
    long now = System.currentTimeMillis();
    long elapsed = now - lastUpdateTime;
    if (elapsed >= 1000) {
      fps = (int) (frameCount * 1000L / elapsed);
      speedKbps = byteCount * 1000f / elapsed / 1024f;
      frameCount = 0;
      byteCount = 0;
      lastUpdateTime = now;
      updateText();
    }
  }

  /** keepAlive RTT 测量结果回调，ms 为往返延迟毫秒数 */
  public void onLatency(long ms) {
    latencyMs = ms;
    updateText();
  }

  private void updateText() {
    String latencyStr = latencyMs < 0 ? "--" : latencyMs + "ms";
    String speedStr = speedKbps >= 1024
      ? String.format("%.1fMB/s", speedKbps / 1024f)
      : String.format("%.0fKB/s", speedKbps);
    
    // 格式：fps 速度 模式 延迟
    String text = fps + "fps  " + speedStr + "  " + connectionModeStr + "  " + latencyStr;
    AppData.uiHandler.post(() -> textView.setText(text));
  }
}
