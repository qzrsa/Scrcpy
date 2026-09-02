package qzrs.Scrcpy.client.view;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;

import java.nio.ByteBuffer;
import java.util.Objects;

import qzrs.Scrcpy.R;
import qzrs.Scrcpy.client.Client;
import qzrs.Scrcpy.client.tools.ClientController;
import qzrs.Scrcpy.client.tools.ControlPacket;
import qzrs.Scrcpy.client.tools.StatsOverlay;
import qzrs.Scrcpy.databinding.ActivityFullBinding;
import qzrs.Scrcpy.entity.AppData;
import qzrs.Scrcpy.entity.Device;
import qzrs.Scrcpy.helper.PublicTools;
import qzrs.Scrcpy.helper.ViewTools;

public class FullActivity extends Activity implements SensorEventListener {
  // 是否用本机重力驱动旋转；false = 横竖屏改为跟随被控端画面方向（正/反向仍由系统按重力自动选择）
  private static final boolean USE_LOCAL_SENSOR_ROTATE = false;
  // 宽高比判定阈值：大于 1.15 判横屏，小于 0.87 判竖屏，中间地带维持现状（防分屏/方屏抖动）
  private static final float RATIO_LANDSCAPE = 1.15f;
  private static final float RATIO_PORTRAIT = 0.87f;
  // 两次切换之间的最小间隔，避免分辨率频繁变化时反复重建 Activity
  private static final long ORIENTATION_SWITCH_INTERVAL = 1000L;

  private boolean isClose = false;
  private Device device;
  private ClientController clientController;
  private ActivityFullBinding activityFullBinding;
  private StatsOverlay statsOverlay;
  private boolean autoRotate;
  private boolean showFps = true;
  private boolean light = true;
  private int deviceOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
  private long lastOrientationSwitchTime = 0L;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ViewTools.setStatusAndNavBar(this);
    ViewTools.setFullScreen(this);
    activityFullBinding = ActivityFullBinding.inflate(this.getLayoutInflater());
    setContentView(activityFullBinding.getRoot());
    String uuid = getIntent().getStringExtra("uuid");
    device = Client.getDevice(uuid);
    clientController = Client.getClientController(uuid);
    if (device == null || clientController == null) return;
    clientController.setFullView(this);
    statsOverlay = clientController.getStatsOverlay();
    if (statsOverlay != null) {
      statsOverlay.setAnchorView(activityFullBinding.buttonNetwork);
      if (statsOverlay.isShowing()) statsOverlay.show(); // 横竖屏切换后重新定位
    }
    // 初始化
    activityFullBinding.barView.setVisibility(View.GONE);
    setNavBarHide(device.showNavBarOnConnect);
    autoRotate = AppData.setting.getAutoRotate();
    activityFullBinding.buttonAutoRotate.setImageResource(autoRotate ? R.drawable.auto : R.drawable.un_auto);
    // FPS 浮层：默认开启，左上角显示
    showFps = AppData.setting.getShowFps();
    activityFullBinding.buttonFps.setImageResource(showFps ? R.drawable.fps : R.drawable.fps_off);
    if (statsOverlay != null) {
      if (showFps && !statsOverlay.isFpsShowing()) statsOverlay.showFps();
      else if (!showFps && statsOverlay.isFpsShowing()) statsOverlay.hideFps();
    }
    if (!Objects.equals(device.startApp, "")) {
      activityFullBinding.buttonHome.setVisibility(View.GONE);
      activityFullBinding.buttonSwitch.setVisibility(View.GONE);
      activityFullBinding.buttonApp.setVisibility(View.GONE);
    }
    // 按键监听
    setButtonListener();
    setKeyEvent();
    // 更新textureView
    activityFullBinding.textureViewLayout.addView(clientController.getTextureView(), 0);
    activityFullBinding.textureViewLayout.post(this::updateMaxSize);
    // 进入全屏时先按被控端当前分辨率摆正一次横竖屏
    Pair<Integer, Integer> videoSize = clientController.getVideoSize();
    if (videoSize != null) applyDeviceOrientation(videoSize.first, videoSize.second);
    // 页面自动旋转
    AppData.sensorManager.registerListener(this, AppData.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
  }

  @Override
  protected void onPause() {
    AppData.sensorManager.unregisterListener(this);
    if (isChangingConfigurations()) activityFullBinding.textureViewLayout.removeView(clientController.getTextureView());
    else if (!isClose) clientController.handleAction(device.fullToMiniOnRunning ? "changeToMini" : "changeToSmall", ByteBuffer.wrap("changeToFull".getBytes()), 0);
    super.onPause();
  }

  @Override
  public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
    activityFullBinding.textureViewLayout.post(this::updateMaxSize);
    super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
  }

  @Override
  public void onBackPressed() {
  }

  private void updateMaxSize() {
    int width = activityFullBinding.textureViewLayout.getMeasuredWidth();
    int height = activityFullBinding.textureViewLayout.getMeasuredHeight();
    ByteBuffer byteBuffer = ByteBuffer.allocate(8);
    byteBuffer.putInt(width);
    byteBuffer.putInt(height);
    byteBuffer.flip();
    clientController.handleAction("updateMaxSize", byteBuffer, 0);
    if (!device.customResolutionOnConnect && device.changeResolutionOnRunning) clientController.handleAction("writeByteBuffer", ControlPacket.createChangeResolutionEvent((float) width / height), 0);
  }

  public void hide() {
    if (device == null || clientController == null) return;
    try {
      isClose = true;
      activityFullBinding.textureViewLayout.removeView(clientController.getTextureView());
      finish();
    } catch (Exception ignored) {
    }
  }

  // 设置按钮监听
  private void setButtonListener() {
    activityFullBinding.buttonBack.setOnClickListener(v -> clientController.handleAction("buttonBack", null, 0));
    activityFullBinding.buttonHome.setOnClickListener(v -> clientController.handleAction("buttonHome", null, 0));
    activityFullBinding.buttonSwitch.setOnClickListener(v -> clientController.handleAction("buttonSwitch", null, 0));
    activityFullBinding.buttonApp.setOnClickListener(v -> {
      clientController.handleAction("changeToApp", null, 0);
      changeBarView();
    });
    activityFullBinding.buttonMini.setOnClickListener(v -> clientController.handleAction("changeToMini", null, 0));
    activityFullBinding.buttonSmall.setOnClickListener(v -> clientController.handleAction("changeToSmall", null, 0));
    activityFullBinding.buttonClose.setOnClickListener(v -> Client.sendAction(device.uuid, "close", null, 0));
    activityFullBinding.buttonRotate.setOnClickListener(v -> {
      clientController.handleAction("buttonRotate", null, 0);
      changeBarView();
    });
    activityFullBinding.buttonNavBar.setOnClickListener(v -> {
      setNavBarHide(activityFullBinding.navBar.getVisibility() == View.GONE);
      changeBarView();
    });
    activityFullBinding.buttonPower.setOnClickListener(v -> {
      clientController.handleAction("buttonPower", null, 0);
      changeBarView();
    });
    activityFullBinding.buttonLight.setOnClickListener(v -> {
      light = !light;
      activityFullBinding.buttonLight.setImageResource(light ? R.drawable.lightbulb_off : R.drawable.lightbulb);
      clientController.handleAction(light ? "buttonLight" : "buttonLightOff", null, 0);
      changeBarView();
    });
    activityFullBinding.buttonMore.setOnClickListener(v -> changeBarView());
    // 最左侧 WIFI 图标：点击切换统计信息覆盖层显示/隐藏
    activityFullBinding.buttonNetwork.setOnClickListener(v -> {
      if (statsOverlay == null) return;
      if (statsOverlay.isShowing()) statsOverlay.hide();
      else statsOverlay.show();
    });
    // 锁定按钮（位于"更多"弹出面板内）
    activityFullBinding.buttonLock.setOnClickListener(v -> {
      clientController.handleAction("buttonLock", null, 0);
      changeBarView();
    });
    // WIFI 图标颜色随网络质量变化（绿/橙/红/白）
    if (statsOverlay != null) {
      statsOverlay.setOnQualityListener(color ->
        activityFullBinding.buttonNetwork.setImageTintList(ColorStateList.valueOf(color)));
    }
    // 自动旋转：开启时横竖屏跟随被控端画面，关闭时锁定当前方向
    activityFullBinding.buttonAutoRotate.setOnClickListener(v -> {
      autoRotate = !autoRotate;
      AppData.setting.setAutoRotate(autoRotate);
      activityFullBinding.buttonAutoRotate.setImageResource(autoRotate ? R.drawable.auto : R.drawable.un_auto);
      if (autoRotate) {
        Pair<Integer, Integer> videoSize = clientController.getVideoSize();
        if (videoSize != null) applyDeviceOrientation(videoSize.first, videoSize.second);
        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
      } else {
        lockCurrentOrientation();
      }
    });
    // FPS 开关（位于"更多"弹出面板内）：控制左上角 FPS 浮层显隐
    activityFullBinding.buttonFps.setOnClickListener(v -> {
      if (statsOverlay == null) return;
      showFps = !showFps;
      AppData.setting.setShowFps(showFps);
      activityFullBinding.buttonFps.setImageResource(showFps ? R.drawable.fps : R.drawable.fps_off);
      if (showFps) statsOverlay.showFps();
      else statsOverlay.hideFps();
    });
    // 被控端音量调节（不关闭面板，方便连续调音量）
    activityFullBinding.buttonVolumeUp.setOnClickListener(v -> clientController.handleAction("buttonVolumeUp", null, 0));
    activityFullBinding.buttonVolumeDown.setOnClickListener(v -> clientController.handleAction("buttonVolumeDown", null, 0));
  }

  // 导航栏隐藏
  private void setNavBarHide(boolean isShow) {
    activityFullBinding.navBar.setVisibility(isShow ? View.VISIBLE : View.GONE);
    activityFullBinding.buttonNavBar.setImageResource(isShow ? R.drawable.not_equal : R.drawable.equals);
    activityFullBinding.textureViewLayout.post(this::updateMaxSize);
    activityFullBinding.buttonMore.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
  }

  private void changeBarView() {
    boolean toShowView = activityFullBinding.barView.getVisibility() == View.GONE;
    boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    ViewTools.viewAnim(activityFullBinding.barView, toShowView, 0, PublicTools.dp2px(40f) * (isLandscape ? -1 : 1), (isStart -> {
      if (isStart && toShowView) activityFullBinding.barView.setVisibility(View.VISIBLE);
      else if (!isStart && !toShowView) activityFullBinding.barView.setVisibility(View.GONE);
    }));
  }

  private int lastOrientation = -1;

  // 根据被控端视频分辨率切换本端横竖屏（被控端进游戏强制横屏时自动跟随）
  // 用 SENSOR_LANDSCAPE / SENSOR_PORTRAIT：横竖由被控端决定，正/反向由系统按本机重力自动选择
  public void applyDeviceOrientation(int width, int height) {
    if (!autoRotate || width <= 0 || height <= 0) return;
    float ratio = width * 1.0f / height;
    int target;
    if (ratio > RATIO_LANDSCAPE) target = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    else if (ratio < RATIO_PORTRAIT) target = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
    else return; // 接近正方形或分屏，维持现状避免来回抖
    if (target == deviceOrientation) return;
    long now = System.currentTimeMillis();
    if (now - lastOrientationSwitchTime < ORIENTATION_SWITCH_INTERVAL) return;
    lastOrientationSwitchTime = now;
    deviceOrientation = target;
    setRequestedOrientation(target);
  }

  // 锁定到当前实际方向（关闭自动旋转时调用）
  private void lockCurrentOrientation() {
    deviceOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    int current = getResources().getConfiguration().orientation;
    setRequestedOrientation(current == Configuration.ORIENTATION_LANDSCAPE
      ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
  }

  @Override
  public void onSensorChanged(SensorEvent sensorEvent) {
    // 横竖屏已改为跟随被控端画面方向（见 applyDeviceOrientation），本机重力不再驱动旋转
    if (!USE_LOCAL_SENSOR_ROTATE) return;
    if (!autoRotate || Sensor.TYPE_ACCELEROMETER != sensorEvent.sensor.getType()) return;
    float[] values = sensorEvent.values;
    float x = values[0];
    float y = values[1];
    int newOrientation = lastOrientation;

    if (x > -3 && x < 3 && y >= 4.5) newOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    else if (y > -3 && y < 3 && x >= 4.5) newOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    else if (y > -3 && y < 3 && x <= -4.5) newOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
    else if (x > -3 && x < 3 && y <= -4.5) newOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;

    if (lastOrientation != newOrientation) {
      lastOrientation = newOrientation;
      setRequestedOrientation(newOrientation);
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int i) {

  }

  // 设置键盘监听
  private void setKeyEvent() {
    activityFullBinding.editText.requestFocus();
    activityFullBinding.editText.setInputType(InputType.TYPE_NULL);
    activityFullBinding.editText.setOnKeyListener((v, keyCode, event) -> {
      if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
        clientController.handleAction("writeByteBuffer", ControlPacket.createKeyEvent(event.getKeyCode(), event.getMetaState()), 0);
        return true;
      }
      return false;
    });
  }
}
