package qzrs.Scrcpy;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;

import qzrs.Scrcpy.databinding.ActivitySetBinding;
import qzrs.Scrcpy.entity.AppData;
import qzrs.Scrcpy.helper.LogHelper;
import qzrs.Scrcpy.helper.PublicTools;
import qzrs.Scrcpy.helper.ViewTools;

public class SetActivity extends Activity {
  private ActivitySetBinding activitySetBinding;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ViewTools.setStatusAndNavBar(this);
    activitySetBinding = ActivitySetBinding.inflate(this.getLayoutInflater());
    setContentView(activitySetBinding.getRoot());
    drawUi();
    setButtonListener();
  }

  private void drawUi() {
    // 日志开关
    activitySetBinding.setOther.addView(ViewTools.createSwitchCard(this, 
      getString(R.string.set_log_title), 
      getString(R.string.set_log_detail),
      AppData.setting.getLogEnabled(),
      (checked) -> {
        AppData.setting.setLogEnabled(checked);
        LogHelper.setLogEnabled(checked);
      }).getRoot());
    
    // 查看日志
    activitySetBinding.setOther.addView(ViewTools.createTextCard(this, 
      getString(R.string.set_view_log), 
      () -> showLogContent()).getRoot());
    
    activitySetBinding.setOther.addView(ViewTools.createTextCard(this, getString(R.string.set_other_ip), () -> startActivity(new Intent(this, IpActivity.class))).getRoot());
    activitySetBinding.setOther.addView(ViewTools.createTextCard(this, getString(R.string.set_other_custom_key), () -> startActivity(new Intent(this, AdbKeyActivity.class))).getRoot());
    activitySetBinding.setOther.addView(ViewTools.createTextCard(this, getString(R.string.set_other_reset_key), () -> {
      AppData.keyPair = PublicTools.reGenerateAdbKeyPair();
      Toast.makeText(this, getString(R.string.toast_success), Toast.LENGTH_SHORT).show();
    }).getRoot());
  }
  
  private void showLogContent() {
    try {
      File logFile = LogHelper.getCurrentLogFile();
      if (logFile != null && logFile.exists()) {
        String content = LogHelper.readLogContent();
        if (content.isEmpty()) {
          content = "日志为空或未开启";
        }
        // 显示日志内容对话框
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("日志内容");
        builder.setMessage(content.length() > 5000 ? content.substring(0, 5000) + "\n\n... (内容过长，仅显示前5000字符)" : content);
        builder.setPositiveButton("确定", null);
        builder.setNeutralButton("分享", (dialog, which) -> {
          // 分享日志
          Intent shareIntent = new Intent(Intent.ACTION_SEND);
          shareIntent.setType("text/plain");
          shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Scrcpy 日志");
          shareIntent.putExtra(Intent.EXTRA_TEXT, content);
          startActivity(Intent.createChooser(shareIntent, "分享日志"));
        });
        builder.show();
      } else {
        Toast.makeText(this, "日志文件不存在", Toast.LENGTH_SHORT).show();
      }
    } catch (Exception e) {
      Toast.makeText(this, "读取日志失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  private void setButtonListener() {
    activitySetBinding.backButton.setOnClickListener(v -> finish());
  }
}
