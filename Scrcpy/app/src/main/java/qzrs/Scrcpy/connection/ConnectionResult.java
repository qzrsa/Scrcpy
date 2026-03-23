package qzrs.Scrcpy.connection;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * 连接结果，包含连接成功后的各种通道
 */
public class ConnectionResult {
    
    // 连接状态
    private boolean success;
    private String errorMessage;
    private ConnectionMode mode;
    
    // 主数据通道（控制命令）
    private Socket mainSocket;
    private InputStream mainInputStream;
    private OutputStream mainOutputStream;
    
    // 视频流通道
    private Socket videoSocket;
    private InputStream videoInputStream;
    private OutputStream videoOutputStream;
    
    // 音频流通道（可选）
    private Socket audioSocket;
    private InputStream audioInputStream;
    
    // 连接信息
    private String remoteAddress;
    private int remotePort;
    private long connectionTime;  // 连接耗时（毫秒）
    private boolean isDirect;  // 是否是直连（false 表示走了中继）

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public ConnectionMode getMode() {
        return mode;
    }

    public void setMode(ConnectionMode mode) {
        this.mode = mode;
    }

    public Socket getMainSocket() {
        return mainSocket;
    }

    public void setMainSocket(Socket mainSocket) {
        this.mainSocket = mainSocket;
    }

    public InputStream getMainInputStream() {
        return mainInputStream;
    }

    public void setMainInputStream(InputStream mainInputStream) {
        this.mainInputStream = mainInputStream;
    }

    public OutputStream getMainOutputStream() {
        return mainOutputStream;
    }

    public void setMainOutputStream(OutputStream mainOutputStream) {
        this.mainOutputStream = mainOutputStream;
    }

    public Socket getVideoSocket() {
        return videoSocket;
    }

    public void setVideoSocket(Socket videoSocket) {
        this.videoSocket = videoSocket;
    }

    public InputStream getVideoInputStream() {
        return videoInputStream;
    }

    public void setVideoInputStream(InputStream videoInputStream) {
        this.videoInputStream = videoInputStream;
    }

    public OutputStream getVideoOutputStream() {
        return videoOutputStream;
    }

    public void setVideoOutputStream(OutputStream videoOutputStream) {
        this.videoOutputStream = videoOutputStream;
    }

    public Socket getAudioSocket() {
        return audioSocket;
    }

    public void setAudioSocket(Socket audioSocket) {
        this.audioSocket = audioSocket;
    }

    public InputStream getAudioInputStream() {
        return audioInputStream;
    }

    public void setAudioInputStream(InputStream audioInputStream) {
        this.audioInputStream = audioInputStream;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public long getConnectionTime() {
        return connectionTime;
    }

    public void setConnectionTime(long connectionTime) {
        this.connectionTime = connectionTime;
    }

    public boolean isDirect() {
        return isDirect;
    }

    public void setDirect(boolean direct) {
        isDirect = direct;
    }

    /**
     * 关闭所有连接
     */
    public void closeAll() {
        try {
            if (mainSocket != null) mainSocket.close();
        } catch (Exception ignored) {}
        
        try {
            if (videoSocket != null) videoSocket.close();
        } catch (Exception ignored) {}
        
        try {
            if (audioSocket != null) audioSocket.close();
        } catch (Exception ignored) {}
    }

    /**
     * 创建成功结果
     */
    public static ConnectionResult success(ConnectionMode mode) {
        ConnectionResult result = new ConnectionResult();
        result.success = true;
        result.mode = mode;
        result.connectionTime = System.currentTimeMillis();
        return result;
    }

    /**
     * 创建失败结果
     */
    public static ConnectionResult fail(String errorMessage) {
        ConnectionResult result = new ConnectionResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }
}
