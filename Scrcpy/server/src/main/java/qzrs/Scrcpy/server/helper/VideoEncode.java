/*
 * 本项目大量借鉴学习了开源投屏软件：Scrcpy，在此对该项目表示感谢
 */
package qzrs.Scrcpy.server.helper;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.IBinder;
import android.system.ErrnoException;
import android.util.Log;
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
  private static boolean useH265;

  private static IBinder display;

  public static void init() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException, ErrnoException {
    useH265 = Options.supportH265 && EncodecTools.isSupportH265();
    ByteBuffer byteBuffer = ByteBuffer.allocate(9);
    byteBuffer.put((byte) (useH265 ? 1 : 0));
    byteBuffer.putInt(Device.videoSize.first);
    byteBuffer.putInt(Device.videoSize.second);
    byteBuffer.flip();
    Server.writeVideo(byteBuffer);
    // 创建显示器，名称随机化以保护隐私
    String displayName = "scrcpy_" + System.currentTimeMillis() % 10000;
    display = SurfaceControl.createDisplay(displayName, Build.VERSION.SDK_INT < Build.VERSION_CODES.R || (Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !"S".equals(Build.VERSION.CODENAME)));
    // 创建Codec
    createEncodecFormat();
    startEncode();
  }

  private static void createEncodecFormat() throws IOException {
    String codecMime = useH265 ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
    encedec = MediaCodec.createEncoderByType(codecMime);
    encodecFormat = new MediaFormat();
    encodecFormat.setString(MediaFormat.KEY_MIME, codecMime);
    encodecFormat.setInteger(MediaFormat.KEY_BIT_RATE, Options.maxVideoBit);
    encodecFormat.setInteger(MediaFormat.KEY_FRAME_RATE, Options.maxFps);
    encodecFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) encodecFormat.setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, Options.maxFps * 3);
    // 禁用 B 帧，提高编码效率
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      encodecFormat.setInteger("max-bframes", 0);
    }
    encodecFormat.setFloat("max-fps-to-encoder", Options.maxFps);
    encodecFormat.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 50_000);
    encodecFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
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
    try {
      encedec.stop();
      encedec.reset();
    } catch (Exception e) {
      Log.w("VideoEncode", "stopEncode reset 失败，尝试 release: " + e.getMessage());
      try {
        encedec.release();
      } catch (Exception ex) {
        Log.w("VideoEncode", "stopEncode release 异常: " + ex.getMessage());
      }
    }
    if (surface != null) {
      surface.release();
      surface = null;
    }
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
      int outIndex;
      do outIndex = encedec.dequeueOutputBuffer(bufferInfo, -1); while (outIndex < 0);
      ByteBuffer buffer = encedec.getOutputBuffer(outIndex);
      if (buffer == null) return;
      ControlPacket.sendVideoEvent(bufferInfo.presentationTimeUs, buffer);
      encedec.releaseOutputBuffer(outIndex, false);
    } catch (IllegalStateException e) {
      Log.w("VideoEncode", "encodeOut 异常: " + e.getMessage());
    }
  }

  public static void release() {
    try {
      if (surface != null) {
        surface.release();
        surface = null;
      }
      if (encedec != null) {
        encedec.stop();
        encedec.release();
        encedec = null;
      }
      if (display != null) {
        SurfaceControl.destroyDisplay(display);
        display = null;
      }
    } catch (Exception e) {
      Log.w("VideoEncode", "release 异常: " + e.getMessage());
    }
  }

}
