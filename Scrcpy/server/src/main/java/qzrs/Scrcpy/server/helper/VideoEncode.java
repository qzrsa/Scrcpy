/*
 * 本项目大量借鉴学习了开源投屏软件：Scrcpy，在此对该项目表示感谢
 */
package qzrs.Scrcpy.server.helper;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.system.ErrnoException;
import android.view.Surface;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

import qzrs.Scrcpy.server.Server;
import qzrs.Scrcpy.server.entity.Device;
import qzrs.Scrcpy.server.entity.Options;
import qzrs.Scrcpy.server.wrappers.SurfaceControl;

public final class VideoEncode {
  private static MediaCodec encedec;
  private static MediaFormat encodecFormat;
  public static boolean isHasChangeConfig = false;
  // Wire values are deliberately stable: 0/1 retain compatibility with the
  // previous H.264/H.265 header, while 2/3 add the scrcpy 4.1 VP codecs.
  public static final byte CODEC_H264 = 0;
  public static final byte CODEC_H265 = 1;
  public static final byte CODEC_VP8 = 2;
  public static final byte CODEC_VP9 = 3;
  private static byte videoCodec;

  private static IBinder display;

  // 自适应码率下限(bps)，避免画质崩塌；当前生效码率，用于去重避免重复 setParameters
  private static final int AUTO_BITRATE_FLOOR = 1_000_000;
  private static int currentBitrate = 0;
  // 已编码输出帧数（用于向客户端上报采集帧率）
  public static volatile long encodedFrameCount = 0;

  public static void init() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException, ErrnoException {
    createEncodecFormat();
    ByteBuffer byteBuffer = ByteBuffer.allocate(9);
    byteBuffer.put(videoCodec);
    byteBuffer.putInt(Device.videoSize.first);
    byteBuffer.putInt(Device.videoSize.second);
    byteBuffer.flip();
    Server.writeVideo(byteBuffer);
    // 创建显示器
    display = SurfaceControl.createDisplay("scrcpy", Build.VERSION.SDK_INT < Build.VERSION_CODES.R || (Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !"S".equals(Build.VERSION.CODENAME)));
    startEncode();
  }

  private static void createEncodecFormat() throws IOException {
    String codecMime = selectAndCreateEncoder();
    encodecFormat = new MediaFormat();
    encodecFormat.setString(MediaFormat.KEY_MIME, codecMime);
    encodecFormat.setInteger(MediaFormat.KEY_BIT_RATE, Options.maxVideoBit);
    currentBitrate = Options.maxVideoBit; // 自适应模式从封顶起步，由客户端向下收敛
    encodecFormat.setInteger(MediaFormat.KEY_FRAME_RATE, Options.maxFps);
    encodecFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) encodecFormat.setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, Options.maxFps * 3);
    encodecFormat.setFloat("max-fps-to-encoder", Options.maxFps);
    encodecFormat.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 50_000);
    encodecFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
  }

  /**
   * Keep H.264/H.265 as the preferred path, but use VP9/VP8 when the device
   * lacks an AVC/HEVC encoder and the receiving device advertised a decoder.
   * This mirrors the codec availability improvement introduced by scrcpy 4.1.
   */
  private static String selectAndCreateEncoder() throws IOException {
    byte[] candidates = new byte[] {CODEC_H265, CODEC_H264, CODEC_VP9, CODEC_VP8};
    IOException lastError = null;
    for (byte candidate : candidates) {
      if (!isCandidateAvailable(candidate)) continue;
      try {
        String mime = mimeForCodec(candidate);
        encedec = MediaCodec.createEncoderByType(mime);
        videoCodec = candidate;
        return mime;
      } catch (IOException e) {
        lastError = e;
      }
    }
    if (lastError != null) throw lastError;
    throw new IOException("No mutually supported hardware video encoder");
  }

  private static boolean isCandidateAvailable(byte codec) {
    switch (codec) {
      case CODEC_H265:
        return Options.supportH265 && EncodecTools.isSupportH265();
      case CODEC_H264:
        return EncodecTools.isSupportH264();
      case CODEC_VP9:
        return Options.supportVp9 && EncodecTools.isSupportVp9();
      case CODEC_VP8:
        return Options.supportVp8 && EncodecTools.isSupportVp8();
      default:
        return false;
    }
  }

  private static String mimeForCodec(byte codec) {
    switch (codec) {
      case CODEC_H265:
        return MediaFormat.MIMETYPE_VIDEO_HEVC;
      case CODEC_VP8:
        return MediaFormat.MIMETYPE_VIDEO_VP8;
      case CODEC_VP9:
        return MediaFormat.MIMETYPE_VIDEO_VP9;
      default:
        return MediaFormat.MIMETYPE_VIDEO_AVC;
    }
  }

  // 初始化编码器
  private static Surface surface;

  public static void startEncode() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException, ErrnoException {
    ControlPacket.sendVideoSizeEvent();
    encodecFormat.setInteger(MediaFormat.KEY_WIDTH, Device.videoSize.first);
    encodecFormat.setInteger(MediaFormat.KEY_HEIGHT, Device.videoSize.second);
    encedec.configure(encodecFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    // 绑定Display和Surface
    surface = encedec.createInputSurface();
    setDisplaySurface(display, surface);
    // 启动编码
    encedec.start();
  }

  public static void stopEncode() {
    encedec.stop();
    encedec.reset();
    surface.release();
  }

  private static void setDisplaySurface(IBinder display, Surface surface) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    SurfaceControl.openTransaction();
    try {
      SurfaceControl.setDisplaySurface(display, surface);
      SurfaceControl.setDisplayProjection(display, 0, new Rect(0, 0, Device.displayInfo.width, Device.displayInfo.height), new Rect(0, 0, Device.videoSize.first, Device.videoSize.second));
      SurfaceControl.setDisplayLayerStack(display, Device.displayInfo.layerStack);
    } finally {
      SurfaceControl.closeTransaction();
    }
  }

  private static final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

  public static void encodeOut() throws IOException {
    try {
      // 找到已完成的输出缓冲区
      // 使用有限超时(1s)而非-1无限等待：关闭时线程可在超时后检测中断标志优雅退出，避免阻塞在 native 调用里只能靠 Runtime.exit(0)
      int outIndex;
      do {
        outIndex = encedec.dequeueOutputBuffer(bufferInfo, 1000);
      } while (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED);
      if (outIndex < 0) return; // INFO_TRY_AGAIN_LATER：本轮无输出，直接返回由调用方循环重试
      encodedFrameCount++; // 统计：成功编码出一帧
      ByteBuffer buffer = encedec.getOutputBuffer(outIndex);
      if (buffer == null) return;
      ControlPacket.sendVideoEvent(bufferInfo.presentationTimeUs, buffer);
      encedec.releaseOutputBuffer(outIndex, false);
    } catch (IllegalStateException ignored) {
    }
  }

  public static void release() {
    try {
      stopEncode();
      encedec.release();
      SurfaceControl.destroyDisplay(display);
    } catch (Exception ignored) {
    }
  }

  /**
   * 自适应码率：客户端按链路RTT动态请求的目标码率(bps)。
   * 限制在 [下限, maxVideoBit封顶] 区间内，仅当与当前值不同才调用 setParameters，
   * 并用 try/catch 兜底（编码器未在正确状态时不抛异常中断主流程）。
   */
  public static int getCurrentBitrate() {
    return currentBitrate;
  }

  public static void requestBitrate(int bitrate) {
    if (encedec == null) return;
    int clamped = Math.max(AUTO_BITRATE_FLOOR, Math.min(bitrate, Options.maxVideoBit));
    if (clamped == currentBitrate) return;
    try {
      // 运行时动态改码率：走公开的 Bundle 参数接口（MediaCodec.Parameters 为隐藏类，
      // 不在公开 SDK 中，编译期不可见）。KEY_BIT_RATE 即创建编码器时用的同一个码率键。
      Bundle params = new Bundle();
      params.putInt(MediaFormat.KEY_BIT_RATE, clamped);
      encedec.setParameters(params);
      currentBitrate = clamped;
    } catch (IllegalStateException ignored) {
    }
  }

}
