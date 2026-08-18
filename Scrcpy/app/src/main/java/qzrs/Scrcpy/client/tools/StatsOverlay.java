package qzrs.Scrcpy.client.tools;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import qzrs.Scrcpy.entity.AppData;
import qzrs.Scrcpy.entity.MyInterface;

/**
 * 悬浮统计信息覆盖层：默认隐藏，由底部 WIFI 图标点击切换显示。
 * 展示 12 行链路/解码详情；WIFI 图标颜色随网络质量变化（绿/橙/红）。
 */
public class StatsOverlay {
  private final TextView textView;
  private final WindowManager.LayoutParams params;
  private boolean isAdded = false;

  // 统计数据
  private int frameCount = 0;            // 视频接收帧计数（用于接收帧率）
  private long byteCount = 0;            // 视频接收字节累计
  private int fps = 0;                   // 视频接收帧率
  private float speedKbps = 0f;          // 网速 KB/s
  private long latencyMs = -1;           // keepAlive RTT（网络/接口/端到端均以此为基准）
  private int captureFps = 0;            // 视频采集帧率（服务端上报）
  private int renderFps = 0;             // 视频渲染帧率（客户端解码输出）
  private int bitrate = 0;               // 当前编码码率 bps（服务端上报）
  private String decodeMethod = "--";    // 视频解码方式
  private boolean isDirect = false;      // 是否网络直连
  private String source = "设备屏幕";     // 视频源
  private int lastLossPct = -1;          // 丢包率（基于采集vs接收帧率估算）

  // 网络质量阈值与配色
  private static final long RTT_GREEN_MS = 80;
  private static final long RTT_ORANGE_MS = 200;
  private static final int LOSS_GREEN_PCT = 5;
  private static final int LOSS_ORANGE_PCT = 15;
  private static final int COLOR_GREEN = 0xFF4CAF50;
  private static final int COLOR_ORANGE = 0xFFFF9800;
  private static final int COLOR_RED = 0xFFF44336;
  private int lastQualityColor = 0;

  private MyInterface.MyFunctionInt onQualityListener;

  private long lastUpdateTime = System.currentTimeMillis();

  public StatsOverlay() {
    textView = new TextView(AppData.applicationContext);
    textView.setTextColor(Color.WHITE);
    textView.setTextSize(12f);
    textView.setLineSpacing(2f, 1f);
    textView.setShadowLayer(3f, 1f, 1f, Color.BLACK);
    textView.setPadding(12, 8, 12, 8);
    textView.setBackgroundColor(0x88000000);

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

  public void show() {
    AppData.uiHandler.post(() -> {
      if (!isAdded) {
        AppData.windowManager.addView(textView, params);
        isAdded = true;
      }
    });
  }

  public void hide() {
    AppData.uiHandler.post(() -> {
      if (isAdded) {
        AppData.windowManager.removeView(textView);
        isAdded = false;
      }
    });
  }

  /** 覆盖层当前是否已显示（供 WIFI 图标做点击切换判断） */
  public boolean isShowing() {
    return isAdded;
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

  /** 服务端每秒上报：视频采集帧率 */
  public void onCaptureFps(int fps) {
    captureFps = fps;
    updateText();
  }

  /** 客户端解码输出帧率（渲染帧率） */
  public void onRenderFps(int fps) {
    renderFps = fps;
    updateText();
  }

  public void setDecodeMethod(String method) {
    decodeMethod = method;
    updateText();
  }

  public void setDirect(boolean direct) {
    isDirect = direct;
    updateText();
  }

  public void setSource(String src) {
    source = src;
    updateText();
  }

  public void setBitrate(int bps) {
    bitrate = bps;
    updateText();
  }

  /** 注册网络质量变化回调（用于给 WIFI 图标染色） */
  public void setOnQualityListener(MyInterface.MyFunctionInt listener) {
    onQualityListener = listener;
    updateQuality(latencyMs, lastLossPct); // 立即推送当前颜色
  }

  private void updateText() {
    String latency = latencyMs < 0 ? "--" : latencyMs + "ms";
    String speed = speedKbps >= 1024
      ? String.format("%.1fMB/s", speedKbps / 1024f)
      : String.format("%.0fKB/s", speedKbps);
    if (bitrate > 0) speed += " (" + String.format("%.1f", bitrate / 1000000f) + "Mbps)";
    int recv = fps;
    int lossPct = (captureFps > 0 && recv > 0) ? Math.max(0, (int) ((1f - (float) recv / captureFps) * 100)) : -1;
    lastLossPct = lossPct;
    int total = latencyMs < 0 ? -1 : (int) (latencyMs + (renderFps > 0 ? 1000 / renderFps : 0));
    String loss = lossPct < 0 ? "--" : lossPct + "%";
    String text = "视频源: " + source
      + "\n网络直连: " + (isDirect ? "是" : "否(ADB隧道)")
      + "\n视频解码方式: " + decodeMethod
      + "\n网速: " + speed
      + "\n总延迟: " + (total < 0 ? "--" : total + "ms")
      + "\n网络延迟: " + latency
      + "\n端到端RTT: " + latency
      + "\n接口RTT: " + latency
      + "\n视频采集帧率: " + captureFps + " fps"
      + "\n视频接收帧率: " + recv + " fps"
      + "\n视频渲染帧率: " + renderFps + " fps"
      + "\n丢包率: " + loss;
    AppData.uiHandler.post(() -> {
      textView.setText(text);
      updateQuality(latencyMs, lossPct);
    });
  }

  /** 根据 RTT 与丢包率推导网络质量配色 */
  private void updateQuality(long rtt, int lossPct) {
    int color;
    if (rtt < 0) color = 0xFFFFFFFF; // 未知：白
    else if (rtt <= RTT_GREEN_MS && (lossPct < 0 || lossPct < LOSS_GREEN_PCT)) color = COLOR_GREEN;
    else if (rtt <= RTT_ORANGE_MS && (lossPct < 0 || lossPct < LOSS_ORANGE_PCT)) color = COLOR_ORANGE;
    else color = COLOR_RED;
    if (color != lastQualityColor) {
      lastQualityColor = color;
      final int c = color;
      if (onQualityListener != null) AppData.uiHandler.post(() -> onQualityListener.run(c));
    }
  }
}
