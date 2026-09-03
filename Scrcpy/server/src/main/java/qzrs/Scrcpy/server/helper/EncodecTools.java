package qzrs.Scrcpy.server.helper;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import java.util.ArrayList;
import java.util.Objects;

public class EncodecTools {
  private static ArrayList<String> hevcEncodecList = null;
  private static ArrayList<String> avcEncodecList = null;
  private static ArrayList<String> vp8EncodecList = null;
  private static ArrayList<String> vp9EncodecList = null;
  private static ArrayList<String> opusEncodecList = null;

  // 获取解码器列表
  private static void getEncodecList() {
    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
    hevcEncodecList = new ArrayList<>();
    avcEncodecList = new ArrayList<>();
    vp8EncodecList = new ArrayList<>();
    vp9EncodecList = new ArrayList<>();
    opusEncodecList = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecList.getCodecInfos()) {
      if (mediaCodecInfo.isEncoder()) {
        String codecName = mediaCodecInfo.getName();
        if (codecName.toLowerCase().contains("opus")) opusEncodecList.add(codecName);
        // 要求硬件实现
        if (!codecName.startsWith("OMX.google") && !codecName.startsWith("c2.android")) {
          for (String supportType : mediaCodecInfo.getSupportedTypes()) {
            if (Objects.equals(supportType, MediaFormat.MIMETYPE_VIDEO_HEVC)) hevcEncodecList.add(codecName);
            else if (Objects.equals(supportType, MediaFormat.MIMETYPE_VIDEO_AVC)) avcEncodecList.add(codecName);
            else if (Objects.equals(supportType, MediaFormat.MIMETYPE_VIDEO_VP8)) vp8EncodecList.add(codecName);
            else if (Objects.equals(supportType, MediaFormat.MIMETYPE_VIDEO_VP9)) vp9EncodecList.add(codecName);
          }
        }
      }
    }
  }

  // 获取解码器是否支持
  public static boolean isSupportOpus() {
    if (opusEncodecList == null) getEncodecList();
    return opusEncodecList.size() > 0;
  }

  public static boolean isSupportH265() {
    if (hevcEncodecList == null) getEncodecList();
    return hevcEncodecList.size() > 0;
  }

  public static boolean isSupportH264() {
    if (avcEncodecList == null) getEncodecList();
    return avcEncodecList.size() > 0;
  }

  public static boolean isSupportVp8() {
    if (vp8EncodecList == null) getEncodecList();
    return vp8EncodecList.size() > 0;
  }

  public static boolean isSupportVp9() {
    if (vp9EncodecList == null) getEncodecList();
    return vp9EncodecList.size() > 0;
  }

}
