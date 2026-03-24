package qzrs.Scrcpy.helper;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * 日志工具类
 * - 每天创建1个日志文件
 * - 日志最多保存5个
 * - 自动清理多余的旧日志
 * 
 * 日志路径（按优先级）：
 * 1. 外部存储: /storage/emulated/0/Android/data/qzrs.Scrcpy/files/logs
 * 2. 内部存储: /data/data/qzrs.Scrcpy/files/logs
 */
public class LogHelper {
    private static final String TAG = "Scrcpy";
    private static final String LOG_DIR = "logs";
    private static final int MAX_LOG_FILES = 5;
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat FILE_NAME_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    
    private static File logFile;
    private static File logDir;
    private static boolean useInternalStorage = true;
    private static boolean logEnabled = false; // 日志开关，默认关闭
    
    /**
     * 设置日志开关
     */
    public static void setLogEnabled(boolean enabled) {
        logEnabled = enabled;
        if (enabled) {
            i(TAG, "日志已开启");
        }
    }
    
    /**
     * 获取日志开关状态
     */
    public static boolean isLogEnabled() {
        return logEnabled;
    }
    
    /**
     * 初始化日志系统
     */
    public static void init(Context context) {
        // 读取日志开关设置
        try {
            logEnabled = qzrs.Scrcpy.entity.AppData.setting.getLogEnabled();
        } catch (Exception e) {
            logEnabled = false;
        }
        
        logDir = getLogDirectory(context);
        
        // 确保目录存在
        if (!logDir.exists()) {
            boolean created = logDir.mkdirs();
            if (!created) {
                // 如果创建失败，尝试使用内部存储
                Log.w(TAG, "创建日志目录失败，尝试使用内部存储");
                useInternalStorage = true;
                logDir = getInternalLogDirectory(context);
                logDir.mkdirs();
            }
        }
        
        cleanOldLogs();
        logFile = getTodayLogFile();
        
        i(TAG, "========== 应用启动 ==========");
        i(TAG, "存储类型: " + (useInternalStorage ? "内部存储" : "外部存储"));
        i(TAG, "日志路径: " + getLogDirectoryPath());
        i(TAG, "日志文件: " + getLogFilePath());
    }
    
    /**
     * 获取内部存储日志目录
     * 路径: /data/data/qzrs.Scrcpy/files/logs
     */
    private static File getInternalLogDirectory(Context context) {
        return new File(context.getFilesDir(), LOG_DIR);
    }
    
    /**
     * 获取外部存储日志目录
     * 路径: /storage/emulated/0/Android/data/qzrs.Scrcpy/files/logs
     */
    private static File getExternalLogDirectory(Context context) {
        File androidDataDir = new File(Environment.getExternalStorageDirectory(), "Android/data");
        File packageDir = new File(androidDataDir, context.getPackageName());
        File filesDir = new File(packageDir, "files");
        return new File(filesDir, LOG_DIR);
    }
    
    /**
     * 获取日志目录（优先使用外部存储的Android/data目录）
     */
    private static File getLogDirectory(Context context) {
        // 优先使用外部存储的 Android/data 目录
        File externalDir = getExternalLogDirectory(context);
        if (canWrite(externalDir)) {
            useInternalStorage = false;
            return externalDir;
        }
        
        // 外部存储不可用，使用内部存储
        File internalDir = getInternalLogDirectory(context);
        if (canWrite(internalDir)) {
            useInternalStorage = true;
            return internalDir;
        }
        
        // 都不可用，返回内部存储（会尝试创建）
        useInternalStorage = true;
        return internalDir;
    }
    
    /**
     * 检查目录是否可写
     */
    private static boolean canWrite(File dir) {
        try {
            if (!dir.exists()) {
                return dir.mkdirs();
            }
            // 尝试创建测试文件
            File testFile = new File(dir, ".write_test");
            boolean canWrite = testFile.createNewFile();
            if (canWrite) {
                testFile.delete();
            }
            return canWrite;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取今天的日志文件
     * 文件名: yyyy-MM-dd.log（每天一个文件）
     */
    private static File getTodayLogFile() {
        String fileName = FILE_NAME_FORMAT.format(new Date()) + ".log";
        return new File(logDir, fileName);
    }
    
    /**
     * 清理超过5个的旧日志
     */
    private static void cleanOldLogs() {
        if (logDir == null || !logDir.exists()) {
            return;
        }
        
        File[] files = logDir.listFiles((dir, name) -> name.endsWith(".log"));
        
        if (files == null || files.length <= MAX_LOG_FILES) {
            return;
        }
        
        // 按文件名排序（日期），保留最新的5个
        Arrays.sort(files, Comparator.comparing(File::getName).reversed());
        
        int deleteCount = files.length - MAX_LOG_FILES;
        for (int i = 0; i < deleteCount; i++) {
            files[i].delete();
        }
    }
    
    /**
     * 写日志
     */
    private static void writeLog(String level, String tag, String message, Throwable throwable) {
        // 检查日志开关
        if (!logEnabled) {
            return;
        }
        
        if (logFile == null || logDir == null) {
            return;
        }
        
        // 检查目录是否存在
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        String today = DATE_FORMAT.format(new Date());
        String lastModifiedDate = logFile.exists() ? DATE_FORMAT.format(new Date(logFile.lastModified())) : "";
        
        if (!today.equals(lastModifiedDate)) {
            logFile = getTodayLogFile();
            cleanOldLogs();
        }
        
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter pw = new PrintWriter(bw)) {
            
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(TIME_FORMAT.format(new Date())).append("] ");
            sb.append("[").append(level).append("] ");
            sb.append("[").append(tag).append("] ");
            sb.append(message);
            
            if (throwable != null) {
                sb.append("\n");
                StringWriter sw = new StringWriter();
                PrintWriter swPw = new PrintWriter(sw);
                throwable.printStackTrace(swPw);
                sb.append(sw.toString());
            }
            
            pw.println(sb.toString());
            pw.flush();
            
        } catch (IOException e) {
            Log.e(TAG, "写日志失败: " + e.getMessage());
        }
    }
    
    // ==================== 公开方法 ====================
    
    public static void d(String tag, String message) {
        Log.d(tag, message);
        writeLog("D", tag, message, null);
    }
    
    public static void i(String tag, String message) {
        Log.i(tag, message);
        writeLog("I", tag, message, null);
    }
    
    public static void w(String tag, String message) {
        Log.w(tag, message);
        writeLog("W", tag, message, null);
    }
    
    public static void e(String tag, String message) {
        Log.e(tag, message);
        writeLog("E", tag, message, null);
    }
    
    public static void e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
        writeLog("E", tag, message, throwable);
    }
    
    public static void wtf(String tag, String message) {
        Log.wtf(tag, message);
        writeLog("F", tag, "WTF: " + message, null);
    }
    
    public static void wtf(String tag, String message, Throwable throwable) {
        Log.wtf(tag, message, throwable);
        writeLog("F", tag, "WTF: " + message, throwable);
    }
    
    // ==================== 便捷方法 ====================
    
    public static void methodIn(String tag) {
        d(tag, ">>> 进入方法");
    }
    
    public static void methodOut(String tag) {
        d(tag, "<<< 退出方法");
    }
    
    public static void methodTime(String tag, String methodName, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        d(tag, "方法 [" + methodName + "] 执行耗时: " + duration + "ms");
    }
    
    public static void var(String tag, String name, Object value) {
        d(tag, "变量 [" + name + "] = " + String.valueOf(value));
    }
    
    public static void call(String tag, String methodName, Object... args) {
        StringBuilder sb = new StringBuilder();
        sb.append("调用方法: ").append(methodName).append("(");
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.valueOf(args[i]));
            }
        }
        sb.append(")");
        d(tag, sb.toString());
    }
    
    public static void connection(String tag, String deviceAddress, int port, int mode) {
        String[] modeNames = {"默认(ADB)", "P2P直连", "中继模式"};
        String modeStr = mode >= 0 && mode < modeNames.length ? modeNames[mode] : "未知";
        i(tag, "连接设备: 地址=" + deviceAddress + ", 端口=" + port + ", 模式=" + modeStr);
    }
    
    public static void device(String tag, String deviceName, String uuid, int connectionMode) {
        String[] modeNames = {"默认(ADB)", "P2P直连", "中继模式"};
        String mode = connectionMode >= 0 && connectionMode < modeNames.length 
            ? modeNames[connectionMode] : "未知";
        i(tag, "设备信息: 名称=" + deviceName + ", UUID=" + uuid + ", 连接模式=" + mode);
    }
    
    public static void config(String tag, String key, Object oldValue, Object newValue) {
        i(tag, "配置变更: " + key + " [" + oldValue + " -> " + newValue + "]");
    }
    
    public static String getLogFilePath() {
        return logFile != null ? logFile.getAbsolutePath() : "";
    }
    
    public static String getLogDirectoryPath() {
        return logDir != null ? logDir.getAbsolutePath() : "";
    }
    
    /**
     * 获取存储类型描述
     */
    /**
     * 获取当前日志文件
     */
    public static File getCurrentLogFile() {
        return logFile;
    }
    
    /**
     * 读取日志内容
     */
    public static String readLogContent() {
        if (logFile == null || !logFile.exists()) {
            return "";
        }
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(logFile));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            return content.toString();
        } catch (Exception e) {
            return "读取失败: " + e.getMessage();
        }
    }

    public static String getStorageType() {
        return useInternalStorage ? "内部存储" : "外部存储";
    }
}
